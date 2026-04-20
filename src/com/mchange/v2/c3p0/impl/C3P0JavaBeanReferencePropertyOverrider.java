package com.mchange.v2.c3p0.impl;

import com.mchange.v2.c3p0.PoolBackedDataSource;

import java.util.Map;
import javax.naming.RefAddr;
import javax.naming.StringRefAddr;
import com.mchange.v2.cfg.PropertiesConfig;
import com.mchange.v2.naming.JavaBeanReferencePropertyOverrider;

import com.mchange.v2.c3p0.AbstractComboPooledDataSource;

public class C3P0JavaBeanReferencePropertyOverrider implements JavaBeanReferencePropertyOverrider
{
    public static C3P0JavaBeanReferencePropertyOverrider INSTANCE = new C3P0JavaBeanReferencePropertyOverrider();

    private boolean extensionsDataSource(Class beanClass)
    {
        return
            PoolBackedDataSource.class.isAssignableFrom(beanClass) ||
            AbstractComboPooledDataSource.class.isAssignableFrom(beanClass);
    }

    public RefAddr overrideRefAddr(Class beanClass, PropertiesConfig pcfg, String propName, Class propType, Object val) throws Exception // null means don't override encoding
    {
        if ( extensionsDataSource(beanClass) && "extensions".equals(propName) && propType == Map.class)
            {
                return new StringRefAddr( propName, C3P0ImplUtils.stringifyExtensions((Map) val) );
            }
        else
            {
                //System.err.println("Create no override for " + propName + "; assignable test: " + extensionsDataSource(beanClass) + "; type test: " + (propType == Map.class));
                return null;
            }
    }
    public Object overrideDecodeRefAddr(Class beanClass, PropertiesConfig pcfg, String propName, Class propType, RefAddr refAddr) throws Exception // null means don't override decoding
    {
        if ( extensionsDataSource(beanClass) && "extensions".equals(propName) && propType == Map.class)
            {
                return C3P0ImplUtils.unstringifyExtensions((String) refAddr.getContent());
            }
        else
            {
                //System.err.println("No overridden decode for " + propName);
                return null;
            }
    }

    private C3P0JavaBeanReferencePropertyOverrider()
    {}
}
