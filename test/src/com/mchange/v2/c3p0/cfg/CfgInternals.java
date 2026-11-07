package com.mchange.v2.c3p0.cfg;

/**
 *  A test-side window onto package-private details of <code>com.mchange.v2.c3p0.cfg</code>.
 *
 *  <p>Declared into the library's own package from the test sources, the way
 *  {@link com.mchange.v2.c3p0.stmt.StatementCacheAuditor} is, so that tests can assert against
 *  internals without the library widening its public API to accommodate them.</p>
 *
 *  <p>Each accessor is a <b>method</b>, deliberately, even where it returns a compile-time
 *  constant. A test that copied such a constant into its own class file would keep asserting the
 *  old value after a rename, passing while the library had moved on &mdash; which is the one
 *  thing a test pinning a wire-format detail must not do.</p>
 */
public final class CfgInternals
{
    /**
     *  The value the XML source writes at a named scope's bare key to mark it declared when it
     *  would otherwise contribute no keys at all.
     *
     *  <p>Lives on {@code C3P0ConfigUtilsNoLogging}, which is package-private because a
     *  {@code PropertiesConfigSource} runs while MLog is still loading its own configuration and
     *  so must not touch anything that logs.</p>
     */
    public static String potentiallyEmptyNamedScopeMarkerValue()
    { return C3P0ConfigUtilsNoLogging.POTENTIALLY_EMPTY_NAMED_SCOPE_MARKER_VALUE; }

    /**
     *  The resource-path list c3p0's build generates, as the library itself reads it.
     *
     *  <p>{@code MLogC3P0SharedResources} is package-private and generated, so a test can only
     *  reach it from inside the package.</p>
     */
    public static String[] declaredSharedResources()
    { return MLogC3P0SharedResources.get(); }

    private CfgInternals()
    {}
}
