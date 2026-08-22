package com.mchange.v2.c3p0.stmt;

import java.lang.reflect.Method;
import java.sql.*;
import java.util.*;
import java.util.logging.Level;

import com.mchange.v2.async.ThreadPoolAsynchronousRunner;
import com.mchange.v2.c3p0.test.fakedriver.*;

/**
 * Shows what Deathmarch.cullNext() does when it meets the two states behind
 * https://github.com/swaldman/c3p0/issues/196, and confirms that
 * {@link StatementCacheAuditor} recognizes both of them <i>before</i> cullNext() walks into them.
 *
 * <p>Unlike RemovalPendingLeakDemo, which reaches its state by driving the public API, this class
 * injects the two states directly into the cache's structures. That is deliberate. The states are
 * reachable -- RemovalPendingLeakDemo shows one route to a stranded Statement, and a Statement that
 * has been re-admitted to the cache while stranded is exactly case B below -- but reaching them by
 * accident takes a race we cannot schedule. What this class establishes is the other half: given
 * the state, these are exactly the two errors the issue reports.
 *
 * <pre>mill test.runMain com.mchange.v2.c3p0.stmt.CullNextInconsistencyDemo</pre>
 */
public final class CullNextInconsistencyDemo
{
    public final static String SQL_A = "SELECT a FROM t WHERE k = ?";
    public final static String SQL_B = "SELECT b FROM t WHERE k = ?";

    public final static class Findings
    {
        /** issue #196, error 1: NullPointerException reading sck.stmtText inside cullNext(). */
        public boolean reproducedNullKeyNpe;
        /** issue #196, error 2: "Inconsistency!!! Statement culled from deathmarch failed to be removed ...". */
        public boolean reproducedCullFailedToRemove;

        public Throwable noKeyOutcome;
        public Throwable strandedOutcome;

        public List noKeyAuditViolations    = new ArrayList();
        public List strandedAuditViolations = new ArrayList();

        public boolean auditCaughtNoKeyState    = false;
        public boolean auditCaughtStrandedState = false;
    }

    public static Findings run( boolean verbose ) throws Exception
    {
        Findings findings = new Findings();

        // cullNext() only reads sck.stmtText when FINEST is loggable, which is where error 1 lives
        Level saved = java.util.logging.Logger.getLogger("com.mchange.v2.c3p0.stmt.GooGooStatementCache").getLevel();
        java.util.logging.Logger.getLogger("com.mchange.v2.c3p0.stmt.GooGooStatementCache").setLevel( Level.FINEST );
        try
        {
            // ---- case A: a deathmarched Statement that stmtToKey no longer knows -----------
            // What a removeStatement(...) aborting between its stmtToKey.remove(...) and its
            // removeStatementFromDeathmarches(...) leaves behind.
            Harness a = new Harness();
            try
            {
                Object stmt = a.cacheOneCheckedInStatement();
                a.cache.mainLock.lock();
                try
                { a.cache.stmtToKey.remove( stmt ); }
                finally
                { a.cache.mainLock.unlock(); }

                findings.noKeyAuditViolations = StatementCacheAuditor.checkQuietly( a.cache );
                findings.auditCaughtNoKeyState = mentionsIssue196( findings.noKeyAuditViolations );
                say( verbose, "A. deathmarched Statement removed from stmtToKey" );
                report( verbose, findings.noKeyAuditViolations );

                findings.noKeyOutcome = a.forceCull();
                say( verbose, "   forcing a cull produced: " + findings.noKeyOutcome );
                findings.reproducedNullKeyNpe =
                    findings.noKeyOutcome instanceof NullPointerException && inCullNext( findings.noKeyOutcome );
            }
            finally
            { a.shutdown(); }

            // ---- case B: a deathmarched Statement stranded in removalPending ---------------
            // What RemovalPendingLeakDemo's stranded Statement becomes once the cache re-admits it:
            // present and correct everywhere, except that removeStatement(...) will never act on it.
            Harness b = new Harness();
            try
            {
                Object stmt = b.cacheOneCheckedInStatement();
                b.cache.removalPendingLock.lock();
                try
                { b.cache.removalPending.add( stmt ); }
                finally
                { b.cache.removalPendingLock.unlock(); }

                findings.strandedAuditViolations = StatementCacheAuditor.checkQuietly( b.cache );
                findings.auditCaughtStrandedState = !findings.strandedAuditViolations.isEmpty();
                say( verbose, "B. deathmarched Statement stranded in removalPending" );
                report( verbose, findings.strandedAuditViolations );

                findings.strandedOutcome = b.forceCull();
                say( verbose, "   forcing a cull produced: " + findings.strandedOutcome );
                findings.reproducedCullFailedToRemove =
                    findings.strandedOutcome != null
                    && String.valueOf( findings.strandedOutcome.getMessage() )
                             .indexOf("Statement culled from deathmarch failed to be removed") >= 0;
            }
            finally
            { b.shutdown(); }
        }
        finally
        { java.util.logging.Logger.getLogger("com.mchange.v2.c3p0.stmt.GooGooStatementCache").setLevel( saved ); }

        return findings;
    }

    /** Does the audit name the invariant that both issue #196 errors are symptoms of? */
    private static boolean mentionsIssue196( List violations )
    {
        for ( Iterator ii = violations.iterator(); ii.hasNext(); )
            if ( String.valueOf( ii.next() ).indexOf("absent from stmtToKey") >= 0 )
                return true;
        return false;
    }

    private static boolean inCullNext( Throwable t )
    {
        StackTraceElement[] frames = t.getStackTrace();
        for (int i = 0; i < frames.length; ++i)
            if ( "cullNext".equals( frames[i].getMethodName() ) )
                return true;
        return false;
    }

    private static void report( boolean verbose, List violations )
    {
        if (! verbose )
            return;
        for ( Iterator ii = violations.iterator(); ii.hasNext(); )
            System.out.println("     * " + ii.next());
    }

    private static void say( boolean verbose, String s )
    {
        if ( verbose )
            System.out.println( s );
    }

    /** A one-Connection, one-Statement-per-Connection cache, so any new Statement forces a cull. */
    private final static class Harness
    {
        final FakeDriverConfig             cfg;
        final Timer                        timer;
        final ThreadPoolAsynchronousRunner runner;
        final GooGooStatementCache         cache;
        final Connection                   conn;
        final Method                       prepare;

        Harness() throws Exception
        {
            this.cfg     = FakeDriverConfig.register("cullNextDemo-" + System.nanoTime(), 7L);
            this.timer   = new Timer("CullNextInconsistencyDemo-timer", true);
            this.runner  = new ThreadPoolAsynchronousRunner( 1, true, timer, "CullNextInconsistencyDemo-helper" );
            this.cache   = new PerConnectionMaxOnlyStatementCache( runner, null, 1, false );
            this.conn    = FakeConnection.create( cfg );
            this.prepare = Connection.class.getMethod("prepareStatement", new Class[] { String.class });
        }

        Object cacheOneCheckedInStatement() throws SQLException
        {
            Object stmt = cache.checkoutStatement( conn, prepare, new Object[] { SQL_A } );
            cache.checkinStatement( stmt );
            return stmt;
        }

        /** Asking for a second Statement on a one-Statement Connection forces cullNext(). */
        Throwable forceCull()
        {
            try
            {
                cache.checkoutStatement( conn, prepare, new Object[] { SQL_B } );
                return null;
            }
            catch ( Throwable t )
            { return t; }
        }

        void shutdown()
        {
            try { cache.close(); } catch ( Throwable t ) { /* shutting down */ }
            runner.close( true );
            timer.cancel();
            FakeDriverConfig.unregister( cfg.name );
        }
    }

    public static void main( String[] argv ) throws Exception
    {
        Findings f = run( true );
        System.out.println();
        System.out.println("==== findings ====");
        System.out.println("  A. audit named the issue #196 invariant before the cull:  " + f.auditCaughtNoKeyState);
        System.out.println("  A. reproduced error 1 (NPE on sck.stmtText in cullNext):  " + f.reproducedNullKeyNpe);
        System.out.println("  B. audit flagged the stranded Statement before the cull:  " + f.auditCaughtStrandedState);
        System.out.println("  B. reproduced error 2 (culled from deathmarch, not removed): " + f.reproducedCullFailedToRemove);
        System.exit( f.reproducedNullKeyNpe && f.reproducedCullFailedToRemove ? 0 : 1 );
    }

    private CullNextInconsistencyDemo()
    {}
}
