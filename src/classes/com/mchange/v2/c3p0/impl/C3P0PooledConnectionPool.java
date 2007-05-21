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


package com.mchange.v2.c3p0.impl;

import com.mchange.v2.c3p0.stmt.*;
import com.mchange.v2.c3p0.ConnectionCustomizer;
import com.mchange.v2.c3p0.SQLWarnings;
import com.mchange.v2.c3p0.UnifiedConnectionTester;
import com.mchange.v2.c3p0.WrapperConnectionPoolDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.util.LinkedList;

import javax.sql.ConnectionEvent;
import javax.sql.ConnectionEventListener;
import javax.sql.ConnectionPoolDataSource;
import javax.sql.PooledConnection;

import com.mchange.v1.db.sql.ConnectionUtils;
import com.mchange.v2.async.AsynchronousRunner;
import com.mchange.v2.async.ThreadPoolAsynchronousRunner;
import com.mchange.v2.log.MLevel;
import com.mchange.v2.log.MLog;
import com.mchange.v2.log.MLogger;
import com.mchange.v2.c3p0.ConnectionTester;
import com.mchange.v2.c3p0.QueryConnectionTester;
import com.mchange.v2.resourcepool.CannotAcquireResourceException;
import com.mchange.v2.resourcepool.ResourcePool;
import com.mchange.v2.resourcepool.ResourcePoolException;
import com.mchange.v2.resourcepool.ResourcePoolFactory;
import com.mchange.v2.resourcepool.TimeoutException;
import com.mchange.v2.sql.SqlUtils;

public final class C3P0PooledConnectionPool
{
    private final static boolean ASYNCHRONOUS_CONNECTION_EVENT_LISTENER = false;

    private final static Throwable[] EMPTY_THROWABLE_HOLDER = new Throwable[1];

    final static MLogger logger = MLog.getLogger( C3P0PooledConnectionPool.class );

    final ResourcePool rp;
    final ConnectionEventListener cl = new ConnectionEventListenerImpl();

    final ConnectionTester     connectionTester;
    final GooGooStatementCache scache;

    final int checkoutTimeout;

    final AsynchronousRunner sharedTaskRunner;

    final ThrowableHolderPool thp = new ThrowableHolderPool();

    C3P0PooledConnectionPool( final ConnectionPoolDataSource cpds,
                    final DbAuth auth,
                    int min, 
                    int max, 
                    int start,
                    int inc,
                    int acq_retry_attempts,
                    int acq_retry_delay,
                    boolean break_after_acq_failure,
                    int checkoutTimeout, //milliseconds
                    int idleConnectionTestPeriod, //seconds
                    int maxIdleTime, //seconds
                    int maxIdleTimeExcessConnections, //seconds
                    int maxConnectionAge, //seconds
                    int propertyCycle, //seconds
                    int unreturnedConnectionTimeout, //seconds
                    boolean debugUnreturnedConnectionStackTraces,
                    final boolean testConnectionOnCheckout,
                    final boolean testConnectionOnCheckin,
                    int maxStatements,
                    int maxStatementsPerConnection,
                    final ConnectionTester connectionTester,
                    final ConnectionCustomizer connectionCustomizer,
                    final String testQuery,
                    final ResourcePoolFactory fact,
                    ThreadPoolAsynchronousRunner taskRunner,
                    final String parentDataSourceIdentityToken) throws SQLException
                    {
        try
        {
            if (maxStatements > 0 && maxStatementsPerConnection > 0)
                this.scache = new DoubleMaxStatementCache( taskRunner, maxStatements, maxStatementsPerConnection );
            else if (maxStatementsPerConnection > 0)
                this.scache = new PerConnectionMaxOnlyStatementCache( taskRunner, maxStatementsPerConnection );
            else if (maxStatements > 0)
                this.scache = new GlobalMaxOnlyStatementCache( taskRunner, maxStatements );
            else
                this.scache = null;

            this.connectionTester = connectionTester;

            this.checkoutTimeout = checkoutTimeout;

            this.sharedTaskRunner = taskRunner;

            class PooledConnectionResourcePoolManager implements ResourcePool.Manager
            {	
                //SynchronizedIntHolder totalOpenedCounter  = new SynchronizedIntHolder();
                //SynchronizedIntHolder connectionCounter   = new SynchronizedIntHolder();
                //SynchronizedIntHolder failedCloseCounter  = new SynchronizedIntHolder();

                final boolean connectionTesterIsDefault = (connectionTester instanceof DefaultConnectionTester);
                final boolean c3p0PooledConnections = (cpds instanceof WrapperConnectionPoolDataSource);

                public Object acquireResource() throws Exception
                { 
                    PooledConnection out;

                    if ( connectionCustomizer == null)
                    {
                        out = (auth.equals( C3P0ImplUtils.NULL_AUTH ) ?
                               cpds.getPooledConnection() :
                               cpds.getPooledConnection( auth.getUser(), 
                                                         auth.getPassword() ) );
                    }
                    else
                    {
                        try
                        { 
                            WrapperConnectionPoolDataSourceBase wcpds = (WrapperConnectionPoolDataSourceBase) cpds;

                            out = (auth.equals( C3P0ImplUtils.NULL_AUTH ) ?
                                   wcpds.getPooledConnection( connectionCustomizer, parentDataSourceIdentityToken ) :
                                   wcpds.getPooledConnection( auth.getUser(), 
                                                              auth.getPassword(),
                                                              connectionCustomizer, parentDataSourceIdentityToken ) );
                        }
                        catch (ClassCastException e)
                        {
                            throw SqlUtils.toSQLException("Cannot use a ConnectionCustomizer with a non-c3p0 ConnectionPoolDataSource." +
                                            " ConnectionPoolDataSource: " + cpds.getClass().getName(), e);
                        }
                    }

                    //connectionCounter.increment(); 
                    //totalOpenedCounter.increment();

                    try
                    {
                        if (scache != null)
                        {
                            if (c3p0PooledConnections)
                                ((AbstractC3P0PooledConnection) out).initStatementCache(scache);
                            else
                            {
                                // System.err.print("Warning! StatementPooling not ");
                                // System.err.print("implemented for external (non-c3p0) ");
                                // System.err.println("ConnectionPoolDataSources.");

                                logger.warning("StatementPooling not " +
                                                "implemented for external (non-c3p0) " +
                                "ConnectionPoolDataSources.");
                            }
                        }
                        
                        // log and clear any SQLWarnings present upon acquisition
                        Connection con = null;
                        try
                        {
                            con = out.getConnection();
                            SQLWarnings.logAndClearWarnings( con );
                        }
                        finally
                        {
                            //invalidate the proxy Connection
                            ConnectionUtils.attemptClose( con );
                        }
                        
                        out.addConnectionEventListener( cl );
                        return out;
                    }
                    catch (Exception e)
                    {
                        if (logger.isLoggable( MLevel.WARNING ))
                            logger.warning("A PooledConnection was acquired, but an Exception occurred while preparing it for use. " +
                            "Attempting to destroy.");
                        try { destroyResource( out ); }
                        catch (Exception e2)
                        {
                            if (logger.isLoggable( MLevel.WARNING ))
                                logger.log( MLevel.WARNING, 
                                                "An Exception occurred while trying to close partially acquired PooledConnection.",
                                                e2 );
                        }

                        throw e;
                    }
                    finally
                    {
                        if (logger.isLoggable( MLevel.FINEST ))
                            logger.finest(this + ".acquireResource() returning. " );
                        //"Currently open Connections: " + connectionCounter.getValue() +
                        //"; Failed close count: " + failedCloseCounter.getValue() +
                        //"; Total processed by this pool: " + totalOpenedCounter.getValue());
                    }
                }

                // REFURBISHMENT:
                // the PooledConnection refurbishes itself when 
                // its Connection view is closed, prior to being
                // checked back in to the pool. But we still may want to
                // test to make sure it is still good.

                public void refurbishResourceOnCheckout( Object resc ) throws Exception
                {
                    if ( testConnectionOnCheckout )
                    {
                        if ( logger.isLoggable( MLevel.FINER ) )
                            finerLoggingTestPooledConnection( resc, "CHECKOUT" );
                        else
                            testPooledConnection( resc );
                    }
                    if ( connectionCustomizer != null )
                    {
                        Connection physicalConnection = null;
                        try
                        { 
                            physicalConnection =  ((AbstractC3P0PooledConnection) resc).getPhysicalConnection();
                            connectionCustomizer.onCheckOut( physicalConnection, parentDataSourceIdentityToken );
                        }
                        catch (ClassCastException e)
                        {
                            throw SqlUtils.toSQLException("Cannot use a ConnectionCustomizer with a non-c3p0 PooledConnection." +
                                            " PooledConnection: " + resc + 
                                            "; ConnectionPoolDataSource: " + cpds.getClass().getName(), e);
                        }
                    }
                }

                public void refurbishResourceOnCheckin( Object resc ) throws Exception
                {
                    if ( connectionCustomizer != null )
                    {
                        Connection physicalConnection = null;
                        try
                        { 
                            physicalConnection =  ((AbstractC3P0PooledConnection) resc).getPhysicalConnection();
                            connectionCustomizer.onCheckIn( physicalConnection, parentDataSourceIdentityToken );
                            SQLWarnings.logAndClearWarnings( physicalConnection );
                        }
                        catch (ClassCastException e)
                        {
                            throw SqlUtils.toSQLException("Cannot use a ConnectionCustomizer with a non-c3p0 PooledConnection." +
                                            " PooledConnection: " + resc + 
                                            "; ConnectionPoolDataSource: " + cpds.getClass().getName(), e);
                        }
                    }
                    else
                    {
                        PooledConnection pc = (PooledConnection) resc;
                        Connection con = null;

                        try
                        {
                            //we don't want any callbacks while we're clearing warnings
                            pc.removeConnectionEventListener( cl );

                            con = pc.getConnection();
                            SQLWarnings.logAndClearWarnings(con);
                        }
                        finally
                        {
                            // close the proxy Connection
                            ConnectionUtils.attemptClose(con);
                            
                            pc.addConnectionEventListener( cl );
                        }
                    }
                    
                    if ( testConnectionOnCheckin )
                    { 
                        if ( logger.isLoggable( MLevel.FINER ) )
                            finerLoggingTestPooledConnection( resc, "CHECKIN" );
                        else
                            testPooledConnection( resc );
                    }
                }

                public void refurbishIdleResource( Object resc ) throws Exception
                { 
                    if ( logger.isLoggable( MLevel.FINER ) )
                        finerLoggingTestPooledConnection( resc, "IDLE CHECK" );
                    else
                        testPooledConnection( resc );
                }

                private void finerLoggingTestPooledConnection(Object resc, String testImpetus) throws Exception
                {
                    logger.finer("Testing PooledConnection [" + resc + "] on " + testImpetus + ".");
                    try
                    {
                        testPooledConnection( resc );
                        logger.finer("Test of PooledConnection [" + resc + "] on "+testImpetus+" has SUCCEEDED.");
                    }
                    catch (Exception e)
                    {
                        logger.log(MLevel.FINER, "Test of PooledConnection [" + resc + "] on "+testImpetus+" has FAILED.", e);
                        e.fillInStackTrace();
                        throw e;
                    }
                }

                private void testPooledConnection(Object resc) throws Exception
                { 
                    PooledConnection pc = (PooledConnection) resc;

                    Throwable[] throwableHolder = EMPTY_THROWABLE_HOLDER;
                    int status;
                    Connection conn = null;
                    Throwable rootCause = null;
                    try	
                    { 
                        //we don't want any callbacks while we're testing the resource
                        pc.removeConnectionEventListener( cl );

                        conn = pc.getConnection(); //checkout proxy connection

                        // if this is a c3p0 pooled-connection, let's get underneath the
                        // proxy wrapper, and test the physical connection sometimes. 
                        // this is faster, when the testQuery would not otherwise be cached,
                        // and it avoids a potential statusOnException() double-check by the
                        // PooledConnection implementation should the test query provoke an
                        // Exception
                        Connection testConn;
                        if (scache != null) //when there is a statement cache...
                        {
                            // if it's the slow, default query, faster to test the raw Connection 
                            if (testQuery == null && connectionTesterIsDefault && c3p0PooledConnections)
                                testConn = ((AbstractC3P0PooledConnection) pc).getPhysicalConnection();
                            else //test will likely be faster on the proxied Connection, because the test query is probably cached
                                testConn = conn; 
                        }
                        else //where there's no statement cache, better to use the physical connection, if we can get it
                        {
                            if (c3p0PooledConnections)
                                testConn = ((AbstractC3P0PooledConnection) pc).getPhysicalConnection();
                            else    
                                testConn = conn;
                        }

                        if ( testQuery == null )
                            status = connectionTester.activeCheckConnection( testConn );
                        else
                        {
                            if (connectionTester instanceof UnifiedConnectionTester)
                            {
                                throwableHolder = thp.getThrowableHolder();
                                status = ((UnifiedConnectionTester) connectionTester).activeCheckConnection( testConn, testQuery, throwableHolder );
                            }
                            else if (connectionTester instanceof QueryConnectionTester)
                                status = ((QueryConnectionTester) connectionTester).activeCheckConnection( testConn, testQuery );
                            else
                            {
                                // System.err.println("[c3p0] WARNING: testQuery '" + testQuery +
                                // "' ignored. Please set a ConnectionTester that implements " +
                                // "com.mchange.v2.c3p0.advanced.QueryConnectionTester, or use the " +
                                // "DefaultConnectionTester, to test with the testQuery.");

                                logger.warning("[c3p0] testQuery '" + testQuery +
                                                "' ignored. Please set a ConnectionTester that implements " +
                                                "com.mchange.v2.c3p0.QueryConnectionTester, or use the " +
                                "DefaultConnectionTester, to test with the testQuery.");
                                status = connectionTester.activeCheckConnection( testConn );
                            }
                        }
                    }
                    catch (Exception e)
                    {
                        if (Debug.DEBUG)
                            logger.log(MLevel.FINE, "A Connection test failed with an Exception.", e);
                        //e.printStackTrace();
                        status = ConnectionTester.CONNECTION_IS_INVALID;
//                      System.err.println("rootCause ------>");
//                      e.printStackTrace();
                        rootCause = e;
                    }
                    finally
                    { 
                        if (rootCause == null)
                            rootCause = throwableHolder[0];
                        else if (throwableHolder[0] != null && logger.isLoggable(MLevel.FINE))
                            logger.log(MLevel.FINE, "Internal Connection Test Exception", throwableHolder[0]);
                        
                        if (throwableHolder != EMPTY_THROWABLE_HOLDER)
                            thp.returnThrowableHolder( throwableHolder );
                        
                        ConnectionUtils.attemptClose( conn ); //invalidate proxy connection
                        pc.addConnectionEventListener( cl );  //should we move this to CONNECTION_IS_OKAY case? (it should work either way)
                    }

                    switch (status)
                    {
                    case ConnectionTester.CONNECTION_IS_OKAY:
                        break; //no problem, babe
                    case ConnectionTester.DATABASE_IS_INVALID:
                        rp.resetPool();
                        //intentional cascade...
                    case ConnectionTester.CONNECTION_IS_INVALID:
                        Exception throwMe;
                        if (rootCause == null)
                            throwMe = new SQLException("Connection is invalid");
                        else
                            throwMe = SqlUtils.toSQLException("Connection is invalid", rootCause);
                        throw throwMe;
                    default:
                        throw new Error("Bad Connection Tester (" + 
                                        connectionTester + ") " +
                                        "returned invalid status (" + status + ").");
                    }
                }

                public void destroyResource(Object resc) throws Exception
                { 
                    try
                    {
                        if ( connectionCustomizer != null )
                        {
                            Connection physicalConnection = null;
                            try
                            { 
                                physicalConnection =  ((AbstractC3P0PooledConnection) resc).getPhysicalConnection();
                                connectionCustomizer.onDestroy( physicalConnection, parentDataSourceIdentityToken );
                            }
                            catch (ClassCastException e)
                            {
                                throw SqlUtils.toSQLException("Cannot use a ConnectionCustomizer with a non-c3p0 PooledConnection." +
                                                " PooledConnection: " + resc + 
                                                "; ConnectionPoolDataSource: " + cpds.getClass().getName(), e);
                            }
                            catch (Exception e)
                            {
                                if (logger.isLoggable( MLevel.WARNING ))
                                    logger.log( MLevel.WARNING,
                                                    "An exception occurred while executing the onDestroy() method of " + connectionCustomizer +
                                                    ". c3p0 will attempt to destroy the target Connection regardless, but this issue " +
                                                    " should be investigated and fixed.",
                                                    e );
                            }
                        }

                        if (Debug.DEBUG && Debug.TRACE == Debug.TRACE_MAX && logger.isLoggable( MLevel.FINER ))
                            logger.log( MLevel.FINER, "Preparing to destroy PooledConnection: " + resc);

                        ((PooledConnection) resc).close();

                        // inaccurate, as Connections can be removed more than once
                        //connectionCounter.decrement();

                        if (Debug.DEBUG && Debug.TRACE == Debug.TRACE_MAX && logger.isLoggable( MLevel.FINER ))
                            logger.log( MLevel.FINER, 
                                            "Successfully destroyed PooledConnection: " + resc );
                        //". Currently open Connections: " + connectionCounter.getValue() +
                        //"; Failed close count: " + failedCloseCounter.getValue() +
                        //"; Total processed by this pool: " + totalOpenedCounter.getValue());
                    }
                    catch (Exception e)
                    {
                        //failedCloseCounter.increment();

                        if (Debug.DEBUG && Debug.TRACE == Debug.TRACE_MAX && logger.isLoggable( MLevel.FINER ))
                            logger.log( MLevel.FINER, "Failed to destroy PooledConnection: " + resc );
                        //". Currently open Connections: " + connectionCounter.getValue() +
                        //"; Failed close count: " + failedCloseCounter.getValue() +
                        //"; Total processed by this pool: " + totalOpenedCounter.getValue());

                        throw e;
                    }
                }
            }

            ResourcePool.Manager manager = new PooledConnectionResourcePoolManager();

            synchronized (fact)
            {
                fact.setMin( min );
                fact.setMax( max );
                fact.setStart( start );
                fact.setIncrement( inc );
                fact.setIdleResourceTestPeriod( idleConnectionTestPeriod * 1000);
                fact.setResourceMaxIdleTime( maxIdleTime * 1000 );
                fact.setExcessResourceMaxIdleTime( maxIdleTimeExcessConnections * 1000 );
                fact.setResourceMaxAge( maxConnectionAge * 1000 );
                fact.setExpirationEnforcementDelay( propertyCycle * 1000 );
                fact.setDestroyOverdueResourceTime( unreturnedConnectionTimeout * 1000 );
                fact.setDebugStoreCheckoutStackTrace( debugUnreturnedConnectionStackTraces );
                fact.setAcquisitionRetryAttempts( acq_retry_attempts );
                fact.setAcquisitionRetryDelay( acq_retry_delay );
                fact.setBreakOnAcquisitionFailure( break_after_acq_failure );
                rp = fact.createPool( manager );
            }
        }
        catch (ResourcePoolException e)
        { throw SqlUtils.toSQLException(e); }
                    }

    public PooledConnection checkoutPooledConnection() throws SQLException
    { 
        //System.err.println(this + " -- CHECKOUT");
        try { return (PooledConnection) rp.checkoutResource( checkoutTimeout ); }
        catch (TimeoutException e)
        { throw SqlUtils.toSQLException("An attempt by a client to checkout a Connection has timed out.", e); }
        catch (CannotAcquireResourceException e)
        { throw SqlUtils.toSQLException("Connections could not be acquired from the underlying database!", "08001", e); }
        catch (Exception e)
        { throw SqlUtils.toSQLException(e); }
    }

    public void checkinPooledConnection(PooledConnection pcon) throws SQLException
    { 
        //System.err.println(this + " -- CHECKIN");
        try { rp.checkinResource( pcon ); } 
        catch (ResourcePoolException e)
        { throw SqlUtils.toSQLException(e); }
    }

    public float getEffectivePropertyCycle() throws SQLException
    {
        try
        { return rp.getEffectiveExpirationEnforcementDelay() / 1000f; }
        catch (ResourcePoolException e)
        { throw SqlUtils.toSQLException(e); }
    }

    public int getNumThreadsAwaitingCheckout() throws SQLException
    {
        try
        { return rp.getNumCheckoutWaiters(); }
        catch (ResourcePoolException e)
        { throw SqlUtils.toSQLException(e); }
    }

    public int getStatementCacheNumStatements()
    { return scache == null ? 0 : scache.getNumStatements(); }

    public int getStatementCacheNumCheckedOut()
    { return scache == null ? 0 : scache.getNumStatementsCheckedOut(); }

    public int getStatementCacheNumConnectionsWithCachedStatements()
    { return scache == null ? 0 : scache.getNumConnectionsWithCachedStatements(); }

    public String dumpStatementCacheStatus()
    { return scache == null ? "Statement caching disabled." : scache.dumpStatementCacheStatus(); }

    public void close() throws SQLException
    { close( true ); }

    public void close( boolean close_outstanding_connections ) throws SQLException
    { 
        // System.err.println(this + " closing.");
        Exception throwMe = null;

        try { if (scache != null) scache.close(); }
        catch (SQLException e)
        { throwMe = e; }

        try 
        { rp.close( close_outstanding_connections ); }
        catch (ResourcePoolException e)
        {
            if ( throwMe != null && logger.isLoggable( MLevel.WARNING ) )
                logger.log( MLevel.WARNING, "An Exception occurred while closing the StatementCache.", throwMe);
            throwMe = e; 
        }

        if (throwMe != null)
            throw SqlUtils.toSQLException( throwMe );
    }

    class ConnectionEventListenerImpl implements ConnectionEventListener
    {

        //
        // We might want to check Connections in asynchronously, 
        // because this is called
        // (indirectly) from a sync'ed method of NewPooledConnection, but
        // NewPooledConnection may be closed synchronously from a sync'ed
        // method of the resource pool, leading to a deadlock. Checking
        // Connections in asynchronously breaks the cycle.
        //
        // But then we want checkins to happen quickly and reliably,
        // whereas pool shutdowns are rare, so perhaps it's best to
        // leave this synchronous, and let the closing of pooled
        // resources on pool closes happen asynchronously to break
        // the deadlock. 
        //
        // For now we're leaving both versions around, but with faster
        // and more reliable synchronous checkin enabled, and async closing
        // of resources in BasicResourcePool.close().
        //
        public void connectionClosed(final ConnectionEvent evt)
        { 
            //System.err.println("Checking in: " + evt.getSource());

            if (ASYNCHRONOUS_CONNECTION_EVENT_LISTENER) 
            {
                Runnable r = new Runnable()
                {
                    public void run()
                    { doCheckinResource( evt ); }
                };
                sharedTaskRunner.postRunnable( r );
            }
            else
                doCheckinResource( evt );
        }

        private void doCheckinResource(ConnectionEvent evt)
        {
            try
            { rp.checkinResource( evt.getSource() ); }
            catch (Exception e)
            { 
                //e.printStackTrace(); 
                logger.log( MLevel.WARNING, 
                                "An Exception occurred while trying to check a PooledConection into a ResourcePool.",
                                e );
            }
        }

        //
        // We might want to update the pool asynchronously, because this is called
        // (indirectly) from a sync'ed method of NewPooledConnection, but
        // NewPooledConnection may be closed synchronously from a sync'ed
        // method of the resource pool, leading to a deadlock. Updating
        // pool status asynchronously breaks the cycle.
        //
        // But then we want checkins to happen quickly and reliably,
        // whereas pool shutdowns are rare, so perhaps it's best to
        // leave all ConnectionEvent handling synchronous, and let the closing of pooled
        // resources on pool closes happen asynchronously to break
        // the deadlock. 
        //
        // For now we're leaving both versions around, but with faster
        // and more reliable synchrounous ConnectionEventHandling enabled, and async closing
        // of resources in BasicResourcePool.close().
        //
        public void connectionErrorOccurred(final ConnectionEvent evt)
        {
//          System.err.println("CONNECTION ERROR OCCURRED!");
//          System.err.println();
            if ( logger.isLoggable( MLevel.FINE ) )
                logger.fine("CONNECTION ERROR OCCURRED!");

            final PooledConnection pc = (PooledConnection) evt.getSource();
            int status;
            if (pc instanceof C3P0PooledConnection)
                status = ((C3P0PooledConnection) pc).getConnectionStatus();
            else if (pc instanceof NewPooledConnection)
                status = ((NewPooledConnection) pc).getConnectionStatus();
            else //default to invalid connection, but not invalid database
                status = ConnectionTester.CONNECTION_IS_INVALID;

            final int final_status = status;

            if (ASYNCHRONOUS_CONNECTION_EVENT_LISTENER) 
            {
                Runnable r = new Runnable()
                {
                    public void run()
                    { doMarkPoolStatus( pc, final_status ); }
                };
                sharedTaskRunner.postRunnable( r );
            }
            else
                doMarkPoolStatus( pc, final_status );
        }

        private void doMarkPoolStatus(PooledConnection pc, int status)
        {
            try
            {
                switch (status)
                {
                case ConnectionTester.CONNECTION_IS_OKAY:
                    throw new RuntimeException("connectionErrorOcccurred() should only be " +
                    "called for errors fatal to the Connection.");
                case ConnectionTester.CONNECTION_IS_INVALID:
                    rp.markBroken( pc );
                    break;
                case ConnectionTester.DATABASE_IS_INVALID:
                    if (logger.isLoggable(MLevel.WARNING))
                        logger.warning("A ConnectionTest has failed, reporting that all previously acquired Connections are likely invalid. " +
                        "The pool will be reset.");
                    rp.resetPool();
                    break;
                default:
                    throw new RuntimeException("Bad Connection Tester (" + connectionTester + ") " +
                                    "returned invalid status (" + status + ").");
                }
            }
            catch ( ResourcePoolException e )
            {
                //System.err.println("Uh oh... our resource pool is probably broken!");
                //e.printStackTrace();
                logger.log(MLevel.WARNING, "Uh oh... our resource pool is probably broken!", e);
            }
        }
    }

    public int getNumConnections() throws SQLException
    { 
        try { return rp.getPoolSize(); }
        catch ( Exception e )
        { 
            //e.printStackTrace();
            logger.log( MLevel.WARNING, null, e );
            throw SqlUtils.toSQLException( e );
        }
    }

    public int getNumIdleConnections() throws SQLException
    { 
        try { return rp.getAvailableCount(); }
        catch ( Exception e )
        { 
            //e.printStackTrace();
            logger.log( MLevel.WARNING, null, e );
            throw SqlUtils.toSQLException( e );
        }
    }

    public int getNumBusyConnections() throws SQLException
    { 
        try 
        {
            synchronized ( rp )
            { return (rp.getAwaitingCheckinCount() - rp.getExcludedCount()); }
        }
        catch ( Exception e )
        { 
            //e.printStackTrace();
            logger.log( MLevel.WARNING, null, e );
            throw SqlUtils.toSQLException( e );
        }
    }

    public int getNumUnclosedOrphanedConnections() throws SQLException
    {
        try { return rp.getExcludedCount(); }
        catch ( Exception e )
        { 
            //e.printStackTrace();
            logger.log( MLevel.WARNING, null, e );
            throw SqlUtils.toSQLException( e );
        }
    }
    
    public long getStartTime() throws SQLException
    {
        try { return rp.getStartTime(); }
        catch ( Exception e )
        { 
            //e.printStackTrace();
            logger.log( MLevel.WARNING, null, e );
            throw SqlUtils.toSQLException( e );
        }
    }

    public long getUpTime() throws SQLException
    {
        try { return rp.getUpTime(); }
        catch ( Exception e )
        { 
            //e.printStackTrace();
            logger.log( MLevel.WARNING, null, e );
            throw SqlUtils.toSQLException( e );
        }
    }

    public long getNumFailedCheckins() throws SQLException
    {
        try { return rp.getNumFailedCheckins(); }
        catch ( Exception e )
        { 
            //e.printStackTrace();
            logger.log( MLevel.WARNING, null, e );
            throw SqlUtils.toSQLException( e );
        }
    }

    public long getNumFailedCheckouts() throws SQLException
    {
        try { return rp.getNumFailedCheckouts(); }
        catch ( Exception e )
        { 
            //e.printStackTrace();
            logger.log( MLevel.WARNING, null, e );
            throw SqlUtils.toSQLException( e );
        }
    }

    public long getNumFailedIdleTests() throws SQLException
    {
        try { return rp.getNumFailedIdleTests(); }
        catch ( Exception e )
        { 
            //e.printStackTrace();
            logger.log( MLevel.WARNING, null, e );
            throw SqlUtils.toSQLException( e );
        }
    }

    public Throwable getLastCheckinFailure() throws SQLException
    {
        try { return rp.getLastCheckinFailure(); }
        catch ( Exception e )
        { 
            //e.printStackTrace();
            logger.log( MLevel.WARNING, null, e );
            throw SqlUtils.toSQLException( e );
        }
    }

    public Throwable getLastCheckoutFailure() throws SQLException
    {
        try { return rp.getLastCheckoutFailure(); }
        catch ( Exception e )
        { 
            //e.printStackTrace();
            logger.log( MLevel.WARNING, null, e );
            throw SqlUtils.toSQLException( e );
        }
    }

    public Throwable getLastIdleTestFailure() throws SQLException
    {
        try { return rp.getLastIdleCheckFailure(); }
        catch ( Exception e )
        { 
            //e.printStackTrace();
            logger.log( MLevel.WARNING, null, e );
            throw SqlUtils.toSQLException( e );
        }
    }

    public Throwable getLastConnectionTestFailure() throws SQLException
    {
        try { return rp.getLastResourceTestFailure(); }
        catch ( Exception e )
        { 
            //e.printStackTrace();
            logger.log( MLevel.WARNING, null, e );
            throw SqlUtils.toSQLException( e );
        }
    }
    
    public Throwable getLastAcquisitionFailure() throws SQLException
    {
        try { return rp.getLastAcquisitionFailure(); }
        catch ( Exception e )
        { 
            //e.printStackTrace();
            logger.log( MLevel.WARNING, null, e );
            throw SqlUtils.toSQLException( e );
        }
    }

    /**
     * Discards all Connections managed by the pool
     * and reacquires new Connections to populate.
     * Current checked out Connections will still
     * be valid, and should still be checked into the
     * pool (so the pool can destroy them).
     */
    public void reset() throws SQLException
    { 
        try { rp.resetPool(); }
        catch ( Exception e )
        { 
            //e.printStackTrace();
            logger.log( MLevel.WARNING, null, e );
            throw SqlUtils.toSQLException( e );
        }
    }

    final static class ThrowableHolderPool
    {
        LinkedList l = new LinkedList();

        synchronized Throwable[] getThrowableHolder()
        {
            if (l.size() == 0)
                return new Throwable[1];
            else
                return (Throwable[]) l.remove(0);
        }

        synchronized void returnThrowableHolder(Throwable[] th)
        {
            th[0] = null;
            l.add(th);
        }
    }
    
}
