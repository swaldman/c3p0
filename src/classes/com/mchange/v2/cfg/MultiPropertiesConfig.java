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


package com.mchange.v2.cfg;

import java.util.*;
import java.io.*;
import com.mchange.v2.log.*;

/**
 * MultiPropertiesConfig allows applications to accept configuration data
 * from a more than one property file (each of which is to be loaded from
 * a unique path using this class' ClassLoader's resource-loading mechanism),
 * and permits access to property data via the resource path from which the
 * properties were loaded, via the prefix of the property (where hierarchical 
 * property names are presumed to be '.'-separated), and simply by key.
 * In the by-key and by-prefix indices, when two definitions conflict, the
 * key value pairing specified in the MOST RECENT properties file shadows
 * earlier definitions, and files are loaded in the order of the list of
 * resource paths provided a constructor.
 *
 * The rescource path "/" is a special case that always refers to System
 * properties. No actual resource will be loaded.

 * The class manages a special instance called "vmConfig" which is accessable
 * via a static method. It's resource path is list specified by a text-file,
 * itself a ClassLoader managed resource, which must be located at
 * <tt>/com/mchange/v2/cfg/vmConfigResourcePaths.txt</tt>. This file should
 * be one resource path per line, with blank lines ignored and lines beginning
 * with '#' treated as comments.
 */
public abstract class MultiPropertiesConfig
{
    final static MultiPropertiesConfig EMPTY = new BasicMultiPropertiesConfig( new String[0] );

    final static String VM_CONFIG_RSRC_PATHS = "/com/mchange/v2/cfg/vmConfigResourcePaths.txt";

    static MultiPropertiesConfig vmConfig = null;

    public static MultiPropertiesConfig read(String[] resourcePath, MLogger logger)
    { return new BasicMultiPropertiesConfig( resourcePath, logger ); }

    public static MultiPropertiesConfig read(String[] resourcePath)
    { return new BasicMultiPropertiesConfig( resourcePath ); }

    public static MultiPropertiesConfig combine( MultiPropertiesConfig[] configs )
    { return new CombinedMultiPropertiesConfig( configs ); }

    public static MultiPropertiesConfig readVmConfig(String[] defaultResources, String[] preemptingResources)
    {
	List l = new LinkedList();
	if (defaultResources != null)
	    l.add( read( defaultResources ) );
	l.add( readVmConfig() );
	if (preemptingResources != null)
	    l.add( read( preemptingResources ) );
	return combine( (MultiPropertiesConfig[]) l.toArray( new MultiPropertiesConfig[ l.size() ] ) );
    }

    public static MultiPropertiesConfig readVmConfig()
    {
	if ( vmConfig == null )
	    {
		List rps = new ArrayList();

		BufferedReader br = null;
		try
		    {
			InputStream is = MultiPropertiesConfig.class.getResourceAsStream( VM_CONFIG_RSRC_PATHS );
			if ( is != null )
			    {
				br = new BufferedReader( new InputStreamReader( is, "8859_1" ) );
				String rp;
				while ((rp = br.readLine()) != null)
				    {
					rp = rp.trim();
					if ("".equals( rp ) || rp.startsWith("#"))
					    continue;
					
					rps.add( rp );
				    }
				vmConfig = new BasicMultiPropertiesConfig( (String[]) rps.toArray( new String[ rps.size() ] ) ); 
			    }
			else
			    {
				//System.err.println("Resource path list could not be found at resource path: " + VM_CONFIG_RSRC_PATHS);
				//System.err.println("Using empty vmconfig.");
				vmConfig = EMPTY;
			    }
		    }
		catch (IOException e)
		    { e.printStackTrace(); }
		finally
		    {
			try { if ( br != null ) br.close(); }
			catch (IOException e) { e.printStackTrace(); }
		    }
	    }
	return vmConfig;
    }

    public boolean foundVmConfig()
    { return vmConfig != EMPTY; }

    public abstract String[] getPropertiesResourcePaths();

    public abstract Properties getPropertiesByResourcePath(String path);

    public abstract Properties getPropertiesByPrefix(String pfx);

    public abstract String getProperty( String key );
}
