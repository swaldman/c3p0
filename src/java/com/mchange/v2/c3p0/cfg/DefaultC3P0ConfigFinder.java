/*
 * Distributed as part of c3p0 v.0.9.5.2
 *
 * Copyright (C) 2015 Machinery For Change, Inc.
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

import java.io.*;
import com.mchange.v2.log.*;

import java.util.HashMap;
import java.util.Properties;

public class DefaultC3P0ConfigFinder implements C3P0ConfigFinder
{
    final static String XML_CFG_FILE_KEY               = "com.mchange.v2.c3p0.cfg.xml";
    final static String XML_CFG_EXPAND_ENTITY_REFS_KEY = "com.mchange.v2.c3p0.cfg.xml.expandEntityReferences";
    final static String CLASSLOADER_RESOURCE_PREFIX    = "classloader:";

    final static MLogger logger = MLog.getLogger( DefaultC3P0ConfigFinder.class );

    final boolean warn_of_xml_overrides;

    public DefaultC3P0ConfigFinder( boolean warn_of_xml_overrides )
    { this.warn_of_xml_overrides = warn_of_xml_overrides; }

    public DefaultC3P0ConfigFinder() 
    { this( false ); }   

    public C3P0Config findConfig() throws Exception
    {
	C3P0Config out;

	HashMap flatDefaults = C3P0ConfigUtils.extractHardcodedC3P0Defaults();

	// this includes System properties, but we have to check for System properties
	// again, since we want system properties to override unspecified user, default-config
	// properties in the XML
	flatDefaults.putAll( C3P0ConfigUtils.extractC3P0PropertiesResources() );

	String cfgFile = C3P0Config.getPropsFileConfigProperty( XML_CFG_FILE_KEY );
	boolean expandEntityReferences = findExpandEntityReferences();
	
	if (cfgFile == null)
	    {
		C3P0Config xmlConfig = C3P0ConfigXmlUtils.extractXmlConfigFromDefaultResource( expandEntityReferences );
		if (xmlConfig != null)
		    {
			insertDefaultsUnderNascentConfig( flatDefaults, xmlConfig );
			out = xmlConfig;
			
			mbOverrideWarning("resource", C3P0ConfigXmlUtils.XML_CONFIG_RSRC_PATH);
		    }
		else
		    out = C3P0ConfigUtils.configFromFlatDefaults( flatDefaults );
	    }
	else
	    {
		cfgFile = cfgFile.trim();

		InputStream is = null;
		try
		    {
			if ( cfgFile.startsWith( CLASSLOADER_RESOURCE_PREFIX ) )
			{
			    ClassLoader cl = this.getClass().getClassLoader();
			    String rsrcPath = cfgFile.substring( CLASSLOADER_RESOURCE_PREFIX.length() );

			    // eliminate leading slash because ClassLoader.getResource
			    // is always absolute and does not expect a leading slash
			    if (rsrcPath.startsWith("/")) 
				rsrcPath = rsrcPath.substring(1);

			    is = cl.getResourceAsStream( rsrcPath );
			    if ( is == null )
				throw new FileNotFoundException("Specified ClassLoader resource '" + rsrcPath + "' could not be found. " +
								"[ Found in configuration: " + XML_CFG_FILE_KEY + '=' + cfgFile + " ]");

			    mbOverrideWarning( "resource", rsrcPath );
			}
			else
			{
			    is = new BufferedInputStream( new FileInputStream( cfgFile ) );
			    mbOverrideWarning( "file", cfgFile );
			}

			C3P0Config xmlConfig = C3P0ConfigXmlUtils.extractXmlConfigFromInputStream( is, expandEntityReferences );
			insertDefaultsUnderNascentConfig( flatDefaults, xmlConfig );
			out = xmlConfig;
		    }
		finally
		    {
			try { if (is != null) is.close();}
			catch (Exception e)
			    { e.printStackTrace(); }
		    }
	    }

	// overwrite default, unspecified user config with System properties
	// defined values
	Properties sysPropConfig = C3P0ConfigUtils.findAllC3P0SystemProperties();
	out.defaultConfig.props.putAll( sysPropConfig );

	return out;
    }

    private void insertDefaultsUnderNascentConfig(HashMap flatDefaults, C3P0Config config)
    {
	flatDefaults.putAll( config.defaultConfig.props );
	config.defaultConfig.props = flatDefaults;
    }

    private void mbOverrideWarning( String srcType, String srcName )
    {
	if ( warn_of_xml_overrides && logger.isLoggable( MLevel.WARNING ) )
	    logger.log( MLevel.WARNING, "Configuation defined in " + srcType + "'" + srcName + "' overrides all other c3p0 config." );
    }

    private boolean findExpandEntityReferences()
    {
	String check = C3P0Config.getPropsFileConfigProperty( XML_CFG_EXPAND_ENTITY_REFS_KEY );
	boolean out = (check != null && check.trim().equalsIgnoreCase("true"));
	if ( out && logger.isLoggable( MLevel.WARNING ) )
	    logger.log( MLevel.WARNING,
			"Configuration property '" + XML_CFG_EXPAND_ENTITY_REFS_KEY + "' is set to 'true'. " +
			"Entity references will be resolved in XML c3p0 configuration files. This may be a security hazard. " +
			"Be sure you understand your XML config files, including the full transitive closure of entity references. " +
			"See CVE-2018-20433, https://nvd.nist.gov/vuln/detail/CVE-2018-20433");
	return out;
    }
}
