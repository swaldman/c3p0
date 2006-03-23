/*
 * Distributed as part of c3p0 v.0.9.1-pre6
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


package com.mchange.v2.c3p0.test;

import java.util.*;
import java.sql.*;
import javax.sql.*;
import com.mchange.v2.c3p0.*;
import com.mchange.v1.db.sql.*;

public final class ConnectionDispersionTest
{
    private final static int DELAY_TIME = 120000;
    //private final static int DELAY_TIME = 300000;

    private final static int NUM_THREADS = 600;
    //private final static int NUM_THREADS = 300;
    //private final static int NUM_THREADS = 50;

    private final static Integer ZERO = new Integer(0);

    private static boolean should_go = false;

    private static DataSource cpds;

    private static int ready_count = 0;

    private static synchronized void setDataSource(DataSource ds)
    { cpds = ds; }

    private static synchronized DataSource getDataSource()
    { return cpds; }

    private static synchronized int ready()
    { return ++ready_count; }

    private static synchronized boolean isReady()
    { return ready_count == NUM_THREADS; }

    private static synchronized void start()
    {
	should_go = true;
	ConnectionDispersionTest.class.notifyAll();
    }

    private static synchronized void stop()
    {
	should_go = false;
	ConnectionDispersionTest.class.notifyAll();
    }

    private static synchronized boolean shouldGo()
    { return should_go; }

    public static void main(String[] argv)
    {
	String jdbc_url = null;
	String username = null;
	String password = null;
	if (argv.length == 3)
	    {
		jdbc_url = argv[0];
		username = argv[1];
		password = argv[2];
	    }
	else if (argv.length == 1)
	    {
		jdbc_url = argv[0];
		username = null;
		password = null;
	    }
	else
	    usage();
	
	if (! jdbc_url.startsWith("jdbc:") )
	    usage();
	
	try
	    {
		ComboPooledDataSource ds = new ComboPooledDataSource();
		ds.setJdbcUrl( jdbc_url );
		ds.setUser( username );
		ds.setPassword( password );
		setDataSource( ds );

		List threads = new ArrayList( NUM_THREADS );

		for (int i = 0; i < NUM_THREADS; ++i)
		    {
			Thread t = new CompeteThread();
			t.start();
			threads.add( t );
			Thread.currentThread().yield();
		    }

		synchronized ( ConnectionDispersionTest.class )
		    { while (! isReady()) ConnectionDispersionTest.class.wait(); }

		System.err.println("Starting the race.");
		start();

		System.err.println("Sleeping " + ((float) DELAY_TIME/1000) + 
				   " seconds to let the race run");
		Thread.sleep(DELAY_TIME);
		System.err.println("Stopping the race.");
		stop();
		for (int i = 0; i < NUM_THREADS; ++i)
		    ((Thread) threads.get(i)).join();

		Map outcomeMap = new TreeMap();
		for (int i = 0; i < NUM_THREADS; ++i)
		    {
			Integer outcome = new Integer( ((CompeteThread) threads.get(i)).getCount() );
			Integer old = (Integer) outcomeMap.get( outcome );
			if (old == null)
			    old = ZERO;
			outcomeMap.put( outcome, new Integer(old.intValue() + 1) );
		    }

		int last = 0;
		for (Iterator ii = outcomeMap.keySet().iterator(); ii.hasNext(); )
		    {
			Integer outcome = (Integer) ii.next();
			Integer count = (Integer) outcomeMap.get( outcome );
			int oc = outcome.intValue();
			int c = count.intValue();
			for (; last < oc; ++last)
			    System.out.println(String.valueOf(10000 + last).substring(1) + ": ");
			++last;
			System.out.print(String.valueOf(10000 + oc).substring(1) + ": ");
// 			if (oc < 10)
// 			    System.out.print(' ');
			for(int i = 0; i < c; ++i)
			    System.out.print('*');
			System.out.println();
		    }
		
// 		List outcomes = new ArrayList(NUM_THREADS);
// 		for (int i = 0; i < NUM_THREADS; ++i)
// 		    outcomes.add( new Integer( ((CompeteThread) threads.get(i)).getCount() ) );
// 		Collections.sort( outcomes );
		
// 		System.out.println("Connection counts:");
// 		for (int i = 0; i < NUM_THREADS; ++i)
// 		    System.out.println( outcomes.get(i) + "  (" + i + ")");
	    }
	catch (Exception e)
	    { e.printStackTrace(); }
    }

    static class CompeteThread extends Thread
    {
	DataSource ds;
	int count;

	synchronized void increment()
	{ ++count; }

	synchronized int getCount()
	{ return count; }

	public void run()
	{
	    try
		{
		    this.ds = getDataSource();
		    synchronized ( ConnectionDispersionTest.class )
			{
			    ready();
			    ConnectionDispersionTest.class.wait();
			}
		    while ( shouldGo() )
			{
			    Connection c = null;
			    ResultSet rs = null;
			    try
				{ 
				    c = ds.getConnection();
				    increment();
				    rs = c.getMetaData().getTables( null, 
								    null, 
								    "PROBABLYNOT", 
								    new String[] {"TABLE"} );
				}
			    catch (SQLException e)
				{ e.printStackTrace(); }
			    finally
				{ 
				    try {if (rs != null) rs.close(); }
				    catch (Exception e)
					{ e.printStackTrace(); }
				    
				    try {if (c != null) c.close(); }
				    catch (Exception e)
					{ e.printStackTrace(); }
				}
			}
		}
	    catch (Exception e)
		{ e.printStackTrace(); }
	}
    }

    private static void usage()
    {
	System.err.println("java " +
			   "-Djdbc.drivers=<comma_sep_list_of_drivers> " +
			   ConnectionDispersionTest.class.getName() +
			   " <jdbc_url> [<username> <password>]" );
	System.exit(-1);
    }
}
