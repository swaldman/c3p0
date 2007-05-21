/*
 * Distributed as part of c3p0 v.0.9.1.2
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
     *  The array may contain nulls, but <TT>o</TT>
     *  must be non-null.
     */
    public static int indexOf(Object[] array, Object o)
    {
    for (int i = 0, len = array.length; i < len; ++i)
        if (o.equals(array[i])) return i;
    return -1;
    }
    
    public static int identityIndexOf(Object[] array, Object o)
    {
    for (int i = 0, len = array.length; i < len; ++i)
        if (o == array[i]) return i;
    return -1;
    }
    
    public static boolean startsWith( byte[] checkMe, byte[] maybePrefix )
    {
    int cm_len = checkMe.length;
    int mp_len = maybePrefix.length;
    if (cm_len < mp_len)
        return false;
    for (int i = 0; i < mp_len; ++i)
        if (checkMe[i] != maybePrefix[i])
        return false;
    return true;
    }
    
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

    /**
     * @deprecated use the various toString(T[] methods)
     */
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
    
    //these methods are obsoleted by Arrays.toString() in jdk1.5, but
    //for libs that support older VMs...
    private static String toString(String[] strings, int guessed_len)
    {
        StringBuffer sb = new StringBuffer( guessed_len );
        boolean first = true;
        sb.append('[');
        for (int i = 0, len = strings.length; i < len; ++i)
        {
            if (first)
                first = false;
            else
                sb.append(',');
            sb.append( strings[i] );
        }
        sb.append(']');
        return sb.toString();
    }
    
    public static String toString(boolean[] arr)
    {
        String[] strings = new String[arr.length];
        int chars = 0;
        for(int i = 0, len = arr.length; i < len; ++i)
        {
            String str = String.valueOf(arr[i]);
            chars += str.length();
            strings[i] = str;
        }
        return toString(strings, chars + arr.length + 1);
    }
    
    public static String toString(byte[] arr)
    {
        String[] strings = new String[arr.length];
        int chars = 0;
        for(int i = 0, len = arr.length; i < len; ++i)
        {
            String str = String.valueOf(arr[i]);
            chars += str.length();
            strings[i] = str;
        }
        return toString(strings, chars + arr.length + 1);
    }
    
    public static String toString(char[] arr)
    {
        String[] strings = new String[arr.length];
        int chars = 0;
        for(int i = 0, len = arr.length; i < len; ++i)
        {
            String str = String.valueOf(arr[i]);
            chars += str.length();
            strings[i] = str;
        }
        return toString(strings, chars + arr.length + 1);
    }

    public static String toString(short[] arr)
    {
        String[] strings = new String[arr.length];
        int chars = 0;
        for(int i = 0, len = arr.length; i < len; ++i)
        {
            String str = String.valueOf(arr[i]);
            chars += str.length();
            strings[i] = str;
        }
        return toString(strings, chars + arr.length + 1);
    }

    public static String toString(int[] arr)
    {
        String[] strings = new String[arr.length];
        int chars = 0;
        for(int i = 0, len = arr.length; i < len; ++i)
        {
            String str = String.valueOf(arr[i]);
            chars += str.length();
            strings[i] = str;
        }
        return toString(strings, chars + arr.length + 1);
    }
    
    public static String toString(long[] arr)
    {
        String[] strings = new String[arr.length];
        int chars = 0;
        for(int i = 0, len = arr.length; i < len; ++i)
        {
            String str = String.valueOf(arr[i]);
            chars += str.length();
            strings[i] = str;
        }
        return toString(strings, chars + arr.length + 1);
    }
    
   public static String toString(float[] arr)
    {
        String[] strings = new String[arr.length];
        int chars = 0;
        for(int i = 0, len = arr.length; i < len; ++i)
        {
            String str = String.valueOf(arr[i]);
            chars += str.length();
            strings[i] = str;
        }
        return toString(strings, chars + arr.length + 1);
    }
    
    public static String toString(double[] arr)
    {
        String[] strings = new String[arr.length];
        int chars = 0;
        for(int i = 0, len = arr.length; i < len; ++i)
        {
            String str = String.valueOf(arr[i]);
            chars += str.length();
            strings[i] = str;
        }
        return toString(strings, chars + arr.length + 1);
    }

    public static String toString(Object[] arr)
    {
        String[] strings = new String[arr.length];
        int chars = 0;
        for(int i = 0, len = arr.length; i < len; ++i)
        {
            String str;
            Object o = arr[i];
            if (o instanceof Object[])
                str = toString((Object[]) o);
            else if (o instanceof double[])
                str = toString((double[]) o);
            else if (o instanceof float[])
                str = toString((float[]) o);
            else if (o instanceof long[])
                str = toString((long[]) o);
            else if (o instanceof int[])
                str = toString((int[]) o);
            else if (o instanceof short[])
                str = toString((short[]) o);
            else if (o instanceof char[])
                str = toString((char[]) o);
            else if (o instanceof byte[])
                str = toString((byte[]) o);
            else if (o instanceof boolean[])
                str = toString((boolean[]) o);
            else
                str = String.valueOf(arr[i]);
            chars += str.length();
            strings[i] = str;
        }
        return toString(strings, chars + arr.length + 1);
    }

    
    private ArrayUtils()
    {}
    
    /*
    public static void main(String[] argv)
    {
        int[] is = {1,2,3,4};
        String[] ss = {"Hello", "There"};
        Object[] os = {"Poop", is, ss, new Thread()};
        
        System.out.println( toString(is) );
        System.out.println( toString(ss) );
        System.out.println( toString(os) );
    }
    */
}    


