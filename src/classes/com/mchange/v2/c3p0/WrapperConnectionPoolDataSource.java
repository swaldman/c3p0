/*
 * Distributed as part of c3p0 v.0.8.5
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


package com.mchange.v2.c3p0;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyVetoException;
import java.beans.VetoableChangeListener;
import java.beans.PropertyChangeListener;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.sql.*;
import javax.sql.*;
import com.mchange.v2.c3p0.impl.*;

public final class WrapperConnectionPoolDataSource extends WrapperConnectionPoolDataSourceBase implements ConnectionPoolDataSource
{
    ConnectionTester connectionTester = C3P0Defaults.connectionTester();

    {
	VetoableChangeListener setConnectionTesterListener = new VetoableChangeListener()
	    {
		// always called within synchronized mutators of the parent class... needn't explicitly sync here
		public void vetoableChange( PropertyChangeEvent evt ) throws PropertyVetoException
		{
		    Object val = evt.getNewValue();
		    try
			{
			    if ( "connectionTesterClassName".equals( evt.getPropertyName() ) )
				recreateConnectionTester( (String) val );
			}
		    catch ( Exception e )
			{
			    e.printStackTrace();
			    throw new PropertyVetoException("Could not instantiate connection tester class with name '" + val + "'.", evt);
			}
		}
	    };
	this.addVetoableChangeListener( setConnectionTesterListener );
    }

    //implementation of javax.sql.ConnectionPoolDataSource
    public synchronized PooledConnection getPooledConnection()
	throws SQLException
    { 
	Connection conn = getNestedDataSource().getConnection();
	if ( this.isUsesTraditionalReflectiveProxies() )
	    {
		//return new C3P0PooledConnection( new com.mchange.v2.c3p0.test.CloseReportingConnection( conn ), 
		return new C3P0PooledConnection( conn, 
						 connectionTester,
						 this.isAutoCommitOnClose(), 
						 this.isForceIgnoreUnresolvedTransactions() ); 
	    }
	else
	    {
		return new NewPooledConnection( conn, 
						connectionTester,
						this.isAutoCommitOnClose(), 
						this.isForceIgnoreUnresolvedTransactions() ); 
	    }
    } 
 
    public synchronized PooledConnection getPooledConnection(String user, String password)
	throws SQLException
    { 
	Connection conn = getNestedDataSource().getConnection(user, password);
	if ( this.isUsesTraditionalReflectiveProxies() )
	    {
		//return new C3P0PooledConnection( new com.mchange.v2.c3p0.test.CloseReportingConnection( conn ), 
		return new C3P0PooledConnection( conn,
						 connectionTester,
						 this.isAutoCommitOnClose(), 
						 this.isForceIgnoreUnresolvedTransactions() ); 
	    }
	else
	    {
		return new NewPooledConnection( conn, 
						connectionTester,
						this.isAutoCommitOnClose(), 
						this.isForceIgnoreUnresolvedTransactions() ); 
	    }
    }
 
    public synchronized PrintWriter getLogWriter()
	throws SQLException
    { return getNestedDataSource().getLogWriter(); }

    public synchronized void setLogWriter(PrintWriter out)
	throws SQLException
    { getNestedDataSource().setLogWriter( out ); }

    public synchronized void setLoginTimeout(int seconds)
	throws SQLException
    { getNestedDataSource().setLoginTimeout( seconds ); }

    public synchronized int getLoginTimeout()
	throws SQLException
    { return getNestedDataSource().getLoginTimeout(); }

    //"virtual properties"
    public synchronized String getUser()
    { 
	try { return C3P0ImplUtils.findAuth( this.getNestedDataSource() ).getUser(); }
	catch (SQLException e)
	    {
		e.printStackTrace();
		return null; 
	    }
    }

    public synchronized String getPassword()
    { 
	try { return C3P0ImplUtils.findAuth( this.getNestedDataSource() ).getPassword(); }
	catch (SQLException e)
	    { 
		e.printStackTrace();
		return null; 
	    }
    }

    //other code
    private void recreateConnectionTester(String className) throws Exception
    {
	if (className != null)
	    {
		ConnectionTester ct = (ConnectionTester) Class.forName( className ).newInstance();
		this.connectionTester = ct;
	    }
	else
	    this.connectionTester = C3P0Defaults.connectionTester();
    }
}
