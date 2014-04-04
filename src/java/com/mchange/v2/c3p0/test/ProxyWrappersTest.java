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





