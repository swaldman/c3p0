/*
 * Distributed as part of c3p0 v.0.9.1.2
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


package com.mchange.v2.c3p0.stmt;

import java.util.*;
import java.sql.*;
import java.lang.reflect.*;
import com.mchange.v2.async.AsynchronousRunner;
import com.mchange.v2.sql.SqlUtils;
import com.mchange.v2.util.ResourceClosedException;
import com.mchange.v2.log.*;
import com.mchange.v1.db.sql.StatementUtils;

import java.io.StringWriter;
import java.io.PrintWriter;
import java.io.IOException;
import com.mchange.v2.io.IndentedWriter;

public abstract class GooGooStatementCache
{
    private final static MLogger logger = MLog.getLogger( GooGooStatementCache.class );

    private final static int DESTROY_NEVER          = 0;
    private final static int DESTROY_IF_CHECKED_IN  = 1 << 0; 
    private final static int DESTROY_IF_CHECKED_OUT = 1 << 1;
    private final static int DESTROY_ALWAYS         = (DESTROY_IF_CHECKED_IN | DESTROY_IF_CHECKED_OUT);

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

    /* MT: end protected by this' lock */

    /* MT: protected by its own lock */

    AsynchronousRunner blockingTaskAsyncRunner;

    // This set is used to ensure that multiple threads
    // do not try to remove the same statement from the
    // cache, if for example a Statement is both deathmarched
    // away and its parent Connection is closed.
    //
    // ALL ACCESS SHOULD BE EXPLICITLY SYNCHRONIZED
    // ON removalPending's lock!
    HashSet removalPending = new HashSet();

    /* MT: end protected by its own lock */

    public GooGooStatementCache(AsynchronousRunner blockingTaskAsyncRunner)
    { 
        this.blockingTaskAsyncRunner = blockingTaskAsyncRunner; 
        this.cxnStmtMgr = createConnectionStatementManager();
    }

    public synchronized int getNumStatements()
    { return this.isClosed() ? -1 : countCachedStatements(); }

    public synchronized int getNumStatementsCheckedOut()
    { return this.isClosed() ? -1 : checkedOut.size(); }

    public synchronized int getNumConnectionsWithCachedStatements()
    { return isClosed() ? -1 : cxnStmtMgr.getNumConnectionsWithCachedStatements(); }

    public synchronized String dumpStatementCacheStatus()
    {
        if (isClosed())
            return this + "status: Closed.";
        else
        {
            StringWriter sw = new StringWriter(2048);
            IndentedWriter iw = new IndentedWriter( sw );
            try
            {
                iw.print(this);
                iw.println(" status:");
                iw.upIndent();
                iw.println("core stats:");
                iw.upIndent();
                iw.print("num cached statements: ");
                iw.println( this.countCachedStatements() );
                iw.print("num cached statements in use: ");
                iw.println( checkedOut.size() );
                iw.print("num connections with cached statements: ");
                iw.println(cxnStmtMgr.getNumConnectionsWithCachedStatements());
                iw.downIndent();
                iw.println("cached statement dump:");
                iw.upIndent();
                for (Iterator ii = cxnStmtMgr.connectionSet().iterator(); ii.hasNext();)
                {
                    Connection pcon = (Connection) ii.next();
                    iw.print(pcon);
                    iw.println(':');
                    iw.upIndent();
                    for (Iterator jj = cxnStmtMgr.statementSet(pcon).iterator(); jj.hasNext();)
                        iw.println(jj.next());
                    iw.downIndent();
                }

                iw.downIndent();
                iw.downIndent();
                return sw.toString();
            }
            catch (IOException e)
            {
                if (logger.isLoggable(MLevel.SEVERE))
                    logger.log(MLevel.SEVERE, "Huh? We've seen an IOException writing to s StringWriter?!", e);
                return e.toString();
            }
        }
    }

    abstract ConnectionStatementManager createConnectionStatementManager();

    public synchronized Object checkoutStatement( Connection physicalConnection,
                    Method stmtProducingMethod, 
                    Object[] args )  
    throws SQLException, ResourceClosedException
    {
        try
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
                    logger.finest(this.getClass().getName() + " ----> CACHE HIT");
                //System.err.println("-------------> CACHE HIT!");

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
                //System.err.print("checkoutStatement(): ");
                //printStats();
                if (logger.isLoggable(MLevel.FINEST))
                    logger.finest("checkoutStatement: " + statsString());
            }

            return out;
        }
        catch (NullPointerException npe)
        {
            if (checkedOut == null) //we're closed
            {
                if (logger.isLoggable(MLevel.FINE))
                    logger.log( MLevel.FINE, 
                                "A client attempted to work with a closed Statement cache, " + "" +
                                "provoking a NullPointerException. c3p0 recovers, but this should be rare.", 
                                npe);
                throw new ResourceClosedException( npe );
            }
            else
                throw npe;
        }
    }

    public synchronized void checkinStatement( Object pstmt )
    throws SQLException
    {
        if (checkedOut == null) //we're closed
        {
            synchronousDestroyStatement( pstmt );

            return;
        }
        else if (! checkedOut.remove( pstmt ) )
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
//              System.err.println("Problem with checked-in Statement, discarding.");
//              e.printStackTrace();
                if (logger.isLoggable(MLevel.INFO))
                    logger.log(MLevel.INFO, "Problem with checked-in Statement, discarding.", e);
            }

            // swaldman -- 2004-01-31: readd problem statement to checkedOut for consistency
            // the statement is not yet checked-in, but it is removed from checked out, and this
            // violates the consistency assumption of removeStatement(). Thanks to Zach Scott for
            // calling attention to this issue.
            checkedOut.add( pstmt );

            removeStatement( pstmt, DESTROY_ALWAYS ); //force destruction of the statement even though it appears checked-out
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
//          System.err.print("checkinStatement(): ");
//          printStats();
            if (logger.isLoggable(MLevel.FINEST))
                logger.finest("checkinStatement(): " + statsString());
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
//          System.err.print("checkinAll(): ");
//          printStats();
            if (logger.isLoggable(MLevel.FINEST))
                logger.log(MLevel.FINEST, "checkinAll(): " + statsString());
        }
    }

    /*
     * we only selectively sync' parts of this method, because we wish to wait for
     * Statements we wish to destroy the Statements synchronously, but without
     * holding the pool's lock.
     */
    public void closeAll(Connection pcon) throws SQLException
    {
//      System.err.println( this + ": closeAll( " + pcon + " )" );
//      new Exception("closeAll()").printStackTrace();

//      assert !Thread.holdsLock( this );

        if (! this.isClosed())
        {
            if (Debug.DEBUG && Debug.TRACE == Debug.TRACE_MAX)
            {
                if (logger.isLoggable(MLevel.FINEST))
                {
                    logger.log(MLevel.FINEST, "ENTER METHOD: closeAll( " + pcon + " )! -- num_connections: " + 
                                    cxnStmtMgr.getNumConnectionsWithCachedStatements());
                    //logger.log(MLevel.FINEST, "Set of statements for connection: " + cSet + (cSet != null ? "; size: " + cSet.size() : ""));
                }
            }

            Set stmtSet = null;
            synchronized (this)
            {
                Set cSet = cxnStmtMgr.statementSet( pcon ); 

                if (cSet != null)
                {
                    //the removeStatement(...) removes from cSet, so we can't be iterating over cSet directly
                    stmtSet = new HashSet( cSet );
                    //System.err.println("SIZE FOR CONNECTION SET: " + stmtSet.size());

                    for (Iterator ii = stmtSet.iterator(); ii.hasNext(); )
                    {
                        Object stmt = ii.next();
                        // we remove without destroying, leaving the destruction
                        // until when we lose the pool's lock
                        removeStatement( stmt, DESTROY_NEVER ); 
                    }
                }
            }

            if ( stmtSet != null )
            {
                for (Iterator ii = stmtSet.iterator(); ii.hasNext(); )
                {
                    Object stmt = ii.next();
                    synchronousDestroyStatement( stmt );
                }
            }

            if (Debug.DEBUG && Debug.TRACE == Debug.TRACE_MAX)
            {
                if (logger.isLoggable(MLevel.FINEST))
                    logger.finest("closeAll(): " + statsString());
            }
        }
//      else
//      {
//      if (logger.isLoggable(MLevel.FINER))
//      logger.log(MLevel.FINER, 
//      this + ":  call to closeAll() when statment cache is already closed! [not harmful! debug only!]", 
//      new Exception("DUPLICATE CLOSE DEBUG STACK TRACE."));
//      }
    }

    public synchronized void close() 
    throws SQLException
    {
        //System.err.println( this + ": close()" );

        if (! isClosed())
        {
            for (Iterator ii = stmtToKey.keySet().iterator(); ii.hasNext(); )
                synchronousDestroyStatement( ii.next() );

            cxnStmtMgr       = null;
            stmtToKey        = null;
            keyToKeyRec      = null;
            checkedOut       = null;
        }
        else
        {
            if (logger.isLoggable(MLevel.FINE))
                logger.log(MLevel.FINE, this + ": duplicate call to close() [not harmful! -- debug only!]", new Exception("DUPLICATE CLOSE DEBUG STACK TRACE."));
        }

    }


    public synchronized boolean isClosed()
    { return cxnStmtMgr == null; }

    /* non-public methods that needn't be called with this' lock below */

    private void destroyStatement( final Object pstmt )
    {
        class StatementCloseTask implements Runnable
        {
            public void run()
            { StatementUtils.attemptClose( (PreparedStatement) pstmt ); }
        }

        Runnable r = new StatementCloseTask();

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
        else 
        {
            //System.err.println("-------> Multiply prepared statement! " + key.stmtText );
            if (logger.isLoggable(MLevel.INFO))
                logger.info("Multiply prepared statement! " + key.stmtText );
            if (Debug.DEBUG && logger.isLoggable(MLevel.FINE))
                logger.fine("(The same statement has already been prepared by this Connection, " +
                                "and that other instance has not yet been closed, so the statement pool " +
                                "has to prepare a second PreparedStatement object rather than reusing " +
                                "the previously-cached Statement. The new Statement will be cached, in case " +
                "you frequently need multiple copies of this Statement.)");
        }
        keySet( key ).add( ps );
        cxnStmtMgr.addStatementForConnection( ps, pConn );

        if (Debug.DEBUG && Debug.TRACE == Debug.TRACE_MAX)
        {
//          System.err.println("cxnStmtMgr.statementSet( " + pConn + " ).size(): " + 
//          cxnStmtMgr.statementSet( pConn ).size());
            if (logger.isLoggable(MLevel.FINEST))
                logger.finest("cxnStmtMgr.statementSet( " + pConn + " ).size(): " + 
                                cxnStmtMgr.statementSet( pConn ).size());
        }

        checkedOut.add( ps );
    }

    private void removeStatement( Object ps , int destruction_policy )
    {
        synchronized (removalPending)
        {
            if ( removalPending.contains( ps ) )
                return;
            else
                removalPending.add(ps);
        }

        StatementCacheKey sck = (StatementCacheKey) stmtToKey.remove( ps );
        removeFromKeySet( sck, ps );
        Connection pConn = sck.physicalConnection;

        boolean checked_in = !checkedOut.contains( ps );

        if ( checked_in )
        {
            removeStatementFromDeathmarches( ps, pConn );
            removeFromCheckoutQueue( sck , ps );
            if ((destruction_policy & DESTROY_IF_CHECKED_IN) != 0)
                destroyStatement( ps );
        }
        else
        {
            checkedOut.remove( ps );
            if ((destruction_policy & DESTROY_IF_CHECKED_OUT) != 0)
                destroyStatement( ps );
        }


        boolean check =	cxnStmtMgr.removeStatementForConnection( ps, pConn );
        if (Debug.DEBUG && check == false)
        {
            //new Exception("WARNING: removed a statement that apparently wasn't in a statement set!!!").printStackTrace();
            if (logger.isLoggable(MLevel.WARNING))
                logger.log(MLevel.WARNING, 
                                this + " removed a statement that apparently wasn't in a statement set!!!",
                                new Exception("LOG STACK TRACE"));
        }

        synchronized (removalPending)
        { removalPending.remove(ps); }
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

            class StmtAcquireTask implements Runnable
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
            }

            Runnable r = new StmtAcquireTask();
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

    private String statsString()
    {
        int total_size = this.countCachedStatements();
        int checked_out_size = checkedOut.size();
        int num_connections  = cxnStmtMgr.getNumConnectionsWithCachedStatements(); 
        int num_keys = keyToKeyRec.size(); 

        StringBuffer sb = new StringBuffer(255);
        sb.append(this.getClass().getName());
        sb.append(" stats -- ");
        sb.append("total size: ");
        sb.append(total_size);
        sb.append("; checked out: ");
        sb.append(checked_out_size);
        sb.append("; num connections: ");
        sb.append(num_connections);
        sb.append("; num keys: ");
        sb.append(num_keys);
        return sb.toString();
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
                {
//                  System.err.println("CULLING: " + 
//                  ((StatementCacheKey) stmtToKey.get(ps)).stmtText);
                    if (logger.isLoggable(MLevel.FINEST))
                        logger.finest("CULLING: " + ((StatementCacheKey) stmtToKey.get(ps)).stmtText);
                }
                // we do not undeathmarch the statement ourselves, because removeStatement( ... )
                // should remove from all deathmarches...
                removeStatement( ps, DESTROY_ALWAYS ); 
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

        public Set connectionSet()
        { return cxnToStmtSets.keySet(); }

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

