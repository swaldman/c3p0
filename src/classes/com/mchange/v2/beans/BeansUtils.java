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


package com.mchange.v2.beans;

import java.beans.*;
import java.lang.reflect.*;
import java.util.*;
import com.mchange.v2.log.*;

import com.mchange.v2.lang.Coerce;

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

    public static boolean equalsByAccessibleProperties( Object bean0, Object bean1 )
	throws IntrospectionException
    { return equalsByAccessibleProperties( bean0, bean1, Collections.EMPTY_SET ); }

    public static boolean equalsByAccessibleProperties( Object bean0, Object bean1, Collection ignoreProps )
	throws IntrospectionException
    {
	Map m0 = new HashMap();
	Map m1 = new HashMap();
	extractAccessiblePropertiesToMap( m0, bean0, ignoreProps );
	extractAccessiblePropertiesToMap( m1, bean1, ignoreProps );
	//System.err.println("Map0 -> " + m0);
	//System.err.println("Map1 -> " + m1);
	return m0.equals(m1);
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
		    logger.log( MLevel.FINE, "Converting exception to throwable IntrospectionException" );
		    
		throw new IntrospectionException( e.getMessage() );
	    }
    }

    public static void overwriteAccessiblePropertiesFromMap( Map sourceMap, Object destBean, boolean skip_nulls )
	throws IntrospectionException
    { overwriteAccessiblePropertiesFromMap( sourceMap, destBean, skip_nulls, Collections.EMPTY_SET ); }

    public static void overwriteAccessiblePropertiesFromMap( Map sourceMap, Object destBean, boolean skip_nulls, Collection ignoreProps )
	throws IntrospectionException
    {
	overwriteAccessiblePropertiesFromMap( sourceMap, 
					      destBean, 
					      skip_nulls, 
					      ignoreProps, 
					      false,
					      MLevel.WARNING,
					      MLevel.WARNING,
					      true);
    }
    
    public static void overwriteAccessiblePropertiesFromMap( Map sourceMap, 
							     Object destBean, 
							     boolean skip_nulls, 
							     Collection ignoreProps, 
							     boolean coerce_strings,
							     MLevel cantWriteLevel,
							     MLevel cantCoerceLevel,
							     boolean die_on_one_prop_failure)
	throws IntrospectionException
    {
	if (cantWriteLevel == null)
	    cantWriteLevel = MLevel.WARNING;
	if (cantCoerceLevel == null)
	    cantCoerceLevel = MLevel.WARNING;

	Set sourceMapProps = sourceMap.keySet();

	String propName = null;
	BeanInfo beanInfo = Introspector.getBeanInfo( destBean.getClass(), Object.class ); //so we don't see message about getClass()
	PropertyDescriptor[] pds = beanInfo.getPropertyDescriptors();
	//System.err.println("ignoreProps: " + ignoreProps );
	for( int i = 0, len = pds.length; i < len; ++i)
	    {
		PropertyDescriptor pd = pds[i];
		propName = pd.getName();

		if (! sourceMapProps.contains( propName ))
		    continue;
		
		if ( ignoreProps != null && ignoreProps.contains( propName ) )
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
		boolean rethrow = false;
		
		Class propType = pd.getPropertyType();;

// 		try
// 		    {
		
			if ( setter == null )
			    {
				if ( pd instanceof IndexedPropertyDescriptor )
				    {
					if ( logger.isLoggable( MLevel.FINER ) )
					    logger.finer("BeansUtils.overwriteAccessiblePropertiesFromMap() does not" +
							 " support indexed properties that do not provide single-valued" +
							 " array getters and setters! [The indexed methods provide no means" +
							 " of modifying the size of the array in the destination bean if" +
							 " it does not match the source.]");
					
				    }
				
				if ( logger.isLoggable( cantWriteLevel ))
				    {
					String msg = "Property inaccessible for overwriting: " + propName; 
					logger.log( cantWriteLevel, msg );
					if (die_on_one_prop_failure)
					    {
						rethrow = true;
						throw new IntrospectionException( msg );
					    }
				    }
				
			    }
			else
			    {
				if (coerce_strings &&
				    propVal != null && 
				    propVal.getClass() == String.class && 
				    (propType = pd.getPropertyType()) != String.class &&
				    Coerce.canCoerce( propType ))
				    {
					Object coercedPropVal;
					try
					    { 
						coercedPropVal = Coerce.toObject( (String) propVal, propType ); 
						System.err.println(propName + "-> coercedPropVal: " + coercedPropVal);
						setter.invoke( destBean, new Object[] { coercedPropVal } );
					    }
					catch (IllegalArgumentException e) 
					    {
						// thrown by Coerce.toObject()
						// recall that NumberFormatException inherits from IllegalArgumentException
						String msg = 
						    "Failed to coerce property: " + propName +
						    " [propVal: " + propVal + "; propType: " + propType + "]";
						if ( logger.isLoggable( cantCoerceLevel ) )
						    logger.log( cantCoerceLevel, msg, e );
						if (die_on_one_prop_failure)
						    {
							rethrow = true;
							throw new IntrospectionException( msg );
						    }
					    }
					catch (Exception e)
					    {
						String msg = 
						    "Failed to set property: " + propName +
						    " [propVal: " + propVal + "; propType: " + propType + "]";
						if ( logger.isLoggable( cantWriteLevel ) )
						    logger.log( cantWriteLevel, msg, e );
						if (die_on_one_prop_failure)
						    {
							rethrow = true;
							throw new IntrospectionException( msg );
						    }
					    }
				    }
				else
				    {
					try
					    {
						//System.err.println("invoking method: " + setter);
						setter.invoke( destBean, new Object[] { propVal } );
					    }
					catch (Exception e)
					    {
						String msg = 
						    "Failed to set property: " + propName +
						    " [propVal: " + propVal + "; propType: " + propType + "]";
						if ( logger.isLoggable( cantWriteLevel ) )
						    logger.log( cantWriteLevel, msg, e );
						if (die_on_one_prop_failure)
						    {
							rethrow = true;
							throw new IntrospectionException( msg );
						    }
					    }
				    }
			    }
// 		    }
// 		catch (Exception e)
// 		    {
// 			if (e instanceof IntrospectionException && rethrow)
// 			    throw (IntrospectionException) e;
// 			else
// 			    {
// 				String msg = 
// 				    "An exception occurred while trying to set property '" + propName +
// 				    "' to value '" + propVal + "'. ";
// 				logger.log(MLevel.WARNING, msg, e);
// 				if (die_on_one_prop_failure)
// 				    {
// 					rethrow = true;
// 					throw new IntrospectionException( msg + e.toString());
// 				    }
// 			    }
// 		    }
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
