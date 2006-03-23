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

import java.lang.reflect.Modifier;
import java.io.IOException;
import com.mchange.v2.codegen.*;

public class InnerBeanPropertyBeanGenerator extends SimplePropertyBeanGenerator
{
    String innerBeanClassName;

    int inner_bean_member_modifiers = Modifier.PROTECTED;

    int inner_bean_accessor_modifiers = Modifier.PROTECTED;
    int inner_bean_replacer_modifiers = Modifier.PROTECTED;

    String innerBeanInitializationExpression = null; //if you want it to stay null, set to String "null"

    public void setInnerBeanClassName(String innerBeanClassName)
    { this.innerBeanClassName = innerBeanClassName; }

    public String getInnerBeanClassName()
    { return innerBeanClassName; }

    private String defaultInnerBeanInitializationExpression()
    { return "new " + innerBeanClassName + "()"; }

    private String findInnerBeanClassName()
    { return (innerBeanClassName == null ? "InnerBean" : innerBeanClassName); }

    private String findInnerBeanInitializationExpression()
    { return (innerBeanInitializationExpression == null ? defaultInnerBeanInitializationExpression() : innerBeanInitializationExpression); }

    private int findInnerClassModifiers()
    {
	int out = Modifier.STATIC;
	if (Modifier.isPublic( inner_bean_accessor_modifiers ) || Modifier.isPublic( inner_bean_replacer_modifiers ))
	    out |= Modifier.PUBLIC;
	else if (Modifier.isProtected( inner_bean_accessor_modifiers ) || Modifier.isProtected( inner_bean_replacer_modifiers ))
	    out |= Modifier.PROTECTED;
	else if (Modifier.isPrivate( inner_bean_accessor_modifiers ) && Modifier.isPrivate( inner_bean_replacer_modifiers ))
	    out |= Modifier.PRIVATE;
	//else leave as package accessible
	return out;
    }


    //TODO: add a hook for subclassses to custom define maskedProps
    private void writeSyntheticInnerBeanClass() throws IOException
    {
	int num_props = props.length;
	Property[] maskedProps = new Property[ num_props ];
	for (int i = 0; i < num_props; ++i)
	    {
		maskedProps[i] = new SimplePropertyMask( props[i] )
		    {
			public int getVariableModifiers()
			{ return Modifier.PRIVATE | Modifier.TRANSIENT; }
		    };
	    }

	ClassInfo ci = new WrapperClassInfo( info )
	    {
		public String getClassName()
		{ return "InnerBean"; }
		
		public int getModifiers()
		{ return findInnerClassModifiers(); }
	    };

	createInnerGenerator().generate( ci, maskedProps, iw );
    }

    protected PropertyBeanGenerator createInnerGenerator()
    {
	SimplePropertyBeanGenerator innerGenerator = new SimplePropertyBeanGenerator();
	innerGenerator.setInner( true ); 
	innerGenerator.addExtension( new SerializableExtension() );
	CloneableExtension ce = new CloneableExtension();
	ce.setExceptionSwallowing( true );
	innerGenerator.addExtension( ce );
	return innerGenerator;
    }

    protected void writeOtherVariables() throws IOException
    {
	iw.println(  CodegenUtils.getModifierString( inner_bean_member_modifiers ) + ' ' +
		     findInnerBeanClassName() + " innerBean = " + findInnerBeanInitializationExpression() + ';');
	iw.println();
	iw.println( CodegenUtils.getModifierString( inner_bean_accessor_modifiers ) + ' ' +
		    findInnerBeanClassName() + " accessInnerBean()");
	iw.println("{ return innerBean; }");
    }

    protected void writeOtherFunctions() throws IOException
    {
	iw.print( CodegenUtils.getModifierString( inner_bean_replacer_modifiers ) + ' ' +
		  findInnerBeanClassName() + " replaceInnerBean( " + findInnerBeanClassName() + " innerBean )");
	if (constrainedProperties())
	    iw.println(" throws PropertyVetoException");
	else
	    iw.println();
	iw.println("{");
	iw.upIndent();
	iw.println("beforeReplaceInnerBean();");
	iw.println("this.innerBean = innerBean;");
	iw.println("afterReplaceInnerBean();");
	iw.downIndent();
	iw.println("}");
	iw.println();

	boolean is_abstract = Modifier.isAbstract( info.getModifiers() );
	iw.print("protected ");
	if (is_abstract)
	    iw.print("abstract ");
	iw.print("void beforeReplaceInnerBean()");
	if (constrainedProperties())
	    iw.print(" throws PropertyVetoException");
	if (is_abstract)
	    iw.println(';');
	else
	    iw.println(" {} //hook method for subclasses");
	iw.println();

	iw.print("protected ");
	if (is_abstract)
	    iw.print("abstract ");
	iw.print("void afterReplaceInnerBean()");
	if (is_abstract)
	    iw.println(';');
	else
	    iw.println(" {} //hook method for subclasses");
	iw.println();

	BeangenUtils.writeExplicitDefaultConstructor( Modifier.PUBLIC, info, iw );
	iw.println();
	iw.println("public " + info.getClassName() + "(" + findInnerBeanClassName() + " innerBean)");
	iw.println("{ this.innerBean = innerBean; }");
    }

    protected void writeOtherClasses() throws IOException
    {
	if (innerBeanClassName == null)
	    writeSyntheticInnerBeanClass();
    }

    protected void writePropertyVariable( Property prop ) throws IOException
    { /* do nothing... we have no members, only the inner bean */ }

    protected void writePropertyGetter( Property prop, Class propType ) throws IOException
    { 
	String stn = prop.getSimpleTypeName();
	String pfx = ("boolean".equals( stn )  ? "is" : "get" );
	String methodName = pfx + BeangenUtils.capitalize( prop.getName() );
	iw.print( CodegenUtils.getModifierString( prop.getGetterModifiers() ) );
	iw.println(' ' + prop.getSimpleTypeName() + ' ' + methodName + "()");
	iw.println('{');
	iw.upIndent();
	iw.println( stn + ' ' +  prop.getName() + " = innerBean." + methodName + "();"); 
	String retVal = this.getGetterDefensiveCopyExpression( prop, propType ); 
	if (retVal == null) retVal = prop.getName();
	iw.println("return " + retVal + ';');
	iw.downIndent();
	iw.println('}');
    }

    protected void writePropertySetter( Property prop, Class propType ) throws IOException
    {
	String stn = prop.getSimpleTypeName();
	String pfx = ("boolean".equals( stn )  ? "is" : "get" );

	String setVal = this.getSetterDefensiveCopyExpression( prop, propType );
	if (setVal == null) setVal = prop.getName();
	String getExpression = ("innerBean." + pfx + BeangenUtils.capitalize( prop.getName() ) + "()");
	String setStatement = ("innerBean.set" + BeangenUtils.capitalize( prop.getName() ) + "( " + setVal + " );");
	BeangenUtils.writePropertySetterWithGetExpressionSetStatement(prop, getExpression, setStatement, iw);
    }
}