/*
 * Distributed as part of c3p0 v.0.9.1-pre6
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


package com.mchange.v2.c3p0;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyVetoException;
import java.beans.VetoableChangeListener;
import java.beans.PropertyChangeListener;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.Map;
import java.sql.*;
import javax.sql.*;
import com.mchange.v2.c3p0.cfg.C3P0Config;
import com.mchange.v2.c3p0.impl.*;
import com.mchange.v2.log.*;

// MT: Most methods are left unsynchronized, because getNestedDataSource() is synchronized, and for most methods, that's
//     the critical part. Previous oversynchronization led to hangs, when getting the Connection for one Thread happened
//     to hang, blocking access to getPooledConnection() for all Threads.
public final class WrapperConnectionPoolDataSource extends WrapperConnectionPoolDataSourceBase implements ConnectionPoolDataSource
{
    final static MLogger logger = MLog.getLogger( WrapperConnectionPoolDataSource.class );

    //MT: protected by this' lock
    ConnectionTester connectionTester = C3P0ImplUtils.defaultConnectionTester();
    Map              userOverrides;

    public WrapperConnectionPoolDataSource()
    {
	VetoableChangeListener setConnectionTesterListener = new VetoableChangeListener()
	    {
		// always called within synchronized mutators of the parent class... needn't explicitly sync here
		public void vetoableChange( PropertyChangeEvent evt ) throws PropertyVetoException
		{
		    String propName = evt.getPropertyName();
		    Object val = evt.getNewValue();

		    if ( "connectionTesterClassName".equals( propName ) )
			{
			    try
				{ recreateConnectionTester( (String) val ); }
			    catch ( Exception e )
				{
				    //e.printStackTrace();
				    if ( logger.isLoggable( MLevel.WARNING ) )
					logger.log( MLevel.WARNING, "Failed to create ConnectionTester of class " + val, e );
				    
				    throw new PropertyVetoException("Could not instantiate connection tester class with name '" + val + "'.", evt);
				}
			}
		    else if ("userOverridesAsString".equals( propName ))
			{
			    try
				{ WrapperConnectionPoolDataSource.this.userOverrides = C3P0ImplUtils.parseUserOverridesAsString( (String) val ); }
			    catch (Exception e)
				{
				    if ( logger.isLoggable( MLevel.WARNING ) )
					logger.log( MLevel.WARNING, "Failed to parse stringified userOverrides. " + val, e );
				    
				    throw new PropertyVetoException("Failed to parse stringified userOverrides. " + val, evt);
				}
			}
		}
	    };
	this.addVetoableChangeListener( setConnectionTesterListener );

	//set up initial value of userOverrides
	try
	    { this.userOverrides = C3P0ImplUtils.parseUserOverridesAsString( this.getUserOverridesAsString() ); }
	catch (Exception e)
	    {
		if ( logger.isLoggable( MLevel.WARNING ) )
		    logger.log( MLevel.WARNING, "Failed to parse stringified userOverrides. " + this.getUserOverridesAsString(), e );
	    }

	C3P0Registry.register( this );
    }

    public WrapperConnectionPoolDataSource( String configName )
    {
	this();
	
	try
	    {
		if (configName != null)
		    C3P0Config.bindNamedConfigToBean( this, configName ); 
	    }
	catch (Exception e)
	    {
		if (logger.isLoggable( MLevel.WARNING ))
		    logger.log( MLevel.WARNING, 
				"Error binding WrapperConnectionPoolDataSource to named-config '" + configName + 
				"'. Some default-config values may be used.", 
				e);
	    }
    }

    // implementation of javax.sql.ConnectionPoolDataSource
    //
    // getNestedDataSource() is sync'ed, which is enough. Unsync'ed this method,
    // because when sync'ed a hang in retrieving one connection blocks all
    //
    public PooledConnection getPooledConnection()
	throws SQLException
    { 
	DataSource nds = getNestedDataSource();
	if (nds == null)
	    throw new SQLException( "No standard DataSource has been set beneath this wrapper! [ nestedDataSource == null ]");
	Connection conn = nds.getConnection();
	if (conn == null)
	    throw new SQLException("An (unpooled) DataSource returned null from its getConnection() method! " +
				   "DataSource: " + getNestedDataSource());
	if ( this.isUsesTraditionalReflectiveProxies() )
	    {
		//return new C3P0PooledConnection( new com.mchange.v2.c3p0.test.CloseReportingConnection( conn ), 
		return new C3P0PooledConnection( conn, 
						 connectionTester,
						 this.isAutoCommitOnClose(), 
						 this.isForceIgnoreUnresolvedTransactions() ); 
	    }
	else
	    {
		return new NewPooledConnection( conn, 
						connectionTester,
						this.isAutoCommitOnClose(), 
						this.isForceIgnoreUnresolvedTransactions() ); 
	    }
    } 
 
    // getNestedDataSource() is sync'ed, which is enough. Unsync'ed this method,
    // because when sync'ed a hang in retrieving one connection blocks all
    //
    public PooledConnection getPooledConnection(String user, String password)
	throws SQLException
    { 
	DataSource nds = getNestedDataSource();
	if (nds == null)
	    throw new SQLException( "No standard DataSource has been set beneath this wrapper! [ nestedDataSource == null ]");
	Connection conn = nds.getConnection(user, password);
	if (conn == null)
	    throw new SQLException("An (unpooled) DataSource returned null from its getConnection() method! " +
				   "DataSource: " + getNestedDataSource());
	if ( this.isUsesTraditionalReflectiveProxies() )
	    {
		//return new C3P0PooledConnection( new com.mchange.v2.c3p0.test.CloseReportingConnection( conn ), 
		return new C3P0PooledConnection( conn,
						 connectionTester,
						 this.isAutoCommitOnClose(), 
						 this.isForceIgnoreUnresolvedTransactions() ); 
	    }
	else
	    {
		return new NewPooledConnection( conn, 
						connectionTester,
						this.isAutoCommitOnClose(), 
						this.isForceIgnoreUnresolvedTransactions() ); 
	    }
    }
 
    public PrintWriter getLogWriter()
	throws SQLException
    { return getNestedDataSource().getLogWriter(); }

    public void setLogWriter(PrintWriter out)
	throws SQLException
    { getNestedDataSource().setLogWriter( out ); }

    public void setLoginTimeout(int seconds)
	throws SQLException
    { getNestedDataSource().setLoginTimeout( seconds ); }

    public int getLoginTimeout()
	throws SQLException
    { return getNestedDataSource().getLoginTimeout(); }

    //"virtual properties"

    public String getUser()
    { 
	try { return C3P0ImplUtils.findAuth( this.getNestedDataSource() ).getUser(); }
	catch (SQLException e)
	    {
		//e.printStackTrace();
		if ( logger.isLoggable( MLevel.WARNING ) )
		    logger.log( MLevel.WARNING, 
				"An Exception occurred while trying to find the 'user' property from our nested DataSource." +
				" Defaulting to no specified username.", e );
		return null; 
	    }
    }

    public String getPassword()
    { 
	try { return C3P0ImplUtils.findAuth( this.getNestedDataSource() ).getPassword(); }
	catch (SQLException e)
	    { 
		//e.printStackTrace();
		if ( logger.isLoggable( MLevel.WARNING ) )
		    logger.log( MLevel.WARNING, "An Exception occurred while trying to find the 'password' property from our nested DataSource." + 
				" Defaulting to no specified password.", e );
		return null; 
	    }
    }

    public Map getUserOverrides()
    { return userOverrides; }

    public String toString()
    {
	StringBuffer sb = new StringBuffer();
	sb.append( super.toString() );

// 	if (userOverrides != null)
// 	    sb.append("; userOverrides: " + userOverrides.toString());

	return sb.toString();
    }

    protected String extraToStringInfo()
    {
	if (userOverrides != null)
	    return "; userOverrides: " + userOverrides.toString();
	else
	    return null;
    }

    //other code
    private synchronized void recreateConnectionTester(String className) throws Exception
    {
	if (className != null)
	    {
		ConnectionTester ct = (ConnectionTester) Class.forName( className ).newInstance();
		this.connectionTester = ct;
	    }
	else
	    this.connectionTester = C3P0ImplUtils.defaultConnectionTester();
    }
}
