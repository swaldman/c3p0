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

package com.mchange.v2.c3p0.test.junit;

import java.sql.*;
import java.util.*;
import junit.framework.*;

public final class ConnectionPropertiesResetJUnitTestCase extends C3P0JUnitTestCaseBase
{
    final static Map TM;

    static
    {
	Map tmp = new HashMap();
	tmp.put("FAKE", SQLData.class);
	TM = Collections.unmodifiableMap( tmp );
    }

    public void testAllConnectionDefaultsReset()
    {
// 	System.err.println("XOXO err");
// 	System.out.println("XOXO out");

	cpds.setInitialPoolSize(5);
	cpds.setMinPoolSize(5);
	cpds.setMaxPoolSize(5);
	cpds.setMaxIdleTime(0);
	cpds.setTestConnectionOnCheckout(false);
	cpds.setTestConnectionOnCheckin(false);
	cpds.setIdleConnectionTestPeriod(0);

	String dfltCat;
	int dflt_txn_isolation;

	try
	    {
		Connection con = null;
		try
		    {
			con = cpds.getConnection();
			

			dfltCat = con.getCatalog();
			dflt_txn_isolation = con.getTransactionIsolation();

			try { con.setReadOnly(true); } catch (Exception e) { /* setReadOnly() not supported */ }
			try { con.setTypeMap(TM); } catch (Exception e) { /* setTypeMap() not supported */ }
			try { con.setCatalog("C3P0TestCatalogXXX"); } catch (Exception e) { /* setCatalog() not supported */ }
			try 
			    { 
				con.setTransactionIsolation( dflt_txn_isolation == Connection.TRANSACTION_SERIALIZABLE ? 
							     Connection.TRANSACTION_READ_COMMITTED : 
							     Connection.TRANSACTION_SERIALIZABLE ); 
			    } 
			catch (Exception e) { /* setTransactionIsolation() not fully supported */ }
		    }
		finally
		    { 
			try { if (con != null) con.close(); }
			catch (Exception e) {}
		    }

		Connection[] cons = new Connection[5];
		for (int i = 0; i < 5; ++i)
		    {
			cons[i] = cpds.getConnection();
			assertFalse( "Connection from pool should not be readOnly!", cons[i].isReadOnly() );

			// some drivers return null rather than an empty type map
			Map typeMap = cons[i].getTypeMap();
			assertTrue( "Connection from pool should have an empty type map!", (typeMap == null ? true : typeMap.isEmpty() ) ); 

			assertEquals( "Connection from pool should have default catalog set!", dfltCat, cons[i].getCatalog() );
			assertEquals( "Connection from pool should have default txn isolation set!", dflt_txn_isolation, cons[i].getTransactionIsolation() );
			cons[i].close();
		    }
	    }
	catch (Exception e)
	    {
		e.printStackTrace();
		fail( e.getMessage() );
	    }
    }
}
