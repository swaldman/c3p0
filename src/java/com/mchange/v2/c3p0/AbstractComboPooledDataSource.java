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

import java.beans.*;
import java.io.*;
import java.sql.*;
import java.util.*;
import javax.naming.*;
import com.mchange.v2.log.*;
import com.mchange.v2.naming.*;
import com.mchange.v2.c3p0.impl.*;

import javax.sql.DataSource;
import com.mchange.v2.beans.BeansUtils;
import com.mchange.v2.c3p0.cfg.C3P0Config;
import com.mchange.v2.lang.ObjectUtils;

/**
 * <p>For the meaning of most of these properties, please see c3p0's top-level documentation!</p>
 */
public abstract class AbstractComboPooledDataSource extends AbstractPoolBackedDataSource implements PooledDataSource, Serializable, Referenceable
{
    final static MLogger logger = MLog.getLogger( AbstractComboPooledDataSource.class );

    final static Set TO_STRING_IGNORE_PROPS = new HashSet( Arrays.asList( new String[] { 
                    "connection",
                    "lastAcquisitionFailureDefaultUser",
                    "lastCheckinFailureDefaultUser",
                    "lastCheckoutFailureDefaultUser",
                    "lastConnectionTestFailureDefaultUser",
                    "lastIdleTestFailureDefaultUser",
                    "logWriter",
                    "loginTimeout",
                    "numBusyConnections",
                    "numBusyConnectionsAllUsers",
                    "numBusyConnectionsDefaultUser",
                    "numConnections",
                    "numConnectionsAllUsers",
                    "numConnectionsDefaultUser",
                    "numFailedCheckinsDefaultUser",
                    "numFailedCheckoutsDefaultUser",
                    "numFailedIdleTestsDefaultUser",
                    "numIdleConnections",
                    "numIdleConnectionsAllUsers",
                    "numThreadsAwaitingCheckoutDefaultUser",
                    "numIdleConnectionsDefaultUser",
                    "numUnclosedOrphanedConnections",
                    "numUnclosedOrphanedConnectionsAllUsers",
                    "numUnclosedOrphanedConnectionsDefaultUser",
                    "numUserPools",
                    "effectivePropertyCycleDefaultUser",
                    "parentLogger",
                    "startTimeMillisDefaultUser",
                    "statementCacheNumCheckedOutDefaultUser",
                    "statementCacheNumCheckedOutStatementsAllUsers",
                    "statementCacheNumConnectionsWithCachedStatementsAllUsers",
                    "statementCacheNumConnectionsWithCachedStatementsDefaultUser",
                    "statementCacheNumStatementsAllUsers",
                    "statementCacheNumStatementsDefaultUser",
		    "statementDestroyerNumConnectionsInUseAllUsers",
		    "statementDestroyerNumConnectionsWithDeferredDestroyStatementsAllUsers",
		    "statementDestroyerNumDeferredDestroyStatementsAllUsers",
		    "statementDestroyerNumConnectionsInUseDefaultUser",
		    "statementDestroyerNumConnectionsWithDeferredDestroyStatementsDefaultUser",
		    "statementDestroyerNumDeferredDestroyStatementsDefaultUser",
		    "statementDestroyerNumThreads",
		    "statementDestroyerNumActiveThreads",
		    "statementDestroyerNumIdleThreads",
		    "statementDestroyerNumTasksPending",
                    "threadPoolSize",
                    "threadPoolNumActiveThreads",
                    "threadPoolNumIdleThreads",
                    "threadPoolNumTasksPending",
                    "threadPoolStackTraces",
                    "threadPoolStatus",
                    "overrideDefaultUser",
                    "overrideDefaultPassword",
                    "password",
                    "reference",
                    "upTimeMillisDefaultUser",
                    "user",
                    "userOverridesAsString",
                    "allUsers",
                    "connectionPoolDataSource",
		    "propertyChangeListeners",
		    "vetoableChangeListeners"
    } ) );

    // just so we have a unified syntax when we guard against degenerate parameter changes, which
    // otherwise might needlessly reset pools.
    private static boolean diff( int a, int b ) { return a != b; }
    private static boolean diff( boolean a, boolean b ) { return a != b; }
    private static boolean diff( Object a, Object b ) {	return !ObjectUtils.eqOrBothNull(a, b ); }

    // not reassigned post-ctor; mutable elements protected by their own locks
    // when (very rarely) necessery, we sync this -> wcpds -> dmds

    // note that serialization of these guys happens via our superclass
    // we just have to make sure they get properly reset on deserialization
    transient DriverManagerDataSource         dmds;
    transient WrapperConnectionPoolDataSource wcpds;

    public AbstractComboPooledDataSource()
    { this( true ); }

    public AbstractComboPooledDataSource( boolean autoregister )
    {
        super( autoregister );

        // System.err.println("...Initializing ComboPooledDataSource.");

        dmds  = new DriverManagerDataSource();
        wcpds = new WrapperConnectionPoolDataSource();

        wcpds.setNestedDataSource( dmds );

        try
        { this.setConnectionPoolDataSource( wcpds ); }
        catch (PropertyVetoException e)
        {
            logger.log(MLevel.WARNING, "Hunh??? This can't happen. We haven't set up any listeners to veto the property change yet!", e);
            throw new RuntimeException("Hunh??? This can't happen. We haven't set up any listeners to veto the property change yet! " + e);
        }

        // set things up in case there are future changes to our ConnectionPoolDataSource
        //
        setUpPropertyEvents();
    }

    private void setUpPropertyEvents()
    {
        VetoableChangeListener wcpdsConsistencyEnforcer = new VetoableChangeListener()
        {
            // always called within synchronized mutators of the parent class... needn't explicitly sync here
            public void vetoableChange( PropertyChangeEvent evt ) throws PropertyVetoException
            {
                String propName = evt.getPropertyName();
                Object val = evt.getNewValue();

                if ( "connectionPoolDataSource".equals( propName ) )
                {
                    if (val instanceof WrapperConnectionPoolDataSource)
                    {
                        DataSource nested = (DataSource) ((WrapperConnectionPoolDataSource)val).getNestedDataSource();
                        if (! (nested instanceof DriverManagerDataSource) )
                            throw new PropertyVetoException(this.getClass().getName() + " requires that its unpooled DataSource " +
                                            " be set at all times, and that it be a" +
                                            " com.mchange.v2.c3p0.DriverManagerDataSource. Bad: " + nested, evt);
                    }
                    else
                        throw new PropertyVetoException(this.getClass().getName() + " requires that its ConnectionPoolDataSource " +
                                        " be set at all times, and that it be a" +
                                        " com.mchange.v2.c3p0.WrapperConnectionPoolDataSource. Bad: " + val, evt);
                }
            }
        };
        this.addVetoableChangeListener( wcpdsConsistencyEnforcer );

        PropertyChangeListener wcpdsStateUpdater = new PropertyChangeListener()
        {
            public void propertyChange( PropertyChangeEvent evt )
            { 
                String propName = evt.getPropertyName();
                Object val = evt.getNewValue();

                if ( "connectionPoolDataSource".equals( propName ) )
		    updateLocalVarsFromCpdsProp(); 
	    }
        };
        this.addPropertyChangeListener( wcpdsStateUpdater );
    }

    private void updateLocalVarsFromCpdsProp()
    {
        this.wcpds = (WrapperConnectionPoolDataSource) this.getConnectionPoolDataSource();
        this.dmds  = (DriverManagerDataSource) wcpds.getNestedDataSource();
    }

    public AbstractComboPooledDataSource(String configName)
    { 
        this();
        initializeNamedConfig( configName, true );
    }

//  // workaround sun big id #6342411 (in which reflective
//  // access to a public method of a non-public class fails,
//  // even if the non-public class is accessed via a public
//  // subclass)
//  public String getDataSourceName()
//  { return super.getDataSourceName(); }

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
//      System.err.println("setting driverClass: " + driverClass); 
    }

    public boolean isForceUseNamedDriverClass()
    { return dmds.isForceUseNamedDriverClass(); }

    public void setForceUseNamedDriverClass( boolean forceUseNamedDriverClass )
    { 
        dmds.setForceUseNamedDriverClass( forceUseNamedDriverClass ); 
    }

    public String getJdbcUrl()
    {  
//      System.err.println("getting jdbcUrl: " + dmds.getJdbcUrl()); 
        return dmds.getJdbcUrl(); 
    }

    public void setJdbcUrl( String jdbcUrl )
    { 
	if ( diff( dmds.getJdbcUrl(), jdbcUrl ) )
	{
	    dmds.setJdbcUrl( jdbcUrl ); 
	    this.resetPoolManager( false );
//          System.err.println("setting jdbcUrl: " + jdbcUrl + " [dmds@" + C3P0ImplUtils.identityToken( dmds ) + "]"); 
//          if (jdbcUrl == null)
//          new Exception("*** NULL SETTER ***").printStackTrace();
	}
    }

    public Properties getProperties()
    { 
        //System.err.println("getting properties: " + dmds.getProperties()); 
        return dmds.getProperties(); 
    }

    public void setProperties( Properties properties )
    { 
	if ( diff( dmds.getProperties(), properties ) )
	{
	    //System.err.println("setting properties: " + properties); 
	    dmds.setProperties( properties ); 
	    this.resetPoolManager(false);
	}
    }

    // DriverManagerDataSource "virtual properties" based on properties
    public String getUser()
    { return dmds.getUser(); }

    public void setUser( String user )
    {
	if ( diff( dmds.getUser(), user ) )
	{
	    dmds.setUser( user ); 
	    this.resetPoolManager( false );
	}
    }

    public String getPassword()
    { return dmds.getPassword(); }

    public void setPassword( String password )
    { 
	if ( diff( dmds.getPassword(), password ) )
	{
	    dmds.setPassword( password ); 
	    this.resetPoolManager( false );
	}
    }

    // WrapperConnectionPoolDataSource properties
    public int getCheckoutTimeout()
    { return wcpds.getCheckoutTimeout(); }

    public void setCheckoutTimeout( int checkoutTimeout )
    { 
	if ( diff( wcpds.getCheckoutTimeout(), checkoutTimeout ) )
	{
	    wcpds.setCheckoutTimeout( checkoutTimeout ); 
	    this.resetPoolManager( false );
	}
    }

    public int getAcquireIncrement()
    { return wcpds.getAcquireIncrement(); }

    public void setAcquireIncrement( int acquireIncrement )
    {
	if ( diff( wcpds.getAcquireIncrement(), acquireIncrement ) )
	{
	    wcpds.setAcquireIncrement( acquireIncrement ); 
	    this.resetPoolManager( false );
	}
    }

    public int getAcquireRetryAttempts()
    { return wcpds.getAcquireRetryAttempts(); }

    public void setAcquireRetryAttempts( int acquireRetryAttempts )
    { 
	if ( diff( wcpds.getAcquireRetryAttempts(), acquireRetryAttempts ) )
	{
	    wcpds.setAcquireRetryAttempts( acquireRetryAttempts ); 
	    this.resetPoolManager( false );
	}
    }

    public int getAcquireRetryDelay()
    { return wcpds.getAcquireRetryDelay(); }

    public void setAcquireRetryDelay( int acquireRetryDelay )
    {
	if ( diff( wcpds.getAcquireRetryDelay(), acquireRetryDelay ) )
	{
	    wcpds.setAcquireRetryDelay( acquireRetryDelay ); 
	    this.resetPoolManager( false );
	}
    }

    public boolean isAutoCommitOnClose()
    { return wcpds.isAutoCommitOnClose(); }

    public void setAutoCommitOnClose( boolean autoCommitOnClose )
    { 
	if ( diff(wcpds.isAutoCommitOnClose(), autoCommitOnClose) )
	{
	    wcpds.setAutoCommitOnClose( autoCommitOnClose ); 
	    this.resetPoolManager( false );
	}
    }

    public String getContextClassLoaderSource()
    { return wcpds.getContextClassLoaderSource(); }

    public void setContextClassLoaderSource( String contextClassLoaderSource ) throws PropertyVetoException
    {
	if ( diff( wcpds.getContextClassLoaderSource(), contextClassLoaderSource ) )
	{
	    wcpds.setContextClassLoaderSource( contextClassLoaderSource ); 
	    this.resetPoolManager( false );
	}
    }

    public String getConnectionTesterClassName()
    { return wcpds.getConnectionTesterClassName(); }

    public void setConnectionTesterClassName( String connectionTesterClassName ) throws PropertyVetoException
    { 
	if ( diff( wcpds.getConnectionTesterClassName(), connectionTesterClassName ) )
	{
	    wcpds.setConnectionTesterClassName( connectionTesterClassName ); 
	    this.resetPoolManager( false );
	}
    }

    public String getAutomaticTestTable()
    { return wcpds.getAutomaticTestTable(); }

    public void setAutomaticTestTable( String automaticTestTable )
    { 
	if ( diff( wcpds.getAutomaticTestTable(), automaticTestTable ) )
	{
	    wcpds.setAutomaticTestTable( automaticTestTable ); 
	    this.resetPoolManager( false );
	}
    }

    public boolean isForceIgnoreUnresolvedTransactions()
    { return wcpds.isForceIgnoreUnresolvedTransactions(); }

    public void setForceIgnoreUnresolvedTransactions( boolean forceIgnoreUnresolvedTransactions )
    { 
	if ( diff( wcpds.isForceIgnoreUnresolvedTransactions(), forceIgnoreUnresolvedTransactions ) )
	{
	    wcpds.setForceIgnoreUnresolvedTransactions( forceIgnoreUnresolvedTransactions ); 
	    this.resetPoolManager( false );
	}
    }

    public boolean isPrivilegeSpawnedThreads()
    { return wcpds.isPrivilegeSpawnedThreads(); }

    public void setPrivilegeSpawnedThreads( boolean privilegeSpawnedThreads )
    { 
	if ( diff( wcpds.isPrivilegeSpawnedThreads(), privilegeSpawnedThreads ) )
	{
	    wcpds.setPrivilegeSpawnedThreads( privilegeSpawnedThreads ); 
	    this.resetPoolManager( false );
	}
    }

    public int getIdleConnectionTestPeriod()
    { return wcpds.getIdleConnectionTestPeriod(); }

    public void setIdleConnectionTestPeriod( int idleConnectionTestPeriod )
    { 
	if ( diff( wcpds.getIdleConnectionTestPeriod(), idleConnectionTestPeriod ) )
	{
	    wcpds.setIdleConnectionTestPeriod( idleConnectionTestPeriod ); 
	    this.resetPoolManager( false );
	}
    }

    public int getInitialPoolSize()
    { return wcpds.getInitialPoolSize(); }

    public void setInitialPoolSize( int initialPoolSize )
    { 
	if ( diff( wcpds.getInitialPoolSize(), initialPoolSize ) )
	{
	    wcpds.setInitialPoolSize( initialPoolSize ); 
	    this.resetPoolManager( false );
	}
    }

    public int getMaxIdleTime()
    { return wcpds.getMaxIdleTime(); }

    public void setMaxIdleTime( int maxIdleTime )
    { 
	if ( diff( wcpds.getMaxIdleTime(), maxIdleTime ) )
	{
	    wcpds.setMaxIdleTime( maxIdleTime ); 
	    this.resetPoolManager( false );
	}
    }

    public int getMaxPoolSize()
    { return wcpds.getMaxPoolSize(); }

    public void setMaxPoolSize( int maxPoolSize )
    { 
	if ( diff( wcpds.getMaxPoolSize(), maxPoolSize ) )
	{
	    wcpds.setMaxPoolSize( maxPoolSize ); 
	    this.resetPoolManager( false );
	}
    }

    public int getMaxStatements()
    { return wcpds.getMaxStatements(); }

    public void setMaxStatements( int maxStatements )
    { 
	if ( diff( wcpds.getMaxStatements(), maxStatements ) )
	{
	    wcpds.setMaxStatements( maxStatements ); 
	    this.resetPoolManager( false );
	}
    }

    public int getMaxStatementsPerConnection()
    { return wcpds.getMaxStatementsPerConnection(); }

    public void setMaxStatementsPerConnection( int maxStatementsPerConnection )
    { 
	if ( diff( wcpds.getMaxStatementsPerConnection(), maxStatementsPerConnection ) )
	{
	    wcpds.setMaxStatementsPerConnection( maxStatementsPerConnection ); 
	    this.resetPoolManager( false );
	}
    }

    public int getMinPoolSize()
    { return wcpds.getMinPoolSize(); }

    public void setMinPoolSize( int minPoolSize )
    { 
	if ( diff( wcpds.getMinPoolSize(), minPoolSize ) )
	{
	    wcpds.setMinPoolSize( minPoolSize ); 
	    this.resetPoolManager( false );
	}
    }

    public String getOverrideDefaultUser()
    { return wcpds.getOverrideDefaultUser(); }

    public void setOverrideDefaultUser(String overrideDefaultUser)
    { 
	if ( diff( wcpds.getOverrideDefaultUser(), overrideDefaultUser ) )
	{
	    wcpds.setOverrideDefaultUser( overrideDefaultUser ); 
	    this.resetPoolManager( false );
	}
    }

    public String getOverrideDefaultPassword()
    { return wcpds.getOverrideDefaultPassword(); }

    public void setOverrideDefaultPassword(String overrideDefaultPassword)
    { 
	if ( diff( wcpds.getOverrideDefaultPassword(), overrideDefaultPassword ) )
	{
	    wcpds.setOverrideDefaultPassword( overrideDefaultPassword ); 
	    this.resetPoolManager( false );
	}
    }

    public int getPropertyCycle()
    { return wcpds.getPropertyCycle(); }

    public void setPropertyCycle( int propertyCycle )
    { 
	if ( diff( wcpds.getPropertyCycle(), propertyCycle ) )
	{
	    wcpds.setPropertyCycle( propertyCycle ); 
	    this.resetPoolManager( false );
	}
    }

    public boolean isBreakAfterAcquireFailure()
    { return wcpds.isBreakAfterAcquireFailure(); }

    public void setBreakAfterAcquireFailure( boolean breakAfterAcquireFailure )
    {
	if ( diff( wcpds.isBreakAfterAcquireFailure(), breakAfterAcquireFailure ) )
	{
	    wcpds.setBreakAfterAcquireFailure( breakAfterAcquireFailure ); 
	    this.resetPoolManager( false );
	}
    }

    public boolean isTestConnectionOnCheckout()
    { return wcpds.isTestConnectionOnCheckout(); }

    public void setTestConnectionOnCheckout( boolean testConnectionOnCheckout )
    { 
	if ( diff( wcpds.isTestConnectionOnCheckout(), testConnectionOnCheckout ) )
	{
	    wcpds.setTestConnectionOnCheckout( testConnectionOnCheckout ); 
	    this.resetPoolManager( false );
	}
    }

    public boolean isTestConnectionOnCheckin()
    { return wcpds.isTestConnectionOnCheckin(); }

    public void setTestConnectionOnCheckin( boolean testConnectionOnCheckin )
    { 
	if ( diff( wcpds.isTestConnectionOnCheckin(), testConnectionOnCheckin ) )
	{
	    wcpds.setTestConnectionOnCheckin( testConnectionOnCheckin ); 
	    this.resetPoolManager( false );
	}
    }

    public boolean isUsesTraditionalReflectiveProxies()
    { return wcpds.isUsesTraditionalReflectiveProxies(); }

    public void setUsesTraditionalReflectiveProxies( boolean usesTraditionalReflectiveProxies )
    {
	if ( diff( wcpds.isUsesTraditionalReflectiveProxies(), usesTraditionalReflectiveProxies ) )
	{
	    wcpds.setUsesTraditionalReflectiveProxies( usesTraditionalReflectiveProxies ); 
	    this.resetPoolManager( false );
	}
    }

    public String getPreferredTestQuery()
    { return wcpds.getPreferredTestQuery(); }

    public void setPreferredTestQuery( String preferredTestQuery )
    { 
	if ( diff( wcpds.getPreferredTestQuery(), preferredTestQuery ) )
	{
	    wcpds.setPreferredTestQuery( preferredTestQuery ); 
	    this.resetPoolManager( false );
	}
    }

    public int getMaxAdministrativeTaskTime()
    { return wcpds.getMaxAdministrativeTaskTime(); }

    public void setMaxAdministrativeTaskTime( int maxAdministrativeTaskTime )
    { 
	if ( diff( wcpds.getMaxAdministrativeTaskTime(), maxAdministrativeTaskTime ) )
	{
	    wcpds.setMaxAdministrativeTaskTime( maxAdministrativeTaskTime ); 
	    this.resetPoolManager( false );
	}
    }

    public int getMaxIdleTimeExcessConnections()
    { return wcpds.getMaxIdleTimeExcessConnections(); }

    public void setMaxIdleTimeExcessConnections( int maxIdleTimeExcessConnections )
    { 
	if ( diff( wcpds.getMaxIdleTimeExcessConnections(), maxIdleTimeExcessConnections ) )
	{
	    wcpds.setMaxIdleTimeExcessConnections( maxIdleTimeExcessConnections ); 
	    this.resetPoolManager( false );
	}
    }

    public int getMaxConnectionAge()
    { return wcpds.getMaxConnectionAge(); }

    public void setMaxConnectionAge( int maxConnectionAge )
    { 
	if ( diff( wcpds.getMaxConnectionAge(), maxConnectionAge ) )
	{
	    wcpds.setMaxConnectionAge( maxConnectionAge ); 
	    this.resetPoolManager( false );
	}
    }

    public String getConnectionCustomizerClassName()
    { return wcpds.getConnectionCustomizerClassName(); }

    public void setConnectionCustomizerClassName( String connectionCustomizerClassName )
    { 
	if ( diff( wcpds.getConnectionCustomizerClassName(), connectionCustomizerClassName ) )
	{
	    wcpds.setConnectionCustomizerClassName( connectionCustomizerClassName ); 
	    this.resetPoolManager( false );
	}
    }

    public int getUnreturnedConnectionTimeout()
    { return wcpds.getUnreturnedConnectionTimeout(); }

    public void setUnreturnedConnectionTimeout(int unreturnedConnectionTimeout)
    {
	if ( diff( wcpds.getUnreturnedConnectionTimeout(), unreturnedConnectionTimeout ) )
	{
	    wcpds.setUnreturnedConnectionTimeout( unreturnedConnectionTimeout ); 
	    this.resetPoolManager( false );
	}
    }

    public String getUserOverridesAsString()
    { return wcpds.getUserOverridesAsString(); }

    public void setUserOverridesAsString(String uoas) throws PropertyVetoException
    {
	if ( diff( wcpds.getUserOverridesAsString(), uoas ) )
	{
	    wcpds.setUserOverridesAsString( uoas ); 
	    this.resetPoolManager( false );
	}
    }

    public Map getUserOverrides()
    { return wcpds.getUserOverrides(); }

    public boolean isDebugUnreturnedConnectionStackTraces()
    { return wcpds.isDebugUnreturnedConnectionStackTraces(); }

    public void setDebugUnreturnedConnectionStackTraces(boolean debugUnreturnedConnectionStackTraces)
    {
	if ( diff( wcpds.isDebugUnreturnedConnectionStackTraces(), debugUnreturnedConnectionStackTraces ) )
	{
	    wcpds.setDebugUnreturnedConnectionStackTraces( debugUnreturnedConnectionStackTraces ); 
	    this.resetPoolManager( false );
	}
    }

    public int getStatementCacheNumDeferredCloseThreads()
    { return wcpds.getStatementCacheNumDeferredCloseThreads(); }

    public void setStatementCacheNumDeferredCloseThreads(int statementCacheNumDeferredCloseThreads)
    {
	if ( diff( wcpds.getStatementCacheNumDeferredCloseThreads(), statementCacheNumDeferredCloseThreads ) )
	{
	    wcpds.setStatementCacheNumDeferredCloseThreads( statementCacheNumDeferredCloseThreads ); 
	    this.resetPoolManager( false );
	}
    }

    // shared properties (count: 1)
    public String getFactoryClassLocation()
    { return super.getFactoryClassLocation(); }

    public void setFactoryClassLocation( String factoryClassLocation )
    {
	if ( 
	        diff( dmds.getFactoryClassLocation(), factoryClassLocation )  ||
	        diff( wcpds.getFactoryClassLocation(), factoryClassLocation ) ||
	        diff( super.getFactoryClassLocation(), factoryClassLocation )
	   )
	{
	    dmds.setFactoryClassLocation( factoryClassLocation );
	    wcpds.setFactoryClassLocation( factoryClassLocation );
	    super.setFactoryClassLocation( factoryClassLocation );
	}
    }

    public String toString()
    {
        //System.err.println("ComboPooledDataSource.toString()");

        StringBuffer sb = new StringBuffer(512);
        sb.append( this.getClass().getName() );
        sb.append(" [ ");
        try { BeansUtils.appendPropNamesAndValues(sb, this, TO_STRING_IGNORE_PROPS); }
        catch (Exception e)
        { 
            sb.append( e.toString() ); 
            //e.printStackTrace();
        }
        sb.append(" ]");

//      Map userOverrides = wcpds.getUserOverrides();
//      if (userOverrides != null)
//      sb.append("; userOverrides: " + userOverrides.toString());

        return sb.toString();
    }

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
            updateLocalVarsFromCpdsProp();
            setUpPropertyEvents();
            break;
        default:
            throw new IOException("Unsupported Serialized Version: " + version);
        }
    }

    // JDBC4 Wrapper stuff
    public boolean isWrapperFor(Class<?> iface) throws SQLException
    {
	return  ( iface == DataSource.class || iface.isAssignableFrom(DataSource.class) );
    }

    public <T> T unwrap(Class<T> iface) throws SQLException
    {
	if (this.isWrapperFor( iface ))
	    return (T) dmds;
	else
	    throw new SQLException(this + " is not a Wrapper for " + iface.getName());
    }
}

//now, referenceability happens exactly the same way it does for PoolBackedDataSource
//all this stuff (and the maintenance hassle of it) should be unnecessary

/*
 // WrapperConnectionPoolDataSource properties -- count: 28
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
// 	("maxAdministrativeTaskTime");
// 	("maxIdleTimeExcessConnections");
// 	("maxConnectionAge");
// 	("connectionTesterClassName");

   final static JavaBeanReferenceMaker referenceMaker = new JavaBeanReferenceMaker();

    static
    {
	referenceMaker.setFactoryClassName( C3P0JavaBeanObjectFactory.class.getName() );

	// DriverManagerDataSource properties (count: 4)
	referenceMaker.addReferenceProperty("description");
	referenceMaker.addReferenceProperty("driverClass");
	referenceMaker.addReferenceProperty("jdbcUrl");
	referenceMaker.addReferenceProperty("properties");

	// WrapperConnectionPoolDataSource properties (count: 27)
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
	referenceMaker.addReferenceProperty("overrideDefaultUser");
	referenceMaker.addReferenceProperty("overrideDefaultPassword");
	referenceMaker.addReferenceProperty("maxAdministrativeTaskTime");
	referenceMaker.addReferenceProperty("maxIdleTimeExcessConnections");
	referenceMaker.addReferenceProperty("maxConnectionAge");

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
	synchronized ( this )
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
 */
