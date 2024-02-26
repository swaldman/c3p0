package com.mchange.v2.c3p0;

import java.sql.Statement;
import java.sql.SQLException;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;

/**
 *  <p><b>Most clients need never use or know about this interface -- c3p0-provided Statements
 *  can be treated like any other Statement.</b></p>
 *
 *  <p>An interface implemented by proxy Connections returned
 *  by c3p0 PooledDataSources. It provides protected access to the underlying
 *  dbms-vendor specific Connection, which may be useful if you want to
 *  access non-standard API offered by your jdbc driver.
 */
public interface C3P0ProxyStatement extends Statement
{
    /**
     *  A token representing an unwrapped, unproxied jdbc Connection
     *  for use in {@link #rawStatementOperation}
     */
    public final static Object RAW_STATEMENT = new Object();
    
    /**
     *  <p>Allows one to work with the unproxied, raw vendor-provided Statement . Some 
     *  database companies never got over the "common interfaces mean
     *  no more vendor lock-in!" thing, and offer non-standard API
     *  on their Statements. This method permits you to "pierce" the
     *  connection-pooling layer to call non-standard methods on the
     *  original Statement, or to pass the original Statement to 
     *  functions that are not implementation neutral.</p>
     *
     *  <p>To use this functionality, you'll need to cast a Statement
     *  retrieved from a c3p0-provided Connection to a 
     *  C3P0ProxyStatement.</p>
     *
     *  <p>This method works by making a reflective call of method <code>m</code> on
     *  Object <code>target</code> (which may be null for static methods), passing
     *  and argument list <code>args</code>. For the method target, or for any argument,
     *  you may substitute the special token <code>C3P0ProxyStatement.RAW_STATEMENT</code></p>
     *
     *  <p>Any ResultSets returned by the operation will be proxied
     *  and c3p0-managed, meaning that these resources will be automatically closed 
     *  if the user does not close them first when this Statement is closed or checked
     *  into the statement cache. <b>Any other resources returned by the operation are the user's
     *  responsibility to clean up!</b></p>
     *
     *  <p>If you have turned statement pooling on, incautious use of this method can corrupt the 
     *  PreparedStatement cache, by breaking the invariant
     *  that all cached PreparedStatements should be equivalent to a PreparedStatement newly created
     *  with the same arguments to prepareStatement(...) or prepareCall(...). If your vendor supplies API
     *  that allows you to modify the state or configuration of a Statement in some nonstandard way,
     *  and you do not undo this modification prior to closing the Statement or the Connection that
     *  prepared it, future preparers of the same Statement may or may not see your modification,
     *  depending on your use of the cache. Thus, it is inadvisable to use this method to call
     *  nonstandard mutators on PreparedStatements if statement pooling is turned on.. 
     */
    public Object rawStatementOperation(Method m, Object target, Object[] args)
	throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, SQLException;
}
