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

import java.util.*;
import com.mchange.v1.util.*;

/*
 * Implementation notes: many AbstractMap methods are written in
 * terms of entrySet(). It is most important to get that right.
 */
abstract class IdMap extends AbstractMap implements Map
{
    Map         inner;
    Identicator id;

    protected IdMap(Map inner, Identicator id)
    {
	this.inner = inner;
	this.id = id;
    }
    
    public Object put(Object key, Object value) 
    { return inner.put( createIdKey( key ), value ); }

    public boolean containsKey(Object key)
    { return inner.containsKey( createIdKey( key ) ); }

    public Object get(Object key)
    { return inner.get( createIdKey( key ) ); }

    public Object remove(Object key)
    { return inner.remove( createIdKey( key ) ); }

    protected Object removeIdHashKey( IdHashKey idhk )
    { return inner.remove( idhk ); }

    public Set entrySet()
    { return new UserEntrySet(); }

    protected final Set internalEntrySet()
    { return inner.entrySet(); }

    protected abstract IdHashKey createIdKey(Object o);

    protected final Entry createIdEntry(Object key, Object val)
    { return new SimpleMapEntry( createIdKey( key ), val); }
    
    protected final Entry createIdEntry(Entry entry)
    { return createIdEntry( entry.getKey(), entry.getValue() ); }

    private final class UserEntrySet extends AbstractSet
    {
	Set innerEntries = inner.entrySet();
	
	public Iterator iterator()
	{
	    return new WrapperIterator(innerEntries.iterator(), true)
		{
		    protected Object transformObject(Object o)
		    { return new UserEntry( (Entry) o ); }
		};
	}
	
	public int size()
	{ return innerEntries.size(); }
	
	public boolean contains(Object o)
	{ 
	    if (o instanceof Entry)
		{
		    Entry entry = (Entry) o;
		    return innerEntries.contains( createIdEntry( entry ) ); 
		}
	    else
		return false;
	}
	
	public boolean remove(Object o)
	{
	    if (o instanceof Entry)
		{
		    Entry entry = (Entry) o;
		    return innerEntries.remove( createIdEntry( entry ) ); 
		}
	    else
		return false;
	}

	public void clear()
	{ inner.clear(); }
    }

    protected static class UserEntry extends AbstractMapEntry
    {
	private Entry innerEntry;

	UserEntry(Entry innerEntry)
	{ this.innerEntry = innerEntry; }

	public final Object getKey()
	{ return ((IdHashKey) innerEntry.getKey()).getKeyObj(); }

	public final Object getValue()
	{ return innerEntry.getValue(); }

	public final Object setValue(Object value)
	{ return innerEntry.setValue( value ); }
    }
}
