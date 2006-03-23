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

import java.lang.String;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;

public abstract class SynchronizedFilterStatement implements Statement
{
	protected Statement inner;
	
	public SynchronizedFilterStatement(Statement inner)
	{ this.inner = inner; }
	
	public SynchronizedFilterStatement()
	{}
	
	public synchronized void setInner( Statement inner )
	{ this.inner = inner; }
	
	public synchronized Statement getInner()
	{ return inner; }
	
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
