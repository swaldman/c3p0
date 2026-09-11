package com.mchange.v2.c3p0.impl;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import java.util.Map;
import java.util.Set;
import com.mchange.v2.c3p0.C3P0Registry;
import com.mchange.v2.c3p0.cfg.C3P0CurrentConfigFinder;
import com.mchange.v2.naming.JavaBeanObjectFactory;

public class C3P0JavaBeanObjectFactory extends JavaBeanObjectFactory
{
    private final static Class[]  CTOR_ARG_TYPES = new Class[] { boolean.class };
    private final static Object[] CTOR_ARGS      = new Object[] { Boolean.FALSE };

    public C3P0JavaBeanObjectFactory()
    {
        this.setReferencePropertyOverrider(C3P0JavaBeanReferencePropertyOverrider.INSTANCE);
        this.setConfigFinder(C3P0CurrentConfigFinder.INSTANCE);
    }

    @Override
    protected Object createBlankInstance(Class beanClass) throws Exception
    {
	if ( IdentityTokenized.class.isAssignableFrom( beanClass ) )
	    {
                try
                {
                    Constructor ctor = beanClass.getConstructor( CTOR_ARG_TYPES );
                    return ctor.newInstance( CTOR_ARGS );
                }
                catch (InvocationTargetException e)
	        {
                    // reflective construction wraps whatever the constructor threw, while the
                    // Class.newInstance() this replaces let it propagate. Keep propagating it.
                    Throwable t = e.getCause();
                    if (t instanceof Exception) throw (Exception) t;
                    else if (t instanceof Error) throw (Error) t;
                    else throw e;
                }
	    }
	else
	    return super.createBlankInstance( beanClass );
    }

    @Override
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
