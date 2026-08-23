package com.mchange.v2.c3p0.test.junit;

import java.sql.Connection;
import java.util.*;
import junit.framework.*;

import com.mchange.v2.c3p0.ComboPooledDataSource;
import com.mchange.v2.c3p0.DataSources;
import com.mchange.v2.c3p0.impl.C3P0TestInternals;
import com.mchange.v2.c3p0.stmt.GooGooStatementCache;
import com.mchange.v2.c3p0.test.fakedriver.FakeDriver;
import com.mchange.v2.c3p0.test.fakedriver.FakeDriverConfig;

/**
 * A DataSource has one pool per authentication, not one pool, so instrumentation that assumes the
 * default authentication silently reports on the wrong cache -- or on none -- for a client that
 * calls getConnection(user, password).
 */
public final class C3P0TestInternalsJUnitTestCase extends TestCase
{
    private FakeDriverConfig      cfg;
    private ComboPooledDataSource ds;

    public void setUp() throws Exception
    {
        FakeDriver.ensureRegistered();
        this.cfg = FakeDriverConfig.register("internals-" + System.nanoTime(), 5L);
        this.ds  = new ComboPooledDataSource();
        ds.setDriverClass( FakeDriver.DRIVER_CLASS_NAME );
        ds.setJdbcUrl( cfg.jdbcUrl() );
        // Use the named Driver class directly rather than going through DriverManager, which hands
        // a URL to registered drivers in order and takes the first Connection offered -- so a badly
        // behaved driver registered by some other test can answer for ours.
        ds.setForceUseNamedDriverClass( true );
        ds.setMaxStatementsPerConnection( 3 );
        ds.setMinPoolSize( 1 );
        ds.setMaxPoolSize( 2 );
        ds.setInitialPoolSize( 1 );
        ds.setTestConnectionOnCheckout( false );
        // fail fast: a test that cannot get a Connection should say so, not hang
        ds.setCheckoutTimeout( 10000 );
        ds.setAcquireRetryAttempts( 2 );
        ds.setAcquireRetryDelay( 250 );
    }

    public void tearDown() throws Exception
    {
        if ( ds != null )
            DataSources.destroy( ds );
        if ( cfg != null )
            FakeDriverConfig.unregister( cfg.name );
    }

    /** Observing a DataSource must not build its pool manager, which would start threads. */
    public void testReportsNothingBeforeTheDataSourceIsUsed() throws Exception
    {
        assertNull("An unused DataSource has no default pool", C3P0TestInternals.poolOf( ds ));
        assertNull("An unused DataSource has no statement cache", C3P0TestInternals.statementCacheOf( ds ));
        assertTrue("An unused DataSource has no pools at all", C3P0TestInternals.poolsOf( ds ).isEmpty());
        assertTrue("An unused DataSource has no statement caches", C3P0TestInternals.statementCachesOf( ds ).isEmpty());
    }

    public void testDefaultAuthPoolIsFoundOnceUsed() throws Exception
    {
        useAConnection( null, null );

        assertNotNull("The default pool should exist once a Connection has been served",
                      C3P0TestInternals.poolOf( ds ));
        assertNotNull("... and it should have a statement cache, since one is configured",
                      C3P0TestInternals.statementCacheOf( ds ));
        assertEquals("Only the default authentication has been used", 1, C3P0TestInternals.poolsOf( ds ).size());
    }

    /** The gap this API had: a pool reached only through getConnection(user, password). */
    public void testPerAuthenticationPoolsAreReachable() throws Exception
    {
        useAConnection( null, null );
        useAConnection( "alice", "secret" );

        GooGooStatementCache dflt  = C3P0TestInternals.statementCacheOf( ds );
        GooGooStatementCache alice = C3P0TestInternals.statementCacheOf( ds, "alice", "secret" );

        assertNotNull("The default authentication's cache should be reachable", dflt);
        assertNotNull("alice's cache should be reachable too", alice);
        assertNotSame("Each authentication gets its own pool, and so its own statement cache", dflt, alice);

        assertEquals("Two authentications have been used, so there should be two pools",
                     2, C3P0TestInternals.poolsOf( ds ).size());
        assertEquals("... and two statement caches",
                     2, C3P0TestInternals.statementCachesOf( ds ).size());
        assertEquals("authsOf should agree with poolsOf",
                     C3P0TestInternals.poolsOf( ds ).keySet(), C3P0TestInternals.authsOf( ds ));

        // every cache reachable one at a time is also in the collective view, and vice versa
        List all = C3P0TestInternals.statementCachesOf( ds );
        assertTrue("statementCachesOf should include the default authentication's cache", contains( all, dflt ));
        assertTrue("statementCachesOf should include alice's cache", contains( all, alice ));
    }

    /** Credentials nobody has used name no pool, and asking must not create one. */
    public void testUnknownAuthenticationYieldsNullWithoutCreatingAPool() throws Exception
    {
        useAConnection( null, null );

        assertNull("No pool should exist for credentials never used",
                   C3P0TestInternals.poolOf( ds, "bob", "hunter2" ));
        assertNull("... nor a statement cache",
                   C3P0TestInternals.statementCacheOf( ds, "bob", "hunter2" ));
        assertEquals("Asking about bob must not have created bob a pool",
                     1, C3P0TestInternals.poolsOf( ds ).size());
    }

    private void useAConnection( String user, String password ) throws Exception
    {
        Connection con = ( user == null ? ds.getConnection() : ds.getConnection( user, password ) );
        try
        { con.prepareStatement("SELECT a FROM t WHERE k = ?").close(); }
        finally
        { con.close(); }
    }

    private static boolean contains( List list, Object o )
    {
        for ( Iterator ii = list.iterator(); ii.hasNext(); )
            if ( ii.next() == o )
                return true;
        return false;
    }
}
