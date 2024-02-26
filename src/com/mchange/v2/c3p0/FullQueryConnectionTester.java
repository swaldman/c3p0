package com.mchange.v2.c3p0;

import java.sql.Connection;

public interface FullQueryConnectionTester extends QueryConnectionTester
{
    public int statusOnException(Connection c, Throwable t, String preferredTestQuery);
}

