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

public class BasicMultiPropertiesConfig extends MultiPropertiesConfig
{
    String[] rps;
    Map  propsByResourcePaths = new HashMap();
    Map  propsByPrefixes;

    Properties propsByKey;

    public BasicMultiPropertiesConfig(String[] resourcePaths)
    { this( resourcePaths, null ); }

    public BasicMultiPropertiesConfig(String[] resourcePaths, MLogger logger)
    {
	List goodPaths = new ArrayList();
	for( int i = 0, len = resourcePaths.length; i < len; ++i )
	    {
		String rp = resourcePaths[i];
		if ("/".equals(rp))
		    {
			try
			    {
				propsByResourcePaths.put( rp, System.getProperties() );
				goodPaths.add( rp );
			    }
			catch (SecurityException e)
			    {
				if (logger != null)
				    {
					if ( logger.isLoggable( MLevel.WARNING ) )
					    logger.log( MLevel.WARNING, 
							"Read of system Properties blocked -- " +
							"ignoring any configuration via System properties, and using Empty Properties! " +
							"(But any configuration via a resource properties files is still okay!)",
							e );
				    }
				else
				    {
					System.err.println("Read of system Properties blocked -- " +
							   "ignoring any configuration via System properties, and using Empty Properties! " +
							   "(But any configuration via a resource properties files is still okay!)");
					e.printStackTrace(); 
				    }
			    }
		    }
		else
		    {
			Properties p = new Properties();
			InputStream pis = MultiPropertiesConfig.class.getResourceAsStream( rp );
			if ( pis != null )
			    {
				try
				    {
					p.load( pis );
					propsByResourcePaths.put( rp, p );
					goodPaths.add( rp );
				    }
				catch (IOException e)
				    {
					if (logger != null)
					    {
						if ( logger.isLoggable( MLevel.WARNING ) )
						    logger.log( MLevel.WARNING, 
								"An IOException occurred while loading configuration properties from resource path '" + rp + "'.",
								e );
					    }
					else
					    e.printStackTrace(); 
				    }
				finally
				    {
					try { if ( pis != null ) pis.close(); }
					catch (IOException e) 
					    { 
						if (logger != null)
						    {
							if ( logger.isLoggable( MLevel.WARNING ) )
							    logger.log( MLevel.WARNING, 
									"An IOException occurred while closing InputStream from resource path '" + rp + "'.",
									e );
						    }
						else
						    e.printStackTrace(); 
					    }
				    }
			    }
			else
			    {
				if (logger != null)
				    {
					if ( logger.isLoggable( MLevel.FINE ) )
					    logger.fine( "Configuration properties not found at ResourcePath '" + rp + "'. [logger name: " + logger.getName() + ']' );
				    }
// 			else if (Debug.DEBUG && Debug.TRACE == Debug.TRACE_MAX)
// 			    System.err.println("Configuration properties not found at ResourcePath '" + rp + "'." );
			    }
		    }
	    }
	
	this.rps = (String[]) goodPaths.toArray( new String[ goodPaths.size() ] );
	this.propsByPrefixes = Collections.unmodifiableMap( extractPrefixMapFromRsrcPathMap(rps, propsByResourcePaths) );
	this.propsByResourcePaths = Collections.unmodifiableMap( propsByResourcePaths );
	this.propsByKey = extractPropsByKey(rps, propsByResourcePaths);
    }


    private static String extractPrefix( String s )
    {
	int lastdot = s.lastIndexOf('.');
	if ( lastdot < 0 )
	    return null;
	else
	    return s.substring(0, lastdot);
    }

    private static Properties findProps(String rp, Map pbrp)
    {
	//System.err.println("findProps( " + rp + ", ... )");
	Properties p;
	
	// MOVED THIS LOGIC INTO CONSTRUCTOR ABOVE, TO TREAT SYSTEM PROPS UNIFORMLY
	// WITH THE REST, AND TO AVOID UNINTENTIONAL ATTEMPTS TO READ RESOURCE "/"
	// AS STREAM -- swaldman, 2006-01-19
	
// 	if ( "/".equals( rp ) )
// 	    {
// 		try { p = System.getProperties(); }
// 		catch ( SecurityException e )
// 		    {
// 			System.err.println(BasicMultiPropertiesConfig.class.getName() +
// 					   " Read of system Properties blocked -- ignoring any configuration via System properties, and using Empty Properties! " +
// 					   "(But any configuration via a resource properties files is still okay!)"); 
// 			p = new Properties(); 
// 		    }
// 	    }
// 	else
	p = (Properties) pbrp.get( rp );
	
// 	System.err.println( p );

	return p;
    }

    private static Properties extractPropsByKey( String[] resourcePaths, Map pbrp )
    {
	Properties out = new Properties();
	for (int i = 0, len = resourcePaths.length; i < len; ++i)
	    {
		String rp = resourcePaths[i];
		Properties p = findProps( rp, pbrp );
		if (p == null)
		    {
			System.err.println("Could not find loaded properties for resource path: " + rp);
			continue;
		    }
		for (Iterator ii = p.keySet().iterator(); ii.hasNext(); )
		    {
			Object kObj = ii.next();
			if (!(kObj instanceof String))
			    {
				// note that we can not use the MLog library here, because initialization
				// of that library depends on this function.
				System.err.println( BasicMultiPropertiesConfig.class.getName() + ": " +
						    "Properties object found at resource path " +
						    ("/".equals(rp) ? "[system properties]" : "'" + rp + "'") +
						    "' contains a key that is not a String: " +
						    kObj);
				System.err.println("Skipping...");
				continue;
			    }
			Object vObj = p.get( kObj );
			if (vObj != null && !(vObj instanceof String))
			    {
				// note that we can not use the MLog library here, because initialization
				// of that library depends on this function.
				System.err.println( BasicMultiPropertiesConfig.class.getName() + ": " +
						    "Properties object found at resource path " +
						    ("/".equals(rp) ? "[system properties]" : "'" + rp + "'") +
						    " contains a value that is not a String: " +
						    vObj);
				System.err.println("Skipping...");
				continue;
			    }

			String key = (String) kObj;
			String val = (String) vObj;
			out.put( key, val );
		    }
	    }
	return out;
    }

    private static Map extractPrefixMapFromRsrcPathMap(String[] resourcePaths, Map pbrp)
    {
	Map out = new HashMap();
	//for( Iterator ii = pbrp.values().iterator(); ii.hasNext(); )
	for (int i = 0, len = resourcePaths.length; i < len; ++i)
	    {
		String rp = resourcePaths[i];
		Properties p = findProps( rp, pbrp );
		if (p == null)
		    {
			System.err.println(BasicMultiPropertiesConfig.class.getName() + " -- Could not find loaded properties for resource path: " + rp);
			continue;
		    }
		for (Iterator jj = p.keySet().iterator(); jj.hasNext(); )
		    {
			Object kObj = jj.next();
			if (! (kObj instanceof String))
			    {
				// note that we can not use the MLog library here, because initialization
				// of that library depends on this function.
				System.err.println( BasicMultiPropertiesConfig.class.getName() + ": " +
						    "Properties object found at resource path " +
						    ("/".equals(rp) ? "[system properties]" : "'" + rp + "'") +
						    "' contains a key that is not a String: " +
						    kObj);
				System.err.println("Skipping...");
				continue;
			    }

			String key = (String) kObj;
			String prefix = extractPrefix( key );
			while (prefix != null)
			    {
				Properties byPfx = (Properties) out.get( prefix );
				if (byPfx == null)
				    {
					byPfx = new Properties();
					out.put( prefix, byPfx );
				    }
				byPfx.put( key, p.get( key ) );

				prefix=extractPrefix( prefix );
			    }
		    }
	    }
	return out;
    }

    public String[] getPropertiesResourcePaths()
    { return (String[]) rps.clone(); }

    public Properties getPropertiesByResourcePath(String path)
    { 
	Properties out = ((Properties) propsByResourcePaths.get( path )); 
	return (out == null ? new Properties() : out);
    }

    public Properties getPropertiesByPrefix(String pfx)
    {
	Properties out = ((Properties) propsByPrefixes.get( pfx ));
	return (out == null ? new Properties() : out);
    }

    public String getProperty( String key )
    { return propsByKey.getProperty( key ); }
}
