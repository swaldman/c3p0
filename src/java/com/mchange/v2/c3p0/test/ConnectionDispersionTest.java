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

		// get the DataSource initialized
		ds.getConnection().close();

		System.err.println("Generating thread list...");
		List threads = new ArrayList( NUM_THREADS );
		for (int i = 0; i < NUM_THREADS; ++i)
		    {
			Thread t = new CompeteThread();
			t.start();
			threads.add( t );
			Thread.currentThread().yield();
		    }
		System.err.println("Thread list generated.");

		synchronized ( ConnectionDispersionTest.class )
		    { while (! isReady()) ConnectionDispersionTest.class.wait(); }

		System.err.println("Starting the race.");
		start();

		System.err.println("Sleeping " + ((float) DELAY_TIME/1000) + 
				   " seconds to let the race run");
		Thread.sleep(DELAY_TIME);
		System.err.println("Stopping the race.");
		stop();

		System.err.println("Waiting for Threads to complete.");
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
			    //System.err.println("Getting ready... already ready? " + ready_count);

			    ready();
			    ConnectionDispersionTest.class.wait();

			    //System.err.println("All ready. Starting.");
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
		    //System.err.println("Thread completed.");
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
