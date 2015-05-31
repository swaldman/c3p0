/*
 * Distributed as part of c3p0 v.0.9.5.1
 *
 * Copyright (C) 2015 Machinery For Change, Inc.
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

import java.sql.*;
import javax.sql.DataSource;
import com.mchange.v2.c3p0.DataSources;


/**
 *  This example shows how to programmatically get and directly use
 *  an pool-backed DataSource
 */
public final class UsePoolBackedDataSource
{

    public static void main(String[] argv)
    {
	try
	    {
		// Note: your JDBC driver must be loaded [via Class.forName( ... ) or -Djdbc.properties]
		// prior to acquiring your DataSource!

		// Acquire the DataSource... this is the only c3p0 specific code here
		DataSource unpooled = DataSources.unpooledDataSource("jdbc:postgresql://localhost/test",
								     "swaldman",
								     "test");
		DataSource pooled = DataSources.pooledDataSource( unpooled );



		// get hold of a Connection an do stuff, in the usual way
		Connection con  = null;
		Statement  stmt = null;
		ResultSet  rs   = null;
		try
		    {
			con = pooled.getConnection();
			stmt = con.createStatement();
			rs = stmt.executeQuery("SELECT * FROM foo");
			while (rs.next())
			    System.out.println( rs.getString(1) );
		    }
		finally
		    {
			//i try to be neurotic about ResourceManagement,
			//explicitly closing each resource
			//but if you are in the habit of only closing
			//parent resources (e.g. the Connection) and
			//letting them close their children, all
			//c3p0 DataSources will properly deal.
			attemptClose(rs);
			attemptClose(stmt);
			attemptClose(con);
		    }
	    }
	catch (Exception e)
	    { e.printStackTrace(); }
    }

    static void attemptClose(ResultSet o)
    {
	try
	    { if (o != null) o.close();}
	catch (Exception e)
	    { e.printStackTrace();}
    }

    static void attemptClose(Statement o)
    {
	try
	    { if (o != null) o.close();}
	catch (Exception e)
	    { e.printStackTrace();}
    }

    static void attemptClose(Connection o)
    {
	try
	    { if (o != null) o.close();}
	catch (Exception e)
	    { e.printStackTrace();}
    }

    private UsePoolBackedDataSource()
    {}
}
