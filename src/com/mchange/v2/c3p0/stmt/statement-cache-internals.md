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
- **Overload.** Produced by `checkoutStatement(...)` when `prepareAssimilateNewStatement(...)`
  refused — every Statement the cache held for that Connection was checked out, so nothing could be
  culled. It is returned to the client but *not cached*: it appears in none of the collections. The
  cache destroys it if it is ever checked in, and knows nothing about it otherwise.

  Because the cache does not track it, nothing here can clean it up: `checkinAll(...)` and
  `closeAll(...)` sweep only Statements the cache accepted. So the caller is told. The four-argument
  `checkoutStatement(...)` sets its `actuallyCachedHolder` to say whether the Statement was taken
  into the cache, and `NewPooledConnection` registers the ones that were not through
  `markActiveUncachedStatement(...)`, so that `cleanupUncachedStatements(...)` closes them when the
  logical Connection closes — the same machinery that handles Statements when caching is off
  entirely. Before that (6df8c127), an overload Statement whose client never closed it stayed open
  for the life of the physical Connection.

## 4. The collections

### 4.1 Guarded by `mainLock`

| Field | Type | Holds |
| --- | --- | --- |
| `stmtToKey` | `HashMap` | **every cached Statement** → its `StatementCacheKey`. This map defines cache membership; the others must agree with it |
| `keyToKeyRec` | `HashMap` | key → `KeyRec` |
| `KeyRec.allStmts` | `HashSet` | every cached Statement produced under that key. Usually one, more when a client prepares the same statement concurrently (logged as "Multiply-cached PreparedStatement") |
| `KeyRec.checkoutQueue` | `LinkedList` | those of `allStmts` that are checked in, ie available for checkout |
| `checkedOut` | `HashSet` | every cached Statement currently held by a client |
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

These hold whenever no cache operation is in progress — which, because `mainLock` is held for the
whole of every operation (§7), means: whenever another thread can acquire `mainLock`.

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

All five entry points take `mainLock` for their duration.

| Method | What it does to the structures |
| --- | --- |
| `checkoutStatement(pcon, method, args [, actuallyCachedHolder])` | Cache hit: takes the head of the key's `checkoutQueue`, adds it to `checkedOut`, removes it from the deathmarches. Miss: acquires a new Statement (§7), then, if `prepareAssimilateNewStatement(pcon)` allows, adds it to `stmtToKey`, the key's `allStmts`, `cxnStmtMgr` and `checkedOut`; otherwise returns it uncached as an overload Statement. The optional holder reports which happened, so the caller can take responsibility for what the cache declined to keep (§3) |
| `checkinStatement(pstmt)` | Removes it from `checkedOut`, restores its state (§9), then appends it to its key's `checkoutQueue` and adds it to the deathmarches. A Statement that cannot be restored is put back into `checkedOut` and removed with `DESTROY_ALWAYS`. A Statement the cache does not hold is simply destroyed |
| `checkinAll(pcon)` | Checks in every checked-out Statement on that Connection. Iterates a clone, since check-in can remove |
| `closeAll(pcon)` | Removes every Statement on that Connection with `DESTROY_NEVER`, under the lock, then **releases the lock** and destroys them synchronously |
| `close()` | Destroys every cached Statement synchronously, closes the destruction manager, nulls the collections, and signals `conditionStatementPerhapsAcquired` so that nobody is left waiting for a Statement that will now never arrive (§7) |

`removeStatement(ps, policy)` is the single path out of the cache, used by `checkinStatement`,
`closeAll` and `cullNext`. It removes the Statement from every collection above, and destroys it
according to the policy: `DESTROY_NEVER`, `DESTROY_IF_CHECKED_IN`, `DESTROY_IF_CHECKED_OUT`,
`DESTROY_ALWAYS`. It must leave nothing behind however it exits — including by exception, which a
misbehaving driver can cause — so its body sits in a `try`/`finally` that always clears
`removalPending`, and it logs and returns rather than dereferencing a key it no longer has.

`Deathmarch.cullNext()` picks the least recently checked-in Statement that is not checked out —
defensively, since by invariant 7 no deathmarched Statement should be checked out — and removes it
with `DESTROY_ALWAYS`. A deathmarched Statement with no key is an inconsistency: it is logged,
dropped from the deathmarch, and the next candidate tried.

## 7. Locking, and the one window

**`mainLock` is held for the whole of every public method, with a single exception:**
`acquireStatement(...)` hands statement production to `blockingTaskAsyncRunner` and awaits
`conditionStatementPerhapsAcquired`, releasing `mainLock` while the driver prepares the Statement.
That keeps a slow `prepareStatement(...)` from stalling every other Connection's caching.

Two things follow. First, the awaiting thread has mutated nothing — it computed a key and found the
checkout queue empty — so the cache is consistent throughout the window, and any moment another
thread can take `mainLock` is a moment the invariants hold. Second, and less comfortably, *cache
state can change across that window*: by the time `checkoutStatement(...)` resumes, the key's
queue, the maxima and the Connection's Statement set may all differ from what it saw. Do not
presume atomicity before and after.

The wait ends on one of three things, and every one of them must be made to happen, because none of
them is guaranteed by the driver:

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
serialized. `checkoutStatement(...)` awaits while holding that monitor.

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
and `unmarkConnectionInUse(...)`. The marker does not nest.

## 9. Statement state, and hazards

A cached Statement must be indistinguishable from a fresh one. On check-in, `refreshStatement(...)`
clears parameters, batches and warnings, and reverses whatever else the client changed.

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
