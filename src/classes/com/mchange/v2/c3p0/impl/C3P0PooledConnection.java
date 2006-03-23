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

import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import javax.sql.*;
import com.mchange.v2.log.*;
import com.mchange.v2.sql.*;
import com.mchange.v2.sql.filter.*;
import com.mchange.v2.c3p0.*;
import com.mchange.v2.c3p0.stmt.*;
import com.mchange.v1.util.ClosableResource;
import com.mchange.v2.c3p0.C3P0ProxyConnection;
import com.mchange.v2.c3p0.util.ConnectionEventSupport;
import com.mchange.v2.lang.ObjectUtils;

public final class C3P0PooledConnection implements PooledConnection, ClosableResource
{
    final static MLogger logger = MLog.getLogger( C3P0PooledConnection.class );

    final static ClassLoader CL = C3P0PooledConnection.class.getClassLoader();
    final static Class[]     PROXY_CTOR_ARGS = new Class[]{ InvocationHandler.class };

    final static Constructor CON_PROXY_CTOR;

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

		Class[] argClasses = new Class[0];
		RS_CLOSE_METHOD = ResultSet.class.getMethod("close", argClasses);
		STMT_CLOSE_METHOD = Statement.class.getMethod("close", argClasses);

		CLOSE_ARGS = new Object[0];

		OBJECT_METHODS = Collections.unmodifiableSet( new HashSet( Arrays.asList( Object.class.getMethods() ) ) );
	    }
	catch (Exception e)
	    { 
		//e.printStackTrace();
		logger.log(MLevel.SEVERE, "An Exception occurred in static initializer of" + C3P0PooledConnection.class.getName(), e);
		throw new InternalError("Something is very wrong, or this is a pre 1.3 JVM." +
					"We cannot set up dynamic proxies and/or methods!");
	    }
    }

    //MT: post-constructor constants
    final ConnectionTester connectionTester;
    final boolean autoCommitOnClose;
    final boolean forceIgnoreUnresolvedTransactions;
    final boolean supports_setTypeMap;
    final boolean supports_setHoldability;
    final int dflt_txn_isolation;
    final String dflt_catalog;
    final int dflt_holdability;

    //MT: thread-safe
    final ConnectionEventSupport ces = new ConnectionEventSupport(this);

    //MT: threadsafe, but reassigned (on close)
    volatile Connection physicalConnection;
    volatile Exception  invalidatingException = null;

    //MT: threadsafe, but reassigned, and a read + reassignment must happen
    //    atomically. protected by this' lock.
    ProxyConnection exposedProxy;

    //MT: protected by this' lock
    int connection_status = ConnectionTester.CONNECTION_IS_OKAY;

    /*
     * contains all unclosed Statements not managed by a StatementCache
     * associated with the physical connection
     *
     * MT: protected by its own lock, not reassigned
     */
    final Set uncachedActiveStatements = Collections.synchronizedSet( new HashSet() );

    //MT: Thread-safe, assigned
    volatile GooGooStatementCache scache;
    volatile boolean isolation_lvl_nondefault = false;
    volatile boolean catalog_nondefault       = false; 
    volatile boolean holdability_nondefault   = false; 

    public C3P0PooledConnection(Connection con, 
				ConnectionTester connectionTester,
				boolean autoCommitOnClose, 
				boolean forceIgnoreUnresolvedTransactions) throws SQLException
    { 
	this.physicalConnection = con; 
	this.connectionTester = connectionTester;
	this.autoCommitOnClose = autoCommitOnClose;
	this.forceIgnoreUnresolvedTransactions = forceIgnoreUnresolvedTransactions;
	this.supports_setTypeMap = C3P0ImplUtils.supportsMethod(con, "setTypeMap", new Class[]{ Map.class });
	this.supports_setHoldability = C3P0ImplUtils.supportsMethod(con, "setHoldability", new Class[]{ Integer.class });
	this.dflt_txn_isolation = con.getTransactionIsolation();
	this.dflt_catalog = con.getCatalog();
	this.dflt_holdability = (supports_setHoldability ? con.getHoldability() : ResultSet.CLOSE_CURSORS_AT_COMMIT);
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
    public synchronized Connection getConnection()
	throws SQLException
    { 
	if ( exposedProxy != null)
	    {
		//DEBUG
		//System.err.println("[DOUBLE_GET_TESTER] -- double getting a Connection from " + this );
		//new Exception("[DOUBLE_GET_TESTER] -- Double-Get Stack Trace").printStackTrace();
		//origGet.printStackTrace();

// 		System.err.println("c3p0 -- Uh oh... getConnection() was called on a PooledConnection when " +
// 				   "it had already provided a client with a Connection that has not yet been " +
// 				   "closed. This probably indicates a bug in the connection pool!!!");

		logger.warning("c3p0 -- Uh oh... getConnection() was called on a PooledConnection when " +
			       "it had already provided a client with a Connection that has not yet been " +
			       "closed. This probably indicates a bug in the connection pool!!!");

		return exposedProxy;
	    }
	else
	    { return getCreateNewConnection(); }
    }

    // must be called from sync'ed method to protecte
    // exposedProxy
    private Connection getCreateNewConnection()
	throws SQLException
    {
	try
	    {
		//DEBUG
		//origGet = new Exception("[DOUBLE_GET_TESTER] -- Orig Get");
		
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
		//e.printStackTrace();
		logger.log(MLevel.WARNING, "Failed to acquire connection!", e);
		throw new SQLException("Failed to acquire connection!");
	    }
    }

    public void closeAll() throws SQLException
    {
	if (scache != null)
	    scache.closeAll( physicalConnection );
    }

    public void close() throws SQLException
    { this.close( false ); }

    //TODO: factor out repetitive debugging code
    private synchronized void close(boolean known_invalid) throws SQLException
    {
	//System.err.println("Closing " + this);
	if ( physicalConnection != null )
	    {
		try
		    { 
			StringBuffer debugOnlyLog = null;
			if ( Debug.DEBUG && known_invalid )
			    {
				debugOnlyLog = new StringBuffer();
				debugOnlyLog.append("[ exceptions: ");
			    }

			Exception exc = cleanupUncachedActiveStatements();
			if (Debug.DEBUG && exc != null) 
			    {
				if (known_invalid)
				    debugOnlyLog.append( exc.toString() + ' ' );
				else
				    logger.log(MLevel.WARNING, "An exception occurred while cleaning up uncached active Statements.", exc);
				    //exc.printStackTrace();
			    }

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
				    exposedProxy.silentClose( known_invalid );
			    }
			catch (Exception e)
			    {
				if (Debug.DEBUG)
				    {
					if (known_invalid)
					    debugOnlyLog.append( e.toString() + ' ' );
					else
					    logger.log(MLevel.WARNING, "An exception occurred.", exc);
					    //e.printStackTrace();
				    }
				exc = e;
			    }
			try
			    { this.closeAll(); }
			catch (Exception e)
			    {
				if (Debug.DEBUG)
				    {
					if (known_invalid)
					    debugOnlyLog.append( e.toString() + ' ' );
					else
					    logger.log(MLevel.WARNING, "An exception occurred.", exc);
					    //e.printStackTrace();
				    }
				exc = e;
			    }
			
			try { physicalConnection.close(); }
			catch (Exception e)
			    {
				if (Debug.DEBUG)
				    {
					if (known_invalid)
					    debugOnlyLog.append( e.toString() + ' ' );
					else
					    logger.log(MLevel.WARNING, "An exception occurred.", exc);
					    e.printStackTrace();
				    }
				exc = e;
			    }

			if (exc != null)
			    {
				if (known_invalid)
				    {
					debugOnlyLog.append(" ]");
					if (Debug.DEBUG)
					    {
// 						System.err.print("[DEBUG]" + this + ": while closing a PooledConnection known to be invalid, ");
// 						System.err.println("  some exceptions occurred. This is probably not a problem:");
// 						System.err.println( debugOnlyLog.toString() );


						logger.fine(this + ": while closing a PooledConnection known to be invalid, " +
							    "  some exceptions occurred. This is probably not a problem: " +
							    debugOnlyLog.toString() );
					    }
				    }
				else
				    throw new SQLException("At least one error occurred while attempting " +
							   "to close() the PooledConnection: " + exc);
			    }
			if (Debug.TRACE == Debug.TRACE_MAX)
			    logger.fine("C3P0PooledConnection closed. [" + this + ']');
			    //System.err.println("C3P0PooledConnection closed. [" + this + ']');
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
    { reset( false ); }

    private void reset( boolean known_resolved_txn ) throws SQLException
    {
	ensureOkay();
	C3P0ImplUtils.resetTxnState( physicalConnection, forceIgnoreUnresolvedTransactions, autoCommitOnClose, known_resolved_txn );
	if (isolation_lvl_nondefault)
	    {
		physicalConnection.setTransactionIsolation( dflt_txn_isolation );
		isolation_lvl_nondefault = false; 
	    }
	if (catalog_nondefault)
	    {
		physicalConnection.setCatalog( dflt_catalog );
		catalog_nondefault = false; 
	    }
	if (holdability_nondefault) //we don't test if holdability is supported, 'cuz it can never go nondefault if it's not.
	    {
		physicalConnection.setHoldability( dflt_holdability );
		holdability_nondefault = false; 
	    }

	try
	    { physicalConnection.setReadOnly( false ); }
	catch ( Throwable t )
	    {
		if (logger.isLoggable( MLevel.FINE ))
		    logger.log(MLevel.FINE, "A Throwable occurred while trying to reset the readOnly property of our Connection to false!", t);
	    }

	try
	    { if (supports_setTypeMap) physicalConnection.setTypeMap( Collections.EMPTY_MAP ); }
	catch ( Throwable t )
	    {
		if (logger.isLoggable( MLevel.FINE ))
		    logger.log(MLevel.FINE, "A Throwable occurred while trying to reset the typeMap property of our Connection to Collections.EMPTY_MAP!", t);
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
				    logger.log(MLevel.WARNING, "An exception occurred while cleaning up a ResultSet.", e);
				    //e.printStackTrace();
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
	    throw new SQLException( invalidatingException == null ?
				    "Connection is closed or broken." :
				    "Connection is broken. Invalidating Exception: " + invalidatingException.toString());
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
			logger.log(MLevel.WARNING, "An exception occurred while cleaning up a resource.", t);
			//t.printStackTrace();
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

    Statement createProxyStatement( Statement innerStmt ) throws Exception
    { return this.createProxyStatement( false, innerStmt ); }


    private static class StatementProxyingSetManagedResultSet extends SetManagedResultSet
    {
	private Statement proxyStatement;

	StatementProxyingSetManagedResultSet(Set activeResultSets)
	{ super( activeResultSets ); }

	public void setProxyStatement( Statement proxyStatement )
	{ this.proxyStatement = proxyStatement; }

	public Statement getStatement() throws SQLException
	{ return (proxyStatement == null ? super.getStatement() : proxyStatement); }
    }

    /*
     * TODO: factor all this convolution out into
     *       C3P0Statement
     */
    Statement createProxyStatement( //final Method cachedStmtProducingMethod, 
				    //final Object[] cachedStmtProducingMethodArgs, 
				   final boolean inner_is_cached,
				   final Statement innerStmt) throws Exception
    {
	final Set activeResultSets = Collections.synchronizedSet( new HashSet() );
	final Connection parentConnection = exposedProxy;

	if (Debug.DEBUG && parentConnection == null)
	    {
// 		System.err.print("PROBABLE C3P0 BUG -- ");
// 		System.err.println(this + ": created a proxy Statement when there is no active, exposed proxy Connection???");

		logger.warning("PROBABLE C3P0 BUG -- " +
			       this + ": created a proxy Statement when there is no active, exposed proxy Connection???");

	    }


	//we can use this one wrapper under all circumstances
	//except jdbc3 CallableStatement multiple open ResultSets...
	//avoid object allocation in statement methods where possible.
	final StatementProxyingSetManagedResultSet mainResultSet = new StatementProxyingSetManagedResultSet( activeResultSets );

	class WrapperStatementHelper
	{
	    Statement wrapperStmt;
	    Statement nakedInner;

	    public WrapperStatementHelper(Statement wrapperStmt, Statement nakedInner)
	    {
		this.wrapperStmt = wrapperStmt;
		this.nakedInner = nakedInner;

		if (! inner_is_cached)
		    uncachedActiveStatements.add( wrapperStmt );
	    }

	    private boolean closeAndRemoveActiveResultSets()
	    { return closeAndRemoveResultSets( activeResultSets ); }

	    public ResultSet wrap(ResultSet rs)
	    {
		if (mainResultSet.getInner() == null)
		    {
			mainResultSet.setInner(rs);
			mainResultSet.setProxyStatement( wrapperStmt );
			return mainResultSet;
		    }
		else
		    {
			//for the case of multiple open ResultSets
			StatementProxyingSetManagedResultSet out 
			    = new StatementProxyingSetManagedResultSet( activeResultSets );
			out.setInner( rs );
			out.setProxyStatement( wrapperStmt );
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
			uncachedActiveStatements.remove( wrapperStmt ); 
		    }

		if (!okay)
		    throw new SQLException("Failed to close an orphaned ResultSet properly.");
	    }

	    public Object doRawStatementOperation(Method m, Object target, Object[] args) 
		throws IllegalAccessException, InvocationTargetException, SQLException
	    {
		if (target == C3P0ProxyStatement.RAW_STATEMENT)
		    target = nakedInner;
		for (int i = 0, len = args.length; i < len; ++i)
		    if (args[i] == C3P0ProxyStatement.RAW_STATEMENT)
			args[i] = nakedInner;
		
		Object out = m.invoke(target, args);
		
		if (out instanceof ResultSet)
		    out = wrap( (ResultSet) out );

		return out;
	    }
	}

	if (innerStmt instanceof CallableStatement)
	    {
		class ProxyCallableStatement extends FilterCallableStatement implements C3P0ProxyStatement
		    {
			WrapperStatementHelper wsh;

			ProxyCallableStatement(CallableStatement is)
			{ 
			    super( is ); 
			    this.wsh = new WrapperStatementHelper(this, is);
			}

			public Connection getConnection()
			{ return parentConnection; }

			public ResultSet getResultSet() throws SQLException
			{ return wsh.wrap( super.getResultSet() ); }
			
			public ResultSet getGeneratedKeys() throws SQLException
			{ return wsh.wrap( super.getGeneratedKeys() ); }
			
			public ResultSet executeQuery(String sql) throws SQLException
			{ return wsh.wrap( super.executeQuery(sql) ); }
			
			public ResultSet executeQuery() throws SQLException
			{ return wsh.wrap( super.executeQuery() ); }
			
			public Object rawStatementOperation(Method m, Object target, Object[] args) 
			    throws IllegalAccessException, InvocationTargetException, SQLException
			{ return wsh.doRawStatementOperation( m, target, args); }

			public void close() throws SQLException
			{ wsh.doClose(); }
		    }

		return new ProxyCallableStatement((CallableStatement) innerStmt );
	    }
	else if (innerStmt instanceof PreparedStatement)
	    {
		class ProxyPreparedStatement extends FilterPreparedStatement implements C3P0ProxyStatement
		    {
			WrapperStatementHelper wsh;

			ProxyPreparedStatement(PreparedStatement ps)
			{ 
			    super( ps ); 
			    this.wsh = new WrapperStatementHelper(this, ps);
			}

			public Connection getConnection()
			{ return parentConnection; }

			public ResultSet getResultSet() throws SQLException
			{ return wsh.wrap( super.getResultSet() ); }
			
			public ResultSet getGeneratedKeys() throws SQLException
			{ return wsh.wrap( super.getGeneratedKeys() ); }
			
			public ResultSet executeQuery(String sql) throws SQLException
			{ return wsh.wrap( super.executeQuery(sql) ); }
			
			public ResultSet executeQuery() throws SQLException
			{ return wsh.wrap( super.executeQuery() ); }
			
			public Object rawStatementOperation(Method m, Object target, Object[] args) 
			    throws IllegalAccessException, InvocationTargetException, SQLException
			{ return wsh.doRawStatementOperation( m, target, args); }

			public void close() throws SQLException
			{ wsh.doClose(); }
		    }

		return new ProxyPreparedStatement((PreparedStatement) innerStmt );
	    }
	else
	    {
		class ProxyStatement extends FilterStatement implements C3P0ProxyStatement
		    {
			WrapperStatementHelper wsh;

			ProxyStatement(Statement s)
			{ 
			    super( s ); 
			    this.wsh = new WrapperStatementHelper(this, s);
			}

			public Connection getConnection()
			{ return parentConnection; }

			public ResultSet getResultSet() throws SQLException
			{ return wsh.wrap( super.getResultSet() ); }
			
			public ResultSet getGeneratedKeys() throws SQLException
			{ return wsh.wrap( super.getGeneratedKeys() ); }
			
			public ResultSet executeQuery(String sql) throws SQLException
			{ return wsh.wrap( super.executeQuery(sql) ); }
			
			public Object rawStatementOperation(Method m, Object target, Object[] args) 
			    throws IllegalAccessException, InvocationTargetException, SQLException
			{ return wsh.doRawStatementOperation( m, target, args); }

			public void close() throws SQLException
			{ wsh.doClose(); }
		    }

		return new ProxyStatement( innerStmt );
	    }
    }

    final class ProxyConnectionInvocationHandler implements InvocationHandler
    {
	//MT: ThreadSafe, but reassigned -- protected by this' lock
	Connection activeConnection = physicalConnection;
	DatabaseMetaData metaData   = null;
	boolean connection_error_signaled = false;
		
	/*
	 * contains all unclosed ResultSets derived from this Connection's metadata
	 * associated with the physical connection
	 *
	 * MT: protected by this' lock
	 */
	final Set activeMetaDataResultSets = new HashSet();
		
	//being careful with doRawConnectionOperation
	//we initialize lazily, because this will be very rarely used
	Set doRawResultSets = null;

	boolean txn_known_resolved = true;
	
	public String toString()
	{ return "C3P0ProxyConnection [Invocation Handler: " + super.toString() + ']'; }
		
	private Object doRawConnectionOperation(Method m, Object target, Object[] args) 
	    throws IllegalAccessException, InvocationTargetException, SQLException, Exception
	{
	    if (activeConnection == null)
		throw new SQLException("Connection previously closed. You cannot operate on a closed Connection.");
			    
	    if (target == C3P0ProxyConnection.RAW_CONNECTION)
		target = activeConnection;
	    for (int i = 0, len = args.length; i < len; ++i)
		if (args[i] == C3P0ProxyConnection.RAW_CONNECTION)
		    args[i] = activeConnection;
		    
	    Object out = m.invoke(target, args);
		    
	    // we never cache Statements generated by an operation on the raw Connection
	    if (out instanceof Statement)
		out = createProxyStatement( false, (Statement) out );
	    else if (out instanceof ResultSet)
		{
		    if (doRawResultSets == null)
			doRawResultSets = new HashSet();
		    out = new NullStatementSetManagedResultSet( (ResultSet) out, doRawResultSets );	
		}
	    return out;
	}
		
	public synchronized Object invoke(Object proxy, Method m, Object[] args)
	    throws Throwable
	{
	    if ( OBJECT_METHODS.contains( m ) )
		return m.invoke( this, args );
	    
	    try
		{
		    String mname = m.getName();
		    if (activeConnection != null)
			{	
			    if (mname.equals("rawConnectionOperation"))
				{
				    ensureOkay();
				    txn_known_resolved = false;

				    return doRawConnectionOperation((Method) args[0], args[1], (Object[]) args[2]);    
				}
			    else if (mname.equals("setTransactionIsolation"))
				{
				    ensureOkay();

				    //don't modify txn_known_resolved

				    m.invoke( activeConnection, args );

				    int lvl = ((Integer) args[0]).intValue();
				    isolation_lvl_nondefault = (lvl != dflt_txn_isolation);

				    //System.err.println("updated txn isolation to " + lvl + ", nondefault level? " + isolation_lvl_nondefault);

				    return null;
				}
			    else if (mname.equals("setCatalog"))
				{
				    ensureOkay();

				    //don't modify txn_known_resolved

				    m.invoke( activeConnection, args );

				    String catalog = (String) args[0];
				    catalog_nondefault = ObjectUtils.eqOrBothNull(catalog, dflt_catalog);

				    return null;
				}
			    else if (mname.equals("setHoldability"))
				{
				    ensureOkay();

				    //don't modify txn_known_resolved

				    m.invoke( activeConnection, args ); //will throw an exception if setHoldability() not supported...

				    int holdability = ((Integer) args[0]).intValue();
				    holdability_nondefault = (holdability != dflt_holdability);

				    return null;
				}
			    else if (mname.equals("createStatement"))
				{
				    ensureOkay();
				    txn_known_resolved = false;
	
				    Object stmt = m.invoke( activeConnection, args );
				    return createProxyStatement( (Statement) stmt );
				}
			    else if (mname.equals("prepareStatement"))
				{
				    ensureOkay();
				    txn_known_resolved = false;
	
				    Object pstmt;
				    if (scache == null)
					{
					    pstmt = m.invoke( activeConnection, args );
					    return createProxyStatement( (Statement) pstmt );
					}
				    else
					{
					    pstmt = scache.checkoutStatement( physicalConnection,
									      m, 
									      args );
					    return createProxyStatement( true,
									 (Statement) pstmt );
					}
				}
			    else if (mname.equals("prepareCall"))
				{
				    ensureOkay();
				    txn_known_resolved = false;
	
				    Object cstmt;
				    if (scache == null)
					{
					    cstmt = m.invoke( activeConnection, args );
					    return createProxyStatement( (Statement) cstmt ); 
					}
				    else
					{
					    cstmt = scache.checkoutStatement( physicalConnection, m, args );
					    return createProxyStatement( true,
									 (Statement) cstmt );
					}
				}
			    else if (mname.equals("getMetaData"))
				{
				    ensureOkay();
				    txn_known_resolved = false; //views of tables etc. might be txn dependent
	
				    DatabaseMetaData innerMd = activeConnection.getMetaData();
				    if (metaData == null)
					{
					    // exposedProxy is protected by C3P0PooledConnection.this' lock
					    synchronized (C3P0PooledConnection.this)
						{ metaData = new SetManagedDatabaseMetaData(innerMd, activeMetaDataResultSets, exposedProxy); }
					}
				    return metaData;
				}
			    else if (mname.equals("silentClose"))
				{
				    //the PooledConnection doesn't have to be okay
	
				    doSilentClose( proxy, ((Boolean) args[0]).booleanValue(), this.txn_known_resolved );
				    return null;
				}
			    else if ( mname.equals("close") )
				{
				    //the PooledConnection doesn't have to be okay
	
				    Exception e = doSilentClose( proxy, false, this.txn_known_resolved );
				    if (! connection_error_signaled)
					ces.fireConnectionClosed();
				    //System.err.println("close() called on a ProxyConnection.");
				    if (e != null)
					{
					    // 					    System.err.print("user close exception -- ");
					    // 					    e.printStackTrace();
					    throw e;
					}
				    else
					return null;
				}
// 			    else if ( mname.equals("finalize") ) //REMOVE THIS CASE -- TMP DEBUG
// 				{
// 				    System.err.println("Connection apparently finalized!");
// 				    return m.invoke( activeConnection, args );
// 				}
			    else
				{
				    ensureOkay();
					    

				    // we've disabled setting txn_known_resolved to true, ever, because
				    // we failed to deal with the case that clients would work with previously
				    // acquired Statements and ResultSets after a commit(), rollback(), or setAutoCommit().
				    // the new non-reflective proxies have been modified to deal with this case.
				    // here, with soon-to-be-deprecated in "traditional reflective proxies mode"
				    // we are reverting to the conservative, always-presume-you-have-to-rollback
				    // policy.

				    //txn_known_resolved = ( mname.equals("commit") || mname.equals( "rollback" ) || mname.equals( "setAutoCommit" ) );
				    txn_known_resolved = false; 

				    return m.invoke( activeConnection, args );
				}
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
		    Throwable convertMe = e.getTargetException();
		    SQLException sqle = handleMaybeFatalToPooledConnection( convertMe, proxy, false );
		    sqle.fillInStackTrace();
		    throw sqle;
		}
	}
	
	private Exception doSilentClose(Object proxyConnection, boolean pooled_connection_is_dead)
	{ return doSilentClose( proxyConnection, pooled_connection_is_dead, false ); }

	private Exception doSilentClose(Object proxyConnection, boolean pooled_connection_is_dead, boolean known_resolved_txn)
	{
	    if ( activeConnection != null )
		{
		    synchronized ( C3P0PooledConnection.this ) //uh oh... this is a nested lock acq... is there a deadlock hazard here?
			{
			    if ( C3P0PooledConnection.this.exposedProxy == proxyConnection )
				{
				    C3P0PooledConnection.this.exposedProxy = null;
				    //System.err.println("Reset exposed proxy.");
					    
				    //DEBUG
				    //origGet = null;
				}
			    else //else case -- DEBUG only
				logger.warning("(c3p0 issue) doSilentClose( ... ) called on a proxyConnection " +
					       "other than the current exposed proxy for its PooledConnection. [exposedProxy: " +
					       exposedProxy + ", proxyConnection: " + proxyConnection);
// 				System.err.println("[DEBUG] WARNING: doSilentClose( ... ) called on a proxyConnection " +
// 						   "other than the current exposed proxy for its PooledConnection. [exposedProxy: " +
// 						   exposedProxy + ", proxyConnection: " + proxyConnection);
			}
			    
		    Exception out = null;
			    
		    Exception exc1 = null, exc2 = null, exc3 = null, exc4 = null;
		    try 
			{ 
			    if (! pooled_connection_is_dead)
				C3P0PooledConnection.this.reset(known_resolved_txn); 
			}
		    catch (Exception e)
			{ 
			    exc1 = e;
			    // 		    if (Debug.DEBUG)
			    // 			{
			    // 			    System.err.print("exc1 -- ");
			    // 			    exc1.printStackTrace();
			    // 			}
			}
			    
		    exc2 = cleanupUncachedActiveStatements();
		    // 	    if (Debug.DEBUG && exc2 != null)
		    // 		{
		    // 		    System.err.print("exc2 -- ");
		    // 		    exc2.printStackTrace();
		    // 		}
		    String errSource;
		    if (doRawResultSets != null)
			{
			    activeMetaDataResultSets.addAll( doRawResultSets );
			    errSource = "DataBaseMetaData or raw Connection operation";
			}
		    else
			errSource = "DataBaseMetaData";

		    if (!closeAndRemoveResultSets( activeMetaDataResultSets ))
			exc3 = new SQLException("Failed to close some " + errSource + " Result Sets.");
		    // 	    if (Debug.DEBUG && exc3 != null)
		    // 		{
		    // 		    System.err.print("exc3 -- ");
		    // 		    exc3.printStackTrace();
		    // 		}
		    if (scache != null)
			{
			    try
				{ scache.checkinAll( physicalConnection ); }
			    catch ( Exception e )
				{ exc4 = e; }
			    // 		    if (Debug.DEBUG && exc4 != null)
			    // 			{
			    // 			    System.err.print("exc4 -- ");
			    // 			    exc4.printStackTrace();
			    // 			}
			}
			    
		    if (exc1 != null)
			{
			    handleMaybeFatalToPooledConnection( exc1, proxyConnection, true );
			    out = exc1;
			}
		    else if (exc2 != null)
			{
			    handleMaybeFatalToPooledConnection( exc2, proxyConnection, true );
			    out = exc2;
			}
		    else if (exc3 != null)
			{
			    handleMaybeFatalToPooledConnection( exc3, proxyConnection, true );
			    out = exc3;
			}
		    else if (exc4 != null)
			{
			    handleMaybeFatalToPooledConnection( exc4, proxyConnection, true );
			    out = exc4;
			}
			    
		    // 	    if (out != null)
		    // 		{
		    // 		    System.err.print("out -- ");
		    // 		    out.printStackTrace();
		    // 		}
	
		    activeConnection = null;
		    return out;
		}
	    else
		return null;
	}
	
	private SQLException handleMaybeFatalToPooledConnection( Throwable t, Object proxyConnection, boolean already_closed )
	{
	    //System.err.println("handleMaybeFatalToPooledConnection()");
	
	    SQLException sqle = SqlUtils.toSQLException( t );
	    int status = connectionTester.statusOnException( physicalConnection, sqle );
	    updateConnectionStatus( status ); 
	    if (status != ConnectionTester.CONNECTION_IS_OKAY)
		{
		    if (Debug.DEBUG)
			{
// 			    System.err.print(C3P0PooledConnection.this + " will no longer be pooled because it has been " +
// 					     "marked invalid by the following Exception: ");
// 			    t.printStackTrace();

			    logger.log(MLevel.INFO, 
				       C3P0PooledConnection.this + " will no longer be pooled because it has been marked invalid by an Exception.",
				       t );
			}
			    
		    invalidatingException = sqle;

		    /*
		      ------
		      A users have complained that SQLExceptions ought not close their Connections underneath
		      them under any circumstance. Signalling the Connection error after updating the Connection
		      status should be sufficient from the pool's perspective, because the PooledConnection
		      will be marked broken by the pool and will be destroyed on checkin. I think actually
		      close()ing the Connection when it appears to be broken rather than waiting for users
		      to close() it themselves is overly aggressive, so I'm commenting the old behavior out.
		      The only potential downside to this approach is that users who do not close() in a finally
		      clause properly might see their close()es skipped by exceptions that previously would
		      have led to automatic close(). But relying on the automatic close() was never reliable
		      (since it only ever happened when c3p0 determined a Connection to be absolutely broken),
		      and is generally speaking a client error that c3p0 ought not be responsible for dealing
		      with. I think it's right to leave this out. -- swaldman 2004-12-09
		      ------
		      
		      if (! already_closed )
		          doSilentClose( proxyConnection, true );
		    */

		    if (! connection_error_signaled)
			{
			    ces.fireConnectionErrorOccurred( sqle );
			    connection_error_signaled = true;
			}
		}
	    return sqle;
	}
	

    }

    interface ProxyConnection extends C3P0ProxyConnection
    { void silentClose( boolean known_invalid ) throws SQLException; }

    public synchronized int getConnectionStatus()
    { return this.connection_status; }

    private synchronized void updateConnectionStatus(int status)
    {
	switch ( this.connection_status )
	    {
	    case ConnectionTester.DATABASE_IS_INVALID:
		//can't get worse than this, do nothing.
		break;
	    case ConnectionTester.CONNECTION_IS_INVALID:
		if (status == ConnectionTester.DATABASE_IS_INVALID)
		    doBadUpdate(status);
		break;
	    case ConnectionTester.CONNECTION_IS_OKAY:
		if (status != ConnectionTester.CONNECTION_IS_OKAY)
		    doBadUpdate(status);
		break;
	    default:
		throw new InternalError(this + " -- Illegal Connection Status: " + this.connection_status);
	    }
    }

    //must be called from sync'ed method
    private void doBadUpdate(int new_status)
    {
	this.connection_status = new_status;
	try { this.close( true ); }
	catch (SQLException e)
	    {
// 		System.err.print("Broken Connection Close Error: ");
// 		e.printStackTrace(); 

		logger.log(MLevel.WARNING, "Broken Connection Close Error. ", e);
	    }
    }
}







