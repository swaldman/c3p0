/*
 * Distributed as part of c3p0 v.0.9.5.2
 *
 * Copyright (C) 2015 Machinery For Change, Inc.
 *
 * Author: Steve Waldman <swaldman@mchange.com>
 *
 * This library is free software; you can redistribute it and/or modify
 * it under the terms of EITHER:
 *
 *     1) The GNU Lesser General Public License (LGPL), version 2.1, as 
 *        published by the Free Software Foundation
 *
 * OR
 *
 *     2) The Eclipse Public License (EPL), version 1.0
 *
 * You may choose which license to accept if you wish to redistribute
 * or modify this work. You may offer derivatives of this work
 * under the license you have chosen, or you may provide the same
 * choice of license which you have been offered here.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * You should have received copies of both LGPL v2.1 and EPL v1.0
 * along with this software; see the files LICENSE-EPL and LICENSE-LGPL.
 * If not, the text of these licenses are currently available at
 *
 * LGPL v2.1: http://www.gnu.org/licenses/old-licenses/lgpl-2.1.html
 *  EPL v1.0: http://www.eclipse.org/org/documents/epl-v10.php 
 * 
 */

package com.mchange.v2.c3p0;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.util.Properties;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import javax.sql.DataSource;
import com.mchange.v2.sql.SqlUtils;
import com.mchange.v2.log.MLevel;
import com.mchange.v2.log.MLog;
import com.mchange.v2.log.MLogger;
import com.mchange.v2.c3p0.cfg.C3P0Config;
import com.mchange.v2.c3p0.impl.DriverManagerDataSourceBase;

public final class DriverManagerDataSource extends DriverManagerDataSourceBase implements DataSource
{
    final static MLogger logger;

    static
    {
	logger = MLog.getLogger( DriverManagerDataSource.class );


	// some drivers are not robust to simultanous attempts to
	// load. if our driver will be preloaded by the DriverManager class,
	// get it over with before we try anything
	try { Class.forName( "java.sql.DriverManager" ); }
	catch ( Exception e )
	    { 
		String msg = "Could not load the DriverManager class?!?";
		if ( logger.isLoggable( MLevel.SEVERE ) )
		    logger.log( MLevel.SEVERE, msg );
		throw new InternalError( msg );
	    }
    }

    //MT: protected by this' lock
    Driver driver;
    
    //MT: protected by this' lock
    boolean driver_class_loaded = false;

    public DriverManagerDataSource()
    { this( true ); }

    public DriverManagerDataSource(boolean autoregister)
    {
        super( autoregister );

        setUpPropertyListeners();

        String user = C3P0Config.initializeStringPropertyVar("user", null);
        String password = C3P0Config.initializeStringPropertyVar("password", null);

        if (user != null)
            this.setUser( user );

        if (password != null)
            this.setPassword( password );
    }

    private void setUpPropertyListeners()
    {
        PropertyChangeListener driverClassListener = new PropertyChangeListener()
        {
            public void propertyChange( PropertyChangeEvent evt )
            {
                if ( "driverClass".equals( evt.getPropertyName() ) )
		{
		    synchronized (DriverManagerDataSource.this) 
		    {
			setDriverClassLoaded( false );

			// guard against setting to empty String or whitespace values. 
			// JMX clients sometimes (unfortunately) represent null properties as blank fields, then update them to empty or whitespace Strings.
			if ( driverClass != null && driverClass.trim().length() == 0 ) //an empty String or all whitespace name
			    driverClass = null;
		    }
		}
            }
        };
        this.addPropertyChangeListener( driverClassListener );
    }
    
    private synchronized boolean isDriverClassLoaded()
    { return driver_class_loaded; }
    
    private synchronized void setDriverClassLoaded(boolean dcl)
    { 
	this.driver_class_loaded = dcl; 
	if (! driver_class_loaded) clearDriver(); // if we are changing to a yet-unloaded Driver class, the existing driver must be stale
    }
    
    private synchronized void ensureDriverLoaded() throws SQLException
    {
        try
        {
            if (! isDriverClassLoaded())
            {
                if (driverClass != null)
                {
                    Class driverCls = loadDriver(driverClass);
                    if(!driverCls.getClassLoader().equals(this.getClass().getClassLoader()))
                    {
                        logger.warning( "Driver '" + driverClass + "' was loaded with a classloader different than C3P0's. Forcing lookup by name sonce the DriverManager won't fin it from C3P0's classloader." );
                        logger.finest("Driver '" + driverClass + "' classloader: " + driverCls.getClassLoader().toString());
                        logger.finest("C3P0 classloader: " + this.getClass().getClassLoader().toString());
                        setForceUseNamedDriverClass(true);
                    }
                }
                setDriverClassLoaded( true );
            }
        }
        catch (ClassNotFoundException e)
        {
            if (logger.isLoggable(MLevel.WARNING))
                logger.log(MLevel.WARNING, "Could not load driverClass " + driverClass, e);
        }
    }

    private Class loadDriver(String driverClass) throws ClassNotFoundException
    {
        try
        {
            return Class.forName(driverClass);
        }
        catch (ClassNotFoundException e)
        {
            if (Thread.currentThread().getContextClassLoader() != null)
            {
                return Class.forName(driverClass, true, Thread.currentThread().getContextClassLoader());
            }
            else
            {
                throw e;
            }
        }
    }

    // should NOT be sync'ed -- driver() is sync'ed and that's enough
    // sync'ing the method creates the danger that one freeze on connect
    // blocks access to the entire DataSource
    public Connection getConnection() throws SQLException
    { 
        ensureDriverLoaded();

        Connection out = driver().connect( jdbcUrl, properties ); 
        if (out == null)
            throw new SQLException("Apparently, jdbc URL '" + jdbcUrl + "' is not valid for the underlying " +
                            "driver [" + driver() + "].");
        return out;
    }

    // should NOT be sync'ed -- driver() is sync'ed and that's enough
    // sync'ing the method creates the danger that one freeze on connect
    // blocks access to the entire DataSource
    public Connection getConnection(String username, String password) throws SQLException
    { 
        ensureDriverLoaded();

        Connection out = driver().connect( jdbcUrl, overrideProps(username, password) );  
        if (out == null)
            throw new SQLException("Apparently, jdbc URL '" + jdbcUrl + "' is not valid for the underlying " +
                            "driver [" + driver() + "].");
        return out;
    }

    public PrintWriter getLogWriter() throws SQLException
    { return DriverManager.getLogWriter(); }

    public void setLogWriter(PrintWriter out) throws SQLException
    { DriverManager.setLogWriter( out ); }

    public int getLoginTimeout() throws SQLException
    { return DriverManager.getLoginTimeout(); }

    public void setLoginTimeout(int seconds) throws SQLException
    { DriverManager.setLoginTimeout( seconds ); }

    //overrides
    public synchronized void setJdbcUrl(String jdbcUrl)
    {
        //System.err.println( "setJdbcUrl( " + jdbcUrl + " )");
        //new Exception("DEBUG STACK TRACE").printStackTrace();
        super.setJdbcUrl( jdbcUrl );
        clearDriver();
    }

    //"virtual properties"
    public synchronized void setUser(String user)
    {
        String oldUser = this.getUser();
        if (! eqOrBothNull( user, oldUser ))
        {
            if (user != null)
                properties.put( SqlUtils.DRIVER_MANAGER_USER_PROPERTY, user ); 
            else
                properties.remove( SqlUtils.DRIVER_MANAGER_USER_PROPERTY );

            pcs.firePropertyChange("user", oldUser, user);
        }
    }

    public synchronized String getUser()
    {
//      System.err.println("getUser() -- DriverManagerDataSource@" + System.identityHashCode( this ) + 
//      " using Properties@" + System.identityHashCode( properties ));
//      new Exception("STACK TRACE DUMP").printStackTrace();
        return properties.getProperty( SqlUtils.DRIVER_MANAGER_USER_PROPERTY ); 
    }

    public synchronized void setPassword(String password)
    {
        String oldPass = this.getPassword();
        if (! eqOrBothNull( password, oldPass ))
        {
            if (password != null)
                properties.put( SqlUtils.DRIVER_MANAGER_PASSWORD_PROPERTY, password ); 
            else
                properties.remove( SqlUtils.DRIVER_MANAGER_PASSWORD_PROPERTY );

            pcs.firePropertyChange("password", oldPass, password);
        }
    }

    public synchronized String getPassword()
    { return properties.getProperty( SqlUtils.DRIVER_MANAGER_PASSWORD_PROPERTY ); }

    private final Properties overrideProps(String user, String password)
    {
        Properties overriding = (Properties) properties.clone(); //we are relying on a defensive clone in our base class!!!

        if (user != null)
            overriding.put(SqlUtils.DRIVER_MANAGER_USER_PROPERTY, user);
        else
            overriding.remove(SqlUtils.DRIVER_MANAGER_USER_PROPERTY);

        if (password != null)
            overriding.put(SqlUtils.DRIVER_MANAGER_PASSWORD_PROPERTY, password);
        else
            overriding.remove(SqlUtils.DRIVER_MANAGER_PASSWORD_PROPERTY);

        return overriding;
    }

    private synchronized Driver driver() throws SQLException
    {
 	//To simulate an unreliable DataSource...
   	//double d = Math.random() * 10;
   	//if ( d > 1 )
   	//    throw new SQLException(this.getClass().getName() + " TEST of unreliable Connection. If you're not testing, you shouldn't be seeing this!");

        //System.err.println( "driver() <-- " + this );
        if (driver == null)
	{
	    if (driverClass != null && forceUseNamedDriverClass)
	    {
		if ( Debug.DEBUG && logger.isLoggable( MLevel.FINER ) )
		    logger.finer( "Circumventing DriverManager and instantiating driver class '" + driverClass + 
				  "' directly. (forceUseNamedDriverClass = " + forceUseNamedDriverClass + ")" );

		try
		{
		    driver = (Driver) loadDriver(driverClass).newInstance();
		    this.setDriverClassLoaded( true );
		}
		catch (Exception e)
		    { SqlUtils.toSQLException("Cannot instantiate specified JDBC driver. Exception while initializing named, forced-to-use driver class'" + driverClass +"'", e); }
	    }
	    else
		driver = DriverManager.getDriver( jdbcUrl );
        }
        return driver;
    }

    private synchronized void clearDriver()
    { driver = null; }

    private static boolean eqOrBothNull( Object a, Object b )
    { return (a == b || (a != null && a.equals(b))); }

    // serialization stuff -- set up bound/constrained property event handlers on deserialization
    private static final long serialVersionUID = 1;
    private static final short VERSION = 0x0001;

    private void writeObject( ObjectOutputStream oos ) throws IOException
    {
        oos.writeShort( VERSION );
    }

    private void readObject( ObjectInputStream ois ) throws IOException, ClassNotFoundException
    {
        short version = ois.readShort();
        switch (version)
        {
        case VERSION:
            setUpPropertyListeners();
            break;
        default:
            throw new IOException("Unsupported Serialized Version: " + version);
        }
    }

    // JDBC4 Wrapper stuff
    private boolean isWrapperForThis(Class<?> iface)
    { return iface.isAssignableFrom( this.getClass() ); }

    public boolean isWrapperFor(Class<?> iface) throws SQLException
    {
	return isWrapperForThis( iface );
    }

    public <T> T unwrap(Class<T> iface) throws SQLException
    {
	if ( this.isWrapperForThis( iface ) )
	    return (T) this;
	else
	    throw new SQLException(this + " is not a wrapper for or implementation of " + iface.getName());
    }
}
