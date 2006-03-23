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


package com.mchange.v2.lang;

import com.mchange.v2.log.*;
import com.mchange.v1.util.StringTokenizerUtils;

public final class VersionUtils
{
    private final static MLogger logger = MLog.getLogger( VersionUtils.class );

    private final static int[] DFLT_VERSION_ARRAY = {1,1};

    private final static int[] JDK_VERSION_ARRAY;
    private final static int JDK_VERSION; //two digit int... 10 for 1.0, 11 for 1.1, etc.

    static
    {
	String vstr = System.getProperty( "java.version" );
	int[] v;
	if (vstr == null)
	    {
		if (logger.isLoggable( MLevel.WARNING ))
		    logger.warning("Could not find java.version System property. Defaulting to JDK 1.1");
		v = DFLT_VERSION_ARRAY;
	    }
	else
	    { 
		try { v = extractVersionNumberArray( vstr, "._" ); }
		catch ( NumberFormatException e )
		    {
			if (logger.isLoggable( MLevel.WARNING ))
			    logger.warning("java.version ''" + vstr + "'' could not be parsed. Defaulting to JDK 1.1.");
			v = DFLT_VERSION_ARRAY;
		    }
	    }
	int jdkv = 0;
	if (v.length > 0)
	    jdkv += (v[0] * 10);
	if (v.length > 1)
	    jdkv += (v[1]);

	JDK_VERSION_ARRAY = v;
	JDK_VERSION = jdkv;

	//System.err.println( JDK_VERSION );
    }

    public static boolean isJavaVersion10()
    { return (JDK_VERSION == 10); }

    public static boolean isJavaVersion11()
    { return (JDK_VERSION == 11); }

    public static boolean isJavaVersion12()
    { return (JDK_VERSION == 12); }

    public static boolean isJavaVersion13()
    { return (JDK_VERSION == 13); }

    public static boolean isJavaVersion14()
    { return (JDK_VERSION == 14); }

    public static boolean isJavaVersion15()
    { return (JDK_VERSION == 15); }

    public static boolean isAtLeastJavaVersion10()
    { return (JDK_VERSION >= 10); }

    public static boolean isAtLeastJavaVersion11()
    { return (JDK_VERSION >= 11); }

    public static boolean isAtLeastJavaVersion12()
    { return (JDK_VERSION >= 12); }

    public static boolean isAtLeastJavaVersion13()
    { return (JDK_VERSION >= 13); }

    public static boolean isAtLeastJavaVersion14()
    { return (JDK_VERSION >= 14); }

    public static boolean isAtLeastJavaVersion15()
    { return (JDK_VERSION >= 15); }

    public static int[] extractVersionNumberArray(String versionString, String delims)
	throws NumberFormatException
    {
	String[] intStrs = StringTokenizerUtils.tokenizeToArray( versionString, delims, false );
	int len = intStrs.length;
	int[] out = new int[ len ];
	for (int i = 0; i < len; ++i)
	    out[i] = Integer.parseInt( intStrs[i] );
	return out;
    }

    public boolean prefixMatches( int[] pfx, int[] fullVersion )
    {
	if (pfx.length > fullVersion.length)
	    return false;
	else
	    {
		for (int i = 0, len = pfx.length; i < len; ++i)
		    if (pfx[i] != fullVersion[i])
			return false;
		return true;
	    }
    }

    public static int lexicalCompareVersionNumberArrays(int[] a, int[] b)
    {
	int alen = a.length;
	int blen = b.length;
	for (int i = 0; i < alen; ++i)
	    {
		if (i == blen)
		    return 1; //a is larger if they are the same to a point, but a has an extra version number
		else if (a[i] > b[i])
		    return 1;
		else if (a[i] < b[i])
		    return -1;
	    }
	if (blen > alen)
	    return -1; //a is smaller if they are the same to a point, but b has an extra version number
	else
	    return 0;
    }
}