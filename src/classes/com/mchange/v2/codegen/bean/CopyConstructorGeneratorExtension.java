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

public class CopyConstructorGeneratorExtension implements GeneratorExtension 
{
    int ctor_modifiers = Modifier.PUBLIC;

    public Collection extraGeneralImports()
    { return Collections.EMPTY_SET; }

    public Collection extraSpecificImports()
    { return Collections.EMPTY_SET; }

    public Collection extraInterfaceNames()
    { return Collections.EMPTY_SET; }

    public void generate(ClassInfo info, Class superclassType, Property[] props, Class[] propTypes, IndentedWriter iw)
	throws IOException
    {
	iw.print( CodegenUtils.getModifierString( ctor_modifiers ) );
	iw.print(" " + info.getClassName() + "( ");
	iw.print( info.getClassName() + " copyMe" );
	iw.println(" )");
	iw.println("{");
	iw.upIndent();

	for (int i = 0, len = props.length; i < len; ++i)
	    {
		String propGetterMethodCall;
		if (propTypes[i] == boolean.class)
		    propGetterMethodCall = "is" + BeangenUtils.capitalize( props[i].getName() ) + "()";
		else
		    propGetterMethodCall = "get" + BeangenUtils.capitalize( props[i].getName() ) + "()";
		iw.println(props[i].getSimpleTypeName() + ' ' + props[i].getName() + " = copyMe." + propGetterMethodCall + ';');
		iw.print("this." + props[i].getName() + " = ");
		String setExp = props[i].getDefensiveCopyExpression();
		if (setExp == null)
		    setExp = props[i].getName();
		iw.println(setExp + ';');
	    }

	iw.downIndent();
	iw.println("}");
    }
}
