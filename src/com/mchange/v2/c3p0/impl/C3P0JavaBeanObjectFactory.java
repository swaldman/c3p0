package com.mchange.v2.c3p0.impl;

import java.lang.reflect.Constructor;
import java.util.Map;
import java.util.Set;
import com.mchange.v2.c3p0.C3P0Registry;
import com.mchange.v2.naming.JavaBeanObjectFactory;

public class C3P0JavaBeanObjectFactory extends JavaBeanObjectFactory
{
    private final static Class[]  CTOR_ARG_TYPES = new Class[] { boolean.class };
    private final static Object[] CTOR_ARGS      = new Object[] { Boolean.FALSE };

    protected Object createBlankInstance(Class beanClass) throws Exception
    {
	if ( IdentityTokenized.class.isAssignableFrom( beanClass ) )
	    {
		Constructor ctor = beanClass.getConstructor( CTOR_ARG_TYPES );
		return ctor.newInstance( CTOR_ARGS );
	    }
	else
	    return super.createBlankInstance( beanClass );
    }

    protected Object findBean(Class beanClass, Map propertyMap, Set refProps ) throws Exception
    {
	Object out = super.findBean( beanClass, propertyMap, refProps );
	if (out instanceof IdentityTokenized)
	    out = C3P0Registry.reregister( (IdentityTokenized) out );
	//System.err.println("--> findBean()");
	//System.err.println(out);
	return out;
    }
}
