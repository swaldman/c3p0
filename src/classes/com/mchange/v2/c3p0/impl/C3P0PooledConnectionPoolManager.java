/*
 * Distributed as part of c3p0 v.0.8.4.5
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


package com.mchange.v2.c3p0.impl;

import java.beans.*;
import java.util.*;
import java.lang.reflect.*;
import javax.sql.*;
import java.sql.SQLException;
import com.mchange.v2.c3p0.*;
import com.mchange.v2.c3p0.stmt.*;
import com.mchange.v2.async.*;
import com.mchange.v2.coalesce.*;
import com.mchange.v2.sql.SqlUtils;
import com.mchange.v2.resourcepool.ResourcePoolFactory;

public final class C3P0PooledConnectionPoolManager
{
    private final static CoalesceChecker COALESCE_CHECKER = new CoalesceChecker()
	{
	    // note that we expect all ConnectionTesters of a single class to be effectively
	    // equivalent, since they are to be construvcted via a no-arg ctor and no
	    // extra initialization is performed. thus we only compare the classes of ConnectionTesters.
	    public boolean checkCoalesce( Object a, Object b )
	    {
		C3P0PooledConnectionPoolManager aa = (C3P0PooledConnectionPoolManager) a;
		C3P0PooledConnectionPoolManager bb = (C3P0PooledConnectionPoolManager) b;

		return
		    aa.cpds.equals( bb.cpds ) &&
		    aa.num_task_threads == bb.num_task_threads &&
		    aa.maxStatements == bb.maxStatements &&
		    aa.minPoolSize == bb.minPoolSize &&
		    aa.idleConnectionTestPeriod == bb.idleConnectionTestPeriod &&
		    aa.maxIdleTime == bb.maxIdleTime &&
		    aa.acquireIncrement == bb.acquireIncrement &&
		    aa.testConnectionOnCheckout == bb.testConnectionOnCheckout &&
		    aa.autoCommitOnClose == bb.autoCommitOnClose &&
		    aa.forceIgnoreUnresolvedTransactions == bb.forceIgnoreUnresolvedTransactions &&
		    aa.defaultAuth.equals( bb.defaultAuth ) &&
		    aa.connectionTester.getClass().equals( bb.connectionTester.getClass() );
	    };

	    public int coalesceHash( Object a )
	    {
		C3P0PooledConnectionPoolManager aa = (C3P0PooledConnectionPoolManager) a;
		return
		    aa.cpds.hashCode() ^
		    aa.num_task_threads ^
		    aa.maxStatements ^
		    aa.minPoolSize ^
		    aa.idleConnectionTestPeriod ^
		    aa.maxIdleTime ^
		    aa.acquireIncrement ^
		    (aa.testConnectionOnCheckout ? 1<<0 : 0) ^
		    (aa.autoCommitOnClose ? 1<<1 : 0) ^
		    (aa.forceIgnoreUnresolvedTransactions ? 1<<2 : 0) ^
		    aa.defaultAuth.hashCode() ^
		    aa.connectionTester.getClass().hashCode(); 
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

    /* MT: independently thread-safe, never reassigned post-ctor or factory */
    final ConnectionPoolDataSource     cpds;
    /* MT: end independently thread-safe, never reassigned post-ctor or factory */

    /* MT: unchanging after constructor completes */
    int num_task_threads = DFLT_NUM_TASK_THREADS_PER_DATA_SOURCE;

    int maxStatements                          = PoolConfig.defaultMaxStatements(); 
    int minPoolSize                            = PoolConfig.defaultMinPoolSize();  
    int maxPoolSize                            = PoolConfig.defaultMaxPoolSize();  
    int idleConnectionTestPeriod               = PoolConfig.defaultIdleConnectionTestPeriod();
    int maxIdleTime                            = PoolConfig.defaultMaxIdleTime();  
    int acquireIncrement                       = PoolConfig.defaultAcquireIncrement(); 
    boolean testConnectionOnCheckout           = PoolConfig.defaultTestConnectionOnCheckout(); 
    boolean autoCommitOnClose                  = PoolConfig.defaultAutoCommitOnClose(); 
    boolean forceIgnoreUnresolvedTransactions  = PoolConfig.defaultForceIgnoreUnresolvedTransactions(); 
    DbAuth defaultAuth                         = C3P0ImplUtils.NULL_AUTH; 
    ConnectionTester connectionTester;

    // connectionTester default setup is more than a one-liner...
    {
	try { connectionTester = (ConnectionTester) Class.forName( PoolConfig.defaultConnectionTesterClassName() ).newInstance(); }
	catch ( Exception e )
	    {
		e.printStackTrace();
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
	this.rpfact = ResourcePoolFactory.createInstance( taskRunner, null, timer );
	if (this.maxStatements > 0)
	    this.scache = new GooGooStatementCache( taskRunner, maxStatements );
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
		    { e.printStackTrace(); }
	    }

	this.taskRunner.close( true );
	this.timer.cancel();
	this.scache = null;

	this.taskRunner = null;
	this.timer = null;
	this.rpfact = null;
	this.authsToPools = null;
    }

    /*
     * COALESCER is unsync'ed -- we sync the factory method instead
     */
    public synchronized static C3P0PooledConnectionPoolManager find(ConnectionPoolDataSource cpds, int num_task_threads )
	throws SQLException
    {
 	C3P0PooledConnectionPoolManager nascent = new C3P0PooledConnectionPoolManager( cpds, num_task_threads );
	C3P0PooledConnectionPoolManager out = (C3P0PooledConnectionPoolManager) COALESCER.coalesce( nascent );
	if ( out == nascent ) //the new guy is the ONE
	    out.poolsInit();
	return out;
    }

    private C3P0PooledConnectionPoolManager(ConnectionPoolDataSource cpds, int num_task_threads )
	throws SQLException
    {
	try
	    {
		this.cpds = cpds;
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
				else if ("minPoolSize".equals(propName))
				    this.minPoolSize = value;
				else if ("maxPoolSize".equals(propName))
				    this.maxPoolSize = value;
				else if ("idleConnectionTestPeriod".equals(propName))
				    this.idleConnectionTestPeriod = value;
				else if ("maxIdleTime".equals(propName))
				    this.maxIdleTime = value;
				else if ("acquireIncrement".equals(propName))
				    this.acquireIncrement = value;
				// System.err.println( propName + " -> " + propVal );
			    }
			else if (propCl == String.class)
			    {
				propVal = readMethod.invoke( cpds, C3P0ImplUtils.NOARGS );
				String value = (String) propVal;
				if ("connectionTesterClassName".equals(propName))
				    this.connectionTester =
					(ConnectionTester) Class.forName( value ).newInstance();
				// System.err.println( propName + " -> " + propVal );
			    }
			else if (propCl == boolean.class)
			    {
				propVal = readMethod.invoke( cpds, C3P0ImplUtils.NOARGS );
				boolean value = ((Boolean) propVal).booleanValue();
				if ("testConnectionOnCheckout".equals(propName))
				    this.testConnectionOnCheckout = value;
				else if ("autoCommitOnClose".equals(propName))
				    this.autoCommitOnClose = value;
				else if ("forceIgnoreUnresolvedTransactions".equals(propName))
				    this.forceIgnoreUnresolvedTransactions = value;
				// System.err.println( propName + " -> " + propVal );
			    }

		    }
	    }
	catch (Exception e)
	    {
		if (Debug.DEBUG)
		    e.printStackTrace();
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

    public C3P0PooledConnectionPool getPool()
	throws SQLException
    { return getPool( defaultAuth ); }

    public synchronized int getNumConnectionsAllAuths() throws SQLException
    {
	int out = 0;
	for (Iterator ii = authsToPools.values().iterator(); ii.hasNext(); )
	    out += ((C3P0PooledConnectionPool) ii.next()).getNumConnections();
	return out;
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

    private C3P0PooledConnectionPool createPooledConnectionPool(DbAuth auth)
	throws SQLException
    {
	return new C3P0PooledConnectionPool( cpds,
					     auth,
					     minPoolSize,
					     maxPoolSize,
					     acquireIncrement,
					     idleConnectionTestPeriod,
					     maxIdleTime,
					     testConnectionOnCheckout,
					     scache,
					     connectionTester,
					     rpfact );
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
				
