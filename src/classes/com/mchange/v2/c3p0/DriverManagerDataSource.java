/*
 * Distributed as part of c3p0 v.0.8.5-pre9
 *
 * Copyright (C) 2004 Machinery For Change, Inc.
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


package com.mchange.v2.c3p0;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyVetoException;
import java.beans.VetoableChangeListener;
import java.io.PrintWriter;
import java.util.Properties;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import javax.sql.DataSource;
import com.mchange.v2.sql.SqlUtils;
import com.mchange.v2.c3p0.impl.DriverManagerDataSourceBase;

public final class DriverManagerDataSource extends DriverManagerDataSourceBase implements DataSource
{
    Driver driver;

    {
	VetoableChangeListener registerDriverListener = new VetoableChangeListener()
	    {
		public void vetoableChange( PropertyChangeEvent evt ) throws PropertyVetoException
		{
		    Object val = evt.getNewValue();
		    try
			{
			    if ( "driverClass".equals( evt.getPropertyName() ) )
				Class.forName( (String) val );
			}
		    catch ( ClassNotFoundException e )
			{
			    e.printStackTrace();
			    throw new PropertyVetoException("Could not locate driver class with name '" + val + "'.", evt);
			}
		}
	    };
	this.addVetoableChangeListener( registerDriverListener );
    }

    public synchronized Connection getConnection() throws SQLException
    { return driver().connect( jdbcUrl, properties ); }

    public synchronized Connection getConnection(String username, String password) throws SQLException
    { return driver().connect( jdbcUrl, overrideProps(username, password) );  }

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
	super.setJdbcUrl( jdbcUrl );
	this.driver = null;
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
    { return properties.getProperty( SqlUtils.DRIVER_MANAGER_USER_PROPERTY ); }

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

    private Driver driver() throws SQLException
    {
	if (driver == null)
	    driver = DriverManager.getDriver( jdbcUrl );
	return driver;
    }

    private static boolean eqOrBothNull( Object a, Object b )
    { return (a == b || (a != null && a.equals(b))); }
}
