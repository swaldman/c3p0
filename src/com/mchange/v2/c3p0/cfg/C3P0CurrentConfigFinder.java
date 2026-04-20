package com.mchange.v2.c3p0.cfg;

import com.mchange.v2.cfg.PropertiesConfig;
import com.mchange.v2.cfg.CurrentConfigFinder;

public class C3P0CurrentConfigFinder implements CurrentConfigFinder
{
    public static C3P0CurrentConfigFinder INSTANCE = new C3P0CurrentConfigFinder();

    public PropertiesConfig findCurrentConfig()
    { return C3P0Config.getMultiPropertiesConfig(); }

    private C3P0CurrentConfigFinder()
    {}
}

