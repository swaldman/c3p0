package com.mchange.v2.c3p0.cfg;

import junit.framework.TestCase;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import com.mchange.v2.cfg.MConfig;
import com.mchange.v2.cfg.MultiPropertiesConfig;
import com.mchange.v2.log.MLog;

/**
 *  Named scopes whose names share a dotted prefix must not read each other's configuration.
 *
 *  <h3>Why this exists</h3>
 *
 *  <p>Allowing a dot in a named-config name (which c3p0-0.14.2 permitted in XML, and 0.15.0
 *  briefly refused) puts scope {@code a.b}'s keys underneath scope {@code a}'s prefix, because
 *  {@code getPropertiesByPrefix} matches whole dot-separated tokens. Read naively, that made a
 *  scope inherit every deeper scope's settings, and made two accessors disagree:
 *  {@code getUnspecifiedUserProperties("a")} reported {@code a.b}'s values while
 *  {@code getUnspecifiedUserProperty("maxPoolSize","a")}, which builds an exact key, reported
 *  the default. One of those is wrong whichever you believe.</p>
 *
 *  <p>Two rules fix it. {@code _namedConfigIsDefined} asks whether any key really belongs to this
 *  scope rather than merely sitting under its prefix, so a name nothing declared stays undefined
 *  even when a deeper scope exists. And the conversion of a scope's keys to base-prefixed
 *  properties keeps only those whose remainder after the scope prefix carries no further dot,
 *  which excludes {@code user-overrides.*}, {@code extensions.*} and any deeper scope's keys
 *  alike.</p>
 *
 *  <h3>Which of those two rules these cases actually hold down</h3>
 *
 *  <p>Only the first. Replacing the definedness walk with the naive emptiness test it was written
 *  to displace &mdash; {@code if (!defined.isEmpty()) return true} &mdash; fails four cases here,
 *  which is the regression this file exists for.</p>
 *
 *  <p>Removing the remainder test from the conversion fails NOTHING, in this file or anywhere in
 *  the suite, and that is a fact about the code rather than a hole here.
 *  {@code convertScopeOwnPropsToBasePrefixProps} has exactly one caller, and its output is then
 *  filtered again by {@code _getUnspecifiedUserProperties}'s own "no compound keys" test, which
 *  discards precisely the dotted keys the remainder test would have kept out. The two guards are
 *  not reachable independently, so no test written against c3p0's public accessors can tell them
 *  apart. The remainder test is still the right thing to write &mdash; it makes the conversion
 *  correct where it stands, rather than correct-by-coincidence at a distance &mdash; but it is
 *  defence in depth, and should not be described as covered.</p>
 *
 *  <h3>What is covered</h3>
 *
 *  <p>The three kinds of thing a scope can hold &mdash; its own pool properties, its extensions,
 *  and its user overrides &mdash; across the default scope, an ordinary named scope, a name that
 *  is <i>shadowed</i> by a deeper one without being declared itself, a name that is shadowed
 *  <i>and</i> declared, and the deeper names doing the shadowing. Nothing here needs a
 *  database.</p>
 */
public class NamedScopeShadowingJUnitTestCase extends TestCase
{
    /*
     *  One document for every case. 'a' is never declared but is shadowed by 'a.b'; 'c' IS
     *  declared and is shadowed by 'c.d'. Each scope's three kinds carry distinct values, so an
     *  assertion can tell "read the right scope" from "read something".
     */
    private final static String XML =
        "<c3p0-config>\n" +
        "  <default-config>\n" +
        "    <property name=\"maxPoolSize\">30</property>\n" +
        "    <extensions><property name=\"dflt\">D</property></extensions>\n" +
        "    <user-overrides user=\"steve\"><property name=\"maxPoolSize\">31</property></user-overrides>\n" +
        "    <user-overrides user=\"dfltOnly\"><property name=\"minPoolSize\">32</property></user-overrides>\n" +
        "  </default-config>\n" +
        "  <named-config name=\"plain\">\n" +
        "    <property name=\"maxPoolSize\">40</property>\n" +
        "    <extensions><property name=\"plainExt\">P</property></extensions>\n" +
        "    <user-overrides user=\"steve\"><property name=\"maxPoolSize\">41</property></user-overrides>\n" +
        "  </named-config>\n" +
        "  <named-config name=\"a.b\">\n" +
        "    <property name=\"maxPoolSize\">50</property>\n" +
        "    <extensions><property name=\"abExt\">AB</property></extensions>\n" +
        "    <user-overrides user=\"steve\"><property name=\"maxPoolSize\">51</property></user-overrides>\n" +
        "  </named-config>\n" +
        "  <named-config name=\"c\">\n" +
        "    <property name=\"minPoolSize\">60</property>\n" +
        "    <extensions><property name=\"cExt\">C</property></extensions>\n" +
        "    <user-overrides user=\"steve\"><property name=\"minPoolSize\">61</property></user-overrides>\n" +
        "  </named-config>\n" +
        "  <named-config name=\"c.d\">\n" +
        "    <property name=\"maxPoolSize\">70</property>\n" +
        "    <extensions><property name=\"cdExt\">CD</property></extensions>\n" +
        "    <user-overrides user=\"steve\"><property name=\"maxPoolSize\">71</property></user-overrides>\n" +
        "  </named-config>\n" +
        "</c3p0-config>\n";

    private Path dir;

    @Override
    @SuppressWarnings("unchecked")
    protected void setUp() throws Exception
    {
        dir = Files.createTempDirectory( "c3p0-shadowing-" ).toRealPath();
        Path file = dir.resolve( "c3p0-config.xml" );
        PrintWriter pw = new PrintWriter( file.toFile(), "UTF-8" );
        try { pw.print( XML ); }
        finally { pw.close(); }

        MultiPropertiesConfig fromXml = MConfig.AsProvidedVetoable.readUncachedResourceConfig(
            new String[0], new String[] { "c3p0-xml:" + file.toUri() }, new ArrayList() );
        C3P0Config.refreshMainConfigWithOverrides( fromXml, "named scope shadowing test" );
    }

    @Override
    protected void tearDown() throws Exception
    {
        C3P0Config.refreshMainConfig();
        deleteRecursively( dir );
    }

    private static void deleteRecursively( Path p )
    {
        try
        {
            if ( Files.isDirectory( p ) )
                for ( Path child : Files.newDirectoryStream( p ) ) deleteRecursively( child );
            Files.deleteIfExists( p );
        }
        catch ( Exception e ) { /* best effort */ }
    }

    // ------------------------------------------------------------- the three kinds, per scope

    private static String prop( String scope, String key )
    {
        Map<String,String> all = C3P0Config.getUnspecifiedUserProperties( scope );
        assertNotNull( "getUnspecifiedUserProperties(" + scope + ") should never be null", all );
        return all.get( key );
    }

    private static String single( String scope, String key )
    { return C3P0Config.getUnspecifiedUserProperty( key, scope ); }

    private static Map<String,String> extensions( String scope )
    { return C3P0Config.getExtensions( scope ); }

    private static Map<String,String> overridesFor( String scope, String user )
    {
        Map<String,Map<String,String>> all = C3P0Config.getUserOverrides( scope );
        assertNotNull( "getUserOverrides(" + scope + ") should never be null here", all );
        return all.get( user );
    }

    /**
     *  What the default scope resolves this key to &mdash; which is what every scope that does not
     *  set the key itself must resolve it to as well. The XML above deliberately leaves minPoolSize
     *  unset in its default-config, so that is the value these cases compare against.
     *
     *  <p>Read, rather than written down. This was once the literal "3", c3p0's compiled-in
     *  minPoolSize default, which is what the default test fixture leaves it at &mdash; but
     *  test/resources-local-rough/c3p0.properties sets minPoolSize, so under
     *  <code>C3P0_TEST_CONFIG=rough</code> the literal was simply wrong, and two cases here failed
     *  with a bare <code>expected:&lt;3&gt; but was:&lt;10&gt;</code> that pointed at nothing.</p>
     *
     *  <p>Any fixture is entitled to set any pool property. What these cases are about is which
     *  SCOPE a value is read from, never which number it is, so the expectation should come from
     *  the configuration in force rather than from a guess about it.</p>
     */
    private static String defaultScopeValue( String key )
    {
        String out = prop( null, key );
        assertNotNull( "precondition: the default scope must resolve '" + key + "' to something, "
                           + "or comparing a scope against it proves nothing", out );
        return out;
    }

    // =========================================================== the default scope

    public void testTheDefaultScopeCarriesItsOwnThreeKinds()
    {
        assertEquals( "30", prop( null, "maxPoolSize" ) );
        assertEquals( "D", extensions( null ).get( "dflt" ) );
        assertEquals( "31", overridesFor( null, "steve" ).get( "maxPoolSize" ) );
        assertEquals( "32", overridesFor( null, "dfltOnly" ).get( "minPoolSize" ) );
    }

    // =========================================================== an ordinary named scope

    public void testAnOrdinaryNamedScopeCarriesItsOwnThreeKinds()
    {
        assertEquals( "40", prop( "plain", "maxPoolSize" ) );

        assertEquals( "the scope's extensions replace the default scope's", "P", extensions( "plain" ).get( "plainExt" ) );
        assertNull( "and do not inherit them", extensions( "plain" ).get( "dflt" ) );

        assertEquals( "the named scope's own override wins", "41", overridesFor( "plain", "steve" ).get( "maxPoolSize" ) );
        assertEquals( "and a user named only in the default scope survives the merge",
                      "32", overridesFor( "plain", "dfltOnly" ).get( "minPoolSize" ) );
    }

    // =========================================================== shadowed, never declared

    /**
     *  'a' is not declared; only 'a.b' is. So 'a' must behave exactly as a name nobody has ever
     *  mentioned &mdash; in all three kinds.
     *
     *  <p>This is the case that used to leak. {@code a.b}'s keys sit under {@code a}'s prefix,
     *  so a definedness test that merely counted keys called 'a' defined, and the conversion
     *  then handed it {@code a.b}'s properties.</p>
     */
    public void testAShadowedUndeclaredScopeIsWhollyInert()
    {
        assertEquals( "must not inherit a.b's maxPoolSize", "30", prop( "a", "maxPoolSize" ) );
        assertEquals( "and must resolve a key nothing in this document sets exactly as the "
                          + "default scope does, like any undefined scope",
                      defaultScopeValue( "minPoolSize" ), prop( "a", "minPoolSize" ) );

        assertNull( "must not inherit a.b's extensions", extensions( "a" ).get( "abExt" ) );
        assertEquals( "it sees the default scope's, like any undefined scope", "D", extensions( "a" ).get( "dflt" ) );

        assertEquals( "must not inherit a.b's user override", "31", overridesFor( "a", "steve" ).get( "maxPoolSize" ) );
    }

    /** Said the strongest way available: indistinguishable from a name nothing mentions. */
    public void testAShadowedUndeclaredScopeAnswersExactlyAsAnUnknownOneDoes()
    {
        assertEquals( prop( "nosuch", "maxPoolSize" ), prop( "a", "maxPoolSize" ) );
        assertEquals( prop( "nosuch", "minPoolSize" ), prop( "a", "minPoolSize" ) );
        assertEquals( extensions( "nosuch" ), extensions( "a" ) );
        assertEquals( C3P0Config.getUserOverrides( "nosuch" ), C3P0Config.getUserOverrides( "a" ) );
    }

    // =========================================================== shadowed AND declared

    /**
     *  'c' is declared and is also shadowed by 'c.d'. No definedness test can help here &mdash;
     *  'c' really is defined &mdash; so this is what the remainder-anchored conversion is for.
     *
     *  <p>'c' sets only minPoolSize, so if it absorbed 'c.d' it would acquire a maxPoolSize of
     *  70 that nothing configured for it.</p>
     */
    public void testADeclaredScopeIsNotPollutedByADeeperScopesProperties()
    {
        assertEquals( "its own property", "60", prop( "c", "minPoolSize" ) );
        assertEquals( "and NOT c.d's maxPoolSize", "30", prop( "c", "maxPoolSize" ) );
    }

    public void testADeclaredScopeIsNotPollutedByADeeperScopesExtensions()
    {
        assertEquals( "C", extensions( "c" ).get( "cExt" ) );
        assertNull( "c.d's extension must not appear", extensions( "c" ).get( "cdExt" ) );
    }

    public void testADeclaredScopeIsNotPollutedByADeeperScopesUserOverrides()
    {
        Map<String,String> steve = overridesFor( "c", "steve" );
        assertEquals( "c's own override for steve", "61", steve.get( "minPoolSize" ) );
        assertEquals( "the default scope's other property for steve survives the cross-scope merge",
                      "31", steve.get( "maxPoolSize" ) );
        assertFalse( "and c.d's override for steve must not reach c: " + steve,
                     "71".equals( steve.get( "maxPoolSize" ) ) );
    }

    // =========================================================== the shadowing scopes themselves

    /** The deeper names must be fully readable; the point was never to disable them. */
    public void testTheDeeperScopesKeepTheirOwnThreeKinds()
    {
        assertEquals( "50", prop( "a.b", "maxPoolSize" ) );
        assertEquals( "AB", extensions( "a.b" ).get( "abExt" ) );
        assertEquals( "51", overridesFor( "a.b", "steve" ).get( "maxPoolSize" ) );

        assertEquals( "70", prop( "c.d", "maxPoolSize" ) );
        assertEquals( "CD", extensions( "c.d" ).get( "cdExt" ) );
        assertEquals( "71", overridesFor( "c.d", "steve" ).get( "maxPoolSize" ) );

        // 'c' sets minPoolSize=60 and 'c.d' sets none, so c.d must fall through to the default
        // scope rather than up into the prefix-sharing name above it.
        String dfltMinPoolSize = defaultScopeValue( "minPoolSize" );
        assertFalse( "precondition: the test fixture must not itself resolve minPoolSize to c's "
                         + "own value, or this assertion could not tell inheritance from a correct "
                         + "fallback. Fixture value: " + dfltMinPoolSize,
                     "60".equals( dfltMinPoolSize ) );
        assertEquals( "a deeper scope does not inherit its prefix-sharing sibling's property either",
                      dfltMinPoolSize, prop( "c.d", "minPoolSize" ) );
    }

    // =========================================================== definedness, both sides

    /**
     *  The mirror case: a deeper name nobody declared, beneath a scope that IS declared.
     *
     *  <p>{@code plain} is declared; {@code plain.sub} is not. Shadowing runs from deeper keys
     *  up into shallower prefixes, so this direction should be unaffected &mdash; which is worth
     *  pinning rather than assuming, since it is the case a reader of the definedness walk is
     *  most likely to think is symmetric with the other.</p>
     */
    public void testADeeperUndeclaredNameBeneathADeclaredScopeIsUndefinedAndInert()
    {
        assertEquals( "must not inherit 'plain''s property", "30", prop( "plain.sub", "maxPoolSize" ) );
        assertNull( "nor its extensions", extensions( "plain.sub" ).get( "plainExt" ) );
        assertEquals( "nor its user override", "31", overridesFor( "plain.sub", "steve" ).get( "maxPoolSize" ) );
    }

    // =========================================================== the diagnostic

    /*
     *  warnOnUnexpectedProperties and the undefined-scope notices log directly rather than
     *  through delayed items, so they can only be seen where they land. MLog picks its backend
     *  from the CLASSPATH; under this build that is java.util.logging. Where it resolves
     *  elsewhere these assertions would pass vacuously, so they skip and say so.
     */
    private static boolean warningsAreVisible()
    { return MLog.getLogger( C3P0Config.class ).getClass().getName().indexOf( "jdk14" ) >= 0; }

    private static class Captor extends Handler
    {
        final List<String> records = new ArrayList<String>();

        @Override
        public void publish( LogRecord r )
        { if ( r.getLevel().intValue() >= Level.WARNING.intValue() ) records.add( String.valueOf( r.getMessage() ) ); }

        @Override
        public void flush() {}

        @Override
        public void close() {}
    }

    /** The WARNINGs C3P0Config logs while the given scope's properties are first read. */
    private static List<String> warningsWhileReading( String scope )
    {
        Logger jul    = Logger.getLogger( C3P0Config.class.getName() );
        Captor captor = new Captor();
        jul.addHandler( captor );
        try { C3P0Config.getUnspecifiedUserProperties( scope ); }
        finally { jul.removeHandler( captor ); }
        return captor.records;
    }

    private static boolean mentions( List<String> messages, String fragment )
    {
        for ( String m : messages ) if ( m.indexOf( fragment ) >= 0 ) return true;
        return false;
    }

    /**
     *  A shadowed-but-undeclared scope must SAY it is undefined.
     *
     *  <p>Reading correctly is not enough. Once a deeper scope exists, the keys under this
     *  name's prefix are non-empty, so a check gated on emptiness never reaches the definedness
     *  test and the notice goes missing &mdash; leaving a deployment that typed a scope name one
     *  segment short of a real one with no clue why its settings do nothing. The values are
     *  right either way, which is exactly why this needs asserting separately: nothing else here
     *  would notice.</p>
     *
     *  <p>Must be the first read of this scope in the method: both the definedness answer and
     *  the property map are cached, so a second call is silent.</p>
     */
    public void testAShadowedUndeclaredScopeSaysItIsUndefined()
    {
        if ( !warningsAreVisible() ) return;

        List<String> warnings = warningsWhileReading( "a" );
        assertTrue( "reading shadowed-but-undeclared scope 'a' should report it undefined: " + warnings,
                    mentions( warnings, "undefined named config 'a'" ) );
    }

    /** And a scope that really is declared must not be slandered, shadowed or otherwise. */
    public void testDeclaredScopesAreNotReportedUndefined()
    {
        if ( !warningsAreVisible() ) return;

        for ( String scope : new String[] { "plain", "c", "c.d", "a.b" } )
        {
            List<String> warnings = warningsWhileReading( scope );
            assertFalse( "'" + scope + "' is declared and must not be called undefined: " + warnings,
                         mentions( warnings, "undefined named config '" + scope + "'" ) );
        }
    }

    /**
     *  Definedness itself, on both sides of a shadow and in both directions.
     *
     *  <p>{@code namedConfigIsDefined} is private, and this is the one thing that observes it:
     *  an undefined scope announces itself, a defined one stays quiet. So the warning is read
     *  here as the <i>answer</i> rather than as a message &mdash; making it package-private would
     *  let this assert the predicate directly, which would be a little stronger, but the
     *  behaviour under test is what a deployment actually sees.</p>
     *
     *  <p>The four states that must be distinguished, two of them shadowed:</p>
     *  <ul>
     *    <li>{@code a} &mdash; shadowed by a deeper scope, itself undeclared: UNDEFINED</li>
     *    <li>{@code c} &mdash; shadowed by a deeper scope, itself declared: DEFINED</li>
     *    <li>{@code a.b}, {@code c.d} &mdash; the shadowing scopes: DEFINED</li>
     *    <li>{@code plain.sub} &mdash; a deeper name under a declared scope: UNDEFINED</li>
     *  </ul>
     *
     *  <p>Each scope is read exactly once here, because both the definedness answer and the
     *  property map are cached: a second read of the same scope is silent whatever the truth.</p>
     */
    public void testDefinednessIsCorrectOnBothSidesOfAShadow()
    {
        if ( !warningsAreVisible() ) return;

        String[] undefined = { "a", "plain.sub", "nosuch" };
        String[] defined   = { "a.b", "c", "c.d", "plain" };

        for ( String scope : undefined )
            assertTrue( "'" + scope + "' should be undefined and should say so",
                        mentions( warningsWhileReading( scope ), "undefined named config '" + scope + "'" ) );

        for ( String scope : defined )
            assertFalse( "'" + scope + "' should be defined and should stay quiet",
                         mentions( warningsWhileReading( scope ), "undefined named config '" + scope + "'" ) );
    }

    // =========================================================== the invariant that broke

    /**
     *  The map accessor and the single-key accessor must agree, for every scope.
     *
     *  <p>They are built differently &mdash; one from the by-prefix bag, one from an exact key
     *  &mdash; and that is exactly how the leak announced itself: scope 'a' read 77 one way and
     *  30 the other. Two views of one configuration disagreeing is the failure this release was
     *  written to eliminate, so it is asserted directly rather than left implied.</p>
     */
    public void testTheMapAndSingleKeyAccessorsAgreeForEveryScope()
    {
        for ( String scope : new String[] { null, "plain", "plain.sub", "a", "a.b", "c", "c.d", "nosuch" } )
            for ( String key : new String[] { "maxPoolSize", "minPoolSize" } )
                assertEquals( "scope '" + scope + "', key '" + key + "'",
                              prop( scope, key ), single( scope, key ) );
    }
}
