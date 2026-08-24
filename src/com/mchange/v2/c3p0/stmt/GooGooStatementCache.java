package com.mchange.v2.c3p0.stmt;

import java.util.*;
import java.sql.*;
import java.lang.reflect.*;
import java.util.concurrent.locks.*;
import com.mchange.v2.async.AsynchronousRunner;
import com.mchange.v2.holders.SynchronizedIntHolder;
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

    final ReentrantLock mainLock = new ReentrantLock();
    final Condition     conditionStatementPerhapsAcquired = mainLock.newCondition();

    final boolean cancelAutomaticallyClosedStatements;

    // Alternative culling algorithm minimizes hazard to drivers that can't have Statements closed beneath
    // active Connections. No longer necessary now that we've implemented deferred culling and not so good
    // since when all Connections are in use, nothing can be culled and new Statements cannot be cached
    private final static boolean CULL_ONLY_FROM_UNUSED_CONNECTIONS = false; //alternative culling

    /* MT: protected by mainLock */

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

    /* MT: end protected by mainLock */

    /* MT: protected by its own lock */

    final AsynchronousRunner blockingTaskAsyncRunner;

    final StatementDestructionManager destructo;

    // the parent WeakHashMap and all mutable Hazard objects are protected by the stmtToHazards monitor!
    //
    // we use WeakHashMap because values are set externally, by Statement proxies (via Connection proxies),
    // and need not reliably map to Statements ever actually incorporated into the cache.
    // uncached Statements will fall out of this map after they are garbage collected
    final WeakHashMap stmtToHazards = new WeakHashMap();

    /* MT: end protected by its own lock */

    // This set is used to ensure that multiple threads
    // do not try to remove the same statement from the
    // cache, if for example a Statement is both deathmarched
    // away and its parent Connection is closed.
    //
    // ALL ACCESS SHOULD BE EXPLICITLY SYNCHRONIZED
    // ON removalPending's lock!
    final HashSet removalPending     = new HashSet();
    final Lock    removalPendingLock = new ReentrantLock();

    public GooGooStatementCache(AsynchronousRunner blockingTaskAsyncRunner, AsynchronousRunner deferredStatementDestroyer, boolean cancelAutomaticallyClosedStatements)
    {
        this.blockingTaskAsyncRunner = blockingTaskAsyncRunner;
        this.cxnStmtMgr = createConnectionStatementManager();
	this.destructo =
	    deferredStatementDestroyer != null                                       ?
	    (StatementDestructionManager) new CautiousStatementDestructionManager( deferredStatementDestroyer )  :
	    (StatementDestructionManager) new IncautiousStatementDestructionManager( blockingTaskAsyncRunner );
        this.cancelAutomaticallyClosedStatements = cancelAutomaticallyClosedStatements;
    }

    public int getNumStatements()
    {
        mainLock.lock();
        try
        { return this.isClosed() ? -1 : countCachedStatements(); }
        finally
        { mainLock.unlock(); }
    }

    public int getNumStatementsCheckedOut()
    {
        mainLock.lock();
        try
        { return this.isClosed() ? -1 : checkedOut.size(); }
        finally
        { mainLock.unlock(); }
    }

    public int getNumConnectionsWithCachedStatements()
    {
        mainLock.lock();
        try
        { return isClosed() ? -1 : cxnStmtMgr.getNumConnectionsWithCachedStatements(); }
        finally
        { mainLock.unlock(); }
    }

    public String dumpStatementCacheStatus()
    {
        mainLock.lock();
        try
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
        finally
        { mainLock.unlock(); }
    }

    public void waitMarkConnectionInUse(Connection physicalConnection) throws InterruptedException { destructo.waitMarkConnectionInUse( physicalConnection ); }
    public boolean tryMarkConnectionInUse(Connection physicalConnection)                           { return destructo.tryMarkConnectionInUse( physicalConnection ); }
    public void unmarkConnectionInUse(Connection physicalConnection)                               { destructo.unmarkConnectionInUse( physicalConnection ); }
    public Boolean inUse(Connection physicalConnection)                                            { return destructo.tvlInUse( physicalConnection ); }

    public int getStatementDestroyerNumConnectionsInUse()                           { return destructo.getNumConnectionsInUse(); }
    public int getStatementDestroyerNumConnectionsWithDeferredDestroyStatements()   { return destructo.getNumConnectionsWithDeferredDestroyStatements(); }
    public int getStatementDestroyerNumDeferredDestroyStatements()                  { return destructo.getNumDeferredDestroyStatements(); }


    abstract ConnectionStatementManager createConnectionStatementManager();

    public Object checkoutStatement( Connection physicalConnection, Method stmtProducingMethod, Object[] args )
        throws SQLException, ResourceClosedException
    { return checkoutStatement( physicalConnection, stmtProducingMethod, args, null ); }

    /**
     * @param actuallyCachedHolder if non-null, set to whether the returned Statement was taken into the cache.
     *        A Statement the cache declines to keep -- because everything it held for this Connection
     *        was checked out, so nothing could be culled -- is an "overload Statement", and is returned uncached.
     *        The cache destroys it if it is ever checked in, and knows nothing about it otherwise: whoever holds one owns it.
     *        But clients that try to ensure that abandoned Statements get close()ed when parent Connections are checked-in
     *        need to know that the cache was unable to take control over the "overload Statement", so that they know to
     *        close the Statement themselves upon Connection check-in.
     */
    public Object checkoutStatement( Connection physicalConnection, Method stmtProducingMethod, Object[] args, boolean[] actuallyCachedHolder )
        throws SQLException, ResourceClosedException
    {
        mainLock.lock();
        try
        {
            Object out = null;

            StatementCacheKey key = StatementCacheKey.find( physicalConnection,
                            stmtProducingMethod,
                            args );
            LinkedList l = checkoutQueue( key );
            if (l == null || l.isEmpty()) //we need a new statement
            {
                // we might conditionStatementPerhapsAcquired.await() here...
                // don't presume atomicity before and after!
                out = acquireStatement( physicalConnection, stmtProducingMethod, args );

                if ( prepareAssimilateNewStatement( physicalConnection ) )
                {
                    assimilateNewCheckedOutStatement( key, physicalConnection, out );
                    if (actuallyCachedHolder != null) actuallyCachedHolder[0] = true;
                }
                else
                {
                    // we can't assimilate the statement...
                    // so, we just return our newly created statement, without caching it.
                    // on check-in, it will simply be destroyed... this is an "overload statement"
                    // we let the client know this by setting actuallyCachedHolder[0] to false
                    if (actuallyCachedHolder != null) actuallyCachedHolder[0] = false;
                }
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

                // we are reusing a cached Statement, so the Statement is not an
                // "overload Statement". it is managed by the cache
                if (actuallyCachedHolder != null) actuallyCachedHolder[0] = true;
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
        finally
        { mainLock.unlock(); }
    }

    public void checkinStatement( Object pstmt )
	throws SQLException
    {
        mainLock.lock();
        try
        {
            if (checkedOut == null) //we're closed
            {
                destructo.synchronousDestroyStatement( pstmt );

                return;
            }
            else if (! checkedOut.remove( pstmt ) )
            {
                if (! ourResource( pstmt ) ) //this is not our resource, or it is an overload statement
                    destructo.uncheckedDestroyStatement( pstmt ); // so we just destroy
                //in the else case, it's already checked-in, so we ignore

                return;
            }

            try
            { refreshStatement( (PreparedStatement) pstmt ); }
            catch (Exception e)
            {
                if (Debug.DEBUG)
                {
                    if (e instanceof IrreversibleHazardException)
                    {
                        // these represent occasions where we basically choose not to cache, because
                        // we can't guarantee the quality of the statements if we did cache them.
                        // this is a choice we make under some circumstance, not a problem of which most
                        // users must be informed
                        if (logger.isLoggable(MLevel.DEBUG))
                            logger.log(MLevel.DEBUG, "A Statement could not be restored to its initial condition for (re)inclusion in the cache. Discarding.", e);
                    }
                    else
                    {
                        // System.err.println("Problem with checked-in Statement, discarding.");
                        // e.printStackTrace();
                        if (logger.isLoggable(MLevel.INFO))
                            logger.log(MLevel.INFO, "Problem with checked-in Statement, discarding.", e);
                    }
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
                // System.err.print("checkinStatement(): ");
                // printStats();
                if (logger.isLoggable(MLevel.FINEST))
                    logger.finest("checkinStatement(): " + statsString());
            }
        }
        finally
        { mainLock.unlock(); }
    }


    public void checkinAll(Connection pcon) throws SQLException
    {
        mainLock.lock();
        try
        {
            //new Exception("checkinAll()").printStackTrace();

            HashSet stmtSet = cxnStmtMgr.statementSet( pcon );
            if (stmtSet != null)
            {
                // we clone to prevent a rare ConcurrentModificationException, which can occur if
                // an Exception occurs during Statement checkin
                //
                // see https://github.com/swaldman/c3p0/pull/22
                Set snapshot = (Set) stmtSet.clone();

                for (Iterator ii = snapshot.iterator(); ii.hasNext(); )
                {
                    Object stmt = ii.next();
                    if (checkedOut.contains( stmt ))
                        checkinStatement( stmt );
                }
            }

            if (Debug.DEBUG && Debug.TRACE == Debug.TRACE_MAX)
            {
                // System.err.print("checkinAll(): ");
                // printStats();
                if (logger.isLoggable(MLevel.FINEST))
                    logger.log(MLevel.FINEST, "checkinAll(): " + statsString());
            }
        }
        finally
        { mainLock.unlock(); }
    }

    /*
     * we only selectively sync' parts of this method, because we wish to wait for
     * Statements we wish to destroy the Statements synchronously, but without
     * holding the pool's mainLock.
     */
    public void closeAll(Connection pcon) throws SQLException
    {
//      System.err.println( this + ": closeAll( " + pcon + " )" );
//      new Exception("closeAll()").printStackTrace();

//      assert mainLock.isHeldByCurrentThread();

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

            mainLock.lock();
            try
            {
                HashSet cSet = cxnStmtMgr.statementSet( pcon );

                if (cSet != null)
                {
                    //the removeStatement(...) removes from cSet, so we can't be iterating over cSet directly
                    stmtSet = (HashSet) cSet.clone();
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
            finally
            { mainLock.unlock(); }

            if ( stmtSet != null )
            {
                for (Iterator ii = stmtSet.iterator(); ii.hasNext(); )
                {
                    Object stmt = ii.next();
                    destructo.synchronousDestroyStatement( stmt );
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

    public void close()
	throws SQLException
    {
        mainLock.lock();
        try
        {
            //System.err.println( this + ": close()" );

            if (! isClosed())
            {
                for (Iterator ii = stmtToKey.keySet().iterator(); ii.hasNext(); )
                    destructo.synchronousDestroyStatement( ii.next() );
                destructo.close();

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

            // arguably this should be in the (! isClose) branch above, but it's harmless to
            // repeat and if somehow there are threads still waiting on the lock during a
            // duplicate close() call, the redundant call would liberate them.
            //
            // that shouldn't happen. but what if it did?
            conditionStatementPerhapsAcquired.signalAll();
        }
        finally
        { mainLock.unlock(); }

    }


    public boolean isClosed()
    {
        mainLock.lock();
        try
        {
            return cxnStmtMgr == null;
        }
        finally
        { mainLock.unlock(); }
    }


    // should only be called with the stmtToHazards monitor
    private Hazards _hazardsForStatement( Object pstmt, boolean create )
    {
        Hazards out = (Hazards) stmtToHazards.get( pstmt );
        if (create && out == null)
        {
            out = new Hazards();
            stmtToHazards.put( pstmt, out );
        }
        return out;
    }

    // should only be called with the stmtToHazards monitor
    private void _clearHazardsForStatement( Object pstmt )
    { stmtToHazards.remove( pstmt ); }

    // can be called without the stmtToHazards monitor, acquires it
    // not currently used, but for symmetry with clearHazardsForStatement(...), which is used
    private Hazards hazardsForStatement( Object pstmt, boolean create )
    { synchronized(stmtToHazards) { return _hazardsForStatement( pstmt, create ); } }
    
    // can be called without the stmtToHazards monitor, acquires it
    private void clearHazardsForStatement( Object pstmt )
    { synchronized(stmtToHazards) { _clearHazardsForStatement( pstmt ); } }

    public void markCursorNameSet(Object pstmt)                       { synchronized(stmtToHazards) { _hazardsForStatement( pstmt, true ).markCursorNameSet(); } }
    public void markCloseOnCompletionSet(Object pstmt)                { synchronized(stmtToHazards) { _hazardsForStatement( pstmt, true ).markCloseOnCompletionSet(); } }
    public void markQueryTimeoutUpdatedFrom(Object pstmt, int from)   { synchronized(stmtToHazards) { _hazardsForStatement( pstmt, true ).markQueryTimeoutUpdatedFrom(from); } }
    public void markFetchDirectionUpdatedFrom(Object pstmt, int from) { synchronized(stmtToHazards) { _hazardsForStatement( pstmt, true ).markFetchDirectionUpdatedFrom(from); } }
    public void markFetchSizeUpdatedFrom(Object pstmt, int from)      { synchronized(stmtToHazards) { _hazardsForStatement( pstmt, true ).markFetchSizeUpdatedFrom(from); } }
    public void markMaxFieldSizeUpdatedFrom(Object pstmt, int from)   { synchronized(stmtToHazards) { _hazardsForStatement( pstmt, true ).markMaxFieldSizeUpdatedFrom(from); } }
    public void markMaxRowsUpdatedFrom(Object pstmt, long from)       { synchronized(stmtToHazards) { _hazardsForStatement( pstmt, true ).markMaxRowsUpdatedFrom(from); } }

    /* non-public methods that MUST be called with mainLock */

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
                logger.info("Multiply-cached PreparedStatement: " + key.stmtText );
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
                logger.finest("assimilateNewCheckedOutStatement(...) -- cxnStmtMgr.statementSet( " + pConn + " ).size(): " +
                                cxnStmtMgr.statementSet( pConn ).size());
        }

        checkedOut.add( ps );
    }

    private void removeStatement( Object ps , int destruction_policy )
    {
        removalPendingLock.lock();
        try
        {
            if ( removalPending.contains( ps ) )
                return;
            else
                removalPending.add(ps);
        }
        finally
        { removalPendingLock.unlock(); }

        try
        {
            clearHazardsForStatement( ps );

            StatementCacheKey sck = (StatementCacheKey) stmtToKey.remove( ps );
            if ( sck == null ) //not (or no longer) a cached Statement -- there is nothing to remove
            {
                if (logger.isLoggable(MLevel.WARNING))
                    logger.log(MLevel.WARNING,
                               this + " was asked to remove a Statement it does not hold. Please report this. " +
                               "[The Statement cache recovers; this is a diagnostic.]",
                               new Exception("LOG STACK TRACE"));

                // rather than provoke null pointer Exceptions trying to remove a Statement
                // that appears already not to be in the cache, we just quit.
                return;
            }

            removeFromKeySet( sck, ps );
            Connection pConn = sck.physicalConnection;

            boolean checked_in = !checkedOut.contains( ps );

            if ( checked_in )
            {
                removeStatementFromDeathmarches( ps, pConn );
                removeFromCheckoutQueue( sck , ps );
                if ((destruction_policy & DESTROY_IF_CHECKED_IN) != 0)
                    destructo.deferredDestroyStatement( pConn, ps );
            }
            else
            {
                checkedOut.remove( ps );
                if ((destruction_policy & DESTROY_IF_CHECKED_OUT) != 0)
                    destructo.deferredDestroyStatement( pConn, ps );
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
        }
        finally
        {
            // no matter what, we'd better get this Statement out of removalPending,
            // or else it will become a never removable zombie (see the guard at
            // the very beginning of this method.)
            removalPendingLock.lock();
            try
            { removalPending.remove(ps); }
            finally
            { removalPendingLock.unlock(); }
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
            final Throwable[] exceptionHolder = new Throwable[1];

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
			exceptionHolder[0] = targetException;
                    }
                    catch ( Exception e )
                    { exceptionHolder[0] = e; }
                    catch ( Throwable t )
                    {
                        if (logger.isLoggable(MLevel.SEVERE))
                            logger.log( MLevel.SEVERE, "An unexpected Error (or other non-Exception Throwable) occurred while trying to produce a cacheable PreparedStatement.", t );
                        exceptionHolder[0] = t;
                    }
                    finally
                    {
                        mainLock.lock();
                        try
                        {
                            // JDBC drivers' statement producing methods must either return a Statement or
                            // throw an Exception. Returning null is out-of-spec. If that happens,
                            // we need to move on, and we need to see a Statement or an Exception in order
                            // to do that. Obviously, we are able only to supply an Exception.
                            if ( outHolder[0] == null && exceptionHolder[0] == null )
                                exceptionHolder[0] = new SQLException(
                                    "JDBC driver bug: " + stmtProducingMethod.getName() + "(...) returned null rather than a " +
                                    "Statement, and threw no Exception. [Connection class: " + pConn.getClass().getName() + "] " +
                                    "c3p0 cannot cache what it was not given." );
                            conditionStatementPerhapsAcquired.signalAll();
                        }
                        finally
                        { mainLock.unlock(); }
                    }
                }
            }

            Runnable r = new StmtAcquireTask();
            blockingTaskAsyncRunner.postRunnable(r);

            // be sure to handle any of the conditions that would break this while below!
            while ( outHolder[0] == null && exceptionHolder[0] == null && !this.isClosed())
                conditionStatementPerhapsAcquired.await(); //give up our lock while the Statement gets prepared
            Throwable t = exceptionHolder[0];
            if (t != null)
            {
                if (t instanceof Exception)
                    throw new SQLException("A problem occurred while trying to acquire a cached PreparedStatement in a background thread.", exceptionHolder[0] );
                else if (t instanceof Error)
                    throw (Error) t;
                else
                    throw new Error("Unexpected non-Error, non-Exception Throwable while trying to acquire a cached PreparedStatement in a background thread: " + t, t);
            }
            else if (outHolder[0] != null)
                return outHolder[0];
            else if (this.isClosed())
            {
                // ResourceClosedException are caught by Statement proxies, which fall back to an
                // uncached Statement -- so a Connection in use can survive a DataSource reset,
                // but just lose the Statement cache
                throw new ResourceClosedException("The Statement cache has been close()ed. It will no longer acquire Statements.");
            }
            else
                throw new RuntimeException("Unexpected state in GooGooStatementCache.acquireStatement(...), probably a programmer error, a new lock pass-through condition not handled.");
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

    private final static String PREPARED_STMT_WITH_BROKEN_EQUALITY_WARNING_MSG =
        "Apparent JDBC Driver Bug! PreparedStatement.equals(...) is improperly implemented (perhaps as a naive dynamic proxy?) " +
        "A PreparedStatement fails to equal itself! PreparedStatement caching will be pathological " +
        "under these circumstances. Please turn it off, set configuration parameters maxStatements and maxStatementsPerConnection both to 0.";

    private boolean removeFromCheckoutQueue( StatementCacheKey key, Object pstmt )
    {
        boolean out;
        LinkedList q = checkoutQueue( key );
        out = q.remove( pstmt );

        // apparently this happens with some versions of some drivers?
        // see https://github.com/swaldman/c3p0/pull/59/
        if (!out && !pstmt.equals(pstmt))
        {
            // LinkedList.remove(Object) is the one lookup in this class that a broken equals(...)
            // defeats -- HashMap and HashSet compare by identity first -- so we finish the job by
            // identity here. We must not throw: we are in the middle of removeStatement(...), and
            // leaving the cache half-updated is far worse than a badly behaved driver.
            for (Iterator ii = q.iterator(); ii.hasNext(); )
                if (ii.next() == pstmt)
                {
                    ii.remove();
                    out = true;
                    break;
                }
            if (logger.isLoggable(MLevel.WARNING))
                logger.warning(PREPARED_STMT_WITH_BROKEN_EQUALITY_WARNING_MSG); // same message, logged not thrown
        }
        if (q.isEmpty() && keySet( key ).isEmpty())
            keyToKeyRec.remove( key );
        return out;
    }

    private boolean ourResource( Object ps )
    { return stmtToKey.keySet().contains( ps ); }

    // any Exception forces removal
    private void refreshStatement( PreparedStatement ps ) throws Exception
    {
        if (!ps.isPoolable())
            throw new IrreversibleHazardException("Statement has been explicitly marked non-poolable.");
        Hazards hazards;
        synchronized (stmtToHazards)
        {
            hazards = _hazardsForStatement( ps, false );
            if (hazards != null)
            {
                hazards = hazards.snapshot();
                _clearHazardsForStatement( ps );
            }
        }
        if (hazards != null)
        {
            if (hazards.isCursorNameSet())
                throw new IrreversibleHazardException("Client set a cursor name. We have no means by which to restore Statement to initial, unset-cursor-name state.");
            if (hazards.isCloseOnCompletionSet())
                throw new IrreversibleHazardException("Client set closeOnCompletion(). We have no means by which to unset that value and restore the default non-auto-closing state.");
            if (hazards.isQueryTimeoutUpdated())
                ps.setQueryTimeout(hazards.getQueryTimeoutUpdatedFrom());
            if (hazards.isFetchDirectionUpdated())
                ps.setFetchDirection(hazards.getFetchDirectionUpdatedFrom());
            if (hazards.isFetchSizeUpdated())
                ps.setFetchSize(hazards.getFetchSizeUpdatedFrom());
            if (hazards.isMaxFieldSizeUpdated())
                ps.setMaxFieldSize(hazards.getMaxFieldSizeUpdatedFrom());
            if (hazards.isMaxRowsUpdated())
            {
                long initial = hazards.getMaxRowsUpdatedFrom();
                CarefulMaxRowsReaderWriter.writeMaxRows(ps, initial);
            }
        }
	ps.clearParameters();
	ps.clearBatch();
        ps.clearWarnings();
    }

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
	int in_use = destructo.countConnectionsInUse();
	if (in_use >= 0)
	    {
		sb.append("; num connections in use: ");
		sb.append(in_use);
	    }
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
            assert mainLock.isHeldByCurrentThread();

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
            assert mainLock.isHeldByCurrentThread();

            Long old = (Long) stmtsToLongs.remove( ps );
            if (Debug.DEBUG && old == null)
                throw new RuntimeException("Internal inconsistency: A (not new) checking-out statement is not in deathmarch.");
            Object check = longsToStmts.remove( old );
            if (Debug.DEBUG && check == null)
                throw new RuntimeException("Internal inconsistency: A deathmarch's stmtsToLongs and longsToStmts disagree.");
        }

        boolean cullNext()
        {
            assert mainLock.isHeldByCurrentThread();

            Object cullMeStmt = null;
            StatementCacheKey sck = null;

            if (CULL_ONLY_FROM_UNUSED_CONNECTIONS) //alternative implementation -- cull only from unused Connections
            {
                for (Iterator ii = longsToStmts.keySet().iterator(); cullMeStmt == null && ii.hasNext(); )
                {
                    Long l = (Long) ii.next();
                    Object maybeCullMe = longsToStmts.get( l );
                    StatementCacheKey maybeSck = (StatementCacheKey) stmtToKey.get( maybeCullMe );
                    if (maybeSck != null)
                    {
                        Connection pCon = maybeSck.physicalConnection;
                        if (! destructo.knownInUse( pCon ) ) //we don't cull Statements underneath of Connections in current use
                        {
                            // we've found the first statement in the deathmarch
                            // that we can cull...
                            cullMeStmt = maybeCullMe;
                            sck = maybeSck;
                        }
                    }
                }
            }
	    else //strict LRU culling
	    {
                // find the first available statement, if any, THAT IS NOT CHECKED OUT
                for( Object mbCullMe : longsToStmts.values() )
                {
                    if (!checkedOut.contains( mbCullMe ))
                    {
                        cullMeStmt = mbCullMe;
                        //System.err.println("Found statement to cull: " + cullMeStmt);
                        break;
                    }
                }
	    }

            if ( cullMeStmt == null ) // we didn't find a Statement we could cull
                return false;
            else
            {
                if (sck == null)
                    sck = ((StatementCacheKey) stmtToKey.get(cullMeStmt));
                if (sck == null) // this shouldn't happen, but very rarely for some deployments it apparently does
                {
                    // A deathmarched Statement the cache no longer knows. Removing it is hopeless --
                    // removeStatement(...) looks it up by key -- so drop it from this deathmarch
                    // rather than returning to it on every future cull.
                    if (logger.isLoggable(MLevel.WARNING))
                        logger.log(MLevel.WARNING,
                                   this + " found a Statement in a deathmarch that the cache no longer holds: " +
                                   cullMeStmt + ". Dropping it from the deathmarch. Please report this.",
                                   new Exception("LOG STACK TRACE"));
                    undeathmarchStatement( cullMeStmt );
                    return cullNext(); // no Statement was actually freed, so try the next candidate
                                       // -- terminates, since each call drops one deathmarch entry
                }
                if (Debug.DEBUG && Debug.TRACE == Debug.TRACE_MAX)
                {
                    if (logger.isLoggable(MLevel.FINEST))
                        logger.finest("CULLING: " + sck.stmtText);
                }

                // we do not undeathmarch the statement ourselves, because removeStatement( ... )
                // should remove from all deathmarches...
                removeStatement( cullMeStmt, DESTROY_ALWAYS );
                if (Debug.DEBUG && this.contains( cullMeStmt ))
                    throw new RuntimeException("Inconsistency!!! Statement culled from deathmarch failed to be removed by removeStatement( ... )!");

                return true;
            }
        }


        /*
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
        */

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

        public HashSet statementSet( Connection pcon )
        { return (HashSet) cxnToStmtSets.get( pcon ); }

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

    /*
     * Some drivers cannot abide the close()ing of a Statement while the parent Connection object is
     * in use elsewhere. For those driver, CautiosStatementDestructionManager should be used. For
     * drivers that do not have this issue [formally close()ing a Statement while a Connection is in
     * use should be within spec], best to default to the faster, simpler IncautiousStatementDestructionManager
     */
    private abstract class StatementDestructionManager
    {
	AsynchronousRunner runner;

	//{ System.err.println("Statement cache destruction manager: " + this.getClass().getName()); }

	StatementDestructionManager(AsynchronousRunner runner)
	{ this.runner = runner; }

	abstract void waitMarkConnectionInUse(Connection physicalConnection) throws InterruptedException;
	abstract boolean tryMarkConnectionInUse(Connection physicalConnection);
	abstract void unmarkConnectionInUse(Connection physicalConnection);
	abstract void deferredDestroyStatement(Object parentConnection, Object pstmt);

	// return -1 if unknown
	abstract int countConnectionsInUse();

	//we don't cull Statements underneat of Connections in current use
	abstract boolean knownInUse(Connection pCon);

	// to enable assertions about a Connection's status
	// returns null if unknown.
	//
	// "three-valued logic in use"
	abstract Boolean tvlInUse( Connection pCon );

	abstract int getNumConnectionsInUse();
	abstract int getNumConnectionsWithDeferredDestroyStatements();
	abstract int getNumDeferredDestroyStatements();

	abstract void close();

	/* non-public methods that needn't be called with any lock below */

        void cancelClose( PreparedStatement pstmt )
        {
            try
            {
                if ( cancelAutomaticallyClosedStatements )
                {
                    boolean trace = Debug.DEBUG && logger.isLoggable( MLevel.FINEST );
                    if (trace) logger.log( MLevel.FINEST, "Canceling cached statement prior to autoclose.");
                    pstmt.cancel();
                    if (trace) logger.log( MLevel.FINEST, "Canceled cached statement prior to autoclose." );
                }
            }
            catch ( SQLException e )
            {
                if ( Debug.DEBUG && logger.isLoggable( MLevel.FINER ) )
                    logger.log( MLevel.FINER,
                                "An Exception occurred while trying to cancel an unclosed Statement we are about to close. " +
                                "(Because cancelAutomaticallyClosedStatements is set to true, we make one best-effort attempt to cancel. No action is necessary.)",
                                e );
            }
            finally
            { StatementUtils.attemptClose( (PreparedStatement) pstmt ); }
        }

	final void uncheckedDestroyStatement( final Object pstmt )
	{
	    class UncheckedStatementCloseTask implements Runnable
	    {
		public void run()
		{ cancelClose( (PreparedStatement) pstmt ); }
	    }

	    Runnable r = new UncheckedStatementCloseTask();

	    runner.postRunnable(r);
	}

	final void synchronousDestroyStatement( final Object pstmt )
	{ cancelClose( (PreparedStatement) pstmt ); }

	/* end non-public methods that needn't be called with any lock */

    }

    private final class IncautiousStatementDestructionManager extends StatementDestructionManager
    {
	IncautiousStatementDestructionManager(AsynchronousRunner runner)
	{ super( runner ); }

	void waitMarkConnectionInUse(Connection physicalConnection) throws InterruptedException {}
	boolean tryMarkConnectionInUse(Connection physicalConnection) { return true; }
	void unmarkConnectionInUse(Connection physicalConnection) {}
	void deferredDestroyStatement(Object parentConnection, Object pstmt) { uncheckedDestroyStatement( pstmt ); }
	void close() {}

	// return -1 if unknown
	int countConnectionsInUse() { return -1; }

	// under alternative implementation we don't cull Statements
	// underneath of Connections in current use
	boolean knownInUse(Connection pCon) { return false; }

	Boolean tvlInUse( Connection pCon ) { return null; }

	int getNumConnectionsInUse() { return -1; }
	int getNumConnectionsWithDeferredDestroyStatements() { return -1; }
	int getNumDeferredDestroyStatements() { return -1; }
    }

    private final class CautiousStatementDestructionManager extends StatementDestructionManager
    {
	// we refuse to cull PreparedStatements from currently in-use Connections
	// to avoid potential concurrency issues in drivers not robust to concurrent
	// use of children of a single Connection.
	HashSet inUseConnections = new HashSet();

    	// This Map is used to keep track of Statements removed
	// from the cache and "closed", but not actually, physically
	// close()ed yet, because when they were removed from the cache,
	// their parent Connection was in-use. Under some JDBC drivers,
	// (Oracle, this means you), trying to close() a Statement whose
	// parent Connection is concurrently in use leads to problems.
	// So, we just remove these Statements from the cache, and shove
	// them into a "TO BE DESTROYED" list. Only when the parent
	// Connection is no longer in use do we actually destroy the removed
	// Statements.
	HashMap connectionsToZombieStatementSets = new HashMap();

	AsynchronousRunner deferredStatementDestroyer;

	boolean closed = false;

        final ReentrantLock csdmLock = new ReentrantLock();
        final Condition statementsMaybeDestroyed = csdmLock.newCondition();

	void close()
	{
            csdmLock.lock();
            try { closed = true; }
            finally { csdmLock.unlock(); }
        }

	CautiousStatementDestructionManager(AsynchronousRunner deferredStatementDestroyer)
	{
	    super( deferredStatementDestroyer );
	    this.deferredStatementDestroyer = deferredStatementDestroyer;
	}

	private String trace()
	{
	    Set keys = connectionsToZombieStatementSets.keySet();
	    int sum = 0;
	    for ( Iterator ii = keys.iterator(); ii.hasNext(); )
		{
		    Object con = ii.next();
		    Set stmts = (Set) connectionsToZombieStatementSets.get( con );
		    synchronized ( stmts )
			{ sum += (stmts == null ? 0 : stmts.size()); }
		}
	    return this.getClass().getName() + " [connections in use: " + inUseConnections.size() + "; connections with deferred statements: " + keys.size() + "; statements to destroy: " + sum + "]";
	}

	private void printAllStats()
	{
	    printStats();
	    System.err.println(trace());
	}

	void waitMarkConnectionInUse(Connection physicalConnection) throws InterruptedException
	{
            csdmLock.lock();
            try
            {
                if (! closed)
                    {
                        Set stmts = statementsUnderDestruction( physicalConnection );
                        if (stmts != null)
                            {
                                if (Debug.DEBUG && logger.isLoggable(MLevel.FINE))
                                    {

                                        //System.err.println("*********************************************** => " + stmts);

                                        logger.log(MLevel.FINE,
                                                   "A connection is waiting to be accepted by the Statement cache because " +
                                                   stmts.size() +
                                                   " cached Statements are still being destroyed.");
                                        //printAllStats();
                                    }
                                while (! stmts.isEmpty())
                                    statementsMaybeDestroyed.await();
                            }
                        inUseConnections.add( physicalConnection );
                    }
            }
            finally
            { csdmLock.unlock(); }
	}

	boolean tryMarkConnectionInUse(Connection physicalConnection)
	{
            csdmLock.lock();
            try
            {
                if (! closed)
                    {
                        Set stmts = statementsUnderDestruction( physicalConnection );
                        if ( stmts != null)
                            {
                                int sz = stmts.size();
                                if (Debug.DEBUG && logger.isLoggable(MLevel.FINE))
                                    {
                                        logger.log(MLevel.FINE,
                                                   "A connection could not be accepted by the Statement cache because " +
                                                   sz +
                                                   " cached Statements are still being destroyed.");
                                    }
                                return false;
                            }
                        else
                            {
                                inUseConnections.add( physicalConnection );
                                return true;
                            }
                    }
                else
                    return true;
            }
            finally
            { csdmLock.unlock(); }
	}

	void unmarkConnectionInUse(Connection physicalConnection)
	{
            csdmLock.lock();
            try
            {
                boolean unmarked = inUseConnections.remove( physicalConnection );

                Set zombieStatements = (Set) connectionsToZombieStatementSets.get( physicalConnection );

                if ( zombieStatements != null )
                    {
                        //System.err.println("zombieStatements: " + zombieStatements);
                        destroyAllTrackedStatements( physicalConnection );
                    }
            }
            finally
            { csdmLock.unlock(); }
	}

	void deferredDestroyStatement(Object parentConnection, Object pstmt)
	{
            csdmLock.lock();
            try
            {
                if (! closed)
                    {
                        if (inUseConnections.contains(parentConnection))
                            {
                                Set s = (Set) connectionsToZombieStatementSets.get(parentConnection);
                                if (s == null)
                                    {
                                        s = Collections.synchronizedSet( new HashSet() );
                                        connectionsToZombieStatementSets.put(parentConnection, s);
                                    }
                                s.add( pstmt );
                            }
                        else
                            {
                                uncheckedDestroyStatement( pstmt );
                            }
                    }
                else
                    uncheckedDestroyStatement( pstmt );
            }
            finally
            { csdmLock.unlock(); }
	}

	// return -1 if unknown
	int countConnectionsInUse()
        {
            csdmLock.lock();
	    try { return inUseConnections.size(); }
            finally { csdmLock.unlock(); }
        }

	// under alternative implementation we don't cull Statements
	// underneath of Connections in current use
	boolean knownInUse(Connection pCon)
        {
            csdmLock.lock();
            try { return inUseConnections.contains(pCon); }
            finally { csdmLock.unlock(); }
        }

	// we don't sync 'cuz we're just wrapping
	// a sync'ed method
	Boolean tvlInUse( Connection pCon )
	{ return Boolean.valueOf( knownInUse( pCon ) ); }

	int getNumConnectionsInUse()
        {
            csdmLock.lock();
            try { return inUseConnections.size(); }
            finally { csdmLock.unlock(); }
        }

	int getNumConnectionsWithDeferredDestroyStatements()
        {
            csdmLock.lock();
            try { return connectionsToZombieStatementSets.keySet().size(); }
            finally { csdmLock.unlock(); }
        }

        int getNumDeferredDestroyStatements()
	{
            csdmLock.lock();
            try
            {
                Set keys = connectionsToZombieStatementSets.keySet();
                int sum = 0;
                for ( Iterator ii = keys.iterator(); ii.hasNext(); )
                    {
                        Object con = ii.next();
                        Set stmts = (Set) connectionsToZombieStatementSets.get( con );
                        synchronized ( stmts )
                            { sum += (stmts == null ? 0 : stmts.size()); }
                    }
                return sum;
            }
            finally
            { csdmLock.unlock(); }
	}

	private void trackedDestroyStatement( final Object parentConnection, final Object pstmt )
	{
	    //System.err.println("trackedDestroyStatement()");

	    final class TrackedStatementCloseTask implements Runnable
	    {
		public void run()
		{
		    // debug
		    //System.err.println("TrackedStatementCloseTask.run()");

                    csdmLock.lock();
		    try
                    {
                        //System.err.println("TrackedStatementCloseTask -- with lock");

                        final Set stmts = (Set) connectionsToZombieStatementSets.get( parentConnection );
                        if ( stmts != null )
                            {
                                StatementUtils.attemptClose( (PreparedStatement) pstmt );
                                //System.err.println( "Closed tracked statement: " + pstmt);
                                boolean removed1 = stmts.remove( pstmt );
                                assert removed1;
                                if (stmts.isEmpty())
                                    {
                                        Object removed2 = connectionsToZombieStatementSets.remove( parentConnection );
                                        //assert removed2 != null;
                                        assert removed2 == stmts;
                                        statementsMaybeDestroyed.signalAll();
                                        //System.err.println( "Notify -- all statements closed after close tracked statements: " + pstmt);
                                    }
                                //else
                                //System.err.println("Statements remaining open -- " + stmts);
                            }
                    }
                    finally
                    { csdmLock.unlock(); }

		    //printAllStats();
		}
	    }

	    Runnable r = new TrackedStatementCloseTask();

	    if (! closed)
		{
		    //blockingTaskAsyncRunner.postRunnable(r);
		    deferredStatementDestroyer.postRunnable(r);
		    //System.err.println("trackedDestroyStatement() -- posted task.");
		}
	    else
		{
		    r.run();
		    //System.err.println("trackedDestroyStatement() -- closed, so destroying synchronously.");
		}
	}

        // always called with csdmLock
	private void destroyAllTrackedStatements( final Object parentConnection )
	{
	    //System.err.println("destroyAllTrackedStatements()");

	    final class TrackedDestroyAllStatementsTask implements Runnable
	    {
		public void run()
		{
		    // debug
		    //System.err.println("TrackedDestroyAllStatementsTask.run()");

                    csdmLock.lock();
		    try
                    {
                        //System.err.println("TrackedDestroyAllStatementsTask -- with lock");

                        final Set stmts = (Set) connectionsToZombieStatementSets.remove( parentConnection );
                        if (stmts != null)
                            {
                                //System.err.println("TrackedDestroyAllStatementsTask -- to destroy " + stmts);
                                for(Iterator ii = stmts.iterator(); ii.hasNext(); )
                                    {
                                        PreparedStatement pstmt = (PreparedStatement) ii.next();
                                        StatementUtils.attemptClose( pstmt );
                                        ii.remove();
                                    }
                                statementsMaybeDestroyed.signalAll();
                                //System.err.println( "Notify -- closed all tracked statements." );
                            }
                        //else
                        //System.err.println("No statements for Connection " + parentConnection + "; with connections: " + connectionsToZombieStatementSets.keySet());
                    }
                    finally
                    { csdmLock.unlock(); }

		    //printAllStats();
		}
	    }

	    Runnable r = new TrackedDestroyAllStatementsTask();

	    if (! closed)
		{
		    //blockingTaskAsyncRunner.postRunnable(r);
		    deferredStatementDestroyer.postRunnable(r);
		    //System.err.println("destroyAllTrackedStatements() -- posted task.");
		}
	    else
		{
		    r.run();
		    //System.err.println("destroyAllTrackedStatements() -- closed, so destroying synchronously.");
		}
	}

	private Set statementsUnderDestruction( Object parentConnection )
	{
	    assert csdmLock.isHeldByCurrentThread();

	    return (Set) connectionsToZombieStatementSets.get( parentConnection );
	}
    }
}
