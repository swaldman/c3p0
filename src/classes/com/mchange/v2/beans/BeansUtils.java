/*
 * Distributed as part of c3p0 v.0.8.5pre4
 *
 * Copyright (C) 2003 Machinery For Change, Inc.
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


package com.mchange.v2.beans;

import java.beans.*;
import java.lang.reflect.*;

public final class BeansUtils
{
    final static Object[] EMPTY_ARGS = new Object[0];

    public static PropertyEditor findPropertyEditor( PropertyDescriptor pd )
    {
	PropertyEditor out = null;
	Class editorClass = null;
	try
	    {
		editorClass = pd.getPropertyEditorClass();
		if (editorClass != null)
		    out = (PropertyEditor) editorClass.newInstance();
	    }
	catch (Exception e)
	    {
		e.printStackTrace();
		System.err.println("WARNING: Bad property editor class " + editorClass.getName() + 
				   " registered for property " + pd.getName());
	    }

	if ( out == null )
	    out = PropertyEditorManager.findEditor( pd.getPropertyType() );
	return out;
    }

    public static void overwriteAccessibleProperties( Object sourceBean, Object destBean )
	throws IntrospectionException
    {
	try
	    {
		BeanInfo beanInfo = Introspector.getBeanInfo( sourceBean.getClass() );
		PropertyDescriptor[] pds = beanInfo.getPropertyDescriptors();
		for( int i = 0, len = pds.length; i < len; ++i)
		    {
			PropertyDescriptor pd = pds[i];
			Method getter = pd.getReadMethod();
			Method setter = pd.getWriteMethod();

			if ( getter == null || setter == null )
			    {
				if ( pd instanceof IndexedPropertyDescriptor )
				    {
					System.err.println("WARNING: BeansUtils.overwriteAccessibleProperties() does not");
					System.err.println("support indexed properties that do not provide single-valued");
					System.err.println("array getters and setters! [The indexed methods provide no means");
					System.err.println("of modifying the size of the array in the destination bean if");
					System.err.println("it does not match the source.]");
				    }

				System.err.println("Property inaccessible for overwriting: " + pd.getName());
			    }
			else
			    {
				Object value = getter.invoke( sourceBean, EMPTY_ARGS );
				setter.invoke( destBean, new Object[] { value } );
			    }
		    }
	    }
	catch ( IntrospectionException e )
	    { throw e; }
	catch ( Exception e )
	    {
		e.printStackTrace();
		throw new IntrospectionException( e.getMessage() );
	    }
    }

    public static void debugShowPropertyChange( PropertyChangeEvent evt )
    {
	System.err.println("PropertyChangeEvent: [ propertyName -> " + evt.getPropertyName() + 
			   ", oldValue -> " + evt.getOldValue() +
			   ", newValue -> " + evt.getNewValue() +
			   " ]");
    }

    private BeansUtils()
    {}
}
