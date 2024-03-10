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
		
		//we'll let sysprops of c3p0.properties set-up this stuff for now 
		//cpds.setJdbcUrl( argv[0] );
		//cpds.setUser( argv[1] );
		//cpds.setPassword( argv[2] );
		
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





