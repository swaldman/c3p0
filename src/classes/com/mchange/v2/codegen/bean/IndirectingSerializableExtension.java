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
import java.io.Serializable;
import java.io.IOException;
import com.mchange.v2.codegen.IndentedWriter;
import com.mchange.v2.ser.IndirectPolicy;

public class IndirectingSerializableExtension extends SerializableExtension
{
    protected String findIndirectorExpr;
    protected String indirectorClassName;

    /**
     * We expect this indirector to be a public class with a public no_arg ctor;
     * If you need the indirector initialized somehow, you'll have to extend
     * the class.
     *
     * @see #writeInitializeIndirector
     * @see #writeExtraDeclarations
     */
    public IndirectingSerializableExtension( String indirectorClassName )
    { 
	this.indirectorClassName = indirectorClassName;
	this.findIndirectorExpr = "new " + indirectorClassName + "()";
    }

    protected IndirectingSerializableExtension()
    {}

    public Collection extraSpecificImports()
    {
	Collection col = super.extraSpecificImports();
	col.add( indirectorClassName );
	col.add( "com.mchange.v2.ser.IndirectlySerialized" );
	col.add( "com.mchange.v2.ser.Indirector" );
	col.add( "com.mchange.v2.ser.SerializableUtils" );
	col.add( "java.io.NotSerializableException" );
	return col;
    }

    protected IndirectPolicy indirectingPolicy( Property prop, Class propType )
    {
	if (Serializable.class.isAssignableFrom( propType ))
	    return IndirectPolicy.DEFINITELY_DIRECT;
	else
	    return IndirectPolicy.INDIRECT_ON_EXCEPTION;
    }

    /**
     * hook method... does nothing by default... override at will.
     * The indirector will be called, uh, "indirector".
     * You are in the middle of a method when you define this.
     */
    protected void writeInitializeIndirector( Property prop, Class propType, IndentedWriter iw ) throws IOException
    {}

    protected void writeExtraDeclarations(ClassInfo info, Class superclassType, Property[] props, Class[] propTypes, IndentedWriter iw)
	throws IOException
    {}

    public void generate(ClassInfo info, Class superclassType, Property[] props, Class[] propTypes, IndentedWriter iw)
	throws IOException
    {
	super.generate( info, superclassType, props, propTypes, iw);
	writeExtraDeclarations( info, superclassType, props, propTypes, iw);
    }

    protected void writeStoreObject( Property prop, Class propType, IndentedWriter iw ) throws IOException
    {
	IndirectPolicy policy = indirectingPolicy( prop, propType );
	if (policy == IndirectPolicy.DEFINITELY_INDIRECT)
	    writeIndirectStoreObject( prop, propType, iw );
	else if (policy == IndirectPolicy.INDIRECT_ON_EXCEPTION)
	    {
		iw.println("try");
		iw.println("{");
		iw.upIndent();
		iw.println("//test serialize");
		iw.println("SerializableUtils.toByteArray(" + prop.getName() + ");");
		super.writeStoreObject( prop, propType, iw );
		iw.downIndent();
		iw.println("}");
		iw.println("catch (NotSerializableException nse)");
		iw.println("{");
		iw.upIndent();
		writeIndirectStoreObject( prop, propType, iw );
		iw.downIndent();
		iw.println("}");
	    }
	else if (policy == IndirectPolicy.DEFINITELY_DIRECT)
	    super.writeStoreObject( prop, propType, iw );
	else
	    throw new InternalError("indirectingPolicy() overridden to return unknown policy: " + policy);
    }

    protected void writeIndirectStoreObject( Property prop, Class propType, IndentedWriter iw ) throws IOException
    {
	iw.println("try");
	iw.println("{");
	iw.upIndent();

	iw.println("Indirector indirector = " + findIndirectorExpr + ';');
	writeInitializeIndirector( prop, propType, iw );
	iw.println("oos.writeObject( indirector.indirectForm( " + prop.getName() + " ) );");

	iw.downIndent();
	iw.println("}");
	iw.println("catch (IOException indirectionIOException)");
	iw.println("{ throw indirectionIOException; }");
	iw.println("catch (Exception indirectionOtherException)");
	iw.println("{ throw new IOException(\"Problem indirectly serializing " + prop.getName() + ": \" + indirectionOtherException.toString() ); }");
    }

    protected void writeUnstoreObject( Property prop, Class propType, IndentedWriter iw ) throws IOException
    {
	IndirectPolicy policy = indirectingPolicy( prop, propType );
	if (policy == IndirectPolicy.DEFINITELY_INDIRECT || policy == IndirectPolicy.INDIRECT_ON_EXCEPTION)
	    {
		iw.println("Object o = ois.readObject();");
		iw.println("if (o instanceof IndirectlySerialized) o = ((IndirectlySerialized) o).getObject();");
		iw.println("this." + prop.getName() + " = (" + prop.getSimpleTypeName() + ") o;");
	    }
	else if (policy == IndirectPolicy.DEFINITELY_DIRECT)
	    super.writeUnstoreObject( prop, propType, iw );
	else
	    throw new InternalError("indirectingPolicy() overridden to return unknown policy: " + policy);
    }

}
