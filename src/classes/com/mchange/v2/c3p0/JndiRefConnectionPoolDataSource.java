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

import com.mchange.v2.c3p0.impl.*;

import java.beans.PropertyVetoException;
import java.io.PrintWriter;
import java.io.Serializable;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Hashtable;
import javax.naming.NamingException;
import javax.naming.Reference;
import javax.naming.Referenceable;
import javax.sql.ConnectionPoolDataSource;
import javax.sql.PooledConnection;
import com.mchange.v2.beans.BeansUtils;
import com.mchange.v2.log.MLevel;
import com.mchange.v2.log.MLog;
import com.mchange.v2.log.MLogger;
import com.mchange.v2.naming.JavaBeanReferenceMaker;
import com.mchange.v2.naming.JavaBeanObjectFactory;
import com.mchange.v2.naming.ReferenceMaker;

public final class JndiRefConnectionPoolDataSource extends IdentityTokenResolvable implements ConnectionPoolDataSource, Serializable, Referenceable
{
    final static MLogger logger = MLog.getLogger( JndiRefConnectionPoolDataSource.class );

    final static Collection IGNORE_PROPS = Arrays.asList( new String[] {"reference", "pooledConnection"} );

    JndiRefForwardingDataSource     jrfds;
    WrapperConnectionPoolDataSource wcpds;

    String identityToken;

    {
	jrfds = new JndiRefForwardingDataSource();
	wcpds = new WrapperConnectionPoolDataSource();
	wcpds.setNestedDataSource( jrfds );

	this.identityToken = C3P0ImplUtils.identityToken( this );
	C3P0Registry.register( this );
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
	
    public int getAcquireRetryAttempts()
    { return wcpds.getAcquireRetryAttempts(); }
	
    public void setAcquireRetryAttempts( int ara )
    { wcpds.setAcquireRetryAttempts( ara ); }
	
    public int getAcquireRetryDelay()
    { return wcpds.getAcquireRetryDelay(); }
	
    public void setAcquireRetryDelay( int ard )
    { wcpds.setAcquireRetryDelay( ard ); }
	
    public boolean isAutoCommitOnClose()
    { return wcpds.isAutoCommitOnClose(); }

    public void setAutoCommitOnClose( boolean autoCommitOnClose )
    { wcpds.setAutoCommitOnClose( autoCommitOnClose ); }
	
    public void setAutomaticTestTable( String att )
    { wcpds.setAutomaticTestTable( att ); }
	
    public String getAutomaticTestTable()
    { return wcpds.getAutomaticTestTable(); }
	
    public void setBreakAfterAcquireFailure( boolean baaf )
    { wcpds.setBreakAfterAcquireFailure( baaf ); }
	
    public boolean isBreakAfterAcquireFailure()
    { return wcpds.isBreakAfterAcquireFailure(); }

    public void setCheckoutTimeout( int ct )
    { wcpds.setCheckoutTimeout( ct ); }

    public int getCheckoutTimeout()
    { return wcpds.getCheckoutTimeout(); }
	
    public String getConnectionTesterClassName()
    { return wcpds.getConnectionTesterClassName(); }
	
    public void setConnectionTesterClassName( String connectionTesterClassName ) throws PropertyVetoException
    { wcpds.setConnectionTesterClassName( connectionTesterClassName ); }
	
    public boolean isForceIgnoreUnresolvedTransactions()
    { return wcpds.isForceIgnoreUnresolvedTransactions(); }
	
    public void setForceIgnoreUnresolvedTransactions( boolean forceIgnoreUnresolvedTransactions )
    { wcpds.setForceIgnoreUnresolvedTransactions( forceIgnoreUnresolvedTransactions ); }
	
    public String getIdentityToken()
    { return identityToken; }
	
    public void setIdentityToken(String identityToken)
    { this.identityToken = identityToken; }
	
    public void setIdleConnectionTestPeriod( int idleConnectionTestPeriod )
    { wcpds.setIdleConnectionTestPeriod( idleConnectionTestPeriod ); }
    
    public int getIdleConnectionTestPeriod()
    { return wcpds.getIdleConnectionTestPeriod(); }
	
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
	
    public int getMaxStatementsPerConnection()
    { return wcpds.getMaxStatementsPerConnection(); }
	
    public void setMaxStatementsPerConnection( int mspc )
    { wcpds.setMaxStatementsPerConnection( mspc ); }
	
    public int getMinPoolSize()
    { return wcpds.getMinPoolSize(); }
	
    public void setMinPoolSize( int minPoolSize )
    { wcpds.setMinPoolSize( minPoolSize ); }
	
    public String getPreferredTestQuery()
    { return wcpds.getPreferredTestQuery(); }
	
    public void setPreferredTestQuery( String ptq )
    { wcpds.setPreferredTestQuery( ptq ); }
	
    public int getPropertyCycle()
    { return wcpds.getPropertyCycle(); }
	
    public void setPropertyCycle( int propertyCycle )
    { wcpds.setPropertyCycle( propertyCycle ); }
	
    public boolean isTestConnectionOnCheckin()
    { return wcpds.isTestConnectionOnCheckin(); }
	
    public void setTestConnectionOnCheckin( boolean testConnectionOnCheckin )
    { wcpds.setTestConnectionOnCheckin( testConnectionOnCheckin ); }
	
    public boolean isTestConnectionOnCheckout()
    { return wcpds.isTestConnectionOnCheckout(); }
	
    public void setTestConnectionOnCheckout( boolean testConnectionOnCheckout )
    { wcpds.setTestConnectionOnCheckout( testConnectionOnCheckout ); }
	
    public boolean isUsesTraditionalReflectiveProxies()
    { return wcpds.isUsesTraditionalReflectiveProxies(); }
	
    public void setUsesTraditionalReflectiveProxies( boolean utrp )
    { wcpds.setUsesTraditionalReflectiveProxies( utrp ); }
	
    public String getFactoryClassLocation()
    { return jrfds.getFactoryClassLocation(); }

    public void setFactoryClassLocation( String factoryClassLocation )
    { 
	jrfds.setFactoryClassLocation( factoryClassLocation );
	wcpds.setFactoryClassLocation( factoryClassLocation );
    }

    final static JavaBeanReferenceMaker referenceMaker = new JavaBeanReferenceMaker();
    
    static
    {
	referenceMaker.setFactoryClassName( C3P0JavaBeanObjectFactory.class.getName() );
	referenceMaker.addReferenceProperty("acquireIncrement");
	referenceMaker.addReferenceProperty("acquireRetryAttempts");
	referenceMaker.addReferenceProperty("acquireRetryDelay");
	referenceMaker.addReferenceProperty("autoCommitOnClose");
	referenceMaker.addReferenceProperty("automaticTestTable");
	referenceMaker.addReferenceProperty("checkoutTimeout");
	referenceMaker.addReferenceProperty("connectionTesterClassName");
	referenceMaker.addReferenceProperty("factoryClassLocation");
	referenceMaker.addReferenceProperty("forceIgnoreUnresolvedTransactions");
	referenceMaker.addReferenceProperty("idleConnectionTestPeriod");
	referenceMaker.addReferenceProperty("identityToken");
	referenceMaker.addReferenceProperty("initialPoolSize");
	referenceMaker.addReferenceProperty("jndiEnv");
	referenceMaker.addReferenceProperty("jndiLookupCaching");
	referenceMaker.addReferenceProperty("jndiName");
	referenceMaker.addReferenceProperty("maxIdleTime");
	referenceMaker.addReferenceProperty("maxPoolSize");
	referenceMaker.addReferenceProperty("maxStatements");
	referenceMaker.addReferenceProperty("maxStatementsPerConnection");
	referenceMaker.addReferenceProperty("minPoolSize");
	referenceMaker.addReferenceProperty("preferredTestQuery");
	referenceMaker.addReferenceProperty("propertyCycle");
	referenceMaker.addReferenceProperty("testConnectionOnCheckin");
	referenceMaker.addReferenceProperty("testConnectionOnCheckout");
	referenceMaker.addReferenceProperty("usesTraditionalReflectiveProxies");
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

    public String toString()
    {
	StringBuffer sb = new StringBuffer(512);
	sb.append( super.toString() );
	sb.append(" [");
	try { BeansUtils.appendPropNamesAndValues( sb, this, IGNORE_PROPS ); }
	catch (Exception e)
	    {
		//e.printStackTrace();
		if ( Debug.DEBUG && logger.isLoggable( MLevel.FINE ) )
		    logger.log( MLevel.FINE, "An exception occurred while extracting property names and values for toString()", e);
		sb.append( e.toString() ); 
	    }
	sb.append("]");
	return sb.toString();
    }
}

