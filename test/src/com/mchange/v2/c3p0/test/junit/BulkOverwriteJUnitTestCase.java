package com.mchange.v2.c3p0.test.junit;

import java.util.*;
import junit.framework.*;

import com.mchange.v2.c3p0.DataSources;

public final class BulkOverwriteJUnitTestCase extends C3P0JUnitTestCaseBase
{
    public void testAllMapBulkOverwrite() throws Exception
    {
        cpds.setMaxPoolSize(99);
        cpds.setDebugUnreturnedConnectionStackTraces(false);
        assertEquals( "Direct setting of maxPoolSize should take effect", cpds.getMaxPoolSize(), 99 );
        assertEquals( "Direct setting of debugUnreturnedConnectionStackTraces should take effect", cpds.isDebugUnreturnedConnectionStackTraces(), false );

        Map m = new HashMap();
        m.put("maxPoolSize", new Integer(27));
        m.put("debugUnreturnedConnectionStackTraces", new Boolean("true"));
        DataSources.overwriteJavaBeanProperties( cpds, m, false );

        assertEquals( "Bulk overwrite of maxPoolSize should take effect", cpds.getMaxPoolSize(), 27 );
        assertEquals( "Bulk overwrite of debugUnreturnedConnectionStackTraces should take effect", cpds.isDebugUnreturnedConnectionStackTraces(), true );

        m.put("debugUnreturnedConnectionStackTraces", "false");
        DataSources.overwriteJavaBeanProperties( cpds, m, false );
        assertEquals( "Bulk overwrite of debugUnreturnedConnectionStackTraces with String but without coercion should not take effect", cpds.isDebugUnreturnedConnectionStackTraces(), true );

        m.put("debugUnreturnedConnectionStackTraces", "false");
        DataSources.overwriteJavaBeanProperties( cpds, m, true );
        assertEquals( "Bulk overwrite of debugUnreturnedConnectionStackTraces with String and with coercion should take effect", cpds.isDebugUnreturnedConnectionStackTraces(), false );
    }

    public void testAllC3P0PrefixedPropertiesBulkOverwrite() throws Exception
    {
        cpds.setMaxPoolSize(99);
        cpds.setDebugUnreturnedConnectionStackTraces(false);
        assertEquals( "Direct setting of maxPoolSize should take effect", cpds.getMaxPoolSize(), 99 );
        assertEquals( "Direct setting of debugUnreturnedConnectionStackTraces should take effect", cpds.isDebugUnreturnedConnectionStackTraces(), false );

        Properties p = new Properties();
        p.setProperty("c3p0.maxPoolSize", "27");
        p.setProperty("c3p0.debugUnreturnedConnectionStackTraces", "true");
        DataSources.overwriteC3P0PrefixedProperties( cpds, p );

        assertEquals( "Bulk overwrite of maxPoolSize should take effect", cpds.getMaxPoolSize(), 27 );
        assertEquals( "Bulk overwrite of debugUnreturnedConnectionStackTraces should take effect", cpds.isDebugUnreturnedConnectionStackTraces(), true );
    }
}
