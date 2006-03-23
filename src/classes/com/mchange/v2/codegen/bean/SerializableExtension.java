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
import java.io.IOException;
import com.mchange.v2.codegen.IndentedWriter;


/**
 *  Note: this class pays no attention to whether users have marked any property variables as transient.
 *  In fact, it will work most efficiently if users mark ALL variables as transient... to define transient
 *  properties for this class, use the constructor which allows a user-specified set of transients.
 */
public class SerializableExtension implements GeneratorExtension
{
    Set transientProperties;
    Map transientPropertyInitializers;

    /**
     *  @param transientProperties a set of Strings, the names of all properties that should be considered transient and not serialized
     *  @param transientPropertyInitializers an optional Map of a subset of the transient property names to non-default initialization
     *                                       expressions, which should be unterminated expressions, and which will be used verbatim in 
     *                                       the generated code.
     */
    public SerializableExtension(Set transientProperties, Map transientPropertyInitializers)
    { 
	this.transientProperties = transientProperties; 
	this.transientPropertyInitializers = transientPropertyInitializers;
    }

    public SerializableExtension()
    { this ( Collections.EMPTY_SET, null ); }


    public Collection extraGeneralImports()
    { return Collections.EMPTY_SET; }

    public Collection extraSpecificImports()
    {
	Set set = new HashSet();
	set.add( "java.io.IOException" );
	set.add( "java.io.Serializable" );
	set.add( "java.io.ObjectOutputStream" );
	set.add( "java.io.ObjectInputStream" );
	return set;
    }

    public Collection extraInterfaceNames()
    {
	Set set = new HashSet();
	set.add( "Serializable" );
	return set;
    }

    public void generate(ClassInfo info, Class superclassType, Property[] props, Class[] propTypes, IndentedWriter iw)
	throws IOException
    {
	iw.println("private static final long serialVersionUID = 1;"); 
	iw.println("private static final short VERSION = 0x0001;"); 
	iw.println();
	iw.println("private void writeObject( ObjectOutputStream oos ) throws IOException");
	iw.println("{");
	iw.upIndent();
	
	iw.println( "oos.writeShort( VERSION );" );

	for( int i = 0, len = props.length; i < len; ++i )
	    {
		Property prop = props[i];
		if (! transientProperties.contains( prop.getName() ) )
		    {
			Class propType = propTypes[i];
			if (propType != null && propType.isPrimitive()) //primitives should always resolve, object types may not, and be null
			    {
				if (propType == byte.class)
				    iw.println("oos.writeByte(" + prop.getName() + ");");
				else if (propType == char.class)
				    iw.println("oos.writeChar(" + prop.getName() + ");");
				else if (propType == short.class)
				    iw.println("oos.writeShort(" + prop.getName() + ");");
				else if (propType == int.class)
				    iw.println("oos.writeInt(" + prop.getName() + ");");
				else if (propType == boolean.class)
				    iw.println("oos.writeBoolean(" + prop.getName() + ");");
				else if (propType == long.class)
				    iw.println("oos.writeLong(" + prop.getName() + ");");
				else if (propType == float.class)
				    iw.println("oos.writeFloat(" + prop.getName() + ");");
				else if (propType == double.class)
				    iw.println("oos.writeDouble(" + prop.getName() + ");");
			    }
			else
			    writeStoreObject( prop, propType, iw );
		    }
	    }
	iw.downIndent();
	iw.println("}");
	iw.println();

	iw.println("private void readObject( ObjectInputStream ois ) throws IOException, ClassNotFoundException");
	iw.println("{");
	iw.upIndent();
	iw.println("short version = ois.readShort();");
	iw.println("switch (version)");
	iw.println("{");
	iw.upIndent();
	
	iw.println("case VERSION:");
	iw.upIndent();
	for( int i = 0, len = props.length; i < len; ++i )
	    {
		Property prop = props[i];
		if (! transientProperties.contains( prop.getName() ) )
		    {
			Class propType = propTypes[i];
			if (propType != null && propType.isPrimitive()) //if a propType is unresolvable, it ain't a primitive
			    {
				if (propType == byte.class)
				    iw.println("this." + prop.getName() + " = ois.readByte();");
				else if (propType == char.class)
				    iw.println("this." + prop.getName() + " = ois.readChar();");
				else if (propType == short.class)
				    iw.println("this." + prop.getName() + " = ois.readShort();");
				else if (propType == int.class)
				    iw.println("this." + prop.getName() + " = ois.readInt();");
				else if (propType == boolean.class)
				    iw.println("this." + prop.getName() + " = ois.readBoolean();");
				else if (propType == long.class)
				    iw.println("this." + prop.getName() + " = ois.readLong();");
				else if (propType == float.class)
				    iw.println("this." + prop.getName() + " = ois.readFloat();");
				else if (propType == double.class)
				    iw.println("this." + prop.getName() + " = ois.readDouble();");
			    }
			else
			    writeUnstoreObject( prop, propType, iw );
		    }
		else
		    {
			String initializer = (String) transientPropertyInitializers.get( prop.getName() );
			if (initializer != null)
			    iw.println("this." + prop.getName() + " = " + initializer +';');
		    }
	    }
	iw.println("break;");
	iw.downIndent();
	iw.println("default:");
	iw.upIndent();
	iw.println("throw new IOException(\"Unsupported Serialized Version: \" + version);");
	iw.downIndent();

	iw.downIndent();
	iw.println("}");

	iw.downIndent();
	iw.println("}");
    }

    protected void writeStoreObject( Property prop, Class propType, IndentedWriter iw ) throws IOException
    {
	iw.println("oos.writeObject( " + prop.getName() + " );");
    }

    protected void writeUnstoreObject( Property prop, Class propType, IndentedWriter iw ) throws IOException
    {
	iw.println("this." + prop.getName() + " = (" + prop.getSimpleTypeName() + ") ois.readObject();");
    }
}
