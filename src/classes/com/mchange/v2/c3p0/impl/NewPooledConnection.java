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


package com.mchange.v2.c3p0.impl;

import java.util.*;
import java.sql.*;
import javax.sql.*;
import com.mchange.v2.c3p0.*;
import com.mchange.v2.c3p0.stmt.*;
import com.mchange.v2.c3p0.util.*;

import java.lang.reflect.Method;
import com.mchange.v2.sql.SqlUtils;

public final class NewPooledConnection implements PooledConnection
{
    //MT: thread-safe post-constructor constants
    final Connection             physicalConnection;
    final ConnectionTester       connectionTester;
    final boolean                autoCommitOnClose;
    final boolean                forceIgnoreUnresolvedTransactions;
    final ConnectionEventSupport ces;

    //MT:  protected by this' lock
    GooGooStatementCache scache                    = null;
    Throwable            invalidatingException     = null;
    int                  connection_status         = ConnectionTester.CONNECTION_IS_OKAY;
    Set                  uncachedActiveStatements  = new HashSet(); //cached statements are managed by the cache
    Map                  resultSetsForStatements   = new HashMap(); //for both cached and uncached statements
    Set                  metaDataResultSets        = new HashSet();
    boolean              connection_error_signaled = false;

    //MT: thread-safe, volatile
    volatile NewProxyConnection exposedProxy = null;

    // public API
    public NewPooledConnection(Connection con, 
			       ConnectionTester connectionTester,
			       boolean autoCommitOnClose, 
			       boolean forceIgnoreUnresolvedTransactions)
    { 
	this.physicalConnection                = con; 
	this.connectionTester                  = connectionTester;
	this.autoCommitOnClose                 = autoCommitOnClose;
	this.forceIgnoreUnresolvedTransactions = forceIgnoreUnresolvedTransactions;
	this.ces                               = new ConnectionEventSupport(this);
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
 			System.err.println("c3p0 -- Uh oh... getConnection() was called on a PooledConnection when " +
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
	if ( ! rss.remove( rs ) )
	    throw new InternalError("Marking a ResultSet inactive that we did not know was opened!");
    }

    synchronized void markActiveMetaDataResultSet( ResultSet rs )
    { metaDataResultSets.add( rs ); }

    synchronized void markInactiveMetaDataResultSet( ResultSet rs )
    { metaDataResultSets.remove( rs ); }

    synchronized void markClosedProxyConnection( NewProxyConnection npc ) throws SQLException
    {
	if (npc != exposedProxy)
	    throw new InternalError("C3P0 Error: An exposed proxy asked a PooledConnection that was not its parents to clean up its resources!");

	List closeExceptions = new LinkedList();
	cleanupResultSets( closeExceptions );
	cleanupUncachedStatements( closeExceptions );
	if ( closeExceptions.size() > 0 )
	    {
		System.err.println("[c3p0] The following Exceptions occurred while trying to close a Connection's stranded resources:");
		for ( Iterator ii = closeExceptions.iterator(); ii.hasNext(); )
		    {
			Throwable t = (Throwable) ii.next();
			System.err.print("[c3p0 -- conection resource close Exception]: ");
			t.printStackTrace();
		    }
	    }
    }

    synchronized boolean isStatementCaching()
    { return scache != null; }

    synchronized SQLException handleThrowable( Throwable t )
    {
	SQLException sqle = SqlUtils.toSQLException( t );
	int status = connectionTester.statusOnException( physicalConnection, sqle );
	updateConnectionStatus( status ); 
	if (status != ConnectionTester.CONNECTION_IS_OKAY)
	    {
		if (Debug.DEBUG)
		    {
			System.err.print(this + " invalidated by Exception: ");
			t.printStackTrace();
		    }
		    
		try { close( t ); }
		catch (SQLException e)
		    {
			e.printStackTrace();
			throw new InternalError("C3P0 Error: NewPooledConnection's private close() method should " +
						"suppress any Exceptions if a throwable cause is provided.");
		    }

		if (! connection_error_signaled)
		    {
			ces.fireConnectionErrorOccurred( sqle );
			connection_error_signaled = true;
		    }
		else
		    {
			System.err.println("[c3p0] Warning: PooledConnection that has already signalled a Connection error is still in use!");
			System.err.println("[c3p0] Another error has occurred [ " + t + " ] which will not be reported to listeners!");
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
		    { closeExceptions.add(e); }
		
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
			if (Debug.TRACE == Debug.TRACE_MAX)
			    logCloseExceptions( cause, closeExceptions );
			else
			    logCloseExceptions( cause, null );
		    }
	    }
    }

    private void cleanupResultSets( List closeExceptions )
    {
	cleanupAllStatementResultSets( closeExceptions );
	cleanupMetaDataResultSets( closeExceptions );
    }

    private void cleanupMetaDataResultSets( List closeExceptions )
    {
	for ( Iterator ii = metaDataResultSets.iterator(); ii.hasNext(); )
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
				System.err.print("ResultSet close() failed: ");
				e.printStackTrace();
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
	if (cause != null)
	    {
		System.err.println("[c3p0] A PooledConnection died due to the following error!");
		cause.printStackTrace();
	    }
	if ( exceptions != null )
	    {
		if ( cause == null )
		    System.err.println("[c3p0] The following Exceptions occurred while trying to close a PooledConnection's resources normally.");
		else
		    System.err.println("[c3p0] The following Exceptions occurred while trying to close a broken PooledConnection.");
		for ( Iterator ii = exceptions.iterator(); ii.hasNext(); )
		    {
			Throwable t = (Throwable) ii.next();
			System.err.print("[c3p0 -- close Exception]: ");
			t.printStackTrace();
		    }
	    }
    }

}
