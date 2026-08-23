package com.mchange.v2.c3p0.test.junit;

import java.lang.reflect.Method;
import java.sql.*;
import java.util.Timer;
import junit.framework.*;

import com.mchange.v2.async.ThreadPoolAsynchronousRunner;
import com.mchange.v2.c3p0.stmt.GooGooStatementCache;
import com.mchange.v2.c3p0.stmt.PerConnectionMaxOnlyStatementCache;
import com.mchange.v2.c3p0.test.fakedriver.FakeConnection;
import com.mchange.v2.c3p0.test.fakedriver.FakeDriverConfig;
import com.mchange.v2.c3p0.test.stmt.SimulatedPooledConnection;

/**
 * A JDBC driver's statement-producing methods must return a Statement or throw. Some return null
 * and throw nothing -- this project's own MockDriver among them.
 *
 * <p>GooGooStatementCache acquires Statements on a background thread and waits until one of a
 * Statement or an Exception appears, so a null used to leave the waiting thread stranded for good,
 * holding its NewPooledConnection's monitor, with no exception and nothing logged. It cost a
 * ten-minute build timeout and a thread dump to find. The cache now treats a null return as the
 * driver bug it is.
 *
 * <p>Note that the checkout runs on its own thread and is joined with a timeout: a test for an
 * unbounded wait must not itself be able to wait unboundedly.
 */
public final class NullStatementAcquisitionJUnitTestCase extends TestCase
{
    private final static long COMPLETION_TIMEOUT_MILLIS = 30000;

    public void testNullFromPrepareStatementFailsRatherThanHangs() throws Exception
    {
        Throwable t = checkoutWith( 1.0d, SimulatedPooledConnection.PREPARE_STATEMENT_SIMPLE,
                                    new Object[] { "SELECT a FROM t WHERE k = ?" } );
        assertNotNull("A driver that returns null should have produced an Exception", t);
        assertTrue("Should fail as a SQLException, not " + t.getClass().getName(), t instanceof SQLException);
        assertTrue("The failure should name the driver's misbehavior, but said: " + describe( t ),
                   describe( t ).indexOf("returned null") >= 0);
    }

    public void testNullFromPrepareCallFailsRatherThanHangs() throws Exception
    {
        Throwable t = checkoutWith( 1.0d, SimulatedPooledConnection.PREPARE_CALL_SIMPLE,
                                    new Object[] { "{ call harness_proc( ? ) }" } );
        assertNotNull("A driver that returns null should have produced an Exception", t);
        assertTrue("The failure should name the driver's misbehavior, but said: " + describe( t ),
                   describe( t ).indexOf("returned null") >= 0);
    }

    /** ... and a driver that behaves is still served, so the check above cannot pass vacuously. */
    public void testAWellBehavedDriverStillYieldsStatements() throws Exception
    {
        Throwable t = checkoutWith( 0.0d, SimulatedPooledConnection.PREPARE_STATEMENT_SIMPLE,
                                    new Object[] { "SELECT a FROM t WHERE k = ?" } );
        assertNull("A conforming driver's Statement should have been cached and returned: " + describe( t ), t);
    }

    /**
     * Checks out one Statement against a fake driver configured to return null with the given
     * probability. Returns whatever the checkout threw, or null if it succeeded; fails the test
     * rather than hanging if the checkout does not complete.
     */
    private Throwable checkoutWith( double nullProbability, final Method producer, final Object[] args )
        throws Exception
    {
        FakeDriverConfig cfg = FakeDriverConfig.register("nullstmt-" + System.nanoTime(), 3L);
        cfg.returnNullFromPrepareProbability = nullProbability;

        Timer timer = new Timer("NullStatementAcquisitionJUnitTestCase-timer", true);
        ThreadPoolAsynchronousRunner runner =
            new ThreadPoolAsynchronousRunner( 3, true, timer, "NullStatementAcquisitionJUnitTestCase" );
        final GooGooStatementCache cache = new PerConnectionMaxOnlyStatementCache( runner, null, 3, false );
        final Connection conn = FakeConnection.create( cfg );

        final Throwable[] thrown    = new Throwable[1];
        final Object[]    statement = new Object[1];

        Thread checkout = new Thread("nullstmt-checkout")
        {
            public void run()
            {
                try
                { statement[0] = cache.checkoutStatement( conn, producer, args ); }
                catch ( Throwable t )
                { thrown[0] = t; }
            }
        };
        checkout.setDaemon( true );

        try
        {
            checkout.start();
            checkout.join( COMPLETION_TIMEOUT_MILLIS );
            assertFalse("checkoutStatement did not complete within " + COMPLETION_TIMEOUT_MILLIS +
                        "ms -- a null Statement has stranded the acquiring thread again",
                        checkout.isAlive());

            if ( thrown[0] == null )
                assertNotNull("A conforming driver should have yielded a Statement", statement[0]);

            return thrown[0];
        }
        finally
        {
            try { cache.close(); } catch ( Exception e ) { /* shutting down */ }
            runner.close( true );
            timer.cancel();
            FakeDriverConfig.unregister( cfg.name );
        }
    }

    /** The whole causal chain, since the informative message is the cause, not the wrapper. */
    private static String describe( Throwable t )
    {
        StringBuffer sb = new StringBuffer(512);
        for ( Throwable c = t; c != null; c = c.getCause() )
        {
            if ( sb.length() > 0 )
                sb.append(" <- ");
            sb.append( c );
        }
        return sb.toString();
    }
}
