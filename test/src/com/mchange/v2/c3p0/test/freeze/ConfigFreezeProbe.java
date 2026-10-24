package com.mchange.v2.c3p0.test.freeze;

import com.mchange.v2.c3p0.cfg.C3P0Config;
import com.mchange.v2.c3p0.test.junit.ChildJvmProbeRunner;
import com.mchange.v2.log.MLog;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 *  The child half of {@link ConfigFreezeJUnitTestCase}. Answers one question per process about
 *  <code>com.mchange.v2.c3p0.freezeConfig</code>.
 *
 *  <p>One question per process because the things under test are one-shot per JVM: the flag's
 *  value is read from the sealed System properties snapshot, which C3P0Config's own class
 *  initializer takes and which nothing can retake; and the "stack trace only the first time"
 *  latch is a static that never resets. A test can witness one first load and one first refusal
 *  per process, so each data point gets its own process. Same reason ConfigFinderLatchProbe and
 *  ConfigPrecedenceHarness fork.</p>
 *
 *  <p>The flag arrives as a <code>-D</code> on the child rather than a System property set from
 *  inside, which is both how a deployment would set it and the only way that works: it has to be
 *  in place before C3P0Config seals.</p>
 */
public class ConfigFreezeProbe
{
    public final static String FREEZE_KEY = "com.mchange.v2.c3p0.freezeConfig";

    public final static String OVERRIDE_REFRESH_LOG_CONFIG = "com.mchange.v2.c3p0.cfg.overrideRefreshLogConfig";

    /**
     *  Deliberately not a c3p0 property. What a freeze protects is the whole configuration, and
     *  reading a plain key straight back out of the MultiPropertiesConfig observes that directly,
     *  without also depending on how c3p0 extracts bean properties from it.
     */
    public final static String PROBE_KEY = "com.mchange.v2.c3p0.test.freezeProbe";

    /** And one that is, so the bean-level view is covered too. */
    public final static String POOL_SIZE_KEY = "c3p0.maxPoolSize";

    private final static String REFUSAL = "has been frozen";

    /** AbstractBooleanProperty's unconfigured-weak-default notice, as it would name this flag. */
    private final static String FREEZE_NAG = "'" + FREEZE_KEY + "' has not been set";

    /** The same notice about anything else. Counted so the case above cannot pass vacuously. */
    private final static String ANY_NAG = "less secure value";

    private final static Capture capture = new Capture();

    public static void main( String[] argv )
    {
        try
        {
            // Must be installed before C3P0Config is touched: its class initializer performs the
            // first config load, and whether THAT is refused is one of the questions here.
            Logger mchange = Logger.getLogger( "com.mchange.v2" );
            mchange.setLevel( Level.ALL );
            mchange.addHandler( capture );

            String query = argv.length > 0 ? argv[0] : "refreshRefused";

            if ( "refreshRefused".equals( query ) )
                refreshWithOverrides();
            else if ( "refreshAccepted".equals( query ) )
                refreshWithOverrides();
            else if ( "repeatedRefusals".equals( query ) )
                repeatedRefusals();
            else if ( "startupStillLoads".equals( query ) )
                startupStillLoads();
            else if ( "freezeFromOverrides".equals( query ) )
                freezeFromOverrides();
            else if ( "logConfigPropagation".equals( query ) )
                logConfigPropagation();
            else if ( "unsetFlagIsQuiet".equals( query ) )
                unsetFlagIsQuiet();
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

    /**
     *  THE FEATURE, and its complement. One refresh carrying overrides, reported in enough
     *  detail that the parent can assert either outcome: frozen, the overrides must not land and
     *  the refusal must be said; unfrozen, the identical refresh must land and nothing be
     *  refused. The same code answers both queries, so neither case can pass because the refresh
     *  it describes was never actually attempted.
     */
    private static void refreshWithOverrides()
    {
        touchConfig();
        answer( "probeBefore", probeValue() );
        answer( "poolSizeBefore", poolSize() );

        Properties overrides = new Properties();
        overrides.setProperty( PROBE_KEY, "from-override" );
        overrides.setProperty( POOL_SIZE_KEY, "37" );
        C3P0Config.refreshMainConfig( overrides, "ConfigFreezeProbe refreshWithOverrides" );

        answer( "probeAfter", probeValue() );
        answer( "poolSizeAfter", poolSize() );
        answer( "refusals", capture.countAtOrAbove( Level.WARNING, REFUSAL ) );
    }

    /**
     *  A deployment that keeps calling refresh should keep being told it is being ignored -- the
     *  warning is how an operator discovers their refresh is not taking effect, so going quiet
     *  after the first would suppress exactly the thing it exists to report. The stack trace
     *  comes once, though: it identifies the caller, and the next caller is usually the same one.
     */
    private static void repeatedRefusals()
    {
        touchConfig();

        C3P0Config.refreshMainConfig();
        C3P0Config.refreshMainConfig();
        C3P0Config.refreshMainConfig();

        answer( "refusals", capture.countAtOrAbove( Level.WARNING, REFUSAL ) );
        answer( "withStackTrace", capture.countWithThrowable( REFUSAL ) );
    }

    /**
     *  A freeze must not stop c3p0 configuring itself in the first place. The first load is
     *  C3P0Config's own class initializer, which does not go through refreshMainConfig(..) at
     *  all, so there is nothing there to refuse -- and that is the design, not an accident:
     *  a flag that left c3p0 running on hardcoded defaults alone would be a worse outcome than
     *  the one it guards against.
     */
    private static void startupStillLoads()
    {
        touchConfig();
        answer( "probeValue", probeValue() );
        answer( "poolSize", poolSize() );
        answer( "refusals", capture.countAtOrAbove( Level.WARNING, REFUSAL ) );
    }

    /**
     *  THE GUARANTEE THAT MAKES freezeConfig A CONTROL RATHER THAN A SUGGESTION: the decision is
     *  made under the configuration IN FORCE, never the one being proposed. So a refresh carrying
     *  freezeConfig=false cannot unfreeze itself.
     *
     *  <p>The corollary is the mildly surprising half, and it is what makes this reachable at
     *  all: a refresh whose own overrides ESTABLISH the freeze is not refused, because at the
     *  moment of the decision those overrides are not yet config. The freeze binds from the next
     *  refresh onward. So: establish, then try to undo.</p>
     */
    private static void freezeFromOverrides()
    {
        touchConfig();

        Properties establish = new Properties();
        establish.setProperty( FREEZE_KEY, "true" );
        establish.setProperty( PROBE_KEY, "first" );
        C3P0Config.refreshMainConfig( establish, "ConfigFreezeProbe establishes the freeze" );

        answer( "probeAfterEstablishing", probeValue() );
        answer( "refusalsAfterEstablishing", capture.countAtOrAbove( Level.WARNING, REFUSAL ) );

        Properties undo = new Properties();
        undo.setProperty( FREEZE_KEY, "false" );
        undo.setProperty( PROBE_KEY, "second" );
        C3P0Config.refreshMainConfig( undo, "ConfigFreezeProbe tries to thaw" );

        answer( "probeAfterThawAttempt", probeValue() );
        answer( "refusalsAfterThawAttempt", capture.countAtOrAbove( Level.WARNING, REFUSAL ) );
    }

    /**
     *  The composition worth pinning. c3p0 can push its own configuration into MLog's, when
     *  com.mchange.v2.c3p0.cfg.overrideRefreshLogConfig is set -- and logging configuration
     *  decides what a running system records about what is happening to it. That push is the last
     *  thing refreshMainConfig(..) does, so freezing c3p0's configuration closes the route into
     *  MLog's as well: one flag, not two.
     *
     *  <p>Answers the same way whether or not the freeze is set, so the parent can assert both
     *  halves and the frozen case cannot pass vacuously.</p>
     */
    private static void logConfigPropagation()
    {
        touchConfig();
        answer( "reachedMLogBefore", String.valueOf( MLog.getConfigProperty( PROBE_KEY ) ) );

        Properties overrides = new Properties();
        overrides.setProperty( PROBE_KEY, "from-override" );
        C3P0Config.refreshMainConfig( overrides, "ConfigFreezeProbe logConfigPropagation" );

        answer( "probeAfter", probeValue() );
        answer( "reachedMLogAfter", String.valueOf( MLog.getConfigProperty( PROBE_KEY ) ) );
        answer( "refusals", capture.countAtOrAbove( Level.WARNING, REFUSAL ) );
    }

    /**
     *  Why AbstractBooleanProperty gained a warnUnconfiguredWeakDefault flag. Unfrozen is the
     *  settled default, not a transitional one, so the "has not been set ... we default to the
     *  less secure value ... THIS MAY CHANGE IN A FUTURE RELEASE" notice would be asking every
     *  deployment to act on something that is not going to change. Said often enough, it teaches
     *  readers to skim past the notices that do mean something.
     *
     *  <p>Reports notices about anything else too, because there SHOULD be one here --
     *  byNameInstantiation.enforceWhitelist is genuinely transitional and genuinely nags. Without
     *  that, silence about freezeConfig would be indistinguishable from silence about everything,
     *  which is what a broken capture, or a warnUnconfiguredWeakDefault flag that disabled the
     *  notice globally, would also look like.</p>
     */
    private static void unsetFlagIsQuiet()
    {
        touchConfig();

        C3P0Config.refreshMainConfig();
        C3P0Config.refreshMainConfig();

        answer( "freezeNags", capture.countAtOrAbove( Level.WARNING, FREEZE_NAG ) );
        answer( "anyNags", capture.countAtOrAbove( Level.WARNING, ANY_NAG ) );
        answer( "refusals", capture.countAtOrAbove( Level.WARNING, REFUSAL ) );
    }

    /** Provokes C3P0Config's class initialization, which performs the first config load. */
    private static void touchConfig()
    { C3P0Config.getMultiPropertiesConfig(); }

    private static String probeValue()
    { return String.valueOf( C3P0Config.getMultiPropertiesConfig().getProperty( PROBE_KEY ) ); }

    /** The bean-level view of the same configuration. Hardcoded defaults always answer this. */
    private static String poolSize()
    { return String.valueOf( C3P0Config.getUnspecifiedUserProperty( "maxPoolSize", null ) ); }

    private static void answer( String key, int value )
    { ChildJvmProbeRunner.answer( key, value ); }

    private static void answer( String key, String value )
    { ChildJvmProbeRunner.answer( key, value ); }

    /** Collects log records so the probe can report on what c3p0 said, and at what volume. */
    private final static class Capture extends Handler
    {
        private final List<LogRecord> records = new ArrayList<LogRecord>();

        @Override
        public synchronized void publish( LogRecord record )
        { records.add( record ); }

        @Override
        public void flush() {}

        @Override
        public void close() {}

        /**
         *  Counts by level as well as by text. A message demoted to FINE is, in any ordinary
         *  deployment, no message at all -- so a test that cares whether c3p0 SAID something has
         *  to care at what volume.
         */
        synchronized int countAtOrAbove( Level level, String substring )
        {
            int out = 0;
            for ( LogRecord r : records )
                if ( r.getLevel().intValue() >= level.intValue()
                     && String.valueOf( r.getMessage() ).indexOf( substring ) >= 0 ) ++out;
            return out;
        }

        synchronized int countWithThrowable( String substring )
        {
            int out = 0;
            for ( LogRecord r : records )
                if ( r.getThrown() != null
                     && String.valueOf( r.getMessage() ).indexOf( substring ) >= 0 ) ++out;
            return out;
        }
    }
}
