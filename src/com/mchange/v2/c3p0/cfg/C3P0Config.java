package com.mchange.v2.c3p0.cfg;

import java.beans.*;
import java.util.*;
import java.lang.reflect.InvocationTargetException;
import com.mchange.v2.c3p0.impl.*;
import com.mchange.v2.beans.*;
import com.mchange.v2.cfg.*;
import com.mchange.v2.log.*;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.mchange.v1.lang.BooleanUtils;
import com.mchange.v2.lang.ObjectUtils;
import com.mchange.v2.c3p0.C3P0Registry;
import com.mchange.v2.csv.MalformedCsvException;
import com.mchange.v2.reflect.ByNameInstantiationUtils;
import com.mchange.v2.reflect.InstantiationNotPermittedException;

import static com.mchange.v2.c3p0.cfg.C3P0ConfigUtils.PROPS_FILE_BASE_PFX;
import static com.mchange.v2.c3p0.cfg.C3P0ConfigUtils.PROPS_FILE_PROP_PFX;
import static com.mchange.v2.c3p0.cfg.C3P0ConfigUtils.PROP_STYLE_DEFAULT_USER_OVERRIDES_PFX;

import static com.mchange.v2.c3p0.cfg.C3P0ConfigUtilsNoLogging.PROP_STYLE_NAMED_CFG_PART;
import static com.mchange.v2.c3p0.cfg.C3P0ConfigUtilsNoLogging.PROP_STYLE_EXTENSIONS_PART;
import static com.mchange.v2.c3p0.cfg.C3P0ConfigUtilsNoLogging.PROP_STYLE_USER_OVERRIDES_PART;

import static com.mchange.v2.c3p0.cfg.C3P0ConfigUtilsNoLogging.POTENTIALLY_EMPTY_NAMED_SCOPE_MARKER_VALUE;

import static com.mchange.v2.c3p0.cfg.C3P0ConfigUtilsNoLogging.nameContainsMarkers;

//all internal maps should be HashMaps (the implementation presumes HashMaps)

/**
 *  c3p0's configuration, as one body of properties-style keys.
 *
 *  <h3>One representation</h3>
 *
 *  <p>Configuration arrives from many places &mdash; {@code c3p0.properties}, HOCON,
 *  {@code c3p0-config.xml}, System properties, programmatic overrides &mdash; and all of it is
 *  read into a single {@link com.mchange.v2.cfg.MultiPropertiesConfig} of properties-style keys.
 *  Every accessor here derives from that one view by key prefix. There is no second
 *  representation to keep in step, which is the point: divergence between two views of one body
 *  of configuration was a recurring source of bugs, where a setting would be visible to one
 *  reader and invisible to another.</p>
 *
 *  <p>See the manual's <i>Precedence of Configuration Settings</i> for which source wins. In
 *  brief: a sealed System property outranks everything, then live System properties, then
 *  programmatic refresh overrides, then XML, then {@code c3p0.properties}, then HOCON, then the
 *  shipped defaults, then c3p0's hard-coded defaults, and backstops below those.</p>
 *
 *  <h3>How c3p0-config.xml becomes properties</h3>
 *
 *  <p>The XML format is read by a {@link com.mchange.v2.cfg.PropertiesConfigSource} which
 *  translates it into properties keys, so that XML layers with every other source by the
 *  ordinary rules. mchange-commons-java knows nothing of the format: c3p0 declares
 *  {@link C3P0ExtraPropertiesConfigSources} through a
 *  {@code mcfg-extra-properties-config-sources.txt} resource, and that resolver claims
 *  identifiers beginning {@code c3p0-xml:}. See
 *  {@link com.mchange.v2.cfg.ExtraPropertiesConfigSources} for the mechanism and
 *  {@link C3P0XmlPropertiesConfigSource} for the parser.</p>
 *
 *  <p>An identifier is {@code c3p0-xml:} followed by either a classloader resource path beginning
 *  {@code /} &mdash; where <b>every</b> resource at that path is read and merged, earlier
 *  classpath entries winning, just as for properties resources &mdash; or a {@code file:} URL,
 *  which gets the full {@code file:} treatment including {@code ${system.property}} references,
 *  {@code ?required=} and {@code ?permissions=useronly}. {@code c3p0-xml:/} alone names nothing
 *  and is refused, as is a query string on the classloader form, which would otherwise be read
 *  as part of the resource path and silently find nothing. Because the {@code file:} form can
 *  refuse on permission grounds, the source is a {@link com.mchange.v2.cfg.VetoableConfig}:
 *  plain {@code MConfig.AsProvided} will not read it.</p>
 *
 *  <p>Property names are mapped by <b>shape</b>, since the parser has no list of c3p0's
 *  configurable properties to validate against. Within {@code <default-config>}, a bare name
 *  takes a {@code c3p0.} prefix and a compound name is kept as written &mdash; so
 *  {@code maxPoolSize} becomes {@code c3p0.maxPoolSize}, while
 *  {@code com.mchange.v2.log.freezeConfig} means itself. Within
 *  {@code <named-config name="X">}, names are prefixed {@code c3p0.named-configs.X.} and a
 *  leading {@code c3p0.} is dropped first, so both spellings of a pool property arrive at one
 *  key; a fully-qualified name is <b>refused with a warning</b> there, because a named config is
 *  a per-DataSource scope while such settings are read globally, with no DataSource in hand.
 *  {@code <user-overrides user="..."/>} maps the same way beneath
 *  {@code c3p0.user-overrides.<user>.} or
 *  {@code c3p0.named-configs.X.user-overrides.<user>.} &mdash; a {@code name} attribute is
 *  accepted in place of {@code user}, with a warning, because a since-corrected error in c3p0's
 *  own documentation showed it. {@code <extensions>} names are never processed, being names a
 *  deployment invented for its own use, and are not read beneath a user override at all, since
 *  extensions are per-config rather than per-user.</p>
 *
 *  <h3>Dots in a named-config name or a user-override user name</h3>
 *
 *  <p>Both <b>may</b> contain dots. A database user name of the form {@code first.last} is
 *  ordinary, and c3p0-0.14.2 accepted a dotted {@code <named-config>} name in XML, where it kept
 *  scopes in a bean keyed by the attribute and so never split a flat key. These names become
 *  segments of one flat key namespace here, in which a dot is the only separator there is, so
 *  reading them back takes care; refusing them outright, which 0.15.0 briefly did, withdrew
 *  configuration that had worked for years.</p>
 *
 *  <p>What <b>is</b> refused, with a warning, is a name carrying {@code named-configs},
 *  {@code user-overrides} or {@code extensions} as one of its dot-separated elements. Such a name
 *  is not merely awkward but genuinely ambiguous: {@code c3p0.named-configs.a.extensions.b} is
 *  equally an extension {@code b} of scope {@code a} and a property of a scope called
 *  {@code a.extensions.b}, and nothing in the key says which. A named config so named is refused
 *  by the parser and refused again on lookup, so the two ends agree. The same tokens in a
 *  <i>user</i> name are merely discouraged, with a warning: those resolve unambiguously, since a
 *  user name is always bounded by the {@code user-overrides} element on one side and its property
 *  on the other.</p>
 *
 *  <p>A user name is read as <b>everything between {@code user-overrides.} and the final
 *  element</b>, the final element being the property. That is unambiguous because a pool property
 *  name never contains a dot. It cuts the other way for a compound property name written under a
 *  user override, which is absorbed into the user name instead &mdash; accepted deliberately,
 *  since such a key means nothing in that position either way and still draws an
 *  unexpected-property warning.</p>
 *
 *  <h3>A declared-but-empty named config</h3>
 *
 *  <p>c3p0 distinguishes a scope <i>declared empty</i> &mdash; which falls through to
 *  lower-precedence configuration &mdash; from one <i>never declared</i>, which reverts to the
 *  default scope. A scope with no properties would otherwise leave no trace at all, so this
 *  source writes the <b>bare scope key</b> for it, carrying
 *  {@link C3P0ConfigUtilsNoLogging#POTENTIALLY_EMPTY_NAMED_SCOPE_MARKER_VALUE} as its value: a
 *  value that names itself in a config dump rather than sitting there as an unexplained empty
 *  string.</p>
 *
 *  <p>The mark is written <b>only where it is needed</b> &mdash; where the scope contributed no
 *  keys of its own, as judged by what actually landed rather than by which child elements were
 *  present, so that a scope whose every property was refused still reads as declared. A populated
 *  scope carries no mark, which also matches what a deployment writes by hand: nobody sets the
 *  base key of a scope they are giving properties to. Since a key is a prefix of itself, the mark
 *  reads as {@code containsKey(pfx)} on a by-prefix lookup, and <i>declared</i> remains the
 *  broader question &mdash; a scope declared in properties-style configuration carries no mark
 *  and is declared nonetheless. The mark is for named configs alone: for
 *  {@code <user-overrides>} and {@code <extensions>}, declared-and-empty and undeclared mean the
 *  same thing to c3p0, and an empty one of either is not content.</p>
 *
 *  <h3>Scopes whose names share a dotted prefix</h3>
 *
 *  <p>Because by-prefix lookup matches whole dot-separated tokens, scope {@code a.b}'s keys sit
 *  underneath scope {@code a}'s prefix. Neither scope may read the other's configuration, and two
 *  rules keep them apart. <b>Definedness</b> asks whether some key really belongs to this scope
 *  &mdash; its mark, a dotless property of its own, or its own {@code user-overrides.} or
 *  {@code extensions.} subtree &mdash; rather than merely sitting under its prefix, so a name
 *  nothing declared stays undefined however many deeper scopes exist. And a scope's keys are
 *  converted to base-prefixed properties only where <b>the remainder after its prefix carries no
 *  further dot</b>, which excludes user overrides, extensions, and every deeper scope's keys
 *  alike.</p>
 *
 *  <p>Both are load-bearing, and for different cases: definedness handles a shadowed name nothing
 *  declared, while the conversion rule handles a shadowed name that <i>is</i> declared, where no
 *  definedness test could help. Read naively, a scope inherited every deeper scope's settings and
 *  {@code getUnspecifiedUserProperties} disagreed with {@code getUnspecifiedUserProperty} about
 *  the same scope &mdash; two views of one configuration, which is the failure this package was
 *  rewritten to eliminate.</p>
 *
 *  <h3>XML parsing is hardened, and stays hardened</h3>
 *
 *  <p>Doctypes, external entities, external DTDs and schemas, XInclude and entity expansion are
 *  all disabled &mdash; see CVE-2018-20433. The single escape is the System property
 *  {@code com.mchange.v2.c3p0.cfg.xml.usePermissiveParser}, read <b>only</b> from the sealed
 *  snapshot of System properties and never from configuration: a flag that relaxes XML parsing
 *  must not be settable by the documents whose parsing it relaxes. In practice it means a
 *  {@code -D}. c3p0's deprecated
 *  {@code com.mchange.v2.c3p0.cfg.xml.expandEntityReferences} is <b>not</b> honored; if it is
 *  set, a warning points at {@code usePermissiveParser} instead, so that a deployment migrating
 *  from older versions is told why its entity references stopped resolving rather than left to
 *  discover it.</p>
 */
public final class C3P0Config
{
    public final static String FREEZE_CONFIG_KEY = "com.mchange.v2.c3p0.freezeConfig";

    public final static String SYNC_REFRESH_LOG_CONFIG = "com.mchange.v2.c3p0.cfg.syncRefreshLogConfig";

    public final static String PROPS_FILE_RSRC_PATH = "/c3p0.properties";

    private final static SealedSystemPropertiesBooleanProperty syncRefreshLogConfig = new SealedSystemPropertiesBooleanProperty(SYNC_REFRESH_LOG_CONFIG, false);
    private final static SealedSystemPropertiesBooleanProperty freezeConfigProperty = new SealedSystemPropertiesBooleanProperty(FREEZE_CONFIG_KEY, true, false, false);

    private final static Object NULL_TOKEN = new Object();

    final static MLogger logger;

    //MT: protected by class' lock
    private static MultiPropertiesConfig _MPCONFIG;
    private static boolean refreshDebugWarned = false;
    private static HashMap<String,Boolean>           cacheNamedConfigIsDefined      = new HashMap<>();
    private static HashMap<PropKeyConfigName,Object> cacheUnspecifiedUserProperty   = new HashMap<>(); // val is property value or NULL_TOKEN
    private static HashMap<Object,Object>            cacheExtensions                = new HashMap<>(); // key is configName or NULL_TOKEN, val is Map<String,String> or NULL_TOKEN
    private static HashMap<Object,Object>            cacheUnspecifiedUserProperties = new HashMap<>(); // key is configName or NULL_TOKEN, val is Map<String,String> or NULL_TOKEN
    private static HashMap<Object,Object>            cacheUserOverrides             = new HashMap<>(); // key is configName or NULL_TOKEN, val is Map<String,Map<String,String>> or NULL_TOKEN

    final static Pattern EXTENSIONS_KEY_PATTERN = Pattern.compile("^c3p0(?:\\.named-configs\\.(.*))?\\.extensions\\..+$");
    final static Pattern NON_EXTENSIONS_KEY_PATTERN = Pattern.compile("^c3p0(?:\\.named-configs\\.(.*?))?(?:\\.user-overrides\\.(.*))?\\.([^\\.]+)$");
    final static Pattern NAMED_CONFIGS_PATTERN = Pattern.compile("^c3p0\\.named-configs\\..*\\.([^\\.]*)$");

    static
    {
	logger = MLog.getLogger( C3P0Config.class );
        MultiPropertiesConfig initial = findInitialMultiPropertiesConfig();
        setLibraryMultiPropertiesConfig( initial );
    }

    private static synchronized void resetCaches()
    {
        cacheNamedConfigIsDefined.clear();
        cacheUnspecifiedUserProperty.clear();
        cacheExtensions.clear();
        cacheUnspecifiedUserProperties.clear();
        cacheUserOverrides.clear();
    }

    // it's important that we return _MPCONFIG directly, no defensive copies or anything
    // it's unmodifiable, and elsewhere we use simple identity tests to ensure cache freshness
    //
    // see e.g. com.mchange.v2.c3p0.impl.DefaultConnectionTester
    private static synchronized MultiPropertiesConfig MPCONFIG()
    { return _MPCONFIG; }

    private static synchronized void setLibraryMultiPropertiesConfig( MultiPropertiesConfig mpc )
    {
        _MPCONFIG = mpc;
        warnOnSet(_MPCONFIG);
        if (logger.isLoggable(MLevel.INFO))
            logger.log(MLevel.INFO,
                       "c3p0 config is now drawn from the following configuration sources: " +
                       Arrays.toString(_MPCONFIG.getPropertiesResourcePaths()) +
                       ". Later paths shadow earlier paths where values conflict.");
    }

    /**
     *  This will include System properties, and
     *  sensitive values like c3p0.password (even if they
     *  are supplied by XML config).
     *
     *  The return value should not be promiscuously printed
     *  or dumped. If you do want to print it, consider
     *  <code>com.mchange.v2.util.PropertiesMasker.DEFAULT.masked(...)</code> first.
     */
    public static Properties allCurrentProperties()
    { return MPCONFIG().getPropertiesByPrefix(""); }

    // we don't synchronize at the method level to respect
    // the fine-grained sync'ing of the overload to which we ultimately delegate
    public static void refreshMainConfig()
    { refreshMainConfigWithBackstopsAndOverrides( (MultiPropertiesConfig[]) null, null, null, null ); }

    // we don't synchronize at the method level to respect
    // the fine-grained sync'ing of the overload to which we ultimately delegate
    public static void refreshMainConfigWithOverrides( Properties overrides, String overridesDescription )
    { refreshMainConfigWithBackstopsAndOverrides(null, null, overrides, overridesDescription); }

    // we don't synchronize at the method level to respect
    // the fine-grained sync'ing of the overload to which we ultimately delegate
    public static void refreshMainConfigWithBackstops( Properties backstops, String backstopsDescription )
    { refreshMainConfigWithBackstopsAndOverrides(backstops, backstopsDescription, null, null); }

    // we don't synchronize at the method level to respect
    // the fine-grained sync'ing of the overload to which we ultimately delegate
    public static void refreshMainConfigWithBackstopsAndOverrides( Properties backstops, String backstopsDescription, Properties overrides, String overridesDescription )
    {
        refreshMainConfigWithBackstopsAndOverrides(
            backstops == null ? null : MultiPropertiesConfig.fromProperties( "PROGRAMMATICALLY_SUPPLIED_BACKSTOPS", backstops ),
            backstopsDescription,
            overrides == null ? null : MultiPropertiesConfig.fromProperties( "PROGRAMMATICALLY_SUPPLIED_OVERRIDES", overrides ),
            overridesDescription
        );
    }

    // we don't synchronize at the method level to respect
    // the fine-grained sync'ing of the overload to which we ultimately delegate
    public static void refreshMainConfigWithOverrides( MultiPropertiesConfig overrides, String overridesDescription )
    { refreshMainConfigWithBackstopsAndOverrides(null, null, overrides, overridesDescription); }

    // we don't synchronize at the method level to respect
    // the fine-grained sync'ing of the overload to which we ultimately delegate
    public static void refreshMainConfigWithBackstops( MultiPropertiesConfig backstops, String backstopsDescription )
    { refreshMainConfigWithBackstopsAndOverrides(backstops, backstopsDescription, null, null); }

    // we don't synchronize at the method level to respect
    // the fine-grained sync'ing of the overload to which we ultimately delegate
    public static void refreshMainConfigWithBackstopsAndOverrides( MultiPropertiesConfig backstops, String backstopsDescription, MultiPropertiesConfig overrides, String overridesDescription )
    { refreshMainConfigWithBackstopsAndOverrides( backstops == null ? null : new MultiPropertiesConfig[]{ backstops }, backstopsDescription, overrides == null ? null : new MultiPropertiesConfig[]{ overrides }, overridesDescription ); }

    // we don't synchronize at the method level to respect
    // the fine-grained sync'ing of the overload to which we ultimately delegate
    /**
     * @deprecated Please use <code>refreshMainConfigWithOverrides( MultiPropertiesConfig[] overrides, String overridesDescription )</code>.
     *             It's clearer.
     */
    @Deprecated
    public static void refreshMainConfig( MultiPropertiesConfig[] overrides, String overridesDescription )
    { refreshMainConfigWithBackstopsAndOverrides(null, null, overrides, overridesDescription); }

    // we don't synchronize at the method level to respect
    // the fine-grained sync'ing of the overload to which we ultimately delegate
    public static void refreshMainConfigWithOverrides( MultiPropertiesConfig[] overrides, String overridesDescription )
    { refreshMainConfigWithBackstopsAndOverrides(null, null, overrides, overridesDescription); }

    // we don't synchronize at the method level to respect
    // the fine-grained sync'ing of the overload to which we ultimately delegate
    public static void refreshMainConfigWithBackstops( MultiPropertiesConfig[] backstops, String backstopsDescription )
    { refreshMainConfigWithBackstopsAndOverrides(backstops, backstopsDescription, null, null); }

    private final static MultiPropertiesConfig[] EMPTY_MPC_ARRAY = new MultiPropertiesConfig[0];

    // later elements of overrides take precedence over earlier ones, later elements of backstops take precedence over earlier ones
    // we synchronize internally to avoid unnecessarily nesting locks when we call C3P0Registry.markConfigRefreshed()
    public static void refreshMainConfigWithBackstopsAndOverrides( MultiPropertiesConfig[] backstops, String backstopsDescription, MultiPropertiesConfig[] overrides, String overridesDescription )
    {
        synchronized (C3P0Config.class)
        {
            resetCaches();
            boolean configFrozen = freezeConfigProperty.getValue(MPCONFIG(), logger);
            if (configFrozen)
            {
                Throwable debugStackTrace;
                if (refreshDebugWarned)
                    debugStackTrace = null;
                else
                {
                    debugStackTrace = new Exception("DEBUG STACK TRACE, attempted refresh of frozen c3p0 config (" + FREEZE_CONFIG_KEY + "=true)");
                    refreshDebugWarned = true;
                }
                if (logger.isLoggable(MLevel.WARNING))
                    logger.log(MLevel.WARNING,
                               "An attempt to refresh c3p0 config will be ignored, because the configuration has been frozen (" + FREEZE_CONFIG_KEY + "=true)",
                               debugStackTrace );

                return;
            }

            MultiPropertiesConfig initial = findInitialMultiPropertiesConfig(); // always has sealed and then snapshot of System properties as its highest precedence elements
            if ( overrides != null || backstops != null)
            {
                MultiPropertiesConfig[] nullsafeBackstops = backstops == null ? EMPTY_MPC_ARRAY : backstops;
                MultiPropertiesConfig[] nullsafeOverrides = overrides == null ? EMPTY_MPC_ARRAY : new MultiPropertiesConfig[] { SealedSystemProperties.capCurrentThenSealAndCombine(overrides) };
                int blen = nullsafeBackstops.length;
                int olen = nullsafeOverrides.length;
                MultiPropertiesConfig[] combineMe = new MultiPropertiesConfig[ blen + 1 + olen ];
                System.arraycopy(nullsafeBackstops,0,combineMe,0,blen);
                combineMe[blen] = initial;
                System.arraycopy(nullsafeOverrides,0,combineMe,blen+1,olen);

                MultiPropertiesConfig  newMpc = MConfig.combine( combineMe );

                setLibraryMultiPropertiesConfig( newMpc );

                if ( logger.isLoggable( MLevel.INFO ) )
                {
                    StringBuilder sb = new StringBuilder();
                    sb.append("c3p0 main configuration was refreshed");
                    if (olen > 0)
                    {
                        sb.append(", with overrides specified");
                        if (overridesDescription != null)
                            sb.append(" -- " + overridesDescription);
                    }
                    if (blen > 0)
                    {
                        sb.append(", with backstops specified");
                        if (backstopsDescription != null)
                            sb.append(" -- " + backstopsDescription);
                    }
                    sb.append(".");

                    // NOTE: older callers might end up with double periods, given that the prior format
                    //       maybe encouraged ending descriptions with a period. The older format was
                    //       (overridesDescription == null ? "." : " - " + overridesDescription), with
                    //       no terminating period applied to the description, and a single rather than
                    //       double '-' functioning as an em-dash.

                    logger.log( MLevel.INFO, sb.toString() );
                }
            }
            else
            {
                setLibraryMultiPropertiesConfig( initial );

                if ( logger.isLoggable( MLevel.INFO ) )
                    logger.log( MLevel.INFO, "c3p0 main configuration was refreshed, with no overrides or backstops specified (and any previous overrides or backstops removed)." );
            }

            if (syncRefreshLogConfig.getValue(MPCONFIG(), logger))
                MLog.refreshConfigWithBackstopsAndOverrides( backstops, backstopsDescription, overrides, overridesDescription );

            boolean configEndsFrozen = freezeConfigProperty.getValue(MPCONFIG(), logger);
            if ( configEndsFrozen && logger.isLoggable( MLevel.INFO ) )
                logger.log( MLevel.INFO, "c3p0 configuration is now frozen, and cannot be updated or refreshed. [" + FREEZE_CONFIG_KEY + "=true]" );
        }
        // let's not unnecessarily nest locks
	C3P0Registry.markConfigRefreshed();
    }

    // only for backwards compatability if, very unusually, legacy config files are found
    private static String[] backstopHardcodedMLogC3P0Defaults()
    { return new String[] {"/com/mchange/v2/log/default-mchange-log.properties","/mchange-commons.properties","/mchange-log.properties","/c3p0-default.properties"}; }

    // only for backwards compatability if, very unusually, legacy config files are found
    private static String[] backstopHardcodedMLogC3P0Preempts()
    { return new String[] {"hocon:/reference,/application,/c3p0,/","/c3p0.properties","c3p0-xml:/c3p0-config.xml","c3p0-xml:prop:${com.mchange.v2.c3p0.cfg.xml}","/",SealedSystemProperties.NOTIONAL_RESOURCE_PATH}; }

    private static MultiPropertiesConfig findInitialMultiPropertiesConfig()
    {
        MultiPropertiesConfig out;

        MultiPropertiesConfig withoutDefaults;

        // this case is for compatability, to not break old behavior if deployments had customized the
        // old-school config-libray configuring resorces that we now drop completely ftom the canonical distribution
        if (MLog.getConfigResourcePathsTxtPaths() == null && MConfig.WithTraditionalDefaultSources.traditionalPathFileSourcedPathsFound())
        {
            // very inelegantly, keep these load paths in sync with those com.mchange.v2.log.MLogConfig.refresh()
            String[] defaults = backstopHardcodedMLogC3P0Defaults();
            String[] preempts = backstopHardcodedMLogC3P0Preempts();
            withoutDefaults = MConfig.WithTraditionalDefaultSources.readUncachedResourceConfig( defaults, preempts );

            if ( logger.isLoggable( MLevel.WARNING ) )
            {
                logger.log(MLevel.WARNING,
                           "At least one of the following resources " + MConfig.WithTraditionalDefaultSources.traditionalPathFileResourcesSearched() +
                           " has been found, and no resource paths have been found in a newer " + MLogConfig.MLOG_CONFIG_RESOURCE_PATHS_RESOURCE +
                           ", so c3p0 will configure itself using the traditional mechanism that used the legacy files. " +
                           "However, c3p0 is transitioning from this mechanism, which is fragile and obscure. The supported way to customize configuration " +
                           "sources for c3p0 is to augment built-in list by calling an overload of C3P0Config.refreshMainConfigWithBackstopsAndOverrides(...) and similar methods.");
            }
        }
        else
        {
            if (MConfig.WithTraditionalDefaultSources.traditionalPathFileSourcedPathsFound())
            {
                if ( logger.isLoggable( MLevel.WARNING ) )
                {
                    logger.log(MLevel.WARNING,
                               "At least one of the following legacy resources " + MConfig.WithTraditionalDefaultSources.traditionalPathFileResourcesSearched() +
                               " has been found. Resource paths have also been found in a newer " + MLogConfig.MLOG_CONFIG_RESOURCE_PATHS_RESOURCE +
                               ", so c3p0 will configure itself using the newer mechanism. Packaging both is a misconfiguration (and probably results from multiple versions of c3p0 on your CLASSPATH). " +
                               "Please remove any of " + MConfig.WithTraditionalDefaultSources.traditionalPathFileResourcesSearched() + " from your CLASSPATH.");
                }
            }

            String[] resources = MLogC3P0SharedResources.get();
            withoutDefaults = MConfig.AsProvidedIgnoreVetoed.readUncachedResourceConfig(SealedSystemProperties.capCurrentThenSealed(resources));
        }

        Properties unprefixedDefaults = C3P0ConfigUtils.extractHardcodedC3P0DefaultsStringCoercedOnly();
        Properties prefixedDefaults = new Properties();
        for (String unprefixed : unprefixedDefaults.stringPropertyNames())
            prefixedDefaults.put("c3p0." + unprefixed, unprefixedDefaults.getProperty(unprefixed));
        MultiPropertiesConfig defaults = MultiPropertiesConfig.fromProperties("C3P0_HARDCODED_DEFAULTS",prefixedDefaults);
        out = MConfig.combine(new MultiPropertiesConfig[]{defaults,withoutDefaults});

        if (logger.isLoggable(MLevel.FINE))
            logger.log(MLevel.FINE,
                       "Initial c3p0 properties-style config is drawn from the following configuration sources: " +
                       Arrays.toString(out.getPropertiesResourcePaths()) +
                       ". Later paths shadow earlier paths where values conflict.");

        return out;
    }

    /**
     *  @deprecated In general, try to use com.mchange.v2.cfg.SealedSystemPropertiesStringProperty with C3P0Config.getMultiPropertiesConfig() as its argument.
     */
    @Deprecated
    public static String getPropsFileConfigProperty( String prop )
    { return MPCONFIG().getProperty( prop ); }

    private static String defaultScopePrefixNoDot(String user)
    { return (user == null ? PROPS_FILE_BASE_PFX : PROP_STYLE_DEFAULT_USER_OVERRIDES_PFX + "." + user); }

    private static String defaultScopeKey(String user, String unprefixedKey)
    { return defaultScopePrefixNoDot(user) + "." + unprefixedKey; }

    private static String defaultScopeExtensionsKey()
    { return defaultScopeKey(null, PROP_STYLE_EXTENSIONS_PART); }

    private static String namedScopePrefixNoDot(String configName, String user)
    {
        String base = C3P0ConfigUtils.PROP_STYLE_NAMED_CFG_PFX + "." + configName;
        return (user == null ? base : base + "." + PROP_STYLE_USER_OVERRIDES_PART + "." + user);
    }

    private static String namedScopeKey(String configName, String user, String unprefixedKey)
    { return namedScopePrefixNoDot(configName, user) + "." + unprefixedKey; }

    private static String namedScopeExtensionsKey(String configName)
    { return namedScopeKey(configName, null, PROP_STYLE_EXTENSIONS_PART); }

    // we rely here upon the fact that 'extensions' and 'user-overrides' are not permitted to
    // be dot-separated elements of names. if they could be, we'd not be able to distinguish
    // an extension or user-overrides defined in the named config whose definition we are
    // examining and an alternative named config called <name>.extension or <name>.user-overrides
    //
    // C3P0XmlPropertiesConfigSource refuses names including 'extensions' and 'user-overrides', and
    // C3P0Config refuses to look them up. Relax either refusal and we would no longer be able to
    // definitively distinguish defined from shadowed scopes.
    private static boolean _namedConfigIsDefined(String configName)
    {
        String prefix = namedScopePrefixNoDot(configName, null);

        // never null, whether empty or undefined
        // but if defined, it will be non-empty (even if in fact
        // it contains no subkeys, a dummy base key will exist if
        // the named scope has been defined but no keys were set
        Properties defined = MPCONFIG().getPropertiesByPrefix(prefix);

        // there could be shadowed deeper scopes, so we check the keys
        // to make sure they are really in this specific scope
        for(String key : defined.stringPropertyNames())
        {
            if (prefix.equals(key)) // the scope-defining key
                return true;
            else
            {
                String afterPrefix = key.substring(prefix.length()+1); // +1 to get past the dot
                int next_dot_index = afterPrefix.indexOf('.'); 
                if (next_dot_index < 0)
                    return true; // final property name, no scopes beneath
                else
                {
                    String nextElement = afterPrefix.substring(0,next_dot_index);
                    if (PROP_STYLE_EXTENSIONS_PART.equals(nextElement) || PROP_STYLE_USER_OVERRIDES_PART.equals(nextElement)) // no extra elements in scope name, extensions or user-overrides can't be in scope name
                        return true;
                }
            }
        }

        return false;
    }

    private static synchronized boolean namedConfigIsDefined(String configName)
    {
        Boolean cached = cacheNamedConfigIsDefined.get(configName);
        boolean out;
        if (cached == null)
        {
            out = _namedConfigIsDefined(configName);
            cacheNamedConfigIsDefined.put(configName,out);
        }
        else
            out = cached.booleanValue();
        return out;
    }

    /*

    // nothing actually uses this, and we're incompatibly reorganizing
    // com.mchange.v2.c3p0.cfg, so let's leave this gone unless there's
    // some real reason to restore it

    public static Properties getNamedConfig(String configName)
    {
        if (!namedConfigIsDefined(configName))
            return null;
        else
        {
            String prefix = namedScopePrefixNoDot(configName,null);
            return MPCONFIG().getPropertiesByPrefix(prefix); // never null, whether empty or undefined
        }
    }
    */

    private static final class PropKeyConfigName
    {
        final String propKey;
        final String configName;

        PropKeyConfigName(String propKey, String configName)
        {
            this.propKey = propKey;
            this.configName = configName;
        }

        @Override
        public boolean equals(Object o) // instanceof safe 'cuz we're final
        { return o instanceof PropKeyConfigName ? equals( (PropKeyConfigName) o  ) : false; }

        private boolean equals(PropKeyConfigName other) // null guarded by instanceof
        { return ObjectUtils.eqOrBothNull(this.propKey,other.propKey) && ObjectUtils.eqOrBothNull(this.configName, other.configName); }

        @Override
        public int hashCode()
        { return ObjectUtils.hashOrZero(propKey) ^ ObjectUtils.hashOrZero(configName); }
    }

    private static String _getUnspecifiedUserProperty( String propKey, String configName )
    {
	  String out = null;

 	  if (configName == null)
	      out = MPCONFIG().getProperty(defaultScopeKey(null,propKey));
	  else
          {
              out = MPCONFIG().getProperty(namedScopeKey(configName,null,propKey));
              if (out == null )
              {
                  if (logger.isLoggable(MLevel.WARNING) && !namedConfigIsDefined(configName))
                      logger.log(MLevel.WARNING, "Key '" + propKey + "' requested from undefined named config '" + configName + "'. Checking default config.");
                  out = MPCONFIG().getProperty(defaultScopeKey(null,propKey));
              }
          }

	  return out;
    }

    private static String _getUnspecifiedUserProperty( PropKeyConfigName pkcn )
    { return _getUnspecifiedUserProperty( pkcn.propKey, pkcn.configName ); }

    public static synchronized String getUnspecifiedUserProperty(PropKeyConfigName pkcn )
    {
        Object cached = cacheUnspecifiedUserProperty.get(pkcn);
        String out;
        if (cached == null)
        {
            out = _getUnspecifiedUserProperty( pkcn );
            Object toCache = out == null ? NULL_TOKEN : out;
            cacheUnspecifiedUserProperty.put(pkcn,toCache);
        }
        else
            out = cached instanceof String ? (String) cached : null;
        return out;
    }

    public static synchronized String getUnspecifiedUserProperty(String propKey, String configName)
    { return getUnspecifiedUserProperty(new PropKeyConfigName(propKey,configName)); }


    // we need simple keys in the output, not prefixed keys
    // we return Map because we can make it unmodifiable
    private static Map<String,String> _getExtensions(String configName)
    {
	Properties raw;
        String parentKey;
	if (configName != null)
        {
            String namedKey = namedScopeExtensionsKey(configName);
            raw = MPCONFIG().getPropertiesByPrefix(namedKey);
            parentKey = namedKey;
            if (raw.isEmpty())
            {
                if (!namedConfigIsDefined(configName))
                {
                    if (logger.isLoggable(MLevel.WARNING))
                        logger.log(MLevel.WARNING, "Extensions have been requested from undefined named config '" + configName + "'. Checking default config.");
                    String defaultKey = defaultScopeExtensionsKey();
                    raw = MPCONFIG().getPropertiesByPrefix(defaultKey);
                    parentKey = defaultKey;
                }
                else
                    parentKey = namedKey; // doesn't matter
            }
        }
        else
        {
            String defaultKey = defaultScopeExtensionsKey(); // yes, we could just make this parent key... but for consistency of style with the rest
            raw = MPCONFIG().getPropertiesByPrefix(defaultKey);
            parentKey = defaultKey;
        }

        int parent_key_len = parentKey.length();
        Map<String,String> out = new HashMap<>();
        for(String rawKey : raw.stringPropertyNames())
        {
            if (!rawKey.equals(parentKey)) // omit the base key with no subkey, and no '.'
            {
              String val = raw.getProperty(rawKey);
              out.put(rawKey.substring(parent_key_len+1),val); // +1 to get rid of the subkey'.'
            }
        }
        return Collections.unmodifiableMap( out );
    }

    public static synchronized Map<String,String> getExtensions(String configName)
    {
        Object configNameCacheKey = configName == null ? NULL_TOKEN : configName;
        Object cached = cacheExtensions.get(configNameCacheKey);
        Map<String,String> out;
        if (cached == null)
        {
            out = _getExtensions( configName );
            Object toCache = out == null ? NULL_TOKEN : out;
            cacheExtensions.put(configNameCacheKey,toCache);
        }
        else
            out = cached instanceof Map ? (Map<String,String>) cached : null;
        return out;
    }

    private static boolean isExtensionsKey(String key)
    {
        Matcher m = EXTENSIONS_KEY_PATTERN.matcher(key);
        return m.matches();
    }

    private static boolean isUserOverridesKey(String key)
    {
        Matcher m = NON_EXTENSIONS_KEY_PATTERN.matcher(key);
        if (m.matches())
            return m.group(2) != null;
        else
            return false;
    }


    private static Properties convertNamedScopePropsToBasePrefixProps(Properties props)
    {
        Properties out = new Properties();
        for (String key : props.stringPropertyNames())
        {
            if (! isUserOverridesKey(key) && ! isExtensionsKey(key) )
            {
                Matcher m = NAMED_CONFIGS_PATTERN.matcher(key);
                if (m.matches())
                {
                    String val = props.getProperty(key);
                    if (!POTENTIALLY_EMPTY_NAMED_SCOPE_MARKER_VALUE.equals(val))
                        out.setProperty( PROPS_FILE_PROP_PFX + m.group(1), val );
                }
            }
        }
        return out;
    }


    // extracts properties directly defined in a namedScope, not as a compound (or the termination of a compound) property
    // containing dots, only atoms that contain no dots
    private static Properties convertScopeOwnPropsToBasePrefixProps(String namedPrefixNoDot, Properties prefixedProps)
    {
        String scopePfxDot = namedPrefixNoDot + ".";
        Properties out = new Properties();
        for (String key : prefixedProps.stringPropertyNames())
        {
            if (key.startsWith(scopePfxDot)) // ignore the key that may exist to simply define a potentially empty scope, just the bare prefix with no dot
            {
                String remainder = key.substring(scopePfxDot.length());
                if (remainder.indexOf('.') < 0) // this scope's OWN pool property, and so excludes user-overrides.*, extensions.*, and the keys of any deeper dotted scope that shares this prefix
                    out.setProperty( PROPS_FILE_PROP_PFX + remainder, prefixedProps.getProperty(key) );
            }
        }
        return out;
    }


    private static synchronized Map<String,String> _getUnspecifiedUserProperties(String configName)
    {
        String defaultPrefix = defaultScopePrefixNoDot(null);

        Properties raw = MPCONFIG().getPropertiesByPrefix(defaultPrefix);
        if (configName != null)
        {
            if (nameContainsMarkers(configName))
            {
                if (logger.isLoggable(MLevel.WARNING))
                    logger.log(MLevel.WARNING,
                               "Looking up properties for an illegal configuration name '" + configName +
                               "'. Regardless of what you may think you have configured, no named-scope specific properties will be found for this name, only default-scope values will obtain.");
                return null;
            }
            else
            {
                String namedPrefix = namedScopePrefixNoDot(configName,null);
                Properties named = MPCONFIG().getPropertiesByPrefix(namedPrefix);

                // even if named is non-empty, the scope could be undefined
                // because we could have a shadowing name that includes dots.
                // so there's not much information in named.isEmpty(), we use
                // namedConfigIsDefined(...)
                if (!namedConfigIsDefined(configName))
                    {
                        if (logger.isLoggable(MLevel.WARNING))
                            logger.log(MLevel.WARNING, "Properties (for an unspecified user) cannot be found for undefined " +
                                       "named config '" + configName + "'. Only default config values obtain.");
                        // nothing to overlay, raw already holds the default scope
                    }
                else
                    raw.putAll(convertScopeOwnPropsToBasePrefixProps(namedPrefix, named));
            }
        }

        int prefix_len = defaultPrefix.length();
        Map<String,String> out = new HashMap<>();
        for(String rawKey : raw.stringPropertyNames())
        {
            if (!defaultPrefix.equals(rawKey)) // omit the base key with no subkey, and no '.'
            {
                String val = raw.getProperty(rawKey);
                String frontTrimmed = rawKey.substring(prefix_len+1); // "c3p0.", ie 5
                if (frontTrimmed.indexOf('.') < 0) // no compound keys
                    out.put(frontTrimmed,val);
            }
        }
        if (out.isEmpty())
        {
            if (logger.isLoggable(MLevel.SEVERE))
                logger.log(MLevel.SEVERE,
                           "Our map of config properties for the unspecified user is empty?!? This should never happen. " +
                           "It should always be populated at the very least by default property values. " +
                           "Callers of getUnspecifiedUserProperties(...) will probably provoke NullPointerExceptions, " +
                           "as no one guards against this pathological case.");
            return null;
        }
        else
            return Collections.unmodifiableMap( out );
    }

    public static synchronized Map<String,String> getUnspecifiedUserProperties(String configName)
    {
        Object configNameCacheKey = configName == null ? NULL_TOKEN : configName;
        Object cached = cacheUnspecifiedUserProperties.get(configNameCacheKey);
        Map<String,String> out;
        if (cached == null)
        {
            out = _getUnspecifiedUserProperties( configName );
            Object toCache = out == null ? NULL_TOKEN : out; 
            cacheUnspecifiedUserProperties.put(configNameCacheKey,toCache);
        }
        else
            out = cached instanceof Map ? (Map<String,String>) cached : null;
        return out;
    }

    private final static Pattern USER_OVERRIDES_KEY_TAIL_PATTERN = Pattern.compile("^(.*)\\.([^\\.]*)$");

    // no equals()/hashcode(), identity is fine for us
    private final static class UserOverrideTriplet
    {
        final String user;
        final String key;
        final String value;

        UserOverrideTriplet(String user, String key, String value)
        {
            this.user = user;
            this.key = key;
            this.value = value;
        }
    }

    private static Set<UserOverrideTriplet> convertUserOverridesPropsToBasePrefixProps(String bareScopeUserOverridesPrefixNoDot, Properties props)
    {
        Set<UserOverrideTriplet> out = new HashSet<>();
        for (String key : props.stringPropertyNames())
        {
            if (!bareScopeUserOverridesPrefixNoDot.equals(key))
            {
                int strip_me_len = bareScopeUserOverridesPrefixNoDot.length() + 1; // don't forget the period
                String stripped = key.substring(strip_me_len);
                Matcher m = USER_OVERRIDES_KEY_TAIL_PATTERN.matcher(stripped);
                if (m.matches())
                {
                    String val = props.getProperty(key);
                    out.add( new UserOverrideTriplet(m.group(1),m.group(2),val) );
                }
            }
        }
        return out;
    }

    private static Map<String,Map<String,String>> _getUserOverridesFromScope(String bareScopeUserOverridesPrefixNoDot)
    {
	Properties               props    = MPCONFIG().getPropertiesByPrefix( bareScopeUserOverridesPrefixNoDot );
        Set<UserOverrideTriplet> triplets = convertUserOverridesPropsToBasePrefixProps( bareScopeUserOverridesPrefixNoDot, props );

        Map<String,Map<String,String>> out = new HashMap<>();
        for (UserOverrideTriplet triplet : triplets)
        {
            Map<String,String> inner = out.get(triplet.user);
            if (inner == null)
            {
                inner = new HashMap<String,String>();
                out.put(triplet.user,inner);
            }
            inner.put(triplet.key,triplet.value);
        }
        return out;
    }

    // overwrites defaults!
    private static void userOverridesMergeOver(Map<String,Map<String,String>> overrides, Map<String,Map<String,String>> defaults)
    {
        Set<String> overrideKeys = overrides.keySet();
        for (String key : overrideKeys)
        {
            Map<String,String> fromOverrides = overrides.get(key);
            Map<String,String> fromDefaults  = defaults.get(key);
            if (fromDefaults == null)
                defaults.put(key, new HashMap(fromOverrides));
            else
                fromDefaults.putAll(fromOverrides);
        }
    }

    private static Map<String,Map<String,String>> _getUserOverrides( String configName )
    {
        if (configName != null && nameContainsMarkers(configName))
        {
            if (logger.isLoggable(MLevel.WARNING))
                logger.log(MLevel.WARNING,
                           "Looking up user-overrides for an illegal configuration name '" + configName +
                           "'. Regardless of what you may think you have configured, no user-overrides will be found for this name.");
            return null;
        }
        else
        {
            String defaultPrefix = defaultScopePrefixNoDot(null)  + "." + PROP_STYLE_USER_OVERRIDES_PART;
            Map<String,Map<String,String>> tmp = _getUserOverridesFromScope(defaultPrefix);

            if (configName != null)
            {
                String namedPrefix = namedScopePrefixNoDot(configName,null) + "." + PROP_STYLE_USER_OVERRIDES_PART;
                Map<String,Map<String,String>> namedUserOverrides = _getUserOverridesFromScope(namedPrefix);
                userOverridesMergeOver(namedUserOverrides, tmp);
            }

            if (tmp.isEmpty())
                return null;
            else
            {
                HashMap<String,Map<String,String>> out = new HashMap<>();
                for (String user : tmp.keySet())
                {
                    Map<String,String> userMap = tmp.get(user);
                    out.put( user, Collections.unmodifiableMap(userMap) );
                }
                return Collections.unmodifiableMap(out);
            }
        }
    }

    public static synchronized Map<String,Map<String,String>> getUserOverrides(String configName)
    {
        Object configNameCacheKey = configName == null ? NULL_TOKEN : configName;
        Object cached = cacheUserOverrides.get(configNameCacheKey);
        Map<String,Map<String,String>> out;
        if (cached == null)
        {
            out = _getUserOverrides( configName );
            Object toCache = out == null ? NULL_TOKEN : out; 
            cacheUserOverrides.put(configNameCacheKey,toCache);
        }
        else
            out = cached instanceof Map ? (Map<String,Map<String,String>>) cached : null;
        return out;
    }

    public static String getUserOverridesAsString(String configName) throws IOException, MalformedCsvException
    {
	Map userOverrides = getUserOverrides( configName );
	if (userOverrides == null)
	    return null;
	else
	    return C3P0ImplUtils.createUserOverridesAsString( userOverrides ).intern();
    }

    final static Class[] SUOAS_ARGS = new Class[] { String.class };

    final static Collection SKIP_BIND_PROPS = Arrays.asList( new String[] {"loginTimeout", "properties"} );

    public static void bindUserOverridesAsString(Object bean, String uoas) throws Exception
    {
	Method m = bean.getClass().getMethod( "setUserOverridesAsString", SUOAS_ARGS );
	m.invoke( bean, new Object[] { uoas } );
    }

    public static void bindUserOverridesToBean(Object bean, String configName) throws Exception
    { bindUserOverridesAsString( bean, getUserOverridesAsString( configName ) ); }

    public static void bindNamedConfigToBean(Object bean, String configName, boolean shouldBindUserOverridesAsString) throws IntrospectionException
    {
	Map defaultUserProps = C3P0Config.getUnspecifiedUserProperties( configName );
	Map extensions = C3P0Config.getExtensions( configName );
	Map union = new HashMap();
	union.putAll( defaultUserProps );
	union.put( "extensions", extensions );
	BeansUtils.overwriteAccessiblePropertiesFromMap( union,
							 bean,
							 false,
							 SKIP_BIND_PROPS,
							 true,
							 MLevel.FINEST,
							 MLevel.WARNING,
							 false);
	try
	    {
		if ( shouldBindUserOverridesAsString )
		    bindUserOverridesToBean( bean, configName );
	    }
	catch (NoSuchMethodException e)
	    {
		e.printStackTrace();
		/* ignore */
	    }
	catch (Exception e)
	    {
		if (logger.isLoggable( MLevel.WARNING ))
		    logger.log( MLevel.WARNING,
				"An exception occurred while trying to bind user overrides " +
				"for named config '" + configName + "'. Only default user configs " +
				"will be used."
				, e);
	    }
    }

    /*
     *  Note that on initialization of a DataSource, no config name is known.
     *  We initialize local vars using the default config. The DataSources class
     *  and/or constructors that accept a configName then overwrite the initial
     *  values with namedConfig overrides if supplied.
     */
    public static String initializeUserOverridesAsString()
    {
	try
	    { return getUserOverridesAsString( null ); }
	catch (Exception e)
	    {
		if (logger.isLoggable( MLevel.WARNING ))
		    logger.log( MLevel.WARNING, "Error initializing default user overrides. User overrides may be ignored.", e);
		return null;
	    }
    }

    public static Map initializeExtensions()
    { return getExtensions( null ); }

    public static String initializeStringPropertyVar(String propKey, String dflt, Validator<String> validator)
    {
        String out = getUnspecifiedUserProperty( propKey, null );
        try
            {
                if (out == null) out = dflt;
                else if (validator != null) out = validator.validate(out);
                return out;
            }
        catch (InvalidConfigException ice)
            {
                logger.log(MLevel.WARNING, "'" + out + "' is not a legal value for property '" + propKey +
                           "'. Using default value: " + dflt, ice);
                return dflt;
            }
    }

    public static String initializeStringPropertyVar(String propKey, String dflt)
    { return initializeStringPropertyVar(propKey, dflt, null); }


    public static int initializeIntPropertyVar(String propKey, int dflt)
    {
	boolean set = false;
	int out = -1;

	String outStr = getUnspecifiedUserProperty( propKey, null );
	if (outStr != null)
	    {
		try
		    {
			out = Integer.parseInt( outStr.trim() );
			set = true;
		    }
		catch (NumberFormatException e)
		    {
			logger.log(MLevel.WARNING, "'" + outStr + "' is not a legal value for property '" + propKey +
				   "'. Using default value: " + dflt, e);
		    }
	    }

	if (!set)
	    out = dflt;

	//System.err.println("initializing " + propKey + " to " + out);
	return out;
    }

    public static boolean initializeBooleanPropertyVar(String propKey, boolean dflt)
    {
	boolean set = false;
	boolean out = false;

	String outStr = getUnspecifiedUserProperty( propKey, null );
	if (outStr != null)
	    {
		try
		    {
			out = BooleanUtils.parseBoolean( outStr.trim() );
			set = true;
		    }
		catch (IllegalArgumentException e)
		    {
			logger.log(MLevel.WARNING, "'" + outStr + "' is not a legal value for property '" + propKey +
				                   "'. Using default value: " + dflt, e);
		    }
	    }

	if (!set)
	    out = dflt;

	return out;
    }

    // it's important that we return _MPCONFIG directly, no defensive copies or anything
    // it's unmodifiable, and elsewhere we use simple identity tests to ensure cache freshness
    //
    // see e.g. com.mchange.v2.c3p0.impl.DefaultConnectionTester
    public static MultiPropertiesConfig getMultiPropertiesConfig()
    { return MPCONFIG(); }

    private static void warnOnSet(MultiPropertiesConfig mpcfg)
    {
        tempWarnMultipleXmlSources(mpcfg);
        warnOnUnexpectedProperties(mpcfg);
    }

    private static void tempWarnMultipleXmlSources(MultiPropertiesConfig mpcfg)
    {
        // Under 0.14.x, naming com.mchange.v2.c3p0.cfg.xml meant a classpath /c3p0-config.xml was
        // ignored: only one XML file was ever read. Both are read now, the named file winning key
        // by key, which is a supported configuration -- but a deployment that added the System
        // property and forgot to remove the resource used to get one file and now gets a merge.
        // Remove in a later release, when the merge is no longer news.
        List<String> xmlSources = new ArrayList<String>();
        for (String path : mpcfg.getPropertiesResourcePaths())
            if (path.startsWith("c3p0-xml:") && mpcfg.wasRead(path)) xmlSources.add(path);
        if (xmlSources.size() > 1 && logger.isLoggable(MLevel.WARNING))
            logger.log(MLevel.WARNING,
                       "c3p0 read XML configuration from more than one source: " + xmlSources +
                       ". (Note that System property 'com.mchange.v2.c3p0.cfg.xml' was set to " + mpcfg.getProperty("com.mchange.v2.c3p0.cfg.xml") +
                       ".) These are merged, later sources winning key by key. Before 0.15.0 only one " +
                       "would have been read, so if you named a file in 'com.mchange.v2.c3p0.cfg.xml' " +
                       "expecting it to replace a c3p0-config.xml resource on your classpath, rather than merge over it, " +
                       "remove the resource.");
    }

    private static void warnIfNameContainsMarkers(String configName, Set<String> warned)
    {
        if (configName != null && !warned.contains(configName) && nameContainsMarkers(configName))
        {
            logger.log(MLevel.WARNING, "A named-config has config-name '" + configName + "'. Names containing '" + PROP_STYLE_NAMED_CFG_PART + "', '" + PROP_STYLE_EXTENSIONS_PART + "' or '" + PROP_STYLE_USER_OVERRIDES_PART + "' are not supported. This configuration will be unreadable.");
            warned.add(configName);
        }
    }

    private static void warnIfUserContainsMarkers(String user, Set<String> warned)
    {
        if (user != null && !warned.contains(user) && nameContainsMarkers(user))
        {
            logger.log(MLevel.WARNING, "A user-overrides is defined for user '" + user + "'. User names containing '" + PROP_STYLE_NAMED_CFG_PART + "', '" + PROP_STYLE_EXTENSIONS_PART + "' or '" + PROP_STYLE_USER_OVERRIDES_PART + "' are not recommended.");
            warned.add(user);
        }
    }

    private static void warnOnUnexpectedProperties(MultiPropertiesConfig mpcfg)
    {
        if (logger.isLoggable(MLevel.WARNING))
        {
            Set<String> warnedNames = new HashSet<>();
            Set<String> warnedUsers = new HashSet<>();
            
            Properties c3p0Prefixed = mpcfg.getPropertiesByPrefix(PROPS_FILE_BASE_PFX);
            Set<String> unknown = new TreeSet<>();
            for( String key : c3p0Prefixed.stringPropertyNames() )
            {
                if (!POTENTIALLY_EMPTY_NAMED_SCOPE_MARKER_VALUE.equals(mpcfg.getProperty(key))) // never warn on items set to be markers of potentially empty named scopes
                {
                    Matcher mek = EXTENSIONS_KEY_PATTERN.matcher(key);
                    if (!mek.matches()) // we don't warn on extensions
                    {
                        Matcher mnek = NON_EXTENSIONS_KEY_PATTERN.matcher(key);
                        if (mnek.matches())
                        {
                            String configName = mnek.group(1);
                            String user = mnek.group(2);
                            String prop = mnek.group(3);

                            warnIfNameContainsMarkers( configName, warnedNames );
                            warnIfUserContainsMarkers( user, warnedUsers );

                            if (!C3P0Defaults.isKnownProperty(prop))
                                unknown.add(key);
                        }
                        else
                            unknown.add(key);
                    }
                    else
                        warnIfNameContainsMarkers( mek.group(1), warnedNames );
                }
            }
            for ( String key : unknown )
                logger.log( MLevel.WARNING, "Unexpected c3p0-config property: " + key );
        }
    }
}
