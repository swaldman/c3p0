package com.mchange.v2.c3p0.test.cfgfinder;

import com.mchange.v2.c3p0.cfg.C3P0Config;
import com.mchange.v2.c3p0.test.junit.ChildJvmProbeRunner;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 *  The child half of {@link ConfigFinderLatchJUnitTestCase}. Answers one question per process,
 *  on stdout, prefixed so the parent can find it among whatever c3p0 logs on the way up.
 *
 *  <p>One question per process because what is under test is a LATCH: C3P0Config settles which
 *  C3P0ConfigFinder it will use exactly once, during its own class initialization, and nothing
 *  afterwards can displace it. A latch is only observable if you can arrange a second "first
 *  load", and in a single JVM you cannot -- so the parent forks one of these per data point.
 *  This is the same reason ConfigPrecedenceHarness forks.</p>
 *
 *  <p>Which finder is "installed" is not directly observable, since C3P0Config holds it in a
 *  private static. These probes infer it from whether a given finder's findConfig() was called,
 *  which is what MarkerC3P0ConfigFinder and ThrowingC3P0ConfigFinder count.</p>
 */
public class ConfigFinderLatchProbe
{
    public final static String FINDER_KEY  = "com.mchange.v2.c3p0.cfg.finder";
    public final static String XML_CFG_KEY = "com.mchange.v2.c3p0.cfg.xml";

    public final static String MARKER      = "com.mchange.v2.c3p0.test.cfgfinder.MarkerC3P0ConfigFinder";
    public final static String THROWING    = "com.mchange.v2.c3p0.test.cfgfinder.ThrowingC3P0ConfigFinder";
    public final static String SUBCLASSING = "com.mchange.v2.c3p0.test.cfgfinder.SubclassingC3P0ConfigFinder";

    private final static Capture capture = new Capture();

    public static void main( String[] argv )
    {
        try
        {
            // Must be installed before C3P0Config is touched: its class initializer performs the
            // first config load, which is the event half these probes are about.
            Logger mchange = Logger.getLogger( "com.mchange.v2" );
            mchange.setLevel( Level.ALL );
            mchange.addHandler( capture );

            String query = argv.length > 0 ? argv[0] : "startup";

            if ( "startupInstalls".equals( query ) )
                startupInstalls();
            else if ( "refreshViaSystemProperty".equals( query ) )
                refreshViaSystemProperty();
            else if ( "refreshViaOverrides".equals( query ) )
                refreshViaOverrides();
            else if ( "xmlOverrideWarning".equals( query ) )
                xmlOverrideWarning();
            else if ( "throwingFinder".equals( query ) )
                throwingFinder();
            else if ( "subclassingFinder".equals( query ) )
                subclassingFinder();
            else
            {
                System.err.println( "Unknown query '" + query + "'." );
                System.exit( 2 );
            }
        }
        catch ( Throwable t )
        {
            t.printStackTrace();
            answer( "threw", t.getClass().getName() + ": " + t.getMessage() );
        }

        System.exit( 0 );
    }

    /** A finder named in config before the first load is installed and used. */
    private static void startupInstalls()
    {
        touchConfig();
        answer( "markerCalls", MarkerC3P0ConfigFinder.calls() );
    }

    /**
     *  A finder named only AFTER the first load, through a System property. One would expect
     *  this to reach the lookup -- the key is absent when System properties are sealed, so the
     *  seal does not cover it -- but it does not: c3p0 reads its resource-backed sources through
     *  a cache, System properties included, so the late value never enters the configuration at
     *  all. Hence nothing to refuse, and no refusal logged.
     *
     *  <p>Reported rather than assumed either way, since "the key was not visible" and "the key
     *  was refused" are very different reasons for a finder not to run.</p>
     */
    private static void refreshViaSystemProperty()
    {
        touchConfig();
        int before = MarkerC3P0ConfigFinder.calls();

        System.setProperty( FINDER_KEY, MARKER );
        C3P0Config.refreshMainConfig();

        answer( "markerCallsBefore", before );
        answer( "markerCallsAfter", MarkerC3P0ConfigFinder.calls() );
        answer( "refusalWarnings", capture.countAtOrAbove( Level.WARNING, "will be IGNORED" ) );
        answer( "configVisibleToTheLookup", String.valueOf( C3P0Config.getMultiPropertiesConfig().getProperty( FINDER_KEY ) ) );
    }

    /** The other injection route: the finder key passed as a refresh override. */
    private static void refreshViaOverrides()
    {
        touchConfig();
        int before = MarkerC3P0ConfigFinder.calls();

        Properties overrides = new Properties();
        overrides.setProperty( FINDER_KEY, MARKER );
        C3P0Config.refreshMainConfig( overrides, "ConfigFinderLatchProbe refreshViaOverrides" );

        answer( "markerCallsBefore", before );
        answer( "markerCallsAfter", MarkerC3P0ConfigFinder.calls() );
        answer( "refusalWarnings", capture.countAtOrAbove( Level.WARNING, "will be IGNORED" ) );
        answer( "configVisibleToTheLookup", String.valueOf( C3P0Config.getMultiPropertiesConfig().getProperty( FINDER_KEY ) ) );
    }

    /**
     *  THE REGRESSION THAT STARTED THIS. warn_on_conflicting_overrides is passed to
     *  DefaultC3P0ConfigFinder's constructor, and is false on the first load (the static
     *  initializer's call) and true on a refresh that carries overrides. Caching the finder
     *  instance would freeze it at false forever and silence the warning for the life of the
     *  JVM -- silently, since the warning simply stops.
     *
     *  <p>So: no warning at startup, a warning after a refresh with overrides. The first half
     *  matters as much as the second, since a probe that warned at startup would pass the
     *  second assertion while proving nothing about the refresh.</p>
     */
    private static void xmlOverrideWarning() throws Exception
    {
        touchConfig();
        answer( "warnAtStartup", capture.countAtOrAbove( Level.WARNING, "overrides all other c3p0 config" ) );

        Properties overrides = new Properties();
        overrides.setProperty( "maxPoolSize", "13" );
        C3P0Config.refreshMainConfig( overrides, "ConfigFinderLatchProbe xmlOverrideWarning" );

        answer( "warnAfterRefresh", capture.countAtOrAbove( Level.WARNING, "overrides all other c3p0 config" ) );
    }

    /**
     *  A finder whose findConfig() throws. c3p0 must fall back for that refresh WITHOUT
     *  replacing the finder, so the next refresh consults it again -- a transient failure to
     *  read configuration should not permanently downgrade how configuration is read.
     */
    private static void throwingFinder()
    {
        touchConfig();
        answer( "callsAfterStartup", ThrowingC3P0ConfigFinder.calls() );
        answer( "configUsableAfterStartup", usable() );

        C3P0Config.refreshMainConfig();

        answer( "callsAfterRefresh", ThrowingC3P0ConfigFinder.calls() );
        answer( "configUsableAfterRefresh", usable() );
    }

    /**
     *  A deployment finder that extends c3p0's own must survive a refresh. c3p0 rebuilds its own
     *  default finder every refresh, to pick up the current warn_on_conflicting_overrides, and an
     *  instanceof test there would replace this subclass with a plain DefaultC3P0ConfigFinder.
     */
    private static void subclassingFinder()
    {
        touchConfig();
        answer( "callsAfterStartup", SubclassingC3P0ConfigFinder.calls() );

        C3P0Config.refreshMainConfig();

        answer( "callsAfterRefresh", SubclassingC3P0ConfigFinder.calls() );
    }

    /** Hardcoded defaults are enough to answer this, so a usable config always can. */
    private static String usable()
    { return String.valueOf( C3P0Config.getUnspecifiedUserProperty( "maxPoolSize", null ) ); }

    /** Provokes C3P0Config's class initialization, which performs the first config load. */
    private static void touchConfig()
    { C3P0Config.getMultiPropertiesConfig(); }

    private static void answer( String key, int value )
    { ChildJvmProbeRunner.answer( key, value ); }

    private static void answer( String key, String value )
    { ChildJvmProbeRunner.answer( key, value ); }

    /** Collects log records so the probe can report on what c3p0 said. */
    private final static class Capture extends Handler
    {
        private final List<String> messages = new ArrayList<String>();
        private final List<Level>  levels   = new ArrayList<Level>();

        @Override
        public synchronized void publish( LogRecord record )
        {
            messages.add( String.valueOf( record.getMessage() ) );
            levels.add( record.getLevel() );
        }

        @Override
        public void flush() {}

        @Override
        public void close() {}

        /**
         *  Counts by level as well as by text. A message demoted to FINE or FINEST is, in any
         *  ordinary deployment, no message at all -- so the tests that care whether c3p0 SAID
         *  something have to care at what volume.
         */
        synchronized int countAtOrAbove( Level level, String substring )
        {
            int out = 0;
            for ( int i = 0, len = messages.size(); i < len; ++i )
                if ( levels.get( i ).intValue() >= level.intValue()
                     && messages.get( i ).indexOf( substring ) >= 0 ) ++out;
            return out;
        }
    }
}
