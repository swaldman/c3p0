/*
 * Distributed as part of c3p0 v.0.8.4-test1
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

import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import javax.sql.*;
import com.mchange.v2.sql.*;
import com.mchange.v2.c3p0.*;
import com.mchange.v2.c3p0.stmt.*;
import com.mchange.v1.util.ClosableResource;
import com.mchange.v2.c3p0.util.ConnectionEventSupport;

public final class C3P0PooledConnection implements PooledConnection, ClosableResource
{
    final static ClassLoader CL = C3P0PooledConnection.class.getClassLoader();
    final static Class[]     PROXY_CTOR_ARGS = new Class[]{ InvocationHandler.class };

    final static Constructor CON_PROXY_CTOR;
    final static Constructor STMT_PROXY_CTOR;
    final static Constructor PSTMT_PROXY_CTOR;
    final static Constructor CSTMT_PROXY_CTOR;
    final static Constructor RS_PROXY_CTOR;

    final static Method RS_CLOSE_METHOD;
    final static Method STMT_CLOSE_METHOD;

    final static Object[] CLOSE_ARGS;

    final static Set OBJECT_METHODS;

    /**
     * @deprecated use or rewrite in terms of ReflectUtils.findProxyConstructor()
     */
    private static Constructor createProxyConstructor(Class intfc) throws NoSuchMethodException
    { 
	Class[] proxyInterfaces = new Class[] { intfc };
	Class proxyCl = Proxy.getProxyClass(CL, proxyInterfaces);
	return proxyCl.getConstructor( PROXY_CTOR_ARGS ); 
    }

    static
    {
	try
	    {
		CON_PROXY_CTOR = createProxyConstructor( ProxyConnection.class );
		STMT_PROXY_CTOR = createProxyConstructor( Statement.class );
		PSTMT_PROXY_CTOR = createProxyConstructor( PreparedStatement.class );
		CSTMT_PROXY_CTOR = createProxyConstructor( CallableStatement.class );
		RS_PROXY_CTOR = createProxyConstructor( ResultSet.class );

		Class[] argClasses = new Class[0];
		RS_CLOSE_METHOD = ResultSet.class.getMethod("close", argClasses);
		STMT_CLOSE_METHOD = Statement.class.getMethod("close", argClasses);

		CLOSE_ARGS = new Object[0];

		OBJECT_METHODS = Collections.unmodifiableSet( new HashSet( Arrays.asList( Object.class.getMethods() ) ) );
	    }
	catch (Exception e)
	    { 
		e.printStackTrace();
		throw new InternalError("Something is very wrong, or this is a pre 1.3 JVM." +
					"We cannot set up dynamic proxies and/or methods!");
	    }
    }

    //MT: post-constructor constants
    final boolean autoCommitOnClose;
    final boolean forceIgnoreUnresolvedTransactions;

    //MT: thread-safe
    final ConnectionEventSupport ces = new ConnectionEventSupport(this);

    //MT: threadsafe, but reassigned (on close)
    volatile Connection physicalConnection;

    //MT: threadsafe, but reassigned, and a read + reassignment must happen
    //    atomically. protected by this' lock.
    ProxyConnection exposedProxy;


    /*
     * contains all unclosed Statements not managed by a StatementCache
     * associated with the physical connection
     *
     * MT: protected by its own lock, not reassigned
     */
    final Set uncachedActiveStatements = Collections.synchronizedSet( new HashSet() );

    //MT: Thread-safe, assigned
    volatile GooGooStatementCache scache;

    public C3P0PooledConnection(Connection con, boolean autoCommitOnClose, boolean forceIgnoreUnresolvedTransactions)
    { 
	this.physicalConnection = con; 
	this.autoCommitOnClose = autoCommitOnClose;
	this.forceIgnoreUnresolvedTransactions = forceIgnoreUnresolvedTransactions;
    }

    Connection getPhysicalConnection()
    { return physicalConnection; }

    boolean isClosed() throws SQLException
    { return (physicalConnection == null); }

    void initStatementCache( GooGooStatementCache scache )
    { this.scache = scache; }

    //DEBUG
    //Exception origGet = null;

    // synchronized to protect exposedProxy
    public synchronized  Connection getConnection() throws SQLException
    {
	if (exposedProxy != null)
	    {
		//DEBUG
		//System.err.println("XXXXXX!!!!! double getting a Connection from " + this );
		//new Exception("Double-Get Stack Trace").printStackTrace();
		//origGet.printStackTrace();

		System.err.println("c3p0 -- Uh oh... getConnection() was called on a PooledConnection when " +
				   "it had already provided a client with a Connection that has not yet been " +
				   "closed. This probably indicates a bug in the connection pool!!!");
		return exposedProxy;
	    }
	else
	    {
		try
		    {
			//DEBUG
			//origGet = new Exception("Orig Get");

			ensureOkay();
			/*
			 * we reset the physical connection when we close an exposed proxy
			 * no need to do it again when we create one
			 */
			//reset();
			return (exposedProxy = createProxyConnection()); 
		    }
		catch (SQLException e)
		    { throw e; }
		catch (Exception e)
		    {
			e.printStackTrace();
			throw new SQLException("Failed to acquire connection!");
		    }
	    }
    }

    public void closeAll() throws SQLException
    {
	if (scache != null)
	    scache.closeAll( physicalConnection );
    }

    public synchronized void close() throws SQLException
    {
	if ( physicalConnection != null )
	    {
		try
		    { 
			Exception exc = cleanupUncachedActiveStatements();
			if (Debug.DEBUG && exc != null) exc.printStackTrace();

			try 
			    { 
				// we've got to use silentClose() rather than close() here,
				// 'cuz if there's still an exposedProxy (say a user forgot to
				// close his Connection) before we close, and we use regular (loud)
				// close, we will try to check this dead or dying PooledConnection
				// back into the pool. We only want to do this when close is called
				// on user proxies, and the underlying PooledConnection might still
				// be good. The PooledConnection itself should only be closed by the
				// pool.
				if (exposedProxy != null)
				    exposedProxy.silentClose();
			    }
			catch (Exception e)
			    {
				if (Debug.DEBUG) e.printStackTrace();
				exc = e;
			    }
			try
			    { this.closeAll(); }
			catch (Exception e)
			    {
				if (Debug.DEBUG) e.printStackTrace();
				exc = e;
			    }
			
			try { physicalConnection.close(); }
			catch (Exception e)
			    {
				if (Debug.DEBUG) e.printStackTrace();
				exc = e;
			    }

			if (exc != null)
			    throw new SQLException("At least one error occurred while attempting " +
						   "to close() the PooledConnection: " + exc);
			if (Debug.TRACE == Debug.TRACE_MAX)
			    System.err.println("C3P0PooledConnection closed. [" + this + ']');
		    }
		finally
		    { physicalConnection = null; }
	    }
    }

    public void addConnectionEventListener(ConnectionEventListener listener)
    { ces.addConnectionEventListener( listener ); }

    public void removeConnectionEventListener(ConnectionEventListener listener)
    { ces.removeConnectionEventListener( listener ); }

    private void reset() throws SQLException
    {
	ensureOkay();

	//System.err.println("autoCommitOnClose: " + autoCommitOnClose);
	//System.err.println("forceIgnoreUnresolvedTransactions: " + forceIgnoreUnresolvedTransactions);

	if ( !forceIgnoreUnresolvedTransactions && !physicalConnection.getAutoCommit() )
	    {
		if ( autoCommitOnClose )
		    physicalConnection.commit();
		else
		    physicalConnection.rollback();

		physicalConnection.setAutoCommit( true );
	    }
    }

    boolean closeAndRemoveResultSets(Set rsSet)
    {
	boolean okay = true;
	synchronized (rsSet)
	    {
		for (Iterator ii = rsSet.iterator(); ii.hasNext(); )
		    {
			ResultSet rs = (ResultSet) ii.next();
			try
			    { rs.close(); }
			catch (SQLException e)
			    {
				if (Debug.DEBUG)
				    e.printStackTrace();
				okay = false;
			    }
			finally 
			    { ii.remove(); }
		    }
	    }
	return okay;
    }

    void ensureOkay() throws SQLException
    {
	if (physicalConnection == null)
	    throw new SQLException("Connection is broken");
    }

    boolean closeAndRemoveResourcesInSet(Set s, Method closeMethod)
    {
	boolean okay = true;
	
	Set temp;
	synchronized (s)
	    { temp = new HashSet( s ); }

	for (Iterator ii = temp.iterator(); ii.hasNext(); )
	    {
		Object rsrc = ii.next();
		try
		    { closeMethod.invoke(rsrc, CLOSE_ARGS); }
		catch (Exception e)
		    {
			Throwable t = e;
			if (t instanceof InvocationTargetException)
			    t = ((InvocationTargetException) e).getTargetException();
			t.printStackTrace();
			okay = false;
		    }
		finally 
		    { s.remove( rsrc ); }
	    }

// We had to abandon the idea of simply iterating over s directly, because	
// our resource close methods sometimes try to remove the resource from
// its parent Set. This is important (when the user closes the resources
// directly), but leads to ConcurrenModificationExceptions while we are
// iterating over the Set to close. So, now we iterate over a copy, but remove
// from the original Set. Since removal is idempotent, it don't matter if
// the close method already provoked a remove. Sucks that we have to copy
// the set though.
//
// Original (direct iteration) version:
//
//  	synchronized (s)
//  	    {
//  		for (Iterator ii = s.iterator(); ii.hasNext(); )
//  		    {
//  			Object rsrc = ii.next();
//  			try
//  			    { closeMethod.invoke(rsrc, CLOSE_ARGS); }
//  			catch (Exception e)
//  			    {
//  				Throwable t = e;
//  				if (t instanceof InvocationTargetException)
//  				    t = ((InvocationTargetException) e).getTargetException();
//  				t.printStackTrace();
//  				okay = false;
//  			    }
//  			finally 
//  			    { ii.remove(); }
//  		    }
//  	    }


	return okay;
    }

    private SQLException cleanupUncachedActiveStatements()
    {
	//System.err.println("IN  uncachedActiveStatements.size(): " + uncachedActiveStatements.size());

	boolean okay = closeAndRemoveResourcesInSet(uncachedActiveStatements, STMT_CLOSE_METHOD);

	//System.err.println("OUT uncachedActiveStatements.size(): " + uncachedActiveStatements.size());

	if (okay)
	    return null;
	else
	    return new SQLException("An exception occurred while trying to " +
				    "clean up orphaned resources.");
    }

    ProxyConnection createProxyConnection() throws Exception
    {
	// we should always have a separate handler for each proxy connection, so
	// that object methods behave as expected... the handler covers
	// all object methods on behalf of the proxy.
	InvocationHandler handler = new ProxyConnectionInvocationHandler();
	return (ProxyConnection) CON_PROXY_CTOR.newInstance( new Object[] {handler} );
    }

    Statement createProxyStatement( Statement innerStmt, 
				    Constructor stmtProxyCtor) throws Exception
    { return this.createProxyStatement( false, innerStmt, stmtProxyCtor ); }


    /*
     * TODO: factor all this convolution out into
     *       C3P0Statement
     */
    Statement createProxyStatement( //final Method cachedStmtProducingMethod, 
				    //final Object[] cachedStmtProducingMethodArgs, 
				    final boolean inner_is_cached,
				    final Statement innerStmt, 
				    final Constructor stmtProxyCtor) throws Exception
    {
	final Set activeResultSets = Collections.synchronizedSet( new HashSet() );

	//we can use this one wrapper under all circumstances
	//except jdbc3 CallableStatement multiple open ResultSets...
	//avoid object allocation in statement methods where possible.

	final SetManagedResultSet mainResultSet = new SetManagedResultSet( activeResultSets );

	class WrapperStatementHelper
	{
	    Statement wrappedStmt;

	    public WrapperStatementHelper(Statement wrappedStmt)
	    {
		this.wrappedStmt = wrappedStmt;
		if (! inner_is_cached)
		    uncachedActiveStatements.add( wrappedStmt );
	    }

	    private boolean closeAndRemoveActiveResultSets()
	    { return closeAndRemoveResultSets( activeResultSets ); }

	    public ResultSet wrap(ResultSet rs)
	    {
		if (mainResultSet.getInner() == null)
		    {
			mainResultSet.setInner(rs);
			return mainResultSet;
		    }
		else
		    {
			SetManagedResultSet out 
			    = new SetManagedResultSet( activeResultSets );
			out.setInner( rs );
			return out;
		    }
	    }

	    public void doClose()
		throws SQLException
	    {
		boolean okay = closeAndRemoveActiveResultSets();

		if (inner_is_cached) //this statement was cached
		    scache.checkinStatement( innerStmt );
		else
		    {
			innerStmt.close();
			uncachedActiveStatements.remove( wrappedStmt ); 
		    }

		if (!okay)
		    throw new SQLException("Failed to close an orphaned ResultSet properly.");
	    }
	}

	if (innerStmt instanceof CallableStatement)
	    {
		return new C3P0CallableStatement((CallableStatement) innerStmt )
		    {
			WrapperStatementHelper wsh = new WrapperStatementHelper(this);

			public ResultSet getResultSet() throws SQLException
			{ return wsh.wrap( super.getResultSet() ); }
			
			public ResultSet executeQuery(String sql) throws SQLException
			{ return wsh.wrap( super.executeQuery(sql) ); }
			
			public ResultSet executeQuery() throws SQLException
			{ return wsh.wrap( super.executeQuery() ); }
			
			public void close() throws SQLException
			{ wsh.doClose(); }
		    };
	    }
	else if (innerStmt instanceof PreparedStatement)
	    {
		return new C3P0PreparedStatement((PreparedStatement) innerStmt )
		    {
			WrapperStatementHelper wsh = new WrapperStatementHelper(this);

			public ResultSet getResultSet() throws SQLException
			{ return wsh.wrap( super.getResultSet() ); }
			
			public ResultSet executeQuery(String sql) throws SQLException
			{ return wsh.wrap( super.executeQuery(sql) ); }
			
			public ResultSet executeQuery() throws SQLException
			{ return wsh.wrap( super.executeQuery() ); }
			
			public void close() throws SQLException
			{ wsh.doClose(); }
		    };
	    }
	else
	    {
		return new C3P0Statement( innerStmt )
		    {
			WrapperStatementHelper wsh = new WrapperStatementHelper(this);

			public ResultSet getResultSet() throws SQLException
			{ return wsh.wrap( super.getResultSet() ); }
			
			public ResultSet executeQuery(String sql) throws SQLException
			{ return wsh.wrap( super.executeQuery(sql) ); }
			
			public void close() throws SQLException
			{ wsh.doClose(); }
		    };
	    }
    }


    class ProxyConnectionInvocationHandler implements InvocationHandler
    {
	//MT: ThreadSafe, but reassigned
	volatile Connection activeConnection = physicalConnection;

	volatile DatabaseMetaData metaData = null;
	
	/*
	 * contains all unclosed ResultSets derived from this Connection's metadata
	 * associated with the physical connection
	 *
	 * MT: protected by its own lock, not reassigned
	 */
	final Set activeMetaDataResultSets = Collections.synchronizedSet( new HashSet() );

	private Exception doSilentClose(Object pc)
	{
	    synchronized ( C3P0PooledConnection.this )
		{
		    if ( C3P0PooledConnection.this.exposedProxy == pc )
			{
			    C3P0PooledConnection.this.exposedProxy = null;
			    //System.err.println("Reset exposed proxy.");

			    //DEBUG
			    //origGet = null;
			}
		}

	    activeConnection = null;
	    Exception out = null;
	    
	    Exception exc1 = null, exc2 = null, exc3 = null, exc4 = null;
	    try { C3P0PooledConnection.this.reset(); }
	    catch (Exception e)
		{ 
		    if (Debug.DEBUG)
			e.printStackTrace();
		    exc1 = e;
		}
	    
	    exc2 = cleanupUncachedActiveStatements();
	    if (Debug.DEBUG && exc2 != null)
		exc2.printStackTrace();
	    if (!closeAndRemoveResultSets( activeMetaDataResultSets ))
		exc3 = new SQLException("Failed to close some DatabaseMetaData Result Sets.");
	    if (Debug.DEBUG && exc3 != null)
		exc3.printStackTrace();
	    if (scache != null)
		{
		    try
			{ scache.checkinAll( physicalConnection ); }
		    catch ( Exception e )
			{ exc4 = e; }
		    if (Debug.DEBUG && exc4 != null)
			exc4.printStackTrace();
		}
	    
	    if (exc1 != null)
		out = exc1;
	    else if (exc2 != null)
		out = exc2;
	    else if (exc3 != null)
		out = exc3;
	    else if (exc4 != null)
		out = exc4;
	    return out;
	}
	
	public Object invoke(Object proxy, Method m, Object[] args)
	    throws Throwable
	{
	    if ( OBJECT_METHODS.contains( m ) )
		return m.invoke( this, args );

	    ensureOkay();
	    
	    try
		{
		    String mname = m.getName();
		    if (activeConnection != null)
			{
			    if (mname.equals("createStatement"))
				{
				    Object stmt = m.invoke( activeConnection, args );
				    return createProxyStatement( (Statement) stmt, STMT_PROXY_CTOR );
				}
			    else if (mname.equals("prepareStatement"))
				{
				    Object pstmt;
				    if (scache == null)
					{
					    pstmt = m.invoke( activeConnection, args );
					    return createProxyStatement( (Statement) pstmt, 
									 PSTMT_PROXY_CTOR );
					}
				    else
					{
					    pstmt = scache.checkoutStatement( physicalConnection,
									      m, 
									      args );
					    return createProxyStatement( true,
									 (Statement) pstmt, 
									 PSTMT_PROXY_CTOR );
					}
				}
			    else if (mname.equals("prepareCall"))
				{
				    Object cstmt;
				    if (scache == null)
					{
					    cstmt = m.invoke( activeConnection, args );
					    return createProxyStatement( (Statement) cstmt, 
									 CSTMT_PROXY_CTOR );
					}
				    else
					{
					    cstmt = scache.checkoutStatement( physicalConnection, m, args );
					    return createProxyStatement( true,
									 (Statement) cstmt, 
									 CSTMT_PROXY_CTOR );
					}
				}
			    else if (mname.equals("getMetaData"))
				{
				    DatabaseMetaData innerMd = activeConnection.getMetaData();
				    if (metaData == null)
					metaData = new SetManagedDatabaseMetaData(innerMd, 
										  activeMetaDataResultSets);
				    return metaData;
				}
			    else if (mname.equals("silentClose"))
				{
				    doSilentClose( proxy );
				    return null;
				}
			    else if ( mname.equals("close") )
				{
				    Exception e = doSilentClose( proxy );
				    ces.fireConnectionClosed();
				    //System.err.println("close() called on a ProxyConnection.");
				    if (e != null)
					throw e;
				    else
					return null;
				}
			    else
				return m.invoke( activeConnection, args );
			}
		    else
			{
			    if (mname.equals("close") || 
				mname.equals("silentClose"))
				return null;
			    else if (mname.equals("isClosed"))
				return new Boolean(true);
			    else
				{
				    throw new SQLException("You can't operate on " +
				    			   "a closed connection!!!");
				}
			}
		}
	    catch (InvocationTargetException e)
		{
		    Throwable throwMe = e.getTargetException();
		    SQLException sqle = SqlUtils.toSQLException( throwMe );
		    ces.fireConnectionErrorOccurred( sqle );
		    throw sqle;
		}
	}
    }

    interface ProxyConnection extends Connection
    {	void silentClose() throws SQLException;	}
}







//      Object findCreateCachedStatement(Method m, Object[] args) throws Exception
//      {
//  	String stmtText = (String) args[0];
//  	boolean is_callable = m.getName().equals("prepareCall");
//  	int result_set_type;
//  	int result_set_concurrency;

//  	if (args.length == 1)
//  	    {
//  		result_set_type = ResultSet.TYPE_FORWARD_ONLY;
//  		result_set_concurrency = ResultSet.CONCUR_READ_ONLY;
//  	    }
//  	else if (args.length == 3)
//  	    {
//  		result_set_type = ((Integer) args[1]).intValue();
//  		result_set_concurrency = ((Integer) args[2]).intValue();
//  	    }
//  	else
//  	    throw new IllegalArgumentException("Unexpected number of args to " + m.getName() +
//  					       " [right now we do not support new JDBC3 variants!]");

//  	StatementCacheKey sck = new StatementCacheKey( stmtCache, 
//  						       stmtText, 
//  						       is_callable,
//  						       result_set_type,
//  						       result_set_concurrency );

//  	synchronized( sck )
//  	    {
//  		Object out = stmtCache.fetchStatement( sck );
//  		if (out == null)
//  		    {
//  			out = m.invoke( physicalConnection, args );
//  			stmtCache.cacheStatement( sck, out );
//  		    }
//  		return out;
//  	    }
//      }

