/*
 * Distributed as part of c3p0 v.0.9.0-pre4
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


package com.mchange.v2.c3p0.impl;

import java.beans.*;
import java.util.*;
import java.lang.reflect.*;
import java.sql.*;
import javax.sql.*;
import com.mchange.v2.c3p0.*;
import com.mchange.v2.c3p0.stmt.*;
import com.mchange.v2.async.*;
import com.mchange.v2.coalesce.*;
import com.mchange.v1.db.sql.*;
import com.mchange.v2.log.*;
import com.mchange.v2.sql.SqlUtils;
import com.mchange.v2.resourcepool.ResourcePoolFactory;
import com.mchange.v2.resourcepool.BasicResourcePoolFactory;

public final class C3P0PooledConnectionPoolManager
{
    private final static MLogger logger = MLog.getLogger( C3P0PooledConnectionPoolManager.class );

    private final static boolean POOL_EVENT_SUPPORT = false;

    private final static CoalesceChecker COALESCE_CHECKER = new CoalesceChecker()
	{
	    // note that we expect all ConnectionTesters of a single class to be effectively
	    // equivalent, since they are to be constructed via a no-arg ctor and no
	    // extra initialization is performed. thus we only compare the classes of ConnectionTesters.
	    public boolean checkCoalesce( Object a, Object b )
	    {
		C3P0PooledConnectionPoolManager aa = (C3P0PooledConnectionPoolManager) a;
		C3P0PooledConnectionPoolManager bb = (C3P0PooledConnectionPoolManager) b;

		return
		    aa.poolOwnerIdentityToken.equals( bb.poolOwnerIdentityToken ) &&
		    (aa.preferredTestQuery == null ? (bb.preferredTestQuery == null ) : (aa.preferredTestQuery.equals( bb.preferredTestQuery ))) &&
		    (aa.automaticTestTable == null ? (bb.automaticTestTable == null ) : (aa.automaticTestTable.equals( bb.automaticTestTable ))) &&
		    aa.sourceCpdsIdentityToken.equals( bb.sourceCpdsIdentityToken ) &&
		    aa.num_task_threads == bb.num_task_threads &&
		    aa.maxStatements == bb.maxStatements &&
		    aa.maxStatementsPerConnection == bb.maxStatementsPerConnection &&
		    aa.minPoolSize == bb.minPoolSize &&
		    aa.idleConnectionTestPeriod == bb.idleConnectionTestPeriod &&
		    aa.maxIdleTime == bb.maxIdleTime &&
		    aa.checkoutTimeout == bb.checkoutTimeout &&
		    aa.acquireIncrement == bb.acquireIncrement &&
		    aa.acquireRetryAttempts == bb.acquireRetryAttempts &&
		    aa.acquireRetryDelay == bb.acquireRetryDelay &&
		    aa.breakAfterAcquireFailure == bb.breakAfterAcquireFailure &&
		    aa.testConnectionOnCheckout == bb.testConnectionOnCheckout &&
		    aa.testConnectionOnCheckin == bb.testConnectionOnCheckin &&
		    aa.autoCommitOnClose == bb.autoCommitOnClose &&
		    aa.forceIgnoreUnresolvedTransactions == bb.forceIgnoreUnresolvedTransactions &&
		    aa.defaultAuth.equals( bb.defaultAuth ) &&
		    aa.connectionTester.getClass().equals( bb.connectionTester.getClass() );
	    };

	    public int coalesceHash( Object a )
	    {
		C3P0PooledConnectionPoolManager aa = (C3P0PooledConnectionPoolManager) a;
		int out =
		    aa.poolOwnerIdentityToken.hashCode() ^
		    (aa.preferredTestQuery == null ? 0 : aa.preferredTestQuery.hashCode()) ^
		    (aa.automaticTestTable == null ? 0 : aa.automaticTestTable.hashCode()) ^
		    aa.sourceCpdsIdentityToken.hashCode() ^
		    aa.num_task_threads ^
		    aa.maxStatements ^
		    aa.maxStatementsPerConnection ^
		    aa.minPoolSize ^
		    aa.idleConnectionTestPeriod ^
		    aa.maxIdleTime ^
		    aa.checkoutTimeout ^
		    aa.acquireIncrement ^
		    aa.acquireRetryAttempts ^
		    aa.acquireRetryDelay ^
		    (aa.testConnectionOnCheckout          ? 1<<0 : 0) ^
		    (aa.testConnectionOnCheckin           ? 1<<1 : 0) ^
		    (aa.autoCommitOnClose                 ? 1<<2 : 0) ^
		    (aa.forceIgnoreUnresolvedTransactions ? 1<<3 : 0) ^
		    (aa.breakAfterAcquireFailure          ? 1<<4 : 0) ^
		    aa.defaultAuth.hashCode() ^
		    aa.connectionTester.getClass().hashCode(); 
		//System.err.println("coalesceHash() --> " + out);
		return out;
	    };
	};

    // unsync'ed coalescer -- we synchronize the static factory method that uses it
    final static Coalescer COALESCER = CoalescerFactory.createCoalescer( COALESCE_CHECKER, true, false );

    final static int DFLT_NUM_TASK_THREADS_PER_DATA_SOURCE = 3;

    //MT: protected by this' lock
    Set activeClients = new HashSet();
    //RoundRobinAsynchronousRunner taskRunner;
    ThreadPoolAsynchronousRunner taskRunner;
    Timer                        timer; 
    ResourcePoolFactory          rpfact;
    GooGooStatementCache         scache;
    Map                          authsToPools;
    boolean                      is_first_pool = true;
    String                       realTestQuery;

    String poolOwnerIdentityToken = null;

    /* MT: independently thread-safe, never reassigned post-ctor or factory */
    final ConnectionPoolDataSource cpds;
    final String                   sourceCpdsIdentityToken;
    /* MT: end independently thread-safe, never reassigned post-ctor or factory */

    /* MT: unchanging after constructor completes */
    int num_task_threads = DFLT_NUM_TASK_THREADS_PER_DATA_SOURCE;

    int maxStatements                          = PoolConfig.defaultMaxStatements(); 
    int maxStatementsPerConnection             = PoolConfig.defaultMaxStatementsPerConnection(); 
    int minPoolSize                            = PoolConfig.defaultMinPoolSize();  
    int maxPoolSize                            = PoolConfig.defaultMaxPoolSize();  
    int idleConnectionTestPeriod               = PoolConfig.defaultIdleConnectionTestPeriod();
    int maxIdleTime                            = PoolConfig.defaultMaxIdleTime();  
    int checkoutTimeout                        = PoolConfig.defaultCheckoutTimeout(); 
    int acquireIncrement                       = PoolConfig.defaultAcquireIncrement(); 
    int acquireRetryAttempts                   = PoolConfig.defaultAcquireRetryAttempts(); 
    int acquireRetryDelay                      = PoolConfig.defaultAcquireRetryDelay(); 
    boolean breakAfterAcquireFailure           = PoolConfig.defaultBreakAfterAcquireFailure(); 
    boolean testConnectionOnCheckout           = PoolConfig.defaultTestConnectionOnCheckout(); 
    boolean testConnectionOnCheckin            = PoolConfig.defaultTestConnectionOnCheckin(); 
    boolean autoCommitOnClose                  = PoolConfig.defaultAutoCommitOnClose(); 
    boolean forceIgnoreUnresolvedTransactions  = PoolConfig.defaultForceIgnoreUnresolvedTransactions(); 
    String preferredTestQuery                  = PoolConfig.defaultPreferredTestQuery(); 
    String automaticTestTable                  = PoolConfig.defaultAutomaticTestTable(); 
    DbAuth defaultAuth                         = C3P0ImplUtils.NULL_AUTH; 
    ConnectionTester connectionTester;

    // connectionTester default setup is more than a one-liner...
    {
	try { connectionTester = (ConnectionTester) Class.forName( PoolConfig.defaultConnectionTesterClassName() ).newInstance(); }
	catch ( Exception e )
	    {
		//e.printStackTrace();
		logger.log(MLevel.WARNING, 
			   "Could not load ConnectionTester " + PoolConfig.defaultConnectionTesterClassName() +
			   ", using built in default.", 
			   e);
		connectionTester = C3P0Defaults.connectionTester();
	    }
    }
    /* MT: end unchanging after constructor completes */

    private synchronized void poolsInit()
    {
	this.timer = new Timer( true );
	this.taskRunner = new ThreadPoolAsynchronousRunner( num_task_threads, true, timer );
	//this.taskRunner = new RoundRobinAsynchronousRunner( num_task_threads, true );
	//this.rpfact = ResourcePoolFactory.createInstance( taskRunner, timer );
	if (POOL_EVENT_SUPPORT)
	    this.rpfact = ResourcePoolFactory.createInstance( taskRunner, null, timer );
	else
	    this.rpfact = BasicResourcePoolFactory.createNoEventSupportInstance( taskRunner, timer );
	if (this.maxStatements > 0 && this.maxStatementsPerConnection > 0)
	    this.scache = new DoubleMaxStatementCache( taskRunner, maxStatements, maxStatementsPerConnection );
	else if (this.maxStatementsPerConnection > 0)
	    this.scache = new PerConnectionMaxOnlyStatementCache( taskRunner, maxStatementsPerConnection );
	else if (this.maxStatements > 0)
	    this.scache = new GlobalMaxOnlyStatementCache( taskRunner, maxStatements );
	else
	    this.scache = null;
	this.authsToPools = new HashMap();
    }

    private synchronized void poolsDestroy()
    {
	//System.err.println("poolsDestroy() -- " + this);
	for (Iterator ii = authsToPools.values().iterator(); ii.hasNext(); )
	    {
		try
		    { ((C3P0PooledConnectionPool) ii.next()).close(); }
		catch ( Exception e )
		    { 
			//e.printStackTrace(); 
			logger.log(MLevel.WARNING, "An Exception occurred while trying to clean up a pool!", e);
		    }
	    }

	this.taskRunner.close( true );
	this.timer.cancel();

	try {if (scache != null) scache.close();}
	catch (SQLException e)
	    { 
		//e.printStackTrace(); 
		logger.log(MLevel.WARNING, "An Exception occurred while trying to clean up a Statement cache!", e);
	    }

	this.scache = null;
	this.taskRunner = null;
	this.timer = null;
	this.rpfact = null;
	this.authsToPools = null;
    }

    /*
     * COALESCER is unsync'ed -- we sync the factory method instead
     */
    public synchronized static C3P0PooledConnectionPoolManager find(String poolOwnerIdentityToken, 
								    ConnectionPoolDataSource cpds, 
								    String sourceCpdsIdentityToken, 
								    int num_task_threads )
	throws SQLException
    {
 	C3P0PooledConnectionPoolManager nascent = new C3P0PooledConnectionPoolManager( poolOwnerIdentityToken, 
										       cpds, 
										       sourceCpdsIdentityToken, 
										       num_task_threads );
	C3P0PooledConnectionPoolManager out = (C3P0PooledConnectionPoolManager) COALESCER.coalesce( nascent );
	if ( out == nascent ) //the new guy is the ONE
	    out.poolsInit();
	//System.err.println("CANONICAL: " + out);
	return out;
    }

    private C3P0PooledConnectionPoolManager(String poolOwnerIdentityToken, 
					    ConnectionPoolDataSource cpds, 
					    String sourceCpdsIdentityToken, 
					    int num_task_threads )
	throws SQLException
    {
	try
	    {
		this.poolOwnerIdentityToken = poolOwnerIdentityToken;
		this.cpds = cpds;
		this.sourceCpdsIdentityToken = sourceCpdsIdentityToken;
		this.num_task_threads = num_task_threads;

		// we look for non-standard props user and
		// password, available as read-only props on
		// our implementation of ConnectionPoolDataSource.
		//
		// If other implementations are used, the only
		// hazard is the possibility that there will be 
		// two pools for the same real authorization credentials
		// one for when the credentials are explicitly specified,
		// and one for when the defaults are used.

		this.defaultAuth = C3P0ImplUtils.findAuth( cpds );

		BeanInfo bi = Introspector.getBeanInfo( cpds.getClass() );
		PropertyDescriptor[] pds = bi.getPropertyDescriptors();
		for (int i = 0, len = pds.length; i < len; ++i)
		    {
			PropertyDescriptor pd = pds[i];
			Class propCl = pd.getPropertyType();
			String propName = pd.getName();
			Method readMethod = pd.getReadMethod();
			Object propVal;
			if (propCl == int.class)
			    {
				propVal = readMethod.invoke( cpds, C3P0ImplUtils.NOARGS );
				int value = ((Integer) propVal).intValue();
				if ("maxStatements".equals(propName))
				    this.maxStatements = value;
				else if ("maxStatementsPerConnection".equals(propName))
				    this.maxStatementsPerConnection = value;
				else if ("minPoolSize".equals(propName))
				    this.minPoolSize = value;
				else if ("maxPoolSize".equals(propName))
				    this.maxPoolSize = value;
				else if ("idleConnectionTestPeriod".equals(propName))
				    this.idleConnectionTestPeriod = value;
				else if ("maxIdleTime".equals(propName))
				    this.maxIdleTime = value;
				else if ("checkoutTimeout".equals(propName))
				    this.checkoutTimeout = value;
				else if ("acquireIncrement".equals(propName))
				    this.acquireIncrement = value;
				else if ("acquireRetryAttempts".equals(propName))
				    this.acquireRetryAttempts = value;
				else if ("acquireRetryDelay".equals(propName))
				    this.acquireRetryDelay = value;
				// System.err.println( propName + " -> " + propVal );
			    }
			else if (propCl == String.class)
			    {
				propVal = readMethod.invoke( cpds, C3P0ImplUtils.NOARGS );
				String value = (String) propVal;
				if ("connectionTesterClassName".equals(propName))
				    this.connectionTester =
					(ConnectionTester) Class.forName( value ).newInstance();
				else if ("preferredTestQuery".equals(propName))
				    this.preferredTestQuery = value;
				else if ("automaticTestTable".equals(propName))
				    this.automaticTestTable = value;
				// System.err.println( propName + " -> " + propVal );
			    }
			else if (propCl == boolean.class)
			    {
				propVal = readMethod.invoke( cpds, C3P0ImplUtils.NOARGS );
				boolean value = ((Boolean) propVal).booleanValue();
				if ("testConnectionOnCheckout".equals(propName))
				    this.testConnectionOnCheckout = value;
				else if ("testConnectionOnCheckin".equals(propName))
				    this.testConnectionOnCheckin = value;
				else if ("autoCommitOnClose".equals(propName))
				    this.autoCommitOnClose = value;
				else if ("forceIgnoreUnresolvedTransactions".equals(propName))
				    this.forceIgnoreUnresolvedTransactions = value;
				else if ("breakAfterAcquireFailure".equals(propName))
				    this.breakAfterAcquireFailure = value;
				// System.err.println( propName + " -> " + propVal );
			    }

		    }
	    }
	catch (Exception e)
	    {
		if (Debug.DEBUG)
		    logger.log(MLevel.FINE, null, e);
		    //e.printStackTrace();
		throw SqlUtils.toSQLException(e);
	    }
    }

    public synchronized C3P0PooledConnectionPool getPool(String username, String password)
	throws SQLException
    { return getPool( new DbAuth( username, password ) ); }

    public synchronized C3P0PooledConnectionPool getPool(DbAuth auth)
	throws SQLException
    {
	C3P0PooledConnectionPool out = (C3P0PooledConnectionPool) authsToPools.get(auth);
	if (out == null)
	    {
		out = createPooledConnectionPool(auth);
		authsToPools.put( auth, out );
	    }
	return out;
    }

//
// best from a security perspective if we keep these to ourselves...
//

//     public synchronized Set getManagedAuths()
//     { return Collections.unmodifiableSet( authsToPools.keySet() ); }

    public synchronized int getNumManagedAuths()
    { return authsToPools.size(); }

    public C3P0PooledConnectionPool getPool()
	throws SQLException
    { return getPool( defaultAuth ); }

    public synchronized int getNumIdleConnectionsAllAuths() throws SQLException
    {
	int out = 0;
	for (Iterator ii = authsToPools.values().iterator(); ii.hasNext(); )
	    out += ((C3P0PooledConnectionPool) ii.next()).getNumIdleConnections();
	return out;
    }

    public synchronized int getNumBusyConnectionsAllAuths() throws SQLException
    {
	int out = 0;
	for (Iterator ii = authsToPools.values().iterator(); ii.hasNext(); )
	    out += ((C3P0PooledConnectionPool) ii.next()).getNumBusyConnections();
	return out;
    }

    public synchronized int getNumConnectionsAllAuths() throws SQLException
    {
	int out = 0;
	for (Iterator ii = authsToPools.values().iterator(); ii.hasNext(); )
	    out += ((C3P0PooledConnectionPool) ii.next()).getNumConnections();
	return out;
    }

    public synchronized int getNumUnclosedOrphanedConnectionsAllAuths() throws SQLException
    {
	int out = 0;
	for (Iterator ii = authsToPools.values().iterator(); ii.hasNext(); )
	    out += ((C3P0PooledConnectionPool) ii.next()).getNumUnclosedOrphanedConnections();
	return out;
    }

    public synchronized void softResetAllAuths() throws SQLException
    {
	for (Iterator ii = authsToPools.values().iterator(); ii.hasNext(); )
	    ((C3P0PooledConnectionPool) ii.next()).reset();
    }

    public synchronized void close()
    {
	// System.err.println("close()ing " + this);
	if (authsToPools != null)
	    poolsDestroy();
    }

    protected synchronized void finalize()
    {
	// System.err.println("finalizing... " + this);
 	this.close();
    }

    // called only from sync'ed methods
    private C3P0PooledConnectionPool createPooledConnectionPool(DbAuth auth)
	throws SQLException
    {
	if ( is_first_pool )
	    {
		if (automaticTestTable != null)
		    {
			this.realTestQuery = initializeAutomaticTestTable();
			if (this.preferredTestQuery != null)
			    {
// 				System.err.println("[c3p0] WARNING -- Both automaticTestTable and preferredTestQuery have been set! " +
// 						   "Using automaticTestTable, and ignoring preferredTestQuery. Real test query is '" +
// 						   realTestQuery + "'.");
				
				if ( logger.isLoggable( MLevel.WARNING ) )
				    {
					logger.logp(MLevel.WARNING, 
						    C3P0PooledConnectionPoolManager.class.getName(),
						    "createPooledConnectionPool",
						    "[c3p0] Both automaticTestTable and preferredTestQuery have been set! Using automaticTestTable, and ignoring preferredTestQuery. Real test query is ''{0}''.",
						    realTestQuery
						    );
				    }
			    }
		    }
		else
		    {
			this.realTestQuery = this.preferredTestQuery;
		    }
	    }

	C3P0PooledConnectionPool out =  new C3P0PooledConnectionPool( cpds,
								      auth,
								      minPoolSize,
								      maxPoolSize,
								      acquireIncrement,
								      acquireRetryAttempts,
								      acquireRetryDelay,
								      breakAfterAcquireFailure,
								      checkoutTimeout,
								      idleConnectionTestPeriod,
								      maxIdleTime,
								      testConnectionOnCheckout,
								      testConnectionOnCheckin,
								      scache,
								      connectionTester,
								      realTestQuery,
								      rpfact );
	is_first_pool = false;
	return out;
    }

    public synchronized void registerActiveClient( Object o )
    {
	activeClients.add( o );
	if ( this.authsToPools == null )
	    this.poolsInit();
    }

    public synchronized void unregisterActiveClient( Object o )
    {
	// System.err.println("unregisterActiveClient() called.");
	// System.err.println("active clients before -- " + activeClients);
	
	activeClients.remove( o );
	if (activeClients.size() == 0)
	    this.close();

	// System.err.println("active clients after -- " + activeClients);
    }

    public synchronized void forceDestroy()
    {
	if (activeClients.size() > 0)
	    {
		activeClients.clear();
		this.close();
	    }
    }

    // only called from sync'ed methods
    private String initializeAutomaticTestTable() throws SQLException
    {
	PooledConnection throwawayPooledConnection = cpds.getPooledConnection(); 
	Connection c = null;
	PreparedStatement testStmt = null;
	PreparedStatement createStmt = null;
	ResultSet mdrs = null;
	ResultSet rs = null;
	boolean exists;
	boolean has_rows;
	String out;
	try
	    {
		c = throwawayPooledConnection.getConnection();

		DatabaseMetaData dmd = c.getMetaData();
		String q = dmd.getIdentifierQuoteString();
		String quotedTableName = q + automaticTestTable + q;
		out = "SELECT * FROM " + quotedTableName;
		mdrs = dmd.getTables( null, null, automaticTestTable, new String[] {"TABLE"} );
		exists = mdrs.next();

		//System.err.println("Table " + automaticTestTable + " exists? " + exists);
		
		if (exists)
		    {
			testStmt = c.prepareStatement( out );
			rs = testStmt.executeQuery();
			has_rows = rs.next();
			if (has_rows)
			    throw new SQLException("automatic test table '" + automaticTestTable + 
						   "' contains rows, and it should not! Please set this " +
						   "parameter to the name of a table c3p0 can create on its own, " +
						   "that is not used elsewhere in the database!");
		    }
		else
		    {
			createStmt = c.prepareStatement("CREATE TABLE " + quotedTableName + " ( a CHAR(1) )");
			createStmt.executeUpdate();
		    }
		return out;
	    }
	finally
	    { 
		ResultSetUtils.attemptClose( mdrs );
		ResultSetUtils.attemptClose( rs );
		StatementUtils.attemptClose( testStmt );
		StatementUtils.attemptClose( createStmt );
		ConnectionUtils.attemptClose( c ); 
		try{ if (throwawayPooledConnection != null) throwawayPooledConnection.close(); }
		catch ( Exception e ) 
		    { 
			//e.printStackTrace(); 
			logger.log(MLevel.WARNING, "A PooledConnection failed to close.", e);
		    }
	    }
    }
}





//     public static find(ConnectionPoolDataSource cpds,
// 		       DbAuth defaultAuth,      //may be null
// 		       int maxStatements,
// 		       int minPoolSize,
// 		       int maxPoolSize,
// 		       int idleConnectionTestPeriod,
// 		       int maxIdleTime,
// 		       int acquireIncrement,
// 		       boolean testConnectionOnCheckout,
// 		       boolean autoCommitOnClose,
// 		       boolean forceIgnoreUnresolvedTransactions,
// 		       ConnectionTester connectionTester)
//     {
// 	C3P0PooledConnectionPoolManager nascent = new C3P0PooledConnectionPoolManager( cpds,
// 										       defaultAuth,  
// 										       maxStatements,
// 										       minPoolSize,
// 										       maxPoolSize,
// 										       idleConnectionTestPeriod,
// 										       maxIdleTime,
// 										       acquireIncrement,
// 										       testConnectionOnCheckout,
// 										       autoCommitOnClose,
// 										       forceIgnoreUnresolvedTransactions,
// 										       connectionTester);
// 	C3P0PooledConnectionPoolManager out = (C3P0PooledConnectionPoolManager) coalescer.coalesce( nascent );
// 	if ( out == nascent ) //the new guy is the ONE
// 	    out.poolInit();
// 	return out;
//     }

//     private C3P0PooledConnectionPoolManager(ConnectionPoolDataSource cpds,
// 					    DbAuth defaultAuth,      //may be null
// 					    int maxStatements,
// 					    int minPoolSize,
// 					    int maxPoolSize,
// 					    int idleConnectionTestPeriod,
// 					    int maxIdleTime,
// 					    int acquireIncrement,
// 					    boolean testConnectionOnCheckout,
// 					    boolean autoCommitOnClose,
// 					    boolean forceIgnoreUnresolvedTransactions,
// 					    ConnectionTester connectionTester)
//     {
// 	this.cpds = cpds;
// 	this.defaultAuth = (defaultAuth == null ? C3P0ImplUtils.NULL_AUTH : defaultAuth);
// 	this.maxStatements = maxStatements;
// 	this.minPoolSize = minPoolSize;
// 	this.maxPoolSize = maxPoolSize;
// 	this.idleConnectionTestPeriod = idleConnectionTestPeriod;
// 	this.maxIdleTime = maxIdleTime;
// 	this.acquireIncrement = acquireIncrement;
// 	this.testConnectionOnCheckout = testConnectionOnCheckout;
// 	this.autoCommitOnClose = autoCommitOnClose;
// 	this.testConnectionOnCheckout = testConnectionOnCheckout;
// 	this.forceIgnoreUnresolvedTransactions = forceIgnoreUnresolvedTransactions;
//     }
				
