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


package com.mchange.v2.c3p0.test.junit;

import com.mchange.v2.c3p0.*;
import junit.framework.TestCase;

public abstract class C3P0JUnitTestCaseBase extends TestCase
{
    protected ComboPooledDataSource cpds;

    protected void setUp() 
    {
	String url      = System.getProperty("c3p0.test.jdbc.url");
	String user     = System.getProperty("c3p0.test.jdbc.user");
	String password = System.getProperty("c3p0.test.jdbc.password");

	//C3P0JUnitTestConfig.loadDrivers();
	cpds = new ComboPooledDataSource();
	cpds.setJdbcUrl( url );
	cpds.setUser( user );
	cpds.setPassword( password );
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
