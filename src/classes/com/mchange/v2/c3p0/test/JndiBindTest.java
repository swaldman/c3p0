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

import javax.naming.*;
import javax.sql.*;
import com.mchange.v2.c3p0.*;

public final class JndiBindTest
{
    public static void main(String[] argv)
    {
	try
	    {
		String driverClass = null;
		String jdbc_url    = null;
		String username    = null;
		String password    = null;
		String dmds_name   = null;
		String cpds_name   = null;
		String pbds_name   = null;

		if (argv.length == 7)
		    {
			driverClass = argv[0];
			jdbc_url   = argv[1];
			username   = argv[2];
			password   = argv[3];
			dmds_name  = argv[4];
			cpds_name = argv[5];
			pbds_name = argv[6];
		    }
		else if (argv.length == 5)
		    {
			driverClass = argv[0];
			jdbc_url   = argv[1];
			username   = null;
			password   = null;
			dmds_name  = argv[2];
			cpds_name = argv[3];
			pbds_name = argv[4];
		    }
		else
		    usage();

		if (! jdbc_url.startsWith("jdbc:") )
		    usage();

		DataSource dmds = DriverManagerDataSourceFactory.create( driverClass,
									 jdbc_url, 
									 username, 
									 password );
		WrapperConnectionPoolDataSource cpds = new WrapperConnectionPoolDataSource();
		cpds.setNestedDataSource(dmds);
		DataSource pbds = PoolBackedDataSourceFactory.create( driverClass,
								      jdbc_url, 
								      username, 
								      password );

		InitialContext ctx = new InitialContext();
		ctx.rebind( dmds_name , dmds );
		System.out.println( "DriverManagerDataSource bounds as " + dmds_name );
		ctx.rebind( cpds_name , cpds );
		System.out.println( "ConnectionPoolDataSource bounds as " + cpds_name );
		ctx.rebind( pbds_name , pbds );
		System.out.println( "PoolDataSource bounds as " + pbds_name );
	    }
	catch (Exception e)
	    { e.printStackTrace(); }
    }

    private static void usage()
    {
	System.err.println("java " + JndiBindTest.class.getName() + " \\");
	System.err.println("\t<jdbc_driver_class> \\");
	System.err.println("\t<jdbc_url> [<username> <password>] \\");
	System.err.println("\t<dmds_name> <cpds_name> <pbds_name>" );
	System.exit(-1);
    }
}
