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


package com.mchange.v2.c3p0.stmt;

import java.util.*;
import java.sql.*;
import javax.sql.*;
import com.mchange.v2.c3p0.*;
import com.mchange.v1.db.sql.*;

public final class StatementCacheBenchmark
{
    final static String EMPTY_TABLE_CREATE = "CREATE TABLE emptyyukyuk (a varchar(8), b varchar(8))";
    final static String EMPTY_TABLE_SELECT = "SELECT * FROM emptyyukyuk";
    final static String EMPTY_TABLE_DROP   = "DROP TABLE emptyyukyuk";

    final static String EMPTY_TABLE_CONDITIONAL_SELECT = "SELECT * FROM emptyyukyuk where a = ?";

    final static int NUM_ITERATIONS = 2000;

    public static void main(String[] argv)
    {
	DataSource ds_unpooled = null;
	DataSource ds_pooled   = null;
	try
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

		ds_unpooled = DriverManagerDataSourceFactory.create(jdbc_url, username, password);
		ds_pooled
    		    = PoolBackedDataSourceFactory.create(jdbc_url, 
    							 username, 
    							 password,
    							 5,
    							 20,
    							 5,
    							 0,
    							 100 );

		create(ds_pooled);

		perform( ds_pooled, "pooled" );
		perform( ds_unpooled, "unpooled" );
	    }
	catch( Exception e )
	    { e.printStackTrace(); }
	finally
	    {
		try { drop(ds_pooled); }
		catch (Exception e)
		    { e.printStackTrace(); }
	    }
    }

    private static void perform( DataSource ds, String name )
	throws SQLException 
    {
	Connection c = null;
	PreparedStatement ps = null;
	try
	    {
		c = ds.getConnection();
		long start = System.currentTimeMillis();
		for (int i = 0; i < NUM_ITERATIONS; ++i)
		    {
			PreparedStatement test = 
			    c.prepareStatement( EMPTY_TABLE_CONDITIONAL_SELECT );
			test.close();
		    }
		long end = System.currentTimeMillis();
		System.err.println(name + " --> " +
				   (end - start) / (float) NUM_ITERATIONS + 
				   " [" + NUM_ITERATIONS + " iterations]"); 
	    }
	finally
	    {
		StatementUtils.attemptClose( ps );
		ConnectionUtils.attemptClose( c );
	    }
    }

    private static void usage()
    {
	System.err.println("java " +
			   "-Djdbc.drivers=<comma_sep_list_of_drivers> " +
			   StatementCacheBenchmark.class.getName() +
			   " <jdbc_url> [<username> <password>]" );
	System.exit(-1);
    }

    static void create(DataSource ds)
	throws SQLException
    {
	System.err.println("Creating test schema.");
	Connection        con = null;
	PreparedStatement ps1 = null;
	try 
	    { 
		con = ds.getConnection();
		ps1 = con.prepareStatement(EMPTY_TABLE_CREATE);
		ps1.executeUpdate();
		System.err.println("Test schema created.");
	    }
	finally
	    {
		StatementUtils.attemptClose( ps1 );
		ConnectionUtils.attemptClose( con ); 
	    }
    }

    static void drop(DataSource ds)
	throws SQLException
    {
	Connection con        = null;
	PreparedStatement ps1 = null;
	try 
	    { 
		con = ds.getConnection();
		ps1 = con.prepareStatement(EMPTY_TABLE_DROP);
		ps1.executeUpdate();
	    }
	finally
	    {
		StatementUtils.attemptClose( ps1 );
		ConnectionUtils.attemptClose( con ); 
	    }
	System.err.println("Test schema dropped.");
    }
}





