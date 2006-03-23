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

import java.io.*;
import java.util.*;
import com.mchange.v2.log.*;
import java.lang.reflect.Modifier;
import com.mchange.v1.lang.ClassUtils;
import com.mchange.v2.codegen.CodegenUtils;
import com.mchange.v2.codegen.IndentedWriter;

public class SimplePropertyBeanGenerator implements PropertyBeanGenerator
{
    private final static MLogger logger = MLog.getLogger( SimplePropertyBeanGenerator.class );

    private boolean inner              = false;
    private int     java_version       = 130; //1.3.0
    private boolean force_unmodifiable = false;
    private String  generatorName      = this.getClass().getName();

    // helper vars for generate method
    protected ClassInfo      info;
    protected Property[]     props;
    protected IndentedWriter iw;

    protected Set generalImports;
    protected Set specificImports;
    protected Set interfaceNames;

    protected Class   superclassType;
    protected List    interfaceTypes;
    protected Class[] propertyTypes;

    protected List generatorExtensions = new ArrayList();

    public synchronized void setInner( boolean inner )
    { this.inner = inner; }

    public synchronized boolean isInner()
    { return inner; }

    /**
     * @param version a three digit number -- for example Java 1.3.1 is 131
     */
    public synchronized void setJavaVersion(int version) 
    { this.java_version = java_version; }

    public synchronized int getJavaVersion()
    { return java_version; }

    public synchronized void setGeneratorName(String generatorName) 
    { this.generatorName = generatorName; }

    public synchronized String getGeneratorName()
    { return generatorName; }

    public synchronized void setForceUnmodifiable(boolean force_unmodifiable)
    { this.force_unmodifiable = force_unmodifiable; }

    public synchronized boolean isForceUnmodifiable()
    { return force_unmodifiable; }

    public synchronized void addExtension( GeneratorExtension ext )
    { generatorExtensions.add( ext ); }

    public synchronized void removeExtension( GeneratorExtension ext )
    { generatorExtensions.remove( ext ); }

    public synchronized void generate( ClassInfo info, Property[] props, Writer w) throws IOException
    {
	this.info = info;
	this.props = props;
	Arrays.sort( props, BeangenUtils.PROPERTY_COMPARATOR );
	this.iw = ( w instanceof IndentedWriter ? (IndentedWriter) w : new IndentedWriter(w));

	this.generalImports = new TreeSet();
	if ( info.getGeneralImports() != null )
	    generalImports.addAll( Arrays.asList( info.getGeneralImports() ) );

	this.specificImports = new TreeSet();
	if ( info.getSpecificImports() != null )
	    specificImports.addAll( Arrays.asList( info.getSpecificImports() ) );

	this.interfaceNames = new TreeSet();
	if ( info.getInterfaceNames() != null )
	    interfaceNames.addAll( Arrays.asList( info.getInterfaceNames() ) );

	addInternalImports();
	addInternalInterfaces();

	resolveTypes();

	if (! inner )
	    {
		writeHeader();
		iw.println();
	    }

	writeClassDeclaration();
	iw.println('{');
	iw.upIndent();

	writeCoreBody();

	iw.downIndent();
	iw.println('}');
    }

    protected void resolveTypes()
    {
	String[] gen = (String[]) generalImports.toArray( new String[ generalImports.size() ] );
	String[] spc = (String[]) specificImports.toArray( new String[ specificImports.size() ] );

	if ( info.getSuperclassName() != null )
	    {
		try
		    { superclassType = ClassUtils.forName( info.getSuperclassName(), gen, spc ); }
		catch ( Exception e )
		    {
// 			System.err.println("WARNING: " + this.getClass().getName() + " could not resolve " +
// 					   "superclass '" + info.getSuperclassName() + "'.");
			if ( logger.isLoggable( MLevel.WARNING ) )
			    logger.warning(this.getClass().getName() + " could not resolve superclass '" + info.getSuperclassName() + "'.");

			superclassType = null;
		    }
	    }

	interfaceTypes = new ArrayList( interfaceNames.size() );
	for ( Iterator ii = interfaceNames.iterator(); ii.hasNext(); )
	    {
		String name = (String) ii.next();
		try 
		    { interfaceTypes.add( ClassUtils.forName( name , gen, spc ) ); }
		catch ( Exception e )
		    {
// 			System.err.println("WARNING: " + this.getClass().getName() + " could not resolve " +
// 					   "interface '" + name + "'.");

			if ( logger.isLoggable( MLevel.WARNING ) )
			    logger.warning(this.getClass().getName() + " could not resolve interface '" + name + "'.");

			interfaceTypes.add( null );
		    }
	    }

	propertyTypes = new Class[ props.length ];
	for ( int i = 0, len = props.length; i < len; ++i )
	    {
		String name = props[i].getSimpleTypeName();
		try 
		    { propertyTypes[i] = ClassUtils.forName( name , gen, spc ); }
		catch ( Exception e )
		    {
// 			e.printStackTrace();
// 			System.err.println("WARNING: " + this.getClass().getName() + " could not resolve " +
// 					   "property type '" + name + "'.");

			if ( logger.isLoggable( MLevel.WARNING ) )
			    logger.log( MLevel.WARNING, this.getClass().getName() + " could not resolve property type '" + name + "'.", e);

			propertyTypes[i] = null;
		    }
	    }
    }

    protected void addInternalImports()
    {
	if (boundProperties())
	    {
		specificImports.add("java.beans.PropertyChangeEvent");
		specificImports.add("java.beans.PropertyChangeSupport");
		specificImports.add("java.beans.PropertyChangeListener");
	    }
	if (constrainedProperties())
	    {
		specificImports.add("java.beans.PropertyChangeEvent");
		specificImports.add("java.beans.PropertyVetoException");
		specificImports.add("java.beans.VetoableChangeSupport");
		specificImports.add("java.beans.VetoableChangeListener");
	    }

	for (Iterator ii = generatorExtensions.iterator(); ii.hasNext(); )
	    {
		GeneratorExtension ge = (GeneratorExtension) ii.next();
		specificImports.addAll( ge.extraSpecificImports() );
		generalImports.addAll( ge.extraGeneralImports() );
	    }
    }

    protected void addInternalInterfaces()
    {
	for (Iterator ii = generatorExtensions.iterator(); ii.hasNext(); )
	    {
		GeneratorExtension ge = (GeneratorExtension) ii.next();
		interfaceNames.addAll( ge.extraInterfaceNames() );
	    }
    }

    protected void writeCoreBody() throws IOException
    {
	writeJavaBeansChangeSupport();
	writePropertyVariables();
	writeOtherVariables();
	iw.println();

	writeGetterSetterPairs();
	if ( boundProperties() )
	    {
		iw.println();
		writeBoundPropertyEventSourceMethods();
	    }
	if ( constrainedProperties() )
	    {
		iw.println();
		writeConstrainedPropertyEventSourceMethods();
	    }
	writeInternalUtilityFunctions();
	writeOtherFunctions();

	writeOtherClasses();

	String[] completed_intfc_names = (String[]) interfaceNames.toArray( new String[ interfaceNames.size() ] );
	String[] completed_gen_imports = (String[]) generalImports.toArray( new String[ generalImports.size() ] );
	String[] completed_spc_imports = (String[]) specificImports.toArray( new String[ specificImports.size() ] );
	ClassInfo completedClassInfo = new SimpleClassInfo( info.getPackageName(),
							    info.getModifiers(),
							    info.getClassName(),
							    info.getSuperclassName(),
							    completed_intfc_names,
							    completed_gen_imports,
							    completed_spc_imports );
	for (Iterator ii = generatorExtensions.iterator(); ii.hasNext(); )
	    {
		GeneratorExtension ext = (GeneratorExtension) ii.next();
		iw.println();
		ext.generate( completedClassInfo, superclassType, props, propertyTypes, iw );
	    }
    }

    protected void writeInternalUtilityFunctions() throws IOException
    {
	iw.println("private boolean eqOrBothNull( Object a, Object b )");
	iw.println("{");
	iw.upIndent();

	iw.println("return");
	iw.upIndent();
	iw.println("a == b ||");
	iw.println("(a != null && a.equals(b));");
	iw.downIndent();

	iw.downIndent();
	iw.println("}");
    }

    protected void writeConstrainedPropertyEventSourceMethods() throws IOException
    {
	iw.println("public void addVetoableChangeListener( VetoableChangeListener vcl )");
	iw.println("{ vcs.addVetoableChangeListener( vcl ); }");
	iw.println();
	
	iw.println("public void removeVetoableChangeListener( VetoableChangeListener vcl )");
	iw.println("{ vcs.removeVetoableChangeListener( vcl ); }");
	iw.println();

	if (java_version >= 140)
	    {
		iw.println("public VetoableChangeListener[] getVetoableChangeListeners()");
		iw.println("{ return vcs.getPropertyChangeListeners(); }");
	    }
    }

    protected void writeBoundPropertyEventSourceMethods() throws IOException
    {
	iw.println("public void addPropertyChangeListener( PropertyChangeListener pcl )");
	iw.println("{ pcs.addPropertyChangeListener( pcl ); }");
	iw.println();
	
	iw.println("public void addPropertyChangeListener( String propName, PropertyChangeListener pcl )");
	iw.println("{ pcs.addPropertyChangeListener( propName, pcl ); }");
	iw.println();
	
	iw.println("public void removePropertyChangeListener( PropertyChangeListener pcl )");
	iw.println("{ pcs.removePropertyChangeListener( pcl ); }");
	iw.println();

	iw.println("public void removePropertyChangeListener( String propName, PropertyChangeListener pcl )");
	iw.println("{ pcs.removePropertyChangeListener( propName, pcl ); }");
	iw.println();

	if (java_version >= 140)
	    {
		iw.println("public PropertyChangeListener[] getPropertyChangeListeners()");
		iw.println("{ return pcs.getPropertyChangeListeners(); }");
	    }
    }

    protected void writeJavaBeansChangeSupport() throws IOException
    {
	if ( boundProperties() )
	    {
		iw.println("protected PropertyChangeSupport pcs = new PropertyChangeSupport( this );");
		iw.println();
		iw.println("protected PropertyChangeSupport getPropertyChangeSupport()");
		iw.println("{ return pcs; }");
		
	    }
	if ( constrainedProperties() )
	    {
		iw.println("protected VetoableChangeSupport vcs = new VetoableChangeSupport( this );");
		iw.println();
		iw.println("protected VetoableChangeSupport getVetoableChangeSupport()");
		iw.println("{ return vcs; }");
	    }
    }

    protected void writeOtherVariables() throws IOException //hook method for subclasses
    {}

    protected void writeOtherFunctions() throws IOException //hook method for subclasses
    {}

    protected void writeOtherClasses() throws IOException //hook method for subclasses
    {}

    protected void writePropertyVariables() throws IOException
    {
	for (int i = 0, len = props.length; i < len; ++i)
	    writePropertyVariable( props[i] );
    }

    protected void writePropertyVariable( Property prop ) throws IOException
    {
	BeangenUtils.writePropertyVariable( prop, iw );
// 	iw.print( CodegenUtils.getModifierString( prop.getVariableModifiers() ) );
// 	iw.print( ' ' + prop.getSimpleTypeName() + ' ' + prop.getName());
// 	String dflt = prop.getDefaultValueExpression();
// 	if (dflt != null)
// 	    iw.print( " = " + dflt );
// 	iw.println(';');
    }

    /**
     * @deprecated
     */
    protected void writePropertyMembers() throws IOException 
    { throw new InternalError("writePropertyMembers() deprecated and removed. please us writePropertyVariables()."); }

    /**
     * @deprecated
     */
    protected void writePropertyMember( Property prop ) throws IOException
    { throw new InternalError("writePropertyMember() deprecated and removed. please us writePropertyVariable()."); }

    protected void writeGetterSetterPairs() throws IOException
    {
	for (int i = 0, len = props.length; i < len; ++i)
	    {
		writeGetterSetterPair( props[i], propertyTypes[i] );
		if ( i != len - 1) iw.println();
	    }
    }

    protected void writeGetterSetterPair( Property prop, Class propType ) throws IOException
    {
	writePropertyGetter( prop, propType );
	
	if (! prop.isReadOnly() && ! force_unmodifiable)
	    {
		iw.println();
		writePropertySetter( prop, propType );
	    }
    }

    protected void writePropertyGetter( Property prop, Class propType ) throws IOException
    { 
	BeangenUtils.writePropertyGetter( prop, this.getGetterDefensiveCopyExpression( prop, propType ), iw );

// 	String pfx = ("boolean".equals( prop.getSimpleTypeName() ) ? "is" : "get" );
// 	iw.print( CodegenUtils.getModifierString( prop.getGetterModifiers() ) );
// 	iw.println(' ' + prop.getSimpleTypeName() + ' ' + pfx + BeangenUtils.capitalize( prop.getName() ) + "()");
// 	String retVal = getGetterDefensiveCopyExpression( prop, propType );
// 	if (retVal == null) retVal = prop.getName();
// 	iw.println("{ return " + retVal + "; }");
    }


//     boolean changeMarked( Property prop )
//     { return prop.isBound() || prop.isConstrained(); }

    protected void writePropertySetter( Property prop, Class propType ) throws IOException
    {
	BeangenUtils.writePropertySetter( prop, this.getSetterDefensiveCopyExpression( prop, propType ), iw );

// 	iw.print( CodegenUtils.getModifierString( prop.getSetterModifiers() ) );
// 	iw.print(" void set" + BeangenUtils.capitalize( prop.getName() ) + "( " + prop.getSimpleTypeName() + ' ' + prop.getName() + " )");
// 	if ( prop.isConstrained() )
// 	    iw.println(" throws PropertyVetoException");
// 	else
// 	    iw.println();
// 	String setVal = getSetterDefensiveCopyExpression( prop, propType );
// 	if (setVal == null) setVal = prop.getName();
// 	iw.println('{');
// 	iw.upIndent();


// 	if ( changeMarked( prop ) )
// 	    {
// 		iw.println( prop.getSimpleTypeName() + " oldVal = this." + prop.getName() + ';');

// 		String oldValExpr = "oldVal";
// 		String newValExpr = prop.getName();
// 		String changeCheck;
// 		if ( propType != null && propType.isPrimitive() ) //sometimes the type can't be resolved. if so, it ain't primitive.
// 		    {
// 			// PropertyChange support already has overrides
// 			// for boolean and int 
// 			if (propType == byte.class)
// 			    {
// 				oldValExpr  = "new Byte( "+ oldValExpr +" )";
// 				newValExpr  = "new Byte( "+ newValExpr +" )";
// 			    }
// 			else if (propType == char.class)
// 			    {
// 				oldValExpr  = "new Character( "+ oldValExpr +" )";
// 				newValExpr  = "new Character( "+ newValExpr +" )";
// 			    }
// 			else if (propType == short.class)
// 			    {
// 				oldValExpr  = "new Short( "+ oldValExpr +" )";
// 				newValExpr  = "new Short( "+ newValExpr +" )";
// 			    }
// 			else if (propType == float.class)
// 			    {
// 				oldValExpr  = "new Float( "+ oldValExpr +" )";
// 				newValExpr  = "new Float( "+ newValExpr +" )";
// 			    }
// 			else if (propType == double.class)
// 			    {
// 				oldValExpr  = "new Double( "+ oldValExpr +" )";
// 				newValExpr  = "new Double( "+ newValExpr +" )";
// 			    }

// 			changeCheck = "oldVal != " + prop.getName();
// 		    }
// 		else
// 		    changeCheck = "! eqOrBothNull( oldVal, " + prop.getName() + " )";
			
// 		if ( prop.isConstrained() )
// 		    {
// 			iw.println("if ( " + changeCheck + " )");
// 			iw.upIndent();
// 			iw.println("vcs.fireVetoableChange( \"" + prop.getName() + "\", " + oldValExpr + ", " + newValExpr + " );");
// 			iw.downIndent();
// 		    }

// 		iw.println("this." + prop.getName() + " = " + setVal + ';');
				
// 		if ( prop.isBound() )
// 		    {
// 			iw.println("if ( " + changeCheck + " )");
// 			iw.upIndent();
// 			iw.println("pcs.firePropertyChange( \"" + prop.getName() + "\", " + oldValExpr + ", " + newValExpr + " );");
// 			iw.downIndent();
// 		    }
// 	    }
// 	else
// 	    	iw.println("this." + prop.getName() + " = " + setVal + ';');

// 	iw.downIndent();
// 	iw.println('}');
    }

    protected String getGetterDefensiveCopyExpression( Property prop, Class propType )
    { return prop.getDefensiveCopyExpression(); }
    
    protected String getSetterDefensiveCopyExpression( Property prop, Class propType )
    { return prop.getDefensiveCopyExpression(); }
    
    protected String getConstructorDefensiveCopyExpression( Property prop, Class propType )
    { return prop.getDefensiveCopyExpression(); }

    protected void writeHeader() throws IOException
    {
	writeBannerComments();
	iw.println();
	iw.println("package " + info.getPackageName() + ';');
	iw.println();
	writeImports();
    }

    protected void writeBannerComments() throws IOException
    {
	iw.println("/*");
	iw.println(" * This class autogenerated by " + generatorName + '.');
	iw.println(" * DO NOT HAND EDIT!");
	iw.println(" */");
    }

    protected void writeImports() throws IOException
    {
	for ( Iterator ii = generalImports.iterator(); ii.hasNext(); )
	    iw.println("import " + ii.next() + ".*;");
	for ( Iterator ii = specificImports.iterator(); ii.hasNext(); )
	    iw.println("import " + ii.next() + ";");
    }

    protected void writeClassDeclaration() throws IOException
    {
	iw.print( CodegenUtils.getModifierString( info.getModifiers() ) + " class " + info.getClassName() );
	String superclassName = info.getSuperclassName();
	if (superclassName != null)
	    iw.print( " extends " + superclassName );
	if (interfaceNames.size() > 0)
	    {
		iw.print(" implements ");
		boolean first = true;
		for (Iterator ii = interfaceNames.iterator(); ii.hasNext(); )
		    {
			if (first) 
			    first = false;
			else
			    iw.print(", ");
				
			iw.print( (String) ii.next() );
		    }
	    }
	iw.println();
    }

    boolean boundProperties()
    {
	for (int i = 0, len = props.length; i < len; ++i)
	    if (props[i].isBound()) return true;
	return false;
    }

    boolean constrainedProperties()
    {
	for (int i = 0, len = props.length; i < len; ++i)
	    if (props[i].isConstrained()) return true;
	return false;
    }

    public static void main( String[] argv )
    {
	try
	    {
		ClassInfo info = new SimpleClassInfo("test",
						     Modifier.PUBLIC,
						     argv[0],
						     null,
						     null,
						     new String[] {"java.awt"},
						     null);
		
		Property[] props = 
		    {
			new SimpleProperty( "number",
					    "int",
					    null,
					    "7",
					    false,
					    true,
					    false
					    ),
			new SimpleProperty( "fpNumber",
					    "float",
					    null,
					    null,
					    true,
					    true,
					    false
					    ),
			new SimpleProperty( "location",
					    "Point",
					    "new Point( location.x, location.y )",
					    "new Point( 0, 0 )",
					    false,
					    true,
					    true
					    )
		    };

		FileWriter fw = new FileWriter( argv[0] + ".java" );
		SimplePropertyBeanGenerator g = new SimplePropertyBeanGenerator();
		g.addExtension( new SerializableExtension() );
		g.generate(info, props, fw );
		fw.flush();
		fw.close();
	    }
	catch ( Exception e )
	    { e.printStackTrace(); }
    }
}
