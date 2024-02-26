package com.mchange.v2.c3p0;

import java.sql.Connection;

/**
 *  <p>Having expanded the once-simple ConnectionTester interface to support both
 *  user-specified queries and return of root cause Exceptions (via an out-param),
 *  this interface has grown unnecessarily complex.</p>
 *  
 *  <p>If you wish to implement a custom Connection tester, here is the simple
 *  way to do it</p>
 *  
 *  <ol>
 *    <li>Extend {@link com.mchange.v2.c3p0.AbstractConnectionTester}</li>
 *    <li>
 *        Override only the two abstract methods
 *        <ul>
 *           <li><code>public int activeCheckConnection(Connection c, String preferredTestQuery, Throwable[] rootCauseOutParamHolder)</code></li>
 *           <li><code>public int statusOnException(Connection c, Throwable t, String preferredTestQuery, Throwable[] rootCauseOutParamHolder)</code></li>
 *        </ul>
 *    </li>
 *    <li>Take care to ensure that your methods are defined to allow <code>preferredTestQuery</code> and 
 *    <code>rootCauseOutParamHolder</code> to be <code>null</code>.</li>
 *  </ol>
 *  
 *  <p>Parameter <code>rootCauseOutParamHolder</code> is an optional parameter, which if supplied, will be a Throwable array whose size
 *  it at least one. If a Connection test fails because of some Exception, the Connection tester may set this Exception as the
 *  zero-th element of the array to provide information about why and how the test failed.</p> 
 */
public abstract class AbstractConnectionTester implements UnifiedConnectionTester
{
    /**
     *  Override, but remember that <code>preferredTestQuery</code> and <code>rootCauseOutParamHolder</code>
     *  can be null.
     */
    public abstract int activeCheckConnection(Connection c, String preferredTestQuery, Throwable[] rootCauseOutParamHolder);

    /**
     *  Override, but remember that <code>preferredTestQuery</code> and <code>rootCauseOutParamHolder</code>
     *  can be null.
     */
    public abstract int statusOnException(Connection c, Throwable t, String preferredTestQuery, Throwable[] rootCauseOutParamHolder);

    //usually just leave the rest of these as-is
    public int activeCheckConnection(Connection c)
    { return activeCheckConnection( c, null, null); }

    public int activeCheckConnection(Connection c, Throwable[] rootCauseOutParamHolder)
    { return activeCheckConnection( c, null, rootCauseOutParamHolder); }

    public int activeCheckConnection(Connection c, String preferredTestQuery)
    { return activeCheckConnection( c, preferredTestQuery, null); }

    public int statusOnException(Connection c, Throwable t)
    { return statusOnException( c, t, null, null); }

    public int statusOnException(Connection c, Throwable t, Throwable[] rootCauseOutParamHolder)
    { return statusOnException( c, t, null, rootCauseOutParamHolder); }

    public int statusOnException(Connection c, Throwable t, String preferredTestQuery)
    { return statusOnException( c, t, preferredTestQuery, null); }

    public boolean equals( Object o ) { return this.getClass().equals( o.getClass() ); }
    public int hashCode() { return this.getClass().getName().hashCode(); }
}
