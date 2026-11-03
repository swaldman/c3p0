package com.mchange.v2.c3p0.test.cfglib;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

/**
 *  A synthetic classpath for the config classes, so a test can control what classloader
 *  resources they see.
 *
 *  <h3>Why this exists here</h3>
 *
 *  <p>c3p0's XML config source resolves the classloader form of its identifiers through
 *  <b>its own</b> ClassLoader &mdash;
 *  {@code C3P0XmlPropertiesConfigSource.class.getClassLoader().getResources(path)} &mdash; which
 *  is the only correct choice for a library (the thread context loader is whatever the caller
 *  last set) and also means there is no injecting a loader from outside. The only way to put a
 *  {@code /c3p0-config.xml} where that source will look is to load the source itself in a loader
 *  whose classpath you built.</p>
 *
 *  <p>This is a trimmed descendant of mchange-commons-java's own {@code CfgScenario}, which does
 *  the same job for the {@code com.mchange.v2.cfg} tests. That one is not reusable here:
 *  mchange-commons-java's test module is not published, so c3p0 cannot depend on it. Copied
 *  rather than extracted because only a fraction is wanted &mdash; no scenario-directory
 *  convention, no HOCON-present toggle, no cached-reader shim &mdash; and because a shared
 *  harness across two separately released projects would couple their test trees for no gain.</p>
 *
 *  <h3>The useful side effect</h3>
 *
 *  <p>Loading the classes afresh per scenario also resets their process-wide statics: the sealed
 *  System-properties snapshot, {@code MConfig}'s cache, and (since this release)
 *  {@code ExtraPropertiesConfigSources}' cached resolver list. Each case therefore looks like a
 *  fresh JVM, which is otherwise very difficult to arrange for one-way latches.</p>
 *
 *  <p>The parent is the platform loader rather than the application loader: it supplies the JDK
 *  while hiding every com.mchange class, so the child is forced to define its own copies. That
 *  is what makes the statics fresh, and it is why classes loaded here are reached reflectively
 *  &mdash; the copy under test is not the copy this test was compiled against.</p>
 */
public final class CfgLoaderScenario implements Closeable
{
    private final URLClassLoader loader;

    /**
     *  @param extraRoots directories to put FIRST on the classpath, in order, so earlier ones
     *                    shadow later ones exactly as classpath entries do
     */
    public static CfgLoaderScenario open( File... extraRoots )
    {
        List<URL> urls = new ArrayList<URL>();
        for ( File root : extraRoots ) urls.add( toUrl( root ) );

        // the code sources that carry the classes under test: c3p0's own, and the config library's
        urls.add( codeSourceOf( "com.mchange.v2.c3p0.cfg.C3P0XmlPropertiesConfigSource" ) );
        urls.add( codeSourceOf( "com.mchange.v2.cfg.MConfig" ) );

        return new CfgLoaderScenario( new URLClassLoader( urls.toArray( new URL[ urls.size() ] ),
                                                          platformClassLoader() ) );
    }

    private CfgLoaderScenario( URLClassLoader loader )
    { this.loader = loader; }

    public ClassLoader classLoader()
    { return loader; }

    public Class<?> loadClass( String className )
    {
        try { return loader.loadClass( className ); }
        catch ( ClassNotFoundException e )
        { throw new RuntimeException( "Scenario ClassLoader could not load " + className, e ); }
    }

    /**
     *  Renders delayed log items as {@code {level, text, exceptionClassName}} triples.
     *
     *  <p>Necessary because the items come from classes this loader defined, so they are not
     *  assignment-compatible with the copies the test was compiled against &mdash; the whole
     *  point of the fresh loader. Strings cross the boundary; instances do not.</p>
     */
    public List<String[]> renderLogItems( List<?> items )
    {
        List<String[]> out = new ArrayList<String[]>();
        if ( items == null ) return out;
        for ( Object item : items )
            out.add( new String[] { str( call( item, "getLevel" ) ),
                                    (String) call( item, "getText" ),
                                    className( call( item, "getException" ) ) } );
        return out;
    }

    private static String str( Object o )
    { return o == null ? null : o.toString(); }

    private static String className( Object o )
    { return o == null ? null : o.getClass().getName(); }

    private static Object call( Object target, String methodName )
    {
        try { return target.getClass().getMethod( methodName ).invoke( target ); }
        catch ( Exception e )
        { throw new RuntimeException( "Could not read " + methodName + "() from " + target, e ); }
    }

    @Override
    public void close() throws IOException
    { loader.close(); }

    public void closeQuietly()
    { try { close(); } catch ( IOException e ) { /* a test's leaked loader is not worth failing over */ } }

    // ---------------------------------------------------------------- classpath

    private static ClassLoader platformClassLoader()
    {
        // On Java 9+ the platform loader; on Java 8, the extension loader. Either way it carries
        // the JDK and no application classes. Reached this way rather than through
        // ClassLoader.getPlatformClassLoader() so this still compiles under a Java 8 toolchain.
        return ClassLoader.getSystemClassLoader().getParent();
    }

    private static URL toUrl( File f )
    {
        try { return f.toURI().toURL(); }
        catch ( MalformedURLException e )
        { throw new RuntimeException( "Could not use '" + f + "' as a classpath root", e ); }
    }

    private static URL codeSourceOf( String className )
    {
        try
        {
            Class<?> c = Class.forName( className );
            URL out = c.getProtectionDomain().getCodeSource().getLocation();
            if ( out == null )
                throw new RuntimeException( className + " has no code source location, so its classes " +
                                            "cannot be put on a synthetic classpath." );
            return out;
        }
        catch ( ClassNotFoundException e )
        { throw new RuntimeException( "Could not locate " + className, e ); }
    }
}
