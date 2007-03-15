/*
 * Distributed as part of c3p0 v.0.9.1.1
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


package com.mchange.v2.util;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;


import com.mchange.v1.util.AbstractMapEntry;
import com.mchange.v1.util.WrapperIterator;

//TODO -- ensure that cleanCleared() gets called only once, even in methods implemented
//        as loops. (cleanCleared() is idempotent, so the repeated calls are okay,
//        but they're wasteful.

/**
 * <p>This class is <u>not</u> Thread safe.
 * Use in single threaded contexts, or contexts where
 * single threaded-access can be guaranteed, or
 * wrap with Collections.synchronizedMap().</p>
 * 
 * <p>This class does not accept null keys or values.</p>
 */
public class DoubleWeakHashMap implements Map
{
    HashMap        inner;
    ReferenceQueue keyQ = new ReferenceQueue();
    ReferenceQueue valQ = new ReferenceQueue();
    
    CheckKeyHolder holder = new CheckKeyHolder();
    
    Set userKeySet = null;
    Collection valuesCollection = null;
    
    public DoubleWeakHashMap()
    { this.inner = new HashMap(); }
    
    public DoubleWeakHashMap(int initialCapacity)
    { this.inner = new HashMap( initialCapacity ); }
    
    public DoubleWeakHashMap(int initialCapacity, float loadFactor)
    { this.inner = new HashMap( initialCapacity, loadFactor ); }
    
    public DoubleWeakHashMap(Map m)
    {
        this();
        putAll(m);
    }

    public void cleanCleared()
    {
        WKey wk;
        while ((wk = (WKey) keyQ.poll()) != null)
            inner.remove(wk);
        
        WVal wv;
        while ((wv = (WVal) valQ.poll()) != null)
            inner.remove(wv.getWKey());
    }
    
    public void clear()
    {
        cleanCleared();
        inner.clear();
    }

    public boolean containsKey(Object key)
    {
        cleanCleared();
        try 
            { return inner.containsKey( holder.set(key) ); }
        finally
            { holder.clear(); }
    }

    public boolean containsValue(Object val)
    {
        for (Iterator ii = inner.values().iterator(); ii.hasNext();)
        {
            WVal wval = (WVal) ii.next();
            if (val.equals(wval.get()))
                return true;
        }
        return false;
    }

    public Set entrySet()
    {
        cleanCleared();
        return new UserEntrySet();
    }

    public Object get(Object key)
    {
        try
        {
            cleanCleared();
            WVal wval = (WVal) inner.get(holder.set(key));
            return (wval == null ? null : wval.get());
        }
        finally
        { holder.clear(); }
    }

    public boolean isEmpty()
    {
        cleanCleared();
        return inner.isEmpty();
    }

    public Set keySet()
    {
        cleanCleared();
        if (userKeySet == null)
            userKeySet = new UserKeySet();
        return userKeySet;
    }

    public Object put(Object key, Object val)
    {
        cleanCleared();
        WVal wout = doPut(key, val);
        if (wout != null)
            return wout.get();
        else
            return null;
    }
    
    private WVal doPut(Object key, Object val)
    {
        WKey wk = new WKey(key, keyQ);
        WVal wv = new WVal(wk, val, valQ);
        return (WVal) inner.put(wk, wv);
    }

    public void putAll(Map m)
    {
       cleanCleared();
       for (Iterator ii = m.entrySet().iterator(); ii.hasNext();)
       {
           Map.Entry entry = (Map.Entry) ii.next();
           this.doPut( entry.getKey(), entry.getValue() );
       }
    }

    public Object remove(Object key)
    {
        try
        {
            cleanCleared();
            WVal wv = (WVal) inner.remove( holder.set(key) );
            return (wv == null ? null : wv.get());
        }
        finally
        { holder.clear(); }
    }

    public int size()
    {
        cleanCleared();
        return inner.size();
    }

    public Collection values()
    {
        if (valuesCollection == null)
            this.valuesCollection = new ValuesCollection();
        return valuesCollection;
    }
    
    final static class CheckKeyHolder
    {
        Object checkKey;
        
        public Object get()
        {return checkKey; }
        
        public CheckKeyHolder set(Object ck)
        { 
            assert this.checkKey == null : "Illegal concurrenct use of DoubleWeakHashMap!";
            
            this.checkKey = ck; 
            return this;
        }
        
        public void clear()
        { checkKey = null; }
        
        public int hashCode()
        { return checkKey.hashCode(); }
        
        public boolean equals(Object o)
        {
            assert this.get() != null : "CheckedKeyHolder should never do an equality check while its value is null." ;
            
            if (this == o)
                return true;
            else if (o instanceof CheckKeyHolder)
                return this.get().equals( ((CheckKeyHolder) o).get() );
            else if (o instanceof WKey)
                return this.get().equals( ((WKey) o).get() );
            else
                return false;
        }
    }
    
    final static class WKey extends WeakReference
    {
        int cachedHash;
        
        WKey(Object keyObj, ReferenceQueue rq)
        {
            super(keyObj, rq);
            this.cachedHash = keyObj.hashCode();
        }
        
        public int hashCode()
        { return cachedHash; }
        
        public boolean equals(Object o)
        {
            if (this == o)
                return true;
            else if (o instanceof WKey)
            {
                WKey oo = (WKey) o;
                Object myVal = this.get();
                Object ooVal = oo.get();
                if (myVal == null || ooVal == null)
                    return false;
                else
                    return myVal.equals(ooVal);
            }
            else if (o instanceof CheckKeyHolder)
            {
                CheckKeyHolder oo = (CheckKeyHolder) o;
                Object myVal = this.get();
                Object ooVal = oo.get();
                if (myVal == null || ooVal == null)
                    return false;
                else
                    return myVal.equals(ooVal);
            }
            else
                return false;
        }
    }
    
    final static class WVal extends WeakReference
    {
        WKey key;
        WVal(WKey key, Object valObj, ReferenceQueue rq)
        {
            super(valObj, rq);
            this.key = key;
        }
        
        public WKey getWKey()
        { return key; }
    }
    
    
    private final class UserEntrySet extends AbstractSet
    {
        private Set innerEntrySet()
        {
            cleanCleared();
            return inner.entrySet();
        }

        public Iterator iterator()
        {
            return new WrapperIterator(innerEntrySet().iterator(), true)
            {
                protected Object transformObject(Object o)
                {
                    Entry innerEntry = (Entry) o;
                    Object key = ((WKey) innerEntry.getKey()).get();
                    Object val = ((WVal) innerEntry.getValue()).get();
                    
                    if (key == null || val == null)
                        return WrapperIterator.SKIP_TOKEN;
                    else
                        return new UserEntry( innerEntry, key, val ); 
                }
            };
        }
        
        public int size()
        { return innerEntrySet().size(); }
    }
    
    class UserEntry extends AbstractMapEntry
    {
        Entry innerEntry;
        Object key;
        Object val;

        UserEntry(Entry innerEntry, Object key, Object value)
        { 
            this.innerEntry = innerEntry; 
            this.key = key;
            this.val = val;
        }

        public final Object getKey()
        { return key; }

        public final Object getValue()
        { return val; }

        public final Object setValue(Object value)
        { return innerEntry.setValue( new WVal( (WKey) innerEntry.getKey() ,value, valQ) ); }
    }    
    
    class UserKeySet implements Set
    {
        public boolean add(Object o)
        {
            cleanCleared();
            throw new UnsupportedOperationException("You cannot add to a Map's key set.");
        }

        public boolean addAll(Collection c)
        {
            cleanCleared();
            throw new UnsupportedOperationException("You cannot add to a Map's key set.");
        }

        public void clear()
        { DoubleWeakHashMap.this.clear(); }

        public boolean contains(Object o)
        {
            return DoubleWeakHashMap.this.containsKey(o);
        }

        public boolean containsAll(Collection c)
        {
            for (Iterator ii = c.iterator(); ii.hasNext();)
                if (! this.contains(ii.next()))
                    return false;
            return true;
        }

        public boolean isEmpty()
        { return DoubleWeakHashMap.this.isEmpty(); }

        public Iterator iterator()
        {
            cleanCleared();
            return new WrapperIterator(DoubleWeakHashMap.this.inner.keySet().iterator(), true)
            {
                protected Object transformObject(Object o)
                {
                    Object key = ((WKey) o).get();
                    
                    if (key == null)
                        return WrapperIterator.SKIP_TOKEN;
                    else
                        return key; 
                }
            };
        }

        public boolean remove(Object o)
        {
            return (DoubleWeakHashMap.this.remove(o) != null);
        }

        public boolean removeAll(Collection c)
        {
            boolean out = false;
            for (Iterator ii = c.iterator(); ii.hasNext();)
                out |= this.remove(ii.next());
            return out;
        }

        public boolean retainAll(Collection c)
        {
            //we implicitly cleanCleared() by calling iterator()
            boolean out = false;
            for (Iterator ii = this.iterator(); ii.hasNext();)
            {
                if (!c.contains(ii.next()))
                {
                    ii.remove();
                    out = true;
                }
            }
            return out;
        }

        public int size()
        { return DoubleWeakHashMap.this.size(); }

        public Object[] toArray()
        { 
            cleanCleared();
            return new HashSet( this ).toArray(); 
        }

        public Object[] toArray(Object[] array)
        {
            cleanCleared();
            return new HashSet( this ).toArray(array); 
        }
    }

    class ValuesCollection implements Collection
    {

        public boolean add(Object o)
        {
            cleanCleared();
            throw new UnsupportedOperationException("DoubleWeakHashMap does not support adding to its values Collection.");
        }

        public boolean addAll(Collection c)
        {
            cleanCleared();
            throw new UnsupportedOperationException("DoubleWeakHashMap does not support adding to its values Collection.");
        }

        public void clear()
        { DoubleWeakHashMap.this.clear(); }

        public boolean contains(Object o)
        { return DoubleWeakHashMap.this.containsValue(o); }

        public boolean containsAll(Collection c)
        {
            for (Iterator ii = c.iterator(); ii.hasNext();)
                if (!this.contains(ii.next()))
                    return false;
            return true;
        }

        public boolean isEmpty()
        { return DoubleWeakHashMap.this.isEmpty(); }

        public Iterator iterator()
        {
            return new WrapperIterator(inner.values().iterator(), true)
            {
                protected Object transformObject(Object o)
                {
                    Object val = ((WVal) o).get();
                    
                    if (val == null)
                        return WrapperIterator.SKIP_TOKEN;
                    else
                        return val; 
                }
            };            
        }

        public boolean remove(Object o)
        {
            cleanCleared();
            return removeValue(o);
        }

        public boolean removeAll(Collection c)
        {
            cleanCleared();
            boolean out = false;
            for (Iterator ii = c.iterator(); ii.hasNext();)
                out |= removeValue(ii.next());
            return out;
        }

        public boolean retainAll(Collection c)
        {
            cleanCleared();
            return retainValues(c);
        }

        public int size()
        { return DoubleWeakHashMap.this.size(); }

        public Object[] toArray()
        { 
            cleanCleared();
            return new ArrayList(this).toArray();
        }

        public Object[] toArray(Object[] array)
        {
            cleanCleared();
            return new ArrayList(this).toArray(array);
        }

        private boolean removeValue(Object val)
        {
            boolean out = false;
            for (Iterator ii = inner.values().iterator(); ii.hasNext();)
            {
                WVal wv = (WVal) ii.next();
                if (val.equals(wv.get()))
                {
                    ii.remove();
                    out = true;
                }
            }
            return out;
        }
        
        private boolean retainValues(Collection c)
        {
            boolean out = false;
            for (Iterator ii = inner.values().iterator(); ii.hasNext();)
            {
                WVal wv = (WVal) ii.next();
                if (! c.contains(wv.get()) )
                {
                    ii.remove();
                    out = true;
                }
            }
            return out;
        }
    }
    
    /*
    public static void main(String[] argv)
    {
        DoubleWeakHashMap m = new DoubleWeakHashMap();
        //Set keySet = new HashSet();
        //Set valSet = new HashSet();

        while (true)
        {
            System.err.println( m.inner.size() );

            //if (Math.random() < 0.1f)
            //    valSet.clear();
            
            Object key = new Object();
            Object val = new long[100000];
            //keySet.add(key);
            //valSet.add(val);
            m.put( key, val );
        }
    }
    */
}
