/*
 * Distributed as part of c3p0 v.0.8.4-test5
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

import java.util.*;
import java.sql.*;
import javax.sql.*;
import com.mchange.v2.c3p0.*;
import com.mchange.v1.db.sql.*;

public final class StatsTest
{
    static void display( ComboPooledDataSource cpds ) throws Exception
    {
	System.err.println("num_connections: " + cpds.getNumConnections());
	System.err.println("num_busy_connections: " + cpds.getNumBusyConnections());
	System.err.println("num_idle_connections: " + cpds.getNumIdleConnections());
	System.err.println();
    }

    public static void main(String[] argv)
    {
	try
	    {
		ComboPooledDataSource cpds = new ComboPooledDataSource();
		cpds.setDriverClass( "org.postgresql.Driver" );
		cpds.setJdbcUrl( "jdbc:postgresql://localhost/c3p0-test" );
		cpds.setUser("swaldman");
		cpds.setPassword("test");
		cpds.setMinPoolSize(5);
		cpds.setAcquireIncrement(5);
		cpds.setMaxPoolSize(20);

		System.err.println("Initial...");
		display( cpds );
		Thread.sleep(2000);

 		HashSet hs = new HashSet();
 		for (int i = 0; i < 20; ++i)
 		    {
			Connection c = cpds.getConnection();
 			hs.add( c );
			System.err.println( c );
 			display( cpds );
			Thread.sleep(1000);
 		    }

		for (Iterator ii = hs.iterator(); ii.hasNext(); )
		    {
			((Connection) ii.next()).close();
			display( cpds );
		    }

		System.err.println("Closing data source, \"forcing\" garbage collection, and sleeping for 5 seconds...");
		cpds.close();
		System.gc();
		Thread.sleep(5000);
// 		System.gc();
// 		Thread.sleep(5000);
		System.err.println("Bye!");
	    }
	catch( Exception e )
	    { e.printStackTrace(); }
    }
}





