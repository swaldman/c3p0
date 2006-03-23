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

public abstract class SynchronizedFilterResultSet implements ResultSet
{
	protected ResultSet inner;
	
	public SynchronizedFilterResultSet(ResultSet inner)
	{ this.inner = inner; }
	
	public SynchronizedFilterResultSet()
	{}
	
	public synchronized void setInner( ResultSet inner )
	{ this.inner = inner; }
	
	public synchronized ResultSet getInner()
	{ return inner; }
	
	public synchronized ResultSetMetaData getMetaData() throws SQLException
	{ return inner.getMetaData(); }
	
	public synchronized SQLWarning getWarnings() throws SQLException
	{ return inner.getWarnings(); }
	
	public synchronized void clearWarnings() throws SQLException
	{ inner.clearWarnings(); }
	
	public synchronized boolean wasNull() throws SQLException
	{ return inner.wasNull(); }
	
	public synchronized BigDecimal getBigDecimal(int a) throws SQLException
	{ return inner.getBigDecimal(a); }
	
	public synchronized BigDecimal getBigDecimal(String a, int b) throws SQLException
	{ return inner.getBigDecimal(a, b); }
	
	public synchronized BigDecimal getBigDecimal(int a, int b) throws SQLException
	{ return inner.getBigDecimal(a, b); }
	
	public synchronized BigDecimal getBigDecimal(String a) throws SQLException
	{ return inner.getBigDecimal(a); }
	
	public synchronized Timestamp getTimestamp(int a) throws SQLException
	{ return inner.getTimestamp(a); }
	
	public synchronized Timestamp getTimestamp(String a) throws SQLException
	{ return inner.getTimestamp(a); }
	
	public synchronized Timestamp getTimestamp(int a, Calendar b) throws SQLException
	{ return inner.getTimestamp(a, b); }
	
	public synchronized Timestamp getTimestamp(String a, Calendar b) throws SQLException
	{ return inner.getTimestamp(a, b); }
	
	public synchronized InputStream getAsciiStream(String a) throws SQLException
	{ return inner.getAsciiStream(a); }
	
	public synchronized InputStream getAsciiStream(int a) throws SQLException
	{ return inner.getAsciiStream(a); }
	
	public synchronized InputStream getUnicodeStream(String a) throws SQLException
	{ return inner.getUnicodeStream(a); }
	
	public synchronized InputStream getUnicodeStream(int a) throws SQLException
	{ return inner.getUnicodeStream(a); }
	
	public synchronized InputStream getBinaryStream(int a) throws SQLException
	{ return inner.getBinaryStream(a); }
	
	public synchronized InputStream getBinaryStream(String a) throws SQLException
	{ return inner.getBinaryStream(a); }
	
	public synchronized String getCursorName() throws SQLException
	{ return inner.getCursorName(); }
	
	public synchronized Reader getCharacterStream(int a) throws SQLException
	{ return inner.getCharacterStream(a); }
	
	public synchronized Reader getCharacterStream(String a) throws SQLException
	{ return inner.getCharacterStream(a); }
	
	public synchronized boolean isBeforeFirst() throws SQLException
	{ return inner.isBeforeFirst(); }
	
	public synchronized boolean isAfterLast() throws SQLException
	{ return inner.isAfterLast(); }
	
	public synchronized boolean isFirst() throws SQLException
	{ return inner.isFirst(); }
	
	public synchronized boolean isLast() throws SQLException
	{ return inner.isLast(); }
	
	public synchronized void beforeFirst() throws SQLException
	{ inner.beforeFirst(); }
	
	public synchronized void afterLast() throws SQLException
	{ inner.afterLast(); }
	
	public synchronized boolean absolute(int a) throws SQLException
	{ return inner.absolute(a); }
	
	public synchronized void setFetchDirection(int a) throws SQLException
	{ inner.setFetchDirection(a); }
	
	public synchronized int getFetchDirection() throws SQLException
	{ return inner.getFetchDirection(); }
	
	public synchronized void setFetchSize(int a) throws SQLException
	{ inner.setFetchSize(a); }
	
	public synchronized int getFetchSize() throws SQLException
	{ return inner.getFetchSize(); }
	
	public synchronized int getConcurrency() throws SQLException
	{ return inner.getConcurrency(); }
	
	public synchronized boolean rowUpdated() throws SQLException
	{ return inner.rowUpdated(); }
	
	public synchronized boolean rowInserted() throws SQLException
	{ return inner.rowInserted(); }
	
	public synchronized boolean rowDeleted() throws SQLException
	{ return inner.rowDeleted(); }
	
	public synchronized void updateNull(int a) throws SQLException
	{ inner.updateNull(a); }
	
	public synchronized void updateNull(String a) throws SQLException
	{ inner.updateNull(a); }
	
	public synchronized void updateBoolean(int a, boolean b) throws SQLException
	{ inner.updateBoolean(a, b); }
	
	public synchronized void updateBoolean(String a, boolean b) throws SQLException
	{ inner.updateBoolean(a, b); }
	
	public synchronized void updateByte(int a, byte b) throws SQLException
	{ inner.updateByte(a, b); }
	
	public synchronized void updateByte(String a, byte b) throws SQLException
	{ inner.updateByte(a, b); }
	
	public synchronized void updateShort(int a, short b) throws SQLException
	{ inner.updateShort(a, b); }
	
	public synchronized void updateShort(String a, short b) throws SQLException
	{ inner.updateShort(a, b); }
	
	public synchronized void updateInt(String a, int b) throws SQLException
	{ inner.updateInt(a, b); }
	
	public synchronized void updateInt(int a, int b) throws SQLException
	{ inner.updateInt(a, b); }
	
	public synchronized void updateLong(int a, long b) throws SQLException
	{ inner.updateLong(a, b); }
	
	public synchronized void updateLong(String a, long b) throws SQLException
	{ inner.updateLong(a, b); }
	
	public synchronized void updateFloat(String a, float b) throws SQLException
	{ inner.updateFloat(a, b); }
	
	public synchronized void updateFloat(int a, float b) throws SQLException
	{ inner.updateFloat(a, b); }
	
	public synchronized void updateDouble(String a, double b) throws SQLException
	{ inner.updateDouble(a, b); }
	
	public synchronized void updateDouble(int a, double b) throws SQLException
	{ inner.updateDouble(a, b); }
	
	public synchronized void updateBigDecimal(int a, BigDecimal b) throws SQLException
	{ inner.updateBigDecimal(a, b); }
	
	public synchronized void updateBigDecimal(String a, BigDecimal b) throws SQLException
	{ inner.updateBigDecimal(a, b); }
	
	public synchronized void updateString(String a, String b) throws SQLException
	{ inner.updateString(a, b); }
	
	public synchronized void updateString(int a, String b) throws SQLException
	{ inner.updateString(a, b); }
	
	public synchronized void updateBytes(int a, byte[] b) throws SQLException
	{ inner.updateBytes(a, b); }
	
	public synchronized void updateBytes(String a, byte[] b) throws SQLException
	{ inner.updateBytes(a, b); }
	
	public synchronized void updateDate(String a, Date b) throws SQLException
	{ inner.updateDate(a, b); }
	
	public synchronized void updateDate(int a, Date b) throws SQLException
	{ inner.updateDate(a, b); }
	
	public synchronized void updateTimestamp(int a, Timestamp b) throws SQLException
	{ inner.updateTimestamp(a, b); }
	
	public synchronized void updateTimestamp(String a, Timestamp b) throws SQLException
	{ inner.updateTimestamp(a, b); }
	
	public synchronized void updateAsciiStream(String a, InputStream b, int c) throws SQLException
	{ inner.updateAsciiStream(a, b, c); }
	
	public synchronized void updateAsciiStream(int a, InputStream b, int c) throws SQLException
	{ inner.updateAsciiStream(a, b, c); }
	
	public synchronized void updateBinaryStream(int a, InputStream b, int c) throws SQLException
	{ inner.updateBinaryStream(a, b, c); }
	
	public synchronized void updateBinaryStream(String a, InputStream b, int c) throws SQLException
	{ inner.updateBinaryStream(a, b, c); }
	
	public synchronized void updateCharacterStream(int a, Reader b, int c) throws SQLException
	{ inner.updateCharacterStream(a, b, c); }
	
	public synchronized void updateCharacterStream(String a, Reader b, int c) throws SQLException
	{ inner.updateCharacterStream(a, b, c); }
	
	public synchronized void updateObject(String a, Object b) throws SQLException
	{ inner.updateObject(a, b); }
	
	public synchronized void updateObject(int a, Object b) throws SQLException
	{ inner.updateObject(a, b); }
	
	public synchronized void updateObject(int a, Object b, int c) throws SQLException
	{ inner.updateObject(a, b, c); }
	
	public synchronized void updateObject(String a, Object b, int c) throws SQLException
	{ inner.updateObject(a, b, c); }
	
	public synchronized void insertRow() throws SQLException
	{ inner.insertRow(); }
	
	public synchronized void updateRow() throws SQLException
	{ inner.updateRow(); }
	
	public synchronized void deleteRow() throws SQLException
	{ inner.deleteRow(); }
	
	public synchronized void refreshRow() throws SQLException
	{ inner.refreshRow(); }
	
	public synchronized void cancelRowUpdates() throws SQLException
	{ inner.cancelRowUpdates(); }
	
	public synchronized void moveToInsertRow() throws SQLException
	{ inner.moveToInsertRow(); }
	
	public synchronized void moveToCurrentRow() throws SQLException
	{ inner.moveToCurrentRow(); }
	
	public synchronized Statement getStatement() throws SQLException
	{ return inner.getStatement(); }
	
	public synchronized Blob getBlob(String a) throws SQLException
	{ return inner.getBlob(a); }
	
	public synchronized Blob getBlob(int a) throws SQLException
	{ return inner.getBlob(a); }
	
	public synchronized Clob getClob(String a) throws SQLException
	{ return inner.getClob(a); }
	
	public synchronized Clob getClob(int a) throws SQLException
	{ return inner.getClob(a); }
	
	public synchronized void updateRef(String a, Ref b) throws SQLException
	{ inner.updateRef(a, b); }
	
	public synchronized void updateRef(int a, Ref b) throws SQLException
	{ inner.updateRef(a, b); }
	
	public synchronized void updateBlob(String a, Blob b) throws SQLException
	{ inner.updateBlob(a, b); }
	
	public synchronized void updateBlob(int a, Blob b) throws SQLException
	{ inner.updateBlob(a, b); }
	
	public synchronized void updateClob(int a, Clob b) throws SQLException
	{ inner.updateClob(a, b); }
	
	public synchronized void updateClob(String a, Clob b) throws SQLException
	{ inner.updateClob(a, b); }
	
	public synchronized void updateArray(String a, Array b) throws SQLException
	{ inner.updateArray(a, b); }
	
	public synchronized void updateArray(int a, Array b) throws SQLException
	{ inner.updateArray(a, b); }
	
	public synchronized Object getObject(int a) throws SQLException
	{ return inner.getObject(a); }
	
	public synchronized Object getObject(String a, Map b) throws SQLException
	{ return inner.getObject(a, b); }
	
	public synchronized Object getObject(String a) throws SQLException
	{ return inner.getObject(a); }
	
	public synchronized Object getObject(int a, Map b) throws SQLException
	{ return inner.getObject(a, b); }
	
	public synchronized boolean getBoolean(int a) throws SQLException
	{ return inner.getBoolean(a); }
	
	public synchronized boolean getBoolean(String a) throws SQLException
	{ return inner.getBoolean(a); }
	
	public synchronized byte getByte(String a) throws SQLException
	{ return inner.getByte(a); }
	
	public synchronized byte getByte(int a) throws SQLException
	{ return inner.getByte(a); }
	
	public synchronized short getShort(String a) throws SQLException
	{ return inner.getShort(a); }
	
	public synchronized short getShort(int a) throws SQLException
	{ return inner.getShort(a); }
	
	public synchronized int getInt(String a) throws SQLException
	{ return inner.getInt(a); }
	
	public synchronized int getInt(int a) throws SQLException
	{ return inner.getInt(a); }
	
	public synchronized long getLong(int a) throws SQLException
	{ return inner.getLong(a); }
	
	public synchronized long getLong(String a) throws SQLException
	{ return inner.getLong(a); }
	
	public synchronized float getFloat(String a) throws SQLException
	{ return inner.getFloat(a); }
	
	public synchronized float getFloat(int a) throws SQLException
	{ return inner.getFloat(a); }
	
	public synchronized double getDouble(int a) throws SQLException
	{ return inner.getDouble(a); }
	
	public synchronized double getDouble(String a) throws SQLException
	{ return inner.getDouble(a); }
	
	public synchronized byte[] getBytes(String a) throws SQLException
	{ return inner.getBytes(a); }
	
	public synchronized byte[] getBytes(int a) throws SQLException
	{ return inner.getBytes(a); }
	
	public synchronized boolean next() throws SQLException
	{ return inner.next(); }
	
	public synchronized URL getURL(int a) throws SQLException
	{ return inner.getURL(a); }
	
	public synchronized URL getURL(String a) throws SQLException
	{ return inner.getURL(a); }
	
	public synchronized int getType() throws SQLException
	{ return inner.getType(); }
	
	public synchronized boolean previous() throws SQLException
	{ return inner.previous(); }
	
	public synchronized void close() throws SQLException
	{ inner.close(); }
	
	public synchronized String getString(String a) throws SQLException
	{ return inner.getString(a); }
	
	public synchronized String getString(int a) throws SQLException
	{ return inner.getString(a); }
	
	public synchronized Ref getRef(String a) throws SQLException
	{ return inner.getRef(a); }
	
	public synchronized Ref getRef(int a) throws SQLException
	{ return inner.getRef(a); }
	
	public synchronized Time getTime(int a, Calendar b) throws SQLException
	{ return inner.getTime(a, b); }
	
	public synchronized Time getTime(String a) throws SQLException
	{ return inner.getTime(a); }
	
	public synchronized Time getTime(int a) throws SQLException
	{ return inner.getTime(a); }
	
	public synchronized Time getTime(String a, Calendar b) throws SQLException
	{ return inner.getTime(a, b); }
	
	public synchronized Date getDate(String a) throws SQLException
	{ return inner.getDate(a); }
	
	public synchronized Date getDate(int a) throws SQLException
	{ return inner.getDate(a); }
	
	public synchronized Date getDate(int a, Calendar b) throws SQLException
	{ return inner.getDate(a, b); }
	
	public synchronized Date getDate(String a, Calendar b) throws SQLException
	{ return inner.getDate(a, b); }
	
	public synchronized boolean first() throws SQLException
	{ return inner.first(); }
	
	public synchronized boolean last() throws SQLException
	{ return inner.last(); }
	
	public synchronized Array getArray(String a) throws SQLException
	{ return inner.getArray(a); }
	
	public synchronized Array getArray(int a) throws SQLException
	{ return inner.getArray(a); }
	
	public synchronized boolean relative(int a) throws SQLException
	{ return inner.relative(a); }
	
	public synchronized void updateTime(String a, Time b) throws SQLException
	{ inner.updateTime(a, b); }
	
	public synchronized void updateTime(int a, Time b) throws SQLException
	{ inner.updateTime(a, b); }
	
	public synchronized int findColumn(String a) throws SQLException
	{ return inner.findColumn(a); }
	
	public synchronized int getRow() throws SQLException
	{ return inner.getRow(); }
}
