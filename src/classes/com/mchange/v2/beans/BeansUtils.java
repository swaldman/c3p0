/*
 * Distributed as part of c3p0 v.0.9.0-pre4
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


package com.mchange.v2.beans;

import java.beans.*;
import java.lang.reflect.*;
import java.util.*;
import com.mchange.v2.log.*;

public final class BeansUtils
{
    final static MLogger logger = MLog.getLogger( BeansUtils.class );

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
// 		e.printStackTrace();
// 		System.err.println("WARNING: Bad property editor class " + editorClass.getName() + 
// 				   " registered for property " + pd.getName());
		if (logger.isLoggable( MLevel.WARNING ) )
		    logger.log(MLevel.WARNING, "Bad property editor class " + editorClass.getName() + " registered for property " + pd.getName(), e);
	    }

	if ( out == null )
	    out = PropertyEditorManager.findEditor( pd.getPropertyType() );
	return out;
    }

    public static void overwriteAccessibleProperties( Object sourceBean, Object destBean )
	throws IntrospectionException
    { overwriteAccessibleProperties( sourceBean, destBean, Collections.EMPTY_SET ); }

    public static void overwriteAccessibleProperties( Object sourceBean, Object destBean, Collection ignoreProps )
	throws IntrospectionException
    {
	try
	    {
		BeanInfo beanInfo = Introspector.getBeanInfo( sourceBean.getClass(), Object.class ); //so we don't see message about getClass()
		PropertyDescriptor[] pds = beanInfo.getPropertyDescriptors();
		for( int i = 0, len = pds.length; i < len; ++i)
		    {
			PropertyDescriptor pd = pds[i];
			if ( ignoreProps.contains( pd.getName() ) )
			    continue;

			Method getter = pd.getReadMethod();
			Method setter = pd.getWriteMethod();

			if ( getter == null || setter == null )
			    {
				if ( pd instanceof IndexedPropertyDescriptor )
				    {
// 					System.err.println("WARNING: BeansUtils.overwriteAccessibleProperties() does not");
// 					System.err.println("support indexed properties that do not provide single-valued");
// 					System.err.println("array getters and setters! [The indexed methods provide no means");
// 					System.err.println("of modifying the size of the array in the destination bean if");
// 					System.err.println("it does not match the source.]");

					if ( logger.isLoggable( MLevel.WARNING ) )
					    logger.warning("BeansUtils.overwriteAccessibleProperties() does not" +
							   " support indexed properties that do not provide single-valued" +
							   " array getters and setters! [The indexed methods provide no means" +
							   " of modifying the size of the array in the destination bean if" +
							   " it does not match the source.]");
				    }

				//System.err.println("Property inaccessible for overwriting: " + pd.getName());
				if (logger.isLoggable( MLevel.INFO ))
				    logger.info("Property inaccessible for overwriting: " + pd.getName());
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
		//e.printStackTrace();
		if (Debug.DEBUG && Debug.TRACE >= Debug.TRACE_MED && logger.isLoggable( MLevel.FINE ))
		    logger.log( MLevel.FINE, "Converting exception to throwable IntrospectionException", e );
		    
		throw new IntrospectionException( e.getMessage() );
	    }
    }

    public static void overwriteAccessiblePropertiesFromMap( Map sourceMap, Object destBean, boolean skip_nulls )
	throws IntrospectionException
    { overwriteAccessiblePropertiesFromMap( sourceMap, destBean, skip_nulls, Collections.EMPTY_SET ); }

    public static void overwriteAccessiblePropertiesFromMap( Map sourceMap, Object destBean, boolean skip_nulls, Collection ignoreProps )
	throws IntrospectionException
    {
	String propName = null;
	try
	    {
		BeanInfo beanInfo = Introspector.getBeanInfo( destBean.getClass(), Object.class ); //so we don't see message about getClass()
		PropertyDescriptor[] pds = beanInfo.getPropertyDescriptors();
		//System.err.println("ignoreProps: " + ignoreProps );
		for( int i = 0, len = pds.length; i < len; ++i)
		    {
			PropertyDescriptor pd = pds[i];
			propName = pd.getName();
			if ( ignoreProps.contains( propName ) )
			    {
				//System.err.println("ignoring: " + propName);
				continue;
			    }
			//else
			//    System.err.println("not ignoring: " + propName);

			Object propVal = sourceMap.get( propName );
			if (propVal == null)
			    {
				if (skip_nulls) continue;
				//do we need to worry about primitives here?
			    }

			Method setter = pd.getWriteMethod();

			if ( setter == null )
			    {
				if ( pd instanceof IndexedPropertyDescriptor )
				    {
// 					System.err.println("WARNING: BeansUtils.overwriteAccessiblePropertiesFromMap() does not");
// 					System.err.println("support indexed properties that do not provide single-valued");
// 					System.err.println("array getters and setters! [The indexed methods provide no means");
// 					System.err.println("of modifying the size of the array in the destination bean if");
// 					System.err.println("it does not match the source.]");

					if ( logger.isLoggable( MLevel.WARNING ) )
					    logger.warning("BeansUtils.overwriteAccessiblePropertiesFromMap() does not" +
							   " support indexed properties that do not provide single-valued" +
							   " array getters and setters! [The indexed methods provide no means" +
							   " of modifying the size of the array in the destination bean if" +
							   " it does not match the source.]");

				    }

				//System.err.println("Property inaccessible for overwriting: " + pd.getName());
				if (logger.isLoggable( MLevel.INFO ))
				    logger.info("Property inaccessible for overwriting: " + pd.getName());
			    }
			else
			    {
				//System.err.println("invoking method: " + setter);
				setter.invoke( destBean, new Object[] { propVal } );
			    }
		    }
	    }
	catch ( IntrospectionException e )
	    {
// 		if (propName != null)
// 		    System.err.println("Problem occurred while overwriting property: " + propName);
		if ( logger.isLoggable( MLevel.WARNING ) )
		    logger.warning("Problem occurred while overwriting property: " + propName);
		if (Debug.DEBUG && Debug.TRACE >= Debug.TRACE_MED && logger.isLoggable( MLevel.FINE ))
		    logger.logp( MLevel.FINE, 
				 BeansUtils.class.getName(),
				 "overwriteAccessiblePropertiesFromMap( Map sourceMap, Object destBean, boolean skip_nulls, Collection ignoreProps )",
				 (propName != null ? "Problem occurred while overwriting property: " + propName : "") + " throwing...",
				 e );
		throw e; 
	    }
	catch ( Exception e )
	    {
		//e.printStackTrace();
		if (Debug.DEBUG && Debug.TRACE >= Debug.TRACE_MED && logger.isLoggable( MLevel.FINE ))
		    logger.log( MLevel.FINE, "Converting exception to throwable IntrospectionException [propName: " + propName + "]", e );
		throw new IntrospectionException( e.toString() + (propName == null ? "" : " [" + propName + ']') );
	    }
    }

    public static void appendPropNamesAndValues(StringBuffer appendIntoMe, Object bean, Collection ignoreProps) throws IntrospectionException
    {
	Map tmp = new TreeMap( String.CASE_INSENSITIVE_ORDER );
	extractAccessiblePropertiesToMap( tmp, bean, ignoreProps );
	boolean first = true;
	for (Iterator ii = tmp.keySet().iterator(); ii.hasNext(); )
	    {
		String key = (String) ii.next();
		Object val = tmp.get( key );
		if (first)
		    first = false;
		else
		    appendIntoMe.append( ", " );
		appendIntoMe.append( key );
		appendIntoMe.append( " -> ");
		appendIntoMe.append( val );
	    }
    }


    public static void extractAccessiblePropertiesToMap( Map fillMe, Object bean ) throws IntrospectionException
    { extractAccessiblePropertiesToMap( fillMe, bean, Collections.EMPTY_SET ); }

    public static void extractAccessiblePropertiesToMap( Map fillMe, Object bean, Collection ignoreProps ) throws IntrospectionException
    {
	String propName = null;
	try
	    {
		BeanInfo bi = Introspector.getBeanInfo( bean.getClass(), Object.class );
		PropertyDescriptor[] pds = bi.getPropertyDescriptors();
		for (int i = 0, len = pds.length; i < len; ++i)
		    {
			PropertyDescriptor pd = pds[i];
			propName = pd.getName();
			if (ignoreProps.contains( propName ))
			    continue;
			
			Method readMethod = pd.getReadMethod();
			Object propVal = readMethod.invoke( bean, EMPTY_ARGS );
			fillMe.put( propName, propVal );
		    }
	    }
	catch ( IntrospectionException e )
	    {
// 		if (propName != null)
// 		    System.err.println("Problem occurred while overwriting property: " + propName);
		if ( logger.isLoggable( MLevel.WARNING ) )
		    logger.warning("Problem occurred while overwriting property: " + propName);
		if (Debug.DEBUG && Debug.TRACE >= Debug.TRACE_MED && logger.isLoggable( MLevel.FINE ))
		    logger.logp( MLevel.FINE, 
				 BeansUtils.class.getName(),
				 "extractAccessiblePropertiesToMap( Map fillMe, Object bean, Collection ignoreProps )",
				 (propName != null ? "Problem occurred while overwriting property: " + propName : "") + " throwing...",
				 e );
		throw e; 
	    }
	catch ( Exception e )
	    {
		//e.printStackTrace();
		if (Debug.DEBUG && Debug.TRACE >= Debug.TRACE_MED && logger.isLoggable( MLevel.FINE ))
		    logger.logp( MLevel.FINE, 
				 BeansUtils.class.getName(),
				 "extractAccessiblePropertiesToMap( Map fillMe, Object bean, Collection ignoreProps )",
				 "Caught unexpected Exception; Converting to IntrospectionException.",
				 e );
		throw new IntrospectionException( e.toString() + (propName == null ? "" : " [" + propName + ']') );
	    }
    }

    private static void overwriteProperty( String propName, Object value, Method putativeSetter, Object target )
	throws Exception
    {
	if ( putativeSetter.getDeclaringClass().isAssignableFrom( target.getClass() ) )
	    putativeSetter.invoke( target, new Object[] { value } );
	else
	    {
		BeanInfo beanInfo = Introspector.getBeanInfo( target.getClass(), Object.class );
		PropertyDescriptor pd = null;

		PropertyDescriptor[] pds = beanInfo.getPropertyDescriptors();
		for( int i = 0, len = pds.length; i < len; ++i)
		    if (propName.equals( pds[i].getName() ))
			{
			    pd = pds[i];
			    break;
			}

		Method targetSetter = pd.getWriteMethod();
		targetSetter.invoke( target, new Object[] { value } );
	    }
    }


    public static void overwriteSpecificAccessibleProperties( Object sourceBean, Object destBean, Collection props )
	throws IntrospectionException
    {
	try
	    {
		Set _props = new HashSet(props);

		BeanInfo beanInfo = Introspector.getBeanInfo( sourceBean.getClass(), Object.class ); //so we don't see message about getClass()
		PropertyDescriptor[] pds = beanInfo.getPropertyDescriptors();
		for( int i = 0, len = pds.length; i < len; ++i)
		    {
			PropertyDescriptor pd = pds[i];
			String name = pd.getName();
			if (! _props.remove( name ) )
			    continue;

			Method getter = pd.getReadMethod();
			Method setter = pd.getWriteMethod();

			if ( getter == null || setter == null )
			    {
				if ( pd instanceof IndexedPropertyDescriptor )
				    {
// 					System.err.println("WARNING: BeansUtils.overwriteAccessibleProperties() does not");
// 					System.err.println("support indexed properties that do not provide single-valued");
// 					System.err.println("array getters and setters! [The indexed methods provide no means");
// 					System.err.println("of modifying the size of the array in the destination bean if");
// 					System.err.println("it does not match the source.]");

					if ( logger.isLoggable( MLevel.WARNING ) )
					    logger.warning("BeansUtils.overwriteAccessibleProperties() does not" +
							   " support indexed properties that do not provide single-valued" +
							   " array getters and setters! [The indexed methods provide no means" +
							   " of modifying the size of the array in the destination bean if" +
							   " it does not match the source.]");
				    }

				if ( logger.isLoggable( MLevel.INFO ) )
				    logger.info("Property inaccessible for overwriting: " + pd.getName());
			    }
			else
			    {
				Object value = getter.invoke( sourceBean, EMPTY_ARGS );
				overwriteProperty( name, value, setter, destBean );
				//setter.invoke( destBean, new Object[] { value } );
			    }
		    }
		if ( logger.isLoggable( MLevel.WARNING ) )
		    {
			for (Iterator ii = _props.iterator(); ii.hasNext(); )
			    logger.warning("failed to find expected property: " + ii.next());
			//System.err.println("failed to find expected property: " + ii.next());
		    }
	    }
	catch ( IntrospectionException e )
	    { throw e; }
	catch ( Exception e )
	    {
		//e.printStackTrace();
		if (Debug.DEBUG && Debug.TRACE >= Debug.TRACE_MED && logger.isLoggable( MLevel.FINE ))
		    logger.logp( MLevel.FINE, 
				 BeansUtils.class.getName(),
				 "overwriteSpecificAccessibleProperties( Object sourceBean, Object destBean, Collection props )",
				 "Caught unexpected Exception; Converting to IntrospectionException.",
				 e );
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
