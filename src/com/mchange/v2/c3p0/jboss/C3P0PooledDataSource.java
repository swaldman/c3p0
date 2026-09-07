package com.mchange.v2.c3p0.jboss;

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
import com.mchange.v2.c3p0.impl.C3P0ImplUtils;

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
    @Override
    public void setJndiName(String jndiName) throws NamingException
    {
        C3P0ImplUtils.jndiAssertNameIsAcceptable( jndiName );

	String unbindName = this.jndiName;
	this.jndiName = jndiName;
	rebind( unbindName );
    }

    @Override
    public String getJndiName()
    { return jndiName; }

    // DriverManagerDataSourceProperties  (count: 4)
    @Override
    public String getDescription()
    { return combods.getDescription(); }

    @Override
    public void setDescription( String description ) throws NamingException
    {
	combods.setDescription( description );
	rebind();
    }

    @Override
    public String getDriverClass()
    { return combods.getDriverClass(); }

    @Override
    public void setDriverClass( String driverClass ) throws PropertyVetoException, NamingException
    {
	combods.setDriverClass( driverClass );
	rebind();
    }

    @Override
    public String getJdbcUrl()
    { return combods.getJdbcUrl(); }

    @Override
    public void setJdbcUrl( String jdbcUrl ) throws NamingException
    {
	combods.setJdbcUrl( jdbcUrl );
	rebind();
    }

    // DriverManagerDataSource "virtual properties" based on properties
    @Override
    public String getUser()
    { return combods.getUser(); }

    @Override
    public void setUser( String user ) throws NamingException
    {
	combods.setUser( user );
	rebind();
    }

    @Override
    public String getPassword()
    { return combods.getPassword(); }

    @Override
    public void setPassword( String password ) throws NamingException
    {
	combods.setPassword( password );
	rebind();
    }

    // WrapperConnectionPoolDataSource properties (count: 21)
    @Override
    public int getCheckoutTimeout()
    { return combods.getCheckoutTimeout(); }

    @Override
    public void setCheckoutTimeout( int checkoutTimeout ) throws NamingException
    {
	combods.setCheckoutTimeout( checkoutTimeout );
	rebind();
    }

    @Override
    public int getConnectionIsValidTimeout()
    { return combods.getConnectionIsValidTimeout(); }

    @Override
    public void setConnectionIsValidTimeout( int connectionIsValidTimeout ) throws NamingException
    {
	combods.setConnectionIsValidTimeout( connectionIsValidTimeout );
	rebind();
    }

    @Override
    public int getAcquireIncrement()
    { return combods.getAcquireIncrement(); }

    @Override
    public void setAcquireIncrement( int acquireIncrement ) throws NamingException
    {
	combods.setAcquireIncrement( acquireIncrement );
	rebind();
    }

    @Override
    public int getAcquireRetryAttempts()
    { return combods.getAcquireRetryAttempts(); }

    @Override
    public void setAcquireRetryAttempts( int acquireRetryAttempts ) throws NamingException
    {
	combods.setAcquireRetryAttempts( acquireRetryAttempts );
	rebind();
    }

    @Override
    public int getAcquireRetryDelay()
    { return combods.getAcquireRetryDelay(); }

    @Override
    public void setAcquireRetryDelay( int acquireRetryDelay ) throws NamingException
    {
	combods.setAcquireRetryDelay( acquireRetryDelay );
	rebind();
    }

    @Override
    public boolean isAutoCommitOnClose()
    { return combods.isAutoCommitOnClose(); }

    @Override
    public void setAutoCommitOnClose( boolean autoCommitOnClose ) throws NamingException
    {
	combods.setAutoCommitOnClose( autoCommitOnClose );
	rebind();
    }

    @Override
    public boolean isCancelAutomaticallyClosedStatements()
    { return combods.isCancelAutomaticallyClosedStatements(); }

    @Override
    public void setCancelAutomaticallyClosedStatements( boolean cancelAutomaticallyClosedStatements ) throws NamingException
    {
	combods.setCancelAutomaticallyClosedStatements( cancelAutomaticallyClosedStatements );
	rebind();
    }

    @Override
    public String getConnectionTesterClassName()
    { return combods.getConnectionTesterClassName(); }

    @Override
    public void setConnectionTesterClassName( String connectionTesterClassName ) throws PropertyVetoException, NamingException
    {
	combods.setConnectionTesterClassName( connectionTesterClassName );
	rebind();
    }

    @Override
    public String getTaskRunnerFactoryClassName()
    { return combods.getTaskRunnerFactoryClassName(); }

    @Override
    public void setTaskRunnerFactoryClassName( String taskRunnerFactoryClassName ) throws PropertyVetoException, NamingException
    {
	combods.setTaskRunnerFactoryClassName( taskRunnerFactoryClassName );
	rebind();
    }

    @Override
    public String getAutomaticTestTable()
    { return combods.getAutomaticTestTable(); }

    @Override
    public void setAutomaticTestTable( String automaticTestTable ) throws NamingException
    {
	combods.setAutomaticTestTable( automaticTestTable );
	rebind();
    }

    @Override
    public boolean isForceIgnoreUnresolvedTransactions()
    { return combods.isForceIgnoreUnresolvedTransactions(); }

    @Override
    public void setForceIgnoreUnresolvedTransactions( boolean forceIgnoreUnresolvedTransactions ) throws NamingException
    {
	combods.setForceIgnoreUnresolvedTransactions( forceIgnoreUnresolvedTransactions );
	rebind();
    }

    @Override
    public int getIdleConnectionTestPeriod()
    { return combods.getIdleConnectionTestPeriod(); }

    @Override
    public void setIdleConnectionTestPeriod( int idleConnectionTestPeriod ) throws NamingException
    {
	combods.setIdleConnectionTestPeriod( idleConnectionTestPeriod );
	rebind();
    }

    @Override
    public int getInitialPoolSize()
    { return combods.getInitialPoolSize(); }

    @Override
    public void setInitialPoolSize( int initialPoolSize ) throws NamingException
    {
	combods.setInitialPoolSize( initialPoolSize );
	rebind();
    }

    @Override
    public int getMaxIdleTime()
    { return combods.getMaxIdleTime(); }

    @Override
    public void setMaxIdleTime( int maxIdleTime ) throws NamingException
    {
	combods.setMaxIdleTime( maxIdleTime );
	rebind();
    }

    @Override
    public int getMaxPoolSize()
    { return combods.getMaxPoolSize(); }

    @Override
    public void setMaxPoolSize( int maxPoolSize ) throws NamingException
    {
	combods.setMaxPoolSize( maxPoolSize );
	rebind();
    }

    @Override
    public int getMaxStatements()
    { return combods.getMaxStatements(); }

    @Override
    public void setMaxStatements( int maxStatements ) throws NamingException
    {
	combods.setMaxStatements( maxStatements );
	rebind();
    }

    @Override
    public int getMaxStatementsPerConnection()
    { return combods.getMaxStatementsPerConnection(); }

    @Override
    public void setMaxStatementsPerConnection( int maxStatementsPerConnection ) throws NamingException
    {
	combods.setMaxStatementsPerConnection( maxStatementsPerConnection );
	rebind();
    }

    @Override
    public int getMinPoolSize()
    { return combods.getMinPoolSize(); }

    @Override
    public void setMinPoolSize( int minPoolSize ) throws NamingException
    {
	combods.setMinPoolSize( minPoolSize );
	rebind();
    }

    @Override
    public int getPropertyCycle()
    { return combods.getPropertyCycle(); }

    @Override
    public void setPropertyCycle( int propertyCycle ) throws NamingException
    {
	combods.setPropertyCycle( propertyCycle );
	rebind();
    }

    @Override
    public boolean isBreakAfterAcquireFailure()
    { return combods.isBreakAfterAcquireFailure(); }

    @Override
    public void setBreakAfterAcquireFailure( boolean breakAfterAcquireFailure ) throws NamingException
    {
	combods.setBreakAfterAcquireFailure( breakAfterAcquireFailure );
	rebind();
    }

    @Override
    public boolean isTestConnectionOnCheckout()
    { return combods.isTestConnectionOnCheckout(); }

    @Override
    public void setTestConnectionOnCheckout( boolean testConnectionOnCheckout ) throws NamingException
    {
	combods.setTestConnectionOnCheckout( testConnectionOnCheckout );
	rebind();
    }

    @Override
    public boolean isTestConnectionOnCheckin()
    { return combods.isTestConnectionOnCheckin(); }

    @Override
    public void setTestConnectionOnCheckin( boolean testConnectionOnCheckin ) throws NamingException
    {
	combods.setTestConnectionOnCheckin( testConnectionOnCheckin );
	rebind();
    }

    @Override
    public boolean isAttemptResurrectOnCheckin()
    { return combods.isAttemptResurrectOnCheckin(); }

    @Override
    public void setAttemptResurrectOnCheckin( boolean attemptResurrectOnCheckin ) throws NamingException
    {
	combods.setAttemptResurrectOnCheckin( attemptResurrectOnCheckin );
	rebind();
    }

    @Override
    public String getPreferredTestQuery()
    { return combods.getPreferredTestQuery(); }

    @Override
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

    @Override
    public int getNumHelperThreads()
    { return combods.getNumHelperThreads(); }

    @Override
    public void setNumHelperThreads( int numHelperThreads ) throws NamingException
    {
	combods.setNumHelperThreads( numHelperThreads );
	rebind();
    }

    // shared properties (count: 1)
    @Override
    public String getFactoryClassLocation()
    { return combods.getFactoryClassLocation(); }

    @Override
    public void setFactoryClassLocation( String factoryClassLocation ) throws NamingException
    {
	combods.setFactoryClassLocation( factoryClassLocation );
	rebind();
    }

    // PooledDataSource statistics

    @Override
    public int getNumUserPools() throws SQLException
    { return combods.getNumUserPools(); }

    @Override
    public int getNumConnectionsDefaultUser() throws SQLException
    { return combods.getNumConnectionsDefaultUser(); }

    @Override
    public int getNumIdleConnectionsDefaultUser() throws SQLException
    { return combods.getNumIdleConnectionsDefaultUser(); }

    @Override
    public int getNumBusyConnectionsDefaultUser() throws SQLException
    { return combods.getNumBusyConnectionsDefaultUser(); }

    @Override
    public int getNumUnclosedOrphanedConnectionsDefaultUser() throws SQLException
    { return combods.getNumUnclosedOrphanedConnectionsDefaultUser(); }

    @Override
    public int getNumConnections(String username, String password) throws SQLException
    { return combods.getNumConnections(username, password); }

    @Override
    public int getNumIdleConnections(String username, String password) throws SQLException
    { return combods.getNumIdleConnections(username, password); }

    @Override
    public int getNumBusyConnections(String username, String password) throws SQLException
    { return combods.getNumBusyConnections(username, password); }

    @Override
    public int getNumUnclosedOrphanedConnections(String username, String password) throws SQLException
    { return combods.getNumUnclosedOrphanedConnections(username, password); }

    @Override
    public int getNumConnectionsAllUsers() throws SQLException
    { return combods.getNumConnectionsAllUsers(); }

    @Override
    public int getNumIdleConnectionsAllUsers() throws SQLException
    { return combods.getNumIdleConnectionsAllUsers(); }

    @Override
    public int getNumBusyConnectionsAllUsers() throws SQLException
    { return combods.getNumBusyConnectionsAllUsers(); }

    @Override
    public int getNumUnclosedOrphanedConnectionsAllUsers() throws SQLException
    { return combods.getNumUnclosedOrphanedConnectionsAllUsers(); }

    // PooledDataSource operations
    @Override
    public void softResetDefaultUser() throws SQLException
    { combods.softResetDefaultUser(); }

    @Override
    public void softReset(String username, String password) throws SQLException
    { combods.softReset(username, password); }

    @Override
    public void softResetAllUsers() throws SQLException
    { combods.softResetAllUsers(); }

    @Override
    public void hardReset() throws SQLException
    { combods.hardReset(); }

    @Override
    public void close() throws SQLException
    { combods.close(); }

    //JBoss only... (but these methods need not be called for the mbean to work)
    @Override
    public void create() throws Exception
    { }

    // the mbean works without this, but if called we start populating the pool early
    @Override
    public void start() throws Exception
    {
	//System.err.println("Bound C3P0 PooledDataSource to name '" + jndiName + "'. Starting...");
	logger.log(MLevel.INFO, "Bound C3P0 PooledDataSource to name ''{0}''. Starting...", jndiName);
	combods.getNumBusyConnectionsDefaultUser(); //just touch the datasource to start it up.
    }


    @Override
    public void stop()
    { }

    @Override
    public void destroy()
    {
        try
        {
            combods.close();
            logger.log(MLevel.INFO, "Destroyed C3P0 PooledDataSource with name ''{0}''.", jndiName);
        }
        catch (Exception e)
        {
            logger.log(MLevel.INFO, "Failed to destroy C3P0 PooledDataSource.", e);
        }
    }

    @Override
    public String getConnectionCustomizerClassName()
    { return combods.getConnectionCustomizerClassName(); }

    @Override
    public float getEffectivePropertyCycle(String username, String password) throws SQLException
    { return combods.getEffectivePropertyCycle(username, password); }

    @Override
    public float getEffectivePropertyCycleDefaultUser() throws SQLException
    { return combods.getEffectivePropertyCycleDefaultUser(); }

    @Override
    public int getMaxAdministrativeTaskTime()
    { return combods.getMaxAdministrativeTaskTime(); }

    @Override
    public int getMaxConnectionAge()
    { return combods.getMaxConnectionAge(); }

    @Override
    public int getMaxIdleTimeExcessConnections()
    { return combods.getMaxIdleTimeExcessConnections(); }

    @Override
    public int getUnreturnedConnectionTimeout()
    { return combods.getUnreturnedConnectionTimeout(); }

    @Override
    public boolean isDebugUnreturnedConnectionStackTraces()
    { return combods.isDebugUnreturnedConnectionStackTraces(); }

    @Override
    public boolean isForceSynchronousCheckins()
    { return combods.isForceSynchronousCheckins(); }

    @Override
    public void setConnectionCustomizerClassName(String connectionCustomizerClassName) throws NamingException
    {
        combods.setConnectionCustomizerClassName(connectionCustomizerClassName);
        rebind();
    }

    @Override
    public void setDebugUnreturnedConnectionStackTraces(boolean debugUnreturnedConnectionStackTraces) throws NamingException
    {
        combods.setDebugUnreturnedConnectionStackTraces(debugUnreturnedConnectionStackTraces);
        rebind();
    }

    @Override
    public void setForceSynchronousCheckins(boolean forceSynchronousCheckins) throws NamingException
    {
        combods.setForceSynchronousCheckins(forceSynchronousCheckins);
        rebind();
    }

    @Override
    public void setMaxAdministrativeTaskTime(int maxAdministrativeTaskTime) throws NamingException
    {
        combods.setMaxAdministrativeTaskTime(maxAdministrativeTaskTime);
        rebind();
    }

    @Override
    public void setMaxConnectionAge(int maxConnectionAge) throws NamingException
    {
        combods.setMaxConnectionAge( maxConnectionAge );
        rebind();
    }

    @Override
    public void setMaxIdleTimeExcessConnections(int maxIdleTimeExcessConnections) throws NamingException
    {
        combods.setMaxIdleTimeExcessConnections(maxIdleTimeExcessConnections);
        rebind();
    }

    @Override
    public void setUnreturnedConnectionTimeout(int unreturnedConnectionTimeout) throws NamingException
    {
        combods.setUnreturnedConnectionTimeout(unreturnedConnectionTimeout);
        rebind();
    }
}
