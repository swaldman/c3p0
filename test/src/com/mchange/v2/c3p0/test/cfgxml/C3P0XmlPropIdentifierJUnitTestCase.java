package com.mchange.v2.c3p0.test.cfgxml;

import junit.framework.TestCase;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.mchange.v2.cfg.ConfigVetoedException;
import com.mchange.v2.cfg.DelayedLogItem;
import com.mchange.v2.cfg.MConfig;
import com.mchange.v2.cfg.MultiPropertiesConfig;
import com.mchange.v2.c3p0.cfg.C3P0XmlPropertiesConfigSource;
import com.mchange.v2.c3p0.test.cfglib.SealedSystemPropertiesTestSupport;
import com.mchange.v2.log.MLog;

/**
 *  <code>c3p0-xml:prop:${some.property}</code> &mdash; one identifier, two value forms.
 *
 *  <h3>What this form is for</h3>
 *
 *  <p>c3p0's manual has always documented <code>com.mchange.v2.c3p0.cfg.xml</code> as naming an XML
 *  config file by absolute filesystem path, <b>or</b>, when its value begins
 *  <code>classloader:</code>, as naming a ClassLoader resource. One property, two meanings,
 *  decided by the value. This identifier is where that decision is made, and it is the entry
 *  <code>build.mill</code> puts in c3p0's declared resource-path list.</p>
 *
 *  <p>The shape matters, and was arrived at the hard way. An earlier attempt split the two
 *  meanings across two list entries &mdash; one for each value form &mdash; which worked, but
 *  meant that whichever entry did not match complained about a value that was never addressed to
 *  it. A <code>classloader:</code> value drew a WARNING from the <code>file:</code> entry saying
 *  the config was being <i>ignored</i>, on every load, while in fact the other entry had just read
 *  it. One documented property gets one identifier, which is the only arrangement in which
 *  absence can be reported exactly once.</p>
 *
 *  <h3>Why the snapshot is discarded per case</h3>
 *
 *  <p>The value is read from the <b>sealed</b> snapshot of System properties, which MLog's own
 *  initialization has already taken by the time any test method runs. So each case discards it
 *  first. That is also the deployment story, and
 *  {@link #testAValueSetAfterTheSealIsNotHonored} is the case that pins it: the value is what the
 *  property held at the seal, in practice a <code>-D</code>, and setting it from application code
 *  after startup will not redirect a config read.</p>
 *
 *  <p>The consequence for these cases is that <b>every value a method needs must be set before
 *  that method's first read</b>, because the first read takes the snapshot. Several cases below
 *  therefore set two or three properties up front and then read each in turn, which looks
 *  gratuitous and is not.</p>
 *
 *  @see C3P0XmlPropertyReferenceJUnitTestCase the <code>file:${...}</code> reference machinery
 *       this form delegates to for the filesystem case
 */
public final class C3P0XmlPropIdentifierJUnitTestCase extends TestCase
{
    private final static String[] NONE = new String[0];

    private final static String PFX = "com.mchange.v2.c3p0.test.cfgxml.prop.";

    /** On the ordinary test classpath, holding maxPoolSize 42 and an extension probeKey. */
    private final static String PROBE_RESOURCE = "/com/mchange/v2/c3p0/test/cfgxml/c3p0-config-probe.xml";

    /** The same, at a path with upper-case letters in it. maxPoolSize 4242. */
    private final static String MIXED_CASE_RESOURCE = "/com/mchange/v2/c3p0/test/cfgxml/C3P0ConfigMixedCase.xml";

    private final static String XML_BODY =
        "<c3p0-config><default-config><property name=\"maxPoolSize\">99</property></default-config></c3p0-config>";

    private Path dir;

    @Override
    protected void setUp() throws Exception
    {
        // toRealPath: on macOS the temp dir sits under /var, itself a symlink, and the
        // permissions chain reasons about links -- we want the paths we think we have
        dir = Files.createTempDirectory( "c3p0-cfgxml-prop-" ).toRealPath();
        clearOurKeys();
        SealedSystemPropertiesTestSupport.unsealSystemProperties();
    }

    @Override
    protected void tearDown() throws Exception
    {
        deleteRecursively( dir );
        clearOurKeys();
        SealedSystemPropertiesTestSupport.unsealSystemProperties();
    }

    // ------------------------------------------------------------- fixture

    private Path write( String name, String contents, String mode ) throws IOException
    {
        Path p = dir.resolve( name );
        Files.createDirectories( p.getParent() );
        Files.write( p, contents.getBytes( "8859_1" ) );
        if ( posixSupported() ) Files.setPosixFilePermissions( p, PosixFilePermissions.fromString( mode ) );
        return p;
    }

    private boolean posixSupported()
    { return dir.getFileSystem().supportedFileAttributeViews().contains( "posix" ); }

    /**
     *  Whether resource lookup here can tell two paths apart that differ only in case.
     *
     *  <p>On a case-insensitive filesystem -- APFS as macOS ships it -- a classpath directory
     *  resolves a lowercased resource path to the same file, so an assertion that the path's case
     *  was preserved cannot fail however the code mangles it. Inside a jar, and on Linux, it can.
     *  This is the same shape as posixSupported() above: say so, and skip, rather than assert
     *  something that is locally vacuous and pretend it was checked.</p>
     */
    private static boolean resourceLookupIsCaseSensitive()
    {
        ClassLoader cl = C3P0XmlPropIdentifierJUnitTestCase.class.getClassLoader();
        String real = MIXED_CASE_RESOURCE.substring( 1 );
        return cl.getResource( real ) != null && cl.getResource( real.toLowerCase() ) == null;
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

    private static void clearOurKeys()
    {
        List<String> doomed = new ArrayList<String>();
        for ( String k : System.getProperties().stringPropertyNames() )
            if ( k.startsWith( PFX ) ) doomed.add( k );
        for ( String k : doomed ) System.clearProperty( k );
    }

    /** The identifier naming property PFX + suffix, which setVal(..) gives a value. */
    private static String prop( String suffix )
    { return "c3p0-xml:prop:${" + PFX + suffix + "}"; }

    private static void setVal( String suffix, String value )
    { System.setProperty( PFX + suffix, value ); }

    /** c3p0-xml: sources are vetoable, so reads go through the vetoable facade. */
    @SuppressWarnings("unchecked")
    private static MultiPropertiesConfig read( String... identifiers ) throws ConfigVetoedException
    { return MConfig.AsProvidedVetoable.readUncachedResourceConfig( NONE, identifiers, new ArrayList() ); }

    private static List<String> pathsOf( MultiPropertiesConfig mpc )
    { return Arrays.asList( mpc.getPropertiesResourcePaths() ); }

    @SuppressWarnings("unchecked")
    private static String warningMentioning( MultiPropertiesConfig mpc, String fragment )
    {
        for ( Object o : mpc.getDelayedLogItems() )
        {
            DelayedLogItem item = (DelayedLogItem) o;
            if (! DelayedLogItem.Level.WARNING.equals( item.getLevel() ) ) continue;
            String text = String.valueOf( item.getText() );
            if ( text.indexOf( fragment ) >= 0 ) return text;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<String> warningsOf( MultiPropertiesConfig mpc )
    {
        List<String> out = new ArrayList<String>();
        for ( Object o : mpc.getDelayedLogItems() )
        {
            DelayedLogItem item = (DelayedLogItem) o;
            if ( DelayedLogItem.Level.WARNING.equals( item.getLevel() ) )
                out.add( String.valueOf( item.getText() ) );
        }
        return out;
    }

    // =========================================================== the filesystem value form

    /** The documented default: an absolute filesystem path. */
    public void testAPlainPathValueIsReadAsAFile() throws Exception
    {
        Path xml = write( "c3p0-config.xml", XML_BODY, "rw-------" );
        setVal( "plain", xml.toString() );

        MultiPropertiesConfig mpc = read( prop( "plain" ) );

        assertEquals( "99", mpc.getProperty( "c3p0.maxPoolSize" ) );
        assertEquals( "and the identifier is reported as written, not resolved",
                      Arrays.asList( prop( "plain" ) ), pathsOf( mpc ) );
    }

    /**
     *  A path containing a space, which is the case that says HOW the filesystem form is reached.
     *
     *  <p>This form must hand the <i>reference</i> &mdash; <code>file:${name}</code> &mdash; to the
     *  file machinery, not the value it just read. The reference-resolving path in
     *  FileUrlPropertiesConfigSource deliberately does not parse a substituted path as a URI,
     *  precisely so that a path holding a space (or a <code>%</code>, <code>?</code> or
     *  <code>#</code>) survives. An implementation that instead built <code>"file:" + value</code>
     *  would route through <code>Paths.get(new URI(..))</code> and die here with
     *  <code>URISyntaxException: Illegal character in path</code> &mdash; which is exactly what an
     *  earlier draft of this feature did, and what this case exists to prevent returning.</p>
     *
     *  <p><code>C:\Program Files\</code> and <code>~/Library/Application Support/</code> are not
     *  exotic places to keep a config file.</p>
     */
    public void testAPathValueContainingASpaceIsRead() throws Exception
    {
        Path xml = write( "has space/c3p0-config.xml", XML_BODY, "rw-------" );
        setVal( "spaced", xml.toString() );

        MultiPropertiesConfig mpc = read( prop( "spaced" ) );

        assertEquals( "a path with a space must read exactly as one without",
                      "99", mpc.getProperty( "c3p0.maxPoolSize" ) );
    }

    /**
     *  Query directives cannot be smuggled through the property value.
     *
     *  <p>A value is a path, whole. <code>?permissions=useronly</code> appended to it is part of
     *  the filename, not a directive &mdash; so this world-readable file is not vetoed, it is
     *  simply not found. That follows from handing the reference rather than the value to the
     *  file machinery (the query string is split off the identifier BEFORE substitution), and it
     *  is worth pinning: the alternative would let whoever controls the property turn a
     *  security directive on or off, or make an optional config <code>required=true</code>.</p>
     */
    public void testAQueryStringInTheValueIsNotADirective() throws Exception
    {
        if ( !posixSupported() ) return;

        Path xml = write( "exposed-config.xml", XML_BODY, "rw-r--r--" );

        // both values before the first read: a read seals the snapshot, so a property set after
        // one is invisible (testAValueSetAfterTheSealIsNotHonored is that rule, asserted)
        setVal( "smuggled", xml.toString() + "?permissions=useronly" );
        setVal( "control",  xml.toString() );

        assertNull( "the value is a path, whole: nothing should have been read",
                    read( prop( "smuggled" ) ).getProperty( "c3p0.maxPoolSize" ) );

        // the control: the same world-readable file, named without the pseudo-directive, loads --
        // so the null above is the query text being part of the path, not a permissions veto
        assertEquals( "99", read( prop( "control" ) ).getProperty( "c3p0.maxPoolSize" ) );
    }

    // =========================================================== the classloader value form

    /** The form the manual documents and that nothing supported until now. */
    public void testAClassloaderValueIsReadAsAResource() throws Exception
    {
        setVal( "cl", "classloader:" + PROBE_RESOURCE );

        MultiPropertiesConfig mpc = read( prop( "cl" ) );

        assertEquals( "42", mpc.getProperty( "c3p0.maxPoolSize" ) );
        assertEquals( "the whole document is read, extensions included",
                      "from-probe", mpc.getProperty( "c3p0.extensions.probeKey" ) );
    }

    /**
     *  With or without the leading slash. The manual says "in any location you specify under your
     *  classpath", and deployments write it both ways; getResources(..) wants it without.
     */
    public void testAClassloaderValueNeedsNoLeadingSlash() throws Exception
    {
        setVal( "noSlash", "classloader:" + PROBE_RESOURCE.substring( 1 ) );

        assertEquals( "42", read( prop( "noSlash" ) ).getProperty( "c3p0.maxPoolSize" ) );
    }

    /** The "classloader:" prefix is matched case-insensitively. */
    public void testTheClassloaderPrefixIsCaseInsensitive() throws Exception
    {
        // both before the first read, which seals the snapshot
        setVal( "upper", "CLASSLOADER:" + PROBE_RESOURCE );
        setVal( "mixed", "ClassLoader:" + PROBE_RESOURCE );

        assertEquals( "42", read( prop( "upper" ) ).getProperty( "c3p0.maxPoolSize" ) );
        assertEquals( "42", read( prop( "mixed" ) ).getProperty( "c3p0.maxPoolSize" ) );
    }

    /**
     *  ...but the resource path after the prefix is passed through untouched.
     *
     *  <p>Testing the prefix case-insensitively is easy to do by lowercasing the whole value and
     *  then taking the substring off the lowercased copy. That would break every resource whose
     *  path has a capital letter in it, which is most of them when the path names a class-shaped
     *  file. The implementation tests {@code propVal.toLowerCase()} but slices {@code propVal}.</p>
     *
     *  <p>Guarded, and honestly so: see {@link #resourceLookupIsCaseSensitive}. On macOS's
     *  default case-insensitive filesystem this assertion cannot fail, so it is skipped rather
     *  than counted. It bites in a jar and on a case-sensitive filesystem.</p>
     */
    public void testTheResourcePathsCaseIsPreserved() throws Exception
    {
        if ( !resourceLookupIsCaseSensitive() ) return;

        setVal( "casePath", "classloader:" + MIXED_CASE_RESOURCE );

        assertEquals( "a mixed-case resource path must survive the prefix test",
                      "4242", read( prop( "casePath" ) ).getProperty( "c3p0.maxPoolSize" ) );
    }

    /** A classloader: value naming nothing is ordinary absence, like any missing resource. */
    public void testAClassloaderValueNamingNothingIsAbsence() throws Exception
    {
        setVal( "missing", "classloader:/com/mchange/v2/c3p0/no/such/c3p0-config.xml" );

        MultiPropertiesConfig mpc = read( prop( "missing" ) );

        assertNull( mpc.getProperty( "c3p0.maxPoolSize" ) );
        assertEquals( "absence must not be fatal", 0, mpc.getPropertiesResourcePaths().length );
        assertTrue( "and a missing resource is not a malformed identifier: " + warningsOf( mpc ),
                    warningsOf( mpc ).isEmpty() );
    }

    /**
     *  "classloader:" with nothing after it names no resource, and is refused rather than read as
     *  the root. Inherited from the bare classloader form, which refuses "c3p0-xml:/" for the
     *  same reason, so the two forms cannot disagree about it.
     */
    public void testAClassloaderValueWithNoPathIsRefused() throws Exception
    {
        // both before the first read, which seals the snapshot
        setVal( "empty", "classloader:" );
        setVal( "rootOnly", "classloader:/" );

        assertNotNull( "expected a complaint about naming no resource",
                       warningMentioning( read( prop( "empty" ) ), "names no resource" ) );
        assertNotNull( "a bare slash is the same case",
                       warningMentioning( read( prop( "rootOnly" ) ), "names no resource" ) );
    }

    // =========================================================== absence, and the seal

    /**
     *  An unset property makes the configuration absent rather than malformed, which is what lets
     *  c3p0 name its optional XML file unconditionally in a shared resource-path list. It must
     *  also be QUIET: this is the state of nearly every c3p0 deployment, so a warning here would
     *  be a warning everywhere.
     */
    public void testAnUnsetPropertyIsOrdinaryAbsence() throws Exception
    {
        MultiPropertiesConfig mpc = read( prop( "neverSet" ) );

        assertNull( "nothing should have been read", mpc.getProperty( "c3p0.maxPoolSize" ) );
        assertEquals( "and the absence must not be fatal", 0, mpc.getPropertiesResourcePaths().length );
        assertTrue( "the common case must not warn: " + warningsOf( mpc ),
                    warningsOf( mpc ).isEmpty() );
    }

    /**
     *  The absence is still ACCOUNTED FOR, at FINE. OwnLogCarryingMissingFileException means the
     *  thrower has already made its own complete report, and its catcher adds nothing of its own
     *  -- so a source that throws it without adding an item makes the absence silent at every
     *  level, and someone debugging a config that will not load gets nothing back. An earlier
     *  draft did exactly that.
     */
    @SuppressWarnings("unchecked")
    public void testAnUnsetPropertyIsStillReportedAtFine() throws Exception
    {
        MultiPropertiesConfig mpc = read( prop( "neverSetEither" ) );

        boolean found = false;
        for ( Object o : mpc.getDelayedLogItems() )
        {
            DelayedLogItem item = (DelayedLogItem) o;
            if (! DelayedLogItem.Level.FINE.equals( item.getLevel() ) ) continue;
            String text = String.valueOf( item.getText() );
            if ( text.indexOf( PFX + "neverSetEither" ) >= 0 ) found = true;
        }
        assertTrue( "the skipped identifier should be accounted for at FINE, naming the property",
                    found );
    }

    /**
     *  The value is read from the sealed snapshot, not from live System properties.
     *
     *  <p>Setting the property from application code after startup must not redirect a config
     *  read. An earlier draft called System.getProperty(..) directly, which honored a late set --
     *  and, worse, disagreed with the file: machinery reading the very same property name from the
     *  snapshot.</p>
     */
    public void testAValueSetAfterTheSealIsNotHonored() throws Exception
    {
        // seal with the property unset: the read below must see the snapshot, not the live value
        read( prop( "notYetSet" ) );

        System.setProperty( PFX + "notYetSet", "classloader:" + PROBE_RESOURCE );

        MultiPropertiesConfig mpc = read( prop( "notYetSet" ) );

        assertNull( "a value set after the seal must not redirect a config read",
                    mpc.getProperty( "c3p0.maxPoolSize" ) );
    }

    // =========================================================== malformed identifiers

    /**
     *  The subscheme takes a property reference and nothing else. Each of these is a WARNING
     *  rather than a silent skip, because an identifier that cannot mean anything is a mistake in
     *  the resource-path list, not a missing file.
     */
    public void testAMalformedReferenceIsRefused() throws Exception
    {
        String[] malformed = new String[] {
            "c3p0-xml:prop:",                      // nothing at all
            "c3p0-xml:prop:nobraces",              // a bare name
            "c3p0-xml:prop:${}",                   // no name
            "c3p0-xml:prop:${   }",                // whitespace is not a name
            "c3p0-xml:prop: ${" + PFX + "x}",      // whitespace before the reference
            "c3p0-xml:prop:${" + PFX + "x} ",      // and after
        };

        for ( String identifier : malformed )
        {
            MultiPropertiesConfig mpc = read( identifier );
            assertNull( "should have read nothing: " + identifier, mpc.getProperty( "c3p0.maxPoolSize" ) );
            assertNotNull( "expected a WARNING naming the required shape, for: " + identifier,
                           warningMentioning( mpc, "${<some-var-name>}" ) );
        }
    }

    /** Whitespace inside the braces is tolerated, as the complaint above promises. */
    public void testWhitespaceInsideTheBracesIsTolerated() throws Exception
    {
        setVal( "padded", "classloader:" + PROBE_RESOURCE );

        MultiPropertiesConfig mpc = read( "c3p0-xml:prop:${  " + PFX + "padded  }" );

        assertEquals( "42", mpc.getProperty( "c3p0.maxPoolSize" ) );
    }

    /** The subscheme itself is matched case-insensitively, as 'c3p0-xml:' is. */
    public void testThePropSubschemeIsCaseInsensitive() throws Exception
    {
        setVal( "caseScheme", "classloader:" + PROBE_RESOURCE );

        assertEquals( "42", read( "c3p0-xml:PROP:${" + PFX + "caseScheme}" ).getProperty( "c3p0.maxPoolSize" ) );
        assertEquals( "42", read( "c3p0-xml:Prop:${" + PFX + "caseScheme}" ).getProperty( "c3p0.maxPoolSize" ) );
    }

    // =========================================================== the wiring

    /**
     *  Every c3p0-xml: identifier c3p0 actually DECLARES must be a form this source accepts.
     *
     *  <p>This is the case that would have caught the real bug. The subscheme was renamed in the
     *  source while <code>build.mill</code> went on declaring the old spelling, so the shipped
     *  resource-path list named a form nothing implemented -- and the only symptom was a WARNING
     *  on every single c3p0 startup, which no test asserted the absence of. The declared list and
     *  the implemented forms are two halves of one decision, written down in two files, and
     *  nothing but this checks that they agree.</p>
     *
     *  <p>Absence is fine here: the properties these identifiers name usually are not set, and
     *  not finding a file is not the same as not understanding an identifier. Only a parse
     *  complaint fails.</p>
     */
    public void testEveryDeclaredC3P0XmlIdentifierIsAFormThisSourceAccepts() throws Exception
    {
        List<String> declared = MLog.getConfigResourcePathsTxtPaths();
        assertNotNull( "c3p0 ships an mlog-config-resource-paths.txt; it should have been found",
                       declared );

        int checked = 0;
        for ( String identifier : declared )
        {
            if (! C3P0XmlPropertiesConfigSource.isC3P0XmlIdentifier( identifier ) ) continue;
            ++checked;
            try
            { new C3P0XmlPropertiesConfigSource().propertiesFromSource( identifier ); }
            catch ( Exception e )
            {
                // the identifier was understood; there was simply nothing at it
                if ( e instanceof java.io.FileNotFoundException ) continue;
                if ( e instanceof com.mchange.v2.cfg.OwnLogCarryingMissingFileException ) continue;

                fail( "declared identifier '" + identifier + "' is not a form C3P0XmlPropertiesConfigSource " +
                      "accepts -- build.mill and the source disagree. " + e.getClass().getName() + ": " + e.getMessage() );
            }
        }
        assertTrue( "c3p0 declares c3p0-xml: identifiers; some should have been checked", checked > 0 );
    }

    /** And the specific one the manual's com.mchange.v2.c3p0.cfg.xml story depends on. */
    public void testC3P0DeclaresItsXmlPropertyThroughThePropForm() throws Exception
    {
        List<String> declared = MLog.getConfigResourcePathsTxtPaths();
        assertNotNull( declared );
        assertTrue( "the declared list should name com.mchange.v2.c3p0.cfg.xml via prop:, found: " + declared,
                    declared.contains( "c3p0-xml:prop:${com.mchange.v2.c3p0.cfg.xml}" ) );
    }
}
