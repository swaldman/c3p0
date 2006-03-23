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
import java.util.*;
import javax.naming.*;
import com.mchange.v2.log.*;
import java.lang.reflect.Method;
import javax.naming.spi.ObjectFactory;
import com.mchange.v2.beans.BeansUtils;
import com.mchange.v2.lang.Coerce;
import com.mchange.v2.ser.SerializableUtils;

public class JavaBeanObjectFactory implements ObjectFactory
{
    private final static MLogger logger = MLog.getLogger( JavaBeanObjectFactory.class );

    final static Object NULL_TOKEN = new Object();

    public Object getObjectInstance(Object refObj, Name name, Context nameCtx, Hashtable env)
	throws Exception
    {
	if (refObj instanceof Reference)
	    {
		Reference ref = (Reference) refObj;
		Map refAddrsMap = new HashMap();
		for (Enumeration e = ref.getAll(); e.hasMoreElements(); )
		    {
			RefAddr addr = (RefAddr) e.nextElement();
			refAddrsMap.put( addr.getType(), addr );
		    }
		Class beanClass = Class.forName( ref.getClassName() );
		Set refProps = null;
		RefAddr refPropsRefAddr = (BinaryRefAddr) refAddrsMap.remove( JavaBeanReferenceMaker.REF_PROPS_KEY );
		if ( refPropsRefAddr != null )
		    refProps = (Set) SerializableUtils.fromByteArray( (byte[]) refPropsRefAddr.getContent() );
		Map propMap = createPropertyMap( beanClass, refAddrsMap );
		return findBean( beanClass, propMap, refProps );
	    }
	else
	    return null;
    }

    private Map createPropertyMap( Class beanClass, Map refAddrsMap ) throws Exception
    {
	BeanInfo bi = Introspector.getBeanInfo( beanClass );
	PropertyDescriptor[] pds = bi.getPropertyDescriptors();

	Map out = new HashMap();
	for (int i = 0, len = pds.length; i < len; ++i)
	    {
		PropertyDescriptor pd = pds[i];
		String propertyName = pd.getName();
		Class  propertyType = pd.getPropertyType();
		Object addr = refAddrsMap.remove( propertyName );
		if (addr != null)
		    {
			if ( addr instanceof StringRefAddr )
			    {
				String content = (String) ((StringRefAddr) addr).getContent();
				if ( Coerce.canCoerce( propertyType ) )
				    out.put( propertyName, Coerce.toObject( content, propertyType ) );
				else
				    {
					PropertyEditor pe = BeansUtils.findPropertyEditor( pd );
					pe.setAsText( content );
					out.put( propertyName, pe.getValue() );
				    }
			    }
			else if ( addr instanceof BinaryRefAddr )
			    {
				byte[] content = (byte[]) ((BinaryRefAddr) addr).getContent();
				if ( content.length == 0 )
				    out.put( propertyName, NULL_TOKEN ); //we use an empty array to mean null
				else
				    out.put( propertyName, SerializableUtils.fromByteArray( content ) ); //this will handle "indirectly serialized" objects.
			    }
			else
			    {
				if (logger.isLoggable( MLevel.WARNING ))
				    logger.warning(this.getClass().getName() + " -- unknown RefAddr subclass: " + addr.getClass().getName());
			    }
		    }
	    }
	for ( Iterator ii = refAddrsMap.keySet().iterator(); ii.hasNext(); )
	    {
		String type = (String) ii.next();
		if (logger.isLoggable( MLevel.WARNING ))
		    logger.warning(this.getClass().getName() + " -- RefAddr for unknown property: " + type);
	    }
	return out;
    }

    protected Object findBean(Class beanClass, Map propertyMap, Set refProps ) throws Exception
    {
	Object bean = beanClass.newInstance();
	BeanInfo bi = Introspector.getBeanInfo( bean.getClass() );
	PropertyDescriptor[] pds = bi.getPropertyDescriptors();
	
	for (int i = 0, len = pds.length; i < len; ++i)
	    {
		PropertyDescriptor pd = pds[i];
		String propertyName = pd.getName();
		Object value = propertyMap.get( propertyName );
		Method setter = pd.getWriteMethod();
		if (value != null)
		    {
			if (setter != null)
			    setter.invoke( bean, new Object[] { (value == NULL_TOKEN ? null : value) } );
			else
			    {
				//System.err.println(this.getClass().getName() + ": Could not restore read-only property '" + propertyName + "'.");
				if (logger.isLoggable( MLevel.WARNING ))
				    logger.warning(this.getClass().getName() + ": Could not restore read-only property '" + propertyName + "'.");
			    }
		    }
		else
		    {
			if (setter != null)
			    {
				if (refProps == null || refProps.contains( propertyName ))
				    {
					//System.err.println(this.getClass().getName() +
					//": WARNING -- Expected writable property '" + propertyName + "' left at default value");
					if (logger.isLoggable( MLevel.WARNING ))
					    logger.warning(this.getClass().getName() + " -- Expected writable property ''" + propertyName + "'' left at default value");
				    }
			    }
		    }
	    }
	
	return bean;
    }
}
