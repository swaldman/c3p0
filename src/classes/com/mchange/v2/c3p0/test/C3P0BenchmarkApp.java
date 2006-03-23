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

public final class C3P0BenchmarkApp
{
    final static String EMPTY_TABLE_CREATE = "CREATE TABLE emptyyukyuk (a varchar(8), b varchar(8))";
    final static String EMPTY_TABLE_SELECT = "SELECT * FROM emptyyukyuk";
    final static String EMPTY_TABLE_DROP   = "DROP TABLE emptyyukyuk";
    
    final static String EMPTY_TABLE_CONDITIONAL_SELECT = "SELECT * FROM emptyyukyuk where a = ?";

    final static String N_ENTRY_TABLE_CREATE = "CREATE TABLE n_entryyukyuk (a INTEGER)";
    final static String N_ENTRY_TABLE_INSERT = "INSERT INTO n_entryyukyuk VALUES ( ? )";
    final static String N_ENTRY_TABLE_SELECT = "SELECT * FROM n_entryyukyuk";
    final static String N_ENTRY_TABLE_DROP   = "DROP TABLE n_entryyukyuk";

    //final static int NUM_ITERATIONS = 20;
    final static int NUM_ITERATIONS = 2000;
    //final static int NUM_ITERATIONS = 10000;
    //final static int NUM_ITERATIONS = 20000;
    //final static int NUM_ITERATIONS = 100000;

    public static void main(String[] argv)
    {
//      com.mchange.v2.log.MLog.getLogger( C3P0BenchmarkApp.class ).info("this is some info.");
// 	com.mchange.v2.log.MLog.getLogger( C3P0BenchmarkApp.class ).log(com.mchange.v2.log.MLevel.WARNING, "this is a warning.", new Exception("test"));
// 	com.mchange.v2.log.MLog.getLogger( C3P0BenchmarkApp.class ).log(com.mchange.v2.log.MLevel.FINE, "this is fine.");

// 	System.getProperties().put("sprong", java.awt.Color.blue);
// 	System.getProperties().put(java.awt.Color.blue, "sprong");


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

//  		ds_unpooled = DriverManagerDataSourceFactory.create(jdbc_url, username, password);

//  		ds_pooled
//  //  		    = PoolBackedDataSourceFactory.create(jdbc_url, username, password);
//      		    = PoolBackedDataSourceFactory.create(jdbc_url, 
//      							 username, 
//      							 password,
//      							 5,
//      							 20,
//      							 5,
//      							 0,
//      							 100 );

		ds_unpooled = DataSources.unpooledDataSource(jdbc_url, username, password);
		//ds_pooled = DataSources.pooledDataSource( ds_unpooled );

   		//DataSource ds_unpooled_screwy = C3P0TestUtils.unreliableCommitDataSource( ds_unpooled );
   		//ds_pooled = DataSources.pooledDataSource( ds_unpooled_screwy );

// 		PoolConfig pc = new PoolConfig();
// 		pc.setMaxStatements(200);
// 		pc.setCheckoutTimeout(500);
//  		ds_pooled = DataSources.pooledDataSource( ds_unpooled, pc );
//  		ds_pooled = DataSources.pooledDataSource( ds_unpooled, "foo", "goo" );

		//ComboPooledDataSource cpds = new ComboPooledDataSource("dumbTestConfig");
 		ComboPooledDataSource cpds = new ComboPooledDataSource();
 		cpds.setJdbcUrl( jdbc_url );
 		cpds.setUser( username );
 		cpds.setPassword( password );
 		ds_pooled = cpds;

 		create(ds_pooled);

		System.out.println("Please wait. Tests can be very slow.");
		List l = new ArrayList();
 		l.add( new ConnectionAcquisitionTest() );
    		l.add( new StatementCreateTest() );
    		l.add( new StatementEmptyTableSelectTest() );
   		//l.add( new DataBaseMetaDataListNonexistentTablesTest() );
   		l.add( new PreparedStatementEmptyTableSelectTest() );
 		l.add( new PreparedStatementAcquireTest() );
   		l.add( new ResultSetReadTest() );
    		l.add( new FiveThreadPSQueryTestTest() );
		for (int i = 0, len = l.size(); i < len; ++i)
		    ((Test) l.get(i)).perform( ds_unpooled, ds_pooled, NUM_ITERATIONS );
	    }
	catch( Throwable t )
	    {
		System.err.print("Aborting tests on Throwable -- ");
		t.printStackTrace(); 
		if (t instanceof Error)
		    throw (Error) t;
	    }
	finally
	    {
		//System.err.println( "pooled data sources: " + C3P0Registry.getPooledDataSources() );

 		try { drop(ds_pooled); }
		catch (Exception e)
		    { e.printStackTrace(); }

 		try { DataSources.destroy(ds_pooled); }
		catch (Exception e)
		    { e.printStackTrace(); }

 		try { DataSources.destroy(ds_unpooled); }
		catch (Exception e)
		    { e.printStackTrace(); }
	    }
    }

    private static void usage()
    {
	System.err.println("java " +
			   "-Djdbc.drivers=<comma_sep_list_of_drivers> " +
			   C3P0BenchmarkApp.class.getName() +
			   " <jdbc_url> [<username> <password>]" );
	System.exit(-1);
    }

    static void create(DataSource ds)
	throws SQLException
    {
	System.err.println("Creating test schema.");
	Connection        con = null;
	PreparedStatement ps1 = null;
	PreparedStatement ps2 = null;
	PreparedStatement ps3 = null;
	try 
	    { 
		con = ds.getConnection();
		ps1 = con.prepareStatement(EMPTY_TABLE_CREATE);
		ps2 = con.prepareStatement(N_ENTRY_TABLE_CREATE);
		ps3 = con.prepareStatement(N_ENTRY_TABLE_INSERT);

		ps1.executeUpdate();
		ps2.executeUpdate();

  		for (int i = 0; i < NUM_ITERATIONS; ++i)
   		    {
   			ps3.setInt(1, i );
   			ps3.executeUpdate();
   			System.err.print('.');
   		    }
		System.err.println();
		System.err.println("Test schema created.");
	    }
	finally
	    {
		StatementUtils.attemptClose( ps1 );
		StatementUtils.attemptClose( ps2 );
		StatementUtils.attemptClose( ps3 );
		ConnectionUtils.attemptClose( con ); 
	    }
    }

    static void drop(DataSource ds)
	throws SQLException
    {
	Connection con        = null;
	PreparedStatement ps1 = null;
	PreparedStatement ps2 = null;
	try 
	    { 
		con = ds.getConnection();
		ps1 = con.prepareStatement(EMPTY_TABLE_DROP);
		ps2 = con.prepareStatement(N_ENTRY_TABLE_DROP);

		ps1.executeUpdate();
		ps2.executeUpdate();

		// should be superfluous 'cuz should be autocommit
		//con.commit();

		System.err.println("Test schema dropped.");
	    }
	finally
	    {
		StatementUtils.attemptClose( ps1 );
		StatementUtils.attemptClose( ps2 );
		ConnectionUtils.attemptClose( con ); 
	    }
    }

    static abstract class Test
    {
	String name;
	
	Test(String name)
	{ this.name = name; }

	public void perform(DataSource unpooled, DataSource pooled, int iterations) throws Exception
	{
	    double msecs_unpooled = test(unpooled, iterations) / ((double) iterations);
	    double msecs_pooled = test(pooled, iterations) / ((double) iterations);
	    System.out.println(name + " [ " + iterations + " iterations ]:");
	    System.out.println('\t' + "unpooled: " + msecs_unpooled + " msecs");
	    System.out.println('\t' + "  pooled: " + msecs_pooled + " msecs");
	    System.out.println('\t' + "speed-up factor: " + msecs_unpooled / msecs_pooled + " times");
	    System.out.println('\t' + "speed-up absolute: " + (msecs_unpooled - msecs_pooled)  + 
			       " msecs");
	    System.out.println();

// 	    PooledDataSource pds = (PooledDataSource) pooled;
// 	    System.out.println( pds.getNumConnections() );
// 	    System.out.println( pds.getNumIdleConnections() );
// 	    System.out.println( pds.getNumBusyConnections() );
// 	    System.out.println( pds.getNumConnectionsAllAuths() );
	}

	protected abstract long test(DataSource ds, int n) throws Exception;
    }

    static class ConnectionAcquisitionTest extends Test
    {
	ConnectionAcquisitionTest()
	{ super("Connection Acquisition and Cleanup"); }

	protected long test(DataSource ds, int n) throws Exception
	{
	    long start;
	    long end;
	    
	    start = System.currentTimeMillis();
	    for (int i = 0; i < n; ++i)
		{
		    Connection con = null;
		    try
			{ con = ds.getConnection(); }
		    finally
			{ ConnectionUtils.attemptClose( con ); }
		    //System.err.print(i + "\t");
		}
	    end = System.currentTimeMillis();
	    return end - start;
	}
    }

    static class StatementCreateTest extends Test
    {
	StatementCreateTest()
	{ super("Statement Creation and Cleanup"); }

	protected long test(DataSource ds, int n) throws SQLException
	{
	    Connection con = null;
	    try 
		{ 
		    con = ds.getConnection();
		    return test( con , n );
		}
	    finally
		{ ConnectionUtils.attemptClose( con ); }
	}

	long test(Connection con, int n) throws SQLException
	{ 
	    long start;
	    long end;
	    
	    Statement stmt = null;
	    start = System.currentTimeMillis();
	    for (int i = 0; i < n; ++i)
		{
		    try
			{ stmt = con.createStatement();	}
		    finally
			{ StatementUtils.attemptClose( stmt ); }
		}
	    end = System.currentTimeMillis();
	    return end - start;
	}
    }


    static class StatementEmptyTableSelectTest extends Test
    {
	StatementEmptyTableSelectTest()
	{ super("Empty Table Statement Select (on a single Statement)"); }

	protected long test(DataSource ds, int n) throws SQLException
	{
	    Connection con  = null;
	    Statement  stmt = null;
	    try 
		{ 
		    con = ds.getConnection();
		    stmt = con.createStatement();
		    //System.err.println( stmt.getClass().getName() );
		    return test( stmt , n );
		}
	    finally
		{ 
		    StatementUtils.attemptClose( stmt ); 
		    ConnectionUtils.attemptClose( con ); 
		}
	}

	long test(Statement stmt, int n) throws SQLException
	{ 
	    long start;
	    long end;
	    
	    start = System.currentTimeMillis();
	    for (int i = 0; i < n; ++i)
		stmt.executeQuery(EMPTY_TABLE_SELECT).close();
	    end = System.currentTimeMillis();
	    return end - start;
	}
    }

    static class DataBaseMetaDataListNonexistentTablesTest extends Test
    {
	DataBaseMetaDataListNonexistentTablesTest()
	{ super("DataBaseMetaDataListNonexistentTablesTest"); }

	protected long test(DataSource ds, int n) throws SQLException
	{
	    Connection con  = null;
	    Statement  stmt = null;
	    try 
		{ 
		    con = ds.getConnection();
		    return test( con , n );
		}
	    finally
		{ 
		    StatementUtils.attemptClose( stmt ); 
		    ConnectionUtils.attemptClose( con ); 
		}
	}

	long test(Connection con, int n) throws SQLException
	{ 
	    ResultSet rs = null;

	    try
		{
		    long start;
		    long end;
		    
		    start = System.currentTimeMillis();
		    for (int i = 0; i < n; ++i)
			rs = con.getMetaData().getTables( null, 
							null, 
							"PROBABLYNOT", 
							new String[] {"TABLE"} );
		    end = System.currentTimeMillis();
		    return end - start;
		}
	finally
	    { ResultSetUtils.attemptClose( rs ); }
	}
    }

    static class PreparedStatementAcquireTest extends Test
    {
	PreparedStatementAcquireTest()
	{ super("Acquire and Cleanup a PreparedStatement (same statement, many times)"); }

	protected long test(DataSource ds, int n) throws SQLException
	{
	    long start;
	    long end;
	    
	    Connection        con   = null;
	    PreparedStatement pstmt = null;
	    try 
		{ 
		    con = ds.getConnection();
		    start = System.currentTimeMillis();
		    for (int i = 0; i < n; ++i)
			{
			    try
   				{ pstmt = con.prepareStatement(EMPTY_TABLE_CONDITIONAL_SELECT); }

/*
    Leftover random abuses from ad hoc testing...

 				{
 				    pstmt = con.prepareStatement(EMPTY_TABLE_CONDITIONAL_SELECT, 
 								 ResultSet.TYPE_SCROLL_SENSITIVE, 
 								 ResultSet.CONCUR_UPDATABLE, 
 								 ResultSet.HOLD_CURSORS_OVER_COMMIT);
 				}


  				{ pstmt = con.prepareStatement(N_ENTRY_TABLE_INSERT); }
*/
			    finally
				{ StatementUtils.attemptClose( pstmt ); }
			}
		    end = System.currentTimeMillis();
		    return end - start;
		}
	    finally
		{ ConnectionUtils.attemptClose( con ); 	}
	}
    }

    static class PreparedStatementEmptyTableSelectTest extends Test
    {
	PreparedStatementEmptyTableSelectTest()
	{ super("Empty Table PreparedStatement Select (on a single PreparedStatement)"); }

	protected long test(DataSource ds, int n) throws SQLException
	{
	    Connection        con   = null;
	    PreparedStatement pstmt = null;
	    try 
		{ 
		    con = ds.getConnection();
		    pstmt = con.prepareStatement(EMPTY_TABLE_SELECT);

// 		    Leftover from ad-hoc testing...
//
// 		    pstmt = con.prepareStatement(EMPTY_TABLE_SELECT, 
// 						 ResultSet.TYPE_SCROLL_SENSITIVE, 
// 						 ResultSet.CONCUR_UPDATABLE, 
// 						 ResultSet.HOLD_CURSORS_OVER_COMMIT);
		    return test( pstmt , n );
		}
	    finally
		{ 
		    StatementUtils.attemptClose( pstmt ); 
		    ConnectionUtils.attemptClose( con ); 
		}
	}

	long test(PreparedStatement pstmt, int n) throws SQLException
	{ 
	    long start;
	    long end;
	    
	    start = System.currentTimeMillis();
	    for (int i = 0; i < n; ++i)
		pstmt.executeQuery().close();
	    end = System.currentTimeMillis();
	    return end - start;
	}
    }

    static class ResultSetReadTest extends Test
    {
  	ResultSetReadTest()
  	{ super("Reading one row / one entry from a result set"); }

  	protected long test(DataSource ds, int n) throws SQLException
  	{
	    if (n > 10000)
		throw new IllegalArgumentException("10K max.");

  	    long start;
  	    long end;
	    
  	    Connection        con   = null;
  	    PreparedStatement pstmt = null;
  	    ResultSet         rs    = null;
	    
	    try
		{
		    con = ds.getConnection();
		    pstmt = con.prepareStatement(N_ENTRY_TABLE_SELECT);
		    rs = pstmt.executeQuery();

		    start = System.currentTimeMillis();
		    for (int i = 0; i < n; ++i)
			{
			    if (! rs.next() )
				System.err.println("huh?");
			    rs.getInt(1);
			}
		    end = System.currentTimeMillis();
		    return end - start;
		}
	    finally
		{ 
		    ResultSetUtils.attemptClose( rs ); 
		    StatementUtils.attemptClose( pstmt ); 
		    ConnectionUtils.attemptClose( con ); 
		}
	}
    }

    static class FiveThreadPSQueryTestTest extends Test
    {
	// only for stupid test to simulate (illegal) concurrent access to a Statement
// 	volatile Statement stmt;

  	FiveThreadPSQueryTestTest()
  	{ 
	    super( "Five threads getting a connection, executing a query, " + 
		   System.getProperty( "line.separator" ) +
		   "and retrieving results concurrently via a prepared statement (in a transaction)." ); 
	}

  	protected long test(final DataSource ds, final int n) throws Exception
  	{
	    class QueryThread extends Thread
	    {
		QueryThread(int num)
		{ super("QueryThread-" + num);}

		public void run()
		{
		    Connection        con   = null;
		    PreparedStatement pstmt = null;
		    ResultSet         rs    = null;
		    
		    for (int i = 0; i < (n / 5); ++i)
			{
			    try
				{
				    con = ds.getConnection();

// 				    System.err.println("before txn isolation set: " + con.getTransactionIsolation());
// 				    con.setTransactionIsolation( Connection.TRANSACTION_SERIALIZABLE );
// 				    //con.setTransactionIsolation( Connection.TRANSACTION_READ_UNCOMMITTED );
// 				    System.err.println("after txn isolation set: " + con.getTransactionIsolation());

				    con.setAutoCommit( false );

				    pstmt = con.prepareStatement( EMPTY_TABLE_CONDITIONAL_SELECT );

// 				    if (Math.random() < 0.5)
// 					stmt = pstmt;
// 				    else if (stmt != null)
// 					stmt.getResultSet();

//  				    if (Math.random() < 0.1 && con instanceof C3P0ProxyConnection)
//  					con.close();

				    pstmt.setString(1, "boo");
				    rs = pstmt.executeQuery();
				    while( rs.next() )
					System.err.println("Huh?? Empty table has values?");
				    //System.out.println(this + "   " + i);

// 				    if (ds instanceof PooledDataSource)
// 					{
// 					    PooledDataSource pds = (PooledDataSource) ds;
// 					    System.err.println("numConnections: " + pds.getNumConnections() );
// 					    System.err.println("numIdleConnections: " + pds.getNumIdleConnections() );
// 					    System.err.println("numBusyConnections: " + pds.getNumBusyConnections() );
// 					    System.err.println();
// 					}

				    con.commit();
				}
			    catch (Exception e)
				{ 
				    System.err.print("FiveThreadPSQueryTestTest exception -- ");
				    e.printStackTrace(); 
				    try { if (con != null) con.rollback(); }
				    catch (SQLException e2)
					{
					    System.err.print("Rollback on exception failed! -- ");
					    e2.printStackTrace();
					}
				}
			    finally
				{
				    ResultSetUtils.attemptClose( rs ); 
				    StatementUtils.attemptClose( pstmt );
				    ConnectionUtils.attemptClose( con ); 
				    con = null;

// 				    StatementUtils.attemptClose( pstmt ); //dup close
// 				    ConnectionUtils.attemptClose( con ); //dup close
// 				    try { System.err.println( pstmt.getConnection() ); } catch (Exception e) {e.printStackTrace();}
// 				    ResultSetUtils.attemptClose( rs ); 
				}
			}
		    //System.out.println(this + " finished.");
		}
	    }

	    long start = System.currentTimeMillis();

	    Thread[] ts = new Thread[5];
	    for (int i = 0; i < 5; ++i)
		{
		    ts[i] = new QueryThread(i);
		    ts[i].start();
		}
	    for (int i = 0; i < 5; ++i)
		ts[i].join();

	    return System.currentTimeMillis() - start;
	}

    }


//      static class TenByTwoResultSetReadTest extends Test
//      {
//  	TenByTwoResultSetReadTest()
//  	{ super("Reading all entryies from a 10 row 2 col result set"); }

//  	protected long test(DataSource ds, int n) throws SQLException
//  	{
//  	    long start;
//  	    long end;
	    
//  	    long start_ctrl;
//  	    long end_ctrl;

//  	    Connection        con   = null;
//  	    PreparedStatement pstmt = null;
//  	    ResultSet         rs    = null;
	    
//  	    start = System.currentTimeMillis();
//  	    for (int i = 0; i < n; ++i)
//  		{
//  		    try
//  			{
//  			    con = ds.getConnection();
//  			    pstmt = con.prepareStatement(N_ENTRY_TABLE_SELECT);
//  			    rs = pstmt.executeQuery();
//  			    while( rs.next() )
//  				{
//  				    rs.getInt(1);
//  				    rs.getInt(2);
//  				}
//  			}
//  		    finally
//  			{ 
//  			    ResultSetUtils.attemptClose( rs ); 
//  			    StatementUtils.attemptClose( pstmt ); 
//  			    ConnectionUtils.attemptClose( con ); 
//  			}
//  		}
//  	    end = System.currentTimeMillis();


//  	    start_ctrl = System.currentTimeMillis();
//  	    for (int i = 0; i < n; ++i)
//  		{
//  		    try
//  			{
//  			    con = ds.getConnection();
//  			    pstmt = con.prepareStatement(N_ENTRY_TABLE_SELECT);
//  			    rs = pstmt.executeQuery();
//  			}
//  		    finally
//  			{ 
//  			    ResultSetUtils.attemptClose( rs ); 
//  			    StatementUtils.attemptClose( pstmt ); 
//  			    ConnectionUtils.attemptClose( con ); 
//  			}
//  		}
//  	    end_ctrl = System.currentTimeMillis();

//  	    return (end - start) - (end_ctrl - start_ctrl);
//  	}
//      }
}





