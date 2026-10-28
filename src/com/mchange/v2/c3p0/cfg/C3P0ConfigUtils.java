package com.mchange.v2.c3p0.cfg;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import com.mchange.v2.cfg.*;
import com.mchange.v2.log.*;
import com.mchange.v2.c3p0.impl.*;

import com.mchange.v2.lang.Coerce;

public final class C3P0ConfigUtils
{
    public final static String PROPS_FILE_BASE_PFX      = "c3p0";
    public final static String PROPS_FILE_RSRC_PATH     = "/c3p0.properties";
    public final static String PROPS_FILE_PROP_PFX      = PROPS_FILE_BASE_PFX + ".";
    public final static int    PROPS_FILE_PROP_PFX_LEN  = 5;

    private final static String[] MISSPELL_PFXS = {"/c3pO", "/c3po", "/C3P0", "/C3PO"};

    final static String PROP_STYLE_NAMED_CFG_PART                 = "named-configs";
    final static String PROP_STYLE_NAMED_CFG_PFX                  = "c3p0." + PROP_STYLE_NAMED_CFG_PART;
    final static int    PROP_STYLE_NAMED_CFG_PFX_LEN              = PROP_STYLE_NAMED_CFG_PFX.length();
    final static String PROP_STYLE_USER_OVERRIDES_PART            = "user-overrides";
    final static String PROP_STYLE_DEFAULT_USER_OVERRIDES_PFX     = "c3p0." + PROP_STYLE_USER_OVERRIDES_PART;
    final static int    PROP_STYLE_DEFAULT_USER_OVERRIDES_PFX_LEN = PROP_STYLE_DEFAULT_USER_OVERRIDES_PFX.length();
    final static String PROP_STYLE_EXTENSIONS_PART                = "extensions";
    final static String PROP_STYLE_EXTENSIONS_PFX                 = "c3p0." + PROP_STYLE_EXTENSIONS_PART;
    final static int    PROP_STYLE_EXTENSIONS_PFX_LEN             = PROP_STYLE_EXTENSIONS_PFX.length();

    final static Set EXPECTED_NOT_COERCIBLE_DEFAULTS;

    final static MLogger logger = MLog.getLogger( C3P0ConfigUtils.class );

    final static Set<String> DANGEROUS_NO_PREFIX_C3P0_PROPERTIES; // might point c3p0 to execute unexpected code, or escalate security privileges (in older JVMs)

    // MT: protected by class' lock
    private static HashMap    UNCOERCED_HARDCODED_DEFAULTS = null;
    private static HashMap    COERCED_IF_POSSIBLE_HARDCODED_DEFAULTS = null;
    private static Properties ONLY_COERCED_HARDCODED_DEFAULTS = null;

    static
    {
        if ( logger.isLoggable(MLevel.WARNING) && C3P0ConfigUtils.class.getResource( PROPS_FILE_RSRC_PATH ) == null )
        {
            // warn on a misspelling... its an ugly way to do this, but since resources are not listable...
            for (int i = 0; i < MISSPELL_PFXS.length; ++i)
            {
                String test = MISSPELL_PFXS[i] + ".properties";
                if (C3P0ConfigUtils.class.getResource( MISSPELL_PFXS[i] + ".properties" ) != null)
                {
                    logger.warning("POSSIBLY MISSPELLED c3p0.properties CONFIG RESOURCE FOUND. " +
                                   "Please ensure the file name is c3p0.properties, all lower case, " +
                                   "with the digit 0 (NOT the letter O) in c3p0. It should be placed " +
                                   " in the top level of c3p0's effective classpath.");
                    break;
                }
            }
        }

        HashSet<String> tmp = new HashSet<>();
        for (Iterator ii = C3P0Defaults.getKnownProperties( null ).iterator(); ii.hasNext(); )
        {
            String propName = (String) ii.next();
            if (propName != null && propName.indexOf("ClassName") >= 0)
                tmp.add(propName);
        }
        tmp.add("driverClass");
        tmp.add("factoryClassLocation");
        tmp.add("privilegeSpawnedThreads");
        DANGEROUS_NO_PREFIX_C3P0_PROPERTIES = Collections.unmodifiableSet(tmp);

        HashSet noncoercibleTmp = new HashSet();
        noncoercibleTmp.add("extensions");
        EXPECTED_NOT_COERCIBLE_DEFAULTS = Collections.unmodifiableSet(noncoercibleTmp);
    }

    /**
     *  @param stringify_coercibles these properties may eventually be used to initialize bean properties
     *         reflectively. to avoid having to get types exactly right (e.g. is it a Double or a double or an int?),
     *         it can be convenient to just represent these values as Strings and coerce those to the appropriate
     *         type when needed using com.mchange.v2.lang.Coerce. If true, we convert all coercible values into Strings.
     *         Non-coercible values are always left alone.
     */
    public synchronized static HashMap extractHardcodedC3P0Defaults(boolean stringify_coercibles)
    {
        if (stringify_coercibles)
        {
            if (COERCED_IF_POSSIBLE_HARDCODED_DEFAULTS == null)
                COERCED_IF_POSSIBLE_HARDCODED_DEFAULTS = _extractHardcodedC3P0Defaults(true);
            return (HashMap) COERCED_IF_POSSIBLE_HARDCODED_DEFAULTS.clone();
        }
        else
        {
            if (UNCOERCED_HARDCODED_DEFAULTS == null)
                UNCOERCED_HARDCODED_DEFAULTS = _extractHardcodedC3P0Defaults(false);
            return (HashMap) UNCOERCED_HARDCODED_DEFAULTS.clone();
        }
    }

    
    private static HashMap _extractHardcodedC3P0Defaults(boolean stringify_coercibles)
    {
	HashMap out = new HashMap();

	try
	    {
		Method[] methods = C3P0Defaults.class.getMethods();
		for (int i = 0, len = methods.length; i < len; ++i)
		    {
			Method m = methods[i];
			int mods = m.getModifiers();
			if ((mods & Modifier.PUBLIC) != 0 && (mods & Modifier.STATIC) != 0 && m.getParameterTypes().length == 0)
			    {
				Object val = m.invoke( null, (Object[]) null ); // cast to suppress inexact type warning
				if ( val != null )
				    out.put( m.getName(), stringify_coercibles && Coerce.canCoerce( val ) ? String.valueOf( val ) : val);
			    }
		    }
	    }
	catch (Exception e)
	    {
		logger.log( MLevel.WARNING, "Failed to extract hardcoded default config!?", e );
	    }

	return out;
    }

    public static HashMap extractHardcodedC3P0Defaults()
    { return extractHardcodedC3P0Defaults( true ); }

    public static synchronized Properties extractHardcodedC3P0DefaultsStringCoercedOnly()
    {
        if (ONLY_COERCED_HARDCODED_DEFAULTS == null)
        {
            Properties tmp = new Properties();
            HashMap raw = extractHardcodedC3P0Defaults( true );
            for (Object k : raw.keySet())
            {
                Object v = raw.get(k);
                if (v instanceof String)
                    tmp.setProperty((String)k,(String)v); // k should always be a String, v might not be
                else
                {
                    if (logger.isLoggable(MLevel.WARNING) && !EXPECTED_NOT_COERCIBLE_DEFAULTS.contains(k))
                        logger.log(MLevel.WARNING, "Library flaw: C3P0 default for '" + k + "' could not be coerced to String. It is of class '" + v.getClass().getName() + "'. value : " + v);
                }
            }
            ONLY_COERCED_HARDCODED_DEFAULTS = tmp;
        }
        return (Properties) ONLY_COERCED_HARDCODED_DEFAULTS.clone();
    }

    @Deprecated
    public static HashMap extractC3P0PropertiesResources()
    { return extractC3P0PropertiesResources(C3P0Config.getMultiPropertiesConfig()); }

    public static HashMap extractC3P0PropertiesResources(MultiPropertiesConfig mpcfg)
    {
	HashMap out = new HashMap();

// 	Properties props = findResourceProperties();
// 	props.putAll( findAllC3P0Properties() );

 	Properties props = findAllOneLevelC3P0Properties(mpcfg);
	for (Iterator ii = props.keySet().iterator(); ii.hasNext(); )
	    {
		String key = (String) ii.next();
		String val = (String) props.get(key);
		if ( key.startsWith(PROPS_FILE_PROP_PFX) )
		    out.put( key.substring(PROPS_FILE_PROP_PFX_LEN).trim(), val.trim() );
	    }

	return out;
    }

    /**
     *  @deprecated In general, try to use com.mchange.v2.cfg.SealedSystemPropertiesStringProperty with C3P0Config.getMultiPropertiesConfig() as its argument.
     */
    @Deprecated
    public static String getPropsFileConfigProperty( String prop )
    { return C3P0Config.getPropsFileConfigProperty( prop ); }

    //public static Properties findResourceProperties()
    //{ return C3P0Config.findResourceProperties(); }

    //private static Properties findAllOneLevelC3P0Properties()
    //{ return C3P0Config.findAllOneLevelC3P0Properties(); }

    static Properties findAllC3P0SystemPropertiesRespectSealedAndDangerousProperties()
    {
	Properties out = new Properties();

	try
	    {
		for (Iterator ii = C3P0Defaults.getKnownProperties( null ).iterator(); ii.hasNext(); )
		    {
			String key = (String) ii.next();
			String prefixedKey = "c3p0." + key;
                        String sealedValue = SealedSystemProperties.get().getProperty( prefixedKey );

			String value;
                        if (sealedValue != null)
                        {
                            value = sealedValue;
                            if (logger.isLoggable(MLevel.WARNING))
                            {
                                String current = System.getProperty(prefixedKey);
                                if (!sealedValue.equals(current))
                                    logger.log(MLevel.WARNING,
                                               "System property '" + prefixedKey +
                                               "' has changed from its originally sealed value of '" + sealedValue +
                                               "' to a new value of '" + current +
                                               "'. The new value will NOT become a part of c3p0's configuration. " +
                                               "The sealed value will remain.");
                            }
                        }
                        else if (DANGEROUS_NO_PREFIX_C3P0_PROPERTIES.contains(key))
                        {
                            value = null; // sealed value is null, and we accept no late introductions of dangerous keys
                            if (logger.isLoggable(MLevel.WARNING))
                            {
                                String current = System.getProperty(prefixedKey);
                                if (current != null)
                                    logger.log(MLevel.WARNING,
                                               "System property '" + prefixedKey +
                                               "' has been introduced at runtime with value '" + current +
                                               "'. It was originally left unset in System properties. '" + prefixedKey +
                                               "' is considered a dangerous property, late introductions via System properties are not supported. " +
                                               "The configuration of '" + prefixedKey +
                                               "' may be affected by other ways of changing c3p0's configuration, " +
                                               "but the System property change, on its own, will not take effect.");
                            }
                        }
                        else
                            value = System.getProperty( prefixedKey );

			if (value != null && value.trim().length() > 0)
			    out.put( key, value );
		    }
	    }
	catch (SecurityException e)
	    { 
		if ( logger.isLoggable( MLevel.WARNING ) )
		    logger.log( MLevel.WARNING, 
				"A SecurityException occurred while trying to read c3p0 System properties. " + 
				"c3p0 configuration set via System properties may be ignored!",
				e );
	    }

	return out;
    }

    /**
     * @return null if no per-user override is found
     */
    public static Object extractUserOverride(String propName, String userName, Map userOverrides)
    {
	Map specificUserOverrides = (Map) userOverrides.get( userName ); 
	if (specificUserOverrides != null)
	    return specificUserOverrides.get( propName );
	else
	    return null;
    }

    public static Boolean extractBooleanUserOverride(String propName, String userName, Map userOverrides)
    {
	Object check = extractUserOverride( propName, userName, userOverrides);
	if ( check == null || check instanceof Boolean )
	    return (Boolean) check;
	else if (check instanceof String)
	    return Boolean.valueOf( (String) check );
	else
	    throw new ClassCastException("Parameter '" + propName + "' as overridden for user '" + userName + "' is " + check + ", which cannot be converted to Boolean.");
    }

    public static Integer extractIntUserOverride(String propName, String userName, Map userOverrides)
    {
	Object check = extractUserOverride( propName, userName, userOverrides);
	if ( check == null || check instanceof Integer )
	    return (Integer) check;
	else if (check instanceof String)
	    return Integer.valueOf( (String) check );
	else
	    throw new ClassCastException("Parameter '" + propName + "' as overridden for user '" + userName + "' is " + check + ", which cannot be converted to Boolean.");
    }

    static Properties findAllOneLevelC3P0Properties(MultiPropertiesConfig mpcfg)
    { 
	Properties out =  mpcfg.getPropertiesByPrefix("c3p0"); 
	for (Iterator ii = out.keySet().iterator(); ii.hasNext(); )
	    if (((String) ii.next()).lastIndexOf('.') > 4) ii.remove();
	return out;
    }

    static HashMap findPropStyleUserOverrides(MultiPropertiesConfig mpcfg)
    {
	HashMap userNamesToOverrides = new HashMap();

	Properties props =  mpcfg.getPropertiesByPrefix(PROP_STYLE_DEFAULT_USER_OVERRIDES_PFX);
	for ( Iterator ii = props.keySet().iterator(); ii.hasNext(); ) // iterate through c3p0.user-overrides.xxx subproperties
	{
	    String fullKey  = (String) ii.next();
	    String userProp = fullKey.substring( PROP_STYLE_DEFAULT_USER_OVERRIDES_PFX_LEN + 1 ); //expect <username>.<property>, e.g. swaldman.maxPoolSize
	    int dot_index = userProp.indexOf('.');
	    if ( dot_index < 0 ) // if there is no dot
	    {
		if ( logger.isLoggable( MLevel.WARNING ) )
		    logger.log(MLevel.WARNING, 
			       "Bad specification of user-override property '" + fullKey + 
			       "', propfile key should look like '" + C3P0ConfigUtils.PROP_STYLE_DEFAULT_USER_OVERRIDES_PFX + 
			       ".<user>.<property>'. Ignoring.");
		continue; 
	    }

	    String user = userProp.substring( 0, dot_index );
	    String propName = userProp.substring( dot_index + 1 );

	    HashMap userOverridesMap = (HashMap) userNamesToOverrides.get( user );
	    if (userOverridesMap == null)
	    {
		userOverridesMap = new HashMap();
		userNamesToOverrides.put( user, userOverridesMap );
	    }
	    userOverridesMap.put( propName, props.get( fullKey ) );
	}

	return userNamesToOverrides;
    }

    static HashMap findPropStyleExtensions(MultiPropertiesConfig mpcfg)
    {
	HashMap extensions = new HashMap();

	Properties props =  mpcfg.getPropertiesByPrefix(PROP_STYLE_EXTENSIONS_PFX);
	for ( Iterator ii = props.keySet().iterator(); ii.hasNext(); ) // iterate through c3p0.extensions.xxx subproperties
	{
	    String fullKey  = (String) ii.next();
	    String extensionsKey = fullKey.substring( PROP_STYLE_EXTENSIONS_PFX_LEN + 1 ); 
	    extensions.put( extensionsKey, props.get( fullKey ) );
	}

	return extensions;
    }

    static HashMap findPropStyleNamedScopes(MultiPropertiesConfig mpcfg)
    { 
	HashMap namesToNamedScopes = new HashMap();

	Properties props =  mpcfg.getPropertiesByPrefix(PROP_STYLE_NAMED_CFG_PFX);
	for ( Iterator ii = props.keySet().iterator(); ii.hasNext(); ) // iterate through c3p0.named-config.xxx subproperties
	{
	    String fullKey  = (String) ii.next();
	    String nameProp = fullKey.substring( PROP_STYLE_NAMED_CFG_PFX_LEN + 1 ); //expect <cfgname>.<property>, e.g. myconfig.maxPoolSize
	    int dot_index = nameProp.indexOf('.');
	    if ( dot_index < 0 ) // if there is no dot
	    {
		if ( logger.isLoggable( MLevel.WARNING ) )
		    logger.log(MLevel.WARNING, 
			       "Bad specification of named config property '" + fullKey + 
			       "', propfile key should look like '" + PROP_STYLE_NAMED_CFG_PFX + 
			       ".<cfgname>.<property>' or '" + PROP_STYLE_NAMED_CFG_PFX + ".<cfgname>.user-overrides.<user>.<property>'. Ignoring.");
		continue; 
	    }

	    String configName = nameProp.substring( 0, dot_index );
	    String propName   = nameProp.substring( dot_index + 1 );

	    // System.err.println( "********** " + PROP_STYLE_NAMED_CFG_PFX_LEN + "|" + nameProp + "|" + configName + "|" + propName );

	    NamedScope scope = (NamedScope) namesToNamedScopes.get( configName );
	    if ( scope == null )
	    {
		scope = new NamedScope();
		namesToNamedScopes.put( configName, scope );
	    }

	    int second_dot_index = propName.indexOf('.');

	    if ( second_dot_index >= 0 ) //compound property
	    {
		if ( propName.startsWith( PROP_STYLE_USER_OVERRIDES_PART ) )
		{
		    int third_dot_index = propName.substring( second_dot_index + 1 ).indexOf('.');
		    if ( third_dot_index < 0 )
		    {
			if ( logger.isLoggable( MLevel.WARNING ) )
			    logger.log( MLevel.WARNING, "Misformatted user-override property; missing user or property name: " + propName );
		    }

		    String user = propName.substring( second_dot_index + 1, third_dot_index );
		    String userPropName = propName.substring( third_dot_index + 1 );

		    HashMap userOverridesMap = (HashMap) scope.userNamesToOverrides.get( user );
		    if (userOverridesMap == null)
		    {
			userOverridesMap = new HashMap();
			scope.userNamesToOverrides.put( user, userOverridesMap );
		    }
		    userOverridesMap.put( userPropName, props.get( fullKey ) );
		}
		else if ( propName.startsWith( PROP_STYLE_EXTENSIONS_PART ) )
		{
		    String extensionsKey = propName.substring( second_dot_index + 1 );
		    scope.extensions.put( extensionsKey, props.get( fullKey ) );
		}
		else
		{
		    if ( logger.isLoggable( MLevel.WARNING ) )
			logger.log( MLevel.WARNING, "Unexpected compound property, ignored: " + propName );
		}
	    }
	    else 
		scope.props.put( propName, props.get( fullKey ) );
	}

	return namesToNamedScopes;
    }

    private C3P0ConfigUtils()
    {}
}
