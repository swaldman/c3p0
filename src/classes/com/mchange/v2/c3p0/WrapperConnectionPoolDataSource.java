/*
 * Distributed as part of c3p0 v.0.8.4-test2
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
import java.lang.reflect.Method;
import java.sql.*;
import javax.sql.*;
import com.mchange.v2.c3p0.impl.*;

public final class WrapperConnectionPoolDataSource extends WrapperConnectionPoolDataSourceBase implements ConnectionPoolDataSource
{
    {
	VetoableChangeListener setConnectionTesterListener = new VetoableChangeListener()
	    {
		public void vetoableChange( PropertyChangeEvent evt ) throws PropertyVetoException
		{
		    Object val = evt.getNewValue();
		    try
			{
			    if ( "connectionTesterClassName".equals( evt.getPropertyName() ) )
				checkConnectionTesterClassName( (String) val );
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
    public PooledConnection getPooledConnection()
	throws SQLException
    { 
	return new C3P0PooledConnection( getNestedDataSource().getConnection(), 
					 this.isAutoCommitOnClose(), 
					 this.isForceIgnoreUnresolvedTransactions() ); 
    } 
 
    public PooledConnection getPooledConnection(String user, String password)
	throws SQLException
    { 
	return new C3P0PooledConnection( getNestedDataSource().getConnection(user, password), 
					 this.isAutoCommitOnClose(), 
					 this.isForceIgnoreUnresolvedTransactions() ); 
    }
 
    public PrintWriter getLogWriter()
	throws SQLException
    { return getNestedDataSource().getLogWriter(); }

    public void setLogWriter(PrintWriter out)
	throws SQLException
    { getNestedDataSource().setLogWriter( out ); }

    public void setLoginTimeout(int seconds)
	throws SQLException
    { getNestedDataSource().setLoginTimeout( seconds ); }

    public int getLoginTimeout()
	throws SQLException
    { return getNestedDataSource().getLoginTimeout(); }

    //"virtual properties"
    public String getUser()
    { 
	try { return C3P0ImplUtils.findAuth( this.getNestedDataSource() ).getUser(); }
	catch (SQLException e)
	    {
		e.printStackTrace();
		return null; 
	    }
    }

    public String getPassword()
    { 
	try { return C3P0ImplUtils.findAuth( this.getNestedDataSource() ).getPassword(); }
	catch (SQLException e)
	    { 
		e.printStackTrace();
		return null; 
	    }
    }

    //other code
    private void checkConnectionTesterClassName(String className) throws Exception
    {
	if (className != null)
	    Class.forName( className ).newInstance();
    }
}
