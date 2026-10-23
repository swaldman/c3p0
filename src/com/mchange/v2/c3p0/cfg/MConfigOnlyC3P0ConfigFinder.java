package com.mchange.v2.c3p0.cfg;

import java.util.HashMap;

public class MConfigOnlyC3P0ConfigFinder implements C3P0ConfigFinder
{
    @Override
    public C3P0Config findConfig()
    {
        HashMap flatDefaults = C3P0ConfigUtils.extractHardcodedC3P0Defaults();
        flatDefaults.putAll( C3P0ConfigUtils.extractC3P0PropertiesResources() );
        return C3P0ConfigUtils.configFromFlatDefaults( flatDefaults );
    }
}
