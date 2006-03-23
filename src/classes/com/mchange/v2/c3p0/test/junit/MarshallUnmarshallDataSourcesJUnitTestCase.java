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
    final static Collection EXCLUDE_PROPS = Arrays.asList( new String[]{"connection",
									"logWriter",
									"numBusyConnections",
									"numBusyConnectionsAllUsers",
									"numBusyConnectionsDefaultUser",
									"numConnections",
									"numConnectionsAllUsers",
									"numConnectionsDefaultUser",
									"numIdleConnections",
									"numIdleConnectionsAllUsers",
									"numIdleConnectionsDefaultUser",
									"numUnclosedOrphanedConnections",
									"numUnclosedOrphanedConnectionsAllUsers",
									"numUnclosedOrphanedConnectionsDefaultUser",
									"numUserPools" } );

    public void testSerializationRoundTrip()
    {
	try
	    {
		byte[] pickled = SerializableUtils.toByteArray(cpds);
		ComboPooledDataSource unpickled = (ComboPooledDataSource) SerializableUtils.fromByteArray( pickled );
		assertTrue( "Marshalled and unmarshalled DataSources should have the same properties!", 
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
