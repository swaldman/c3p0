package com.mchange.v2.c3p0.cfg;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 *  Pins the configuration precedence order documented under
 *  <i>Precedence of Configuration Settings</i> in c3p0's manual.
 *
 *  <h3>Why this is a harness and not a JUnit test</h3>
 *
 *  <p>Two reasons, either of which alone would be enough.</p>
 *
 *  <ul>
 *    <li><b>The sources are classloader resources, and <code>MConfig</code> offers no
 *        <code>ClassLoader</code> parameter.</b> There is no way to hand it a synthetic
 *        <code>c3p0.properties</code> or <code>application.conf</code> in process. The only
 *        lever is the CLASSPATH of a JVM, so each data point needs its own JVM with a
 *        synthetic directory ahead of everything else.</li>
 *    <li><b><code>C3P0Config</code> resolves configuration once, during class
 *        initialization.</b> Even with injectable resources, a single JVM could answer only
 *        one question.</li>
 *  </ul>
 *
 *  <p>It also deliberately shadows c3p0's own <code>c3p0-default.properties</code> and the
 *  test module's <code>c3p0.properties</code>, which would be hostile inside a shared test
 *  run, and it needs typesafe-config on the child CLASSPATH &mdash; a dependency the
 *  published test module should not carry.</p>
 *
 *  <h3>What the documented order actually comes from</h3>
 *
 *  <p>Not, for the paths that matter most, from
 *  <code>resources/mchange-config-resource-paths.txt</code>. c3p0 passes explicit arrays to
 *  <code>MConfig.WithTraditionalDefaultSources.readCachedResourceConfig</code> in
 *  <code>C3P0Config.findLibraryMultiPropertiesConfig()</code>, and its
 *  <code>preempts</code> places <code>/c3p0.properties</code> <i>after</i> the
 *  <code>hocon:</code> specification &mdash; the reverse of what the resource-paths file
 *  declares. A path named twice collapses to its highest-preference position, so the
 *  preempting order is the one that survives, and <code>c3p0.properties</code> outranks
 *  <code>c3p0.conf</code>. The resource-paths file is misleading on exactly this point; this
 *  harness is the record of which one is true.</p>
 *
 *  <h3>Scope</h3>
 *
 *  <p>The within-HOCON ranking (<code>.conf</code> over <code>.json</code> over
 *  <code>.properties</code>, and <code>c3p0</code> over <code>application</code> over
 *  <code>reference</code>) is typesafe-config's <code>parseResourcesAnySyntax</code> and
 *  fallback contract, not c3p0's. It is pinned here because the manual asserts it, but a
 *  failure there means typesafe-config changed, not that c3p0 broke.</p>
 *
 *  <p>Exits nonzero, reporting every scenario that failed, so it is usable as a regression
 *  check. Takes two arguments: the CLASSPATH for child JVMs <i>with</i> typesafe-config, and
 *  the same CLASSPATH <i>without</i> it. They must differ &mdash; typesafe-config arrives as
 *  an optional dependency of mchange-commons-java, so the no-HOCON regime is produced by
 *  subtracting it, and a subtraction that matched nothing would silently turn the no-HOCON
 *  scenario into a duplicate of the other one.</p>
 */
public class ConfigPrecedenceHarness
{
    private final static String PROBE_KEY = "c3p0." + ConfigPrecedenceProbe.PROBE_PROPERTY;

    /** c3p0's compiled-in default for maxPoolSize; what we see when no source supplies one. */
    private final static String HARDCODED_DEFAULT = "15";

    private final static String JAVA =
        System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";

    private final String withHoconClasspath;
    private final String withoutHoconClasspath;
    private final Path   dir;

    private final List<String> failures = new ArrayList<String>();
    private int scenarios = 0;

    public static void main( String[] argv ) throws Exception
    {
        if ( argv.length < 2 )
        {
            System.err.println( "Usage: ConfigPrecedenceHarness <withHoconClasspath> <withoutHoconClasspath>" );
            System.exit( 2 );
        }
        String with    = argv[0];
        String without = argv[1];

        Path dir = Files.createTempDirectory( "c3p0-precedence-" );
        ConfigPrecedenceHarness h = new ConfigPrecedenceHarness( with, without, dir );
        try     { h.run(); }
        finally { deleteRecursively( dir ); }
    }

    private ConfigPrecedenceHarness( String withHoconClasspath, String withoutHoconClasspath, Path dir )
    {
        this.withHoconClasspath    = withHoconClasspath;
        this.withoutHoconClasspath = withoutHoconClasspath;
        this.dir                   = dir;
    }

    private void run() throws Exception
    {
        // If these are equal the subtraction matched nothing and the two regimes would be
        // the same test run twice. That is a harness bug, not a c3p0 result; say so.
        if ( withHoconClasspath.equals( withoutHoconClasspath ) )
        {
            System.out.println( "HARNESS BROKEN -- the with- and without-typesafe-config CLASSPATHs are identical," );
            System.out.println( "so the no-HOCON regime cannot be exercised. Check how the caller subtracts it." );
            System.exit( 2 );
        }

        System.out.println( "Synthetic resource directory: " + dir );
        System.out.println();

        rankingWithHocon();
        rankingWithoutHocon();
        systemPropertiesOutrankEveryFile();
        xmlDefaultConfigOutranksEveryFile();
        namedConfigPrecedence();
        aCollapsedHoconBlobIsSurvivable();
        pathFileDispatch();

        System.out.println();
        if ( failures.isEmpty() )
        {
            System.out.println( "PASSED -- " + scenarios + " scenarios." );
            System.exit( 0 );
        }
        else
        {
            System.out.println( "FAILED -- " + failures.size() + " of " + scenarios + " scenarios:" );
            for ( String f : failures ) System.out.println( "  * " + f );
            System.exit( 1 );
        }
    }

    // ----------------------------------------------------------------- scenarios

    /**
     *  The whole documented ladder, ranked by successive elimination: lay every source down
     *  with a distinct value, ask which won, blank that source, ask again.
     */
    private void rankingWithHocon() throws Exception
    {
        List<String> expected = Arrays.asList(
            "c3p0.properties",
            "c3p0.conf", "c3p0.json",
            "application.conf", "application.json", "application.properties",
            "reference.conf", "reference.json", "reference.properties",
            "c3p0-default.properties", "mchange-log.properties", "mchange-commons.properties"
        );
        check( "ranking of every configuration file, typesafe-config present",
               expected, rank( allSources(), true ) );
    }

    /**
     *  With typesafe-config absent the <code>hocon:</code> path contributes nothing, every
     *  conditional entry in the manual drops out, and what remains must still be in the
     *  documented order. This is what makes listing <code>c3p0.properties</code> exactly once
     *  correct under both regimes.
     */
    private void rankingWithoutHocon() throws Exception
    {
        List<String> expected = Arrays.asList(
            "c3p0.properties", "c3p0-default.properties",
            "mchange-log.properties", "mchange-commons.properties"
        );
        check( "ranking of every configuration file, typesafe-config ABSENT",
               expected, rank( allSources(), false ) );
    }

    /** System properties sit above every file. */
    private void systemPropertiesOutrankEveryFile() throws Exception
    {
        clean();
        write( "c3p0.properties", PROBE_KEY + "=111" );
        write( "c3p0.conf",       PROBE_KEY + "=333" );
        check( "a system property outranks c3p0.properties",
               "999", ask( plain(), true, "-D" + PROBE_KEY + "=999" ) );
    }

    /**
     *  The default-config of a <code>c3p0-config.xml</code> sits above every properties-format
     *  source and below system properties.
     */
    private void xmlDefaultConfigOutranksEveryFile() throws Exception
    {
        clean();
        write( "c3p0.properties", PROBE_KEY + "=111" );
        writeXml( "<c3p0-config>\n  <default-config>\n" +
                  "    <property name=\"" + ConfigPrecedenceProbe.PROBE_PROPERTY + "\">222</property>\n" +
                  "  </default-config>\n</c3p0-config>" );

        check( "c3p0-config.xml default-config outranks c3p0.properties",
               "222", ask( plain(), true ) );
        check( "a system property outranks the c3p0-config.xml default-config",
               "999", ask( plain(), true, "-D" + PROBE_KEY + "=999" ) );
    }

    /**
     *  Named configurations are consulted before the default configuration is reached at all,
     *  so any named-config value outranks a plain system property. Within the named scope, an
     *  XML <code>named-config</code> is merged over a properties-style one.
     */
    private void namedConfigPrecedence() throws Exception
    {
        String namedPfx = "c3p0.named-configs.myconf.";

        clean();
        write( "c3p0.properties", namedPfx + ConfigPrecedenceProbe.PROBE_PROPERTY + "=111" );
        check( "a properties-style named config outranks a plain system property",
               "111", ask( named("myconf"), true, "-D" + PROBE_KEY + "=999" ) );

        // ... while the default config, which the named scope falls through to, still sees it.
        check( "the same system property still governs the default config",
               "999", ask( plain(), true, "-D" + PROBE_KEY + "=999" ) );

        writeXml( "<c3p0-config>\n  <named-config name=\"myconf\">\n" +
                  "    <property name=\"" + ConfigPrecedenceProbe.PROBE_PROPERTY + "\">222</property>\n" +
                  "  </named-config>\n</c3p0-config>" );
        check( "an XML named-config outranks a properties-style named config",
               "222", ask( named("myconf"), true, "-D" + PROBE_KEY + "=999" ) );

        // A named-config key given AS a system property: system properties are the top
        // resource path, so it wins within the named scope too.
        clean();
        write( "c3p0.properties", namedPfx + ConfigPrecedenceProbe.PROBE_PROPERTY + "=111" );
        check( "a named-config key set as a system property outranks one in c3p0.properties",
               "777", ask( named("myconf"), true,
                           "-D" + namedPfx + ConfigPrecedenceProbe.PROBE_PROPERTY + "=777" ) );

        clean();
        write( "c3p0.properties", namedPfx + ConfigPrecedenceProbe.PROBE_PROPERTY + "=111" );
        write( "c3p0.conf",       namedPfx + ConfigPrecedenceProbe.PROBE_PROPERTY + "=333" );
        check( "named-config values follow the same source ordering as plain values",
               "111", ask( named("myconf"), true ) );

        // A named config nobody declared falls through to the default config, with a warning.
        clean();
        write( "c3p0.properties", PROBE_KEY + "=111" );
        check( "an undeclared named config falls through to the default config",
               "111", ask( named("nosuchconfig"), true ) );
    }

    /**
     *  One unresolvable substitution anywhere in the HOCON group takes the whole blob down --
     *  it is a single resource path, merged and resolved as a unit. c3p0 must survive that
     *  with the remaining sources intact, rather than losing its configuration.
     */
    private void aCollapsedHoconBlobIsSurvivable() throws Exception
    {
        clean();
        write( "c3p0.properties", PROBE_KEY + "=111" );
        write( "application.conf", PROBE_KEY + "=333\nbroken=${nope.not.here}" );

        check( "an unresolvable substitution in application.conf does not take c3p0.properties with it",
               "111", ask( plain(), true ) );

        String paths = ask( new String[] { "paths" }, true );
        if ( paths.indexOf( "hocon:" ) >= 0 )
            fail( "a collapsed HOCON blob should drop out of the resource paths, but they were: " + paths );
        else
            pass( "a collapsed HOCON blob drops out of the resource paths" );
    }

    // ----------------------------------------------------------------- path-file dispatch

    /**
     *  c3p0 no longer ships a /mchange-config-resource-paths.txt. It decides between the
     *  traditional path-file mechanism and its hardcoded source list by asking whether the
     *  path files YIELD PATHS -- deliberately not whether one exists, because a path file that
     *  is empty or wholly commented out exists and yields nothing, and the traditional pathway
     *  then falls back to a hardcoded backstop whose ordering is NOT c3p0's.
     *
     *  <p>That combination was reachable for a while: it put /mchange-commons.properties above
     *  /c3p0-default.properties, so an end-user file could silently replace c3p0's own shipped
     *  securelyStringifiable whitelist. These scenarios are the regression guard. The three
     *  "yields nothing" shapes must be indistinguishable from having no path file at all.</p>
     */
    private void pathFileDispatch() throws Exception
    {
        clean();
        // Every ordinary source present, so no path is dropped merely for being absent and the
        // lists below are comparable.
        write( "mchange-commons.properties", PROBE_KEY + "=1" );
        write( "mchange-log.properties",     PROBE_KEY + "=2" );
        write( "c3p0.properties",            PROBE_KEY + "=3" );

        List<String> noPathFile = resourcePaths();

        writePathFile( "" );
        check( "a zero-byte path file resolves exactly as no path file does",
               noPathFile, resourcePaths() );

        writePathFile( "#\n# every line commented out\n#\n" );
        check( "a wholly commented-out path file resolves exactly as no path file does",
               noPathFile, resourcePaths() );

        // A path file that really does supply a path takes the traditional branch, and the
        // path it supplies has to land where the traditional mechanism puts it: above
        // c3p0-default.properties, below the hocon group.
        write( "my-extra.properties", PROBE_KEY + "=4" );
        writePathFile( "/my-extra.properties\n" );
        List<String> withExtra = resourcePaths();

        if ( !withExtra.contains( "/my-extra.properties" ) )
            fail( "a path file supplying /my-extra.properties should put it in the resolved list, got: " + withExtra );
        else if ( noPathFile.contains( "/my-extra.properties" ) )
            fail( "/my-extra.properties should not appear without a path file naming it" );
        else
        {
            pass( "a path file that supplies a path takes the traditional branch" );

            int extra  = withExtra.indexOf( "/my-extra.properties" );
            int dflt   = withExtra.indexOf( "/c3p0-default.properties" );
            int hocon  = indexOfHocon( withExtra );
            check( "the supplied path outranks c3p0-default.properties", true, extra > dflt );
            check( "...and is outranked by the hocon group", true, hocon > extra );
        }

        // A path file naming its own hocon: specification overlaps c3p0's, which is the
        // legitimate route into ensureHoconInterresolvability. Two hocon paths result, each
        // rewritten to fall back to the other. Each must still END in "/", or system
        // properties would stop being last-wins inside that blob.
        writePathFile( "hocon:/myref,/application,/\n" );
        List<String> twoHocons = resourcePaths();
        int hoconCount = 0;
        boolean allEndInSystemProperties = true;
        for ( String path : twoHocons )
            if ( path.startsWith( "hocon:" ) )
            {
                ++hoconCount;
                if ( !path.endsWith( ",/" ) ) allEndInSystemProperties = false;
            }
        check( "two overlapping hocon specifications yield two hocon paths", 2, hoconCount );
        check( "every rewritten hocon path must still end in system properties: " + twoHocons,
               true, allEndInSystemProperties );
    }

    private void writePathFile( String contents ) throws IOException
    { write( "mchange-config-resource-paths.txt", contents ); }

    private static int indexOfHocon( List<String> paths )
    {
        for ( int i = 0, len = paths.size(); i < len; ++i )
            if ( paths.get(i).startsWith( "hocon:" ) ) return i;
        return -1;
    }

    /** The resolved resource paths, lowest preference first. */
    private List<String> resourcePaths() throws Exception
    {
        String joined = ask( new String[] { "paths" }, true );
        // '|' separated by the probe precisely because hocon identifiers contain commas.
        return Arrays.asList( joined.split( "\\|" ) );
    }

    // ----------------------------------------------------------------- ranking engine

    /** name -> how to write "this source claims value v" */
    private Map<String,String> allSources()
    {
        Map<String,String> out = new LinkedHashMap<String,String>();
        out.put( "mchange-commons.properties", "props" );
        out.put( "mchange-log.properties",     "props" );
        out.put( "c3p0-default.properties",    "props" );
        out.put( "reference.properties",       "props" );
        out.put( "reference.json",             "json"  );
        out.put( "reference.conf",             "props" );
        out.put( "application.properties",     "props" );
        out.put( "application.json",           "json"  );
        out.put( "application.conf",           "props" );
        out.put( "c3p0.properties",            "props" );
        out.put( "c3p0.json",                  "json"  );
        out.put( "c3p0.conf",                  "props" );
        return out;
    }

    /**
     *  Lays every source down with a value that identifies it, then repeatedly asks which won
     *  and blanks it, yielding the full ranking highest-preference first.
     */
    private List<String> rank( Map<String,String> sources, boolean withHocon ) throws Exception
    {
        clean();

        // A distinct numeric value per source, and a way back from value to source name.
        Map<String,String> valueToName = new LinkedHashMap<String,String>();
        int v = 101;
        for ( Map.Entry<String,String> e : sources.entrySet() )
        {
            String value = String.valueOf( v++ );
            valueToName.put( value, e.getKey() );
            writeClaim( e.getKey(), e.getValue(), value );
        }

        List<String> order = new ArrayList<String>();
        for ( int i = 0, len = sources.size(); i < len; ++i )
        {
            String winner = ask( plain(), withHocon );
            if ( HARDCODED_DEFAULT.equals( winner ) ) break;   // nothing left to find

            String name = valueToName.get( winner );
            if ( name == null )
            {
                fail( "ranking produced an unrecognized value '" + winner + "'" );
                break;
            }
            order.add( name );
            blank( name, sources.get( name ) );
        }
        return order;
    }

    // ----------------------------------------------------------------- child JVMs

    private String[] plain()                 { return new String[] { "plain" }; }
    private String[] named( String cfgName ) { return new String[] { "named", cfgName }; }

    /** Forks a JVM with the synthetic directory first on the CLASSPATH and returns its answer. */
    private String ask( String[] query, boolean withHocon, String... jvmArgs ) throws Exception
    {
        // The synthetic directory goes first, so it shadows c3p0's own
        // c3p0-default.properties and the test module's c3p0.properties.
        String cp = dir.toString() + File.pathSeparator +
                    ( withHocon ? withHoconClasspath : withoutHoconClasspath );

        List<String> cmd = new ArrayList<String>();
        cmd.add( JAVA );
        cmd.addAll( Arrays.asList( jvmArgs ) );
        // Keep the child quiet; its logs would otherwise bury the answer.
        cmd.add( "-Dcom.mchange.v2.log.MLog=com.mchange.v2.log.FallbackMLog" );
        cmd.add( "-Dcom.mchange.v2.log.FallbackMLog.DEFAULT_CUTOFF_LEVEL=OFF" );
        cmd.add( "-cp" );
        cmd.add( cp );
        cmd.add( ConfigPrecedenceProbe.class.getName() );
        cmd.addAll( Arrays.asList( query ) );

        ProcessBuilder pb = new ProcessBuilder( cmd );
        pb.redirectErrorStream( false );
        Process p = pb.start();

        String answer = null;
        java.io.BufferedReader br =
            new java.io.BufferedReader( new java.io.InputStreamReader( p.getInputStream() ) );
        try
        {
            for ( String line = br.readLine(); line != null; line = br.readLine() )
                if ( line.startsWith( ConfigPrecedenceProbe.ANSWER_PFX ) )
                    answer = line.substring( ConfigPrecedenceProbe.ANSWER_PFX.length() );
        }
        finally { br.close(); }

        p.waitFor();
        return answer;
    }

    // ----------------------------------------------------------------- synthetic resources

    private void clean() throws IOException
    {
        File[] fs = dir.toFile().listFiles();
        if ( fs != null ) for ( File f : fs ) f.delete();
    }

    private void write( String name, String contents ) throws IOException
    {
        PrintWriter pw = new PrintWriter( dir.resolve( name ).toFile(), "UTF-8" );
        try     { pw.println( contents ); }
        finally { pw.close(); }
    }

    private void writeXml( String contents ) throws IOException
    { write( "c3p0-config.xml", contents ); }

    /** "This source claims <code>value</code> for the probe property." */
    private void writeClaim( String name, String kind, String value ) throws IOException
    {
        if ( "json".equals( kind ) )
            write( name, "{\"c3p0\":{\"" + ConfigPrecedenceProbe.PROBE_PROPERTY + "\":" + value + "}}" );
        else
            write( name, PROBE_KEY + "=" + value );
    }

    /** Present but silent -- not absent, so the resource path stays in play. */
    private void blank( String name, String kind ) throws IOException
    { write( name, "json".equals( kind ) ? "{ }" : "" ); }

    // ----------------------------------------------------------------- reporting

    private void check( String what, Object expected, Object found )
    {
        if ( expected.equals( found ) ) pass( what );
        else fail( what + "\n      expected: " + expected + "\n      found:    " + found );
    }

    private void pass( String what )
    {
        ++scenarios;
        System.out.println( "  ok   " + what );
    }

    private void fail( String what )
    {
        ++scenarios;
        System.out.println( "  FAIL " + what );
        failures.add( what );
    }

    private static void deleteRecursively( Path p )
    {
        File f = p.toFile();
        File[] kids = f.listFiles();
        if ( kids != null ) for ( File k : kids ) deleteRecursively( k.toPath() );
        f.delete();
    }
}
