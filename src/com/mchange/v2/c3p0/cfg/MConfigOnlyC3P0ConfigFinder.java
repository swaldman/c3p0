package com.mchange.v2.c3p0.cfg;

import java.util.HashMap;
import com.mchange.v2.cfg.MultiPropertiesConfig;

public class MConfigOnlyC3P0ConfigFinder implements C3P0ConfigFinder
{
    @Override
    public C3P0Config findConfig()
    {
        MultiPropertiesConfig mpcfg = C3P0Config.getMultiPropertiesConfig();
        HashMap flatDefaults = new HashMap(C3P0ConfigUtils.extractHardcodedC3P0DefaultsStringCoercedOnly());
        flatDefaults.putAll( C3P0ConfigUtils.extractC3P0PropertiesResources(mpcfg) );
        return C3P0ConfigUtils.configFromFlatDefaults( flatDefaults );
    }
}
