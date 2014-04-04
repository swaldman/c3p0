/*
 * Distributed as part of c3p0 0.9.5-pre8
 *
 * Copyright (C) 2014 Machinery For Change, Inc.
 *
 * Author: Steve Waldman <swaldman@mchange.com>
 *
 * This library is free software; you can redistribute it and/or modify
 * it under the terms of EITHER:
 *
 *     1) The GNU Lesser General Public License (LGPL), version 2.1, as 
 *        published by the Free Software Foundation
 *
 * OR
 *
 *     2) The Eclipse Public License (EPL), version 1.0
 *
 * You may choose which license to accept if you wish to redistribute
 * or modify this work. You may offer derivatives of this work
 * under the license you have chosen, or you may provide the same
 * choice of license which you have been offered here.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * You should have received copies of both LGPL v2.1 and EPL v1.0
 * along with this software; see the files LICENSE-EPL and LICENSE-LGPL.
 * If not, the text of these licenses are currently available at
 *
 * LGPL v2.1: http://www.gnu.org/licenses/old-licenses/lgpl-2.1.html
 *  EPL v1.0: http://www.eclipse.org/org/documents/epl-v10.php 
 * 
 */

package com.mchange.v2.c3p0.impl;

import java.sql.*;
import java.util.Set;
import com.mchange.v2.sql.filter.FilterDatabaseMetaData;

final class SetManagedDatabaseMetaData extends FilterDatabaseMetaData
{
    Set activeResultSets;
    Connection returnableProxy;

    SetManagedDatabaseMetaData( DatabaseMetaData inner, Set activeResultSets, Connection returnableProxy )
    {
		super( inner );
		this.activeResultSets = activeResultSets;
		this.returnableProxy = returnableProxy;
    }

    public Connection getConnection() throws SQLException
    { return returnableProxy; }

    public ResultSet getProcedures(String a, String b, String c) throws SQLException
    {
        return new NullStatementSetManagedResultSet( inner.getProcedures(a, b, c), activeResultSets );
    }

    public ResultSet getProcedureColumns(String a, String b, String c, String d) throws SQLException
    {
        return new NullStatementSetManagedResultSet( inner.getProcedureColumns(a, b, c, d), activeResultSets );
    }

    public ResultSet getTables(String a, String b, String c, String[] d) throws SQLException
    {
        return new NullStatementSetManagedResultSet( inner.getTables(a, b, c, d), activeResultSets );
    }

    public ResultSet getSchemas() throws SQLException
    {
        return new NullStatementSetManagedResultSet( inner.getSchemas(), activeResultSets );
    }

    public ResultSet getCatalogs() throws SQLException
    {
        return new NullStatementSetManagedResultSet( inner.getCatalogs(), activeResultSets );
    }

    public ResultSet getTableTypes() throws SQLException
    {
        return new NullStatementSetManagedResultSet( inner.getTableTypes(), activeResultSets );
    }

    public ResultSet getColumns(String a, String b, String c, String d) throws SQLException
    {
        return new NullStatementSetManagedResultSet( inner.getColumns(a, b, c, d), activeResultSets );
    }

    public ResultSet getColumnPrivileges(String a, String b, String c, String d) throws SQLException
    {
        return new NullStatementSetManagedResultSet( inner.getColumnPrivileges(a, b, c, d), activeResultSets );
    }

    public ResultSet getTablePrivileges(String a, String b, String c) throws SQLException
    {
        return new NullStatementSetManagedResultSet( inner.getTablePrivileges(a, b, c), activeResultSets );
    }

    public ResultSet getBestRowIdentifier(String a, String b, String c, int d, boolean e) throws SQLException
    {
        return new NullStatementSetManagedResultSet( inner.getBestRowIdentifier(a, b, c, d, e), activeResultSets );
    }

    public ResultSet getVersionColumns(String a, String b, String c) throws SQLException
    {
        return new NullStatementSetManagedResultSet( inner.getVersionColumns(a, b, c), activeResultSets );
    }

    public ResultSet getPrimaryKeys(String a, String b, String c) throws SQLException
    {
        return new NullStatementSetManagedResultSet( inner.getPrimaryKeys(a, b, c), activeResultSets );
    }

    public ResultSet getImportedKeys(String a, String b, String c) throws SQLException
    {
        return new NullStatementSetManagedResultSet( inner.getImportedKeys(a, b, c), activeResultSets );
    }

    public ResultSet getExportedKeys(String a, String b, String c) throws SQLException
    {
        return new NullStatementSetManagedResultSet( inner.getExportedKeys(a, b, c), activeResultSets );
    }

    public ResultSet getCrossReference(String a, String b, String c, String d, String e, String f) throws SQLException
    {
        return new NullStatementSetManagedResultSet( inner.getCrossReference(a, b, c, d, e, f), activeResultSets );
    }

    public ResultSet getTypeInfo() throws SQLException
    {
        return new NullStatementSetManagedResultSet( inner.getTypeInfo(), activeResultSets );
    }

    public ResultSet getIndexInfo(String a, String b, String c, boolean d, boolean e) throws SQLException
    {
        return new NullStatementSetManagedResultSet( inner.getIndexInfo(a, b, c, d, e), activeResultSets );
    }

    public ResultSet getUDTs(String a, String b, String c, int[] d) throws SQLException
    {
        return new NullStatementSetManagedResultSet( inner.getUDTs(a, b, c, d), activeResultSets );
    }
}


