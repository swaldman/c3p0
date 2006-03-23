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


package com.mchange.v1.identicator;

// revisit equals() if ever made non-final

final class StrongIdHashKey extends IdHashKey
{
    Object      keyObj;
    
    public StrongIdHashKey(Object keyObj, Identicator id)
    {
	super( id );
	this.keyObj = keyObj;
    }

    public Object getKeyObj()
    { return keyObj; }

    public boolean equals(Object o)
    {
	//  fast type-exact match for final class
	if (o instanceof StrongIdHashKey)
	    return id.identical( keyObj, ((StrongIdHashKey) o).keyObj );
	else
	    return false;
    }

    public int hashCode()
    { return id.hash( keyObj ); }
}
