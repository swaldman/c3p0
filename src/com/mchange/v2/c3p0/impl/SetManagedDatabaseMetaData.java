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

    @Override
    public Connection getConnection() throws SQLException
    { return returnableProxy; }

    @Override
    public ResultSet getProcedures(String a, String b, String c) throws SQLException
    {
        return new NullStatementSetManagedResultSet( inner.getProcedures(a, b, c), activeResultSets );
    }

    @Override
    public ResultSet getProcedureColumns(String a, String b, String c, String d) throws SQLException
    {
        return new NullStatementSetManagedResultSet( inner.getProcedureColumns(a, b, c, d), activeResultSets );
    }

    @Override
    public ResultSet getTables(String a, String b, String c, String[] d) throws SQLException
    {
        return new NullStatementSetManagedResultSet( inner.getTables(a, b, c, d), activeResultSets );
    }

    @Override
    public ResultSet getSchemas() throws SQLException
    {
        return new NullStatementSetManagedResultSet( inner.getSchemas(), activeResultSets );
    }

    @Override
    public ResultSet getCatalogs() throws SQLException
    {
        return new NullStatementSetManagedResultSet( inner.getCatalogs(), activeResultSets );
    }

    @Override
    public ResultSet getTableTypes() throws SQLException
    {
        return new NullStatementSetManagedResultSet( inner.getTableTypes(), activeResultSets );
    }

    @Override
    public ResultSet getColumns(String a, String b, String c, String d) throws SQLException
    {
        return new NullStatementSetManagedResultSet( inner.getColumns(a, b, c, d), activeResultSets );
    }

    @Override
    public ResultSet getColumnPrivileges(String a, String b, String c, String d) throws SQLException
    {
        return new NullStatementSetManagedResultSet( inner.getColumnPrivileges(a, b, c, d), activeResultSets );
    }

    @Override
    public ResultSet getTablePrivileges(String a, String b, String c) throws SQLException
    {
        return new NullStatementSetManagedResultSet( inner.getTablePrivileges(a, b, c), activeResultSets );
    }

    @Override
    public ResultSet getBestRowIdentifier(String a, String b, String c, int d, boolean e) throws SQLException
    {
        return new NullStatementSetManagedResultSet( inner.getBestRowIdentifier(a, b, c, d, e), activeResultSets );
    }

    @Override
    public ResultSet getVersionColumns(String a, String b, String c) throws SQLException
    {
        return new NullStatementSetManagedResultSet( inner.getVersionColumns(a, b, c), activeResultSets );
    }

    @Override
    public ResultSet getPrimaryKeys(String a, String b, String c) throws SQLException
    {
        return new NullStatementSetManagedResultSet( inner.getPrimaryKeys(a, b, c), activeResultSets );
    }

    @Override
    public ResultSet getImportedKeys(String a, String b, String c) throws SQLException
    {
        return new NullStatementSetManagedResultSet( inner.getImportedKeys(a, b, c), activeResultSets );
    }

    @Override
    public ResultSet getExportedKeys(String a, String b, String c) throws SQLException
    {
        return new NullStatementSetManagedResultSet( inner.getExportedKeys(a, b, c), activeResultSets );
    }

    @Override
    public ResultSet getCrossReference(String a, String b, String c, String d, String e, String f) throws SQLException
    {
        return new NullStatementSetManagedResultSet( inner.getCrossReference(a, b, c, d, e, f), activeResultSets );
    }

    @Override
    public ResultSet getTypeInfo() throws SQLException
    {
        return new NullStatementSetManagedResultSet( inner.getTypeInfo(), activeResultSets );
    }

    @Override
    public ResultSet getIndexInfo(String a, String b, String c, boolean d, boolean e) throws SQLException
    {
        return new NullStatementSetManagedResultSet( inner.getIndexInfo(a, b, c, d, e), activeResultSets );
    }

    @Override
    public ResultSet getUDTs(String a, String b, String c, int[] d) throws SQLException
    {
        return new NullStatementSetManagedResultSet( inner.getUDTs(a, b, c, d), activeResultSets );
    }
}


