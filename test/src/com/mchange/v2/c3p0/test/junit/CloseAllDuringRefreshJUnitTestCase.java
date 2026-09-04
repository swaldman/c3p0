package com.mchange.v2.c3p0.test.junit;

import java.lang.reflect.Method;
import java.sql.Connection;
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
 * refreshStatement(...) restores a checked-in Statement to its initial condition, and does so with
 * a series of blocking JDBC calls. While those are made under mainLock, nothing else can touch the
 * Statement. Move them out from under the lock -- worth doing, since mainLock is global to the pool
 * and a slow refresh stalls caching for every Connection -- and a window opens in which the
 * Statement is checked out, its owner is inside refreshStatement rather than using it, and another
 * thread holds the lock.
 *
 * <p>closeAll(pcon) in that window removes the Statement and destroys it. The refresh, still
 * running, then goes on calling clearBatch(), clearWarnings() and the rest on a Statement c3p0 has
 * just physically closed. On a real driver that is an SQLException from a Statement we closed
 * ourselves; here the fake driver records it as a use-after-close anomaly, which is what this test
 * asserts against.
 *
 * <p>This is not a defect in c3p0 as it stands, where refreshStatement(...) is called with mainLock
 * held and closeAll(pcon) therefore cannot interleave. It is a test to keep alongside any change
 * that moves the refresh out of the lock, so the window that change opens is closed deliberately
 * rather than discovered later.
 */
public final class CloseAllDuringRefreshJUnitTestCase extends TestCase
{
    private final static String SQL        = "SELECT a FROM t WHERE k = ?";
    private final static long   TIMEOUT_MS = 30000;

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
        this.cfg    = FakeDriverConfig.register("closeAllRefresh-" + System.nanoTime(), 7L);
        this.timer  = new Timer("closeAllRefresh-timer", true);
        this.runner = new ThreadPoolAsynchronousRunner( 3, true, timer, "closeAllRefresh" );
        this.cache  = new PerConnectionMaxOnlyStatementCache( runner, null, 5, false );
        this.conn   = FakeConnection.create( cfg );
    }

    public void tearDown() throws Exception
    {
        if ( cfg != null )
        {
            CountDownLatch gate = cfg.statementMethodGate;   // never leave a parked thread behind
            if ( gate != null )
                gate.countDown();
        }
        try { if ( cache != null ) cache.close(); } catch ( Exception e ) { /* shutting down */ }
        if ( runner != null ) runner.close( true );
        if ( timer != null )  timer.cancel();
        if ( cfg != null )    FakeDriverConfig.unregister( cfg.name );
    }

    public void testCloseAllDoesNotDestroyAStatementBeingRefreshed() throws Exception
    {
        Object stmt = cache.checkoutStatement( conn, PREPARE_STATEMENT, new Object[] { SQL } );
        assertEquals( 1, StatementCacheAuditor.numStatementsForConnection( cache, conn ) );

        // park the check-in inside refreshStatement(...), on the first JDBC call it makes
        CountDownLatch reached = new CountDownLatch( 1 );
        CountDownLatch gate    = new CountDownLatch( 1 );
        cfg.gateOnStatementMethod  = "isPoolable";
        cfg.statementMethodReached = reached;
        cfg.statementMethodGate    = gate;

        CheckinThread checkin = new CheckinThread( stmt );
        checkin.start();

        assertTrue("The checking-in thread never reached the driver; the gate is not working.",
                   reached.await( TIMEOUT_MS, TimeUnit.MILLISECONDS ));

        // Now try to destroy the Connection's cached Statements while that refresh is midway
        // through. Whether this is even possible is the whole question, so it runs on its own
        // thread: where refreshStatement(...) is called with mainLock held, the parked check-in
        // still holds the lock and closeAll(...) cannot proceed at all. That is the safe
        // arrangement, and there is no window to test -- but we must not deadlock discovering it.
        CloseAllThread closeAll = new CloseAllThread();
        closeAll.start();
        boolean windowExists = closeAll.completed.await( 3000, TimeUnit.MILLISECONDS );

        gate.countDown();   // release the refresh either way, so nothing is left parked
        closeAll.join( TIMEOUT_MS );
        checkin.join( TIMEOUT_MS );
        assertFalse("The checking-in thread never finished.", checkin.isAlive());
        assertFalse("The closeAll thread never finished.", closeAll.isAlive());
        assertNull("closeAll(...) failed: " + closeAll.failure, closeAll.failure);

        if (! windowExists )
            return; // refresh runs under mainLock; closeAll could not interleave. Nothing to assert.

        assertEquals("closeAll(...) destroyed a Statement while refreshStatement(...) was still " +
                     "making JDBC calls on it, so c3p0 went on using a Statement it had just closed: " +
                     cfg.stats.anomalies(),
                     0, cfg.stats.numAnomalies());

        assertTrue("invariants: " + StatementCacheAuditor.checkQuietly( cache ),
                   StatementCacheAuditor.checkQuietly( cache ).isEmpty());
    }

    private final class CloseAllThread extends Thread
    {
        final CountDownLatch completed = new CountDownLatch( 1 );
        volatile Throwable failure;

        CloseAllThread()
        {
            super("closeAllRefresh-closeAll");
            this.setDaemon( true );
        }

        public void run()
        {
            try { cache.closeAll( conn ); }
            catch ( Throwable t ) { failure = t; }
            finally { completed.countDown(); }
        }
    }

    private final class CheckinThread extends Thread
    {
        private final Object stmt;
        volatile Throwable failure;

        CheckinThread( Object stmt )
        {
            super("closeAllRefresh-checkin");
            this.stmt = stmt;
            this.setDaemon( true );
        }

        public void run()
        {
            try { cache.checkinStatement( stmt ); }
            catch ( Throwable t ) { failure = t; }
        }
    }
}
