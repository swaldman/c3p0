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
import com.mchange.v1.lang.BooleanUtils;
import com.mchange.v2.c3p0.C3P0Registry;
import com.mchange.v2.csv.MalformedCsvException;
import com.mchange.v2.reflect.ByNameInstantiationUtils;
import com.mchange.v2.reflect.InstantiationNotPermittedException;

//all internal maps should be HashMaps (the implementation presumes HashMaps)

public final class C3P0Config
{
    public final static String FREEZE_CONFIG_KEY = "com.mchange.v2.c3p0.freezeConfig";

    public final static String CFG_FINDER_CLASSNAME_KEY    = "com.mchange.v2.c3p0.cfg.finder";
    public final static String OVERRIDE_REFRESH_LOG_CONFIG = "com.mchange.v2.c3p0.cfg.overrideRefreshLogConfig";

    public final static String DEFAULT_CONFIG_NAME = "default";

    public final static String PROPS_FILE_RSRC_PATH = "/c3p0.properties";

    private final static SealedSystemPropertiesStringProperty  cfgFinderClassname = new SealedSystemPropertiesStringProperty( CFG_FINDER_CLASSNAME_KEY );
    private final static SealedSystemPropertiesBooleanProperty overrideRefreshLogConfig = new SealedSystemPropertiesBooleanProperty( OVERRIDE_REFRESH_LOG_CONFIG, false );
    private final static SealedSystemPropertiesBooleanProperty freezeConfigProperty = new SealedSystemPropertiesBooleanProperty(FREEZE_CONFIG_KEY, true, false, false);

    final static MLogger logger;

    //MT: protected by class' lock
    private static MultiPropertiesConfig _MPCONFIG;
    private static C3P0Config _MAIN;
    private static C3P0ConfigFinder _CFG_FINDER = null;
    private static boolean refreshDebugWarned = false;

    private static class MultiPropertiesConfigMainConfig
    {
        MultiPropertiesConfig multiPropertiesConfig;
        C3P0Config mainConfig;
    }

    static
    {
	logger = MLog.getLogger( C3P0Config.class );

	// if ( logger.isLoggable( MLevel.FINE ) )
	//     logger.log( MLevel.INFO, "Updated main c3p0 cofiguration." );

        MultiPropertiesConfig initial = findInitialMultiPropertiesConfig();

        // temporary, ugly, because config finders
        // don't accept initial as an argument to findConfig()
        // so they expect to find the initial config set
        setLibraryMultiPropertiesConfig( initial );

        MultiPropertiesConfigMainConfig mm = findLibraryC3P0Config( false, initial );

	setLibraryMultiPropertiesConfig( mm.multiPropertiesConfig );
	setMainConfig( mm.mainConfig );

        // we now warn within findLibraryConfig(...),
        // when reconciling C3P0Config and MultiPropertiesConfig views
	//warnOnUnknownProperties( MAIN() );
    }

    // it's important that we return this directly, no defensive copies or anything
    // it's unmodifiable, and elsewhere we use simple identity tests to ensure cache freshness
    private static synchronized MultiPropertiesConfig MPCONFIG() 
    { return _MPCONFIG; }

    private static synchronized C3P0Config MAIN()
    { return _MAIN; }

    private static synchronized void setLibraryMultiPropertiesConfig( MultiPropertiesConfig mpc )
    { _MPCONFIG = mpc; }

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

    private static synchronized void setMainConfig( C3P0Config protoMain )
    { _MAIN = protoMain; }

    // we don't synchronize at the method level to respect
    // the fine-grained sync'ing of the overload to which we ultimately delegate
    public static void refreshMainConfig()
    { refreshMainConfig( (MultiPropertiesConfig[]) null, null ); }

    // we don't synchronize at the method level to respect
    // the fine-grained sync'ing of the overload to which we ultimately delegate
    public static void refreshMainConfig( Properties overrides, String overridesDescription )
    { refreshMainConfig(overrides, overridesDescription, null, null); }

    // we don't synchronize at the method level to respect
    // the fine-grained sync'ing of the overload to which we ultimately delegate
    public static void refreshMainConfig( Properties overrides, String overridesDescription, Properties backstops, String backstopsDescription )
    {
        refreshMainConfig(
            overrides == null ? null : MultiPropertiesConfig.fromProperties( "PROGRAMMATICALLY_SUPPLIED_OVERRIDES", overrides ),
            overridesDescription,
            backstops == null ? null : MultiPropertiesConfig.fromProperties( "PROGRAMMATICALLY_SUPPLIED_BACKSTOPS", backstops ),
            backstopsDescription
        );
    }

    // we don't synchronize at the method level to respect
    // the fine-grained sync'ing of the overload to which we ultimately delegate
    public static void refreshMainConfig( MultiPropertiesConfig overrides, String overridesDescription )
    { refreshMainConfig(overrides, overridesDescription, null, null); }

    // we don't synchronize at the method level to respect
    // the fine-grained sync'ing of the overload to which we ultimately delegate
    public static void refreshMainConfig( MultiPropertiesConfig overrides, String overridesDescription, MultiPropertiesConfig backstops, String backstopsDescription )
    { refreshMainConfig( overrides == null ? null : new MultiPropertiesConfig[]{ overrides }, overridesDescription, backstops == null ? null : new MultiPropertiesConfig[]{ backstops }, backstopsDescription ); }

    // we don't synchronize at the method level to respect
    // the fine-grained sync'ing of the overload to which we ultimately delegate
    public static void refreshMainConfig( MultiPropertiesConfig[] overrides, String overridesDescription )
    { refreshMainConfig(overrides, overridesDescription, null, null); }

    private final static MultiPropertiesConfig[] EMPTY_MPC_ARRAY = new MultiPropertiesConfig[0];

    // later elements of overrides take precedence over earlier ones, later elements of backstops take precedence over earlier ones
    // we synchronize internally to avoid unnecessarily nesting locks when we call C3P0Registry.markConfigRefreshed()
    public static void refreshMainConfig( MultiPropertiesConfig[] overrides, String overridesDescription, MultiPropertiesConfig[] backstops, String backstopsDescription )
    {
        synchronized (C3P0Config.class)
        {
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

                // temporary, ugly, because config finders
                // don't accept initial as an argument to findConfig()
                // so they expect to find an initial config set
                setLibraryMultiPropertiesConfig( newMpc );

                MultiPropertiesConfigMainConfig mm = findLibraryC3P0Config( true, newMpc );
                setLibraryMultiPropertiesConfig( mm.multiPropertiesConfig );
                setMainConfig( mm.mainConfig );

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
                // temporary, ugly, because config finders
                // don't accept initial as an argument to findConfig()
                // so they expect to find an initial config set
                setLibraryMultiPropertiesConfig( initial );

                MultiPropertiesConfigMainConfig mm = findLibraryC3P0Config( false, initial );

                setLibraryMultiPropertiesConfig( mm.multiPropertiesConfig );
                setMainConfig( mm.mainConfig );

                if ( logger.isLoggable( MLevel.INFO ) )
                    logger.log( MLevel.INFO, "c3p0 main configuration was refreshed, with no overrides or backstops specified (and any previous overrides or backstops removed)." );
            }

            if (overrideRefreshLogConfig.getValue(MPCONFIG(), logger))
                MLog.refreshConfig( new MultiPropertiesConfig[] {MPCONFIG()}, "refreshed c3p0 config overrides prior log settings." );

            /*
            System.err.println("All properties...");
            Properties props = initial.getPropertiesByPrefix("");
            Map<Object,Object> m = new TreeMap<Object,Object>();
            m.putAll( (Map<Object,Object>) props );
            for ( Map.Entry<Object,Object> entry : m.entrySet() )
                System.err.println( entry.getKey() + " --> " + entry.getValue() );
            */
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
            withoutDefaults = MConfig.AsProvided.readCachedResourceConfig(SealedSystemProperties.capCurrentThenSealed(resources));
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

    private static MultiPropertiesConfigMainConfig findLibraryC3P0Config( boolean warn_on_conflicting_overrides, MultiPropertiesConfig mpcfg )
    {
	C3P0Config protoMain;

        // note: this has the side-effect of sealing SealedSystemProperties
        //       if they have not already been sealed.
	String cname = cfgFinderClassname.getValue(mpcfg, logger);

	try
	    {
		if (cname != null)
                {
                    if (_CFG_FINDER == null)
                    {
                        // since cname had to have been explicitly specified in config, we do not gate this
                        _CFG_FINDER = (C3P0ConfigFinder) ByNameInstantiationUtils.instantiateByNameUngated( cname );
                        if (logger.isLoggable(MLevel.WARNING))
                            logger.log(MLevel.WARNING,
                                       "A deployment-specified C3P0ConfigFinder has been installed, '" + cname +
                                       "'. This is unsupported and potentially dangerous. " +
                                       "If this is intended, please let c3p0's developers know what you are up to, " +
                                       "so that whatever it is can be better supported. " +
                                       "Unless users make a case to keep it, support for deployment-specified C3P0ConfigFinders may be removed from a future release."
                                       );
                    }
                    else if (!_CFG_FINDER.getClass().getName().equals(cname) && logger.isLoggable(MLevel.WARNING))
                    {
                        String theConfigFinder = _CFG_FINDER.getClass().equals(DefaultC3P0ConfigFinder.class) ? "The default C3P0ConfigFinder" : "C3P0ConfigFinder '" + _CFG_FINDER.getClass().getName() + "'";
                        logger.log(MLevel.WARNING,
                                   "For security reasons, an attempt to install C3P0ConfigFinder '" +
                                   cname +
                                   "' will be IGNORED! " +
                                   theConfigFinder +
                                   " has already been installed and will continue to be used on configuration refreshes. " +
                                   "c3p0 does not support changing the C3P0ConfigFinder instance of a live application.");
                    }
                }
	    }
	catch (Exception e)
	    {
		// reflective construction wraps whatever the constructor threw; report the cause
		Throwable t = ( e instanceof InvocationTargetException ? e.getCause() : e );

		if ( logger.isLoggable(MLevel.WARNING) )
		    logger.log( MLevel.WARNING, "Could not load specified C3P0ConfigFinder class'" + cname + "'.", t);
	    }

	try
	    { 
		if (_CFG_FINDER == null)
		    {
			Class.forName("org.w3c.dom.Node");
			Class.forName("com.mchange.v2.c3p0.cfg.C3P0ConfigXmlUtils"); //fail nicely if we don't have XML libs
			_CFG_FINDER = new DefaultC3P0ConfigFinder( warn_on_conflicting_overrides );
		    }

                // this is just an ugly hack. the built-in DefaultC3P0ConfigFinder expected to be
                // rebuilt on any refresh so warn_on_conflicting_overrides could become a constructor
                // override. User-supplied C3P0ConfigFinder classes should be immutable and stateless,
                // and can carry no constructor args
                //
                // for our inelegant special case, we'll just reconstruct the thing
                if (_CFG_FINDER.getClass().equals(DefaultC3P0ConfigFinder.class))
                    _CFG_FINDER = new DefaultC3P0ConfigFinder( warn_on_conflicting_overrides );

	    }
	catch (Exception e)
	    { 
		if ( e instanceof ClassNotFoundException )
		{
		    if ( logger.isLoggable(MLevel.WARNING) )
			logger.log( MLevel.WARNING, "XML configuration disabled! Verify that standard XML libs are available.", e);
		}
		else
		{
		    if ( logger.isLoggable(MLevel.SEVERE) )
			logger.log( MLevel.SEVERE, "An unexpected error occurred while trying to check for XML libraries. XML config will likely be ignored!", e );
		}

                _CFG_FINDER = new MConfigOnlyC3P0ConfigFinder();
	    }

        String configFinderIdSuffix;
        try
        {
            protoMain = _CFG_FINDER.findConfig();
            if (_CFG_FINDER.getClass().equals(DefaultC3P0ConfigFinder.class))
                configFinderIdSuffix = "Standard-XML-Config";
            else
                configFinderIdSuffix = _CFG_FINDER.getClass().getName(); 
        }
        catch (Exception e)
        {
            if (_CFG_FINDER.getClass().equals(DefaultC3P0ConfigFinder.class))
            {
                if (logger.isLoggable(MLevel.SEVERE))
                    logger.log(MLevel.SEVERE,
                               "c3p0's default configuration finder threw an unexpected Exception while trying to load XML configuration. " +
                               "Any configuration it would have found -- c3p0-config.xml in particular -- IS BEING IGNORED. " +
                               "c3p0's configuration will be constructed only from its properties/hocon-based configuration.", e);
            }
            else
            {
                if (logger.isLoggable(MLevel.WARNING))
                    logger.log(MLevel.WARNING,
                               "C3P0ConfigFinder '" + _CFG_FINDER.getClass().getName() + "' threw an Exception while trying to locate configuration. " +
                               "c3p0's configuration will be constructed only from its properties/hocon-based configuration.", e);
            }

            // this is the most likely to succeed source of config we have.
            // if it fails with some unchecked Exception, we give up
            protoMain = new MConfigOnlyC3P0ConfigFinder().findConfig();
            configFinderIdSuffix = null; // we should not see any non-name-scoped config not already in _MPCONFIG
        }

	HashMap propStyleConfigNamesToNamedScopes = C3P0ConfigUtils.findPropStyleNamedScopes(mpcfg);
	HashMap cfgFoundConfigNamesToNamedScopes = protoMain.configNamesToNamedScopes;
	HashMap mergedConfigNamesToNamedScopes = new HashMap();

	HashSet allConfigNames = new HashSet( cfgFoundConfigNamesToNamedScopes.keySet() );
	allConfigNames.addAll( propStyleConfigNamesToNamedScopes.keySet() );

	for ( Iterator ii = allConfigNames.iterator(); ii.hasNext(); )
	{
	    String cfgName = (String) ii.next();
	    NamedScope cfgFound  = (NamedScope) cfgFoundConfigNamesToNamedScopes.get( cfgName );
	    NamedScope propStyle = (NamedScope) propStyleConfigNamesToNamedScopes.get( cfgName );
	    if ( cfgFound != null && propStyle != null )
		mergedConfigNamesToNamedScopes.put( cfgName, cfgFound.mergedOver( propStyle ) );
	    else if ( cfgFound != null && propStyle == null )
		mergedConfigNamesToNamedScopes.put( cfgName, cfgFound );
	    else if ( cfgFound == null && propStyle != null )
		mergedConfigNamesToNamedScopes.put( cfgName, propStyle );
	    else 
		throw new AssertionError("Huh? allConfigNames is the union, every name should be in one of the two maps.");
	}

	HashMap propStyleUserOverridesDefaultConfig = C3P0ConfigUtils.findPropStyleUserOverrides(mpcfg);
	HashMap propStyleExtensionsDefaultConfig = C3P0ConfigUtils.findPropStyleExtensions(mpcfg);
	NamedScope mergedDefaultConfig = new NamedScope( protoMain.defaultConfig.props, 
							 NamedScope.mergeUserNamesToOverrides( protoMain.defaultConfig.userNamesToOverrides, propStyleUserOverridesDefaultConfig ), 
							 NamedScope.mergeExtensions( protoMain.defaultConfig.extensions, propStyleExtensionsDefaultConfig ) );

        C3P0Config normative = new C3P0Config( mergedDefaultConfig, mergedConfigNamesToNamedScopes );
        MultiPropertiesConfigMainConfig out = new MultiPropertiesConfigMainConfig();
        out.multiPropertiesConfig = reconcileMultiPropertiesConfig(normative, mpcfg, configFinderIdSuffix);
        out.mainConfig = normative;

        if (logger.isLoggable(MLevel.INFO))
            logger.log(MLevel.INFO,
                       "c3p0 properties-style config is now drawn from the following configuration sources: " +
                       Arrays.toString(out.multiPropertiesConfig.getPropertiesResourcePaths()) +
                       ". Later paths shadow earlier paths where values conflict.");

	return out;
    }

    private static MultiPropertiesConfig reconcileMultiPropertiesConfig(C3P0Config normative, MultiPropertiesConfig mpcfg, String configFinderIdSuffix)
    {
        Properties normativeAdditions = new Properties();
        addDefaultScopePropertiesIfNewOrDifferent(mpcfg, normativeAdditions, normative.defaultConfig.props);
        for (Object rawUser : normative.defaultConfig.userNamesToOverrides.keySet())
        {
            String user = (String) rawUser;
            Map userOverrides = (Map) normative.defaultConfig.userNamesToOverrides.get(rawUser);
            addUserOverridesPropertiesWithPrefixIfNewOrDifferent(mpcfg, normativeAdditions, "c3p0.user-overrides." + user + ".", userOverrides, user, null);
        }
        addExtensionsPropertiesWithPrefixIfNewOrDifferent(mpcfg, normativeAdditions, "c3p0.extensions.", normative.defaultConfig.extensions);

        for (Object rawConfigName : normative.configNamesToNamedScopes.keySet())
        {
            String configName = (String) rawConfigName;
            NamedScope scope  = (NamedScope) normative.configNamesToNamedScopes.get(rawConfigName);
            addNamedScopePropertiesIfNewOrDifferent(mpcfg, normativeAdditions, "c3p0.named-configs." + configName + ".", scope.props, configName);
            for (Object rawUser : scope.userNamesToOverrides.keySet())
            {
                String user = (String) rawUser;
                Map userOverrides = (Map) scope.userNamesToOverrides.get(rawUser);
                addUserOverridesPropertiesWithPrefixIfNewOrDifferent(mpcfg, normativeAdditions, "c3p0.named-configs." + configName + ".user-overrides." + user + ".", userOverrides, user, configName);
            }
            addExtensionsPropertiesWithPrefixIfNewOrDifferent(mpcfg, normativeAdditions, "c3p0.named-configs." + configName + ".extensions.", scope.extensions);
        }

        MultiPropertiesConfig out;
        if (normativeAdditions.size() > 0)
        {
            MultiPropertiesConfig extra = MultiPropertiesConfig.fromProperties( "C3P0_CONFIG_FINDER_ADDITIONS-" + configFinderIdSuffix, normativeAdditions );
            out = MConfig.combine(new MultiPropertiesConfig[]{mpcfg,extra});
        }
        else
            out = mpcfg;

        return out;
    }

    private static void addDefaultScopePropertiesIfNewOrDifferent(MultiPropertiesConfig base, Properties dest, Map source)
    {
        for (Object rawKey : source.keySet())
        {
            String key = (String) rawKey;
            String val = asPropertyValue(key, source.get(rawKey));
            if (val != null)
            {
                String propsKey;
                if (key.indexOf('.') >= 0)
                    propsKey = key;
                else if (C3P0Defaults.isKnownProperty(key))
                    propsKey = C3P0ConfigUtils.PROPS_FILE_PROP_PFX + key;
                else // unexpected noncompoundKey
                {
                    String prefixed = C3P0ConfigUtils.PROPS_FILE_PROP_PFX + key;
                    if (logger.isLoggable(MLevel.WARNING))
                        logger.log(MLevel.WARNING,
                                   "Found unexpected (non-compound) key in C3P0Config: " + key +
                                   ". Perhaps it is a misspelling? In the properties-based config, it will appear as '"
                                   + prefixed + "'.");
                    propsKey = prefixed;
                }

                addPropertyIfNewOrDifferent(base,dest,propsKey,val);
            }
        }
    }

    private static void addNamedScopePropertiesIfNewOrDifferent(MultiPropertiesConfig base, Properties dest, String prefix, Map source, String scopeName)
    {
        for (Object rawKey : source.keySet())
        {
            String key = (String) rawKey;
            
            String propsKey;
            if (key.startsWith(C3P0ConfigUtils.PROPS_FILE_PROP_PFX) && key.lastIndexOf('.') == C3P0ConfigUtils.PROPS_FILE_PROP_PFX_LEN - 1) // two-part c3p0.xxx key
            {
                propsKey = key.substring(C3P0ConfigUtils.PROPS_FILE_PROP_PFX_LEN);
                if (logger.isLoggable(MLevel.WARNING) && !C3P0Defaults.isKnownProperty(propsKey))
                    logger.log(MLevel.WARNING, "Found unexpected c3p0-prefixed, two-part key in C3P0Config, in named scope '" + scopeName  + "': " + key);
            }
            else if (key.indexOf('.') >= 0)
            {
                if (logger.isLoggable(MLevel.WARNING))
                    logger.log(MLevel.WARNING, "Found unexpected compound key in C3P0Config, in named scope '" + scopeName  + "': " + key);
                propsKey = key;
            }
            else
            {
                if (logger.isLoggable(MLevel.WARNING) && !C3P0Defaults.isKnownProperty(key))
                    logger.log(MLevel.WARNING,
                               "Found unexpected (noncompound) key in C3P0Config, in named scope '" + scopeName  + "': " + key +
                               ". In the properties-based config, it will appear as '" + prefix + key + "'.");
                propsKey = key;
            }

            String val = asPropertyValue(propsKey, source.get(rawKey));
            if (val != null)
                addPropertyIfNewOrDifferent(base,dest,prefix+propsKey,val);
        }
    }

    // include the '.' in the prefix!
    private static void addUserOverridesPropertiesWithPrefixIfNewOrDifferent(MultiPropertiesConfig base, Properties dest, String prefix, Map source, String userName, String scopeName)
    {
        for (Object rawKey : source.keySet())
        {
            String key = (String) rawKey;

            String propsKey;
            if (key.startsWith(C3P0ConfigUtils.PROPS_FILE_PROP_PFX) && key.lastIndexOf('.') == C3P0ConfigUtils.PROPS_FILE_PROP_PFX_LEN - 1) // two-part c3p0.xxx key
            {
                propsKey = key.substring(C3P0ConfigUtils.PROPS_FILE_PROP_PFX_LEN);
                if (logger.isLoggable(MLevel.WARNING) && !C3P0Defaults.isKnownProperty(propsKey))
                    logger.log(MLevel.WARNING,
                               "Found unexpected c3p0-prefixed, two-part key in C3P0Config, in a user-override for user '" + userName +
                               (scopeName == null ? "' of the default scope" : "' of named scope '" + scopeName + "'") +
                               ": " + key + ". Perhaps it is a misspelling? It will become '" + prefix+propsKey +
                               "' in the properties-based config.");
            }
            else if (key.indexOf('.') >= 0)
            {
                if (logger.isLoggable(MLevel.WARNING))
                {
                    String msg;
                    if (scopeName == null)
                        msg = "Found unexpected key in C3P0Config, in a user-override for user '" + userName + "' of the default scope: " + key;
                    else
                        msg = "Found unexpected key in C3P0Config, in a user-override for user '" + userName + "' of named scope '" + scopeName + "': " + key;
                    logger.log(MLevel.WARNING, msg);
                }
                propsKey = key;
            }
            else
            {
                propsKey = key;
                if (logger.isLoggable(MLevel.WARNING))
                    logger.log(MLevel.WARNING,
                               "Found unexpected (non-compound) key in C3P0Config, in a user-override for user '" + userName +
                               (scopeName == null ? "' of the default scope" : "' of named scope '" + scopeName + "'") +
                               ": " + key + ". Perhaps it is a misspelling? It will become '" + prefix+propsKey +
                               "' in the properties-based config.");
            }

            String val = asPropertyValue(prefix + propsKey, source.get(rawKey));
            if (val != null)
                addPropertyIfNewOrDifferent(base,dest,prefix+propsKey,val);
        }
    }

    private static void addExtensionsPropertiesWithPrefixIfNewOrDifferent(MultiPropertiesConfig base, Properties dest, String prefix, Map source)
    {
        for (Object rawKey : source.keySet())
        {
            String key = (String) rawKey;

            // we pass throigh extensions exactly as provided. we don't presume
            // or know of what we should warn

            String val = asPropertyValue(prefix + key, source.get(rawKey));
            if (val != null)
                addPropertyIfNewOrDifferent(base,dest,prefix+key,val);
        }
    }

    private static String asPropertyValue(String describeKey, Object rawVal)
    {
        if (rawVal instanceof String) return (String) rawVal;
        if (logger.isLoggable(MLevel.WARNING))
            logger.log(MLevel.WARNING, "'" + describeKey + "' has non-String value, not reflected in properties-style config.");
        return null;
    }

    private static void addPropertyIfNewOrDifferent(MultiPropertiesConfig base, Properties dest, String key, String value)
    {
        String checkVal = base.getProperty(key);
        if (checkVal == null || !checkVal.equals(value))
        {
            // System.err.println("Adding " + key + " -> " + value + "; checkVal: " + checkVal + "; value: " + value);
            dest.setProperty(key,value);
        }
    }

    /*
    // we now warn when reconciling C3P0Config and MultiPropertiesConfig views
    private static void warnOnUnknownProperties( C3P0Config cfg )
    {
	warnOnUnknownProperties( cfg.defaultConfig );
	for (Iterator ii = cfg.configNamesToNamedScopes.values().iterator(); ii.hasNext(); )
	    warnOnUnknownProperties( (NamedScope) ii.next() );
    }

    private static void warnOnUnknownProperties( NamedScope scope )
    {
	warnOnUnknownProperties( scope.props );
	for (Iterator ii = scope.userNamesToOverrides.values().iterator(); ii.hasNext(); )
	    warnOnUnknownProperties( (Map) ii.next() );
    }

    private static void warnOnUnknownProperties( Map propMap )
    {
	for (Iterator ii = propMap.keySet().iterator(); ii.hasNext(); )
	    {
		String prop = (String) ii.next();
		if (! C3P0Defaults.isKnownProperty( prop ) && logger.isLoggable( MLevel.WARNING ))
		    logger.log( MLevel.WARNING, "Unknown c3p0-config property: " + prop);
	    }
    }
    */

    /**
     *  @deprecated In general, try to use com.mchange.v2.cfg.SealedSystemPropertiesStringProperty with C3P0Config.getMultiPropertiesConfig() as its argument.
     */
    @Deprecated
    public static String getPropsFileConfigProperty( String prop )
    { return MPCONFIG().getProperty( prop ); }

    // static Properties findResourceProperties()
    // { return MPCONFIG().getPropertiesByResourcePath(PROPS_FILE_RSRC_PATH); }

    public static String getUnspecifiedUserProperty( String propKey, String configName )
    {
	  String out = null;

 	  if (configName == null)
	      out = (String) MAIN().defaultConfig.props.get( propKey );
	  else
	      {
		  NamedScope named = (NamedScope) MAIN().configNamesToNamedScopes.get( configName );
		  if (named != null)
		      out = (String) named.props.get(propKey);
		  else
		      logger.warning("named-config with name '" + configName + "' does not exist. Using default-config for property '" + propKey + "'.");

		  if (out == null)
		      out = (String) MAIN().defaultConfig.props.get( propKey );
	      }
	  
	  return out;
    }

    public static Map getExtensions(String configName)
    {
	HashMap raw = MAIN().defaultConfig.extensions;
	if (configName != null)
	    {
		  NamedScope named = (NamedScope) MAIN().configNamesToNamedScopes.get( configName );
		  if (named != null)
		      raw = named.extensions;
		  else
		      logger.warning("named-config with name '" + configName + "' does not exist. Using default-config extensions.");
	    }
	return Collections.unmodifiableMap( raw );
    }

    public static Map getUnspecifiedUserProperties(String configName)
    {
	Map out = new HashMap();

	out.putAll( MAIN().defaultConfig.props );

	if (configName != null)
	    {
		  NamedScope named = (NamedScope) MAIN().configNamesToNamedScopes.get( configName );
		  if (named != null)
		      out.putAll( named.props );
		  else
		      logger.warning("named-config with name '" + configName + "' does not exist. Using default-config.");
	    }

	return out;
    }

    public static Map getUserOverrides( String configName )
    {
	Map out = new HashMap();

	NamedScope namedConfigScope = null;

	if (configName != null)
	    namedConfigScope = (NamedScope) MAIN().configNamesToNamedScopes.get( configName );

	out.putAll( MAIN().defaultConfig.userNamesToOverrides );

	if (namedConfigScope != null)
	    out.putAll( namedConfigScope.userNamesToOverrides );

	return (out.isEmpty() ? null : out );
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

    NamedScope defaultConfig;
    HashMap configNamesToNamedScopes;

    C3P0Config( NamedScope defaultConfig, HashMap configNamesToNamedScopes)
    {
	this.defaultConfig = defaultConfig;
	this.configNamesToNamedScopes = configNamesToNamedScopes;
    }

    public static void debugMain() { MAIN().debug(); }

    public void debug()
    {
        System.err.println("defaultConfig:");
        debugNamedScope( this.defaultConfig );
        for (Object o : configNamesToNamedScopes.keySet())
        {
            System.err.println();
            System.err.println("namedScope '" + o + "':");
            debugNamedScope((NamedScope) configNamesToNamedScopes.get(o));
        }
    }

    public void debugNamedScope(NamedScope scope)
    {
        System.err.println( "props:" );
        System.err.println( scope.props );
        System.err.println();
        System.err.println( "userNamesToOverrides:" );
        System.err.println( scope.userNamesToOverrides );
        System.err.println();
        System.err.println( "extensions:" );
        System.err.println( scope.extensions );
    }

//     C3P0Config()
//     {
// 	this.defaultConfig = new NamedScope();
// 	this.configNamesToNamedScopes = new HashMap();
//     }

}
