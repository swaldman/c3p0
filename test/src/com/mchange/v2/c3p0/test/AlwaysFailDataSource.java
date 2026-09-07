package com.mchange.v2.c3p0.test;

import java.sql.*;
import javax.sql.*;

public final class AlwaysFailDataSource implements DataSource
{
    private static String MESSAGE = "AlwaysFailDataSource always fails.";

    private static SQLException failure() { return new SQLException( MESSAGE ); }
    @Override
    public Connection getConnection() throws SQLException { throw failure(); } 
    @Override
    public Connection getConnection( String user, String password )  throws SQLException { throw failure(); } 
    @Override
    public java.io.PrintWriter getLogWriter() throws SQLException { throw failure(); } 
    @Override
    public void setLogWriter(java.io.PrintWriter pw) throws SQLException { throw failure(); }  
    @Override
    public void setLoginTimeout(int i) throws SQLException { throw failure(); }
    @Override
    public int getLoginTimeout() throws SQLException { throw failure(); }
    @Override
    public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException
    { throw new SQLFeatureNotSupportedException( MESSAGE ); }
    @Override
    public <T> T unwrap(Class<T> clz) throws SQLException { throw failure(); }
    @Override
    public boolean isWrapperFor(java.lang.Class<?> clz) throws SQLException { throw failure(); }
}
