package com.mchange.v2.c3p0.test.cfgcompat;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 *  Runs {@link ConfigOutcomeProbe} once per scenario, each in its own JVM, against whatever
 *  classpath it is handed.
 *
 *  <p>A fresh JVM per scenario is not a convenience. c3p0 settles its configuration once per
 *  process -- in 0.14.2 by latching a C3P0ConfigFinder during class initialization, in the
 *  current build by reading a cached resource config and sealing System properties -- so a second
 *  scenario in the same JVM would be answered from the first one's configuration.</p>
 *
 *  <p>The child inherits none of this JVM's System properties and none of its classpath: the
 *  classpath is exactly the scenario's resource directory followed by the target build, so that
 *  the same code can be pointed at c3p0-0.14.2 or at the current tree and the only difference
 *  between the two runs is which jars answered.</p>
 */
public final class ConfigOutcomeRunner
{
    /** Generous: a child JVM reads configuration from scratch and may log about it at length. */
    private final static int TIMEOUT_SECONDS = 120;

    private final List<String> targetClasspath;
    private final File workDir;

    /**
     *  @param targetClasspath the build under test -- c3p0 plus mchange-commons-java plus
     *         whatever else it needs, and the directory holding the compiled probe
     *  @param workDir where scenario resource directories are written
     */
    public ConfigOutcomeRunner( List<String> targetClasspath, File workDir )
    {
        this.targetClasspath = new ArrayList<String>( targetClasspath );
        this.workDir         = workDir;
    }

    /** The probe's answers for one scenario, in the canonical order the probe emitted them. */
    public List<String> outcomes( ConfigScenario scenario ) throws Exception
    {
        File dir = new File( workDir, scenario.name );
        List<String> materializedSysprops = scenario.materialize( dir );

        List<String> command = new ArrayList<String>();
        command.add( new File( new File( System.getProperty( "java.home" ), "bin" ), "java" ).getAbsolutePath() );
        command.add( "-cp" );
        command.add( classpath( dir ) );

        // force a logging backend that cannot complain its way out of initializing, and keep the
        // child quiet enough that its answers are findable
        command.add( "-Dcom.mchange.v2.log.MLog=com.mchange.v2.log.jdk14logging.Jdk14MLog" );
        command.add( "-Dcom.mchange.v2.log.FallbackMLog.DEFAULT_CUTOFF_LEVEL=OFF" );
        command.addAll( scenario.syspropArgs() );
        command.addAll( materializedSysprops );   // e.g. -Dcom.mchange.v2.c3p0.cfg.xml=<path written just now>

        command.add( ConfigOutcomeProbe.class.getName() );
        command.add( scenario.name );
        command.addAll( scenario.namedScopes );

        ProcessBuilder pb = new ProcessBuilder( command );
        pb.redirectErrorStream( true );
        Process p = pb.start();

        List<String> outcomes = new ArrayList<String>();
        StringBuilder all = new StringBuilder();
        BufferedReader br = new BufferedReader( new InputStreamReader( p.getInputStream(), Charset.forName( "UTF-8" ) ) );
        try
        {
            String line;
            while ( ( line = br.readLine() ) != null )
            {
                all.append( line ).append( '\n' );
                if ( line.startsWith( ConfigOutcomeProbe.OUT_PFX ) )
                    outcomes.add( line.substring( ConfigOutcomeProbe.OUT_PFX.length() ) );
            }
        }
        finally { br.close(); }

        if (! p.waitFor( TIMEOUT_SECONDS, TimeUnit.SECONDS ) )
        {
            p.destroyForcibly();
            throw new Exception( "Scenario '" + scenario.name + "' did not finish within " +
                                 TIMEOUT_SECONDS + " seconds. Output so far:\n" + all );
        }

        // a probe that answers nothing would make every comparison vacuously true
        if ( outcomes.isEmpty() )
            throw new Exception( "Scenario '" + scenario.name + "' produced no answers. Child output:\n" + all );

        return outcomes;
    }

    private String classpath( File scenarioDir )
    {
        StringBuilder sb = new StringBuilder( scenarioDir.getAbsolutePath() );
        for ( String entry : targetClasspath )
            sb.append( File.pathSeparatorChar ).append( entry );
        return sb.toString();
    }

    /**
     *  Resources that a scenario supplies and so must not arrive from anywhere else.
     *
     *  <p><code>c3p0-default.properties</code> is deliberately NOT here: c3p0 ships one inside
     *  its own jar, the 0.14.2 baseline was captured with it present, and it carries only
     *  <code>com.mchange.v2.naming.*</code> whitelists that no scenario asks about. Dropping it
     *  would make the two runs differ by something neither run is about.</p>
     */
    private final static String[] SCENARIO_OWNED_RESOURCES =
    { "c3p0.properties", "c3p0-config.xml", "application.conf", "application.json",
      "application.properties", "c3p0.conf", "c3p0.json", "mchange-commons.properties",
      "mchange-log.properties" };

    /**
     *  This JVM's classpath with any entry that carries configuration of its own removed.
     *
     *  <p>Necessary because the test module's own classpath includes
     *  <code>test/resources-local</code>, which holds a real c3p0.properties and c3p0-config.xml
     *  for the database tests. Inherited into a child, those would answer questions the scenario
     *  meant to answer, and every comparison would be against a mixture.</p>
     *
     *  @param dropped if non-null, receives the entries removed, for a failure message to name
     */
    public static List<String> sanitizedCurrentClasspath( List<String> dropped )
    {
        List<String> out = new ArrayList<String>();
        for ( String entry : System.getProperty( "java.class.path" ).split( java.util.regex.Pattern.quote( File.pathSeparator ) ) )
        {
            if ( carriesConfiguration( new File( entry ) ) )
            {
                if ( dropped != null ) dropped.add( entry );
            }
            else out.add( entry );
        }
        return out;
    }

    private static boolean carriesConfiguration( File entry )
    {
        if ( entry.isDirectory() )
        {
            for ( String rsrc : SCENARIO_OWNED_RESOURCES )
                if ( new File( entry, rsrc ).exists() ) return true;
            return false;
        }
        if (! entry.isFile() ) return false;

        try
        {
            java.util.zip.ZipFile zf = new java.util.zip.ZipFile( entry );
            try
            {
                for ( String rsrc : SCENARIO_OWNED_RESOURCES )
                    if ( zf.getEntry( rsrc ) != null ) return true;
                return false;
            }
            finally { zf.close(); }
        }
        catch ( Exception e )
        { return false; } // not a zip, or unreadable: it cannot be shadowing a resource either
    }

    // ------------------------------------------------------------------ baseline capture

    /**
     *  Captures a baseline, which is a maintainer's deliberate act rather than part of any test
     *  run: <code>java ... ConfigOutcomeRunner &lt;outFile&gt; &lt;workDir&gt; &lt;cp entry&gt;...</code>
     *
     *  <p>The point of writing the file rather than comparing live is that the comparison then
     *  needs neither the old jars nor a network. The file is the record of what 0.14.2 did, and
     *  it should only ever change when somebody re-captures it on purpose.</p>
     */
    public static void main( String[] argv ) throws Exception
    {
        if ( argv.length < 3 )
        {
            System.err.println( "usage: ConfigOutcomeRunner <outFile> <workDir> <classpath entry>..." );
            System.exit( 2 );
        }

        File out     = new File( argv[0] );
        File workDir = new File( argv[1] );
        List<String> cp = new ArrayList<String>();
        for ( int i = 2; i < argv.length; ++i ) cp.add( argv[i] );

        ConfigOutcomeRunner runner = new ConfigOutcomeRunner( cp, workDir );
        List<ConfigScenario> scenarios = ConfigScenario.all();

        Map<String,List<String>> captured = new LinkedHashMap<String,List<String>>();
        long started = System.currentTimeMillis();
        for ( int i = 0, len = scenarios.size(); i < len; ++i )
        {
            ConfigScenario scenario = scenarios.get( i );
            captured.put( scenario.name, runner.outcomes( scenario ) );
            System.err.println( "[" + (i+1) + "/" + len + "] " + scenario.name );
        }

        // gzipped: the uncompressed capture is ~4MB, and test/resources is published into the
        // c3p0-test jar. 190KB of gzip costs one GZIPOutputStream and saves shipping the rest.
        Writer w = new OutputStreamWriter(
            new java.util.zip.GZIPOutputStream( new FileOutputStream( out ) ), "UTF-8" );
        try
        {
            w.write( "# c3p0 configuration outcomes, captured from a baseline build.\n" );
            w.write( "# Generated by ConfigOutcomeRunner.main; do not hand-edit.\n" );
            w.write( "# scenarios: " + scenarios.size() + "\n" );
            for ( Map.Entry<String,List<String>> entry : captured.entrySet() )
                for ( String line : entry.getValue() )
                    w.write( line + "\n" );
        }
        finally { w.close(); }

        System.err.println( "Captured " + scenarios.size() + " scenarios to " + out +
                            " in " + ((System.currentTimeMillis() - started)/1000) + "s" );
    }
}
