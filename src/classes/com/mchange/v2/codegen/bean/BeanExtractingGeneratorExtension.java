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


package com.mchange.v2.codegen.bean;

import java.util.*;
import java.lang.reflect.Modifier;
import java.io.IOException;
import com.mchange.v2.codegen.CodegenUtils;
import com.mchange.v2.codegen.IndentedWriter;

public class BeanExtractingGeneratorExtension implements GeneratorExtension 
{
    int ctor_modifiers   = Modifier.PUBLIC;
    int method_modifiers = Modifier.PRIVATE;

    public void setConstructorModifiers( int ctor_modifiers )
    { this.ctor_modifiers = ctor_modifiers; }

    public int getConstructorModifiers()
    { return ctor_modifiers; }

    public void setExtractMethodModifiers( int ctor_modifiers )
    { this.method_modifiers = method_modifiers; }

    public int getExtractMethodModifiers()
    { return method_modifiers; }

    public Collection extraGeneralImports()
    { return Collections.EMPTY_SET; }

    public Collection extraSpecificImports()
    {
	Set set = new HashSet();
	set.add("java.beans.BeanInfo");
	set.add("java.beans.PropertyDescriptor");
	set.add("java.beans.Introspector");
	set.add("java.beans.IntrospectionException");
	set.add("java.lang.reflect.InvocationTargetException");
	return set;
    }

    public Collection extraInterfaceNames()
    { return Collections.EMPTY_SET; }

    public void generate(ClassInfo info, Class superclassType, Property[] props, Class[] propTypes, IndentedWriter iw)
	throws IOException
    {
	iw.println("private static Class[] NOARGS = new Class[0];");
	iw.println();
	iw.print( CodegenUtils.getModifierString( method_modifiers ) );
	iw.print(" void extractPropertiesFromBean( Object bean ) throws InvocationTargetException, IllegalAccessException, IntrospectionException");
	iw.println("{");
	iw.upIndent();

	iw.println("BeanInfo bi = Introspector.getBeanInfo( bean.getClass() );");
	iw.println("PropertyDescriptor[] pds = bi.getPropertyDescriptors();");
	iw.println("for (int i = 0, len = pds.length; i < len; ++i)");
	iw.println("{");
	iw.upIndent();

	for (int i = 0, len = props.length; i < len; ++i)
	    {
		iw.println("if (\"" + props[i].getName() + "\".equals( pds[i].getName() ) )");
		iw.upIndent();
		iw.println("this." + props[i].getName() + " = " + extractorExpr( props[i], propTypes[i] ) + ';');
		iw.downIndent();
	    }
	iw.println("}"); //for loop


	iw.downIndent();
	iw.println("}"); //method
	iw.println();
	iw.print( CodegenUtils.getModifierString( ctor_modifiers ) );
	iw.println(' ' + info.getClassName() + "( Object bean ) throws InvocationTargetException, IllegalAccessException, IntrospectionException");
	iw.println("{");
	iw.upIndent();
	iw.println("extractPropertiesFromBean( bean );");
	iw.downIndent();
	iw.println("}");
    }

    private String extractorExpr( Property prop, Class propType )
    {
	if ( propType.isPrimitive() )
	    {
		String castType = BeangenUtils.capitalize( prop.getSimpleTypeName() );
		String valueMethod = prop.getSimpleTypeName() + "Value()";

		if ( propType == char.class)
		    castType = "Character";
		else if ( propType == int.class)
		    castType = "Integer";

		return "((" + castType + ") pds[i].getReadMethod().invoke( bean, NOARGS ))." + valueMethod;
	    }
	else
	    return "(" + prop.getSimpleTypeName() + ") pds[i].getReadMethod().invoke( bean, NOARGS )";
    }
}
