/*
 * Distributed as part of c3p0 v.0.8.5pre4
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


package com.mchange.v2.c3p0.test;

import java.sql.*;
import javax.sql.*;
import java.lang.reflect.*;
import java.util.Random;

public final class C3P0TestUtils
{
    public static DataSource unreliableCommitDataSource(DataSource ds) throws Exception
    {
	return (DataSource) Proxy.newProxyInstance( C3P0TestUtils.class.getClassLoader(),
						    new Class[] { DataSource.class },
						    new StupidDataSourceInvocationHandler( ds ) );
    }

    private C3P0TestUtils()
    {}

    static class StupidDataSourceInvocationHandler implements InvocationHandler
    {
	DataSource ds;
	Random r = new Random();
	
	StupidDataSourceInvocationHandler(DataSource ds)
	{ this.ds = ds; }
	
	public Object invoke(Object proxy, Method method, Object[] args)
	    throws Throwable
	{
	    if ( "getConnection".equals(method.getName()) )
		{
		    Connection conn = (Connection) method.invoke( ds, args );
		    return Proxy.newProxyInstance( C3P0TestUtils.class.getClassLoader(),
						   new Class[] { Connection.class },
						   new StupidConnectionInvocationHandler( conn ) );
		}
	    else
		return method.invoke( ds, args );
	}
    }

    static class StupidConnectionInvocationHandler implements InvocationHandler
    {
	Connection conn;
	Random r = new Random();
	
	boolean invalid = false;

	StupidConnectionInvocationHandler(Connection conn)
	{ this.conn = conn; }
	
	public Object invoke(Object proxy, Method method, Object[] args)
	    throws Throwable
	{
	    if ("close".equals(method.getName()))
		{
		    if (invalid)
			{
			    new Exception("Duplicate close() called on Connection!!!").printStackTrace();
			}
		    else
			{
			    //new Exception("CLOSE CALLED ON UNPOOLED DATASOURCE CONNECTION!").printStackTrace();
			    invalid = true;
			}
		    return null;
		}
	    else if ( invalid )
		throw new SQLException("Connection closed -- cannot " + method.getName());
	    else if ( "commit".equals(method.getName())  && r.nextInt(100) == 0 )
		{
		    conn.rollback();
		    throw new SQLException("Random commit exception!!!");
		}
	    else if (r.nextInt(200) == 0)
		{
		    conn.rollback();
		    conn.close();
		    throw new SQLException("Random Fatal Exception Occurred!!!");
		}
	    else
		return method.invoke( conn, args );
	}
    }

}
