/*
 * Distributed as part of c3p0 v.0.8.5-pre7a
 *
 * Copyright (C) 2004 Machinery For Change, Inc.
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


package com.mchange.v2.c3p0.impl;

import java.sql.*;
import java.util.*;
import com.mchange.v2.c3p0.advanced.QueryConnectionTester;
import com.mchange.v1.db.sql.ResultSetUtils;
import com.mchange.v1.db.sql.StatementUtils;

public class DefaultConnectionTester implements QueryConnectionTester
{
    final static int HASH_CODE = DefaultConnectionTester.class.getName().hashCode();

    final static Set INVALID_DB_STATES;

    static
    {
	Set temp = new HashSet();
	temp.add("08001"); //SQL State "Unable to connect to data source"
	temp.add("08007"); //SQL State "Connection failure during transaction"

	// MySql appently uses this state to indicate a stale, expired
	// connection when the database is fine, so we'll not presume
	// this SQL state signals an invalid database.
	//temp.add("08S01"); //SQL State "Communication link failure"

	INVALID_DB_STATES = Collections.unmodifiableSet( temp );
    }

    public int statusOnException(Connection c, Throwable t)
    {
	if (t instanceof SQLException)
	    { 
		String state = ((SQLException) t).getSQLState();
		if ( INVALID_DB_STATES.contains( state ) )
		    return DATABASE_IS_INVALID;
		else
		    return activeCheckConnection(c); 
	    }
	else //something is broke
	    return CONNECTION_IS_INVALID; 
    }

    public int activeCheckConnection(Connection c)
    {
	ResultSet rs = null;
	try
	    { 
		rs = c.getMetaData().getTables( null, 
						null, 
						"PROBABLYNOT", 
						new String[] {"TABLE"} );
		return CONNECTION_IS_OKAY;
	    }
	catch (SQLException e)
	    { 
		String state = e.getSQLState();
		if ( INVALID_DB_STATES.contains( state ) )
		    return DATABASE_IS_INVALID;
		else
		    return CONNECTION_IS_INVALID; 
	    }
	finally
	    { ResultSetUtils.attemptClose( rs ); }
    }

    public int activeCheckConnection(Connection c, String query)
    {
	Statement stmt = null;
	ResultSet rs   = null;
	try
	    { 
		stmt = c.createStatement();
		rs = stmt.executeQuery( query );
		//rs.next();
		return CONNECTION_IS_OKAY;
	    }
	catch (SQLException e)
	    { 
		String state = e.getSQLState();
		if ( INVALID_DB_STATES.contains( state ) )
		    return DATABASE_IS_INVALID;
		else
		    return CONNECTION_IS_INVALID; 
	    }
	finally
	    { 
		ResultSetUtils.attemptClose( rs ); 
		StatementUtils.attemptClose( stmt );
	    }
    }

    public boolean equals( Object o )
    { return ( o != null && o.getClass() == DefaultConnectionTester.class ); }
    
    public int hashCode()
    { return HASH_CODE; }
}

