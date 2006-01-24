/*
 * Distributed as part of c3p0 v.0.9.0.4
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

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.ConnectionEvent;
import javax.sql.ConnectionEventListener;
import javax.sql.ConnectionPoolDataSource;
import javax.sql.PooledConnection;

import com.mchange.v1.db.sql.ConnectionUtils;
import com.mchange.v2.log.MLevel;
import com.mchange.v2.log.MLog;
import com.mchange.v2.log.MLogger;
import com.mchange.v2.c3p0.ConnectionTester;
import com.mchange.v2.c3p0.QueryConnectionTester;
import com.mchange.v2.c3p0.stmt.GooGooStatementCache;
import com.mchange.v2.resourcepool.CannotAcquireResourceException;
import com.mchange.v2.resourcepool.ResourcePool;
import com.mchange.v2.resourcepool.ResourcePoolException;
import com.mchange.v2.resourcepool.ResourcePoolFactory;
import com.mchange.v2.resourcepool.TimeoutException;
import com.mchange.v2.sql.SqlUtils;

public final class C3P0PooledConnectionPool
{
    final static MLogger logger = MLog.getLogger( C3P0PooledConnectionPool.class );

    ResourcePool rp;
    ConnectionEventListener cl = new ConnectionEventListenerImpl();

    ConnectionTester     connectionTester;
    GooGooStatementCache scache;

    int checkoutTimeout;

    C3P0PooledConnectionPool( final ConnectionPoolDataSource cpds,
			      final DbAuth auth,
			      int  min, 
			      int  max, 
			      int  inc,
			      int acq_retry_attempts,
			      int acq_retry_delay,
			      boolean break_after_acq_failure,
			      int checkoutTimeout, //milliseconds
			      int idleConnectionTestPeriod, //seconds
			      int maxIdleTime, //seconds
			      final boolean testConnectionOnCheckout,
			      final boolean testConnectionOnCheckin,
			      GooGooStatementCache myscache,
			      final ConnectionTester connectionTester,
			      final String testQuery,
			      final ResourcePoolFactory fact) throws SQLException
    {
        try
	    {
		this.scache = myscache;
		this.connectionTester = connectionTester;

		this.checkoutTimeout = checkoutTimeout;

		ResourcePool.Manager manager = new ResourcePool.Manager()
		    {	
			public Object acquireResource() throws Exception
			{ 
			    PooledConnection out = (auth.equals( C3P0ImplUtils.NULL_AUTH ) ?
						    cpds.getPooledConnection() :
						    cpds.getPooledConnection( auth.getUser(), 
									      auth.getPassword() ) );
			    if (scache != null)
				{
				    if (out instanceof C3P0PooledConnection)
					((C3P0PooledConnection) out).initStatementCache(scache);
				    else if (out instanceof NewPooledConnection)
					((NewPooledConnection) out).initStatementCache(scache);
				    else
					{
// 					    System.err.print("Warning! StatementPooling not ");
// 					    System.err.print("implemented for external (non-c3p0) ");
// 					    System.err.println("ConnectionPoolDataSources.");

					    logger.warning("StatementPooling not " +
							   "implemented for external (non-c3p0) " +
							   "ConnectionPoolDataSources.");
					}
				}
			    out.addConnectionEventListener( cl );
			    return out;
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
			}

			public void refurbishResourceOnCheckin( Object resc ) throws Exception
			{
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
			    
			    int status;
			    Connection conn = null;
			    Throwable rootCause = null;
			    try	
				{ 
				    //we don't want any callbacks while we're testing the resource
				    pc.removeConnectionEventListener( cl );

				    conn = pc.getConnection(); //checkout proxy connection
				    if ( testQuery == null )
					status = connectionTester.activeCheckConnection( conn );
				    else
					{
					    if (connectionTester instanceof QueryConnectionTester)
						status = ((QueryConnectionTester) connectionTester).activeCheckConnection( conn, testQuery );
					    else
						{
// 						    System.err.println("[c3p0] WARNING: testQuery '" + testQuery +
// 								       "' ignored. Please set a ConnectionTester that implements " +
// 								       "com.mchange.v2.c3p0.advanced.QueryConnectionTester, or use the " +
// 								       "DefaultConnectionTester, to test with the testQuery.");

						    logger.warning("[c3p0] testQuery '" + testQuery +
								   "' ignored. Please set a ConnectionTester that implements " +
								   "com.mchange.v2.c3p0.advanced.QueryConnectionTester, or use the " +
								   "DefaultConnectionTester, to test with the testQuery.");
						    status = connectionTester.activeCheckConnection( conn );
						}
					}
				}
			    catch (SQLException e)
				{
				    if (Debug.DEBUG)
					logger.log(MLevel.FINE, "A Connection test failed with an Exception.", e);
					//e.printStackTrace();
				    status = ConnectionTester.CONNECTION_IS_INVALID;
				    rootCause = e;
				}
			    finally
				{ 
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
			{ ((PooledConnection) resc).close();}
		    };
		
		synchronized (fact)
		    {
			fact.setMin( min );
			fact.setMax( max );
			fact.setIncrement( inc );
			fact.setIdleResourceTestPeriod( idleConnectionTestPeriod * 1000);
			fact.setResourceMaxAge( maxIdleTime * 1000 );
			fact.setAcquisitionRetryAttempts( acq_retry_attempts );
			fact.setAcquisitionRetryDelay( acq_retry_delay );
			fact.setBreakOnAcquisitionFailure( break_after_acq_failure );
			fact.setAgeIsAbsolute( false ); //we timeout Connections only when idle
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
    
    public void close() throws SQLException
    { 
	// System.err.println(this + " closing.");
        try { rp.close(); }
        catch (ResourcePoolException e)
	    { throw SqlUtils.toSQLException(e); }
    }
    
    class ConnectionEventListenerImpl implements ConnectionEventListener
    {
	public void connectionClosed(ConnectionEvent evt)
	{ 
	    //System.err.println("Checking in: " + evt.getSource());
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

	public void connectionErrorOccurred(ConnectionEvent evt)
	{
// 	    System.err.println("CONNECTION ERROR OCCURRED!");
// 	    System.err.println();
	    if ( logger.isLoggable( MLevel.FINE ) )
		logger.fine("CONNECTION ERROR OCCURRED!");
	    try
		{
		    PooledConnection pc = (PooledConnection) evt.getSource();
		    int status;
		    if (pc instanceof C3P0PooledConnection)
			status = ((C3P0PooledConnection) pc).getConnectionStatus();
		    else if (pc instanceof NewPooledConnection)
			status = ((NewPooledConnection) pc).getConnectionStatus();
		    else //default to invalid connection, but not invalid database
			status = ConnectionTester.CONNECTION_IS_INVALID;
		    switch (status)
			{
			case ConnectionTester.CONNECTION_IS_OKAY:
			    throw new InternalError("connectionErrorOcccurred() should only be " +
						    "called for errors fatal to the Connection.");
			case ConnectionTester.CONNECTION_IS_INVALID:
			    rp.markBroken( pc );
			    break;
			case ConnectionTester.DATABASE_IS_INVALID:
			    rp.resetPool();
			    break;
			default:
			    throw new InternalError("Bad Connection Tester (" + connectionTester + ") " +
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
}
