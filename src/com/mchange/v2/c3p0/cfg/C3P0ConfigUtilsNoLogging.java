package com.mchange.v2.c3p0.cfg;

import java.util.*;

/**
 *  PropertiesConfigSource implementations must not touch anything that directly logs,
 *  as they are delegates of the com.mchange.v2.cfg library which must initialize
 *  prior to to mlog library, as mlog itself is configured by that library.
 *
 *  This class is a location for utilities that need to be hit by c3p0 PropertiesConfigSource
 *  implementations and also used by other classes. The main C3P0ConfigUtils class sets up
 *  logging in its class initializer, and so requires that the config library initialization
 *  be completed before it is touched. This class imposes no such restriction.
 */
final class C3P0ConfigUtilsNoLogging
{
    final static String PROP_STYLE_NAMED_CFG_PART      = "named-configs";
    final static String PROP_STYLE_USER_OVERRIDES_PART = "user-overrides";
    final static String PROP_STYLE_EXTENSIONS_PART     = "extensions";

    final static String POTENTIALLY_EMPTY_NAMED_SCOPE_MARKER_VALUE = "com.mchange.v2.c3p0.cfg.potentiallyEmptyNamedScopeMarkerValue";

    /**
     * @param name must not be null, guard prior to calling
     */
    static boolean nameContainsMarkers(String name)
    {
        Set<String> nameElements = new HashSet<String>(Arrays.asList(name.split("\\.")));
        return (nameElements.contains(PROP_STYLE_NAMED_CFG_PART) || nameElements.contains(PROP_STYLE_EXTENSIONS_PART) || nameElements.contains(PROP_STYLE_USER_OVERRIDES_PART));
    }

    private C3P0ConfigUtilsNoLogging() {}
}
