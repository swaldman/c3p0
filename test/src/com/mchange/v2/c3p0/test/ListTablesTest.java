package com.mchange.v2.c3p0.test;

import java.sql.*;
import javax.sql.*;
import javax.naming.*;
import com.mchange.v1.db.sql.*;

public final class ListTablesTest
{
    public static void main(String[] argv)
    {
	try
	    {
		InitialContext ctx = new InitialContext();
		DataSource ds = (DataSource) ctx.lookup(argv[0]);
		System.err.println( ds.getClass() );
		Connection con = null;
		ResultSet  rs  = null;
		try
		    {
			con = ds.getConnection();
			DatabaseMetaData md = con.getMetaData();
			rs = md.getTables( null, null, "%", null);
			while (rs.next())
			    System.out.println(rs.getString(3));
		    }
		finally
		    {
			ResultSetUtils.attemptClose( rs );
			ConnectionUtils.attemptClose( con );
		    }
	    }
	catch (Exception e)
	    { e.printStackTrace(); }
    }
}
