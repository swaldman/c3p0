package com.mchange.v2.c3p0.test.xmlcfg;

import com.mchange.v2.c3p0.test.junit.ChildJvmProbeRunner;

import junit.framework.TestCase;

import java.util.Map;

/**
 *  c3p0-config.xml never reaches the MultiPropertiesConfig, so every control read through one is
 *  silently ignored when it is set there.
 *
 *  <h3>The claim</h3>
 *
 *  <p>c3p0 holds its configuration in two places and treats them as two views of one body of
 *  data: <code>C3P0Config.getMultiPropertiesConfig()</code>, which is what gets handed to every
 *  <code>PropertiesConfig</code>-taking accessor in c3p0 and in mchange-commons-java; and
 *  <code>C3P0Config</code>'s own <code>defaultConfig</code> scope, reached through
 *  <code>getUnspecifiedUserProperty(..)</code>, which is the same data plus named scopes and
 *  per-user overrides. Divergences between them have been found and closed before.</p>
 *
 *  <p>One is left. <code>DefaultC3P0ConfigFinder.findConfig()</code> parses c3p0-config.xml and
 *  folds what it finds into the <code>C3P0Config</code> it returns. Nothing folds it into the
 *  MultiPropertiesConfig -- which was built by <code>findLibraryMultiPropertiesConfig()</code>
 *  BEFORE the finder ran, from hardcoded defaults, properties resources and System properties
 *  only, and is never revisited. So XML is the one configuration source the two views do not
 *  share.</p>
 *
 *  <h3>What it costs</h3>
 *
 *  <p>Every reader of a <code>com.mchange.v2.*</code> key takes the MultiPropertiesConfig as its
 *  PropertiesConfig: the by-name instantiation whitelist and its enforcement flag, the four
 *  JNDI/serialization hardening flags, the JNDI name guard, the management coordinator, the VMID,
 *  the connection-tester settings, c3p0's own config finder, overrideRefreshLogConfig, and the
 *  freeze added today. A deployment that sets any of those in c3p0-config.xml -- which is where
 *  many deployments keep all of their c3p0 configuration -- gets nothing. The only diagnostic is
 *  "Unknown c3p0-config property: ...", which reads like a misspelling rather than like a
 *  security control being dropped on the floor.</p>
 *
 *  <p>And it runs both ways. A control the XML tried to tighten stays loose; a capability the XML
 *  tried to enable stays disabled, so an operator concludes the switch does nothing.</p>
 *
 *  <h3>Reading this suite</h3>
 *
 *  <p>Each case says in its own comment whether it is a bug being demonstrated, a regression
 *  guard over something already fixed, or a constraint that held all along and must keep holding.
 *  Two still fail: {@link #testAnExtensionsKeyIsNotPrefixCorrected}, and the user-override half of
 *  {@link #testReconciliationIsQuietAboutSupportedKeysAndNotAboutTypos}.</p>
 *
 *  <p>No failing assertion here asks for behaviour that does not already exist somewhere else in
 *  c3p0 -- each was checked against the same key arriving by a route that works.</p>
 *
 *  <p>Each case forks a JVM, because which XML file c3p0 reads is settled once, from the sealed
 *  System properties snapshot taken during C3P0Config's class initialization. See
 *  {@link XmlConfigReachProbe}.</p>
 */
public final class XmlConfigReachJUnitTestCase extends TestCase
{
    private Map<String,String> probe( String query, String... jvmArgs ) throws Exception
    { return ChildJvmProbeRunner.run( XmlConfigReachProbe.class, query, jvmArgs ); }

    // ------------------------------------------------------------- the divergence

    /**
     *  FAILS TODAY. The bug in its most reduced form: three keys of different shapes, declared in
     *  the XML's <code>default-config</code>, read back through both views. Whatever else is true,
     *  two views of one body of configuration must not disagree about what is in it.
     */
    /**
     *  A key declared in XML lands in the one view <b>at its own name</b>, and a fully-qualified
     *  one is not reachable as a pool property.
     *
     *  <p>This case used to be about two views of configuration agreeing. There is one view now,
     *  so what is left to pin is the shape of it: {@code com.mchange.*} keys mean themselves, and
     *  {@code getUnspecifiedUserProperty} -- which prefixes what it is given -- cannot see them.
     *  That asymmetry is the design, not a gap: a pool property is per-DataSource and a
     *  {@code com.mchange.*} setting is read statically, with no DataSource in hand.</p>
     */
    public void testXmlDeclaredKeysLandAtTheirOwnNamesInTheOneView() throws Exception
    {
        Map<String,String> a = probe( "twoViews" );

        assertEquals( "precondition: the XML was read at all", "47", a.get( "poolViaDefaultConfig" ) );
        assertEquals( "a pool property is also visible under its c3p0. spelling",
                      "47", a.get( "poolViaMpconfigPrefixed" ) );

        assertEquals( "a security control declared in XML must be visible to every reader that consults the config",
                      "true", a.get( "securityViaMpconfig" ) );
        assertEquals( "and so must an ordinary non-pool key", "17", a.get( "legacyViaMpconfig" ) );

        assertEquals( "but a fully-qualified key is NOT a pool property, and must not be reachable as one",
                      "null", a.get( "securityViaDefaultConfig" ) );
        assertEquals( "nor this one", "null", a.get( "legacyViaDefaultConfig" ) );
    }

    /**
     *  FAILS TODAY, and is the half of the divergence that is not about controls at all. Pool
     *  properties set in XML are invisible to <code>allCurrentProperties()</code> and to
     *  everything else that reads the MultiPropertiesConfig, so c3p0's own account of its
     *  configuration omits the source an XML-configured deployment actually uses.
     *
     *  <p>Under the prefixed spelling, which is where the MultiPropertiesConfig keeps pool
     *  properties. The two views spelling these differently is deliberate and is not the bug.</p>
     */
    public void testAPoolPropertySetInXmlIsVisibleInTheMultiPropertiesConfig() throws Exception
    {
        Map<String,String> a = probe( "twoViews" );

        assertEquals( "precondition: the XML was read at all", "47", a.get( "poolViaDefaultConfig" ) );
        assertEquals( "a pool property from XML belongs in the MultiPropertiesConfig under its prefixed name",
                      "47", a.get( "poolViaMpconfigPrefixed" ) );
    }

    /**
     *  PASSES TODAY AND MUST KEEP PASSING. The bare name must stay out of the MultiPropertiesConfig.
     *  There, <code>maxPoolSize</code> is an ordinary unprefixed key in the same namespace as
     *  <code>com.mchange.v2.*</code> and anything else a deployment puts in c3p0.properties;
     *  promoting pool properties into it unprefixed would put c3p0's own property names into a
     *  namespace it does not own.
     */
    public void testAPoolPropertyDoesNotLeakIntoTheMultiPropertiesConfigUnprefixed() throws Exception
    {
        Map<String,String> a = probe( "twoViews" );

        assertEquals( "null", a.get( "poolViaMpconfig" ) );
    }

    // ------------------------------------------------------------- what the divergence costs

    /**
     *  FAILS TODAY. The consequence that matters most: a deployment that turns the by-name
     *  instantiation whitelist ON in its c3p0-config.xml is not enforcing it.
     *
     *  <p>The behavioural assertion is the one to keep. A fix that made the flag read true
     *  without the gate closing would be worse than the bug, because it would also silence the
     *  warning that currently tells an operator enforcement is off.</p>
     */
    public void testAWhitelistEnforcedByXmlIsActuallyEnforced() throws Exception
    {
        Map<String,String> a = probe( "whitelistEnforcement" );

        assertEquals( "the flag must read as set", "true", a.get( "enforcing" ) );
        assertEquals( "and the gate must actually refuse a class nothing whitelisted",
                      "true", a.get( "gateRefusedUnwhitelisted" ) );
    }

    /**
     *  FAILS TODAY. A second control, in a different subsystem, through a different accessor, so
     *  that special-casing one key could not satisfy both -- and in the opposite direction, to
     *  make the point that ignored configuration is a defect either way round. Here the XML asks
     *  c3p0 to permit something it refuses by default, and c3p0 goes on refusing while reporting
     *  nothing.
     */
    public void testAJndiHardeningFlagRelaxedByXmlIsActuallyRelaxed() throws Exception
    {
        Map<String,String> a = probe( "namingControl" );

        assertEquals( "true", a.get( "accepting" ) );
    }

    /**
     *  FAILS TODAY, and is the sharpest statement of what separates this family of keys from the
     *  pool properties: the SPELLING. A fully-qualified key is already in the properties-style
     *  view's own namespace. It belongs there verbatim, and every consumer looks it up verbatim --
     *  DefaultConnectionTester reads this one straight off the MultiPropertiesConfig, and is now
     *  its only reader, C3P0Defaults' legacy default for connectionIsValidTimeout having been
     *  withdrawn.
     *
     *  <p>Under a <code>c3p0.</code> prefix such a key is present and unreachable, which is
     *  indistinguishable from absent for every consumer, while looking in a config dump exactly
     *  like it worked. So this asserts both spellings.</p>
     *
     *  <p>Not a security control, so a fix for this is not mistaken for a security patch.</p>
     */
    public void testAFullyQualifiedKeyFromXmlLandsUnderItsOwnNameNotUnderAPrefix() throws Exception
    {
        Map<String,String> a = probe( "legacyTimeout" );

        assertEquals( "the key every consumer actually looks up", "17", a.get( "bare" ) );
        assertEquals( "and not a prefixed spelling nothing reads", "null", a.get( "prefixed" ) );
    }

    /**
     *  FAILS TODAY. The freeze added today, which is the control whose silence is hardest to
     *  notice: when it works, the evidence is a warning; when it is ignored, the evidence is the
     *  absence of a warning that would not have appeared anyway. A deployment that pins its
     *  configuration in c3p0-config.xml is not pinned.
     */
    public void testAFreezeDeclaredInXmlActuallyFreezes() throws Exception
    {
        Map<String,String> a = probe( "freezeFromXml" );

        assertEquals( "the refresh must be refused", "1", a.get( "refusals" ) );
        assertFalse( "so its override must not have landed", "37".equals( a.get( "poolPropAfterRefresh" ) ) );
    }

    // ------------------------------------------------------------- constraints on the fix

    /**
     *  PASSES TODAY AND MUST KEEP PASSING. Pool properties and named scopes are what
     *  c3p0-config.xml is chiefly for. Whatever folds XML into the MultiPropertiesConfig must
     *  leave that path alone.
     */
    public void testTheExistingXmlPathStillWorks() throws Exception
    {
        Map<String,String> a = probe( "poolProperties" );

        assertEquals( "47", a.get( "defaultScope" ) );
        assertEquals( "11", a.get( "namedScope" ) );
    }

    /**
     *  THE CONSTRAINT A FIX MUST NOT BREAK, and the reason this is not simply "fold the XML in on
     *  top". c3p0's settled policy is earliest-or-narrowest: a System property was captured before
     *  application code ran and is taken as the operator's word, and where two sources disagree
     *  the safer reading wins. A file on the classpath must not acquire a way to loosen what an
     *  operator set on the command line.
     *
     *  <p>Both halves, because the policy is narrowest-wins and not a precedence ladder. The first
     *  passes today, for the wrong reason -- the XML is ignored entirely. The second fails today,
     *  and is the same bug as the rest: the XML asks for the NARROWER setting and is still
     *  ignored.</p>
     */
    /**
     *  A sealed System property outranks XML <b>in both directions</b>. CHANGED in 0.15.0.
     *
     *  <p>0.14.x honored an XML file that asked to <i>narrow</i> a security control the operator
     *  had left loose: the XML-derived value sat in one representation and the System property in
     *  another, and {@code SealedSystemPropertiesBooleanProperty}'s {@code strongest} arbitration
     *  picked the safer of the two. With one view, precedence resolves before arbitration sees
     *  anything, so the System property simply wins.</p>
     *
     *  <p>That is the invariant we want, and the simpler one to state: a sealed System property
     *  wins, full stop. {@code -D...enforceWhitelist=true} brooks no exceptions, which was always
     *  the intent -- arranging it under the old layering would have meant re-layering System
     *  properties over whatever a C3P0ConfigFinder returned, and there was then no sealed variant
     *  to re-layer. What changes is the loosening direction: an operator who sets a control to
     *  false now gets false, where before an XML file could raise it.</p>
     */
    public void testASealedSystemPropertyOutranksXmlInBothDirections() throws Exception
    {
        Map<String,String> a = probe( "systemPropertyNarrowsOverXml",
                                      "-D" + XmlConfigReachProbe.ACCEPT_DESER_KEY + "=false",
                                      "-D" + XmlConfigReachProbe.ENFORCE_KEY + "=false" );

        assertEquals( "the literal key spellings in the probe must still match their constants",
                      "true", a.get( "enforceKeySpellingMatches" ) );
        assertEquals( "the literal key spellings in the probe must still match their constants",
                      "true", a.get( "acceptDeserKeySpellingMatches" ) );

        assertEquals( "an operator's refusal must stand against an XML file asking to relax it",
                      "false", a.get( "accepting" ) );
        assertEquals( "and must stand equally against one asking to tighten it, which 0.14.x honored",
                      "false", a.get( "enforcing" ) );
    }

    // ------------------------------------------------------------- the same divergence, by spelling

    /**
     *  REGRESSION GUARD for the second divergence this work turned up, and the one that was
     *  briefly created by the first fix rather than found in old code.
     *
     *  <p>Nothing documents <code>&lt;property name="c3p0.maxPoolSize"&gt;</code> inside XML --
     *  the prefix belongs to the properties-style view, and the enclosing element already says
     *  which scope you are in. But people write it, having typed exactly that in c3p0.properties
     *  the day before. For a while it resolved in the properties view and not in the bean view,
     *  so <code>c3p0.minPoolSize</code> read back as 4 while the pool ran with 3: the original
     *  bug with its sign flipped, and this time the properties view was the one lying.</p>
     *
     *  <p>Fixed where it belongs, in XML property extraction, so one correction serves both
     *  views. Asserted in all three scopes that hold pool properties, because each reaches the
     *  properties view by a different path.</p>
     */
    public void testAC3p0PrefixedSpellingInXmlResolvesTheSameInBothViews() throws Exception
    {
        Map<String,String> a = probe( "prefixedSpellingInEveryScope" );

        assertEquals( "default scope, properties view", "4", a.get( "defaultViaMpconfig" ) );
        assertEquals( "default scope, bean view -- the one the pool actually reads",
                      "4", a.get( "defaultViaBean" ) );

        assertEquals( "named scope, properties view", "7", a.get( "namedViaMpconfig" ) );
        assertEquals( "named scope, bean view", "7", a.get( "namedViaBean" ) );

        assertEquals( "user override, properties view", "20", a.get( "userOverrideViaMpconfig" ) );
        assertEquals( "and the prefix is corrected, not concatenated",
                      "null", a.get( "userOverrideDoubledPrefix" ) );
    }

    /**
     *  REGRESSION GUARD. An unrecognized bare name is a misspelled pool property, and it belongs
     *  in c3p0's own namespace even though nothing will read it. Promoted bare, a typo sits in the
     *  top-level namespace beside the <code>com.mchange.*</code> keys, where a later reader of a
     *  config dump cannot tell a c3p0 property from a library one.
     */
    public void testAMisspelledPoolPropertyStaysInC3p0sNamespace() throws Exception
    {
        Map<String,String> a = probe( "misspelledKeyNamespacing" );

        assertEquals( "12", a.get( "prefixed" ) );
        assertEquals( "and not loose in the top-level namespace", "null", a.get( "bare" ) );
    }

    /**
     *  FAILS TODAY. The prefix correction reaches one place it should not.
     *
     *  <p>An extensions key is a name the deployment invented for its own code to read back
     *  through <code>getExtensions(..)</code>. c3p0 assigns it no meaning and keeps no vocabulary
     *  of valid ones -- which is exactly why the reconciler passes extensions through untouched
     *  and warns about nothing in them. But the correction lives in
     *  <code>extractPropertiesFromLevel(..)</code>, which <code>extractExtensionsFromLevel(..)</code>
     *  also calls, so <code>c3p0.initSql</code> is silently rewritten to <code>initSql</code> and
     *  the deployment's own lookup of its own key returns null.
     *
     *  <p>It warns while doing it, which makes this findable, but the warning says the name "was
     *  corrected" -- and here there was nothing to correct.</p>
     */
    public void testAnExtensionsKeyIsNotPrefixCorrected() throws Exception
    {
        Map<String,String> a = probe( "extensionsKeysArePreserved" );

        assertEquals( "an invented extensions name must survive verbatim",
                      "SET SCHEMA 'prefixed'", a.get( "prefixedKey" ) );
        assertEquals( "and must not have been moved to a different key",
                      "null", a.get( "correctedAwayTo" ) );
        assertEquals( "precondition: ordinary extensions keys work", "plainValue", a.get( "plainKey" ) );
    }

    /**
     *  REGRESSION GUARD over a guarantee this work established without being asked to, and which
     *  nothing else here covers: the two views agree across a REFRESH, not just across a first
     *  load.
     *
     *  <p>A programmatic refresh override loses to c3p0-config.xml. That is deliberate and
     *  long-standing -- the finder layers the XML over everything else and announces it, "overrides
     *  all other c3p0 config" -- and it is not what this asserts. The point is that the override
     *  does enter the properties view on its way in, so without reconciliation on the way out that
     *  view would be left reporting 37 while the pool ran with 30. It used to. Now both say 30.</p>
     */
    /**
     *  A programmatic refresh override now <b>beats</b> c3p0-config.xml. CHANGED in 0.15.0.
     *
     *  <p>0.14.x put XML on top of everything -- {@code insertDefaultsUnderNascentConfig}, and
     *  {@code mbOverrideWarning} promising it "overrides all other c3p0 config". Read as an
     *  ordinary properties layer, XML now sits where its position in the source list puts it:
     *  below programmatic overrides, and below System properties. Both readings must agree about
     *  that, which is the part this case still has to assert.</p>
     */
    public void testARefreshOverrideNowOutranksXml() throws Exception
    {
        Map<String,String> a = probe( "refreshOverrideShadowedByXml" );

        assertEquals( "precondition: the XML value is in force to begin with", "30", a.get( "mpconfigBefore" ) );
        assertEquals( "30", a.get( "beanBefore" ) );

        assertEquals( "the override wins, where in 0.14.x the XML did", "37", a.get( "mpconfigAfter" ) );
        assertEquals( "and the pool-property reading must agree with it", "37", a.get( "beanAfter" ) );
    }

    // ------------------------------------------------------------- and the other library that reads it

    /**
     *  Once fully-qualified keys work in <code>default-config</code>,
     *  <code>com.mchange.v2.log.*</code> is one of the namespaces a deployment can reach that
     *  way. So with <code>syncRefreshLogConfig</code> set, log configuration written in
     *  c3p0-config.xml must arrive in MLog's configuration and not merely in c3p0's -- otherwise
     *  the compound-key support stops at the library boundary, for the one namespace whose whole
     *  purpose is to configure something else.
     *
     *  <p>The contested key is the assertion that earns its keep. c3p0 documents XML as beating
     *  programmatic config, so the forwarded XML layer has to go in ABOVE the forwarded
     *  overrides; forward it below them and every other assertion here still passes. The two
     *  views are checked against each other as well, since the point is that MLog ends up
     *  agreeing with c3p0 rather than merely receiving something.</p>
     *
     *  <p>Two c3p0 pool properties are reported, and they answer differently, which is worth
     *  knowing rather than tidying away. Only ONE layer is forwarded, so a hardcoded default
     *  never travels -- that is the improvement over pushing c3p0's whole resolved config, which
     *  duplicated resource paths in MLog's source list and left
     *  <code>C3P0_HARDCODED_DEFAULTS</code> reported as MLog's weakest source. But a pool
     *  property the XML itself sets DOES travel, because it rides in the same finder-additions
     *  layer as the log keys. MLog ignores it; it shows up in a config dump. Pinned so that if
     *  the forwarded layer is ever narrowed to a namespace, the change is deliberate.</p>
     */
    public void testLogConfigDeclaredInXmlReachesMLog() throws Exception
    {
        Map<String,String> a = probe( "logConfigFromXml",
                                      "-D" + XmlConfigReachProbe.SYNC_REFRESH_LOG_CONFIG + "=true" );

        assertEquals( "precondition: the sync is enabled", "true", a.get( "syncEnabled" ) );

        assertEquals( "a log key only the XML sets must reach MLog", "from-xml", a.get( "mlogXmlOnly" ) );
        assertEquals( "and a programmatic override must still reach it too",
                      "from-override", a.get( "mlogOverrideOnly" ) );

        // CHANGED in 0.15.0: the override wins, in c3p0 and so in MLog. What this case is really
        // for is that the two agree -- a key contested between XML and an override must not
        // resolve one way for c3p0 and the other way for logging.
        assertEquals( "the override beats XML in c3p0, where in 0.14.x the XML won",
                      "override-wins", a.get( "c3p0Contested" ) );
        assertEquals( "and MLog must resolve it the same way c3p0 did",
                      "override-wins", a.get( "mlogContested" ) );

        assertEquals( "a hardcoded c3p0 default must not travel into MLog's configuration",
                      "null", a.get( "mlogHardcodedDefault" ) );
        assertEquals( "though a pool property the XML sets does, since MLog reads the same resources",
                      "47", a.get( "mlogXmlSetPoolProp" ) );
    }

    /**
     *  Two c3p0-xml: sources both contributing should be announced once. RED pending the
     *  C3P0Config change.
     *
     *  <p>Under 0.14.x there was no point having both a classpath {@code /c3p0-config.xml} and a
     *  file named by {@code com.mchange.v2.c3p0.cfg.xml}: only one was ever read. Both are read
     *  now, the named file winning per key. That is a supported configuration and the warning is
     *  meant to be temporary -- it is for the deployment that added the System property and
     *  forgot to remove the resource, which used to get one file and now gets a merge.</p>
     */
    public void testMoreThanOneXmlSourceIsAnnounced() throws Exception
    {
        Map<String,String> a = probe( "xmlSourceCount" );

        assertEquals( "precondition: the test classpath supplies a c3p0-config.xml and the probe " +
                      "supplies a file, so two sources contributed",
                      "2", a.get( "xmlSourcesRead" ) );
        assertEquals( "precondition: the named file still wins per key", "47", a.get( "poolProp" ) );

        assertEquals( "two XML sources should draw exactly one warning", 1,
                      Integer.parseInt( a.get( "warningsNamingMultipleXml" ) ) );
    }


    // ------------------------------------------------------------- what c3p0 says about all this

    /**
     *  A misspelled pool property must be reported, and a supported fully-qualified key must not
     *  be. RED pending the C3P0Config change.
     *
     *  <p>The reconciler that used to say this is gone with the second representation, and the
     *  c3p0-xml: source cannot replace it: living in mchange-commons-java, it has no list of
     *  c3p0's configurable properties to check a name against, so it can only comment on shape.
     *  Only C3P0Config has both the loaded configuration and C3P0Defaults, which is where the
     *  check belongs -- after a load or a refresh.</p>
     *
     *  <p>The misspelling is the diagnostic's whole reason to exist: the operator's setting does
     *  nothing, and only c3p0 is in a position to say so. All three scopes matter, because
     *  silence in one and not another is worse than silence everywhere -- an operator who has
     *  seen the warning fire for a sibling key reads its absence as confirmation.</p>
     */
    public void testUnknownKeysAreReportedAndSupportedOnesAreNot() throws Exception
    {
        Map<String,String> a = probe( "warningsByScope" );

        assertEquals( "a supported fully-qualified key must draw no warning naming it",
                      "0", a.get( "warningsNamingSupportedKey" ) );

        // Both directions, in all three scopes. The first three are the half this case originally
        // lacked, and lacking them is how an adder came to warn about every valid per-user pool
        // property while the suite stayed green: "typos are reported" was asserted, "valid keys are
        // not" was not, and the two are independent in three separate pieces of code.
        assertEquals( "a valid pool property in the default scope must draw no warning",
                      "0", a.get( "warningsNamingDefaultScopeValid" ) );
        assertEquals( "nor one in a user override",
                      "0", a.get( "warningsNamingUserOverrideValid" ) );
        assertEquals( "nor one in a named scope",
                      "0", a.get( "warningsNamingNamedScopeValid" ) );

        assertFalse( "a typo in the default scope must be reported",
                     "0".equals( a.get( "warningsNamingDefaultScopeTypo" ) ) );
        assertFalse( "a typo in a named scope must be reported",
                     "0".equals( a.get( "warningsNamingNamedScopeTypo" ) ) );
        assertFalse( "and a typo in a user override must be reported too",
                     "0".equals( a.get( "warningsNamingUserOverrideTypo" ) ) );
    }

    /**
     *  PASSES TODAY AND MUST KEEP PASSING. A named config is a per-DataSource scope; the
     *  MultiPropertiesConfig is global, and every reader of a <code>com.mchange.v2.*</code> key
     *  reads it statically, with no DataSource in hand. A control named inside a
     *  <code>named-config</code> therefore has no coherent global meaning, and a fix that folded
     *  every scope in would let one named config silently change a control for the whole JVM --
     *  including loosening one.
     *
     *  <p>Whether such a key deserves a warning of its own is a separate question, and not
     *  asserted here.</p>
     */
    public void testANamedConfigCannotSetAGlobalControl() throws Exception
    {
        Map<String,String> a = probe( "namedScopeDoesNotLeak" );

        assertEquals( "null", a.get( "acceptDeserViaMpconfig" ) );
        assertEquals( "null", a.get( "enforceViaMpconfig" ) );
        assertEquals( "false", a.get( "accepting" ) );
        assertEquals( "false", a.get( "enforcing" ) );
    }
}
