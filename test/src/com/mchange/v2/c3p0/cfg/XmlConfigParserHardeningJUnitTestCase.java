package com.mchange.v2.c3p0.cfg;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.PrintWriter;
import java.lang.reflect.Method;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import junit.framework.TestCase;

/**
 *  c3p0's XML configuration is parsed with a deliberately restricted DocumentBuilderFactory
 *  (CVE-2018-20433 and the follow-up reported by Aaron Massey). These tests hold both halves of
 *  that: that the attacks are actually refused, and that each individual restriction is actually
 *  applied to the factory.
 *
 *  <p>The second half is not redundant. cautionDocumentBuilderFactory sets nine restrictions and
 *  tolerates the failure of any of them, logging at FINE, because a parser that does not
 *  recognize one should not stop c3p0 from starting. The cost of that tolerance is that a
 *  restriction can silently become a no-op -- and on the JDK's own parser disallow-doctype-decl
 *  alone refuses every attack below, so eight of the nine could stop working with no test
 *  noticing. These therefore read each setting back off the configured factory.</p>
 *
 *  <p>The hazard is concrete rather than hypothetical, and it is worth spelling out because it
 *  does not look the same everywhere. The JAXP 1.5 properties take a String naming permitted
 *  protocols, and the empty String means none. Pass a boolean instead and:</p>
 *
 *  <ul>
 *    <li>on Java 11, setAttribute throws IllegalArgumentException, the tolerant helper catches
 *        it and logs at FINE, and the property keeps its default of "all" -- every protocol
 *        permitted. The restriction is simply absent, and nothing says so above FINE.</li>
 *    <li>on Java 17 and later, setAttribute accepts the value and newDocumentBuilder() throws
 *        instead, so c3p0 cannot read XML configuration at all.</li>
 *  </ul>
 *
 *  <p>Reading the value back catches both, which is why that is the assertion to keep if these
 *  ever have to be thinned out. Building a DocumentBuilder from the configured factory catches
 *  only the second, but it is the one that distinguishes "configured oddly" from "cannot
 *  start".</p>
 *
 *  <p>No database is needed.</p>
 */
public class XmlConfigParserHardeningJUnitTestCase extends TestCase
{
    private final static String SECRET = "SECRET-FILE-CONTENTS-SHOULD-NEVER-BE-READ";

    private final static String ORDINARY_CONFIG =
        "<c3p0-config><default-config>"
        + "<property name=\"maxPoolSize\">30</property>"
        + "</default-config></c3p0-config>";

    private File secretFile;
    private File externalDtd;

    @Override
    protected void setUp() throws Exception
    {
        secretFile = File.createTempFile( "c3p0-xxe-test-secret", ".txt" );
        PrintWriter pw = new PrintWriter( secretFile );
        try { pw.print( SECRET ); } finally { pw.close(); }

        externalDtd = File.createTempFile( "c3p0-xxe-test", ".dtd" );
        pw = new PrintWriter( externalDtd );
        try { pw.print( "<!ENTITY x \"FROM-EXTERNAL-DTD\">" ); } finally { pw.close(); }
    }

    @Override
    protected void tearDown() throws Exception
    {
        if ( secretFile  != null ) secretFile.delete();
        if ( externalDtd != null ) externalDtd.delete();
    }

    private String fileUrl( File f )
    { return "file://" + f.getAbsolutePath(); }

    /** @return the Throwable the parse failed with, or null if it succeeded. */
    private Throwable parseFailure( String xml, boolean usePermissiveParser )
    {
        try
        {
            InputStream is = new ByteArrayInputStream( xml.getBytes( "UTF-8" ) );
            try { C3P0ConfigXmlUtils.extractXmlConfigFromInputStream( is, usePermissiveParser ); }
            finally { is.close(); }
            return null;
        }
        catch ( Throwable t ) { return t; }
    }

    private void assertRefused( String what, String xml )
    {
        Throwable t = parseFailure( xml, false );
        assertNotNull( what + " must not be parsed by the hardened parser.", t );
        assertTrue( "The refusal should not mention the file we planted, was: " + t.getMessage(),
                    String.valueOf( t.getMessage() ).indexOf( SECRET ) < 0 );
    }

    // ==================== the attacks, through c3p0's own entry point ====================

    public void testAnOrdinaryConfigParses()
    {
        Throwable t = parseFailure( ORDINARY_CONFIG, false );
        assertNull( "Hardening must not cost us ordinary configuration: " + t, t );
    }

    /** Hardening is easy to get wrong in ways that only show up on realistic documents. */
    public void testARealisticConfigWithCommentsAndNamedConfigsParses()
    {
        String xml =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<!-- a comment, which is a node too -->\n"
            + "<c3p0-config>\n"
            + "  <default-config>\n"
            + "    <property name=\"maxPoolSize\">30</property>\n"
            + "    <property name=\"preferredTestQuery\">SELECT 1</property>\n"
            + "  </default-config>\n"
            + "  <named-config name=\"kittycat\">\n"
            + "    <property name=\"minPoolSize\">2</property>\n"
            + "  </named-config>\n"
            + "</c3p0-config>\n";

        Throwable t = parseFailure( xml, false );
        assertNull( "" + t, t );
    }

    /** The classic XXE: an external general entity used to read a local file. */
    public void testAnExternalGeneralEntityIsRefused()
    {
        assertRefused( "An external general entity",
                       "<!DOCTYPE c [<!ENTITY s SYSTEM \"" + fileUrl( secretFile ) + "\">]>"
                       + "<c3p0-config>&s;</c3p0-config>" );
    }

    public void testAnExternalDtdReferenceIsRefused()
    {
        assertRefused( "An external DTD reference",
                       "<!DOCTYPE c3p0-config SYSTEM \"" + fileUrl( externalDtd ) + "\">"
                       + "<c3p0-config>&x;</c3p0-config>" );
    }

    /** Entity expansion as denial of service, rather than as disclosure. */
    public void testEntityExpansionIsRefused()
    {
        assertRefused( "A billion-laughs document",
                       "<!DOCTYPE c [<!ENTITY a \"aaaaaaaaaa\">"
                       + "<!ENTITY b \"&a;&a;&a;&a;&a;&a;&a;&a;&a;&a;\">"
                       + "<!ENTITY c \"&b;&b;&b;&b;&b;&b;&b;&b;&b;&b;\">]>"
                       + "<c3p0-config>&c;</c3p0-config>" );
    }

    /**
     *  Even an innocuous DOCTYPE is refused, because the restriction doing the work is a blanket
     *  ban on doctype declarations rather than an attempt to tell dangerous ones from safe ones.
     *  That is deliberate, and it is a documented reason someone might set usePermissiveParser.
     */
    public void testEvenAHarmlessDoctypeIsRefused()
    {
        assertRefused( "A bare DOCTYPE",
                       "<!DOCTYPE c3p0-config><c3p0-config><default-config/></c3p0-config>" );
    }

    /**
     *  usePermissiveParser is the documented escape hatch for configurations written before any
     *  of this existed. It really does lift the restrictions -- which is the point, and the
     *  reason it is off by default and warns when set.
     */
    public void testThePermissiveParserReallyIsPermissive()
    {
        String xxe = "<!DOCTYPE c [<!ENTITY s SYSTEM \"" + fileUrl( secretFile ) + "\">]>"
                     + "<c3p0-config>&s;</c3p0-config>";

        assertNotNull( "Precondition: the hardened parser refuses this.", parseFailure( xxe, false ) );
        assertNull( "The permissive parser is documented to allow it.", parseFailure( xxe, true ) );
    }

    // ==================== that each restriction is actually applied ====================

    /** The factory as cautionDocumentBuilderFactory leaves it. */
    private static DocumentBuilderFactory cautioned() throws Exception
    { return cautioned( DocumentBuilderFactory.newInstance() ); }

    private static DocumentBuilderFactory cautioned( DocumentBuilderFactory dbf ) throws Exception
    {
        Method m = C3P0ConfigXmlUtils.class.getDeclaredMethod(
            "cautionDocumentBuilderFactory", DocumentBuilderFactory.class );
        m.setAccessible( true );
        m.invoke( null, dbf );
        return dbf;
    }

    private void assertFeature( DocumentBuilderFactory dbf, String uri, boolean expected ) throws Exception
    {
        assertEquals( "Feature '" + uri + "' was not applied to the parser factory.",
                      expected, dbf.getFeature( uri ) );
    }

    public void testDoctypeDeclarationsAreDisallowedOnTheFactory() throws Exception
    { assertFeature( cautioned(), "http://apache.org/xml/features/disallow-doctype-decl", true ); }

    public void testExternalEntityFeaturesAreDisabledOnTheFactory() throws Exception
    {
        DocumentBuilderFactory dbf = cautioned();
        assertFeature( dbf, "http://xml.org/sax/features/external-general-entities", false );
        assertFeature( dbf, "http://xml.org/sax/features/external-parameter-entities", false );
        assertFeature( dbf, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false );
    }

    /**
     *  Arranged the opposite way first, deliberately. The JDK's own parser already defaults
     *  FEATURE_SECURE_PROCESSING on, so merely asserting it is true proves nothing: the
     *  assertion would hold with c3p0 setting nothing at all. Turning it off first makes the
     *  test about what c3p0 does rather than about what this JVM happens to default to.
     */
    public void testSecureProcessingIsEnabledOnTheFactory() throws Exception
    {
        DocumentBuilderFactory fresh = DocumentBuilderFactory.newInstance();
        fresh.setFeature( XMLConstants.FEATURE_SECURE_PROCESSING, false );
        assertFalse( "Precondition: we start from the insecure setting.",
                     fresh.getFeature( XMLConstants.FEATURE_SECURE_PROCESSING ) );

        assertFeature( cautioned( fresh ), XMLConstants.FEATURE_SECURE_PROCESSING, true );
    }

    public void testXIncludeAndEntityExpansionAreOffOnTheFactory() throws Exception
    {
        DocumentBuilderFactory dbf = cautioned();
        assertFalse( "XInclude resolution must be off.", dbf.isXIncludeAware() );
        assertFalse( "Entity reference expansion must be off.", dbf.isExpandEntityReferences() );
    }

    /**
     *  The empty String means "no protocols permitted". Reading it back is what distinguishes a
     *  restriction that applies from one that merely looks applied -- and it is the assertion
     *  that holds on every JVM, since the default these fall back to when a set fails is "all".
     */
    public void testExternalAccessIsDeniedOnTheFactory() throws Exception
    {
        DocumentBuilderFactory dbf = cautioned();
        assertEquals( "External DTD access must be denied, as an empty protocol list.",
                      "", dbf.getAttribute( XMLConstants.ACCESS_EXTERNAL_DTD ) );
        assertEquals( "External schema access must be denied, as an empty protocol list.",
                      "", dbf.getAttribute( XMLConstants.ACCESS_EXTERNAL_SCHEMA ) );
    }

    /**
     *  And the fully configured factory must still be able to produce a parser. On Java 17 and
     *  later this is where a wrongly-typed JAXP property surfaces -- setAttribute takes it
     *  quietly and newDocumentBuilder throws, so c3p0 cannot read XML configuration at all.
     *  On Java 11 the same mistake is caught earlier and more quietly, which is why this test
     *  does not stand in for reading the values back.
     */
    public void testTheCautionedFactoryCanStillProduceAParser() throws Exception
    {
        assertNotNull( "Every restriction applied, the factory must still build a DocumentBuilder.",
                       cautioned().newDocumentBuilder() );
    }

    // ==================== the mechanism the warning depends on ====================

    /**
     *  disallow-doctype-decl is the restriction that refuses every attack above, and it is set
     *  through a helper that tolerates failure. If it cannot be set, c3p0 warns -- but only
     *  because the helper reports the failure. A helper that always claimed success would leave
     *  that warning unreachable and the weakened parser unannounced.
     */
    public void testTheFeatureHelperReportsFailureRatherThanSwallowingIt() throws Exception
    {
        Method m = C3P0ConfigXmlUtils.class.getDeclaredMethod(
            "attemptSetFeature", DocumentBuilderFactory.class, String.class, boolean.class );
        m.setAccessible( true );

        Object supported = m.invoke( null, DocumentBuilderFactory.newInstance(),
                                     "http://apache.org/xml/features/disallow-doctype-decl", Boolean.TRUE );
        assertEquals( "A feature that was set should be reported as set.", Boolean.TRUE, supported );

        Object unsupported = m.invoke( null, DocumentBuilderFactory.newInstance(),
                                       "http://example.com/no/such/feature", Boolean.TRUE );
        assertEquals( "A feature that could not be set must be reported, not swallowed.",
                      Boolean.FALSE, unsupported );
    }
}
