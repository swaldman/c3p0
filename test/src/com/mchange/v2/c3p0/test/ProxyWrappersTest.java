package com.mchange.v2.c3p0.test;

import java.util.*;
import java.sql.*;
import javax.sql.*;
import com.mchange.v2.c3p0.*;
import com.mchange.v1.db.sql.*;

public final class ProxyWrappersTest
{
    public static void main(String[] argv)
    {
	ComboPooledDataSource cpds = null;
	Connection c               = null;
	try
	    {
		cpds = new ComboPooledDataSource();

		// let system properties or c3p0.properties set this stuff up
		// cpds.setDriverClass( "org.postgresql.Driver" );
		// cpds.setJdbcUrl( "jdbc:postgresql://localhost/c3p0-test" );
		// cpds.setUser("swaldman");
		// cpds.setPassword("test");
		
		cpds.setMinPoolSize(5);
		cpds.setAcquireIncrement(5);
		cpds.setMaxPoolSize(20);

		c = cpds.getConnection();
		c.setAutoCommit( false );
		Statement stmt = c.createStatement();
		stmt.executeUpdate("CREATE TABLE pwtest_table (col1 char(5), col2 char(5))");
		ResultSet rs = stmt.executeQuery("SELECT * FROM pwtest_table");
		System.err.println("rs: " + rs);
		System.err.println("rs.getStatement(): " + rs.getStatement());
		System.err.println("rs.getStatement().getConnection(): " + rs.getStatement().getConnection());
	    }
	catch( Exception e )
	    { e.printStackTrace(); }
	finally
	    {
		try { if (c!= null) c.rollback(); }
		catch (Exception e) { e.printStackTrace(); }
		try { if (cpds!= null) cpds.close(); }
		catch (Exception e) { e.printStackTrace(); }
	    }
    }
}





