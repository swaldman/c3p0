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

import com.mchange.v2.coalesce.*;

public final class IdentityTokenizedCoalesceChecker implements CoalesceChecker
{
    public static IdentityTokenizedCoalesceChecker INSTANCE = new IdentityTokenizedCoalesceChecker();

    public boolean checkCoalesce( Object a, Object b )
    {
	IdentityTokenized aa = (IdentityTokenized) a;
	IdentityTokenized bb = (IdentityTokenized) b;
	
	String ta = aa.getIdentityToken();
	String tb = bb.getIdentityToken();
	
	if (ta == null || tb == null)
	    throw new NullPointerException( "[c3p0 bug] An IdentityTokenized object has no identity token set?!?! " + (ta == null ? ta : tb) );
	else
	    return ta.equals(tb);
    }
    
    public int coalesceHash( Object a )
    { 
	String t = ((IdentityTokenized) a).getIdentityToken();
	return (t != null ? t.hashCode() : 0); 
    }

    private IdentityTokenizedCoalesceChecker()
    {}
}
