package com.mchange.v2.c3p0.test.junit;

import java.util.*;
import javax.naming.*;
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
				    "parentLogger",
				    "propertyChangeListeners",
				    "reference", //references that yield the same object need not be .equals(...)
                                    "startTimeMillisDefaultUser",
                                    "statementCacheNumCheckedOutDefaultUser",
                                    "statementCacheNumCheckedOutStatementsAllUsers",
                                    "statementCacheNumConnectionsWithCachedStatementsAllUsers",
                                    "statementCacheNumConnectionsWithCachedStatementsDefaultUser",
                                    "statementCacheNumDeferredCloseThreads",
                                    "statementCacheNumStatementsAllUsers",
                                    "statementCacheNumStatementsDefaultUser",
				    "statementDestroyerNumActiveThreads",
				    "statementDestroyerNumConnectionsWithDeferredDestroyStatementsAllUsers",
				    "statementDestroyerNumConnectionsWithDeferredDestroyStatementsDefaultUser",
				    "statementDestroyerNumConnectionsInUseAllUsers",
				    "statementDestroyerNumConnectionsInUseDefaultUser",
				    "statementDestroyerNumDeferredDestroyStatementsAllUsers",
				    "statementDestroyerNumDeferredDestroyStatementsDefaultUser",
				    "statementDestroyerNumIdleThreads",
				    "statementDestroyerNumTasksPending",
				    "statementDestroyerNumThreads",
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

		// we check references specially; comparison by equals(...) doesn't work
		// not all elements have good .equals(...) methods defined, so functionally equivalent RefAddresses yield false
		compareReferences( cpds.getReference(), unpickled.getReference() );

		// sideBySidePrintReferences( cpds.getReference(), unpickled.getReference() );

		assertTrue( "Marshalled and unmarshalled DataSources should have the same properties!\n\n[[[[cpds]]]:\n" + cpds + "\n\n[[[unpickled]]]:\n" + unpickled + "\n\n", 
			    // BeansUtils.equalsByAccessiblePropertiesVerbose( cpds, unpickled, EXCLUDE_PROPS ) );
			    BeansUtils.equalsByAccessibleProperties( cpds, unpickled, EXCLUDE_PROPS ) );
	    }
	catch (Exception e)
	    {
		e.printStackTrace();
		fail( e.getMessage() );
	    }
    }

    private void compareReferences( Reference ref1, Reference ref2 ) throws Exception
    {
	ComboPooledDataSource cpds1 = (ComboPooledDataSource) ReferenceableUtils.referenceToObject( ref1, null, null, null );
	ComboPooledDataSource cpds2 = (ComboPooledDataSource) ReferenceableUtils.referenceToObject( ref2, null, null, null );
	assertTrue( "Marshalled and unmarshalled DataSources references point to the equivalent DataSources",
		    //		    BeansUtils.equalsByAccessiblePropertiesVerbose( cpds1, cpds2, EXCLUDE_PROPS ) );
		    BeansUtils.equalsByAccessibleProperties( cpds1, cpds2, EXCLUDE_PROPS ) );
		
    }

    private void sideBySidePrintReferences( Reference ref1, Reference ref2 ) throws Exception
    {
	System.err.println("SIDE BY SIDE:");

	int sz1 = ref1.size();
	int sz2 = ref2.size();
	if (sz1 != sz2)
	    System.err.println("Sizes differ! sz1: " + sz1 + "; sz2: " +sz2);
	for ( int i = 0, len = Math.max( sz1, sz2 ); i < len; ++i )
	{
	    RefAddr ra1 = (i < sz1) ? ref1.get( i ) : null;
	    RefAddr ra2 = (i < sz2) ? ref2.get( i ) : null;
	    
	    String s1 = ra1 == null ? "XXXXX" : ra1.getContent().toString() + " [" + ra1.getType() + "]";
	    String s2 = ra2 == null ? "XXXXX" : ra2.getContent().toString() + " [" + ra2.getType() + "]";
	    System.err.println( "\t" + s1 + "      " + s2 + "      " + (s1 != null && s1.equals(s2)) );
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
