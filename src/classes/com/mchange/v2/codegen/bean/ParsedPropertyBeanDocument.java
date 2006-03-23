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

import org.w3c.dom.*;
import java.lang.reflect.Modifier;
import com.mchange.v1.xml.DomParseUtils;

public class ParsedPropertyBeanDocument
{
    final static String[] EMPTY_SA = new String[0];

    String   packageName;
    int      class_modifiers;
    String   className;
    String   superclassName;
    String[] interfaceNames  = EMPTY_SA;
    String[] generalImports  = EMPTY_SA;
    String[] specificImports = EMPTY_SA;
    Property[] properties;
    
    public ParsedPropertyBeanDocument(Document doc)
    {
	Element rootElem = doc.getDocumentElement();
        this.packageName = DomParseUtils.allTextFromUniqueChild( rootElem, "package" );
	Element modifiersElem = DomParseUtils.uniqueImmediateChild( rootElem, "modifiers" );
	if (modifiersElem != null)
	    class_modifiers = parseModifiers( modifiersElem );
	else
	    class_modifiers = Modifier.PUBLIC;
	
	Element importsElem = DomParseUtils.uniqueChild( rootElem, "imports" );
	if (importsElem != null)
	    {
		this.generalImports = DomParseUtils.allTextFromImmediateChildElements( importsElem, "general" );
		this.specificImports = DomParseUtils.allTextFromImmediateChildElements( importsElem, "specific" );
	    }
	this.className = DomParseUtils.allTextFromUniqueChild( rootElem, "output-class" ); 
	this.superclassName = DomParseUtils.allTextFromUniqueChild( rootElem, "extends" ); 

	Element implementsElem = DomParseUtils.uniqueChild( rootElem, "implements" );
	if (implementsElem != null)
	    this.interfaceNames = DomParseUtils.allTextFromImmediateChildElements( implementsElem, "interface" );
	Element propertiesElem = DomParseUtils.uniqueChild( rootElem, "properties" );
	this.properties = findProperties( propertiesElem );
    }


    public ClassInfo getClassInfo()
    {
	return new ClassInfo()
	    {
		public String getPackageName()
		{ return packageName; }

		public int getModifiers()
		{ return class_modifiers; }

		public String getClassName()
		{ return className; }

		public String getSuperclassName()
		{ return superclassName; }

		public String[] getInterfaceNames()
		{ return interfaceNames; }

		public String[] getGeneralImports()
		{ return generalImports; }

		public String[] getSpecificImports()
		{ return specificImports; }
	    };
    }

    public Property[] getProperties()
    { return (Property[]) properties.clone(); }

    private Property[] findProperties( Element propertiesElem )
    {
	NodeList nl = DomParseUtils.immediateChildElementsByTagName( propertiesElem, "property" );
	int len = nl.getLength();
	Property[] out = new Property[ len ];
	for( int i = 0; i < len; ++i)
	    {
		Element propertyElem = (Element) nl.item( i );

		int variable_modifiers;
		String name;
		String simpleTypeName;
		String  defensiveCopyExpression;
		String  defaultValueExpression;
		int     getter_modifiers;
		int     setter_modifiers;
		boolean is_read_only;
		boolean is_bound;
		boolean is_constrained;
		
		variable_modifiers = modifiersThroughParentElem( propertyElem, "variable", Modifier.PRIVATE );
		name = DomParseUtils.allTextFromUniqueChild( propertyElem, "name", true );
		simpleTypeName = DomParseUtils.allTextFromUniqueChild( propertyElem, "type", true );
		defensiveCopyExpression = DomParseUtils.allTextFromUniqueChild( propertyElem, "defensive-copy", true );
		defaultValueExpression = DomParseUtils.allTextFromUniqueChild( propertyElem, "default-value", true );
		getter_modifiers = modifiersThroughParentElem( propertyElem, "getter", Modifier.PUBLIC );
		setter_modifiers = modifiersThroughParentElem( propertyElem, "setter", Modifier.PUBLIC );
		Element readOnlyElem = DomParseUtils.uniqueChild( propertyElem, "read-only" );
		is_read_only = (readOnlyElem != null);
		Element isBoundElem = DomParseUtils.uniqueChild( propertyElem, "bound" );
		is_bound = (isBoundElem != null);
		Element isConstrainedElem = DomParseUtils.uniqueChild( propertyElem, "constrained" );
		is_constrained = (isConstrainedElem != null);
		out[i] = new SimpleProperty( variable_modifiers, name, simpleTypeName, defensiveCopyExpression, 
					     defaultValueExpression, getter_modifiers, setter_modifiers, 
					     is_read_only, is_bound, is_constrained );
	    }
	return out;
    }

    private static int modifiersThroughParentElem( Element grandparentElem, String parentElemName, int default_mods )
    {
	Element parentElem = DomParseUtils.uniqueChild( grandparentElem, parentElemName );
	if (parentElem != null )
	    {
		Element modifiersElem = DomParseUtils.uniqueChild( parentElem, "modifiers" );
		if (modifiersElem != null)
		    return parseModifiers( modifiersElem );
		else
		    return default_mods;
	    }
	else
	    return default_mods;
    }

    private static int parseModifiers( Element modifiersElem )
    {
	int out = 0;
	String[] all_modifiers = DomParseUtils.allTextFromImmediateChildElements( modifiersElem, "modifier", true );
	for ( int i = 0, len = all_modifiers.length; i < len; ++i)
	    {
		String modifier = all_modifiers[i];
		if ("public".equals( modifier )) out |= Modifier.PUBLIC;
		else if ("protected".equals( modifier )) out |= Modifier.PROTECTED;
		else if ("private".equals( modifier )) out |= Modifier.PRIVATE;
		else if ("final".equals( modifier )) out |= Modifier.FINAL;
		else if ("abstract".equals( modifier )) out |= Modifier.ABSTRACT;
		else if ("static".equals( modifier )) out |= Modifier.STATIC;
		else if ("synchronized".equals( modifier )) out |= Modifier.SYNCHRONIZED;
		else if ("volatile".equals( modifier )) out |= Modifier.VOLATILE;
		else if ("transient".equals( modifier )) out |= Modifier.TRANSIENT;
		else if ("strictfp".equals( modifier )) out |= Modifier.STRICT;
		else if ("native".equals( modifier )) out |= Modifier.NATIVE;
		else if ("interface".equals( modifier )) out |= Modifier.INTERFACE; // ????
		else throw new IllegalArgumentException("Bad modifier: " + modifier);
	    }
	return out;
    }


}
