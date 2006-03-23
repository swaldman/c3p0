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


package com.mchange.v2.codegen;

import java.lang.reflect.*;
import java.io.File;
import java.io.Writer;
import com.mchange.v1.lang.ClassUtils;

public final class CodegenUtils
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

    public static Class unarrayClass( Class cl )
    {
	Class out = cl;
	while ( out.isArray() )
	    out = out.getComponentType();
	return out;
    }

    public static boolean inSamePackage(String cn1, String cn2)
    {
       int pkgdot = cn1.lastIndexOf('.');
       int pkgdot2 = cn2.lastIndexOf('.');

       //always return true of one class is a primitive or unpackages
       if (pkgdot < 0 || pkgdot2 < 0)
           return true;
       if ( cn1.substring(0, pkgdot).equals(cn1.substring(0, pkgdot)) )
       {
          if (cn2.indexOf('.') >= 0)
            return false;
          else
            return true;
       }
       else
         return false;
    }

    /**
     * @return fully qualified class name last element
     */
    public static String fqcnLastElement(String fqcn)
    { return ClassUtils.fqcnLastElement( fqcn ); }

    public static String methodSignature( Method m )
    { return methodSignature( m, null ); }

    public static String methodSignature( Method m, String[] argNames )
    { return methodSignature( Modifier.PUBLIC, m, argNames ); }

    public static String methodSignature( int modifiers, Method m, String[] argNames )
    {
	StringBuffer sb = new StringBuffer(256);
        sb.append(getModifierString(modifiers));
	sb.append(' ');
	sb.append( ClassUtils.simpleClassName( m.getReturnType() ) );
	sb.append(' ');
	sb.append( m.getName() );
	sb.append('(');
        Class[] cls = m.getParameterTypes();
        for(int i = 0, len = cls.length; i < len; ++i)
        {
           if (i != 0)
             sb.append(", ");
           sb.append( ClassUtils.simpleClassName( cls[i] ) );
	   sb.append(' ');
           sb.append( argNames == null ? String.valueOf((char) ('a' + i)) : argNames[i] );
        }
        sb.append(')');
	Class[] excClasses = m.getExceptionTypes();
	if (excClasses.length > 0)
        {
           sb.append(" throws ");
           for (int i = 0, len = excClasses.length; i < len; ++i)
           {
             if (i != 0)
               sb.append(", ");
             sb.append( ClassUtils.simpleClassName( excClasses[i] ) );
           }   
        }
        return sb.toString();
    }

    public static String methodCall( Method m )
    { return methodCall( m, null ); }

    public static String methodCall( Method m, String[] argNames )
    {
       StringBuffer sb = new StringBuffer(256);
       sb.append( m.getName() );
       sb.append('(');
        Class[] cls = m.getParameterTypes();
        for(int i = 0, len = cls.length; i < len; ++i)
        {
           if (i != 0)
             sb.append(", ");
           sb.append( argNames == null ? generatedArgumentName( i ) : argNames[i] );
        }
        sb.append(')');
	return sb.toString();
    } 

    public static String generatedArgumentName( int index )
    { return String.valueOf((char) ('a' + index)); }

    public static String simpleClassName( Class cl )
    { return ClassUtils.simpleClassName( cl ); }

    public static IndentedWriter toIndentedWriter( Writer w )
    { return (w instanceof IndentedWriter ? (IndentedWriter) w : new IndentedWriter(w)); }

    public static String packageNameToFileSystemDirPath(String packageName)
    {
	StringBuffer sb = new StringBuffer( packageName );
	for (int i = 0, len = sb.length(); i < len; ++i)
	    if ( sb.charAt(i) == '.' )
		sb.setCharAt(i, File.separatorChar);
	sb.append( File.separatorChar );
	return sb.toString();
    }

    private CodegenUtils()
    {}
}
