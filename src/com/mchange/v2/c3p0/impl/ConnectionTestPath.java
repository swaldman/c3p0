package com.mchange.v2.c3p0.impl;

import java.sql.Connection;
import javax.sql.PooledConnection;

interface ConnectionTestPath
{
    // if proxyConn is provided, just test it.
    // if not, we have to get a proxy Connection from the PooledConnection,
    // then be sure to close() or detach the proxy
    public void testPooledConnection(PooledConnection pc, Connection proxyConn) throws Exception;
}
