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

public final class JndiLookupTest
{
    public static void main(String[] argv)
    {
	try
	    {
		
		String dmds_name  = null;
		String cpds_name = null;
		String pbds_name = null;

		if (argv.length == 3)
		    {
			dmds_name = argv[0];
			cpds_name = argv[1];
			pbds_name = argv[2];
		    }
		else
		    usage();

		InitialContext ctx = new InitialContext();
		DataSource dmds = (DataSource) ctx.lookup( dmds_name );
		dmds.getConnection().close();
		System.out.println( "DriverManagerDataSource " + dmds_name + 
				    " sucessfully looked up and checked.");
		ConnectionPoolDataSource cpds = (ConnectionPoolDataSource) ctx.lookup( cpds_name );
		cpds.getPooledConnection().close();
		System.out.println( "ConnectionPoolDataSource " + cpds_name + 
				    " sucessfully looked up and checked.");
		DataSource pbds = (DataSource) ctx.lookup( pbds_name );
		pbds.getConnection().close();
		System.out.println( "PoolBackedDataSource " + pbds_name + 
				    " sucessfully looked up and checked.");
	    }
	catch (Exception e)
	    { e.printStackTrace(); }
    }

    private static void usage()
    {
	System.err.println("java " + 
			   JndiLookupTest.class.getName() + " \\");
	System.err.println("\t<dmds_name> <wcpds_name> <wpbds_name>" );
	System.exit(-1);
    }
}
