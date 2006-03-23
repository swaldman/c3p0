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
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.Date;
import java.sql.ParameterMetaData;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Map;

public abstract class SynchronizedFilterCallableStatement implements CallableStatement
{
	protected CallableStatement inner;
	
	public SynchronizedFilterCallableStatement(CallableStatement inner)
	{ this.inner = inner; }
	
	public SynchronizedFilterCallableStatement()
	{}
	
	public synchronized void setInner( CallableStatement inner )
	{ this.inner = inner; }
	
	public synchronized CallableStatement getInner()
	{ return inner; }
	
	public synchronized boolean wasNull() throws SQLException
	{ return inner.wasNull(); }
	
	public synchronized BigDecimal getBigDecimal(int a, int b) throws SQLException
	{ return inner.getBigDecimal(a, b); }
	
	public synchronized BigDecimal getBigDecimal(int a) throws SQLException
	{ return inner.getBigDecimal(a); }
	
	public synchronized BigDecimal getBigDecimal(String a) throws SQLException
	{ return inner.getBigDecimal(a); }
	
	public synchronized Timestamp getTimestamp(String a) throws SQLException
	{ return inner.getTimestamp(a); }
	
	public synchronized Timestamp getTimestamp(String a, Calendar b) throws SQLException
	{ return inner.getTimestamp(a, b); }
	
	public synchronized Timestamp getTimestamp(int a, Calendar b) throws SQLException
	{ return inner.getTimestamp(a, b); }
	
	public synchronized Timestamp getTimestamp(int a) throws SQLException
	{ return inner.getTimestamp(a); }
	
	public synchronized Blob getBlob(String a) throws SQLException
	{ return inner.getBlob(a); }
	
	public synchronized Blob getBlob(int a) throws SQLException
	{ return inner.getBlob(a); }
	
	public synchronized Clob getClob(String a) throws SQLException
	{ return inner.getClob(a); }
	
	public synchronized Clob getClob(int a) throws SQLException
	{ return inner.getClob(a); }
	
	public synchronized void setNull(String a, int b, String c) throws SQLException
	{ inner.setNull(a, b, c); }
	
	public synchronized void setNull(String a, int b) throws SQLException
	{ inner.setNull(a, b); }
	
	public synchronized void setBigDecimal(String a, BigDecimal b) throws SQLException
	{ inner.setBigDecimal(a, b); }
	
	public synchronized void setBytes(String a, byte[] b) throws SQLException
	{ inner.setBytes(a, b); }
	
	public synchronized void setTimestamp(String a, Timestamp b, Calendar c) throws SQLException
	{ inner.setTimestamp(a, b, c); }
	
	public synchronized void setTimestamp(String a, Timestamp b) throws SQLException
	{ inner.setTimestamp(a, b); }
	
	public synchronized void setAsciiStream(String a, InputStream b, int c) throws SQLException
	{ inner.setAsciiStream(a, b, c); }
	
	public synchronized void setBinaryStream(String a, InputStream b, int c) throws SQLException
	{ inner.setBinaryStream(a, b, c); }
	
	public synchronized void setObject(String a, Object b) throws SQLException
	{ inner.setObject(a, b); }
	
	public synchronized void setObject(String a, Object b, int c, int d) throws SQLException
	{ inner.setObject(a, b, c, d); }
	
	public synchronized void setObject(String a, Object b, int c) throws SQLException
	{ inner.setObject(a, b, c); }
	
	public synchronized void setCharacterStream(String a, Reader b, int c) throws SQLException
	{ inner.setCharacterStream(a, b, c); }
	
	public synchronized void registerOutParameter(String a, int b) throws SQLException
	{ inner.registerOutParameter(a, b); }
	
	public synchronized void registerOutParameter(int a, int b) throws SQLException
	{ inner.registerOutParameter(a, b); }
	
	public synchronized void registerOutParameter(int a, int b, int c) throws SQLException
	{ inner.registerOutParameter(a, b, c); }
	
	public synchronized void registerOutParameter(int a, int b, String c) throws SQLException
	{ inner.registerOutParameter(a, b, c); }
	
	public synchronized void registerOutParameter(String a, int b, int c) throws SQLException
	{ inner.registerOutParameter(a, b, c); }
	
	public synchronized void registerOutParameter(String a, int b, String c) throws SQLException
	{ inner.registerOutParameter(a, b, c); }
	
	public synchronized Object getObject(String a, Map b) throws SQLException
	{ return inner.getObject(a, b); }
	
	public synchronized Object getObject(int a, Map b) throws SQLException
	{ return inner.getObject(a, b); }
	
	public synchronized Object getObject(int a) throws SQLException
	{ return inner.getObject(a); }
	
	public synchronized Object getObject(String a) throws SQLException
	{ return inner.getObject(a); }
	
	public synchronized boolean getBoolean(int a) throws SQLException
	{ return inner.getBoolean(a); }
	
	public synchronized boolean getBoolean(String a) throws SQLException
	{ return inner.getBoolean(a); }
	
	public synchronized byte getByte(String a) throws SQLException
	{ return inner.getByte(a); }
	
	public synchronized byte getByte(int a) throws SQLException
	{ return inner.getByte(a); }
	
	public synchronized short getShort(int a) throws SQLException
	{ return inner.getShort(a); }
	
	public synchronized short getShort(String a) throws SQLException
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
	
	public synchronized double getDouble(String a) throws SQLException
	{ return inner.getDouble(a); }
	
	public synchronized double getDouble(int a) throws SQLException
	{ return inner.getDouble(a); }
	
	public synchronized byte[] getBytes(int a) throws SQLException
	{ return inner.getBytes(a); }
	
	public synchronized byte[] getBytes(String a) throws SQLException
	{ return inner.getBytes(a); }
	
	public synchronized URL getURL(String a) throws SQLException
	{ return inner.getURL(a); }
	
	public synchronized URL getURL(int a) throws SQLException
	{ return inner.getURL(a); }
	
	public synchronized void setBoolean(String a, boolean b) throws SQLException
	{ inner.setBoolean(a, b); }
	
	public synchronized void setByte(String a, byte b) throws SQLException
	{ inner.setByte(a, b); }
	
	public synchronized void setShort(String a, short b) throws SQLException
	{ inner.setShort(a, b); }
	
	public synchronized void setInt(String a, int b) throws SQLException
	{ inner.setInt(a, b); }
	
	public synchronized void setLong(String a, long b) throws SQLException
	{ inner.setLong(a, b); }
	
	public synchronized void setFloat(String a, float b) throws SQLException
	{ inner.setFloat(a, b); }
	
	public synchronized void setDouble(String a, double b) throws SQLException
	{ inner.setDouble(a, b); }
	
	public synchronized String getString(String a) throws SQLException
	{ return inner.getString(a); }
	
	public synchronized String getString(int a) throws SQLException
	{ return inner.getString(a); }
	
	public synchronized Ref getRef(int a) throws SQLException
	{ return inner.getRef(a); }
	
	public synchronized Ref getRef(String a) throws SQLException
	{ return inner.getRef(a); }
	
	public synchronized void setURL(String a, URL b) throws SQLException
	{ inner.setURL(a, b); }
	
	public synchronized void setTime(String a, Time b) throws SQLException
	{ inner.setTime(a, b); }
	
	public synchronized void setTime(String a, Time b, Calendar c) throws SQLException
	{ inner.setTime(a, b, c); }
	
	public synchronized Time getTime(int a, Calendar b) throws SQLException
	{ return inner.getTime(a, b); }
	
	public synchronized Time getTime(String a) throws SQLException
	{ return inner.getTime(a); }
	
	public synchronized Time getTime(int a) throws SQLException
	{ return inner.getTime(a); }
	
	public synchronized Time getTime(String a, Calendar b) throws SQLException
	{ return inner.getTime(a, b); }
	
	public synchronized Date getDate(int a, Calendar b) throws SQLException
	{ return inner.getDate(a, b); }
	
	public synchronized Date getDate(String a) throws SQLException
	{ return inner.getDate(a); }
	
	public synchronized Date getDate(int a) throws SQLException
	{ return inner.getDate(a); }
	
	public synchronized Date getDate(String a, Calendar b) throws SQLException
	{ return inner.getDate(a, b); }
	
	public synchronized void setString(String a, String b) throws SQLException
	{ inner.setString(a, b); }
	
	public synchronized Array getArray(int a) throws SQLException
	{ return inner.getArray(a); }
	
	public synchronized Array getArray(String a) throws SQLException
	{ return inner.getArray(a); }
	
	public synchronized void setDate(String a, Date b, Calendar c) throws SQLException
	{ inner.setDate(a, b, c); }
	
	public synchronized void setDate(String a, Date b) throws SQLException
	{ inner.setDate(a, b); }
	
	public synchronized ResultSetMetaData getMetaData() throws SQLException
	{ return inner.getMetaData(); }
	
	public synchronized ResultSet executeQuery() throws SQLException
	{ return inner.executeQuery(); }
	
	public synchronized int executeUpdate() throws SQLException
	{ return inner.executeUpdate(); }
	
	public synchronized void addBatch() throws SQLException
	{ inner.addBatch(); }
	
	public synchronized void setNull(int a, int b, String c) throws SQLException
	{ inner.setNull(a, b, c); }
	
	public synchronized void setNull(int a, int b) throws SQLException
	{ inner.setNull(a, b); }
	
	public synchronized void setBigDecimal(int a, BigDecimal b) throws SQLException
	{ inner.setBigDecimal(a, b); }
	
	public synchronized void setBytes(int a, byte[] b) throws SQLException
	{ inner.setBytes(a, b); }
	
	public synchronized void setTimestamp(int a, Timestamp b, Calendar c) throws SQLException
	{ inner.setTimestamp(a, b, c); }
	
	public synchronized void setTimestamp(int a, Timestamp b) throws SQLException
	{ inner.setTimestamp(a, b); }
	
	public synchronized void setAsciiStream(int a, InputStream b, int c) throws SQLException
	{ inner.setAsciiStream(a, b, c); }
	
	public synchronized void setUnicodeStream(int a, InputStream b, int c) throws SQLException
	{ inner.setUnicodeStream(a, b, c); }
	
	public synchronized void setBinaryStream(int a, InputStream b, int c) throws SQLException
	{ inner.setBinaryStream(a, b, c); }
	
	public synchronized void clearParameters() throws SQLException
	{ inner.clearParameters(); }
	
	public synchronized void setObject(int a, Object b) throws SQLException
	{ inner.setObject(a, b); }
	
	public synchronized void setObject(int a, Object b, int c, int d) throws SQLException
	{ inner.setObject(a, b, c, d); }
	
	public synchronized void setObject(int a, Object b, int c) throws SQLException
	{ inner.setObject(a, b, c); }
	
	public synchronized void setCharacterStream(int a, Reader b, int c) throws SQLException
	{ inner.setCharacterStream(a, b, c); }
	
	public synchronized void setRef(int a, Ref b) throws SQLException
	{ inner.setRef(a, b); }
	
	public synchronized void setBlob(int a, Blob b) throws SQLException
	{ inner.setBlob(a, b); }
	
	public synchronized void setClob(int a, Clob b) throws SQLException
	{ inner.setClob(a, b); }
	
	public synchronized void setArray(int a, Array b) throws SQLException
	{ inner.setArray(a, b); }
	
	public synchronized ParameterMetaData getParameterMetaData() throws SQLException
	{ return inner.getParameterMetaData(); }
	
	public synchronized void setBoolean(int a, boolean b) throws SQLException
	{ inner.setBoolean(a, b); }
	
	public synchronized void setByte(int a, byte b) throws SQLException
	{ inner.setByte(a, b); }
	
	public synchronized void setShort(int a, short b) throws SQLException
	{ inner.setShort(a, b); }
	
	public synchronized void setInt(int a, int b) throws SQLException
	{ inner.setInt(a, b); }
	
	public synchronized void setLong(int a, long b) throws SQLException
	{ inner.setLong(a, b); }
	
	public synchronized void setFloat(int a, float b) throws SQLException
	{ inner.setFloat(a, b); }
	
	public synchronized void setDouble(int a, double b) throws SQLException
	{ inner.setDouble(a, b); }
	
	public synchronized void setURL(int a, URL b) throws SQLException
	{ inner.setURL(a, b); }
	
	public synchronized void setTime(int a, Time b) throws SQLException
	{ inner.setTime(a, b); }
	
	public synchronized void setTime(int a, Time b, Calendar c) throws SQLException
	{ inner.setTime(a, b, c); }
	
	public synchronized boolean execute() throws SQLException
	{ return inner.execute(); }
	
	public synchronized void setString(int a, String b) throws SQLException
	{ inner.setString(a, b); }
	
	public synchronized void setDate(int a, Date b, Calendar c) throws SQLException
	{ inner.setDate(a, b, c); }
	
	public synchronized void setDate(int a, Date b) throws SQLException
	{ inner.setDate(a, b); }
	
	public synchronized SQLWarning getWarnings() throws SQLException
	{ return inner.getWarnings(); }
	
	public synchronized void clearWarnings() throws SQLException
	{ inner.clearWarnings(); }
	
	public synchronized void setFetchDirection(int a) throws SQLException
	{ inner.setFetchDirection(a); }
	
	public synchronized int getFetchDirection() throws SQLException
	{ return inner.getFetchDirection(); }
	
	public synchronized void setFetchSize(int a) throws SQLException
	{ inner.setFetchSize(a); }
	
	public synchronized int getFetchSize() throws SQLException
	{ return inner.getFetchSize(); }
	
	public synchronized int getResultSetHoldability() throws SQLException
	{ return inner.getResultSetHoldability(); }
	
	public synchronized ResultSet executeQuery(String a) throws SQLException
	{ return inner.executeQuery(a); }
	
	public synchronized int executeUpdate(String a, int b) throws SQLException
	{ return inner.executeUpdate(a, b); }
	
	public synchronized int executeUpdate(String a, String[] b) throws SQLException
	{ return inner.executeUpdate(a, b); }
	
	public synchronized int executeUpdate(String a, int[] b) throws SQLException
	{ return inner.executeUpdate(a, b); }
	
	public synchronized int executeUpdate(String a) throws SQLException
	{ return inner.executeUpdate(a); }
	
	public synchronized int getMaxFieldSize() throws SQLException
	{ return inner.getMaxFieldSize(); }
	
	public synchronized void setMaxFieldSize(int a) throws SQLException
	{ inner.setMaxFieldSize(a); }
	
	public synchronized int getMaxRows() throws SQLException
	{ return inner.getMaxRows(); }
	
	public synchronized void setMaxRows(int a) throws SQLException
	{ inner.setMaxRows(a); }
	
	public synchronized void setEscapeProcessing(boolean a) throws SQLException
	{ inner.setEscapeProcessing(a); }
	
	public synchronized int getQueryTimeout() throws SQLException
	{ return inner.getQueryTimeout(); }
	
	public synchronized void setQueryTimeout(int a) throws SQLException
	{ inner.setQueryTimeout(a); }
	
	public synchronized void setCursorName(String a) throws SQLException
	{ inner.setCursorName(a); }
	
	public synchronized ResultSet getResultSet() throws SQLException
	{ return inner.getResultSet(); }
	
	public synchronized int getUpdateCount() throws SQLException
	{ return inner.getUpdateCount(); }
	
	public synchronized boolean getMoreResults() throws SQLException
	{ return inner.getMoreResults(); }
	
	public synchronized boolean getMoreResults(int a) throws SQLException
	{ return inner.getMoreResults(a); }
	
	public synchronized int getResultSetConcurrency() throws SQLException
	{ return inner.getResultSetConcurrency(); }
	
	public synchronized int getResultSetType() throws SQLException
	{ return inner.getResultSetType(); }
	
	public synchronized void addBatch(String a) throws SQLException
	{ inner.addBatch(a); }
	
	public synchronized void clearBatch() throws SQLException
	{ inner.clearBatch(); }
	
	public synchronized int[] executeBatch() throws SQLException
	{ return inner.executeBatch(); }
	
	public synchronized ResultSet getGeneratedKeys() throws SQLException
	{ return inner.getGeneratedKeys(); }
	
	public synchronized void close() throws SQLException
	{ inner.close(); }
	
	public synchronized boolean execute(String a, int b) throws SQLException
	{ return inner.execute(a, b); }
	
	public synchronized boolean execute(String a) throws SQLException
	{ return inner.execute(a); }
	
	public synchronized boolean execute(String a, int[] b) throws SQLException
	{ return inner.execute(a, b); }
	
	public synchronized boolean execute(String a, String[] b) throws SQLException
	{ return inner.execute(a, b); }
	
	public synchronized Connection getConnection() throws SQLException
	{ return inner.getConnection(); }
	
	public synchronized void cancel() throws SQLException
	{ inner.cancel(); }
}
