package com.mchange.v2.c3p0.test.cfgwiden;

import com.mchange.v2.c3p0.cfg.C3P0Config;
import com.mchange.v2.c3p0.test.junit.ChildJvmProbeRunner;
import com.mchange.v2.cfg.PropertiesConfig;
import com.mchange.v2.cfg.SealedSystemProperties;
import com.mchange.v2.cfg.WhitelistInfo;
import com.mchange.v2.reflect.ByNameInstantiationUtils;
import com.mchange.v2.reflect.InstantiationNotPermittedException;

/**
 *  Child-JVM probe for whether a System property set after configuration has been read can widen
 *  the by-name instantiation whitelist. See {@link ConfigWideningJUnitTestCase} for what each
 *  answer is for.
 *
 *  <p>A child JVM per case, unavoidably. System properties are sealed once per process, c3p0's
 *  configuration is memoized in a static, and this probe deliberately writes a System property and
 *  forces a configuration refresh &mdash; three JVM-global effects that would follow the rest of
 *  the suite around for the life of the process.</p>
 */
public final class ConfigWideningProbe
{
    /**
     *  A whitelist subkey nothing configures. That it is a <i>new</i> subkey is the whole point:
     *  the sealed snapshot names no counterpart, so there is nothing for the per-key intersection
     *  between System properties and supplied configuration to narrow it against, and the live
     *  value stands alone.
     */
    public final static String LATE_KEY =
        "com.mchange.v2.reflect.byNameInstantiation.whitelist.lateSetByProbe";

    /** A name no whitelist in c3p0 or in the test fixtures contains. */
    public final static String EVIL = "com.example.WidenedInByALateSystemProperty";

    /** Query asking the probe to set the property but NOT to refresh configuration. */
    public final static String NO_REFRESH = "noRefresh";

    private static boolean permitted( String fqcn )
    {
        // The current configuration, re-fetched: a refresh replaces it, and asking the gate about
        // a stale one would prove nothing about what a refresh did.
        PropertiesConfig pcfg = C3P0Config.getMultiPropertiesConfig();
        try
        {
            ByNameInstantiationUtils.checkWarnThrowForInstantiateByNameGated( fqcn, pcfg );
            return true;
        }
        catch ( InstantiationNotPermittedException e )
        { return false; }
    }

    private static int whitelistSize()
    {
        WhitelistInfo info = ByNameInstantiationUtils.currentWhitelistInfo( C3P0Config.getMultiPropertiesConfig() );
        return info.getWhitelist().size();
    }

    public static void main( String[] argv )
    {
        try
        {
            String query = argv.length > 0 ? argv[0] : "widenByRefresh";

            // The first consult of c3p0's configuration, which is also what seals the System
            // properties snapshot. Everything after this is "late".
            C3P0Config.getMultiPropertiesConfig();

            ChildJvmProbeRunner.answer( "sealed", String.valueOf( SealedSystemProperties.isSealed() ) );
            ChildJvmProbeRunner.answer( "enforcing",
                String.valueOf( ByNameInstantiationUtils.isEnforcingWhitelist( C3P0Config.getMultiPropertiesConfig() ) ) );
            ChildJvmProbeRunner.answer( "permittedBefore", String.valueOf( permitted( EVIL ) ) );
            ChildJvmProbeRunner.answer( "sizeBefore", whitelistSize() );

            System.setProperty( LATE_KEY, EVIL );
            ChildJvmProbeRunner.answer( "liveSysprop", String.valueOf( System.getProperty( LATE_KEY ) != null ) );
            ChildJvmProbeRunner.answer( "permittedAfterSetProperty", String.valueOf( permitted( EVIL ) ) );

            if ( ! NO_REFRESH.equals( query ) )
            {
                // Reported rather than thrown: a refusal to refresh is a legitimate outcome here
                // (that is what freezeConfig does), and letting it bubble would fail the case
                // instead of being its answer.
                try
                {
                    C3P0Config.refreshMainConfig();
                    ChildJvmProbeRunner.answer( "refreshed", "true" );
                }
                catch ( Exception e )
                {
                    ChildJvmProbeRunner.answer( "refreshed", "false" );
                    ChildJvmProbeRunner.answer( "refreshThrew", e.getClass().getName() );
                }
            }
            else
                ChildJvmProbeRunner.answer( "refreshed", "skipped" );

            ChildJvmProbeRunner.answer( "permittedAfter", String.valueOf( permitted( EVIL ) ) );
            ChildJvmProbeRunner.answer( "sizeAfter", whitelistSize() );
            ChildJvmProbeRunner.answer( "sourceAfter",
                ByNameInstantiationUtils.currentWhitelistInfo( C3P0Config.getMultiPropertiesConfig() ).getSource().name() );
        }
        catch ( Throwable t )
        {
            ChildJvmProbeRunner.answer( "threw", t.toString() );
            t.printStackTrace();
        }
    }

    private ConfigWideningProbe()
    {}
}
