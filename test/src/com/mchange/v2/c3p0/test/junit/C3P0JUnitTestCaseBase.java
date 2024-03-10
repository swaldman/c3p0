package com.mchange.v2.c3p0.test.junit;

import com.mchange.v2.c3p0.*;
import junit.framework.TestCase;

public abstract class C3P0JUnitTestCaseBase extends TestCase
{
    protected ComboPooledDataSource cpds;

    protected void setUp() 
    {
        //we let this stuff get setup in c3p0.properties now
        
        /*
	String url      = System.getProperty("c3p0.test.jdbc.url");
	String user     = System.getProperty("c3p0.test.jdbc.user");
	String password = System.getProperty("c3p0.test.jdbc.password");
         */

	//C3P0JUnitTestConfig.loadDrivers();
	cpds = new ComboPooledDataSource();
    
    /*
	cpds.setJdbcUrl( url );
	cpds.setUser( user );
	cpds.setPassword( password );
    */
    }

    protected void tearDown() 
    { 
	try { cpds.close(); }
	catch ( Exception e )
	    {
		System.err.println("Exception on DataSource close in JUnit test tearDown():");
		e.printStackTrace(); 
	    }
    }
}
