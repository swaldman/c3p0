/*
 * Distributed as part of c3p0 v.0.8.5-pre8
 *
 * Copyright (C) 2004 Machinery For Change, Inc.
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


package com.mchange.v2.c3p0.stmt;

import java.util.*;
import java.sql.*;
import java.lang.reflect.*;
import com.mchange.v2.async.AsynchronousRunner;
import com.mchange.v2.sql.SqlUtils;
import com.mchange.v1.db.sql.StatementUtils;

public abstract class GooGooStatementCache
{
    /* MT: protected by this's lock */

    // contains all statements in the cache, 
    // organized by connection
    ConnectionStatementManager cxnStmtMgr;

    // contains all statements in the cache, 
    // bound to the keys that produced them
    HashMap stmtToKey      = new HashMap();

    // maps all known keys to their set of statements
    // and to a queue of statements, if any, available
    // for checkout
    HashMap keyToKeyRec    = new HashMap();
    
    // contains all checked out statements -- in the cache, 
    // but not currently available for checkout, nor for
    // culling in case of overflow
    HashSet checkedOut = new HashSet();
    
    int stmt_count;

    /* MT: end protected by this' lock */

    //MT: protected by its own lock
    AsynchronousRunner blockingTaskAsyncRunner;

    public GooGooStatementCache(AsynchronousRunner blockingTaskAsyncRunner)
    { 
	this.blockingTaskAsyncRunner = blockingTaskAsyncRunner; 
	this.cxnStmtMgr = createConnectionStatementManager();
    }

    abstract ConnectionStatementManager createConnectionStatementManager();

    public synchronized Object checkoutStatement( Connection physicalConnection,
						  Method stmtProducingMethod, 
						  Object[] args )
	throws SQLException
    {
	Object out = null;

	StatementCacheKey key = StatementCacheKey.find( physicalConnection, 
							stmtProducingMethod, 
							args );
	LinkedList l = checkoutQueue( key );
	if (l == null || l.isEmpty()) //we need a new statement
	    {
		// we might wait() here... 
		// don't presume atomicity before and after!
		out = acquireStatement( physicalConnection, stmtProducingMethod, args );

		if ( prepareAssimilateNewStatement( physicalConnection ) )
		    assimilateNewCheckedOutStatement( key, physicalConnection, out );
		// else case: we can't assimilate the statement...
		// so, we just return our newly created statement, without caching it.
		// on check-in, it will simply be destroyed... this is an "overload statement"
	    }
	else //okay, we can use an old one
	    {
  		if (Debug.DEBUG && Debug.TRACE == Debug.TRACE_MAX)
  		    System.err.println("-------------> CACHE HIT!");

		out = l.get(0);
		l.remove(0);
		if (! checkedOut.add( out ))
		    throw new RuntimeException("Internal inconsistency: " +
					       "Checking out a statement marked " + 
					       "as already checked out!");
		removeStatementFromDeathmarches( out, physicalConnection );
	    }

	if (Debug.DEBUG && Debug.TRACE == Debug.TRACE_MAX)
	    {
		System.err.print("checkoutStatement(): ");
		printStats();
	    }

	return out;
    }

    public synchronized void checkinStatement( Object pstmt )
	throws SQLException
    {
	if (! checkedOut.remove( pstmt ) )
	    {
		if (! ourResource( pstmt ) ) //this is not our resource, or it is an overload statement
		    destroyStatement( pstmt ); // so we just destroy
		//in the else case, it's already checked-in, so we ignore
		
		return;
	    }

	try
	    { refreshStatement( (PreparedStatement) pstmt ); }
	catch (Exception e)
	    {
		if (Debug.DEBUG)
		    {
			System.err.println("Problem with checked-in Statement, discarding.");
			e.printStackTrace();
		    }
		
		// swaldman -- 2004-01-31: readd problem statement to checkedOut for consistency
		// the statement is not yet checked-in, but it is removed from checked out, and this
		// violates the consistency assumption of removeStatement(). Thanks to Zach Scott for
		// calling attention to this issue.
		checkedOut.add( pstmt );

		removeStatement( pstmt, true ); //force destruction of the statement even though it appears checked-out
		return;
	    }

	StatementCacheKey key = (StatementCacheKey) stmtToKey.get( pstmt );
	if (Debug.DEBUG && key == null)
	    throw new RuntimeException("Internal inconsistency: " +
				       "A checked-out statement has no key associated with it!");

	LinkedList l = checkoutQueue( key );
	l.add( pstmt );
	addStatementToDeathmarches( pstmt, key.physicalConnection );

	if (Debug.DEBUG && Debug.TRACE == Debug.TRACE_MAX)
	    {
		System.err.print("checkinStatement(): ");
		printStats();
	    }
    }


    public synchronized void checkinAll(Connection pcon)
	throws SQLException
    {
	//new Exception("checkinAll()").printStackTrace();

	Set stmtSet = cxnStmtMgr.statementSet( pcon );
	if (stmtSet != null)
	    {
		for (Iterator ii = stmtSet.iterator(); ii.hasNext(); )
		    {
			Object stmt = ii.next();
			if (checkedOut.contains( stmt ))
			    checkinStatement( stmt );
		    }
	    }

	if (Debug.DEBUG && Debug.TRACE == Debug.TRACE_MAX)
	    {
		System.err.print("checkinAll(): ");
		printStats();
	    }
    }

    public synchronized void closeAll(Connection pcon)
	throws SQLException
    {
	//new Exception("closeAll()").printStackTrace();

	Set cSet = cxnStmtMgr.statementSet( pcon );

  	if (Debug.DEBUG && Debug.TRACE == Debug.TRACE_MAX)
	    {
		System.err.println("ENTER METHOD: closeAll( " + pcon + " )! -- " +
				   "num_connections: " + cxnStmtMgr.getNumConnectionsWithCachedStatements());
		System.err.print("Set of statements for connection: " + cSet);
		if (cSet != null)
		    System.err.println("; size: " + cSet.size());
		else
		    System.err.println();
	    }

	if (cSet != null)
	    {
		//the removeStatement(...) removes from cSet, so we can't be iterating over cSet directly
		Set stmtSet = new HashSet( cSet );
		//System.err.println("SIZE FOR CONNECTION SET: " + stmtSet.size());
		for (Iterator ii = stmtSet.iterator(); ii.hasNext(); )
		    {
			Object stmt = ii.next();
			removeStatement( stmt, true );
		    }
	    }

	if (Debug.DEBUG && Debug.TRACE == Debug.TRACE_MAX)
	    {
		System.err.print("closeAll(): ");
		printStats();
	    }
    }

    public synchronized void close() 
	throws SQLException
    {
	for (Iterator ii = stmtToKey.keySet().iterator(); ii.hasNext(); )
	    synchronousDestroyStatement( ii.next() );

	cxnStmtMgr       = null;
	stmtToKey        = null;
	keyToKeyRec      = null;
	checkedOut       = null;
	stmt_count       = -1;
    }


    /* non-public methods that needn't be called with this' lock below */
     
    private void destroyStatement( final Object pstmt )
    {
 	Runnable r = new Runnable()
 	    {
 		public void run()
 		{  StatementUtils.attemptClose( (PreparedStatement) pstmt ); }
 	    };
	blockingTaskAsyncRunner.postRunnable(r);
    }

    private void synchronousDestroyStatement( final Object pstmt )
    { StatementUtils.attemptClose( (PreparedStatement) pstmt ); }

    /* end non-public methods that needn't be called with this' lock */



    /* non-public methods that MUST be called with this' lock */

    abstract boolean prepareAssimilateNewStatement(Connection pcon);

    abstract void addStatementToDeathmarches( Object pstmt, Connection physicalConnection );
    abstract void removeStatementFromDeathmarches( Object pstmt, Connection physicalConnection );

    final int countCachedStatements()
    { return stmtToKey.size(); }
    
    private void assimilateNewCheckedOutStatement( StatementCacheKey key, 
						   Connection pConn, 
						   Object ps )
    {
	stmtToKey.put( ps, key );
	HashSet ks = keySet( key );
	if (ks == null)
	    keyToKeyRec.put( key, new KeyRec() );
  	else if (Debug.DEBUG && Debug.TRACE == Debug.TRACE_MAX)
  	    System.err.println("-------> Multiply prepared statement! " + key.stmtText );
	keySet( key ).add( ps );
	cxnStmtMgr.addStatementForConnection( ps, pConn );

	if (Debug.DEBUG && Debug.TRACE == Debug.TRACE_MAX)
	    {
		System.err.println("cxnStmtMgr.statementSet( " + pConn + " ).size(): " + 
				   cxnStmtMgr.statementSet( pConn ).size());
	    }

	++stmt_count;

	checkedOut.add( ps );
    }

    private void removeStatement( Object ps , boolean force_destroy )
    {
	StatementCacheKey sck = (StatementCacheKey) stmtToKey.remove( ps );
	removeFromKeySet( sck, ps );
	Connection pConn = sck.physicalConnection;

	if (! checkedOut.contains( ps ) )
	    {
		removeStatementFromDeathmarches( ps, pConn );
		removeFromCheckoutQueue( sck , ps );
		destroyStatement( ps );
	    }
	else
	    {
		checkedOut.remove( ps ); // usually we let it defer destruction until check-in!
		if (force_destroy)       // but occasionally we want the statement assuredly gone.
		    destroyStatement( ps );
	    }

	boolean check =	cxnStmtMgr.removeStatementForConnection( ps, pConn );
	if (Debug.DEBUG && check == false)
	    new Exception("WARNING: removed a statement that apparently wasn't in a statement set!!!").printStackTrace();
	--stmt_count;
    }

    private Object acquireStatement(final Connection pConn, 
				    final Method stmtProducingMethod, 
				    final Object[] args )
	throws SQLException
    {
	try
	    {
		final Object[] outHolder = new Object[1];
		final SQLException[] exceptionHolder = new SQLException[1];
		
		Runnable r = new Runnable()
		    {
			public void run()
			{
			    try
				{
				    outHolder[0] = 
					stmtProducingMethod.invoke( pConn, 
								    args ); 
				}
			    catch ( InvocationTargetException e )
				{ 
				    Throwable targetException = e.getTargetException();
				    if ( targetException instanceof SQLException )
					exceptionHolder[0] = (SQLException) targetException;
				    else
					exceptionHolder[0] 
					    = SqlUtils.toSQLException(targetException);
				}
			    catch ( Exception e )
				{ exceptionHolder[0] = SqlUtils.toSQLException(e); }
			    finally
				{ 
				    synchronized ( GooGooStatementCache.this )
					{ GooGooStatementCache.this.notifyAll(); }
				}
			}
		    };
		
		blockingTaskAsyncRunner.postRunnable(r);
		while ( outHolder[0] == null && exceptionHolder[0] == null )
		    this.wait(); //give up our lock while the Statement gets prepared
		if (exceptionHolder[0] != null)
		    throw exceptionHolder[0];
		else
		    {
			Object out = outHolder[0];
			return out;
		    }
	    }
	catch ( InterruptedException e )
	    { throw SqlUtils.toSQLException( e ); }
    }

    private KeyRec keyRec( StatementCacheKey key )
    { return ((KeyRec) keyToKeyRec.get( key )); }

    private HashSet keySet( StatementCacheKey key )
    { 
	KeyRec rec = keyRec( key );
	return (rec == null ? null : rec.allStmts); 
    }

    private boolean removeFromKeySet( StatementCacheKey key, Object pstmt )
    {
	boolean out;
	HashSet stmtSet = keySet( key );
	out = stmtSet.remove( pstmt );
	if (stmtSet.isEmpty() && checkoutQueue( key ).isEmpty())
	    keyToKeyRec.remove( key );
	return out;
    }

    private LinkedList checkoutQueue( StatementCacheKey key )
    { 
	KeyRec rec = keyRec( key );
	return ( rec == null ? null : rec.checkoutQueue );
    }

    private boolean removeFromCheckoutQueue( StatementCacheKey key, Object pstmt )
    {
	boolean out;
	LinkedList q = checkoutQueue( key );
	out = q.remove( pstmt );
	if (q.isEmpty() && keySet( key ).isEmpty())
	    keyToKeyRec.remove( key );
	return out;
    }

    private boolean ourResource( Object ps )
    { return stmtToKey.keySet().contains( ps ); }

    private void refreshStatement( PreparedStatement ps ) throws Exception
    { ps.clearParameters(); }

    private void printStats()
    {
	//new Exception("printStats()").printStackTrace();
	int total_size = this.countCachedStatements();
	int checked_out_size = checkedOut.size();
	int num_connections  = cxnStmtMgr.getNumConnectionsWithCachedStatements(); 
	int num_keys = keyToKeyRec.size(); 
	System.err.print(this.getClass().getName() + " stats -- ");
	System.err.print("total size: " + total_size);
	System.err.print("; checked out: " + checked_out_size);
	System.err.print("; num connections: " + num_connections);
	System.err.println("; num keys: " + num_keys);
    }

    private static class KeyRec
    {
	HashSet  allStmts       = new HashSet();
	LinkedList checkoutQueue  = new LinkedList();
    }

    protected class Deathmarch
    {
	TreeMap longsToStmts = new TreeMap(); 
	HashMap stmtsToLongs = new HashMap();

	long last_long = -1;

	public void deathmarchStatement( Object ps )
	{
	    //System.err.println("deathmarchStatement( " + ps + " )");
	    if (Debug.DEBUG)
		{
		    Long old = (Long) stmtsToLongs.get( ps );
		    if (old != null)
			throw new RuntimeException("Internal inconsistency: " +
						   "A statement is being double-deathmatched. no checked-out statements should be in a deathmarch already; " +
						   "no already checked-in statement should be deathmarched!");
		}

	    Long youth = getNextLong();
	    stmtsToLongs.put( ps, youth );
	    longsToStmts.put( youth, ps );
	}

	public void undeathmarchStatement( Object ps )
	{
	    Long old = (Long) stmtsToLongs.remove( ps );
	    if (Debug.DEBUG && old == null)
		throw new RuntimeException("Internal inconsistency: " +
					   "A (not new) checking-out statement is not in deathmarch.");
	    Object check = longsToStmts.remove( old );
	    if (Debug.DEBUG && old == null)
		throw new RuntimeException("Internal inconsistency: " +
					   "A (not new) checking-out statement is not in deathmarch.");
	}

	public boolean cullNext()
	{
	    if ( longsToStmts.isEmpty() )
		return false;
	    else
		{
		    Long l = (Long) longsToStmts.firstKey();
		    Object ps = longsToStmts.get( l );
		    if (Debug.DEBUG && Debug.TRACE == Debug.TRACE_MAX)
			System.err.println("CULLING: " + 
					   ((StatementCacheKey) stmtToKey.get(ps)).stmtText);
		    // we do not undeathmarch the statement ourselves, because removeStatement( ... )
		    // should remove from all deathmarches...
		    removeStatement( ps, true ); 
		    if (Debug.DEBUG && this.contains( ps ))
			throw new RuntimeException("Inconsistency!!! Statement culled from deathmarch failed to be removed by removeStatement( ... )!");
		    return true;
		}
	}

	public boolean contains( Object ps )
	{ return stmtsToLongs.keySet().contains( ps ); }

	public int size()
	{ return longsToStmts.size(); }

	private Long getNextLong()
	{ return new Long( ++last_long ); }
    }

    protected static abstract class ConnectionStatementManager
    {
	Map cxnToStmtSets = new HashMap();

	public int getNumConnectionsWithCachedStatements()
	{ return cxnToStmtSets.size(); }

	public Set statementSet( Connection pcon )
	{ return (Set) cxnToStmtSets.get( pcon ); }

	public int getNumStatementsForConnection( Connection pcon )
	{
	    Set stmtSet = statementSet( pcon );
	    return (stmtSet == null ? 0 : stmtSet.size());
	}

	public void addStatementForConnection( Object ps, Connection pcon )
	{
	    Set stmtSet = statementSet( pcon );
	    if (stmtSet == null)
		{
		    stmtSet = new HashSet();
		    cxnToStmtSets.put( pcon, stmtSet );
		}
	    stmtSet.add( ps );
	}

	public boolean removeStatementForConnection( Object ps, Connection pcon )
	{
	    boolean out;

	    Set stmtSet = statementSet( pcon );
	    if ( stmtSet != null )
		{
		    out = stmtSet.remove( ps );
		    if (stmtSet.isEmpty())
			cxnToStmtSets.remove( pcon );
		}
	    else
		out = false;

	    return out;
	}
    }

    // i want this as optimized as possible, so i'm adopting the philosophy that all
    // classes are abstract or final, to help enable compiler inlining...
    protected static final class SimpleConnectionStatementManager extends ConnectionStatementManager 
    {}

    protected final class DeathmarchConnectionStatementManager extends ConnectionStatementManager
    {
	Map cxnsToDms = new HashMap();

	public void addStatementForConnection( Object ps, Connection pcon )
	{
	    super.addStatementForConnection( ps, pcon );
	    Deathmarch dm = (Deathmarch) cxnsToDms.get( pcon );
	    if (dm == null)
		{
		    dm = new Deathmarch();
		    cxnsToDms.put( pcon, dm );
		}
	}

	public boolean removeStatementForConnection( Object ps, Connection pcon )
	{
	    boolean out = super.removeStatementForConnection( ps, pcon );
	    if (out)
		{
		    if ( statementSet( pcon ) == null )
			cxnsToDms.remove( pcon );
		}
	    return out;
	}

	public Deathmarch getDeathmarch( Connection pcon )
	{ return (Deathmarch) cxnsToDms.get( pcon ); }
    }
}

