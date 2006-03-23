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

import java.util.Comparator;
import java.io.IOException;
import java.lang.reflect.Modifier;
import com.mchange.v1.lang.ClassUtils;
import com.mchange.v2.codegen.CodegenUtils;
import com.mchange.v2.codegen.IndentedWriter;

public final class BeangenUtils
{
    public final static Comparator PROPERTY_COMPARATOR = new Comparator()
    {
	public int compare(Object a, Object b)
	{
	    Property aa = (Property) a;
	    Property bb = (Property) b;

	    return String.CASE_INSENSITIVE_ORDER.compare(aa.getName(), bb.getName() );
	}
     };

    public static String capitalize( String propName )
    {
	char c = propName.charAt( 0 );
	return Character.toUpperCase(c) + propName.substring(1);
    }

//     public static Class[] attemptResolveTypes(ClassInfo info, Property[] props)
//     {
// 	String[] gen = info.getGeneralImports();
// 	String[] spc = info.getSpecificImports();

// 	Class[] out = new Class[ props.length ];
// 	for ( int i = 0, len = props.length; i < len; ++i )
// 	    {
// 		String name = props[i].getSimpleTypeName();
// 		try 
// 		    { out[i] = ClassUtils.forName( name , gen, spc ); }
// 		catch ( Exception e )
// 		    {
// 			e.printStackTrace();
// 			System.err.println("WARNING: " + this.getClass().getName() + " could not resolve " +
// 					   "property type '" + name + "'.");
// 			out[i] = null;
// 		    }
// 	    }
//     }

    public static void writeExplicitDefaultConstructor( int ctor_modifiers, ClassInfo info, IndentedWriter iw) throws IOException
    {
	iw.print( CodegenUtils.getModifierString( ctor_modifiers ) );
	iw.println(' ' + info.getClassName() + "()");
	iw.println("{}");
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

    /**
     * @deprecated use writePropertyVariable
     */
    public static void writePropertyMember( Property prop, IndentedWriter iw ) throws IOException
    { writePropertyVariable( prop, iw ); }

    public static void writePropertyVariable( Property prop, IndentedWriter iw ) throws IOException
    { writePropertyVariable( prop, prop.getDefaultValueExpression(), iw ); }

    /**
     * @deprecated use writePropertyVariable
     */
    public static void writePropertyMember( Property prop, String defaultValueExpression, IndentedWriter iw ) throws IOException
    { writePropertyVariable( prop, defaultValueExpression, iw ); }

    public static void writePropertyVariable( Property prop, String defaultValueExpression, IndentedWriter iw ) throws IOException
    {
	iw.print( CodegenUtils.getModifierString( prop.getVariableModifiers() ) );
	iw.print( ' ' + prop.getSimpleTypeName() + ' ' + prop.getName());
	String dflt = defaultValueExpression;
	if (dflt != null)
	    iw.print( " = " + dflt );
	iw.println(';');
    }

    public static void writePropertyGetter( Property prop, IndentedWriter iw ) throws IOException
    { writePropertyGetter( prop, prop.getDefensiveCopyExpression(), iw ); }

    public static void writePropertyGetter( Property prop, String defensiveCopyExpression, IndentedWriter iw ) throws IOException
    {
	String pfx = ("boolean".equals( prop.getSimpleTypeName() ) ? "is" : "get" );
	iw.print( CodegenUtils.getModifierString( prop.getGetterModifiers() ) );
	iw.println(' ' + prop.getSimpleTypeName() + ' ' + pfx + BeangenUtils.capitalize( prop.getName() ) + "()");
	String retVal = defensiveCopyExpression; 
	if (retVal == null) retVal = prop.getName();
	iw.println("{ return " + retVal + "; }");
    }

    public static void writePropertySetter( Property prop, IndentedWriter iw ) 
	throws IOException
    { writePropertySetter( prop, prop.getDefensiveCopyExpression(), iw ); }

    public static void writePropertySetter( Property prop, String setterDefensiveCopyExpression, IndentedWriter iw ) 
	throws IOException
    {
	String setVal = setterDefensiveCopyExpression;
	if (setVal == null) setVal = prop.getName();
	String usualGetExpression = ("this." + prop.getName());
	String usualSetStatement = ("this." + prop.getName() + " = " + setVal + ';');
	writePropertySetterWithGetExpressionSetStatement(prop, usualGetExpression, usualSetStatement, iw);
    }

    public static void writePropertySetterWithGetExpressionSetStatement( Property prop, String getExpression, String setStatement, IndentedWriter iw ) 
	throws IOException
    {
	iw.print( CodegenUtils.getModifierString( prop.getSetterModifiers() ) );
	iw.print(" void set" + BeangenUtils.capitalize( prop.getName() ) + "( " + prop.getSimpleTypeName() + ' ' + prop.getName() + " )");
	if ( prop.isConstrained() )
	    iw.println(" throws PropertyVetoException");
	else
	    iw.println();
	iw.println('{');
	iw.upIndent();

	if ( changeMarked( prop ) )
	    {
		iw.println( prop.getSimpleTypeName() + " oldVal = " + getExpression + ';');

		String oldValExpr = "oldVal";
		String newValExpr = prop.getName();
		String changeCheck;

		String simpleTypeName = prop.getSimpleTypeName();
		if ( ClassUtils.isPrimitive( simpleTypeName ) )
		    {
			Class propType = ClassUtils.classForPrimitive( simpleTypeName );

			// PropertyChangeSupport already has overloads
			// for boolean and int 
			if (propType == byte.class)
			    {
				oldValExpr  = "new Byte( "+ oldValExpr +" )";
				newValExpr  = "new Byte( "+ newValExpr +" )";
			    }
			else if (propType == char.class)
			    {
				oldValExpr  = "new Character( "+ oldValExpr +" )";
				newValExpr  = "new Character( "+ newValExpr +" )";
			    }
			else if (propType == short.class)
			    {
				oldValExpr  = "new Short( "+ oldValExpr +" )";
				newValExpr  = "new Short( "+ newValExpr +" )";
			    }
			else if (propType == float.class)
			    {
				oldValExpr  = "new Float( "+ oldValExpr +" )";
				newValExpr  = "new Float( "+ newValExpr +" )";
			    }
			else if (propType == double.class)
			    {
				oldValExpr  = "new Double( "+ oldValExpr +" )";
				newValExpr  = "new Double( "+ newValExpr +" )";
			    }

			changeCheck = "oldVal != " + prop.getName();
		    }
		else
		    changeCheck = "! eqOrBothNull( oldVal, " + prop.getName() + " )";
			
		if ( prop.isConstrained() )
		    {
			iw.println("if ( " + changeCheck + " )");
			iw.upIndent();
			iw.println("vcs.fireVetoableChange( \"" + prop.getName() + "\", " + oldValExpr + ", " + newValExpr + " );");
			iw.downIndent();
		    }

		iw.println( setStatement );
				
		if ( prop.isBound() )
		    {
			iw.println("if ( " + changeCheck + " )");
			iw.upIndent();
			iw.println("pcs.firePropertyChange( \"" + prop.getName() + "\", " + oldValExpr + ", " + newValExpr + " );");
			iw.downIndent();
		    }
	    }
	else
	    iw.println( setStatement );

	iw.downIndent();
	iw.println('}');
    }

    private static boolean changeMarked( Property prop )
    { return prop.isBound() || prop.isConstrained(); }

    private BeangenUtils()
    {}
}
