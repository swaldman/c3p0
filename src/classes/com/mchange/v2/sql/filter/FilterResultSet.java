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


package com.mchange.v2.sql.filter;

import java.io.InputStream;
import java.io.Reader;
import java.lang.Object;
import java.lang.String;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Date;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Map;

public abstract class FilterResultSet implements ResultSet
{
	protected ResultSet inner;
	
	public FilterResultSet(ResultSet inner)
	{ this.inner = inner; }
	
	public FilterResultSet()
	{}
	
	public void setInner( ResultSet inner )
	{ this.inner = inner; }
	
	public ResultSet getInner()
	{ return inner; }
	
	public ResultSetMetaData getMetaData() throws SQLException
	{ return inner.getMetaData(); }
	
	public SQLWarning getWarnings() throws SQLException
	{ return inner.getWarnings(); }
	
	public void clearWarnings() throws SQLException
	{ inner.clearWarnings(); }
	
	public boolean wasNull() throws SQLException
	{ return inner.wasNull(); }
	
	public BigDecimal getBigDecimal(int a) throws SQLException
	{ return inner.getBigDecimal(a); }
	
	public BigDecimal getBigDecimal(String a, int b) throws SQLException
	{ return inner.getBigDecimal(a, b); }
	
	public BigDecimal getBigDecimal(int a, int b) throws SQLException
	{ return inner.getBigDecimal(a, b); }
	
	public BigDecimal getBigDecimal(String a) throws SQLException
	{ return inner.getBigDecimal(a); }
	
	public Timestamp getTimestamp(int a) throws SQLException
	{ return inner.getTimestamp(a); }
	
	public Timestamp getTimestamp(String a) throws SQLException
	{ return inner.getTimestamp(a); }
	
	public Timestamp getTimestamp(int a, Calendar b) throws SQLException
	{ return inner.getTimestamp(a, b); }
	
	public Timestamp getTimestamp(String a, Calendar b) throws SQLException
	{ return inner.getTimestamp(a, b); }
	
	public InputStream getAsciiStream(String a) throws SQLException
	{ return inner.getAsciiStream(a); }
	
	public InputStream getAsciiStream(int a) throws SQLException
	{ return inner.getAsciiStream(a); }
	
	public InputStream getUnicodeStream(String a) throws SQLException
	{ return inner.getUnicodeStream(a); }
	
	public InputStream getUnicodeStream(int a) throws SQLException
	{ return inner.getUnicodeStream(a); }
	
	public InputStream getBinaryStream(int a) throws SQLException
	{ return inner.getBinaryStream(a); }
	
	public InputStream getBinaryStream(String a) throws SQLException
	{ return inner.getBinaryStream(a); }
	
	public String getCursorName() throws SQLException
	{ return inner.getCursorName(); }
	
	public Reader getCharacterStream(int a) throws SQLException
	{ return inner.getCharacterStream(a); }
	
	public Reader getCharacterStream(String a) throws SQLException
	{ return inner.getCharacterStream(a); }
	
	public boolean isBeforeFirst() throws SQLException
	{ return inner.isBeforeFirst(); }
	
	public boolean isAfterLast() throws SQLException
	{ return inner.isAfterLast(); }
	
	public boolean isFirst() throws SQLException
	{ return inner.isFirst(); }
	
	public boolean isLast() throws SQLException
	{ return inner.isLast(); }
	
	public void beforeFirst() throws SQLException
	{ inner.beforeFirst(); }
	
	public void afterLast() throws SQLException
	{ inner.afterLast(); }
	
	public boolean absolute(int a) throws SQLException
	{ return inner.absolute(a); }
	
	public void setFetchDirection(int a) throws SQLException
	{ inner.setFetchDirection(a); }
	
	public int getFetchDirection() throws SQLException
	{ return inner.getFetchDirection(); }
	
	public void setFetchSize(int a) throws SQLException
	{ inner.setFetchSize(a); }
	
	public int getFetchSize() throws SQLException
	{ return inner.getFetchSize(); }
	
	public int getConcurrency() throws SQLException
	{ return inner.getConcurrency(); }
	
	public boolean rowUpdated() throws SQLException
	{ return inner.rowUpdated(); }
	
	public boolean rowInserted() throws SQLException
	{ return inner.rowInserted(); }
	
	public boolean rowDeleted() throws SQLException
	{ return inner.rowDeleted(); }
	
	public void updateNull(int a) throws SQLException
	{ inner.updateNull(a); }
	
	public void updateNull(String a) throws SQLException
	{ inner.updateNull(a); }
	
	public void updateBoolean(int a, boolean b) throws SQLException
	{ inner.updateBoolean(a, b); }
	
	public void updateBoolean(String a, boolean b) throws SQLException
	{ inner.updateBoolean(a, b); }
	
	public void updateByte(int a, byte b) throws SQLException
	{ inner.updateByte(a, b); }
	
	public void updateByte(String a, byte b) throws SQLException
	{ inner.updateByte(a, b); }
	
	public void updateShort(int a, short b) throws SQLException
	{ inner.updateShort(a, b); }
	
	public void updateShort(String a, short b) throws SQLException
	{ inner.updateShort(a, b); }
	
	public void updateInt(String a, int b) throws SQLException
	{ inner.updateInt(a, b); }
	
	public void updateInt(int a, int b) throws SQLException
	{ inner.updateInt(a, b); }
	
	public void updateLong(int a, long b) throws SQLException
	{ inner.updateLong(a, b); }
	
	public void updateLong(String a, long b) throws SQLException
	{ inner.updateLong(a, b); }
	
	public void updateFloat(String a, float b) throws SQLException
	{ inner.updateFloat(a, b); }
	
	public void updateFloat(int a, float b) throws SQLException
	{ inner.updateFloat(a, b); }
	
	public void updateDouble(String a, double b) throws SQLException
	{ inner.updateDouble(a, b); }
	
	public void updateDouble(int a, double b) throws SQLException
	{ inner.updateDouble(a, b); }
	
	public void updateBigDecimal(int a, BigDecimal b) throws SQLException
	{ inner.updateBigDecimal(a, b); }
	
	public void updateBigDecimal(String a, BigDecimal b) throws SQLException
	{ inner.updateBigDecimal(a, b); }
	
	public void updateString(String a, String b) throws SQLException
	{ inner.updateString(a, b); }
	
	public void updateString(int a, String b) throws SQLException
	{ inner.updateString(a, b); }
	
	public void updateBytes(int a, byte[] b) throws SQLException
	{ inner.updateBytes(a, b); }
	
	public void updateBytes(String a, byte[] b) throws SQLException
	{ inner.updateBytes(a, b); }
	
	public void updateDate(String a, Date b) throws SQLException
	{ inner.updateDate(a, b); }
	
	public void updateDate(int a, Date b) throws SQLException
	{ inner.updateDate(a, b); }
	
	public void updateTimestamp(int a, Timestamp b) throws SQLException
	{ inner.updateTimestamp(a, b); }
	
	public void updateTimestamp(String a, Timestamp b) throws SQLException
	{ inner.updateTimestamp(a, b); }
	
	public void updateAsciiStream(String a, InputStream b, int c) throws SQLException
	{ inner.updateAsciiStream(a, b, c); }
	
	public void updateAsciiStream(int a, InputStream b, int c) throws SQLException
	{ inner.updateAsciiStream(a, b, c); }
	
	public void updateBinaryStream(int a, InputStream b, int c) throws SQLException
	{ inner.updateBinaryStream(a, b, c); }
	
	public void updateBinaryStream(String a, InputStream b, int c) throws SQLException
	{ inner.updateBinaryStream(a, b, c); }
	
	public void updateCharacterStream(int a, Reader b, int c) throws SQLException
	{ inner.updateCharacterStream(a, b, c); }
	
	public void updateCharacterStream(String a, Reader b, int c) throws SQLException
	{ inner.updateCharacterStream(a, b, c); }
	
	public void updateObject(String a, Object b) throws SQLException
	{ inner.updateObject(a, b); }
	
	public void updateObject(int a, Object b) throws SQLException
	{ inner.updateObject(a, b); }
	
	public void updateObject(int a, Object b, int c) throws SQLException
	{ inner.updateObject(a, b, c); }
	
	public void updateObject(String a, Object b, int c) throws SQLException
	{ inner.updateObject(a, b, c); }
	
	public void insertRow() throws SQLException
	{ inner.insertRow(); }
	
	public void updateRow() throws SQLException
	{ inner.updateRow(); }
	
	public void deleteRow() throws SQLException
	{ inner.deleteRow(); }
	
	public void refreshRow() throws SQLException
	{ inner.refreshRow(); }
	
	public void cancelRowUpdates() throws SQLException
	{ inner.cancelRowUpdates(); }
	
	public void moveToInsertRow() throws SQLException
	{ inner.moveToInsertRow(); }
	
	public void moveToCurrentRow() throws SQLException
	{ inner.moveToCurrentRow(); }
	
	public Statement getStatement() throws SQLException
	{ return inner.getStatement(); }
	
	public Blob getBlob(String a) throws SQLException
	{ return inner.getBlob(a); }
	
	public Blob getBlob(int a) throws SQLException
	{ return inner.getBlob(a); }
	
	public Clob getClob(String a) throws SQLException
	{ return inner.getClob(a); }
	
	public Clob getClob(int a) throws SQLException
	{ return inner.getClob(a); }
	
	public void updateRef(String a, Ref b) throws SQLException
	{ inner.updateRef(a, b); }
	
	public void updateRef(int a, Ref b) throws SQLException
	{ inner.updateRef(a, b); }
	
	public void updateBlob(String a, Blob b) throws SQLException
	{ inner.updateBlob(a, b); }
	
	public void updateBlob(int a, Blob b) throws SQLException
	{ inner.updateBlob(a, b); }
	
	public void updateClob(int a, Clob b) throws SQLException
	{ inner.updateClob(a, b); }
	
	public void updateClob(String a, Clob b) throws SQLException
	{ inner.updateClob(a, b); }
	
	public void updateArray(String a, Array b) throws SQLException
	{ inner.updateArray(a, b); }
	
	public void updateArray(int a, Array b) throws SQLException
	{ inner.updateArray(a, b); }
	
	public Object getObject(int a) throws SQLException
	{ return inner.getObject(a); }
	
	public Object getObject(String a, Map b) throws SQLException
	{ return inner.getObject(a, b); }
	
	public Object getObject(String a) throws SQLException
	{ return inner.getObject(a); }
	
	public Object getObject(int a, Map b) throws SQLException
	{ return inner.getObject(a, b); }
	
	public boolean getBoolean(int a) throws SQLException
	{ return inner.getBoolean(a); }
	
	public boolean getBoolean(String a) throws SQLException
	{ return inner.getBoolean(a); }
	
	public byte getByte(String a) throws SQLException
	{ return inner.getByte(a); }
	
	public byte getByte(int a) throws SQLException
	{ return inner.getByte(a); }
	
	public short getShort(String a) throws SQLException
	{ return inner.getShort(a); }
	
	public short getShort(int a) throws SQLException
	{ return inner.getShort(a); }
	
	public int getInt(String a) throws SQLException
	{ return inner.getInt(a); }
	
	public int getInt(int a) throws SQLException
	{ return inner.getInt(a); }
	
	public long getLong(int a) throws SQLException
	{ return inner.getLong(a); }
	
	public long getLong(String a) throws SQLException
	{ return inner.getLong(a); }
	
	public float getFloat(String a) throws SQLException
	{ return inner.getFloat(a); }
	
	public float getFloat(int a) throws SQLException
	{ return inner.getFloat(a); }
	
	public double getDouble(int a) throws SQLException
	{ return inner.getDouble(a); }
	
	public double getDouble(String a) throws SQLException
	{ return inner.getDouble(a); }
	
	public byte[] getBytes(String a) throws SQLException
	{ return inner.getBytes(a); }
	
	public byte[] getBytes(int a) throws SQLException
	{ return inner.getBytes(a); }
	
	public boolean next() throws SQLException
	{ return inner.next(); }
	
	public URL getURL(int a) throws SQLException
	{ return inner.getURL(a); }
	
	public URL getURL(String a) throws SQLException
	{ return inner.getURL(a); }
	
	public int getType() throws SQLException
	{ return inner.getType(); }
	
	public boolean previous() throws SQLException
	{ return inner.previous(); }
	
	public void close() throws SQLException
	{ inner.close(); }
	
	public String getString(String a) throws SQLException
	{ return inner.getString(a); }
	
	public String getString(int a) throws SQLException
	{ return inner.getString(a); }
	
	public Ref getRef(String a) throws SQLException
	{ return inner.getRef(a); }
	
	public Ref getRef(int a) throws SQLException
	{ return inner.getRef(a); }
	
	public Time getTime(int a, Calendar b) throws SQLException
	{ return inner.getTime(a, b); }
	
	public Time getTime(String a) throws SQLException
	{ return inner.getTime(a); }
	
	public Time getTime(int a) throws SQLException
	{ return inner.getTime(a); }
	
	public Time getTime(String a, Calendar b) throws SQLException
	{ return inner.getTime(a, b); }
	
	public Date getDate(String a) throws SQLException
	{ return inner.getDate(a); }
	
	public Date getDate(int a) throws SQLException
	{ return inner.getDate(a); }
	
	public Date getDate(int a, Calendar b) throws SQLException
	{ return inner.getDate(a, b); }
	
	public Date getDate(String a, Calendar b) throws SQLException
	{ return inner.getDate(a, b); }
	
	public boolean first() throws SQLException
	{ return inner.first(); }
	
	public boolean last() throws SQLException
	{ return inner.last(); }
	
	public Array getArray(String a) throws SQLException
	{ return inner.getArray(a); }
	
	public Array getArray(int a) throws SQLException
	{ return inner.getArray(a); }
	
	public boolean relative(int a) throws SQLException
	{ return inner.relative(a); }
	
	public void updateTime(String a, Time b) throws SQLException
	{ inner.updateTime(a, b); }
	
	public void updateTime(int a, Time b) throws SQLException
	{ inner.updateTime(a, b); }
	
	public int findColumn(String a) throws SQLException
	{ return inner.findColumn(a); }
	
	public int getRow() throws SQLException
	{ return inner.getRow(); }
}
