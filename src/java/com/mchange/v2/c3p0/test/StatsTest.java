/*
 * Distributed as part of c3p0 0.9.5-pre8
 *
 * Copyright (C) 2014 Machinery For Change, Inc.
 *
 * Author: Steve Waldman <swaldman@mchange.com>
 *
 * This library is free software; you can redistribute it and/or modify
 * it under the terms of EITHER:
 *
 *     1) The GNU Lesser General Public License (LGPL), version 2.1, as 
 *        published by the Free Software Foundation
 *
 * OR
 *
 *     2) The Eclipse Public License (EPL), version 1.0
 *
 * You may choose which license to accept if you wish to redistribute
 * or modify this work. You may offer derivatives of this work
 * under the license you have chosen, or you may provide the same
 * choice of license which you have been offered here.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * You should have received copies of both LGPL v2.1 and EPL v1.0
 * along with this software; see the files LICENSE-EPL and LICENSE-LGPL.
 * If not, the text of these licenses are currently available at
 *
 * LGPL v2.1: http://www.gnu.org/licenses/old-licenses/lgpl-2.1.html
 *  EPL v1.0: http://www.eclipse.org/org/documents/epl-v10.php 
 * 
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
	System.err.println("numConnections: " + cpds.getNumConnections());
	System.err.println("numBusyConnections: " + cpds.getNumBusyConnections());
	System.err.println("numIdleConnections: " + cpds.getNumIdleConnections());
	System.err.println("numUnclosedOrphanedConnections: " + cpds.getNumUnclosedOrphanedConnections());
	System.err.println();
    }

    public static void main(String[] argv)
    {
	try
	    {
		ComboPooledDataSource cpds = new ComboPooledDataSource();
		cpds.setJdbcUrl( argv[0] );
		cpds.setUser( argv[1] );
		cpds.setPassword( argv[2] );
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
			System.err.println( "Adding (" + (i + 1) + ") " + c );
 			display( cpds );
			Thread.sleep(1000);

// 			if (i == 9)
// 			    {
//  				//System.err.println("hardReset()ing");
//  				//cpds.hardReset();
// 				System.err.println("softReset()ing");
// 				cpds.softReset();
// 			    }
 		    }
		
		int count = 0;
		for (Iterator ii = hs.iterator(); ii.hasNext(); )
		    {
			Connection c = ((Connection) ii.next());
			System.err.println("Removing " + ++count);
			ii.remove();
			try { c.getMetaData().getTables( null, null, "PROBABLYNOT", new String[] {"TABLE"} ); }
			catch (Exception e)
			    { 
				System.err.println( e ); 
				System.err.println();
				continue;
			    }
			finally
			    { c.close(); }
			Thread.sleep(2000);
			display( cpds );
		    }

		System.err.println("Closing data source, \"forcing\" garbage collection, and sleeping for 5 seconds...");
		cpds.close();
		System.gc();
		System.err.println("Main Thread: Sleeping for five seconds!");
		Thread.sleep(5000);
// 		System.gc();
// 		Thread.sleep(5000);
		System.err.println("Bye!");
	    }
	catch( Exception e )
	    { e.printStackTrace(); }
    }
}





