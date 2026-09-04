package com.mchange.v2.c3p0.stmt;

import java.sql.Connection;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Checks the internal invariants of a live GooGooStatementCache from outside it.
 *
 * <p>This class deliberately lives in test sources, but declares itself into the cache's own
 * package, so it can reach the cache's package-private structures without any modification to
 * the library.
 *
 * <p><b>Why an outside observer is a sound one.</b> Every public method of GooGooStatementCache
 * holds mainLock for its whole duration, with exactly one exception: acquireStatement() awaits
 * conditionStatementPerhapsAcquired, releasing mainLock while the driver prepares a Statement.
 * At that point the awaiting thread has mutated nothing -- it has computed a key and found the
 * checkout queue empty, and will not touch the cache again until it reacquires the lock. So any
 * moment at which another thread can acquire mainLock is a moment at which the cache should be
 * entirely self-consistent. That is what this class asserts.
 *
 * <p><b>Everything here compares objects by identity.</b> Some of the failure modes we are
 * hunting involve JDBC drivers whose Statements have a broken equals(...) -- see
 * https://github.com/swaldman/c3p0/pull/59/ -- so an auditor that used equals(...) could not
 * tell a real inconsistency from the pathology that caused it.
 *
 * <p>No reflection is involved: every structure this reads is package-private, and this class is
 * declared into that package. Since it needs nothing but mainLock and that access, the auditor can
 * also be attached to a live application: put the c3p0-test jar on the classpath and call
 * {@link #startWatchdog}.
 */
public final class StatementCacheAuditor
{
    private final static AtomicReference FIRST_FAILURE = new AtomicReference( null );

    /**
     * The first inconsistency this auditor saw, if any. Recorded separately because the throw may
     * be swallowed by application code (or by c3p0's own catch blocks) before anyone sees it.
     */
    public static InconsistentStatementCacheException firstFailure()
    { return (InconsistentStatementCacheException) FIRST_FAILURE.get(); }

    public static void resetFirstFailure()
    { FIRST_FAILURE.set( null ); }

    /**
     * Acquires mainLock, checks every invariant, and throws on the first inconsistency, having
     * recorded it in {@link #firstFailure}. A closed cache is vacuously consistent.
     */
    public static void assertConsistent( GooGooStatementCache cache, String context )
    {
        cache.mainLock.lock();
        try
        {
            List violations = violations( cache );
            if (! violations.isEmpty() )
            {
                InconsistentStatementCacheException e =
                    new InconsistentStatementCacheException( context, violations, dump( cache ) );
                FIRST_FAILURE.compareAndSet( null, e );
                throw e;
            }
        }
        finally
        { cache.mainLock.unlock(); }
    }

    /**
     * Whether the cache currently holds this exact Statement object, compared by identity.
     *
     * <p>Callers use this to tell a cached Statement from an "overload" Statement -- one the cache
     * produced but declined to keep, because every Statement it held was checked out. The cache
     * destroys an overload Statement when it is checked in, but knows nothing about it otherwise,
     * so whoever holds one owns it.
     */
    public static boolean containsStatement( GooGooStatementCache cache, Object pstmt )
    {
        cache.mainLock.lock();
        try
        { return cache.stmtToKey != null && get( cache.stmtToKey, pstmt ) != null; }
        finally
        { cache.mainLock.unlock(); }
    }

    /**
     * The Statements currently stranded in removalPending. Outside of a removeStatement(...) call
     * this must be empty: GooGooStatementCache.removeStatement(...) returns immediately for any
     * Statement listed here, so a Statement left behind can never be removed from the cache again.
     */
    public static List removalPending( GooGooStatementCache cache )
    {
        cache.removalPendingLock.lock();
        try
        { return new ArrayList( cache.removalPending ); }
        finally
        { cache.removalPendingLock.unlock(); }
    }

    /** Whether this exact Statement object sits in any deathmarch, global or per-connection. */
    public static boolean inAnyDeathmarch( GooGooStatementCache cache, Object pstmt )
    {
        cache.mainLock.lock();
        try
        {
            GooGooStatementCache.Deathmarch global = globalDeathmarch( cache );
            if ( global != null && idSet( global.stmtsToLongs.keySet() ).contains( pstmt ) )
                return true;
            Map perConnection = perConnectionDeathmarches( cache );
            if ( perConnection != null )
            {
                for ( Iterator ii = perConnection.values().iterator(); ii.hasNext(); )
                {
                    GooGooStatementCache.Deathmarch dm = (GooGooStatementCache.Deathmarch) ii.next();
                    if ( idSet( dm.stmtsToLongs.keySet() ).contains( pstmt ) )
                        return true;
                }
            }
            return false;
        }
        finally
        { cache.mainLock.unlock(); }
    }

    /** Convenience: check, but report rather than throw. Returns an empty list when all is well. */
    public static List checkQuietly( GooGooStatementCache cache )
    {
        cache.mainLock.lock();
        try
        { return violations( cache ); }
        finally
        { cache.mainLock.unlock(); }
    }

    /**
     * Every invariant, as a list of human-readable failures. MUST be called with the cache's
     * mainLock held.
     */
    public static List violations( GooGooStatementCache cache )
    {
        List out = new ArrayList();

        if ( cache.cxnStmtMgr == null ) // closed
            return out;

        // ---- 1. removalPending must be empty between operations. A Statement stranded here is
        //         permanent: removeStatement(...) returns early for it forever after.
        cache.removalPendingLock.lock();
        try
        {
            if (! cache.removalPending.isEmpty() )
                out.add("removalPending is not empty between operations -- these Statements can never be " +
                        "removed from the cache again: " + describe( cache.removalPending ));
        }
        finally
        { cache.removalPendingLock.unlock(); }

        Set cached     = idSet( cache.stmtToKey.keySet() );
        Set checkedOut = idSet( cache.checkedOut );

        // ---- 2. keyToKeyRec's allStmts sets, unioned, are exactly stmtToKey's key set, and each
        //         Statement appears under its own key.
        Set allStmtsUnion   = newIdSet();
        Set checkoutQueueUnion = newIdSet();
        for ( Iterator ii = cache.keyToKeyRec.entrySet().iterator(); ii.hasNext(); )
        {
            Map.Entry entry = (Map.Entry) ii.next();
            StatementCacheKey key = (StatementCacheKey) entry.getKey();
            GooGooStatementCache.KeyRec rec = (GooGooStatementCache.KeyRec) entry.getValue();
            Set allStmts     = idSet( rec.allStmts );
            List checkoutQ   = rec.checkoutQueue;
            Set checkoutQSet = idSet( checkoutQ );

            if ( allStmts.isEmpty() && checkoutQ.isEmpty() )
                out.add("keyToKeyRec retains an entirely empty KeyRec for " + describeKey( key ));

            for ( Iterator jj = allStmts.iterator(); jj.hasNext(); )
            {
                Object stmt = jj.next();
                if (! allStmtsUnion.add( stmt ) )
                    out.add("Statement appears in the allStmts of more than one key: " + stmt);
                Object stmtKey = get( cache.stmtToKey, stmt );
                if ( stmtKey == null )
                    out.add("Statement in KeyRec.allStmts for " + describeKey( key ) + " is absent from stmtToKey: " + stmt);
                else if ( stmtKey != key )
                    out.add("Statement is filed under " + describeKey( key ) + " but stmtToKey maps it to " +
                            describeKey( (StatementCacheKey) stmtKey ) + ": " + stmt);
            }

            // ---- 3. checkoutQueue is a duplicate-free subset of allStmts, disjoint from checkedOut
            if ( checkoutQSet.size() != checkoutQ.size() )
                out.add("checkoutQueue for " + describeKey( key ) + " contains duplicates: " + describe( checkoutQ ));
            for ( Iterator jj = checkoutQSet.iterator(); jj.hasNext(); )
            {
                Object stmt = jj.next();
                if (! allStmts.contains( stmt ) )
                    out.add("Statement is in the checkoutQueue for " + describeKey( key ) + " but not in its allStmts: " + stmt);
                if ( checkedOut.contains( stmt ) )
                    out.add("Statement is simultaneously available for checkout and marked checked out: " + stmt);
                if (! checkoutQueueUnion.add( stmt ) )
                    out.add("Statement appears in more than one checkoutQueue: " + stmt);
            }
        }
        if (! sameMembers( allStmtsUnion, cached ) )
            out.add("The union of all KeyRec.allStmts does not match stmtToKey's key set. " +
                    "Only in allStmts: " + describe( minus( allStmtsUnion, cached ) ) +
                    "; only in stmtToKey: " + describe( minus( cached, allStmtsUnion ) ));

        // ---- 4. checkedOut is a subset of the cached Statements
        for ( Iterator ii = checkedOut.iterator(); ii.hasNext(); )
        {
            Object stmt = ii.next();
            if (! cached.contains( stmt ) )
                out.add("Statement is marked checked out but is not in stmtToKey: " + stmt);
        }

        // ---- 5. cxnStmtMgr agrees with stmtToKey, statement for statement and connection for
        //         connection. A Statement stranded here inflates the per-connection count forever,
        //         which is what forces a cull on every subsequent checkout.
        Set cxnMgrUnion = newIdSet();
        for ( Iterator ii = cache.cxnStmtMgr.cxnToStmtSets.entrySet().iterator(); ii.hasNext(); )
        {
            Map.Entry entry = (Map.Entry) ii.next();
            Connection pcon = (Connection) entry.getKey();
            Set stmtSet = idSet( (Set) entry.getValue() );
            if ( stmtSet.isEmpty() )
                out.add("cxnStmtMgr retains an empty Statement set for connection " + pcon);
            for ( Iterator jj = stmtSet.iterator(); jj.hasNext(); )
            {
                Object stmt = jj.next();
                if (! cxnMgrUnion.add( stmt ) )
                    out.add("Statement is filed under more than one connection in cxnStmtMgr: " + stmt);
                StatementCacheKey key = (StatementCacheKey) get( cache.stmtToKey, stmt );
                if ( key == null )
                    out.add("Statement is in cxnStmtMgr for connection " + pcon + " but absent from stmtToKey: " + stmt);
                else if ( key.physicalConnection != pcon )
                    out.add("Statement is filed under connection " + pcon + " but its key names connection " +
                            key.physicalConnection + ": " + stmt);
            }
        }
        if (! sameMembers( cxnMgrUnion, cached ) )
            out.add("cxnStmtMgr's Statements do not match stmtToKey's key set. " +
                    "Only in cxnStmtMgr: " + describe( minus( cxnMgrUnion, cached ) ) +
                    "; only in stmtToKey: " + describe( minus( cached, cxnMgrUnion ) ));

        // ---- 6-9. the deathmarches
        Set checkedIn = minus( cached, checkedOut );

        GooGooStatementCache.Deathmarch global = globalDeathmarch( cache );
        if ( global != null )
        {
            checkDeathmarch( out, cache, global, "the global deathmarch", cached, checkedOut, null );
            Set globalStmts = idSet( global.stmtsToLongs.keySet() );
            if (! sameMembers( globalStmts, checkedIn ) )
                out.add("The global deathmarch does not hold exactly the checked-in cached Statements. " +
                        "Only in deathmarch: " + describe( minus( globalStmts, checkedIn ) ) +
                        "; only checked in: " + describe( minus( checkedIn, globalStmts ) ));
        }

        Map perConnection = perConnectionDeathmarches( cache );
        if ( perConnection != null )
        {
            Set perConnectionUnion = newIdSet();
            for ( Iterator ii = perConnection.entrySet().iterator(); ii.hasNext(); )
            {
                Map.Entry entry = (Map.Entry) ii.next();
                Connection pcon = (Connection) entry.getKey();
                GooGooStatementCache.Deathmarch dm = (GooGooStatementCache.Deathmarch) entry.getValue();
                checkDeathmarch( out, cache, dm, "the deathmarch for " + pcon, cached, checkedOut, pcon );
                perConnectionUnion.addAll( idSet( dm.stmtsToLongs.keySet() ) );
            }
            if (! sameMembers( perConnectionUnion, checkedIn ) )
                out.add("The per-connection deathmarches do not together hold exactly the checked-in cached " +
                        "Statements. Only in deathmarches: " + describe( minus( perConnectionUnion, checkedIn ) ) +
                        "; only checked in: " + describe( minus( checkedIn, perConnectionUnion ) ));

            // ---- 10. a Deathmarch exists for exactly those connections that have Statements
            Set dmConnections  = idSet( perConnection.keySet() );
            Set mgrConnections = idSet( cache.cxnStmtMgr.cxnToStmtSets.keySet() );
            if (! sameMembers( dmConnections, mgrConnections ) )
                out.add("Connections with a Deathmarch do not match connections with cached Statements. " +
                        "Only with a Deathmarch: " + describe( minus( dmConnections, mgrConnections ) ) +
                        "; only with Statements: " + describe( minus( mgrConnections, dmConnections ) ));
        }

        // ---- checked-in Statements are exactly those available for checkout
        if (! sameMembers( checkoutQueueUnion, checkedIn ) )
            out.add("The checkout queues do not together hold exactly the checked-in cached Statements. " +
                    "Only in queues: " + describe( minus( checkoutQueueUnion, checkedIn ) ) +
                    "; only checked in: " + describe( minus( checkedIn, checkoutQueueUnion ) ));

        return out;
    }

    private static void checkDeathmarch( List out,
                                         GooGooStatementCache cache,
                                         GooGooStatementCache.Deathmarch dm,
                                         String what,
                                         Set cached,
                                         Set checkedOut,
                                         Connection expectedConnection )
    {
        // ---- 6. stmtsToLongs and longsToStmts are mutual inverses
        if ( dm.stmtsToLongs.size() != dm.longsToStmts.size() )
            out.add( what + " has diverged: stmtsToLongs holds " + dm.stmtsToLongs.size() +
                     " entries, longsToStmts holds " + dm.longsToStmts.size() );
        for ( Iterator ii = dm.stmtsToLongs.entrySet().iterator(); ii.hasNext(); )
        {
            Map.Entry entry = (Map.Entry) ii.next();
            Object stmt = entry.getKey();
            Object back = dm.longsToStmts.get( entry.getValue() );
            if ( back != stmt )
                out.add( what + " maps " + stmt + " to " + entry.getValue() + ", but that maps back to " + back );

            // ---- 7. THE invariant. Both failures reported in issue #196 -- the NPE on
            //         sck.stmtText and "Statement culled from deathmarch failed to be removed by
            //         removeStatement( ... )" -- are this invariant violated, seen late.
            if (! cached.contains( stmt ) )
                out.add("A Statement is in " + what + " but is absent from stmtToKey. This is the state " +
                        "that makes cullNext() throw (issue #196): " + stmt);
            if ( checkedOut.contains( stmt ) )
                out.add("A Statement is in " + what + " while checked out: " + stmt);

            // ---- 9. per-connection deathmarches hold only their own connection's Statements
            if ( expectedConnection != null )
            {
                StatementCacheKey key = (StatementCacheKey) get( cache.stmtToKey, stmt );
                if ( key != null && key.physicalConnection != expectedConnection )
                    out.add("A Statement belonging to " + key.physicalConnection + " is in " + what + ": " + stmt);
            }
        }
    }

    /**
     * A full, statement-by-statement rendering of the cache. Acquires mainLock, which is reentrant,
     * so this is also safe to call from code that already holds it.
     */
    public static String dump( GooGooStatementCache cache )
    {
        cache.mainLock.lock();
        try
        { return _dump( cache ); }
        finally
        { cache.mainLock.unlock(); }
    }

    private static String _dump( GooGooStatementCache cache )
    {
        StringBuffer sb = new StringBuffer(4096);
        sb.append( cache.getClass().getName() ).append('\n');

        if ( cache.cxnStmtMgr == null )
        {
            sb.append("  <closed>\n");
            return sb.toString();
        }

        cache.removalPendingLock.lock();
        try
        { sb.append("  removalPending (").append( cache.removalPending.size() ).append("): ")
            .append( describe( cache.removalPending ) ).append('\n'); }
        finally
        { cache.removalPendingLock.unlock(); }

        sb.append("  stmtToKey (").append( cache.stmtToKey.size() ).append("):\n");
        for ( Iterator ii = cache.stmtToKey.entrySet().iterator(); ii.hasNext(); )
        {
            Map.Entry entry = (Map.Entry) ii.next();
            StatementCacheKey key = (StatementCacheKey) entry.getValue();
            sb.append("    ").append( entry.getKey() )
              .append( cache.checkedOut.contains( entry.getKey() ) ? "  [CHECKED OUT]" : "  [checked in]" )
              .append(" -> ").append( key == null ? "<null key>" : key.stmtText )
              .append(" @ ").append( key == null ? "?" : String.valueOf( key.physicalConnection ) )
              .append('\n');
        }

        sb.append("  checkedOut (").append( cache.checkedOut.size() ).append("): ")
          .append( describe( cache.checkedOut ) ).append('\n');

        sb.append("  keyToKeyRec (").append( cache.keyToKeyRec.size() ).append("):\n");
        for ( Iterator ii = cache.keyToKeyRec.entrySet().iterator(); ii.hasNext(); )
        {
            Map.Entry entry = (Map.Entry) ii.next();
            StatementCacheKey key = (StatementCacheKey) entry.getKey();
            sb.append("    '").append( key.stmtText ).append("' @ ").append( key.physicalConnection ).append('\n');
            GooGooStatementCache.KeyRec rec = (GooGooStatementCache.KeyRec) entry.getValue();
            sb.append("        allStmts:      ").append( describe( rec.allStmts ) ).append('\n');
            sb.append("        checkoutQueue: ").append( describe( rec.checkoutQueue ) ).append('\n');
        }

        sb.append("  cxnStmtMgr (").append( cache.cxnStmtMgr.cxnToStmtSets.size() ).append(" connections):\n");
        for ( Iterator ii = cache.cxnStmtMgr.cxnToStmtSets.entrySet().iterator(); ii.hasNext(); )
        {
            Map.Entry entry = (Map.Entry) ii.next();
            sb.append("    ").append( entry.getKey() ).append(": ")
              .append( describe( (Collection) entry.getValue() ) ).append('\n');
        }

        GooGooStatementCache.Deathmarch global = globalDeathmarch( cache );
        if ( global != null )
            sb.append("  global deathmarch (LRU first): ").append( describe( global.longsToStmts.values() ) ).append('\n');

        Map perConnection = perConnectionDeathmarches( cache );
        if ( perConnection != null )
        {
            sb.append("  per-connection deathmarches (LRU first):\n");
            for ( Iterator ii = perConnection.entrySet().iterator(); ii.hasNext(); )
            {
                Map.Entry entry = (Map.Entry) ii.next();
                GooGooStatementCache.Deathmarch dm = (GooGooStatementCache.Deathmarch) entry.getValue();
                sb.append("    ").append( entry.getKey() ).append(": ")
                  .append( describe( dm.longsToStmts.values() ) ).append('\n');
            }
        }

        return sb.toString();
    }

    /**
     * Audits the cache every intervalMillis until the returned Thread is interrupted, recording
     * the first inconsistency in {@link #firstFailure}. Suitable for attaching to a live
     * application, where the surrounding code cannot be made to call assertConsistent(...).
     */
    public static Thread startWatchdog( final GooGooStatementCache cache, final long intervalMillis )
    {
        Thread t = new Thread("StatementCacheAuditor-watchdog")
        {
            public void run()
            {
                while (! Thread.currentThread().isInterrupted() )
                {
                    try
                    {
                        Thread.sleep( intervalMillis );
                        if ( cache.isClosed() )
                            return;
                        assertConsistent( cache, "watchdog" );
                    }
                    catch ( InterruptedException e )
                    { return; }
                    catch ( InconsistentStatementCacheException e )
                    { return; } // recorded in firstFailure; no point spinning on a broken cache
                    catch ( Exception e )
                    { /* eg a ResourceClosedException as the cache shuts down under us */ }
                }
            }
        };
        t.setDaemon( true );
        t.start();
        return t;
    }

    // ---- deathmarch discovery ------------------------------------------------------------

    public static GooGooStatementCache.Deathmarch globalDeathmarch( GooGooStatementCache cache )
    {
        if ( cache instanceof DoubleMaxStatementCache )
            return ((DoubleMaxStatementCache) cache).globalDeathmarch;
        else if ( cache instanceof GlobalMaxOnlyStatementCache )
            return ((GlobalMaxOnlyStatementCache) cache).globalDeathmarch;
        else
            return null;
    }

    /** Maps each Connection to its Deathmarch, or null for caches that keep no per-connection deathmarches. */
    public static Map perConnectionDeathmarches( GooGooStatementCache cache )
    {
        GooGooStatementCache.DeathmarchConnectionStatementManager dcsm = null;
        if ( cache instanceof DoubleMaxStatementCache )
            dcsm = ((DoubleMaxStatementCache) cache).dcsm;
        else if ( cache instanceof PerConnectionMaxOnlyStatementCache )
            dcsm = ((PerConnectionMaxOnlyStatementCache) cache).dcsm;
        return ( dcsm == null ? null : dcsm.cxnsToDms );
    }

    // ---- identity-based helpers ----------------------------------------------------------

    private static Set newIdSet()
    { return Collections.newSetFromMap( new IdentityHashMap() ); }

    private static Set idSet( Collection c )
    {
        Set out = newIdSet();
        if ( c != null )
            out.addAll( c );
        return out;
    }

    private static Set minus( Set a, Set b )
    {
        Set out = newIdSet();
        for ( Iterator ii = a.iterator(); ii.hasNext(); )
        {
            Object o = ii.next();
            if (! b.contains( o ) )
                out.add( o );
        }
        return out;
    }

    private static boolean sameMembers( Set a, Set b )
    { return a.size() == b.size() && minus( a, b ).isEmpty(); }

    /**
     * HashMap.get(...) by identity. A Statement whose equals(...) is broken cannot be found by
     * the ordinary lookup, and we must not let that confuse the audit.
     */
    private static Object get( Map map, Object identityKey )
    {
        for ( Iterator ii = map.entrySet().iterator(); ii.hasNext(); )
        {
            Map.Entry entry = (Map.Entry) ii.next();
            if ( entry.getKey() == identityKey )
                return entry.getValue();
        }
        return null;
    }

    /**
     * Renders a key without calling its toString(). StatementCacheKey.toString() passes its
     * (usually null) columnIndexes to com.mchange.v1.util.ArrayUtils.toString(int[]), which
     * dereferences the array without a null check -- so printing an ordinary key throws
     * NullPointerException.
     */
    private static String describeKey( StatementCacheKey key )
    {
        if ( key == null )
            return "<null key>";

        // Everything that distinguishes one key from another, plus the instance's identity hash:
        // keys are compared by identity (ValueIdentityStatementCacheKey leaves equals/hashCode to
        // Object and relies on a coalescer for uniqueness), so two keys that read alike are still
        // two keys, and a violation message that cannot tell them apart is no use.
        StringBuffer sb = new StringBuffer(160);
        sb.append("key@").append( Integer.toHexString( System.identityHashCode( key ) ) );
        sb.append("['").append( key.stmtText ).append("' @ ").append( key.physicalConnection );
        if ( key.is_callable )
            sb.append(", callable");
        sb.append(", rs=").append( key.result_set_type ).append('/').append( key.result_set_concurrency );
        if ( key.columnIndexes != null )
            sb.append(", columnIndexes=").append( Arrays.toString( key.columnIndexes ) );
        if ( key.columnNames != null )
            sb.append(", columnNames=").append( Arrays.toString( key.columnNames ) );
        if ( key.autogeneratedKeys != null )
            sb.append(", autogeneratedKeys=").append( key.autogeneratedKeys );
        if ( key.resultSetHoldability != null )
            sb.append(", resultSetHoldability=").append( key.resultSetHoldability );
        sb.append(']');
        return sb.toString();
    }

    private static String describe( Collection c )
    {
        if ( c == null )
            return "<null>";
        StringBuffer sb = new StringBuffer(256);
        sb.append('[');
        boolean first = true;
        for ( Iterator ii = c.iterator(); ii.hasNext(); )
        {
            if (! first )
                sb.append(", ");
            sb.append( ii.next() );
            first = false;
        }
        sb.append(']');
        return sb.toString();
    }

    private StatementCacheAuditor()
    {}
}
