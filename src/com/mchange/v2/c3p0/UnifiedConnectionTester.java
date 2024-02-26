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
public interface UnifiedConnectionTester extends FullQueryConnectionTester
{
    public final static int CONNECTION_IS_OKAY       = ConnectionTester.CONNECTION_IS_OKAY;
    public final static int CONNECTION_IS_INVALID    = ConnectionTester.CONNECTION_IS_INVALID;
    public final static int DATABASE_IS_INVALID      = ConnectionTester.DATABASE_IS_INVALID;
    
    public int activeCheckConnection(Connection c);
    public int activeCheckConnection(Connection c, Throwable[] rootCauseOutParamHolder);
    public int activeCheckConnection(Connection c, String preferredTestQuery);
    public int activeCheckConnection(Connection c, String preferredTestQuery, Throwable[] rootCauseOutParamHolder);

    public int statusOnException(Connection c, Throwable t);
    public int statusOnException(Connection c, Throwable t, Throwable[] rootCauseOutParamHolder);
    public int statusOnException(Connection c, Throwable t, String preferredTestQuery);
    public int statusOnException(Connection c, Throwable t, String preferredTestQuery, Throwable[] rootCauseOutParamHolder);

    public boolean equals(Object o);
    public int hashCode();
}
