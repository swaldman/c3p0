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
 * <p>closeAll(pcon) in that window removes the Statement and destroys it, and the refresh goes on
 * calling clearBatch(), clearWarnings() and the rest on a Statement c3p0 has just physically
 * closed. What the test asserts against is not that use-after-close, though, but the thing beneath
 * it: two threads inside one Statement at once. JDBC objects are not thread-safe, so that is
 * undefined behavior rather than a specified exception. Closing twice, and using after close, are
 * left alone -- cleanup should be idempotent, and where a Statement might otherwise go unclosed,
 * closing it twice is the right direction to err.
 *
 * <p><b>Why this exercises the deferred-destroy arrangement, which is not the default.</b>
 * statementCacheNumDeferredCloseThreads defaults to 0, which gives
 * IncautiousStatementDestructionManager, and that manager closes Statements on a pool thread
 * without regard to whether the parent Connection is in use. That is knowingly out of spec -- JDBC
 * asks that only one thread interact with a Connection's children at a time, and close() counts as
 * interacting -- and it has been c3p0's default for two decades because most drivers tolerate it,
 * and because changing the default would disturb a great many working deployments to fix something
 * they are not suffering from.
 *
 * <p>So under the default, a destroy overlapping a refresh is not a new violation. It is the same
 * bargain those users already have, and it ends well enough: the refresh throws and the Statement
 * is discarded, or the refresher reacquires the lock, finds the Statement no longer in the cache,
 * and destroys it. Asserting otherwise here would be asserting a guarantee c3p0 deliberately does
 * not make. It would also make this test load-dependent -- green when run alone, red under a full
 * suite -- since whether the asynchronous close lands inside the refresh window is a matter of
 * scheduling.
 *
 * <p>The deferred-destroy arrangement is where the guarantee actually exists: it is what users
 * whose drivers insist on the spec (Oracle's, most often) configure precisely to avoid concurrent
 * access to a Connection's children. So that is the arrangement worth holding to it, and the one
 * this test builds. A caller must have marked the Connection in use before calling closeAll(pcon)
 * -- every path within c3p0 does -- and the test does the same, standing in for the pool.
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
    private ThreadPoolAsynchronousRunner deferredDestroyer;
    private GooGooStatementCache         cache;
    private Connection                   conn;

    public void setUp() throws Exception
    {
        this.cfg    = FakeDriverConfig.register("closeAllRefresh-" + System.nanoTime(), 7L);
        this.timer  = new Timer("closeAllRefresh-timer", true);
        this.runner = new ThreadPoolAsynchronousRunner( 3, true, timer, "closeAllRefresh" );
        // a deferred statement destroyer, so the cache uses CautiousStatementDestructionManager --
        // the arrangement in which destruction can be held back while a Connection is in use
        this.deferredDestroyer = new ThreadPoolAsynchronousRunner( 1, true );
        this.cache  = new PerConnectionMaxOnlyStatementCache( runner, deferredDestroyer, 5, false );
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
        if ( deferredDestroyer != null ) deferredDestroyer.close( true );
        if ( timer != null )  timer.cancel();
        if ( cfg != null )    FakeDriverConfig.unregister( cfg.name );
    }

    public void testCloseAllDoesNotDestroyAStatementBeingRefreshed() throws Exception
    {
        // Mark the Connection in use, as C3P0PooledConnectionPool does for the whole of a checkout
        // and as callers of closeAll(...) are now required to do. Without it the cache has no
        // reason to hold destruction back, and closeAll(...) merely closes asynchronously rather
        // than synchronously -- which does not avoid the overlap, it only makes it a matter of
        // scheduling, green when this test runs alone and red when it runs under load.
        cache.waitMarkConnectionInUse( conn );

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

        // as the pool does on check-in; this is also what releases any deferred destruction
        cache.unmarkConnectionInUse( conn );

        if (! windowExists )
            return; // refresh runs under mainLock; closeAll could not interleave. Nothing to assert.

        // What is forbidden is two threads inside one Statement at once: JDBC objects are not
        // thread-safe, which is the very hazard CautiousStatementDestructionManager exists to avoid.
        //
        // Deliberately NOT asserted: use-after-close, and closing twice. Those follow from the same
        // overlap, but they are the tolerable half of it. Cleanup should be idempotent, and where a
        // Statement might not get closed, closing it twice is the right direction to err.
        assertEquals("closeAll(...) worked on a Statement while refreshStatement(...) was still " +
                     "making JDBC calls on it, from another thread: " + cfg.stats.concurrentUseAnomalies(),
                     0, cfg.stats.concurrentUseAnomalies().size());

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
