package com.mchange.v2.c3p0.impl;

import javax.sql.*;
import com.mchange.v2.c3p0.stmt.*;

interface InternalPooledConnection extends PooledConnection
{
    public void initStatementCache( GooGooStatementCache scache );
    public GooGooStatementCache getStatementCache();
    public int getConnectionStatus();
}
