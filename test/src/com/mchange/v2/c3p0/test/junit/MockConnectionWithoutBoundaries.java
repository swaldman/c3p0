package com.mchange.v2.c3p0.test.junit;

import java.sql.*;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

public class MockConnectionWithoutBoundaries implements Connection {

  @Override
  public void abort(Executor executor) throws SQLException {
  }

  @Override
  public void clearWarnings() throws SQLException {
  }

  @Override
  public void close() throws SQLException {
  }

  @Override
  public void commit() throws SQLException {
  }

  @Override
  public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
    return null;
  }

  @Override
  public Blob createBlob() throws SQLException {
    return null;
  }

  @Override
  public Clob createClob() throws SQLException {
    return null;
  }

  @Override
  public NClob createNClob() throws SQLException {
    return null;
  }

  @Override
  public SQLXML createSQLXML() throws SQLException {
    return null;
  }

  @Override
  public Statement createStatement() throws SQLException {
    return null;
  }

  @Override
  public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
    return null;
  }

  @Override
  public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability)
      throws SQLException {

    return null;
  }

  @Override
  public Struct createStruct(String typeName, Object[] attributes) throws SQLException {

    return null;
  }

  @Override
  public boolean getAutoCommit() throws SQLException {

    return false;
  }

  @Override
  public String getCatalog() throws SQLException {

    return null;
  }

  @Override
  public Properties getClientInfo() throws SQLException {

    return null;
  }

  @Override
  public String getClientInfo(String name) throws SQLException {

    return null;
  }

  @Override
  public int getHoldability() throws SQLException {

    return 0;
  }

  @Override
  public DatabaseMetaData getMetaData() throws SQLException {

    return null;
  }

  @Override
  public int getNetworkTimeout() throws SQLException {

    return 0;
  }

  @Override
  public String getSchema() throws SQLException {

    return null;
  }

  @Override
  public int getTransactionIsolation() throws SQLException {

    return 0;
  }

  @Override
  public Map<String, Class<?>> getTypeMap() throws SQLException {

    return null;
  }

  @Override
  public SQLWarning getWarnings() throws SQLException {

    return null;
  }

  @Override
  public boolean isClosed() throws SQLException {

    return false;
  }

  @Override
  public boolean isReadOnly() throws SQLException {

    return false;
  }

  @Override
  public boolean isValid(int timeout) throws SQLException {

    return true; // otherwise we'll blow stack if testing with testConnectionOnCheckout=true
  }

  @Override
  public String nativeSQL(String sql) throws SQLException {

    return null;
  }

  @Override
  public CallableStatement prepareCall(String sql) throws SQLException {

    return null;
  }

  @Override
  public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {

    return null;
  }

  @Override
  public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency,
      int resultSetHoldability) throws SQLException {

    return null;
  }

  @Override
  public PreparedStatement prepareStatement(String sql) throws SQLException {

    return null;
  }

  @Override
  public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {

    return null;
  }

  @Override
  public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {

    return null;
  }

  @Override
  public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {

    return null;
  }

  @Override
  public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency)
      throws SQLException {

    return null;
  }

  @Override
  public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency,
      int resultSetHoldability) throws SQLException {

    return null;
  }

  @Override
  public void releaseSavepoint(Savepoint savepoint) throws SQLException {

  }

  @Override
  public void rollback() throws SQLException {

  }

  @Override
  public void rollback(Savepoint savepoint) throws SQLException {

  }

  @Override
  public void setAutoCommit(boolean autoCommit) throws SQLException {

  }

  @Override
  public void setCatalog(String catalog) throws SQLException {

  }

  @Override
  public void setClientInfo(Properties properties) throws SQLClientInfoException {

  }

  @Override
  public void setClientInfo(String name, String value) throws SQLClientInfoException {

  }

  @Override
  public void setHoldability(int holdability) throws SQLException {

  }

  @Override
  public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException {

  }

  @Override
  public void setReadOnly(boolean readOnly) throws SQLException {

  }

  @Override
  public Savepoint setSavepoint() throws SQLException {

    return null;
  }

  @Override
  public Savepoint setSavepoint(String name) throws SQLException {

    return null;
  }

  @Override
  public void setSchema(String schema) throws SQLException {

  }

  @Override
  public void setTransactionIsolation(int level) throws SQLException {

  }

  @Override
  public void setTypeMap(Map<String, Class<?>> map) throws SQLException {

  }

  @Override
  public boolean isWrapperFor(Class<?> iface) throws SQLException {

    return false;
  }

  @Override
  public <T> T unwrap(Class<T> iface) throws SQLException {

    return null;
  }

}
