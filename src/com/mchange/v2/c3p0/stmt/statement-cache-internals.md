# The c3p0 Statement Cache: structures and invariants

`GooGooStatementCache` caches physical `PreparedStatement`s and `CallableStatement`s on behalf of
c3p0's pooled `Connection`s. It keeps six or seven collections in step with one another, and almost
every hard bug in this package has been one of them drifting out of agreement with the others.

This document says what those collections hold, what must be true of them between operations, and
which locks protect what. It describes the code as it stands; if you change the code, change this.

Everything here is checked mechanically. `StatementCacheAuditor`, in the test module and declared
into this package, verifies the invariants in §5 against a live cache. See §11.

---

## 1. The files

| File | What it is |
| --- | --- |
| `GooGooStatementCache.java` | Everything: the collections, the algorithms, and the nested `KeyRec`, `Deathmarch`, `ConnectionStatementManager` and `StatementDestructionManager` classes |
| `PerConnectionMaxOnlyStatementCache.java`, `GlobalMaxOnlyStatementCache.java`, `DoubleMaxStatementCache.java` | The three concrete caches. Each supplies a culling policy and a set of deathmarches |
| `StatementCacheKey.java` and its three subclasses | The cache key. Only `ValueIdentityStatementCacheKey` is live — see §10 |
| `Hazards.java`, `IrreversibleHazardException.java` | Statement state a client mutated, and whether it can be undone — see §9 |
| `CarefulMaxRowsReaderWriter.java` | `maxRows` across drivers that may lack `getLargeMaxRows()`/`setLargeMaxRows()` |
| `StatementCache.java` | Vestigial, and `@Deprecated` since 00548767. Nothing implements it; `GooGooStatementCache` declares the same methods independently |

## 2. Three caches, one mechanism

`C3P0PooledConnectionPool` picks the implementation from configuration. All three share
`GooGooStatementCache`'s collections and differ only in `prepareAssimilateNewStatement(pcon)` — the
method that decides whether there is room for one more Statement, culling if need be — and in which
deathmarches they keep.

| Implementation | Configured by | Deathmarches | Room for a new Statement when |
| --- | --- | --- | --- |
| `PerConnectionMaxOnlyStatementCache` | `maxStatementsPerConnection` only | one per Connection | that Connection holds fewer than its maximum, or a cull from that Connection's deathmarch succeeds |
| `GlobalMaxOnlyStatementCache` | `maxStatements` only | one, global | the cache holds fewer than the maximum, or a cull from the global deathmarch succeeds |
| `DoubleMaxStatementCache` | both | one global **and** one per Connection | the per-Connection limit allows it *and* the global limit does, culling from whichever deathmarch is at its limit |

With neither maximum set there is no cache at all, and `scache` is null throughout the pool.

## 3. What a Statement can be

A physical Statement the cache has produced is in exactly one of three conditions.

- **Checked out.** In the cache, held by a client, not available to anyone else and not eligible for
  culling. It is in `checkedOut`.
- **Checked in.** In the cache and idle: available for checkout, and eligible for culling. It is in
  its key's `checkoutQueue` and in every deathmarch that covers its Connection.
- **Overload.** Returned to the client but *not cached*: it appears in none of the collections. The
  cache destroys it if it is ever checked in, and knows nothing about it otherwise. Three things
  produce one, all decided inside `checkoutStatement(...)`:

  - **No room.** `prepareAssimilateNewStatement(...)` refused — every Statement the cache held for
    that Connection was checked out, so nothing could be culled. This is the original case, and the
    one the name describes.
  - **A `closeAll(...)` straddled the acquisition** — see §7.
  - **The driver reissued a Statement the cache already holds** — see below.

  Because the cache does not track it, nothing here can clean it up: `checkinAll(...)` and
  `closeAll(...)` sweep only Statements the cache accepted. So the caller is told. The four-argument
  `checkoutStatement(...)` sets its `actuallyCachedHolder` to say whether the Statement was taken
  into the cache, and `NewPooledConnection` registers the ones that were not through
  `markActiveUncachedStatement(...)`, so that `cleanupUncachedStatements(...)` closes them when the
  logical Connection closes — the same machinery that handles Statements when caching is off
  entirely. Before that (6df8c127), an overload Statement whose client never closed it stayed open
  for the life of the physical Connection.

That third case is the newest and the most surprising, so it is worth stating plainly: **do not
presume that `Connection.prepareStatement(...)` hands back a Statement the cache has never seen.**
Some drivers cache Statements themselves — Oracle's implicit statement caching, notably — and
anything wrapping Connections can have the same effect. Assimilating such a Statement a second time
forks the bookkeeping: `stmtToKey.put(...)` repoints it at the new key while it remains in the old
key's `allStmts` and `checkoutQueue`, and `checkedOut.add(...)` marks as checked out a Statement
still sitting in a deathmarch. Nothing notices at the time. It surfaces later and elsewhere, as
"A statement is being double-deathmatched" or "A checked-out statement has no key associated with
it" — the pair reported against 0.14.2-pre1.

So `assimilateNewCheckedOutStatement(...)` declines a Statement already present in `stmtToKey`, and
*disowns* the copy it holds by `removeStatement( ps, DESTROY_NEVER )`. `DESTROY_NEVER`
emphatically: the object being disowned is the very one about to be returned to a client. The
client still gets its Statement — if the driver's own cache was content to hand it out, we have no
reason to refuse — but this cache stops offering it for checkout and stops culling it while
somebody is using it. Caching remains correct while this happens, only less effective, and it is
logged at `WARNING`.

## 4. The collections

### 4.1 Guarded by `mainLock`

| Field | Type | Holds |
| --- | --- | --- |
| `stmtToKey` | `HashMap` | **every cached Statement** → its `StatementCacheKey`. This map defines cache membership; the others must agree with it |
| `keyToKeyRec` | `HashMap` | key → `KeyRec` |
| `KeyRec.allStmts` | `HashSet` | every cached Statement produced under that key. Usually one, more when a client prepares the same statement concurrently (logged as "Multiply-cached PreparedStatement") |
| `KeyRec.checkoutQueue` | `LinkedList` | those of `allStmts` that are checked in, ie available for checkout |
| `checkedOut` | `HashSet` | every cached Statement currently held by a client |
| `cxnToValidAcquiringThreadSet` | `HashMap` | physical Connection → the threads presently acquiring a Statement for it. Unlike everything else here, this is *not* empty between operations — that is its whole purpose (§7) |
| `cxnStmtMgr` | `ConnectionStatementManager` | its `cxnToStmtSets` maps physical Connection → the set of cached Statements on it. This is what enforces per-Connection maxima and what `closeAll(pcon)` walks |
| `globalDeathmarch` | `Deathmarch` | on `GlobalMaxOnly` and `DoubleMax`: all checked-in Statements, in LRU order |
| `dcsm.cxnsToDms` | `Map` | on `PerConnectionMaxOnly` and `DoubleMax`: physical Connection → that Connection's `Deathmarch` |

A `Deathmarch` is a pair of mutually inverse maps: `longsToStmts`, a `TreeMap` keyed by an
ever-increasing `long`, so iteration yields least-recently-checked-in first; and `stmtsToLongs`, its
inverse, for removal. A Statement joins a deathmarch on check-in and leaves it on checkout or
removal. Culling walks `longsToStmts` from the LRU end.

### 4.2 Guarded by their own locks

| Field | Lock | Holds |
| --- | --- | --- |
| `removalPending` | `removalPendingLock` | Statements currently inside `removeStatement(...)`, so that a second, concurrent removal does not repeat the work. **Must be empty between operations** — see §5.1 |
| `stmtToHazards` | its own monitor | `WeakHashMap` of Statement → `Hazards`. Weak because the Statement proxies mark hazards on Statements that may never have entered the cache |
| `inUseConnections`, `connectionsToZombieStatementSets` | `csdmLock` | `CautiousStatementDestructionManager` only — see §8 |

## 5. The invariants

These hold **whenever another thread can acquire `mainLock`**. That is a stronger claim than
"between operations": two operations release the lock partway (§7), and the invariants hold across
those windows too. `cxnToValidAcquiringThreadSet` is the one structure exempt, being non-empty
precisely while a thread sits in one of them.

Throughout, "the cached Statements" means `stmtToKey.keySet()`, and membership is by **identity**.
Statements are compared by identity deliberately: a driver whose `PreparedStatement.equals(...)` is
broken is a real hazard here (see PR #59), and identity is the only comparison it cannot defeat.

1. **`removalPending` is empty.** A Statement left behind can never be removed from the cache again,
   because every later `removeStatement(...)` for it returns at the guard. This is why that method's
   body sits in a `try`/`finally`. A leak here is what turned an isolated fault into the permanent,
   recurring corruption of [issue #196](https://github.com/swaldman/c3p0/issues/196).
2. **The `KeyRec.allStmts` sets partition the cached Statements**, and each Statement is in the
   `allStmts` of exactly the key `stmtToKey` names for it.
3. **Each `checkoutQueue` is a duplicate-free subset of its own `allStmts`, disjoint from
   `checkedOut`.** A Statement cannot be both available and checked out.
4. **`checkedOut` is a subset of the cached Statements.**
5. **`cxnStmtMgr`'s statement sets partition the cached Statements**, and each Statement is filed
   under exactly the Connection its key names. No empty set is retained. A Statement stranded here
   inflates its Connection's count forever, so every later checkout on that Connection culls.
6. **Each deathmarch's `stmtsToLongs` and `longsToStmts` are mutual inverses** of equal size.
7. **Every deathmarched Statement is cached and not checked out.** Both failures reported in issue
   #196 are this invariant violated, discovered late by `cullNext()`: with `FINEST` loggable it
   throws `NullPointerException` reading `sck.stmtText`; without, `removeStatement(...)` cannot act
   on a Statement it has no key for and the post-check throws `Inconsistency!!! Statement culled
   from deathmarch failed to be removed`.
8. **The checked-in Statements** — cached minus `checkedOut` — **are exactly each of** (1) the
   contents of the global deathmarch (where there is one); (2) the union of the per-Connection
   deathmarches (where there are); *and* (3) the union of all `checkoutQueue`s. Each is checked
   against the checked-in set separately, so a disagreement between any two of them is a bug, and
   which two tells you where to look.
9. **Each per-Connection deathmarch holds only its own Connection's Statements.**
10. **A `Deathmarch` exists for exactly those Connections that have cached Statements**, ie
    `cxnsToDms.keySet()` equals `cxnToStmtSets.keySet()`.

A closed cache satisfies all of these vacuously: `close()` nulls out `cxnStmtMgr`, `stmtToKey`,
`keyToKeyRec` and `checkedOut`, and `isClosed()` is `cxnStmtMgr == null`.

## 6. Operations

All five entry points take `mainLock`. Three hold it throughout; `checkoutStatement(...)` and
`checkinStatement(...)` release it partway (§7).

| Method | What it does to the structures |
| --- | --- |
| `checkoutStatement(pcon, method, args [, actuallyCachedHolder])` | Cache hit: takes the head of the key's `checkoutQueue`, adds it to `checkedOut`, removes it from the deathmarches. Miss: registers the calling thread in `cxnToValidAcquiringThreadSet`, acquires a new Statement (§7), then caches it — adding it to `stmtToKey`, the key's `allStmts`, `cxnStmtMgr` and `checkedOut` — unless a `closeAll(pcon)` intervened, `prepareAssimilateNewStatement(pcon)` found no room, or the driver handed back a Statement already cached. Each of those three comes back as an overload Statement instead (§3). The optional holder reports which happened, so the caller can take responsibility for what the cache declined to keep |
| `checkinStatement(pstmt)` | Three phases. Under the lock: a Statement that is not checked out is destroyed (not ours, or an overload Statement) or ignored (already checked in), and we are done. Then, **lock released**, `refreshStatement(...)` restores its state (§9); the Statement stays in `checkedOut` throughout, so it is nobody else's to hand out. Under the lock again, everything is retested, because anything may have happened meanwhile: a closed cache destroys it; a Statement no longer checked out is destroyed or ignored as before; one that failed to refresh is removed with `DESTROY_ALWAYS`; and only otherwise is it taken out of `checkedOut`, appended to its key's `checkoutQueue`, and added to the deathmarches |
| `checkinAll(pcon)` | Checks in every checked-out Statement on that Connection. Iterates a clone, since check-in can remove |
| `closeAll(pcon)` | Under the lock: drops `pcon` from `cxnToValidAcquiringThreadSet`, so that Statements presently being acquired for it will not be cached (§7), then removes every Statement on that Connection with `DESTROY_NEVER`. Then, **lock released**, hands each to `deferredDestroyStatement(...)`. **Callers must already have marked `pcon` in use** — see §8 |
| `close()` | Destroys every cached Statement synchronously, closes the destruction manager, nulls the collections, and signals `conditionStatementPerhapsAcquired` so that nobody is left waiting for a Statement that will now never arrive (§7) |

`removeStatement(ps, policy)` is the single path out of the cache, used by `checkinStatement`,
`closeAll`, `cullNext`, and the duplicate guard in `assimilateNewCheckedOutStatement` (§3). It
removes the Statement from every collection above, and destroys it according to the policy:
`DESTROY_NEVER`, `DESTROY_IF_CHECKED_IN`, `DESTROY_IF_CHECKED_OUT`, `DESTROY_ALWAYS`. It must leave
nothing behind however it exits — including by exception, which a misbehaving driver can cause — so
its body sits in a `try`/`finally` that always clears `removalPending`, and it logs and returns
rather than dereferencing a key it no longer has.

`Deathmarch.cullNext()` picks the least recently checked-in Statement that is not checked out —
defensively, since by invariant 7 no deathmarched Statement should be checked out — and removes it
with `DESTROY_ALWAYS`. A deathmarched Statement with no key is an inconsistency: it is logged,
dropped from the deathmarch, and the next candidate tried.

## 7. Locking, and the two windows

**`mainLock` is held for the whole of every public method, with two exceptions.** Both are points
at which the cache must make a blocking JDBC call, and `mainLock` is global to the pool: holding it
across one of those would let a single slow Connection stall caching for every other.

- **Window 1, acquisition.** `acquireStatement(...)` hands statement production to
  `blockingTaskAsyncRunner` and awaits `conditionStatementPerhapsAcquired`, releasing `mainLock`
  while the driver prepares the Statement.
- **Window 2, refresh.** `checkinStatement(...)` releases the lock to run `refreshStatement(...)`,
  which restores a Statement to its pristine condition (§9) and may make a series of JDBC calls
  doing it. `refreshStatement(...)` must not be called under `mainLock`, and says so at its
  declaration.

**In both, the cache is left consistent.** That is what makes an external observer such as
`StatementCacheAuditor` a complete oracle: any moment another thread can take `mainLock` is a moment
the invariants of §5 hold. The reasons differ. In window 1 the waiting thread has mutated nothing —
it computed a key, found the checkout queue empty, and registered itself as a valid acquirer. In
window 2 the Statement remains in `checkedOut`, which is not a bookkeeping convenience but the
truth: its client has let go, the cache has not yet taken it back, and until it does the Statement
is nobody else's to hand out.

**In both, cache state can change across the window**, so neither may presume atomicity before and
after. By the time either resumes, the key's queue, the maxima, the Connection's Statement set — and
whether the cache is open at all — may differ from what it saw. Both cope by rechecking rather than
remembering, but they must recheck different things.

`checkinStatement(...)` retests the state directly: is the cache still open, is the Statement still
checked out. Either may have changed, and the answers determine everything it does next.

`checkoutStatement(...)` cannot, because what it needs to know is not the state now but whether
something happened *while it waited*. `closeAll(pcon)` is the case that matters. It flushes a
Connection's cached Statements, but a thread inside window 1 holds a Statement that is in no
collection yet, so nothing sweeps it; assimilating it afterwards would recreate that Connection's
statement set and `Deathmarch` in a cache that had just been emptied of them, with nothing left to
clean them up. Hence `cxnToValidAcquiringThreadSet`: a thread adds itself before acquiring,
`closeAll(pcon)` removes that Connection's entry entire, and a thread that finds its own
registration gone knows a `closeAll` straddled its acquisition and returns its Statement uncached
(§3). Note what this deliberately does *not* do. `closeAll(pcon)` does not mean the Connection is
being destroyed — it is documented only as flushing that Connection's cached Statements, and a
caller might reasonably use it to free capacity held by an idle Connection. So acquisitions
beginning *after* it must cache normally; only those that straddled it are affected.

The wait in window 1 ends on one of three things, and every one of them must be made to happen,
because none of them is guaranteed by the driver:

- **a Statement**, the ordinary case;
- **an exception** — including one the cache manufactures, when a driver returns null from a
  statement-producing method rather than returning a Statement or throwing, which would otherwise
  satisfy neither condition and leave the waiter here for good;
- **the cache closing**, which `close()` signals. A DataSource shutdown closes the cache before it
  closes the task runner, and the runner discards whatever acquisition tasks were still queued — a
  discarded task never runs, so it never signals, so without that signal a waiter behind one would
  wait forever, holding its `NewPooledConnection`'s monitor. Waiters released this way see
  `ResourceClosedException`, which the Statement proxies catch in order to fall back to an uncached
  Statement, so a Connection in use survives a reset and merely loses the cache.

Lock order is `mainLock` first, then any of `removalPendingLock`, the `stmtToHazards` monitor, or
`csdmLock`. Nothing acquires `mainLock` while holding one of those, except the statement-acquisition
task, which holds none.

Callers add a layer the cache does not know about: `NewPooledConnection` wraps every call into the
cache in `synchronized (this)`, so cache operations concerning one physical Connection are
serialized. Both windows are entered while holding that monitor, so in production neither
interleaving above can arise at all. That is worth being uneasy about rather than reassured by: it
means the cache's correctness here rests on a discipline kept by another class, which nothing checks
and a later refactor could quietly drop. Both windows are defended within the cache itself for that
reason.

`ReentrantLock` and `Condition` are used rather than `synchronized`/`wait()` because native
`wait()` pins virtual threads.

## 8. Destroying Statements

Removal from the cache and physical `close()` are separate steps, mediated by a
`StatementDestructionManager` chosen at construction:

- **`IncautiousStatementDestructionManager`** — the default, used when
  `statementCacheNumDeferredCloseThreads` is 0. Closes Statements on the shared task runner, whenever
  asked.
- **`CautiousStatementDestructionManager`** — used when a deferred-destroy thread is configured.
  Some drivers (Oracle, notably) object to a Statement being closed while its parent Connection is
  in use elsewhere. So this manager tracks in-use Connections, and parks Statements removed from a
  busy Connection in `connectionsToZombieStatementSets` — out of the cache, not yet closed — until
  the Connection is released. A Connection with Statements still awaiting destruction cannot be
  checked out again; the pool's `tryMarkConnectionInUse(...)` refuses it and tries another.

The pool brackets each checkout with `waitMarkConnectionInUse(...)`/`tryMarkConnectionInUse(...)`
and `unmarkConnectionInUse(...)` — marking as it hands a Connection out and unmarking as it takes it
back, so the mark spans the whole of a client's use, every Statement check-in included. Nothing
marks per Statement.

**The marker neither nests nor counts.** A second mark is not an increment, and the first unmark
clears it outright. So nothing may mark a Connection already marked: two attempts to be careful on
one call path leave the Connection *unmarked* for the remainder of the outer one, which is worse
than either alone.

`closeAll(pcon)` therefore **requires that its caller have already marked `pcon` in use**, rather
than marking on its own behalf. It does a great deal of work on one Connection's children, so a mark
is needed; but it is only ever reached from a path that has already marked, so taking one itself
would be exactly the double-mark above. It asserts the requirement rather than trusting it, and
`NewPooledConnection.closeAllCachedStatements()` records the obligation at the one place inside c3p0
that calls it. The destruction it queues goes through `deferredDestroyStatement(...)`, so that a
Cautious manager can hold those Statements back should the Connection prove to be in use for a
reason we have not contemplated.

## 9. Statement state, and hazards

A cached Statement must be indistinguishable from a fresh one. On check-in, `refreshStatement(...)`
clears parameters, batches and warnings, and reverses whatever else the client changed. It runs
**outside `mainLock`** (§7), but still synchronously, on the client's own thread. That cost is old
and has never occasioned complaint — but it is growing: through 0.14.x a refresh made two JDBC
calls, and as of 0.15.0 it can make nine. `checkinStatement(...)` carries a note on what an
asynchronous check-in would have to guarantee first, which comes to this: no Statement refresh may
outlive the in-use mark on its Connection (§8).

Clients change that state through the proxies, which report each mutation to the cache
(`markQueryTimeoutUpdatedFrom(...)`, `markMaxRowsUpdatedFrom(...)`, and so on). The cache records
the *original* value in a `Hazards` object in `stmtToHazards` and restores it on check-in. Two
mutations cannot be reversed — a cursor name, and `closeOnCompletion()` — and a Statement that has
suffered either is discarded on check-in via `IrreversibleHazardException`, as is one whose client
called `setPoolable(false)`.

`stmtToHazards` is weak because the proxies mark hazards on Statements that may never enter the
cache; those entries fall out when the Statement is collected.

## 10. Keys

`StatementCacheKey.find(...)` is `synchronized` on `StatementCacheKey.class`, and dispatches to one
of three implementations by a compile-time constant. Only `ValueIdentityStatementCacheKey` is in
use.

It leaves `equals`/`hashCode` to `Object` and gets uniqueness from a weak coalescer instead: two
requests for the same logical key yield the *same instance*. So `keyToKeyRec` is effectively
identity-keyed, and the cache's own strong reference to a key keeps its coalescer entry alive for
as long as it matters.

A key covers rather more than the SQL text: the physical Connection, whether it is a call, result
set type and concurrency, and the JDBC 3 column indexes, column names, auto-generated-keys and
holdability arguments. Two keys can therefore carry the same SQL on the same Connection and still be
distinct — `prepareStatement(sql)` and `prepareStatement(sql, type, concurrency)` produce different
keys — which is worth remembering when reading a diagnostic that prints only the SQL.

## 11. Checking the invariants

`StatementCacheAuditor` (test sources, this package) checks §5 against a live cache by taking
`mainLock` and looking. It uses no reflection — everything it reads is package-private and it is
declared into this package — so it needs no cooperation from this class, and can be attached to a
running application:

```java
GooGooStatementCache scache = C3P0TestInternals.statementCacheOf( myPooledDataSource );
StatementCacheAuditor.startWatchdog( scache, 50 ); // millis
```

Remember that a DataSource has one pool, and so one cache, per authentication: the call above finds
the default one, `statementCacheOf( pds, user, password )` finds another, and `statementCachesOf( pds )`
finds them all, which is what to watch on a DataSource serving several credentials. None of them
creates anything, so attaching the auditor cannot itself start threads or open Connections.

The harnesses that exercise it need no database — they run against an in-process fake JDBC driver —
and audit after every operation, so an inconsistency is reported where it happens rather than later,
at `cullNext()`:

```
C3P0_TEST_JVM_ARGS='-ea' mill test.c3p0StmtCacheStress      # drives this class directly
C3P0_TEST_JVM_ARGS='-ea' mill test.c3p0StmtCacheFullStack   # drives a real ComboPooledDataSource
C3P0_TEST_JVM_ARGS='-ea' mill test.c3p0StmtCacheIssue196    # the issue #196 failure mode, step by step
```

Run them with assertions enabled: `Deathmarch` guards its methods with
`assert mainLock.isHeldByCurrentThread()`.
