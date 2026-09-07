package com.mchange.v2.c3p0.filter;

import java.io.PrintWriter;
import java.lang.String;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;


public abstract class FilterDataSource implements DataSource
{
    protected DataSource inner;

    public FilterDataSource(DataSource inner)
    {
       this.inner = inner;
    }

    @Override
    public Connection getConnection() throws SQLException
    {
        return inner.getConnection();
    }

    @Override
    public Connection getConnection(String a, String b) throws SQLException
    {
        return inner.getConnection(a, b);
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException
    {
        return inner.getLogWriter();
    }

    @Override
    public int getLoginTimeout() throws SQLException
    {
        return inner.getLoginTimeout();
    }

    @Override
    public void setLogWriter(PrintWriter a) throws SQLException
    {
        inner.setLogWriter(a);
    }

    @Override
    public void setLoginTimeout(int a) throws SQLException
    {
        inner.setLoginTimeout(a);
    }

}

