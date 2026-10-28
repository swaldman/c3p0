package com.mchange.v2.c3p0.test.cfgcompat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 *  Asks one JVM's worth of configuration questions and prints the answers canonically.
 *
 *  <p>This class reaches C3P0Config <b>reflectively, and imports nothing from c3p0 or
 *  mchange-commons-java</b>. That is the whole point: one compiled probe has to run against
 *  c3p0-0.14.2 (with mchange-commons-java 0.6.1) and against the current build, whose
 *  <code>com.mchange.v2.c3p0.cfg</code> package has been reorganized out from under any code
 *  that named its types. Compiling two probes and trusting them to ask the same questions would
 *  be the weaker arrangement -- the comparison is only as good as the probes' being identical,
 *  so there is one probe.</p>
 *
 *  <p>It asks only what 0.14.2 and the current build both expose: the facade accessors on
 *  C3P0Config. Deliberately NOT asked:</p>
 *
 *  <ul>
 *    <li><code>allCurrentProperties()</code> and the <code>MultiPropertiesConfig</code>, because
 *    the current build layers C3P0_HARDCODED_DEFAULTS underneath the properties view and 0.14.2
 *    did not. That divergence is known, intended, and would swamp every scenario's diff with the
 *    same forty lines.</li>
 *    <li><code>getUserOverridesAsString</code>'s actual text, because it is a serialization
 *    format, not a configuration outcome. Whether it is null IS an outcome -- beans bind null
 *    differently from empty -- so that much is recorded.</li>
 *  </ul>
 *
 *  <p>One JVM per scenario, because c3p0 settles its configuration once per process. Run with
 *  the scenario's resources on the classpath and its System properties on the command line; the
 *  scenario name is the sole argument, used only to label output.</p>
 */
public class ConfigOutcomeProbe
{
    /** Prefix on every answer, so the parent can find it among whatever c3p0 logs on the way up. */
    public final static String OUT_PFX = "#OUTCOME# ";

    /** Stands for the default config in scope-qualified output, since null has no spelling. */
    public final static String DEFAULT_SCOPE = "-";

    /** A name no scenario defines, so "asked about a config that isn't there" is always covered. */
    public final static String UNDEFINED_SCOPE = "nosuchconfig";

    /**
     *  The properties interrogated one at a time. Chosen to cover the coercion paths (int,
     *  boolean, String, and the two enum-ish ones) and to include properties no scenario ever
     *  sets, since "absent" has to be pinned as carefully as "present".
     */
    public final static String[] PROBED_PROPERTIES =
    {
        "maxPoolSize", "minPoolSize", "initialPoolSize", "acquireIncrement", "maxStatements",
        "maxStatementsPerConnection", "idleConnectionTestPeriod", "maxIdleTime",
        "unreturnedConnectionTimeout", "numHelperThreads", "checkoutTimeout",
        "acquireRetryAttempts", "acquireRetryDelay", "propertyCycle",
        "testConnectionOnCheckout", "testConnectionOnCheckin", "autoCommitOnClose",
        "breakAfterAcquireFailure", "forceSynchronousCheckins", "privilegeSpawnedThreads",
        "preferredTestQuery", "automaticTestTable", "driverClass", "jdbcUrl", "user", "password",
        "connectionCustomizerClassName", "contextClassLoaderSource", "markSessionBoundaries",
        "taskRunnerFactoryClassName", "connectionTesterClassName",
        "noSuchPropertyAtAll"
    };

    private final static List<String> lines = new ArrayList<String>();

    public static void main( String[] argv )
    {
        String scenario = argv.length > 0 ? argv[0] : "unnamed";
        try
        {
            Class<?> cfg = Class.forName( "com.mchange.v2.c3p0.cfg.C3P0Config" );

            List<String> scopes = new ArrayList<String>();
            scopes.add( null );
            for ( int i = 1; i < argv.length; ++i ) scopes.add( argv[i] );
            scopes.add( UNDEFINED_SCOPE );

            for ( String scope : scopes )
            {
                probeScalars( cfg, scope );
                probeMap( cfg, "props", "getUnspecifiedUserProperties", scope );
                probeMap( cfg, "ext",   "getExtensions",                scope );
                probeUserOverrides( cfg, scope );
                probeUserOverridesAsString( cfg, scope );
            }

            probeInitializers( cfg );
        }
        catch ( Throwable t )
        {
            emit( "THREW|" + t.getClass().getName() + "|" + oneLine( t.getMessage() ) );
        }

        // sorted so the output is a canonical set rather than an execution trace: a reordering
        // inside the implementation is not a configuration change and must not read as one
        Collections.sort( lines );
        StringBuilder sb = new StringBuilder();
        for ( String line : lines )
            sb.append( OUT_PFX ).append( scenario ).append( '|' ).append( line ).append( '\n' );
        System.out.print( sb );
        System.out.flush();
        System.exit( 0 );
    }

    private static void probeScalars( Class<?> cfg, String scope ) throws Exception
    {
        Method m = cfg.getMethod( "getUnspecifiedUserProperty", String.class, String.class );
        for ( String prop : PROBED_PROPERTIES )
            emit( "prop|" + s( scope ) + "|" + prop + "|" + val( invoke( m, prop, scope ) ) );
    }

    private static void probeMap( Class<?> cfg, String tag, String methodName, String scope ) throws Exception
    {
        Method m = cfg.getMethod( methodName, String.class );
        Object got = invoke( m, scope );
        if ( got == null ) { emit( tag + "Null|" + s( scope ) ); return; }

        Map<?,?> map = (Map<?,?>) got;
        for ( Object k : new TreeSet<Object>( keysAsStrings( map ) ) )
            emit( tag + "|" + s( scope ) + "|" + k + "|" + val( map.get( k ) ) );
        if ( map.isEmpty() ) emit( tag + "Empty|" + s( scope ) );
    }

    /** Two levels: user -> property -> value. The shape is part of the contract, so flatten it faithfully. */
    private static void probeUserOverrides( Class<?> cfg, String scope ) throws Exception
    {
        Method m = cfg.getMethod( "getUserOverrides", String.class );
        Object got = invoke( m, scope );
        if ( got == null ) { emit( "uoNull|" + s( scope ) ); return; }

        Map<?,?> byUser = (Map<?,?>) got;
        if ( byUser.isEmpty() ) emit( "uoEmpty|" + s( scope ) );
        for ( Object user : new TreeSet<Object>( keysAsStrings( byUser ) ) )
        {
            Object inner = byUser.get( user );
            if (! (inner instanceof Map) )
            {
                emit( "uoBadShape|" + s( scope ) + "|" + user + "|" +
                      (inner == null ? "null" : inner.getClass().getName()) );
                continue;
            }
            Map<?,?> props = (Map<?,?>) inner;
            for ( Object k : new TreeSet<Object>( keysAsStrings( props ) ) )
                emit( "uo|" + s( scope ) + "|" + user + "|" + k + "|" + val( props.get( k ) ) );
            if ( props.isEmpty() ) emit( "uoUserEmpty|" + s( scope ) + "|" + user );
        }
    }

    private static void probeUserOverridesAsString( Class<?> cfg, String scope ) throws Exception
    {
        Method m = cfg.getMethod( "getUserOverridesAsString", String.class );
        try
        {
            Object got = invoke( m, scope );
            emit( "uoStringPresent|" + s( scope ) + "|" + (got != null) );
        }
        catch ( Exception e )
        {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            emit( "uoStringThrew|" + s( scope ) + "|" + cause.getClass().getName() );
        }
    }

    /**
     *  The initializeXxx entry points, which is how the bean layer actually reads configuration.
     *  They take the default scope only, and fold in a compiled-in fallback -- so they are the
     *  closest thing to "what would a DataSource come up with".
     */
    private static void probeInitializers( Class<?> cfg ) throws Exception
    {
        Method mi = cfg.getMethod( "initializeIntPropertyVar", String.class, int.class );
        Method mb = cfg.getMethod( "initializeBooleanPropertyVar", String.class, boolean.class );
        Method ms = cfg.getMethod( "initializeStringPropertyVar", String.class, String.class );

        for ( String prop : PROBED_PROPERTIES )
        {
            emit( "initInt|"  + prop + "|" + val( invoke( mi, prop, Integer.valueOf( -1 ) ) ) );
            emit( "initBool|" + prop + "|" + val( invoke( mb, prop, Boolean.FALSE ) ) );
            emit( "initStr|"  + prop + "|" + val( invoke( ms, prop, "DFLT" ) ) );
        }

        emit( "initUoString|" + (invoke( cfg.getMethod( "initializeUserOverridesAsString" ) ) != null) );

        Object ext = invoke( cfg.getMethod( "initializeExtensions" ) );
        if ( ext == null ) emit( "initExtNull" );
        else
        {
            Map<?,?> map = (Map<?,?>) ext;
            for ( Object k : new TreeSet<Object>( keysAsStrings( map ) ) )
                emit( "initExt|" + k + "|" + val( map.get( k ) ) );
            if ( map.isEmpty() ) emit( "initExtEmpty" );
        }
    }

    // ----------------------------------------------------------------- plumbing

    /** An invocation that throws is an outcome too, so it is recorded rather than aborting the probe. */
    private static Object invoke( Method m, Object... args ) throws Exception
    { return m.invoke( null, args ); }

    private static List<String> keysAsStrings( Map<?,?> map )
    {
        List<String> out = new ArrayList<String>();
        for ( Object k : map.keySet() ) out.add( String.valueOf( k ) );
        return out;
    }

    private static String s( String scope )
    { return scope == null ? DEFAULT_SCOPE : scope; }

    private static String val( Object o )
    { return o == null ? "NULL" : escape( String.valueOf( o ) ); }

    private static String escape( String s )
    { return oneLine( s ).replace( "\\", "\\\\" ).replace( "|", "\\|" ); }

    private static String oneLine( String s )
    { return s == null ? "null" : s.replace( "\r", "\\r" ).replace( "\n", "\\n" ); }

    private static void emit( String line )
    { lines.add( line ); }
}
