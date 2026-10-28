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
import static com.mchange.v2.c3p0.cfg.C3P0ConfigUtils.PROP_STYLE_NAMED_CFG_PART;
import static com.mchange.v2.c3p0.cfg.C3P0ConfigUtils.PROP_STYLE_EXTENSIONS_PART;
import static com.mchange.v2.c3p0.cfg.C3P0ConfigUtils.PROP_STYLE_USER_OVERRIDES_PART;

//all internal maps should be HashMaps (the implementation presumes HashMaps)

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

    // it's important that we return this directly, no defensive copies or anything
    // it's unmodifiable, and elsewhere we use simple identity tests to ensure cache freshness
    private static synchronized MultiPropertiesConfig MPCONFIG()
    { return _MPCONFIG; }

    private static synchronized void setLibraryMultiPropertiesConfig( MultiPropertiesConfig mpc )
    {
        _MPCONFIG = mpc;
        warnOnSet(_MPCONFIG);
    }

    /**
     *  This will include System properties, and
     *  sensitive values like c3p0.password (even if they
     *  are supplied by XML config or a C3P0ConfigFinder).
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
        }
        // let's not unnecessarily nest locks
	C3P0Registry.markConfigRefreshed();
    }

    private static MultiPropertiesConfig findInitialMultiPropertiesConfig()
    {
        MultiPropertiesConfig out;

        MultiPropertiesConfig withoutDefaults;
        if (MConfig.WithTraditionalDefaultSources.traditionalPathFileSourcedPathsFound())
        {
            // very inelegantly, keep these load paths in sync with those com.mchange.v2.log.MLogConfig.refresh()
            String[] defaults = MLogConfig.mlogC3p0Defaults();
            String[] preempts = SealedSystemProperties.capCurrentThenSealed(MLogConfig.mlogC3p0Preempts());
            withoutDefaults = MConfig.WithTraditionalDefaultSources.readCachedResourceConfig( defaults, preempts );

            if ( logger.isLoggable( MLevel.WARNING ) )
            {
                logger.log(MLevel.WARNING,
                           "At least one of the following resources " + MConfig.WithTraditionalDefaultSources.traditionalPathFileResourcesSearched() +
                           " has been found, so c3p0 will configure itself using the traditional mechanism that honored these files. " +
                           "However, c3p0 is transitioning from this mechanism, which is fragile and obscure. The supported way to customize configuration " +
                           "sources for c3p0 is to augment a now hardcoded list when calling an overload of refreshMainConfig(...).");
            }
        }
        else
        {
            // very inelegantly, keep these load paths in sync with those com.mchange.v2.log.MLogConfig.refresh()
            String[] resources = MLogConfig.mlogC3p0Unified();
            withoutDefaults = MConfig.AsProvidedIgnoreVetoed.readCachedResourceConfig(SealedSystemProperties.capCurrentThenSealed(resources));
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

    private static String defaultScopePrefix(String user)
    { return (user == null ? PROPS_FILE_BASE_PFX : PROP_STYLE_DEFAULT_USER_OVERRIDES_PFX + "." + user); }

    private static String defaultScopeKey(String user, String unprefixedKey)
    { return defaultScopePrefix(user) + "." + unprefixedKey; }

    private static String defaultScopeExtensionsKey()
    { return defaultScopeKey(null, PROP_STYLE_EXTENSIONS_PART); }

    private static String namedScopePrefix(String configName, String user)
    {
        String base = C3P0ConfigUtils.PROP_STYLE_NAMED_CFG_PFX + "." + configName;
        return (user == null ? base : base + "." + PROP_STYLE_USER_OVERRIDES_PART + "." + user);
    }

    private static String namedScopeKey(String configName, String user, String unprefixedKey)
    { return namedScopePrefix(configName, user) + "." + unprefixedKey; }

    private static String namedScopeExtensionsKey(String configName)
    { return namedScopeKey(configName, null, PROP_STYLE_EXTENSIONS_PART); }

    private static boolean _namedConfigIsDefined(String configName)
    {
        String prefix = namedScopePrefix(configName, null);

        // never null, whether empty or undefined
        // but if defined, it will be non-empty (even if in fact
        // it contains no subkeys, a dummy base key will exist if
        // the named scope has been defined but no keys were set
        Properties defined = MPCONFIG().getPropertiesByPrefix(prefix);
        return (defined.size() > 0);
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

    public static Properties getNamedConfig(String configName)
    {
        if (!namedConfigIsDefined(configName))
            return null;
        else
            return MPCONFIG().getPropertiesByPrefix(namedScopePrefix(configName,null)); // never null, whether empty or undefined
    }

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

    final static Pattern NAMED_CONFIGS_PATTERN = Pattern.compile("^c3p0\\.named-configs\\.[^\\.]*\\.(.*)$");

    private static Properties convertNamedScopePropsToBasePrefixProps(Properties props)
    {
        Properties out = new Properties();
        for (String key : props.stringPropertyNames())
        {
            Matcher m = NAMED_CONFIGS_PATTERN.matcher(key);
            if (m.matches())
            {
                String val = props.getProperty(key);
                out.setProperty( PROPS_FILE_PROP_PFX + m.group(1), val );
            }
        }
        return out;
    }

    private static synchronized Map<String,String> _getUnspecifiedUserProperties(String configName)
    {
        String defaultPrefix = defaultScopePrefix(null);

	Properties raw = MPCONFIG().getPropertiesByPrefix(defaultPrefix);
	if (configName != null)
        {
            String namedPrefix = namedScopePrefix(configName,null);
            Properties named = MPCONFIG().getPropertiesByPrefix(namedPrefix);
            if (named.isEmpty())
            {
                if (!namedConfigIsDefined(configName))
                {
                    if (logger.isLoggable(MLevel.WARNING))
                        logger.log(MLevel.WARNING, "Properties (for an unspecified user) cannot be found for undefined named config '" + configName + "'. Only default config values obtain.");
                    //no need, we already contain only default properties
                    //raw = MPCONFIG().getPropertiesByPrefix(defaultKey);
                }
            }
            else
                raw.putAll(convertNamedScopePropsToBasePrefixProps(named)); // we override with values from the named scope, converted into normal c3p0.xxx-style props
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

    private final static Pattern USER_OVERRIDES_KEY_TAIL_PATTERN = Pattern.compile("^([^\\.]*)\\.(.*)$");

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

    private static Set<UserOverrideTriplet> convertNamedScopePropsToBasePrefixProps(String bareScopeUserOverridesPrefix, Properties props)
    {
        Set<UserOverrideTriplet> out = new HashSet<>();
        for (String key : props.stringPropertyNames())
        {
            if (!bareScopeUserOverridesPrefix.equals(key))
            {
                int strip_me_len = bareScopeUserOverridesPrefix.length() + 1; // don't forget the period
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

    private static Map<String,Map<String,String>> _getUserOverridesFromScope(String bareScopeUserOverridesPrefix)
    {
	Properties               props    = MPCONFIG().getPropertiesByPrefix( bareScopeUserOverridesPrefix );
        Set<UserOverrideTriplet> triplets = convertNamedScopePropsToBasePrefixProps( bareScopeUserOverridesPrefix, props );

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
        String defaultPrefix = defaultScopePrefix(null)  + "." + PROP_STYLE_USER_OVERRIDES_PART;
        Map<String,Map<String,String>> tmp = _getUserOverridesFromScope(defaultPrefix);

	if (configName != null)
        {
            String namedPrefix = namedScopePrefix(configName,null) + "." + PROP_STYLE_USER_OVERRIDES_PART;
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

    private static void warnOnUnexpectedProperties(MultiPropertiesConfig mpcfg)
    {
        if (logger.isLoggable(MLevel.WARNING))
        {
            Properties c3p0Prefixed = mpcfg.getPropertiesByPrefix(PROPS_FILE_BASE_PFX);
            Set<String> unknown = new TreeSet<>();
            for( String key : c3p0Prefixed.stringPropertyNames() )
            {
                String[] elements = key.split("\\.");
                String second;
                String fourth;
                String last;

                switch (elements.length)
                {
                case 0:
                    logger.severe("Huh?!? We should only see items with at least a 'c3p0' element here! key=" + key);
                    unknown.add(key);
                    break;
                case 1:
                    unknown.add(key); // just c3p0 is not an expected config key
                    break;
                case 2: // c3p0.xxx
                    if (!C3P0Defaults.isKnownProperty(elements[1]))
                        unknown.add(key);
                    break;
                case 3: // c3p0.named-configs.intergalacto (define scope without subkey) or c3p0.extensions.xxx (any xxx permitted)
                    second = elements[1];
                    if (!(PROP_STYLE_NAMED_CFG_PART.equals(second) || PROP_STYLE_EXTENSIONS_PART.equals(second)))
                        unknown.add(key);
                    break;
                case 4: // c3p0.named-configs.intergalacto.xxx or c3p0.extensions.xxx.yyy (any xxx.yyy permitted) or c3p0.user-overrides.steve.xxx
                    second = elements[1];
                    last   = elements[3];
                    if (!PROP_STYLE_EXTENSIONS_PART.equals(second))
                    {
                        if (PROP_STYLE_NAMED_CFG_PART.equals(second))
                        { if (!C3P0Defaults.isKnownProperty(last)) unknown.add(key); }
                        else if (PROP_STYLE_USER_OVERRIDES_PART.equals(second))
                        { if (!C3P0Defaults.isKnownProperty(last)) unknown.add(key); }
                        else
                            unknown.add(key);
                    }
                    break;
                case 5:
                    second = elements[1];
                    fourth = elements[3];
                    last   = elements[4];
                    if (!(PROP_STYLE_EXTENSIONS_PART.equals(second) || (PROP_STYLE_NAMED_CFG_PART.equals(second) && PROP_STYLE_EXTENSIONS_PART.equals(fourth)))) // skip valid extensions keys
                    {
                        unknown.add(key);
                    }
                    break;
                case 6: // c3p0.named-configs.intergalacto.user-overrides.steve.xxx
                    second = elements[1];
                    fourth = elements[3];
                    last   = elements[5];
                    if (!(PROP_STYLE_EXTENSIONS_PART.equals(second) || (PROP_STYLE_NAMED_CFG_PART.equals(second) && PROP_STYLE_EXTENSIONS_PART.equals(fourth)))) // skip valid extensions keys
                    {
                        if (PROP_STYLE_NAMED_CFG_PART.equals(second) && PROP_STYLE_USER_OVERRIDES_PART.equals(fourth))
                        { if (!C3P0Defaults.isKnownProperty(last)) unknown.add(key); }
                        else
                            unknown.add(key);
                    }
                    break;
                default: // c3p0.extensions.xxx.yyy.zzz or c3p0.named-configs.intergalacto.extensions.xxx.yyy.zzz
                    second = elements[1];
                    fourth = elements[3];
                    last   = elements[elements.length-1];
                    if (!(PROP_STYLE_EXTENSIONS_PART.equals(second) || (PROP_STYLE_NAMED_CFG_PART.equals(second) && PROP_STYLE_EXTENSIONS_PART.equals(fourth)))) // skip valid extensions keys
                        unknown.add(key);
                    break;
                }
            }
            for ( String key : unknown )
                logger.log( MLevel.WARNING, "Unexpected c3p0-config property: " + key );
        }
    }
}
