/*
 * Distributed as part of c3p0 v.0.9.2-pre1
 *
 * Copyright (C) 2010 Machinery For Change, Inc.
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


package com.mchange.v2.c3p0.management;

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.*;
import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.DynamicMBean;
import javax.management.InvalidAttributeValueException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanConstructorInfo;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanParameterInfo;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.sql.ConnectionPoolDataSource;
import javax.sql.DataSource;

import com.mchange.v1.lang.ClassUtils;
import com.mchange.v2.c3p0.ComboPooledDataSource;
import com.mchange.v2.c3p0.DriverManagerDataSource;
import com.mchange.v2.c3p0.PooledDataSource;
import com.mchange.v2.c3p0.PoolBackedDataSource;
import com.mchange.v2.c3p0.WrapperConnectionPoolDataSource;
import com.mchange.v2.c3p0.impl.AbstractPoolBackedDataSource;
import com.mchange.v2.log.MLog;
import com.mchange.v2.log.MLogger;
import com.mchange.v2.log.MLevel;
import com.mchange.v2.management.ManagementUtils;

public class DynamicPooledDataSourceManagerMBean implements DynamicMBean
{
    final static MLogger logger = MLog.getLogger( DynamicPooledDataSourceManagerMBean.class );

    final static Set HIDE_PROPS;
    final static Set HIDE_OPS;
    final static Set FORCE_OPS;
    
    final static Set FORCE_READ_ONLY_PROPS;

    static
    {
        Set hpTmp = new HashSet();
        hpTmp.add("connectionPoolDataSource");
        hpTmp.add("nestedDataSource");
        hpTmp.add("reference");
        hpTmp.add("connection");
        hpTmp.add("password");
        hpTmp.add("pooledConnection");
        hpTmp.add("properties");
        hpTmp.add("logWriter");
        hpTmp.add("lastAcquisitionFailureDefaultUser");
        hpTmp.add("lastCheckoutFailureDefaultUser");
        hpTmp.add("lastCheckinFailureDefaultUser");
        hpTmp.add("lastIdleTestFailureDefaultUser");
        hpTmp.add("lastConnectionTestFailureDefaultUser");
        HIDE_PROPS = Collections.unmodifiableSet( hpTmp );
        
	Class[] userPassArgs = new Class[] { String.class, String.class };
        Set hoTmp = new HashSet();
        try
        {
            hoTmp.add(PooledDataSource.class.getMethod("close", new Class[] { boolean.class }) );
            hoTmp.add(PooledDataSource.class.getMethod("getConnection", userPassArgs ) );

            hoTmp.add(PooledDataSource.class.getMethod("getLastAcquisitionFailure", userPassArgs ) );
            hoTmp.add(PooledDataSource.class.getMethod("getLastCheckinFailure", userPassArgs ) );
            hoTmp.add(PooledDataSource.class.getMethod("getLastCheckoutFailure", userPassArgs ) );
            hoTmp.add(PooledDataSource.class.getMethod("getLastIdleTestFailure", userPassArgs ) );
            hoTmp.add(PooledDataSource.class.getMethod("getLastConnectionTestFailure", userPassArgs ) );
        }
        catch (Exception e)
        {
            logger.log(MLevel.WARNING, "Tried to hide an operation from being exposed by mbean, but failed to find the operation!", e);
        }
        HIDE_OPS = Collections.unmodifiableSet(hoTmp);
        
        Set fropTmp = new HashSet();
        fropTmp.add("identityToken");
        FORCE_READ_ONLY_PROPS = Collections.unmodifiableSet(fropTmp);

	Set foTmp = new HashSet();
	FORCE_OPS = Collections.unmodifiableSet(foTmp);
    }

    final static MBeanOperationInfo[] OP_INFS = extractOpInfs();

    MBeanInfo info = null;

    PooledDataSource pds;
    String mbeanName;
    MBeanServer mbs;
    
    ConnectionPoolDataSource cpds;
    DataSource unpooledDataSource;

    //attr names to attr infos
    Map pdsAttrInfos;               
    Map cpdsAttrInfos;              
    Map unpooledDataSourceAttrInfos;
    
    PropertyChangeListener pcl = new PropertyChangeListener()
    {
        public void propertyChange(PropertyChangeEvent evt)
        {
            String propName = evt.getPropertyName();
            Object val = evt.getNewValue();

            if ("nestedDataSource".equals(propName) || "connectionPoolDataSource".equals(propName))
                reinitialize();
        }
    };

    public DynamicPooledDataSourceManagerMBean(PooledDataSource pds, String mbeanName, MBeanServer mbs)
        throws Exception
    { 
        this.pds = pds; 
        this.mbeanName = mbeanName;
        this.mbs = mbs;
        
        if (pds instanceof ComboPooledDataSource)
            /* do nothing */;
        else if (pds instanceof AbstractPoolBackedDataSource)
            ((AbstractPoolBackedDataSource) pds).addPropertyChangeListener(pcl);
        else
            logger.warning(this + "managing an unexpected PooledDataSource. Only top-level attributes will be available. PooledDataSource: " + pds);
        
        Exception e = reinitialize();
        if (e != null) 
            throw e;
    }
    
    private synchronized Exception reinitialize()
    {
        try
        {
            // for ComboPooledDataSource, everything we care about is exposed via the PooledDataSource
            // for other implementations, we have to pay attention to nested DataSources
            if (!(pds instanceof ComboPooledDataSource) && pds instanceof AbstractPoolBackedDataSource)
            {
                if (this.cpds instanceof WrapperConnectionPoolDataSource) //implies non-null, this is a reinit
                    ((WrapperConnectionPoolDataSource) this.cpds).removePropertyChangeListener(pcl);
                
                
                // yeah, we reassign instantly, but for my comfort...
                this.cpds = null;
                this.unpooledDataSource = null;
                
                this.cpds = ((AbstractPoolBackedDataSource) pds).getConnectionPoolDataSource();

                if (cpds instanceof WrapperConnectionPoolDataSource)
                {
                    this.unpooledDataSource = ((WrapperConnectionPoolDataSource) cpds).getNestedDataSource();
                    ((WrapperConnectionPoolDataSource) this.cpds).addPropertyChangeListener(pcl);
                }
            }

            pdsAttrInfos = extractAttributeInfos( pds );
            cpdsAttrInfos = extractAttributeInfos( cpds );
            unpooledDataSourceAttrInfos = extractAttributeInfos( unpooledDataSource );

            Set allAttrNames = new HashSet();
            allAttrNames.addAll(pdsAttrInfos.keySet());
            allAttrNames.addAll(cpdsAttrInfos.keySet());
            allAttrNames.addAll(unpooledDataSourceAttrInfos.keySet());

            Set allAttrs = new HashSet();
            for(Iterator ii = allAttrNames.iterator(); ii.hasNext();)
            {
                String name = (String) ii.next();
                Object attrInfo;
                attrInfo = pdsAttrInfos.get(name);
                if (attrInfo == null)
                    attrInfo = cpdsAttrInfos.get(name);
                if (attrInfo == null)
                    attrInfo = unpooledDataSourceAttrInfos.get(name);
                allAttrs.add(attrInfo);
            }

            String className = this.getClass().getName();
            MBeanAttributeInfo[] attrInfos = (MBeanAttributeInfo[]) allAttrs.toArray(new MBeanAttributeInfo[ allAttrs.size() ]);
            Class[] ctorArgClasses = {PooledDataSource.class, String.class, MBeanServer.class};
            MBeanConstructorInfo[] constrInfos 
               = new MBeanConstructorInfo[] { new MBeanConstructorInfo("Constructor from PooledDataSource", this.getClass().getConstructor(ctorArgClasses)) };
            this.info = new MBeanInfo( this.getClass().getName(),
                            "An MBean to monitor and manage a PooledDataSource",
                            attrInfos,
                            constrInfos,
                            OP_INFS,
                            null);
            
            // we need to reregister when the attributes we support may have changed, to be sure
            // that the MBeanInfo is reread.
            try
            {
                ObjectName oname = ObjectName.getInstance( mbeanName );
                if (mbs.isRegistered( oname ))
                {
                    mbs.unregisterMBean( oname );
                    if (logger.isLoggable(MLevel.FINER))
                        logger.log(MLevel.FINER, "MBean: " + mbeanName + " unregistered, in order to be reregistered after update.");
                }
                mbs.registerMBean( this, oname );
                if (logger.isLoggable(MLevel.FINER))
                    logger.log(MLevel.FINER, "MBean: " + mbeanName + " registered.");
                
                return null;
            }
            catch (Exception e)
            {
                if ( logger.isLoggable(MLevel.WARNING) )
                    logger.log(MLevel.WARNING, 
                               "An Exception occurred while registering/reregistering mbean " + mbeanName +
                               ". MBean may not be registered, or may not work properly.",
                               e );
                return e;
            }
        }
        catch (NoSuchMethodException e)
        {
            if (logger.isLoggable(MLevel.SEVERE))
                logger.log( MLevel.SEVERE,
                                "Huh? We can't find our own constructor?? The one we're in?",
                                e);
            return e;
        }
    }

    // this method is fragile, makes assumptions that may have to change with
    // the PooledDataSource interface. It presumes that methods that look like
    // JavaBean properties should be skipped as attributes, that methods with
    // two string arguments are always username and password, that methods with
    // a return value are simple getters, while void methods are modifiers. At the
    // time of this writing, these assumptions all hold for PooledDataSource.
    // But beware the future.
    private static MBeanOperationInfo[] extractOpInfs()
    {
        MBeanParameterInfo user = new MBeanParameterInfo("user", "java.lang.String", "The database username of a pool-owner.");
        MBeanParameterInfo pwd = new MBeanParameterInfo("password", "java.lang.String", "The database password of a pool-owner.");
        MBeanParameterInfo[] userPass = {user, pwd};
        MBeanParameterInfo[] empty = {};

        Method[] meths = PooledDataSource.class.getMethods();
        Set attrInfos = new TreeSet(ManagementUtils.OP_INFO_COMPARATOR);

        for (int i = 0; i < meths.length; ++i)
        {
            Method meth = meths[i];
            if (HIDE_OPS.contains(meth))
                continue;
            
            String mname = meth.getName();
            Class[] params = meth.getParameterTypes();

	    if (! FORCE_OPS.contains(mname))
		{
		    //get rid of things we'd have picked up as attributes
		    if (mname.startsWith("set") && params.length == 1)
			continue;
		    if ((mname.startsWith("get") || mname.startsWith("is")) && params.length == 0)
			continue;
		}

            Class retType = meth.getReturnType();
            int impact = (retType == void.class ? MBeanOperationInfo.ACTION : MBeanOperationInfo.INFO);
            MBeanParameterInfo[] pi;
            if (params.length == 2 && params[0] == String.class && params[1] == String.class)
                pi = userPass;
            else if (params.length == 0)
                pi = empty;
            else
                pi = null;

            MBeanOperationInfo opi;
            if (pi != null)
                opi = new MBeanOperationInfo( mname, // name
                                null,  // desc
                                pi,
                                retType.getName(),
                                impact );
            else
            {
                //System.err.println("autobuilding opi from meth " + meth);
                opi = new MBeanOperationInfo(meth.toString(), meth);
            }

            //System.err.println("Created MBeanOperationInfo: " + opi + " [" + opi.getName() + ']');
            attrInfos.add( opi );
        }

        return (MBeanOperationInfo[]) attrInfos.toArray( new MBeanOperationInfo[ attrInfos.size() ] );
    }

    public synchronized Object getAttribute(String attr) throws AttributeNotFoundException, MBeanException, ReflectionException
    {
        try
        {
            AttrRec rec = attrRecForAttribute(attr);
            if (rec == null)
                throw new AttributeNotFoundException(attr);
            else
            {
                MBeanAttributeInfo ai = rec.attrInfo;
                if (! ai.isReadable() )
                    throw new IllegalArgumentException(attr + " not readable.");
                else
                {
                    String name = ai.getName();
                    String pfx = ai.isIs() ? "is" : "get";
                    String mname = pfx + Character.toUpperCase(name.charAt(0)) + name.substring(1);
                    Object target = rec.target; 
                    Method m = target.getClass().getMethod(mname, null);
                    return m.invoke(target, null);
                }
            }
        }
        catch (Exception e)
        {
            if (logger.isLoggable(MLevel.WARNING))
                logger.log(MLevel.WARNING, "Failed to get requested attribute: " + attr, e);
            throw new MBeanException(e);
        }
    }

    public synchronized AttributeList getAttributes(String[] attrs)
    {
        AttributeList al = new AttributeList();
        for (int i = 0, len = attrs.length; i < len; ++i)
        {
            String attr = attrs[i];
            try
            {
                Object val = getAttribute(attr);
                al.add(new Attribute(attr, val));
            }
            catch (Exception e)
            {
                if (logger.isLoggable(MLevel.WARNING))
                    logger.log(MLevel.WARNING, "Failed to get requested attribute (for list): " + attr, e);
            }
        }
        return al;
    }

    private AttrRec attrRecForAttribute(String attr)
    {
        assert (Thread.holdsLock(this));
        
        if (pdsAttrInfos.containsKey(attr))
            return new AttrRec(pds, (MBeanAttributeInfo) pdsAttrInfos.get(attr));
        else if (cpdsAttrInfos.containsKey(attr))
            return new AttrRec(cpds, (MBeanAttributeInfo) cpdsAttrInfos.get(attr));
        else if (unpooledDataSourceAttrInfos.containsKey(attr))
            return new AttrRec(unpooledDataSource, (MBeanAttributeInfo) unpooledDataSourceAttrInfos.get(attr));
        else
            return null;
    }

    public synchronized MBeanInfo getMBeanInfo()
    { 
        if (info == null)
            reinitialize();
        return info; 
    }

    public synchronized Object invoke(String operation, Object[] paramVals, String[] signature) throws MBeanException, ReflectionException
    {
        try
        {
            int slen = signature.length;
            Class[] paramTypes = new Class[ slen ];
            for (int i = 0; i < slen; ++i)
                paramTypes[i] = ClassUtils.forName( signature[i] );
            
            //all operations should be on pds
            Method m = pds.getClass().getMethod(operation, paramTypes);
            return m.invoke(pds, paramVals);
        }
        catch (NoSuchMethodException e)
        {
            // although not generally legal as of the latest JMX spec,
            // someone could be trying to work with attributes through
            // invoke. If so, we try to deal
            try
            {
            boolean two = false;
            if (signature.length == 0 && ( operation.startsWith("get") || (two = operation.startsWith("is")) ))
            {
                int i = two ? 2 : 3;
                String attr = Character.toLowerCase(operation.charAt(i)) + operation.substring(i + 1);
                return getAttribute( attr );
            }
            else if (signature.length == 1 && operation.startsWith("set"))
            {
                setAttribute(new Attribute(Character.toLowerCase(operation.charAt(3)) + operation.substring(4), paramVals[0]));
                return null;
            }
            else
                throw new MBeanException(e);
            }
            catch (Exception e2)
            { throw new MBeanException(e2); }
        }
        catch (Exception e)
        { throw new MBeanException(e); }
    }

    public synchronized void setAttribute(Attribute attrObj) throws AttributeNotFoundException, InvalidAttributeValueException, MBeanException, ReflectionException
    {
        try
        {
            String attr = attrObj.getName();
            
            if (attr == "factoryClassLocation") // special case
            {
                if (pds instanceof ComboPooledDataSource)
                {
                    ((ComboPooledDataSource) pds).setFactoryClassLocation((String) attrObj.getValue());
                    return;
                }
                else if (pds instanceof AbstractPoolBackedDataSource)
                {
                    String val = (String) attrObj.getValue();
                    AbstractPoolBackedDataSource apbds = ((AbstractPoolBackedDataSource) pds);
                    apbds.setFactoryClassLocation( val );
                    ConnectionPoolDataSource checkDs1 = apbds.getConnectionPoolDataSource();
                    if (checkDs1 instanceof WrapperConnectionPoolDataSource)
                    {
                        WrapperConnectionPoolDataSource wcheckDs1 = (WrapperConnectionPoolDataSource) checkDs1;
                        wcheckDs1.setFactoryClassLocation( val );
                        DataSource checkDs2 = wcheckDs1.getNestedDataSource();
                        if (checkDs2 instanceof DriverManagerDataSource)
                            ((DriverManagerDataSource) checkDs2).setFactoryClassLocation( val );
                    }
                    return;
                }
                // else try treating factoryClassLocation like any other attribute
                // on the presumption that some future, unexpected DataSource that
                // exposes this property will not require the property to be set at
                // multiple levels, as PoolBackedDataSource does...
            }
            
            AttrRec rec = attrRecForAttribute(attr);
            if (rec == null)
                throw new AttributeNotFoundException(attr);
            else
            {
                MBeanAttributeInfo ai = rec.attrInfo;
                if (! ai.isWritable() )
                    throw new IllegalArgumentException(attr + " not writable.");
                else
                {
                    Class attrType = ClassUtils.forName( rec.attrInfo.getType() );
                    String name = ai.getName();
                    String pfx = "set";
                    String mname = pfx + Character.toUpperCase(name.charAt(0)) + name.substring(1);
                    Object target = rec.target; 
                    Method m = target.getClass().getMethod(mname, new Class[] {attrType});
                    m.invoke(target, new Object[] { attrObj.getValue() });
                    
                    // if we were unable to set this attribute directly in the PooledDataSource,
                    // we are updating a property of a nested DataSource, and we should reset
                    // the pool manager of the PooledDataSource implementation so that these
                    // properties are reread and the changes take effect.
                    if (target != pds)
                    {
                         if (pds instanceof AbstractPoolBackedDataSource)
                            ((AbstractPoolBackedDataSource) pds).resetPoolManager(false);
                         else if (logger.isLoggable(MLevel.WARNING))
                             logger.warning("MBean set a nested ConnectionPoolDataSource or DataSource parameter on an unknown PooledDataSource type. " + 
                                             "Could not reset the pool manager, so the changes may not take effect. " + "" +
                                              "c3p0 may need to be updated for PooledDataSource type " + pds.getClass() + ".");
                             
                    }
                }
            }
        }
        catch (Exception e)
        {
            if (logger.isLoggable(MLevel.WARNING))
                logger.log(MLevel.WARNING, "Failed to set requested attribute: " + attrObj, e);
            throw new MBeanException(e);
        }
    }

    public synchronized AttributeList setAttributes(AttributeList al)
    {
        AttributeList out = new AttributeList();
        for (int i = 0, len = al.size(); i < len; ++i)
        {
            Attribute attrObj = (Attribute) al.get(i);
            
            try
            {
                this.setAttribute( attrObj );
                out.add(attrObj);
            }
            catch (Exception e)
            {
                if (logger.isLoggable(MLevel.WARNING))
                    logger.log(MLevel.WARNING, "Failed to set requested attribute (from list): " + attrObj, e);
            }
        }
        return out;
    }

    private static Map extractAttributeInfos(Object bean)
    {
        if ( bean != null)
        {
            try
            {
                Map out = new HashMap();
                BeanInfo beanInfo = Introspector.getBeanInfo( bean.getClass(), Object.class ); //so we don't see getClass() as a property
                PropertyDescriptor[] pds = beanInfo.getPropertyDescriptors();
                //System.err.println("ignoreProps: " + ignoreProps );
                for( int i = 0, len = pds.length; i < len; ++i)
                {
                    PropertyDescriptor pd = pds[i];

                    String name;
                    String desc;
                    Method getter;
                    Method setter;

                    name = pd.getName();

                    if (HIDE_PROPS.contains( name ))
                        continue;

                    desc = getDescription( name );
                    getter = pd.getReadMethod();
                    setter = pd.getWriteMethod();
                    
                    if (FORCE_READ_ONLY_PROPS.contains(name))
                        setter = null;

                    /*
                     * Note that it's not a problem that these
                     * getters and setters are not against this class
                     * the MBeanAttributInfo just uses the method
                     * names and attribute type to construct itself,
                     * and does not hold the methods themselves for
                     * future invocation.
                     */

                    try
                    {
                        out.put( name, new MBeanAttributeInfo(name, desc, getter, setter) );
                    }
                    catch (javax.management.IntrospectionException e)
                    {
                        if (logger.isLoggable( MLevel.WARNING ))
                            logger.log( MLevel.WARNING, "IntrospectionException while setting up MBean attribute '" + name + "'", e);
                    }
                }

                return Collections.synchronizedMap(out);
            }
            catch (java.beans.IntrospectionException e)
            {
                if (logger.isLoggable( MLevel.WARNING ))
                    logger.log( MLevel.WARNING, "IntrospectionException while setting up MBean attributes for " + bean, e);
                return Collections.EMPTY_MAP;
            }
        }
        else
            return Collections.EMPTY_MAP;
    }

    // TODO: use a ResourceBundle for attribute descriptions.
    // (Extra credit -- build from xml file, build docs same way)
    private static String getDescription(String attrName)
    { return null; }

    private static class AttrRec
    {
        Object target;
        MBeanAttributeInfo attrInfo;
    
        AttrRec(Object target, MBeanAttributeInfo attrInfo)
        {
            this.target = target;
            this.attrInfo = attrInfo;
        }
    }

}
