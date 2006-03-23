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
import com.mchange.v2.log.*;

import java.lang.reflect.Modifier;
import java.io.IOException;
import com.mchange.v2.codegen.CodegenUtils;
import com.mchange.v2.codegen.IndentedWriter;


/**
 * Writes a constructor that takes an explicitly listed subset of a bean's properties
 * for its arguments, and sets these properties initial values appropriately.
 * Skips any specified names for properties that are not found in a bean being generated.
 * Writes nothing if there are none of the property names are properties of the bean.
 */
public class ExplicitPropsConstructorGeneratorExtension implements GeneratorExtension 
{
    final static MLogger logger = MLog.getLogger( ExplicitPropsConstructorGeneratorExtension.class );

    String[] propNames;

    boolean skips_silently = false;

    public ExplicitPropsConstructorGeneratorExtension()
    {}

    public ExplicitPropsConstructorGeneratorExtension(String[] propNames)
    { this.propNames = propNames; }

    public String[] getPropNames()
    { return (String[]) propNames.clone(); }

    public void setPropNames(String[] propNames)
    { this.propNames = (String[]) propNames.clone(); }

    public boolean isSkipsSilently()
    { return skips_silently; }

    public void setsSkipsSilently( boolean skips_silently )
    { this.skips_silently = skips_silently; }

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
	Map propNamesToProps = new HashMap();
	for (int i = 0, len = props.length; i < len; ++i)
	    propNamesToProps.put( props[i].getName(), props[i] );

	List subPropsList = new ArrayList( propNames.length );
	for (int i = 0, len = propNames.length; i < len; ++i)
	    {
		Property p = (Property) propNamesToProps.get( propNames[i] );
		if ( p == null )
		    logger.warning("Could not include property '" + propNames[i] +"' in explicit-props-constructor generated for bean class '" +
				   info.getClassName() +"' because the property is not defined for the bean. Skipping.");
		else
		    subPropsList.add(p);
	    }

	if (subPropsList.size() > 0)
	    {
		Property[] subProps = (Property[]) subPropsList.toArray( new Property[ subPropsList.size() ] );

		iw.print( CodegenUtils.getModifierString( ctor_modifiers ) );
		iw.print( info.getClassName() + "( ");
		BeangenUtils.writeArgList(subProps, true, iw);
		iw.println(" )");
		iw.println("{");
		iw.upIndent();
		
		for (int i = 0, len = subProps.length; i < len; ++i)
		    {
			iw.print("this." + subProps[i].getName() + " = ");
			String setExp = subProps[i].getDefensiveCopyExpression();
			if (setExp == null)
			    setExp = subProps[i].getName();
			iw.println(setExp + ';');
		    }
		
		iw.downIndent();
		iw.println("}");
	    }
    }
}
