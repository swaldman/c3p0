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

import java.lang.String;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.util.Map;


/*
 * NOT YET USED
 */
class C3P0Connection implements Connection
{
    final Connection inner;

    protected C3P0Connection(Connection inner)
    {
       this.inner = inner;
    }


    public void setReadOnly(boolean a) throws SQLException
    {
        inner.setReadOnly(a);
    }

    public void close() throws SQLException
    {
        inner.close();
    }

    public boolean isClosed() throws SQLException
    {
        return inner.isClosed();
    }

    public boolean isReadOnly() throws SQLException
    {
        return inner.isReadOnly();
    }

    public SQLWarning getWarnings() throws SQLException
    {
        return inner.getWarnings();
    }

    public void clearWarnings() throws SQLException
    {
        inner.clearWarnings();
    }

    public DatabaseMetaData getMetaData() throws SQLException
    {
        return inner.getMetaData();
    }

    public Statement createStatement() throws SQLException
    {
        return inner.createStatement();
    }

    public Statement createStatement(int a, int b) throws SQLException
    {
        return inner.createStatement(a, b);
    }

    public PreparedStatement prepareStatement(String a) throws SQLException
    {
        return inner.prepareStatement(a);
    }

    public PreparedStatement prepareStatement(String a, int b, int c) throws SQLException
    {
        return inner.prepareStatement(a, b, c);
    }

    public CallableStatement prepareCall(String a) throws SQLException
    {
        return inner.prepareCall(a);
    }

    public CallableStatement prepareCall(String a, int b, int c) throws SQLException
    {
        return inner.prepareCall(a, b, c);
    }

    public String nativeSQL(String a) throws SQLException
    {
        return inner.nativeSQL(a);
    }

    public void setAutoCommit(boolean a) throws SQLException
    {
        inner.setAutoCommit(a);
    }

    public boolean getAutoCommit() throws SQLException
    {
        return inner.getAutoCommit();
    }

    public void commit() throws SQLException
    {
        inner.commit();
    }

    public void rollback() throws SQLException
    {
        inner.rollback();
    }

    public void setCatalog(String a) throws SQLException
    {
        inner.setCatalog(a);
    }

    public String getCatalog() throws SQLException
    {
        return inner.getCatalog();
    }

    public void setTransactionIsolation(int a) throws SQLException
    {
        inner.setTransactionIsolation(a);
    }

    public int getTransactionIsolation() throws SQLException
    {
        return inner.getTransactionIsolation();
    }

    public Map getTypeMap() throws SQLException
    {
        return inner.getTypeMap();
    }

    public void setTypeMap(Map a) throws SQLException
    {
        inner.setTypeMap(a);
    }

}

