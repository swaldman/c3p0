package com.mchange.v2.c3p0.test.junit;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.util.Timer;
import junit.framework.*;

import com.mchange.v2.async.ThreadPoolAsynchronousRunner;
import com.mchange.v2.c3p0.stmt.GooGooStatementCache;
import com.mchange.v2.c3p0.stmt.PerConnectionMaxOnlyStatementCache;
import com.mchange.v2.c3p0.test.fakedriver.FakeConnection;
import com.mchange.v2.c3p0.test.fakedriver.FakeDriverConfig;
import com.mchange.v2.util.ResourceClosedException;

/**
 * Closing a statement cache must release whoever is waiting on it for a Statement.
 *
 * <p>GooGooStatementCache acquires Statements on a background thread and waits for one. Closing a
 * DataSource runs C3P0PooledConnectionPoolManager.poolsDestroy(), which closes each pool -- and so
 * each cache -- and then closes the shared task runner with skip_remaining_tasks = true, discarding
 * whatever acquisition tasks were still queued. A discarded task never completes and never signals,
 * so before the cache learned to wake its waiters on close(), a thread waiting behind a queued task
 * waited for good, holding its NewPooledConnection's monitor.
 *
 * <p>The test reproduces that shape exactly: one acquisition running on a single-threaded runner,
 * a second queued behind it, then the cache closed and the runner closed the way poolsDestroy()
 * closes it. Both waiters must come back, and must come back with ResourceClosedException, which is
 * what the Statement proxies catch in order to fall back to an uncached Statement -- so a Connection
 * in use survives a DataSource reset, merely losing the cache.
 *
 * <p>As with NullStatementAcquisitionJUnitTestCase, the waits are bounded: a test for an unbounded
 * wait must not itself be able to wait unboundedly.
 */
public final class StatementCacheCloseReleasesWaitersJUnitTestCase extends TestCase
{
    private final static long PREPARE_LATENCY_MILLIS   = 30000; // long enough that only close() can end the wait
    private final static long COMPLETION_TIMEOUT_MILLIS = 30000;
    private final static long SETUP_TIMEOUT_MILLIS      = 10000;

    public void testCloseReleasesThreadsWaitingForStatements() throws Exception
    {
        FakeDriverConfig cfg = FakeDriverConfig.register("closewaiters-" + System.nanoTime(), 11L);
        cfg.prepareLatencyMinMillis = PREPARE_LATENCY_MILLIS;
        cfg.prepareLatencyMaxMillis = PREPARE_LATENCY_MILLIS;

        Timer timer = new Timer("closewaiters-timer", true);
        // one thread, so the second acquisition must queue behind the first
        ThreadPoolAsynchronousRunner runner = new ThreadPoolAsynchronousRunner( 1, true, timer, "closewaiters" );
        GooGooStatementCache cache = new PerConnectionMaxOnlyStatementCache( runner, null, 10, false );
        Connection conn = FakeConnection.create( cfg );

        try
        {
            Waiter running = new Waiter( cache, conn, "SELECT running" );
            running.start();
            awaitOrFail("the first acquisition never started running", runner, true, 0);

            Waiter queued = new Waiter( cache, conn, "SELECT queued" );
            queued.start();
            awaitOrFail("the second acquisition never queued", runner, false, 1);

            cache.close();
            runner.close( true ); // as poolsDestroy() does: the queued task is discarded, unrun

            running.join( COMPLETION_TIMEOUT_MILLIS );
            queued.join( COMPLETION_TIMEOUT_MILLIS );

            assertFalse("The waiter whose acquisition was running was not released by close()", running.isAlive());
            assertFalse("The waiter whose acquisition was discarded unrun was stranded by close()", queued.isAlive());

            assertTrue("Expected ResourceClosedException, so that the Statement proxies can fall back to an " +
                       "uncached Statement, but the running acquisition's waiter got: " + running.describeOutcome(),
                       running.outcome instanceof ResourceClosedException);
            assertTrue("Expected ResourceClosedException, but the discarded acquisition's waiter got: " +
                       queued.describeOutcome(),
                       queued.outcome instanceof ResourceClosedException);
        }
        finally
        {
            timer.cancel();
            FakeDriverConfig.unregister( cfg.name );
        }
    }

    /** Waits, rather than sleeping a guessed interval, for the runner to reach the state we need. */
    private static void awaitOrFail( String message, ThreadPoolAsynchronousRunner runner,
                                     boolean wantActive, int wantPending )
        throws InterruptedException
    {
        long deadline = System.currentTimeMillis() + SETUP_TIMEOUT_MILLIS;
        while ( System.currentTimeMillis() < deadline )
        {
            if ( (!wantActive || runner.getActiveCount() > 0) && runner.getPendingTaskCount() == wantPending )
                return;
            Thread.sleep( 10 );
        }
        fail( message + " (active: " + runner.getActiveCount() + ", pending: " + runner.getPendingTaskCount() + ")" );
    }

    private final static class Waiter extends Thread
    {
        private final static Method PREPARE_STATEMENT;

        static
        {
            try
            { PREPARE_STATEMENT = Connection.class.getMethod("prepareStatement", new Class[] { String.class }); }
            catch ( NoSuchMethodException e )
            { throw new ExceptionInInitializerError( e ); }
        }

        private final GooGooStatementCache cache;
        private final Connection           conn;
        private final String               sql;

        volatile Object    statement;
        volatile Throwable outcome;

        Waiter( GooGooStatementCache cache, Connection conn, String sql )
        {
            super("closewaiters-" + sql);
            this.cache = cache;
            this.conn  = conn;
            this.sql   = sql;
            this.setDaemon( true );
        }

        public void run()
        {
            try
            { statement = cache.checkoutStatement( conn, PREPARE_STATEMENT, new Object[] { sql } ); }
            catch ( Throwable t )
            { outcome = t; }
        }

        String describeOutcome()
        { return ( outcome == null ? "a Statement: " + statement : outcome.getClass().getName() + ": " + outcome.getMessage() ); }
    }
}
