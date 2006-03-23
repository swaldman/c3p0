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


package com.mchange.v1.util;

import com.mchange.v2.lang.ObjectUtils;

public final class ArrayUtils
{
    /**
     * returns a hash-code for an array consistent with Arrays.equals( ... )
     */
    public static int hashArray(Object[] oo)
    {
	int len = oo.length;
	int out = len;
  	for (int i = 0; i < len; ++i)
  	    {
  		//we rotate the bits of the element hashes
  		//around so that the hash has some loaction
  		//dependency
  		int elem_hash = ObjectUtils.hashOrZero(oo[i]);
  		int rot = i % 32;
  		int rot_hash = elem_hash >>> rot;
  		rot_hash |= elem_hash << (32 - rot);
  		out ^= rot_hash;
  	    }
	return out;
    }

    /**
     * returns a hash-code for an array consistent with Arrays.equals( ... )
     */
    public static int hashArray(int[] ii)
    {
	int len = ii.length;
	int out = len;
  	for (int i = 0; i < len; ++i)
  	    {
  		//we rotate the bits of the element hashes
  		//around so that the hash has some loaction
  		//dependency
  		int elem_hash = ii[i];
  		int rot = i % 32;
  		int rot_hash = elem_hash >>> rot;
  		rot_hash |= elem_hash << (32 - rot);
  		out ^= rot_hash;
  	    }
	return out;
    }

    public static int hashOrZeroArray(Object[] oo)
    { return (oo == null ? 0 : hashArray(oo)); }

    public static int hashOrZeroArray(int[] ii)
    { return (ii == null ? 0 : hashArray(ii)); }

    public static String stringifyContents(Object[] array)
    {
	StringBuffer sb = new StringBuffer();
	sb.append("[ ");
	for (int i = 0, len = array.length; i < len; ++i)
	    {
		if (i != 0)
		    sb.append(", ");
		sb.append( array[i].toString() );
	    }
	sb.append(" ]");
	return sb.toString();
    }
}

