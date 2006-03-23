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


package com.mchange.v2.c3p0.dbms;

import java.lang.reflect.*;
import java.sql.*;
import com.mchange.v2.c3p0.*;
import com.mchange.v2.sql.SqlUtils;
import oracle.sql.BLOB;
import oracle.sql.CLOB;
import oracle.jdbc.driver.OracleConnection;

/**
 *  A convenience class for OracleUsers who wish to use Oracle-specific Connection API
 *  without working directly with c3p0 raw connection operations.
 */
public final class OracleUtils
{
    final static Class[] CREATE_TEMP_ARGS = new Class[]{Connection.class, boolean.class, int.class};
    
    /**
     *  Uses Oracle-specific API on the raw, underlying Connection to create a temporary BLOB.
     *  <b>Users are responsible for calling freeTemporary on the returned BLOB prior to Connection close() / check-in!
     *  c3p0 will <i>not</i> automatically clean up temporary BLOBs.</b>
     *
     * @param c3p0ProxyCon may be a c3p0 proxy for an <tt>oracle.jdbc.driver.OracleConnection</tt>, or an 
     *        <tt>oracle.jdbc.driver.OracleConnection</tt> directly.
     */
    public static BLOB createTemporaryBLOB(Connection c3p0ProxyCon, boolean cache, int duration) throws SQLException
    { 
	if (c3p0ProxyCon instanceof C3P0ProxyConnection)
	    {
		try
		    {
			C3P0ProxyConnection castCon = (C3P0ProxyConnection) c3p0ProxyCon;
			Method m = BLOB.class.getMethod("createTemporary", CREATE_TEMP_ARGS);
			Object[] args = new Object[] {C3P0ProxyConnection.RAW_CONNECTION, Boolean.valueOf( cache ), new Integer( duration )};
			return (BLOB) castCon.rawConnectionOperation(m, null, args);			
		    }
		catch (InvocationTargetException e)
		    {
			if (Debug.DEBUG)
			    e.printStackTrace();
			throw SqlUtils.toSQLException( e.getTargetException() );
		    }
		catch (Exception e)
		    {
			if (Debug.DEBUG)
			    e.printStackTrace();
			throw SqlUtils.toSQLException( e );
		    }
	    }
	else if (c3p0ProxyCon instanceof OracleConnection)
	    return BLOB.createTemporary( c3p0ProxyCon, cache, duration );
	else
	    throw new SQLException("Cannot create an oracle BLOB from a Connection that is neither an oracle.jdbc.driver.Connection, " +
				   "nor a C3P0ProxyConnection wrapped around an oracle.jdbc.driver.Connection.");
    }
	
    /**
     *  Uses Oracle-specific API on the raw, underlying Connection to create a temporary CLOB.
     *  <b>Users are responsible for calling freeTemporary on the returned BLOB prior to Connection close() / check-in!
     *  c3p0 will <i>not</i> automatically clean up temporary CLOBs.</b>
     *
     * @param c3p0ProxyCon may be a c3p0 proxy for an <tt>oracle.jdbc.driver.OracleConnection</tt>, or an 
     *        <tt>oracle.jdbc.driver.OracleConnection</tt> directly.
     */
    public static CLOB createTemporaryCLOB(Connection c3p0ProxyCon, boolean cache, int duration) throws SQLException
    { 
	if (c3p0ProxyCon instanceof C3P0ProxyConnection)
	    {
		try
		    {
			C3P0ProxyConnection castCon = (C3P0ProxyConnection) c3p0ProxyCon;
			Method m = CLOB.class.getMethod("createTemporary", CREATE_TEMP_ARGS);
			Object[] args = new Object[] {C3P0ProxyConnection.RAW_CONNECTION, Boolean.valueOf( cache ), new Integer( duration )};
			return (CLOB) castCon.rawConnectionOperation(m, null, args);			
		    }
		catch (InvocationTargetException e)
		    {
			if (Debug.DEBUG)
			    e.printStackTrace();
			throw SqlUtils.toSQLException( e.getTargetException() );
		    }
		catch (Exception e)
		    {
			if (Debug.DEBUG)
			    e.printStackTrace();
			throw SqlUtils.toSQLException( e );
		    }
	    }
	else if (c3p0ProxyCon instanceof OracleConnection)
	    return CLOB.createTemporary( c3p0ProxyCon, cache, duration );
	else
	    throw new SQLException("Cannot create an oracle CLOB from a Connection that is neither an oracle.jdbc.driver.Connection, " +
				   "nor a C3P0ProxyConnection wrapped around an oracle.jdbc.driver.Connection.");
    }
	
    private OracleUtils()
    {}
}
