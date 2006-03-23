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

import java.lang.reflect.Method;

import com.mchange.v2.c3p0.ComboPooledDataSource;
import com.mchange.v2.c3p0.C3P0ProxyConnection;
import com.mchange.v2.c3p0.C3P0ProxyStatement;
import com.mchange.v2.c3p0.util.TestUtils;

public final class RawConnectionOpTest
{
    public static void main(String[] argv)
    {
	try
	    {
		String jdbc_url    = null;
		String username    = null;
		String password    = null;
		
		if (argv.length == 3)
		    {
			jdbc_url   = argv[0];
			username   = argv[1];
			password   = argv[2];
		    }
		else if (argv.length == 1)
		    {
			jdbc_url   = argv[0];
			username   = null;
			password   = null;
		    }
		else
		    usage();
		
		if (! jdbc_url.startsWith("jdbc:") )
		    usage();
				    
		ComboPooledDataSource cpds = new ComboPooledDataSource();
		cpds.setJdbcUrl( jdbc_url );
		cpds.setUser( username );
		cpds.setPassword( password );
  		cpds.setMaxPoolSize( 10 );
//  		cpds.setUsesTraditionalReflectiveProxies( true );
				
		C3P0ProxyConnection conn = (C3P0ProxyConnection) cpds.getConnection();
		Method toStringMethod = Object.class.getMethod("toString", new Class[]{});
		Method identityHashCodeMethod = System.class.getMethod("identityHashCode", new Class[] {Object.class});
		System.out.println("rawConnection.toString() -> " + 
				   conn.rawConnectionOperation(toStringMethod, C3P0ProxyConnection.RAW_CONNECTION, new Object[]{}));
		Integer ihc = (Integer) conn.rawConnectionOperation(identityHashCodeMethod, null, new Object[]{C3P0ProxyConnection.RAW_CONNECTION});
		System.out.println("System.identityHashCode( rawConnection ) -> " + Integer.toHexString( ihc.intValue() ));

		C3P0ProxyStatement stmt = (C3P0ProxyStatement) conn.createStatement();
		System.out.println("rawStatement.toString() -> " + 
				   stmt.rawStatementOperation(toStringMethod, C3P0ProxyStatement.RAW_STATEMENT, new Object[]{}));
		Integer ihc2 = (Integer) stmt.rawStatementOperation(identityHashCodeMethod, null, new Object[]{C3P0ProxyStatement.RAW_STATEMENT});
		System.out.println("System.identityHashCode( rawStatement ) -> " + Integer.toHexString( ihc2.intValue() ));

		conn.close();	

  		for (int i = 0; i < 10; ++i)
  		    {
  			C3P0ProxyConnection check = null;
  			try
  			    {
  				check = (C3P0ProxyConnection) cpds.getConnection();
  				//System.err.println( TestUtils.samePhysicalConnection( conn, check ) );
  				System.err.println( TestUtils.physicalConnectionIdentityHashCode( check ) == ihc.intValue() );
  			    }
  			finally
  			    { /* if (check != null) check.close(); */ }
  		    }
	    }
	catch (Exception e)
	    { e.printStackTrace(); }
    }

    private static void usage()
    {
	System.err.println("java " + RawConnectionOpTest.class.getName() + " \\");
	System.err.println("\t<jdbc_driver_class> \\");
	System.err.println("\t<jdbc_url> [<username> <password>]");
	System.exit(-1);
    }
}
