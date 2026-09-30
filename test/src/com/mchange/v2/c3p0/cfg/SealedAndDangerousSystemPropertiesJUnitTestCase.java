package com.mchange.v2.c3p0.cfg;

import java.lang.reflect.Field;
import java.util.Properties;
import java.util.Set;

import com.mchange.v2.c3p0.impl.C3P0Defaults;
import com.mchange.v2.cfg.SealedSystemProperties;

import junit.framework.TestCase;

/**
 *  c3p0 reads its <code>c3p0.*</code> settings from System properties, and
 *  C3P0Config.refreshMainConfig() is public API, so that read happens again on every refresh.
 *  Left alone it would mean a System property written at any point in a process's life takes
 *  effect the next time anything refreshes -- and among the 47 known properties are several
 *  that name a class to instantiate or a codebase to load it from.
 *
 *  <p>Sealing all of them was not an option: runtime reconfiguration is supported, and most of
 *  these are pool tuning. So the rule has two halves, and both halves need holding down.</p>
 *
 *  <ul>
 *    <li>A value present when the snapshot was sealed always wins, for every property. It
 *        cannot be changed, and it cannot be removed.</li>
 *    <li>A property <i>absent</i> at seal time may still be introduced later and take
 *        effect -- that is the supported reconfiguration -- <i>unless</i> it is one of the
 *        dangerous ones, for which absent stays absent.</li>
 *  </ul>
 *
 *  <p>The second exception is the one worth being careful about. Introducing a setting is the
 *  interesting attack, not modifying one: nobody configures connectionCustomizerClassName, so
 *  for exactly the properties where protection matters most, "cannot be changed" alone would
 *  have bound least often.</p>
 *
 *  <p>No database is needed.</p>
 */
public class SealedAndDangerousSystemPropertiesJUnitTestCase extends TestCase
{
    private final static String ORDINARY  = "maxPoolSize";                   // pool tuning
    private final static String DANGEROUS = "connectionCustomizerClassName"; // names a class to instantiate

    /** Saved and restored around each case, so a preset c3p0.* in the environment survives. */
    private final static String[] TOUCHED =
    {
        "c3p0." + ORDINARY,
        "c3p0." + DANGEROUS,
        "c3p0.connectionTesterClassName",
        "c3p0.taskRunnerFactoryClassName",
        "c3p0.driverClass",
        "c3p0.factoryClassLocation",
        "c3p0.privilegeSpawnedThreads"
    };

    private Properties saved;

    @Override
    protected void setUp() throws Exception
    {
        saved = new Properties();
        for ( String k : TOUCHED )
        {
            String v = System.getProperty( k );
            if ( v != null ) saved.setProperty( k, v );
            System.clearProperty( k );
        }
        asAFreshJvm();
    }

    @Override
    protected void tearDown() throws Exception
    {
        for ( String k : TOUCHED )
        {
            String v = saved.getProperty( k );
            if ( v == null ) System.clearProperty( k ); else System.setProperty( k, v );
        }
        asAFreshJvm();
    }

    /**
     *  Begin again as an unstarted JVM would, so a property set now lands in the snapshot.
     *
     *  <p>The seal is deliberately one-way and offers no reset -- a seal that could be undone
     *  would not be a seal -- but every case here needs to look like a fresh process, so we
     *  reach past it reflectively. Only tests may do this.</p>
     */
    private static void asAFreshJvm() throws Exception
    {
        Field f = SealedSystemProperties.class.getDeclaredField( "theProperties" );
        f.setAccessible( true );
        f.set( null, null );
    }

    /** The values c3p0 would actually adopt from System properties right now. */
    private static Properties effective()
    { return C3P0ConfigUtils.findAllC3P0SystemPropertiesRespectSealedAndDangerousProperties(); }

    // ==================== what the seal holds, for every property ====================

    public void testAValueSetBeforeTheSealIsAdopted()
    {
        System.setProperty( "c3p0." + ORDINARY, "42" );

        assertEquals( "42", effective().getProperty( ORDINARY ) );
    }

    public void testASealedValueCannotBeChanged()
    {
        System.setProperty( "c3p0." + ORDINARY, "42" );
        assertEquals( "Precondition: sealed at 42.", "42", effective().getProperty( ORDINARY ) );

        System.setProperty( "c3p0." + ORDINARY, "99" );

        assertEquals( "The sealed value must survive a later write.",
                      "42", effective().getProperty( ORDINARY ) );
    }

    /** Removal is a change too: clearing a sealed property must not unset it. */
    public void testASealedValueCannotBeRemoved()
    {
        System.setProperty( "c3p0." + ORDINARY, "42" );
        assertEquals( "Precondition: sealed at 42.", "42", effective().getProperty( ORDINARY ) );

        System.clearProperty( "c3p0." + ORDINARY );

        assertEquals( "42", effective().getProperty( ORDINARY ) );
    }

    // ==================== what may still be introduced, and what may not ====================

    /**
     *  Runtime reconfiguration is supported, and this is the half of the rule that keeps it
     *  working. If this test fails, the protection has been bought by breaking a feature.
     */
    public void testAnOrdinaryPropertyMayBeIntroducedAfterTheSeal()
    {
        assertNull( "Precondition: nothing was sealed for this key.", effective().getProperty( ORDINARY ) );

        System.setProperty( "c3p0." + ORDINARY, "77" );

        assertEquals( "Pool tuning must remain reconfigurable at runtime.",
                      "77", effective().getProperty( ORDINARY ) );
    }

    /** A dangerous property, though, must not appear where none was configured. */
    public void testADangerousPropertyMayNotBeIntroducedAfterTheSeal()
    {
        assertNull( "Precondition: nothing was sealed for this key.", effective().getProperty( DANGEROUS ) );

        System.setProperty( "c3p0." + DANGEROUS, "com.example.IntroducedAfterTheSeal" );

        assertNull( "Naming a class to instantiate where none was configured is the attack this exists for.",
                    effective().getProperty( DANGEROUS ) );
    }

    /** Dangerous is not the same as forbidden: configured before the seal, it is honored. */
    public void testADangerousPropertySetBeforeTheSealIsAdopted()
    {
        System.setProperty( "c3p0." + DANGEROUS, "com.example.SetBeforeTheSeal" );

        assertEquals( "com.example.SetBeforeTheSeal", effective().getProperty( DANGEROUS ) );
    }

    public void testADangerousPropertyKeepsItsSealedValueWhenChanged()
    {
        System.setProperty( "c3p0." + DANGEROUS, "com.example.SetBeforeTheSeal" );
        assertEquals( "Precondition.", "com.example.SetBeforeTheSeal", effective().getProperty( DANGEROUS ) );

        System.setProperty( "c3p0." + DANGEROUS, "com.example.ChangedAfterTheSeal" );

        assertEquals( "com.example.SetBeforeTheSeal", effective().getProperty( DANGEROUS ) );
    }

    /** Blank values were always skipped, and the new branching must not have changed that. */
    public void testABlankValueIsIgnored()
    {
        System.setProperty( "c3p0." + ORDINARY, "   " );

        assertNull( effective().getProperty( ORDINARY ) );
    }

    // ==================== which properties are dangerous ====================

    /**
     *  Every *ClassName property is derived rather than listed, so one added later is covered
     *  without anyone remembering to come back here.
     */
    public void testEveryClassNamePropertyIsDangerous()
    {
        int found = 0;
        for ( Object o : C3P0Defaults.getKnownProperties( null ) )
        {
            String prop = (String) o;
            if ( prop.indexOf( "ClassName" ) >= 0 )
            {
                assertTrue( prop + " names a class to instantiate and must be dangerous.",
                            C3P0ConfigUtils.DANGEROUS_NO_PREFIX_C3P0_PROPERTIES.contains( prop ) );
                ++found;
            }
        }
        assertTrue( "The derivation should have matched something; it currently matches three.", found >= 3 );
    }

    /** The three that the *ClassName pattern cannot see. */
    public void testTheExplicitlyListedDangerousPropertiesAreDangerous()
    {
        Set<String> dangerous = C3P0ConfigUtils.DANGEROUS_NO_PREFIX_C3P0_PROPERTIES;

        assertTrue( "driverClass names a class to instantiate.", dangerous.contains( "driverClass" ) );
        assertTrue( "factoryClassLocation names a codebase to load classes from.",
                    dangerous.contains( "factoryClassLocation" ) );
        assertTrue( "privilegeSpawnedThreads affects the privileges spawned threads run with.",
                    dangerous.contains( "privilegeSpawnedThreads" ) );
    }

    /**
     *  A misspelled literal would silently protect nothing -- the set would contain a name no
     *  property ever has, and the real property would stay introducible.
     */
    public void testEveryDangerousPropertyIsAPropertyThatExists()
    {
        for ( String prop : C3P0ConfigUtils.DANGEROUS_NO_PREFIX_C3P0_PROPERTIES )
            assertTrue( "'" + prop + "' is listed as dangerous but is not a known c3p0 property.",
                        C3P0Defaults.isKnownProperty( prop ) );
    }

    /** and the protection must stay narrow, or it would break reconfiguration wholesale. */
    public void testOrdinaryTuningPropertiesAreNotDangerous()
    {
        Set<String> dangerous = C3P0ConfigUtils.DANGEROUS_NO_PREFIX_C3P0_PROPERTIES;

        assertFalse( dangerous.contains( "maxPoolSize" ) );
        assertFalse( dangerous.contains( "minPoolSize" ) );
        assertFalse( dangerous.contains( "checkoutTimeout" ) );
        assertTrue( "The dangerous set should stay a small minority of the known properties.",
                    dangerous.size() * 4 < C3P0Defaults.getKnownProperties( null ).size() );
    }
}
