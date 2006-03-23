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


package com.mchange.v2.c3p0.impl;

import java.beans.*;
import java.util.*;
import java.lang.reflect.*;
import java.sql.*;
import javax.sql.*;
import com.mchange.v2.c3p0.*;
import com.mchange.v2.c3p0.impl.*;
import com.mchange.v2.c3p0.stmt.*;
import com.mchange.v2.c3p0.cfg.*;
import com.mchange.v2.async.*;
import com.mchange.v2.coalesce.*;
import com.mchange.v1.db.sql.*;
import com.mchange.v2.log.*;
import com.mchange.v1.lang.BooleanUtils;
import com.mchange.v2.sql.SqlUtils;
import com.mchange.v2.resourcepool.ResourcePoolFactory;
import com.mchange.v2.resourcepool.BasicResourcePoolFactory;

public final class C3P0PooledConnectionPoolManager
{
    private final static MLogger logger = MLog.getLogger( C3P0PooledConnectionPoolManager.class );

    private final static boolean POOL_EVENT_SUPPORT = false;

    private final static CoalesceChecker COALESCE_CHECKER = IdentityTokenizedCoalesceChecker.INSTANCE;

    // unsync'ed coalescer -- we synchronize the static factory method that uses it
    final static Coalescer COALESCER = CoalescerFactory.createCoalescer( COALESCE_CHECKER, true, false );

    final static int DFLT_NUM_TASK_THREADS_PER_DATA_SOURCE = 3;

    //MT: protected by this' lock
    ThreadPoolAsynchronousRunner taskRunner;
    Timer                        timer; 
    ResourcePoolFactory          rpfact;
    Map                          authsToPools;

    /* MT: independently thread-safe, never reassigned post-ctor or factory */
    final ConnectionPoolDataSource cpds;
    final Map propNamesToReadMethods;
    final Map flatPropertyOverrides;
    final Map userOverrides;
    final DbAuth defaultAuth;
    /* MT: end independently thread-safe, never reassigned post-ctor or factory */

    /* MT: unchanging after constructor completes */
    int num_task_threads = DFLT_NUM_TASK_THREADS_PER_DATA_SOURCE;

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
	this.authsToPools = new HashMap();
    }

    private void poolsDestroy()
    { poolsDestroy( true ); }

    private synchronized void poolsDestroy( boolean close_outstanding_connections )
    {
	//System.err.println("poolsDestroy() -- " + this);
	for (Iterator ii = authsToPools.values().iterator(); ii.hasNext(); )
	    {
		try
		    { ((C3P0PooledConnectionPool) ii.next()).close( close_outstanding_connections ); }
		catch ( Exception e )
		    { 
			//e.printStackTrace(); 
			logger.log(MLevel.WARNING, "An Exception occurred while trying to clean up a pool!", e);
		    }
	    }

	this.taskRunner.close( true );
	this.timer.cancel();

	this.taskRunner = null;
	this.timer = null;
	this.rpfact = null;
	this.authsToPools = null;
    }

    public C3P0PooledConnectionPoolManager(ConnectionPoolDataSource cpds, 
					   Map flatPropertyOverrides,     // Map of properties, usually null
					   Map forceUserOverrides,        // userNames to Map of properties, usually null
					   int num_task_threads )
	throws SQLException
    {
	try
	    {
		this.cpds = cpds;
		this.flatPropertyOverrides = flatPropertyOverrides;
		this.num_task_threads = num_task_threads;

		DbAuth auth = null;

		if ( flatPropertyOverrides != null )
		    {
			String overrideUser     = (String) flatPropertyOverrides.get("overrideDefaultUser");
			String overridePassword = (String) flatPropertyOverrides.get("overrideDefaultPassword");

			if (overrideUser == null)
			    {
				overrideUser     = (String) flatPropertyOverrides.get("user");
				overridePassword = (String) flatPropertyOverrides.get("password");
			    }

			if (overrideUser != null)
			    auth = new DbAuth( overrideUser, overridePassword );
		    }

		if (auth == null)
		    auth = C3P0ImplUtils.findAuth( cpds );

		this.defaultAuth = auth;

		Map tmp = new HashMap();
		BeanInfo bi = Introspector.getBeanInfo( cpds.getClass() );
		PropertyDescriptor[] pds = bi.getPropertyDescriptors();
		PropertyDescriptor pd = null;
		for (int i = 0, len = pds.length; i < len; ++i)
		    {
			pd = pds[i];

			String name = pd.getName();
			Method m = pd.getReadMethod();

			if (m != null)
			    tmp.put( name, m );
		    }
		this.propNamesToReadMethods = tmp;

		if (forceUserOverrides == null)
		    {
			Method uom = (Method) propNamesToReadMethods.get( "userOverridesAsString" );
			if (uom != null)
			    {
				String uoas = (String) uom.invoke( cpds, null );
				//System.err.println("uoas: " + uoas);
				Map uo = C3P0ImplUtils.parseUserOverridesAsString( uoas );
				this.userOverrides = uo;
			    }
			else
			    this.userOverrides = Collections.EMPTY_MAP;
		    }
		else
		    this.userOverrides = forceUserOverrides;

		poolsInit();
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

    public synchronized Set getManagedAuths()
    { return Collections.unmodifiableSet( authsToPools.keySet() ); }

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

    public void close()
    { this.close( true ); }

    public synchronized void close( boolean close_outstanding_connections )
    {
	// System.err.println("close()ing " + this);
	if (authsToPools != null)
	    poolsDestroy( close_outstanding_connections );
    }

    protected synchronized void finalize()
    {
	// System.err.println("finalizing... " + this);
 	this.close();
    }

    private Object getObject(String propName, String userName)
    {
	Object out = null;

	if (userName != null)
	    {
		//userOverrides are usually config file defined, unless rarely used forceUserOverrides is supplied!
		Map specificUserOverrides = (Map) userOverrides.get( userName ); 
		if (specificUserOverrides != null)
		    out = specificUserOverrides.get( propName );
	    }

	if (out == null && flatPropertyOverrides != null) //flatPropertyOverrides is a rarely used mechanism for forcing a config
	    out = flatPropertyOverrides.get( propName );

	//if the ConnectionPoolDataSource has config parameter defined as a property use it 
	//(unless there was a user-specific or force override found above)
	if (out == null) 
	    {
		try
		    {
			Method m = (Method) propNamesToReadMethods.get( propName );
			if (m != null)
			    {
				Object readProp = m.invoke( cpds, null );
				if (readProp != null)
				    out = readProp.toString();
			    }
		    }
		catch (Exception e)
		    {
			if (logger.isLoggable( MLevel.WARNING ))
			    logger.log(MLevel.WARNING, 
				       "An exception occurred while trying to read property '" + propName + 
				       "' from ConnectionPoolDataSource: " + cpds +
				       ". Default config value will be used.",
				       e );
		    }
	    }

	//if the ConnectionPoolDataSource DID NOT have config parameter defined as a property
	//(and there was no user-specific or force override)
	//use config-defined default
	if (out == null)
	    out = C3P0Config.getUnspecifiedUserProperty( propName, null );

	return out;
    }

    private String getString(String propName, String userName)
    {
	Object o = getObject( propName,  userName);
	return (o == null ? null : o.toString());
    }

    private int getInt(String propName, String userName) throws Exception
    {
	Object o = getObject( propName,  userName);
	if (o instanceof Integer)
	    return ((Integer) o).intValue();
	else if (o instanceof String)
	    return Integer.parseInt( (String) o );
	else
	    throw new Exception("Unexpected object found for putative int property '" + propName +"': " + o);
    }

    private boolean getBoolean(String propName, String userName) throws Exception
    {
	Object o = getObject( propName,  userName);
	if (o instanceof Boolean)
	    return ((Boolean) o).booleanValue();
	else if (o instanceof String)
	    return BooleanUtils.parseBoolean( (String) o );
	else
	    throw new Exception("Unexpected object found for putative boolean property '" + propName +"': " + o);
    }

    public String getAutomaticTestTable(String userName)
    { return getString("automaticTestTable", userName ); }

    public String getPreferredTestQuery(String userName)
    { return getString("preferredTestQuery", userName ); }

    public int getMinPoolSize(String userName)
    {
	try
	    { return getInt("minPoolSize", userName ); }
	catch (Exception e)
	    {
		if ( logger.isLoggable( MLevel.FINE ) )
		    logger.log( MLevel.FINE, "Could not fetch int property", e);
		return C3P0Defaults.minPoolSize();
	    }
    }

    private int getMaxPoolSize(String userName)
    {
	try
	    { return getInt("maxPoolSize", userName ); }
	catch (Exception e)
	    {
		if ( logger.isLoggable( MLevel.FINE ) )
		    logger.log( MLevel.FINE, "Could not fetch int property", e);
		return C3P0Defaults.maxPoolSize();
	    }
    }

    private int getMaxStatements(String userName)
    {
	try
	    { return getInt("maxStatements", userName ); }
	catch (Exception e)
	    {
		if ( logger.isLoggable( MLevel.FINE ) )
		    logger.log( MLevel.FINE, "Could not fetch int property", e);
		return C3P0Defaults.maxStatements();
	    }
    }

    private int getMaxStatementsPerConnection(String userName)
    {
	try
	    { return getInt("maxStatementsPerConnection", userName ); }
	catch (Exception e)
	    {
		if ( logger.isLoggable( MLevel.FINE ) )
		    logger.log( MLevel.FINE, "Could not fetch int property", e);
		return C3P0Defaults.maxStatementsPerConnection();
	    }
    }

    private int getAcquireIncrement(String userName)
    {
	try
	    { return getInt("acquireIncrement", userName ); }
	catch (Exception e)
	    {
		if ( logger.isLoggable( MLevel.FINE ) )
		    logger.log( MLevel.FINE, "Could not fetch int property", e);
		return C3P0Defaults.acquireIncrement();
	    }
    }

    private int getAcquireRetryAttempts(String userName)
    {
	try
	    { return getInt("acquireRetryAttempts", userName ); }
	catch (Exception e)
	    {
		if ( logger.isLoggable( MLevel.FINE ) )
		    logger.log( MLevel.FINE, "Could not fetch int property", e);
		return C3P0Defaults.acquireRetryAttempts();
	    }
    }

    private int getAcquireRetryDelay(String userName)
    {
	try
	    { return getInt("acquireRetryDelay", userName ); }
	catch (Exception e)
	    {
		if ( logger.isLoggable( MLevel.FINE ) )
		    logger.log( MLevel.FINE, "Could not fetch int property", e);
		return C3P0Defaults.acquireRetryDelay();
	    }
    }

    private boolean getBreakAfterAcquireFailure(String userName)
    {
	try
	    { return getBoolean("breakAfterAcquireFailure", userName ); }
	catch (Exception e)
	    {
		if ( logger.isLoggable( MLevel.FINE ) )
		    logger.log( MLevel.FINE, "Could not fetch boolean property", e);
		return C3P0Defaults.breakAfterAcquireFailure();
	    }
    }

    private int getCheckoutTimeout(String userName)
    {
	try
	    { return getInt("checkoutTimeout", userName ); }
	catch (Exception e)
	    {
		if ( logger.isLoggable( MLevel.FINE ) )
		    logger.log( MLevel.FINE, "Could not fetch int property", e);
		return C3P0Defaults.checkoutTimeout();
	    }
    }

    private int getIdleConnectionTestPeriod(String userName)
    {
	try
	    { return getInt("idleConnectionTestPeriod", userName ); }
	catch (Exception e)
	    {
		if ( logger.isLoggable( MLevel.FINE ) )
		    logger.log( MLevel.FINE, "Could not fetch int property", e);
		return C3P0Defaults.idleConnectionTestPeriod();
	    }
    }

    private int getMaxIdleTime(String userName)
    {
	try
	    { return getInt("maxIdleTime", userName ); }
	catch (Exception e)
	    {
		if ( logger.isLoggable( MLevel.FINE ) )
		    logger.log( MLevel.FINE, "Could not fetch int property", e);
		return C3P0Defaults.maxIdleTime();
	    }
    }

    private boolean getTestConnectionOnCheckout(String userName)
    {
	try
	    { return getBoolean("testConnectionOnCheckout", userName ); }
	catch (Exception e)
	    {
		if ( logger.isLoggable( MLevel.FINE ) )
		    logger.log( MLevel.FINE, "Could not fetch boolean property", e);
		return C3P0Defaults.testConnectionOnCheckout();
	    }
    }

    private boolean getTestConnectionOnCheckin(String userName)
    {
	try
	    { return getBoolean("testConnectionOnCheckin", userName ); }
	catch (Exception e)
	    {
		if ( logger.isLoggable( MLevel.FINE ) )
		    logger.log( MLevel.FINE, "Could not fetch boolean property", e);
		return C3P0Defaults.testConnectionOnCheckin();
	    }
    }

    public String getConnectionTesterClassName(String userName)
    { return getString("connectionTesterClassName", userName ); }

    private ConnectionTester getConnectionTester(String userName)
    { return C3P0Registry.getConnectionTester( getConnectionTesterClassName( userName ) ); }

    // called only from sync'ed methods
    private C3P0PooledConnectionPool createPooledConnectionPool(DbAuth auth)
	throws SQLException
    {
	String userName = auth.getUser();
	String automaticTestTable = getAutomaticTestTable( userName );
	String realTestQuery;

	if (automaticTestTable != null)
	    {
		realTestQuery = initializeAutomaticTestTable( automaticTestTable );
		if (this.getPreferredTestQuery( userName ) != null)
		    {
			if ( logger.isLoggable( MLevel.WARNING ) )
			    {
				logger.logp(MLevel.WARNING, 
					    C3P0PooledConnectionPoolManager.class.getName(),
					    "createPooledConnectionPool",
					    "[c3p0] Both automaticTestTable and preferredTestQuery have been set! " +
					    "Using automaticTestTable, and ignoring preferredTestQuery. Real test query is ''{0}''.",
					    realTestQuery
					    );
			    }
		    }
	    }
	else
	    realTestQuery = this.getPreferredTestQuery( userName );
    
	C3P0PooledConnectionPool out =  new C3P0PooledConnectionPool( cpds,
								      auth,
								      this.getMinPoolSize( userName ),
								      this.getMaxPoolSize( userName ),
								      this.getAcquireIncrement( userName ),
								      this.getAcquireRetryAttempts( userName ),
								      this.getAcquireRetryDelay( userName ),
								      this.getBreakAfterAcquireFailure( userName ),
								      this.getCheckoutTimeout( userName ),
								      this.getIdleConnectionTestPeriod( userName ),
								      this.getMaxIdleTime( userName ),
								      this.getTestConnectionOnCheckout( userName ),
								      this.getTestConnectionOnCheckin( userName ),
								      this.getMaxStatements( userName ),
								      this.getMaxStatementsPerConnection( userName ),
								      this.getConnectionTester( userName ),
								      realTestQuery,
								      rpfact,
								      taskRunner );
	return out;
    }


    // only called from sync'ed methods
    private String initializeAutomaticTestTable(String automaticTestTable) throws SQLException
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
			String createSql = "CREATE TABLE " + quotedTableName + " ( a CHAR(1) )";
			try
			    {
				createStmt = c.prepareStatement( createSql );
				createStmt.executeUpdate();
			    }
			catch (SQLException e)
			    {
				if (logger.isLoggable( MLevel.WARNING ))
				    logger.log(MLevel.WARNING, 
					       "An attempt to create an automatic test table failed. Create SQL: " +
					       createSql,
					       e );
				throw e;
			    }
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
				
//     private final static CoalesceChecker COALESCE_CHECKER = new CoalesceChecker()
// 	{
// 	    // note that we expect all ConnectionTesters of a single class to be effectively
// 	    // equivalent, since they are to be constructed via a no-arg ctor and no
// 	    // extra initialization is performed. thus we only compare the classes of ConnectionTesters.
// 	    public boolean checkCoalesce( Object a, Object b )
// 	    {
// 		C3P0PooledConnectionPoolManager aa = (C3P0PooledConnectionPoolManager) a;
// 		C3P0PooledConnectionPoolManager bb = (C3P0PooledConnectionPoolManager) b;

// 		return
// 		    aa.poolOwnerIdentityToken.equals( bb.poolOwnerIdentityToken ) &&
// 		    (aa.preferredTestQuery == null ? (bb.preferredTestQuery == null ) : (aa.preferredTestQuery.equals( bb.preferredTestQuery ))) &&
// 		    (aa.automaticTestTable == null ? (bb.automaticTestTable == null ) : (aa.automaticTestTable.equals( bb.automaticTestTable ))) &&
// 		    aa.sourceCpdsIdentityToken.equals( bb.sourceCpdsIdentityToken ) &&
// 		    aa.num_task_threads == bb.num_task_threads &&
// 		    aa.maxStatements == bb.maxStatements &&
// 		    aa.maxStatementsPerConnection == bb.maxStatementsPerConnection &&
// 		    aa.minPoolSize == bb.minPoolSize &&
// 		    aa.idleConnectionTestPeriod == bb.idleConnectionTestPeriod &&
// 		    aa.maxIdleTime == bb.maxIdleTime &&
// 		    aa.checkoutTimeout == bb.checkoutTimeout &&
// 		    aa.acquireIncrement == bb.acquireIncrement &&
// 		    aa.acquireRetryAttempts == bb.acquireRetryAttempts &&
// 		    aa.acquireRetryDelay == bb.acquireRetryDelay &&
// 		    aa.breakAfterAcquireFailure == bb.breakAfterAcquireFailure &&
// 		    aa.testConnectionOnCheckout == bb.testConnectionOnCheckout &&
// 		    aa.testConnectionOnCheckin == bb.testConnectionOnCheckin &&
// 		    aa.autoCommitOnClose == bb.autoCommitOnClose &&
// 		    aa.forceIgnoreUnresolvedTransactions == bb.forceIgnoreUnresolvedTransactions &&
// 		    aa.defaultAuth.equals( bb.defaultAuth ) &&
// 		    aa.connectionTester.getClass().equals( bb.connectionTester.getClass() );
// 	    };

// 	    public int coalesceHash( Object a )
// 	    {
// 		C3P0PooledConnectionPoolManager aa = (C3P0PooledConnectionPoolManager) a;
// 		int out =
// 		    aa.poolOwnerIdentityToken.hashCode() ^
// 		    (aa.preferredTestQuery == null ? 0 : aa.preferredTestQuery.hashCode()) ^
// 		    (aa.automaticTestTable == null ? 0 : aa.automaticTestTable.hashCode()) ^
// 		    aa.sourceCpdsIdentityToken.hashCode() ^
// 		    aa.num_task_threads ^
// 		    aa.maxStatements ^
// 		    aa.maxStatementsPerConnection ^
// 		    aa.minPoolSize ^
// 		    aa.idleConnectionTestPeriod ^
// 		    aa.maxIdleTime ^
// 		    aa.checkoutTimeout ^
// 		    aa.acquireIncrement ^
// 		    aa.acquireRetryAttempts ^
// 		    aa.acquireRetryDelay ^
// 		    (aa.testConnectionOnCheckout          ? 1<<0 : 0) ^
// 		    (aa.testConnectionOnCheckin           ? 1<<1 : 0) ^
// 		    (aa.autoCommitOnClose                 ? 1<<2 : 0) ^
// 		    (aa.forceIgnoreUnresolvedTransactions ? 1<<3 : 0) ^
// 		    (aa.breakAfterAcquireFailure          ? 1<<4 : 0) ^
// 		    aa.defaultAuth.hashCode() ^
// 		    aa.connectionTester.getClass().hashCode(); 
// 		//System.err.println("coalesceHash() --> " + out);
// 		return out;
// 	    };
// 	};

//     int maxStatements                          = PoolConfig.defaultMaxStatements(); 
//     int maxStatementsPerConnection             = PoolConfig.defaultMaxStatementsPerConnection(); 
//     int minPoolSize                            = PoolConfig.defaultMinPoolSize();  
//     int maxPoolSize                            = PoolConfig.defaultMaxPoolSize();  
//     int idleConnectionTestPeriod               = PoolConfig.defaultIdleConnectionTestPeriod();
//     int maxIdleTime                            = PoolConfig.defaultMaxIdleTime();  
//     int checkoutTimeout                        = PoolConfig.defaultCheckoutTimeout(); 
//     int acquireIncrement                       = PoolConfig.defaultAcquireIncrement(); 
//     int acquireRetryAttempts                   = PoolConfig.defaultAcquireRetryAttempts(); 
//     int acquireRetryDelay                      = PoolConfig.defaultAcquireRetryDelay(); 
//     boolean breakAfterAcquireFailure           = PoolConfig.defaultBreakAfterAcquireFailure(); 
//     boolean testConnectionOnCheckout           = PoolConfig.defaultTestConnectionOnCheckout(); 
//     boolean testConnectionOnCheckin            = PoolConfig.defaultTestConnectionOnCheckin(); 
//     boolean autoCommitOnClose                  = PoolConfig.defaultAutoCommitOnClose(); 
//     boolean forceIgnoreUnresolvedTransactions  = PoolConfig.defaultForceIgnoreUnresolvedTransactions(); 
//     String preferredTestQuery                  = PoolConfig.defaultPreferredTestQuery(); 
//     String automaticTestTable                  = PoolConfig.defaultAutomaticTestTable(); 
//     DbAuth defaultAuth                         = C3P0ImplUtils.NULL_AUTH; 
//     ConnectionTester connectionTester          = C3P0ImplUtils.defaultConnectionTester();;


// 		// we look for non-standard props user and
// 		// password, available as read-only props on
// 		// our implementation of ConnectionPoolDataSource.
// 		//
// 		// If other implementations are used, the only
// 		// hazard is the possibility that there will be 
// 		// two pools for the same real authorization credentials
// 		// one for when the credentials are explicitly specified,
// 		// and one for when the defaults are used.

// 		this.defaultAuth = C3P0ImplUtils.findAuth( cpds );

// 		BeanInfo bi = Introspector.getBeanInfo( cpds.getClass() );
// 		PropertyDescriptor[] pds = bi.getPropertyDescriptors();
// 		for (int i = 0, len = pds.length; i < len; ++i)
// 		    {
// 			PropertyDescriptor pd = pds[i];
// 			Class propCl = pd.getPropertyType();
// 			String propName = pd.getName();
// 			Method readMethod = pd.getReadMethod();
// 			Object propVal;
// 			if (propCl == int.class)
// 			    {
// 				propVal = readMethod.invoke( cpds, C3P0ImplUtils.NOARGS );
// 				int value = ((Integer) propVal).intValue();
// 				if ("maxStatements".equals(propName))
// 				    this.maxStatements = value;
// 				else if ("maxStatementsPerConnection".equals(propName))
// 				    this.maxStatementsPerConnection = value;
// 				else if ("minPoolSize".equals(propName))
// 				    this.minPoolSize = value;
// 				else if ("maxPoolSize".equals(propName))
// 				    this.maxPoolSize = value;
// 				else if ("idleConnectionTestPeriod".equals(propName))
// 				    this.idleConnectionTestPeriod = value;
// 				else if ("maxIdleTime".equals(propName))
// 				    this.maxIdleTime = value;
// 				else if ("checkoutTimeout".equals(propName))
// 				    this.checkoutTimeout = value;
// 				else if ("acquireIncrement".equals(propName))
// 				    this.acquireIncrement = value;
// 				else if ("acquireRetryAttempts".equals(propName))
// 				    this.acquireRetryAttempts = value;
// 				else if ("acquireRetryDelay".equals(propName))
// 				    this.acquireRetryDelay = value;
// 				// System.err.println( propName + " -> " + propVal );
// 			    }
// 			else if (propCl == String.class)
// 			    {
// 				propVal = readMethod.invoke( cpds, C3P0ImplUtils.NOARGS );
// 				String value = (String) propVal;
// 				if ("connectionTesterClassName".equals(propName))
// 				    this.connectionTester =
// 					(ConnectionTester) Class.forName( value ).newInstance();
// 				else if ("preferredTestQuery".equals(propName))
// 				    this.preferredTestQuery = value;
// 				else if ("automaticTestTable".equals(propName))
// 				    this.automaticTestTable = value;
// 				// System.err.println( propName + " -> " + propVal );
// 			    }
// 			else if (propCl == boolean.class)
// 			    {
// 				propVal = readMethod.invoke( cpds, C3P0ImplUtils.NOARGS );
// 				boolean value = ((Boolean) propVal).booleanValue();
// 				if ("testConnectionOnCheckout".equals(propName))
// 				    this.testConnectionOnCheckout = value;
// 				else if ("testConnectionOnCheckin".equals(propName))
// 				    this.testConnectionOnCheckin = value;
// 				else if ("autoCommitOnClose".equals(propName))
// 				    this.autoCommitOnClose = value;
// 				else if ("forceIgnoreUnresolvedTransactions".equals(propName))
// 				    this.forceIgnoreUnresolvedTransactions = value;
// 				else if ("breakAfterAcquireFailure".equals(propName))
// 				    this.breakAfterAcquireFailure = value;
// 				// System.err.println( propName + " -> " + propVal );
// 			    }

// 		    }

