# The statement cache auditor, and the harnesses around it

`StatementCacheAuditor` checks the internal invariants of a live `GooGooStatementCache` from
outside it. It was written for [issue #196](https://github.com/swaldman/c3p0/issues/196), where two
failures reported from `Deathmarch.cullNext()` turned out to be one corrupt state discovered
thousands of operations after it arose. The auditor's purpose is to catch that state where it
happens.

It is test code, but it is not only for tests: it needs nothing but `mainLock` and package-private
access, so it can be attached to a running application — see §4.2.

For what the collections are and what must be true of them, see
`src/com/mchange/v2/c3p0/stmt/statement-cache-internals.md`. This document is about the instrument,
not the thing measured.

| Where | What |
| --- | --- |
| `test/src/com/mchange/v2/c3p0/stmt/` | the auditor, its exception, and the `cullNext` demonstration — declared into the library's own package |
| `test/src/com/mchange/v2/c3p0/impl/` | `C3P0TestInternals`, which finds live caches behind a DataSource |
| `test/src/com/mchange/v2/c3p0/test/fakedriver/` | the in-process fake JDBC driver |
| `test/src/com/mchange/v2/c3p0/test/stmt/` | the two harnesses and the demonstrations |
| `test/src/com/mchange/v2/c3p0/test/junit/` | the JUnit cases (§10) |

---

## 1. Why an outside observer is sound

Every public method of `GooGooStatementCache` holds `mainLock` for its whole duration, with exactly
one exception: `acquireStatement(...)` awaits `conditionStatementPerhapsAcquired` while the driver
prepares a Statement — and at that point the waiting thread has computed a key, found the checkout
queue empty, and mutated nothing.

**So any moment another thread can acquire `mainLock` is a moment the cache must be fully
consistent.** An auditor that takes the lock and looks is therefore a complete oracle, and needs no
cooperation from the class it audits.

Two properties follow from how it is written:

- **Everything is compared by identity, never `equals`.** Some of the failure modes here involve
  drivers whose `PreparedStatement.equals(...)` is broken ([PR #59](https://github.com/swaldman/c3p0/pull/59/)),
  and an auditor using `equals` could not tell a real inconsistency from the pathology that caused
  it. Internally it uses `Collections.newSetFromMap(new IdentityHashMap())` throughout, and looks
  keys up by scanning for `==` rather than by `Map.get`.
- **No reflection.** Every structure it reads is package-private, and it is declared into that
  package. Nothing here can fail under a security manager, a module system, or a JDK that tightens
  `setAccessible`.

The ten invariants themselves are listed in `statement-cache-internals.md` §5, and §5 of that
document is the specification this class implements. Keep them in step.

## 2. Reaching a live cache: `C3P0TestInternals`

A DataSource has one pool — and so one statement cache — **per authentication**, because Connections
authenticated as different users are not interchangeable. Every `getConnection(user, password)` with
a new credential pair gets its own.

| Method | Returns |
| --- | --- |
| `statementCacheOf( pds )` | the default authentication's cache; null if no pool exists yet, or caching is off |
| `statementCacheOf( pds, user, password )` | that authentication's cache, or null |
| `poolOf( pds )` / `poolOf( pds, user, password )` | the `C3P0PooledConnectionPool` itself, or null |
| `poolsOf( pds )` | `DbAuth` → pool, a snapshot of every pool the DataSource has |
| `authsOf( pds )` | the authentications currently pooled |
| `statementCachesOf( pds )` | every live cache, across all authentications |

`statementCachesOf(...)` is the one to watch on a DataSource serving several credentials, since it
needs no advance knowledge of which are in play.

**Nothing here creates anything.** These read the DataSource's `poolManager` field directly rather
than calling `getPoolManager()`, which would build the manager — and building it starts a `Timer`
and the helper thread pools. Observing a DataSource must not be what starts its threads, so an
untouched DataSource reports null and empty until it has served a Connection.

## 3. The auditor's API

| Method | Locking | Behavior |
| --- | --- | --- |
| `violations( cache )` | **caller must hold `mainLock`** | returns a `List` of human-readable failures; empty when consistent |
| `checkQuietly( cache )` | takes `mainLock` | the same list, with the locking done for you. Reports; never throws |
| `assertConsistent( cache, context )` | takes `mainLock` | throws `InconsistentStatementCacheException` on the first violation, and records it in `firstFailure()` |
| `dump( cache )` | takes `mainLock` (reentrant, so safe from code already holding it) | every structure, Statement by Statement |
| `startWatchdog( cache, intervalMillis )` | takes `mainLock` per check | audits on a daemon thread until interrupted; see §4.2 |
| `firstFailure()` / `resetFirstFailure()` | none | the first inconsistency seen, recorded separately because the throw may be swallowed |
| `containsStatement( cache, pstmt )` | takes `mainLock` | is this exact object cached? Identity, not `equals` |
| `removalPending( cache )` | takes `removalPendingLock` | Statements presently inside `removeStatement(...)` |
| `inAnyDeathmarch( cache, pstmt )` | takes `mainLock` | is this exact object in any deathmarch, global or per-connection? |
| `globalDeathmarch( cache )` / `perConnectionDeathmarches( cache )` | none (plain field reads) | the deathmarches, for tests that need to inspect them directly |

A closed cache is vacuously consistent: every method reports no violations once `close()` has nulled
the collections.

`InconsistentStatementCacheException` is a `RuntimeException`. Its message carries the context
string, the numbered violations, **and a full cache dump**, because these states are rare enough
that a second occurrence cannot be counted on. `getViolations()` and `getContext()` give the parts
back separately.

## 4. Four ways to use it

### 4.1 After every operation — strictest, for harnesses

```java
StatementCacheAuditor.assertConsistent( scache, "after checkinStatement" );
```

This is what both harnesses do (§7), with the context string naming the operation just completed.
It is the strictest possible use: an inconsistency is reported at the operation that caused it,
rather than later at `cullNext()`, which is all a production failure shows you. Auditing every
operation costs a `mainLock` acquisition and a walk of the structures, which is substantial — the
stress harness manages tens of thousands of operations per second anyway, and the cost is worth it
for a test.

### 4.2 As a watchdog — for a live application

The reason this class avoids reflection and creates nothing. To diagnose a cache that only misbehaves
in someone else's environment:

```bash
mill publishLocal        # the library
mill test.publishLocal   # the c3p0-test jar, which carries the auditor
```

Then, in the application, once the DataSource has served at least one Connection:

```java
GooGooStatementCache scache = C3P0TestInternals.statementCacheOf( myPooledDataSource );
Thread watchdog = StatementCacheAuditor.startWatchdog( scache, 50 ); // millis between checks
```

or, for a DataSource serving several credentials:

```java
for ( Iterator ii = C3P0TestInternals.statementCachesOf( myPooledDataSource ).iterator(); ii.hasNext(); )
    StatementCacheAuditor.startWatchdog( (GooGooStatementCache) ii.next(), 50 );
```

The watchdog thread is a daemon. It **stops at the first inconsistency** — there is no point
spinning on a broken cache, and the state is recorded in `firstFailure()` — and it returns quietly
when the cache closes. Interrupt it to stop it early.

Choosing an interval is a trade: each check takes `mainLock`, so a 50ms interval on a busy pool
is noticeable but tolerable for a diagnostic run, while several seconds is nearly free and still
catches a corruption that persists (and the ones that matter here do persist — that is what
made #196 recur). Start high; lower it if the fault escapes you.

Retrieve what it found:

```java
InconsistentStatementCacheException e = StatementCacheAuditor.firstFailure();
if ( e != null ) log.severe( e.getMessage() ); // context, violations, and the full dump
```

### 4.3 Quietly — when you want the answer, not an exception

```java
List violations = StatementCacheAuditor.checkQuietly( scache );
```

Used by the demonstrations, which show the state before and after an injected fault rather than
failing on it.

### 4.4 As a dump — for a bug report

```java
System.err.println( StatementCacheAuditor.dump( scache ) );
```

Prints `removalPending`, `stmtToKey` (with each Statement marked checked in or out), `checkedOut`,
each `KeyRec`'s `allStmts` and `checkoutQueue`, `cxnStmtMgr` by Connection, and every deathmarch in
LRU order. Keys are rendered by the auditor rather than by `StatementCacheKey.toString()`, and
include the key's identity hash and every discriminating field, since two keys can carry the same
SQL on the same Connection and differ only in result-set type.

## 5. Reading the output

A violation looks like this — the state that produces both failures reported in #196:

```
com.mchange.v2.c3p0.stmt.InconsistentStatementCacheException: Statement cache inconsistency
detected [after checkoutStatement] -- 2 violation(s):
  * Statement appears in the allStmts of more than one key: FakeStmt-80[FakeCxn-3, 'SELECT col5 ...']
  * Statement is filed under key@4d179cbe['SELECT col5 ...' @ FakeCxn-3, rs=1003/1007] but stmtToKey
    maps it to key@10132b2b['SELECT col5 ...' @ FakeCxn-3, rs=1004/1007]: FakeStmt-80[...]

CACHE DUMP:
...
```

Each message maps to an invariant in `statement-cache-internals.md` §5:

| Message begins | Invariant | Usually means |
| --- | --- | --- |
| `removalPending is not empty between operations` | 1 | something threw inside `removeStatement(...)`; those Statements can never be removed again |
| `Statement in KeyRec.allStmts ... is absent from stmtToKey`, `The union of all KeyRec.allStmts does not match` | 2 | a removal aborted partway, or a Statement was assimilated twice |
| `Statement appears in the allStmts of more than one key`, `Statement is filed under ... but stmtToKey maps it to` | 2 | the driver handed back a Statement c3p0 still held, so `stmtToKey.put(...)` overwrote |
| `Statement is simultaneously available for checkout and marked checked out`, `checkoutQueue ... contains duplicates`, `appears in more than one checkoutQueue` | 3 | checkout and check-in disagree |
| `Statement is marked checked out but is not in stmtToKey` | 4 | removed while checked out, or assimilation failed partway |
| `cxnStmtMgr ... but absent from stmtToKey`, `filed under connection ... but its key names`, `retains an empty Statement set` | 5 | a Statement stranded in `cxnStmtMgr` — it pins that Connection's count at its maximum forever |
| `has diverged: stmtsToLongs holds ... longsToStmts holds` | 6 | a deathmarch's two maps disagree |
| **`A Statement is in ... but is absent from stmtToKey`** | **7** | **this is issue #196**: the next cull will throw either an NPE on `sck.stmtText` or "Inconsistency!!! Statement culled from deathmarch failed to be removed" |
| `A Statement is in ... while checked out` | 7 | a checked-out Statement was left in a deathmarch, and could be culled from under its user |
| `The global deathmarch does not hold exactly`, `The per-connection deathmarches do not together hold`, `The checkout queues do not together hold` | 8 | the three views of "checked in" have drifted apart; which pair disagrees localizes it |
| `A Statement belonging to ... is in the deathmarch for` | 9 | a per-connection deathmarch holds another Connection's Statement |
| `Connections with a Deathmarch do not match connections with cached Statements` | 10 | `cxnsToDms` and `cxnToStmtSets` disagree |

## 6. The fake driver

`test/src/com/mchange/v2/c3p0/test/fakedriver/` is an in-process JDBC driver: no database, no
network, runs at CPU speed, and misbehaves on demand. Connections and Statements are
`java.lang.reflect.Proxy` instances, which avoids hand-implementing some three hundred methods and
makes `equals` breakable per Statement.

Register a config, then point a DataSource at its URL (`jdbc:c3p0fake:<name>`):

```java
FakeDriver.ensureRegistered();
FakeDriverConfig cfg = FakeDriverConfig.register( "my-run", seed );
cfg.prepareLatencyMaxMillis = 5;
ds.setDriverClass( FakeDriver.DRIVER_CLASS_NAME );
ds.setJdbcUrl( cfg.jdbcUrl() );
ds.setForceUseNamedDriverClass( true ); // see the note below
```

`setForceUseNamedDriverClass(true)` is worth setting for any in-process driver: `DriverManager`
offers a URL to registered drivers in registration order and takes the first Connection it is
offered, so a badly behaved driver registered by another test can answer for yours. That is not
hypothetical — it hung this suite once, and is why `MockDriver` now accepts only its own schemes.

### Knobs, and what each attacks

| Field | Default | Attacks |
| --- | --- | --- |
| `prepareLatencyMinMillis` / `prepareLatencyMaxMillis` | 0 | widens the `mainLock`-releasing await in `acquireStatement()` — the one point where cache state can change mid-`checkoutStatement` |
| `closeLatencyMinMillis` / `closeLatencyMaxMillis` | 0 | widens the asynchronous/deferred destruction window |
| `notPoolableProbability` | 0 | drives check-in into its `IrreversibleHazardException` path, and so into `removeStatement( ps, DESTROY_ALWAYS )` against a Statement just re-added to `checkedOut` |
| `refreshFailureProbability` | 0 | the same path via a `SQLException` from `clearParameters()`/`clearBatch()`/`clearWarnings()` |
| `closeFailureProbability` | 0 | destruction that fails |
| `executeFailureProbability` | 0 | Statement-level failures mid-workload |
| `recycleClosedStatementProbability` | 0 | Oracle-style implicit statement caching: reissues a `PreparedStatement` object it handed out before, once closed |
| `handBackLiveStatementProbability` | 0 | **out of spec**: reissues an object it (and c3p0) still consider open. Corrupts the cache within tens of operations; kept for investigating driver-identity hypotheses, not for regression runs |
| `returnNullFromPrepareProbability` | 0 | **out of spec**: returns null having thrown nothing, which used to strand the acquiring thread for good |
| `connectionInvalidProbability` | 0 | `isValid(...)` failures, forcing pool churn |
| `supportLargeMaxRows` | true | when false, `get/setLargeMaxRows` throw `SQLFeatureNotSupportedException`, exercising `CarefulMaxRowsReaderWriter`'s fallback |

### `FakeDriverStats` — oracles independent of the cache

Reachable as `cfg.stats`. The driver tracks every Statement it issues, so these catch things the
cache's own invariants cannot:

| | Meaning |
| --- | --- |
| `anomalies()` | use-after-close: c3p0 handed out or used a Statement the driver had closed. Always a defect |
| `unclosedStatements()` | still open at the end of a run. After an orderly shutdown, must be empty |
| `redundantCloses` | closed more than once. Legal per JDBC, but means c3p0 destroyed the same Statement twice |
| `statementsPrepared` / `statementsClosed` | should be equal after an orderly shutdown |
| `statementsRecycled` | how often a recycling mode actually fired, so a scenario cannot pass by never exercising it |

## 7. The harnesses

### 7.1 Cache-level: `StatementCacheStressHarness`

Drives `GooGooStatementCache` directly, with no pool or proxies in the way, at deliberately tiny
maxima so nearly every checkout must cull.

Fidelity matters: `NewPooledConnection` wraps every one of its calls into the cache in
`synchronized (this)`, so in production cache calls concerning one physical Connection are
serialized. `SimulatedPooledConnection` reproduces that discipline, and the pool's
`waitMarkConnectionInUse` / `tryMarkConnectionInUse` / `unmarkConnectionInUse` protocol, so the
harness cannot "reproduce" a failure that production locking makes impossible. Its
`serializePerConnection` flag exists to ask the opposite question.

| Scenario | Covers |
| --- | --- |
| `perConnection-incautious` | the shape reported in #196, with c3p0's default (incautious) destruction |
| `perConnection-cautious` | ... and with a deferred statement destroyer, which holds Statements back while their Connection is in use |
| `globalMax-incautious` | one global deathmarch, so threads on different Connections cull each other's Statements |
| `doubleMax-cautious` | both maxima, so every checked-in Statement is in two deathmarches at once |
| `doubleMax-refreshFailures` | Statements that refuse to be refreshed, plus close and execute failures |
| `perConnection-cautious-recycling` | Oracle-style statement recycling, and no `largeMaxRows` support |
| `doubleMax-everything` | all of the above, with the widest prepare latency and the most Connection churn |

### 7.2 Full stack: `StatementCacheFullStackHarness`

The same audit through a real `ComboPooledDataSource` over the fake driver, so the proxies, the
resource pool, connection testing, expiry and the deferred destroyer all take part. Connections are
configured to expire aggressively, because Connection destruction concurrent with statement caching
is where the pool and the cache interact most.

| Scenario | Covers |
| --- | --- |
| `fullstack-perConnection` | the reporter's shape, default destruction |
| `fullstack-perConnection-deferredClose` | with a deferred statement destroyer |
| `fullstack-doubleMax-deferredClose` | both maxima |
| `fullstack-churn-and-faults` | one-second Connection lifetimes, driver faults, statement recycling, `cancelAutomaticallyClosedStatements` |

Two things it does that the cache-level harness cannot:

- **It watches what c3p0 logs.** A pool thread that hits an internal inconsistency logs it and
  carries on, so a harness that only caught exceptions from its own threads would miss it. A
  `java.util.logging.Handler` scans for the cache's own inconsistency messages and fails the run.
- **It attributes leaks.** Never-closed Statements are split into those the client had closed
  (c3p0's check-in path lost them) and those the client abandoned (nothing swept them up when the
  logical Connection closed). Both fail the run; the bucket tells you where to look.

### 7.3 The demonstrations

Narrated, single-threaded, deterministic — for understanding a failure rather than hunting one.

| Class | Shows |
| --- | --- |
| `RemovalPendingLeakDemo` | how an exception inside `removeStatement(...)` used to strand a Statement in `removalPending`, and what the cache looks like afterwards. Reaches the state by driving the public API |
| `CullNextInconsistencyDemo` | what `cullNext()` does when it meets the two #196 states, which it injects directly, and that the auditor names both **before** the cull walks into them |
| `Issue196Demo` | runs both and prints a verdict. Exits 0 when the cache survives everything |

## 8. System properties

All are read by the harnesses' `main(...)`, and are passed through `C3P0_TEST_JVM_ARGS` (see
`CLAUDE.md` for how the build forwards that).

| Property | Applies to | Default | Meaning |
| --- | --- | --- | --- |
| `c3p0.test.stmtcache.durationSeconds` | both | 10 | per scenario |
| `c3p0.test.stmtcache.seed` | both | the clock | printed on every run; set it to repeat one |
| `c3p0.test.stmtcache.threads` | both | 16 | worker threads |
| `c3p0.test.stmtcache.distinctSql` | both | 8 | size of the SQL alphabet, ie how many distinct keys per Connection |
| `c3p0.test.stmtcache.scenario` | both | all | run only the named scenario |
| `c3p0.test.stmtcache.quiet` | both | true | quiets c3p0's own logging, which at INFO buries results under "Multiply-cached PreparedStatement". `=false` restores it |
| `c3p0.test.stmtcache.connections` | stress | 6 | simulated pooled Connections |
| `c3p0.test.stmtcache.auditEveryOps` | stress | 1 | audit every n-th operation. Raise it to let corruption accumulate |
| `c3p0.test.stmtcache.keepGoing` | stress | false | count the cache's own internal-inconsistency throws and carry on, the way an application that logs and continues would. For watching a damaged cache degenerate; auditor failures stay fatal |
| `c3p0.test.stmtcache.handBackLiveProbability` | stress | 0 | the out-of-spec driver mode of §6 |
| `c3p0.test.stmtcache.maxStatementsPerSession` | full stack | 6 | Statements open at once per session; below `maxStatementsPerConnection` there are no overload Statements |
| `c3p0.test.stmtcache.abandonProbability` | full stack | 0.15 | how often a client leaves a Statement unclosed |

**Run with `-ea`.** `Deathmarch` guards its methods with `assert mainLock.isHeldByCurrentThread()`,
and the harnesses warn at startup if assertions are off.

## 9. Commands

```bash
# the three harness entry points -- no database required, nonzero exit on failure
C3P0_TEST_JVM_ARGS='-ea' mill test.c3p0StmtCacheStress
C3P0_TEST_JVM_ARGS='-ea' mill test.c3p0StmtCacheFullStack
C3P0_TEST_JVM_ARGS='-ea' mill test.c3p0StmtCacheIssue196

# a long soak with a replayable seed
C3P0_TEST_JVM_ARGS='-ea -Dc3p0.test.stmtcache.durationSeconds=600 -Dc3p0.test.stmtcache.seed=12345' \
  mill test.c3p0StmtCacheStress

# one scenario, verbose c3p0 logging
C3P0_TEST_JVM_ARGS='-ea -Dc3p0.test.stmtcache.scenario=doubleMax-everything -Dc3p0.test.stmtcache.quiet=false' \
  mill test.c3p0StmtCacheStress

# let corruption accumulate rather than stopping at the first sign
C3P0_TEST_JVM_ARGS='-ea -Dc3p0.test.stmtcache.auditEveryOps=100000000 -Dc3p0.test.stmtcache.keepGoing=true' \
  mill test.c3p0StmtCacheStress

# one JUnit case
mill test.testOnly com.mchange.v2.c3p0.test.junit.StatementCacheInvariantsJUnitTestCase
```

## 10. What the tests use

`mill test` runs all of these. The whole statement-cache set takes about six seconds, of which
six are the two three-second stress runs; everything else is measured in milliseconds.

| Test | Asserts | Uses |
| --- | --- | --- |
| `StatementCacheInvariantsJUnitTestCase` | two short fixed-seed stress runs stay consistent, leak nothing, and provoke no driver anomalies | the stress harness, auditing every operation |
| `StatementCacheIssue196JUnitTestCase` | the auditor names both #196 states, and `cullNext()` no longer throws `NullPointerException` on a keyless deathmarched Statement | `CullNextInconsistencyDemo` → `checkQuietly`, `dump` |
| `RemovalPendingLeakJUnitTestCase` | `removeStatement(...)` never strands `removalPending`, and the cache recovers from a driver whose `equals` is broken | `RemovalPendingLeakDemo` → `checkQuietly`, `removalPending`, `inAnyDeathmarch` |
| `NullStatementAcquisitionJUnitTestCase` | a driver returning null from `prepareStatement`/`prepareCall` fails fast instead of stranding the acquiring thread | the fake driver only |
| `StatementCacheCloseReleasesWaitersJUnitTestCase` | closing the cache releases threads waiting for a Statement, including one whose acquisition task was discarded unrun | the fake driver only |
| `C3P0TestInternalsJUnitTestCase` | per-authentication pools and caches are reachable and distinct, and observing creates nothing | `C3P0TestInternals` |

Both of the last two join their threads with a timeout: **a test for an unbounded wait must not
itself be able to wait unboundedly**, or a regression hangs the build instead of reporting.

## 11. Costs and caveats

- **`firstFailure()` is static and global**, shared across every cache in the JVM. Call
  `resetFirstFailure()` at the start of a run, as the harnesses do.
- **The watchdog stops at the first inconsistency.** It records and returns rather than spinning on
  a cache that will now fail every check.
- **Auditing takes `mainLock`**, so it contends with real work. Per-operation auditing is for tests;
  in production, prefer a watchdog at an interval of seconds (§4.2).
- **`violations( cache )` requires the caller to hold `mainLock`.** Use `checkQuietly(...)` or
  `assertConsistent(...)` unless you already hold it.
- **The auditor sees a closed cache as consistent**, which is correct but means a check that runs
  after shutdown proves nothing.
- **Identity everywhere.** If you extend the auditor, do not reach for `Map.get`, `Set.contains` or
  `List.remove` on Statements: a driver with a broken `equals` will make them lie, and the point of
  this class is to be the thing that does not.
