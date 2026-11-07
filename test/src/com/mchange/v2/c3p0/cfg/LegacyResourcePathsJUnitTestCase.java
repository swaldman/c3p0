package com.mchange.v2.c3p0.cfg;

import junit.framework.TestCase;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import com.mchange.v2.c3p0.test.cfglib.CfgLoaderScenario;
import com.mchange.v2.log.MLog;

/**
 *  The legacy {@code /mchange-config-resource-paths.txt} fallback, and what it can no longer do.
 *
 *  <h3>The hazard</h3>
 *
 *  <p>Before 0.15.0, the config library took its resource-path list from
 *  {@code /mchange-config-resource-paths.txt} or
 *  {@code /com/mchange/v2/cfg/vmConfigResourcePaths.txt}. c3p0 ships neither now, declaring its
 *  sources through {@code mlog-config-resource-paths.txt} instead, but the old mechanism was kept
 *  so that a deployment which had customized one of those files would not break.</p>
 *
 *  <p>That shim turned out to carry more risk than the compatibility it bought. c3p0-0.14.2's jar
 *  still contains such a file, so <b>two c3p0 jars on one classpath</b> — an ordinary packaging
 *  accident — put one there, and the legacy branch composed its path list from a second,
 *  hand-written copy of c3p0's sources. That copy drifted: it still named
 *  {@code c3p0-xml:file:${com.mchange.v2.c3p0.cfg.xml}} after the identifier was renamed to
 *  {@code prop:}, so a stray old jar silently disabled the {@code classloader:} form of that
 *  property and warned, misleadingly, that the value was not an absolute path.</p>
 *
 *  <p>Two changes answer it. The legacy branch is now reached only when no
 *  {@code mlog-config-resource-paths.txt} is found at all — so a deployment that ships one, which
 *  every c3p0 deployment does, cannot be diverted by a stray legacy file. And the hand-written
 *  copy was corrected. These cases pin both, plus the agreement that drifted in the first
 *  place.</p>
 *
 *  <p>Nothing here needs a database.</p>
 */
public class LegacyResourcePathsJUnitTestCase extends TestCase
{
    private final static String LEGACY_RESOURCE  = "mchange-config-resource-paths.txt";
    private final static String DECLARED_RESOURCE = "mlog-config-resource-paths.txt";
    private final static String EXTRAS_RESOURCE  = "mcfg-extra-properties-config-sources.txt";

    private Path dir;

    @Override
    protected void setUp() throws Exception
    { dir = Files.createTempDirectory( "c3p0-legacy-paths-" ).toRealPath(); }

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

    // ------------------------------------------------------------- the shipped artifacts agree

    /**
     *  The build generates c3p0's declared source list twice — as a classpath resource mlog
     *  reads, and as a Java array C3P0Config reads — and the two must say the same thing.
     *
     *  <p>They come from one list in {@code build.mill}, so this is a check that the generator
     *  stays honest rather than a guess about drift.</p>
     */
    public void testTheGeneratedResourceAndTheGeneratedArrayAgree() throws Exception
    {
        List<String> fromResource = readPathList( "/" + DECLARED_RESOURCE );
        List<String> fromArray    = Arrays.asList( CfgInternals.declaredSharedResources() );

        assertFalse( "c3p0 should ship a declared source list", fromResource.isEmpty() );
        assertEquals( "the generated resource and the generated array must name the same sources, in order",
                      fromArray, fromResource );
    }

    /**
     *  And the declared list must still name the sources c3p0's behavior depends on. A list that
     *  silently lost one of these would leave the library reading less configuration than it
     *  documents, which is how the drift this case guards against made itself felt.
     */
    public void testTheDeclaredListNamesTheSourcesThatMatter()
    {
        List<String> declared = Arrays.asList( CfgInternals.declaredSharedResources() );

        for ( String required : new String[] { "/c3p0.properties",
                                               "c3p0-xml:/c3p0-config.xml",
                                               "c3p0-xml:prop:${com.mchange.v2.c3p0.cfg.xml}",
                                               "/",
                                               "SEALED_SYSTEM_PROPERTIES" } )
            assertTrue( "the declared source list must name '" + required + "': " + declared,
                        declared.contains( required ) );
    }

    // ------------------------------------------------------------- the gate

    /**
     *  c3p0 ships its own declaration, so the legacy branch is unreachable for it.
     *
     *  <p>This is what makes a stray 0.14.2 jar harmless: the gate is "was a
     *  {@code mlog-config-resource-paths.txt} found at all", and c3p0's own is always found. A
     *  deployment can therefore acquire a legacy file by accident without its configuration
     *  changing.</p>
     */
    public void testC3P0ShipsADeclarationSoTheLegacyBranchCannotBeReached()
    {
        assertNotNull( "c3p0 ships " + DECLARED_RESOURCE + "; if this is null the legacy branch " +
                       "becomes reachable and a stray old jar could divert configuration",
                       MLog.getConfigResourcePathsTxtPaths() );
    }

    // ------------------------------------------------------------- the legacy branch itself

    /**
     *  Reached on purpose, and required to resolve exactly as the declared list does.
     *
     *  <p>A scenario ClassLoader carries neither of c3p0's generated resources, so the legacy
     *  branch is reachable there: supply a legacy file and it is taken. The two composed path
     *  lists must then agree, and the configuration read through each must agree &mdash; which is
     *  the outcome-level form of "the hand-written copy still matches the generated list", and
     *  the assertion the {@code file:}/{@code prop:} drift would have failed.</p>
     *
     *  <p>The legacy file used here is c3p0-0.14.2's own, verbatim, since that is the one a
     *  deployment acquires by accident.</p>
     */
    public void testTheLegacyBranchResolvesAsTheDeclaredListDoes() throws Exception
    {
        File shared   = writeSharedConfigRoot();
        File legacy   = writeRoot( "legacy", LEGACY_RESOURCE, C3P0_0_14_2_RESOURCE_PATHS );
        File declared = writeRoot( "declared", DECLARED_RESOURCE, joinLines( CfgInternals.declaredSharedResources() ) );

        // the external XML named in its classloader: form -- the case the drift broke
        String before = System.getProperty( XML_KEY );
        System.setProperty( XML_KEY, "classloader:/deep/extern-config.xml" );
        try
        {
            Outcome viaLegacy   = readThrough( legacy, shared );             // no declaration: legacy branch
            Outcome viaDeclared = readThrough( declared, shared );           // declaration present: new branch

            assertTrue( "with no declaration present, the legacy branch should be reachable",
                        viaLegacy.legacyBranchReachable );
            assertTrue( "and taken -- the legacy file's own paths reached the configuration",
                        viaLegacy.readLegacyFilesPaths );
            assertFalse( "with a declaration present, the legacy branch should be unreachable",
                         viaDeclared.legacyBranchReachable );
            assertFalse( "and so the legacy file's paths must not be read",
                         viaDeclared.readLegacyFilesPaths );

            // The legacy branch should resolve every declared source AND the legacy file's own
            // additions -- honoring those is the whole point of the compatibility mechanism. So
            // the claim is that nothing BUT the legacy file's own contribution differs, which is
            // the outcome-level form of "the hand-written copy still matches the generated list".
            List<String> legacyMinusItsOwn = new ArrayList<String>( viaLegacy.paths );
            assertTrue( "the legacy file's own path should have been read, which is also how we " +
                        "know that branch ran: " + viaLegacy.paths,
                        legacyMinusItsOwn.remove( LEGACY_ONLY_PATH ) );

            assertEquals( "apart from the legacy file's own addition, both branches must compose " +
                          "the same resolved source list",
                          viaDeclared.paths, legacyMinusItsOwn );
            assertEquals( "and must read the same value from the classloader-named external XML",
                          viaDeclared.externalXmlMaxPoolSize, viaLegacy.externalXmlMaxPoolSize );
            assertEquals( "which must be the value that XML actually sets",
                          "999", viaLegacy.externalXmlMaxPoolSize );
        }
        finally
        {
            if ( before == null ) System.clearProperty( XML_KEY );
            else System.setProperty( XML_KEY, before );
        }
    }

    /**
     *  And a legacy file alongside a declaration changes nothing &mdash; the stray-jar case,
     *  which is the one a real deployment will meet.
     */
    public void testALegacyFileBesideADeclarationIsInert() throws Exception
    {
        File shared = writeSharedConfigRoot();
        File both   = writeRoot( "both", DECLARED_RESOURCE, joinLines( CfgInternals.declaredSharedResources() ) );
        PrintWriter pw = new PrintWriter( new File( both, LEGACY_RESOURCE ), "UTF-8" );
        try { pw.print( C3P0_0_14_2_RESOURCE_PATHS ); }
        finally { pw.close(); }

        File declaredOnly = writeRoot( "declaredOnly", DECLARED_RESOURCE, joinLines( CfgInternals.declaredSharedResources() ) );

        Outcome withStray = readThrough( both, shared );
        Outcome clean     = readThrough( declaredOnly, shared );

        assertFalse( "a declaration is present, so the legacy branch must not be reachable",
                     withStray.legacyBranchReachable );
        assertFalse( "and must not be taken -- this is the stray-jar case, and the gate is what " +
                     "keeps a packaging accident from changing how configuration resolves",
                     withStray.readLegacyFilesPaths );
        assertEquals( "a stray legacy file must not change the resolved source list",
                      clean.paths, withStray.paths );
        assertEquals( "nor any value read through it",
                      clean.classpathXmlMaxPoolSize, withStray.classpathXmlMaxPoolSize );
    }

    // ------------------------------------------------------------- fixture

    private final static String XML_KEY = "com.mchange.v2.c3p0.cfg.xml";

    /** Not any c3p0 default, so its presence is unambiguous. */
    private final static String LEGACY_MARKER_VALUE = "77";

    /** Named by the legacy file and by nothing c3p0 declares. */
    private final static String LEGACY_ONLY_PATH = "/legacy-marker.properties";

    /** c3p0-0.14.2's own file, verbatim from its jar. */
    private final static String C3P0_0_14_2_RESOURCE_PATHS =
        "#\n" +
        "# note that later files \"shadow\" earlier ones, and that\n" +
        "# the name '/' is reserved as a special token for\n" +
        "# System properties.\n" +
        "#\n" +
        "\n" +
        "/legacy-marker.properties\n" +
        "/mchange-commons.properties\n" +
        "/mchange-log.properties\n" +
        "/c3p0-default.properties\n" +
        "/c3p0.properties\n" +
        "hocon:/reference,/application,/c3p0,/\n" +
        "/\n";

    private static String joinLines( String[] lines )
    {
        StringBuilder sb = new StringBuilder();
        for ( String line : lines ) sb.append( line ).append( '\n' );
        return sb.toString();
    }

    private File writeRoot( String name, String resourceName, String contents ) throws Exception
    {
        File root = new File( dir.toFile(), name );
        assertTrue( root.mkdirs() || root.isDirectory() );
        PrintWriter pw = new PrintWriter( new File( root, resourceName ), "UTF-8" );
        try { pw.print( contents ); }
        finally { pw.close(); }
        return root;
    }

    /**
     *  Everything both branches must see alike: the extras resolver that makes {@code c3p0-xml:}
     *  readable at all, a classpath c3p0-config.xml, a c3p0.properties, and the external XML the
     *  classloader: form names.
     */
    private File writeSharedConfigRoot() throws Exception
    {
        File root = new File( dir.toFile(), "shared" );
        assertTrue( root.mkdirs() || root.isDirectory() );

        write( new File( root, EXTRAS_RESOURCE ), "com.mchange.v2.c3p0.cfg.C3P0ExtraPropertiesConfigSources\n" );
        write( new File( root, "c3p0.properties" ), "c3p0.minPoolSize=7\n" );

        // named ONLY by the legacy file, so reading it is proof the legacy branch ran
        write( new File( root, "legacy-marker.properties" ), "c3p0.acquireIncrement=" + LEGACY_MARKER_VALUE + "\n" );
        write( new File( root, "c3p0-config.xml" ),
               "<c3p0-config><default-config><property name=\"checkoutTimeout\">4321</property>" +
               "</default-config></c3p0-config>\n" );

        File deep = new File( root, "deep" );
        assertTrue( deep.mkdirs() || deep.isDirectory() );
        write( new File( deep, "extern-config.xml" ),
               "<c3p0-config><default-config><property name=\"maxPoolSize\">999</property>" +
               "</default-config></c3p0-config>\n" );

        return root;
    }

    private static void write( File f, String contents ) throws Exception
    {
        PrintWriter pw = new PrintWriter( f, "UTF-8" );
        try { pw.print( contents ); }
        finally { pw.close(); }
    }

    private static List<String> readPathList( String resourcePath ) throws Exception
    {
        InputStream is = LegacyResourcePathsJUnitTestCase.class.getResourceAsStream( resourcePath );
        assertNotNull( "no resource at " + resourcePath, is );
        List<String> out = new ArrayList<String>();
        BufferedReader br = new BufferedReader( new InputStreamReader( is, "UTF-8" ) );
        try
        {
            String line;
            while ( ( line = br.readLine() ) != null )
            {
                String trimmed = line.trim();
                if ( trimmed.length() > 0 && !trimmed.startsWith( "#" ) ) out.add( trimmed );
            }
        }
        finally { br.close(); }
        return out;
    }

    // ------------------------------------------------------------- reading through a scenario

    private final static class Outcome
    {
        final List<String> paths;
        final String       externalXmlMaxPoolSize;
        final String       classpathXmlMaxPoolSize;
        final boolean      legacyBranchReachable; // no declaration found, so a legacy file would govern
        final boolean      readLegacyFilesPaths;   // and its paths actually reached the configuration

        Outcome( List<String> paths, String externalXmlMaxPoolSize, String classpathXmlMaxPoolSize,
                 boolean legacyBranchReachable, boolean readLegacyFilesPaths )
        {
            this.paths                   = paths;
            this.externalXmlMaxPoolSize  = externalXmlMaxPoolSize;
            this.classpathXmlMaxPoolSize = classpathXmlMaxPoolSize;
            this.legacyBranchReachable   = legacyBranchReachable;
            this.readLegacyFilesPaths    = readLegacyFilesPaths;
        }
    }

    /**
     *  Loads c3p0's configuration afresh over the given classpath roots and reports what it read.
     *
     *  <p>A scenario ClassLoader is what makes this possible at all: C3P0Config settles its
     *  configuration once per class initialization, and carries neither of c3p0's generated
     *  resources unless a root supplies one &mdash; so whether the legacy branch is reachable is
     *  a property of the roots rather than of the JVM running the test.</p>
     */
    private Outcome readThrough( File... roots ) throws Exception
    {
        CfgLoaderScenario scenario = CfgLoaderScenario.open( roots );
        try
        {
            Class<?> cfg  = scenario.loadClass( "com.mchange.v2.c3p0.cfg.C3P0Config" );
            Class<?> mpcC = scenario.loadClass( "com.mchange.v2.cfg.MultiPropertiesConfig" );

            Object conf = cfg.getMethod( "getMultiPropertiesConfig" ).invoke( null );
            String[] paths = (String[]) mpcC.getMethod( "getPropertiesResourcePaths" ).invoke( conf );

            Method gupp = cfg.getMethod( "getUnspecifiedUserProperty", String.class, String.class );
            String external  = (String) gupp.invoke( null, "maxPoolSize", null );
            String classpath = (String) gupp.invoke( null, "checkoutTimeout", null );

            // The gate itself, read inside the scenario: C3P0Config takes the legacy branch exactly
            // when no mlog-config-resource-paths.txt was found. Reading the gate rather than
            // guessing from the resolved paths, which cannot distinguish the branches -- both
            // compose the same list when the hand-written copy is correct, which is the point.
            Class<?> mlog = scenario.loadClass( "com.mchange.v2.log.MLog" );
            Object declaredPaths = mlog.getMethod( "getConfigResourcePathsTxtPaths" ).invoke( null );

            // Which branch ran, read structurally rather than from a log. The legacy file names
            // a marker properties resource that c3p0's own declared list does not, and only the
            // legacy branch folds a legacy file's paths into what it reads -- so the marker's
            // value is present exactly when that branch ran.
            //
            // Deliberately NOT read from log output. MLog's backend depends on JVM-global
            // initialization state and test order, and capturing stderr to see the warnings
            // poisoned MLog for later tests in the same JVM -- 32 failures in classes with
            // nothing to do with this one.
            String marker = (String) gupp.invoke( null, "acquireIncrement", null );

            return new Outcome( Arrays.asList( paths ), external, classpath,
                                declaredPaths == null, LEGACY_MARKER_VALUE.equals( marker ) );
        }
        finally { scenario.closeQuietly(); }
    }

}
