package com.mchange.v2.c3p0.cfg;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import junit.framework.TestCase;

import com.mchange.v2.c3p0.impl.C3P0Defaults;
import com.mchange.v2.cfg.MultiPropertiesConfig;
import com.mchange.v2.cfg.WhitelistInfo;
import com.mchange.v2.reflect.ByNameInstantiationUtils;

/**
 *  The by-name instantiation whitelists c3p0 ships in <code>/c3p0-default.properties</code>.
 *
 *  <p>These are long, hand-maintained, comma-separated lists continued across many lines with
 *  trailing backslashes, which is a shape that fails quietly. A dropped backslash truncates a list
 *  at that line; a doubled comma contributes an empty entry; a mistyped subkey makes a whole group
 *  vanish. None of those is a parse error, so nothing reports them -- the only visible symptom
 *  would be a deployment whose driver stops being whitelisted, and in 0.15.0, with enforcement off
 *  by default, not even that: just a warning nobody reads until the default changes.</p>
 *
 *  <p>So these assertions are mostly about the <i>file</i> rather than about behaviour. They are
 *  deliberately not a fixed inventory: the driver lists are expected to grow, and a test that had
 *  to be updated for every added driver would be edited without being read. What is pinned is that
 *  the keys exist, that every entry is well formed, that nothing is listed twice, that a handful of
 *  load-bearing names survive, and that what the file says actually reaches the effective
 *  whitelist.</p>
 *
 *  <p>Read from the shipped resource specifically, not from the effective whitelist, so the
 *  assertions describe what c3p0 distributes rather than whatever the test fixture adds on top of
 *  it.</p>
 */
public class ShippedWhitelistsJUnitTestCase extends TestCase
{
    private final static String SHIPPED_DEFAULTS = "/c3p0-default.properties";

    private final static String PFX      = "com.mchange.v2.reflect.byNameInstantiation.whitelist";
    private final static String INTERNAL = PFX + ".c3p0-internal";
    private final static String COMMON   = PFX + ".c3p0-internal-common-jdbc-drivers";
    private final static String LEGACY   = PFX + ".c3p0-internal-legacy-jdbc-drivers";

    private final static String[] ALL_KEYS = { INTERNAL, COMMON, LEGACY };

    /** Permissive on purpose: this is a well-formedness check, not a taste check. */
    private final static String FQCN_REGEX =
        "[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*";

    private static Properties shipped()
    {
        MultiPropertiesConfig mpc = C3P0Config.getMultiPropertiesConfig();
        Properties out = mpc.getPropertiesByResourcePath( SHIPPED_DEFAULTS );
        assertNotNull( "c3p0 must be reading its own '" + SHIPPED_DEFAULTS + "' -- it is named in "
                           + "the compiled-in source list, so its absence here means either that "
                           + "the resource is off the CLASSPATH or that the list stopped naming it. "
                           + "Sources actually read: " + Arrays.asList( mpc.getPropertiesResourcePaths() ),
                       out );
        return out;
    }

    /** The raw, untrimmed elements of one key, exactly as the properties file spells them. */
    private static List<String> rawEntries( Properties shipped, String key )
    {
        String value = shipped.getProperty( key );
        assertNotNull( "the shipped defaults must define '" + key + "'. A key that disappears -- "
                           + "renamed, mistyped, or lost to an editing slip -- silently removes "
                           + "every name it contributed, since whitelist subkeys merely union.",
                       value );
        // split(",", -1) rather than split(","): the one-arg form discards trailing empty
        // strings, which is exactly the evidence that a continuation backslash went missing and
        // left the value ending in a comma.
        return Arrays.asList( value.split( ",", -1 ) );
    }

    private static List<String> entries( Properties shipped, String key )
    {
        List<String> out = new ArrayList<String>();
        for ( String raw : rawEntries( shipped, key ) ) out.add( raw.trim() );
        return out;
    }

    private static Set<String> allShippedEntries()
    {
        Properties shipped = shipped();
        Set<String> out = new HashSet<String>();
        for ( int i = 0; i < ALL_KEYS.length; ++i ) out.addAll( entries( shipped, ALL_KEYS[i] ) );
        return out;
    }

    private static void assertWhitelisted( String key, String fqcn )
    {
        assertTrue( "'" + fqcn + "' must stay in '" + key + "'", entries( shipped(), key ).contains( fqcn ) );
    }

    public void testTheShippedWhitelistKeysArePresentAndNonEmpty()
    {
        Properties shipped = shipped();
        for ( int i = 0; i < ALL_KEYS.length; ++i )
        {
            List<String> es = entries( shipped, ALL_KEYS[i] );
            assertFalse( "'" + ALL_KEYS[i] + "' must contribute something; an empty value is how a "
                             + "deployment deliberately disables a group, and is not what c3p0 ships",
                         es.isEmpty() || ( es.size() == 1 && es.get( 0 ).length() == 0 ) );
        }
    }

    /**
     *  Catches the failure modes of a backslash-continued comma list: an empty element from a
     *  doubled or trailing comma, and an element carrying whitespace because a line ended with a
     *  space before its comma. (Properties strips the indentation that follows a continuation, so
     *  leading space is handled for us; trailing space inside the value is not.)
     */
    public void testEveryShippedEntryIsAWellFormedClassName()
    {
        Properties shipped = shipped();
        for ( int i = 0; i < ALL_KEYS.length; ++i )
        {
            String key = ALL_KEYS[i];
            for ( String raw : rawEntries( shipped, key ) )
            {
                assertEquals( "an entry of '" + key + "' carries stray whitespace, so it will never "
                                  + "match the class name it was meant to permit: [" + raw + "]",
                              raw.trim(), raw );
                assertTrue( "'" + key + "' contains an empty entry, which means a doubled or "
                                + "trailing comma in the shipped file",
                            raw.length() > 0 );
                assertTrue( "'" + raw + "' in '" + key + "' is not a well-formed class name. A list "
                                + "continued with trailing backslashes can absorb stray text without "
                                + "any parse error, so this is checked rather than assumed.",
                            raw.matches( FQCN_REGEX ) );
            }
        }
    }

    /**
     *  The accident a comma list continued with backslashes is most prone to, caught by its
     *  side effect rather than by its symptom.
     *
     *  <p>Drop one trailing backslash and the value simply ends there -- no parse error, no empty
     *  element once trailing ones are accounted for, just a shorter whitelist. But the lines that
     *  were meant to continue it are then read as property definitions of their own, so the file
     *  acquires a key that is really a fragment of a class-name list. Keys cannot legitimately
     *  contain commas, backslashes or whitespace, so finding one that does identifies the break
     *  wherever in the file it happened -- in a whitelist here, or in any other list this file
     *  grows later.</p>
     */
    public void testNoShippedKeyLooksLikeABrokenContinuation()
    {
        for ( String key : shipped().stringPropertyNames() )
        {
            String offending = null;
            if ( key.indexOf( ',' ) >= 0 )       offending = "a comma";
            else if ( key.indexOf( '\\' ) >= 0 ) offending = "a backslash";
            else
                for ( int i = 0, len = key.length(); i < len && offending == null; ++i )
                    if ( Character.isWhitespace( key.charAt( i ) ) ) offending = "whitespace";

            assertNull( "the shipped defaults contain a key holding " + offending + ", which is "
                            + "what a dropped continuation backslash leaves behind -- the rest of "
                            + "some list got read as a key instead of as values. Key: [" + key + "]",
                        offending );
        }
    }

    /**
     *  Subkeys union, so a duplicate changes nothing functionally -- which is exactly why it would
     *  never be noticed. It does mean two places to edit when a name has to be removed, and a
     *  removal that misses one is a whitelist entry that outlives the decision to drop it.
     */
    public void testNoShippedEntryIsListedTwice()
    {
        Properties shipped = shipped();
        Set<String> seen = new HashSet<String>();
        Set<String> duplicated = new TreeSet<String>();
        for ( int i = 0; i < ALL_KEYS.length; ++i )
            for ( String entry : entries( shipped, ALL_KEYS[i] ) )
                if ( ! seen.add( entry ) ) duplicated.add( entry );

        assertEquals( "no class name should appear in more than one shipped whitelist subkey; "
                          + "duplicated: " + duplicated,
                      Collections.<String>emptySet(), duplicated );
    }

    /**
     *  A few anchors, chosen because they are the values most likely to be sitting in a real
     *  <code>driverClass</code> today. The lists may grow freely; losing one of these to an
     *  editing accident would break a large share of deployments the day enforcement becomes the
     *  default.
     */
    public void testTheMostCommonDriverSpellingsAreWhitelisted()
    {
        assertWhitelisted( COMMON, "org.postgresql.Driver" );
        assertWhitelisted( COMMON, "com.mysql.cj.jdbc.Driver" );
        assertWhitelisted( COMMON, "org.mariadb.jdbc.Driver" );
        assertWhitelisted( COMMON, "oracle.jdbc.OracleDriver" );
        assertWhitelisted( COMMON, "com.microsoft.sqlserver.jdbc.SQLServerDriver" );
        assertWhitelisted( COMMON, "org.h2.Driver" );
    }

    /**
     *  The renamed-driver spellings matter as much as the current ones, and arguably more: c3p0
     *  supports very old applications, and these two are probably the most widely copied
     *  <code>driverClass</code> values in existence.
     */
    public void testTheSupersededDriverSpellingsAreWhitelisted()
    {
        assertWhitelisted( LEGACY, "oracle.jdbc.driver.OracleDriver" );
        assertWhitelisted( LEGACY, "com.mysql.jdbc.Driver" );
    }

    /**
     *  The one entry whose removal breaks every pool rather than some deployments.
     *
     *  <p><code>C3P0Defaults.taskRunnerFactoryClassName()</code> is not null, and
     *  <code>C3P0Registry.getDefaultTaskRunnerFactory()</code> hands it to the <i>gated</i>
     *  instantiation path. So an ordinary pool, configured by nobody, reaches the whitelist with
     *  this name on startup. Under enforcement a whitelist that omits it refuses c3p0's own
     *  default, and the refusal is caught and reported as a warning while the pool quietly falls
     *  back -- so the symptom is a log line, not a failure.</p>
     */
    public void testTheDefaultTaskRunnerFactoryIsWhitelisted()
    {
        String dflt = C3P0Defaults.taskRunnerFactoryClassName();
        assertNotNull( "precondition: this test is about a default that goes through the by-name "
                           + "gate. If taskRunnerFactoryClassName() has become null, no name is "
                           + "instantiated by default and there is nothing here to whitelist.",
                       dflt );
        assertTrue( "c3p0's own default TaskRunnerFactory, '" + dflt + "', must be whitelisted: "
                        + "every pool reaches the by-name gate with it, so a whitelist without it "
                        + "refuses c3p0's default under enforcement",
                    allShippedEntries().contains( dflt ) );
    }

    /**
     *  That the file's contents actually arrive. The subkey union is resolved by key <i>prefix</i>,
     *  so a subkey spelled under the wrong base -- <code>byNameInstantiation.whitelists</code>,
     *  say -- parses perfectly and contributes nothing at all.
     */
    public void testEveryShippedEntryReachesTheEffectiveWhitelist()
    {
        WhitelistInfo info = ByNameInstantiationUtils.currentWhitelistInfo( C3P0Config.getMultiPropertiesConfig() );

        assertEquals( "precondition: this compares the shipped file against the effective whitelist, "
                          + "which only follows if the main whitelist keys are what governs. An "
                          + "override whitelist replaces them outright, and a deprecated key narrows "
                          + "them, so under either this test proves nothing. Source in effect: "
                          + info.getSource(),
                      WhitelistInfo.Source.MAIN_WHITELIST, info.getSource() );

        Set<String> allShipped = allShippedEntries();
        assertFalse( "precondition: there must be shipped entries to compare, or this assertion "
                         + "passes by having nothing to check",
                     allShipped.isEmpty() );

        Set<String> effective = info.getWhitelist();
        Set<String> missing = new TreeSet<String>();
        for ( String entry : allShipped )
            if ( ! effective.contains( entry ) ) missing.add( entry );

        assertEquals( "every name c3p0 ships must survive into the effective whitelist. Missing "
                          + "names mean a subkey that is being parsed but not collected -- most "
                          + "likely spelled under a base key the whitelist machinery does not scan. "
                          + "Keys collected: " + info.getFromKeys(),
                      Collections.<String>emptySet(), missing );
    }
}
