package com.mchange.v2.c3p0.impl;

import java.sql.ResultSet;

/**
 * This is an internal interface, not intended for use by library users.
 * (It is exposed publicly only for use by c3p0's code generation library.)
 */
public interface ProxyResultSetDetachable
{
    void detachProxyResultSet( ResultSet prs );
}

