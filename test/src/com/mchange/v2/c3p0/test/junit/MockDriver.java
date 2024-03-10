package com.mchange.v2.c3p0.test.junit;

import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import junit.framework.*;

public class MockDriver implements Driver {
  public static final AtomicInteger beginRequestCount = new AtomicInteger(0);
  public static final AtomicInteger endRequestCount = new AtomicInteger(0);

  @Override
  public boolean acceptsURL(String url) throws SQLException {
    return true;
  }

  @Override
  public Connection connect(String url, Properties info) throws SQLException {
    if (url.contains("with-request-boundaries")) {
      return new MockConnectionWithBoundaries();
    } else {
      return new MockConnectionWithoutBoundaries();
    }
  }

  @Override
  public int getMajorVersion() {
    return 0;
  }

  @Override
  public int getMinorVersion() {
    return 0;
  }

  @Override
  public Logger getParentLogger() throws SQLFeatureNotSupportedException {
    return null;
  }

  @Override
  public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
    return null;
  }

  @Override
  public boolean jdbcCompliant() {
    return true;
  }

}