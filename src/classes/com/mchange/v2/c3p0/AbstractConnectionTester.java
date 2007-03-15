/*
 * Distributed as part of c3p0 v.0.9.1.1
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
 *    <li>Override only the two abstract methods</li>
 *    <ul>
 *       <li><tt>public int activeCheckConnection(Connection c, String preferredTestQuery, Throwable[] rootCauseOutParamHolder)</tt></li>
 *       <li><tt>public int statusOnException(Connection c, Throwable t, String preferredTestQuery, Throwable[] rootCauseOutParamHolder)</tt></li>
 *    </ul>
 *    <li>Take care to ensure that your methods are defined to allow <tt>preferredTestQuery</tt> and 
 *    <tt>rootCauseOutParamHolder</tt> to be <tt>null</tt>.</li>
 *  </ol>
 *  
 *  <p>Parameter <tt>rootCauseOutParamHolder</tt> is an optional parameter, which if supplied, will be a Throwable array whose size
 *  it at least one. If a Connection test fails because of some Exception, the Connection tester may set this Exception as the
 *  zero-th element of the array to provide information about why and how the test failed.</p> 
 */
public abstract class AbstractConnectionTester implements UnifiedConnectionTester
{
    /**
     *  Override, but remember that <tt>preferredTestQuery</tt> and <tt>rootCauseOutParamHolder</tt>
     *  can be null.
     */
    public abstract int activeCheckConnection(Connection c, String preferredTestQuery, Throwable[] rootCauseOutParamHolder);

    /**
     *  Override, but remember that <tt>preferredTestQuery</tt> and <tt>rootCauseOutParamHolder</tt>
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
}
