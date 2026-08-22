package com.mchange.v2.c3p0.test.stmt;

import java.lang.reflect.Method;
import java.sql.*;
import java.util.*;

import com.mchange.v2.async.ThreadPoolAsynchronousRunner;
import com.mchange.v2.c3p0.stmt.*;
import com.mchange.v2.c3p0.test.fakedriver.*;

/**
 * A deterministic, single-threaded demonstration of the failure mode behind
 * https://github.com/swaldman/c3p0/issues/196.
 *
 * <p>GooGooStatementCache.removeStatement(...) marks a Statement in removalPending, removes it
 * from the cache's structures, and clears the mark at the end. If anything in between throws, the
 * mark is never cleared, and from then on every removeStatement(...) for that Statement returns
 * immediately at the guard and removes nothing. The cache can never be rid of it.
 *
 * <p>To provoke a throw inside that window without touching the library, we use a driver pathology
 * c3p0 already knows about and checks for: a PreparedStatement whose equals(...) does not recognize
 * itself -- see https://github.com/swaldman/c3p0/pull/59/. Note that HashMap and HashSet compare by
 * identity before calling equals(...), so this breaks exactly one lookup inside removeStatement:
 * the LinkedList.remove(Object) in removeFromCheckoutQueue(...), which is where c3p0 raises its
 * "Apparent JDBC Driver Bug!" RuntimeException.
 *
 * <p>Run standalone with:
 * <pre>mill test.runMain com.mchange.v2.c3p0.test.stmt.RemovalPendingLeakDemo</pre>
 */
public final class RemovalPendingLeakDemo
{
    public final static String SQL_A = "SELECT a FROM t WHERE k = ?";
    public final static String SQL_B = "SELECT b FROM t WHERE k = ?";

    /** What the demonstration observed, so that a JUnit case can assert on it. */
    public final static class Findings
    {
        public boolean  removalPendingStranded;
        public List     removalPendingContents = new ArrayList();
        public List     auditViolations        = new ArrayList();
        public Throwable exceptionDuringCull;
        public Throwable exceptionAfterStranding;
        public boolean  sawInconsistencyCulledFromDeathmarch; // issue #196, error 2
        public boolean  sawNullKeyInCullNext;                 // issue #196, error 1
        public String   narrative = "";
        public String   finalDump = "";
    }

    public static Findings run( boolean verbose ) throws Exception
    {
        Findings findings = new Findings();
        StringBuffer narrative = new StringBuffer(4096);

        FakeDriverConfig cfg = FakeDriverConfig.register("removalPendingLeakDemo-" + System.nanoTime(), 42L);
        Timer timer = new Timer("RemovalPendingLeakDemo-timer", true);
        ThreadPoolAsynchronousRunner runner =
            new ThreadPoolAsynchronousRunner( 1, true, timer, "RemovalPendingLeakDemo-helper" );

        // one Statement per Connection, so every new Statement forces a cull
        GooGooStatementCache cache = new PerConnectionMaxOnlyStatementCache( runner, null, 1, false );
        Connection conn = FakeConnection.create( cfg );
        Method prepare = SimulatedPooledConnection.PREPARE_STATEMENT_SIMPLE;

        try
        {
            // ---- 1. cache one Statement and check it back in, so it is deathmarched and available
            Object a = cache.checkoutStatement( conn, prepare, new Object[] { SQL_A } );
            cache.checkinStatement( a );
            say( narrative, verbose, "1. cached and checked in " + a );
            say( narrative, verbose, "   in a deathmarch?  " + StatementCacheAuditor.inAnyDeathmarch( cache, a ) );
            say( narrative, verbose, "   audit:            " + StatementCacheAuditor.checkQuietly( cache ) );

            // ---- 2. the driver pathology arrives
            FakeStatement.of( a ).setBrokenEquals( true );
            say( narrative, verbose, "2. broke equals(...) on " + a + " -- it no longer recognizes itself" );

            // ---- 3. prepare a different Statement, which must cull the first one
            try
            {
                Object b = cache.checkoutStatement( conn, prepare, new Object[] { SQL_B } );
                say( narrative, verbose, "3. checked out " + b + " without incident" );
            }
            catch ( Throwable t )
            {
                findings.exceptionDuringCull = t;
                say( narrative, verbose, "3. culling threw: " + t );
            }

            // ---- 4. what the cache looks like now
            List stranded = StatementCacheAuditor.removalPending( cache );
            findings.removalPendingContents = stranded;
            findings.removalPendingStranded = !stranded.isEmpty();
            findings.auditViolations = StatementCacheAuditor.checkQuietly( cache );

            say( narrative, verbose, "4. removalPending now holds: " + stranded );
            say( narrative, verbose, "   audit violations:" );
            for ( Iterator ii = findings.auditViolations.iterator(); ii.hasNext(); )
                say( narrative, verbose, "     * " + ii.next() );

            // ---- 5. repair the driver, then keep using the cache. The Statement is stranded for
            //         good: every removeStatement(...) for it is now a no-op.
            FakeStatement.of( a ).setBrokenEquals( false );
            say( narrative, verbose, "5. repaired equals(...). The strand, however, is permanent." );

            for (int i = 0; i < 8; ++i)
            {
                try
                {
                    Object s = cache.checkoutStatement( conn, prepare, new Object[] { i % 2 == 0 ? SQL_A : SQL_B } );
                    cache.checkinStatement( s );
                }
                catch ( Throwable t )
                {
                    if ( findings.exceptionAfterStranding == null )
                        findings.exceptionAfterStranding = t;
                    classify( findings, t );
                    say( narrative, verbose, "   iteration " + i + " threw: " + t );
                }
            }

            try
            { cache.closeAll( conn ); }
            catch ( Throwable t )
            {
                classify( findings, t );
                say( narrative, verbose, "   closeAll threw: " + t );
            }

            findings.finalDump = StatementCacheAuditor.dump( cache );

            if ( verbose )
            {
                System.out.println();
                System.out.println("FINAL CACHE STATE:");
                System.out.println( findings.finalDump );
            }
        }
        finally
        {
            try { cache.close(); } catch ( Throwable t ) { /* shutting down */ }
            runner.close( true );
            timer.cancel();
            FakeDriverConfig.unregister( cfg.name );
        }

        findings.narrative = narrative.toString();
        return findings;
    }

    /** Recognizes the two failures reported in issue #196. */
    private static void classify( Findings findings, Throwable t )
    {
        String msg = String.valueOf( t.getMessage() );
        if ( msg.indexOf("Statement culled from deathmarch failed to be removed") >= 0 )
            findings.sawInconsistencyCulledFromDeathmarch = true;
        if ( t instanceof NullPointerException && isCullNextNpe( t ) )
            findings.sawNullKeyInCullNext = true;
    }

    /** The issue's first error: cullNext() logging sck.stmtText for a Statement with no key. */
    private static boolean isCullNextNpe( Throwable t )
    {
        StackTraceElement[] frames = t.getStackTrace();
        for (int i = 0; i < frames.length; ++i)
            if ( "cullNext".equals( frames[i].getMethodName() ) )
                return true;
        return false;
    }

    private static void say( StringBuffer narrative, boolean verbose, String line )
    {
        narrative.append( line ).append('\n');
        if ( verbose )
            System.out.println( line );
    }

    public static void main( String[] argv ) throws Exception
    {
        Findings f = run( true );

        System.out.println();
        System.out.println("==== findings ====");
        System.out.println("  Statement stranded in removalPending: " + f.removalPendingStranded);
        System.out.println("  audit violations:                     " + f.auditViolations.size());
        System.out.println("  reproduced issue #196 error 2         " +
                           "(\"culled from deathmarch failed to be removed\"): " + f.sawInconsistencyCulledFromDeathmarch);
        System.out.println("  reproduced issue #196 error 1         " +
                           "(NullPointerException inside cullNext): " + f.sawNullKeyInCullNext);
        System.exit( f.removalPendingStranded ? 0 : 1 );
    }

    private RemovalPendingLeakDemo()
    {}
}
