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

public final class InterruptedBatchTest
{
    static DataSource ds_unpooled = null;
    static DataSource ds_pooled   = null;

    public static void main(String[] argv)
    {
        if (argv.length > 0)
        {
            System.err.println( C3P0BenchmarkApp.class.getName() + 
                                " now requires no args. Please set everything in standard c3p0 config files.");
            return;                    
        }

	try
	    {
		ds_unpooled = new DriverManagerDataSource();
 		ComboPooledDataSource cpds = new ComboPooledDataSource();
 		ds_pooled = cpds;

		attemptSetupTable();

		performTransaction( true );
		performTransaction( false );

		checkCount();
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
 		try { DataSources.destroy(ds_pooled); }
		catch (Exception e)
		    { e.printStackTrace(); }

 		try { DataSources.destroy(ds_unpooled); }
		catch (Exception e)
		    { e.printStackTrace(); }
	    }
    }

    public static void performTransaction(boolean throwAnException) throws SQLException
    {
	Connection        con        = null;
	PreparedStatement prepStat   = null;
	
	try
	    {
		con = ds_pooled.getConnection();
		con.setAutoCommit(false);

		prepStat = con.prepareStatement("INSERT INTO CG_TAROPT_LOG(CO_ID, ENTDATE, CS_SEQNO, DESCRIPTION) VALUES (?,?,?,?)");
		
		prepStat.setLong     (1, -665);
		prepStat.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
		prepStat.setInt      (3, 1);
		prepStat.setString   (4, "time: " + System.currentTimeMillis());
		
		prepStat.addBatch();
		
		if(throwAnException)
		    throw new NullPointerException("my exception");
		
		prepStat.executeBatch();
		
		con.commit();
	    }
	catch(Exception e)
	    {
		System.out.println("exception caught (NPE expected): " /* + e */);
		e.printStackTrace();
	    }
	finally
	    {
		try { if (prepStat != null) prepStat.close(); } catch (Exception e) { e.printStackTrace(); }
		try { con.close(); } catch (Exception e) { e.printStackTrace(); }
	    }		
    }

    private static void attemptSetupTable() throws Exception
    {
	Connection con = null;
	Statement stmt = null;
	try
	    {
		con = ds_pooled.getConnection();
		stmt = con.createStatement();
		try
		    {
			stmt.executeUpdate("CREATE TABLE CG_TAROPT_LOG ( CO_ID INTEGER, ENTDATE TIMESTAMP, CS_SEQNO INTEGER, DESCRIPTION VARCHAR(32) )");
		    }
		catch (SQLException e) 
		    {
			System.err.println("Table already constructed?");
			e.printStackTrace(); 
		    }

		stmt.executeUpdate("DELETE FROM CG_TAROPT_LOG");
	    }
	finally
	    {
		try { stmt.close(); } catch (SQLException e) { e.printStackTrace(); }
		try { con.close(); } catch (SQLException e) { e.printStackTrace(); }
	    }
    }

    private static void checkCount() throws Exception
    {
	Connection con  = null;
	Statement  stmt = null;
	ResultSet  rs   = null;
	try
	    {
		con  = ds_pooled.getConnection();
		stmt = con.createStatement();

		rs = stmt.executeQuery("SELECT COUNT(*) FROM CG_TAROPT_LOG");
		rs.next();
		System.out.println( rs.getInt(1) + " rows found. (one row expected.)" );
	    }
	finally
	    {
		try { stmt.close(); } catch (SQLException e) { e.printStackTrace(); }
		try { con.close(); } catch (SQLException e) { e.printStackTrace(); }
	    }
    }
}





