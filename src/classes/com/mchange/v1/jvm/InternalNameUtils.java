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


package com.mchange.v1.jvm;

public final class InternalNameUtils
{
    public static String dottifySlashesAndDollarSigns(String str)
    { return _dottifySlashesAndDollarSigns( str ).toString(); }

    public static String decodeType(String internalrep) throws TypeFormatException
    { return _decodeType(internalrep).toString(); }

    public static String decodeTypeList(String internalrep) throws TypeFormatException
    { 
	StringBuffer sb = new StringBuffer(64);
	_decodeTypeList(internalrep, 0, sb);
	return sb.toString();
    }

    public static boolean isPrimitive(char rep)
    { 
	return (rep == 'Z' ||
		rep == 'B' ||
		rep == 'C' ||
		rep == 'S' ||
		rep == 'I' ||
		rep == 'J' ||
		rep == 'F' ||
		rep == 'D' ||
		rep == 'V');
    }

    private static void _decodeTypeList(String typeList, int start_pos, StringBuffer appendTo) throws TypeFormatException
    {
	if (appendTo.length() != 0)
	    appendTo.append(' ');

	char c = typeList.charAt(start_pos);
	if (isPrimitive(c))
	    {
		appendTo.append( _decodeType( typeList.substring(start_pos, start_pos + 1) ) );
		++start_pos;
	    }
	else
	    {
		int stop_index;

		if (c == '[')
		    {
			int finger = start_pos + 1;
			while (typeList.charAt(finger) == '[')
			    ++finger;
			if (typeList.charAt(finger) == 'L')
			    {
				++finger;
				while (typeList.charAt( finger ) != ';')
				    ++finger;
			    }
			stop_index = finger;
		    }
		else
		    {
			stop_index = typeList.indexOf(';', start_pos);
			if (stop_index < 0)
			    throw new TypeFormatException(typeList.substring(start_pos) + " is neither a primitive nor semicolon terminated!");
		    }

		appendTo.append( _decodeType( typeList.substring( start_pos, (start_pos = stop_index + 1) ) ) );
	    }
	if (start_pos < typeList.length())
	    {
		appendTo.append(',');
		_decodeTypeList(typeList, start_pos, appendTo);
	    }
    }

    private static StringBuffer _decodeType(String type) throws TypeFormatException
    {
//  	System.err.println("_decodeType: " + type);

	int array_level = 0;
	StringBuffer out;

	char c = type.charAt(0);
	
	switch (c)
	    {
	    case 'Z':
		out = new StringBuffer("boolean");
		break;
	    case 'B':
		out = new StringBuffer("byte");
		break;
	    case 'C':
		out = new StringBuffer("char");
		break;
	    case 'S':
		out = new StringBuffer("short");
		break;
	    case 'I':
		out = new StringBuffer("int");
		break;
	    case 'J':
		out = new StringBuffer("long");
		break;
	    case 'F':
		out = new StringBuffer("float");
		break;
	    case 'D':
		out = new StringBuffer("double");
		break;
	    case 'V':
		out = new StringBuffer("void");
		break;
	    case '[':
		++array_level;
		out = _decodeType(type.substring(1));
		break;
	    case 'L':
		out = _decodeSimpleClassType(type);
		break;
	    default:
		throw new TypeFormatException(type + " is not a valid inernal type name.");
	    }
	for (int i = 0; i < array_level; ++i)
	    out.append("[]");
	return out;
    }

    private static StringBuffer _decodeSimpleClassType(String type) throws TypeFormatException
    {
	int len = type.length();
	if (type.charAt(0) != 'L' || type.charAt( len - 1 ) != ';')
	    throw new TypeFormatException(type + " is not a valid representation of a simple class type.");

	return _dottifySlashesAndDollarSigns( type.substring(1, len - 1) );
    }

    private static StringBuffer _dottifySlashesAndDollarSigns(String s)
    {
	StringBuffer sb = new StringBuffer( s );
	for (int i = 0, len = sb.length(); i < len; ++i)
	    {
		char c = sb.charAt(i);
		if ( c == '/' || c == '$')
		    sb.setCharAt(i, '.');
	    }
	return sb;
    }

    private InternalNameUtils()
    {}

    public static void main(String[] argv)
    {
	try
	    {
		//System.out.println( decodeType( (new String[0]).getClass().getName() ) );
		System.out.println(decodeTypeList(argv[0]));
	    }
	catch (Exception e)
	    { e.printStackTrace(); }
    }

//      public static String dottifySlashes(String name)
//      {
//  	StringBuffer sb = new StringBuffer(name);

//  	int pos = name.indexOf('/', 0);
//  	while (pos > 0)
//  	    {
//  		sb.setCharAt(pos, '.');
//  		ps = name.indexOf('/', ++pos);
//  	    }
//  	return sb.toString();
//      }

}












