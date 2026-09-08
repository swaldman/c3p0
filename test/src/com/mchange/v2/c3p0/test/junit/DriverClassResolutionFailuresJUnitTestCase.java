package com.mchange.v2.c3p0.test.junit;

import java.sql.SQLException;

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
}
