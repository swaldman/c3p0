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

import java.util.*;
import java.sql.*;
import javax.sql.*;
import com.mchange.v2.c3p0.*;
import com.mchange.v2.c3p0.stmt.*;
import com.mchange.v2.c3p0.util.*;
import com.mchange.v2.log.*;

import java.lang.reflect.Method;
import com.mchange.v2.lang.ObjectUtils;
import com.mchange.v2.sql.SqlUtils;

public final class NewPooledConnection implements PooledConnection
{
    private final static MLogger logger = MLog.getLogger( NewPooledConnection.class );

    //MT: thread-safe post-constructor constants
    final Connection             physicalConnection;
    final ConnectionTester       connectionTester;
    final boolean                autoCommitOnClose;
    final boolean                forceIgnoreUnresolvedTransactions;
    final boolean                supports_setHoldability;
    final boolean                supports_setReadOnly;
    final boolean                supports_setTypeMap;
    final int                    dflt_txn_isolation;
    final String                 dflt_catalog;
    final int                    dflt_holdability;
    final boolean                dflt_readOnly;
    final Map                    dflt_typeMap;
    final ConnectionEventSupport ces;

    //MT:  protected by this' lock
    GooGooStatementCache scache                    = null;
    Throwable            invalidatingException     = null;
    int                  connection_status         = ConnectionTester.CONNECTION_IS_OKAY;
    Set                  uncachedActiveStatements  = new HashSet(); //cached statements are managed by the cache
    Map                  resultSetsForStatements   = new HashMap(); //for both cached and uncached statements
    Set                  metaDataResultSets        = new HashSet();
    Set                  rawConnectionResultSets   = null;          //very rarely used, so we lazy initialize...
    boolean              connection_error_signaled = false;

    //MT: thread-safe, volatile
    volatile NewProxyConnection exposedProxy = null;
    volatile boolean isolation_lvl_nondefault = false; 
    volatile boolean catalog_nondefault       = false; 
    volatile boolean holdability_nondefault   = false; 
    volatile boolean readOnly_nondefault      = false; 
    volatile boolean typeMap_nondefault       = false; 

    // public API
    public NewPooledConnection(Connection con, 
			       ConnectionTester connectionTester,
			       boolean autoCommitOnClose, 
			       boolean forceIgnoreUnresolvedTransactions) throws SQLException
    { 
	this.physicalConnection                = con; 
	this.connectionTester                  = connectionTester;
	this.autoCommitOnClose                 = autoCommitOnClose;
	this.forceIgnoreUnresolvedTransactions = forceIgnoreUnresolvedTransactions;
	this.supports_setHoldability           = C3P0ImplUtils.supportsMethod(con, "setHoldability", new Class[]{ Integer.class });
	this.supports_setReadOnly              = C3P0ImplUtils.supportsMethod(con, "setReadOnly", new Class[]{ Boolean.class });
	this.supports_setTypeMap               = C3P0ImplUtils.supportsMethod(con, "setTypeMap", new Class[]{ Map.class });
	this.dflt_txn_isolation                = con.getTransactionIsolation();
	this.dflt_catalog                      = con.getCatalog();
	this.dflt_holdability                  = (supports_setHoldability ? con.getHoldability() : ResultSet.CLOSE_CURSORS_AT_COMMIT);
	this.dflt_readOnly                     = (supports_setReadOnly ? carefulCheckReadOnly(con) : false);
	this.dflt_typeMap                      = (supports_setTypeMap && (carefulCheckTypeMap(con) == null) ? null : Collections.EMPTY_MAP);
	this.ces                               = new ConnectionEventSupport(this);
    }

    private static boolean carefulCheckReadOnly(Connection con)
    {
	try { return con.isReadOnly(); }
	catch (Exception e)
	    {
		if (false)
		    {
			if (logger.isLoggable(MLevel.FINER))
			    logger.log(MLevel.FINER, con + " threw an Exception when we tried to check its default " +
				       "read only state. This is not usually a problem! It just means the Connection " +
				       "doesn't support the readOnly property, and c3p0 works around this.", e);
		    }
		return false;
	    }
    }

    private static Map carefulCheckTypeMap(Connection con)
    {
	try { return con.getTypeMap(); }
	catch (Exception e)
	    {
		if (false)
		    {
			if (logger.isLoggable(MLevel.FINER))
			    logger.log(MLevel.FINER, con + " threw an Exception when we tried to check its default " +
				       "type map. This is not usually a problem! It just means the Connection " +
				       "doesn't support the typeMap property, and c3p0 works around this.", e);
		    }
		return null;
	    }
    }

    public synchronized Connection getConnection() throws SQLException
    {
	try
	    {
		//throw new SQLException("NOT IMPLEMENTED");
 		if ( exposedProxy == null )
 		    {
 			exposedProxy = new NewProxyConnection( physicalConnection, this );
 		    }
 		else
 		    {
//    			System.err.println("c3p0 -- Uh oh... getConnection() was called on a PooledConnection when " +
//  					   "it had already provided a client with a Connection that has not yet been " +
//  					   "closed. This probably indicates a bug in the connection pool!!!");

			if ( logger.isLoggable( MLevel.WARNING ) )
			    logger.warning("c3p0 -- Uh oh... getConnection() was called on a PooledConnection when " +
					   "it had already provided a client with a Connection that has not yet been " +
					   "closed. This probably indicates a bug in the connection pool!!!");

 		    }
 		return exposedProxy;
	    }
	catch ( Exception e )
	    {
		SQLException sqle = handleThrowable( e );
		throw sqle;
	    }
    }

    public synchronized int getConnectionStatus()
    { return connection_status; }

    public synchronized void closeAll() throws SQLException
    { 
	try
	    {
		closeAllCachedStatements(); 
	    }
	catch ( Exception e )
	    {
		SQLException sqle = handleThrowable( e );
		throw sqle;
	    }
    }

    public synchronized void close() throws SQLException
    { close( null ); }

    public void addConnectionEventListener(ConnectionEventListener cel)
    { ces.addConnectionEventListener( cel );  }

    public void removeConnectionEventListener(ConnectionEventListener cel)
    { ces.removeConnectionEventListener( cel );  }

    // api for C3P0PooledConnectionPool
    public synchronized void initStatementCache( GooGooStatementCache scache )
    { this.scache = scache; }

    public synchronized GooGooStatementCache getStatementCache()
    { return scache; }

    //api for NewProxyConnections
    void markNewTxnIsolation( int lvl ) //intentionally unsync'd -- isolation_lvl_nondefault is marked volatile
    { 
	this.isolation_lvl_nondefault = (lvl != dflt_txn_isolation); 
	//System.err.println("isolation_lvl_nondefault: " + isolation_lvl_nondefault);
    }

    void markNewCatalog( String catalog ) //intentionally unsync'd -- catalog_nondefault is marked volatile
    { 
	this.catalog_nondefault = ObjectUtils.eqOrBothNull(catalog, dflt_catalog); 
    }

    void markNewHoldability( int holdability ) //intentionally unsync'd -- holdability_nondefault is marked volatile
    { 
	this.holdability_nondefault = (holdability != dflt_holdability); 
    }

    void markNewReadOnly( boolean readOnly ) //intentionally unsync'd -- readOnly_nondefault is marked volatile
    { 
	this.readOnly_nondefault = (readOnly != dflt_readOnly); 
    }

    void markNewTypeMap( Map typeMap ) //intentionally unsync'd -- typeMap_nondefault is marked volatile
    { 
	this.typeMap_nondefault = (typeMap != dflt_typeMap);
    }

    synchronized Object checkoutStatement( Method stmtProducingMethod, Object[] args ) throws SQLException
    { return scache.checkoutStatement( physicalConnection, stmtProducingMethod, args ); }

    synchronized void checkinStatement( Statement stmt ) throws SQLException
    { 
	cleanupStatementResultSets( stmt );
	scache.checkinStatement( stmt );
    }

    synchronized void markActiveUncachedStatement( Statement stmt )
    { uncachedActiveStatements.add( stmt );  }

    synchronized void markInactiveUncachedStatement( Statement stmt )
    {
	cleanupStatementResultSets( stmt );
	uncachedActiveStatements.remove( stmt );  
    }

    synchronized void markActiveResultSetForStatement( Statement stmt, ResultSet rs )
    {
	Set rss = resultSets( stmt, true );
	rss.add( rs );
    }

    synchronized void markInactiveResultSetForStatement( Statement stmt, ResultSet rs )
    { 
	Set rss = resultSets( stmt, false );
	if (rss == null)
	    {
		if (logger.isLoggable( MLevel.FINE ))
		    logger.fine( "ResultSet " + rs + " was apparently closed after the Statement that created it had already been closed." );
	    }
	else if ( ! rss.remove( rs ) )
	    throw new InternalError("Marking a ResultSet inactive that we did not know was opened!");
    }

    synchronized void markActiveRawConnectionResultSet( ResultSet rs )
    {
	if (rawConnectionResultSets == null)
	    rawConnectionResultSets = new HashSet();
	rawConnectionResultSets.add( rs );
    }

    synchronized void markInactiveRawConnectionResultSet( ResultSet rs )
    { 
	if ( ! rawConnectionResultSets.remove( rs ) )
	    throw new InternalError("Marking a raw Connection ResultSet inactive that we did not know was opened!");
    }

    synchronized void markActiveMetaDataResultSet( ResultSet rs )
    { metaDataResultSets.add( rs ); }

    synchronized void markInactiveMetaDataResultSet( ResultSet rs )
    { metaDataResultSets.remove( rs ); }

    synchronized void markClosedProxyConnection( NewProxyConnection npc, boolean txn_known_resolved ) 
    {
	try
	    {
		if (npc != exposedProxy)
		    throw new InternalError("C3P0 Error: An exposed proxy asked a PooledConnection that was not its parents to clean up its resources!");
		
		List closeExceptions = new LinkedList();
		cleanupResultSets( closeExceptions );
		cleanupUncachedStatements( closeExceptions );
		checkinAllCachedStatements( closeExceptions );
		if ( closeExceptions.size() > 0 )
		    {
// 			System.err.println("[c3p0] The following Exceptions occurred while trying to clean up a Connection's stranded resources:");
			if ( logger.isLoggable( MLevel.INFO ) )
			    logger.info("[c3p0] The following Exceptions occurred while trying to clean up a Connection's stranded resources:");
			for ( Iterator ii = closeExceptions.iterator(); ii.hasNext(); )
			    {
				Throwable t = (Throwable) ii.next();
// 				System.err.print("[c3p0 -- conection resource close Exception]: ");
// 				t.printStackTrace();
				if ( logger.isLoggable( MLevel.INFO ) )
				    logger.log( MLevel.INFO, "[c3p0 -- conection resource close Exception]", t );
			    }
		    }
		reset( txn_known_resolved );
	    }
	catch (SQLException e) //Connection failed to reset!
	    {
		//e.printStackTrace();
		if (Debug.DEBUG && logger.isLoggable( MLevel.FINE ))
		    logger.log(MLevel.FINE, "An exception occurred while reseting a closed Connection. Invalidating Connection.", e);

		updateConnectionStatus( ConnectionTester.CONNECTION_IS_INVALID );
		fireConnectionErrorOccurred( e );
	    }
	finally
	    {
		fireConnectionClosed();
	    }
    }

    private void reset( boolean txn_known_resolved ) throws SQLException
    {
	C3P0ImplUtils.resetTxnState( physicalConnection, forceIgnoreUnresolvedTransactions, autoCommitOnClose, txn_known_resolved );
	if (isolation_lvl_nondefault)
	    {
		physicalConnection.setTransactionIsolation( dflt_txn_isolation );
		isolation_lvl_nondefault = false; 
		//System.err.println("reset txn isolation: " + dflt_txn_isolation);
	    }
	if (catalog_nondefault)
	    {
		physicalConnection.setCatalog( dflt_catalog );
		catalog_nondefault = false; 
	    }
	if (holdability_nondefault) //this cannot go to true if holdability is not supported, so we don't have to check.
	    {
		physicalConnection.setHoldability( dflt_holdability );
		holdability_nondefault = false; 
	    }
	if (readOnly_nondefault)
	    {
		physicalConnection.setReadOnly( dflt_readOnly );
		readOnly_nondefault = false; 
	    }
	if (typeMap_nondefault)
	    {
		physicalConnection.setTypeMap( dflt_typeMap );
		typeMap_nondefault = false;
	    }
    }

    synchronized boolean isStatementCaching()
    { return scache != null; }

    synchronized SQLException handleThrowable( Throwable t )
    {
	if (Debug.DEBUG && Debug.TRACE == Debug.TRACE_MAX && logger.isLoggable( MLevel.FINER ))
	    logger.log( MLevel.FINER, this + " handling a throwable.", t );

	SQLException sqle = SqlUtils.toSQLException( t );
	//logger.warning("handle throwable ct: " + connectionTester);
	int status = connectionTester.statusOnException( physicalConnection, sqle );
	updateConnectionStatus( status ); 
	if (status != ConnectionTester.CONNECTION_IS_OKAY)
	    {
		if (Debug.DEBUG)
		    {
// 			System.err.print(this + " invalidated by Exception: ");
// 			t.printStackTrace();
			if ( logger.isLoggable( MLevel.FINE ) )
			    logger.log(MLevel.FINE, this + " invalidated by Exception.", t);
		    }

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

		try { close( t ); }
		catch (SQLException e)
		    {
			e.printStackTrace();
			throw new InternalError("C3P0 Error: NewPooledConnection's private close() method should " +
						"suppress any Exceptions if a throwable cause is provided.");
		    }
		*/

		if (! connection_error_signaled)
		    {
			ces.fireConnectionErrorOccurred( sqle );
			connection_error_signaled = true;
		    }
		else
		    {
// 			System.err.println("[c3p0] Warning: PooledConnection that has already signalled a Connection error is still in use!");
// 			System.err.println("[c3p0] Another error has occurred [ " + t + " ] which will not be reported to listeners!");
			if ( logger.isLoggable( MLevel.WARNING ) )
			    {
				logger.log(MLevel.WARNING, "[c3p0] A PooledConnection that has already signalled a Connection error is still in use!");
				logger.log(MLevel.WARNING, "[c3p0] Another error has occurred [ " + t + " ] which will not be reported to listeners!", t);
			    }
		    }
		}
	return sqle;
    }


    // private methods

    private void fireConnectionClosed()
    {
	exposedProxy = null;
	ces.fireConnectionClosed(); 
    }

    private void fireConnectionErrorOccurred(SQLException error)
    { ces.fireConnectionErrorOccurred( error ); }

    // methods below must be called from sync'ed methods

    /*
     *  If a throwable cause is provided, the PooledConnection is known to be broken (cause is an invalidating exception)
     *  and this method will not throw any exceptions, even if some resource closes fail.
     *
     *  If cause is null, then we think the PooledConnection is healthy, and we will report (throw) an exception
     *  if resources unexpectedlay fail to close.
     */
    private void close( Throwable cause ) throws SQLException
    {
	if ( this.invalidatingException == null )
	    {
		List closeExceptions = new LinkedList();
		
		// cleanup ResultSets
		cleanupResultSets( closeExceptions );
		
		// cleanup uncached Statements
		cleanupUncachedStatements( closeExceptions );
		
		// cleanup cached Statements
		try
		    { closeAllCachedStatements(); }
		catch ( SQLException e )
		    { closeExceptions.add(e); }
		
		// cleanup physicalConnection
		try
		    { physicalConnection.close(); }
		catch ( SQLException e )
		    {
			if (logger.isLoggable( MLevel.FINER ))
			    logger.log( MLevel.FINER, "Failed to close physical Connection: " + physicalConnection, e );

			closeExceptions.add(e); 
		    }
		
		// update our state to bad status and closed, and log any exceptions
		if ( connection_status == ConnectionTester.CONNECTION_IS_OKAY )
		    connection_status = ConnectionTester.CONNECTION_IS_INVALID;
		if ( cause == null )
		    {
			this.invalidatingException = new SQLException(this + " explicitly closed!");
			logCloseExceptions( null, closeExceptions );

			if (closeExceptions.size() > 0)
			    throw new SQLException("Some resources failed to close properly while closing " + this);
		    }
		else
		    {
			this.invalidatingException = cause;
			if (Debug.TRACE >= Debug.TRACE_MED)
			    logCloseExceptions( cause, closeExceptions );
			else
			    logCloseExceptions( cause, null );
		    }
	    }
    }

    private void cleanupResultSets( List closeExceptions )
    {
	cleanupAllStatementResultSets( closeExceptions );
	cleanupUnclosedResultSetsSet( metaDataResultSets, closeExceptions );
	if ( rawConnectionResultSets != null )
	    cleanupUnclosedResultSetsSet( rawConnectionResultSets, closeExceptions );
    }

    private void cleanupUnclosedResultSetsSet( Set rsSet, List closeExceptions )
    {
	for ( Iterator ii = rsSet.iterator(); ii.hasNext(); )
	    {
		ResultSet rs = (ResultSet) ii.next();
		try
		    { rs.close(); }
		catch ( SQLException e )
		    { closeExceptions.add(e); }

		ii.remove();
	    }
    }

    private void cleanupStatementResultSets( Statement stmt )
    {
	Set rss = resultSets( stmt, false );
	if ( rss != null )
	    {
		for ( Iterator ii = rss.iterator(); ii.hasNext(); )
		    {
			try
			    { ((ResultSet) ii.next()).close(); }
			catch ( Exception e )
			    {
// 				System.err.print("ResultSet close() failed: ");
// 				e.printStackTrace();
				if ( logger.isLoggable( MLevel.INFO ) )
				    logger.log(MLevel.INFO, "ResultSet close() failed.", e);
			    }
		    }
	    }
	resultSetsForStatements.remove( stmt );
    }

    private void cleanupAllStatementResultSets( List closeExceptions )
    {
	for ( Iterator ii = resultSetsForStatements.keySet().iterator(); ii.hasNext(); )
	    {
		Object stmt = ii.next();
		Set rss = (Set) resultSetsForStatements.get( stmt );
		for (Iterator jj = rss.iterator(); jj.hasNext(); )
		    {
			ResultSet rs = (ResultSet) jj.next();
			try
			    { rs.close(); }
			catch ( SQLException e )
			    { closeExceptions.add(e); }
		    }
	    }
	resultSetsForStatements.clear();
    }

    private void cleanupUncachedStatements( List closeExceptions )
    {
	for ( Iterator ii = uncachedActiveStatements.iterator(); ii.hasNext(); )
	    {
		Statement stmt = (Statement) ii.next();
		try
		    { stmt.close(); }
		catch ( SQLException e )
		    { closeExceptions.add(e); }

		ii.remove();
	    }
    }
    
    private void checkinAllCachedStatements( List closeExceptions )
    {
	try
	    {
		if (scache != null)
		    scache.checkinAll( physicalConnection );
	    }
	catch ( SQLException e )
	    { closeExceptions.add(e); }
    }
    
    private void closeAllCachedStatements() throws SQLException
    {
	if (scache != null)
	    scache.closeAll( physicalConnection );
    }

    private void updateConnectionStatus(int status)
    {
	switch ( this.connection_status )
	    {
	    case ConnectionTester.DATABASE_IS_INVALID:
		//can't get worse than this, do nothing.
		break;
	    case ConnectionTester.CONNECTION_IS_INVALID:
		if (status == ConnectionTester.DATABASE_IS_INVALID)
		    this.connection_status = status;
		break;
	    case ConnectionTester.CONNECTION_IS_OKAY:
		if (status != ConnectionTester.CONNECTION_IS_OKAY)
		    this.connection_status = status;
		break;
	    default:
		throw new InternalError(this + " -- Illegal Connection Status: " + this.connection_status);
	    }
    }

    private Set resultSets( Statement stmt, boolean create )
    { 
	Set out = (Set) resultSetsForStatements.get( stmt ); 
	if ( out == null && create )
	    {
		out = new HashSet();
		resultSetsForStatements.put( stmt, out );
	    }
	return out;
    }

    // static utility functions
    private static void logCloseExceptions( Throwable cause, Collection exceptions )
    {
	if ( logger.isLoggable( MLevel.INFO ) )
	    {
		if (cause != null)
		    {
			// 		System.err.println("[c3p0] A PooledConnection died due to the following error!");
			// 		cause.printStackTrace();
			logger.log(MLevel.INFO, "[c3p0] A PooledConnection died due to the following error!", cause);
		    }
		if ( exceptions != null && exceptions.size() > 0)
		    {
			if ( cause == null )
			    logger.info("[c3p0] Exceptions occurred while trying to close a PooledConnection's resources normally.");
			//System.err.println("[c3p0] The following Exceptions occurred while trying to close a PooledConnection's resources normally.");
			else
			    logger.info("[c3p0] Exceptions occurred while trying to close a Broken PooledConnection.");
			//System.err.println("[c3p0] The following Exceptions occurred while trying to close a broken PooledConnection.");
			for ( Iterator ii = exceptions.iterator(); ii.hasNext(); )
			    {
				Throwable t = (Throwable) ii.next();
				// 			System.err.print("[c3p0 -- close Exception]: ");
				// 			t.printStackTrace();
				logger.log(MLevel.INFO, "[c3p0] NewPooledConnection close Exception.", t);
			    }
		    }
	    }
    }
}
