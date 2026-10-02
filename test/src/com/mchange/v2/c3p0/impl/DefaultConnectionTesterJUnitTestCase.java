package com.mchange.v2.c3p0.impl;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

import junit.framework.TestCase;

import com.mchange.v2.c3p0.ConnectionTester;
import com.mchange.v2.c3p0.cfg.C3P0Config;
import com.mchange.v2.cfg.MultiPropertiesConfig;
import com.mchange.v2.cfg.PropertiesConfig;

import com.mchange.v2.c3p0.impl.DefaultConnectionTester.QuerylessTestRunner;

/**
 *  DefaultConnectionTester had no tests. It also, until recently, could not see a configuration
 *  change: isValidTimeout was a static final set during class initialization, and the chosen
 *  QuerylessTestRunner was a final instance field set in the constructor. Neither could be
 *  revisited, and since C3P0Registry caches ConnectionTester instances by class name -- and
 *  pools hold on to the instance they were given -- a tester outlives any number of
 *  configurations.
 *
 *  <p>Both settings now live in a CachedState keyed on the identity of the MultiPropertiesConfig
 *  they were read from, rebuilt when that identity changes. These tests cover three things: that
 *  the derivations behave as the originals did for a given configuration, that the state is
 *  reused while configuration holds still, and that a change is actually picked up -- the last
 *  being the behaviour that did not exist before.</p>
 *
 *  <p>Declared into com.mchange.v2.c3p0.impl to reach the package-private runner constants.
 *  The derivations themselves are private statics, so those are reached reflectively.</p>
 *
 *  <p>No database is needed: the Connections here are dynamic proxies.</p>
 */
public class DefaultConnectionTesterJUnitTestCase extends TestCase
{
    // The config keys, spelled out rather than borrowed from the class under test. They are
    // documented settings, so a test that pins the literal names is a test of the contract.
    private final static String IS_VALID_TIMEOUT_KEY      = "com.mchange.v2.c3p0.impl.DefaultConnectionTester.isValidTimeout";
    private final static String QUERYLESS_TEST_RUNNER_KEY = "com.mchange.v2.c3p0.impl.DefaultConnectionTester.querylessTestRunner";

    /** Selectable by class name, and records whether it was consulted. */
    public static class RecordingTestRunner implements QuerylessTestRunner
    {
        public static volatile boolean invoked = false;

        @Override
        public int activeCheckConnectionNoQuery( Connection c, Throwable[] rootCauseOutParamHolder )
        {
            invoked = true;
            return ConnectionTester.CONNECTION_IS_OKAY;
        }
    }

    @Override
    protected void setUp() throws Exception
    { RecordingTestRunner.invoked = false; }

    /**
     *  Configuration here is installed globally, since that is the only way to change the
     *  identity the cache keys on. Putting it back is not optional -- the rest of the suite
     *  shares this JVM and reads the same configuration.
     */
    @Override
    protected void tearDown() throws Exception
    { C3P0Config.refreshMainConfig(); }

    // ==================== plumbing ====================

    private static PropertiesConfig cfg( String... keysAndValues )
    {
        Properties p = new Properties();
        for ( int i = 0; i < keysAndValues.length; i += 2 )
            p.setProperty( keysAndValues[i], keysAndValues[i+1] );
        return MultiPropertiesConfig.fromProperties( "/notional-test-resource", p );
    }

    /** Install configuration globally, which is what gives C3P0Config a fresh identity. */
    private static void installConfig( String... keysAndValues )
    {
        Properties p = new Properties();
        for ( int i = 0; i < keysAndValues.length; i += 2 )
            p.setProperty( keysAndValues[i], keysAndValues[i+1] );
        C3P0Config.refreshMainConfig(
            new MultiPropertiesConfig[] { MultiPropertiesConfig.fromProperties( "/notional-test-resource", p ) },
            "DefaultConnectionTesterJUnitTestCase" );
    }

    private static Object invokePrivateStatic( String name, Class<?> argType, Object arg ) throws Exception
    {
        Method m = DefaultConnectionTester.class.getDeclaredMethod( name, argType );
        m.setAccessible( true );
        return m.invoke( null, arg );
    }

    private static int chooseIsValidTimeout( PropertiesConfig pcfg ) throws Exception
    { return ((Integer) invokePrivateStatic( "chooseIsValidTimeout", PropertiesConfig.class, pcfg )).intValue(); }

    private static QuerylessTestRunner chooseQuerylessTestRunner( PropertiesConfig pcfg ) throws Exception
    { return (QuerylessTestRunner) invokePrivateStatic( "chooseQuerylessTestRunner", PropertiesConfig.class, pcfg ); }

    private static Object cachedState() throws Exception
    {
        Method m = DefaultConnectionTester.class.getDeclaredMethod( "getUpdateCachedState" );
        m.setAccessible( true );
        return m.invoke( null );
    }

    private static Object fieldOf( Object o, String name ) throws Exception
    {
        java.lang.reflect.Field f = o.getClass().getDeclaredField( name );
        f.setAccessible( true );
        return f.get( o );
    }

    /** Records the timeout handed to isValid, and answers as told. */
    private static class RecordingConnection implements InvocationHandler
    {
        int     isValidTimeout = Integer.MIN_VALUE;
        boolean isValidAnswer  = true;
        boolean createStatementCalled = false;

        @Override
        public Object invoke( Object proxy, Method m, Object[] args ) throws Throwable
        {
            String name = m.getName();
            if ( "isValid".equals( name ) )
            {
                isValidTimeout = ((Integer) args[0]).intValue();
                return Boolean.valueOf( isValidAnswer );
            }
            if ( "createStatement".equals( name ) )
            {
                createStatementCalled = true;
                throw new SQLException( "no statements here" );
            }
            if ( "toString".equals( name ) ) return "RecordingConnection";
            if ( "hashCode".equals( name ) ) return Integer.valueOf( System.identityHashCode( proxy ) );
            if ( "equals".equals( name ) )   return Boolean.valueOf( proxy == args[0] );
            return null;
        }

        Connection connection()
        { return (Connection) Proxy.newProxyInstance( Connection.class.getClassLoader(),
                                                      new Class<?>[] { Connection.class }, this ); }
    }

    // ==================== isValidTimeout, as the original derived it ====================

    /**
     *  A table rather than a transcription of the old code, so this states the intended
     *  behaviour instead of agreeing with the implementation by construction. The old logic
     *  lived in the static initializer; every case here held then and must hold now.
     */
    public void testIsValidTimeoutDerivation() throws Exception
    {
        assertEquals( "unset means no timeout", 0, chooseIsValidTimeout( cfg() ) );
        assertEquals( "an explicit zero is no timeout", 0, chooseIsValidTimeout( cfg( IS_VALID_TIMEOUT_KEY, "0" ) ) );
        assertEquals( "a negative timeout is clamped to none", 0, chooseIsValidTimeout( cfg( IS_VALID_TIMEOUT_KEY, "-5" ) ) );
        assertEquals( "a positive timeout is used as given", 3, chooseIsValidTimeout( cfg( IS_VALID_TIMEOUT_KEY, "3" ) ) );
        assertEquals( "an unparseable value falls back to none", 0, chooseIsValidTimeout( cfg( IS_VALID_TIMEOUT_KEY, "abc" ) ) );
        assertEquals( "and so does an empty one", 0, chooseIsValidTimeout( cfg( IS_VALID_TIMEOUT_KEY, "" ) ) );
    }

    /**
     *  The timeout is trimmed before parsing. It did not used to be: Integer.parseInt does not
     *  trim, nothing trimmed first, and Properties.load preserves trailing whitespace -- so
     *  "isValidTimeout=3 " in a properties file was unparseable and silently became no timeout
     *  at all, which is about as unhelpful as a configuration failure gets. The runner key was
     *  always trimmed; now the two agree.
     */
    public void testAWhitespacedTimeoutIsTrimmedThenParsed() throws Exception
    {
        assertEquals( "trailing, as Properties.load would leave it", 3, chooseIsValidTimeout( cfg( IS_VALID_TIMEOUT_KEY, "3 " ) ) );
        assertEquals( "leading", 3, chooseIsValidTimeout( cfg( IS_VALID_TIMEOUT_KEY, " 3" ) ) );
        assertEquals( "both", 3, chooseIsValidTimeout( cfg( IS_VALID_TIMEOUT_KEY, "   3   " ) ) );
        assertEquals( "and a value that is only whitespace is still no timeout",
                      0, chooseIsValidTimeout( cfg( IS_VALID_TIMEOUT_KEY, "   " ) ) );
    }

    // ==================== the runner, as the original chose it ====================

    public void testTheDefaultRunnerIsSwitch() throws Exception
    {
        assertSame( DefaultConnectionTester.SWITCH, chooseQuerylessTestRunner( cfg() ) );
    }

    public void testARunnerCanBeNamedByItsStaticField() throws Exception
    {
        assertSame( DefaultConnectionTester.IS_VALID,
                    chooseQuerylessTestRunner( cfg( QUERYLESS_TEST_RUNNER_KEY, "IS_VALID" ) ) );
        assertSame( DefaultConnectionTester.METADATA_TABLESEARCH,
                    chooseQuerylessTestRunner( cfg( QUERYLESS_TEST_RUNNER_KEY, "METADATA_TABLESEARCH" ) ) );
        assertSame( DefaultConnectionTester.THREAD_LOCAL,
                    chooseQuerylessTestRunner( cfg( QUERYLESS_TEST_RUNNER_KEY, "THREAD_LOCAL" ) ) );
        assertSame( DefaultConnectionTester.SWITCH,
                    chooseQuerylessTestRunner( cfg( QUERYLESS_TEST_RUNNER_KEY, "SWITCH" ) ) );
    }

    /** A dotted value is taken as a class name and instantiated. */
    public void testARunnerCanBeNamedByItsClass() throws Exception
    {
        QuerylessTestRunner out =
            chooseQuerylessTestRunner( cfg( QUERYLESS_TEST_RUNNER_KEY, RecordingTestRunner.class.getName() ) );

        assertEquals( RecordingTestRunner.class, out.getClass() );
    }

    /**
     *  Surrounding whitespace is tolerated. Note this does not attribute the trimming to any
     *  one layer: querylessTestRunnerProperty is a SealedSystemPropertiesStringProperty under
     *  TRIM_BLANKS_ARE_NULL, so the value is already trimmed before chooseQuerylessTestRunner
     *  trims it again. Removing either trim leaves this passing -- which is the point of
     *  keeping the redundant one, so the method is correct on its own terms.
     */
    public void testARunnerNameIsTrimmed() throws Exception
    {
        assertSame( DefaultConnectionTester.IS_VALID,
                    chooseQuerylessTestRunner( cfg( QUERYLESS_TEST_RUNNER_KEY, "   IS_VALID   " ) ) );
    }

    public void testAnUnresolvableRunnerFallsBackToTheDefault() throws Exception
    {
        assertSame( "a field that does not exist",
                    DefaultConnectionTester.SWITCH,
                    chooseQuerylessTestRunner( cfg( QUERYLESS_TEST_RUNNER_KEY, "NO_SUCH_RUNNER" ) ) );
        assertSame( "a class that does not exist",
                    DefaultConnectionTester.SWITCH,
                    chooseQuerylessTestRunner( cfg( QUERYLESS_TEST_RUNNER_KEY, "com.example.NoSuchRunner" ) ) );
        assertSame( "a class that exists but is not a runner",
                    DefaultConnectionTester.SWITCH,
                    chooseQuerylessTestRunner( cfg( QUERYLESS_TEST_RUNNER_KEY, "java.lang.String" ) ) );
    }

    // ==================== the cache ====================

    /**
     *  Holding configuration still, the state is computed once and shared. This is what makes
     *  the per-test lookup cheap enough to sit on the connection-test path at all.
     */
    public void testTheStateIsReusedWhileConfigurationHoldsStill() throws Exception
    {
        Object first = cachedState();

        assertSame( "Nothing changed, so nothing should have been recomputed.", first, cachedState() );
        assertSame( first, cachedState() );
    }

    /**
     *  And rebuilt when configuration is replaced. The cache keys on the identity of the
     *  MultiPropertiesConfig rather than on its contents, which is why C3P0Config must hand
     *  back the same object each time and never a defensive copy.
     */
    public void testTheStateIsRebuiltWhenConfigurationChanges() throws Exception
    {
        Object before = cachedState();
        PropertiesConfig configBefore = (PropertiesConfig) fieldOf( before, "config" );

        installConfig( IS_VALID_TIMEOUT_KEY, "7" );

        Object after = cachedState();
        assertNotSame( "A replaced configuration must produce fresh state.", before, after );
        assertNotSame( "whose config is the new one", configBefore, fieldOf( after, "config" ) );
        assertSame( "and which is itself then reused", after, cachedState() );
    }

    // ==================== what did not work before ====================

    /**
     *  The point of the exercise. isValidTimeout was a static final read during class
     *  initialization, so no later configuration could change it, however the deployment was
     *  refreshed. Now it can.
     */
    public void testAChangedTimeoutIsPickedUpAfterARefresh() throws Exception
    {
        installConfig( IS_VALID_TIMEOUT_KEY, "4" );
        assertEquals( 4, ((Integer) fieldOf( cachedState(), "isValidTimeout" )).intValue() );

        installConfig( IS_VALID_TIMEOUT_KEY, "9" );
        assertEquals( "A refresh must be visible, which is what a static final could never be.",
                      9, ((Integer) fieldOf( cachedState(), "isValidTimeout" )).intValue() );
    }

    /**
     *  The runner was a final instance field, so an existing tester kept whatever it was built
     *  with -- and pools hold their tester, so clearing C3P0Registry's cache did not help them.
     */
    public void testAChangedRunnerIsPickedUpAfterARefresh() throws Exception
    {
        installConfig( QUERYLESS_TEST_RUNNER_KEY, "IS_VALID" );
        assertSame( DefaultConnectionTester.IS_VALID, fieldOf( cachedState(), "querylessTestRunner" ) );

        installConfig( QUERYLESS_TEST_RUNNER_KEY, "METADATA_TABLESEARCH" );
        assertSame( DefaultConnectionTester.METADATA_TABLESEARCH, fieldOf( cachedState(), "querylessTestRunner" ) );
    }

    // ==================== end to end, through the public API ====================

    /**
     *  The configured timeout has to reach Connection.isValid, which is the only thing the
     *  setting is for. Tested through the public entry point rather than the derivation, so the
     *  whole path from configuration to JDBC call is covered at once.
     */
    public void testTheConfiguredTimeoutReachesConnectionIsValid() throws Exception
    {
        installConfig( QUERYLESS_TEST_RUNNER_KEY, "IS_VALID", IS_VALID_TIMEOUT_KEY, "6" );

        RecordingConnection rc = new RecordingConnection();
        int status = new DefaultConnectionTester().activeCheckConnection( rc.connection(), null, null );

        assertEquals( ConnectionTester.CONNECTION_IS_OKAY, status );
        assertEquals( "isValid must be called with the configured timeout.", 6, rc.isValidTimeout );
    }

    /** A connection answering false is invalid, not okay. */
    public void testAConnectionThatSaysItIsNotValidIsReportedInvalid() throws Exception
    {
        installConfig( QUERYLESS_TEST_RUNNER_KEY, "IS_VALID" );

        RecordingConnection rc = new RecordingConnection();
        rc.isValidAnswer = false;
        Throwable[] holder = new Throwable[1];
        int status = new DefaultConnectionTester().activeCheckConnection( rc.connection(), null, holder );

        assertEquals( ConnectionTester.CONNECTION_IS_INVALID, status );
        assertNotNull( "and the reason should be reported out", holder[0] );
    }

    /** With a query, the runner is not consulted at all -- the query is the test. */
    public void testTheQueryPathNeverConsultsTheRunner() throws Exception
    {
        installConfig( QUERYLESS_TEST_RUNNER_KEY, RecordingTestRunner.class.getName() );
        assertEquals( "Precondition: our runner is the configured one.",
                      RecordingTestRunner.class, fieldOf( cachedState(), "querylessTestRunner" ).getClass() );

        RecordingConnection rc = new RecordingConnection();
        new DefaultConnectionTester().activeCheckConnection( rc.connection(), "SELECT 1", null );

        assertTrue( "Precondition: the query path tried to make a Statement.", rc.createStatementCalled );
        assertFalse( "The runner must not be consulted when a query was given.", RecordingTestRunner.invoked );
    }

    /**
     *  And with a query the state is never even resolved. That is a cost question rather than a
     *  semantic one -- the lookup takes a global monitor, and this is the connection-test path --
     *  so it cannot be observed from the outside. It is caught by emptying the cache first: if
     *  the query path resolves state, the cache is repopulated behind it.
     */
    public void testTheQueryPathDoesNotEvenResolveTheState() throws Exception
    {
        java.lang.reflect.Field f = DefaultConnectionTester.class.getDeclaredField( "cachedState" );
        f.setAccessible( true );
        f.set( null, null );
        assertNull( "Precondition: the cache is empty.", f.get( null ) );

        RecordingConnection rc = new RecordingConnection();
        new DefaultConnectionTester().activeCheckConnection( rc.connection(), "SELECT 1", null );

        assertTrue( "Precondition: the query path ran.", rc.createStatementCalled );
        assertNull( "The query path reads nothing from the cached state, so it should not have "
                    + "paid to build it.", f.get( null ) );
    }

    // ==================== unrelated, and previously untested ====================

    public void testProbableInvalidDbRecognizesConnectionFailureStates()
    {
        assertTrue( DefaultConnectionTester.probableInvalidDb( new SQLException( "x", "08001" ) ) );
        assertTrue( DefaultConnectionTester.probableInvalidDb( new SQLException( "x", "08007" ) ) );
        assertFalse( "MySQL uses 08S01 for a merely stale connection, so it must not condemn the database.",
                     DefaultConnectionTester.probableInvalidDb( new SQLException( "x", "08S01" ) ) );
        assertFalse( DefaultConnectionTester.probableInvalidDb( new SQLException( "x", "42000" ) ) );
        assertFalse( DefaultConnectionTester.probableInvalidDb( new SQLException( "x" ) ) );
    }
}
