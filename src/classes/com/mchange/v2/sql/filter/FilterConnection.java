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
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.Map;

public abstract class FilterConnection implements Connection
{
	protected Connection inner;
	
	public FilterConnection(Connection inner)
	{ this.inner = inner; }
	
	public FilterConnection()
	{}
	
	public void setInner( Connection inner )
	{ this.inner = inner; }
	
	public Connection getInner()
	{ return inner; }
	
	public Statement createStatement(int a, int b, int c) throws SQLException
	{ return inner.createStatement(a, b, c); }
	
	public Statement createStatement(int a, int b) throws SQLException
	{ return inner.createStatement(a, b); }
	
	public Statement createStatement() throws SQLException
	{ return inner.createStatement(); }
	
	public PreparedStatement prepareStatement(String a, String[] b) throws SQLException
	{ return inner.prepareStatement(a, b); }
	
	public PreparedStatement prepareStatement(String a) throws SQLException
	{ return inner.prepareStatement(a); }
	
	public PreparedStatement prepareStatement(String a, int b, int c) throws SQLException
	{ return inner.prepareStatement(a, b, c); }
	
	public PreparedStatement prepareStatement(String a, int b, int c, int d) throws SQLException
	{ return inner.prepareStatement(a, b, c, d); }
	
	public PreparedStatement prepareStatement(String a, int b) throws SQLException
	{ return inner.prepareStatement(a, b); }
	
	public PreparedStatement prepareStatement(String a, int[] b) throws SQLException
	{ return inner.prepareStatement(a, b); }
	
	public CallableStatement prepareCall(String a, int b, int c, int d) throws SQLException
	{ return inner.prepareCall(a, b, c, d); }
	
	public CallableStatement prepareCall(String a, int b, int c) throws SQLException
	{ return inner.prepareCall(a, b, c); }
	
	public CallableStatement prepareCall(String a) throws SQLException
	{ return inner.prepareCall(a); }
	
	public String nativeSQL(String a) throws SQLException
	{ return inner.nativeSQL(a); }
	
	public void setAutoCommit(boolean a) throws SQLException
	{ inner.setAutoCommit(a); }
	
	public boolean getAutoCommit() throws SQLException
	{ return inner.getAutoCommit(); }
	
	public void commit() throws SQLException
	{ inner.commit(); }
	
	public void rollback(Savepoint a) throws SQLException
	{ inner.rollback(a); }
	
	public void rollback() throws SQLException
	{ inner.rollback(); }
	
	public DatabaseMetaData getMetaData() throws SQLException
	{ return inner.getMetaData(); }
	
	public void setCatalog(String a) throws SQLException
	{ inner.setCatalog(a); }
	
	public String getCatalog() throws SQLException
	{ return inner.getCatalog(); }
	
	public void setTransactionIsolation(int a) throws SQLException
	{ inner.setTransactionIsolation(a); }
	
	public int getTransactionIsolation() throws SQLException
	{ return inner.getTransactionIsolation(); }
	
	public SQLWarning getWarnings() throws SQLException
	{ return inner.getWarnings(); }
	
	public void clearWarnings() throws SQLException
	{ inner.clearWarnings(); }
	
	public Map getTypeMap() throws SQLException
	{ return inner.getTypeMap(); }
	
	public void setTypeMap(Map a) throws SQLException
	{ inner.setTypeMap(a); }
	
	public void setHoldability(int a) throws SQLException
	{ inner.setHoldability(a); }
	
	public int getHoldability() throws SQLException
	{ return inner.getHoldability(); }
	
	public Savepoint setSavepoint() throws SQLException
	{ return inner.setSavepoint(); }
	
	public Savepoint setSavepoint(String a) throws SQLException
	{ return inner.setSavepoint(a); }
	
	public void releaseSavepoint(Savepoint a) throws SQLException
	{ inner.releaseSavepoint(a); }
	
	public void setReadOnly(boolean a) throws SQLException
	{ inner.setReadOnly(a); }
	
	public boolean isReadOnly() throws SQLException
	{ return inner.isReadOnly(); }
	
	public void close() throws SQLException
	{ inner.close(); }
	
	public boolean isClosed() throws SQLException
	{ return inner.isClosed(); }
}
