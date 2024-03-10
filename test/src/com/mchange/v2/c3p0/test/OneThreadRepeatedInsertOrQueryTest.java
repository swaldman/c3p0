package com.mchange.v2.c3p0.test;

import java.util.*;
import java.sql.*;
import javax.sql.*;
import com.mchange.v2.c3p0.*;
import com.mchange.v1.db.sql.*;

public final class OneThreadRepeatedInsertOrQueryTest
{
    final static String INSERT_STMT = "INSERT INTO testpbds VALUES ( ? , ? )";
    final static String SELECT_STMT = "SELECT count(*) FROM testpbds";

    static Random random = new Random();
    static DataSource ds;

    public static void main(String[] argv)
    {

        /*
          Let's get this stuff from c3p0.properties or sysprops
        
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
        */
	
	try
	    {
		//DataSource ds_unpooled = DataSources.unpooledDataSource(jdbc_url, username, password);
		DataSource ds_unpooled = DataSources.unpooledDataSource();
		ds = DataSources.pooledDataSource( ds_unpooled );

		Connection con = null;
		Statement stmt = null;

		try
		    {
			con = ds.getConnection();
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

		while(true)
		    {
			con = null;
			try
			    {
				con = ds.getConnection();
				boolean select = random.nextBoolean();
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
			   OneThreadRepeatedInsertOrQueryTest.class.getName() +
			   " <jdbc_url> [<username> <password>]" );
	System.exit(-1);
    }
}
