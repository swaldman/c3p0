package com.mchange.v2.c3p0.test.junit;

import junit.framework.Assert;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 *  Runs a probe class in a child JVM and collects its answers.
 *
 *  <p>Several of c3p0's configuration behaviours are decided ONCE per JVM -- which
 *  C3P0ConfigFinder is installed, whether configuration is frozen, when System properties are
 *  sealed. Those cannot be observed from a single process, because a test can only ever witness
 *  one first load, so each data point gets its own JVM. ConfigPrecedenceHarness forks for the
 *  same reason; this is that pattern, shared.</p>
 *
 *  <p>A probe prints one <code>key=value</code> per answer, each prefixed with
 *  {@link #ANSWER_PFX} so the parent can pick them out of whatever c3p0 logs on the way up, and
 *  reports a thrown exception under the key <code>threw</code>. Answering nothing is a failure:
 *  a silent probe would otherwise make every assertion vacuous, as would a probe
 *  whose logging silently fell back to System.err -- also checked here.</p>
 */
public final class ChildJvmProbeRunner
{
    /** Prefix on every answer, so the parent can find it among the child's log output. */
    public final static String ANSWER_PFX = "@@PROBE@@ ";

    /** Generous: a child JVM initializes c3p0's configuration from scratch. */
    private final static int TIMEOUT_SECONDS = 120;

    /** What MLog prints when its own initialization failed and it gave up on a real backend. */
    private final static String FALLBACK_NOTICE = "fallback to standard error logging";

    /** For probes: print one answer. */
    public static void answer( String key, String value )
    {
        System.out.println( ANSWER_PFX + key + "=" + value );
        System.out.flush();
    }

    public static void answer( String key, int value )
    { answer( key, String.valueOf( value ) ); }

    /**
     *  Runs <code>probeClass</code> with <code>query</code> as its sole argument, in a child JVM
     *  that inherits this one's classpath but NONE of its system properties -- which is the
     *  point: each case controls exactly the configuration it names.
     *
     *  <p>Jdk14MLog is forced, so a probe's own java.util.logging Handler can observe what c3p0
     *  logs. stderr is folded into stdout so that a failure message carries the child's
     *  diagnostics.</p>
     */
    public static Map<String,String> run( Class<?> probeClass, String query, String... jvmArgs ) throws Exception
    {
        List<String> command = new ArrayList<String>();
        command.add( new File( new File( System.getProperty( "java.home" ), "bin" ), "java" ).getAbsolutePath() );
        command.add( "-cp" );
        command.add( System.getProperty( "java.class.path" ) );
        command.add( "-Dcom.mchange.v2.log.MLog=com.mchange.v2.log.jdk14logging.Jdk14MLog" );
        for ( int i = 0; i < jvmArgs.length; ++i )
            command.add( jvmArgs[i] );
        command.add( probeClass.getName() );
        command.add( query );

        ProcessBuilder pb = new ProcessBuilder( command );
        pb.redirectErrorStream( true );
        Process p = pb.start();

        StringBuilder all = new StringBuilder();
        Map<String,String> answers = new HashMap<String,String>();
        BufferedReader br = new BufferedReader( new InputStreamReader( p.getInputStream(), Charset.forName( "UTF-8" ) ) );
        try
        {
            for ( String line = br.readLine(); line != null; line = br.readLine() )
            {
                all.append( line ).append( '\n' );
                if ( line.startsWith( ANSWER_PFX ) )
                {
                    String kv = line.substring( ANSWER_PFX.length() );
                    int eq = kv.indexOf( '=' );
                    if ( eq >= 0 ) answers.put( kv.substring( 0, eq ), kv.substring( eq + 1 ) );
                }
            }
        }
        finally
        { br.close(); }

        if ( ! p.waitFor( TIMEOUT_SECONDS, TimeUnit.SECONDS ) )
        {
            p.destroyForcibly();
            Assert.fail( "probe '" + query + "' did not finish within " + TIMEOUT_SECONDS + "s. Output:\n" + all );
        }

        Assert.assertNull( "probe '" + query + "' threw. Output:\n" + all, answers.get( "threw" ) );
        Assert.assertFalse( "probe '" + query + "' answered nothing. Output:\n" + all, answers.isEmpty() );

        // The failure mode that assertions cannot see. If anything a probe does re-enters MLog
        // before it is up, the NPE is swallowed by MLog's static initializer and logging drops to
        // System.err for the life of the ClassLoader -- while every answer the probe gives still
        // looks right, except the ones that count log records, which silently count zero. So
        // check it here, once, on behalf of every probe.
        Assert.assertEquals( "probe '" + query + "' degraded to MLog's fallback logging, so anything it "
                                 + "reports about what was logged is unreliable. Output:\n" + all,
                             -1, all.indexOf( FALLBACK_NOTICE ) );

        return answers;
    }

    private ChildJvmProbeRunner()
    {}
}
