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

class CombinedMultiPropertiesConfig extends MultiPropertiesConfig
{
    MultiPropertiesConfig[] configs;
    String[] resourcePaths;

    CombinedMultiPropertiesConfig( MultiPropertiesConfig[] configs )
    { 
	this.configs = configs; 

	List allPaths = new LinkedList();
	for (int i = configs.length - 1; i >= 0; --i)
	    {
		String[] rps = configs[i].getPropertiesResourcePaths();
		for (int j = rps.length - 1; j >= 0; --j)
		    {
			String rp = rps[j];
			if (! allPaths.contains( rp ) )
			    allPaths.add(0, rp);
		    }
	    }
	this.resourcePaths = (String[]) allPaths.toArray( new String[ allPaths.size() ] );
    }

    public String[] getPropertiesResourcePaths()
    { return (String[]) resourcePaths.clone(); }
    
    public Properties getPropertiesByResourcePath(String path)
    {
	for (int i = configs.length - 1; i >= 0; --i)
	    {
		MultiPropertiesConfig config = configs[i];
		Properties check = config.getPropertiesByResourcePath(path);
		if (check != null) 
		    return check;
	    }
	return null;
    }
    
    public Properties getPropertiesByPrefix(String pfx)
    {
	List entries = new LinkedList();
	for (int i = configs.length - 1; i >= 0; --i)
	    {
		MultiPropertiesConfig config = configs[i];
		Properties check = config.getPropertiesByPrefix(pfx);
		if (check != null)
		    entries.addAll( 0, check.entrySet() );
	    }
	if (entries.size() == 0)
	    return null;
	else
	    {
		Properties out = new Properties();
		for (Iterator ii = entries.iterator(); ii.hasNext(); )
		    {
			Map.Entry entry = (Map.Entry) ii.next();
			out.put( entry.getKey(), entry.getValue() );
		    }
		return out;
	    }
    }
    
    public String getProperty( String key )
    {
	for (int i = configs.length - 1; i >= 0; --i)
	    {
		MultiPropertiesConfig config = configs[i];
		String check = config.getProperty(key);
		if (check != null) 
		    return check;
	    }
	return null;
    }
}

