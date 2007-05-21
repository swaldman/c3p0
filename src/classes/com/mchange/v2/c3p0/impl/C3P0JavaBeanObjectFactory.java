/*
 * Distributed as part of c3p0 v.0.9.1.2
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
