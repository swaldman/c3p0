/*
 * Distributed as part of c3p0 v.0.8.4-test2
 *
 * Copyright (C) 2003 Machinery For Change, Inc.
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

import java.io.InputStream;
import java.io.Reader;
import java.lang.Object;
import java.lang.String;
import java.math.BigDecimal;
import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.Date;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Map;


abstract class C3P0CallableStatement extends C3P0PreparedStatement 
    implements CallableStatement
{
    final CallableStatement inner;

    public C3P0CallableStatement(CallableStatement inner)
    {
	super(inner);
	this.inner = inner;
    }

    public BigDecimal getBigDecimal(int a) throws SQLException
    {
        return inner.getBigDecimal(a);
    }

    public BigDecimal getBigDecimal(int a, int b) throws SQLException
    {
        return inner.getBigDecimal(a, b);
    }

    public String getString(int a) throws SQLException
    {
        return inner.getString(a);
    }

    public Clob getClob(int a) throws SQLException
    {
        return inner.getClob(a);
    }

    public double getDouble(int a) throws SQLException
    {
        return inner.getDouble(a);
    }

    public Timestamp getTimestamp(int a, Calendar b) throws SQLException
    {
        return inner.getTimestamp(a, b);
    }

    public Timestamp getTimestamp(int a) throws SQLException
    {
        return inner.getTimestamp(a);
    }

    public byte[] getBytes(int a) throws SQLException
    {
        return inner.getBytes(a);
    }

    public int getInt(int a) throws SQLException
    {
        return inner.getInt(a);
    }

    public Date getDate(int a, Calendar b) throws SQLException
    {
        return inner.getDate(a, b);
    }

    public Date getDate(int a) throws SQLException
    {
        return inner.getDate(a);
    }

    public Object getObject(int a) throws SQLException
    {
        return inner.getObject(a);
    }

    public Object getObject(int a, Map b) throws SQLException
    {
        return inner.getObject(a, b);
    }

    public byte getByte(int a) throws SQLException
    {
        return inner.getByte(a);
    }

    public Array getArray(int a) throws SQLException
    {
        return inner.getArray(a);
    }

    public boolean wasNull() throws SQLException
    {
        return inner.wasNull();
    }

    public boolean getBoolean(int a) throws SQLException
    {
        return inner.getBoolean(a);
    }

    public float getFloat(int a) throws SQLException
    {
        return inner.getFloat(a);
    }

    public Blob getBlob(int a) throws SQLException
    {
        return inner.getBlob(a);
    }

    public long getLong(int a) throws SQLException
    {
        return inner.getLong(a);
    }

    public Time getTime(int a) throws SQLException
    {
        return inner.getTime(a);
    }

    public Time getTime(int a, Calendar b) throws SQLException
    {
        return inner.getTime(a, b);
    }

    public Ref getRef(int a) throws SQLException
    {
        return inner.getRef(a);
    }

    public short getShort(int a) throws SQLException
    {
        return inner.getShort(a);
    }

    public void registerOutParameter(int a, int b, String c) throws SQLException
    {
        inner.registerOutParameter(a, b, c);
    }

    public void registerOutParameter(int a, int b, int c) throws SQLException
    {
        inner.registerOutParameter(a, b, c);
    }

    public void registerOutParameter(int a, int b) throws SQLException
    {
        inner.registerOutParameter(a, b);
    }
}

