package com.mchange.v2.c3p0.test.cfgxml;

import junit.framework.TestCase;

import com.mchange.v2.c3p0.test.cfglib.CfgLoaderScenario;

import java.io.File;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 *  A <code>c3p0-xml:</code> classloader path may be satisfied by more than one resource — one per
 *  classpath entry — and all of them are read and merged, rather than only the first.
 *
 *  <p>This is the same rule, and the same reasoning, as for properties resources: it lets a
 *  framework jar ship a <code>c3p0-config.xml</code> that an application jar partly overrides,
 *  without the application having to restate everything. Precedence follows the classpath, so the
 *  winner is the resource <code>Class.getResourceAsStream</code> would have returned.</p>
 *
 *  <p>Testing it needs several classpath entries carrying one path, and the source resolves
 *  resources through its own ClassLoader — so each case runs in a CfgScenario built over temp
 *  roots, exactly as MergedPropertiesResourcesJUnitTestCase does for properties. The harness is
 *  that test's; only the file contents and the source class differ.</p>
 */
public class MergedC3P0XmlResourcesJUnitTestCase extends TestCase
{
    private final static String PATH     = "/merge-probe-c3p0-config.xml";
    private final static String FILENAME = "merge-probe-c3p0-config.xml";

    private List<File> roots;

    @Override
    protected void setUp() throws Exception
    { roots = new ArrayList<File>(); }

    @Override
    protected void tearDown() throws Exception
    {
        for ( File root : roots )
        {
            File f = new File( root, FILENAME );
            if ( f.exists() ) f.delete();
            root.delete();
        }
    }

    /**
     *  A classpath root carrying the probe document, with the given <code>&lt;property&gt;</code>
     *  lines inside <code>&lt;default-config&gt;</code>. Returned in creation order, which is the
     *  order they go on the classpath — so the first made is the highest priority.
     */
    private File root( String... propertyLines ) throws Exception
    {
        File dir = File.createTempFile( "merge-xml-root", "" );
        dir.delete();
        dir.mkdirs();
        PrintWriter pw = new PrintWriter( new File( dir, FILENAME ), "UTF-8" );
        try
        {
            pw.println( "<c3p0-config><default-config>" );
            for ( String line : propertyLines ) pw.println( line );
            pw.println( "</default-config></c3p0-config>" );
        }
        finally { pw.close(); }
        roots.add( dir );
        return dir;
    }

    private static String prop( String name, String value )
    { return "  <property name=\"" + name + "\">" + value + "</property>"; }

    /** What the source produced, rendered so it can be asserted on across the loader boundary. */
    private static class Result
    {
        Properties     properties;
        List<String[]> items;       // [level, text, exceptionClassName]
        Throwable      thrown;
    }

    @SuppressWarnings("unchecked")
    private Result read( String identifier, File... extraRoots ) throws Exception
    {
        CfgLoaderScenario s = CfgLoaderScenario.open( extraRoots );
        try
        {
            Class<?> srcClass = s.loadClass( "com.mchange.v2.c3p0.cfg.C3P0XmlPropertiesConfigSource" );
            Object   src      = srcClass.getConstructor().newInstance();
            Method   m        = srcClass.getMethod( "propertiesFromSource", String.class );

            Result out = new Result();
            try
            {
                Object parse = m.invoke( src, identifier );
                out.properties = (Properties) parse.getClass().getMethod( "getProperties" ).invoke( parse );
                out.items = s.renderLogItems( (List) parse.getClass().getMethod( "getDelayedLogItems" ).invoke( parse ) );
            }
            catch ( InvocationTargetException ite )
            {
                out.thrown = ite.getCause();
                out.items  = new ArrayList<String[]>();
            }
            return out;
        }
        finally { s.closeQuietly(); }
    }

    private static boolean warns( Result r, String fragment )
    {
        for ( String[] item : r.items )
            if ( "WARNING".equals( item[0] ) && item[1] != null && item[1].contains( fragment ) ) return true;
        return false;
    }

    // ==================== the merge ====================

    /** The point of the exercise: keys from every resource at the path, not just the first. */
    public void testKeysFromEveryResourceAreMerged() throws Exception
    {
        File first  = root( prop( "maxPoolSize", "1" ), prop( "shared", "first-wins" ) );
        File second = root( prop( "minPoolSize", "2" ), prop( "acquireIncrement", "3" ) );

        Result r = read( "c3p0-xml:" + PATH, first, second );

        assertNull( "" + r.thrown, r.thrown );
        assertEquals( "a key only the higher-priority document has", "1", r.properties.getProperty( "c3p0.maxPoolSize" ) );
        assertEquals( "a key only the lower-priority document has -- what the merge adds",
                      "2", r.properties.getProperty( "c3p0.minPoolSize" ) );
        assertEquals( "3", r.properties.getProperty( "c3p0.acquireIncrement" ) );
    }

    /** Precedence is classpath order, earliest wins. */
    public void testTheEarliestClasspathEntryWins() throws Exception
    {
        File first  = root( prop( "maxPoolSize", "from-first" ) );
        File second = root( prop( "maxPoolSize", "from-second" ) );

        assertEquals( "from-first",
                      read( "c3p0-xml:" + PATH, first, second ).properties.getProperty( "c3p0.maxPoolSize" ) );
        assertEquals( "and the ordering really is what decides, not the content",
                      "from-second",
                      read( "c3p0-xml:" + PATH, second, first ).properties.getProperty( "c3p0.maxPoolSize" ) );
    }

    /** An override across classpath entries is reported, as it is for properties resources. */
    public void testAnOverrideAcrossResourcesIsReported() throws Exception
    {
        File first  = root( prop( "maxPoolSize", "from-first" ) );
        File second = root( prop( "maxPoolSize", "from-second" ) );

        assertTrue( warns( read( "c3p0-xml:" + PATH, first, second ), "overrides the following keys" ) );
    }

    /** Named configs and user overrides merge by key like anything else. */
    public void testScopedKeysMergeAcrossResourcesToo() throws Exception
    {
        File first  = root( prop( "maxPoolSize", "1" ) );

        File second = File.createTempFile( "merge-xml-root", "" );
        second.delete();
        second.mkdirs();
        PrintWriter pw = new PrintWriter( new File( second, FILENAME ), "UTF-8" );
        try
        {
            pw.println( "<c3p0-config>" );
            pw.println( "  <named-config name=\"X\"><property name=\"minPoolSize\">7</property></named-config>" );
            pw.println( "</c3p0-config>" );
        }
        finally { pw.close(); }
        roots.add( second );

        Result r = read( "c3p0-xml:" + PATH, first, second );

        assertEquals( "1", r.properties.getProperty( "c3p0.maxPoolSize" ) );
        assertEquals( "7", r.properties.getProperty( "c3p0.named-configs.X.minPoolSize" ) );
    }

    /**
     *  One unparseable document among several must not discard the others. A broken file in one
     *  jar of a deployment is a reason to complain about that file, not to abandon configuration
     *  the rest of the classpath supplied correctly.
     */
    public void testOneBadDocumentDoesNotDiscardTheGoodOnes() throws Exception
    {
        File good = root( prop( "maxPoolSize", "30" ) );

        File bad = File.createTempFile( "merge-xml-root", "" );
        bad.delete();
        bad.mkdirs();
        PrintWriter pw = new PrintWriter( new File( bad, FILENAME ), "UTF-8" );
        try { pw.println( "<c3p0-config><default-config></c3p0-config>" ); }   // unclosed element
        finally { pw.close(); }
        roots.add( bad );

        Result r = read( "c3p0-xml:" + PATH, good, bad );

        assertNull( "" + r.thrown, r.thrown );
        assertEquals( "the readable document still contributes", "30", r.properties.getProperty( "c3p0.maxPoolSize" ) );
        assertTrue( "and the unreadable one is named", warns( r, "Could not parse c3p0 XML configuration from" ) );
    }
}
