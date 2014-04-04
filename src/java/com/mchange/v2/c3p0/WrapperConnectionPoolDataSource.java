/*
 * Distributed as part of c3p0 0.9.5-pre8
 *
 * Copyright (C) 2014 Machinery For Change, Inc.
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
import java.beans.PropertyVetoException;
import java.beans.VetoableChangeListener;
import java.beans.PropertyChangeListener;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.Map;
import java.sql.*;
import javax.sql.*;
import com.mchange.v2.c3p0.cfg.C3P0Config;
import com.mchange.v2.c3p0.cfg.C3P0ConfigUtils;
import com.mchange.v2.c3p0.impl.*;
import com.mchange.v2.log.*;
import com.mchange.v1.db.sql.ConnectionUtils;


// MT: Most methods are left unsynchronized, because getNestedDataSource() is synchronized, and for most methods, that's
//     the only critical part. Previous oversynchronization led to hangs, when getting the Connection for one Thread happened
//     to hang, blocking access to getPooledConnection() for all Threads.
public final class WrapperConnectionPoolDataSource extends WrapperConnectionPoolDataSourceBase implements ConnectionPoolDataSource
{
    final static MLogger logger = MLog.getLogger( WrapperConnectionPoolDataSource.class );

    //MT: protected by this' lock
    ConnectionTester connectionTester = C3P0Registry.getDefaultConnectionTester();
    Map              userOverrides;

    public WrapperConnectionPoolDataSource(boolean autoregister)
    {
	super( autoregister );

	setUpPropertyListeners();

	//set up initial value of userOverrides
	try
	    { this.userOverrides = C3P0ImplUtils.parseUserOverridesAsString( this.getUserOverridesAsString() ); }
	catch (Exception e)
	    {
		if ( logger.isLoggable( MLevel.WARNING ) )
		    logger.log( MLevel.WARNING, "Failed to parse stringified userOverrides. " + this.getUserOverridesAsString(), e );
	    }
    }

    public WrapperConnectionPoolDataSource()
    { this( true ); }

    private void setUpPropertyListeners()
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
    }

    public WrapperConnectionPoolDataSource( String configName )
    {
	this();
	
	try
	    {
		if (configName != null)
		    C3P0Config.bindNamedConfigToBean( this, configName, true ); 
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

    public PooledConnection getPooledConnection()
	throws SQLException
    { return this.getPooledConnection( (ConnectionCustomizer) null, null ); }

    // getNestedDataSource() is sync'ed, which is enough. Unsync'ed this method,
    // because when sync'ed a hang in retrieving one connection blocks all
    //
    protected PooledConnection getPooledConnection( ConnectionCustomizer cc, String pdsIdt )
	throws SQLException
    { 
	DataSource nds = getNestedDataSource();
	if (nds == null)
	    throw new SQLException( "No standard DataSource has been set beneath this wrapper! [ nestedDataSource == null ]");
	Connection conn = null;
	try
	{
	    conn = nds.getConnection();
	    if (conn == null)
		throw new SQLException("An (unpooled) DataSource returned null from its getConnection() method! " +
				       "DataSource: " + getNestedDataSource());
	    if ( this.isUsesTraditionalReflectiveProxies( this.getUser() ) )
	    {
		//return new C3P0PooledConnection( new com.mchange.v2.c3p0.test.CloseReportingConnection( conn ), 
		return new C3P0PooledConnection( conn, 
						 connectionTester,
						 this.isAutoCommitOnClose( this.getUser() ), 
						 this.isForceIgnoreUnresolvedTransactions( this.getUser() ),
						 cc,
						 pdsIdt); 
	    }
	    else
	    {
		return new NewPooledConnection( conn, 
						connectionTester,
						this.isAutoCommitOnClose( this.getUser() ), 
						this.isForceIgnoreUnresolvedTransactions( this.getUser() ),
						this.getPreferredTestQuery( this.getUser() ),
						cc,
						pdsIdt); 
	    }
	}
	catch (SQLException e)
	{
	    // if we did not succeed at emitting the PooledConnection, we should close
	    // the underlying database Connection
	    ConnectionUtils.attemptClose( conn );

	    throw e;
	}
	catch (RuntimeException re)
	{
	    // if we did not succeed at emitting the PooledConnection, we should close
	    // the underlying database Connection
	    ConnectionUtils.attemptClose( conn );

	    throw re;
	}
    } 
 
    public PooledConnection getPooledConnection(String user, String password)
	throws SQLException
    { return this.getPooledConnection( user, password, null, null ); }

    // getNestedDataSource() is sync'ed, which is enough. Unsync'ed this method,
    // because when sync'ed a hang in retrieving one connection blocks all
    //
    protected PooledConnection getPooledConnection(String user, String password, ConnectionCustomizer cc, String pdsIdt)
	throws SQLException
    { 
	DataSource nds = getNestedDataSource();
	if (nds == null)
	    throw new SQLException( "No standard DataSource has been set beneath this wrapper! [ nestedDataSource == null ]");
	Connection conn = null;
	try
	{
	    conn = nds.getConnection(user, password);
	    if (conn == null)
		throw new SQLException("An (unpooled) DataSource returned null from its getConnection() method! " +
				       "DataSource: " + getNestedDataSource());
	    if ( this.isUsesTraditionalReflectiveProxies( user ) )
	    {
		//return new C3P0PooledConnection( new com.mchange.v2.c3p0.test.CloseReportingConnection( conn ), 
		return new C3P0PooledConnection( conn,
						 connectionTester,
						 this.isAutoCommitOnClose( user ), 
						 this.isForceIgnoreUnresolvedTransactions( user ),
						 cc,
						 pdsIdt);
	    }
	    else
	    {
		return new NewPooledConnection( conn, 
						connectionTester,
						this.isAutoCommitOnClose( user ), 
						this.isForceIgnoreUnresolvedTransactions( user ),
						this.getPreferredTestQuery( user ),
						cc,
						pdsIdt); 
	    }
	}
	catch (SQLException e)
	{
	    // if we did not succeed at emitting the PooledConnection, we should close
	    // the underlying database Connection
	    ConnectionUtils.attemptClose( conn );

	    throw e;
	}
	catch (RuntimeException re)
	{
	    // if we did not succeed at emitting the PooledConnection, we should close
	    // the underlying database Connection
	    ConnectionUtils.attemptClose( conn );

	    throw re;
	}
    }

    private boolean isAutoCommitOnClose( String userName )
    {
	if ( userName == null )
	    return this.isAutoCommitOnClose();

	Boolean override = C3P0ConfigUtils.extractBooleanOverride( "autoCommitOnClose", userName, userOverrides );
	return ( override == null ? this.isAutoCommitOnClose() : override.booleanValue() );
    }

    private boolean isForceIgnoreUnresolvedTransactions( String userName )
    {
	if ( userName == null )
	    return this.isForceIgnoreUnresolvedTransactions();

	Boolean override = C3P0ConfigUtils.extractBooleanOverride( "forceIgnoreUnresolvedTransactions", userName, userOverrides );
	return ( override == null ? this.isForceIgnoreUnresolvedTransactions() : override.booleanValue() );
    }

    private boolean isUsesTraditionalReflectiveProxies( String userName )
    {
	if ( userName == null )
	    return this.isUsesTraditionalReflectiveProxies();

	Boolean override = C3P0ConfigUtils.extractBooleanOverride( "usesTraditionalReflectiveProxies", userName, userOverrides );
	return ( override == null ? this.isUsesTraditionalReflectiveProxies() : override.booleanValue() );
    }

    private String getPreferredTestQuery( String userName )
    {
	if ( userName == null )
	    return this.getPreferredTestQuery();

	String override = (String) C3P0ConfigUtils.extractUserOverride( "preferredTestQuery", userName, userOverrides );
	return (override == null ? this.getPreferredTestQuery() : override);
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
	    this.connectionTester = C3P0Registry.getDefaultConnectionTester();
    }
}
