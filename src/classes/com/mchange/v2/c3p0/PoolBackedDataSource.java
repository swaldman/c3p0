/*
 * Distributed as part of c3p0 v.0.8.4-test1
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


package com.mchange.v2.c3p0;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyVetoException;
import java.beans.VetoableChangeListener;
import java.beans.PropertyChangeListener;
import java.io.PrintWriter;
import java.sql.*;
import javax.sql.*;
import com.mchange.v2.c3p0.impl.*;

public final class PoolBackedDataSource extends PoolBackedDataSourceBase implements PooledDataSource
{
     final static String NO_CPDS_ERR_MSG =
       "Attempted to use an uninitialized PoolBackedDataSource. " +
       "Please call setConnectionPoolDataSource( ... ) to initialize.";

    //MT: protected by this' lock
    transient C3P0PooledConnectionPoolManager poolManager;
    transient boolean is_closed = false;
    //MT: end protected by this' lock

    {
	PropertyChangeListener l = new PropertyChangeListener()
	    {
		public void propertyChange( PropertyChangeEvent evt )
		{ resetPoolManager(); }
	    };
	this.addPropertyChangeListener( l );
    }

    //implementation of javax.sql.DataSource
    public Connection getConnection() throws SQLException
    {
	PooledConnection pc = getPoolManager().getPool().checkoutPooledConnection();
	return pc.getConnection();
    }

    public Connection getConnection(String username, String password) throws SQLException
    { 
	PooledConnection pc = getPoolManager().getPool(username, password).checkoutPooledConnection();
	return pc.getConnection();
    }

    public PrintWriter getLogWriter() throws SQLException
    { return assertCpds().getLogWriter(); }

    public void setLogWriter(PrintWriter out) throws SQLException
    { assertCpds().setLogWriter( out ); }

    public int getLoginTimeout() throws SQLException
    { return assertCpds().getLoginTimeout(); }

    public void setLoginTimeout(int seconds) throws SQLException
    { assertCpds().setLoginTimeout( seconds ); }

    //implementation of com.mchange.v2.c3p0.PoolingDataSource
    public int getNumConnections() throws SQLException
    { return getPoolManager().getPool().getNumConnections(); }

    public int getNumIdleConnections() throws SQLException
    { return getPoolManager().getPool().getNumIdleConnections(); }

    public int getNumBusyConnections() throws SQLException
    { return getPoolManager().getPool().getNumBusyConnections(); }

    public int getNumConnections(String username, String password) throws SQLException
    { return getPoolManager().getPool(username, password).getNumConnections(); }

    public int getNumIdleConnections(String username, String password) throws SQLException
    { return getPoolManager().getPool(username, password).getNumIdleConnections(); }

    public int getNumBusyConnections(String username, String password) throws SQLException
    { return getPoolManager().getPool(username, password).getNumBusyConnections(); }

    public int getNumConnectionsAllAuths() throws SQLException
    { return getPoolManager().getNumConnectionsAllAuths(); }

    public void close()
    { 
	resetPoolManager(); 
	is_closed = true;
    }

    //other code
    private synchronized void resetPoolManager()
    {
	if ( poolManager != null )
	    {
		poolManager.unregisterActiveClient( this );
		poolManager = null;
	    }
     }

     private synchronized ConnectionPoolDataSource assertCpds() throws SQLException
     {
	 if ( is_closed )
	     throw new SQLException(this + " has been closed() -- you can no longer use it.");

	 ConnectionPoolDataSource out = this.getConnectionPoolDataSource();
         if ( out == null )
           throw new SQLException(NO_CPDS_ERR_MSG);
         return out;
     }

     private synchronized C3P0PooledConnectionPoolManager getPoolManager() throws SQLException
     {
	if (poolManager == null)
	    {
		poolManager = C3P0PooledConnectionPoolManager.find(assertCpds(), this.getNumHelperThreads());
		poolManager.registerActiveClient( this );
	    }
        return poolManager;	    
     }
}

