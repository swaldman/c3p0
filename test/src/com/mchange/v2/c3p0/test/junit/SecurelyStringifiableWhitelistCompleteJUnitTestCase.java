package com.mchange.v2.c3p0.test.junit;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.lang.reflect.Modifier;

import junit.framework.TestCase;

import com.mchange.v2.log.MLog;
import com.mchange.v2.log.MLogger;
import com.mchange.v2.cfg.SealedSystemPropertiesWhitelistManager;
import com.mchange.v2.naming.SecurelyStringifiable;
import com.mchange.v2.naming.SecurityConfigKey;
import com.mchange.v2.c3p0.ComboPooledDataSource;
import com.mchange.v2.c3p0.cfg.C3P0Config;

/**
 *  Asserts that every SecurelyStringifiable class c3p0 ships is named by c3p0's own
 *  securelyStringifiable whitelist.
 *
 *  <p>Reconstruction of a nested property value is gated by that whitelist, and
 *  JavaBeanObjectFactory handles a refusal by logging a warning and omitting the property.
 *  Reconstructing what you can is sometimes the useful behavior, so warn-and-continue stays --
 *  but it means a class we ship and forget to whitelist produces a partially reconstructed
 *  DataSource rather than a failure, and the symptom surfaces later and elsewhere. For our own
 *  classes that is never the behavior we want, so this test holds the whitelist complete.
 *
 *  <p>It discovers the classes rather than listing them, so adding a SecurelyStringifiable class
 *  to the library without adding it to resources/c3p0-default.properties fails here, at the point
 *  the omission is introduced. The scan covers the library's own code source only: classes from
 *  optional distributions that ship separately (c3p0-loom, say) are not in it, so those remain a
 *  matter of review.
 *
 *  <p>The gate itself -- that this configuration actually reaches a real reconstruction -- is
 *  pinned separately, by NestedDataSourceRoundTripJUnitTestCase. This test asks only whether the
 *  configuration names the right classes, which is why it consults the whitelist directly instead
 *  of reconstructing anything: constructing arbitrary DataSources from synthetic payloads would
 *  register pools and start threads to prove a point about a properties file.
 */
public final class SecurelyStringifiableWhitelistCompleteJUnitTestCase extends TestCase
{
    public void testEverySecurelyStringifiableClassWeShipIsWhitelisted() throws Exception
    {
        TreeSet<String> shipped = securelyStringifiableClassesInC3P0CodeSource();

        // A scan that found nothing would pass vacuously, and silently stop testing the moment
        // the code source layout changed. We ship five; fewer than a few means the scan broke.
        assertTrue( "The scan should find the SecurelyStringifiable classes c3p0 ships -- finding " +
                    "none means the scan itself is broken, not that the whitelist is complete. Found: " + shipped,
                    shipped.size() >= 3 );

        MLogger logger = MLog.getLogger( SecurelyStringifiableWhitelistCompleteJUnitTestCase.class );
        SealedSystemPropertiesWhitelistManager wm =
            new SealedSystemPropertiesWhitelistManager( SecurityConfigKey.SECURELY_STRINGIFIABLE_BASE_KEY, null );

        List<String> unwhitelisted = new ArrayList<>();
        for ( String fqcn : shipped )
            if (! wm.whitelistAccepts( fqcn, C3P0Config.getMultiPropertiesConfig(), logger ))
                unwhitelisted.add( fqcn );

        assertTrue( "c3p0 ships these SecurelyStringifiable classes but does not whitelist them at '" +
                    SecurityConfigKey.SECURELY_STRINGIFIABLE_BASE_KEY + ".whitelist' (or a subkey) in " +
                    "resources/c3p0-default.properties. Reconstructing one of these as a nested property " +
                    "value would be refused, and JavaBeanObjectFactory would warn and omit the property, " +
                    "yielding a partially reconstructed object: " + unwhitelisted +
                    " -- all SecurelyStringifiable classes found: " + shipped,
                    unwhitelisted.isEmpty() );
    }

    /**
     *  Concrete, loadable, SecurelyStringifiable classes in whatever jar or directory c3p0 itself
     *  was loaded from. Classes are resolved without initialization: the point is to inspect the
     *  shipped surface, not to run any of it.
     */
    private static TreeSet<String> securelyStringifiableClassesInC3P0CodeSource() throws Exception
    {
        URL location = ComboPooledDataSource.class.getProtectionDomain().getCodeSource().getLocation();
        assertNotNull( "c3p0 should have a code source to scan.", location );
        File codeSource = new File( location.toURI() );

        List<String> candidates = new ArrayList<>();
        if ( codeSource.isDirectory() )
            collectClassNames( codeSource, codeSource.getPath().length() + 1, candidates );
        else
        {
            try ( JarFile jf = new JarFile( codeSource ) )
            {
                for ( Enumeration<JarEntry> e = jf.entries(); e.hasMoreElements(); )
                {
                    String name = e.nextElement().getName();
                    if ( name.endsWith( ".class" ) )
                        candidates.add( name.substring( 0, name.length() - 6 ).replace( '/', '.' ) );
                }
            }
        }

        ClassLoader cl = SecurelyStringifiableWhitelistCompleteJUnitTestCase.class.getClassLoader();
        TreeSet<String> out = new TreeSet<>();
        for ( String name : candidates )
        {
            Class<?> c;
            try { c = Class.forName( name, false, cl ); }
            catch ( Throwable t ) { continue; } // optional dependency absent, or otherwise unresolvable
            if ( c.isInterface() || Modifier.isAbstract( c.getModifiers() ) )
                continue; // securelyStringify writes getClass().getName(), so only concrete classes are ever named
            if ( SecurelyStringifiable.isSecurelyStringifiable( c ) )
                out.add( name );
        }
        return out;
    }

    private static void collectClassNames( File dir, int prefixLen, List<String> out )
    {
        File[] fs = dir.listFiles();
        if ( fs == null ) return;
        for ( File f : fs )
        {
            if ( f.isDirectory() )
                collectClassNames( f, prefixLen, out );
            else
            {
                String path = f.getPath();
                if ( path.endsWith( ".class" ) )
                    out.add( path.substring( prefixLen, path.length() - 6 ).replace( File.separatorChar, '.' ) );
            }
        }
    }
}
