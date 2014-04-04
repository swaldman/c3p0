/*
 * Distributed as part of c3p0 0.9.5-pre8
 *
 * Copyright (C) 2014 Machinery For Change, Inc.
 *
 * Author: Steve Waldman <swaldman@mchange.com>
 *
 * This library is free software; you can redistribute it and/or modify
 * it under the terms of EITHER:
 *
 *     1) The GNU Lesser General Public License (LGPL), version 2.1, as 
 *        published by the Free Software Foundation
 *
 * OR
 *
 *     2) The Eclipse Public License (EPL), version 1.0
 *
 * You may choose which license to accept if you wish to redistribute
 * or modify this work. You may offer derivatives of this work
 * under the license you have chosen, or you may provide the same
 * choice of license which you have been offered here.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * You should have received copies of both LGPL v2.1 and EPL v1.0
 * along with this software; see the files LICENSE-EPL and LICENSE-LGPL.
 * If not, the text of these licenses are currently available at
 *
 * LGPL v2.1: http://www.gnu.org/licenses/old-licenses/lgpl-2.1.html
 *  EPL v1.0: http://www.eclipse.org/org/documents/epl-v10.php 
 * 
 */

package com.mchange.v2.c3p0.impl;

import java.lang.reflect.*;
import java.util.*;
import com.mchange.v2.c3p0.ConnectionTester;

// all public static methods should have the name of a c3p0 config property and
// return its default value
public final class C3P0Defaults
{
    private final static int MAX_STATEMENTS                             = 0;
    private final static int MAX_STATEMENTS_PER_CONNECTION              = 0;
    private final static int INITIAL_POOL_SIZE                          = 3;  
    private final static int MIN_POOL_SIZE                              = 3;
    private final static int MAX_POOL_SIZE                              = 15;
    private final static int IDLE_CONNECTION_TEST_PERIOD                = 0;  //idle connections never tested
    private final static int MAX_IDLE_TIME                              = 0;  //seconds, 0 means connections never expire
    private final static int PROPERTY_CYCLE                             = 0;  //seconds
    private final static int ACQUIRE_INCREMENT                          = 3;
    private final static int ACQUIRE_RETRY_ATTEMPTS                     = 30;
    private final static int ACQUIRE_RETRY_DELAY                        = 1000; //milliseconds
    private final static int CHECKOUT_TIMEOUT                           = 0;    //milliseconds
    private final static int MAX_ADMINISTRATIVE_TASK_TIME               = 0;    //seconds
    private final static int MAX_IDLE_TIME_EXCESS_CONNECTIONS           = 0;    //seconds
    private final static int MAX_CONNECTION_AGE                         = 0;    //seconds
    private final static int UNRETURNED_CONNECTION_TIMEOUT              = 0;    //seconds
    private final static int STATEMENT_CACHE_NUM_DEFERRED_CLOSE_THREADS = 0;


    private final static boolean BREAK_AFTER_ACQUIRE_FAILURE                 = false;
    private final static boolean TEST_CONNECTION_ON_CHECKOUT                 = false;
    private final static boolean TEST_CONNECTION_ON_CHECKIN                  = false;
    private final static boolean AUTO_COMMIT_ON_CLOSE                        = false;
    private final static boolean FORCE_IGNORE_UNRESOLVED_TXNS                = false;
    private final static boolean USES_TRADITIONAL_REFLECTIVE_PROXIES         = false;
    private final static boolean DEBUG_UNRETURNED_CONNECTION_STACK_TRACES    = false;
    private final static boolean PRIVILEGE_SPAWNED_THREADS                   = false;
    private final static boolean FORCE_USE_NAMED_DRIVER_CLASS                = false;

    private final static int NUM_HELPER_THREADS = 3;

    private final static String AUTOMATIC_TEST_TABLE             = null;
    private final static String CONNECTION_CUSTOMIZER_CLASS_NAME = null;
    private final static String CONNECTION_TESTER_CLASS_NAME     = "com.mchange.v2.c3p0.impl.DefaultConnectionTester";
    private final static String CONTEXT_CLASS_LOADER_SOURCE      = "caller"; // caller | library | none
    private final static String DRIVER_CLASS                     = null;
    private final static String JDBC_URL                         = null;
    private final static String OVERRIDE_DEFAULT_USER            = null;
    private final static String OVERRIDE_DEFAULT_PASSWORD        = null;
    private final static String PASSWORD                         = null;
    private final static String PREFERRED_TEST_QUERY             = null;
    private final static String FACTORY_CLASS_LOCATION           = null;
    private final static String USER_OVERRIDES_AS_STRING         = null;
    private final static String USER                             = null;

    private final static String DATA_SOURCE_NAME                 = null;

    private final static Map EXTENSIONS                          = Collections.emptyMap();

    private final static Set KNOWN_PROPERTIES;

    // we need to initialize this lazily, as DefaultConnectionTester needs to access C3P0Config,
    // which expects this class to be loaded already.
    //
    // MT: protected by class' lock
    // private static ConnectionTester CONNECTION_TESTER = null;

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

    // the arg should just be null, always... 
    // the arg exists only to exclude getKnownProperties from being reflected as a config param whose default value is all the properties
    public static Set getKnownProperties( Object useless )
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

    public static int unreturnedConnectionTimeout()
    { return UNRETURNED_CONNECTION_TIMEOUT; }

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

    public static int statementCacheNumDeferredCloseThreads()
    { return STATEMENT_CACHE_NUM_DEFERRED_CLOSE_THREADS; }

    public static String connectionCustomizerClassName()
    { return CONNECTION_CUSTOMIZER_CLASS_NAME; }

    public static String contextClassLoaderSource()
    { return CONTEXT_CLASS_LOADER_SOURCE; }

    // public synchronized static ConnectionTester connectionTester()
    // { 
    // 	if ( CONNECTION_TESTER == null )
    // 	    CONNECTION_TESTER = new DefaultConnectionTester();
    // 	return CONNECTION_TESTER;
    // }
    //
    // public static String connectionTesterClassName()
    // { return connectionTester().getClass().getName(); }

    public static String connectionTesterClassName()
    { return CONNECTION_TESTER_CLASS_NAME; }

    public static String automaticTestTable()
    { return AUTOMATIC_TEST_TABLE; }

    public static String driverClass()
    { return DRIVER_CLASS; }

    public static boolean forceUseNamedDriverClass()
    { return FORCE_USE_NAMED_DRIVER_CLASS; }

    public static String jdbcUrl()
    { return JDBC_URL; }

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

    public static boolean debugUnreturnedConnectionStackTraces()
    { return DEBUG_UNRETURNED_CONNECTION_STACK_TRACES; }

    public static boolean usesTraditionalReflectiveProxies()
    { return USES_TRADITIONAL_REFLECTIVE_PROXIES; }

    public static boolean privilegeSpawnedThreads()
    { return PRIVILEGE_SPAWNED_THREADS; }

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

    public static int maxAdministrativeTaskTime()
    { return MAX_ADMINISTRATIVE_TASK_TIME; }

    public static int maxIdleTimeExcessConnections()
    { return MAX_IDLE_TIME_EXCESS_CONNECTIONS; }

    public static int maxConnectionAge()
    { return MAX_CONNECTION_AGE; }

    public static String dataSourceName()
    { return DATA_SOURCE_NAME; }

    public static Map extensions()
    { return EXTENSIONS; }
}

