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
