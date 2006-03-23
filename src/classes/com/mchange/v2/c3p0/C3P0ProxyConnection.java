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


package com.mchange.v2.c3p0;

import java.sql.Connection;
import java.sql.SQLException;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;

/**
 *  <p><b>Most clients need never use or know about this interface -- c3p0-provided Connections
 *  can be treated like any other Connection.</b></p>
 *
 *  <p>An interface implemented by proxy Connections returned
 *  by c3p0 PooledDataSources. It provides protected access to the underlying
 *  dbms-vendor specific Connection, which may be useful if you want to
 *  access non-standard API offered by your jdbc driver.
 */
public interface C3P0ProxyConnection extends Connection
{
    /**
     *  A token representing an unwrapped, unproxied jdbc Connection
     *  for use in {@link #rawConnectionOperation}
     */
    public final static Object RAW_CONNECTION = new Object();
    
    /**
     *  <p>Allows one to work with the unproxied, raw Connection. Some 
     *  database companies never got over the "common interfaces mean
     *  no more vendor lock-in!" thing, and offer non-standard API
     *  on their Connections. This method permits you to "pierce" the
     *  connection-pooling layer to call non-standard methods on the
     *  original Connection, or to pass the original Connections to 
     *  functions that are not implementation neutral.</p>
     *
     *  <p>To use this functionality, you'll need to cast a Connection
     *  retrieved from a c3p0 PooledDataSource to a 
     *  C3P0ProxyConnection.</p>
     *
     *  <p>This method works by making a reflective call of method <tt>m</tt> on
     *  Object <tt>target</tt> (which may be null for static methods), passing
     *  and argument list <tt>args</tt>. For the method target, or for any argument,
     *  you may substitute the special token <tt>C3P0ProxyConnection.RAW_CONNECTION</tt></p>
     *
     *  <p>Any Statements or ResultSets returned by the operation will be proxied
     *  and c3p0-managed, meaning that these resources will be automatically closed 
     *  if the user does not close them first when this Connection is checked back
     *  into the pool. <b>Any other resources returned by the operation are the user's
     *  responsibility to clean up!</b></p>
     *
     *  <p>Incautious use of this method can corrupt the Connection pool, by breaking the invariant
     *  that all checked-in Connections should be equivalent. If your vendor supplies API
     *  that allows you to modify the state or configuration of a Connection in some nonstandard way,
     *  you might use this method to do so, and then check the Connection back into the pool.
     *  When you fetch another Connection from the PooledDataSource, it will be undefined
     *  whether the Connection returned will have your altered configuration, or the default
     *  configuration of a "fresh" Connection. Thus, it is inadvisable to use this method to call
     *  nonstandard mutators. 
     */
    public Object rawConnectionOperation(Method m, Object target, Object[] args)
	throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, SQLException;
}