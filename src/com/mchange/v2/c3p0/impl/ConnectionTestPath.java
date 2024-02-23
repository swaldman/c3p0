package com.mchange.v2.c3p0.impl;

import java.sql.Connection;
import javax.sql.PooledConnection;

interface ConnectionTestPath
{
    public void testPooledConnection(PooledConnection pc, Connection proxyConn) throws Exception;
}
