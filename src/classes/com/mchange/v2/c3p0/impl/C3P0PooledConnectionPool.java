/*
 * Distributed as part of c3p0 v.0.8.4.1
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

import java.sql.*;
import javax.sql.*;
import com.mchange.v2.sql.SqlUtils;
import com.mchange.v2.resourcepool.*;
import java.util.Properties;
import com.mchange.v2.c3p0.stmt.*;
import com.mchange.v2.c3p0.ConnectionTester;
import com.mchange.v1.db.sql.ConnectionUtils;

public final class C3P0PooledConnectionPool
{
    final static int ACQ_RETRY_ATTEMPTS = 30;

    ResourcePool rp;
    ConnectionEventListener cl = new ConnectionEventListenerImpl();

    ConnectionTester     connectionTester;
    GooGooStatementCache scache;

    C3P0PooledConnectionPool( final ConnectionPoolDataSource cpds,
			      final DbAuth auth,
			      int  min, 
			      int  max, 
			      int  inc,
			      int idleConnectionTestPeriod, //seconds
			      int maxIdleTime, //seconds
			      final boolean testConnectionOnCheckout,
			      GooGooStatementCache myscache,
			      final ConnectionTester connectionTester,
			      final ResourcePoolFactory fact) throws SQLException
    {
        try
	    {
		this.scache = myscache;
		this.connectionTester = connectionTester;

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
				    else
					{
					    System.err.print("Warning! StatementPooling not ");
					    System.err.print("implemented for external (non-c3p0) ");
					    System.err.println("ConnectionPoolDataSources.");
					}
				}
			    out.addConnectionEventListener( cl );
			    return out;
			}

			// REFURBISHMENT:
			// the PooledConnection refurbishes itself when 
			// its Connection view is closed, prior to being
			// checked back in to the pool.

			public void refurbishResourceOnCheckout( Object resc ) throws Exception
			{
			    if ( testConnectionOnCheckout )
				{
				    //System.err.println("testing connection on checkout...");
				    refurbishResource( resc );
				}
			}

			public void refurbishResourceOnCheckin( Object resc ) throws Exception
			{
			    // do nothing on checkin
			}

			public void refurbishIdleResource( Object resc ) throws Exception
			{ refurbishResource( resc ); }
			
			private void refurbishResource(Object resc) throws Exception
			{ 
			    PooledConnection pc = (PooledConnection) resc;
			    
			    int status;
			    Connection conn = null;
			    try	
				{ 
				    //we don't want any callbacks while we're testing the resource
				    pc.removeConnectionEventListener( cl );

				    conn = pc.getConnection(); //checkout proxy connection
				    status = connectionTester.activeCheckConnection( conn );
				}
			    catch (SQLException e)
				{
				    if (Debug.DEBUG)
					e.printStackTrace();
				    status = ConnectionTester.CONNECTION_IS_INVALID;
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
				    throw new SQLException("Connection is invalid");
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
			fact.setAcquisitionRetryAttempts( ACQ_RETRY_ATTEMPTS );
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
        try { return (PooledConnection) rp.checkoutResource(); }
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
		{ e.printStackTrace(); }
	}

	public void connectionErrorOccurred(ConnectionEvent evt)
	{
	    System.err.println("CONNECTION ERROR OCCURRED!");
	    System.err.println();
	    try
		{
		    PooledConnection pc = (PooledConnection) evt.getSource();
		    int status;
		    if (pc instanceof C3P0PooledConnection)
			status = ((C3P0PooledConnection) pc).getConnectionStatus();
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
		    System.err.println("Uh oh... our resource pool is probably broken!");
		    e.printStackTrace();
		}
	}
    }

    public int getNumConnections() throws SQLException
    { 
	try { return rp.getPoolSize(); }
	catch ( Exception e )
	    { 
		e.printStackTrace();
		throw SqlUtils.toSQLException( e );
	    }
    }

    public int getNumIdleConnections() throws SQLException
    { 
	try { return rp.getAvailableCount(); }
	catch ( Exception e )
	    { 
		e.printStackTrace();
		throw SqlUtils.toSQLException( e );
	    }
    }

    public int getNumBusyConnections() throws SQLException
    { 
	try { return rp.getAwaitingCheckinCount(); }
	catch ( Exception e )
	    { 
		e.printStackTrace();
		throw SqlUtils.toSQLException( e );
	    }
    }
}
