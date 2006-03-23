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

import java.sql.*;
import javax.sql.*;
import com.mchange.v2.c3p0.*;
import com.mchange.v1.db.sql.*;
import javax.naming.Reference;
import javax.naming.Referenceable;
import com.mchange.v2.naming.ReferenceableUtils;
import com.mchange.v2.ser.SerializableUtils;
import com.mchange.v2.c3p0.DriverManagerDataSource;
import com.mchange.v2.c3p0.PoolBackedDataSource;


public final class TestRefSerStuff
{
    static void create(DataSource ds) throws SQLException
    {
	Connection con = null;
	Statement stmt = null;
	try
	    {
		con = ds.getConnection();
		stmt = con.createStatement();
		stmt.executeUpdate("CREATE TABLE TRSS_TABLE ( a_col VARCHAR(16) )");
	    }
	finally
	    {
		StatementUtils.attemptClose( stmt );
		ConnectionUtils.attemptClose( con );
	    }
    }

    static void drop(DataSource ds) throws SQLException
    {
	Connection con = null;
	Statement stmt = null;
	try
	    {
		con = ds.getConnection();
		stmt = con.createStatement();
		stmt.executeUpdate("DROP TABLE TRSS_TABLE");
	    }
	finally
	    {
		StatementUtils.attemptClose( stmt );
		ConnectionUtils.attemptClose( con );
	    }
    }

    static void doSomething(DataSource ds) throws SQLException
    {
	Connection con = null;
	Statement stmt = null;
	try
	    {
		con = ds.getConnection();
		stmt = con.createStatement();
		int i = stmt.executeUpdate("INSERT INTO TRSS_TABLE VALUES ('" + 
					   System.currentTimeMillis() + "')");
		if (i != 1)
		    throw new SQLException("Insert failed somehow strange!");
	    }
	finally
	    {
		StatementUtils.attemptClose( stmt );
		ConnectionUtils.attemptClose( con );
	    }
    }

    private static void usage()
    {
	System.err.println("java " +
			   "-Djdbc.drivers=<comma_sep_list_of_drivers> " +
			   TestRefSerStuff.class.getName() +
			   " <jdbc_url> [<username> <password>]" );
	System.exit(-1);
    }

    static void doTest(DataSource checkMe) throws Exception
    {
	doSomething( checkMe );
	System.err.println("\tcreated: " + checkMe);
	DataSource afterSer = (DataSource) SerializableUtils.testSerializeDeserialize( checkMe );
	doSomething( afterSer );
	System.err.println("\tafter ser: " + afterSer );
	Reference ref = ((Referenceable) checkMe).getReference();
//  		    System.err.println("ref: " + ref);
//  		    System.err.println("Factory Class: " + ref.getFactoryClassName());
	DataSource afterRef = (DataSource) ReferenceableUtils.referenceToObject( ref, 
										 null, 
										 null, 
										 null );
//  		    System.err.println("afterRef data source: " + afterRef);
	doSomething( afterRef );
	System.err.println("\tafter ref: " + afterRef );
    }

    public static void main( String[] argv )
    {
	String jdbcUrl = null;
	String username = null;
	String password = null;
	if (argv.length == 3)
	    {
		jdbcUrl = argv[0];
		username = argv[1];
		password = argv[2];
	    }
	else if (argv.length == 1)
	    {
		jdbcUrl = argv[0];
		username = null;
		password = null;
	    }
	else
	    usage();
	
	if (! jdbcUrl.startsWith("jdbc:") )
	    usage();
	
	
	try
	    {
		DriverManagerDataSource dmds = new DriverManagerDataSource();
		dmds.setJdbcUrl( jdbcUrl );
		dmds.setUser( username );
		dmds.setPassword( password );
		try { drop( dmds ); }
		catch (Exception e)
		    { /* Ignore */ }
		create( dmds );

		System.err.println("DriverManagerDataSource:");
		doTest( dmds );
		
		WrapperConnectionPoolDataSource wcpds = new WrapperConnectionPoolDataSource();
		wcpds.setNestedDataSource( dmds );
		PoolBackedDataSource pbds = new PoolBackedDataSource();
		pbds.setConnectionPoolDataSource( wcpds );
		
		System.err.println("PoolBackedDataSource:");
		doTest( pbds );
	    }
	catch ( Exception e )
	    { e.printStackTrace(); }
    }

}
