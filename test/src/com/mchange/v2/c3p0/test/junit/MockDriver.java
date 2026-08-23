package com.mchange.v2.c3p0.test.junit;

import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import junit.framework.*;

public class MockDriver implements Driver {
  public static final AtomicInteger beginRequestCount = new AtomicInteger(0);
  public static final AtomicInteger endRequestCount = new AtomicInteger(0);

  // the schemes ConnectionBoundariesJUnitTestCase drives this driver with
  public static final String[] URL_PREFIXES = { "mock:", "test:" };

  @Override
  public boolean acceptsURL(String url) throws SQLException {
    // Claiming every URL would be a trap for any other test that registers an in-process driver:
    // DriverManager hands a URL to drivers in registration order and takes the first Connection it
    // is offered, so a promiscuous driver silently serves everybody else's URLs too. That is not
    // theoretical -- on 0.15.x it hung a test whose fake driver's URLs this one was answering, by
    // handing back a Connection whose prepareStatement(...) returns null.
    if (url == null) {
      return false;
    }
    for (String prefix : URL_PREFIXES) {
      if (url.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public Connection connect(String url, Properties info) throws SQLException {
    if (!acceptsURL(url)) {
      return null; // per the JDBC spec, so DriverManager goes on to try the other drivers
    } else if (url.contains("with-request-boundaries")) {
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