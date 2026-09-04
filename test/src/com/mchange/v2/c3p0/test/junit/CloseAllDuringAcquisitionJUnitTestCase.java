package com.mchange.v2.c3p0.test.junit;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Timer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import junit.framework.*;

import com.mchange.v2.async.ThreadPoolAsynchronousRunner;
import com.mchange.v2.c3p0.stmt.GooGooStatementCache;
import com.mchange.v2.c3p0.stmt.PerConnectionMaxOnlyStatementCache;
import com.mchange.v2.c3p0.stmt.StatementCacheAuditor;
import com.mchange.v2.c3p0.test.fakedriver.FakeConnection;
import com.mchange.v2.c3p0.test.fakedriver.FakeDriverConfig;

/**
 * checkoutStatement(...) holds mainLock throughout, except while acquireStatement(...) awaits a
 * Statement from the driver. Decisions taken before that await -- that this was a cache miss, and
 * that there is room for another Statement on this Connection -- are acted on after it, without
 * being rechecked.
 *
 * <p>If closeAll(pcon) runs during that window, the acquiring thread wakes and assimilates its
 * Statement into a Connection's cache that was just emptied, recreating the Connection's statement
 * set and Deathmarch. Nothing sweeps those Statements afterward: the closeAll that would have has
 * already run.
 *
 * <p>Note what this is NOT. closeAll(pcon) does not mean the Connection is being destroyed -- it is
 * documented only as flushing that Connection's cached Statements, and a caller might reasonably
 * use it to free capacity held by an idle Connection. So the remedy cannot be to stop caching for a
 * Connection that has seen a closeAll. Only acquisitions that straddled the call are affected;
 * later ones must cache normally. {@link #testCachingResumesNormallyAfterCloseAll} exists to keep a
 * fix from over-reaching in exactly that way, and should stay green throughout.
 *
 * <p>In production, NewPooledConnection is synchronized on both paths, so this interleaving cannot
 * arise. That is the point: the cache's correctness here rests on a discipline kept by another
 * class, which nothing checks and a later refactor could quietly drop.
 *
 * <p><b>testAcquisitionStraddlingCloseAllIsNotAssimilated is expected to FAIL until that is
 * addressed.</b> It is here so the fix can be watched turning it green.
 */
public final class CloseAllDuringAcquisitionJUnitTestCase extends TestCase
{
    private final static String SEED_SQL    = "SELECT seed FROM t";
    private final static String RACING_SQL  = "SELECT racing FROM t";
    private final static long   TIMEOUT_MS  = 30000;

    private final static Method PREPARE_STATEMENT;

    static
    {
        try
        { PREPARE_STATEMENT = Connection.class.getMethod("prepareStatement", new Class[] { String.class }); }
        catch ( NoSuchMethodException e )
        { throw new ExceptionInInitializerError( e ); }
    }

    private FakeDriverConfig             cfg;
    private Timer                        timer;
    private ThreadPoolAsynchronousRunner runner;
    private GooGooStatementCache         cache;
    private Connection                   conn;

    public void setUp() throws Exception
    {
        this.cfg    = FakeDriverConfig.register("closeAllRace-" + System.nanoTime(), 3L);
        this.timer  = new Timer("closeAllRace-timer", true);
        this.runner = new ThreadPoolAsynchronousRunner( 3, true, timer, "closeAllRace" );
        this.cache  = new PerConnectionMaxOnlyStatementCache( runner, null, 5, false );
        this.conn   = FakeConnection.create( cfg );
    }

    public void tearDown() throws Exception
    {
        if ( cfg != null )
        {
            CountDownLatch gate = cfg.prepareGate;   // never leave a parked thread behind
            if ( gate != null )
                gate.countDown();
        }
        try { if ( cache != null ) cache.close(); } catch ( Exception e ) { /* shutting down */ }
        if ( runner != null ) runner.close( true );
        if ( timer != null )  timer.cancel();
        if ( cfg != null )    FakeDriverConfig.unregister( cfg.name );
    }

    /** <b>Expected red</b> until acquisitions are revalidated after the await. */
    public void testAcquisitionStraddlingCloseAllIsNotAssimilated() throws Exception
    {
        seedOneCachedStatement();

        // park a second acquisition inside acquireStatement()'s await, where mainLock is released
        CountDownLatch reached = new CountDownLatch( 1 );
        CountDownLatch gate    = new CountDownLatch( 1 );
        cfg.prepareReached = reached;
        cfg.prepareGate    = gate;

        Acquirer acquirer = new Acquirer();
        acquirer.start();
        assertTrue("The acquiring thread never reached the driver; the gate is not working.",
                   reached.await( TIMEOUT_MS, TimeUnit.MILLISECONDS ));

        // ... and flush the Connection's cache while it waits there
        cache.closeAll( conn );
        assertEquals("closeAll(...) should have emptied this Connection's cached Statements",
                     0, StatementCacheAuditor.numStatementsForConnection( cache, conn ));

        gate.countDown();
        acquirer.join( TIMEOUT_MS );
        assertFalse("The acquiring thread never finished.", acquirer.isAlive());
        assertNull("The acquisition should have completed, not failed: " + acquirer.failure, acquirer.failure);

        // the Statement is the caller's now, cached or not; don't leak it
        if ( acquirer.statement != null )
            ((PreparedStatement) acquirer.statement).close();

        assertEquals("A Statement acquired across a closeAll(...) was assimilated into the Connection's " +
                     "cache anyway, recreating the statement set and Deathmarch that closeAll(...) had just " +
                     "removed. Nothing will ever sweep it: the closeAll that would have has already run. " +
                     "Such a Statement should be returned to its caller uncached, as an overload Statement.",
                     0, StatementCacheAuditor.numStatementsForConnection( cache, conn ));

        assertTrue("The cache's own invariants should hold regardless: " +
                   StatementCacheAuditor.checkQuietly( cache ),
                   StatementCacheAuditor.checkQuietly( cache ).isEmpty());
    }

    /**
     * The other half of the requirement, and green today. closeAll(pcon) flushes a Connection's
     * Statements; it does not retire the Connection. Checkouts that begin after it must cache
     * normally, so a fix that simply stopped caching for a Connection that had seen a closeAll
     * would be wrong, and this says so.
     */
    public void testCachingResumesNormallyAfterCloseAll() throws Exception
    {
        seedOneCachedStatement();

        cache.closeAll( conn );
        assertEquals( 0, StatementCacheAuditor.numStatementsForConnection( cache, conn ) );

        // a checkout begun entirely after the closeAll -- no race, nothing straddling
        Object ps = cache.checkoutStatement( conn, PREPARE_STATEMENT, new Object[] { RACING_SQL } );
        assertNotNull( ps );

        assertEquals("A Connection that has merely been flushed must go on caching. closeAll(pcon) is " +
                     "documented as flushing that Connection's Statements, not as retiring the Connection.",
                     1, StatementCacheAuditor.numStatementsForConnection( cache, conn ));

        cache.checkinStatement( ps );
        assertTrue("invariants: " + StatementCacheAuditor.checkQuietly( cache ),
                   StatementCacheAuditor.checkQuietly( cache ).isEmpty());
    }

    private void seedOneCachedStatement() throws Exception
    {
        Object seed = cache.checkoutStatement( conn, PREPARE_STATEMENT, new Object[] { SEED_SQL } );
        cache.checkinStatement( seed );
        assertEquals("setUp should leave exactly one Statement cached for this Connection",
                     1, StatementCacheAuditor.numStatementsForConnection( cache, conn ));
    }

    private final class Acquirer extends Thread
    {
        volatile Object    statement;
        volatile Throwable failure;

        Acquirer()
        {
            super("closeAllRace-acquirer");
            this.setDaemon( true );
        }

        public void run()
        {
            try
            { statement = cache.checkoutStatement( conn, PREPARE_STATEMENT, new Object[] { RACING_SQL } ); }
            catch ( Throwable t )
            { failure = t; }
        }
    }
}
