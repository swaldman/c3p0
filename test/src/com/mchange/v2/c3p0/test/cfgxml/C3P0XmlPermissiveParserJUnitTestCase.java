package com.mchange.v2.c3p0.test.cfgxml;

import junit.framework.TestCase;

import com.mchange.v2.cfg.ConfigVetoedException;
import com.mchange.v2.cfg.DelayedLogItem;
import com.mchange.v2.cfg.MConfig;
import com.mchange.v2.cfg.MultiPropertiesConfig;
import com.mchange.v2.c3p0.test.cfglib.SealedSystemPropertiesTestSupport;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 *  <code>com.mchange.v2.c3p0.cfg.xml.usePermissiveParser</code>: the one escape from hardened XML
 *  parsing, and the deprecated spelling that is no longer an escape at all.
 *
 *  <h3>Why these are internal tests</h3>
 *
 *  <p>The flag is read only from the sealed snapshot of System properties, which MLog's own
 *  initialization has already taken by the time any test method runs. So each case has to discard
 *  the snapshot first, which needs {@link SealedSystemPropertiesTestSupport} and therefore this
 *  package &mdash; the same reason, and the same remedy, as
 *  {@link FileUrlPropertyReferenceInternalJUnitTestCase}.</p>
 *
 *  <p>That is also the deployment story: the flag resolves to what a <code>-D</code> said.
 *  Reading it from the snapshot rather than from configuration is the point &mdash; a flag that
 *  relaxes XML parsing must not be settable by the documents whose parsing it relaxes.</p>
 */
public final class C3P0XmlPermissiveParserJUnitTestCase extends TestCase
{
    private final static String[] NONE = new String[0];

    private final static String PERMISSIVE = "com.mchange.v2.c3p0.cfg.xml.usePermissiveParser";
    private final static String EXPAND     = "com.mchange.v2.c3p0.cfg.xml.expandEntityReferences";

    private Path    dir;
    private Path    secret;
    private Properties saved;

    @Override
    protected void setUp() throws Exception
    {
        dir = Files.createTempDirectory( "mchange-cfg-c3p0xml-parser-" ).toRealPath();

        secret = dir.resolve( "secret.txt" );
        write( secret, "TOP-SECRET-VALUE" );

        saved = (Properties) System.getProperties().clone();
        System.clearProperty( PERMISSIVE );
        System.clearProperty( EXPAND );
        SealedSystemPropertiesTestSupport.unsealSystemProperties();
    }

    @Override
    protected void tearDown() throws Exception
    {
        deleteRecursively( dir );
        System.clearProperty( PERMISSIVE );
        System.clearProperty( EXPAND );
        if ( saved.getProperty( PERMISSIVE ) != null ) System.setProperty( PERMISSIVE, saved.getProperty( PERMISSIVE ) );
        if ( saved.getProperty( EXPAND )     != null ) System.setProperty( EXPAND,     saved.getProperty( EXPAND ) );
        SealedSystemPropertiesTestSupport.unsealSystemProperties();
    }

    private static void write( Path p, String contents ) throws Exception
    {
        PrintWriter pw = new PrintWriter( p.toFile(), "UTF-8" );
        try { pw.print( contents ); }
        finally { pw.close(); }
    }

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

    /**
     *  A document that reads a local file through an external entity. This is the attack
     *  CVE-2018-20433 is about: whoever can write the configuration file can exfiltrate anything
     *  the process can read.
     */
    private String xxeDocument() throws Exception
    {
        Path p = dir.resolve( "xxe.xml" );
        write( p,
            "<?xml version=\"1.0\"?>\n" +
            "<!DOCTYPE c3p0-config [ <!ENTITY stolen SYSTEM \"" + secret.toUri() + "\"> ]>\n" +
            "<c3p0-config><default-config>\n" +
            "  <property name=\"maxPoolSize\">&stolen;</property>\n" +
            "</default-config></c3p0-config>\n" );
        return "c3p0-xml:" + p.toUri();
    }

    /** A plain document, for asserting that parsing still works when the flag is on. */
    private String plainDocument() throws Exception
    {
        Path p = dir.resolve( "plain.xml" );
        write( p, "<c3p0-config><default-config><property name=\"maxPoolSize\">30</property></default-config></c3p0-config>" );
        return "c3p0-xml:" + p.toUri();
    }

    @SuppressWarnings("unchecked")
    private static MultiPropertiesConfig read( String identifier ) throws ConfigVetoedException
    { return MConfig.AsProvidedVetoable.readUncachedResourceConfig( NONE, new String[]{ identifier }, new ArrayList() ); }

    @SuppressWarnings("unchecked")
    private static boolean warns( MultiPropertiesConfig mpc, String... fragments )
    {
        for ( Object o : mpc.getDelayedLogItems() )
        {
            DelayedLogItem item = (DelayedLogItem) o;
            if (! DelayedLogItem.Level.WARNING.equals( item.getLevel() ) ) continue;
            String text = String.valueOf( item.getText() );
            boolean all = true;
            for ( String f : fragments ) if ( text.indexOf( f ) < 0 ) all = false;
            if ( all ) return true;
        }
        return false;
    }

    private static String allValues( MultiPropertiesConfig mpc )
    {
        Properties p = mpc.getPropertiesByPrefix( "" );
        StringBuilder sb = new StringBuilder();
        for ( String k : p.stringPropertyNames() ) sb.append( k ).append( '=' ).append( p.getProperty( k ) ).append( ';' );
        return sb.toString();
    }

    // =================================================== hardened by default

    /** THE DEFAULT, and the reason the flag exists at all. */
    public void testWithTheFlagUnsetAnExternalEntityIsNotResolved() throws Exception
    {
        MultiPropertiesConfig mpc = read( xxeDocument() );

        assertFalse( "the file contents must not reach configuration: " + allValues( mpc ),
                     allValues( mpc ).contains( "TOP-SECRET-VALUE" ) );
        assertTrue( "and the refusal must be reported", warns( mpc, "could not be parsed as XML" ) );
    }

    /** Explicitly false is the same as unset, and must not be read as "mentioning it opts in". */
    public void testExplicitlyFalseIsAlsoHardened() throws Exception
    {
        System.setProperty( PERMISSIVE, "false" );

        assertFalse( allValues( read( xxeDocument() ) ).contains( "TOP-SECRET-VALUE" ) );
    }

    // =================================================== the escape, and its noise

    /**
     *  With the flag set, the document parses &mdash; that is what was asked for &mdash; AND the
     *  hazard is named. A relaxation that happened silently would be worse than one refused.
     */
    public void testWithTheFlagSetTheDocumentParsesAndTheHazardIsAnnounced() throws Exception
    {
        System.setProperty( PERMISSIVE, "true" );

        MultiPropertiesConfig mpc = read( xxeDocument() );

        assertEquals( "the entity was resolved, as requested",
                      "TOP-SECRET-VALUE", mpc.getProperty( "c3p0.maxPoolSize" ) );
        assertTrue( "and the CVE must be named", warns( mpc, "CVE-2018-20433" ) );
        assertTrue( "and the property that did it", warns( mpc, PERMISSIVE ) );
    }

    public void testTheFlagIsCaseInsensitive() throws Exception
    {
        System.setProperty( PERMISSIVE, "TRUE" );
        assertEquals( "TOP-SECRET-VALUE", read( xxeDocument() ).getProperty( "c3p0.maxPoolSize" ) );
    }

    /** An uninterpretable value warns and stays hardened: the safe reading of "I cannot tell". */
    public void testAnUninterpretableValueWarnsAndStaysHardened() throws Exception
    {
        System.setProperty( PERMISSIVE, "maybe" );

        MultiPropertiesConfig mpc = read( xxeDocument() );

        assertFalse( allValues( mpc ).contains( "TOP-SECRET-VALUE" ) );
        assertTrue( warns( mpc, "uninterpretable value", PERMISSIVE ) );
    }

    // =================================================== it is a System property, and only that

    /**
     *  THE ASSERTION THAT KEEPS "sealed System property only" FROM DRIFTING. The flag set in the
     *  very document whose parsing it would relax must have no effect — nor set in a properties
     *  source read alongside it. Otherwise an attacker who can write the config file can open the
     *  parser that reads the config file.
     */
    public void testTheFlagCannotBeSetFromTheDocumentItParses() throws Exception
    {
        Path p = dir.resolve( "self-permitting.xml" );
        write( p,
            "<?xml version=\"1.0\"?>\n" +
            "<!DOCTYPE c3p0-config [ <!ENTITY stolen SYSTEM \"" + secret.toUri() + "\"> ]>\n" +
            "<c3p0-config><default-config>\n" +
            "  <property name=\"" + PERMISSIVE + "\">true</property>\n" +
            "  <property name=\"maxPoolSize\">&stolen;</property>\n" +
            "</default-config></c3p0-config>\n" );

        MultiPropertiesConfig mpc = read( "c3p0-xml:" + p.toUri() );

        assertFalse( "a document must not be able to open the parser that reads it: " + allValues( mpc ),
                     allValues( mpc ).contains( "TOP-SECRET-VALUE" ) );
    }

    public void testTheFlagCannotBeSetFromAPropertiesSourceReadAlongside() throws Exception
    {
        Path props = dir.resolve( "opener.properties" );
        write( props, PERMISSIVE + "=true\n" );

        MultiPropertiesConfig mpc =
            MConfig.AsProvidedVetoable.readUncachedResourceConfig(
                NONE, new String[]{ "file:" + props.toUri().getPath(), xxeDocument() }, new ArrayList() );

        assertEquals( "precondition: the properties source really was read",
                      "true", mpc.getProperty( PERMISSIVE ) );
        assertFalse( "but configuration must not govern the parser: " + allValues( mpc ),
                     allValues( mpc ).contains( "TOP-SECRET-VALUE" ) );
    }

    // =================================================== the deprecated spelling

    /**
     *  c3p0's own parser honors expandEntityReferences as a second way to ask for the same thing.
     *  This source does not — but a deployment migrating from that parser deserves to be told why
     *  its entity references stopped resolving, rather than left to find out.
     *
     *  <p>Both halves asserted. The warning alone would pass over an implementation that pointed
     *  helpfully at the new property while quietly honoring the old one, which would be the worst
     *  of both.</p>
     */
    public void testTheDeprecatedSpellingWarnsAndDoesNotRelax() throws Exception
    {
        System.setProperty( EXPAND, "true" );

        MultiPropertiesConfig mpc = read( xxeDocument() );

        assertFalse( "it must NOT relax the parser: " + allValues( mpc ),
                     allValues( mpc ).contains( "TOP-SECRET-VALUE" ) );
        assertTrue( "and must point at the property that would", warns( mpc, EXPAND, PERMISSIVE ) );
    }

    /** The supported flag still governs when both are set. */
    public void testTheSupportedFlagStillWorksAlongsideTheDeprecatedOne() throws Exception
    {
        System.setProperty( EXPAND, "true" );
        System.setProperty( PERMISSIVE, "true" );

        MultiPropertiesConfig mpc = read( xxeDocument() );

        assertEquals( "TOP-SECRET-VALUE", mpc.getProperty( "c3p0.maxPoolSize" ) );
        assertTrue( warns( mpc, EXPAND, PERMISSIVE ) );
        assertTrue( warns( mpc, "CVE-2018-20433" ) );
    }

    /** And a plain document is unaffected by any of this. */
    public void testAPlainDocumentReadsTheSameEitherWay() throws Exception
    {
        assertEquals( "30", read( plainDocument() ).getProperty( "c3p0.maxPoolSize" ) );

        System.setProperty( PERMISSIVE, "true" );
        SealedSystemPropertiesTestSupport.unsealSystemProperties();
        assertEquals( "30", read( plainDocument() ).getProperty( "c3p0.maxPoolSize" ) );
    }
}
