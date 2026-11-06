package com.mchange.v2.c3p0.test.cfgxml;

import junit.framework.TestCase;

import com.mchange.v2.cfg.ConfigVetoedException;
import com.mchange.v2.cfg.DelayedLogItem;
import com.mchange.v2.cfg.MConfig;
import com.mchange.v2.cfg.MultiPropertiesConfig;
import com.mchange.v2.c3p0.cfg.CfgInternals;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 *  <code>c3p0-xml:</code> identifiers: c3p0's XML configuration format read as properties.
 *
 *  <h3>What these cases are really about</h3>
 *
 *  <p>The source has no access to c3p0's list of configurable properties, so it cannot validate a
 *  name — it applies a shape heuristic instead. These cases are that heuristic, written out, plus
 *  the structural rules about where an element may appear.</p>
 *
 *  <p>The asymmetry between the two scopes is the heart of it, and is deliberate. The default
 *  scope shares a namespace with properties-style config, so a fully-qualified name written there
 *  means itself. A named config is a per-DataSource scope, while every reader of a
 *  <code>com.mchange.*</code> setting reads it statically with no DataSource in hand — so such a
 *  name cannot be scoped, and is refused rather than silently promoted to a global.</p>
 *
 *  <p>Read through <code>AsProvidedVetoable</code> throughout: the source honors
 *  <code>permissions=useronly</code> on its <code>file:</code> form, so it is vetoable, and plain
 *  <code>AsProvided</code> declines to read it at all.</p>
 */
public final class C3P0XmlConfigJUnitTestCase extends TestCase
{
    private final static String[] NONE = new String[0];

    private Path dir;

    @Override
    protected void setUp() throws Exception
    { dir = Files.createTempDirectory( "mchange-cfg-c3p0xml-" ).toRealPath(); }

    @Override
    protected void tearDown() throws Exception
    { deleteRecursively( dir ); }

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

    /** An XML document in a temp file, named by a c3p0-xml:file: identifier. */
    private String xml( String body, String query ) throws IOException
    {
        Path p = Files.createTempFile( dir, "c3p0-config-", ".xml" );
        PrintWriter pw = new PrintWriter( p.toFile(), "UTF-8" );
        try { pw.println( "<c3p0-config>" ); pw.println( body ); pw.println( "</c3p0-config>" ); }
        finally { pw.close(); }
        if ( posixSupported() ) Files.setPosixFilePermissions( p, PosixFilePermissions.fromString( "rw-------" ) );
        return "c3p0-xml:" + p.toUri() + (query == null ? "" : "?" + query);
    }

    private String xml( String body ) throws IOException
    { return xml( body, null ); }

    /** A document written verbatim, for the cases about the root element. */
    private String raw( String document ) throws IOException
    {
        Path p = Files.createTempFile( dir, "c3p0-raw-", ".xml" );
        PrintWriter pw = new PrintWriter( p.toFile(), "UTF-8" );
        try { pw.println( document ); }
        finally { pw.close(); }
        return "c3p0-xml:" + p.toUri();
    }

    private boolean posixSupported()
    { return dir.getFileSystem().supportedFileAttributeViews().contains( "posix" ); }

    @SuppressWarnings("unchecked")
    private static MultiPropertiesConfig read( String... identifiers ) throws ConfigVetoedException
    { return MConfig.AsProvidedVetoable.readUncachedResourceConfig( NONE, identifiers, new ArrayList() ); }

    private static Properties propsOf( MultiPropertiesConfig mpc )
    { return mpc.getPropertiesByPrefix( "" ); }

    @SuppressWarnings("unchecked")
    private static boolean warns( MultiPropertiesConfig mpc, String... fragments )
    {
        for ( Object o : mpc.getDelayedLogItems() )
        {
            DelayedLogItem item = (DelayedLogItem) o;
            if (! DelayedLogItem.Level.WARNING.equals( item.getLevel() ) ) continue;
            String text = String.valueOf( item.getText() );
            boolean all = true;
            for ( String f : fragments ) if ( text.indexOf( f ) < 0 ) all = false;
            if ( all ) return true;
        }
        return false;
    }

    private static String assertVetoed( String... identifiers )
    {
        try
        {
            read( identifiers );
            fail( "expected a veto" );
            return null;
        }
        catch ( ConfigVetoedException expected )
        { return expected.getMessage(); }
    }

    // =================================================== the documented examples

    /**
     *  The shape the manual shows, end to end: a default config, a named config, user overrides
     *  and extensions together. Taken from doc/docsrc/index.html rather than invented, so the
     *  mapping is pinned against what deployments were actually told to write.
     */
    public void testTheDocumentedExampleMapsAsExpected() throws Exception
    {
        Properties p = propsOf( read( xml(
            "  <default-config>\n" +
            "    <property name=\"maxPoolSize\">30</property>\n" +
            "    <extensions>\n" +
            "      <property name=\"initSql\">SET SCHEMA 'default'</property>\n" +
            "    </extensions>\n" +
            "  </default-config>\n" +
            "  <named-config name=\"intergalactoApp\">\n" +
            "    <property name=\"maxPoolSize\">1000</property>\n" +
            "    <user-overrides user=\"steve\">\n" +
            "      <property name=\"maxPoolSize\">20</property>\n" +
            "    </user-overrides>\n" +
            "    <extensions>\n" +
            "      <property name=\"initSql\">SET SCHEMA 'intergalacto'</property>\n" +
            "    </extensions>\n" +
            "  </named-config>" ) ) );

        assertEquals( "30",   p.getProperty( "c3p0.maxPoolSize" ) );
        assertEquals( "SET SCHEMA 'default'", p.getProperty( "c3p0.extensions.initSql" ) );
        assertEquals( "1000", p.getProperty( "c3p0.named-configs.intergalactoApp.maxPoolSize" ) );
        assertEquals( "20",   p.getProperty( "c3p0.named-configs.intergalactoApp.user-overrides.steve.maxPoolSize" ) );
        assertEquals( "SET SCHEMA 'intergalacto'",
                      p.getProperty( "c3p0.named-configs.intergalactoApp.extensions.initSql" ) );
    }

    public void testDefaultScopeUserOverridesUseTheDocumentedKeys() throws Exception
    {
        Properties p = propsOf( read( xml(
            "  <default-config>\n" +
            "    <user-overrides user=\"steve\">\n" +
            "      <property name=\"maxPoolSize\">15</property>\n" +
            "      <property name=\"minPoolSize\">5</property>\n" +
            "    </user-overrides>\n" +
            "    <user-overrides user=\"ramona\">\n" +
            "      <property name=\"maxPoolSize\">50</property>\n" +
            "    </user-overrides>\n" +
            "  </default-config>" ) ) );

        assertEquals( "15", p.getProperty( "c3p0.user-overrides.steve.maxPoolSize" ) );
        assertEquals( "5",  p.getProperty( "c3p0.user-overrides.steve.minPoolSize" ) );
        assertEquals( "50", p.getProperty( "c3p0.user-overrides.ramona.maxPoolSize" ) );
    }

    // =================================================== the default scope's name rule

    /** A bare name is a c3p0 pool property and gets the prefix. */
    public void testABareNameInTheDefaultScopeGetsTheC3p0Prefix() throws Exception
    {
        Properties p = propsOf( read( xml( "  <default-config><property name=\"maxPoolSize\">30</property></default-config>" ) ) );
        assertEquals( "30", p.getProperty( "c3p0.maxPoolSize" ) );
    }

    /**
     *  A compound name in the default scope means itself. Two of them, because the two cases look
     *  different and are the same rule: an already-prefixed pool property is already correct, and
     *  a fully-qualified library setting belongs to the namespace it names.
     */
    public void testACompoundNameInTheDefaultScopeIsKeptAsWritten() throws Exception
    {
        Properties p = propsOf( read( xml(
            "  <default-config>\n" +
            "    <property name=\"c3p0.minPoolSize\">10</property>\n" +
            "    <property name=\"com.mchange.v2.log.freezeConfig\">true</property>\n" +
            "  </default-config>" ) ) );

        assertEquals( "10",   p.getProperty( "c3p0.minPoolSize" ) );
        assertEquals( "true", p.getProperty( "com.mchange.v2.log.freezeConfig" ) );
        assertNull( "it must not be prefixed a second time", p.getProperty( "c3p0.c3p0.minPoolSize" ) );
    }

    // =================================================== the named scope's name rule

    public void testANamedScopePrefixesBareAndC3p0PrefixedNamesAlike() throws Exception
    {
        Properties p = propsOf( read( xml(
            "  <named-config name=\"X\">\n" +
            "    <property name=\"maxPoolSize\">1000</property>\n" +
            "    <property name=\"c3p0.acquireIncrement\">50</property>\n" +
            "  </named-config>" ) ) );

        assertEquals( "1000", p.getProperty( "c3p0.named-configs.X.maxPoolSize" ) );
        assertEquals( "50",   p.getProperty( "c3p0.named-configs.X.acquireIncrement" ) );
        assertNull( "the c3p0. prefix must be stripped, not kept",
                    p.getProperty( "c3p0.named-configs.X.c3p0.acquireIncrement" ) );
    }

    /**
     *  THE ASYMMETRY. A fully-qualified name has no meaning in a per-DataSource scope, so it is
     *  refused and said so — where the very same name in the default scope is kept. Promoting it
     *  to a global would be the dangerous reading: an unrelated named config could then change a
     *  library-wide setting.
     */
    public void testANamedScopeRefusesAFullyQualifiedName() throws Exception
    {
        MultiPropertiesConfig mpc = read( xml(
            "  <named-config name=\"X\">\n" +
            "    <property name=\"com.mchange.v2.log.freezeConfig\">true</property>\n" +
            "  </named-config>" ) );

        Properties p = propsOf( mpc );
        assertNull( p.getProperty( "com.mchange.v2.log.freezeConfig" ) );
        assertNull( p.getProperty( "c3p0.named-configs.X.com.mchange.v2.log.freezeConfig" ) );
        assertTrue( warns( mpc, "com.mchange.v2.log.freezeConfig", "named config 'X'" ) );
    }

    /**
     *  And a c3p0-prefixed name of more than two parts is equally unusable there.
     *
     *  <p>What survives is the scope's declaration marker and nothing else, which is a stronger
     *  statement than "contributed nothing": the scope WAS declared, and the one property in it
     *  was refused. This case used to assert the properties were empty outright, from before a
     *  declared scope left a trace of its own.</p>
     *
     *  <p>It is also the case that pins HOW the marker is decided. A scope is marked when nothing
     *  landed under its prefix, not when it had no child elements &mdash; this scope has one
     *  {@code <property>}, so counting elements would call it populated, write no mark, and leave
     *  a declared scope looking undeclared once its only property was refused.</p>
     */
    public void testANamedScopeRefusesALongerC3p0PrefixedName() throws Exception
    {
        MultiPropertiesConfig mpc = read( xml(
            "  <named-config name=\"X\"><property name=\"c3p0.a.b\">v</property></named-config>" ) );

        Properties p = propsOf( mpc );
        assertEquals( "only the declaration marker should survive, got " + p, 1, p.size() );
        assertEquals( MARK, p.getProperty( "c3p0.named-configs.X" ) );
        assertNull( "the refused name must not arrive under the scope", p.getProperty( "c3p0.named-configs.X.c3p0.a.b" ) );
        assertNull( "nor promoted to a global",                        p.getProperty( "c3p0.a.b" ) );
        assertTrue( warns( mpc, "c3p0.a.b", "named config 'X'" ) );
    }

    /** A user override obeys the named-scope rule too, in either scope. */
    public void testAUserOverrideRefusesAFullyQualifiedName() throws Exception
    {
        MultiPropertiesConfig mpc = read( xml(
            "  <default-config>\n" +
            "    <user-overrides user=\"steve\">\n" +
            "      <property name=\"maxPoolSize\">15</property>\n" +
            "      <property name=\"com.mchange.v2.anything\">nope</property>\n" +
            "    </user-overrides>\n" +
            "  </default-config>" ) );

        assertEquals( "15", propsOf( mpc ).getProperty( "c3p0.user-overrides.steve.maxPoolSize" ) );
        assertNull( propsOf( mpc ).getProperty( "com.mchange.v2.anything" ) );
        assertTrue( warns( mpc, "com.mchange.v2.anything", "user override for 'steve'" ) );
    }

    // =================================================== extensions are never touched

    /**
     *  Extension names are a deployment's own, read back through getExtensions(..), so there is no
     *  vocabulary to correct them against and none is applied — not even to a name that looks
     *  exactly like a c3p0 property.
     */
    public void testExtensionNamesArePassedThroughVerbatim() throws Exception
    {
        Properties p = propsOf( read( xml(
            "  <default-config><extensions>\n" +
            "    <property name=\"initSql\">SET SCHEMA 'foo'</property>\n" +
            "    <property name=\"c3p0.looksLikeOurs\">kept</property>\n" +
            "    <property name=\"com.example.deeply.qualified\">kept</property>\n" +
            "  </extensions></default-config>" ) ) );

        assertEquals( "SET SCHEMA 'foo'", p.getProperty( "c3p0.extensions.initSql" ) );
        assertEquals( "kept", p.getProperty( "c3p0.extensions.c3p0.looksLikeOurs" ) );
        assertEquals( "kept", p.getProperty( "c3p0.extensions.com.example.deeply.qualified" ) );
    }

    public void testNamedScopeExtensionsAreScopedAndVerbatim() throws Exception
    {
        Properties p = propsOf( read( xml(
            "  <named-config name=\"X\"><extensions><property name=\"c3p0.odd\">v</property></extensions></named-config>" ) ) );

        assertEquals( "v", p.getProperty( "c3p0.named-configs.X.extensions.c3p0.odd" ) );
    }

    /**
     *  Extensions are per-config, not per-user. c3p0 offers no way to read them per user, so
     *  accepting them here would accept configuration that could never be seen — worse than
     *  refusing it, because nothing would ever say so.
     */
    public void testExtensionsBeneathAUserOverrideAreRefused() throws Exception
    {
        MultiPropertiesConfig mpc = read( xml(
            "  <default-config>\n" +
            "    <user-overrides user=\"steve\">\n" +
            "      <property name=\"maxPoolSize\">15</property>\n" +
            "      <extensions><property name=\"initSql\">nope</property></extensions>\n" +
            "    </user-overrides>\n" +
            "  </default-config>" ) );

        assertEquals( "the override's own properties still load",
                      "15", propsOf( mpc ).getProperty( "c3p0.user-overrides.steve.maxPoolSize" ) );
        for ( Object k : propsOf( mpc ).keySet() )
            assertFalse( "no extension key may be produced: " + k, String.valueOf( k ).contains( "extensions" ) );
        assertTrue( warns( mpc, "extensions", "user-overrides", "steve" ) );
    }

    // =================================================== attributes and structure

    /** A since-corrected error in the manual showed 'name' here, so it is accepted — and nagged. */
    public void testUserOverridesAcceptsNameInPlaceOfUserButPrefersUser() throws Exception
    {
        MultiPropertiesConfig mpc = read( xml(
            "  <default-config><user-overrides name=\"ramona\">\n" +
            "    <property name=\"maxPoolSize\">50</property>\n" +
            "  </user-overrides></default-config>" ) );

        assertEquals( "50", propsOf( mpc ).getProperty( "c3p0.user-overrides.ramona.maxPoolSize" ) );
        assertTrue( warns( mpc, "'name' attribute", "'user' is the documented and preferred" ) );
    }

    public void testAUserOverrideWithNoUserAtAllIsRefused() throws Exception
    {
        MultiPropertiesConfig mpc = read( xml(
            "  <default-config><user-overrides><property name=\"maxPoolSize\">50</property></user-overrides></default-config>" ) );

        assertTrue( propsOf( mpc ).isEmpty() );
        assertTrue( warns( mpc, "neither a 'user' nor a 'name'" ) );
    }

    public void testANamedConfigWithoutANameIsRefused() throws Exception
    {
        MultiPropertiesConfig mpc = read( xml(
            "  <named-config><property name=\"maxPoolSize\">9</property></named-config>" ) );

        assertTrue( propsOf( mpc ).isEmpty() );
        assertTrue( warns( mpc, "'named-config' element has no 'name'" ) );
    }

    public void testADocumentWithTheWrongRootElementIsRefused() throws Exception
    {
        MultiPropertiesConfig mpc = read( raw( "<not-c3p0-config><default-config/></not-c3p0-config>" ) );

        assertEquals( "the source must contribute nothing", Arrays.asList(), Arrays.asList( mpc.getPropertiesResourcePaths() ) );
        assertTrue( warns( mpc, "root element", "should be 'c3p0-config'" ) );
    }

    public void testUnparseableXmlIsRefused() throws Exception
    {
        MultiPropertiesConfig mpc = read( raw( "<c3p0-config><default-config></c3p0-config>" ) );

        assertEquals( Arrays.asList(), Arrays.asList( mpc.getPropertiesResourcePaths() ) );
        assertTrue( warns( mpc, "could not be parsed as XML" ) );
    }

    public void testDuplicateScopesMergeLaterWinningAndSayTheyDid() throws Exception
    {
        MultiPropertiesConfig mpc = read( xml(
            "  <default-config><property name=\"maxPoolSize\">1</property></default-config>\n" +
            "  <default-config><property name=\"maxPoolSize\">2</property></default-config>\n" +
            "  <named-config name=\"X\"><property name=\"minPoolSize\">3</property></named-config>\n" +
            "  <named-config name=\"X\"><property name=\"minPoolSize\">4</property></named-config>" ) );

        assertEquals( "2", propsOf( mpc ).getProperty( "c3p0.maxPoolSize" ) );
        assertEquals( "4", propsOf( mpc ).getProperty( "c3p0.named-configs.X.minPoolSize" ) );
        assertTrue( warns( mpc, "'default-config' elements" ) );
        assertTrue( warns( mpc, "name 'X' appears more than once" ) );
    }

    /**
     *  A misplaced element is REPORTED, not merely skipped. Being silently ignored is how a
     *  nesting mistake becomes a puzzle, and a count is cheap enough to buy the ability to say
     *  "you have one of these where it will never be seen".
     */
    public void testAMisplacedElementIsReportedRatherThanSilentlySkipped() throws Exception
    {
        MultiPropertiesConfig mpc = read( xml(
            "  <default-config>\n" +
            "    <property name=\"maxPoolSize\">30</property>\n" +
            "    <wrapper>\n" +
            "      <user-overrides user=\"steve\"><property name=\"maxPoolSize\">15</property></user-overrides>\n" +
            "    </wrapper>\n" +
            "  </default-config>" ) );

        assertEquals( "30", propsOf( mpc ).getProperty( "c3p0.maxPoolSize" ) );
        assertNull( "a buried user-override contributes nothing",
                    propsOf( mpc ).getProperty( "c3p0.user-overrides.steve.maxPoolSize" ) );
        assertTrue( warns( mpc, "'user-overrides' element(s) are not" ) );
    }

    public void testANamedConfigNotDirectlyBeneathTheRootIsReported() throws Exception
    {
        MultiPropertiesConfig mpc = read( xml(
            "  <default-config>\n" +
            "    <named-config name=\"buried\"><property name=\"maxPoolSize\">9</property></named-config>\n" +
            "  </default-config>" ) );

        assertNull( propsOf( mpc ).getProperty( "c3p0.named-configs.buried.maxPoolSize" ) );
        assertTrue( warns( mpc, "'named-config' element(s) are not" ) );
    }

    // =================================================== the identifier forms

    public void testTheClassloaderFormReadsAResource() throws Exception
    {
        // shipped under test/resources, so it is on the test classpath
        Properties p = propsOf( read( "c3p0-xml:/com/mchange/v2/c3p0/test/cfgxml/c3p0-config-probe.xml" ) );

        assertEquals( "42", p.getProperty( "c3p0.maxPoolSize" ) );
        assertEquals( "from-probe", p.getProperty( "c3p0.extensions.probeKey" ) );
    }

    public void testAnAbsentClassloaderResourceIsOrdinaryAbsence() throws Exception
    {
        MultiPropertiesConfig mpc = read( "c3p0-xml:/no/such/c3p0-config.xml" );
        assertTrue( propsOf( mpc ).isEmpty() );
    }

    public void testASlashAloneIsRefused() throws Exception
    {
        MultiPropertiesConfig mpc = read( "c3p0-xml:/" );
        assertTrue( warns( mpc, "names no resource" ) );
    }

    /**
     *  A query string on the classloader form would be read as part of the resource path, and the
     *  resource would simply not be found — a directive that silently does nothing. Refuse it and
     *  point at the form that does support directives.
     */
    public void testAQueryStringOnTheClassloaderFormIsRefused() throws Exception
    {
        MultiPropertiesConfig mpc = read( "c3p0-xml:/com/mchange/v2/cfg/junit/c3p0-config-probe.xml?required=true" );

        assertTrue( propsOf( mpc ).isEmpty() );
        assertTrue( warns( mpc, "takes no query string" ) );
    }

    public void testAnIdentifierThatIsNeitherFormIsRefused() throws Exception
    {
        MultiPropertiesConfig mpc = read( "c3p0-xml:relative/path.xml" );
        assertTrue( warns( mpc, "must be either a classloader resource path" ) );
    }

    // =================================================== file: directives still apply

    public void testRequiredTrueOnAnAbsentFileVetoes() throws Exception
    {
        String absent = "c3p0-xml:" + dir.resolve( "definitely-absent.xml" ).toUri() + "?required=true";

        String msg = assertVetoed( absent );
        assertTrue( "the veto should name the identifier: " + msg, msg.contains( "definitely-absent" ) );
    }

    public void testAnAbsentFileWithoutRequiredIsSkipped() throws Exception
    {
        String absent = "c3p0-xml:" + dir.resolve( "definitely-absent.xml" ).toUri();
        assertTrue( propsOf( read( absent ) ).isEmpty() );
    }

    public void testUserOnlyPermissionsAreEnforcedOnTheFileForm() throws Exception
    {
        if ( !posixSupported() ) return;

        String secure = xml( "  <default-config><property name=\"maxPoolSize\">30</property></default-config>",
                             "permissions=useronly" );
        assertEquals( "a 0600 document passes", "30", propsOf( read( secure ) ).getProperty( "c3p0.maxPoolSize" ) );

        // now the same document, world-readable
        Path exposed = Files.createTempFile( dir, "c3p0-exposed-", ".xml" );
        PrintWriter pw = new PrintWriter( exposed.toFile(), "UTF-8" );
        try { pw.println( "<c3p0-config><default-config><property name=\"maxPoolSize\">30</property></default-config></c3p0-config>" ); }
        finally { pw.close(); }
        Files.setPosixFilePermissions( exposed, PosixFilePermissions.fromString( "rw-r--r--" ) );

        String msg = assertVetoed( "c3p0-xml:" + exposed.toUri() + "?permissions=useronly" );
        assertTrue( "the veto should still be about permissions: " + msg, msg.contains( "permissions" ) );
    }

    /** An unsupported query key is a veto, not a silently skipped directive — as for file:. */
    public void testAMisspelledQueryKeyIsVetoed() throws Exception
    {
        String id = xml( "  <default-config><property name=\"maxPoolSize\">30</property></default-config>",
                         "Permissions=useronly" );
        assertVetoed( id );
    }

    // ========================================= a declared named scope leaves a mark

    private final static String X = "c3p0.named-configs.X";

    /**
     *  Read from the library rather than written out, so renaming it cannot leave these cases
     *  asserting a string nothing produces any more. Through a method, not a constant, because a
     *  constant would be inlined into this class file and go on agreeing with itself.
     */
    private final static String MARK = CfgInternals.potentiallyEmptyNamedScopeMarkerValue();

    /**
     *  A named config declared with nothing in it still says it exists.
     *
     *  <p>c3p0 behaves differently for a scope declared empty than for one never declared — the
     *  first falls through to lower-precedence config, the second reverts to the default scope —
     *  and a scope with no properties contributes no keys, so without a mark the two would be
     *  indistinguishable. The bare scope key carries it, because a prefix can never collide with
     *  a property.</p>
     */
    public void testAnEmptyNamedScopeIsStillDeclared() throws Exception
    {
        Properties p = propsOf( read( xml( "  <named-config name=\"X\"/>" ) ) );

        assertEquals( "the declaration should be the one and only key, got " + p, 1, p.size() );
        assertEquals( "and its value the marker, which names itself in a config dump rather than " +
                      "sitting there as a mystery empty string", MARK, p.getProperty( X ) );
    }

    /**
     *  A populated scope carries NO mark, because its own keys already make it visible.
     *
     *  <p>The mark used to be unconditional. It is now written only where a scope would otherwise
     *  leave no trace at all, which is both the narrower claim and the one that matches what a
     *  deployment writes by hand: nobody sets the base key of a scope they are giving properties
     *  to.</p>
     */
    public void testAPopulatedNamedScopeIsNotMarked() throws Exception
    {
        Properties p = propsOf( read( xml(
            "  <named-config name=\"X\"><property name=\"maxPoolSize\">30</property></named-config>" ) ) );

        assertNull( "a scope with properties needs no mark to be seen", p.getProperty( X ) );
        assertEquals( "30", p.getProperty( X + ".maxPoolSize" ) );
        assertEquals( "the property and nothing else, got " + p, 1, p.size() );
    }

    /**
     *  Nor when all it holds is a user override, which lives a level deeper but is still a key
     *  under the scope's prefix &mdash; so the scope is visible without a mark.
     */
    public void testANamedScopeHoldingOnlyAUserOverrideIsNotMarked() throws Exception
    {
        Properties p = propsOf( read( xml(
            "  <named-config name=\"X\">\n" +
            "    <user-overrides user=\"steve\"><property name=\"maxPoolSize\">5</property></user-overrides>\n" +
            "  </named-config>" ) ) );

        assertNull( "the override key already declares the scope", p.getProperty( X ) );
        assertEquals( "5", p.getProperty( X + ".user-overrides.steve.maxPoolSize" ) );
        assertEquals( "the override and nothing else, got " + p, 1, p.size() );
    }

    /**
     *  The four states a caller has to tell apart, read through the by-prefix lookup that c3p0
     *  will use, with the XML layered under properties-style config so the awkward state is
     *  present too.
     *
     *  <p>Z is the state that forbids the obvious shorthand. A scope declared only in
     *  properties-style configuration carries no mark, because nothing writes one there, yet it is
     *  plainly declared. So "declared" has to remain {@code size() > 0}; {@code containsKey(pfx)}
     *  answers the narrower question of whether the scope was declared <i>empty</i>.</p>
     *
     *  <p>Note that Y and Z are now indistinguishable, which is the point of marking only where a
     *  mark is needed: both are scopes carrying properties, and it no longer matters which format
     *  declared them. The mark is reserved for X, the one state that would otherwise be invisible.</p>
     */
    public void testTheFourStatesOfANamedScope() throws Exception
    {
        Properties fromProps = new Properties();
        fromProps.setProperty( "c3p0.named-configs.Z.maxPoolSize", "9" );

        MultiPropertiesConfig mpc = MConfig.combine( new MultiPropertiesConfig[] {
            read( xml( "  <named-config name=\"X\"/>\n" +
                       "  <named-config name=\"Y\"><property name=\"maxPoolSize\">7</property></named-config>" ) ),
            MultiPropertiesConfig.fromProperties( "/from.properties", fromProps )
        } );

        assertScope( mpc, "X", 1, true,  false );   // declared empty, by XML: the only marked state
        assertScope( mpc, "Y", 1, false, true  );   // declared with properties, by XML: no mark
        assertScope( mpc, "Z", 1, false, true  );   // declared by properties alone: no mark
        assertScope( mpc, "W", 0, false, false );   // never declared
    }

    /**
     *  @param marked   containsKey(pfx) — was the scope declared empty somewhere that marks?
     *  @param subkeys  does it carry properties?
     */
    private static void assertScope( MultiPropertiesConfig mpc, String name,
                                     int size, boolean marked, boolean subkeys )
    {
        String     pfx   = "c3p0.named-configs." + name;
        Properties under = mpc.getPropertiesByPrefix( pfx );

        assertEquals( name + ": " + under, size, under.size() );
        assertEquals( name + ": marked? " + under, marked, under.containsKey( pfx ) );
        assertEquals( name + ": subkeys? " + under,
                      subkeys, under.size() > ( under.containsKey( pfx ) ? 1 : 0 ) );
        assertEquals( name + ": declared? " + under, marked || subkeys, under.size() > 0 );
    }

    /** The mark passes through combine() unaltered, which is where every read of it happens. */
    public void testTheMarkSurvivesCombine() throws Exception
    {
        MultiPropertiesConfig combined = MConfig.combine( new MultiPropertiesConfig[] {
            read( xml( "  <named-config name=\"X\"/>" ) ) } );

        assertEquals( MARK, combined.getProperty( X ) );
        assertTrue( combined.getPropertiesByPrefix( X ).containsKey( X ) );
    }

    /**
     *  A scope name containing '.' is read, not refused.
     *
     *  <p>0.15.0 briefly refused it, on the grounds that the flat namespace could not hold it
     *  unambiguously. That was a new restriction rather than a reproduced one: <b>c3p0-0.14.2
     *  accepted a dotted scope name in XML</b>, because it read XML into a bean keyed by the
     *  {@code name} attribute and never split a flat key. Refusing it regressed a configuration
     *  that had worked for years, so the refusal is gone.</p>
     *
     *  <p>The ambiguity that motivated it is real but confined to the declaration marker, and is
     *  resolved in favour of the declaration. What a dotted name does and does not reach &mdash;
     *  its own properties, extensions and user overrides, and not those of a scope sharing its
     *  prefix &mdash; is pinned by
     *  {@link com.mchange.v2.c3p0.cfg.NamedScopeShadowingJUnitTestCase}, which replaced the case
     *  that used to assert the refusal here.</p>
     */
    public void testADottedScopeNameIsRead() throws Exception
    {
        MultiPropertiesConfig mpc = read( xml(
            "  <named-config name=\"a.b\"><property name=\"maxPoolSize\">5</property></named-config>" ) );

        Properties p = propsOf( mpc );
        assertEquals( "the dotted scope's property must arrive under its whole name, got " + p,
                      "5", p.getProperty( "c3p0.named-configs.a.b.maxPoolSize" ) );
        assertEquals( "and nothing else, got " + p, 1, p.size() );
        assertFalse( "a name c3p0-0.14.2 accepted must draw no complaint about containing a '.'",
                     warns( mpc, "'a.b'", "may not contain" ) );
    }


    /**
     *  No mark for user overrides or extensions, deliberately. Declared-and-empty is
     *  behaviorally the same as undeclared for both — c3p0 needs only to see that there are no
     *  overrides, or no extensions — and every mark is a key that every future reader of the
     *  properties view has to know to skip.
     *
     *  <p>Which settles a question the narrower marking rule raises: an EMPTY
     *  {@code <user-overrides>} or {@code <extensions>} is not content. Neither writes a key, so
     *  the enclosing scope still has nothing under its prefix and is still marked — the decision
     *  follows what landed, not what elements were present.</p>
     */
    public void testUserOverridesAndExtensionsAreNotMarked() throws Exception
    {
        Properties p = propsOf( read( xml(
            "  <default-config>\n" +
            "    <user-overrides user=\"steve\"/>\n" +
            "    <extensions/>\n" +
            "  </default-config>\n" +
            "  <named-config name=\"X\">\n" +
            "    <user-overrides user=\"steve\"/>\n" +
            "    <extensions/>\n" +
            "  </named-config>" ) ) );

        assertNull( p.getProperty( "c3p0.user-overrides.steve" ) );
        assertNull( p.getProperty( "c3p0.extensions" ) );
        assertNull( p.getProperty( X + ".user-overrides.steve" ) );
        assertNull( p.getProperty( X + ".extensions" ) );

        assertEquals( "only the named config's own declaration should be present, got " + p,
                      1, p.size() );
        assertEquals( MARK, p.getProperty( X ) );
    }
}
