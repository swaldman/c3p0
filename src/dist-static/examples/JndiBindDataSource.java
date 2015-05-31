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
import javax.naming.*;
import javax.sql.DataSource;
import com.mchange.v2.c3p0.DataSources;


/**
 *  This example shows how to acquire a c3p0 DataSource and
 *  bind it to a JNDI name service.
 */
public final class JndiBindDataSource
{
    // be sure to load your database driver class, either via 
    // Class.forName() [as shown below] or externally (e.g. by
    // using -Djdbc.drivers when starting your JVM).
    static
    {
	try 
	    { Class.forName( "org.postgresql.Driver" ); }
	catch (Exception e) 
	    { e.printStackTrace(); }
    }

    public static void main(String[] argv)
    {
	try
	    {
		// let a command line arg specify the name we will
		// bind our DataSource to.
		String jndiName = argv[0];

  		// acquire the DataSource using default pool params... 
  		// this is the only c3p0 specific code here
		DataSource unpooled = DataSources.unpooledDataSource("jdbc:postgresql://localhost/test",
								     "swaldman",
								     "test");
		DataSource pooled = DataSources.pooledDataSource( unpooled );

		// Create an InitialContext, and bind the DataSource to it in 
		// the usual way.
		//
		// We are using the no-arg version of InitialContext's constructor,
		// therefore, the jndi environment must be first set via a jndi.properties
		// file, System properties, or by some other means.
		InitialContext ctx = new InitialContext();
		ctx.rebind( jndiName, pooled );
		System.out.println("DataSource bound to nameservice under the name \"" +
				   jndiName + '\"');
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

    private JndiBindDataSource()
    {}
}
