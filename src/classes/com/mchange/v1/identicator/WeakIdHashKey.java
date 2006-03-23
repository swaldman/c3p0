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

import java.lang.ref.*;

// revisit equals() if ever made non-final

final class WeakIdHashKey extends IdHashKey
{
    Ref keyRef;
    int hash;

    public WeakIdHashKey(Object keyObj, Identicator id, ReferenceQueue rq)
    {
	super( id );

	if (keyObj == null)
	    throw new UnsupportedOperationException("Collection does not accept nulls!");

	this.keyRef = new Ref( keyObj, rq );
	this.hash = id.hash( keyObj );
    }

    public Ref getInternalRef()
    { return this.keyRef; }

    public Object getKeyObj()
    { return keyRef.get(); }

    public boolean equals(Object o)
    {
	// fast type-exact match for final class
	if (o instanceof WeakIdHashKey)
	    {
		WeakIdHashKey other = (WeakIdHashKey) o;
		if (this.keyRef == other.keyRef)
		    return true;
		else
		    {
			Object myKeyObj = this.keyRef.get();
			Object oKeyObj  = other.keyRef.get();
			if (myKeyObj == null || oKeyObj == null)
			    return false;
			else
			    return id.identical( myKeyObj, oKeyObj );
		    }
	    }
	else
	    return false;
    }

    public int hashCode()
    { return hash; }

    class Ref extends WeakReference
    {
	public Ref( Object referant, ReferenceQueue rq )
	{ super( referant, rq ); }
	
	WeakIdHashKey getKey()
	{ return WeakIdHashKey.this; }
    }
}
