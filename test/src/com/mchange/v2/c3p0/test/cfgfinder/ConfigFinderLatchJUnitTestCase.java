package com.mchange.v2.c3p0.test.cfgfinder;

import junit.framework.TestCase;

import com.mchange.v2.c3p0.test.junit.ChildJvmProbeRunner;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 *  A C3P0ConfigFinder sees c3p0's whole configuration and may return anything it likes in place
 *  of it, so it is the one extension point that can defeat every other precaution -- sealed
 *  System properties, whitelists, precedence. As of 0.15.0 it is therefore settled ONCE, during
 *  C3P0Config's class initialization, and no later refresh can displace it.
 *
 *  <h3>Why these fork a JVM</h3>
 *
 *  <p>What is under test is a latch, and a latch is only observable if you can arrange a second
 *  "first load". C3P0Config settles the question in a private static during its own class
 *  initialization, so within one JVM there is exactly one first load and no way back. Each case
 *  below therefore forks a child JVM running {@link ConfigFinderLatchProbe}, which answers one
 *  question and exits. This is the same reason ConfigPrecedenceHarness forks.</p>
 *
 *  <p>Which finder is installed is not directly observable either, so the probes infer it from
 *  whether a given finder's findConfig() was called.</p>
 *
 *  <h3>One case deliberately absent</h3>
 *
 *  <p>C3P0Config guards its default finder behind
 *  <code>Class.forName("org.w3c.dom.Node")</code>, and latches
 *  MConfigOnlyC3P0ConfigFinder if that fails. That branch is <b>not</b> tested because it cannot
 *  be reached: <code>java.sql requires java.xml transitive</code>, so any program able to use
 *  JDBC at all has XML available, and <code>--limit-modules</code> cannot exclude java.xml while
 *  keeping java.sql. The guard predates modularity and is now pure defence.</p>
 */
public final class ConfigFinderLatchJUnitTestCase extends TestCase
{
    // ------------------------------------------------------------- installation

    /** The supported case: named in configuration before anything has loaded, and honored. */
    public void testAFinderNamedBeforeTheFirstLoadIsInstalled() throws Exception
    {
        Map<String,String> answers = probe( "startupInstalls",
                                            "-D" + ConfigFinderLatchProbe.FINDER_KEY + "=" + ConfigFinderLatchProbe.MARKER );

        assertEquals( "a finder named at startup must actually be consulted",
                      "1", answers.get( "markerCalls" ) );
    }

    /**
     *  THE CASE THE LATCH EXISTS FOR, and the one injection route that reaches the lookup. The
     *  finder key is read from the config returned by getMultiPropertiesConfig(), and a refresh
     *  override is folded into that config -- so before 0.15.0 passing the key through
     *  refreshMainConfig(..) installed a finder on a running application.
     *
     *  <p>The probe reports what the lookup could see, so this cannot pass merely because the
     *  key failed to arrive: the key IS visible, and is refused anyway.</p>
     */
    public void testAFinderNamedOnlyByARefreshOverrideIsRefused() throws Exception
    {
        Map<String,String> answers = probe( "refreshViaOverrides" );

        assertEquals( "precondition: no finder should have run before the refresh",
                      "0", answers.get( "markerCallsBefore" ) );
        assertEquals( "the key must reach the lookup, or this test proves nothing",
                      ConfigFinderLatchProbe.MARKER, answers.get( "configVisibleToTheLookup" ) );
        assertEquals( "a finder named only by a refresh override must NOT be installed",
                      "0", answers.get( "markerCallsAfter" ) );
        assertEquals( "and the refusal must be reported, not silent",
                      "1", answers.get( "refusalWarnings" ) );
    }

    /**
     *  The other route one would expect to work, and it does not -- which is worth pinning
     *  because it is not the latch that closes it. c3p0 reads its resource-backed sources
     *  through a cache, System properties included, so a System property set after the first
     *  load never reaches the configuration at all. No refusal is logged because there is
     *  nothing to refuse.
     *
     *  <p>Recorded so that a future change to that caching does not quietly open a second route
     *  while everyone is watching the first. If this ever starts failing, the latch is what
     *  stands behind it, and the refusal count is the thing to check.</p>
     */
    public void testALateSystemPropertyNeverReachesTheFinderLookup() throws Exception
    {
        Map<String,String> answers = probe( "refreshViaSystemProperty" );

        assertEquals( "a System property set after the first load is not visible to the config",
                      "null", answers.get( "configVisibleToTheLookup" ) );
        assertEquals( "0", answers.get( "markerCallsAfter" ) );
        assertEquals( "nothing was offered, so nothing should be refused",
                      "0", answers.get( "refusalWarnings" ) );
    }

    // ------------------------------------------------------------- the regression

    /**
     *  THE REGRESSION THAT PROMPTED ALL OF THIS, and the subtlest thing here.
     *
     *  <p>warn_on_conflicting_overrides reaches DefaultC3P0ConfigFinder only as a constructor
     *  argument, and it is false on the first load -- C3P0Config's static initializer passes
     *  false -- and true on a refresh carrying overrides. So an implementation that cached the
     *  finder instance alongside the latch would freeze the flag at false for the life of the
     *  JVM, and the warning that tells a deployment "your XML config is overriding everything
     *  else" would simply stop. Silently: no error, just one fewer line in the log.</p>
     *
     *  <p>Both halves are asserted. A probe that warned at startup too would satisfy the second
     *  assertion while proving nothing about refreshes, so the zero matters as much as the
     *  one.</p>
     */
    public void testTheXmlOverridesWarningSurvivesARefresh() throws Exception
    {
        Path xml = Files.createTempFile( "c3p0-config-latchprobe-", ".xml" );
        try
        {
            Files.write( xml, ( "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                                + "<c3p0-config>\n"
                                + "  <default-config>\n"
                                + "    <property name=\"maxPoolSize\">42</property>\n"
                                + "  </default-config>\n"
                                + "</c3p0-config>\n" ).getBytes( "UTF-8" ) );

            Map<String,String> answers = probe( "xmlOverrideWarning",
                                                "-D" + ConfigFinderLatchProbe.XML_CFG_KEY + "=" + xml.toString() );

            assertEquals( "the first load passes warn=false, so startup must be quiet",
                          "0", answers.get( "warnAtStartup" ) );
            assertEquals( "a refresh passes warn=true, so the warning must reappear",
                          "1", answers.get( "warnAfterRefresh" ) );
        }
        finally
        { deleteQuietly( xml ); }
    }

    // ------------------------------------------------------------- failure handling

    /**
     *  A finder that fails is still the installed finder. c3p0 falls back to its
     *  properties-only configuration FOR THAT REFRESH, without replacing the finder, so the
     *  next refresh consults it again -- a transient inability to read configuration must not
     *  permanently downgrade how configuration is read.
     *
     *  <p>Also asserts the configuration remains usable throughout, since the fallback exists so
     *  that a broken finder does not leave c3p0 with no configuration at all.</p>
     */
    public void testAFinderWhoseFindConfigThrowsIsNotReplaced() throws Exception
    {
        Map<String,String> answers = probe( "throwingFinder",
                                            "-D" + ConfigFinderLatchProbe.FINDER_KEY + "=" + ConfigFinderLatchProbe.THROWING );

        assertEquals( "the throwing finder must have been consulted at startup",
                      "1", answers.get( "callsAfterStartup" ) );
        assertEquals( "and again on the refresh, so it was not replaced by the fallback",
                      "2", answers.get( "callsAfterRefresh" ) );

        // 15 is C3P0Defaults.maxPoolSize(), reached through the properties-only fallback.
        assertEquals( "a broken finder must not leave c3p0 unconfigured",
                      "15", answers.get( "configUsableAfterStartup" ) );
        assertEquals( "15", answers.get( "configUsableAfterRefresh" ) );
    }

    /**
     *  c3p0 rebuilds its OWN default finder on every refresh, to pick up the current
     *  warn_on_conflicting_overrides, and the test for "is this ours" is
     *  getClass().equals(..) rather than instanceof. The difference matters only for a
     *  deployment finder that EXTENDS DefaultC3P0ConfigFinder: instanceof would quietly
     *  replace it with a plain instance of c3p0's own, discarding the deployment's finder and
     *  routing around the latch in the process.
     *
     *  <p>Found by mutation -- swapping getClass().equals(..) for instanceof broke nothing,
     *  because nothing here subclassed the default finder.</p>
     */
    public void testADeploymentFinderExtendingOursIsNotReplacedByOurs() throws Exception
    {
        Map<String,String> answers = probe( "subclassingFinder",
                                            "-D" + ConfigFinderLatchProbe.FINDER_KEY + "=" + ConfigFinderLatchProbe.SUBCLASSING );

        assertEquals( "the subclass must have been consulted at startup",
                      "1", answers.get( "callsAfterStartup" ) );
        assertEquals( "and again after a refresh, rather than being swapped for the base class",
                      "2", answers.get( "callsAfterRefresh" ) );
    }

    // ------------------------------------------------------------------ forking

    private static Map<String,String> probe( String query, String... jvmArgs ) throws Exception
    { return ChildJvmProbeRunner.run( ConfigFinderLatchProbe.class, query, jvmArgs ); }

    private static void deleteQuietly( Path p )
    { try { Files.deleteIfExists( p ); } catch ( IOException e ) { /* best effort */ } }
}
