/*
 * Distributed as part of c3p0 v.0.9.2-pre7-20121205
 *
 * Copyright (C) 2012 Machinery For Change, Inc.
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
import java.util.HashMap;
import java.util.Properties;
import com.mchange.v2.cfg.MultiPropertiesConfig;

public class DefaultC3P0ConfigFinder implements C3P0ConfigFinder
{
    final static String XML_CFG_FILE_KEY            = "com.mchange.v2.c3p0.cfg.xml";
    final static String CLASSLOADER_RESOURCE_PREFIX = "classloader:";

    public C3P0Config findConfig() throws Exception
    {
	C3P0Config out;

	HashMap flatDefaults = C3P0ConfigUtils.extractHardcodedC3P0Defaults();

	// this includes System properties, but we have to check for System properties
	// again, since we want system properties to override unspecified user, default-config
	// properties in the XML
	flatDefaults.putAll( C3P0ConfigUtils.extractC3P0PropertiesResources() );

	String cfgFile = MultiPropertiesConfig.readVmConfig().getProperty( XML_CFG_FILE_KEY );
	if (cfgFile == null)
	    {
		C3P0Config xmlConfig = C3P0ConfigXmlUtils.extractXmlConfigFromDefaultResource();
		if (xmlConfig != null)
		    {
			insertDefaultsUnderNascentConfig( flatDefaults, xmlConfig );
			out = xmlConfig;
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
			}
			else
			    is = new BufferedInputStream( new FileInputStream( cfgFile ) );

			C3P0Config xmlConfig = C3P0ConfigXmlUtils.extractXmlConfigFromInputStream( is );
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
}
