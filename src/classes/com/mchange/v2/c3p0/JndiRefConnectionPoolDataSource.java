/*
 * Distributed as part of c3p0 v.0.8.4.1
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

import java.beans.PropertyVetoException;
import java.io.PrintWriter;
import java.io.Serializable;
import java.sql.SQLException;
import java.util.Hashtable;
import javax.naming.NamingException;
import javax.naming.Reference;
import javax.naming.Referenceable;
import javax.sql.ConnectionPoolDataSource;
import javax.sql.PooledConnection;
import com.mchange.v2.naming.JavaBeanReferenceMaker;
import com.mchange.v2.naming.JavaBeanObjectFactory;
import com.mchange.v2.naming.ReferenceMaker;

public final class JndiRefConnectionPoolDataSource implements ConnectionPoolDataSource, Serializable, Referenceable
{
    JndiRefForwardingDataSource     jrfds;
    WrapperConnectionPoolDataSource wcpds;

    {
	jrfds = new JndiRefForwardingDataSource();
	wcpds = new WrapperConnectionPoolDataSource();
	wcpds.setNestedDataSource( jrfds );
    }

    public boolean isJndiLookupCaching()
    { return jrfds.isCaching();  }
    
    public void setJndiLookupCaching( boolean caching )
    { jrfds.setCaching( caching ); }
    
    public Hashtable getJndiEnv()
    { return jrfds.getJndiEnv(); }
    
    public void setJndiEnv( Hashtable jndiEnv )
    { jrfds.setJndiEnv( jndiEnv ); }
    
    public Object getJndiName()
    { return jrfds.getJndiName(); }
    
    public void setJndiName( Object jndiName ) throws PropertyVetoException
    { jrfds.setJndiName( jndiName ); }

    public int getAcquireIncrement()
    { return wcpds.getAcquireIncrement(); }
	
    public void setAcquireIncrement( int acquireIncrement )
    { wcpds.setAcquireIncrement( acquireIncrement ); }
	
    public boolean isAutoCommitOnClose()
    { return wcpds.isAutoCommitOnClose(); }

    public void setAutoCommitOnClose( boolean autoCommitOnClose )
    { wcpds.setAutoCommitOnClose( autoCommitOnClose ); }
	
    public String getConnectionTesterClassName()
    { return wcpds.getConnectionTesterClassName(); }
	
    public void setConnectionTesterClassName( String connectionTesterClassName ) throws PropertyVetoException
    { wcpds.setConnectionTesterClassName( connectionTesterClassName ); }
	
    public boolean isForceIgnoreUnresolvedTransactions()
    { return wcpds.isForceIgnoreUnresolvedTransactions(); }
	
    public void setForceIgnoreUnresolvedTransactions( boolean forceIgnoreUnresolvedTransactions )
    { wcpds.setForceIgnoreUnresolvedTransactions( forceIgnoreUnresolvedTransactions ); }
	
    public int getIdleConnectionTestPeriod()
    { return wcpds.getIdleConnectionTestPeriod(); }
	
    public void setIdleConnectionTestPeriod( int idleConnectionTestPeriod )
    { wcpds.setIdleConnectionTestPeriod( idleConnectionTestPeriod ); }
    
    public int getInitialPoolSize()
    { return wcpds.getInitialPoolSize(); }
	
    public void setInitialPoolSize( int initialPoolSize )
    { wcpds.setInitialPoolSize( initialPoolSize ); }

    public int getMaxIdleTime()
    { return wcpds.getMaxIdleTime(); }
	
    public void setMaxIdleTime( int maxIdleTime )
    { wcpds.setMaxIdleTime( maxIdleTime ); }
	
    public int getMaxPoolSize()
    { return wcpds.getMaxPoolSize(); }
	
    public void setMaxPoolSize( int maxPoolSize )
    { wcpds.setMaxPoolSize( maxPoolSize ); }
	
    public int getMaxStatements()
    { return wcpds.getMaxStatements(); }
	
    public void setMaxStatements( int maxStatements )
    { wcpds.setMaxStatements( maxStatements ); }
	
    public int getMinPoolSize()
    { return wcpds.getMinPoolSize(); }
	
    public void setMinPoolSize( int minPoolSize )
    { wcpds.setMinPoolSize( minPoolSize ); }
	
    public int getPropertyCycle()
    { return wcpds.getPropertyCycle(); }
	
    public void setPropertyCycle( int propertyCycle )
    { wcpds.setPropertyCycle( propertyCycle ); }
	
    public boolean isTestConnectionOnCheckout()
    { return wcpds.isTestConnectionOnCheckout(); }
	
    public void setTestConnectionOnCheckout( boolean testConnectionOnCheckout )
    { wcpds.setTestConnectionOnCheckout( testConnectionOnCheckout ); }
	
    public String getFactoryClassLocation()
    {
	return jrfds.getFactoryClassLocation();
    }
    
    public void setFactoryClassLocation( String factoryClassLocation )
    {
	jrfds.setFactoryClassLocation( factoryClassLocation );
	wcpds.setFactoryClassLocation( factoryClassLocation );
    }

    final static JavaBeanReferenceMaker referenceMaker = new JavaBeanReferenceMaker();
    
    static
    {
	referenceMaker.setFactoryClassName( JavaBeanObjectFactory.class.getName() );
	referenceMaker.addReferenceProperty("acquireIncrement");
	referenceMaker.addReferenceProperty("autoCommitOnClose");
	referenceMaker.addReferenceProperty("connectionTesterClassName");
	referenceMaker.addReferenceProperty("factoryClassLocation");
	referenceMaker.addReferenceProperty("forceIgnoreUnresolvedTransactions");
	referenceMaker.addReferenceProperty("idleConnectionTestPeriod");
	referenceMaker.addReferenceProperty("initialPoolSize");
	referenceMaker.addReferenceProperty("jndiEnv");
	referenceMaker.addReferenceProperty("jndiLookupCaching");
	referenceMaker.addReferenceProperty("jndiName");
	referenceMaker.addReferenceProperty("maxIdleTime");
	referenceMaker.addReferenceProperty("maxPoolSize");
	referenceMaker.addReferenceProperty("maxStatements");
	referenceMaker.addReferenceProperty("minPoolSize");
	referenceMaker.addReferenceProperty("nestedDataSource");
	referenceMaker.addReferenceProperty("propertyCycle");
	referenceMaker.addReferenceProperty("testConnectionOnCheckout");
    }
    
    public Reference getReference() throws NamingException
    { return referenceMaker.createReference( this ); }

    //implementation of javax.sql.ConnectionPoolDataSource
    public PooledConnection getPooledConnection()
	throws SQLException
    { return wcpds.getPooledConnection(); } 
 
    public PooledConnection getPooledConnection(String user, String password)
	throws SQLException
    { return wcpds.getPooledConnection( user, password ); } 
 
    public PrintWriter getLogWriter()
	throws SQLException
    { return wcpds.getLogWriter(); }

    public void setLogWriter(PrintWriter out)
	throws SQLException
    { wcpds.setLogWriter( out ); }

    public void setLoginTimeout(int seconds)
	throws SQLException
    { wcpds.setLoginTimeout( seconds ); }

    public int getLoginTimeout()
	throws SQLException
    { return wcpds.getLoginTimeout(); }
}

