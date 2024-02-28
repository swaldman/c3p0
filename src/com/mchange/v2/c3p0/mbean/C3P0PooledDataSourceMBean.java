package com.mchange.v2.c3p0.mbean;

import com.mchange.v2.c3p0.*;
import java.beans.PropertyVetoException;
import java.sql.SQLException;
import java.util.Properties;
import javax.naming.NamingException;

/**
 * @deprecated Please use com.mchange.v2.c3p0.jboss.C3P0PooledDataSourceMBean
 */
public interface C3P0PooledDataSourceMBean
{
    // Jndi Setup
    public void setJndiName(String jndiName) throws NamingException;

    public String getJndiName();

    // DriverManagerDataSourceProperties
    public String getDescription();

    public void setDescription( String description ) throws NamingException;

    public String getDriverClass();

    public void setDriverClass( String driverClass ) throws PropertyVetoException, NamingException;

    public String getJdbcUrl();

    public void setJdbcUrl( String jdbcUrl ) throws NamingException;

    // DriverManagerDataSource "virtual properties" based on properties
    public String getUser();

    public void setUser( String user ) throws NamingException;

    public String getPassword();

    public void setPassword( String password ) throws NamingException;

    // WrapperConnectionPoolDataSource properties
    public int getCheckoutTimeout();

    public void setCheckoutTimeout( int checkoutTimeout ) throws NamingException;

    public int getConnectionIsValidTimeout();

    public void setConnectionIsValidTimeout( int connectionIsValidTimeout ) throws NamingException;

    public int getAcquireIncrement();

    public void setAcquireIncrement( int acquireIncrement ) throws NamingException;

    public int getAcquireRetryAttempts();

    public void setAcquireRetryAttempts( int acquireRetryAttempts ) throws NamingException;

    public int getAcquireRetryDelay();

    public void setAcquireRetryDelay( int acquireRetryDelay ) throws NamingException;

    public boolean isAutoCommitOnClose();

    public void setAutoCommitOnClose( boolean autoCommitOnClose ) throws NamingException;

    public String getConnectionTesterClassName();

    public void setConnectionTesterClassName( String connectionTesterClassName ) throws PropertyVetoException, NamingException;

    public String getTaskRunnerFactoryClassName();

    public void setTaskRunnerFactoryClassName( String taskRunnerFactoryClassName ) throws PropertyVetoException, NamingException;

    public String getAutomaticTestTable();

    public void setAutomaticTestTable( String automaticTestTable ) throws NamingException;

    public boolean isForceIgnoreUnresolvedTransactions();

    public void setForceIgnoreUnresolvedTransactions( boolean forceIgnoreUnresolvedTransactions ) throws NamingException;

    public int getIdleConnectionTestPeriod();

    public void setIdleConnectionTestPeriod( int idleConnectionTestPeriod ) throws NamingException;

    public int getInitialPoolSize();

    public void setInitialPoolSize( int initialPoolSize ) throws NamingException;

    public int getMaxIdleTime();

    public void setMaxIdleTime( int maxIdleTime ) throws NamingException;

    public int getMaxPoolSize();

    public void setMaxPoolSize( int maxPoolSize ) throws NamingException;

    public int getMaxStatements();

    public void setMaxStatements( int maxStatements ) throws NamingException;

    public int getMaxStatementsPerConnection();

    public void setMaxStatementsPerConnection( int maxStatementsPerConnection ) throws NamingException;

    public int getMinPoolSize();

    public void setMinPoolSize( int minPoolSize ) throws NamingException;

    public int getPropertyCycle();

    public void setPropertyCycle( int propertyCycle ) throws NamingException;

    public boolean isBreakAfterAcquireFailure();

    public void setBreakAfterAcquireFailure( boolean breakAfterAcquireFailure ) throws NamingException;

    public boolean isTestConnectionOnCheckout();

    public void setTestConnectionOnCheckout( boolean testConnectionOnCheckout ) throws NamingException;

    public boolean isTestConnectionOnCheckin();

    public void setTestConnectionOnCheckin( boolean testConnectionOnCheckin ) throws NamingException;

    public boolean isAttemptResurrectOnCheckin();
    
    public void setAttemptResurrectOnCheckin( boolean attemptResurrectOnCheckin ) throws NamingException;

    public String getPreferredTestQuery();

    public void setPreferredTestQuery( String preferredTestQuery ) throws NamingException;

    // PoolBackedDataSource properties (count: 2)
    public int getNumHelperThreads();

    public void setNumHelperThreads( int numHelperThreads ) throws NamingException;

    // shared properties (count: 1)
    public String getFactoryClassLocation();

    public void setFactoryClassLocation( String factoryClassLocation ) throws NamingException;

    // PooledDataSource statistics

    public int getNumUserPools() throws SQLException;

    public int getNumConnectionsDefaultUser() throws SQLException;
    public int getNumIdleConnectionsDefaultUser() throws SQLException;
    public int getNumBusyConnectionsDefaultUser() throws SQLException;
    public int getNumUnclosedOrphanedConnectionsDefaultUser() throws SQLException;

    public int getNumConnections(String username, String password) throws SQLException;
    public int getNumIdleConnections(String username, String password) throws SQLException;
    public int getNumBusyConnections(String username, String password) throws SQLException;
    public int getNumUnclosedOrphanedConnections(String username, String password) throws SQLException;

    public int getNumBusyConnectionsAllUsers() throws SQLException;
    public int getNumIdleConnectionsAllUsers() throws SQLException;
    public int getNumConnectionsAllUsers() throws SQLException;
    public int getNumUnclosedOrphanedConnectionsAllUsers() throws SQLException;

    // PooledDataSource operations
    public void softResetDefaultUser() throws SQLException;
    public void softReset(String username, String password) throws SQLException;
    public void softResetAllUsers() throws SQLException;
    public void hardReset() throws SQLException;
    public void close() throws SQLException;

    //JBoss only... (but these methods need not be called for the mbean to work)
    public void create() throws Exception;
    public void start() throws Exception;
    public void stop();
    public void destroy();
}
