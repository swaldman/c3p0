/*
 * Distributed as part of c3p0 v.0.8.4-test1
 *
 * Copyright (C) 2003 Machinery For Change, Inc.
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
import java.sql.DriverManager;
import java.sql.SQLException;
import javax.sql.DataSource;
import com.mchange.v2.sql.SqlUtils;
import com.mchange.v2.c3p0.impl.DriverManagerDataSourceBase;

public final class DriverManagerDataSource extends DriverManagerDataSourceBase implements DataSource
{
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

    public Connection getConnection() throws SQLException
    { return DriverManager.getConnection( jdbcUrl, properties ); }

    public Connection getConnection(String username, String password) throws SQLException
    { return DriverManager.getConnection( jdbcUrl, overrideProps(username, password) );  }

    public PrintWriter getLogWriter() throws SQLException
    { return DriverManager.getLogWriter(); }

    public void setLogWriter(PrintWriter out) throws SQLException
    { DriverManager.setLogWriter( out ); }

    public int getLoginTimeout() throws SQLException
    { return DriverManager.getLoginTimeout(); }

    public void setLoginTimeout(int seconds) throws SQLException
    { DriverManager.setLoginTimeout( seconds ); }

    //"virtual properties"
    public void setUser(String user)
    {
	if (user != null)
	    properties.put( SqlUtils.DRIVER_MANAGER_USER_PROPERTY, user ); 
	else
	    properties.remove( SqlUtils.DRIVER_MANAGER_USER_PROPERTY );
    }

    public String getUser()
    { return properties.getProperty( SqlUtils.DRIVER_MANAGER_USER_PROPERTY ); }

    public void setPassword(String password)
    {
	if (password != null)
	    properties.put( SqlUtils.DRIVER_MANAGER_PASSWORD_PROPERTY, password ); 
	else
	    properties.remove( SqlUtils.DRIVER_MANAGER_PASSWORD_PROPERTY );
    }

    public String getPassword()
    { return properties.getProperty( SqlUtils.DRIVER_MANAGER_PASSWORD_PROPERTY ); }

    private final Properties overrideProps(String user, String password)
    {
	Properties overriding = (Properties) properties.clone(); //we are relying on a defensive clone in our base class!!!
	overriding.put(SqlUtils.DRIVER_MANAGER_USER_PROPERTY, user);
	overriding.put(SqlUtils.DRIVER_MANAGER_PASSWORD_PROPERTY, password);
	return overriding;
    }
}
