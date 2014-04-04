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

package com.mchange.v2.c3p0.impl;

import java.beans.*;
import java.util.*;
import java.lang.reflect.*;
import java.sql.*;
import javax.sql.*;

import java.security.AccessController;
import java.security.PrivilegedAction;

import com.mchange.v2.c3p0.*;
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
    ThreadPoolAsynchronousRunner deferredStatementDestroyer;
    Timer                        timer; 
    ResourcePoolFactory          rpfact;
    Map                          authsToPools;

    /* MT: independently thread-safe, never reassigned post-ctor or factory */
    final ConnectionPoolDataSource cpds;
    final Map propNamesToReadMethods;
    final Map flatPropertyOverrides;
    final Map userOverrides;
    final DbAuth defaultAuth;
    final String parentDataSourceIdentityToken;
    final String parentDataSourceName;
    /* MT: end independently thread-safe, never reassigned post-ctor or factory */

    /* MT: unchanging after constructor completes */
    int num_task_threads = DFLT_NUM_TASK_THREADS_PER_DATA_SOURCE;

    /* MT: end unchanging after constructor completes */

    public int getThreadPoolSize()
    { return taskRunner.getThreadCount(); }

    public int getThreadPoolNumActiveThreads()
    { return taskRunner.getActiveCount(); }

    public int getThreadPoolNumIdleThreads()
    { return taskRunner.getIdleCount(); }

    public int getThreadPoolNumTasksPending()
    { return taskRunner.getPendingTaskCount(); }

    public String getThreadPoolStackTraces()
    { return taskRunner.getStackTraces(); }

    public String getThreadPoolStatus()
    { return taskRunner.getStatus(); }

    public int getStatementDestroyerNumThreads()
    { return deferredStatementDestroyer != null ? deferredStatementDestroyer.getThreadCount() : -1; }

    public int getStatementDestroyerNumActiveThreads()
    { return deferredStatementDestroyer != null ? deferredStatementDestroyer.getActiveCount() : -1; }

    public int getStatementDestroyerNumIdleThreads()
    { return deferredStatementDestroyer != null ? deferredStatementDestroyer.getIdleCount() : -1; }

    public int getStatementDestroyerNumTasksPending()
    { return deferredStatementDestroyer != null ? deferredStatementDestroyer.getPendingTaskCount() : -1; }

    public String getStatementDestroyerStackTraces()
    { return deferredStatementDestroyer != null ? deferredStatementDestroyer.getStackTraces() : null; }

    public String getStatementDestroyerStatus()
    { return deferredStatementDestroyer != null ? deferredStatementDestroyer.getStatus() : null; }



    private ThreadPoolAsynchronousRunner createTaskRunner( int num_threads, int matt /* maxAdministrativeTaskTime */, Timer timer, String threadLabel )
    {
	ThreadPoolAsynchronousRunner out = null;
        if ( matt > 0 )
        {
            int matt_ms = matt * 1000;
            out = new ThreadPoolAsynchronousRunner( num_threads, 
						    true,        // daemon thread
						    matt_ms,     // wait before interrupt()
						    matt_ms * 3, // wait before deadlock declared if no tasks clear
						    matt_ms * 6, // wait before deadlock tasks are interrupted (again)
							         // after the hung thread has been cleared and replaced
							         // (in hopes of getting the thread to terminate for
							         // garbage collection)
						    timer,
						    threadLabel );
        }
        else
            out = new ThreadPoolAsynchronousRunner( num_threads, true, timer, threadLabel );

	return out;
    }

    private String idString()
    {
	StringBuffer sb = new StringBuffer(512);
	sb.append("C3P0PooledConnectionPoolManager");
	sb.append('[');
	sb.append("identityToken->");
	sb.append(parentDataSourceIdentityToken);
	if (parentDataSourceIdentityToken == null || (! parentDataSourceIdentityToken.equals( parentDataSourceName )))
	{
	    sb.append(", dataSourceName->");
	    sb.append( parentDataSourceName );
	}
	sb.append(']');
	return sb.toString();
    }

    private void maybePrivilegedPoolsInit( final boolean privilege_spawned_threads )
    {
	if ( privilege_spawned_threads )
	{
	    PrivilegedAction<Void> privilegedPoolsInit = new PrivilegedAction<Void>()
	    {
		public Void run()
		{
		    _poolsInit();
		    return null;
		}
	    };
	    AccessController.doPrivileged( privilegedPoolsInit );
	}
	else
	    _poolsInit(); 
    }

    private void poolsInit()
    {
	//Threads are shared by all users, can't support per-user overrides
	final boolean privilege_spawned_threads = getPrivilegeSpawnedThreads();
	final String  contextClassLoaderSource = getContextClassLoaderSource();


	class ContextClassLoaderPoolsInitThread extends Thread
	{
	    ContextClassLoaderPoolsInitThread( ClassLoader ccl )
	    { this.setContextClassLoader( ccl ); }
	    
	    public void run()
	    { maybePrivilegedPoolsInit( privilege_spawned_threads ); }
	};
	
	try
	{
	    if ( "library".equalsIgnoreCase( contextClassLoaderSource ) )
	    {
		Thread t = new ContextClassLoaderPoolsInitThread( this.getClass().getClassLoader() );
		t.start();
		t.join();
	    }
	    else if ( "none".equalsIgnoreCase( contextClassLoaderSource ) )
	    {
		Thread t = new ContextClassLoaderPoolsInitThread( null );
		t.start();
		t.join();
	    }
	    else
	    {
		if ( logger.isLoggable( MLevel.WARNING ) && ! "caller".equalsIgnoreCase( contextClassLoaderSource ) )
		    logger.log( MLevel.WARNING, "Unknown contextClassLoaderSource: " + contextClassLoaderSource + " -- should be 'caller', 'library', or 'none'. Using default value 'caller'." );
		maybePrivilegedPoolsInit( privilege_spawned_threads );
	    }
	}
	catch ( InterruptedException e )
	{
	    if ( logger.isLoggable( MLevel.SEVERE ) )
		logger.log( MLevel.SEVERE, "Unexpected interruption while trying to initialize DataSource Thread resources [ poolsInit() ].", e );
	}
    }

    private synchronized void _poolsInit()
    {
	String idStr = idString();

        this.timer = new Timer(idStr + "-AdminTaskTimer", true );

        int matt = this.getMaxAdministrativeTaskTime();

	this.taskRunner = createTaskRunner( num_task_threads, matt, timer, idStr + "-HelperThread" );
        //this.taskRunner = new RoundRobinAsynchronousRunner( num_task_threads, true );
        //this.rpfact = ResourcePoolFactory.createInstance( taskRunner, timer );

        int num_deferred_close_threads = this.getStatementCacheNumDeferredCloseThreads();
	
	if (num_deferred_close_threads > 0)
	    this.deferredStatementDestroyer = createTaskRunner( num_deferred_close_threads, matt, timer, idStr + "-DeferredStatementDestroyerThread" );
	else
	    this.deferredStatementDestroyer = null;

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

	// we have to run remaining tasks to free Threads that may be caught in wait() on Statement destruction
	if ( deferredStatementDestroyer != null )
	    deferredStatementDestroyer.close( false );

        this.timer.cancel();

        this.taskRunner = null;
        this.timer = null;
        this.rpfact = null;
        this.authsToPools = null;
    }

    public C3P0PooledConnectionPoolManager(ConnectionPoolDataSource cpds, 
					   Map flatPropertyOverrides,     // Map of properties, usually null
					   Map forceUserOverrides,        // userNames to Map of properties, usually null
					   int num_task_threads,
					   String parentDataSourceIdentityToken,
					   String parentDataSourceName)
    throws SQLException
    {
        try
        {
            this.cpds = cpds;
            this.flatPropertyOverrides = flatPropertyOverrides;
            this.num_task_threads = num_task_threads;
            this.parentDataSourceIdentityToken = parentDataSourceIdentityToken;
	    this.parentDataSourceName = parentDataSourceName;

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
                    String uoas = (String) uom.invoke( cpds, (Object[]) null ); // cast to suppress inexact type warning
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

    public synchronized C3P0PooledConnectionPool getPool(String username, String password, boolean create) throws SQLException
    {
        if (create)
            return getPool( username, password );
        else
        {
            DbAuth checkAuth = new DbAuth( username, password );
            C3P0PooledConnectionPool out = (C3P0PooledConnectionPool) authsToPools.get(checkAuth);
            if (out == null)
                throw new SQLException("No pool has been initialized for databse user '" + username + "' with the specified password.");
            else
                return out;
        }
    }

    public C3P0PooledConnectionPool getPool(String username, String password)
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

	    if ( logger.isLoggable( MLevel.FINE ) )
		logger.log( MLevel.FINE, "Created new pool for auth, username (masked): '" + auth.getMaskedUserString() + "'." );
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

    public synchronized int getStatementCacheNumStatementsAllUsers() throws SQLException
    {
        int out = 0;
        for (Iterator ii = authsToPools.values().iterator(); ii.hasNext(); )
            out += ((C3P0PooledConnectionPool) ii.next()).getStatementCacheNumStatements();
        return out;
    }

    public synchronized int getStatementCacheNumCheckedOutStatementsAllUsers() throws SQLException
    {
        int out = 0;
        for (Iterator ii = authsToPools.values().iterator(); ii.hasNext(); )
            out += ((C3P0PooledConnectionPool) ii.next()).getStatementCacheNumCheckedOut();
        return out;
    }

    public synchronized int getStatementCacheNumConnectionsWithCachedStatementsAllUsers() throws SQLException
    {
        int out = 0;
        for (Iterator ii = authsToPools.values().iterator(); ii.hasNext(); )
            out += ((C3P0PooledConnectionPool) ii.next()).getStatementCacheNumConnectionsWithCachedStatements();
        return out;
    }

    public synchronized int getStatementDestroyerNumConnectionsInUseAllUsers() throws SQLException
    {
	if ( deferredStatementDestroyer != null )
	    {
		int out = 0;
		for (Iterator ii = authsToPools.values().iterator(); ii.hasNext(); )
		    out += ((C3P0PooledConnectionPool) ii.next()).getStatementDestroyerNumConnectionsInUse();
		return out;
	    }
	else
	    return -1;
    }

    public synchronized int getStatementDestroyerNumConnectionsWithDeferredDestroyStatementsAllUsers() throws SQLException
    {
	if ( deferredStatementDestroyer != null )
	    {
		int out = 0;
		for (Iterator ii = authsToPools.values().iterator(); ii.hasNext(); )
		    out += ((C3P0PooledConnectionPool) ii.next()).getStatementDestroyerNumConnectionsWithDeferredDestroyStatements();
		return out;
	    }
	else
	    return -1;
    }

    public synchronized int getStatementDestroyerNumDeferredDestroyStatementsAllUsers() throws SQLException
    {
	if ( deferredStatementDestroyer != null )
	    {
		int out = 0;
		for (Iterator ii = authsToPools.values().iterator(); ii.hasNext(); )
		    out += ((C3P0PooledConnectionPool) ii.next()).getStatementDestroyerNumDeferredDestroyStatements();
		return out;
	    }
	else
	    return -1;
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

            //userOverrides are usually config file defined, unless rarely used forceUserOverrides is supplied!
        if (userName != null)
	    out = C3P0ConfigUtils.extractUserOverride( propName, userName, userOverrides );

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
                    Object readProp = m.invoke( cpds, (Object[]) null ); // cast to suppress inexact type warning
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

    private int getInitialPoolSize(String userName)
    {
        try
        { return getInt("initialPoolSize", userName ); }
        catch (Exception e)
        {
            if ( logger.isLoggable( MLevel.FINE ) )
                logger.log( MLevel.FINE, "Could not fetch int property", e);
            return C3P0Defaults.initialPoolSize();
        }
    }

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

    private int getUnreturnedConnectionTimeout(String userName)
    {
        try
        { return getInt("unreturnedConnectionTimeout", userName ); }
        catch (Exception e)
        {
            if ( logger.isLoggable( MLevel.FINE ) )
                logger.log( MLevel.FINE, "Could not fetch int property", e);
            return C3P0Defaults.unreturnedConnectionTimeout();
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

    private boolean getDebugUnreturnedConnectionStackTraces(String userName)
    {
        try
        { return getBoolean("debugUnreturnedConnectionStackTraces", userName ); }
        catch (Exception e)
        {
            if ( logger.isLoggable( MLevel.FINE ) )
                logger.log( MLevel.FINE, "Could not fetch boolean property", e);
            return C3P0Defaults.debugUnreturnedConnectionStackTraces();
        }
    }


    private String getConnectionTesterClassName(String userName)
    { return getString("connectionTesterClassName", userName ); }

    private ConnectionTester getConnectionTester(String userName)
    { return C3P0Registry.getConnectionTester( getConnectionTesterClassName( userName ) ); }

    private String getConnectionCustomizerClassName(String userName)
    { return getString("connectionCustomizerClassName", userName ); }

    private ConnectionCustomizer getConnectionCustomizer(String userName) throws SQLException
    { return C3P0Registry.getConnectionCustomizer( getConnectionCustomizerClassName( userName ) ); }

    private int getMaxIdleTimeExcessConnections(String userName)
    {
        try
        { return getInt("maxIdleTimeExcessConnections", userName ); }
        catch (Exception e)
        {
            if ( logger.isLoggable( MLevel.FINE ) )
                logger.log( MLevel.FINE, "Could not fetch int property", e);
            return C3P0Defaults.maxIdleTimeExcessConnections();
        }
    }

    private int getMaxConnectionAge(String userName)
    {
        try
        { return getInt("maxConnectionAge", userName ); }
        catch (Exception e)
        {
            if ( logger.isLoggable( MLevel.FINE ) )
                logger.log( MLevel.FINE, "Could not fetch int property", e);
            return C3P0Defaults.maxConnectionAge();
        }
    }

    private int getPropertyCycle(String userName)
    {
        try
        { return getInt("propertyCycle", userName ); }
        catch (Exception e)
        {
            if ( logger.isLoggable( MLevel.FINE ) )
                logger.log( MLevel.FINE, "Could not fetch int property", e);
            return C3P0Defaults.propertyCycle();
        }
    }

    // properties that don't support per-user overrides

    private String getContextClassLoaderSource()
    { 
	try { return getString("contextClassLoaderSource", null ); }
        catch (Exception e)
        {
            if ( logger.isLoggable( MLevel.FINE ) )
                logger.log( MLevel.FINE, "Could not fetch String property", e);
            return C3P0Defaults.contextClassLoaderSource();
        }
    }
	
    private boolean getPrivilegeSpawnedThreads()
    { 
	try { return getBoolean("privilegeSpawnedThreads", null ); }
        catch (Exception e)
        {
            if ( logger.isLoggable( MLevel.FINE ) )
                logger.log( MLevel.FINE, "Could not fetch boolean property", e);
            return C3P0Defaults.privilegeSpawnedThreads();
        }
    }
	
    private int getMaxAdministrativeTaskTime()
    {
        try
        { return getInt("maxAdministrativeTaskTime", null ); }
        catch (Exception e)
        {
            if ( logger.isLoggable( MLevel.FINE ) )
                logger.log( MLevel.FINE, "Could not fetch int property", e);
            return C3P0Defaults.maxAdministrativeTaskTime();
        }
    }

    private int getStatementCacheNumDeferredCloseThreads()
    {
        try
        { return getInt("statementCacheNumDeferredCloseThreads", null ); }
        catch (Exception e)
        {
            if ( logger.isLoggable( MLevel.FINE ) )
                logger.log( MLevel.FINE, "Could not fetch int property", e);
            return C3P0Defaults.statementCacheNumDeferredCloseThreads();
        }
    }

    // called only from sync'ed methods
    private C3P0PooledConnectionPool createPooledConnectionPool(DbAuth auth) throws SQLException
    {
        String userName = auth.getUser();
        String automaticTestTable = getAutomaticTestTable( userName );
        String realTestQuery;

        if (automaticTestTable != null)
        {
            realTestQuery = initializeAutomaticTestTable( automaticTestTable, auth );
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
        {
	    // when there is an automaticTestTable to be constructed, we
	    // have little choice but to grab a Connection on initialization
	    // to ensure that the table exists before the pool tries to
	    // test Connections. in c3p0-0.9.1-pre10, i added the check below
	    // to grab and destroy a cxn even when we don't
	    // need one, to ensure that db access params are correct before
	    // we start up a pool. a user who frequently creates and destroys
	    // PooledDataSources complained about the extra initialization
	    // time. the main use of this test was to prevent superfluous
	    // bad pools from being intialized when JMX users type bad
	    // authentification information into a query method. This is
	    // now prevented in AbstractPoolBackedDataSource. Still, it is
	    // easy for clients to start pools uselessly by asking for
	    // Connections with bad authentification information. We adopt
	    // the compromise position of "trusting" the DataSource's default
	    // authentification info (as defined by defaultAuth), but ensuring
	    // that authentification succeeds via the check below when non-default
	    // authentification info is provided.

	    if (! defaultAuth.equals( auth ))
		ensureFirstConnectionAcquisition( auth );

            realTestQuery = this.getPreferredTestQuery( userName );
        }

        C3P0PooledConnectionPool out =  new C3P0PooledConnectionPool( cpds,
								      auth,
								      this.getMinPoolSize( userName ),
								      this.getMaxPoolSize( userName ),
								      this.getInitialPoolSize( userName ),
								      this.getAcquireIncrement( userName ),
								      this.getAcquireRetryAttempts( userName ),
								      this.getAcquireRetryDelay( userName ),
								      this.getBreakAfterAcquireFailure( userName ),
								      this.getCheckoutTimeout( userName ),
								      this.getIdleConnectionTestPeriod( userName ),
								      this.getMaxIdleTime( userName ),
								      this.getMaxIdleTimeExcessConnections( userName ),
								      this.getMaxConnectionAge( userName ),
								      this.getPropertyCycle( userName ),
								      this.getUnreturnedConnectionTimeout( userName ),
								      this.getDebugUnreturnedConnectionStackTraces( userName ),
								      this.getTestConnectionOnCheckout( userName ),
								      this.getTestConnectionOnCheckin( userName ),
								      this.getMaxStatements( userName ),
								      this.getMaxStatementsPerConnection( userName ),
								      this.getConnectionTester( userName ),
								      this.getConnectionCustomizer( userName ),
								      realTestQuery,
								      rpfact,
								      taskRunner,
								      deferredStatementDestroyer,
								      parentDataSourceIdentityToken );
        return out;
    }


    // only called from sync'ed methods
    private String initializeAutomaticTestTable(String automaticTestTable, DbAuth auth) throws SQLException
    {
        PooledConnection throwawayPooledConnection =    auth.equals( defaultAuth ) ? 
                                                        cpds.getPooledConnection() : 
                                                        cpds.getPooledConnection(auth.getUser(), auth.getPassword()); 
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
    
    private void ensureFirstConnectionAcquisition(DbAuth auth) throws SQLException
    {
        PooledConnection throwawayPooledConnection =    auth.equals( defaultAuth ) ? 
                                                        cpds.getPooledConnection() : 
                                                        cpds.getPooledConnection(auth.getUser(), auth.getPassword()); 
        Connection c = null;
        try
        {
            c = throwawayPooledConnection.getConnection();
        }
        finally
        { 
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





//public static find(ConnectionPoolDataSource cpds,
//DbAuth defaultAuth,      //may be null
//int maxStatements,
//int minPoolSize,
//int maxPoolSize,
//int idleConnectionTestPeriod,
//int maxIdleTime,
//int acquireIncrement,
//boolean testConnectionOnCheckout,
//boolean autoCommitOnClose,
//boolean forceIgnoreUnresolvedTransactions,
//ConnectionTester connectionTester)
//{
//C3P0PooledConnectionPoolManager nascent = new C3P0PooledConnectionPoolManager( cpds,
//defaultAuth,  
//maxStatements,
//minPoolSize,
//maxPoolSize,
//idleConnectionTestPeriod,
//maxIdleTime,
//acquireIncrement,
//testConnectionOnCheckout,
//autoCommitOnClose,
//forceIgnoreUnresolvedTransactions,
//connectionTester);
//C3P0PooledConnectionPoolManager out = (C3P0PooledConnectionPoolManager) coalescer.coalesce( nascent );
//if ( out == nascent ) //the new guy is the ONE
//out.poolInit();
//return out;
//}

//private C3P0PooledConnectionPoolManager(ConnectionPoolDataSource cpds,
//DbAuth defaultAuth,      //may be null
//int maxStatements,
//int minPoolSize,
//int maxPoolSize,
//int idleConnectionTestPeriod,
//int maxIdleTime,
//int acquireIncrement,
//boolean testConnectionOnCheckout,
//boolean autoCommitOnClose,
//boolean forceIgnoreUnresolvedTransactions,
//ConnectionTester connectionTester)
//{
//this.cpds = cpds;
//this.defaultAuth = (defaultAuth == null ? C3P0ImplUtils.NULL_AUTH : defaultAuth);
//this.maxStatements = maxStatements;
//this.minPoolSize = minPoolSize;
//this.maxPoolSize = maxPoolSize;
//this.idleConnectionTestPeriod = idleConnectionTestPeriod;
//this.maxIdleTime = maxIdleTime;
//this.acquireIncrement = acquireIncrement;
//this.testConnectionOnCheckout = testConnectionOnCheckout;
//this.autoCommitOnClose = autoCommitOnClose;
//this.testConnectionOnCheckout = testConnectionOnCheckout;
//this.forceIgnoreUnresolvedTransactions = forceIgnoreUnresolvedTransactions;
//}

//private final static CoalesceChecker COALESCE_CHECKER = new CoalesceChecker()
//{
//// note that we expect all ConnectionTesters of a single class to be effectively
//// equivalent, since they are to be constructed via a no-arg ctor and no
//// extra initialization is performed. thus we only compare the classes of ConnectionTesters.
//public boolean checkCoalesce( Object a, Object b )
//{
//C3P0PooledConnectionPoolManager aa = (C3P0PooledConnectionPoolManager) a;
//C3P0PooledConnectionPoolManager bb = (C3P0PooledConnectionPoolManager) b;

//return
//aa.poolOwnerIdentityToken.equals( bb.poolOwnerIdentityToken ) &&
//(aa.preferredTestQuery == null ? (bb.preferredTestQuery == null ) : (aa.preferredTestQuery.equals( bb.preferredTestQuery ))) &&
//(aa.automaticTestTable == null ? (bb.automaticTestTable == null ) : (aa.automaticTestTable.equals( bb.automaticTestTable ))) &&
//aa.sourceCpdsIdentityToken.equals( bb.sourceCpdsIdentityToken ) &&
//aa.num_task_threads == bb.num_task_threads &&
//aa.maxStatements == bb.maxStatements &&
//aa.maxStatementsPerConnection == bb.maxStatementsPerConnection &&
//aa.minPoolSize == bb.minPoolSize &&
//aa.idleConnectionTestPeriod == bb.idleConnectionTestPeriod &&
//aa.maxIdleTime == bb.maxIdleTime &&
//aa.checkoutTimeout == bb.checkoutTimeout &&
//aa.acquireIncrement == bb.acquireIncrement &&
//aa.acquireRetryAttempts == bb.acquireRetryAttempts &&
//aa.acquireRetryDelay == bb.acquireRetryDelay &&
//aa.breakAfterAcquireFailure == bb.breakAfterAcquireFailure &&
//aa.testConnectionOnCheckout == bb.testConnectionOnCheckout &&
//aa.testConnectionOnCheckin == bb.testConnectionOnCheckin &&
//aa.autoCommitOnClose == bb.autoCommitOnClose &&
//aa.forceIgnoreUnresolvedTransactions == bb.forceIgnoreUnresolvedTransactions &&
//aa.defaultAuth.equals( bb.defaultAuth ) &&
//aa.connectionTester.getClass().equals( bb.connectionTester.getClass() );
//};

//public int coalesceHash( Object a )
//{
//C3P0PooledConnectionPoolManager aa = (C3P0PooledConnectionPoolManager) a;
//int out =
//aa.poolOwnerIdentityToken.hashCode() ^
//(aa.preferredTestQuery == null ? 0 : aa.preferredTestQuery.hashCode()) ^
//(aa.automaticTestTable == null ? 0 : aa.automaticTestTable.hashCode()) ^
//aa.sourceCpdsIdentityToken.hashCode() ^
//aa.num_task_threads ^
//aa.maxStatements ^
//aa.maxStatementsPerConnection ^
//aa.minPoolSize ^
//aa.idleConnectionTestPeriod ^
//aa.maxIdleTime ^
//aa.checkoutTimeout ^
//aa.acquireIncrement ^
//aa.acquireRetryAttempts ^
//aa.acquireRetryDelay ^
//(aa.testConnectionOnCheckout          ? 1<<0 : 0) ^
//(aa.testConnectionOnCheckin           ? 1<<1 : 0) ^
//(aa.autoCommitOnClose                 ? 1<<2 : 0) ^
//(aa.forceIgnoreUnresolvedTransactions ? 1<<3 : 0) ^
//(aa.breakAfterAcquireFailure          ? 1<<4 : 0) ^
//aa.defaultAuth.hashCode() ^
//aa.connectionTester.getClass().hashCode(); 
////System.err.println("coalesceHash() --> " + out);
//return out;
//};
//};

//int maxStatements                          = PoolConfig.defaultMaxStatements(); 
//int maxStatementsPerConnection             = PoolConfig.defaultMaxStatementsPerConnection(); 
//int minPoolSize                            = PoolConfig.defaultMinPoolSize();  
//int maxPoolSize                            = PoolConfig.defaultMaxPoolSize();  
//int idleConnectionTestPeriod               = PoolConfig.defaultIdleConnectionTestPeriod();
//int maxIdleTime                            = PoolConfig.defaultMaxIdleTime();  
//int checkoutTimeout                        = PoolConfig.defaultCheckoutTimeout(); 
//int acquireIncrement                       = PoolConfig.defaultAcquireIncrement(); 
//int acquireRetryAttempts                   = PoolConfig.defaultAcquireRetryAttempts(); 
//int acquireRetryDelay                      = PoolConfig.defaultAcquireRetryDelay(); 
//boolean breakAfterAcquireFailure           = PoolConfig.defaultBreakAfterAcquireFailure(); 
//boolean testConnectionOnCheckout           = PoolConfig.defaultTestConnectionOnCheckout(); 
//boolean testConnectionOnCheckin            = PoolConfig.defaultTestConnectionOnCheckin(); 
//boolean autoCommitOnClose                  = PoolConfig.defaultAutoCommitOnClose(); 
//boolean forceIgnoreUnresolvedTransactions  = PoolConfig.defaultForceIgnoreUnresolvedTransactions(); 
//String preferredTestQuery                  = PoolConfig.defaultPreferredTestQuery(); 
//String automaticTestTable                  = PoolConfig.defaultAutomaticTestTable(); 
//DbAuth defaultAuth                         = C3P0ImplUtils.NULL_AUTH; 
//ConnectionTester connectionTester          = C3P0ImplUtils.defaultConnectionTester();;


//// we look for non-standard props user and
//// password, available as read-only props on
//// our implementation of ConnectionPoolDataSource.
////
//// If other implementations are used, the only
//// hazard is the possibility that there will be 
//// two pools for the same real authorization credentials
//// one for when the credentials are explicitly specified,
//// and one for when the defaults are used.

//this.defaultAuth = C3P0ImplUtils.findAuth( cpds );

//BeanInfo bi = Introspector.getBeanInfo( cpds.getClass() );
//PropertyDescriptor[] pds = bi.getPropertyDescriptors();
//for (int i = 0, len = pds.length; i < len; ++i)
//{
//PropertyDescriptor pd = pds[i];
//Class propCl = pd.getPropertyType();
//String propName = pd.getName();
//Method readMethod = pd.getReadMethod();
//Object propVal;
//if (propCl == int.class)
//{
//propVal = readMethod.invoke( cpds, C3P0ImplUtils.NOARGS );
//int value = ((Integer) propVal).intValue();
//if ("maxStatements".equals(propName))
//this.maxStatements = value;
//else if ("maxStatementsPerConnection".equals(propName))
//this.maxStatementsPerConnection = value;
//else if ("minPoolSize".equals(propName))
//this.minPoolSize = value;
//else if ("maxPoolSize".equals(propName))
//this.maxPoolSize = value;
//else if ("idleConnectionTestPeriod".equals(propName))
//this.idleConnectionTestPeriod = value;
//else if ("maxIdleTime".equals(propName))
//this.maxIdleTime = value;
//else if ("checkoutTimeout".equals(propName))
//this.checkoutTimeout = value;
//else if ("acquireIncrement".equals(propName))
//this.acquireIncrement = value;
//else if ("acquireRetryAttempts".equals(propName))
//this.acquireRetryAttempts = value;
//else if ("acquireRetryDelay".equals(propName))
//this.acquireRetryDelay = value;
//// System.err.println( propName + " -> " + propVal );
//}
//else if (propCl == String.class)
//{
//propVal = readMethod.invoke( cpds, C3P0ImplUtils.NOARGS );
//String value = (String) propVal;
//if ("connectionTesterClassName".equals(propName))
//this.connectionTester =
//(ConnectionTester) Class.forName( value ).newInstance();
//else if ("preferredTestQuery".equals(propName))
//this.preferredTestQuery = value;
//else if ("automaticTestTable".equals(propName))
//this.automaticTestTable = value;
//// System.err.println( propName + " -> " + propVal );
//}
//else if (propCl == boolean.class)
//{
//propVal = readMethod.invoke( cpds, C3P0ImplUtils.NOARGS );
//boolean value = ((Boolean) propVal).booleanValue();
//if ("testConnectionOnCheckout".equals(propName))
//this.testConnectionOnCheckout = value;
//else if ("testConnectionOnCheckin".equals(propName))
//this.testConnectionOnCheckin = value;
//else if ("autoCommitOnClose".equals(propName))
//this.autoCommitOnClose = value;
//else if ("forceIgnoreUnresolvedTransactions".equals(propName))
//this.forceIgnoreUnresolvedTransactions = value;
//else if ("breakAfterAcquireFailure".equals(propName))
//this.breakAfterAcquireFailure = value;
//// System.err.println( propName + " -> " + propVal );
//}

//}

