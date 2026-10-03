package com.mchange.v2.c3p0;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Hashtable;
import java.util.Properties;

import javax.naming.Context;
import javax.naming.spi.InitialContextFactory;
import javax.sql.DataSource;

import junit.framework.TestCase;

import com.mchange.v2.c3p0.cfg.C3P0Config;
import com.mchange.v2.cfg.MultiPropertiesConfig;
import com.mchange.v2.cfg.PropertiesConfig;
import com.mchange.v2.naming.AlwaysForbidUnsafeInitialContextEnvFilter;
import com.mchange.v2.naming.AlwaysReplaceWithDefaultUnsafeInitialContextEnvFilter;
import com.mchange.v2.naming.ForbiddenInitialContextException;
import com.mchange.v2.naming.SecurityConfigKey;
import com.mchange.v2.naming.UnsafeInitialContextEnvFilter;

/**
 *  A JndiRefForwardingDataSource can arrive by de-Reference-ing or Java deserialization, so the
 *  jndiEnv it carries is of unestablished provenance -- and a JNDI environment names the context
 *  factory to instantiate and the server to fetch from, which is JNDI injection in miniature.
 *  This path used to refuse every non-default environment outright, with a message inviting
 *  anyone who minded to open an issue. It now consults an UnsafeInitialContextEnvFilter that the
 *  deployment names, defaulting to one that refuses, so the refusal became a policy rather than
 *  a hard-coded answer.
 *
 *  <p>Declared into com.mchange.v2.c3p0 because JndiRefForwardingDataSource is package-private.
 *  Configuration is installed globally, since the filter is resolved from
 *  C3P0Config.getMultiPropertiesConfig() rather than from anything passed in -- so tearDown has
 *  to put it back, because the rest of the suite shares this JVM.</p>
 *
 *  <p>No database and no JNDI provider are needed: the context factory, Context, DataSource and
 *  Connection are all dynamic proxies, which is also what lets us see which environment the
 *  InitialContext was actually built from.</p>
 */
public class JndiRefForwardingDataSourceEnvFilterJUnitTestCase extends TestCase
{
    private final static String FILTER_KEY     = SecurityConfigKey.UNSAFE_INITIAL_CONTEXT_ENV_FILTER_CLASS_NAME;
    private final static String LOCAL_NAME     = "java:comp/env/myDS"; // local, so the NameGuard allows it
    private final static String HOSTILE_URL    = "ldap://evil.example.com:1389/payload";

    /**
     *  Records the environment JNDI hands it, and answers lookups with a usable DataSource, so a
     *  forwarding lookup can run to completion. This is the only way to observe which
     *  environment the InitialContext was constructed from.
     */
    public static class RecordingInitialContextFactory implements InitialContextFactory
    {
        public static volatile Hashtable<?,?> recordedEnv;
        public static volatile boolean        consulted = false;

        @Override
        public Context getInitialContext( Hashtable<?,?> environment )
        {
            recordedEnv = environment;
            consulted = true;
            return (Context) Proxy.newProxyInstance(
                Context.class.getClassLoader(), new Class<?>[] { Context.class },
                new InvocationHandler()
                {
                    @Override
                    public Object invoke( Object proxy, Method m, Object[] args )
                    { return "lookup".equals( m.getName() ) ? dummyDataSource() : null; }
                } );
        }
    }

    private static DataSource dummyDataSource()
    {
        return (DataSource) Proxy.newProxyInstance(
            DataSource.class.getClassLoader(), new Class<?>[] { DataSource.class },
            new InvocationHandler()
            {
                @Override
                public Object invoke( Object proxy, Method m, Object[] args )
                {
                    if ( "getConnection".equals( m.getName() ) )
                        return Proxy.newProxyInstance( Connection.class.getClassLoader(),
                                                       new Class<?>[] { Connection.class },
                                                       new InvocationHandler()
                                                       {
                                                           @Override
                                                           public Object invoke( Object p, Method mm, Object[] aa )
                                                           { return null; }
                                                       } );
                    if ( "toString".equals( m.getName() ) ) return "dummyDataSource";
                    if ( "hashCode".equals( m.getName() ) ) return Integer.valueOf( 1 );
                    if ( "equals".equals( m.getName() ) )   return Boolean.valueOf( proxy == args[0] );
                    return null;
                }
            } );
    }

    /** Keeps the context factory, so a lookup can happen at all, and drops everything else. */
    public static class KeepOnlyFactoryFilter implements UnsafeInitialContextEnvFilter
    {
        @Override
        public Hashtable<?,?> safeEnv( Hashtable<?,?> env, Class<?> materializingClass, PropertiesConfig pcfg )
        {
            Hashtable<Object,Object> out = new Hashtable<Object,Object>();
            out.put( Context.INITIAL_CONTEXT_FACTORY, env.get( Context.INITIAL_CONTEXT_FACTORY ) );
            return out;
        }
    }

    /** Accepts whatever it is given, unchanged -- the most permissive thing a deployment can do. */
    public static class AcceptAnythingFilter implements UnsafeInitialContextEnvFilter
    {
        @Override
        public Hashtable<?,?> safeEnv( Hashtable<?,?> env, Class<?> materializingClass, PropertiesConfig pcfg )
        { return env; }
    }

    /** Records what the filter was told about the object whose materialization needs the lookup. */
    public static class MaterializingClassRecordingFilter implements UnsafeInitialContextEnvFilter
    {
        public static volatile Class<?> recorded;

        @Override
        public Hashtable<?,?> safeEnv( Hashtable<?,?> env, Class<?> materializingClass, PropertiesConfig pcfg )
        {
            recorded = materializingClass;
            return env;
        }
    }

    @Override
    protected void setUp() throws Exception
    {
        RecordingInitialContextFactory.recordedEnv = null;
        RecordingInitialContextFactory.consulted   = false;
        MaterializingClassRecordingFilter.recorded = null;
    }

    @Override
    protected void tearDown() throws Exception
    { C3P0Config.refreshMainConfig(); }

    // ==================== plumbing ====================

    private static void installFilter( Class<?> filterClass )
    { installFilter( filterClass == null ? null : filterClass.getName() ); }

    private static void installFilter( String filterClassName )
    {
        if ( filterClassName == null ) { C3P0Config.refreshMainConfig(); return; }
        Properties p = new Properties();
        p.setProperty( FILTER_KEY, filterClassName );
        C3P0Config.refreshMainConfig(
            new MultiPropertiesConfig[] { MultiPropertiesConfig.fromProperties( "/notional-test-resource", p ) },
            "JndiRefForwardingDataSourceEnvFilterJUnitTestCase" );
    }

    /** An environment that both names our recording factory and carries something hostile. */
    private static Hashtable<String,String> envWithFactoryAnd( String hostileUrl )
    {
        Hashtable<String,String> env = new Hashtable<String,String>();
        env.put( Context.INITIAL_CONTEXT_FACTORY, RecordingInitialContextFactory.class.getName() );
        if ( hostileUrl != null ) env.put( Context.PROVIDER_URL, hostileUrl );
        return env;
    }

    @SuppressWarnings("unchecked")
    private static JndiRefForwardingDataSource dataSource( Hashtable<?,?> env ) throws Exception
    {
        JndiRefForwardingDataSource ds = new JndiRefForwardingDataSource( false );
        ds.setJndiName( LOCAL_NAME );
        if ( env != null ) ds.setJndiEnv( (Hashtable) env );
        return ds;
    }

    /** @return the Throwable getConnection() failed with, or null if it succeeded. */
    private static Throwable connectFailure( JndiRefForwardingDataSource ds )
    {
        try { ds.getConnection(); return null; }
        catch ( Throwable t ) { return t; }
    }

    private static boolean chainHas( Throwable t, Class<?> type )
    {
        for ( Throwable w = t; w != null && w != w.getCause(); w = w.getCause() )
            if ( type.isInstance( w ) ) return true;
        return false;
    }

    private static String chainMessages( Throwable t )
    {
        StringBuilder sb = new StringBuilder();
        for ( Throwable w = t; w != null && w != w.getCause(); w = w.getCause() )
            sb.append( w.getMessage() ).append( " | " );
        return sb.toString();
    }

    // ==================== the default is to refuse ====================

    /**
     *  Unconfigured, behaviour is what it was before filters existed: a non-default environment
     *  is not used. The refusal has to be identifiable as one, and has to name the setting that
     *  produced it -- otherwise a deployer cannot tell a policy decision from a malfunction, nor
     *  find the knob.
     */
    public void testANonEmptyEnvironmentIsRefusedByDefault() throws Exception
    {
        installFilter( (Class<?>) null );

        Throwable t = connectFailure( dataSource( envWithFactoryAnd( HOSTILE_URL ) ) );

        assertNotNull( "A non-default environment must not be used by default.", t );
        assertTrue( "The refusal should be a refusal: " + chainMessages( t ),
                    chainHas( t, ForbiddenInitialContextException.class ) );
        assertTrue( "and should name the key that decided: " + chainMessages( t ),
                    chainMessages( t ).indexOf( FILTER_KEY ) >= 0 );
        assertTrue( "and the filter that refused: " + chainMessages( t ),
                    chainMessages( t ).indexOf( AlwaysForbidUnsafeInitialContextEnvFilter.class.getName() ) >= 0 );
        // Both messages name the key and the class, and both chain the cause, so neither of those
        // tells a refusal from a filter that would not construct. Only this does.
        assertTrue( "and must read as a decision rather than a malfunction: " + chainMessages( t ),
                    chainMessages( t ).indexOf( "policy decision" ) >= 0 );
        assertFalse( "nothing failed to instantiate here: " + chainMessages( t ),
                     chainMessages( t ).indexOf( "failed to instantiate" ) >= 0 );
        assertFalse( "and nothing should have reached JNDI at all.",
                     RecordingInitialContextFactory.consulted );
    }

    /** An environment is refused on provenance, not content: there is nothing hostile here. */
    public void testAnInnocuousEnvironmentIsAlsoRefusedByDefault() throws Exception
    {
        installFilter( (Class<?>) null );

        Throwable t = connectFailure( dataSource( envWithFactoryAnd( null ) ) );

        assertTrue( "" + chainMessages( t ), chainHas( t, ForbiddenInitialContextException.class ) );
    }

    // ==================== what bypasses the filter ====================

    /**
     *  An empty environment is behaviourally identical to none -- new InitialContext(empty) and
     *  new InitialContext() do the same thing -- so it never reaches the filter. Without that,
     *  the default filter would refuse a Reference carrying an empty Hashtable. commons
     *  normalizes the same way, so the two sides agree on this input.
     */
    public void testAnEmptyEnvironmentBypassesTheFilter() throws Exception
    {
        installFilter( (Class<?>) null );

        Throwable t = connectFailure( dataSource( new Hashtable<String,String>() ) );

        assertFalse( "An empty environment is not an untrusted one: " + chainMessages( t ),
                     chainHas( t, ForbiddenInitialContextException.class ) );
    }

    public void testANullEnvironmentBypassesTheFilter() throws Exception
    {
        installFilter( (Class<?>) null );

        Throwable t = connectFailure( dataSource( null ) );

        assertFalse( "" + chainMessages( t ), chainHas( t, ForbiddenInitialContextException.class ) );
    }

    // ==================== what a configured filter can do ====================

    /**
     *  The whole point: a deployment that needs a particular remote directory can say so, and
     *  the lookup goes through. This runs to completion -- the environment names our recording
     *  factory, whose Context answers the lookup with a DataSource -- so it covers the path from
     *  configuration all the way to a returned Connection.
     */
    public void testAPermissiveFilterLetsTheLookupProceed() throws Exception
    {
        installFilter( AcceptAnythingFilter.class );

        Throwable t = connectFailure( dataSource( envWithFactoryAnd( HOSTILE_URL ) ) );

        assertNull( "A filter that permits should let the lookup complete: " + chainMessages( t ), t );
        assertTrue( "and JNDI should have been consulted.", RecordingInitialContextFactory.consulted );
    }

    /**
     *  And the environment JNDI sees is the filter's, not the original. This is the property the
     *  filter exists for, and the one that was broken on the commons side at first: the filtered
     *  environment reached the InitialContext constructor while the raw one went on downstream.
     */
    public void testTheInitialContextIsBuiltFromTheFilteredEnvironment() throws Exception
    {
        installFilter( KeepOnlyFactoryFilter.class );

        Throwable t = connectFailure( dataSource( envWithFactoryAnd( HOSTILE_URL ) ) );

        assertNull( "" + chainMessages( t ), t );
        assertNotNull( "Precondition: JNDI was consulted, so it saw some environment.",
                       RecordingInitialContextFactory.recordedEnv );
        assertEquals( "What the filter kept must be present.",
                      RecordingInitialContextFactory.class.getName(),
                      RecordingInitialContextFactory.recordedEnv.get( Context.INITIAL_CONTEXT_FACTORY ) );
        assertNull( "and what it removed must be gone: " + RecordingInitialContextFactory.recordedEnv,
                    RecordingInitialContextFactory.recordedEnv.get( Context.PROVIDER_URL ) );
    }

    /** Discarding the environment entirely leaves the lookup to the JVM default, not refused. */
    public void testReplacingWithTheDefaultIsNotARefusal() throws Exception
    {
        installFilter( AlwaysReplaceWithDefaultUnsafeInitialContextEnvFilter.class );

        Throwable t = connectFailure( dataSource( envWithFactoryAnd( HOSTILE_URL ) ) );

        assertFalse( "Returning null discards the environment; it does not refuse the lookup: "
                     + chainMessages( t ),
                     chainHas( t, ForbiddenInitialContextException.class ) );
        assertFalse( "and the discarded environment must not have reached JNDI either.",
                     RecordingInitialContextFactory.consulted );
    }

    /** The filter is told which class's materialization needs the lookup. */
    public void testTheFilterIsToldWhatIsMaterializing() throws Exception
    {
        installFilter( MaterializingClassRecordingFilter.class );

        connectFailure( dataSource( envWithFactoryAnd( HOSTILE_URL ) ) );

        assertEquals( JndiRefForwardingDataSource.class, MaterializingClassRecordingFilter.recorded );
    }

    // ==================== failures are not decisions ====================

    /**
     *  A filter that cannot be constructed is a different kind of problem from one that refused,
     *  and must not be dressed as a policy decision -- the deployer needs to know their
     *  configuration is broken rather than working as intended.
     */
    public void testAFilterThatCannotBeConstructedIsAFailureNotADecision() throws Exception
    {
        installFilter( "com.example.NoSuchFilter" );

        Throwable t = connectFailure( dataSource( envWithFactoryAnd( HOSTILE_URL ) ) );

        assertNotNull( t );
        assertTrue( "the root cause should say what could not be found: " + chainMessages( t ),
                    chainHas( t, ClassNotFoundException.class ) );
        assertTrue( "the message should name the class it tried: " + chainMessages( t ),
                    chainMessages( t ).indexOf( "com.example.NoSuchFilter" ) >= 0 );
        assertFalse( "and must not claim to be a policy decision: " + chainMessages( t ),
                     chainMessages( t ).indexOf( "policy decision" ) >= 0 );
    }

    // ==================== ordering against the name guard ====================

    /**
     *  The name is checked before the environment. A name the NameGuard refuses should fail on
     *  the name, whatever the environment says -- so a hostile environment cannot be used to
     *  provoke different handling of a name that was never going to be looked up.
     */
    public void testAnUnacceptableNameIsRefusedBeforeTheEnvironmentIsConsidered() throws Exception
    {
        installFilter( MaterializingClassRecordingFilter.class );

        JndiRefForwardingDataSource ds = new JndiRefForwardingDataSource( false );
        // set the field directly: setJndiName is vetoed for unacceptable names, and we want the
        // name to reach dereference() so that the ordering inside it is what gets tested.
        java.lang.reflect.Field f = findField( ds.getClass(), "jndiName" );
        f.setAccessible( true );
        f.set( ds, "ldap://evil.example.com/obj" );
        ds.setJndiEnv( envWithFactoryAnd( HOSTILE_URL ) );

        Throwable t = connectFailure( ds );

        assertNotNull( "A non-local name must be refused.", t );
        assertNull( "The filter should never have been reached.", MaterializingClassRecordingFilter.recorded );
        assertFalse( "and JNDI certainly not.", RecordingInitialContextFactory.consulted );
    }

    private static java.lang.reflect.Field findField( Class<?> cl, String name ) throws Exception
    {
        for ( Class<?> c = cl; c != null; c = c.getSuperclass() )
            try { return c.getDeclaredField( name ); } catch ( NoSuchFieldException e ) { /* keep looking */ }
        throw new NoSuchFieldException( name );
    }
}
