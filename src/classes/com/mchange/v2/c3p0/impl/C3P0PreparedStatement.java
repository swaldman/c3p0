/*
 * Distributed as part of c3p0 v.0.8.4-test1
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
import java.sql.Clob;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Calendar;


abstract class C3P0PreparedStatement extends C3P0Statement implements PreparedStatement
{
    final PreparedStatement inner;

    public C3P0PreparedStatement(PreparedStatement inner)
    {
	super( inner );
	this.inner = inner;
    }

    public void addBatch() throws SQLException
    {
        inner.addBatch();
    }

    public ResultSetMetaData getMetaData() throws SQLException
    {
        return inner.getMetaData();
    }

    public int executeUpdate() throws SQLException
    {
        return inner.executeUpdate();
    }

    public void setArray(int a, Array b) throws SQLException
    {
        inner.setArray(a, b);
    }

    public void setRef(int a, Ref b) throws SQLException
    {
        inner.setRef(a, b);
    }

    public void setString(int a, String b) throws SQLException
    {
        inner.setString(a, b);
    }

    public boolean execute() throws SQLException
    {
        return inner.execute();
    }

    public void setBytes(int a, byte[] b) throws SQLException
    {
        inner.setBytes(a, b);
    }

    public void setTime(int a, Time b) throws SQLException
    {
        inner.setTime(a, b);
    }

    public void setTime(int a, Time b, Calendar c) throws SQLException
    {
        inner.setTime(a, b, c);
    }

    public void setDouble(int a, double b) throws SQLException
    {
        inner.setDouble(a, b);
    }

    public void setBigDecimal(int a, BigDecimal b) throws SQLException
    {
        inner.setBigDecimal(a, b);
    }

    public void setLong(int a, long b) throws SQLException
    {
        inner.setLong(a, b);
    }

    public void setDate(int a, Date b, Calendar c) throws SQLException
    {
        inner.setDate(a, b, c);
    }

    public void setDate(int a, Date b) throws SQLException
    {
        inner.setDate(a, b);
    }

    public void setAsciiStream(int a, InputStream b, int c) throws SQLException
    {
        inner.setAsciiStream(a, b, c);
    }

    public void setByte(int a, byte b) throws SQLException
    {
        inner.setByte(a, b);
    }

    public void setBlob(int a, Blob b) throws SQLException
    {
        inner.setBlob(a, b);
    }

    public void setInt(int a, int b) throws SQLException
    {
        inner.setInt(a, b);
    }

    public void setObject(int a, Object b) throws SQLException
    {
        inner.setObject(a, b);
    }

    public void setObject(int a, Object b, int c) throws SQLException
    {
        inner.setObject(a, b, c);
    }

    public void setObject(int a, Object b, int c, int d) throws SQLException
    {
        inner.setObject(a, b, c, d);
    }

    public void setTimestamp(int a, Timestamp b) throws SQLException
    {
        inner.setTimestamp(a, b);
    }

    public void setTimestamp(int a, Timestamp b, Calendar c) throws SQLException
    {
        inner.setTimestamp(a, b, c);
    }

    public void setUnicodeStream(int a, InputStream b, int c) throws SQLException
    {
        inner.setUnicodeStream(a, b, c);
    }

    public void setCharacterStream(int a, Reader b, int c) throws SQLException
    {
        inner.setCharacterStream(a, b, c);
    }

    public ResultSet executeQuery() throws SQLException
    {
        return inner.executeQuery();
    }

    public void setBoolean(int a, boolean b) throws SQLException
    {
        inner.setBoolean(a, b);
    }

    public void setNull(int a, int b, String c) throws SQLException
    {
        inner.setNull(a, b, c);
    }

    public void setNull(int a, int b) throws SQLException
    {
        inner.setNull(a, b);
    }

    public void setFloat(int a, float b) throws SQLException
    {
        inner.setFloat(a, b);
    }

    public void setClob(int a, Clob b) throws SQLException
    {
        inner.setClob(a, b);
    }

    public void setShort(int a, short b) throws SQLException
    {
        inner.setShort(a, b);
    }

    public void setBinaryStream(int a, InputStream b, int c) throws SQLException
    {
        inner.setBinaryStream(a, b, c);
    }

    public void clearParameters() throws SQLException
    {
        inner.clearParameters();
    }
}

