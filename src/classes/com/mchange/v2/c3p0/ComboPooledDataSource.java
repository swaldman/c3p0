/*
 * Distributed as part of c3p0 v.0.8.4-test5
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

import java.beans.*;
import java.io.*;
import java.sql.*;
import java.util.*;
import javax.naming.*;
import javax.sql.*;
import com.mchange.v2.naming.*;

/**
 * <p>For the meaning of most of these properties, please see {@link PoolConfig}!</p>
 */
public final class ComboPooledDataSource implements PooledDataSource, Serializable, Referenceable
{
    DriverManagerDataSource         dmds;
    WrapperConnectionPoolDataSource wcpds;
    PoolBackedDataSource            pbds;

    {
	dmds  = new DriverManagerDataSource();
	wcpds = new WrapperConnectionPoolDataSource();
	pbds  = new PoolBackedDataSource(); 

	wcpds.setNestedDataSource( dmds );
	pbds.setConnectionPoolDataSource( wcpds );
    }

    // DriverManagerDataSourceProperties
    public String getDescription()
    { return dmds.getDescription(); }
	
    public void setDescription( String description )
    { dmds.setDescription( description ); }
	
    public String getDriverClass()
    { return dmds.getDriverClass(); }
	
    public void setDriverClass( String driverClass ) throws PropertyVetoException
    { dmds.setDriverClass( driverClass ); }
	
    public String getJdbcUrl()
    { return dmds.getJdbcUrl(); }
	
    public void setJdbcUrl( String jdbcUrl )
    { dmds.setJdbcUrl( jdbcUrl ); }
	
    public Properties getProperties()
    { return dmds.getProperties(); }
	
    public void setProperties( Properties properties )
    { dmds.setProperties( properties ); }
	
    // DriverManagerDataSource "virtual properties" based on properties
    public String getUser()
    { return dmds.getUser(); }
	
    public void setUser( String user )
    { dmds.setUser( user ); }
	
    public String getPassword()
    { return dmds.getPassword(); }
	
    public void setPassword( String password )
    { dmds.setPassword( password ); }

    // WrapperConnectionPoolDataSource properties
    public int getAcquireIncrement()
    { return wcpds.getAcquireIncrement(); }
	
    public void setAcquireIncrement( int acquireIncrement )
    { 
	wcpds.setAcquireIncrement( acquireIncrement ); 
	pbds.resetPoolManager();
    }
	
    public boolean isAutoCommitOnClose()
    { return wcpds.isAutoCommitOnClose(); }

    public void setAutoCommitOnClose( boolean autoCommitOnClose )
    { 
	wcpds.setAutoCommitOnClose( autoCommitOnClose ); 
	pbds.resetPoolManager();
    }
	
    public String getConnectionTesterClassName()
    { return wcpds.getConnectionTesterClassName(); }
	
    public void setConnectionTesterClassName( String connectionTesterClassName ) throws PropertyVetoException
    { 
	wcpds.setConnectionTesterClassName( connectionTesterClassName ); 
	pbds.resetPoolManager();
    }
	
    public boolean isForceIgnoreUnresolvedTransactions()
    { return wcpds.isForceIgnoreUnresolvedTransactions(); }
	
    public void setForceIgnoreUnresolvedTransactions( boolean forceIgnoreUnresolvedTransactions )
    { 
	wcpds.setForceIgnoreUnresolvedTransactions( forceIgnoreUnresolvedTransactions ); 
	pbds.resetPoolManager();
    }
	
    public int getIdleConnectionTestPeriod()
    { return wcpds.getIdleConnectionTestPeriod(); }
	
    public void setIdleConnectionTestPeriod( int idleConnectionTestPeriod )
    { 
	wcpds.setIdleConnectionTestPeriod( idleConnectionTestPeriod ); 
	pbds.resetPoolManager();
    }
    
    public int getInitialPoolSize()
    { return wcpds.getInitialPoolSize(); }
	
    public void setInitialPoolSize( int initialPoolSize )
    { 
	wcpds.setInitialPoolSize( initialPoolSize ); 
	pbds.resetPoolManager();
    }

    public int getMaxIdleTime()
    { return wcpds.getMaxIdleTime(); }
	
    public void setMaxIdleTime( int maxIdleTime )
    { 
	wcpds.setMaxIdleTime( maxIdleTime ); 
	pbds.resetPoolManager();
    }
	
    public int getMaxPoolSize()
    { return wcpds.getMaxPoolSize(); }
	
    public void setMaxPoolSize( int maxPoolSize )
    { 
	wcpds.setMaxPoolSize( maxPoolSize ); 
	pbds.resetPoolManager();
    }
	
    public int getMaxStatements()
    { return wcpds.getMaxStatements(); }
	
    public void setMaxStatements( int maxStatements )
    { 
	wcpds.setMaxStatements( maxStatements ); 
	pbds.resetPoolManager();
    }
	
    public int getMinPoolSize()
    { return wcpds.getMinPoolSize(); }
	
    public void setMinPoolSize( int minPoolSize )
    { 
	wcpds.setMinPoolSize( minPoolSize ); 
	pbds.resetPoolManager();
    }
	
    public int getPropertyCycle()
    { return wcpds.getPropertyCycle(); }
	
    public void setPropertyCycle( int propertyCycle )
    { 
	wcpds.setPropertyCycle( propertyCycle ); 
	pbds.resetPoolManager();
    }
	
    public boolean isTestConnectionOnCheckout()
    { return wcpds.isTestConnectionOnCheckout(); }
	
    public void setTestConnectionOnCheckout( boolean testConnectionOnCheckout )
    { 
	wcpds.setTestConnectionOnCheckout( testConnectionOnCheckout ); 
	pbds.resetPoolManager();
    }
	
    // PoolBackedDataSource properties
    public int getNumHelperThreads()
    { return pbds.getNumHelperThreads(); }
	
    public void setNumHelperThreads( int numHelperThreads )
    { pbds.setNumHelperThreads( numHelperThreads ); }

    // shared properties
    public String getFactoryClassLocation()
    {
	return dmds.getFactoryClassLocation();
    }
    
    public void setFactoryClassLocation( String factoryClassLocation )
    {
	dmds.setFactoryClassLocation( factoryClassLocation );
	wcpds.setFactoryClassLocation( factoryClassLocation );
	pbds.setFactoryClassLocation( factoryClassLocation );
    }


    final static JavaBeanReferenceMaker referenceMaker = new JavaBeanReferenceMaker();
    
    static
    {
	referenceMaker.setFactoryClassName( JavaBeanObjectFactory.class.getName() );

	// DriverManagerDataSource properties
	referenceMaker.addReferenceProperty("description");
	referenceMaker.addReferenceProperty("driverClass");
	referenceMaker.addReferenceProperty("jdbcUrl");
	referenceMaker.addReferenceProperty("properties");

	// WrapperConnectionPoolDataSource properties
	referenceMaker.addReferenceProperty("acquireIncrement");
	referenceMaker.addReferenceProperty("autoCommitOnClose");
	referenceMaker.addReferenceProperty("connectionTesterClassName");
	referenceMaker.addReferenceProperty("factoryClassLocation");
	referenceMaker.addReferenceProperty("forceIgnoreUnresolvedTransactions");
	referenceMaker.addReferenceProperty("idleConnectionTestPeriod");
	referenceMaker.addReferenceProperty("initialPoolSize");
	referenceMaker.addReferenceProperty("maxIdleTime");
	referenceMaker.addReferenceProperty("maxPoolSize");
	referenceMaker.addReferenceProperty("maxStatements");
	referenceMaker.addReferenceProperty("minPoolSize");
	referenceMaker.addReferenceProperty("nestedDataSource");
	referenceMaker.addReferenceProperty("propertyCycle");
	referenceMaker.addReferenceProperty("testConnectionOnCheckout");

	// PoolBackedDataSource properties
	referenceMaker.addReferenceProperty("numHelperThreads");

	// shared properties
	referenceMaker.addReferenceProperty("factoryClassLocation");
    }
    
    public Reference getReference() throws NamingException
    { return referenceMaker.createReference( this ); }

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

    public int getNumConnections(String username, String password) throws SQLException
    { return pbds.getNumConnections(); }

    public int getNumIdleConnections(String username, String password) throws SQLException
    { return pbds.getNumIdleConnections(); }

    public int getNumBusyConnections(String username, String password) throws SQLException
    { return pbds.getNumBusyConnections(); }

    public int getNumConnectionsAllAuths() throws SQLException
    { return pbds.getNumConnectionsAllAuths(); }

    public void close()
    { pbds.close(); }
}

