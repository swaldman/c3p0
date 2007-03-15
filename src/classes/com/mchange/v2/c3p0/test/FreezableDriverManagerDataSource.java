/*
 * Distributed as part of c3p0 v.0.9.1.1
 *
 * Copyright (C) 2005 Machinery For Change, Inc.
 *
 * Author: Steve Waldman <swaldman@mchange.com>
 *
 * This library is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License version 2.1, as 
 * published by the Free Software Foundation.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this software; see the file LICENSE.  If not, write to the
 * Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 * Boston, MA 02111-1307, USA.
 */


package com.mchange.v2.c3p0.test;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
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


// this is a copy-paste hack job to do a quick simulation of how c3p0 responds when
// getConnection() freezes but does not fail.
public final class FreezableDriverManagerDataSource extends DriverManagerDataSourceBase implements DataSource
{
    final static MLogger logger = MLog.getLogger( FreezableDriverManagerDataSource.class );
    
    final static File FREEZE_FILE = new File("/tmp/c3p0_freeze_file");

    //MT: protected by this' lock
    Driver driver;
    
    //MT: protected by this' lock
    boolean driver_class_loaded = false;

    public FreezableDriverManagerDataSource()
    { this( true ); }

    public FreezableDriverManagerDataSource(boolean autoregister)
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
    
    private void waitNoFreezeFile() throws SQLException
    {
        try
        {
            while (true)
            {
                if (! FREEZE_FILE.exists())
                    break;
                Thread.sleep(1000);
            }
        }
        catch (InterruptedException e)
        {
            logger.log(MLevel.WARNING, "Frozen cxn acquire interrupted.", e);
            throw new SQLException( e.toString() );
        }
    }

    private void setUpPropertyListeners()
    {
        PropertyChangeListener driverClassListener = new PropertyChangeListener()
        {
            public void propertyChange( PropertyChangeEvent evt )
            {
                Object val = evt.getNewValue();
                if ( "driverClass".equals( evt.getPropertyName() ) )
                    setDriverClassLoaded( false );
            }
        };
        this.addPropertyChangeListener( driverClassListener );
    }
    
    private synchronized boolean isDriverClassLoaded()
    { return driver_class_loaded; }
    
    private synchronized void setDriverClassLoaded(boolean dcl)
    { this.driver_class_loaded = dcl; }
    
    private void ensureDriverLoaded() throws SQLException
    {
        try
        {
            if (! isDriverClassLoaded())
            {
                if (driverClass != null)
                    Class.forName( driverClass );
                setDriverClassLoaded( true );
            }
        }
        catch (ClassNotFoundException e)
        {
            if (logger.isLoggable(MLevel.WARNING))
                logger.log(MLevel.WARNING, "Could not load driverClass " + driverClass, e);
        }
    }

    // should NOT be sync'ed -- driver() is sync'ed and that's enough
    // sync'ing the method creates the danger that one freeze on connect
    // blocks access to the entire DataSource

    public Connection getConnection() throws SQLException
    { 
        ensureDriverLoaded();

        waitNoFreezeFile();

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
        
        waitNoFreezeFile();

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
        //System.err.println( "driver() <-- " + this );
        if (driver == null)
            driver = DriverManager.getDriver( jdbcUrl );
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
}
