/*
 * Distributed as part of c3p0 v.0.9.5.3
 *
 * Copyright (C) 2018 Machinery For Change, Inc.
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

package com.mchange.v2.c3p0.test.junit;

import java.sql.*;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import junit.framework.*;
import com.mchange.v2.c3p0.*;

public final class ConnectionBoundariesJUnitTestCase extends TestCase {

  public static void setUpBeforeClass() throws Exception {
    DriverManager.registerDriver(new MockDriver());
  }

  public void testConnectionWithBoundaries() throws Exception {
    DriverManager.registerDriver(new MockDriver());
    ComboPooledDataSource cpds = new ComboPooledDataSource();
    cpds.setDriverClass("com.mchange.v2.c3p0.test.junit.MockDriver"); // loads the jdbc driver
    cpds.setJdbcUrl("mock:driver@with-request-boundaries");
    cpds.setUser("dbuser");
    cpds.setPassword("dbpassword");

    assertEquals("Expect no calls to beginRequest before getConnection", 0, MockDriver.beginRequestCount.get());
    assertEquals("Expect no calls to endRequest before getConnection", 0, MockDriver.endRequestCount.get());
    Connection con = cpds.getConnection();
    assertEquals("Expect a call to beginRequest after getConnection", 1, MockDriver.beginRequestCount.get());
    assertEquals("Expect no calls to endRequest after getConnection", 0, MockDriver.endRequestCount.get());
    con.close();
    assertEquals("Expect a call to beginRequest after close", 1, MockDriver.beginRequestCount.get());
    assertEquals("Expect a call to endRequest after close", 1, MockDriver.endRequestCount.get());

    assertEquals("Expect no calls to beginRequest before getConnection", 1, MockDriver.beginRequestCount.get());
    assertEquals("Expect no calls to endRequest before getConnection", 1, MockDriver.endRequestCount.get());
    con = cpds.getConnection();
    assertEquals("Expect a call to beginRequest after getConnection", 2, MockDriver.beginRequestCount.get());
    assertEquals("Expect no calls to endRequest after getConnection", 1, MockDriver.endRequestCount.get());
    con.close();
    assertEquals("Expect a call to beginRequest after close", 2, MockDriver.beginRequestCount.get());
    assertEquals("Expect a call to endRequest after close", 2, MockDriver.endRequestCount.get());
  }

  public void testConnectionWithoutBoundaries() throws Exception {
    DriverManager.registerDriver(new MockDriver());
    ComboPooledDataSource cpds = new ComboPooledDataSource();
    cpds.setDriverClass("com.mchange.v2.c3p0.test.junit.MockDriver"); // loads the jdbc driver
    cpds.setJdbcUrl("test:driver@without-request-boundaries");
    cpds.setUser("dbuser");
    cpds.setPassword("dbpassword");

    assertEquals("Expect no calls to beginRequest before getConnection", 0, MockDriver.beginRequestCount.get());
    assertEquals("Expect no calls to endRequest before getConnection", 0, MockDriver.endRequestCount.get());
    Connection con = cpds.getConnection();
    assertEquals("Expect no calls to beginRequest after getConnection", 0, MockDriver.beginRequestCount.get());
    assertEquals("Expect no call to endRequest after getConnection", 0, MockDriver.endRequestCount.get());
    con.close();
    assertEquals("Expect no calls to beginRequest after close", 0, MockDriver.beginRequestCount.get());
    assertEquals("Expect no calls to endRequest after close", 0, MockDriver.endRequestCount.get());

    assertEquals("Expect no calls to beginRequest before getConnection", 0, MockDriver.beginRequestCount.get());
    assertEquals("Expect no calls to endRequest before getConnection", 0, MockDriver.endRequestCount.get());
    con = cpds.getConnection();
    assertEquals("Expect no calls to beginRequest after getConnection", 0, MockDriver.beginRequestCount.get());
    assertEquals("Expect no call to endRequest after getConnection", 0, MockDriver.endRequestCount.get());
    con.close();
    assertEquals("Expect no calls to beginRequest after close", 0, MockDriver.beginRequestCount.get());
    assertEquals("Expect no calls to endRequest after close", 0, MockDriver.endRequestCount.get());
  }

}
