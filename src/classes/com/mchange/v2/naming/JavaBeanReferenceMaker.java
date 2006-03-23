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


package com.mchange.v2.naming;

import java.beans.*;
import java.io.*;
import java.util.*;
import javax.naming.*;
import com.mchange.v2.log.*;
import java.lang.reflect.Method;
import com.mchange.v2.lang.Coerce;
import com.mchange.v2.beans.BeansUtils;
import com.mchange.v2.ser.SerializableUtils;
import com.mchange.v2.ser.IndirectPolicy;

public class JavaBeanReferenceMaker implements ReferenceMaker
{
    private final static MLogger logger = MLog.getLogger( JavaBeanReferenceMaker.class );

    final static String REF_PROPS_KEY = "com.mchange.v2.naming.JavaBeanReferenceMaker.REF_PROPS_KEY";

    final static Object[] EMPTY_ARGS = new Object[0];

    final static byte[] NULL_TOKEN_BYTES = new byte[0];

    String factoryClassName = "com.mchange.v2.naming.JavaBeanObjectFactory";
    String defaultFactoryClassLocation = null;

    Set referenceProperties = new HashSet();

    ReferenceIndirector indirector = new ReferenceIndirector();

    public Hashtable getEnvironmentProperties()
    { return indirector.getEnvironmentProperties(); }

    public void setEnvironmentProperties( Hashtable environmentProperties )
    { indirector.setEnvironmentProperties( environmentProperties ); }

    public void setFactoryClassName(String factoryClassName)
    { this.factoryClassName = factoryClassName; }

    public String getFactoryClassName()
    { return factoryClassName; }

    public String getDefaultFactoryClassLocation()
    { return defaultFactoryClassLocation; }

    public void setDefaultFactoryClassLocation( String defaultFactoryClassLocation )
    { this.defaultFactoryClassLocation = defaultFactoryClassLocation; }

    public void addReferenceProperty( String propName )
    { referenceProperties.add( propName ); }

    public void removeReferenceProperty( String propName )
    { referenceProperties.remove( propName ); }

    public Reference createReference( Object bean )
	throws NamingException
    {
	try
	    {
		BeanInfo bi = Introspector.getBeanInfo( bean.getClass() );
		PropertyDescriptor[] pds = bi.getPropertyDescriptors();
		List refAddrs = new ArrayList();
		String factoryClassLocation = defaultFactoryClassLocation;

		boolean using_ref_props = referenceProperties.size() > 0;

		// we only include this so that on dereference we are not surprised to find some properties missing
		if (using_ref_props)
		    refAddrs.add( new BinaryRefAddr( REF_PROPS_KEY, SerializableUtils.toByteArray( referenceProperties ) ) );

		for (int i = 0, len = pds.length; i < len; ++i)
		    {
			PropertyDescriptor pd = pds[i];
			String propertyName = pd.getName();
			//System.err.println("Making Reference: " + propertyName);

			if (using_ref_props && ! referenceProperties.contains( propertyName ))
			    {
				//System.err.println("Not a ref_prop -- continuing.");
				continue;
			    }

			Class  propertyType = pd.getPropertyType();
			Method getter = pd.getReadMethod();
			Method setter = pd.getWriteMethod();
			if (getter != null && setter != null) //only use properties that are both readable and writable
			    {
				Object val = getter.invoke( bean, EMPTY_ARGS );
				//System.err.println( "val: " + val );
				if (propertyName.equals("factoryClassLocation"))
				    {
					if (String.class != propertyType)
					    throw new NamingException(this.getClass().getName() + " requires a factoryClassLocation property to be a string, " +
								      propertyType.getName() + " is not valid.");
					factoryClassLocation = (String) val;
				    }

				if (val == null)
				    {
					RefAddr addMe = new BinaryRefAddr( propertyName, NULL_TOKEN_BYTES );
					refAddrs.add( addMe );
				    }
				else if ( Coerce.canCoerce( propertyType ) )
				    {
					RefAddr addMe = new StringRefAddr( propertyName, String.valueOf( val ) );
					refAddrs.add( addMe );
				    }
				else  //other Object properties
				    {
					RefAddr addMe = null;
					PropertyEditor pe = BeansUtils.findPropertyEditor( pd );
					if (pe != null)
					    {
						pe.setValue( val );
						String textValue = pe.getAsText();
						if (textValue != null)
						    addMe = new StringRefAddr( propertyName, textValue );
					    }
					if (addMe == null) //property editor approach failed
					    addMe = new BinaryRefAddr( propertyName, SerializableUtils.toByteArray( val, 
														    indirector, 
														    IndirectPolicy.INDIRECT_ON_EXCEPTION ) );
					refAddrs.add( addMe );
				    }
			    }
			else
			    {
// 				System.err.println(this.getClass().getName() +
// 						   ": Skipping " + propertyName + " because it is " + (setter == null ? "read-only." : "write-only."));

				if ( logger.isLoggable( MLevel.WARNING ) )
				    logger.warning(this.getClass().getName() + ": Skipping " + propertyName + 
						   " because it is " + (setter == null ? "read-only." : "write-only."));
			    }

		    }
		Reference out = new Reference( bean.getClass().getName(), factoryClassName, factoryClassLocation );
		for (Iterator ii = refAddrs.iterator(); ii.hasNext(); )
		    out.add( (RefAddr) ii.next() );
		return out;
	    }
	catch ( Exception e )
	    {
		//e.printStackTrace();
		if ( Debug.DEBUG && logger.isLoggable( MLevel.FINE ) )
		    logger.log( MLevel.FINE, "Exception trying to create Reference.", e);

		throw new NamingException("Could not create reference from bean: " + e.toString() );
	    }
    }

}

