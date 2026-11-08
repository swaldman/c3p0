package com.mchange.v2.c3p0.test.cfgwiden;

import java.util.Map;

import junit.framework.TestCase;

import com.mchange.v2.c3p0.test.junit.ChildJvmProbeRunner;

/**
 *  Whether a System property written after c3p0 has read its configuration can widen the by-name
 *  instantiation whitelist, and what stops it.
 *
 *  <p>It can, and the documentation says so. Sealing the System properties snapshot does not
 *  prevent it, because sealing wins by <i>precedence</i> and precedence can only defend a key the
 *  snapshot actually names. A brand-new <code>.whitelist.&lt;something&gt;</code> subkey is named
 *  nowhere in the snapshot, so there is no counterpart for the per-key intersection to narrow it
 *  against; the live value stands alone, and the additive union across subkeys absorbs it. c3p0's
 *  configuration does read live System properties &mdash; the resource path <code>/</code> is in
 *  its compiled-in source list, and is capped by, not replaced by, the sealed snapshot &mdash; so
 *  a refresh picks the new subkey up.</p>
 *
 *  <p>This is written down in code because prose was not enough: the behaviour was argued both
 *  ways from the source before it was measured, and the reading that lost was the one derived from
 *  "the snapshot is immutable", which is true and beside the point. A test says which it is.</p>
 *
 *  <p>It is also the regression test for a change that is invisible from outside. c3p0 once
 *  assembled its configuration through <code>MConfig</code>'s <i>cached</i> read, whose cache keys
 *  on the resource-path array; a refresh over an unchanged path list was a cache hit, so it
 *  re-used a snapshot of <code>/</code> taken at startup and this widening did not occur --
 *  through a plain {@link com.mchange.v2.c3p0.cfg.C3P0Config#refreshMainConfig}. It still occurred
 *  through any refresh whose source list differed, so the protection was an artifact of caching
 *  rather than a property anyone had chosen, and it made c3p0 advertise live System properties
 *  while not always honouring them. Reinstating a cached read would make
 *  {@link #testALateSystemPropertySubkeyWidensTheWhitelistAcrossARefresh} fail, which is the
 *  intended alarm: the semantics would have changed even though no test of configuration
 *  <i>values</i> would notice.</p>
 *
 *  <p>Every case runs under enforcement, since "permitted" is meaningless when the whitelist only
 *  warns, and in its own JVM, because sealing, c3p0's memoized configuration, and writing a System
 *  property are all per-process effects.</p>
 */
public class ConfigWideningJUnitTestCase extends TestCase
{
    private final static String ENFORCE_ON =
        "-Dcom.mchange.v2.reflect.byNameInstantiation.enforceWhitelist=true";

    private final static String FREEZE_CONFIG = "-Dcom.mchange.v2.c3p0.freezeConfig=true";

    /**
     *  An override whitelist, set as a launch-time System property so it is in the sealed
     *  snapshot. Its content is irrelevant except that it does not contain
     *  {@link ConfigWideningProbe#EVIL}.
     */
    private final static String OVERRIDE_WHITELIST =
        "-Dcom.mchange.v2.reflect.byNameInstantiation.overrideWhitelist=com.example.OnlyThis";

    private static Map<String,String> run( String query, String... jvmArgs ) throws Exception
    {
        Map<String,String> answers = ChildJvmProbeRunner.run( ConfigWideningProbe.class, query, jvmArgs );

        assertEquals( "precondition: the probe must have sealed the System properties snapshot by "
                          + "reading configuration, or 'late' means nothing",
                      "true", answers.get( "sealed" ) );
        assertEquals( "precondition: enforcement must be on, or every name is permitted and no "
                          + "assertion about permission means anything",
                      "true", answers.get( "enforcing" ) );
        assertEquals( "precondition: the name must not be whitelisted to begin with",
                      "false", answers.get( "permittedBefore" ) );
        assertEquals( "precondition: the probe must actually have set the System property",
                      "true", answers.get( "liveSysprop" ) );

        return answers;
    }

    private static int size( Map<String,String> answers, String key )
    {
        String raw = answers.get( key );
        assertNotNull( "the probe must report '" + key + "'", raw );
        return Integer.parseInt( raw );
    }

    /**
     *  The documented exposure: write a new whitelist subkey as a System property, force a
     *  configuration refresh, and the whitelist grows.
     */
    public void testALateSystemPropertySubkeyWidensTheWhitelistAcrossARefresh() throws Exception
    {
        Map<String,String> answers = run( "widenByRefresh", ENFORCE_ON );

        assertEquals( "the refresh must have happened", "true", answers.get( "refreshed" ) );
        assertEquals( "a whitelist subkey set as a System property after sealing, picked up by a "
                          + "configuration refresh, widens the effective whitelist. Sealing does not "
                          + "prevent this: it outranks keys the snapshot names, and this subkey "
                          + "appears in no sealed property at all.",
                      "true", answers.get( "permittedAfter" ) );
        assertEquals( "and the whitelist should have grown by exactly the one name added",
                      size( answers, "sizeBefore" ) + 1, size( answers, "sizeAfter" ) );
    }

    /**
     *  The other half of the same fact, and the thing that made the behaviour arguable: setting the
     *  property changes nothing by itself. c3p0 memoizes its configuration, so live System
     *  properties are consulted when the configuration is built, not when it is read.
     */
    public void testALateSystemPropertyAloneChangesNothing() throws Exception
    {
        Map<String,String> answers = run( ConfigWideningProbe.NO_REFRESH, ENFORCE_ON );

        assertEquals( "precondition: this case must not refresh", "skipped", answers.get( "refreshed" ) );
        assertEquals( "writing the System property must not by itself widen anything -- c3p0 reads "
                          + "its memoized configuration, and a refusal already decided stays decided",
                      "false", answers.get( "permittedAfterSetProperty" ) );
        assertEquals( "and still nothing at the end of the case", "false", answers.get( "permittedAfter" ) );
        assertEquals( "with the whitelist the size it started",
                      size( answers, "sizeBefore" ), size( answers, "sizeAfter" ) );
    }

    /**
     *  The mitigation the documentation names. With configuration frozen there is no refresh to
     *  carry the new subkey in, so the window closes.
     */
    public void testFreezeConfigPreventsTheWidening() throws Exception
    {
        Map<String,String> answers = run( "widenByRefresh", ENFORCE_ON, FREEZE_CONFIG );

        assertEquals( "with configuration frozen, a late whitelist subkey must not be permitted "
                          + "even though a refresh was attempted",
                      "false", answers.get( "permittedAfter" ) );
        assertEquals( "and the whitelist must not have grown",
                      size( answers, "sizeBefore" ), size( answers, "sizeAfter" ) );
    }

    /**
     *  The stronger mitigation, and the one that makes the documentation's claim about
     *  <code>.overrideWhitelist</code> worth following. "Nothing in configuration can widen an
     *  override whitelist" has to mean more than that configuration cannot change a key the
     *  override also names: it has to hold against a subkey appearing where none appeared before,
     *  which is the case here.
     */
    public void testASealedOverrideWhitelistPreventsTheWidening() throws Exception
    {
        Map<String,String> answers = run( "widenByRefresh", ENFORCE_ON, OVERRIDE_WHITELIST );

        assertEquals( "precondition: the override must be what governs, or this tests the main "
                          + "whitelist again under another name",
                      "OVERRIDE", answers.get( "sourceAfter" ) );
        assertEquals( "an override whitelist set in sealed System properties must not be widened by "
                          + "a whitelist subkey that arrives later, refresh or no refresh",
                      "false", answers.get( "permittedAfter" ) );
    }
}
