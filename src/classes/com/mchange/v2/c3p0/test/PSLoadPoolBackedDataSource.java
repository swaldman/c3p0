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

public final class PSLoadPoolBackedDataSource
{
    final static String INSERT_STMT = "INSERT INTO testpbds VALUES ( ? , ? )";
    final static String SELECT_STMT = "SELECT count(*) FROM testpbds";

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
			con = ds_unpooled.getConnection();
			stmt = con.createStatement();
			stmt.executeUpdate("CREATE TABLE testpbds ( a varchar(16), b varchar(16) )");
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

		for (int i = 0; i < 50; ++i)
		    {
			Thread t = new ChurnThread();
			t.start();
			System.out.println("THREAD MADE [" + i + "]");
			Thread.sleep(1000);
		    }
		
	    }
	catch (Exception e)
	    { e.printStackTrace(); }
    }

    static class ChurnThread extends Thread
    {
	public void run()
	{
	    try
		{
		    while(true)
			{
			    Connection con = null;
			    try
				{
				    con = ds.getConnection();
				    boolean select = random.nextBoolean();
				    //boolean select = true; //CHURNSELECTTEST
				    if (select)
					executeSelect( con );
				    else
					executeInsert( con );
				}
 			    catch (Exception e)
 				{ e.printStackTrace(); }
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
	PreparedStatement pstmt = null;
	try
	    {
		pstmt = con.prepareStatement(INSERT_STMT);
		pstmt.setInt(1, random.nextInt());
		pstmt.setInt(2, random.nextInt());
		pstmt.executeUpdate();
		System.out.println("INSERTION");
	    }
	finally
	    {
		// make sure forgetting this doesn't starve
		// statement cache, as long as the connection
		// closes...

		StatementUtils.attemptClose( pstmt );
	    }
    }

    static void executeSelect(Connection con) throws SQLException
    {
	long l = System.currentTimeMillis();
	PreparedStatement pstmt = null;
	ResultSet rs   = null;
	try
	    {
		pstmt = con.prepareStatement(SELECT_STMT);
		rs = pstmt.executeQuery();
		rs.next(); //we assume one row, one col
		System.out.println("SELECT [count=" + rs.getInt(1) + ", time=" +
				   (System.currentTimeMillis() - l) + " msecs]");
	    }
	finally
	    {
		ResultSetUtils.attemptClose( rs );
		StatementUtils.attemptClose( pstmt );
	    }
    }

    private static void usage()
    {
	System.err.println("java " +
			   "-Djdbc.drivers=<comma_sep_list_of_drivers> " +
			   PSLoadPoolBackedDataSource.class.getName() +
			   " <jdbc_url> [<username> <password>]" );
	System.exit(-1);
    }
}
