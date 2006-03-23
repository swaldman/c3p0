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

import java.beans.*;
import java.io.*;
import java.sql.*;
import java.util.*;
import javax.naming.*;
import com.mchange.v2.log.*;
import com.mchange.v2.naming.*;
import com.mchange.v2.c3p0.impl.*;

import com.mchange.v2.beans.BeansUtils;
import com.mchange.v2.c3p0.cfg.C3P0Config;

// WrapperConnectionPoolDataSource properties -- count: 24
//
// 	("checkoutTimeout");
// 	("acquireIncrement");
// 	("acquireRetryAttempts");
// 	("acquireRetryDelay");
// 	("autoCommitOnClose");
// 	("connectionTesterClassName");
// 	("forceIgnoreUnresolvedTransactions");
// 	("idleConnectionTestPeriod");
// 	("initialPoolSize");
// 	("maxIdleTime");
// 	("maxPoolSize");
// 	("maxStatements");
// 	("maxStatementsPerConnection");
// 	("minPoolSize");
// 	("propertyCycle");
// 	("breakAfterAcquireFailure");
// 	("testConnectionOnCheckout");
// 	("testConnectionOnCheckin");
// 	("usesTraditionalReflectiveProxies");
// 	("preferredTestQuery");
// 	("automaticTestTable");
// 	("userOverridesAsString");
// 	("overrideDefaultUser");
// 	("overrideDefaultPassword");


/**
 * <p>For the meaning of most of these properties, please see {@link PoolConfig}!</p>
 */
public final class ComboPooledDataSource extends IdentityTokenResolvable implements PooledDataSource, Serializable, Referenceable
{
    final static MLogger logger = MLog.getLogger( ComboPooledDataSource.class );

    final static Set TO_STRING_IGNORE_PROPS = new HashSet( Arrays.asList( new String[] { 
									      "connection",
									      "logWriter",
									      "loginTimeout",
									      "numBusyConnections",
									      "numBusyConnectionsAllUsers",
									      "numBusyConnectionsDefaultUser",
									      "numConnections",
									      "numConnectionsAllUsers",
									      "numConnectionsDefaultUser",
									      "numIdleConnections",
									      "numIdleConnectionsAllUsers",
									      "numIdleConnectionsDefaultUser",
									      "numUnclosedOrphanedConnections",
									      "numUnclosedOrphanedConnectionsAllUsers",
									      "numUnclosedOrphanedConnectionsDefaultUser",
									      "numUserPools",
									      "overrideDefaultUser",
									      "overrideDefaultPassword",
									      "password",
									      "reference",
									      "user",
									      "userOverridesAsString"
									  } ) );

    // not reassigned post-ctor; mutable elements protected by their own locks
    // when (very rarely) necessery, we sync pbds -> wcpds -> dmds
    DriverManagerDataSource         dmds;
    WrapperConnectionPoolDataSource wcpds;
    PoolBackedDataSource            pbds;

    String identityToken;

    {
	// System.err.println("...Initializing ComboPooledDataSource.");

	dmds  = new DriverManagerDataSource();
	wcpds = new WrapperConnectionPoolDataSource();
	pbds  = new PoolBackedDataSource( this ); 

	wcpds.setNestedDataSource( dmds );
	pbds.setConnectionPoolDataSource( wcpds );

	this.identityToken = C3P0ImplUtils.identityToken( this );
	C3P0Registry.register( this );
    }

    public ComboPooledDataSource()
    {}

    public ComboPooledDataSource(String configName)
    {
	try
	    {
		if (configName != null)
		    {
			C3P0Config.bindNamedConfigToBean( this, configName ); 
			if ( this.getDataSourceName().equals( this.getIdentityToken() ) ) //dataSourceName has not been specified in config
			    this.setDataSourceName( configName );
		    }
	    }
	catch (Exception e)
	    {
		if (logger.isLoggable( MLevel.WARNING ))
		    logger.log( MLevel.WARNING, 
				"Error binding ComboPooledDataSource to named-config '" + configName + 
				"'. Some default-config values may be used.", 
				e);
	    }
    }

    // DriverManagerDataSourceProperties  (count: 4)
    public String getDescription()
    { return dmds.getDescription(); }
	
    public void setDescription( String description )
    { dmds.setDescription( description ); }
	
    public String getDriverClass()
    { return dmds.getDriverClass(); }
	
    public void setDriverClass( String driverClass ) throws PropertyVetoException
    { 
	dmds.setDriverClass( driverClass ); 
// 	System.err.println("setting driverClass: " + driverClass); 
    }
	
    public String getJdbcUrl()
    {  
// 	System.err.println("getting jdbcUrl: " + dmds.getJdbcUrl()); 
	return dmds.getJdbcUrl(); 
    }
	
    public void setJdbcUrl( String jdbcUrl )
    { 
	dmds.setJdbcUrl( jdbcUrl ); 
// 	System.err.println("setting jdbcUrl: " + jdbcUrl + " [dmds@" + C3P0ImplUtils.identityToken( dmds ) + "]"); 
// 	if (jdbcUrl == null)
// 	    new Exception("*** NULL SETTER ***").printStackTrace();
    }
	
    public Properties getProperties()
    { 
	//System.err.println("getting properties: " + dmds.getProperties()); 
	return dmds.getProperties(); 
    }
	
    public void setProperties( Properties properties )
    { 
	dmds.setProperties( properties ); 
	//System.err.println("setting properties: " + properties); 
    }
	
    // DriverManagerDataSource "virtual properties" based on properties
    public String getUser()
    { return dmds.getUser(); }
	
    public void setUser( String user )
    { dmds.setUser( user ); }
	
    public String getPassword()
    { return dmds.getPassword(); }
	
    public void setPassword( String password )
    { dmds.setPassword( password ); }

    // WrapperConnectionPoolDataSource properties (count: 21)
    public int getCheckoutTimeout()
    { return wcpds.getCheckoutTimeout(); }
	
    public void setCheckoutTimeout( int checkoutTimeout )
    { 
	synchronized ( pbds )
	    {
		wcpds.setCheckoutTimeout( checkoutTimeout ); 
		pbds.resetPoolManager( false );
	    }
    }
	
    public int getAcquireIncrement()
    { return wcpds.getAcquireIncrement(); }
	
    public void setAcquireIncrement( int acquireIncrement )
    { 
	synchronized ( pbds )
	    {
		wcpds.setAcquireIncrement( acquireIncrement ); 
		pbds.resetPoolManager( false );
	    }
    }
	
    public int getAcquireRetryAttempts()
    { return wcpds.getAcquireRetryAttempts(); }
	
    public void setAcquireRetryAttempts( int acquireRetryAttempts )
    { 
	synchronized ( pbds )
	    {
		wcpds.setAcquireRetryAttempts( acquireRetryAttempts ); 
		pbds.resetPoolManager( false );
	    }
    }
	
    public int getAcquireRetryDelay()
    { return wcpds.getAcquireRetryDelay(); }
	
    public void setAcquireRetryDelay( int acquireRetryDelay )
    { 
	synchronized ( pbds )
	    {
		wcpds.setAcquireRetryDelay( acquireRetryDelay ); 
		pbds.resetPoolManager( false );
	    }
    }
	
    public boolean isAutoCommitOnClose()
    { return wcpds.isAutoCommitOnClose(); }

    public void setAutoCommitOnClose( boolean autoCommitOnClose )
    { 
	synchronized ( pbds )
	    {
		wcpds.setAutoCommitOnClose( autoCommitOnClose ); 
		pbds.resetPoolManager( false );
	    }
    }
	
    public String getConnectionTesterClassName()
    { return wcpds.getConnectionTesterClassName(); }
	
    public void setConnectionTesterClassName( String connectionTesterClassName ) throws PropertyVetoException
    { 
	synchronized ( pbds )
	    {
		wcpds.setConnectionTesterClassName( connectionTesterClassName ); 
		pbds.resetPoolManager( false );
	    }
    }
	
    public String getAutomaticTestTable()
    { return wcpds.getAutomaticTestTable(); }
	
    public void setAutomaticTestTable( String automaticTestTable )
    { 
	synchronized ( pbds )
	    {
		wcpds.setAutomaticTestTable( automaticTestTable ); 
		pbds.resetPoolManager( false );
	    }
    }
	
    public boolean isForceIgnoreUnresolvedTransactions()
    { return wcpds.isForceIgnoreUnresolvedTransactions(); }
	
    public void setForceIgnoreUnresolvedTransactions( boolean forceIgnoreUnresolvedTransactions )
    { 
	synchronized ( pbds )
	    {
		wcpds.setForceIgnoreUnresolvedTransactions( forceIgnoreUnresolvedTransactions ); 
		pbds.resetPoolManager( false );
	    }
    }
	
    public int getIdleConnectionTestPeriod()
    { return wcpds.getIdleConnectionTestPeriod(); }
	
    public void setIdleConnectionTestPeriod( int idleConnectionTestPeriod )
    { 
	synchronized ( pbds )
	    {
		wcpds.setIdleConnectionTestPeriod( idleConnectionTestPeriod ); 
		pbds.resetPoolManager( false );
	    }
    }
    
    public int getInitialPoolSize()
    { return wcpds.getInitialPoolSize(); }
	
    public void setInitialPoolSize( int initialPoolSize )
    { 
	synchronized ( pbds )
	    {
		wcpds.setInitialPoolSize( initialPoolSize ); 
		pbds.resetPoolManager( false );
	    }
    }

    public int getMaxIdleTime()
    { return wcpds.getMaxIdleTime(); }
	
    public void setMaxIdleTime( int maxIdleTime )
    { 
	synchronized ( pbds )
	    {
		wcpds.setMaxIdleTime( maxIdleTime ); 
		pbds.resetPoolManager( false );
	    }
    }
	
    public int getMaxPoolSize()
    { return wcpds.getMaxPoolSize(); }
	
    public void setMaxPoolSize( int maxPoolSize )
    { 
	synchronized ( pbds )
	    {
		wcpds.setMaxPoolSize( maxPoolSize ); 
		pbds.resetPoolManager( false );
	    }
    }
	
    public int getMaxStatements()
    { return wcpds.getMaxStatements(); }
	
    public void setMaxStatements( int maxStatements )
    { 
	synchronized ( pbds )
	    {
		wcpds.setMaxStatements( maxStatements ); 
		pbds.resetPoolManager( false );
	    }
    }
	
    public int getMaxStatementsPerConnection()
    { return wcpds.getMaxStatementsPerConnection(); }
	
    public void setMaxStatementsPerConnection( int maxStatementsPerConnection )
    { 
	synchronized ( pbds )
	    {
		wcpds.setMaxStatementsPerConnection( maxStatementsPerConnection ); 
		pbds.resetPoolManager( false );
	    }
    }
	
    public int getMinPoolSize()
    { return wcpds.getMinPoolSize(); }
	
    public void setMinPoolSize( int minPoolSize )
    { 
	synchronized ( pbds )
	    {
		wcpds.setMinPoolSize( minPoolSize ); 
		pbds.resetPoolManager( false );
	    }
    }
	
    public String getOverrideDefaultUser()
    { return wcpds.getOverrideDefaultUser(); }
	
    public void setOverrideDefaultUser(String overrideDefaultUser)
    { 
	synchronized ( pbds )
	    {
		wcpds.setOverrideDefaultUser( overrideDefaultUser ); 
		pbds.resetPoolManager( false );
	    }
    }
	
    public String getOverrideDefaultPassword()
    { return wcpds.getOverrideDefaultPassword(); }
	
    public void setOverrideDefaultPassword(String overrideDefaultPassword)
    { 
	synchronized ( pbds )
	    {
		wcpds.setOverrideDefaultPassword( overrideDefaultPassword ); 
		pbds.resetPoolManager( false );
	    }
    }
	
    public int getPropertyCycle()
    { return wcpds.getPropertyCycle(); }
	
    public void setPropertyCycle( int propertyCycle )
    { 
	synchronized ( pbds )
	    {
		wcpds.setPropertyCycle( propertyCycle ); 
		pbds.resetPoolManager( false );
	    }
    }
    
    public boolean isBreakAfterAcquireFailure()
    { return wcpds.isBreakAfterAcquireFailure(); }
    
    public void setBreakAfterAcquireFailure( boolean breakAfterAcquireFailure )
    { 
	synchronized ( pbds )
	    {
		wcpds.setBreakAfterAcquireFailure( breakAfterAcquireFailure ); 
		pbds.resetPoolManager( false );
	    }
    }
    
    public boolean isTestConnectionOnCheckout()
    { return wcpds.isTestConnectionOnCheckout(); }
	
    public void setTestConnectionOnCheckout( boolean testConnectionOnCheckout )
    { 
	synchronized ( pbds )
	    {
		wcpds.setTestConnectionOnCheckout( testConnectionOnCheckout ); 
		pbds.resetPoolManager( false );
	    }
    }
	
    public boolean isTestConnectionOnCheckin()
    { return wcpds.isTestConnectionOnCheckin(); }
	
    public void setTestConnectionOnCheckin( boolean testConnectionOnCheckin )
    { 
	synchronized ( pbds )
	    {
		wcpds.setTestConnectionOnCheckin( testConnectionOnCheckin ); 
		pbds.resetPoolManager( false );
	    }
    }
	
    public boolean isUsesTraditionalReflectiveProxies()
    { return wcpds.isUsesTraditionalReflectiveProxies(); }
	
    public void setUsesTraditionalReflectiveProxies( boolean usesTraditionalReflectiveProxies )
    { 
	synchronized ( pbds )
	    {
		wcpds.setUsesTraditionalReflectiveProxies( usesTraditionalReflectiveProxies ); 
		pbds.resetPoolManager( false );
	    }
    }

    public String getPreferredTestQuery()
    { return wcpds.getPreferredTestQuery(); }
	
    public void setPreferredTestQuery( String preferredTestQuery )
    { 
	synchronized ( pbds )
	    {
		wcpds.setPreferredTestQuery( preferredTestQuery ); 
		pbds.resetPoolManager( false );
	    }
    }

    public String getUserOverridesAsString()
    { return wcpds.getUserOverridesAsString(); }
	
    public void setUserOverridesAsString( String userOverridesAsString ) throws PropertyVetoException
    { 
	synchronized ( pbds )
	    {
		wcpds.setUserOverridesAsString( userOverridesAsString ); 
		pbds.resetPoolManager( false );
	    }
    }

    // PoolBackedDataSource properties (count: 2)
    public String getDataSourceName()
    { return pbds.getDataSourceName(); }
	
    public void setDataSourceName( String name )
    { pbds.setDataSourceName( name ); }

    public int getNumHelperThreads()
    { return pbds.getNumHelperThreads(); }
	
    public void setNumHelperThreads( int numHelperThreads )
    { pbds.setNumHelperThreads( numHelperThreads ); }


    // identity tokens
    public String getIdentityToken()
    { return identityToken; }
	
    public void setIdentityToken(String identityToken)
    { this.identityToken = identityToken; }
	
    // shared properties (count: 1)
    public String getFactoryClassLocation()
    {
	return dmds.getFactoryClassLocation();
    }
    
    public void setFactoryClassLocation( String factoryClassLocation )
    {
	synchronized ( pbds )
	    {
		synchronized ( wcpds )
		    {
			synchronized( dmds )
			    {
				dmds.setFactoryClassLocation( factoryClassLocation );
				wcpds.setFactoryClassLocation( factoryClassLocation );
				pbds.setFactoryClassLocation( factoryClassLocation );
			    }
		    }
	    }
    }


    final static JavaBeanReferenceMaker referenceMaker = new JavaBeanReferenceMaker();
    
    static
    {
	referenceMaker.setFactoryClassName( C3P0JavaBeanObjectFactory.class.getName() );

	// DriverManagerDataSource properties (count: 4)
	referenceMaker.addReferenceProperty("description");
	referenceMaker.addReferenceProperty("driverClass");
	referenceMaker.addReferenceProperty("jdbcUrl");
	referenceMaker.addReferenceProperty("properties");

	// WrapperConnectionPoolDataSource properties (count: 22)
	referenceMaker.addReferenceProperty("checkoutTimeout");
	referenceMaker.addReferenceProperty("acquireIncrement");
	referenceMaker.addReferenceProperty("acquireRetryAttempts");
	referenceMaker.addReferenceProperty("acquireRetryDelay");
	referenceMaker.addReferenceProperty("autoCommitOnClose");
	referenceMaker.addReferenceProperty("connectionTesterClassName");
	referenceMaker.addReferenceProperty("forceIgnoreUnresolvedTransactions");
	referenceMaker.addReferenceProperty("idleConnectionTestPeriod");
	referenceMaker.addReferenceProperty("initialPoolSize");
	referenceMaker.addReferenceProperty("maxIdleTime");
	referenceMaker.addReferenceProperty("maxPoolSize");
	referenceMaker.addReferenceProperty("maxStatements");
	referenceMaker.addReferenceProperty("maxStatementsPerConnection");
	referenceMaker.addReferenceProperty("minPoolSize");
	referenceMaker.addReferenceProperty("propertyCycle");
	referenceMaker.addReferenceProperty("breakAfterAcquireFailure");
	referenceMaker.addReferenceProperty("testConnectionOnCheckout");
	referenceMaker.addReferenceProperty("testConnectionOnCheckin");
	referenceMaker.addReferenceProperty("usesTraditionalReflectiveProxies");
	referenceMaker.addReferenceProperty("preferredTestQuery");
	referenceMaker.addReferenceProperty("automaticTestTable");
	referenceMaker.addReferenceProperty("userOverridesAsString");

	// PoolBackedDataSource properties (count: 2)
	referenceMaker.addReferenceProperty("dataSourceName");
	referenceMaker.addReferenceProperty("numHelperThreads");

	// identity token
	referenceMaker.addReferenceProperty("identityToken");

	// shared properties (count: 1)
	referenceMaker.addReferenceProperty("factoryClassLocation");
    }
    
    public Reference getReference() throws NamingException
    { 
	synchronized ( pbds )
	    {
		synchronized ( wcpds )
		    {
			synchronized( dmds )
			    {
				//System.err.println("ComboPooledDataSource.getReference()!!!!");
				//new Exception("PRINT-STACK-TRACE").printStackTrace();
				//javax.naming.Reference out = referenceMaker.createReference( this ); 
				//System.err.println(out);
				//return out;

				return referenceMaker.createReference( this ); 
			    }
		    }
	    }
    }

    // DataSource implementation
    public Connection getConnection() throws SQLException
    { return pbds.getConnection(); }

    public Connection getConnection(String username, String password) throws SQLException
    { return pbds.getConnection( username, password );  }

    public PrintWriter getLogWriter() throws SQLException
    { return pbds.getLogWriter(); }

    public void setLogWriter(PrintWriter out) throws SQLException
    { pbds.setLogWriter( out ); }

    public int getLoginTimeout() throws SQLException
    { return pbds.getLoginTimeout(); }

    public void setLoginTimeout(int seconds) throws SQLException
    { pbds.setLoginTimeout( seconds ); }

    //implementation of com.mchange.v2.c3p0.PoolingDataSource
    public int getNumConnections() throws SQLException
    { return pbds.getNumConnections(); }

    public int getNumIdleConnections() throws SQLException
    { return pbds.getNumIdleConnections(); }

    public int getNumBusyConnections() throws SQLException
    { return pbds.getNumBusyConnections(); }

    public int getNumUnclosedOrphanedConnections() throws SQLException
    { return pbds.getNumUnclosedOrphanedConnections(); }

    public int getNumConnectionsDefaultUser() throws SQLException
    { return pbds.getNumConnectionsDefaultUser(); }

    public int getNumIdleConnectionsDefaultUser() throws SQLException
    { return pbds.getNumIdleConnectionsDefaultUser(); }

    public int getNumBusyConnectionsDefaultUser() throws SQLException
    { return pbds.getNumBusyConnectionsDefaultUser(); }

    public int getNumUnclosedOrphanedConnectionsDefaultUser() throws SQLException
    { return pbds.getNumUnclosedOrphanedConnectionsDefaultUser(); }

    public void softResetDefaultUser() throws SQLException
    { pbds.softResetDefaultUser();}

    public int getNumConnections(String username, String password) throws SQLException
    { return pbds.getNumConnections( username, password ); }

    public int getNumIdleConnections(String username, String password) throws SQLException
    { return pbds.getNumIdleConnections( username, password ); }

    public int getNumBusyConnections(String username, String password) throws SQLException
    { return pbds.getNumBusyConnections( username, password ); }

    public int getNumUnclosedOrphanedConnections(String username, String password) throws SQLException
    { return pbds.getNumUnclosedOrphanedConnections( username, password ); }

    public void softReset(String username, String password) throws SQLException
    { pbds.softReset( username, password );}

    public int getNumBusyConnectionsAllUsers() throws SQLException
    { return pbds.getNumBusyConnectionsAllUsers(); }

    public int getNumIdleConnectionsAllUsers() throws SQLException
    { return pbds.getNumIdleConnectionsAllUsers(); }

    public int getNumConnectionsAllUsers() throws SQLException
    { return pbds.getNumConnectionsAllUsers(); }

    public int getNumUnclosedOrphanedConnectionsAllUsers() throws SQLException
    { return pbds.getNumUnclosedOrphanedConnectionsAllUsers(); }

    public void softResetAllUsers() throws SQLException
    { pbds.softResetAllUsers();}

    public int getNumUserPools() throws SQLException
    { return pbds.getNumUserPools(); }


    public Collection getAllUsers() throws SQLException
    { return pbds.getAllUsers(); }

    public void hardReset() throws SQLException
    { pbds.hardReset(); }

    public void close() throws SQLException
    { pbds.close(); }

    public void close( boolean force_destroy ) throws SQLException
    { pbds.close( force_destroy ); }

    public String toString()
    {
	StringBuffer sb = new StringBuffer(255);
	sb.append( super.toString() );
	sb.append("[ ");
	try { BeansUtils.appendPropNamesAndValues(sb, this, TO_STRING_IGNORE_PROPS); }
	catch (Exception e)
	    { sb.append( e.toString() ); }
	sb.append(" ]");

// 	Map userOverrides = wcpds.getUserOverrides();
// 	if (userOverrides != null)
// 	    sb.append("; userOverrides: " + userOverrides.toString());

	return sb.toString();
    }
}

