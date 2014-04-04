/*
 * Distributed as part of c3p0 0.9.5-pre8
 *
 * Copyright (C) 2014 Machinery For Change, Inc.
 *
 * Author: Steve Waldman <swaldman@mchange.com>
 *
 * This library is free software; you can redistribute it and/or modify
 * it under the terms of EITHER:
 *
 *     1) The GNU Lesser General Public License (LGPL), version 2.1, as 
 *        published by the Free Software Foundation
 *
 * OR
 *
 *     2) The Eclipse Public License (EPL), version 1.0
 *
 * You may choose which license to accept if you wish to redistribute
 * or modify this work. You may offer derivatives of this work
 * under the license you have chosen, or you may provide the same
 * choice of license which you have been offered here.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * You should have received copies of both LGPL v2.1 and EPL v1.0
 * along with this software; see the files LICENSE-EPL and LICENSE-LGPL.
 * If not, the text of these licenses are currently available at
 *
 * LGPL v2.1: http://www.gnu.org/licenses/old-licenses/lgpl-2.1.html
 *  EPL v1.0: http://www.eclipse.org/org/documents/epl-v10.php 
 * 
 */

package com.mchange.v2.c3p0.cfg;

import java.beans.*;
import java.util.*;
import com.mchange.v2.c3p0.impl.*;
import com.mchange.v2.beans.*;
import com.mchange.v2.cfg.*;
import com.mchange.v2.log.*;

import java.io.IOException;
import java.lang.reflect.Method;
import com.mchange.v1.lang.BooleanUtils;
import com.mchange.v2.c3p0.C3P0Registry;

//all internal maps should be HashMaps (the implementation presumes HashMaps)

public final class C3P0Config
{
    final static String PROP_STYLE_NAMED_CFG_PFX          = "c3p0.named-configs";
    final static int    PROP_STYLE_NAMED_CFG_PFX_LEN      = PROP_STYLE_NAMED_CFG_PFX.length();
    final static String PROP_STYLE_USER_OVERRIDES_PART    = "user-overrides";
    final static String PROP_STYLE_USER_OVERRIDES_PFX     = "c3p0." + PROP_STYLE_USER_OVERRIDES_PART;
    final static int    PROP_STYLE_USER_OVERRIDES_PFX_LEN = PROP_STYLE_USER_OVERRIDES_PFX.length();
    final static String PROP_STYLE_EXTENSIONS_PART        = "extensions";
    final static String PROP_STYLE_EXTENSIONS_PFX         = "c3p0." + PROP_STYLE_EXTENSIONS_PART;
    final static int    PROP_STYLE_EXTENSIONS_PFX_LEN     = PROP_STYLE_EXTENSIONS_PFX.length();

    public final static String CFG_FINDER_CLASSNAME_KEY = "com.mchange.v2.c3p0.cfg.finder";

    public final static String DEFAULT_CONFIG_NAME = "default";

    public final static String PROPS_FILE_RSRC_PATH = "/c3p0.properties";

    final static MLogger logger;

    //MT: the value of the ConfigRec is informally immutable (TBD: enforce?)
    //    the (mutable) reference mainConfigRec is protected by class' lock
    private static MultiPropertiesConfig _MPCONFIG;
    private static C3P0Config _MAIN;


    private static synchronized MultiPropertiesConfig MPCONFIG()
    { return _MPCONFIG; }

    private static synchronized C3P0Config MAIN()
    { return _MAIN; }

    private static synchronized void setLibraryMultiPropertiesConfig( MultiPropertiesConfig mpc )
    { _MPCONFIG = mpc; }

    public static Properties allCurrentProperties()
    { return MPCONFIG().getPropertiesByPrefix(""); }

    public static synchronized void setMainConfig( C3P0Config protoMain )
    { _MAIN = protoMain; }

    public static synchronized void refreshMainConfig()
    { refreshMainConfig( null, null ); }

    // later overrides take precedence over earlier ones
    public static synchronized void refreshMainConfig( MultiPropertiesConfig[] overrides, String overridesDescription )
    {
	MultiPropertiesConfig libMpc = findLibraryMultiPropertiesConfig();
	if ( overrides != null )
	{
	    int olen = overrides.length;
	    MultiPropertiesConfig[] combineMe = new MultiPropertiesConfig[ olen + 1 ];
	    combineMe[0] = libMpc;
	    for ( int i = 0; i < olen; ++i )
		combineMe[ i + 1 ] = overrides[i];

	    MultiPropertiesConfig  overriddenMpc = MConfig.combine( combineMe );
	    setLibraryMultiPropertiesConfig( overriddenMpc );
	    setMainConfig( findLibraryC3P0Config( true ) );

	    if ( logger.isLoggable( MLevel.INFO ) )
		logger.log( MLevel.INFO, 
			    "c3p0 main configuration was refreshed, with overrides specified" + (overridesDescription == null ? "." : " - " + overridesDescription ) );
	}
	else
	{
	    setLibraryMultiPropertiesConfig( libMpc );
	    setMainConfig( findLibraryC3P0Config( false ) );

	    if ( logger.isLoggable( MLevel.INFO ) )
		logger.log( MLevel.INFO, "c3p0 main configuration was refreshed, with no overrides specified (and any previous overrides removed)." );
	}

	/*
	System.err.println("All properties...");
	Properties props = libMpc.getPropertiesByPrefix("");
	Map<Object,Object> m = new TreeMap<Object,Object>();
	m.putAll( (Map<Object,Object>) props );
	for ( Map.Entry<Object,Object> entry : m.entrySet() )
	    System.err.println( entry.getKey() + " --> " + entry.getValue() );
	*/

	C3P0Registry.markConfigRefreshed();
    }

    static
    {
	logger = MLog.getLogger( C3P0Config.class );

	// if ( logger.isLoggable( MLevel.FINE ) )
	//     logger.log( MLevel.INFO, "Updated main c3p0 cofiguration." );

	setLibraryMultiPropertiesConfig( findLibraryMultiPropertiesConfig() );
	setMainConfig( findLibraryC3P0Config( false ) );

	warnOnUnknownProperties( MAIN() );
    }

    private static MultiPropertiesConfig findLibraryMultiPropertiesConfig()
    {
	// these should be specified in /mchange-config-resource-paths
	// but just in case that is overridden or omitted...

	String[] defaults = {"/mchange-commons.properties", "/mchange-log.properties"};
	String[] preempts = {"hocon:/reference,/application,/c3p0,/","/c3p0.properties", "/"};

	return MConfig.readVmConfig( defaults, preempts );
    }

    // _MPCONFIG must be set first!
    private static C3P0Config findLibraryC3P0Config( boolean warn_on_conflicting_overrides )
    {
	C3P0Config protoMain;

	String cname = MPCONFIG().getProperty( CFG_FINDER_CLASSNAME_KEY );

	C3P0ConfigFinder cfgFinder = null;
	try
	    {
		if (cname != null)
		    cfgFinder = (C3P0ConfigFinder) Class.forName( cname ).newInstance();
		
	    }
	catch (Exception e)
	    {
		if ( logger.isLoggable(MLevel.WARNING) )
		    logger.log( MLevel.WARNING, "Could not load specified C3P0ConfigFinder class'" + cname + "'.", e);
	    }

	try
	    { 
		if (cfgFinder == null)
		    {
			Class.forName("org.w3c.dom.Node");
			Class.forName("com.mchange.v2.c3p0.cfg.C3P0ConfigXmlUtils"); //fail nicely if we don't have XML libs
			cfgFinder = new DefaultC3P0ConfigFinder( warn_on_conflicting_overrides );
		    }
		protoMain = cfgFinder.findConfig(); 
	    }
	catch (Exception e)
	    { 
		
		if ( logger.isLoggable(MLevel.WARNING) )
		    logger.log( MLevel.WARNING, "XML configuration disabled! Verify that standard XML libs are available.", e);

		HashMap flatDefaults = C3P0ConfigUtils.extractHardcodedC3P0Defaults();
		flatDefaults.putAll( C3P0ConfigUtils.extractC3P0PropertiesResources() );
		protoMain = C3P0ConfigUtils.configFromFlatDefaults( flatDefaults );
	    }

	HashMap propStyleConfigNamesToNamedScopes = findPropStyleNamedScopes();
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

	HashMap propStyleUserOverridesDefaultConfig = findPropStyleUserOverridesDefaultConfig();
	HashMap propStyleExtensionsDefaultConfig = findPropStyleExtensionsDefaultConfig();
	NamedScope mergedDefaultConfig = new NamedScope( protoMain.defaultConfig.props, 
							 NamedScope.mergeUserNamesToOverrides( protoMain.defaultConfig.userNamesToOverrides, propStyleUserOverridesDefaultConfig ), 
							 NamedScope.mergeExtensions( protoMain.defaultConfig.extensions, propStyleExtensionsDefaultConfig ) );

	return new C3P0Config( mergedDefaultConfig, mergedConfigNamesToNamedScopes );
    }



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

    public static String getPropsFileConfigProperty( String prop )
    { return MPCONFIG().getProperty( prop ); }

    static Properties findResourceProperties()
    { return MPCONFIG().getPropertiesByResourcePath(PROPS_FILE_RSRC_PATH); }

    static Properties findAllOneLevelC3P0Properties()
    { 
	Properties out =  MPCONFIG().getPropertiesByPrefix("c3p0"); 
	for (Iterator ii = out.keySet().iterator(); ii.hasNext(); )
	    if (((String) ii.next()).lastIndexOf('.') > 4) ii.remove();
	return out;
    }

    static HashMap findPropStyleUserOverridesDefaultConfig()
    {
	HashMap userNamesToOverrides = new HashMap();

	Properties props =  MPCONFIG().getPropertiesByPrefix(PROP_STYLE_USER_OVERRIDES_PFX);
	for ( Iterator ii = props.keySet().iterator(); ii.hasNext(); ) // iterate through c3p0.user-overrides.xxx subproperties
	{
	    String fullKey  = (String) ii.next();
	    String userProp = fullKey.substring( PROP_STYLE_USER_OVERRIDES_PFX_LEN + 1 ); //expect <username>.<property>, e.g. swaldman.maxPoolSize
	    int dot_index = userProp.indexOf('.');
	    if ( dot_index < 0 ) // if there is no dot
	    {
		if ( logger.isLoggable( MLevel.WARNING ) )
		    logger.log(MLevel.WARNING, 
			       "Bad specification of user-override property '" + fullKey + 
			       "', propfile key should look like '" + PROP_STYLE_USER_OVERRIDES_PFX + 
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

    static HashMap findPropStyleExtensionsDefaultConfig()
    {
	HashMap extensions = new HashMap();

	Properties props =  MPCONFIG().getPropertiesByPrefix(PROP_STYLE_EXTENSIONS_PFX);
	for ( Iterator ii = props.keySet().iterator(); ii.hasNext(); ) // iterate through c3p0.extensions.xxx subproperties
	{
	    String fullKey  = (String) ii.next();
	    String extensionsKey = fullKey.substring( PROP_STYLE_EXTENSIONS_PFX_LEN + 1 ); 
	    extensions.put( extensionsKey, props.get( fullKey ) );
	}

	return extensions;
    }


    static HashMap findPropStyleNamedScopes()
    { 
	HashMap namesToNamedScopes = new HashMap();

	Properties props =  MPCONFIG().getPropertiesByPrefix(PROP_STYLE_NAMED_CFG_PFX);
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

    public static String getUserOverridesAsString(String configName) throws IOException
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

    public static String initializeStringPropertyVar(String propKey, String dflt)
    {
	String out = getUnspecifiedUserProperty( propKey, null );
	if (out == null) out = dflt;
	return out;
    }

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
			logger.info("'" + outStr + "' is not a legal value for property '" + propKey +
				    "'. Using default value: " + dflt);
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
			logger.info("'" + outStr + "' is not a legal value for property '" + propKey +
				    "'. Using default value: " + dflt);
		    }
	    }

	if (!set)
	    out = dflt;

	return out;
    }

    public static MultiPropertiesConfig getMultiPropertiesConfig()
    { return MPCONFIG(); }

    NamedScope defaultConfig;
    HashMap configNamesToNamedScopes;

    C3P0Config( NamedScope defaultConfig, HashMap configNamesToNamedScopes)
    {
	this.defaultConfig = defaultConfig;
	this.configNamesToNamedScopes = configNamesToNamedScopes;
    }

//     C3P0Config()
//     {
// 	this.defaultConfig = new NamedScope();
// 	this.configNamesToNamedScopes = new HashMap();
//     }

}
