package com.mchange.v2.c3p0.test.junit;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import com.mchange.v2.c3p0.DriverManagerDataSource;
import com.mchange.v2.c3p0.DriverManagerDataSourceInternals;

import junit.framework.TestCase;

/**
 *  DriverManagerDataSource caches the Driver it resolves, and must invalidate that cache
 *  when, and only when, the configuration it was resolved from changes.
 *
 *  <h3>The state that makes all of this subtle</h3>
 *
 *  <p><code>driver_class_loaded == false</code> while <code>driver != null</code> is a
 *  perfectly ordinary, indefinitely long-lived state: it is what a DataSource looks like
 *  when its driverClass cannot be loaded but its jdbcUrl does resolve. c3p0 tolerates that
 *  configuration on purpose -- driverClass is documented as a fallback for when the
 *  URL-based lookup fails -- so the flag stays false forever while a perfectly good,
 *  DriverManager-sourced Driver sits in the cache.</p>
 *
 *  <p>Every test here exists because some piece of code assumed those two travelled
 *  together. {@link #testTheFlagIsFalseWhileADriverIsCached} asserts the state itself
 *  first, because if it were ever unreachable the rest of this file would pass vacuously.</p>
 *
 *  <h3>The two regressions pinned here</h3>
 *
 *  <p><b>Discarding a live Driver on every checkout.</b> A clearDriver() placed on the
 *  class-not-found path threw away the cached Driver each time the unloadable driverClass
 *  was re-attempted -- which is once per getConnection(), forever. The pool went back to
 *  DriverManager for every single connection it acquired.
 *  {@link #testTheCachedDriverSurvivesRepeatedCheckouts} counts the lookups.</p>
 *
 *  <p><b>Stranding a stale Driver across a driverClass change.</b> The converse: making the
 *  clear conditional on the flag actually changing meant a driverClass change did not clear
 *  the cache when the flag was already false -- i.e. in precisely the state above. An
 *  operator correcting a bad driverClass, over JMX say, was silently ignored for the life of
 *  the DataSource, even with forceUseNamedDriverClass set.
 *  {@link #testChangingDriverClassClearsTheCachedDriverEvenWhenTheFlagWasAlreadyFalse} and
 *  {@link #testACorrectedDriverClassIsHonouredUnderForcing} pin that, the first structurally
 *  and the second by which Driver actually serves a connection.</p>
 *
 *  <h3>One mutation that survives on purpose</h3>
 *
 *  <p>setDriverClassLoaded(..) keeps a clearDriver() of its own, reached when the flag actually
 *  changes to false. Removing it breaks nothing here, and that is not a gap. Every caller that
 *  passes false -- today, only the driverClass listener -- already clears explicitly, precisely
 *  because the guarded side-effect cannot be relied upon in the flag-already-false state that
 *  this file is about. It is retained as a backstop for a future caller that forgets, and the
 *  only way to cover it would be to call the private setter directly and assert on a side
 *  effect, which would pin the implementation rather than the behaviour. The behaviour it
 *  guards -- a driverClass change always invalidating the cache -- is covered twice above, from
 *  both starting states. Please do not "fix" the survivor by weakening those.</p>
 *
 *  <p>The three mutations that do NOT survive, each killing exactly one or two cases: restoring
 *  the clearDriver() on the class-not-found path; removing the listener's explicit clear so that
 *  only the guarded backstop remains; and removing setJdbcUrl(..)'s clear.</p>
 *
 *  <p>No database is needed: the drivers here are local stubs.</p>
 */
public class DriverCacheInvalidationJUnitTestCase extends TestCase
{
    /**
     *  Whitelisted in test/resources-local/c3p0.properties precisely because it does not
     *  exist, so the suite still runs under enforceWhitelist=true: these cases mean to
     *  exercise the class-not-found path, and a whitelist refusal would preempt it.
     */
    private final static String ABSENT_DRIVER = "no.such.driver.DoesNotExist";

    /** Which driver last served a connect(..). Single-threaded, but volatile is free here. */
    private volatile static String lastServedBy;

    // ------------------------------------------------------------------ the drivers

    /**
     *  Counts the acceptsURL(..) calls DriverManager makes while resolving this driver's own
     *  URL scheme, and records who served a connect(..).
     */
    public static abstract class CountingDriver implements Driver
    {
        abstract String prefix();
        abstract AtomicInteger urlLookups();

        private boolean mine( String url )
        { return url != null && url.startsWith( prefix() ); }

        /**
         *  Counts only lookups for this driver's own scheme. DriverManager offers every URL to
         *  every registered driver in turn, so counting every call would make this total depend
         *  on whatever else is running in this JVM.
         */
        @Override
        public boolean acceptsURL( String url )
        {
            boolean mine = mine( url );
            if ( mine ) urlLookups().incrementAndGet();
            return mine;
        }

        @Override
        public Connection connect( String url, Properties info )
        {
            if ( ! mine( url ) )
                return null; // per the JDBC spec, so DriverManager goes on to try the others
            lastServedBy = this.getClass().getName();
            return new MockConnectionWithoutBoundaries();
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo( String url, Properties info )
        { return new DriverPropertyInfo[0]; }

        @Override
        public int getMajorVersion()
        { return 1; }

        @Override
        public int getMinorVersion()
        { return 0; }

        @Override
        public boolean jdbcCompliant()
        { return false; }

        @Override
        public Logger getParentLogger()
        { return Logger.getLogger( this.getClass().getName() ); }
    }

    /**
     *  Registered with DriverManager, under a scheme private to this test so that registering
     *  it cannot affect any other test sharing this JVM.
     */
    public static class PrimaryDriver extends CountingDriver
    {
        public final static String PREFIX = "jdbc:c3p0test-cacheprimary:";

        public final static AtomicInteger URL_LOOKUPS = new AtomicInteger();

        static
        {
            try { DriverManager.registerDriver( new PrimaryDriver() ); }
            catch ( SQLException e ) { throw new ExceptionInInitializerError( e ); }
        }

        public PrimaryDriver()
        {}

        @Override
        String prefix()
        { return PREFIX; }

        @Override
        AtomicInteger urlLookups()
        { return URL_LOOKUPS; }
    }

    /** A second registered driver, for the jdbcUrl-change case. */
    public static class SecondaryDriver extends CountingDriver
    {
        public final static String PREFIX = "jdbc:c3p0test-cachesecondary:";

        public final static AtomicInteger URL_LOOKUPS = new AtomicInteger();

        static
        {
            try { DriverManager.registerDriver( new SecondaryDriver() ); }
            catch ( SQLException e ) { throw new ExceptionInInitializerError( e ); }
        }

        public SecondaryDriver()
        {}

        @Override
        String prefix()
        { return PREFIX; }

        @Override
        AtomicInteger urlLookups()
        { return URL_LOOKUPS; }
    }

    /**
     *  Deliberately NOT registered with DriverManager, and accepts the PRIMARY scheme. So if
     *  this driver ever serves a connection for a primary-scheme URL, it can only have been
     *  reached by loading driverClass by name: the URL alone would have found PrimaryDriver.
     */
    public static class NamedOnlyDriver extends CountingDriver
    {
        public final static AtomicInteger URL_LOOKUPS = new AtomicInteger();

        public NamedOnlyDriver()
        {}

        @Override
        String prefix()
        { return PrimaryDriver.PREFIX; }

        @Override
        AtomicInteger urlLookups()
        { return URL_LOOKUPS; }
    }

    // ------------------------------------------------------------------ fixture

    @Override
    public void setUp() throws Exception
    {
        // A String constant is inlined at compile time, so naming PREFIX would NOT initialize
        // these classes, and their registration blocks would never run. Force it explicitly.
        Class.forName( PrimaryDriver.class.getName() );
        Class.forName( SecondaryDriver.class.getName() );

        PrimaryDriver.URL_LOOKUPS.set( 0 );
        SecondaryDriver.URL_LOOKUPS.set( 0 );
        NamedOnlyDriver.URL_LOOKUPS.set( 0 );
        lastServedBy = null;
    }

    /**
     *  A DataSource in the state this whole file is about: an unloadable driverClass, a jdbcUrl
     *  that resolves, one checkout already taken so the Driver is cached.
     */
    private DriverManagerDataSource unloadableClassWithResolvableUrl() throws Exception
    {
        DriverManagerDataSource dmds = new DriverManagerDataSource();
        dmds.setJdbcUrl( PrimaryDriver.PREFIX + "whatever" );
        dmds.setDriverClass( ABSENT_DRIVER );
        // forceUseNamedDriverClass deliberately left false: the URL lookup is meant to win
        dmds.getConnection().close();
        return dmds;
    }

    // ------------------------------------------------------------------ the state itself

    /**
     *  THE PRECONDITION FOR EVERYTHING ELSE HERE. If this state were unreachable, every other
     *  case in this file would be testing nothing.
     */
    public void testTheFlagIsFalseWhileADriverIsCached() throws Exception
    {
        DriverManagerDataSource dmds = unloadableClassWithResolvableUrl();

        assertEquals( "the URL lookup must have produced the connection",
                      PrimaryDriver.class.getName(), lastServedBy );
        assertFalse( "driver_class_loaded must be false: the named class could not be loaded",
                     DriverManagerDataSourceInternals.driverClassLoaded( dmds ) );
        assertNotNull( "and yet a Driver must be cached, the one resolved from the jdbcUrl",
                       DriverManagerDataSourceInternals.cachedDriver( dmds ) );
    }

    // ------------------------------------------------------------------ keeping the cache

    /**
     *  The cached Driver must survive repeated checkouts. An unloadable driverClass is retried
     *  on every getConnection() -- the flag never becomes true, so there is nothing to
     *  remember -- and a clear on that path cost a fresh DriverManager lookup, every time,
     *  for the life of the DataSource.
     *
     *  <p>Counted rather than merely observed working, because the broken version worked
     *  perfectly well: it was only slow, and only in a configuration nobody looks at twice.</p>
     */
    public void testTheCachedDriverSurvivesRepeatedCheckouts() throws Exception
    {
        DriverManagerDataSource dmds = unloadableClassWithResolvableUrl();

        int afterFirst = PrimaryDriver.URL_LOOKUPS.get();
        assertTrue( "precondition: resolving the URL at all must take a lookup", afterFirst > 0 );

        for ( int i = 0; i < 5; ++i )
            dmds.getConnection().close();

        assertEquals( "no further DriverManager lookup may be needed once a Driver is cached",
                      afterFirst, PrimaryDriver.URL_LOOKUPS.get() );
        assertNotNull( "and the Driver must still be cached",
                       DriverManagerDataSourceInternals.cachedDriver( dmds ) );
    }

    // ------------------------------------------------------------------ dropping the cache

    /**
     *  THE REGRESSION. driverClass changed, so the cached Driver was resolved under a
     *  configuration that no longer holds and must go -- and this must not depend on
     *  driver_class_loaded having been true, because in this state it never was.
     */
    public void testChangingDriverClassClearsTheCachedDriverEvenWhenTheFlagWasAlreadyFalse() throws Exception
    {
        DriverManagerDataSource dmds = unloadableClassWithResolvableUrl();

        assertFalse( "precondition: the flag is already false",
                     DriverManagerDataSourceInternals.driverClassLoaded( dmds ) );
        assertNotNull( "precondition: a Driver is cached",
                       DriverManagerDataSourceInternals.cachedDriver( dmds ) );

        dmds.setDriverClass( NamedOnlyDriver.class.getName() );

        assertNull( "a driverClass change must invalidate the cached Driver, and the flag being "
                        + "unchanged at false is no reason to keep it",
                    DriverManagerDataSourceInternals.cachedDriver( dmds ) );
    }

    /**
     *  The same from the other starting state, where the flag does change true -> false. This
     *  is the case the setter's own backstop covers; both routes must end with no cached Driver.
     */
    public void testChangingDriverClassClearsTheCachedDriverWhenTheFlagWasTrue() throws Exception
    {
        DriverManagerDataSource dmds = new DriverManagerDataSource();
        dmds.setJdbcUrl( PrimaryDriver.PREFIX + "whatever" );
        dmds.setDriverClass( PrimaryDriver.class.getName() ); // loadable, and what the URL resolves to
        dmds.getConnection().close();

        assertTrue( "precondition: a loadable driverClass must set the flag",
                    DriverManagerDataSourceInternals.driverClassLoaded( dmds ) );
        assertNotNull( "precondition: a Driver is cached",
                       DriverManagerDataSourceInternals.cachedDriver( dmds ) );

        dmds.setDriverClass( ABSENT_DRIVER );

        assertNull( "a driverClass change must invalidate the cached Driver",
                    DriverManagerDataSourceInternals.cachedDriver( dmds ) );
        assertFalse( "and the class is no longer loaded",
                     DriverManagerDataSourceInternals.driverClassLoaded( dmds ) );
    }

    /**
     *  The behavioural half, and the one that says why the structural assertions matter: a
     *  corrected driverClass must actually be used. Here the correction can only be honoured by
     *  the by-name path, since NamedOnlyDriver is not registered with DriverManager -- so if a
     *  stale Driver were kept, PrimaryDriver would serve and the operator's correction would
     *  have been silently discarded.
     */
    public void testACorrectedDriverClassIsHonouredUnderForcing() throws Exception
    {
        DriverManagerDataSource dmds = unloadableClassWithResolvableUrl();
        assertEquals( "precondition: the stale Driver came from the URL lookup",
                      PrimaryDriver.class.getName(), lastServedBy );

        dmds.setForceUseNamedDriverClass( true );
        dmds.setDriverClass( NamedOnlyDriver.class.getName() );

        lastServedBy = null;
        dmds.getConnection().close();

        assertEquals( "the corrected driverClass must serve the connection, not the Driver cached "
                          + "under the old configuration",
                      NamedOnlyDriver.class.getName(), lastServedBy );
    }

    /**
     *  jdbcUrl is the other input the cached Driver was resolved from, and setJdbcUrl(..)
     *  overrides its generated setter for no other purpose than to clear the cache. Pinned
     *  because nothing else covers it, and because it is the precedent that settles what the
     *  driverClass path above should do.
     */
    public void testChangingJdbcUrlClearsTheCachedDriver() throws Exception
    {
        DriverManagerDataSource dmds = unloadableClassWithResolvableUrl();
        assertNotNull( "precondition: a Driver is cached",
                       DriverManagerDataSourceInternals.cachedDriver( dmds ) );

        dmds.setJdbcUrl( SecondaryDriver.PREFIX + "whatever" );

        assertNull( "a jdbcUrl change must invalidate the cached Driver",
                    DriverManagerDataSourceInternals.cachedDriver( dmds ) );

        lastServedBy = null;
        dmds.getConnection().close();

        assertEquals( "and the new URL must be resolved afresh",
                      SecondaryDriver.class.getName(), lastServedBy );
    }
}
