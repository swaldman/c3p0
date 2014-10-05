package com.mchange.v2.c3p0.test;

import java.sql.*;
import javax.sql.*;

public final class AlwaysFailDataSource implements DataSource
{
    private static String MESSAGE = "AlwaysFailDataSource always fails.";

    private static SQLException failure() { return new SQLException( MESSAGE ); }
    public Connection getConnection() throws SQLException { throw failure(); } 
    public Connection getConnection( String user, String password )  throws SQLException { throw failure(); } 
    public java.io.PrintWriter getLogWriter() throws SQLException { throw failure(); } 
    public void setLogWriter(java.io.PrintWriter pw) throws SQLException { throw failure(); }  
    public void setLoginTimeout(int i) throws SQLException { throw failure(); }
    public int getLoginTimeout() throws SQLException { throw failure(); }
    public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException
    { throw new SQLFeatureNotSupportedException( MESSAGE ); }
    public <T> T unwrap(Class<T> clz) throws SQLException { throw failure(); }
    public boolean isWrapperFor(java.lang.Class<?> clz) throws SQLException { throw failure(); }
}
