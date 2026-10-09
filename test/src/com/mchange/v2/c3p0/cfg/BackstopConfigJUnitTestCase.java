package com.mchange.v2.c3p0.cfg;

import java.util.Arrays;
import java.util.Properties;

import com.mchange.v2.cfg.MultiPropertiesConfig;

import junit.framework.TestCase;

/**
 *  C3P0Config.refreshMainConfig(...) accepts <i>backstops</i> as well as <i>overrides</i>:
 *  configuration consulted only where nothing else supplies a value, the mirror of overrides,
 *  which win over everything.
 *
 *  <p>The three layers are combined as <code>[backstops..., libraryConfig, overrides...]</code>,
 *  so every assertion here is about relative precedence rather than about any particular value.
 *  Nothing here needs a database.</p>
 *
 *  <p>One assertion is less obvious than the others and matters more. Overrides and backstops
 *  supplied as <code>Properties</code> are converted to configs that must name <i>distinct</i>
 *  notional resource paths. MConfig.combine's own contract says a resource path can hold only
 *  one position, and that when two combined configs name the same path the outcome is one of two
 *  admissible possibilities which it explicitly declines to choose between. Converting both
 *  layers through the single-argument MultiPropertiesConfig.fromProperties(Properties) would put
 *  them at the same path and leave overrides-beat-backstops resting on an unpromised tiebreak --
 *  which, today, happens to resolve the way we want. So the distinctness is the thing to pin:
 *  testOverridesBeatBackstops would keep passing if it regressed.</p>
 */
public class BackstopConfigJUnitTestCase extends TestCase
{
    /**
     *  Set by the library's own /c3p0-default.properties, so it is present whichever
     *  c3p0.properties a given test run selects.
     */
    private final static String LIBRARY_SET_KEY = "com.mchange.v2.naming.objectFactory.whitelist.c3p0-internal";

    /** Named so that nothing in any shipped or test configuration sets it. */
    private final static String UNSET_KEY = "com.mchange.v2.c3p0.test.backstopOnlyProbeKey";

    private final static String OVERRIDES_PATH = "PROGRAMMATICALLY_SUPPLIED_OVERRIDES";
    private final static String BACKSTOPS_PATH = "PROGRAMMATICALLY_SUPPLIED_BACKSTOPS";

    @Override
    protected void tearDown()
    { C3P0Config.refreshMainConfig(); }

    private static String value( String key )
    { return C3P0Config.getMultiPropertiesConfig().getProperty( key ); }

    private static Properties props( String key, String value )
    {
        Properties p = new Properties();
        p.setProperty( key, value );
        return p;
    }

    public void testABackstopSuppliesAnOtherwiseUnsetKey()
    {
        assertNull( "Precondition: nothing should set " + UNSET_KEY + ", or this test proves nothing.",
                    value( UNSET_KEY ) );

        C3P0Config.refreshMainConfig( (Properties) null, null, props( UNSET_KEY, "fromBackstop" ), "backstop only" );

        assertEquals( "A backstop must supply a value where nothing else does.",
                      "fromBackstop", value( UNSET_KEY ) );
    }

    public void testABackstopLosesToTheLibraryConfiguration()
    {
        String configured = value( LIBRARY_SET_KEY );
        assertNotNull( "Precondition: " + LIBRARY_SET_KEY + " should come from /c3p0-default.properties. " +
                       "If this is null the fixture is broken, not the behavior.", configured );

        C3P0Config.refreshMainConfig( (Properties) null, null, props( LIBRARY_SET_KEY, "BACKSTOP_SHOULD_LOSE" ), "losing backstop" );

        assertEquals( "A backstop must NOT displace a value the configuration already supplies -- " +
                      "that is the whole distinction from an override.",
                      configured, value( LIBRARY_SET_KEY ) );
    }

    public void testOverridesBeatBackstops()
    {
        C3P0Config.refreshMainConfig( props( UNSET_KEY, "fromOverride" ), "ov",
                                      props( UNSET_KEY, "fromBackstop" ), "bs" );

        assertEquals( "Where both layers set a key, the override wins.",
                      "fromOverride", value( UNSET_KEY ) );
    }

    /** A backstop still applies to keys the overrides do not mention. */
    public void testBackstopsAndOverridesComposeRatherThanReplace()
    {
        String otherKey = UNSET_KEY + "2";
        C3P0Config.refreshMainConfig( props( UNSET_KEY, "fromOverride" ), "ov",
                                      props( otherKey, "fromBackstop" ), "bs" );

        assertEquals( "fromOverride", value( UNSET_KEY ) );
        assertEquals( "Supplying overrides must not suppress the backstops.",
                      "fromBackstop", value( otherKey ) );
    }

    /**
     *  See the class comment: this is what makes overrides-beat-backstops a consequence of
     *  position rather than of a tiebreak MConfig.combine declines to promise.
     */
    public void testOverridesAndBackstopsOccupyDistinctResourcePaths()
    {
        C3P0Config.refreshMainConfig( props( UNSET_KEY, "fromOverride" ), "ov",
                                      props( UNSET_KEY, "fromBackstop" ), "bs" );

        String[] paths = C3P0Config.getMultiPropertiesConfig().getPropertiesResourcePaths();
        String   found = Arrays.toString( paths );

        assertTrue( "Programmatic overrides should occupy their own resource path: " + found,
                    Arrays.asList( paths ).contains( OVERRIDES_PATH ) );
        assertTrue( "Programmatic backstops should occupy their own resource path: " + found,
                    Arrays.asList( paths ).contains( BACKSTOPS_PATH ) );

        // and in the right order: backstops below the library config, overrides above it
        int iBackstops = Arrays.asList( paths ).indexOf( BACKSTOPS_PATH );
        int iOverrides = Arrays.asList( paths ).indexOf( OVERRIDES_PATH );
        assertTrue( "Backstops must sit at lower precedence than overrides: " + found,
                    iBackstops < iOverrides );
    }
}
