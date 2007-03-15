/*
 * Distributed as part of c3p0 v.0.9.1.1
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


package com.mchange.v2.c3p0.codegen;

import java.io.*;
import java.util.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import com.mchange.v2.codegen.*;
import com.mchange.v2.codegen.bean.*;
import com.mchange.v2.c3p0.impl.*;

import java.lang.reflect.Modifier;
import com.mchange.v1.xml.DomParseUtils;

public class BeangenDataSourceGenerator
{
    public static void main( String[] argv )
    {
	try
	    {
		if (argv.length != 2)
		    {
			System.err.println("java " + BeangenDataSourceGenerator.class.getName() + 
					   " <infile.xml> <OutputFile.java>");
			return;
		    }
		

		File outFile = new File( argv[1] );
		File parentDir = outFile.getParentFile();
		if (! parentDir.exists())
		    {
			System.err.println("Warning: making parent directory: " + parentDir);
			parentDir.mkdirs();
		    }

		DocumentBuilderFactory fact = DocumentBuilderFactory.newInstance();
		DocumentBuilder db = fact.newDocumentBuilder();
		Document doc = db.parse( new File( argv[0] ) );
		ParsedPropertyBeanDocument parsed = new ParsedPropertyBeanDocument( doc );
		Writer w = new BufferedWriter( new FileWriter( outFile ) );

		SimplePropertyBeanGenerator gen = new SimplePropertyBeanGenerator();
		gen.setGeneratorName( BeangenDataSourceGenerator.class.getName() );

		// tightly coupled to the implementation of SimplePropertyBeanGenerator!
		IndirectingSerializableExtension idse = new IndirectingSerializableExtension("com.mchange.v2.naming.ReferenceIndirector")
		    {
			protected void generateExtraSerInitializers(ClassInfo info, Class superclassType, Property[] props, Class[] propTypes, IndentedWriter iw)
			    throws IOException
			{
			    if (BeangenUtils.hasBoundProperties( props ))
				iw.println("this.pcs = new PropertyChangeSupport( this );");
			    if (BeangenUtils.hasConstrainedProperties( props ))
				iw.println("this.vcs = new VetoableChangeSupport( this );");
			}
		    };
		gen.addExtension( idse );

		PropsToStringGeneratorExtension tsge = new PropsToStringGeneratorExtension();
		tsge.setExcludePropertyNames( Arrays.asList( new String[] {"userOverridesAsString","overrideDefaultUser","overrideDefaultPassword"} ) );
		gen.addExtension( tsge );

		PropertyReferenceableExtension prex = new PropertyReferenceableExtension();
		prex.setUseExplicitReferenceProperties( true );
		// we use the string version to creating dependencies between the bean generator and c3p0 classes
		//prex.setFactoryClassName( C3P0JavaBeanObjectFactory.class.getName() );
		prex.setFactoryClassName( "com.mchange.v2.c3p0.impl.C3P0JavaBeanObjectFactory" );
		gen.addExtension( prex );

		BooleanInitIdentityTokenConstructortorGeneratorExtension biitcge = new BooleanInitIdentityTokenConstructortorGeneratorExtension();
		gen.addExtension( biitcge );

		if ( parsed.getClassInfo().getClassName().equals("WrapperConnectionPoolDataSourceBase") )
		    gen.addExtension( new WcpdsExtrasGeneratorExtension() );

		if (unmodifiableShadow( doc ) )
		    gen.addExtension( new UnmodifiableShadowGeneratorExtension() );


		gen.generate( parsed.getClassInfo(), parsed.getProperties(), w );

		w.flush();
		w.close();

		System.err.println("Processed: " + argv[0] ); //+ " -> " + argv[1]);
	    }
	catch ( Exception e )
	    { e.printStackTrace(); }
    }

    private static boolean unmodifiableShadow( Document doc )
    {
	Element docElem = doc.getDocumentElement();
	return DomParseUtils.uniqueChild(docElem, "unmodifiable-shadow") != null;
    }

    static class BooleanInitIdentityTokenConstructortorGeneratorExtension implements GeneratorExtension
    {
	public Collection extraGeneralImports()  {return Collections.EMPTY_SET;} 

	public Collection extraSpecificImports() 
	{
	    Set out = new HashSet();
	    out.add( "com.mchange.v2.c3p0.C3P0Registry" );
	    return out;
	}

	public Collection extraInterfaceNames()  {return Collections.EMPTY_SET;}

	public void generate(ClassInfo info, Class superclassType, Property[] props, Class[] propTypes, IndentedWriter iw)
	    throws IOException
	{
	    BeangenUtils.writeExplicitDefaultConstructor( Modifier.PRIVATE, info, iw);
	    iw.println();
	    iw.println("public " + info.getClassName() + "( boolean autoregister )");
	    iw.println("{");
	    iw.upIndent();
	    iw.println( "if (autoregister)");
	    iw.println("{");
	    iw.upIndent();
	    iw.println("this.identityToken = C3P0ImplUtils.allocateIdentityToken( this );");
	    iw.println("C3P0Registry.reregister( this );");
	    iw.downIndent();
	    iw.println("}");

	    iw.downIndent();
	    iw.println("}");
	}
    }

    static class WcpdsExtrasGeneratorExtension implements GeneratorExtension
    {
	public Collection extraGeneralImports()  {return Collections.EMPTY_SET;} 

	public Collection extraSpecificImports() 
	{
	    Set out = new HashSet();
	    out.add( "com.mchange.v2.c3p0.ConnectionCustomizer" );
	    out.add( "javax.sql.PooledConnection" );
	    out.add( "java.sql.SQLException" );
	    return out;
	}

	public Collection extraInterfaceNames()  {return Collections.EMPTY_SET;}

	public void generate(ClassInfo info, Class superclassType, Property[] props, Class[] propTypes, IndentedWriter iw)
	    throws IOException
	{
	    iw.println("protected abstract PooledConnection getPooledConnection( ConnectionCustomizer cc, String idt)" +
		       " throws SQLException;");
	    iw.println("protected abstract PooledConnection getPooledConnection(String user, String password, ConnectionCustomizer cc, String idt)" +
		       " throws SQLException;");
	}
    }


    static class UnmodifiableShadowGeneratorExtension implements GeneratorExtension
    {
	BeanExtractingGeneratorExtension      bege;
	CompleteConstructorGeneratorExtension ccge;

	{
	    bege = new BeanExtractingGeneratorExtension();
	    bege.setExtractMethodModifiers( Modifier.PRIVATE );
	    bege.setConstructorModifiers( Modifier.PUBLIC );

	    ccge = new CompleteConstructorGeneratorExtension();
	}	

	public Collection extraGeneralImports()  
	{
	    Set out = new HashSet();
	    out.addAll( bege.extraGeneralImports() );
	    out.addAll( ccge.extraGeneralImports() );
	    return out;
	}

	public Collection extraSpecificImports() 
	{
	    Set out = new HashSet();
	    out.addAll( bege.extraSpecificImports() );
	    out.addAll( ccge.extraSpecificImports() );
	    return out;
	}

	public Collection extraInterfaceNames()  {return Collections.EMPTY_SET;}

	public void generate(ClassInfo info, Class superclassType, Property[] props, Class[] propTypes, IndentedWriter iw)
	    throws IOException
	{
	    ClassInfo innerInfo = new SimpleClassInfo( info.getPackageName(), 
						       Modifier.PUBLIC | Modifier.STATIC, 
						       "UnmodifiableShadow", 
						       info.getSuperclassName(),
						       info.getInterfaceNames(),
						       info.getGeneralImports(),
						       info.getSpecificImports() );

	    SimplePropertyBeanGenerator innerGen = new SimplePropertyBeanGenerator();
	    innerGen.setInner( true );
	    innerGen.setForceUnmodifiable( true );
	    innerGen.addExtension( bege );
	    innerGen.addExtension( ccge );
	    innerGen.generate( innerInfo, props, iw );
	}
    }
}
