/*
 * Distributed as part of c3p0 v.0.9.5-pre1
 *
 * Copyright (C) 2013 Machinery For Change, Inc.
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

//all internal maps should be HashMaps (the implementation presumes HashMaps)

public final class C3P0Config
{
    public final static String CFG_FINDER_CLASSNAME_KEY = "com.mchange.v2.c3p0.cfg.finder";

    public final static String DEFAULT_CONFIG_NAME = "default";

    public final static String PROPS_FILE_RSRC_PATH     = "/c3p0.properties";

    public final static C3P0Config MAIN;

    final static MLogger logger;

    final static MultiPropertiesConfig CONFIG;

    static
    {
// 	Set knownProps = new HashSet();
// 	knownProps.add("acquireIncrement");
// 	knownProps.add("acquireRetryAttempts");
// 	knownProps.add("acquireRetryDelay");
// 	knownProps.add("autoCommitOnClose");
// 	knownProps.add("automaticTestTable");
// 	knownProps.add("breakAfterAcqireFailure");
// 	knownProps.add("checkoutTimeout");
// 	knownProps.add("connectionTesterClassName");
// 	knownProps.add("factoryClassLocation");
// 	knownProps.add("forceIgnoreUnresolvedTransactions");
// 	knownProps.add("idleConnectionTestPeriod");
// 	knownProps.add("initialPoolSize");
// 	knownProps.add("maxIdleTime");
// 	knownProps.add("maxPoolSize");

	C3P0Config protoMain;

	logger = MLog.getLogger( C3P0Config.class );
	CONFIG = MultiPropertiesConfig.readVmConfig( logger );

	String cname = CONFIG.getProperty( CFG_FINDER_CLASSNAME_KEY );

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
			cfgFinder = new DefaultC3P0ConfigFinder();
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
	MAIN = protoMain;

	warnOnUnknownProperties( MAIN );
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

    static String getPropFileConfigProperty( String prop )
    { return CONFIG.getProperty( prop ); }

    static Properties findResourceProperties()
    { return CONFIG.getPropertiesByResourcePath(PROPS_FILE_RSRC_PATH); }

    static Properties findAllC3P0Properties()
    { return CONFIG.getPropertiesByPrefix("c3p0"); }


    public static String getUnspecifiedUserProperty( String propKey, String configName )
    {
	  String out = null;

 	  if (configName == null)
	      out = (String) MAIN.defaultConfig.props.get( propKey );
	  else
	      {
		  NamedScope named = (NamedScope) MAIN.configNamesToNamedScopes.get( configName );
		  if (named != null)
		      out = (String) named.props.get(propKey);
		  else
		      logger.warning("named-config with name '" + configName + "' does not exist. Using default-config for property '" + propKey + "'.");

		  if (out == null)
		      out = (String) MAIN.defaultConfig.props.get( propKey );
	      }
	  
	  return out;
    }

    public static Map getUnspecifiedUserProperties(String configName)
    {
	Map out = new HashMap();

	out.putAll( MAIN.defaultConfig.props );

	if (configName != null)
	    {
		  NamedScope named = (NamedScope) MAIN.configNamesToNamedScopes.get( configName );
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
	    namedConfigScope = (NamedScope) MAIN.configNamesToNamedScopes.get( configName );

	out.putAll( MAIN.defaultConfig.userNamesToOverrides );

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
	BeansUtils.overwriteAccessiblePropertiesFromMap( defaultUserProps, 
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
