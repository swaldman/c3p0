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
import java.util.*;
import com.mchange.v1.util.WrapperIterator;

/**
 * IdWeakHashMap is NOT null-accepting!
 */
public final class IdWeakHashMap extends IdMap implements Map
{
    ReferenceQueue rq;

    public IdWeakHashMap(Identicator id)
    { 
	super ( new HashMap(), id ); 
	this.rq = new ReferenceQueue();
    }

    //all methods from Map interface
    public int size()
    {
	// doing cleanCleared() afterwards, as with other methods
	// would be just as "correct", as weak collections
	// make no guarantees about when things disappear,
	// but for size(), it feels a little more accurate
	// this way.
	cleanCleared();
	return super.size();
    }

    public boolean isEmpty()
    {
	try
	    { return super.isEmpty(); }
	finally
	    { cleanCleared(); }
    }

    public boolean containsKey(Object o)
    {
	try
	    { return super.containsKey( o ); }
	finally
	    { cleanCleared(); }
    }

    public boolean containsValue(Object o)
    {
	try
	    { return super.containsValue( o ); }
	finally
	    { cleanCleared(); }
    }

    public Object get(Object o)
    {
	try
	    { return super.get( o ); }
	finally
	    { cleanCleared(); }
    }

    public Object put(Object k, Object v)
    {
	try
	    { return super.put( k , v ); }
	finally
	    { cleanCleared(); }
    }

    public Object remove(Object o)
    {
	try
	    { return super.remove( o ); }
	finally
	    { cleanCleared(); }
    }

    public void putAll(Map m)
    {
	try
	    { super.putAll( m ); }
	finally
	    { cleanCleared(); }
    }

    public void clear()
    {
	try
	    { super.clear(); }
	finally
	    { cleanCleared(); }
    }

    public Set keySet()
    {
	try
	    { return super.keySet(); }
	finally
	    { cleanCleared(); }
    }

    public Collection values()
    {
	try
	    { return super.values(); }
	finally
	    { cleanCleared(); }
    }

    /*
     * entrySet() is the basis of the implementation of the other
     * Collection returning methods. Get this right and the rest 
     * follow.
     */
    public Set entrySet()
    {
	try
	    { return new WeakUserEntrySet(); }
	finally
	    { cleanCleared(); }
    }

    public boolean equals(Object o)
    {
	try
	    { return super.equals( o ); }
	finally
	    { cleanCleared(); }
    }

    public int hashCode()
    {
	try
	    { return super.hashCode(); }
	finally
	    { cleanCleared(); }
    }

    //internal methods
    protected IdHashKey createIdKey(Object o)
    { return new WeakIdHashKey( o, id, rq ); }

    private void cleanCleared()
    {
	WeakIdHashKey.Ref ref;
	while ((ref = (WeakIdHashKey.Ref) rq.poll()) != null)
	    this.removeIdHashKey( ref.getKey() );
    }

    private final class WeakUserEntrySet extends AbstractSet
    {
	Set innerEntries = internalEntrySet();
	
	public Iterator iterator()
	{
	    try
		{
		    return new WrapperIterator(innerEntries.iterator(), true)
			{
			    protected Object transformObject(Object o)
			    {
				Entry innerEntry = (Entry) o;
				final Object userKey = ((IdHashKey) innerEntry.getKey()).getKeyObj();
				if (userKey == null)
				    return WrapperIterator.SKIP_TOKEN;
				else
				    return new UserEntry( innerEntry ) 
					{ Object preventRefClear = userKey; };
			    }
			};
		}
	finally
	    { cleanCleared(); }
	}
	
	public int size()
	{ 
	    // doing cleanCleared() afterwards, as with other methods
	    // would be just as "correct", as weak collections
	    // make no guarantees about when things disappear,
	    // but for size(), it feels a little more accurate
	    // this way.
	    cleanCleared();
	    return innerEntries.size(); 
	}
	
	public boolean contains(Object o)
	{ 
	    try
		{
		    if (o instanceof Entry)
			{
			    Entry entry = (Entry) o;
			    return innerEntries.contains( createIdEntry( entry ) ); 
			}
		    else
			return false;
		}
	    finally
		{ cleanCleared(); }
	}
	
	public boolean remove(Object o)
	{
	    try
		{
		    if (o instanceof Entry)
			{
			    Entry entry = (Entry) o;
			    return innerEntries.remove( createIdEntry( entry ) ); 
			}
		    else
			return false;
		}
	    finally
		{ cleanCleared(); }
	}

	public void clear()
	{
	    try
		{ inner.clear(); }
	    finally
		{ cleanCleared(); }
	}
    }
}
