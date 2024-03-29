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
