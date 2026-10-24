package com.mchange.v2.c3p0.test.freeze;

import com.mchange.v2.c3p0.test.junit.ChildJvmProbeRunner;

import junit.framework.TestCase;

import java.util.Map;

/**
 *  <code>com.mchange.v2.c3p0.freezeConfig</code>: a deployment that sets it pins c3p0's
 *  configuration, and every later refresh is refused.
 *
 *  <p>Why that is worth a control at all: c3p0's configuration decides where connections are
 *  made, as whom, with what credentials, and what is logged about any of it. Any code in the JVM
 *  can call <code>C3P0Config.refreshMainConfig(..)</code> with overrides of its choosing -- it is
 *  a public static with no permission check -- so a deployment that has settled its pool
 *  configuration and wants it to stay settled has, until now, had no way to say so. This is that
 *  way, and it is deliberately coarse: refuse everything, loudly.</p>
 *
 *  <p>Each case forks a child JVM, because the flag is read from the sealed System properties
 *  snapshot that C3P0Config's class initializer takes, and because the first-refusal stack-trace
 *  latch is a static that never resets. Both are one-shot per process. See
 *  {@link ConfigFreezeProbe}.</p>
 *
 *  <h3>One case that is deliberately absent, and the mutation that survives because of it</h3>
 *
 *  <p>MLogConfig's equivalent suite pins the arbitration between the sealed System properties
 *  snapshot and the configuration in force: where they disagree, frozen wins. That is what the
 *  <code>strongest</code> argument to SealedSystemPropertiesBooleanProperty decides. There is no
 *  such case here, because in c3p0 the disagreement is not reachable.</p>
 *
 *  <p>C3P0Config seals System properties during its own class initialization, in the same breath
 *  as it loads configuration, and the snapshot sits at the TOP of the combined configuration. So
 *  for any key the snapshot carries, the configuration in force reads back the same value --
 *  there is no window, as there is in MLogConfig, in which config is loaded before the seal is
 *  taken. The only source that can outrank the snapshot is a refresh override, and a snapshot
 *  reading <code>true</code> has already refused the refresh that would carry it.</p>
 *
 *  <p>The consequence, confirmed by mutation: flipping <code>strongest</code> to false in
 *  C3P0Config breaks none of these tests. That is an equivalent mutant rather than a gap -- with
 *  the two sources unable to conflict, every reachable input reaches the "at least one is set,
 *  and any set are to weakest" branch, which returns the set value either way. The declaration is
 *  still the right one, and the arbitration it selects is covered where it is reachable, in
 *  mchange-commons-java's MLogConfigFreezeJUnitTestCase. Please do not "fix" the survivor by
 *  weakening it here.</p>
 */
public final class ConfigFreezeJUnitTestCase extends TestCase
{
    private final static String FREEZE_ON  = "-D" + ConfigFreezeProbe.FREEZE_KEY + "=true";
    private final static String PUSH_TO_LOG_ON = "-D" + ConfigFreezeProbe.OVERRIDE_REFRESH_LOG_CONFIG + "=true";

    private Map<String,String> probe( String query, String... jvmArgs ) throws Exception
    { return ChildJvmProbeRunner.run( ConfigFreezeProbe.class, query, jvmArgs ); }

    // ------------------------------------------------------------- refusing

    /**
     *  THE FEATURE. Frozen, a refresh carrying overrides is refused: nothing the overrides name
     *  reaches the configuration, at the raw property level or at the bean level, and c3p0 says
     *  out loud that it ignored the request.
     */
    public void testAFrozenConfigRefusesARefresh() throws Exception
    {
        Map<String,String> a = probe( "refreshRefused", FREEZE_ON );

        assertEquals( "precondition: the probe key starts unset", "null", a.get( "probeBefore" ) );
        assertEquals( "a frozen config must not take the override", "null", a.get( "probeAfter" ) );
        assertEquals( "nor at the bean level", a.get( "poolSizeBefore" ), a.get( "poolSizeAfter" ) );
        assertEquals( "and the refusal must be reported at WARNING", "1", a.get( "refusals" ) );
    }

    /**
     *  The complement, and worth as much. Without the flag the very same refresh must land. A
     *  freeze that refused nothing, or an override that never arrived in the first place, would
     *  satisfy the case above for entirely the wrong reason.
     */
    public void testAnUnfrozenConfigAcceptsTheSameRefresh() throws Exception
    {
        Map<String,String> a = probe( "refreshAccepted" );

        assertEquals( "precondition: the probe key starts unset", "null", a.get( "probeBefore" ) );
        assertEquals( "from-override", a.get( "probeAfter" ) );
        assertEquals( "and at the bean level too", "37", a.get( "poolSizeAfter" ) );
        assertFalse( "the default we displaced must not have been 37 already",
                     "37".equals( a.get( "poolSizeBefore" ) ) );
        assertEquals( "nothing should have been refused", "0", a.get( "refusals" ) );
    }

    /**
     *  A deployment that keeps calling refresh keeps being told it is being ignored. The warning
     *  is how an operator discovers their refresh is not taking effect, so suppressing it after
     *  the first would hide exactly the thing it exists to report. The stack trace comes once,
     *  because it identifies the caller and the next caller is usually the same one.
     */
    public void testEveryRefusalIsReportedButOnlyTheFirstCarriesAStackTrace() throws Exception
    {
        Map<String,String> a = probe( "repeatedRefusals", FREEZE_ON );

        assertEquals( "all three refusals must be reported", "3", a.get( "refusals" ) );
        assertEquals( "and exactly one should say who asked", "1", a.get( "withStackTrace" ) );
    }

    // ------------------------------------------------------------- not refusing the bootstrap

    /**
     *  A freeze must never prevent c3p0 configuring itself in the first place. The first load is
     *  C3P0Config's class initializer, which does not route through refreshMainConfig(..), so
     *  there is nothing there to refuse -- by design, since a flag that left c3p0 running on
     *  hardcoded defaults alone would be a worse outcome than the one it guards against.
     */
    public void testAFrozenConfigStillLoadsInTheFirstPlace() throws Exception
    {
        Map<String,String> a = probe( "startupStillLoads",
                                      FREEZE_ON,
                                      "-D" + ConfigFreezeProbe.PROBE_KEY + "=from-system",
                                      "-D" + ConfigFreezeProbe.POOL_SIZE_KEY + "=23" );

        assertEquals( "the startup load must not be refused", "from-system", a.get( "probeValue" ) );
        assertEquals( "including the bean properties it configures", "23", a.get( "poolSize" ) );
        assertEquals( "and nothing should have been refused on the way up", "0", a.get( "refusals" ) );
    }

    // ------------------------------------------------------------- which config decides

    /**
     *  THE GUARANTEE THAT MAKES freezeConfig A CONTROL RATHER THAN A SUGGESTION: the decision is
     *  made under the configuration IN FORCE, never the one being proposed. Were it otherwise, a
     *  refresh carrying <code>freezeConfig=false</code> would thaw itself and the whole thing
     *  would be decorative.
     *
     *  <p>The corollary is the mildly surprising half, and it is what makes this reachable at
     *  all: a refresh whose own overrides establish the freeze is NOT refused, because at the
     *  moment of the decision those overrides are not yet configuration. The freeze binds from
     *  the next refresh onward.</p>
     */
    public void testAFreezeEstablishedByOverridesCannotBeThawedByThem() throws Exception
    {
        Map<String,String> a = probe( "freezeFromOverrides" );

        assertEquals( "the refresh that establishes the freeze is itself honored",
                      "first", a.get( "probeAfterEstablishing" ) );
        assertEquals( "and is not refused", "0", a.get( "refusalsAfterEstablishing" ) );

        assertEquals( "but a refresh asking to thaw is refused, so the first value stands",
                      "first", a.get( "probeAfterThawAttempt" ) );
        assertEquals( "and that refusal is reported", "1", a.get( "refusalsAfterThawAttempt" ) );
    }

    // ------------------------------------------------------------- what else it closes

    /**
     *  The composition worth pinning. With <code>overrideRefreshLogConfig</code> set, the last
     *  thing a c3p0 refresh does is push c3p0's whole configuration into MLog's -- and logging
     *  configuration decides what a running system records about what is happening to it, which
     *  makes it the more interesting of the two to be able to replace. Freezing c3p0's
     *  configuration closes that route as well: one flag, not two.
     */
    public void testAFrozenConfigCanNoLongerPushConfigIntoMLog() throws Exception
    {
        Map<String,String> a = probe( "logConfigPropagation", FREEZE_ON, PUSH_TO_LOG_ON );

        assertEquals( "precondition: MLog had not seen the key", "null", a.get( "reachedMLogBefore" ) );
        assertEquals( "the refresh itself is refused", "1", a.get( "refusals" ) );
        assertEquals( "so nothing lands in c3p0's config", "null", a.get( "probeAfter" ) );
        assertEquals( "and nothing is pushed into MLog's", "null", a.get( "reachedMLogAfter" ) );
    }

    /**
     *  And the complement, without which the case above proves only that the push is broken: the
     *  same refresh on an unfrozen config really does reach MLog's configuration.
     */
    public void testAnUnfrozenConfigStillPushesConfigIntoMLog() throws Exception
    {
        Map<String,String> a = probe( "logConfigPropagation", PUSH_TO_LOG_ON );

        assertEquals( "precondition: MLog had not seen the key", "null", a.get( "reachedMLogBefore" ) );
        assertEquals( "nothing refused", "0", a.get( "refusals" ) );
        assertEquals( "from-override", a.get( "probeAfter" ) );
        assertEquals( "and it reaches MLog's configuration", "from-override", a.get( "reachedMLogAfter" ) );
    }

    // ------------------------------------------------------------- and quietly

    /**
     *  Why AbstractBooleanProperty gained a <code>warnUnconfiguredWeakDefault</code> flag.
     *  Unfrozen is the settled default, not a transitional one, so the "has not been set ... we
     *  default to the less secure value ... THIS MAY CHANGE IN A FUTURE RELEASE" notice would ask
     *  every deployment, on every refresh, to act on something that is not going to change. Said
     *  that often, it teaches readers to skim past the notices that do mean something.
     *
     *  <p>The second assertion is the one that keeps this honest: there SHOULD be such a notice
     *  in this run, about byNameInstantiation.enforceWhitelist, which is genuinely transitional.
     *  Without it, silence about freezeConfig would be indistinguishable from silence about
     *  everything.</p>
     */
    public void testAnUnsetFreezeFlagIsNotNaggedAbout() throws Exception
    {
        Map<String,String> a = probe( "unsetFlagIsQuiet" );

        assertEquals( "an unset freeze flag must say nothing", "0", a.get( "freezeNags" ) );
        assertFalse( "but the notice itself must still be reaching the log",
                     "0".equals( a.get( "anyNags" ) ) );
        assertEquals( "and nothing should have been refused", "0", a.get( "refusals" ) );
    }
}
