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

    /*
    private static void usage()
    {
	System.err.println("java " +
			   "-Djdbc.drivers=<comma_sep_list_of_drivers> " +
			   TestRefSerStuff.class.getName() +
			   " <jdbc_url> [<username> <password>]" );
	System.exit(-1);
    }
    */

    static void doTest(DataSource checkMe) throws Exception
    {
	doSomething( checkMe );
	System.err.println("\tcreated:   " + checkMe);
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
        if (argv.length > 0)
        {
            System.err.println( TestRefSerStuff.class.getName() + 
                                " now requires no args. Please set everything in standard c3p0 config files.");
            return;                    
        }

        /*
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
	*/
	
	try
	    {
		DriverManagerDataSource dmds = new DriverManagerDataSource();
		//dmds.setJdbcUrl( jdbcUrl );
		//dmds.setUser( username );
		//dmds.setPassword( password );
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
        
        ComboPooledDataSource cpds = new ComboPooledDataSource();
        doTest( cpds );
	    }
	catch ( Exception e )
	    { e.printStackTrace(); }
    }

}
