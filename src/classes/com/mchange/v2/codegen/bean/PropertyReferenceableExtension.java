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
import com.mchange.v2.codegen.IndentedWriter;
import com.mchange.v2.naming.JavaBeanObjectFactory;
import com.mchange.v2.naming.JavaBeanReferenceMaker;

import java.io.IOException;

public class PropertyReferenceableExtension implements GeneratorExtension
{
    boolean explicit_reference_properties = false;

    String factoryClassName = JavaBeanObjectFactory.class.getName();

    String javaBeanReferenceMakerClassName = JavaBeanReferenceMaker.class.getName();

    public void setUseExplicitReferenceProperties( boolean explicit_reference_properties )
    { this.explicit_reference_properties = explicit_reference_properties; }

    public boolean getUseExplicitReferenceProperties()
    { return explicit_reference_properties; }

    public void setFactoryClassName( String factoryClassName )
    { this.factoryClassName = factoryClassName; }

    public String getFactoryClassName()
    { return factoryClassName; }

//     public void setJavaBeanReferenceMakerClassName( String javaBeanReferenceMakerClassName )
//     { this.javaBeanReferenceMakerClassName = javaBeanReferenceMakerClassName; }

//     public String getJavaBeanReferenceMakerClassName()
//     { return javaBeanReferenceMakerClassName; }

    public Collection extraGeneralImports()
    { 
	Set set = new HashSet();
	return set;
    }

    public Collection extraSpecificImports()
    {
	Set set = new HashSet();
	set.add( "javax.naming.Reference" );
	set.add( "javax.naming.Referenceable" );
	set.add( "javax.naming.NamingException" );
	set.add( "com.mchange.v2.naming.JavaBeanObjectFactory" );
	set.add( "com.mchange.v2.naming.JavaBeanReferenceMaker" );
	set.add( "com.mchange.v2.naming.ReferenceMaker" );
	return set;
    }

    public Collection extraInterfaceNames()
    {
	Set set = new HashSet();
	set.add( "Referenceable" );
	return set;
    }

    public void generate(ClassInfo info, Class superclassType, Property[] props, Class[] propTypes, IndentedWriter iw)
	throws IOException
    {
	iw.println("final static JavaBeanReferenceMaker referenceMaker = new " + javaBeanReferenceMakerClassName + "();");
	iw.println();
	iw.println("static"); 
	iw.println("{"); 
	iw.upIndent();
	
	iw.println("referenceMaker.setFactoryClassName( \"" + factoryClassName + "\" );");
	if ( explicit_reference_properties )
	    {
		for( int i = 0, len = props.length; i < len; ++i)
		    iw.println("referenceMaker.addReferenceProperty(\"" + props[i].getName() + "\");");
	    }

	iw.downIndent();
	iw.println("}");
	iw.println();
	iw.println("public Reference getReference() throws NamingException");
	iw.println("{"); 
	iw.upIndent();
	
	iw.println("return referenceMaker.createReference( this );");

	iw.downIndent();
	iw.println("}");
    }
}
