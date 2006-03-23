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


package com.mchange.v2.c3p0.impl;

import java.lang.reflect.*;
import java.util.*;
import com.mchange.v2.c3p0.ConnectionTester;

// all public static methods should have the name of a c3p0 config property and
// return its default value
public final class C3P0Defaults
{
    private final static int MAX_STATEMENTS                = 0;
    private final static int MAX_STATEMENTS_PER_CONNECTION = 0;
    private final static int INITIAL_POOL_SIZE             = 3;   //ignored by present implementation
    private final static int MIN_POOL_SIZE                 = 3;
    private final static int MAX_POOL_SIZE                 = 15;
    private final static int IDLE_CONNECTION_TEST_PERIOD   = -1; //idle connections never tested
    private final static int MAX_IDLE_TIME                 = 0;  //seconds, 0 means connections never expire
    private final static int PROPERTY_CYCLE                = 300; //seconds, but ignored by present implementation
    private final static int ACQUIRE_INCREMENT             = 3;
    private final static int ACQUIRE_RETRY_ATTEMPTS        = 30;
    private final static int ACQUIRE_RETRY_DELAY           = 1000; //milliseconds!
    private final static int CHECKOUT_TIMEOUT              = 0; //milliseconds!

    private final static boolean BREAK_AFTER_ACQUIRE_FAILURE         = false;
    private final static boolean TEST_CONNECTION_ON_CHECKOUT         = false;
    private final static boolean TEST_CONNECTION_ON_CHECKIN          = false;
    private final static boolean AUTO_COMMIT_ON_CLOSE                = false;
    private final static boolean FORCE_IGNORE_UNRESOLVED_TXNS        = false;
    private final static boolean USES_TRADITIONAL_REFLECTIVE_PROXIES = false;

    private final static ConnectionTester CONNECTION_TESTER = new DefaultConnectionTester();

    private final static int NUM_HELPER_THREADS = 3;

    private final static String AUTOMATIC_TEST_TABLE      = null;
    private final static String OVERRIDE_DEFAULT_USER     = null;
    private final static String OVERRIDE_DEFAULT_PASSWORD = null;
    private final static String PASSWORD                  = null;
    private final static String PREFERRED_TEST_QUERY      = null;
    private final static String FACTORY_CLASS_LOCATION    = null;
    private final static String USER_OVERRIDES_AS_STRING  = null;
    private final static String USER                      = null;

    private static Set KNOWN_PROPERTIES;

    static
    {
	Method[] methods = C3P0Defaults.class.getMethods();
	Set s = new HashSet();
	for (int i = 0, len = methods.length; i < len; ++i)
	    {
		Method m = methods[i];
		if (Modifier.isStatic( m.getModifiers() ) && m.getParameterTypes().length == 0)
		    s.add( m.getName() );
	    }
	KNOWN_PROPERTIES = Collections.unmodifiableSet( s );
    }

    public static Set getKnownProperties()
    { return KNOWN_PROPERTIES; }

    public static boolean isKnownProperty( String s )
    { return KNOWN_PROPERTIES.contains( s ); }

    public static int maxStatements()
    { return MAX_STATEMENTS; }

    public static int maxStatementsPerConnection()
    { return MAX_STATEMENTS_PER_CONNECTION; }

    public static int initialPoolSize()
    { return INITIAL_POOL_SIZE; }

    public static int minPoolSize()
    { return MIN_POOL_SIZE; }

    public static int maxPoolSize()
    { return MAX_POOL_SIZE; }

    public static int idleConnectionTestPeriod()
    { return IDLE_CONNECTION_TEST_PERIOD; }

    public static int maxIdleTime()
    { return MAX_IDLE_TIME; }

    public static int propertyCycle()
    { return PROPERTY_CYCLE; }

    public static int acquireIncrement()
    { return ACQUIRE_INCREMENT; }

    public static int acquireRetryAttempts()
    { return ACQUIRE_RETRY_ATTEMPTS; }

    public static int acquireRetryDelay()
    { return ACQUIRE_RETRY_DELAY; }

    public static int checkoutTimeout()
    { return CHECKOUT_TIMEOUT; }

    public static ConnectionTester connectionTester()
    { return CONNECTION_TESTER; }

    public static String connectionTesterClassName()
    { return CONNECTION_TESTER.getClass().getName(); }

    public static String automaticTestTable()
    { return AUTOMATIC_TEST_TABLE; }

    public static int numHelperThreads()
    { return NUM_HELPER_THREADS; }

    public static boolean breakAfterAcquireFailure()
    { return BREAK_AFTER_ACQUIRE_FAILURE; }

    public static boolean testConnectionOnCheckout()
    { return TEST_CONNECTION_ON_CHECKOUT; }

    public static boolean testConnectionOnCheckin()
    { return TEST_CONNECTION_ON_CHECKIN; }

    public static boolean autoCommitOnClose()
    { return AUTO_COMMIT_ON_CLOSE; }

    public static boolean forceIgnoreUnresolvedTransactions()
    { return FORCE_IGNORE_UNRESOLVED_TXNS; }

    public static boolean usesTraditionalReflectiveProxies()
    { return USES_TRADITIONAL_REFLECTIVE_PROXIES; }

    public static String preferredTestQuery()
    { return PREFERRED_TEST_QUERY; }

    public static String userOverridesAsString()
    { return USER_OVERRIDES_AS_STRING; }

    public static String factoryClassLocation()
    { return FACTORY_CLASS_LOCATION; }

    public static String overrideDefaultUser()
    { return OVERRIDE_DEFAULT_USER; }

    public static String overrideDefaultPassword()
    { return OVERRIDE_DEFAULT_PASSWORD; }

    public static String user()
    { return USER; }

    public static String password()
    { return PASSWORD; }
}

