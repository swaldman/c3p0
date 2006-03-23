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

public final class ProxyWrappersTest
{
    public static void main(String[] argv)
    {
	ComboPooledDataSource cpds = null;
	Connection c               = null;
	try
	    {
		cpds = new ComboPooledDataSource();
		cpds.setDriverClass( "org.postgresql.Driver" );
		cpds.setJdbcUrl( "jdbc:postgresql://localhost/c3p0-test" );
		cpds.setUser("swaldman");
		cpds.setPassword("test");
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





