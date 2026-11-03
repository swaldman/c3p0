package com.mchange.v2.c3p0.test.cfgxml;

import junit.framework.TestCase;

import com.mchange.v2.cfg.DelayedLogItem;
import com.mchange.v2.c3p0.cfg.C3P0XmlPropertiesConfigSource;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 *  That each XXE restriction is actually applied to the parser factory -- read back off the
 *  factory, not inferred from an attack being refused.
 *
 *  <p>Ported from c3p0's <code>XmlConfigParserHardeningJUnitTestCase</code> when the XML parser
 *  moved into this library. Its other half, that the attacks are refused and that
 *  <code>usePermissiveParser</code> means what it says, lives in
 *  {@link C3P0XmlPermissiveParserInternalJUnitTestCase}. This is the half that half cannot
 *  cover.</p>
 *
 *  <p>It is not redundant with it. {@code harden(..)} sets nine restrictions and <b>tolerates the
 *  failure of any of them</b>, logging at FINE, because a parser that does not recognize one
 *  should not stop configuration from loading. The cost of that tolerance is that a restriction
 *  can silently become a no-op -- and on the JDK's own parser
 *  <code>disallow-doctype-decl</code> alone refuses every attack, so eight of the nine could stop
 *  working with no behavioural test noticing.</p>
 *
 *  <p>The hazard is concrete, and it does not look the same everywhere. The JAXP 1.5 properties
 *  take a String naming permitted protocols, and the empty String means none. Pass a boolean
 *  instead and:</p>
 *
 *  <ul>
 *    <li>on Java 11, setAttribute throws, the tolerant helper catches it and logs at FINE, and
 *        the property keeps its default of "all" -- every protocol permitted. The restriction is
 *        simply absent, and nothing says so above FINE.</li>
 *    <li>on Java 17 and later, setAttribute accepts the value and newDocumentBuilder() throws
 *        instead, so XML configuration cannot be read at all.</li>
 *  </ul>
 *
 *  <p>Reading the value back catches both, which is why that is the assertion to keep if these
 *  ever have to be thinned out. Building a DocumentBuilder from the configured factory catches
 *  only the second.</p>
 *
 *  <p>Reached by reflection because hardening is private to
 *  {@link C3P0XmlPropertiesConfigSource}, where it belongs: it is not an API, and the only
 *  reason to call it from outside is this.</p>
 */
public final class C3P0XmlHardeningJUnitTestCase extends TestCase
{
    private final static String DISALLOW_DOCTYPE   = "http://apache.org/xml/features/disallow-doctype-decl";
    private final static String EXTERNAL_GENERAL   = "http://xml.org/sax/features/external-general-entities";
    private final static String EXTERNAL_PARAMETER = "http://xml.org/sax/features/external-parameter-entities";
    private final static String LOAD_EXTERNAL_DTD  = "http://apache.org/xml/features/nonvalidating/load-external-dtd";

    private List<DelayedLogItem> items;

    @Override
    protected void setUp()
    { items = new ArrayList<DelayedLogItem>(); }

    /** The factory as harden(..) leaves it. */
    private DocumentBuilderFactory hardened() throws Exception
    { return hardened( DocumentBuilderFactory.newInstance() ); }

    private DocumentBuilderFactory hardened( DocumentBuilderFactory dbf ) throws Exception
    {
        Method m = C3P0XmlPropertiesConfigSource.class.getDeclaredMethod(
            "harden", DocumentBuilderFactory.class, String.class, List.class );
        m.setAccessible( true );
        m.invoke( null, dbf, "c3p0-xml:/notional-for-this-test.xml", items );
        return dbf;
    }

    private void assertFeature( DocumentBuilderFactory dbf, String uri, boolean expected ) throws Exception
    {
        assertEquals( "Feature '" + uri + "' was not applied to the parser factory.",
                      expected, dbf.getFeature( uri ) );
    }

    // ---------------------------------------------- each restriction, read back off the factory

    public void testDoctypeDeclarationsAreDisallowedOnTheFactory() throws Exception
    { assertFeature( hardened(), DISALLOW_DOCTYPE, true ); }

    public void testExternalEntityFeaturesAreDisabledOnTheFactory() throws Exception
    {
        DocumentBuilderFactory dbf = hardened();
        assertFeature( dbf, EXTERNAL_GENERAL,   false );
        assertFeature( dbf, EXTERNAL_PARAMETER, false );
        assertFeature( dbf, LOAD_EXTERNAL_DTD,  false );
    }

    /**
     *  Arranged the opposite way first, deliberately. The JDK's own parser already defaults
     *  FEATURE_SECURE_PROCESSING on, so merely asserting it is true proves nothing -- the
     *  assertion would hold with harden(..) setting nothing at all. Turning it off first makes
     *  the test about what we do rather than about what this JVM happens to default to.
     */
    public void testSecureProcessingIsEnabledOnTheFactory() throws Exception
    {
        DocumentBuilderFactory fresh = DocumentBuilderFactory.newInstance();
        fresh.setFeature( XMLConstants.FEATURE_SECURE_PROCESSING, false );
        assertFalse( "Precondition: we start from the insecure setting.",
                     fresh.getFeature( XMLConstants.FEATURE_SECURE_PROCESSING ) );

        assertFeature( hardened( fresh ), XMLConstants.FEATURE_SECURE_PROCESSING, true );
    }

    public void testXIncludeAndEntityExpansionAreOffOnTheFactory() throws Exception
    {
        DocumentBuilderFactory dbf = hardened();
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
        DocumentBuilderFactory dbf = hardened();
        assertEquals( "External DTD access must be denied, as an empty protocol list.",
                      "", dbf.getAttribute( XMLConstants.ACCESS_EXTERNAL_DTD ) );
        assertEquals( "External schema access must be denied, as an empty protocol list.",
                      "", dbf.getAttribute( XMLConstants.ACCESS_EXTERNAL_SCHEMA ) );
    }

    /**
     *  And the fully configured factory must still be able to produce a parser. On Java 17 and
     *  later this is where a wrongly-typed JAXP property surfaces -- setAttribute takes it
     *  quietly and newDocumentBuilder throws, so XML configuration cannot be read at all. On
     *  Java 11 the same mistake is caught earlier and more quietly, which is why this does not
     *  stand in for reading the values back.
     */
    public void testTheHardenedFactoryCanStillProduceAParser() throws Exception
    {
        assertNotNull( "Every restriction applied, the factory must still build a DocumentBuilder.",
                       hardened().newDocumentBuilder() );
    }

    /** Hardening a factory that supports everything should have nothing to complain about. */
    public void testHardeningTheJdkParserReportsNothingAboveFine() throws Exception
    {
        hardened();
        for ( DelayedLogItem item : items )
            assertFalse( "hardening the JDK's own parser should not warn: " + item.getText(),
                         DelayedLogItem.Level.WARNING.equals( item.getLevel() )
                         || DelayedLogItem.Level.SEVERE.equals( item.getLevel() ) );
    }

    // ---------------------------------------------- the mechanism the warning depends on

    /**
     *  disallow-doctype-decl is the restriction that refuses every attack, and it is set through
     *  a helper that tolerates failure. If it cannot be set, harden(..) warns -- but only
     *  because the helper reports the failure. A helper that always claimed success would leave
     *  that warning unreachable and the weakened parser unannounced.
     */
    public void testTheFeatureHelperReportsFailureRatherThanSwallowingIt() throws Exception
    {
        Method m = C3P0XmlPropertiesConfigSource.class.getDeclaredMethod(
            "attemptSetFeature", DocumentBuilderFactory.class, String.class, boolean.class, List.class );
        m.setAccessible( true );

        Object supported = m.invoke( null, DocumentBuilderFactory.newInstance(),
                                     DISALLOW_DOCTYPE, Boolean.TRUE, items );
        assertEquals( "A feature that was set should be reported as set.", Boolean.TRUE, supported );

        Object unsupported = m.invoke( null, DocumentBuilderFactory.newInstance(),
                                       "http://example.com/no/such/feature", Boolean.TRUE, items );
        assertEquals( "A feature that could not be set must be reported, not swallowed.",
                      Boolean.FALSE, unsupported );

        assertTrue( "and the failure should leave a FINE trace to find it by",
                    mentions( "no/such/feature" ) );
    }

    /** The same for attributes, whose failure mode is the quieter of the two. */
    public void testTheAttributeHelperReportsFailureRatherThanSwallowingIt() throws Exception
    {
        Method m = C3P0XmlPropertiesConfigSource.class.getDeclaredMethod(
            "attemptSetAttribute", DocumentBuilderFactory.class, String.class, Object.class, List.class );
        m.setAccessible( true );

        Object supported = m.invoke( null, DocumentBuilderFactory.newInstance(),
                                     XMLConstants.ACCESS_EXTERNAL_DTD, "", items );
        assertEquals( "An attribute that was set should be reported as set.", Boolean.TRUE, supported );

        Object unsupported = m.invoke( null, DocumentBuilderFactory.newInstance(),
                                       "http://example.com/no/such/attribute", "", items );
        assertEquals( "An attribute that could not be set must be reported, not swallowed.",
                      Boolean.FALSE, unsupported );
    }

    /**
     *  If doctypes cannot be disabled at all, that is said at WARNING -- the one restriction
     *  whose absence is worth interrupting someone over, and the branch no real JVM reaches.
     */
    public void testAnUndisableableDoctypeDrawsAWarning() throws Exception
    {
        DocumentBuilderFactory refuses = new RefusingDocumentBuilderFactory( DISALLOW_DOCTYPE );
        hardened( refuses );

        assertTrue( "expected a WARNING naming the doctype restriction, got: " + items,
                    mentionsAtWarning( "DOCTYPE" ) );
    }

    private boolean mentions( String fragment )
    {
        for ( DelayedLogItem item : items )
            if ( item.getText() != null && item.getText().indexOf( fragment ) >= 0 ) return true;
        return false;
    }

    private boolean mentionsAtWarning( String fragment )
    {
        for ( DelayedLogItem item : items )
            if ( DelayedLogItem.Level.WARNING.equals( item.getLevel() )
                 && item.getText() != null && item.getText().indexOf( fragment ) >= 0 ) return true;
        return false;
    }

    /**
     *  A factory that refuses one named feature and delegates everything else, so the
     *  cannot-disable-doctypes branch can be reached without a parser that behaves that way.
     */
    private final static class RefusingDocumentBuilderFactory extends DocumentBuilderFactory
    {
        private final DocumentBuilderFactory delegate = DocumentBuilderFactory.newInstance();
        private final String refused;

        RefusingDocumentBuilderFactory( String refused ) { this.refused = refused; }

        @Override
        public void setFeature( String name, boolean value ) throws javax.xml.parsers.ParserConfigurationException
        {
            if ( refused.equals( name ) )
                throw new javax.xml.parsers.ParserConfigurationException( "refused by this test: " + name );
            delegate.setFeature( name, value );
        }

        @Override public boolean getFeature( String name ) throws javax.xml.parsers.ParserConfigurationException
        { return delegate.getFeature( name ); }
        @Override public javax.xml.parsers.DocumentBuilder newDocumentBuilder() throws javax.xml.parsers.ParserConfigurationException
        { return delegate.newDocumentBuilder(); }
        @Override public void setAttribute( String name, Object value ) { delegate.setAttribute( name, value ); }
        @Override public Object getAttribute( String name ) { return delegate.getAttribute( name ); }
        @Override public void setXIncludeAware( boolean state ) { delegate.setXIncludeAware( state ); }
        @Override public boolean isXIncludeAware() { return delegate.isXIncludeAware(); }
        @Override public void setExpandEntityReferences( boolean state ) { delegate.setExpandEntityReferences( state ); }
        @Override public boolean isExpandEntityReferences() { return delegate.isExpandEntityReferences(); }
    }
}
