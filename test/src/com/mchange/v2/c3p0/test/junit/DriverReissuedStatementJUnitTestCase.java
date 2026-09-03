package com.mchange.v2.c3p0.test.junit;

import java.sql.*;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import junit.framework.*;

import com.mchange.v2.c3p0.ComboPooledDataSource;
import com.mchange.v2.c3p0.DataSources;
import com.mchange.v2.c3p0.test.fakedriver.FakeDriver;
import com.mchange.v2.c3p0.test.fakedriver.FakeDriverConfig;
import com.mchange.v2.c3p0.test.stmt.StatementCacheStressHarness;

/**
 * c3p0 must not presume that Connection.prepareStatement(...) hands back a Statement it has never
 * seen. Some JDBC drivers cache Statements internally, and a driver-side cache can hand back the
 * very object this cache already holds. See https://github.com/swaldman/c3p0/issues/196
 *
 * <p>Assimilating such a Statement a second time forks the cache's bookkeeping: stmtToKey is
 * repointed at the new key while the Statement remains in the old key's allStmts and checkoutQueue,
 * and checkedOut gains a Statement still sitting in a deathmarch. Nothing notices at the time. It
 * surfaces later as "A statement is being double-deathmatched", or as "A checked-out statement has
 * no key associated with it" -- the pair reported against 0.14.2-pre1.
 *
 * <p>So assimilateNewCheckedOutStatement(...) declines a Statement the cache already holds, and
 * disowns the copy it has. Both halves matter, and this covers them separately:
 * {@link #testCacheStaysConsistentWhenDriverReissuesStatements} fails if the guard goes away, and
 * {@link #testDisownedDuplicateIsNotHandedOutAgain} fails if the guard stops disowning.
 */
public final class DriverReissuedStatementJUnitTestCase extends TestCase
{
    private final static String SQL = "SELECT a FROM t WHERE k = ?";

    /** The one signal that the guard actually fired, so that neither test can pass vacuously. */
    private final static String GUARD_WARNING = "was handed a Statement it already caches";

    private GuardWatcher watcher;

    public void setUp()
    {
        FakeDriver.ensureRegistered();
        this.watcher = new GuardWatcher();
        java.util.logging.Logger.getLogger("").addHandler( watcher );
    }

    public void tearDown()
    {
        java.util.logging.Logger.getLogger("").removeHandler( watcher );
    }

    /**
     * A Statement the cache disowns belongs to its client, and NewPooledConnection closes it when
     * the client abandons it. So the cache must really let go: if it kept the Statement in a
     * checkout queue, it would later hand a physically closed Statement to somebody else.
     *
     * <p>Deterministic, single-threaded, and it fails if removeStatement( ps, DESTROY_NEVER ) is
     * dropped from the guard.
     */
    public void testDisownedDuplicateIsNotHandedOutAgain() throws Exception
    {
        FakeDriverConfig cfg = FakeDriverConfig.register("reissued-disown-" + System.nanoTime(), 1L);
        cfg.handBackLiveStatementProbability = 1.0d; // always reissue a still-open Statement, if there is one

        ComboPooledDataSource ds = newDataSource( cfg );
        try
        {
            Connection con = ds.getConnection();

            // cache a Statement under the plain prepareStatement(String) key, and check it back in
            con.prepareStatement( SQL ).close();

            // the same SQL under a *different* c3p0 key -- result set type and concurrency are part
            // of the key, but a driver-side cache typically keys on SQL text alone. So this misses,
            // and the driver reissues the Statement we already hold.
            PreparedStatement duplicate = con.prepareStatement( SQL, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY );
            assertNotNull("The driver should have produced a Statement", duplicate);
            assertTrue("The duplicate-Statement guard never fired. Either the guard in " +
                       "assimilateNewCheckedOutStatement(...) is gone, or the fake driver no longer " +
                       "reissues Statements -- either way this test is no longer testing what it " +
                       "means to test.", watcher.fired());

            // The client abandons it rather than closing it, then closes its Connection.
            // NewPooledConnection now closes whatever the cache reported as uncached.
            con.close();

            // Ask for the original key again. If the cache failed to disown the Statement, this is
            // a cache hit on a physically closed one.
            Connection con2 = ds.getConnection();
            try
            {
                PreparedStatement again = con2.prepareStatement( SQL );
                again.setString( 1, "x" );
                ResultSet rs = again.executeQuery();
                if ( rs != null )
                    rs.close();
                again.close();
            }
            catch ( SQLException e )
            {
                fail("The cache handed back a Statement that had been closed. It disowned a duplicate " +
                     "without removing it, so it kept offering a Statement whose lifecycle it had given " +
                     "away: " + e.getMessage());
            }
            finally
            { con2.close(); }
        }
        finally
        { destroy( ds, cfg ); }
    }

    /**
     * Under a driver that reissues Statements, the cache's own invariants must hold. Without the
     * guard they do not: the stress harness audits after every operation, and reports the first
     * violation -- or the cache's own "double-deathmatched" / "no key associated with it" throws.
     *
     * <p>Note what is deliberately NOT asserted. Once a driver hands the same Statement to two
     * owners, whichever closes first closes it under the other, so use-after-close and redundant
     * closes are expected here. No bookkeeping on c3p0's side can prevent that; only the driver can.
     */
    public void testCacheStaysConsistentWhenDriverReissuesStatements() throws Exception
    {
        // All three cache implementations, because they keep different deathmarch structures and
        // the guard removes the Statement it declines -- so each drives removeStatement(...)
        // differently. The guard was written against PerConnectionMaxOnly, which is the reporter's
        // configuration; the other two deserve the same cover.
        checkStaysConsistent( StatementCacheStressHarness.PER_CONNECTION_MAX_ONLY, "perConnectionMaxOnly", false );
        checkStaysConsistent( StatementCacheStressHarness.GLOBAL_MAX_ONLY,         "globalMaxOnly",        true  );
        checkStaysConsistent( StatementCacheStressHarness.DOUBLE_MAX,              "doubleMax",            false );
    }

    private void checkStaysConsistent( int cacheKind, String label, boolean deferredStatementDestroyer )
        throws Exception
    {
        StatementCacheStressHarness.Scenario s =
            new StatementCacheStressHarness.Scenario("junit-driver-reissued-statements-" + label);
        s.cacheKind = cacheKind;
        s.deferredStatementDestroyer = deferredStatementDestroyer;
        s.threads     = 8;
        s.connections = 4;
        s.handBackLiveStatementProbability = 0.25d;
        s.expectDriverAliasing = true;

        StatementCacheStressHarness.Result r =
            StatementCacheStressHarness.runScenario( s, 2000, 196196196L, 1 );

        // The substantive check comes first, and deliberately before the sanity checks below: the
        // harness stops at its first fatal, so a cache that breaks immediately also looks like a
        // workload that never ran. This is the assertion that says what actually went wrong.
        assertNull( label + ": the statement cache lost track of its own state while the driver " +
                   "reissued Statements it already held:\n" + r.firstFatal, r.firstFatal );

        assertTrue( label + ": the workload did no work; the harness is misconfigured.", r.operations > 100 );

        assertTrue( label + ": the duplicate-Statement guard never fired across " + r.operations + " operations. " +
                   "Either the guard in assimilateNewCheckedOutStatement(...) is gone, or the fake " +
                   "driver no longer reissues Statements -- either way this test is no longer testing " +
                   "what it means to test.",
                   watcher.fired());
    }

    private ComboPooledDataSource newDataSource( FakeDriverConfig cfg ) throws Exception
    {
        ComboPooledDataSource ds = new ComboPooledDataSource();
        ds.setDriverClass( FakeDriver.DRIVER_CLASS_NAME );
        ds.setJdbcUrl( cfg.jdbcUrl() );
        // don't leave the choice of driver to DriverManager's registration order
        ds.setForceUseNamedDriverClass( true );
        ds.setMaxStatementsPerConnection( 10 );
        ds.setMinPoolSize( 1 );
        ds.setMaxPoolSize( 1 );   // one physical Connection, so we keep meeting the same cache entries
        ds.setInitialPoolSize( 1 );
        ds.setTestConnectionOnCheckout( false );
        ds.setCheckoutTimeout( 10000 );
        return ds;
    }

    private void destroy( ComboPooledDataSource ds, FakeDriverConfig cfg )
    {
        try { DataSources.destroy( ds ); }
        catch ( Exception e ) { /* shutting down */ }
        FakeDriverConfig.unregister( cfg.name );
    }

    /** Watches for the guard's WARNING, so a test cannot pass because the condition never arose. */
    private final static class GuardWatcher extends Handler
    {
        private volatile boolean fired = false;

        boolean fired()
        { return fired; }

        public void publish( LogRecord record )
        {
            String msg = ( record == null ? null : record.getMessage() );
            if ( msg != null && msg.indexOf( GUARD_WARNING ) >= 0 )
                fired = true;
        }

        public void flush() {}
        public void close() {}
    }
}
