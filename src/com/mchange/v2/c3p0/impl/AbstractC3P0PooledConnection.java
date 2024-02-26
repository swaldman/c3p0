package com.mchange.v2.c3p0.impl;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.PooledConnection;
import com.mchange.v2.c3p0.stmt.GooGooStatementCache;
import com.mchange.v1.util.ClosableResource;

abstract class AbstractC3P0PooledConnection implements PooledConnection, ClosableResource
{
    // thread-safe post c'tor constant, accessed directly by C3P0PooledConnectionPool
    // since the StatementCache "in-use" marker doesn't nest, we have to ensure that
    // internal uses Connection tests don't overlap. (External use, due to checkout,
    // is no problem, no internal operation are performed on checked-out PooledConnections
    final Object inInternalUseLock = new Object();

    abstract Connection getPhysicalConnection();
    abstract void initStatementCache(GooGooStatementCache scache);
    abstract void closeMaybeCheckedOut( boolean checked_out ) throws SQLException;
}
