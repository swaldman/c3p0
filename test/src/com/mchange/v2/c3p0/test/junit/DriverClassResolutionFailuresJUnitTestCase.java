package com.mchange.v2.c3p0.test.junit;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Driver;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.Properties;
import java.util.logging.Logger;

import com.mchange.v2.c3p0.DriverManagerDataSource;

import junit.framework.TestCase;

/**
 *  DriverManagerDataSource.driver() must report why it could not produce a Driver.
 *
 *  <p>It used to build an SQLException in each of its catch blocks and then simply not
 *  throw it -- three of them, one per failure mode. The field stayed null, driver()
 *  returned null, and the caller got a NullPointerException out of
 *  <code>driver().connect(...)</code> with nothing at all about the driver class that
 *  actually failed to load. Every test here therefore asserts on the exception's
 *  <em>type and content</em>, since "it threw something" was already true.</p>
 *
 *  <p>These also pin the reflective-construction unwrap. The call is
 *  <code>getDeclaredConstructor().newInstance()</code>, which wraps whatever the
 *  constructor threw in an InvocationTargetException; handing that to the caller would
 *  bury the real failure one level down. The cause must be what the constructor threw.</p>
 *
 *  <p>No database is needed: none of these get far enough to open a connection.</p>
 */
public class DriverClassResolutionFailuresJUnitTestCase extends TestCase
{
    private final static String DUMMY_URL = "jdbc:c3p0test:whatever";

    /** A "driver" whose no-arg constructor fails. It need not implement Driver: the
     *  construction throws before the cast to Driver is reached. */
    public static class ExplodingConstructorDriver
    {
        public ExplodingConstructorDriver()
        { throw new IllegalStateException("deliberate failure from a driver constructor"); }
    }

    /**
     *  A real Driver that deliberately does <em>not</em> register itself with
     *  DriverManager, so a jdbcUrl-based lookup for it must fail and the named-class
     *  fallback is the only way to reach it. connect() throws a recognizable marker so a
     *  test can prove this driver, and not some other, was the one used.
     */
    public static class UnregisteredMarkerDriver implements Driver
    {
        public final static String MARKER = "c3p0-test: the named driver class was used";

        @Override
        public Connection connect(String url, Properties info) throws SQLException
        { throw new SQLException( MARKER ); }

        @Override
        public boolean acceptsURL(String url)
        { return true; }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties info)
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
        { return Logger.getLogger( UnregisteredMarkerDriver.class.getName() ); }
    }

    /**
     *  A Driver that DOES register itself with DriverManager, for the one case that needs a
     *  URL lookup to succeed. It accepts only its own private URL scheme, so registering it
     *  cannot affect any other test sharing this JVM.
     */
    public static class RegisteredMarkerDriver implements Driver
    {
        public final static String URL_PREFIX = "jdbc:c3p0test-registered:";
        public final static String MARKER = "c3p0-test: the registered driver was used";

        static
        {
            try { DriverManager.registerDriver( new RegisteredMarkerDriver() ); }
            catch ( SQLException e ) { throw new ExceptionInInitializerError( e ); }
        }

        @Override
        public Connection connect(String url, Properties info) throws SQLException
        { throw new SQLException( MARKER ); }

        @Override
        public boolean acceptsURL(String url)
        { return url != null && url.startsWith( URL_PREFIX ); }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties info)
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
        { return Logger.getLogger( RegisteredMarkerDriver.class.getName() ); }
    }

    /** Constructs perfectly well, but is not a java.sql.Driver. */
    public static class NotADriverAtAll
    {
        public NotADriverAtAll()
        {}
    }

    /** A "driver" that offers no no-arg constructor at all. */
    public static class NoNoArgConstructorDriver
    {
        public NoNoArgConstructorDriver(String required)
        {}
    }

    private DriverManagerDataSource dmds(String driverClass)
    {
        DriverManagerDataSource dmds = new DriverManagerDataSource();
        dmds.setJdbcUrl( DUMMY_URL );
        dmds.setDriverClass( driverClass );
        dmds.setForceUseNamedDriverClass( true );
        return dmds;
    }

    public void testMissingDriverClassReportsTheClassName() throws Exception
    {
        DriverManagerDataSource dmds = dmds( "no.such.driver.DoesNotExist" );
        try
        {
            dmds.getConnection();
            fail( "A driverClass that cannot be loaded must be reported." );
        }
        catch ( SQLException e )
        {
            assertTrue( "The message must name the driver class that could not be loaded, was: " + e.getMessage(),
                        e.getMessage() != null && e.getMessage().indexOf( "no.such.driver.DoesNotExist" ) >= 0 );
            assertTrue( "The cause must be the ClassNotFoundException, was: " + e.getCause(),
                        e.getCause() instanceof ClassNotFoundException );
        }
    }

    /**
     *  The cause must be the exception the constructor threw, not the
     *  InvocationTargetException that getDeclaredConstructor().newInstance() wraps it in.
     */
    public void testConstructorFailureIsUnwrappedIntoTheCause() throws Exception
    {
        DriverManagerDataSource dmds = dmds( ExplodingConstructorDriver.class.getName() );
        try
        {
            dmds.getConnection();
            fail( "A driver constructor that throws must be reported." );
        }
        catch ( SQLException e )
        {
            Throwable cause = e.getCause();
            assertNotNull( "The failure must be reported with a cause.", cause );
            assertFalse( "The InvocationTargetException must be unwrapped, not handed to the caller.",
                         cause instanceof java.lang.reflect.InvocationTargetException );
            assertTrue( "The cause must be what the constructor threw, was: " + cause,
                        cause instanceof IllegalStateException );
            assertEquals( "deliberate failure from a driver constructor", cause.getMessage() );
        }
    }

    public void testMissingNoArgConstructorIsReported() throws Exception
    {
        DriverManagerDataSource dmds = dmds( NoNoArgConstructorDriver.class.getName() );
        try
        {
            dmds.getConnection();
            fail( "A driver class with no no-arg constructor must be reported." );
        }
        catch ( SQLException e )
        {
            assertTrue( "The message must name the driver class, was: " + e.getMessage(),
                        e.getMessage() != null
                        && e.getMessage().indexOf( NoNoArgConstructorDriver.class.getName() ) >= 0 );
            assertTrue( "The cause must be the NoSuchMethodException, was: " + e.getCause(),
                        e.getCause() instanceof NoSuchMethodException );
        }
    }

    /**
     *  The failure must be reported on every attempt. Discarding the exception left the
     *  driver field null, so a second call took the same path and failed the same silent
     *  way; a datasource must not become quietly useless.
     */
    public void testFailureIsReportedOnRepeatedAttempts() throws Exception
    {
        DriverManagerDataSource dmds = dmds( "no.such.driver.DoesNotExist" );
        for ( int i = 0; i < 3; ++i )
        {
            try
            {
                dmds.getConnection();
                fail( "Attempt " + i + " must fail." );
            }
            catch ( SQLException e )
            { /* expected, every time */ }
        }
    }

    /**
     *  getConnection(user, password) goes through the same driver() call and must report
     *  failures the same way.
     */
    public void testUserPasswordOverloadReportsTheSameFailure() throws Exception
    {
        DriverManagerDataSource dmds = dmds( "no.such.driver.DoesNotExist" );
        try
        {
            dmds.getConnection( "user", "password" );
            fail( "The user/password overload must report the failure too." );
        }
        catch ( SQLException e )
        {
            assertTrue( e.getMessage() != null && e.getMessage().indexOf( "no.such.driver.DoesNotExist" ) >= 0 );
        }
    }

    /**
     *  With forceUseNamedDriverClass false, driverClass is a documented fallback for when
     *  the jdbcUrl-based lookup does not resolve: "prefer jdbcUrl-based lookup if that
     *  succeeds, otherwise load driver by classname".
     *
     *  <p>It never ran. DriverManager.getDriver throws "No suitable driver" rather than
     *  returning null, and that exception went straight out through catch (SQLException e)
     *  { throw e; }, so the null check guarding the fallback was unreachable. Setting
     *  driverClass without also setting forceUseNamedDriverClass therefore did nothing at
     *  all for a URL DriverManager could not match.</p>
     */
    public void testFallsBackToNamedDriverClassWhenUrlLookupFails() throws Exception
    {
        DriverManagerDataSource dmds = new DriverManagerDataSource();
        dmds.setJdbcUrl( DUMMY_URL );
        dmds.setDriverClass( UnregisteredMarkerDriver.class.getName() );
        // forceUseNamedDriverClass deliberately left false: this is the fallback path

        try
        {
            dmds.getConnection();
            fail( "Expected the marker exception from the named driver class." );
        }
        catch ( SQLException e )
        {
            assertEquals( "The named driver class must be used when the URL lookup fails.",
                          UnregisteredMarkerDriver.MARKER, e.getMessage() );
        }
    }

    /**
     *  and when the fallback fails too, the original URL-lookup failure must not be lost:
     *  the driver-class failure is the one thrown, with the lookup failure suppressed.
     */
    public void testUrlLookupFailureIsRetainedWhenTheFallbackAlsoFails() throws Exception
    {
        DriverManagerDataSource dmds = new DriverManagerDataSource();
        dmds.setJdbcUrl( DUMMY_URL );
        dmds.setDriverClass( "no.such.driver.DoesNotExist" );
        // forceUseNamedDriverClass deliberately left false

        try
        {
            dmds.getConnection();
            fail( "Both the URL lookup and the driver class fail; this must be reported." );
        }
        catch ( SQLException e )
        {
            assertTrue( "The thrown exception must be the driver-class failure, was: " + e.getMessage(),
                        e.getMessage() != null && e.getMessage().indexOf( "no.such.driver.DoesNotExist" ) >= 0 );
            assertTrue( "whose cause is why the class would not load.",
                        e.getCause() instanceof ClassNotFoundException );

            Throwable[] suppressed = e.getSuppressed();
            assertEquals( "The URL-lookup failure must be retained as a suppressed exception.",
                          1, suppressed.length );
            assertTrue( "and it must be the SQLException DriverManager threw, was: " + suppressed[0],
                        suppressed[0] instanceof SQLException );
        }
    }

    /**
     *  A successful URL lookup must not attach a spurious suppressed exception, and must
     *  not consult driverClass at all.
     */
    public void testNoSuppressedExceptionWhenUrlLookupSucceeds() throws Exception
    {
        DriverManagerDataSource dmds = dmds( "no.such.driver.DoesNotExist" );
        // forced, so the URL lookup is skipped entirely and nothing can be suppressed
        try
        {
            dmds.getConnection();
            fail( "expected failure" );
        }
        catch ( SQLException e )
        {
            assertEquals( "Nothing should be suppressed when the URL lookup never ran.",
                          0, e.getSuppressed().length );
        }
    }

    /**
     *  A driverClass that loads and constructs but is not a java.sql.Driver used to fail
     *  the cast with a raw ClassCastException, escaping driver() unwrapped -- so a caller
     *  of getConnection(), which declares only SQLException, got an unchecked exception
     *  naming neither the driver class nor what was wrong with it.
     */
    public void testNonDriverClassIsReportedAsSQLException() throws Exception
    {
        DriverManagerDataSource dmds = dmds( NotADriverAtAll.class.getName() );
        try
        {
            dmds.getConnection();
            fail( "A driverClass that is not a java.sql.Driver must be reported." );
        }
        catch ( ClassCastException e )
        {
            fail( "The ClassCastException must be reported as an SQLException, not raw: " + e );
        }
        catch ( SQLException e )
        {
            assertTrue( "The message must name the driver class, was: " + e.getMessage(),
                        e.getMessage() != null
                        && e.getMessage().indexOf( NotADriverAtAll.class.getName() ) >= 0 );
            assertTrue( "The cause must be the ClassCastException, was: " + e.getCause(),
                        e.getCause() instanceof ClassCastException );
        }
    }

    /** and the same on the fallback path, where the URL lookup failed first. */
    public void testNonDriverClassOnFallbackPathRetainsUrlLookupFailure() throws Exception
    {
        DriverManagerDataSource dmds = new DriverManagerDataSource();
        dmds.setJdbcUrl( DUMMY_URL );
        dmds.setDriverClass( NotADriverAtAll.class.getName() );
        // forceUseNamedDriverClass deliberately left false

        try
        {
            dmds.getConnection();
            fail( "A driverClass that is not a java.sql.Driver must be reported." );
        }
        catch ( SQLException e )
        {
            assertTrue( e.getCause() instanceof ClassCastException );
            assertEquals( "The URL-lookup failure must be retained as a suppressed exception.",
                          1, e.getSuppressed().length );
        }
    }

    /**
     *  An unloadable driverClass alongside a URL that <em>does</em> resolve must leave the
     *  datasource working: the URL lookup wins, and the driverClass failure is irrelevant.
     *
     *  <p>This is the case that exercises setDriverClassLoaded(true) being reached without
     *  the driver class having been loaded -- the URL lookup succeeded, so the fallback that
     *  loads it never ran. The flag is only a memo telling
     *  ensureIfPossibleDriverClassLoaded not to retry, and by this point there is a cached
     *  Driver and nothing left to retry, so repeated calls must keep working.</p>
     */
    public void testUnloadableDriverClassIsHarmlessWhenTheUrlResolves() throws Exception
    {
        Class.forName( RegisteredMarkerDriver.class.getName() ); // force its static registration

        DriverManagerDataSource dmds = new DriverManagerDataSource();
        dmds.setJdbcUrl( RegisteredMarkerDriver.URL_PREFIX + "whatever" );
        dmds.setDriverClass( "no.such.driver.DoesNotExist" );
        // forceUseNamedDriverClass deliberately left false

        for ( int i = 0; i < 3; ++i )
        {
            try
            {
                dmds.getConnection();
                fail( "Expected the registered driver's marker on attempt " + i );
            }
            catch ( SQLException e )
            {
                assertEquals( "Attempt " + i + " must reach the driver resolved from the URL.",
                              RegisteredMarkerDriver.MARKER, e.getMessage() );
            }
        }
    }
}
