package com.mchange.v2.c3p0;

import java.sql.Connection;

public interface QueryConnectionTester extends ConnectionTester
{
    public int activeCheckConnection(Connection c, String preferredTestQuery);
}

