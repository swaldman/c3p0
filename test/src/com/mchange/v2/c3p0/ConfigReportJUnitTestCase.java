package com.mchange.v2.c3p0;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.mchange.v2.c3p0.cfg.C3P0Config;

import junit.framework.TestCase;

/**
 *  C3P0Registry reports the security configuration a deployment is actually running: WARNINGs
 *  for each dangerous setting that is enabled, and a FINE dump of the whole picture including
 *  all four classname whitelists and the keys each was computed from.
 *
 *  <p>A report is only as good as its accuracy, and a defect in one has no functional
 *  signature: nothing throws, no value is wrong, every other test stays green. Three such
 *  defects occurred while this was being written -- a latch that was never set, so the startup
 *  banner repeated once per DataSource; a copy-pasted call, so two differently-labelled lines
 *  reported the same whitelist; and a null test against an already-normalized array, so every
 *  refresh claimed both overrides and backstops whatever it was passed. None of them could have
 *  been caught except by reading the log, which is what these tests do.</p>
 *
 *  <p>This works because the test module compiles into the library's own package, so the
 *  report's two latches are reachable and resettable, and because the log backend is pinned to
 *  java.util.logging in the test configuration, so a Handler captures deterministically.
 *  No database is needed.</p>
 */
public class ConfigReportJUnitTestCase extends TestCase
{
    private final static String REGISTRY_LOGGER = C3P0Registry.class.getName();
    private final static String CONFIG_LOGGER   = C3P0Config.class.getName();

    /** Captures records from one named logger, restoring its level and handlers afterward. */
    private static final class Capture implements AutoCloseable
    {
        private final Logger          logger;
        private final Level           priorLevel;
        private final Handler         handler;
        private final List<LogRecord> records = new ArrayList<LogRecord>();

        Capture( String loggerName )
        {
            this.logger     = Logger.getLogger( loggerName );
            this.priorLevel = logger.getLevel();
            this.handler    = new Handler() {
                @Override public void publish( LogRecord r ) { synchronized ( records ) { records.add( r ); } }
                @Override public void flush() {}
                @Override public void close() {}
            };
            handler.setLevel( Level.ALL );
            logger.setLevel( Level.ALL );   // the logger's own level gates before any handler
            logger.addHandler( handler );
        }

        List<String> messagesAtLeast( Level min )
        {
            List<String> out = new ArrayList<String>();
            synchronized ( records )
            {
                for ( LogRecord r : records )
                    if ( r.getLevel().intValue() >= min.intValue() && r.getMessage() != null )
                        out.add( r.getMessage() );
            }
            return out;
        }

        @Override
        public void close()
        {
            logger.removeHandler( handler );
            logger.setLevel( priorLevel );
        }
    }

    @Override
    protected void setUp()
    {
        // both latches are package-private statics; the report is once-per-JVM without this
        C3P0Registry.banner_printed             = false;
        C3P0Registry.current_config_info_printed = false;
    }

    @Override
    protected void tearDown()
    { C3P0Config.refreshMainConfig(); }

    private static ComboPooledDataSource registered( String token ) throws Exception
    {
        ComboPooledDataSource cpds = new ComboPooledDataSource();
        cpds.setIdentityToken( token );
        C3P0Registry.reregister( cpds );
        return cpds;
    }

    /**
     *  The banner names the version and build, which is startup information, not per-DataSource
     *  information. It latches, and the latch is what repeated when it was dropped.
     */
    public void testTheBannerAppearsOnceHoweverManyDataSourcesRegister() throws Exception
    {
        List<ComboPooledDataSource> made = new ArrayList<ComboPooledDataSource>();
        try ( Capture cap = new Capture( REGISTRY_LOGGER ) )
        {
            for ( int i = 0; i < 3; ++i )
                made.add( registered( "banner-probe-" + i ) );

            int banners = 0;
            for ( String msg : cap.messagesAtLeast( Level.INFO ) )
                if ( msg.startsWith( "Initializing c3p0-" ) ) ++banners;

            assertEquals( "The startup banner must appear exactly once, however many DataSources " +
                          "register. More than one means the banner_printed latch is not being set.",
                          1, banners );
        }
        finally
        { for ( ComboPooledDataSource cpds : made ) try { cpds.close(); } catch ( Exception e ) {} }
    }

    /**
     *  Four whitelists, four base keys. Each dump line must report its own whitelist -- the
     *  labels are composed here, while the key names come from the whitelist managers, so a
     *  line querying the wrong manager still reads plausibly.
     */
    public void testTheDumpReportsFourWhitelistsFromFourDistinctKeySets() throws Exception
    {
        ComboPooledDataSource cpds = null;
        try ( Capture cap = new Capture( REGISTRY_LOGGER ) )
        {
            cpds = registered( "dump-probe" );

            String dump = null;
            for ( String msg : cap.messagesAtLeast( Level.FINE ) )
                if ( msg.startsWith( "Selected DataSource-independent config dump" ) ) dump = msg;

            assertNotNull( "The FINE config dump should have been logged on registration.", dump );

            // each whitelist's WhitelistInfo renders "... (computed from keys: [...])"
            Matcher     m    = Pattern.compile( "computed from keys: \\[([^\\]]*)\\]" ).matcher( dump );
            Set<String> keySets = new LinkedHashSet<String>();
            int         lines   = 0;
            while ( m.find() ) { ++lines; keySets.add( m.group( 1 ) ); }

            assertEquals( "The dump should report all four whitelists: " + dump, 4, lines );
            assertEquals( "Each whitelist must be computed from its own keys. Identical key sets mean " +
                          "a line is querying another whitelist's manager: " + keySets,
                          4, keySets.size() );

            // and each reported key set should belong to the base key its label names
            assertTrue( "byNameInstantiation line should name its own key: " + dump,
                        dump.contains( "ByNameInstantiation whitelist" ) );
            for ( String expected : new String[] { "com.mchange.v2.reflect.byNameInstantiation.whitelist",
                                                   "com.mchange.v2.naming.objectFactory.whitelist",
                                                   "com.mchange.v2.naming.referenceableJavaBeanClass.whitelist",
                                                   "com.mchange.v2.naming.securelyStringifiable.whitelist" } )
                assertTrue( "The dump should show a key from the " + expected + " family: " + dump,
                            dump.contains( expected ) );
        }
        finally
        { if ( cpds != null ) try { cpds.close(); } catch ( Exception e ) {} }
    }

    /** The refresh notice must describe what was actually supplied, and nothing else. */
    public void testTheRefreshNoticeReportsOnlyWhatWasSupplied() throws Exception
    {
        Properties p = new Properties();
        p.setProperty( "com.mchange.v2.c3p0.test.configReportProbe", "x" );

        try ( Capture cap = new Capture( CONFIG_LOGGER ) )
        {
            C3P0Config.refreshMainConfig( p, "overridesOnly" );
            assertNoticeSays( cap, "overridesOnly", true, false );
        }

        try ( Capture cap = new Capture( CONFIG_LOGGER ) )
        {
            C3P0Config.refreshMainConfig( (Properties) null, null, p, "backstopsOnly" );
            assertNoticeSays( cap, "backstopsOnly", false, true );
        }

        try ( Capture cap = new Capture( CONFIG_LOGGER ) )
        {
            C3P0Config.refreshMainConfig( p, "bothOverrides", p, "bothBackstops" );
            assertNoticeSays( cap, "bothOverrides", true, true );
        }
    }

    private static void assertNoticeSays( Capture cap, String descriptionPresent,
                                          boolean expectOverrides, boolean expectBackstops )
    {
        String notice = null;
        for ( String msg : cap.messagesAtLeast( Level.INFO ) )
            if ( msg.startsWith( "c3p0 main configuration was refreshed" ) ) notice = msg;

        assertNotNull( "A refresh should log an INFO notice.", notice );
        assertTrue( "The notice should carry the supplied description: " + notice,
                    notice.contains( descriptionPresent ) );
        assertEquals( "The notice should mention overrides only when overrides were supplied: " + notice,
                      expectOverrides, notice.contains( "with overrides specified" ) );
        assertEquals( "The notice should mention backstops only when backstops were supplied: " + notice,
                      expectBackstops, notice.contains( "with backstops specified" ) );
    }
}
