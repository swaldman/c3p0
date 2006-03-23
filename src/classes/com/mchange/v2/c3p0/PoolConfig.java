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

import java.util.Properties;
import java.io.InputStream;
import java.io.IOException;
import com.mchange.v1.io.InputStreamUtils;
import com.mchange.v2.c3p0.impl.C3P0Defaults;
import com.mchange.v2.cfg.MultiPropertiesConfig;
import com.mchange.v2.log.MLevel;
import com.mchange.v2.log.MLog;
import com.mchange.v2.log.MLogger;

/**
 *  <p>Encapsulates all the configuration information required by a c3p0 pooled DataSource.</p>
 *
 *  <p>Newly constructed PoolConfig objects are preset with default values,
 *  which you can define yourself (see below),
 *  or you can rely on c3p0's built-in defaults. Just create a PoolConfig object, and change only the
 *  properties you care about. Then pass it to the {@link com.mchange.v2.c3p0.DataSources#pooledDataSource(javax.sql.DataSource, com.mchange.v2.c3p0.PoolConfig)}
 *  method, and you're off!</p>
 *
 *  <p>For those interested in the details, configuration properties can be specified in several ways:</p>
 *  <ol>
 *    <li>Any property can be set explicitly by calling the corresponding method on a PoolConfig object.</li>
 *    <li>Any property will default to a value defined by a System Property, using the property name shown the table below.</li>
 *    <li>Any property not set in either of the above ways will default to a value found in a user-supplied Java properties file,
 *        which may be placed in the resource path of
 *        the ClassLoader that loaded the c3p0 libraries under the name <tt>/c3p0.properties</tt>.</li>
 *    <li>Any property not set in any of the above ways will be defined according c3p0's built-in defaults.</li>
 *  </ol>
 *
 *  <p><i>Please see c3p0's main documentation for a description of all available parameters.</i></p>
 *
 *  @deprecated as of c3p0-0.9.1. To manipulate config programmaticall, please use ComboPooledDataSource
 *
 */
public final class PoolConfig
{
    final static MLogger logger;

    public final static String INITIAL_POOL_SIZE                    = "c3p0.initialPoolSize"; 
    public final static String MIN_POOL_SIZE                        = "c3p0.minPoolSize";
    public final static String MAX_POOL_SIZE                        = "c3p0.maxPoolSize";
    public final static String IDLE_CONNECTION_TEST_PERIOD          = "c3p0.idleConnectionTestPeriod";
    public final static String MAX_IDLE_TIME                        = "c3p0.maxIdleTime";
    public final static String PROPERTY_CYCLE                       = "c3p0.propertyCycle";
    public final static String MAX_STATEMENTS                       = "c3p0.maxStatements";
    public final static String MAX_STATEMENTS_PER_CONNECTION        = "c3p0.maxStatementsPerConnection";
    public final static String CHECKOUT_TIMEOUT                     = "c3p0.checkoutTimeout";
    public final static String ACQUIRE_INCREMENT                    = "c3p0.acquireIncrement";
    public final static String ACQUIRE_RETRY_ATTEMPTS               = "c3p0.acquireRetryAttempts";
    public final static String ACQUIRE_RETRY_DELAY                  = "c3p0.acquireRetryDelay";
    public final static String BREAK_AFTER_ACQUIRE_FAILURE          = "c3p0.breakAfterAcquireFailure";
    public final static String USES_TRADITIONAL_REFLECTIVE_PROXIES  = "c3p0.usesTraditionalReflectiveProxies";
    public final static String TEST_CONNECTION_ON_CHECKOUT          = "c3p0.testConnectionOnCheckout";
    public final static String TEST_CONNECTION_ON_CHECKIN           = "c3p0.testConnectionOnCheckin";
    public final static String CONNECTION_TESTER_CLASS_NAME         = "c3p0.connectionTesterClassName";
    public final static String AUTOMATIC_TEST_TABLE                 = "c3p0.automaticTestTable";
    public final static String AUTO_COMMIT_ON_CLOSE                 = "c3p0.autoCommitOnClose";
    public final static String FORCE_IGNORE_UNRESOLVED_TRANSACTIONS = "c3p0.forceIgnoreUnresolvedTransactions";
    public final static String NUM_HELPER_THREADS                   = "c3p0.numHelperThreads";
    public final static String PREFERRED_TEST_QUERY                 = "c3p0.preferredTestQuery";
    public final static String FACTORY_CLASS_LOCATION               = "c3p0.factoryClassLocation";
    
    public final static String DEFAULT_CONFIG_RSRC_PATH = "/c3p0.properties";
    
    final static PoolConfig DEFAULTS;

    static
    {
	logger = MLog.getLogger( PoolConfig.class );

	Properties rsrcProps = findResourceProperties();
	PoolConfig rsrcDefaults = extractConfig( rsrcProps, null );

	Properties sysProps;
	try
	    { sysProps = System.getProperties(); }
	catch ( SecurityException e )
	    {
		if (logger.isLoggable(MLevel.WARNING))
		    logger.log(MLevel.WARNING, 
			       "Read of system Properties blocked -- ignoring any c3p0 configuration via System properties! " +
			       "(But any configuration via a c3p0.properties file is still okay!)", 
			       e);
		sysProps = new Properties(); //TODO -- an alternative approach to getting c3p0-specific sysprops if allowed
	    }
	DEFAULTS = extractConfig( sysProps, rsrcDefaults );
    }

    public static int defaultNumHelperThreads()
    { return DEFAULTS.getNumHelperThreads(); }

    public static String defaultPreferredTestQuery()
    { return DEFAULTS.getPreferredTestQuery(); }

    public static String defaultFactoryClassLocation()
    { return DEFAULTS.getFactoryClassLocation(); }

    public static int defaultMaxStatements()
    { return DEFAULTS.getMaxStatements(); }

    public static int defaultMaxStatementsPerConnection()
    { return DEFAULTS.getMaxStatementsPerConnection(); }

    public static int defaultInitialPoolSize()
    { return DEFAULTS.getInitialPoolSize(); }

    public static int defaultMinPoolSize()
    { return DEFAULTS.getMinPoolSize(); }

    public static int defaultMaxPoolSize()
    { return DEFAULTS.getMaxPoolSize(); }

    public static int defaultIdleConnectionTestPeriod()
    { return DEFAULTS.getIdleConnectionTestPeriod(); }

    public static int defaultMaxIdleTime()
    { return DEFAULTS.getMaxIdleTime(); }

    public static int defaultPropertyCycle()
    { return DEFAULTS.getPropertyCycle(); }

    public static int defaultCheckoutTimeout()
    { return DEFAULTS.getCheckoutTimeout(); }

    public static int defaultAcquireIncrement()
    { return DEFAULTS.getAcquireIncrement(); }

    public static int defaultAcquireRetryAttempts()
    { return DEFAULTS.getAcquireRetryAttempts(); }

    public static int defaultAcquireRetryDelay()
    { return DEFAULTS.getAcquireRetryDelay(); }

    public static boolean defaultBreakAfterAcquireFailure()
    { return DEFAULTS.isBreakAfterAcquireFailure(); }

    public static String defaultConnectionTesterClassName()
    { return DEFAULTS.getConnectionTesterClassName(); }

    public static String defaultAutomaticTestTable()
    { return DEFAULTS.getAutomaticTestTable(); }

    public static boolean defaultTestConnectionOnCheckout()
    { return DEFAULTS.isTestConnectionOnCheckout(); }

    public static boolean defaultTestConnectionOnCheckin()
    { return DEFAULTS.isTestConnectionOnCheckin(); }

    public static boolean defaultAutoCommitOnClose()
    { return DEFAULTS.isAutoCommitOnClose(); }

    public static boolean defaultForceIgnoreUnresolvedTransactions()
    { return DEFAULTS.isAutoCommitOnClose(); }

    public static boolean defaultUsesTraditionalReflectiveProxies()
    { return DEFAULTS.isUsesTraditionalReflectiveProxies(); }


    int     maxStatements;
    int     maxStatementsPerConnection;
    int     initialPoolSize;
    int     minPoolSize;
    int     maxPoolSize;
    int     idleConnectionTestPeriod;
    int     maxIdleTime;
    int     propertyCycle;
    int     checkoutTimeout;
    int     acquireIncrement;
    int     acquireRetryAttempts;
    int     acquireRetryDelay;
    boolean breakAfterAcquireFailure;
    boolean testConnectionOnCheckout;
    boolean testConnectionOnCheckin;
    boolean autoCommitOnClose;
    boolean forceIgnoreUnresolvedTransactions;
    boolean usesTraditionalReflectiveProxies;
    String  connectionTesterClassName;
    String  automaticTestTable;
    int     numHelperThreads;
    String  preferredTestQuery;
    String  factoryClassLocation;

    private PoolConfig( Properties props, boolean init ) throws NumberFormatException
    {
	if (init)
	    extractConfig( this, props, DEFAULTS );
    }

    public PoolConfig( Properties props ) throws NumberFormatException
    { this( props, true ); }

    public PoolConfig() throws NumberFormatException
    { this( null, true ); }

    public int getNumHelperThreads()
    { return numHelperThreads; }

    public String getPreferredTestQuery()
    { return preferredTestQuery; }

    public String getFactoryClassLocation()
    { return factoryClassLocation; }

    public int getMaxStatements()
    { return maxStatements; }
    
    public int getMaxStatementsPerConnection()
    { return maxStatementsPerConnection; }
    
    public int getInitialPoolSize()
    { return initialPoolSize; }
    
    public int getMinPoolSize()
    { return minPoolSize; }
    
    public int getMaxPoolSize()
    { return maxPoolSize; }
    
    public int getIdleConnectionTestPeriod()
    { return idleConnectionTestPeriod; }
    
    public int getMaxIdleTime()
    { return maxIdleTime; }
    
    public int getPropertyCycle()
    { return propertyCycle; }
    
    public int getAcquireIncrement()
    { return acquireIncrement; }
    
    public int getCheckoutTimeout()
    { return checkoutTimeout; }
    
    public int getAcquireRetryAttempts()
    { return acquireRetryAttempts; }
    
    public int getAcquireRetryDelay()
    { return acquireRetryDelay; }
    
    public boolean isBreakAfterAcquireFailure()
    { return this.breakAfterAcquireFailure;	}

    public boolean isUsesTraditionalReflectiveProxies()
    { return this.usesTraditionalReflectiveProxies; }

    public String getConnectionTesterClassName()
    { return connectionTesterClassName; }
    
    public String getAutomaticTestTable()
    { return automaticTestTable; }
    
    /**
     * @deprecated use isTestConnectionOnCheckout
     */
    public boolean getTestConnectionOnCheckout()
    { return testConnectionOnCheckout; }

    public boolean isTestConnectionOnCheckout()
    { return this.getTestConnectionOnCheckout(); }

    public boolean isTestConnectionOnCheckin()
    { return testConnectionOnCheckin; }

    public boolean isAutoCommitOnClose()
    { return this.autoCommitOnClose;	}

    public boolean isForceIgnoreUnresolvedTransactions()
    { return this.forceIgnoreUnresolvedTransactions; }
    
    public void setNumHelperThreads( int numHelperThreads )
    { this.numHelperThreads = numHelperThreads;	}

    public void setPreferredTestQuery( String preferredTestQuery )
    { this.preferredTestQuery = preferredTestQuery; }

    public void setFactoryClassLocation( String factoryClassLocation )
    { this.factoryClassLocation = factoryClassLocation;	}

    public void setMaxStatements( int maxStatements )
    { this.maxStatements = maxStatements; }
    
    public void setMaxStatementsPerConnection( int maxStatementsPerConnection )
    { this.maxStatementsPerConnection = maxStatementsPerConnection; }
    
    public void setInitialPoolSize( int initialPoolSize )
    { this.initialPoolSize = initialPoolSize; }
    
    public void setMinPoolSize( int minPoolSize )
    { this.minPoolSize = minPoolSize; }
    
    public void setMaxPoolSize( int maxPoolSize )
    { this.maxPoolSize = maxPoolSize; }
    
    public void setIdleConnectionTestPeriod( int idleConnectionTestPeriod )
    { this.idleConnectionTestPeriod = idleConnectionTestPeriod; }
    
    public void setMaxIdleTime( int maxIdleTime )
    { this.maxIdleTime = maxIdleTime; }
    
    public void setPropertyCycle( int propertyCycle )
    { this.propertyCycle = propertyCycle; }
    
    public void setCheckoutTimeout( int checkoutTimeout )
    { this.checkoutTimeout = checkoutTimeout; }
    
    public void setAcquireIncrement( int acquireIncrement )
    { this.acquireIncrement = acquireIncrement; }
    
    public void setAcquireRetryAttempts( int acquireRetryAttempts )
    { this.acquireRetryAttempts = acquireRetryAttempts; }
    
    public void setAcquireRetryDelay( int acquireRetryDelay )
    { this.acquireRetryDelay = acquireRetryDelay; }
    
    public void setConnectionTesterClassName( String connectionTesterClassName )
    { this.connectionTesterClassName = connectionTesterClassName; }
    
    public void setAutomaticTestTable( String automaticTestTable )
    { this.automaticTestTable = automaticTestTable; }
    
    public void setBreakAfterAcquireFailure( boolean breakAfterAcquireFailure )
    { this.breakAfterAcquireFailure = breakAfterAcquireFailure; }
    
    public void setUsesTraditionalReflectiveProxies( boolean usesTraditionalReflectiveProxies )
    { this.usesTraditionalReflectiveProxies = usesTraditionalReflectiveProxies; }
    
    public void setTestConnectionOnCheckout( boolean testConnectionOnCheckout )
    { this.testConnectionOnCheckout = testConnectionOnCheckout; }
    
    public void setTestConnectionOnCheckin( boolean testConnectionOnCheckin )
    { this.testConnectionOnCheckin = testConnectionOnCheckin; }
    
    public void setAutoCommitOnClose( boolean autoCommitOnClose )
    { this.autoCommitOnClose = autoCommitOnClose;  }

    public void setForceIgnoreUnresolvedTransactions( boolean forceIgnoreUnresolvedTransactions )
    { this.forceIgnoreUnresolvedTransactions = forceIgnoreUnresolvedTransactions; }

    private static PoolConfig extractConfig(Properties props, PoolConfig defaults) throws NumberFormatException
    {
	PoolConfig pcfg = new PoolConfig(null, false);
	extractConfig( pcfg, props, defaults );
	return pcfg;
    }

    private static void extractConfig(PoolConfig pcfg, Properties props, PoolConfig defaults) throws NumberFormatException
    {
	String maxStatementsStr                     = null;
	String maxStatementsPerConnectionStr        = null;
	String initialPoolSizeStr                   = null;
	String minPoolSizeStr                       = null;
	String maxPoolSizeStr                       = null;
	String idleConnectionTestPeriodStr          = null;
	String maxIdleTimeStr                       = null;
	String propertyCycleStr                     = null;
	String checkoutTimeoutStr                   = null;
	String acquireIncrementStr                  = null;
	String acquireRetryAttemptsStr              = null;
	String acquireRetryDelayStr                 = null;
	String breakAfterAcquireFailureStr          = null;
	String usesTraditionalReflectiveProxiesStr  = null;
	String testConnectionOnCheckoutStr          = null;
	String testConnectionOnCheckinStr           = null;
	String autoCommitOnCloseStr                 = null;
	String forceIgnoreUnresolvedTransactionsStr = null;
	String connectionTesterClassName            = null;
	String automaticTestTable                   = null;
	String numHelperThreadsStr                  = null;
	String preferredTestQuery                   = null;
	String factoryClassLocation                 = null;

	if ( props != null )
	    {
		maxStatementsStr = props.getProperty(MAX_STATEMENTS);
		maxStatementsPerConnectionStr = props.getProperty(MAX_STATEMENTS_PER_CONNECTION);
		initialPoolSizeStr = props.getProperty(INITIAL_POOL_SIZE);
		minPoolSizeStr = props.getProperty(MIN_POOL_SIZE);
		maxPoolSizeStr = props.getProperty(MAX_POOL_SIZE);
		idleConnectionTestPeriodStr = props.getProperty(IDLE_CONNECTION_TEST_PERIOD);
		maxIdleTimeStr = props.getProperty(MAX_IDLE_TIME);
		propertyCycleStr = props.getProperty(PROPERTY_CYCLE);
		checkoutTimeoutStr = props.getProperty(CHECKOUT_TIMEOUT);
		acquireIncrementStr = props.getProperty(ACQUIRE_INCREMENT);
		acquireRetryAttemptsStr = props.getProperty(ACQUIRE_RETRY_ATTEMPTS);
		acquireRetryDelayStr = props.getProperty(ACQUIRE_RETRY_DELAY);
		breakAfterAcquireFailureStr = props.getProperty(BREAK_AFTER_ACQUIRE_FAILURE);
		usesTraditionalReflectiveProxiesStr = props.getProperty(USES_TRADITIONAL_REFLECTIVE_PROXIES);
		testConnectionOnCheckoutStr = props.getProperty(TEST_CONNECTION_ON_CHECKOUT);
		testConnectionOnCheckinStr = props.getProperty(TEST_CONNECTION_ON_CHECKIN);
		autoCommitOnCloseStr = props.getProperty(AUTO_COMMIT_ON_CLOSE);
		forceIgnoreUnresolvedTransactionsStr = props.getProperty(FORCE_IGNORE_UNRESOLVED_TRANSACTIONS);
		connectionTesterClassName = props.getProperty(CONNECTION_TESTER_CLASS_NAME);
		automaticTestTable = props.getProperty(AUTOMATIC_TEST_TABLE);
		numHelperThreadsStr = props.getProperty(NUM_HELPER_THREADS);
		preferredTestQuery = props.getProperty(PREFERRED_TEST_QUERY);
		factoryClassLocation = props.getProperty(FACTORY_CLASS_LOCATION);
	    }

	// maxStatements
	if ( maxStatementsStr != null )
	    pcfg.setMaxStatements( Integer.parseInt( maxStatementsStr.trim() ) );
	else if (defaults != null)
	    pcfg.setMaxStatements( defaults.getMaxStatements() );
	else
	    pcfg.setMaxStatements( C3P0Defaults.maxStatements() );

	// maxStatementsPerConnection
	if ( maxStatementsPerConnectionStr != null )
	    pcfg.setMaxStatementsPerConnection( Integer.parseInt( maxStatementsPerConnectionStr.trim() ) );
	else if (defaults != null)
	    pcfg.setMaxStatementsPerConnection( defaults.getMaxStatementsPerConnection() );
	else
	    pcfg.setMaxStatementsPerConnection( C3P0Defaults.maxStatementsPerConnection() );

	// initialPoolSize
	if ( initialPoolSizeStr != null )
	    pcfg.setInitialPoolSize( Integer.parseInt( initialPoolSizeStr.trim() ) );
	else if (defaults != null)
	    pcfg.setInitialPoolSize( defaults.getInitialPoolSize() );
	else
	    pcfg.setInitialPoolSize( C3P0Defaults.initialPoolSize() );

	// minPoolSize
	if ( minPoolSizeStr != null )
	    pcfg.setMinPoolSize( Integer.parseInt( minPoolSizeStr.trim() ) );
	else if (defaults != null)
	    pcfg.setMinPoolSize( defaults.getMinPoolSize() );
	else
	    pcfg.setMinPoolSize( C3P0Defaults.minPoolSize() );

	// maxPoolSize
	if ( maxPoolSizeStr != null )
	    pcfg.setMaxPoolSize( Integer.parseInt( maxPoolSizeStr.trim() ) );
	else if (defaults != null)
	    pcfg.setMaxPoolSize( defaults.getMaxPoolSize() );
	else
	    pcfg.setMaxPoolSize( C3P0Defaults.maxPoolSize() );

	// maxIdleTime
	if ( idleConnectionTestPeriodStr != null )
	    pcfg.setIdleConnectionTestPeriod( Integer.parseInt( idleConnectionTestPeriodStr.trim() ) );
	else if (defaults != null)
	    pcfg.setIdleConnectionTestPeriod( defaults.getIdleConnectionTestPeriod() );
	else
	    pcfg.setIdleConnectionTestPeriod( C3P0Defaults.idleConnectionTestPeriod() );

	// maxIdleTime
	if ( maxIdleTimeStr != null )
	    pcfg.setMaxIdleTime( Integer.parseInt( maxIdleTimeStr.trim() ) );
	else if (defaults != null)
	    pcfg.setMaxIdleTime( defaults.getMaxIdleTime() );
	else
	    pcfg.setMaxIdleTime( C3P0Defaults.maxIdleTime() );

	// propertyCycle
	if ( propertyCycleStr != null )
	    pcfg.setPropertyCycle( Integer.parseInt( propertyCycleStr.trim() ) );
	else if (defaults != null)
	    pcfg.setPropertyCycle( defaults.getPropertyCycle() );
	else
	    pcfg.setPropertyCycle( C3P0Defaults.propertyCycle() );

	// checkoutTimeout
	if ( checkoutTimeoutStr != null )
	    pcfg.setCheckoutTimeout( Integer.parseInt( checkoutTimeoutStr.trim() ) );
	else if (defaults != null)
	    pcfg.setCheckoutTimeout( defaults.getCheckoutTimeout() );
	else
	    pcfg.setCheckoutTimeout( C3P0Defaults.checkoutTimeout() );

	// acquireIncrement
	if ( acquireIncrementStr != null )
	    pcfg.setAcquireIncrement( Integer.parseInt( acquireIncrementStr.trim() ) );
	else if (defaults != null)
	    pcfg.setAcquireIncrement( defaults.getAcquireIncrement() );
	else
	    pcfg.setAcquireIncrement( C3P0Defaults.acquireIncrement() );

	// acquireRetryAttempts
	if ( acquireRetryAttemptsStr != null )
	    pcfg.setAcquireRetryAttempts( Integer.parseInt( acquireRetryAttemptsStr.trim() ) );
	else if (defaults != null)
	    pcfg.setAcquireRetryAttempts( defaults.getAcquireRetryAttempts() );
	else
	    pcfg.setAcquireRetryAttempts( C3P0Defaults.acquireRetryAttempts() );

	// acquireRetryDelay
	if ( acquireRetryDelayStr != null )
	    pcfg.setAcquireRetryDelay( Integer.parseInt( acquireRetryDelayStr.trim() ) );
	else if (defaults != null)
	    pcfg.setAcquireRetryDelay( defaults.getAcquireRetryDelay() );
	else
	    pcfg.setAcquireRetryDelay( C3P0Defaults.acquireRetryDelay() );

	// breakAfterAcquireFailure
	if ( breakAfterAcquireFailureStr != null )
	    pcfg.setBreakAfterAcquireFailure( Boolean.valueOf(breakAfterAcquireFailureStr.trim()).booleanValue() );
	else if (defaults != null)
	    pcfg.setBreakAfterAcquireFailure( defaults.isBreakAfterAcquireFailure() );
	else
	    pcfg.setBreakAfterAcquireFailure( C3P0Defaults.breakAfterAcquireFailure() );

	// usesTraditionalReflectiveProxies
	if ( usesTraditionalReflectiveProxiesStr != null )
	    pcfg.setUsesTraditionalReflectiveProxies( Boolean.valueOf(usesTraditionalReflectiveProxiesStr.trim()).booleanValue() );
	else if (defaults != null)
	    pcfg.setUsesTraditionalReflectiveProxies( defaults.isUsesTraditionalReflectiveProxies() );
	else
	    pcfg.setUsesTraditionalReflectiveProxies( C3P0Defaults.usesTraditionalReflectiveProxies() );

	// testConnectionOnCheckout
	if ( testConnectionOnCheckoutStr != null )
	    pcfg.setTestConnectionOnCheckout( Boolean.valueOf(testConnectionOnCheckoutStr.trim()).booleanValue() );
	else if (defaults != null)
	    pcfg.setTestConnectionOnCheckout( defaults.isTestConnectionOnCheckout() );
	else
	    pcfg.setTestConnectionOnCheckout( C3P0Defaults.testConnectionOnCheckout() );

	// testConnectionOnCheckin
	if ( testConnectionOnCheckinStr != null )
	    pcfg.setTestConnectionOnCheckin( Boolean.valueOf(testConnectionOnCheckinStr.trim()).booleanValue() );
	else if (defaults != null)
	    pcfg.setTestConnectionOnCheckin( defaults.isTestConnectionOnCheckin() );
	else
	    pcfg.setTestConnectionOnCheckin( C3P0Defaults.testConnectionOnCheckin() );

	// autoCommitOnClose
	if ( autoCommitOnCloseStr != null )
	    pcfg.setAutoCommitOnClose( Boolean.valueOf(autoCommitOnCloseStr.trim()).booleanValue() );
	else if (defaults != null)
	    pcfg.setAutoCommitOnClose( defaults.isAutoCommitOnClose() );
	else
	    pcfg.setAutoCommitOnClose( C3P0Defaults.autoCommitOnClose() );

	// forceIgnoreUnresolvedTransactions
	if ( forceIgnoreUnresolvedTransactionsStr != null )
	    pcfg.setForceIgnoreUnresolvedTransactions( Boolean.valueOf( forceIgnoreUnresolvedTransactionsStr.trim() ).booleanValue() );
	else if (defaults != null)
	    pcfg.setForceIgnoreUnresolvedTransactions( defaults.isForceIgnoreUnresolvedTransactions() );
	else
	    pcfg.setForceIgnoreUnresolvedTransactions( C3P0Defaults.forceIgnoreUnresolvedTransactions() );

	// connectionTesterClassName
	if ( connectionTesterClassName != null )
	    pcfg.setConnectionTesterClassName( connectionTesterClassName.trim() );
	else if (defaults != null)
	    pcfg.setConnectionTesterClassName( defaults.getConnectionTesterClassName() );
	else
	    pcfg.setConnectionTesterClassName( C3P0Defaults.connectionTesterClassName() );

	// automaticTestTable
	if ( automaticTestTable != null )
	    pcfg.setAutomaticTestTable( automaticTestTable.trim() );
	else if (defaults != null)
	    pcfg.setAutomaticTestTable( defaults.getAutomaticTestTable() );
	else
	    pcfg.setAutomaticTestTable( C3P0Defaults.automaticTestTable() );

	// numHelperThreads
	if ( numHelperThreadsStr != null )
	    pcfg.setNumHelperThreads( Integer.parseInt( numHelperThreadsStr.trim() ) );
	else if (defaults != null)
	    pcfg.setNumHelperThreads( defaults.getNumHelperThreads() );
	else
	    pcfg.setNumHelperThreads( C3P0Defaults.numHelperThreads() );

	// preferredTestQuery
	if ( preferredTestQuery != null )
	    pcfg.setPreferredTestQuery( preferredTestQuery.trim() );
	else if (defaults != null)
	    pcfg.setPreferredTestQuery( defaults.getPreferredTestQuery() );
	else
	    pcfg.setPreferredTestQuery( C3P0Defaults.preferredTestQuery() );

	// factoryClassLocation
	if ( factoryClassLocation != null )
	    pcfg.setFactoryClassLocation( factoryClassLocation.trim() );
	else if (defaults != null)
	    pcfg.setFactoryClassLocation( defaults.getFactoryClassLocation() );
	else
	    pcfg.setFactoryClassLocation( C3P0Defaults.factoryClassLocation() );
    }

    private static Properties findResourceProperties()
    { return MultiPropertiesConfig.readVmConfig().getPropertiesByResourcePath(DEFAULT_CONFIG_RSRC_PATH); }

    private static Properties origFindResourceProperties()
    {
 	Properties props = new Properties();

 	InputStream is = null; 
 	try
 	    {
 		is = PoolConfig.class.getResourceAsStream(DEFAULT_CONFIG_RSRC_PATH);
 		if ( is != null )
 		    props.load( is );
 	    }
 	catch (IOException e)
 	    {
 		//e.printStackTrace();
 		if ( logger.isLoggable( MLevel.WARNING ) )
 		    logger.log( MLevel.WARNING, "An IOException occurred while trying to read Pool properties!", e );
 		props = new Properties(); 
 	    }
 	finally
 	    { InputStreamUtils.attemptClose( is ); }

 	return props;
    }
}
