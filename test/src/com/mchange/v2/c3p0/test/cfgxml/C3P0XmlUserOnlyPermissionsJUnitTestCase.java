package com.mchange.v2.c3p0.test.cfgxml;

import junit.framework.TestCase;

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

/**
 *  <code>permissions=useronly</code> must mean the same thing through
 *  <code>c3p0-xml:file:</code> as it does through <code>file:</code>.
 *
 *  <h3>Why this needs its own test</h3>
 *
 *  <p>The directive is implemented once, in mchange-commons-java's
 *  <code>FileUrlPropertiesConfigSource</code>, and c3p0's XML source borrows it rather than
 *  reimplementing it &mdash; which is the right arrangement, because these are security
 *  semantics and a second implementation would drift. But the borrowing is in two parts, and
 *  only one of them is hard to forget:</p>
 *
 *  <ul>
 *    <li><code>resolve(..)</code> performs the ownership-and-mode check on the file itself, and a
 *    source cannot skip it, since resolve is how the path is obtained at all.</li>
 *    <li><code>warnIfRemovableByOthers(..)</code> advises about the <i>directories above</i> the
 *    file &mdash; a file whose own mode is sound can still be deleted by anyone who can write a
 *    directory containing it, which no check on the file can prevent. It runs after a successful
 *    read, so a source that borrows resolve and stops there silently delivers three quarters of
 *    what the directive promises.</li>
 *  </ul>
 *
 *  <p>That is exactly what happened: the advisory was private to the implementing class and the
 *  XML source never called it, so for a while <code>permissions=useronly</code> on a
 *  <code>c3p0-xml:file:</code> identifier checked the file and said nothing about its
 *  surroundings. These cases hold the halves together, so the asymmetry cannot come back
 *  unnoticed &mdash; and so that the next source to borrow these utilities has an example of
 *  borrowing both.</p>
 *
 *  <p>The commons-side cases for the same directive live in
 *  <code>FileUrlConfigJUnitTestCase</code>; these deliberately mirror their fixtures, so a
 *  divergence in behaviour shows up as a divergence between two test classes that read alike.</p>
 */
public final class C3P0XmlUserOnlyPermissionsJUnitTestCase extends TestCase
{
    private final static String[] NONE = new String[0];

    private final static String XML =
        "<c3p0-config><default-config><property name=\"maxPoolSize\">42</property></default-config></c3p0-config>";

    private Path dir;

    @Override
    protected void setUp() throws Exception
    {
        // toRealPath: on macOS the temp dir sits under /var, itself a symlink, and the advisory
        // walks the REAL directory chain -- we want the paths we think we have
        dir = Files.createTempDirectory( "c3p0-cfgxml-useronly-" ).toRealPath();
    }

    @Override
    protected void tearDown()
    { deleteRecursively( dir ); }

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

    // ------------------------------------------------------------- fixture

    private boolean posixSupported()
    { return dir.getFileSystem().supportedFileAttributeViews().contains( "posix" ); }

    private Path mkdir( String name, String mode ) throws Exception
    {
        Path d = Files.createDirectory( dir.resolve( name ) );
        if ( posixSupported() ) Files.setPosixFilePermissions( d, PosixFilePermissions.fromString( mode ) );
        return d;
    }

    private Path writeXmlIn( Path parent, String name, String mode ) throws Exception
    {
        Path f = parent.resolve( name );
        Files.write( f, XML.getBytes( "UTF-8" ) );
        if ( posixSupported() ) Files.setPosixFilePermissions( f, PosixFilePermissions.fromString( mode ) );
        return f;
    }

    private static String id( Path p, String query )
    { return "c3p0-xml:" + p.toUri() + (query == null ? "" : "?" + query); }

    /** c3p0-xml: sources are vetoable, so reads go through the vetoable facade. */
    @SuppressWarnings("unchecked")
    private static MultiPropertiesConfig read( String... identifiers ) throws ConfigVetoedException
    { return MConfig.AsProvidedVetoable.readUncachedResourceConfig( NONE, identifiers, new ArrayList() ); }

    /**
     *  The deletion advisory's text, or null if none was emitted. Fails if more than one appears:
     *  the directory walk is supposed to stop at the first problem it finds, since the nearest
     *  offending directory is the one to fix first.
     */
    @SuppressWarnings("unchecked")
    private static String deletionAdvisory( MultiPropertiesConfig mpc )
    {
        String found = null;
        for ( Object o : mpc.getDelayedLogItems() )
        {
            DelayedLogItem item = (DelayedLogItem) o;
            String text = item.getText();
            if ( DelayedLogItem.Level.WARNING.equals( item.getLevel() )
                 && text != null && text.contains( "can delete this configuration" ) )
            {
                assertNull( "at most one deletion advisory per read; a second was: " + text, found );
                found = text;
            }
        }
        return found;
    }

    // ------------------------------------------------------------- the advisory

    /**
     *  THE CASE THIS CLASS EXISTS FOR. A world-writable, non-sticky parent draws the advisory
     *  through a c3p0-xml: identifier, exactly as it does through file:.
     */
    public void testAWorldWritableParentIsReportedThroughAC3P0XmlIdentifier() throws Exception
    {
        if ( !posixSupported() ) return;

        Path open = mkdir( "open-parent", "rwxrwxrwx" );          // NOT sticky: anyone may unlink
        Path f    = writeXmlIn( open, "c3p0-config.xml", "rw-------" );

        MultiPropertiesConfig mpc = read( id( f, "permissions=useronly" ) );

        assertEquals( "the advisory must not change what is read",
                      "42", mpc.getProperty( "c3p0.maxPoolSize" ) );

        String advisory = deletionAdvisory( mpc );
        assertNotNull( "a world-writable, non-sticky parent should be reported", advisory );
        assertTrue( "the advisory should name the offending directory: " + advisory,
                    advisory.contains( "directory '" + open + "'" ) );
        assertTrue( "and should name the identifier as written: " + advisory,
                    advisory.contains( id( f, "permissions=useronly" ) ) );
    }

    /** An ordinary private parent is silent -- the advisory is not simply always emitted. */
    public void testAPrivateParentIsSilent() throws Exception
    {
        if ( !posixSupported() ) return;

        Path shut = mkdir( "shut-parent", "rwx------" );
        Path f    = writeXmlIn( shut, "c3p0-config.xml", "rw-------" );

        MultiPropertiesConfig mpc = read( id( f, "permissions=useronly" ) );

        assertEquals( "42", mpc.getProperty( "c3p0.maxPoolSize" ) );
        assertNull( "an ordinary private directory must be silent", deletionAdvisory( mpc ) );
    }

    /**
     *  Without the directive there is no advisory: it is part of what
     *  {@code permissions=useronly} promises, not a thing said about every file.
     */
    public void testNoDirectiveMeansNoAdvisory() throws Exception
    {
        if ( !posixSupported() ) return;

        Path open = mkdir( "open-nodirective", "rwxrwxrwx" );
        Path f    = writeXmlIn( open, "c3p0-config.xml", "rw-rw-rw-" );

        MultiPropertiesConfig mpc = read( id( f, null ) );

        assertEquals( "42", mpc.getProperty( "c3p0.maxPoolSize" ) );
        assertNull( "no permissions=useronly, no advisory", deletionAdvisory( mpc ) );
    }

    /**
     *  {@code required=true} suppresses the advisory. Recorded as the decision it is, not as a
     *  claim that the risk is gone.
     *
     *  <p>What it suppresses is advice about <b>silence</b>. The message exists to say that a
     *  deletion would pass unnoticed, and it ends by recommending {@code required=true}; firing
     *  it at a deployment that has already done so would be advising someone to do what they
     *  have done, which is how an advisory that cannot be switched off teaches people to ignore
     *  it.</p>
     *
     *  <p>The exposure itself is NOT gone, and the suppression should not be read as saying so.
     *  A directory others can write still lets them delete the file &mdash; now a loud startup
     *  failure rather than a quiet misconfiguration, but still theirs to cause &mdash; and still
     *  lets them replace it, which {@code required=true} does not address at all. The file-level
     *  owner check is a partial backstop there, since a file owned by neither the current user
     *  nor root is refused; it is only partial, because a symlink planted in place of the config
     *  resolves to a target whose owner may well be acceptable.</p>
     *
     *  <p>So this case pins current behaviour, deliberately, and is the place to change if the
     *  suppression is ever narrowed &mdash; for instance to a shorter message that notes the
     *  directory is writable while acknowledging that a disappearance will at least be loud.</p>
     */
    public void testRequiredTrueSuppressesTheAdvisory() throws Exception
    {
        if ( !posixSupported() ) return;

        Path open = mkdir( "open-required", "rwxrwxrwx" );
        Path f    = writeXmlIn( open, "c3p0-config.xml", "rw-------" );

        MultiPropertiesConfig mpc = read( id( f, "permissions=useronly&required=true" ) );

        assertEquals( "42", mpc.getProperty( "c3p0.maxPoolSize" ) );
        assertNull( "the advice about silent omission is suppressed once required=true makes a " +
                    "disappearance loud -- which is not to say the writable directory is now safe",
                    deletionAdvisory( mpc ) );
    }

    /** The advisory never vetoes: the config is read, and a second identifier still reads too. */
    public void testTheAdvisoryNeverVetoes() throws Exception
    {
        if ( !posixSupported() ) return;

        Path open  = mkdir( "open-noveto", "rwxrwxrwx" );
        Path risky = writeXmlIn( open, "c3p0-config.xml", "rw-------" );

        Path shut  = mkdir( "shut-noveto", "rwx------" );
        Path sound = shut.resolve( "other-config.xml" );
        Files.write( sound, "<c3p0-config><default-config><property name=\"minPoolSize\">7</property></default-config></c3p0-config>".getBytes( "UTF-8" ) );
        Files.setPosixFilePermissions( sound, PosixFilePermissions.fromString( "rw-------" ) );

        MultiPropertiesConfig mpc = read( id( risky, "permissions=useronly" ), id( sound, "permissions=useronly" ) );

        assertNotNull( "the risky file is still read", deletionAdvisory( mpc ) );
        assertEquals( "42", mpc.getProperty( "c3p0.maxPoolSize" ) );
        assertEquals( "and the rest of the read proceeds normally", "7", mpc.getProperty( "c3p0.minPoolSize" ) );
    }

    // ------------------------------------------------------------- the file's own mode

    /**
     *  And the half that was never in doubt, so that this class covers the whole directive: a
     *  world-readable file is REFUSED, not merely warned about.
     *
     *  <p>Together with the cases above this pins the distinction worth keeping straight: the
     *  file's own exposure is a veto, while its surroundings' exposure is an advisory. Confusing
     *  the two in either direction would be bad -- a veto on a merely risky directory would
     *  break working deployments, and an advisory on a world-readable secret would leak it.</p>
     */
    public void testAWorldReadableFileIsVetoedNotMerelyAdvised() throws Exception
    {
        if ( !posixSupported() ) return;

        Path shut    = mkdir( "shut-exposed", "rwx------" );
        Path exposed = writeXmlIn( shut, "c3p0-config.xml", "rw-r--r--" );

        try
        {
            read( id( exposed, "permissions=useronly" ) );
            fail( "a world-readable config file must be refused under permissions=useronly" );
        }
        catch ( ConfigVetoedException expected )
        {
            assertTrue( "the veto should be about permissions: " + expected.getMessage(),
                        expected.getMessage().contains( "permissions" ) );
        }
    }
}
