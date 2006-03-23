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

public class SimpleStateBeanImportExportGeneratorExtension implements GeneratorExtension 
{
    int ctor_modifiers = Modifier.PUBLIC;

    public Collection extraGeneralImports()
    { return Collections.EMPTY_SET; }

    public Collection extraSpecificImports()
    { return Collections.EMPTY_SET; }

    public Collection extraInterfaceNames()
    { return Collections.EMPTY_SET; }

    static class SimplePropertyMask implements Property
    {
	Property p;

	SimplePropertyMask(Property p)
	{ this.p = p; }

	public int getVariableModifiers()
	{ return Modifier.PRIVATE; }

	public String  getName()
	{ return p.getName(); }

	public String  getSimpleTypeName()
	{ return p.getSimpleTypeName(); }

	public String getDefensiveCopyExpression()
	{ return null; }

	public String getDefaultValueExpression()
	{ return null; }

	public int getGetterModifiers()
	{ return Modifier.PUBLIC; }

	public int getSetterModifiers()
	{ return Modifier.PUBLIC; }

	public boolean isReadOnly()
	{ return false; }

	public boolean isBound()
	{ return false; }

	public boolean isConstrained()
	{ return false; }
    }

    public void generate(ClassInfo info, Class superclassType, Property[] props, Class[] propTypes, IndentedWriter iw)
	throws IOException
    {
	int num_props = props.length;
	Property[] masked = new Property[ num_props ];
	for (int i = 0; i < num_props; ++i)
	    masked[i] = new SimplePropertyMask( props[i] );

	iw.println("protected static class SimpleStateBean implements ExportedState");
	iw.println("{");
	iw.upIndent();

	for (int i = 0; i < num_props; ++i)
	    {
		masked[i] = new SimplePropertyMask( props[i] );
		BeangenUtils.writePropertyMember( masked[i], iw );
		iw.println();
		BeangenUtils.writePropertyGetter( masked[i], iw );
		iw.println();
		BeangenUtils.writePropertySetter( masked[i], iw );
	    }

	iw.downIndent();
	iw.println("}");
    }
}
