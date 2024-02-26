package com.mchange.v2.c3p0.cfg;

import java.sql.SQLException;

public interface C3P0ConfigFinder
{
    public C3P0Config findConfig() throws Exception;
}
