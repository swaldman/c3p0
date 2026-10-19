package com.mchange.v2.c3p0.cfg;

import java.util.Properties;

import com.mchange.v2.cfg.MultiPropertiesConfig;

/**
 *  The child half of {@link ConfigPrecedenceHarness}. Answers one question about the
 *  configuration this JVM resolved, on stdout, prefixed so the parent can find it among
 *  whatever c3p0 logs on the way up.
 *
 *  <p>One question per process, because <code>C3P0Config</code> resolves its configuration
 *  once during class initialization. The parent forks a fresh JVM for every data point.</p>
 *
 *  <p>Queries:</p>
 *  <ul>
 *    <li><code>plain</code> &mdash; the resolved value of the property named by the second
 *        argument, in the default (non-named) configuration.</li>
 *    <li><code>named &lt;configName&gt;</code> &mdash; the same property as resolved for a
 *        named configuration.</li>
 *    <li><code>paths</code> &mdash; the resource paths actually in play, in increasing order
 *        of preference.</li>
 *    <li><code>rawAfterPreempt &lt;key&gt; &lt;value&gt;</code> &mdash; refreshes the main
 *        config with a preempt setting <i>key</i> to <i>value</i>, then reports what a direct
 *        read of the MultiPropertiesConfig makes of <i>key</i>. This is the lookup path that
 *        bypasses the bean layer, and so the one where a preempt could once outrank a System
 *        property.</li>
 *  </ul>
 */
public class ConfigPrecedenceProbe
{
    /** Prefix on every answer, so the parent can pick it out of the log noise. */
    public final static String ANSWER_PFX = "@@PROBE@@ ";

    /** The property every scenario contends over. Benign, not "dangerous", settable anywhere. */
    public final static String PROBE_PROPERTY = "maxPoolSize";

    public static void main( String[] argv )
    {
        try
        {
            String query = argv.length > 0 ? argv[0] : "plain";

            if ( "plain".equals( query ) )
                answer( C3P0Config.getUnspecifiedUserProperty( PROBE_PROPERTY, null ) );
            else if ( "named".equals( query ) )
                answer( C3P0Config.getUnspecifiedUserProperty( PROBE_PROPERTY, argv[1] ) );
            else if ( "rawAfterPreempt".equals( query ) )
            {
                Properties preempt = new Properties();
                preempt.setProperty( argv[1], argv[2] );
                C3P0Config.refreshMainConfig(
                    new MultiPropertiesConfig[] { MultiPropertiesConfig.fromProperties( preempt ) },
                    "ConfigPrecedenceProbe rawAfterPreempt" );
                answer( C3P0Config.getMultiPropertiesConfig().getProperty( argv[1] ) );
            }
            else if ( "paths".equals( query ) )
            {
                MultiPropertiesConfig mpc = C3P0Config.getMultiPropertiesConfig();
                String[] paths = mpc.getPropertiesResourcePaths();
                StringBuilder sb = new StringBuilder();
                for ( int i = 0; i < paths.length; ++i )
                {
                    if ( i > 0 ) sb.append( '|' );
                    sb.append( paths[i] );
                }
                answer( sb.toString() );
            }
            else
            {
                System.err.println( "Unknown query '" + query + "'." );
                System.exit( 2 );
            }
        }
        catch ( Throwable t )
        {
            // A scenario may deliberately break a config source. Report rather than hang the
            // parent on an empty stdout.
            t.printStackTrace();
            answer( "!THREW " + t.getClass().getName() );
        }

        // c3p0 may have started no threads here, but do not rely on that.
        System.exit( 0 );
    }

    private static void answer( String s )
    {
        System.out.println( ANSWER_PFX + s );
        System.out.flush();
    }
}
