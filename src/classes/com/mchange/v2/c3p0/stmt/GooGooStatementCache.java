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


package com.mchange.v2.c3p0.stmt;

import java.util.*;
import java.sql.*;
import java.lang.reflect.*;
import com.mchange.v2.async.AsynchronousRunner;
import com.mchange.v2.sql.SqlUtils;
import com.mchange.v1.db.sql.StatementUtils;

public final class GooGooStatementCache
{
    /* MT: protected by this's lock */

    // contains all statements in the cache, 
    // organized by connection
    HashMap cxnToStmtSets = new HashMap();

    // contains all statements in the cache, 
    // bound to the keys that produced them
    HashMap stmtToKey      = new HashMap();

    // maps all known keys to their set of statements
    // and to a queue of statements, if any, available
    // for checkout
    HashMap keyToKeyRec    = new HashMap();
    
    // contains only statements available for checkout
    // organized by key. values are Lists
    // Map keyToStmtQueue = new HashMap(); 

    // "death march" structures -- contain only statements
    // available for checkout, therefore also available
    // for purge if the cache overflows
    TreeMap longsToStmts = new TreeMap(); //death-march...
    HashMap stmtsToLongs = new HashMap();

    // contains all checked out statements -- in the cache, 
    // but not currently available for checkout, nor for
    // culling in case of overflow
    HashSet checkedOut = new HashSet();
    
    long last_long = -1;

    int stmt_count;

    /* MT: end protected by this' lock */

    //MT: protected by its own lock
    AsynchronousRunner blockingTaskAsyncRunner;

    //MT: Unchanging
    int max_statements;

    public GooGooStatementCache(AsynchronousRunner blockingTaskAsyncRunner, int max_statements)
    { 
	this.blockingTaskAsyncRunner = blockingTaskAsyncRunner; 
	this.max_statements = max_statements;
    }

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

		int size = this.countCachedStatements();
		if (  size < max_statements || (size == max_statements && cullFromDeathmarch()) )
		    assimilateNewCheckedOutStatement( key, physicalConnection, out );
		// else case: max_statements are checked out... we can't cache any more 
		// so, we just return our newly created statement, without caching it.
		// on check-in, it will simply be destroyed... this is an "overload statement"
	    }
	else //okay, we can use an old one
	    {
//  		if (Debug.DEBUG && Debug.TRACE == Debug.TRACE_MAX)
//  		    System.err.println("-------------> CACHE HIT!");
		out = l.get(0);
		l.remove(0);
		undeathmarchStatement( out );
		if (! checkedOut.add( out ))
		    throw new RuntimeException("Internal inconsistency: " +
					       "Checking out a statement marked " + 
					       "as already checked out!");
	    }

	if (Debug.DEBUG && Debug.TRACE == Debug.TRACE_MAX)
	    printStats();

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
	deathmarchStatement( pstmt );

	if (Debug.DEBUG && Debug.TRACE == Debug.TRACE_MAX)
	    printStats();
    }

    public synchronized void checkinAll(Connection pcon)
	throws SQLException
    {
	HashSet stmtSet = connectionSet( pcon );
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
	    printStats();
    }

    public synchronized void closeAll(Connection pcon)
	throws SQLException
    {
//  	if (Debug.DEBUG && Debug.TRACE == Debug.TRACE_MAX)
//  	    System.err.println("ENTER METHOD: closeAll( " + pcon + " )! -- " +
//  			       "num_connections: " + cxnToStmtSets.size());
	HashSet cSet = connectionSet( pcon );
	//System.err.println("cSet: " + cSet);
	if (cSet != null)
	    {
		HashSet stmtSet = (HashSet) cSet.clone();
		//System.err.println("SIZE FOR CONNECTION SET: " + stmtSet.size());
		for (Iterator ii = stmtSet.iterator(); ii.hasNext(); )
		    {
			Object stmt = ii.next();
			removeStatement( stmt, true );
		    }
	    }

	if (Debug.DEBUG && Debug.TRACE == Debug.TRACE_MAX)
	    printStats();
    }

    public synchronized void close() 
	throws SQLException
    {
	for (Iterator ii = stmtToKey.keySet().iterator(); ii.hasNext(); )
	    destroyStatement( ii.next() );

	cxnToStmtSets  = null;
	stmtToKey      = null;
	keyToKeyRec    = null;
	longsToStmts   = null;
	stmtsToLongs   = null;
	checkedOut     = null;
	stmt_count     = -1;
    }


    /* private methods that needn't be called with this' lock below */
     
    private void destroyStatement( final Object pstmt )
    {
	Runnable r = new Runnable()
	    {
		public void run()
		{  StatementUtils.attemptClose( (PreparedStatement) pstmt ); }
	    };
	blockingTaskAsyncRunner.postRunnable(r);
    }

    /* end private methods that needn't be called with this' lock */



    /* private methods that MUST be called with this' lock */

    private void assimilateNewCheckedOutStatement( StatementCacheKey key, 
						   Connection pConn, 
						   Object ps )
    {
	stmtToKey.put( ps, key );
	HashSet ks = keySet( key );
	if (ks == null)
	    keyToKeyRec.put( key, new KeyRec() );
//  	else if (Debug.DEBUG && Debug.TRACE == Debug.TRACE_MAX)
//  	    System.err.println("-------> Multiply prepared statement! " + key.stmtText );
	keySet( key ).add( ps );
	HashSet cSet = connectionSet( pConn );
	if (cSet == null)
	    {
		cSet = new HashSet();
		cxnToStmtSets.put( pConn, cSet );
	    }
	cSet.add( ps );
	//System.err.println("connectionSet( " + pConn + " ).size(): " + 
	//		      connectionSet( pConn ).size());
	++stmt_count;

	checkedOut.add( ps );
    }

    private void removeStatement( Object ps , boolean force_destroy )
    {
	StatementCacheKey sck = (StatementCacheKey) stmtToKey.remove( ps );
	removeFromKeySet( sck, ps );
	Connection pConn = sck.physicalConnection;
	removeFromConnectionSet( pConn, ps );
	--stmt_count;

	if (! checkedOut.contains( ps ) )
	    {
		undeathmarchStatement( ps );
		removeFromCheckoutQueue( sck , ps );
		destroyStatement( ps );
	    }
	else
	    {
		checkedOut.remove( ps ); // usually we let it defer destruction until check-in!
		if (force_destroy)       // but occasionally we want the statement assuredly gone.
		    destroyStatement( ps );
	    }
    }

    private boolean cullFromDeathmarch()
    {
	if ( longsToStmts.isEmpty() )
	    return false;
	else
	    {
		Long l = (Long) longsToStmts.firstKey();
		Object ps = longsToStmts.get( l );
//  		if (Debug.DEBUG && Debug.TRACE == Debug.TRACE_MAX)
//  		    System.err.println("CULLING: " + 
//  				       ((StatementCacheKey) stmtToKey.get(ps)).stmtText);
		removeStatement( ps, true );
		return true;
	    }
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

    private HashSet connectionSet( Connection pcon )
    { return (HashSet) cxnToStmtSets.get( pcon ); }

    private boolean removeFromConnectionSet( Connection pcon, Object stmt )
    {
//  	if (Debug.DEBUG && Debug.TRACE == Debug.TRACE_MAX)
//  	    System.err.println("ENTER METHOD: " +
//  			       "removeFromConnectionSet( Connection pcon, Object stmt )");

	boolean out;
	HashSet stmtSet = (HashSet) cxnToStmtSets.get( pcon );
	//System.err.println("      stmtSet.size() -- begin: " + stmtSet.size());
	if ( stmtSet != null )
	    {
		out = stmtSet.remove( stmt );
		if (stmtSet.isEmpty())
		    {
			cxnToStmtSets.remove( pcon );
//  			System.err.println("Removed Connection!!!!!!!!!!!!!!!");
		    }
	    }
	else
	    out = false;
	//System.err.println("      stmtSet.size() -- end: " + stmtSet.size());
	return out;
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

    private void deathmarchStatement( Object ps )
    {
	//System.err.println("deathmarchStatement( " + ps + " )");
	if (Debug.DEBUG)
	    {
		Long old = (Long) stmtsToLongs.get( ps );
		if (old != null)
		    throw new RuntimeException("Internal inconsistency: " +
					       "A checking-in statement is already in deathmarch.");
	    }

	Long youth = getNextLong();
	stmtsToLongs.put( ps, youth );
	longsToStmts.put( youth, ps );
    }
    
    private void undeathmarchStatement( Object ps )
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

    private boolean isCheckedIn( Object ps )
    { return stmtsToLongs.keySet().contains( ps ); }

    private boolean ourResource( Object ps )
    { return stmtToKey.keySet().contains( ps ); }

    private void refreshStatement( PreparedStatement ps ) throws Exception
    { ps.clearParameters(); }

    private int countCachedStatements()
    { return stmtToKey.size(); }
    
    private Long getNextLong()
    { return new Long( ++last_long ); }

    private void printStats()
    {
	int total_size = this.countCachedStatements();
	int checked_out_size = checkedOut.size();
	int checked_in_size_stmts = stmtsToLongs.size(); 
	int checked_in_size_longs = longsToStmts.size(); 
	int num_connections = cxnToStmtSets.size(); 
	int num_keys = keyToKeyRec.size(); 
	System.err.println("GooGooStatementCache stats:");
	System.err.println("\ttotal size: " + total_size);
	System.err.println("\tchecked out: " + checked_out_size);
	System.err.println("\tchecked in (deathmarch stmts): " + checked_in_size_stmts);
	System.err.println("\tchecked in (deathmarch longs): " + checked_in_size_longs);
	System.err.println("\tnum connections: " + num_connections);
	System.err.println("\tnum keys: " + num_keys);
	if (total_size != checked_out_size + checked_in_size_stmts ||
	    checked_in_size_stmts != checked_in_size_longs)
	    throw new RuntimeException("Inconsistency!");
    }

    private static class KeyRec
    {
	HashSet  allStmts       = new HashSet();
	LinkedList checkoutQueue  = new LinkedList();
    }
}


