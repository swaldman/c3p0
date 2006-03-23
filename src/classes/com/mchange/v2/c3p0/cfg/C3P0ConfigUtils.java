/*
 * Distributed as part of c3p0 v.0.9.1-pre6
 *
 * Copyright (C) 2005 Machinery For Change, Inc.
 *
 * Author: Steve Waldman <swaldman@mchange.com>
 *
 * This library is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License version 2.1, as 
 * published by the Free Software Foundation.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this software; see the file LICENSE.  If not, write to the
 * Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 * Boston, MA 02111-1307, USA.
 */


package com.mchange.v2.c3p0.cfg;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import com.mchange.v2.cfg.*;
import com.mchange.v2.log.*;
import com.mchange.v2.c3p0.impl.*;

import com.mchange.v1.xml.DomParseUtils;

public final class C3P0ConfigUtils
{
    public final static String XML_CONFIG_RSRC_PATH     = "/c3p0-config.xml";

    public final static String PROPS_FILE_RSRC_PATH     = "/c3p0.properties";
    public final static String PROPS_FILE_PROP_PFX      = "c3p0.";
    public final static int    PROPS_FILE_PROP_PFX_LEN  = 5;

    public final static String LINESEP;

    static
    {
	String ls;

	try
	    { ls = System.getProperty("line.separator", "\r\n"); }
	catch (Exception e)
	    { ls = "\r\n"; }

	LINESEP = ls;
    }
    
    final static MLogger logger = MLog.getLogger( C3P0ConfigUtils.class );

//     final static HashSet extractHardcodedC3P0PropertyNames()
//     {
// 	HashSet out = new HashSet();

// 	try
// 	    {

// 		Method[] methods = C3P0Defaults.class.getMethods();
// 		for (int i = 0, len = methods.length; i < len; ++i)
// 		    {
// 			Method m = methods[i];
// 			int mods = m.getModifiers();
// 			if ((mods & Modifier.PUBLIC) != 0 && (mods & Modifier.STATIC) != 0 && m.getParameterTypes().length == 0)
// 			    out.add( m.getName() );
// 		    }

// 	    }
// 	catch (Exception e)
// 	    {
// 		logger.log( MLevel.WARNING, "Failed to extract hardcoded property names!?", e );
// 	    }
	
// 	return out;
//     }

    public static HashMap extractHardcodedC3P0Defaults(boolean stringify)
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
				if (stringify)
				    {
					Object val = m.invoke( null, null );
					if ( val != null )
					    out.put( m.getName(), String.valueOf( val ) );
				    }
				else
				    out.put( m.getName(), m.invoke( null, null ) );
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

    public static HashMap extractC3P0PropertiesResources()
    {
	HashMap out = new HashMap();

// 	Properties props = findResourceProperties();
// 	props.putAll( findAllC3P0Properties() );

 	Properties props = findAllC3P0Properties();
	for (Iterator ii = props.keySet().iterator(); ii.hasNext(); )
	    {
		String key = (String) ii.next();
		String val = (String) props.get(key);
		if ( key.startsWith(PROPS_FILE_PROP_PFX) )
		    out.put( key.substring(PROPS_FILE_PROP_PFX_LEN).trim(), val.trim() );
	    }

	return out;
    }

    public static C3P0Config extractXmlConfigFromDefaultResource() throws Exception
    {
	InputStream is = null;

	try
	    {
		is = C3P0ConfigUtils.class.getResourceAsStream(XML_CONFIG_RSRC_PATH);
		return (is == null ? null : extractXmlConfigFromInputStream( is ));
	    }
	finally
	    {
		try { if (is != null) is.close(); }
		catch (Exception e)
		    {
			if ( logger.isLoggable( MLevel.FINE ) )
			    logger.log(MLevel.FINE,"Exception on resource InputStream close.", e);
		    }
	    }
    }

    public static C3P0Config extractXmlConfigFromInputStream(InputStream is) throws Exception
    {
	DocumentBuilderFactory fact = DocumentBuilderFactory.newInstance();
	DocumentBuilder db = fact.newDocumentBuilder();
	Document doc = db.parse( is );

	return extractConfigFromXmlDoc(doc);
    }

    public static C3P0Config extractConfigFromXmlDoc(Document doc) throws Exception
    {
	Element docElem = doc.getDocumentElement();
	if (docElem.getTagName().equals("c3p0-config"))
	    {
		NamedScope defaults;
		HashMap configNamesToNamedScopes = new HashMap();

		Element defaultConfigElem = DomParseUtils.uniqueChild( docElem, "default-config" );
		if (defaultConfigElem != null)
		    defaults = extractNamedScopeFromLevel( defaultConfigElem );
		else
		    defaults = new NamedScope();
		NodeList nl = DomParseUtils.immediateChildElementsByTagName(docElem, "named-config");
		for (int i = 0, len = nl.getLength(); i < len; ++i)
		    {
			Element namedConfigElem = (Element) nl.item(i);
			String configName = namedConfigElem.getAttribute("name");
			if (configName != null && configName.length() > 0)
			    {
				NamedScope namedConfig = extractNamedScopeFromLevel( namedConfigElem );
				configNamesToNamedScopes.put( configName, namedConfig);
			    }
			else
			    logger.warning("Configuration XML contained named-config element without name attribute: " + namedConfigElem);
		    }
		return new C3P0Config( defaults, configNamesToNamedScopes );
	    }
	else
	    throw new Exception("Root element of c3p0 config xml should be 'c3p0-config', not '" + docElem.getTagName() + "'.");
    }

    public static C3P0Config configFromFlatDefaults(HashMap flatDefaults)
    {
	NamedScope defaults = new NamedScope();
	defaults.props.putAll( flatDefaults );
	
	HashMap configNamesToNamedScopes = new HashMap();
	
	return new C3P0Config( defaults, configNamesToNamedScopes ); 
    }

    private static NamedScope extractNamedScopeFromLevel(Element elem)
    {
	HashMap props = extractPropertiesFromLevel( elem );
	HashMap userNamesToOverrides = new HashMap();

	NodeList nl = DomParseUtils.immediateChildElementsByTagName(elem, "user-overrides");
	for (int i = 0, len = nl.getLength(); i < len; ++i)
	    {
		Element perUserConfigElem = (Element) nl.item(i);
		String userName = perUserConfigElem.getAttribute("user");
		if (userName != null && userName.length() > 0)
		    {
			HashMap userProps = extractPropertiesFromLevel( perUserConfigElem );
			userNamesToOverrides.put( userName, userProps );
		    }
		else
		    logger.warning("Configuration XML contained user-overrides element without user attribute: " + LINESEP + perUserConfigElem);
	    }

	return new NamedScope(props, userNamesToOverrides);
    }

    private static HashMap extractPropertiesFromLevel(Element elem)
    {
	// System.err.println( "extractPropertiesFromLevel()" );

	HashMap out = new HashMap();

	try
	    {
		NodeList nl = DomParseUtils.immediateChildElementsByTagName(elem, "property");
		int len = nl.getLength();
		for (int i = 0; i < len; ++i)
		    {
			Element propertyElem = (Element) nl.item(i);
			String propName = propertyElem.getAttribute("name");
			if (propName != null && propName.length() > 0)
			    {
				String propVal = DomParseUtils.allTextFromElement(propertyElem, true);
				out.put( propName, propVal );
				//System.err.println( propName + " -> " + propVal );
			    }
			else
			    logger.warning("Configuration XML contained property element without name attribute: " + LINESEP + propertyElem);
		    }
	    }
	catch (Exception e)
	    {
		logger.log( MLevel.WARNING, 
			    "An exception occurred while reading config XML. " +
			    "Some configuration information has probably been ignored.", 
			    e );
	    }

	return out;
    }

    private static Properties findResourceProperties()
    { return MultiPropertiesConfig.readVmConfig().getPropertiesByResourcePath(PROPS_FILE_RSRC_PATH); }

    private static Properties findAllC3P0Properties()
    { return MultiPropertiesConfig.readVmConfig().getPropertiesByPrefix("c3p0"); }

    static Properties findAllC3P0SystemProperties()
    {
	Properties out = new Properties();

	SecurityException sampleExc = null;
	try
	    {
		for (Iterator ii = C3P0Defaults.getKnownProperties().iterator(); ii.hasNext(); )
		    {
			String key = (String) ii.next();
			String prefixedKey = "c3p0." + key;
			String value = System.getProperty( prefixedKey );
			if (value != null && value.trim().length() > 0)
			    out.put( key, value );
		    }
	    }
	catch (SecurityException e)
	    { sampleExc = e; }

	return out;
    }

    private C3P0ConfigUtils()
    {}
}