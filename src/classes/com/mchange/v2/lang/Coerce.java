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


package com.mchange.v2.lang;

import java.util.*;

public final class Coerce
{
    final static Set CAN_COERCE;
    
    static
    {
	Class[] classes =
	    {
		byte.class,
		boolean.class,
		char.class,
		short.class,
		int.class,
		long.class,
		float.class,
		double.class,
		String.class,
		Byte.class,
		Boolean.class,
		Character.class,
		Short.class,
		Integer.class,
		Long.class,
		Float.class,
		Double.class
	    };
	Set tmp = new HashSet();
	tmp.addAll( Arrays.asList( classes ) );
	CAN_COERCE = Collections.unmodifiableSet( tmp );
    }

    public static boolean canCoerce( Class cl )
    { return CAN_COERCE.contains( cl ); }

    public static boolean canCoerce( Object o )
    { return canCoerce( o.getClass() ); }

    public static int toInt( String s )
    { 
	try { return Integer.parseInt( s ); }
	catch ( NumberFormatException e )
	    { return (int) Double.parseDouble( s ); }
    }

    public static long toLong( String s )
    { 
	try { return Long.parseLong( s ); }
	catch ( NumberFormatException e )
	    { return (long) Double.parseDouble( s ); }
    }

    public static float toFloat( String s )
    { return Float.parseFloat( s ); }

    public static double toDouble( String s )
    { return Double.parseDouble( s ); }

    public static byte toByte( String s )
    { return (byte) toInt(s); }

    public static short toShort( String s )
    { return (short) toInt(s); }

    public static boolean toBoolean( String s )
    { return Boolean.valueOf( s ).booleanValue(); }

    public static char toChar( String s )
    {
	s = s.trim();
	if (s.length() == 1)
	    return s.charAt( 0 );
	else
	    return (char) toInt(s);
    }

    public static Object toObject( String s, Class type )
    {
	if ( type == byte.class) type = Byte.class;
	else if ( type == boolean.class) type = Boolean.class;
	else if ( type == char.class) type = Character.class;
	else if ( type == short.class) type = Short.class;
	else if ( type == int.class) type = Integer.class;
	else if ( type == long.class) type = Long.class;
	else if ( type == float.class) type = Float.class;
	else if ( type == double.class) type = Double.class;

	if ( type == String.class )
	    return s;
	else if ( type == Byte.class )
	    return new Byte( toByte( s ) );
	else if ( type == Boolean.class )
	    return Boolean.valueOf( s );
	else if ( type == Character.class )
	    return new Character( toChar( s ) );
	else if ( type == Short.class )
	    return new Short( toShort( s ) );
	else if ( type == Integer.class )
	    return new Integer( s );
	else if ( type == Long.class )
	    return new Long( s );
	else if ( type == Float.class )
	    return new Float( s );
	else if ( type == Double.class )
	    return new Double( s );
	else
	    throw new IllegalArgumentException("Cannot coerce to type: " + type.getName());
    }

    private Coerce()
    {}
}
