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
import java.sql.Connection;
import java.sql.Date;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Calendar;

public abstract class FilterPreparedStatement implements PreparedStatement
{
	protected PreparedStatement inner;
	
	public FilterPreparedStatement(PreparedStatement inner)
	{ this.inner = inner; }
	
	public FilterPreparedStatement()
	{}
	
	public void setInner( PreparedStatement inner )
	{ this.inner = inner; }
	
	public PreparedStatement getInner()
	{ return inner; }
	
	public ResultSetMetaData getMetaData() throws SQLException
	{ return inner.getMetaData(); }
	
	public ResultSet executeQuery() throws SQLException
	{ return inner.executeQuery(); }
	
	public int executeUpdate() throws SQLException
	{ return inner.executeUpdate(); }
	
	public void addBatch() throws SQLException
	{ inner.addBatch(); }
	
	public void setNull(int a, int b, String c) throws SQLException
	{ inner.setNull(a, b, c); }
	
	public void setNull(int a, int b) throws SQLException
	{ inner.setNull(a, b); }
	
	public void setBigDecimal(int a, BigDecimal b) throws SQLException
	{ inner.setBigDecimal(a, b); }
	
	public void setBytes(int a, byte[] b) throws SQLException
	{ inner.setBytes(a, b); }
	
	public void setTimestamp(int a, Timestamp b, Calendar c) throws SQLException
	{ inner.setTimestamp(a, b, c); }
	
	public void setTimestamp(int a, Timestamp b) throws SQLException
	{ inner.setTimestamp(a, b); }
	
	public void setAsciiStream(int a, InputStream b, int c) throws SQLException
	{ inner.setAsciiStream(a, b, c); }
	
	public void setUnicodeStream(int a, InputStream b, int c) throws SQLException
	{ inner.setUnicodeStream(a, b, c); }
	
	public void setBinaryStream(int a, InputStream b, int c) throws SQLException
	{ inner.setBinaryStream(a, b, c); }
	
	public void clearParameters() throws SQLException
	{ inner.clearParameters(); }
	
	public void setObject(int a, Object b) throws SQLException
	{ inner.setObject(a, b); }
	
	public void setObject(int a, Object b, int c, int d) throws SQLException
	{ inner.setObject(a, b, c, d); }
	
	public void setObject(int a, Object b, int c) throws SQLException
	{ inner.setObject(a, b, c); }
	
	public void setCharacterStream(int a, Reader b, int c) throws SQLException
	{ inner.setCharacterStream(a, b, c); }
	
	public void setRef(int a, Ref b) throws SQLException
	{ inner.setRef(a, b); }
	
	public void setBlob(int a, Blob b) throws SQLException
	{ inner.setBlob(a, b); }
	
	public void setClob(int a, Clob b) throws SQLException
	{ inner.setClob(a, b); }
	
	public void setArray(int a, Array b) throws SQLException
	{ inner.setArray(a, b); }
	
	public ParameterMetaData getParameterMetaData() throws SQLException
	{ return inner.getParameterMetaData(); }
	
	public void setBoolean(int a, boolean b) throws SQLException
	{ inner.setBoolean(a, b); }
	
	public void setByte(int a, byte b) throws SQLException
	{ inner.setByte(a, b); }
	
	public void setShort(int a, short b) throws SQLException
	{ inner.setShort(a, b); }
	
	public void setInt(int a, int b) throws SQLException
	{ inner.setInt(a, b); }
	
	public void setLong(int a, long b) throws SQLException
	{ inner.setLong(a, b); }
	
	public void setFloat(int a, float b) throws SQLException
	{ inner.setFloat(a, b); }
	
	public void setDouble(int a, double b) throws SQLException
	{ inner.setDouble(a, b); }
	
	public void setURL(int a, URL b) throws SQLException
	{ inner.setURL(a, b); }
	
	public void setTime(int a, Time b) throws SQLException
	{ inner.setTime(a, b); }
	
	public void setTime(int a, Time b, Calendar c) throws SQLException
	{ inner.setTime(a, b, c); }
	
	public boolean execute() throws SQLException
	{ return inner.execute(); }
	
	public void setString(int a, String b) throws SQLException
	{ inner.setString(a, b); }
	
	public void setDate(int a, Date b, Calendar c) throws SQLException
	{ inner.setDate(a, b, c); }
	
	public void setDate(int a, Date b) throws SQLException
	{ inner.setDate(a, b); }
	
	public SQLWarning getWarnings() throws SQLException
	{ return inner.getWarnings(); }
	
	public void clearWarnings() throws SQLException
	{ inner.clearWarnings(); }
	
	public void setFetchDirection(int a) throws SQLException
	{ inner.setFetchDirection(a); }
	
	public int getFetchDirection() throws SQLException
	{ return inner.getFetchDirection(); }
	
	public void setFetchSize(int a) throws SQLException
	{ inner.setFetchSize(a); }
	
	public int getFetchSize() throws SQLException
	{ return inner.getFetchSize(); }
	
	public int getResultSetHoldability() throws SQLException
	{ return inner.getResultSetHoldability(); }
	
	public ResultSet executeQuery(String a) throws SQLException
	{ return inner.executeQuery(a); }
	
	public int executeUpdate(String a, int b) throws SQLException
	{ return inner.executeUpdate(a, b); }
	
	public int executeUpdate(String a, String[] b) throws SQLException
	{ return inner.executeUpdate(a, b); }
	
	public int executeUpdate(String a, int[] b) throws SQLException
	{ return inner.executeUpdate(a, b); }
	
	public int executeUpdate(String a) throws SQLException
	{ return inner.executeUpdate(a); }
	
	public int getMaxFieldSize() throws SQLException
	{ return inner.getMaxFieldSize(); }
	
	public void setMaxFieldSize(int a) throws SQLException
	{ inner.setMaxFieldSize(a); }
	
	public int getMaxRows() throws SQLException
	{ return inner.getMaxRows(); }
	
	public void setMaxRows(int a) throws SQLException
	{ inner.setMaxRows(a); }
	
	public void setEscapeProcessing(boolean a) throws SQLException
	{ inner.setEscapeProcessing(a); }
	
	public int getQueryTimeout() throws SQLException
	{ return inner.getQueryTimeout(); }
	
	public void setQueryTimeout(int a) throws SQLException
	{ inner.setQueryTimeout(a); }
	
	public void setCursorName(String a) throws SQLException
	{ inner.setCursorName(a); }
	
	public ResultSet getResultSet() throws SQLException
	{ return inner.getResultSet(); }
	
	public int getUpdateCount() throws SQLException
	{ return inner.getUpdateCount(); }
	
	public boolean getMoreResults() throws SQLException
	{ return inner.getMoreResults(); }
	
	public boolean getMoreResults(int a) throws SQLException
	{ return inner.getMoreResults(a); }
	
	public int getResultSetConcurrency() throws SQLException
	{ return inner.getResultSetConcurrency(); }
	
	public int getResultSetType() throws SQLException
	{ return inner.getResultSetType(); }
	
	public void addBatch(String a) throws SQLException
	{ inner.addBatch(a); }
	
	public void clearBatch() throws SQLException
	{ inner.clearBatch(); }
	
	public int[] executeBatch() throws SQLException
	{ return inner.executeBatch(); }
	
	public ResultSet getGeneratedKeys() throws SQLException
	{ return inner.getGeneratedKeys(); }
	
	public void close() throws SQLException
	{ inner.close(); }
	
	public boolean execute(String a, int b) throws SQLException
	{ return inner.execute(a, b); }
	
	public boolean execute(String a) throws SQLException
	{ return inner.execute(a); }
	
	public boolean execute(String a, int[] b) throws SQLException
	{ return inner.execute(a, b); }
	
	public boolean execute(String a, String[] b) throws SQLException
	{ return inner.execute(a, b); }
	
	public Connection getConnection() throws SQLException
	{ return inner.getConnection(); }
	
	public void cancel() throws SQLException
	{ inner.cancel(); }
}
