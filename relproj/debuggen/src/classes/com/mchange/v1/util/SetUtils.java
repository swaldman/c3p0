/*
 * Distributed as part of debuggen v.0.1.0
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


package com.mchange.v1.util;

import java.util.Iterator;
import java.util.Set;
import java.util.AbstractSet;
import java.util.HashSet;

public final class SetUtils
{
    public static Set oneElementUnmodifiableSet(final Object elem)
    {
	return new AbstractSet()
	    {
		public Iterator iterator()
		{ return IteratorUtils.oneElementUnmodifiableIterator( elem ); }

		public int size() { return 1; }

		public boolean isEmpty()
		{ return false; }

		public boolean contains(Object o) 
		{ return o == elem; }

	    };
    }

    public static Set setFromArray(Object[] array)
    {
	HashSet out = new HashSet();
	for (int i = 0, len = array.length; i < len; ++i)
	    out.add( array[i] );
	return out;
    }

    public static boolean equivalentDisregardingSort(Set a, Set b)
    {
	return 
	    a.containsAll( b ) &&
	    b.containsAll( a );
    }

    /**
     * finds a hash value which takes into account
     * the value of all elements, such that two sets
     * for which equivalentDisregardingSort(a, b) returns
     * true will hashContentsDisregardingSort() to the same value
     */
    public static int hashContentsDisregardingSort(Set s)
    {
	int out = 0;
	for (Iterator ii = s.iterator(); ii.hasNext(); )
	    {
		Object o = ii.next();
		if (o != null) out ^= o.hashCode();
	    }
	return out;
    }
}

