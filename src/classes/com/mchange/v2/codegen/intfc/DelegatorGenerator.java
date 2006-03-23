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


package com.mchange.v2.codegen.intfc;

import java.io.*;
import java.util.*;
import java.lang.reflect.*;
import com.mchange.v2.codegen.*;
import com.mchange.v1.lang.ClassUtils;

public class DelegatorGenerator
{
    int class_modifiers         = Modifier.PUBLIC | Modifier.ABSTRACT;
    int method_modifiers        = Modifier.PUBLIC;
    int wrapping_ctor_modifiers = Modifier.PUBLIC;
    int default_ctor_modifiers  = Modifier.PUBLIC;
    boolean wrapping_constructor = true;
    boolean default_constructor  = true;
    boolean inner_getter         = true;
    boolean inner_setter         = true;

    Class   superclass           = null;
    Class[] extraInterfaces      = null;

    final static Comparator classComp = new Comparator()
    {
       public int compare(Object a, Object b)
       { return ((Class) a).getName().compareTo(((Class) b).getName()); }
    };

    public void setGenerateInnerSetter( boolean b )
    { this.inner_setter = b; }

    public boolean isGenerateInnerSetter()
    { return inner_setter; }

    public void setGenerateInnerGetter( boolean b )
    { this.inner_getter = b; }

    public boolean isGenerateInnerGetter()
    { return inner_getter; }

    public void setGenerateNoArgConstructor( boolean b )
    { this.default_constructor = b; }

    public boolean isGenerateNoArgConstructor()
    { return default_constructor; }

    public void setGenerateWrappingConstructor( boolean b )
    { this.wrapping_constructor = b; }

    public boolean isGenerateWrappingConstructor()
    { return wrapping_constructor; }

    public void setWrappingConstructorModifiers( int modifiers )
    { this.wrapping_ctor_modifiers = modifiers; }

    public int getWrappingConstructorModifiers()
    { return wrapping_ctor_modifiers; }

    public void setNoArgConstructorModifiers( int modifiers )
    { this.default_ctor_modifiers = modifiers; }

    public int getNoArgConstructorModifiers()
    { return default_ctor_modifiers; }

    public void setMethodModifiers( int modifiers )
    { this.method_modifiers = modifiers; }

    public int getMethodModifiers()
    { return method_modifiers; }

    public void setClassModifiers( int modifiers )
    { this.class_modifiers = modifiers; }

    public int getClassModifiers()
    { return class_modifiers; }

    public void setSuperclass( Class superclass )
    { this.superclass = superclass; }

    public Class getSuperclass()
    { return superclass; }

    public void setExtraInterfaces( Class[] extraInterfaces )
    { this.extraInterfaces = extraInterfaces; }

    public Class[] getExtraInterfaces()
    { return extraInterfaces; }

    public void writeDelegator(Class intfcl, String genclass, Writer w) throws IOException
    {
	IndentedWriter iw = CodegenUtils.toIndentedWriter(w);
	
	String   pkg      = genclass.substring(0, genclass.lastIndexOf('.'));
	String   sgc      = CodegenUtils.fqcnLastElement( genclass );
	String   scn      = (superclass != null ? ClassUtils.simpleClassName( superclass ) : null);
	String   sin      = ClassUtils.simpleClassName( intfcl );
	String[] eins     = null;
	if (extraInterfaces != null)
	    {
		eins = new String[ extraInterfaces.length ];
		for (int i = 0, len = extraInterfaces.length; i < len; ++i)
		    eins[i] = ClassUtils.simpleClassName( extraInterfaces[i] );
	    }

	Set    imports  = new TreeSet( classComp );
	
	Method[] methods = intfcl.getMethods();
	
	//TODO: don't add array classes!
	//build import set
	if (! CodegenUtils.inSamePackage( intfcl.getName(), genclass ) )
	    imports.add( intfcl );
	if (superclass != null && ! CodegenUtils.inSamePackage( superclass.getName(), genclass ) )
	    imports.add( superclass );
	if (extraInterfaces != null)
	    {
		for (int i = 0, len = extraInterfaces.length; i < len; ++i)
		    {
			Class checkMe = extraInterfaces[i];
			if (! CodegenUtils.inSamePackage( checkMe.getName(), genclass ) )
			    imports.add( checkMe );
		    }
	    }
	for (int i = 0, len = methods.length; i < len; ++i)
	    {
		Class[] args = methods[i].getParameterTypes();
		for (int j = 0, jlen = args.length; j < jlen; ++j)
		    {
			if (! CodegenUtils.inSamePackage( args[j].getName(), genclass ) )
			    imports.add( CodegenUtils.unarrayClass( args[j] ) );
		    }       
		Class[] excClasses = methods[i].getExceptionTypes();
		for (int j = 0, jlen = excClasses.length; j < jlen; ++j)
		    {
			if (! CodegenUtils.inSamePackage( excClasses[j].getName(), genclass ) )
			    {
				//System.err.println("Adding exception type: " + excClasses[j]);
				imports.add( CodegenUtils.unarrayClass( excClasses[j] ) );
			    }
		    }       
		if (! CodegenUtils.inSamePackage( methods[i].getReturnType().getName(), genclass ) )
		    imports.add( CodegenUtils.unarrayClass( methods[i].getReturnType() ) );
	    }
	generateBannerComment( iw );
	iw.println("package " + pkg + ';');
	iw.println();
	for (Iterator ii = imports.iterator(); ii.hasNext(); )
	    iw.println("import "+ ((Class) ii.next()).getName() + ';');
	generateExtraImports( iw );
	iw.println();
	iw.print(CodegenUtils.getModifierString( class_modifiers ) + " class " + sgc);
	if (superclass != null)
	    iw.print(" extends " + scn);
	iw.print(" implements " + sin);
	if (eins != null)
	    for (int i = 0, len = eins.length; i < len; ++i)
		iw.print(", " + eins[i]);
	iw.println();
	iw.println("{");
	iw.upIndent();

	iw.println("protected " + sin + " inner;");
	iw.println();

	if ( wrapping_constructor )
	    {
		iw.println("public" + ' ' + sgc + '(' + sin + " inner)");
		iw.println("{ this.inner = inner; }");
	    }

	if (default_constructor)
	    {
		iw.println();
		iw.println("public" + ' ' + sgc + "()");
		iw.println("{}");
	    }

	if (inner_setter)
	    {
		iw.println();
		iw.println( CodegenUtils.getModifierString( method_modifiers ) + " void setInner( " + sin + " inner )");
		iw.println( "{ this.inner = inner; }" );
	    }
	if (inner_getter)
	    {
		iw.println();
		iw.println( CodegenUtils.getModifierString( method_modifiers ) + ' ' + sin + " getInner()");
		iw.println( "{ return inner; }" );
	    }
	iw.println();
	for (int i = 0, len = methods.length; i < len; ++i)
	    {
		Method method  = methods[i];
		Class  retType = method.getReturnType();

		if (i != 0) iw.println();
		iw.println( CodegenUtils.methodSignature( method_modifiers, method, null ) );
		iw.println("{");
		iw.upIndent();

		generatePreDelegateCode( intfcl, genclass, method, iw );
		generateDelegateCode( intfcl, genclass, method, iw );
		generatePostDelegateCode( intfcl, genclass, method, iw );
	    
		iw.downIndent();
		iw.println("}");
	    }

	iw.println();
	generateExtraDeclarations( intfcl, genclass, iw );

	iw.downIndent();
    	iw.println("}");
    }

    protected void generateDelegateCode( Class intfcl, String genclass, Method method, IndentedWriter iw ) throws IOException 
    {
	Class  retType = method.getReturnType();
	
	iw.println( (retType == void.class ? "" : "return " ) + "inner." + CodegenUtils.methodCall( method ) + ";" );
    }

    protected void generateBannerComment( IndentedWriter iw ) throws IOException 
    {
	iw.println("/*");
	iw.println(" * This class generated by " + this.getClass().getName());
	iw.println(" * " + new Date());
	iw.println(" * DO NOT HAND EDIT!!!!");
	iw.println(" */");
    }

    protected void generateExtraImports( IndentedWriter iw ) throws IOException {}
    protected void generatePreDelegateCode( Class intfcl, String genclass, Method method, IndentedWriter iw ) throws IOException {}
    protected void generatePostDelegateCode( Class intfcl, String genclass, Method method, IndentedWriter iw ) throws IOException {}
    protected void generateExtraDeclarations( Class intfcl, String genclass, IndentedWriter iw ) throws IOException {}
}
