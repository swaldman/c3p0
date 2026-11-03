package com.mchange.v2.c3p0.test.cfgxml;

import junit.framework.TestCase;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import com.mchange.v2.cfg.ConfigVetoedException;
import com.mchange.v2.cfg.MConfig;
import com.mchange.v2.cfg.MultiPropertiesConfig;
import com.mchange.v2.c3p0.test.cfglib.SealedSystemPropertiesTestSupport;

/**
 *  A <code>c3p0-xml:file:</code> identifier may name its file by System-property reference.
 *
 *  <h3>Why the reference syntax exists at all</h3>
 *
 *  <p>THIS IS THE REASON IT WAS BUILT. c3p0 names its XML config file with the System property
 *  <code>com.mchange.v2.c3p0.cfg.xml</code>, and C3P0Config has nowhere to read that property for
 *  itself: the resource-path list is assembled before any configuration is loaded. Written into
 *  the identifier as <code>c3p0-xml:file:${com.mchange.v2.c3p0.cfg.xml}</code>, it works, and the
 *  whole <code>file:</code> treatment comes along with it.</p>
 *
 *  <p>Moved here from mchange-commons-java's FileUrlPropertyReferenceInternalJUnitTestCase when
 *  the XML source moved into c3p0. The reference machinery itself still belongs to, and is still
 *  tested in, the config library; these two cases are about c3p0's use of it, and they are the
 *  reason that machinery is public API rather than an implementation detail of
 *  FileUrlPropertiesConfigSource.</p>
 *
 *  <h3>Why the snapshot has to be discarded per case</h3>
 *
 *  <p>A reference resolves against the <b>sealed</b> snapshot of System properties, which MLog's
 *  own initialization has already taken by the time any test method runs. So each case discards
 *  it first. That is also the deployment story: a reference resolves to what the property held at
 *  the seal, in practice to a <code>-D</code>, and setting it from application code after startup
 *  will not redirect a config read.</p>
 */
public final class C3P0XmlPropertyReferenceJUnitTestCase extends TestCase
{
    private final static String[] NONE = new String[0];

    private final static String PFX = "com.mchange.v2.c3p0.test.cfgxml.xmlref.";

    private final static String XML_BODY =
        "<c3p0-config><default-config><property name=\"maxPoolSize\">42</property></default-config></c3p0-config>";

    private Path       dir;
    private Properties saved;

    @Override
    protected void setUp() throws Exception
    {
        // toRealPath: on macOS the temp dir sits under /var, itself a symlink, and the
        // permissions chain reasons about links -- we want the paths we think we have
        dir   = Files.createTempDirectory( "c3p0-cfgxml-ref-" ).toRealPath();
        saved = (Properties) System.getProperties().clone();
        clearOurKeys();
        SealedSystemPropertiesTestSupport.unsealSystemProperties();
    }

    @Override
    protected void tearDown() throws Exception
    {
        deleteRecursively( dir );
        clearOurKeys();
        for ( String k : saved.stringPropertyNames() )
            if ( k.startsWith( PFX ) ) System.setProperty( k, saved.getProperty( k ) );
        SealedSystemPropertiesTestSupport.unsealSystemProperties();
    }

    // ------------------------------------------------------------- fixture

    private Path write( String name, String contents, String mode ) throws IOException
    {
        Path p = dir.resolve( name );
        Files.write( p, contents.getBytes( "8859_1" ) );
        if ( posixSupported() ) Files.setPosixFilePermissions( p, PosixFilePermissions.fromString( mode ) );
        return p;
    }

    private boolean posixSupported()
    { return dir.getFileSystem().supportedFileAttributeViews().contains( "posix" ); }

    private static void deleteRecursively( Path p )
    {
        try
        {
            if ( Files.isDirectory( p ) )
                for ( Path child : Files.newDirectoryStream( p ) ) deleteRecursively( child );
            Files.deleteIfExists( p );
        }
        catch ( Exception e ) { /* best effort */ }
    }

    private static void clearOurKeys()
    {
        List<String> doomed = new ArrayList<String>();
        for ( String k : System.getProperties().stringPropertyNames() )
            if ( k.startsWith( PFX ) ) doomed.add( k );
        for ( String k : doomed ) System.clearProperty( k );
    }

    private static void setRef( String suffix, String value )
    { System.setProperty( PFX + suffix, value ); }

    private static String ref( String suffix )
    { return "${" + PFX + suffix + "}"; }

    /** c3p0-xml: sources are vetoable, so reads go through the vetoable facade. */
    @SuppressWarnings("unchecked")
    private static MultiPropertiesConfig read( String... paths ) throws ConfigVetoedException
    { return MConfig.AsProvidedVetoable.readUncachedResourceConfig( NONE, paths, new ArrayList() ); }

    private static List<String> pathsOf( MultiPropertiesConfig mpc )
    { return Arrays.asList( mpc.getPropertiesResourcePaths() ); }

    private static String assertVetoed( String... paths )
    {
        try
        {
            read( paths );
            fail( "expected a veto" );
            return null;
        }
        catch ( ConfigVetoedException expected )
        { return expected.getMessage(); }
    }

    // ------------------------------------------------------------- the cases

    public void testAC3P0XmlIdentifierMayNameItsFileByReference() throws Exception
    {
        Path xml = write( "c3p0-config.xml", XML_BODY, "rw-------" );
        setRef( "xmlPath", xml.toString() );

        MultiPropertiesConfig mpc = read( "c3p0-xml:file:" + ref( "xmlPath" ) );

        assertEquals( "42", mpc.getProperty( "c3p0.maxPoolSize" ) );
        assertEquals( "and the identifier is reported as written, not resolved",
                      Arrays.asList( "c3p0-xml:file:" + ref( "xmlPath" ) ), pathsOf( mpc ) );
    }

    /** With the directives too, since they are the same implementation. */
    public void testAC3P0XmlReferenceStillHonorsPermissionsDirectives() throws Exception
    {
        if ( !posixSupported() ) return;

        Path xml = write( "exposed-config.xml", XML_BODY, "rw-r--r--" );
        setRef( "exposedXml", xml.toString() );

        String msg = assertVetoed( "c3p0-xml:file:" + ref( "exposedXml" ) + "?permissions=useronly" );
        assertTrue( "the veto should be about permissions: " + msg, msg.contains( "permissions" ) );
    }

    /**
     *  An unset reference makes the configuration absent rather than malformed, which is what
     *  lets c3p0 name its optional XML file unconditionally in a shared resource-path list.
     */
    public void testAnUnsetReferenceIsOrdinaryAbsence() throws Exception
    {
        MultiPropertiesConfig mpc = read( "c3p0-xml:file:" + ref( "neverSet" ) );

        assertNull( "nothing should have been read", mpc.getProperty( "c3p0.maxPoolSize" ) );
        assertEquals( "and the absence must not be fatal", 0, mpc.getPropertiesResourcePaths().length );
    }
}
