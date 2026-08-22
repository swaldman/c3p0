package com.mchange.v2.c3p0.test.fakedriver;

import java.sql.*;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * An in-process JDBC driver that needs no database, so that statement-cache concurrency tests
 * can run anywhere, at whatever rate the CPU allows, with deliberate latency and fault injection.
 *
 * URLs look like <code>jdbc:c3p0fake:&lt;config-name&gt;</code>, where the config name selects a
 * {@link FakeDriverConfig} previously registered with
 * {@link FakeDriverConfig#register(String,long)}.
 */
public final class FakeDriver implements Driver
{
    public final static String URL_PREFIX = "jdbc:c3p0fake:";

    public final static String DRIVER_CLASS_NAME = FakeDriver.class.getName();

    static
    {
        try
        { DriverManager.registerDriver( new FakeDriver() ); }
        catch ( SQLException e )
        { throw new ExceptionInInitializerError( e ); }
    }

    /** Ensures the driver is registered with DriverManager. */
    public static void ensureRegistered()
    { /* the static initializer above has run by the time we get here */ }

    private static String configName( String url )
    { return url.substring( URL_PREFIX.length() ); }

    public boolean acceptsURL( String url )
    { return url != null && url.startsWith( URL_PREFIX ); }

    public Connection connect( String url, Properties info ) throws SQLException
    {
        if (! acceptsURL( url ) )
            return null; // per spec, so DriverManager tries other drivers

        String name = configName( url );
        FakeDriverConfig config = FakeDriverConfig.lookup( name );
        if ( config == null )
            throw new SQLException("No FakeDriverConfig is registered under the name '" + name + "'. " +
                                   "Call FakeDriverConfig.register( name, seed ) before opening Connections.");
        return FakeConnection.create( config );
    }

    public int getMajorVersion()
    { return 1; }

    public int getMinorVersion()
    { return 0; }

    public Logger getParentLogger() throws SQLFeatureNotSupportedException
    { throw new SQLFeatureNotSupportedException(); }

    public DriverPropertyInfo[] getPropertyInfo( String url, Properties info )
    { return new DriverPropertyInfo[0]; }

    public boolean jdbcCompliant()
    { return false; }
}
