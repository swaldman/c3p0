/*
 * Distributed as part of c3p0 v.0.8.4-test1
 *
 * Copyright (C) 2003 Machinery For Change, Inc.
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


package com.mchange.v2.beangen;

import java.io.IOException;
import java.lang.reflect.Modifier;

public final class BeangenUtils
{
    public static String getModifierString( int modifiers )
    {
	StringBuffer sb = new StringBuffer(32);
	if ( Modifier.isPublic( modifiers ) )
	    sb.append("public ");
	if ( Modifier.isProtected( modifiers ) )
	    sb.append("protected ");
	if ( Modifier.isPrivate( modifiers ) )
	    sb.append("private ");
	if ( Modifier.isAbstract( modifiers ) )
	    sb.append("abstract ");
	if ( Modifier.isStatic( modifiers ) )
	    sb.append("static ");
	if ( Modifier.isFinal( modifiers ) )
	    sb.append("final ");
	if ( Modifier.isSynchronized( modifiers ) )
	    sb.append("synchronized ");
	if ( Modifier.isTransient( modifiers ) )
	    sb.append("transient ");
	if ( Modifier.isVolatile( modifiers ) )
	    sb.append("volatile ");
	if ( Modifier.isStrict( modifiers ) )
	    sb.append("strictfp ");
	if ( Modifier.isNative( modifiers ) )
	    sb.append("native ");
	if ( Modifier.isInterface( modifiers ) ) //????
	    sb.append("interface ");
	return sb.toString().trim();
    }

    public static String capitalize( String propName )
    {
	char c = propName.charAt( 0 );
	return Character.toUpperCase(c) + propName.substring(1);
    }

    public static void writeArgList(Property[] props, boolean declare_types, IndentedWriter iw ) throws IOException
    {
	for (int i = 0, len = props.length; i < len; ++i)
	    {
		if (i != 0)
		    iw.print(", ");
		if (declare_types)
		    iw.print(props[i].getSimpleTypeName() + ' ');
		iw.print( props[i].getName() );
	    }
    }

    private BeangenUtils()
    {}
}
