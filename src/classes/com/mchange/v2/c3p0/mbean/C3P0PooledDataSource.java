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


package com.mchange.v2.c3p0.mbean;

import com.mchange.v2.c3p0.*;
import com.mchange.v2.log.*;
import java.beans.PropertyVetoException;
import java.sql.Connection;
import java.sql.SQLException;
import java.io.PrintWriter;
import java.util.Properties;
import javax.sql.DataSource;
import javax.naming.InitialContext;
import javax.naming.Name;
import javax.naming.Context;
import javax.naming.NameAlreadyBoundException;
import javax.naming.NamingException;

public class C3P0PooledDataSource implements C3P0PooledDataSourceMBean
{
    private final static MLogger logger = MLog.getLogger( C3P0PooledDataSource.class );

    String jndiName;

    ComboPooledDataSource combods = new ComboPooledDataSource();

    private void rebind() throws NamingException
    { rebind(null); }

    private void rebind(String unbindName) throws NamingException
    {
	InitialContext ictx = new InitialContext();
	if (unbindName != null)
	    ictx.unbind( unbindName );
	
	if (jndiName != null)
	{
	    // Thanks to David D. Kilzer for this code to auto-create
	    // subcontext paths!
	    Name name = ictx.getNameParser( jndiName ).parse( jndiName );
	    Context ctx = ictx;
	    for (int i = 0, max = name.size() - 1; i < max; i++)
	    {
		try
		{ ctx = ctx.createSubcontext( name.get( i ) ); }
		catch (NameAlreadyBoundException ignore)
		{ ctx = (Context) ctx.lookup( name.get( i ) ); }
	    }

 	    ictx.rebind( jndiName, combods );
	}


    }

    // Jndi Setup Names
    public void setJndiName(String jndiName) throws NamingException
    {
	String unbindName = this.jndiName;
	this.jndiName = jndiName; 
	rebind( unbindName );
    }

    public String getJndiName()
    { return jndiName; }

    // DriverManagerDataSourceProperties  (count: 4)
    public String getDescription()
    { return combods.getDescription(); }
	
    public void setDescription( String description ) throws NamingException
    { 
	combods.setDescription( description ); 
	rebind();
    }
	
    public String getDriverClass()
    { return combods.getDriverClass(); }
	
    public void setDriverClass( String driverClass ) throws PropertyVetoException, NamingException
    { 
	combods.setDriverClass( driverClass ); 
	rebind();
    }
	
    public String getJdbcUrl()
    { return combods.getJdbcUrl(); }
	
    public void setJdbcUrl( String jdbcUrl ) throws NamingException
    { 
	combods.setJdbcUrl( jdbcUrl ); 
	rebind();
    }
	
    // DriverManagerDataSource "virtual properties" based on properties
    public String getUser()
    { return combods.getUser(); }
	
    public void setUser( String user ) throws NamingException
    { 
	combods.setUser( user ); 
	rebind();
    }
	
    public String getPassword()
    { return combods.getPassword(); }
	
    public void setPassword( String password ) throws NamingException
    { 
	combods.setPassword( password ); 
	rebind();
    }

    // WrapperConnectionPoolDataSource properties (count: 21)
    public int getCheckoutTimeout()
    { return combods.getCheckoutTimeout(); }
	
    public void setCheckoutTimeout( int checkoutTimeout ) throws NamingException
    { 
	combods.setCheckoutTimeout( checkoutTimeout ); 
	rebind();
    }

    public int getAcquireIncrement()
    { return combods.getAcquireIncrement(); }
	
    public void setAcquireIncrement( int acquireIncrement ) throws NamingException
    { 
	combods.setAcquireIncrement( acquireIncrement ); 
	rebind();
    }
	
    public int getAcquireRetryAttempts()
    { return combods.getAcquireRetryAttempts(); }
	
    public void setAcquireRetryAttempts( int acquireRetryAttempts ) throws NamingException
    { 
	combods.setAcquireRetryAttempts( acquireRetryAttempts ); 
	rebind();
    }
	
    public int getAcquireRetryDelay()
    { return combods.getAcquireRetryDelay(); }
	
    public void setAcquireRetryDelay( int acquireRetryDelay ) throws NamingException
    { 
	combods.setAcquireRetryDelay( acquireRetryDelay ); 
	rebind();
    }
	
    public boolean isAutoCommitOnClose()
    { return combods.isAutoCommitOnClose(); }

    public void setAutoCommitOnClose( boolean autoCommitOnClose ) throws NamingException
    { 
	combods.setAutoCommitOnClose( autoCommitOnClose ); 
	rebind();
    }
	
    public String getConnectionTesterClassName()
    { return combods.getConnectionTesterClassName(); }
	
    public void setConnectionTesterClassName( String connectionTesterClassName ) throws PropertyVetoException, NamingException
    { 
	combods.setConnectionTesterClassName( connectionTesterClassName ); 
	rebind();
    }
	
    public String getAutomaticTestTable()
    { return combods.getAutomaticTestTable(); }
	
    public void setAutomaticTestTable( String automaticTestTable ) throws NamingException
    { 
	combods.setAutomaticTestTable( automaticTestTable ); 
	rebind();
    }
	
    public boolean isForceIgnoreUnresolvedTransactions()
    { return combods.isForceIgnoreUnresolvedTransactions(); }
	
    public void setForceIgnoreUnresolvedTransactions( boolean forceIgnoreUnresolvedTransactions ) throws NamingException
    { 
	combods.setForceIgnoreUnresolvedTransactions( forceIgnoreUnresolvedTransactions ); 
	rebind();
    }
	
    public int getIdleConnectionTestPeriod()
    { return combods.getIdleConnectionTestPeriod(); }
	
    public void setIdleConnectionTestPeriod( int idleConnectionTestPeriod ) throws NamingException
    { 
	combods.setIdleConnectionTestPeriod( idleConnectionTestPeriod ); 
	rebind();
    }
    
    public int getInitialPoolSize()
    { return combods.getInitialPoolSize(); }
	
    public void setInitialPoolSize( int initialPoolSize ) throws NamingException
    { 
	combods.setInitialPoolSize( initialPoolSize ); 
	rebind();
    }

    public int getMaxIdleTime()
    { return combods.getMaxIdleTime(); }
	
    public void setMaxIdleTime( int maxIdleTime ) throws NamingException
    { 
	combods.setMaxIdleTime( maxIdleTime ); 
	rebind();
    }
	
    public int getMaxPoolSize()
    { return combods.getMaxPoolSize(); }
	
    public void setMaxPoolSize( int maxPoolSize ) throws NamingException
    { 
	combods.setMaxPoolSize( maxPoolSize ); 
	rebind();
    }
	
    public int getMaxStatements()
    { return combods.getMaxStatements(); }
	
    public void setMaxStatements( int maxStatements ) throws NamingException
    { 
	combods.setMaxStatements( maxStatements ); 
	rebind();
    }
	
    public int getMaxStatementsPerConnection()
    { return combods.getMaxStatementsPerConnection(); }
	
    public void setMaxStatementsPerConnection( int maxStatementsPerConnection ) throws NamingException
    { 
	combods.setMaxStatementsPerConnection( maxStatementsPerConnection ); 
	rebind();
    }
	
    public int getMinPoolSize()
    { return combods.getMinPoolSize(); }
	
    public void setMinPoolSize( int minPoolSize ) throws NamingException
    { 
	combods.setMinPoolSize( minPoolSize ); 
	rebind();
    }
	
    public int getPropertyCycle()
    { return combods.getPropertyCycle(); }
	
    public void setPropertyCycle( int propertyCycle ) throws NamingException
    { 
	combods.setPropertyCycle( propertyCycle ); 
	rebind();
    }
    
    public boolean isBreakAfterAcquireFailure()
    { return combods.isBreakAfterAcquireFailure(); }
    
    public void setBreakAfterAcquireFailure( boolean breakAfterAcquireFailure ) throws NamingException
    { 
	combods.setBreakAfterAcquireFailure( breakAfterAcquireFailure ); 
	rebind();
    }
    
    public boolean isTestConnectionOnCheckout()
    { return combods.isTestConnectionOnCheckout(); }
	
    public void setTestConnectionOnCheckout( boolean testConnectionOnCheckout ) throws NamingException
    { 
	combods.setTestConnectionOnCheckout( testConnectionOnCheckout ); 
	rebind();
    }
	
    public boolean isTestConnectionOnCheckin()
    { return combods.isTestConnectionOnCheckin(); }
	
    public void setTestConnectionOnCheckin( boolean testConnectionOnCheckin ) throws NamingException
    { 
	combods.setTestConnectionOnCheckin( testConnectionOnCheckin ); 
	rebind();
    }
	
    public boolean isUsesTraditionalReflectiveProxies()
    { return combods.isUsesTraditionalReflectiveProxies(); }
	
    public void setUsesTraditionalReflectiveProxies( boolean usesTraditionalReflectiveProxies ) throws NamingException
    { 
	combods.setUsesTraditionalReflectiveProxies( usesTraditionalReflectiveProxies ); 
	rebind();
    }

    public String getPreferredTestQuery()
    { return combods.getPreferredTestQuery(); }
	
    public void setPreferredTestQuery( String preferredTestQuery ) throws NamingException
    { 
	combods.setPreferredTestQuery( preferredTestQuery ); 
	rebind();
    }

    // PoolBackedDataSource properties (count: 2)
    public String getDataSourceName()
    { return combods.getDataSourceName(); }
	
    public void setDataSourceName( String name ) throws NamingException
    { 
	combods.setDataSourceName( name ); 
	rebind();
    }

    public int getNumHelperThreads()
    { return combods.getNumHelperThreads(); }
	
    public void setNumHelperThreads( int numHelperThreads ) throws NamingException
    { 
	combods.setNumHelperThreads( numHelperThreads ); 
	rebind();
    }

    // shared properties (count: 1)
    public String getFactoryClassLocation()
    { return combods.getFactoryClassLocation(); }
    
    public void setFactoryClassLocation( String factoryClassLocation ) throws NamingException
    { 
	combods.setFactoryClassLocation( factoryClassLocation ); 
	rebind();
    }

    // PooledDataSource statistics

    public int getNumUserPools() throws SQLException
    { return combods.getNumUserPools(); }

    public int getNumConnectionsDefaultUser() throws SQLException
    { return combods.getNumConnectionsDefaultUser(); }

    public int getNumIdleConnectionsDefaultUser() throws SQLException
    { return combods.getNumIdleConnectionsDefaultUser(); }

    public int getNumBusyConnectionsDefaultUser() throws SQLException
    { return combods.getNumBusyConnectionsDefaultUser(); }

    public int getNumUnclosedOrphanedConnectionsDefaultUser() throws SQLException
    { return combods.getNumUnclosedOrphanedConnectionsDefaultUser(); }

    public int getNumConnections(String username, String password) throws SQLException
    { return combods.getNumConnections(username, password); }

    public int getNumIdleConnections(String username, String password) throws SQLException
    { return combods.getNumIdleConnections(username, password); }

    public int getNumBusyConnections(String username, String password) throws SQLException
    { return combods.getNumBusyConnections(username, password); }

    public int getNumUnclosedOrphanedConnections(String username, String password) throws SQLException
    { return combods.getNumUnclosedOrphanedConnections(username, password); }

    public int getNumConnectionsAllUsers() throws SQLException
    { return combods.getNumConnectionsAllUsers(); }

    public int getNumIdleConnectionsAllUsers() throws SQLException
    { return combods.getNumIdleConnectionsAllUsers(); }

    public int getNumBusyConnectionsAllUsers() throws SQLException
    { return combods.getNumBusyConnectionsAllUsers(); }

    public int getNumUnclosedOrphanedConnectionsAllUsers() throws SQLException
    { return combods.getNumUnclosedOrphanedConnectionsAllUsers(); }

    // PooledDataSource operations
    public void softResetDefaultUser() throws SQLException
    { combods.softResetDefaultUser(); }

    public void softReset(String username, String password) throws SQLException
    { combods.softReset(username, password); }

    public void softResetAllUsers() throws SQLException
    { combods.softResetAllUsers(); }

    public void hardReset() throws SQLException
    { combods.hardReset(); }

    public void close() throws SQLException
    { combods.close(); }

    //JBoss only... (but these methods need not be called for the mbean to work)
    public void create() throws Exception
    { }

    // the mbean works without this, but if called we start populating the pool early
    public void start() throws Exception
    { 
	//System.err.println("Bound C3P0 PooledDataSource to name '" + jndiName + "'. Starting..."); 
	logger.log(MLevel.INFO, "Bound C3P0 PooledDataSource to name ''{0}''. Starting...", jndiName); 
	combods.getNumBusyConnectionsDefaultUser(); //just touch the datasource to start it up.
    }


    public void stop()
    { }

    public void destroy()
    { }
}

