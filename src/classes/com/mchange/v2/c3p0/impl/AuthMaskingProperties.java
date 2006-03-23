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


package com.mchange.v2.c3p0.impl;

import java.util.Enumeration;
import java.util.Properties;

public class AuthMaskingProperties extends Properties
{
    public AuthMaskingProperties()
    { super(); }

    public AuthMaskingProperties( Properties p )
    { super( p ); }

    public static AuthMaskingProperties fromAnyProperties( Properties p )
    { 
	AuthMaskingProperties out = new AuthMaskingProperties();
	for( Enumeration e = p.propertyNames(); e.hasMoreElements(); )
	    {
		String key = (String) e.nextElement();
		out.setProperty( key, p.getProperty( key ) );
	    }
	return out;
    }

    private String normalToString()
    { return super.toString(); }

    public String toString()
    {
	boolean hasUser = (this.get("user") != null);
	boolean hasPassword = (this.get("password") != null);
	if ( hasUser || hasPassword )
	    {
		AuthMaskingProperties clone = (AuthMaskingProperties) this.clone();
		if (hasUser)
		    clone.put("user", "******");
		if (hasPassword)
		    clone.put("password", "******");
		return clone.normalToString();
	    }
	else
	    return this.normalToString();
    }
}