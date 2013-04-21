/*
 * Distributed as part of c3p0 v.0.9.5-pre2
 *
 * Copyright (C) 2013 Machinery For Change, Inc.
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

package com.mchange.v2.c3p0.test.junit;

import java.util.*;
import javax.naming.Reference;
import junit.framework.*;
import com.mchange.v2.ser.SerializableUtils;
import com.mchange.v2.beans.BeansUtils;
import com.mchange.v2.naming.ReferenceableUtils;
import com.mchange.v2.c3p0.ComboPooledDataSource;

public final class MarshallUnmarshallDataSourcesJUnitTestCase extends C3P0JUnitTestCaseBase
{
    final static Collection EXCLUDE_PROPS = Arrays.asList( new String[]{
                                    "allUsers",
                                    "connection",
                                    "connectionPoolDataSource",
                                    "effectivePropertyCycleDefaultUser",
                                    "lastCheckinFailureDefaultUser",
                                    "lastCheckoutFailureDefaultUser",
                                    "lastConnectionTestFailureDefaultUser",
                                    "lastIdleTestFailureDefaultUser",
									"logWriter",
									"numBusyConnections",
									"numBusyConnectionsAllUsers",
									"numBusyConnectionsDefaultUser",
									"numConnections",
									"numConnectionsAllUsers",
									"numConnectionsDefaultUser",
                                    "numFailedCheckinsDefaultUser",
                                    "numFailedCheckoutsDefaultUser",
                                    "numFailedIdleTestsDefaultUser",
									"numIdleConnections",
									"numIdleConnectionsAllUsers",
									"numIdleConnectionsDefaultUser",
									"numUnclosedOrphanedConnections",
									"numUnclosedOrphanedConnectionsAllUsers",
									"numUnclosedOrphanedConnectionsDefaultUser",
									"numUserPools",
				    "propertyChangeListeners",
                                    "startTimeMillisDefaultUser",
                                    "statementCacheNumCheckedOutDefaultUser",
                                    "statementCacheNumCheckedOutStatementsAllUsers",
                                    "statementCacheNumConnectionsWithCachedStatementsAllUsers",
                                    "statementCacheNumConnectionsWithCachedStatementsDefaultUser",
                                    "statementCacheNumStatementsAllUsers",
                                    "statementCacheNumStatementsDefaultUser",
                                    "threadPoolSize",
                                    "threadPoolNumActiveThreads",
                                    "threadPoolNumIdleThreads",
                                    "threadPoolNumTasksPending",
                                    "threadPoolStackTraces",
                                    "threadPoolStatus",
                                    "upTimeMillisDefaultUser",
				    "vetoableChangeListeners"
                                    } );

    public void testSerializationRoundTrip()
    {
	try
	    {
        cpds.setIdentityToken("poop"); //simulate a never-before-seen data source, so it's a new registration on deserialization
		byte[] pickled = SerializableUtils.toByteArray(cpds);
		ComboPooledDataSource unpickled = (ComboPooledDataSource) SerializableUtils.fromByteArray( pickled );
		assertTrue( "Marshalled and unmarshalled DataSources should have the same properties!\n\n[[[[cpds]]]:\n" + cpds + "\n\n[[[unpickled]]]:\n" + unpickled + "\n\n", 
			    BeansUtils.equalsByAccessibleProperties( cpds, unpickled, EXCLUDE_PROPS ) );
	    }
	catch (Exception e)
	    {
		e.printStackTrace();
		fail( e.getMessage() );
	    }
    }

    public void testRefDerefRoundTrip()
    {
	try
	    {
        cpds.setIdentityToken("scoop"); //simulate a never-before-seen data source, so it's a new registration on deserialization
		Reference ref = cpds.getReference();
		ComboPooledDataSource unpickled = (ComboPooledDataSource) ReferenceableUtils.referenceToObject( ref, null, null, null );
		assertTrue( "Marshalled and unmarshalled DataSources should have the same properties!", 
			    BeansUtils.equalsByAccessibleProperties( cpds, unpickled, EXCLUDE_PROPS ) );
	    }
	catch (Exception e)
	    {
		e.printStackTrace();
		fail( e.getMessage() );
	    }
    }
}
