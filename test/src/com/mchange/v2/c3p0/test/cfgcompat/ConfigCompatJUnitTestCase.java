package com.mchange.v2.c3p0.test.cfgcompat;

import junit.framework.TestCase;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 *  Does the reconstructed C3P0Config answer configuration questions the way c3p0-0.14.2 did?
 *
 *  <h3>How this works</h3>
 *
 *  <p>{@link ConfigScenario} generates configuration situations -- what is in c3p0.properties,
 *  what is in c3p0-config.xml, what is on the command line -- and {@link ConfigOutcomeProbe}
 *  asks each one the same several hundred questions through C3P0Config's facade. The answers
 *  c3p0-0.14.2 gave were captured once, into
 *  <code>outcomes-0.14.2.txt.gz</code>, by pointing the same probe at the released jar (see
 *  {@link ConfigOutcomeRunner#main}). This case re-asks against the current build and compares.</p>
 *
 *  <p>The capture is a checked-in file rather than a live comparison on purpose: the comparison
 *  then needs neither the old jars nor a network, and the file only changes when somebody
 *  re-captures it deliberately. One JVM per scenario, because c3p0 settles its configuration
 *  once per process.</p>
 *
 *  <h3>What is allowed to differ, and why</h3>
 *
 *  <p>Every exemption below is a decision, not a convenience, and each one is written to fail
 *  when it stops being true -- an exemption that silently outlives its reason is worse than no
 *  test.</p>
 *
 *  <ol>
 *    <li><b>{@code getUnspecifiedUserProperties} no longer carries an {@code extensions}
 *    entry.</b> 0.14.2 built the default scope from <i>all</i> of C3P0Defaults, and
 *    {@code C3P0Defaults.extensions()} is a Map -- so a Map sat in the properties map under the
 *    key {@code extensions}, as a value no property coercion could ever have used. The current
 *    build populates the layer from
 *    {@code extractHardcodedC3P0DefaultsStringCoercedOnly()} and so omits it.
 *    {@code bindNamedConfigToBean} puts the real extensions map into its union explicitly, so
 *    nothing downstream loses anything.</li>
 *
 *    <li><b>0.14.2 could not load at all</b> for a properties-style named-scope user override.
 *    Those scenarios are asserted <i>against the crash</i> rather than exempted: see
 *    {@link #testScenarios0142CouldNotLoadAtAll}.</li>
 *
 *    <li><b>A file named by {@code com.mchange.v2.c3p0.cfg.xml} no longer replaces a classpath
 *    {@code /c3p0-config.xml}</b> -- the two are merged, the named file winning key by key.
 *    0.14.2 read exactly one XML document and discarded the other. Exempted only for the
 *    property names a scenario declares in its classpath document and <i>not</i> in its external
 *    one ({@link ConfigScenario#keysOnlyInClasspathXml}), which is why those documents are
 *    written with disjoint names: the keys that may differ are nameable in advance rather than
 *    inferred from the diff.</li>
 *
 *    <li><b>A user-override user name containing a dot now resolves</b>, for properties-style
 *    configuration. 0.14.2 split such a key at the FIRST dot, so
 *    {@code c3p0.user-overrides.first.last.maxPoolSize} was user {@code first} carrying a
 *    property {@code last.maxPoolSize} -- which is no property, so the override silently did
 *    nothing. 0.15.0 splits at the LAST dot, giving user {@code first.last} and property
 *    {@code maxPoolSize}. Exempted <i>narrowly</i>, by
 *    {@link #removeDottedUserOverrideResplits}: a missing answer and an extra answer are
 *    cancelled only when they name the same scope and the same value and re-split the same
 *    underlying key, and only when the current build's user name actually contains a dot.
 *    {@link #testADottedUserOverrideNowResolves} pins the improvement itself.
 *
 *    <p>Note what the {@code dotted-uo-xml} scenarios establish, which is the opposite: 0.14.2
 *    read XML user overrides into a bean keyed directly by the {@code user} attribute, never
 *    through a flat properties key, so <b>0.14.2 handled a dotted user correctly in XML</b>.
 *    Unifying 0.15.0 around one properties-shaped representation had therefore <i>regressed</i>
 *    that route, unnoticed, until these scenarios were added. Those two scenarios are not
 *    exempted from anything -- they match 0.14.2 exactly, and
 *    {@link #testTheXmlRouteIsCompatibleAgainForDottedUsers} says so, so a future regression
 *    fails rather than needing an exemption.</p></li>
 *
 *    <li><b>User overrides for one user now merge across scopes property by property</b>, where
 *    0.14.2 replaced a user's whole override set from the named scope. Agreed to be a fix. It is
 *    exempted <i>narrowly</i>: an extra override is tolerated only where its value is exactly
 *    what the default scope held for that user and property, and only where 0.14.2 already had
 *    that user in that scope. Anything else -- a changed value, a missing override, an extra user
 *    -- fails. {@link #testOneUsersOverridesNowMergeAcrossScopes} pins the improvement itself,
 *    so it is asserted rather than merely tolerated.</li>
 *  </ol>
 */
public final class ConfigCompatJUnitTestCase extends TestCase
{
    private final static String BASELINE_RESOURCE = "/com/mchange/v2/c3p0/test/cfgcompat/outcomes-0.14.2.txt.gz";

    private static Map<String,Set<String>> baseline;
    private static ConfigOutcomeRunner     runner;
    private static File                    workDir;

    @Override
    protected void setUp() throws Exception
    {
        if ( baseline != null ) return;

        baseline = loadBaseline();

        List<String> dropped = new ArrayList<String>();
        List<String> cp = ConfigOutcomeRunner.sanitizedCurrentClasspath( dropped );
        assertFalse( "The current classpath carries no configuration resources at all, which means " +
                     "the sanitizer found nothing to drop -- test/resources-local was expected to " +
                     "supply c3p0.properties and c3p0-config.xml. Check SCENARIO_OWNED_RESOURCES.",
                     dropped.isEmpty() );

        workDir = Files.createTempDirectory( "c3p0-cfgcompat-" ).toFile();
        runner  = new ConfigOutcomeRunner( cp, workDir );
    }

    // ------------------------------------------------------------------ the comparisons

    /**
     *  <b>Every</b> scenario 0.14.2 could load: the current build must answer identically, save
     *  the two documented divergences.
     *
     *  <p>This is the load-bearing case. It is the whole claim that reorganizing
     *  com.mchange.v2.c3p0.cfg around a single representation, and reading c3p0-config.xml as
     *  properties rather than through a second representation, did not change what c3p0 reads.
     *  Until XML was wired in it could only cover the 14 XML-free scenarios; it now covers
     *  127.</p>
     *
     *  <p>Note what makes it non-vacuous in the XML direction: 0.14.2's captured answers include
     *  values that only c3p0-config.xml ever supplied, so if XML stopped being read this case
     *  would fail with those values missing rather than pass quietly.</p>
     */
    public void testEveryLoadableScenarioAnswersAs0142() throws Exception
    {
        List<String> failures = new ArrayList<String>();
        int compared = 0, answers = 0, xmlScenarios = 0, mergeExemptions = 0, shadowExemptions = 0;
        int resplitExemptions = 0;

        for ( ConfigScenario scenario : ConfigScenario.all() )
        {
            if ( crashedUnder0142( scenario ) ) continue;

            Set<String> expected = expected( scenario );
            Set<String> got      = new LinkedHashSet<String>( runner.outcomes( scenario ) );

            ++compared;
            answers += expected.size();
            if ( scenario.xmlBody != null ) ++xmlScenarios;

            Set<String> missing = minus( expected, got );
            Set<String> extra   = minus( got, expected );
            removeExtensionsPseudoProperty( missing );
            mergeExemptions  += removeInheritedUserOverrides( extra, expected );
            shadowExemptions += removeClasspathXmlShadowedKeys( missing, extra, scenario );
            resplitExemptions += removeDottedUserOverrideResplits( missing, extra );

            if (! missing.isEmpty() || ! extra.isEmpty() )
                failures.add( describe( scenario, missing, extra ) );
        }

        assertTrue( "Expected most scenarios to be comparable, found " + compared, compared >= 120 );
        assertTrue( "Expected the XML-carrying scenarios to be compared too, found " + xmlScenarios,
                    xmlScenarios >= 100 );
        assertTrue( "Expected tens of thousands of pinned answers, found " + answers, answers > 30000 );
        assertTrue( "Expected the per-user merge to show up somewhere; if it does not, either the " +
                    "improvement has been lost or no scenario exercises it any more.",
                    mergeExemptions > 0 );
        assertTrue( "Expected the classpath-XML-no-longer-discarded change to show up somewhere; if " +
                    "it does not, either an external XML file has gone back to replacing the resource " +
                    "or no scenario sets com.mchange.v2.c3p0.cfg.xml any more.",
                    shadowExemptions > 0 );
        assertTrue( "Expected the dotted-user re-split to show up somewhere; if it does not, either " +
                    "dotted user names have stopped resolving or no scenario exercises one any more.",
                    resplitExemptions > 0 );

        if (! failures.isEmpty() )
            fail( failures.size() + " of " + compared + " scenarios diverged from c3p0-0.14.2:\n\n" +
                  join( failures ) );
    }

    /**
     *  The improvement itself, asserted rather than merely tolerated.
     *
     *  <p>A user named in both the default scope and a named config keeps the default scope's
     *  other properties. 0.14.2 did <code>out.putAll(namedScope.userNamesToOverrides)</code>,
     *  replacing steve's whole map and losing minPoolSize.</p>
     */
    public void testOneUsersOverridesNowMergeAcrossScopes() throws Exception
    {
        int checked = 0;
        for ( ConfigScenario scenario : ConfigScenario.all() )
        {
            if ( scenario.name.indexOf( "uo-both-xml" ) < 0 ) continue;
            ++checked;

            Set<String> got      = new LinkedHashSet<String>( runner.outcomes( scenario ) );
            Set<String> expected = expected( scenario );
            String      scope    = ConfigScenario.NAMED;

            assertTrue( "the named scope's own value must win in " + scenario.name + ": " +
                        grep( got, "uo|" + scope + "|steve" ),
                        has( got, "uo|" + scope + "|steve|maxPoolSize|99" ) );
            assertTrue( "and the default scope's other property must survive, which is the change: " +
                        grep( got, "uo|" + scope + "|steve" ),
                        has( got, "uo|" + scope + "|steve|minPoolSize|5" ) );
            assertFalse( "0.14.2 is expected NOT to have had it -- if it did, this is no longer a " +
                         "divergence and the exemption should go: " + grep( expected, "uo|" + scope + "|steve" ),
                         has( expected, "uo|" + scope + "|steve|minPoolSize|5" ) );
        }
        assertEquals( "every uo-both-xml scenario should be checked", 20, checked );
    }

    /**
     *  The improvement itself: a dotted user name resolves for properties-style configuration.
     *
     *  <p>Asserted rather than merely tolerated, and asserted on both sides -- that the current
     *  build reads the override under the whole user name, and that 0.14.2 did not. If 0.14.2
     *  turns out to have agreed all along, this is no divergence and exemption 5 should go.</p>
     */
    public void testADottedUserOverrideNowResolves() throws Exception
    {
        ConfigScenario scenario = named( "dotted-uo-props" );

        Set<String> got      = new LinkedHashSet<String>( runner.outcomes( scenario ) );
        Set<String> expected = expected( scenario );

        assertTrue( "the whole dotted name should be the user: " + grep( got, "|uo|-|" ),
                    has( got, "uo|-|" + ConfigScenario.DOTTED_USER + "|maxPoolSize|42" ) );
        assertFalse( "0.14.2 is expected NOT to have read it that way -- if it did, this is no " +
                     "divergence and the exemption should go: " + grep( expected, "|uo|-|" ),
                     has( expected, "uo|-|" + ConfigScenario.DOTTED_USER + "|maxPoolSize|42" ) );
        assertTrue( "and 0.14.2's own reading, user 'first' with property 'last.maxPoolSize', is " +
                    "what the exemption cancels: " + grep( expected, "|uo|-|" ),
                    has( expected, "uo|-|first|last.maxPoolSize|42" ) );

        // the undotted control must be identical in both, or the divergence is not about dots
        assertTrue( has( got,      "uo|-|" + ConfigScenario.PLAIN_USER + "|maxPoolSize|7" ) );
        assertTrue( has( expected, "uo|-|" + ConfigScenario.PLAIN_USER + "|maxPoolSize|7" ) );
    }

    /**
     *  The surprise these scenarios turned up, pinned so it cannot come back.
     *
     *  <p>0.14.2 read XML user overrides into a bean keyed by the {@code user} attribute itself,
     *  so it never split a flat key and <b>handled a dotted user correctly</b>. 0.15.0's single
     *  properties-shaped representation routed XML through the same key grammar as everything
     *  else and so inherited the mis-parse -- a regression against 0.14.2, in the one route that
     *  had worked, which no scenario exercised and nothing caught.</p>
     *
     *  <p>These two scenarios need no dotted-user exemption: beyond the extensions
     *  pseudo-property that exemption 1 removes from every scenario alike, they match 0.14.2
     *  exactly. This case says so by
     *  name, so that a future change which re-breaks the XML route fails here rather than being
     *  absorbed as a new divergence.</p>
     */
    public void testTheXmlRouteIsCompatibleAgainForDottedUsers() throws Exception
    {
        int checked = 0;
        for ( String name : new String[] { "dotted-uo-xml", "dotted-uo-xml-named" } )
        {
            ConfigScenario scenario = named( name );
            ++checked;

            Set<String> got      = new LinkedHashSet<String>( runner.outcomes( scenario ) );
            Set<String> expected = expected( scenario );

            assertTrue( "0.14.2 should have read the dotted user correctly from XML; if it did not, " +
                        "the premise of this case is wrong: " + grep( expected, "|uo|-|" ),
                        has( expected, "uo|-|" + ConfigScenario.DOTTED_USER + "|maxPoolSize|42" ) );
            assertTrue( "and so must we: " + grep( got, "|uo|-|" ),
                        has( got, "uo|-|" + ConfigScenario.DOTTED_USER + "|maxPoolSize|42" ) );

            Set<String> missing = minus( expected, got );
            Set<String> extra   = minus( got, expected );
            removeExtensionsPseudoProperty( missing ); // exemption 1, which applies to every scenario

            assertTrue( "beyond the universal extensions pseudo-property, the XML route must agree with " +
                        "0.14.2 exactly for " + name + " -- no dotted-user exemption may be needed here: " +
                        describe( scenario, missing, extra ),
                        missing.isEmpty() && extra.isEmpty() );
        }
        assertEquals( "both XML scenarios should be checked", 2, checked );
    }

    /**
     *  The three scenarios where <b>0.14.2 itself could not start</b>.
     *
     *  <p>A properties-style <code>c3p0.named-configs.X.user-overrides.USER.PROP</code> key sent
     *  0.14.2's <code>findPropStyleNamedScopes</code> into
     *  <code>String.substring(begin &gt; end)</code> -- it computed the third dot's index within a
     *  substring and then indexed the original string with it -- throwing out of C3P0Config's
     *  static initializer, which leaves the whole library unusable in that JVM. Present at least
     *  as far back as v0.11.0, and plausibly never noticed because the manual's own example of
     *  this key was malformed, so nobody wrote the correct form.</p>
     *
     *  <p>So there is nothing to match here. What is asserted instead is that 0.14.2 died, that
     *  the current build does not, and that it actually reads the key -- otherwise "we fixed it"
     *  would rest on the absence of a crash alone.</p>
     */
    public void testScenarios0142CouldNotLoadAtAll() throws Exception
    {
        int checked = 0;
        for ( ConfigScenario scenario : ConfigScenario.all() )
        {
            if (! crashedUnder0142( scenario ) ) continue;
            ++checked;

            Set<String> expected = expected( scenario );
            assertEquals( "0.14.2's record for " + scenario.name + " should be the crash and nothing else: " + expected,
                          1, expected.size() );
            assertTrue( "and it should be an ExceptionInInitializerError: " + expected,
                        expected.iterator().next().contains( "ExceptionInInitializerError" ) );

            Set<String> got = new LinkedHashSet<String>( runner.outcomes( scenario ) );
            for ( String line : got )
                assertFalse( "the current build must not throw for " + scenario.name + ": " + line,
                             line.startsWith( "THREW" ) );
            assertTrue( "the current build should answer fully, got " + got.size() + " answers", got.size() > 100 );

            // and the key 0.14.2 choked on must actually be read
            assertTrue( "the named-scope user override should be visible in " + scenario.name + ": " +
                        grep( got, "uo|" + ConfigScenario.NAMED + "|steve" ),
                        has( got, "uo|" + ConfigScenario.NAMED + "|steve|maxPoolSize|9" ) );
        }
        assertEquals( "all three properties-style named-user-override scenarios should be here", 3, checked );
    }

    /** Scenario names must be unique, or the capture would hold two blocks under one name. */
    public void testScenarioNamesAreUnique()
    {
        Set<String> seen = new HashSet<String>();
        for ( ConfigScenario scenario : ConfigScenario.all() )
            assertTrue( "duplicate scenario name: " + scenario.name, seen.add( scenario.name ) );
        assertEquals( "every scenario should appear in the capture", seen.size(), baseline.size() );
    }

    // ------------------------------------------------------------------ plumbing

    private static Map<String,Set<String>> loadBaseline() throws Exception
    {
        Map<String,Set<String>> out = new LinkedHashMap<String,Set<String>>();
        InputStream is = ConfigCompatJUnitTestCase.class.getResourceAsStream( BASELINE_RESOURCE );
        assertNotNull( "baseline capture not on the classpath at " + BASELINE_RESOURCE, is );
        BufferedReader br = new BufferedReader(
            new InputStreamReader( new java.util.zip.GZIPInputStream( is ), Charset.forName( "UTF-8" ) ) );
        try
        {
            String line;
            while ( ( line = br.readLine() ) != null )
            {
                if ( line.length() == 0 || line.charAt( 0 ) == '#' ) continue;
                int bar = line.indexOf( '|' );
                String scenario = line.substring( 0, bar );
                Set<String> lines = out.get( scenario );
                if ( lines == null ) { lines = new LinkedHashSet<String>(); out.put( scenario, lines ); }
                lines.add( line );
            }
        }
        finally { br.close(); }
        return out;
    }

    private Set<String> expected( ConfigScenario scenario )
    {
        Set<String> out = baseline.get( scenario.name );
        assertNotNull( "no baseline capture for scenario '" + scenario.name + "'. Scenarios and capture " +
                       "must be generated together -- re-capture with ConfigOutcomeRunner.main.", out );
        return out;
    }

    private static boolean crashedUnder0142( ConfigScenario scenario )
    {
        Set<String> lines = baseline.get( scenario.name );
        if ( lines == null ) return false;
        for ( String line : lines )
            if ( line.startsWith( scenario.name + "|THREW" ) ) return true;
        return false;
    }

    /** Exemption 1: see the class comment. */
    private static void removeExtensionsPseudoProperty( Set<String> missing )
    {
        for ( java.util.Iterator<String> ii = missing.iterator(); ii.hasNext(); )
        {
            String[] parts = ii.next().split( "\\|" );
            if ( parts.length == 5 && "props".equals( parts[1] ) && "extensions".equals( parts[3] ) )
                ii.remove();
        }
    }

    /**
     *  Exemption 2: see the class comment. Removes from <code>extra</code> each additional user
     *  override that is exactly an inherited default-scope value 0.14.2 dropped, and returns how
     *  many were removed.
     *
     *  <p>Narrow on purpose. An addition qualifies only if its value equals what the default
     *  scope held for that same user and property, and only if 0.14.2 already listed that user
     *  in that scope. A changed value, an invented property, or a user who should not be in the
     *  scope at all does not qualify and so fails the comparison.</p>
     */
    /**
     *  Exemption 5: see the class comment. Cancels a missing/extra pair that is the same
     *  configuration key split in two different places.
     *
     *  <p>0.14.2 took the first dot after {@code user-overrides.} as the end of the user name;
     *  0.15.0 takes the last dot before the property as its end. So one key yields
     *  {@code first} + {@code last.maxPoolSize} there and {@code first.last} +
     *  {@code maxPoolSize} here. A pair is cancelled only when both sides name the same scope
     *  and the same value and their user and property concatenate to the same thing, and only
     *  when the current build's user name really does contain a dot -- so a changed value, a
     *  moved scope, a lost override or an undotted user all still fail.</p>
     */
    private static int removeDottedUserOverrideResplits( Set<String> missing, Set<String> extra )
    {
        int removed = 0;
        for ( java.util.Iterator<String> ii = extra.iterator(); ii.hasNext(); )
        {
            String[] p = ii.next().split( "\\|" );
            if ( p.length != 6 || ! "uo".equals( p[1] ) ) continue;
            if ( p[3].indexOf( '.' ) < 0 ) continue; // only a dotted user name may be exempted here

            String joined = p[3] + "." + p[4]; // user + '.' + property, as the key spells it
            String twin   = null;
            for ( String candidate : missing )
            {
                String[] q = candidate.split( "\\|" );
                if ( q.length != 6 || ! "uo".equals( q[1] ) ) continue;
                if ( ! p[2].equals( q[2] ) || ! p[5].equals( q[5] ) ) continue; // same scope, same value
                if ( joined.equals( q[3] + "." + q[4] ) ) { twin = candidate; break; }
            }
            if ( twin != null )
            {
                missing.remove( twin );
                ii.remove();
                ++removed;
            }
        }
        return removed;
    }

    private static int removeInheritedUserOverrides( Set<String> extra, Set<String> expected )
    {
        Map<String,String> baselineOverrides = new LinkedHashMap<String,String>();
        Set<String> usersInScope = new HashSet<String>();
        for ( String line : expected )
        {
            String[] p = line.split( "\\|" );
            if ( p.length == 6 && "uo".equals( p[1] ) )
            {
                baselineOverrides.put( p[2] + "|" + p[3] + "|" + p[4], p[5] );
                usersInScope.add( p[2] + "|" + p[3] );
            }
        }

        int removed = 0;
        for ( java.util.Iterator<String> ii = extra.iterator(); ii.hasNext(); )
        {
            String[] p = ii.next().split( "\\|" );
            if ( p.length != 6 || ! "uo".equals( p[1] ) ) continue;

            String scope = p[2], user = p[3], prop = p[4], value = p[5];
            String fromDefaultScope = baselineOverrides.get( "-|" + user + "|" + prop );

            if ( value.equals( fromDefaultScope ) && usersInScope.contains( scope + "|" + user ) )
            { ii.remove(); ++removed; }
        }
        return removed;
    }

    /**
     *  Exemption 3: see the class comment. Drops, from both sides of the comparison, any answer
     *  about a property the scenario declares in its classpath XML and not in its external one.
     *
     *  <p>Both sides, because these are value CHANGES rather than additions: the hardcoded
     *  defaults layer means every pool property already has a value, so 0.14.2 -- having
     *  discarded the classpath document -- reported the default where we report what that
     *  document says. Scoped to the named properties, so a difference about anything else still
     *  fails.</p>
     */
    private static int removeClasspathXmlShadowedKeys( Set<String> missing, Set<String> extra,
                                                       ConfigScenario scenario )
    {
        if ( scenario.keysOnlyInClasspathXml.isEmpty() ) return 0;

        int removed = 0;
        for ( Set<String> side : java.util.Arrays.asList( missing, extra ) )
            for ( java.util.Iterator<String> ii = side.iterator(); ii.hasNext(); )
                if ( scenario.keysOnlyInClasspathXml.contains( propertyNameOf( ii.next() ) ) )
                { ii.remove(); ++removed; }
        return removed;
    }

    /** The property name an answer line is about, or null where it is not about one. */
    private static String propertyNameOf( String line )
    {
        String[] p = line.split( "\\|" );
        if ( p.length == 5 && ("props".equals( p[1] ) || "prop".equals( p[1] )) ) return p[3];
        if ( p.length == 4 && p[1].startsWith( "init" ) ) return p[2];
        return null;
    }

    private static Set<String> minus( Set<String> a, Set<String> b )
    {
        Set<String> out = new LinkedHashSet<String>( a );
        out.removeAll( b );
        return out;
    }

    private static ConfigScenario named( String name )
    {
        for ( ConfigScenario scenario : ConfigScenario.all() )
            if ( name.equals( scenario.name ) ) return scenario;
        fail( "no scenario named '" + name + "'" );
        return null;
    }

    private static boolean has( Set<String> lines, String suffix )
    {
        for ( String line : lines ) if ( line.endsWith( suffix ) ) return true;
        return false;
    }

    private static String grep( Set<String> lines, String fragment )
    {
        List<String> out = new ArrayList<String>();
        for ( String line : lines ) if ( line.contains( fragment ) ) out.add( line );
        return out.toString();
    }

    private static String describe( ConfigScenario scenario, Set<String> missing, Set<String> extra )
    {
        StringBuilder sb = new StringBuilder();
        sb.append( "  " ).append( scenario.name ).append( '\n' );
        sb.append( "    c3p0.properties: " ).append( scenario.propsFile ).append( '\n' );
        sb.append( "    -D             : " ).append( scenario.sysprops ).append( '\n' );
        appendSome( sb, "0.14.2 said, we do not", missing );
        appendSome( sb, "we say, 0.14.2 did not", extra );
        return sb.toString();
    }

    private static void appendSome( StringBuilder sb, String label, Set<String> lines )
    {
        if ( lines.isEmpty() ) return;
        sb.append( "    " ).append( label ).append( " (" ).append( lines.size() ).append( "):\n" );
        int shown = 0;
        for ( String line : new TreeSet<String>( lines ) )
        {
            if ( shown++ == 12 ) { sb.append( "      ... and " ).append( lines.size() - 12 ).append( " more\n" ); break; }
            sb.append( "      " ).append( line ).append( '\n' );
        }
    }

    private static String join( List<String> parts )
    {
        StringBuilder sb = new StringBuilder();
        for ( String part : parts ) sb.append( part ).append( '\n' );
        return sb.toString();
    }
}
