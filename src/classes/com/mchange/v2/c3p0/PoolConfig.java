/*
 * Distributed as part of c3p0 v.0.8.4.2
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

import java.util.Properties;
import java.io.InputStream;
import java.io.IOException;
import com.mchange.v2.c3p0.impl.C3P0Defaults;
import com.mchange.v1.io.InputStreamUtils;

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
 *  <style type="text/css">
 *     table.propsdoc th { text-align: left; vertical-align: top; }
 *     table.propsdoc td { text-align: left; vertical-align: top; }
 *  </style>
 *
 *  <table class="propsdoc" border="1">
 *  <tr><th>Property Name</th><th>Built-In Default</th><th>Comments</th></tr>
 *  <tr><td><tt>c3p0.initialPoolSize</td><td>3</td><td>&nbsp;</td></tr>
 *  <tr><td><tt>c3p0.minPoolSize</tt></td><td>3</td><td>&nbsp;</td></tr>
 *  <tr><td><tt>c3p0.maxPoolSize</tt></td><td>15</td><td>&nbsp;</td></tr>
 *  <tr>
 *    <td><tt>c3p0.idleConnectionTestPeriod</tt></td>
 *    <td>0</td>
 *    <td>If this is a number greater than 0, c3p0 will test all idle, pooled but unchecked-out connections, every this number of seconds.</td>
 *  </tr>
 *  <tr>
 *    <td><tt>c3p0.maxIdleTime</tt></td>
 *    <td>0</td>
 *    <td>Seconds a Connection can remain pooled but unused before being discarded. Zero means idle connections never expire.</td>
 *  </tr>
 *  <tr>
 *    <td><tt>c3p0.maxStatements</tt></td>
 *    <td>0</td>
 *    <td>The size of c3p0's PreparedStatement cache. Zero means statement cahing is turned off.</td>
 *  </tr>
 *  <tr>
 *    <td><tt>c3p0.propertyCycle</tt></td>
 *    <td>300</td>
 *    <td>Maximum time in seconds before user configuration constraints are enforced.
 *        c3p0 enforces configuration constraints continually, and ignores this parameter.
 *        It is included for JDBC 3 completeness.
 *    </td>
 *  </tr>
 *  <tr>
 *    <td><tt>c3p0.acquireIncrement</tt></td>
 *    <td>3</td>
 *    <td>Determines how many connections at a time c3p0 will try to acquire when the pool is exhausted.</td>
 *  </tr>
 *  <tr>
 *    <td><tt>c3p0.testConnectionOnCheckout</tt></td>
 *    <td>false</td>
 *    <td><b><i>Don't use. Very expensive.</i></b>
 *        If true, an operation will be performed at every connection checkout to verify that the connection is valid.
 *        <b>Better choice:</b> verify connections periodically using c3p0.idleConnectionTestPeriod
 *    </td>
 *  </tr>
 *  <tr>
 *    <td><tt>c3p0.autoCommitOnClose</tt></td>
 *    <td>false</td>
 *    <td>The JDBC spec is unforgivably silent on what should happen to unresolved, pending
 *        transactions on Connection close. C3P0's default policy is to rollback any uncommitted, pending
 *        work. (I think this is absolutely, undeniably the right policy, but there is no consensus among JDBC driver vendors.) 
 *        Setting <tt>autoCommitOnClose</tt> to true causes uncommitted pending work to be committed, rather than rolled
 *        back on Connection close. [<i>Note: Since the spec is absurdly unclear on this question, application authors who wish
 *        to avoid bugs and inconsistent behavior should ensure that all transactions are explicitly either committed or
 *        rolled-back before close is called.</i>]
 *    </td>
 *  </tr>
 *  <tr>
 *    <td><tt>c3p0.forceIgnoreUnresolvedTransactions</tt></td>
 *    <td>false</td>
 *    <td><b><i>Strongly disrecommended. Setting this to <tt>true</tt> may lead to subtle and bizarre bugs.</i></b>
 *        This is a terrible setting, leave it alone unless absolutely necessary. It is here to workaround
 *        broken databases / JDBC drivers that do not properly support transactions, but that allow Connections'
 *        <tt>autoCommit</tt> flags to go to false regardless. If you are using a database that supports transactions
 *        "partially" (this is oxymoronic, as the whole point of transactions is to perform operations reliably and
 *        completely, but nonetheless such databases are out there), if you feel comfortable ignoring the fact that Connections
 *        with <tt>autoCommit == false</tt> may be in the middle of transactions and may hold locks and other resources,
 *        you may turn off c3p0's wise default behavior, which is to protect itself, as well as the usability and consistency
 *        of the database, by either rolling back (default) or committing (see <tt>c3p0.autoCommitOnClose</tt> <i>above</i>)
 *        unresolved transactions. <b>This should only be set to true when you are sure you are using a database that
 *        allows Connections' autoCommit flag to go to false, but offers no other meaningful support of transactions. Otherwise
 *        setting this to true is just a bad idea.</b>
 *    </td>
 *  </tr>
 *  <tr>
 *    <td><tt>c3p0.connectionTesterClassName</tt></td>
 *    <td><small>com.mchange. v2.c3p0.impl. DefaultConnectionTester</small></td>
 *    <td>See {@link com.mchange.v2.c3p0.ConnectionTester}</td>
 *  </tr>
 *  <tr>
 *    <td><tt>c3p0.numHelperThreads</tt></td>
 *    <td>3</td>
 *    <td>c3p0 is very asynchronous. Slow JDBC operations are generally 
 *        performed by helper threads that don't hold contended locks. Spreading
 *        these operations over multiple threads can significantly improve performance
 *        by allowing multiple operations to be performed simultaneously.
 *    </td>
 *  </tr>
 *  <tr>
 *    <td><tt>c3p0.factoryClassLocation</tt></td>
 *    <td>null</td>
 *    <td>DataSources that will be bound by JNDI and use that API's Referenceable interface
 *        to store themselves may specify a URL from which the class capable of dereferencing 
 *        a them may be loaded. If (as is usually the case) the c3p0 libraries will be locally
 *        available to the JNDI service, leave this set as null.
 *    </td>
 *  </tr>
 *  </table>
 *
 */
public final class PoolConfig
{
    public final static String INITIAL_POOL_SIZE                    = "c3p0.initialPoolSize"; 
    public final static String MIN_POOL_SIZE                        = "c3p0.minPoolSize";
    public final static String MAX_POOL_SIZE                        = "c3p0.maxPoolSize";
    public final static String IDLE_CONNECTION_TEST_PERIOD          = "c3p0.idleConnectionTestPeriod";
    public final static String MAX_IDLE_TIME                        = "c3p0.maxIdleTime";
    public final static String PROPERTY_CYCLE                       = "c3p0.propertyCycle";
    public final static String MAX_STATEMENTS                       = "c3p0.maxStatements";
    public final static String ACQUIRE_INCREMENT                    = "c3p0.acquireIncrement";
    public final static String TEST_CONNECTION_ON_CHECKOUT          = "c3p0.testConnectionOnCheckout";
    public final static String CONNECTION_TESTER_CLASS_NAME         = "c3p0.connectionTesterClassName";
    public final static String AUTO_COMMIT_ON_CLOSE                 = "c3p0.autoCommitOnClose";
    public final static String FORCE_IGNORE_UNRESOLVED_TRANSACTIONS = "c3p0.forceIgnoreUnresolvedTransactions";
    public final static String NUM_HELPER_THREADS                   = "c3p0.numHelperThreads";
    public final static String FACTORY_CLASS_LOCATION               = "c3p0.factoryClassLocation";
    
    public final static String DEFAULT_CONFIG_RSRC_PATH = "/c3p0.properties";
    
    final static PoolConfig DEFAULTS;

    static
    {
	Properties rsrcProps = findResourceProperties();
	PoolConfig rsrcDefaults = extractConfig( rsrcProps, null );
	DEFAULTS = extractConfig( System.getProperties(), rsrcDefaults );
    }

    public static int defaultNumHelperThreads()
    { return DEFAULTS.getNumHelperThreads(); }

    public static String defaultFactoryClassLocation()
    { return DEFAULTS.getFactoryClassLocation(); }

    public static int defaultMaxStatements()
    { return DEFAULTS.getMaxStatements(); }

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

    public static int defaultAcquireIncrement()
    { return DEFAULTS.getAcquireIncrement(); }

    public static String defaultConnectionTesterClassName()
    { return DEFAULTS.getConnectionTesterClassName(); }

    public static boolean defaultTestConnectionOnCheckout()
    { return DEFAULTS.isTestConnectionOnCheckout(); }

    public static boolean defaultAutoCommitOnClose()
    { return DEFAULTS.isAutoCommitOnClose(); }

    public static boolean defaultForceIgnoreUnresolvedTransactions()
    { return DEFAULTS.isAutoCommitOnClose(); }


    int     maxStatements;
    int     initialPoolSize;
    int     minPoolSize;
    int     maxPoolSize;
    int     idleConnectionTestPeriod;
    int     maxIdleTime;
    int     propertyCycle;
    int     acquireIncrement;
    boolean testConnectionOnCheckout;
    boolean autoCommitOnClose;
    boolean forceIgnoreUnresolvedTransactions;
    String  connectionTesterClassName;
    int     numHelperThreads;
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

    public String getFactoryClassLocation()
    { return factoryClassLocation; }

    public int getMaxStatements()
    { return maxStatements; }
    
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
    
    public String getConnectionTesterClassName()
    { return connectionTesterClassName; }
    
    /**
     * @deprecated use isTestConnectionOnCheckout
     */
    public boolean getTestConnectionOnCheckout()
    { return testConnectionOnCheckout; }

    public boolean isTestConnectionOnCheckout()
    { return this.getTestConnectionOnCheckout(); }

    public boolean isAutoCommitOnClose()
    { return this.autoCommitOnClose;	}

    public boolean isForceIgnoreUnresolvedTransactions()
    { return this.forceIgnoreUnresolvedTransactions; }
    
    public void setNumHelperThreads( int numHelperThreads )
    { this.numHelperThreads = numHelperThreads;	}

    public void setFactoryClassLocation( String factoryClassLocation )
    { this.factoryClassLocation = factoryClassLocation;	}

    public void setMaxStatements( int maxStatements )
    { this.maxStatements = maxStatements; }
    
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
    
    public void setAcquireIncrement( int acquireIncrement )
    { this.acquireIncrement = acquireIncrement; }
    
    public void setConnectionTesterClassName( String connectionTesterClassName )
    { this.connectionTesterClassName = connectionTesterClassName; }
    
    /**
     * @deprecated you really shouldn't use testConnectionOnCheckout, it's a performance
     *             nightmare. let it default to false, and if you want Connections to be
     *             tested, set a reasonable value for idleConnectionTestPeriod.
     */
    public void setTestConnectionOnCheckout( boolean testConnectionOnCheckout )
    { this.testConnectionOnCheckout = testConnectionOnCheckout; }
    
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
	String initialPoolSizeStr                   = null;
	String minPoolSizeStr                       = null;
	String maxPoolSizeStr                       = null;
	String idleConnectionTestPeriodStr          = null;
	String maxIdleTimeStr                       = null;
	String propertyCycleStr                     = null;
	String acquireIncrementStr                  = null;
	String testConnectionOnCheckoutStr          = null;
	String autoCommitOnCloseStr                 = null;
	String forceIgnoreUnresolvedTransactionsStr = null;
	String connectionTesterClassName            = null;
	String numHelperThreadsStr                  = null;
	String factoryClassLocation                 = null;

	if ( props != null )
	    {
		maxStatementsStr = props.getProperty(MAX_STATEMENTS);
		initialPoolSizeStr = props.getProperty(INITIAL_POOL_SIZE);
		minPoolSizeStr = props.getProperty(MIN_POOL_SIZE);
		maxPoolSizeStr = props.getProperty(MAX_POOL_SIZE);
		idleConnectionTestPeriodStr = props.getProperty(IDLE_CONNECTION_TEST_PERIOD);
		maxIdleTimeStr = props.getProperty(MAX_IDLE_TIME);
		propertyCycleStr = props.getProperty(PROPERTY_CYCLE);
		acquireIncrementStr = props.getProperty(ACQUIRE_INCREMENT);
		testConnectionOnCheckoutStr = props.getProperty(TEST_CONNECTION_ON_CHECKOUT);
		autoCommitOnCloseStr = props.getProperty(AUTO_COMMIT_ON_CLOSE);
		forceIgnoreUnresolvedTransactionsStr = props.getProperty(FORCE_IGNORE_UNRESOLVED_TRANSACTIONS);
		connectionTesterClassName = props.getProperty(CONNECTION_TESTER_CLASS_NAME);
		numHelperThreadsStr = props.getProperty(NUM_HELPER_THREADS);
		factoryClassLocation = props.getProperty(FACTORY_CLASS_LOCATION);
	    }

	// maxStatements
	if ( maxStatementsStr != null )
	    pcfg.setMaxStatements( Integer.parseInt( maxStatementsStr ) );
	else if (defaults != null)
	    pcfg.setMaxStatements( defaults.getMaxStatements() );
	else
	    pcfg.setMaxStatements( C3P0Defaults.maxStatements() );

	// initialPoolSize
	if ( initialPoolSizeStr != null )
	    pcfg.setInitialPoolSize( Integer.parseInt( initialPoolSizeStr ) );
	else if (defaults != null)
	    pcfg.setInitialPoolSize( defaults.getInitialPoolSize() );
	else
	    pcfg.setInitialPoolSize( C3P0Defaults.initialPoolSize() );

	// minPoolSize
	if ( minPoolSizeStr != null )
	    pcfg.setMinPoolSize( Integer.parseInt( minPoolSizeStr ) );
	else if (defaults != null)
	    pcfg.setMinPoolSize( defaults.getMinPoolSize() );
	else
	    pcfg.setMinPoolSize( C3P0Defaults.minPoolSize() );

	// maxPoolSize
	if ( maxPoolSizeStr != null )
	    pcfg.setMaxPoolSize( Integer.parseInt( maxPoolSizeStr ) );
	else if (defaults != null)
	    pcfg.setMaxPoolSize( defaults.getMaxPoolSize() );
	else
	    pcfg.setMaxPoolSize( C3P0Defaults.maxPoolSize() );

	// maxIdleTime
	if ( idleConnectionTestPeriodStr != null )
	    pcfg.setIdleConnectionTestPeriod( Integer.parseInt( idleConnectionTestPeriodStr ) );
	else if (defaults != null)
	    pcfg.setIdleConnectionTestPeriod( defaults.getIdleConnectionTestPeriod() );
	else
	    pcfg.setIdleConnectionTestPeriod( C3P0Defaults.idleConnectionTestPeriod() );

	// maxIdleTime
	if ( maxIdleTimeStr != null )
	    pcfg.setMaxIdleTime( Integer.parseInt( maxIdleTimeStr ) );
	else if (defaults != null)
	    pcfg.setMaxIdleTime( defaults.getMaxIdleTime() );
	else
	    pcfg.setMaxIdleTime( C3P0Defaults.maxIdleTime() );

	// propertyCycle
	if ( propertyCycleStr != null )
	    pcfg.setPropertyCycle( Integer.parseInt( propertyCycleStr ) );
	else if (defaults != null)
	    pcfg.setPropertyCycle( defaults.getPropertyCycle() );
	else
	    pcfg.setPropertyCycle( C3P0Defaults.propertyCycle() );

	// acquireIncrement
	if ( acquireIncrementStr != null )
	    pcfg.setAcquireIncrement( Integer.parseInt( acquireIncrementStr ) );
	else if (defaults != null)
	    pcfg.setAcquireIncrement( defaults.getAcquireIncrement() );
	else
	    pcfg.setAcquireIncrement( C3P0Defaults.acquireIncrement() );

	// testConnectionOnCheckout
	if ( testConnectionOnCheckoutStr != null )
	    pcfg.setTestConnectionOnCheckout( Boolean.valueOf(testConnectionOnCheckoutStr).booleanValue() );
	else if (defaults != null)
	    pcfg.setTestConnectionOnCheckout( defaults.isTestConnectionOnCheckout() );
	else
	    pcfg.setTestConnectionOnCheckout( C3P0Defaults.testConnectionOnCheckout() );

	// autoCommitOnClose
	if ( autoCommitOnCloseStr != null )
	    pcfg.setAutoCommitOnClose( Boolean.valueOf(autoCommitOnCloseStr).booleanValue() );
	else if (defaults != null)
	    pcfg.setAutoCommitOnClose( defaults.isAutoCommitOnClose() );
	else
	    pcfg.setAutoCommitOnClose( C3P0Defaults.autoCommitOnClose() );

	// forceIgnoreUnresolvedTransactions
	if ( forceIgnoreUnresolvedTransactionsStr != null )
	    pcfg.setForceIgnoreUnresolvedTransactions( Boolean.valueOf( forceIgnoreUnresolvedTransactionsStr ).booleanValue() );
	else if (defaults != null)
	    pcfg.setForceIgnoreUnresolvedTransactions( defaults.isForceIgnoreUnresolvedTransactions() );
	else
	    pcfg.setForceIgnoreUnresolvedTransactions( C3P0Defaults.forceIgnoreUnresolvedTransactions() );

	// connectionTesterClassName
	if ( connectionTesterClassName != null )
	    pcfg.setConnectionTesterClassName( connectionTesterClassName );
	else if (defaults != null)
	    pcfg.setConnectionTesterClassName( defaults.getConnectionTesterClassName() );
	else
	    pcfg.setConnectionTesterClassName( C3P0Defaults.connectionTesterClassName() );

	// numHelperThreads
	if ( numHelperThreadsStr != null )
	    pcfg.setNumHelperThreads( Integer.parseInt( numHelperThreadsStr ) );
	else if (defaults != null)
	    pcfg.setNumHelperThreads( defaults.getNumHelperThreads() );
	else
	    pcfg.setNumHelperThreads( C3P0Defaults.numHelperThreads() );

	// factoryClassLocation
	if ( factoryClassLocation != null )
	    pcfg.setFactoryClassLocation( factoryClassLocation );
	else if (defaults != null)
	    pcfg.setFactoryClassLocation( defaults.getFactoryClassLocation() );
	else
	    pcfg.setFactoryClassLocation( C3P0Defaults.factoryClassLocation() );
    }

    private static Properties findResourceProperties()
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
		e.printStackTrace();
		props = new Properties(); 
	    }
	finally
	    { InputStreamUtils.attemptClose( is ); }

	return props;
    }
}