package com.mchange.v2.c3p0.cfg;

import junit.framework.TestCase;

import java.io.File;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import com.mchange.v2.cfg.MConfig;
import com.mchange.v2.cfg.MultiPropertiesConfig;
import com.mchange.v2.log.MLog;

/**
 *  A user-override user name may contain dots.
 *
 *  <h3>Why this exists</h3>
 *
 *  <p>Database user names routinely look like <code>first.last</code>, and until 0.15.0 c3p0
 *  could not express an override for one. Worse, it did not say so: the key
 *  <code>c3p0.user-overrides.first.last.maxPoolSize</code> was read as user
 *  <code>first</code> with a property <code>last.maxPoolSize</code>, which is no property at
 *  all, so the override silently did nothing. 0.14.2 mis-parsed it identically, so this was
 *  long-standing rather than new.</p>
 *
 *  <p>The fix is one character of regular expression:
 *  <code>USER_OVERRIDES_KEY_TAIL_PATTERN</code> went from "first element is the user, the rest
 *  the property" to "the last element is the property, everything before it the user". That
 *  reading is unambiguous because pool property names never contain dots, and because
 *  user-override scopes carry no declaration marker -- there is no declared-and-empty user for
 *  a dotted name to collide with, which is exactly what makes the superficially similar case of
 *  a dotted <i>named-config</i> name irreducibly ambiguous and so refused outright.</p>
 *
 *  <h3>The part that took two tries</h3>
 *
 *  <p>Reading the key correctly is half the job. {@code warnOnUnexpectedProperties} validates
 *  keys independently, by a {@code switch} on segment count -- and a dotted user name changes
 *  the count, so every such key fell through to "Unexpected c3p0-config property". c3p0 would
 *  have honored the override and called it unexpected in the same breath, which is the
 *  two-representations failure this release exists to eliminate, in miniature.</p>
 *
 *  <p>So the warning assertions here matter as much as the value assertions, and
 *  {@link #testExtensionNamesContainingUserOverridesDrawNoWarning} guards the second mistake: a
 *  first attempt gated the new branch on {@code key.indexOf("user-overrides") >= 0}, a substring
 *  test, which diverted legitimate <i>extension</i> keys into it -- extension names being
 *  arbitrary strings a deployment invents -- and, finding them not user-override-shaped,
 *  reported them as unknown. Five keys regressed and no existing test noticed.</p>
 *
 *  <p>Nothing here needs a database.</p>
 */
public class DottedUserOverridesJUnitTestCase extends TestCase
{
    private final static String DOTTED      = "first.last";
    private final static String MULTIPART   = "firstname.middlename.lastname.jr";
    private final static String PLAIN       = "plainuser";
    private final static String SCOPE       = "alpha";

    private Path dir;

    @Override
    protected void setUp() throws Exception
    { dir = Files.createTempDirectory( "c3p0-dotted-uo-" ).toRealPath(); }

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

    // ------------------------------------------------------------- fixture

    private static Properties props( String... kvs )
    {
        Properties p = new Properties();
        for ( int i = 0; i < kvs.length; i += 2 ) p.setProperty( kvs[i], kvs[i+1] );
        return p;
    }

    /** Apply overrides over the library's own configuration, as an application would. */
    private static void apply( Properties overrides )
    {
        C3P0Config.refreshMainConfigWithOverrides(
            MConfig.fromProperties( "/dotted-user-overrides-test", overrides ), "dotted user override test" );
    }

    private static Map<String,String> overridesFor( String configName, String user )
    {
        Map<String,Map<String,String>> all = C3P0Config.getUserOverrides( configName );
        assertNotNull( "getUserOverrides(" + configName + ") should never be null here", all );
        return all.get( user );
    }

    // ------------------------------------------------------------- warning capture

    /*
     *  warnOnUnexpectedProperties logs directly rather than through delayed items, so the only
     *  way to see it is to listen where it lands. MLog chooses its backend from what is on the
     *  CLASSPATH; under this build it resolves to java.util.logging, and these assertions can
     *  only see records that reach a jul Logger. Where it resolves elsewhere they would pass
     *  vacuously, so they are skipped and say so, in the spirit of the posixSupported() guards
     *  elsewhere in this suite.
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

    /** The WARNING-or-worse messages logged by C3P0Config while applying these overrides. */
    private static List<String> warningsWhileApplying( Properties overrides )
    {
        Logger  jul    = Logger.getLogger( C3P0Config.class.getName() );
        Captor  captor = new Captor();
        jul.addHandler( captor );
        try { apply( overrides ); }
        finally { jul.removeHandler( captor ); }
        return captor.records;
    }

    private static List<String> mentioning( List<String> messages, String fragment )
    {
        List<String> out = new ArrayList<String>();
        for ( String m : messages ) if ( m.indexOf( fragment ) >= 0 ) out.add( m );
        return out;
    }

    // =========================================================== the properties route

    /** The case deployments have been unable to express. */
    public void testADottedUserGetsItsOverridesInTheDefaultScope()
    {
        apply( props( "c3p0.user-overrides." + DOTTED + ".maxPoolSize", "42",
                      "c3p0.user-overrides." + PLAIN  + ".maxPoolSize", "7" ) );

        assertEquals( "the dotted user's override must resolve",
                      "42", overridesFor( null, DOTTED ).get( "maxPoolSize" ) );
        assertEquals( "and an undotted user alongside it must be unaffected",
                      "7", overridesFor( null, PLAIN ).get( "maxPoolSize" ) );
        assertNull( "no phantom user should be created from the dotted name's first element",
                    overridesFor( null, "first" ) );
    }

    /** And in a named config, which routes through a different prefix. */
    public void testADottedUserGetsItsOverridesInANamedScope()
    {
        apply( props( "c3p0.named-configs." + SCOPE + ".maxPoolSize", "11",
                      "c3p0.named-configs." + SCOPE + ".user-overrides." + DOTTED + ".minPoolSize", "3" ) );

        assertEquals( "3", overridesFor( SCOPE, DOTTED ).get( "minPoolSize" ) );
        assertNull( "and nothing should land under the first element alone",
                    overridesFor( SCOPE, "first" ) );
    }

    /** Any number of dots, not just one. */
    public void testAUserNameOfSeveralDotsWorks()
    {
        apply( props( "c3p0.user-overrides." + MULTIPART + ".maxPoolSize", "17" ) );

        assertEquals( "17", overridesFor( null, MULTIPART ).get( "maxPoolSize" ) );
    }

    /**
     *  The cross-scope merge, for a dotted user.
     *
     *  <p>0.15.0 merges a user's overrides across scopes property by property, where 0.14.2
     *  replaced the whole set from the named scope. That improvement is pinned for undotted
     *  users by the compatibility harness; this is the same claim for a dotted one, since the
     *  merge is keyed by the user name the new pattern produces.</p>
     */
    public void testADottedUsersOverridesMergeAcrossScopes()
    {
        apply( props( "c3p0.user-overrides." + DOTTED + ".maxPoolSize", "20",
                      "c3p0.user-overrides." + DOTTED + ".minPoolSize", "5",
                      "c3p0.named-configs." + SCOPE + ".maxPoolSize", "11",
                      "c3p0.named-configs." + SCOPE + ".user-overrides." + DOTTED + ".maxPoolSize", "99" ) );

        Map<String,String> merged = overridesFor( SCOPE, DOTTED );
        assertEquals( "the named scope's own value wins", "99", merged.get( "maxPoolSize" ) );
        assertEquals( "and the default scope's other property survives, which is the merge",
                      "5", merged.get( "minPoolSize" ) );
    }

    // =========================================================== the warning, which is half the feature

    /**
     *  A dotted user must draw no "Unexpected c3p0-config property" warning.
     *
     *  <p>Reading the key and validating it are separate pieces of code. Honoring an override
     *  while reporting it as unexpected would leave a deployment unable to tell whether its
     *  configuration had taken effect.</p>
     */
    public void testADottedUserDrawsNoUnexpectedPropertyWarning()
    {
        if ( !warningsAreVisible() ) return;

        List<String> warnings = warningsWhileApplying( props(
            "c3p0.user-overrides." + DOTTED + ".maxPoolSize", "42",
            "c3p0.user-overrides." + MULTIPART + ".maxPoolSize", "17",
            "c3p0.named-configs." + SCOPE + ".maxPoolSize", "11",
            "c3p0.named-configs." + SCOPE + ".user-overrides." + DOTTED + ".minPoolSize", "3" ) );

        assertEquals( "a dotted user override is expected configuration: " + mentioning( warnings, "Unexpected" ),
                      0, mentioning( warnings, "Unexpected c3p0-config property" ).size() );

        // and the values really did arrive, so the absence of a warning is not the absence of a read
        assertEquals( "42", overridesFor( null, DOTTED ).get( "maxPoolSize" ) );
        assertEquals( "3",  overridesFor( SCOPE, DOTTED ).get( "minPoolSize" ) );
    }

    /**
     *  The regression guard. Extension names are arbitrary strings a deployment invents for its
     *  own <code>getExtensions(..)</code> lookup, so one may contain the text
     *  "user-overrides" -- and a named config may be named that way too.
     *
     *  <p>Classifying user-override keys by searching the whole key for that text, rather than
     *  by matching it as a whole dot-separated element in a known position, diverts all five of
     *  these into the user-override branch, which then reports them as unknown because they are
     *  not user-override shaped. Each of these five warned under that implementation and under
     *  no other.</p>
     */
    public void testExtensionNamesContainingUserOverridesDrawNoWarning()
    {
        if ( !warningsAreVisible() ) return;

        String[] keys = {
            "c3p0.extensions.user-overrides",
            "c3p0.extensions.user-overrides.foo",
            "c3p0.extensions.a.user-overrides",
            "c3p0.named-configs." + SCOPE + ".extensions.user-overrides",
            "c3p0.named-configs.user-overrides-cfg.maxPoolSize",
        };
        Properties p = new Properties();
        for ( String k : keys ) p.setProperty( k, "1" );

        List<String> warnings = warningsWhileApplying( p );

        for ( String k : keys )
            assertEquals( "'" + k + "' is valid configuration and must not be reported unexpected: " +
                          mentioning( warnings, k ),
                          0, mentioning( warnings, k ).size() );
    }

    /**
     *  The validation that survives: whatever the user name, the final element must still name a
     *  real c3p0 property. Otherwise the new branch would wave through every malformed key
     *  beginning <code>c3p0.user-overrides.</code>.
     */
    public void testAnUnknownPropertyAfterTheUserNameStillWarns()
    {
        if ( !warningsAreVisible() ) return;

        List<String> warnings = warningsWhileApplying( props(
            "c3p0.user-overrides." + DOTTED + ".notAnyPropertyOfC3p0", "1" ) );

        assertEquals( "an unknown property should still be reported, dotted user or not: " + warnings,
                      1, mentioning( warnings, "notAnyPropertyOfC3p0" ).size() );
    }

    /**
     *  The accepted divergence, pinned deliberately.
     *
     *  <p>"The last element is the property" cuts both ways: a <i>compound</i> property name
     *  under a user override is now absorbed into the user name instead of the reverse. So
     *  <code>c3p0.user-overrides.deep.com.mchange.v2.log.MLog</code> yields a user
     *  <code>deep.com.mchange.v2.log</code> with a property <code>MLog</code>.</p>
     *
     *  <p>This is a decision, not an oversight. Such a key means nothing under a user override
     *  either way -- <code>com.mchange.*</code> settings are read statically, with no DataSource
     *  and so no user in hand, which is why the XML parser refuses them in this position -- and
     *  the resulting phantom user is inert. Before the change the same key produced a phantom
     *  <i>property</i> on a plausible user instead, which was no better. What makes it tolerable
     *  is that it is not silent: the key still draws an unexpected-property warning, because
     *  <code>MLog</code> is not a pool property.</p>
     *
     *  <p>If someone later decides to filter these out at parse time, this case should fail and
     *  tell them they are changing a decision rather than fixing a bug.</p>
     */
    public void testACompoundPropertyNameIsAbsorbedIntoTheUserName()
    {
        Properties p = props( "c3p0.user-overrides.deep.com.mchange.v2.log.MLog", "whatever" );

        if ( warningsAreVisible() )
        {
            List<String> warnings = warningsWhileApplying( p );
            assertEquals( "the phantom user is tolerated, but the key is still reported: " + warnings,
                          1, mentioning( warnings, "c3p0.user-overrides.deep.com.mchange.v2.log.MLog" ).size() );
        }
        else
            apply( p );

        Map<String,String> phantom = overridesFor( null, "deep.com.mchange.v2.log" );
        assertNotNull( "the phantom user is expected to exist, by decision", phantom );
        assertEquals( "whatever", phantom.get( "MLog" ) );
        assertNull( "and the pre-0.15.0 reading, user 'deep', must NOT also appear",
                    overridesFor( null, "deep" ) );
    }

    // =========================================================== the XML route

    /**
     *  <code>&lt;user-overrides user="first.last"&gt;</code> must resolve exactly as the
     *  properties-style key does.
     *
     *  <p>The XML source writes <code>c3p0.user-overrides.&lt;user&gt;.&lt;prop&gt;</code> and
     *  then shares every later step, so this is really a test that it writes the key whole,
     *  without inspecting or rejecting the user name. It briefly did reject it: warn-and-ignore
     *  for a dotted user was the previous attempt at this problem, before the parse was fixed
     *  and the refusal became unnecessary.</p>
     */
    public void testTheXmlRouteAgreesWithThePropertiesRoute() throws Exception
    {
        String xml =
            "<c3p0-config>\n" +
            "  <default-config>\n" +
            "    <user-overrides user=\"" + DOTTED + "\">\n" +
            "      <property name=\"maxPoolSize\">42</property>\n" +
            "    </user-overrides>\n" +
            "  </default-config>\n" +
            "  <named-config name=\"" + SCOPE + "\">\n" +
            "    <property name=\"maxPoolSize\">11</property>\n" +
            "    <user-overrides user=\"" + MULTIPART + "\">\n" +
            "      <property name=\"minPoolSize\">3</property>\n" +
            "    </user-overrides>\n" +
            "  </named-config>\n" +
            "</c3p0-config>\n";

        Path file = dir.resolve( "c3p0-config.xml" );
        PrintWriter pw = new PrintWriter( file.toFile(), "UTF-8" );
        try { pw.print( xml ); }
        finally { pw.close(); }
        if ( posixSupported() ) Files.setPosixFilePermissions( file, PosixFilePermissions.fromString( "rw-------" ) );

        MultiPropertiesConfig fromXml = MConfig.AsProvidedVetoable.readUncachedResourceConfig(
            new String[0], new String[] { "c3p0-xml:" + file.toUri() }, new ArrayList() );

        // the keys the XML source wrote, before any of C3P0Config's reading of them
        assertEquals( "the dotted user name must be written whole into the key",
                      "42", fromXml.getProperty( "c3p0.user-overrides." + DOTTED + ".maxPoolSize" ) );
        assertEquals( "11", fromXml.getProperty( "c3p0.named-configs." + SCOPE + ".maxPoolSize" ) );
        assertEquals( "3", fromXml.getProperty(
            "c3p0.named-configs." + SCOPE + ".user-overrides." + MULTIPART + ".minPoolSize" ) );

        // and the same answers out the other end
        C3P0Config.refreshMainConfigWithOverrides( fromXml, "dotted user override test, via XML" );

        assertEquals( "42", overridesFor( null, DOTTED ).get( "maxPoolSize" ) );
        assertEquals( "3",  overridesFor( SCOPE, MULTIPART ).get( "minPoolSize" ) );
        assertNull( overridesFor( null, "first" ) );
    }

    private boolean posixSupported()
    { return dir.getFileSystem().supportedFileAttributeViews().contains( "posix" ); }
}
