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
import java.util.HashMap;
import java.util.Properties;
import com.mchange.v2.cfg.MultiPropertiesConfig;

public class DefaultC3P0ConfigFinder implements C3P0ConfigFinder
{
    final static String XML_CFG_FILE_KEY = "com.mchange.v2.c3p0.cfg.xml";

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
		C3P0Config xmlConfig = C3P0ConfigUtils.extractXmlConfigFromDefaultResource();
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
		InputStream is = new BufferedInputStream( new FileInputStream( cfgFile ) );
		try
		    {
			C3P0Config xmlConfig = C3P0ConfigUtils.extractXmlConfigFromInputStream( is );
			insertDefaultsUnderNascentConfig( flatDefaults, xmlConfig );
			out = xmlConfig;
		    }
		finally
		    {
			try {is.close();}
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