package com.mchange.v2.c3p0.test.xmlcfg;

import com.mchange.v2.c3p0.cfg.C3P0Config;
import com.mchange.v2.c3p0.test.junit.ChildJvmProbeRunner;
import com.mchange.v2.cfg.MultiPropertiesConfig;
import com.mchange.v2.log.MLog;
import com.mchange.v2.naming.ReferenceableUtils;
import com.mchange.v2.naming.SecurityConfigKey;
import com.mchange.v2.reflect.ByNameInstantiationUtils;
import com.mchange.v2.reflect.InstantiationNotPermittedException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 *  The child half of {@link XmlConfigReachJUnitTestCase}. Each process writes itself a
 *  c3p0-config.xml, points c3p0 at it, provokes the first config load, and answers one question
 *  about what the configuration then looks like through each of c3p0's two views of it.
 *
 *  <p>Forked per data point for the usual reason: which XML file c3p0 reads is decided once, from
 *  the sealed System properties snapshot taken during C3P0Config's own class initialization, and
 *  the load it feeds happens exactly once. The property is set here in main(), before anything
 *  touches C3P0Config and therefore before anything seals -- which is why this works at all, and
 *  why it has to be a fresh JVM each time.</p>
 *
 *  <p>The file is a real temp file rather than a classpath resource so each case can declare
 *  exactly what it needs. c3p0 reads either a file path or a <code>classloader:</code> resource
 *  from <code>com.mchange.v2.c3p0.cfg.xml</code>; the file path is the one a test can control.</p>
 */
public class XmlConfigReachProbe
{
    public final static String XML_CFG_KEY   = "com.mchange.v2.c3p0.cfg.xml";
    public final static String FREEZE_KEY    = "com.mchange.v2.c3p0.freezeConfig";
    /*
     *  SPELLED OUT, not read from the constants that define them, and that is load-bearing.
     *
     *  Reading ByNameInstantiationUtils.ENFORCE_WHITELIST_KEY or
     *  SecurityConfigKey.ACCEPT_DESERIALIZED_INITIAL_CONTEXT_ENVIRONMENT initializes those
     *  classes, which loads configuration, which SEALS the System properties snapshot. In a
     *  static field initializer that happens before main() runs -- and so before useXml(..) can
     *  point c3p0 at the file it just wrote. The seal would then hold no
     *  com.mchange.v2.c3p0.cfg.xml, the identifier c3p0-xml:file:${com.mchange.v2.c3p0.cfg.xml}
     *  would resolve to nothing, and every case here would silently read whatever
     *  c3p0-config.xml happens to be on the test classpath instead of its own.
     *
     *  That is not a quirk of this probe. It is the behaviour: as of 0.15.0 the XML location is
     *  resolved from the sealed snapshot, so it has to be set before anything seals -- in
     *  practice, as a -D. 0.14.x read it from loaded configuration at c3p0's own load time, which
     *  was later and more forgiving. assertKeySpellingsStillMatch() keeps these literals honest.
     */
    public final static String ENFORCE_KEY      = "com.mchange.v2.reflect.byNameInstantiation.enforceWhitelist";
    public final static String ACCEPT_DESER_KEY = "com.mchange.v2.naming.acceptDeserializedInitialContextEnvironment";
    public final static String LEGACY_TIMEOUT_KEY = "com.mchange.v2.c3p0.impl.DefaultConnectionTester.isValidTimeout";

    /** A plain pool property, so the cases can show the existing XML path still works. */
    public final static String POOL_PROP = "maxPoolSize";

    public final static String NAMED_CONFIG = "nombre";

    public final static String SYNC_REFRESH_LOG_CONFIG = "com.mchange.v2.c3p0.cfg.syncRefreshLogConfig";

    /** c3p0's internal prefix for the layer reconciliation adds. Not public API; named here to impersonate it. */
    public final static String FINDER_ADDITIONS_PFX = "C3P0_CONFIG_FINDER_ADDITIONS-";

    /** Log-namespace probe keys. Nothing ships or tests these, so any value is one we put there. */
    public final static String LOG_XML_ONLY_KEY      = "com.mchange.v2.log.c3p0XmlReachProbe.xmlOnly";
    public final static String LOG_CONTESTED_KEY     = "com.mchange.v2.log.c3p0XmlReachProbe.contested";
    public final static String LOG_OVERRIDE_ONLY_KEY = "com.mchange.v2.log.c3p0XmlReachProbe.overrideOnly";

    /** Not whitelisted anywhere, so a gated instantiation of it is refused iff we are enforcing. */
    public final static String UNWHITELISTED = "com.mchange.v2.c3p0.test.xmlcfg.NotWhitelistedAnywhere";

    private final static String REFUSAL = "has been frozen";

    private final static Capture capture = new Capture();

    public static void main( String[] argv )
    {
        try
        {
            Logger mchange = Logger.getLogger( "com.mchange.v2" );
            mchange.setLevel( Level.ALL );
            mchange.addHandler( capture );

            String query = argv.length > 0 ? argv[0] : "twoViews";

            if ( "twoViews".equals( query ) )
                twoViews();
            else if ( "whitelistEnforcement".equals( query ) )
                whitelistEnforcement();
            else if ( "namingControl".equals( query ) )
                namingControl();
            else if ( "legacyTimeout".equals( query ) )
                legacyTimeout();
            else if ( "freezeFromXml".equals( query ) )
                freezeFromXml();
            else if ( "poolProperties".equals( query ) )
                poolProperties();
            else if ( "systemPropertyNarrowsOverXml".equals( query ) )
                systemPropertyNarrowsOverXml();
            else if ( "namedScopeDoesNotLeak".equals( query ) )
                namedScopeDoesNotLeak();
            else if ( "prefixedSpellingInEveryScope".equals( query ) )
                prefixedSpellingInEveryScope();
            else if ( "misspelledKeyNamespacing".equals( query ) )
                misspelledKeyNamespacing();
            else if ( "extensionsKeysArePreserved".equals( query ) )
                extensionsKeysArePreserved();
            else if ( "warningsByScope".equals( query ) )
                warningsByScope();
            else if ( "xmlSourceCount".equals( query ) )
                xmlSourceCount();
            else if ( "refreshOverrideShadowedByXml".equals( query ) )
                refreshOverrideShadowedByXml();
            else if ( "logConfigFromXml".equals( query ) )
                logConfigFromXml();
            else
            {
                System.err.println( "Unknown query '" + query + "'." );
                System.exit( 2 );
            }

            // late, when initializing these classes can no longer seal anything early
            assertKeySpellingsStillMatch();
        }
        catch ( Throwable t )
        {
            t.printStackTrace();
            ChildJvmProbeRunner.answer( "threw", t.getClass().getName() + ": " + t.getMessage() );
        }

        System.exit( 0 );
    }

    // ------------------------------------------------------------- the divergence, stated plainly

    /**
     *  THE BUG, in its most reduced form. c3p0 treats MPCONFIG() and C3P0Config's defaultConfig as
     *  two views of one body of configuration -- the second adding named scopes and per-user
     *  overrides, otherwise the same data. For every source but one, they are. XML is the
     *  exception: DefaultC3P0ConfigFinder folds the XML into the C3P0Config it returns and nothing
     *  folds it into the MultiPropertiesConfig, which was built before the finder ran and is never
     *  revisited.
     *
     *  <p>So this reports both views of three keys of different shapes, and the parent asserts they
     *  agree.</p>
     */
    private static void twoViews() throws Exception
    {
        useXml( "<default-config>"
                    + prop( POOL_PROP, "47" )
                    + prop( ENFORCE_KEY, "true" )
                    + prop( LEGACY_TIMEOUT_KEY, "17" )
                    + "</default-config>" );

        report( "pool", POOL_PROP );
        report( "security", ENFORCE_KEY );
        report( "legacy", LEGACY_TIMEOUT_KEY );

        // The pool property's other spelling. In the MultiPropertiesConfig, pool properties live
        // under a "c3p0." prefix; in defaultConfig they are bare. That difference is deliberate
        // and is not the bug -- but it does mean this key, not the bare one, is where a pool
        // property set in XML ought to show up for allCurrentProperties() and everything else
        // that reads the MultiPropertiesConfig.
        ChildJvmProbeRunner.answer( "poolViaMpconfigPrefixed",
                                    String.valueOf( mpconfig().getProperty( "c3p0." + POOL_PROP ) ) );
    }

    /** Both views of one key, named so the parent can compare them. */
    private static void report( String label, String key )
    {
        ChildJvmProbeRunner.answer( label + "ViaMpconfig",
                                    String.valueOf( mpconfig().getProperty( key ) ) );
        ChildJvmProbeRunner.answer( label + "ViaDefaultConfig",
                                    String.valueOf( C3P0Config.getUnspecifiedUserProperty( key, null ) ) );
    }

    // ------------------------------------------------------------- what the divergence costs

    /**
     *  WHY THIS IS MORE THAN AN INCONSISTENCY. Every reader of a
     *  <code>com.mchange.v2.*</code> control takes MPCONFIG() as its PropertiesConfig, so a
     *  deployment that turns the by-name instantiation whitelist ON in its c3p0-config.xml gets no
     *  enforcement at all, and no diagnostic saying so -- just the "Unknown c3p0-config property"
     *  warning, which reads like a typo rather than like a security control being dropped.
     *
     *  <p>Reports the behaviour as well as the flag. A fix that made isEnforcingWhitelist(..)
     *  answer true without the gate actually closing would be worse than the bug.</p>
     */
    private static void whitelistEnforcement() throws Exception
    {
        useXml( "<default-config>" + prop( ENFORCE_KEY, "true" ) + "</default-config>" );

        ChildJvmProbeRunner.answer( "enforcing",
                                    String.valueOf( ByNameInstantiationUtils.isEnforcingWhitelist( mpconfig() ) ) );

        String refused;
        try
        {
            ByNameInstantiationUtils.checkWarnThrowForInstantiateByNameGated( UNWHITELISTED, mpconfig() );
            refused = "false";
        }
        catch ( InstantiationNotPermittedException e )
        { refused = "true"; }
        ChildJvmProbeRunner.answer( "gateRefusedUnwhitelisted", refused );
    }

    /**
     *  A second control, in a different subsystem, reached by a different accessor, so that a fix
     *  which special-cased one key could not satisfy both. This one is in the LOOSENING direction
     *  -- the XML asks c3p0 to accept a deserialized JNDI InitialContext environment -- which is
     *  equally a thing a deployment may say and equally a thing that should not be silently
     *  dropped. Config that is ignored is dangerous in both directions: here, an operator who
     *  believes they have had to opt into something has in fact never been opted in, and will
     *  conclude the control does nothing.
     */
    private static void namingControl() throws Exception
    {
        useXml( "<default-config>" + prop( ACCEPT_DESER_KEY, "true" ) + "</default-config>" );

        ChildJvmProbeRunner.answer( "accepting",
                                    String.valueOf( ReferenceableUtils.acceptDeserializedInitialContextEnvironment( mpconfig() ) ) );
    }

    /**
     *  THE SHAPE OF THE KEY, which is the whole of what separates this family from the pool
     *  properties. A fully-qualified config key is already in the properties-style view's own
     *  namespace: it belongs there verbatim, and every reader of one looks it up verbatim --
     *  DefaultConnectionTester reads this one straight off the MultiPropertiesConfig. Under a
     *  "c3p0." prefix it is present and unreachable, which is indistinguishable from absent for
     *  every consumer while looking, in a config dump, like it worked.
     *
     *  <p>So both spellings are reported: the bare key must be set and the prefixed one must not.
     *  Not a security control, so a fix for this is not mistaken for a security patch.</p>
     */
    private static void legacyTimeout() throws Exception
    {
        useXml( "<default-config>" + prop( LEGACY_TIMEOUT_KEY, "17" ) + "</default-config>" );

        ChildJvmProbeRunner.answer( "bare",
                                    String.valueOf( mpconfig().getProperty( LEGACY_TIMEOUT_KEY ) ) );
        ChildJvmProbeRunner.answer( "prefixed",
                                    String.valueOf( mpconfig().getProperty( "c3p0." + LEGACY_TIMEOUT_KEY ) ) );
    }

    /**
     *  The freeze, the newest of these controls and the one whose silence is hardest to notice: a
     *  deployment that pins its configuration in c3p0-config.xml is not pinned, and the only
     *  evidence is the absence of a warning that would not have been printed anyway.
     */
    private static void freezeFromXml() throws Exception
    {
        useXml( "<default-config>" + prop( FREEZE_KEY, "true" ) + "</default-config>" );

        Properties overrides = new Properties();
        overrides.setProperty( "c3p0." + POOL_PROP, "37" );
        C3P0Config.refreshMainConfigWithOverrides( overrides, "XmlConfigReachProbe freezeFromXml" );

        ChildJvmProbeRunner.answer( "poolPropAfterRefresh", poolProp( null ) );
        ChildJvmProbeRunner.answer( "refusals", capture.countAtOrAbove( Level.WARNING, REFUSAL ) );
    }

    // ------------------------------------------------------------- guards on any fix

    /**
     *  Passes today and must keep passing. Pool properties are what c3p0-config.xml is chiefly
     *  for, and they arrive through defaultConfig, unprefixed, with named scopes and per-user
     *  overrides layered on. Whatever folds XML into the MultiPropertiesConfig must leave that
     *  path exactly as it is -- including the <code>c3p0.</code> prefix, which the two views spell
     *  differently on purpose.
     */
    private static void poolProperties() throws Exception
    {
        useXml( "<default-config>" + prop( POOL_PROP, "47" ) + "</default-config>"
                    + "<named-config name=\"" + NAMED_CONFIG + "\">" + prop( POOL_PROP, "11" ) + "</named-config>" );

        ChildJvmProbeRunner.answer( "defaultScope", poolProp( null ) );
        ChildJvmProbeRunner.answer( "namedScope", poolProp( NAMED_CONFIG ) );
        ChildJvmProbeRunner.answer( "unprefixedViaMpconfig", String.valueOf( mpconfig().getProperty( POOL_PROP ) ) );
        ChildJvmProbeRunner.answer( "prefixedViaMpconfig", String.valueOf( mpconfig().getProperty( "c3p0." + POOL_PROP ) ) );
    }

    /**
     *  THE CONSTRAINT A FIX MUST NOT BREAK. c3p0's settled policy is earliest-or-narrowest: a
     *  System property is captured before application code runs and so is taken as the operator's
     *  word, and where two sources disagree the safer reading wins. Folding XML into the
     *  configuration must not give a file on the classpath a way to loosen a control an operator
     *  set on the command line.
     *
     *  <p>Both directions, since the policy is "narrowest wins" and not a precedence ladder:</p>
     *  <ul>
     *    <li>XML asks to accept deserialized InitialContext environments, the System property
     *        refuses. The refusal must stand. (Passes today, because the XML is ignored
     *        altogether -- for the wrong reason.)</li>
     *    <li>XML asks to enforce the whitelist, the System property declines to. The XML must
     *        win, because it is the narrower of the two. (Fails today.)</li>
     *  </ul>
     */
    private static void systemPropertyNarrowsOverXml() throws Exception
    {
        useXml( "<default-config>"
                    + prop( ACCEPT_DESER_KEY, "true" )
                    + prop( ENFORCE_KEY, "true" )
                    + "</default-config>" );

        ChildJvmProbeRunner.answer( "accepting",
                                    String.valueOf( ReferenceableUtils.acceptDeserializedInitialContextEnvironment( mpconfig() ) ) );
        ChildJvmProbeRunner.answer( "enforcing",
                                    String.valueOf( ByNameInstantiationUtils.isEnforcingWhitelist( mpconfig() ) ) );
    }

    /**
     *  Passes today and must keep passing. A named config is a per-DataSource scope; the
     *  MultiPropertiesConfig is global, and every reader of a <code>com.mchange.v2.*</code> key
     *  reads it statically, with no DataSource in hand. So a control named inside a
     *  <code>named-config</code> has no coherent global meaning and must not be promoted into
     *  one -- a fix that folded every scope's properties in would let an unrelated named config
     *  silently loosen a control for the whole JVM.
     */
    private static void namedScopeDoesNotLeak() throws Exception
    {
        useXml( "<default-config>" + prop( POOL_PROP, "47" ) + "</default-config>"
                    + "<named-config name=\"" + NAMED_CONFIG + "\">"
                    + prop( ACCEPT_DESER_KEY, "true" )
                    + prop( ENFORCE_KEY, "true" )
                    + "</named-config>" );

        ChildJvmProbeRunner.answer( "acceptDeserViaMpconfig",
                                    String.valueOf( mpconfig().getProperty( ACCEPT_DESER_KEY ) ) );
        ChildJvmProbeRunner.answer( "enforceViaMpconfig",
                                    String.valueOf( mpconfig().getProperty( ENFORCE_KEY ) ) );
        ChildJvmProbeRunner.answer( "accepting",
                                    String.valueOf( ReferenceableUtils.acceptDeserializedInitialContextEnvironment( mpconfig() ) ) );
        ChildJvmProbeRunner.answer( "enforcing",
                                    String.valueOf( ByNameInstantiationUtils.isEnforcingWhitelist( mpconfig() ) ) );
    }

    // ------------------------------------------------------------- the two views, key by key

    /**
     *  THE DIVERGENCE THAT IS NOT ABOUT NAMESPACES BUT ABOUT SPELLING, in all three scopes that
     *  hold pool properties.
     *
     *  <p>Nothing documents <code>&lt;property name="c3p0.maxPoolSize"&gt;</code> inside XML --
     *  the <code>c3p0.</code> prefix belongs to the properties-style view, not to the XML, where
     *  the surrounding element already says which scope you are in. But people write it, because
     *  it is what they typed in c3p0.properties yesterday, and it has to resolve the same way in
     *  both views or one of them is lying about what the pool will actually run with.</p>
     *
     *  <p>Reports both views of each scope, because a fix that reaches only one of them is the
     *  original bug with the sign flipped: the properties view would report a setting that the
     *  pool, which reads the bean view, never applies.</p>
     */
    private static void prefixedSpellingInEveryScope() throws Exception
    {
        useXml( "<default-config>"
                    + prop( "c3p0.minPoolSize", "4" )
                    + "<user-overrides user=\"steve\">" + prop( "c3p0.maxPoolSize", "20" ) + "</user-overrides>"
                    + "</default-config>"
                    + "<named-config name=\"" + NAMED_CONFIG + "\">"
                    + prop( "c3p0.acquireIncrement", "7" )
                    + "</named-config>" );

        ChildJvmProbeRunner.answer( "defaultViaMpconfig",
                                    String.valueOf( mpconfig().getProperty( "c3p0.minPoolSize" ) ) );
        ChildJvmProbeRunner.answer( "defaultViaBean",
                                    String.valueOf( C3P0Config.getUnspecifiedUserProperty( "minPoolSize", null ) ) );

        ChildJvmProbeRunner.answer( "namedViaMpconfig",
                                    String.valueOf( mpconfig().getProperty( "c3p0.named-configs." + NAMED_CONFIG + ".acquireIncrement" ) ) );
        ChildJvmProbeRunner.answer( "namedViaBean",
                                    String.valueOf( C3P0Config.getUnspecifiedUserProperty( "acquireIncrement", NAMED_CONFIG ) ) );

        ChildJvmProbeRunner.answer( "userOverrideViaMpconfig",
                                    String.valueOf( mpconfig().getProperty( "c3p0.user-overrides.steve.maxPoolSize" ) ) );
        ChildJvmProbeRunner.answer( "userOverrideDoubledPrefix",
                                    String.valueOf( mpconfig().getProperty( "c3p0.user-overrides.steve.c3p0.maxPoolSize" ) ) );
    }

    /**
     *  A misspelled pool property is nobody's supported configuration, but it still has to land
     *  somewhere sane. c3p0's own property names live under <code>c3p0.</code>; promoting an
     *  unrecognized bare name into the top-level namespace instead would put a typo next to the
     *  <code>com.mchange.*</code> keys, where a later reader of a config dump cannot tell the two
     *  kinds of thing apart.
     */
    private static void misspelledKeyNamespacing() throws Exception
    {
        useXml( "<default-config>" + prop( "maxPoolSiz", "12" ) + "</default-config>" );

        ChildJvmProbeRunner.answer( "prefixed", String.valueOf( mpconfig().getProperty( "c3p0.maxPoolSiz" ) ) );
        ChildJvmProbeRunner.answer( "bare", String.valueOf( mpconfig().getProperty( "maxPoolSiz" ) ) );
    }

    /**
     *  THE ONE PLACE THE PREFIX CORRECTION MUST NOT REACH. An extensions key is a name the
     *  deployment invented, for its own code to read back through getExtensions(..); c3p0 assigns
     *  it no meaning and has no vocabulary of valid ones. So <code>c3p0.initSql</code> is a
     *  perfectly good extension name, and silently rewriting it to <code>initSql</code> changes
     *  the key the deployment's own code is looking for.
     *
     *  <p>Reports a plain key alongside it, so a fix that preserved the prefixed one by breaking
     *  extensions generally would still be caught.</p>
     */
    private static void extensionsKeysArePreserved() throws Exception
    {
        // Names nothing else can supply. An external XML file no longer REPLACES a
        // /c3p0-config.xml on the classpath -- it layers over it -- so a probe that used
        // 'initSql' would read the test classpath's value and call it its own.
        useXml( "<default-config><extensions>"
                    + prop( "c3p0.probeInitSql", "SET SCHEMA 'prefixed'" )
                    + prop( "probePlainKey", "plainValue" )
                    + "</extensions></default-config>" );

        Map extensions = C3P0Config.getExtensions( null );
        ChildJvmProbeRunner.answer( "prefixedKey", String.valueOf( extensions.get( "c3p0.probeInitSql" ) ) );
        ChildJvmProbeRunner.answer( "correctedAwayTo", String.valueOf( extensions.get( "probeInitSql" ) ) );
        ChildJvmProbeRunner.answer( "plainKey", String.valueOf( extensions.get( "probePlainKey" ) ) );
    }

    /**
     *  A programmatic refresh override LOSES to c3p0-config.xml. That is deliberate and
     *  long-standing -- the finder layers the XML on top of everything else and says so, "overrides
     *  all other c3p0 config" -- and it is not what this checks. What matters is that the two views
     *  tell the same story about it: the override reaches the properties view on its way in, the
     *  XML then beats it in the bean view, and if nothing reconciled them the properties view
     *  would be left reporting a setting the pool had already discarded.
     *
     *  <p>Reports both views before and after, since "they agree" is only worth anything if the
     *  refresh actually happened.</p>
     */
    private static void refreshOverrideShadowedByXml() throws Exception
    {
        useXml( "<default-config>" + prop( POOL_PROP, "30" ) + "</default-config>" );

        ChildJvmProbeRunner.answer( "mpconfigBefore", String.valueOf( mpconfig().getProperty( "c3p0." + POOL_PROP ) ) );
        ChildJvmProbeRunner.answer( "beanBefore", String.valueOf( C3P0Config.getUnspecifiedUserProperty( POOL_PROP, null ) ) );

        Properties overrides = new Properties();
        overrides.setProperty( "c3p0." + POOL_PROP, "37" );
        C3P0Config.refreshMainConfigWithOverrides( overrides, "XmlConfigReachProbe refreshOverrideShadowedByXml" );

        ChildJvmProbeRunner.answer( "mpconfigAfter", String.valueOf( mpconfig().getProperty( "c3p0." + POOL_PROP ) ) );
        ChildJvmProbeRunner.answer( "beanAfter", String.valueOf( C3P0Config.getUnspecifiedUserProperty( POOL_PROP, null ) ) );
    }

    /**
     *  THE OTHER CONSUMER OF A FULLY-QUALIFIED KEY FROM XML: MLog. Once compound keys work in
     *  <code>default-config</code>, <code>com.mchange.v2.log.*</code> is among the namespaces a
     *  deployment can reach that way -- so with
     *  <code>com.mchange.v2.c3p0.cfg.syncRefreshLogConfig</code> set, log configuration written
     *  in c3p0-config.xml has to arrive in MLog's configuration, not merely in c3p0's.
     *
     *  <p>It arrives as a forwarded layer rather than as c3p0's whole resolved config, which is
     *  what keeps MLog's own source list free of duplicated resource paths. The layer is
     *  forwarded ABOVE any programmatic overrides, because that is c3p0's documented precedence:
     *  XML beats programmatic config. So three keys are reported:</p>
     *
     *  <ul>
     *    <li>one only the XML sets -- it must reach MLog at all;</li>
     *    <li>one both the XML and a programmatic override set -- the XML must win, in MLog
     *        exactly as in c3p0, or the forwarded layer went in at the wrong precedence;</li>
     *    <li>one only the override sets -- it must still arrive, so a fix that forwarded the XML
     *        by dropping the overrides would not pass.</li>
     *  </ul>
     *
     *  <p>And a c3p0 pool property, which must NOT arrive: MLog has no use for
     *  <code>c3p0.maxPoolSize</code>, and its presence would mean c3p0 had gone back to pushing
     *  its entire configuration across.</p>
     */
    private static void logConfigFromXml() throws Exception
    {
        useXml( "<default-config>"
                    + prop( LOG_XML_ONLY_KEY, "from-xml" )
                    + prop( LOG_CONTESTED_KEY, "xml-wins" )
                    + prop( POOL_PROP, "47" )
                    + "</default-config>" );

        ChildJvmProbeRunner.answer( "syncEnabled",
                                    String.valueOf( System.getProperty( SYNC_REFRESH_LOG_CONFIG ) != null ) );

        Properties overrides = new Properties();
        overrides.setProperty( LOG_CONTESTED_KEY, "override-wins" );   // and it does, as of 0.15.0
        overrides.setProperty( LOG_OVERRIDE_ONLY_KEY, "from-override" );
        C3P0Config.refreshMainConfigWithOverrides( overrides, "XmlConfigReachProbe logConfigFromXml" );

        ChildJvmProbeRunner.answer( "c3p0Contested",
                                    String.valueOf( mpconfig().getProperty( LOG_CONTESTED_KEY ) ) );

        ChildJvmProbeRunner.answer( "mlogXmlOnly", String.valueOf( MLog.getConfigProperty( LOG_XML_ONLY_KEY ) ) );
        ChildJvmProbeRunner.answer( "mlogContested", String.valueOf( MLog.getConfigProperty( LOG_CONTESTED_KEY ) ) );
        ChildJvmProbeRunner.answer( "mlogOverrideOnly", String.valueOf( MLog.getConfigProperty( LOG_OVERRIDE_ONLY_KEY ) ) );
        // Two kinds of c3p0 key, because they arrive by different routes. The one the XML sets
        // reaches MLog because MLog reads the same shared resource list c3p0 does -- not because
        // anything is forwarded. A hardcoded c3p0 default is not in that list and so does not
        // travel: it is c3p0's, and MLog has no business with it.
        ChildJvmProbeRunner.answer( "mlogXmlSetPoolProp", String.valueOf( MLog.getConfigProperty( "c3p0." + POOL_PROP ) ) );
        ChildJvmProbeRunner.answer( "mlogHardcodedDefault", String.valueOf( MLog.getConfigProperty( "c3p0.acquireIncrement" ) ) );
    }

    /**
     *  c3p0 finds the layer to forward by RESOURCE-PATH PREFIX, which means a caller can name an
     *  override layer with that prefix and present it as finder-found config. The branch that
     *  warns about more than one such path exists for exactly this, and this is the only way to
     *  reach it -- so it is reachable, and worth pinning rather than leaving as untested defence.
     *
     *  <p>What must hold is that the impostor does not win. c3p0's own additions layer is
     *  combined last, so it is last in the resource-path list, and "the last such path" is c3p0's
     *  own. A caller who names the prefix gets a warning and no influence over which layer is
     *  forwarded.</p>
     */
    /**
     *  How many c3p0-xml: sources actually contributed, and whether that was announced.
     *
     *  <p>Under 0.14.x there was no point having both a classpath /c3p0-config.xml and a
     *  separately named file: only one was ever read. Both are read now, the named file winning
     *  per key, which is a supported configuration -- but a deployment that added the System
     *  property and forgot to remove the resource used to get one file and now gets a merge, so
     *  it is worth saying so once. The test environment supplies a classpath c3p0-config.xml, so
     *  this query has two sources without arranging anything.</p>
     */
    private static void xmlSourceCount() throws Exception
    {
        useXml( "<default-config>" + prop( POOL_PROP, "47" ) + "</default-config>" );

        int read = 0;
        for ( String path : mpconfig().getPropertiesResourcePaths() )
            if ( path.startsWith( "c3p0-xml:" ) && mpconfig().wasRead( path ) ) ++read;

        ChildJvmProbeRunner.answer( "xmlSourcesRead", read );
        ChildJvmProbeRunner.answer( "poolProp", String.valueOf( mpconfig().getProperty( "c3p0." + POOL_PROP ) ) );
        ChildJvmProbeRunner.answer( "warningsNamingMultipleXml",
                                    capture.countAtOrAbove( Level.WARNING, "more than one" ) );
    }

    private static void warningsByScope() throws Exception
    {
        // Junk names are deliberately NOT near-misses of the valid ones. warningsNaming(..) is a
        // substring match, so "maxPoolSiz" as a typo for "maxPoolSize" would make the valid key's
        // count match the typo's warning and the two assertions could not be told apart.
        useXml( "<default-config>"
                    + prop( ENFORCE_KEY, "true" )
                    + prop( "maxPoolSize", "47" )
                    + prop( "notAPropertyAtAll", "x" )
                    + "<user-overrides user=\"steve\">"
                    +     prop( "minPoolSize", "4" )
                    +     prop( "alsoNotAProperty", "x" )
                    + "</user-overrides>"
                    + "</default-config>"
                    + "<named-config name=\"" + NAMED_CONFIG + "\">"
                    + prop( "acquireIncrement", "7" )
                    + prop( "stillNotAProperty", "x" )
                    + "</named-config>" );

        ChildJvmProbeRunner.answer( "warningsNamingSupportedKey", capture.warningsNaming( ENFORCE_KEY ) );

        // valid pool properties, one per scope -- all must be silent
        ChildJvmProbeRunner.answer( "warningsNamingDefaultScopeValid", capture.warningsNaming( "maxPoolSize" ) );
        ChildJvmProbeRunner.answer( "warningsNamingUserOverrideValid", capture.warningsNaming( "minPoolSize" ) );
        ChildJvmProbeRunner.answer( "warningsNamingNamedScopeValid", capture.warningsNaming( "acquireIncrement" ) );

        // and junk, one per scope -- all must be reported
        ChildJvmProbeRunner.answer( "warningsNamingDefaultScopeTypo", capture.warningsNaming( "notAPropertyAtAll" ) );
        ChildJvmProbeRunner.answer( "warningsNamingUserOverrideTypo", capture.warningsNaming( "alsoNotAProperty" ) );
        ChildJvmProbeRunner.answer( "warningsNamingNamedScopeTypo", capture.warningsNaming( "stillNotAProperty" ) );
    }

    // ------------------------------------------------------------- plumbing

    /**
     *  Writes the given <code>c3p0-config</code> body to a temp file and points c3p0 at it. Must
     *  run before anything touches C3P0Config: the property is read from the sealed System
     *  properties snapshot, and C3P0Config's class initializer is what takes it.
     */
    private static void useXml( String body ) throws Exception
    {
        File f = File.createTempFile( "c3p0-config-probe", ".xml" );
        f.deleteOnExit();
        Writer w = new OutputStreamWriter( new FileOutputStream( f ), "UTF-8" );
        try
        { w.write( "<c3p0-config>" + body + "</c3p0-config>" ); }
        finally
        { w.close(); }

        System.setProperty( XML_CFG_KEY, f.getAbsolutePath() );

        // and only now
        mpconfig();
    }

    /**
     *  Called AFTER configuration has loaded, when initializing these classes can no longer
     *  seal anything prematurely. If a constant is ever renamed or respelled, the literals above
     *  go stale silently -- every case would still pass, testing a key nothing reads. This is
     *  what stops that.
     */
    private static void assertKeySpellingsStillMatch()
    {
        ChildJvmProbeRunner.answer( "enforceKeySpellingMatches",
            String.valueOf( ENFORCE_KEY.equals( ByNameInstantiationUtils.ENFORCE_WHITELIST_KEY ) ) );
        ChildJvmProbeRunner.answer( "acceptDeserKeySpellingMatches",
            String.valueOf( ACCEPT_DESER_KEY.equals( SecurityConfigKey.ACCEPT_DESERIALIZED_INITIAL_CONTEXT_ENVIRONMENT ) ) );
    }

    private static String prop( String name, String value )
    { return "<property name=\"" + name + "\">" + value + "</property>"; }

    private static MultiPropertiesConfig mpconfig()
    { return C3P0Config.getMultiPropertiesConfig(); }

    private static String poolProp( String configName )
    { return String.valueOf( C3P0Config.getUnspecifiedUserProperty( POOL_PROP, configName ) ); }

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
         *  Warnings that name a key. The question these tests ask is not how many times c3p0
         *  complained, but whether an operator who set THIS key is told anything about it.
         */
        synchronized int warningsNaming( String key )
        { return countAtOrAbove( Level.WARNING, key ); }

        synchronized int countAtOrAbove( Level level, String substring )
        {
            int out = 0;
            for ( LogRecord r : records )
                if ( r.getLevel().intValue() >= level.intValue()
                     && String.valueOf( r.getMessage() ).indexOf( substring ) >= 0 ) ++out;
            return out;
        }
    }
}
