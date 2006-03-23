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
import com.mchange.v2.c3p0.DriverManagerDataSource;

public final class LoadPoolBackedDataSource
{
    final static int NUM_THREADS = 50;
    final static int ITERATIONS_PER_THREAD = 1000;

    static Random random = new Random();
    static DataSource ds;

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
		DataSource ds_unpooled = DataSources.unpooledDataSource(jdbc_url, username, password);
		ds = DataSources.pooledDataSource( ds_unpooled );

		Connection con = null;
		Statement stmt = null;

		try
		    {
			con = ds.getConnection();
			stmt = con.createStatement();
			stmt.executeUpdate("CREATE TABLE testpbds ( a varchar(16), b varchar(16) )");
			System.err.println( "LoadPoolBackedDataSource -- TEST SCHEMA CREATED" );
		    }
		catch (SQLException e)
		    {
			e.printStackTrace();
			System.err.println("relation testpbds already exists, or something " +
					   "bad happened.");
		    }
		finally
		    {
			StatementUtils.attemptClose( stmt );
			ConnectionUtils.attemptClose( con );
		    }

		Thread[] threads = new Thread[NUM_THREADS];
		for (int i = 0; i < NUM_THREADS; ++i)
		    {
			Thread t = new ChurnThread();
			threads[i] = t;
			t.start();
			System.out.println("THREAD MADE [" + i + "]");
			Thread.sleep(1000);
		    }
		for (int i = 0; i < NUM_THREADS; ++i)
		    threads[i].join();
		
	    }
	catch (Exception e)
	    { e.printStackTrace(); }
	finally
	    {
		Connection con = null;
		Statement stmt = null;

		try
		    {
			con = ds.getConnection();
			stmt = con.createStatement();
			stmt.executeUpdate("DROP TABLE testpbds");
			System.err.println( "LoadPoolBackedDataSource -- TEST SCHEMA DROPPED" );
		    }
		catch (Exception e)
		    {
			e.printStackTrace();
		    }
		finally
		    {
			StatementUtils.attemptClose( stmt );
			ConnectionUtils.attemptClose( con );
		    }
	    }
    }

    static class ChurnThread extends Thread
    {
	public void run()
	{
	    try
		{
		    for( int i = 0; i < ITERATIONS_PER_THREAD; ++i )
			{
			    Connection con = null;
			    try
				{
				    con = ds.getConnection();
				    boolean select = random.nextBoolean();
				    if (select)
					executeSelect( con );
				    else
					executeInsert( con );
				    PooledDataSource pds = (PooledDataSource) ds;
				    System.out.println( pds.getNumConnectionsDefaultUser() );
				    System.out.println( pds.getNumIdleConnectionsDefaultUser() );
				    System.out.println( pds.getNumBusyConnectionsDefaultUser() );
				    System.out.println( pds.getNumConnectionsAllUsers() );
				}
			    finally
				{ ConnectionUtils.attemptClose( con ); }

			    //Thread.sleep( random.nextInt( 1000 ) );
			}
		}
	    catch (Exception e)
		{ e.printStackTrace(); }
	}
    }

    static void executeInsert(Connection con) throws SQLException
    {
	Statement stmt = null;
	try
	    {
		stmt = con.createStatement();
		stmt.executeUpdate("INSERT INTO testpbds VALUES ('" +
				   random.nextInt() + "', '" +
				   random.nextInt() + "')");
		System.out.println("INSERTION");
	    }
	finally
	    {
		StatementUtils.attemptClose( stmt );
	    }
    }

    static void executeSelect(Connection con) throws SQLException
    {
	long l = System.currentTimeMillis();
	Statement stmt = null;
	ResultSet rs   = null;
	try
	    {
		stmt = con.createStatement();
		rs = stmt.executeQuery("SELECT count(*) FROM testpbds");
		rs.next(); //we assume one row, one col
		System.out.println("SELECT [count=" + rs.getInt(1) + ", time=" +
				   (System.currentTimeMillis() - l) + " msecs]");
	    }
	finally
	    {
		ResultSetUtils.attemptClose( rs );
		StatementUtils.attemptClose( stmt );
	    }
    }

    private static void usage()
    {
	System.err.println("java " +
			   "-Djdbc.drivers=<comma_sep_list_of_drivers> " +
			   LoadPoolBackedDataSource.class.getName() +
			   " <jdbc_url> [<username> <password>]" );
	System.exit(-1);
    }


}
