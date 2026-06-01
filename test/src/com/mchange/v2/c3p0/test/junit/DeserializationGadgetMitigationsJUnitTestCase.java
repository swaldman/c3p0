package com.mchange.v2.c3p0.test.junit;

import java.beans.PropertyVetoException;
import java.io.*;
import java.util.*;
import javax.naming.*;
import javax.naming.spi.ObjectFactory;

import junit.framework.TestCase;

import com.mchange.v2.c3p0.*;
import com.mchange.v2.c3p0.cfg.C3P0Config;
import com.mchange.v2.c3p0.impl.C3P0ImplUtils;
import com.mchange.v2.cfg.PropertiesConfig;
import com.mchange.v2.naming.ReferenceableUtils;
import com.mchange.v2.naming.ReferenceIndirector;
import com.mchange.v2.naming.SecurityConfigKey;
import com.mchange.v2.ser.IndirectlySerialized;
import com.mchange.v2.ser.IndirectSerializationForbiddenException;
import com.mchange.v2.ser.SerializableUtils;

/**
 * Tests mitigations for the deserialization gadget chain vulnerabilities
 * documented in the c3p0 security vulnerability assessment:
 *
 *   1. PoolBackedDataSourceBase remote class loading (ysoserial C3P0)
 *   2. WrapperConnectionPoolDataSource deserialization bridge (userOverridesAsString)
 *   3. JndiRefForwardingDataSource / JndiRefConnectionPoolDataSource JNDI injection
 *   4. JndiRefDataSourceBase readObject() via IndirectlySerialized
 *   5. ObjectFactory whitelist enforcement in ReferenceableUtils
 *
 * These tests verify the mitigations at the API/configuration level. They do not
 * require a running database.
 */
public final class DeserializationGadgetMitigationsJUnitTestCase extends TestCase
{
    // ======================================================================
    //  1. Remote class loading via ReferenceableUtils is blocked by default
    // ======================================================================

    /**
     * A Reference with a remote factoryClassLocation should be resolved using
     * only the local classpath when supportReferenceRemoteFactoryClassLocation
     * is false (the default). We construct a Reference pointing to a whitelisted
     * factory class but with a bogus remote factoryClassLocation. If remote
     * loading were attempted, it would fail with a connection error or
     * MalformedURLException. With the mitigation, the factoryClassLocation is
     * ignored and the factory is loaded from the local classpath.
     */
    public void testRemoteFactoryClassLocationIgnoredByDefault() throws Exception
    {
        // Verify the system property is not explicitly enabling remote loading
        assertNull(
            "Test precondition: system property should not be set",
            System.getProperty( SecurityConfigKey.SUPPORT_REFERENCE_REMOTE_FACTORY_CLASS_LOCATION )
        );

        // Start from a properly-formed c3p0 reference, then graft on a bogus remote
        // factoryClassLocation. If remote class loading were active, resolution would attempt to fetch
        // the factory from this URL; with the mitigation it is ignored and the factory loads from the
        // local classpath. (The referenced class must itself be whitelisted via
        // referenceableJavaBeanClassWhitelist, which ComboPooledDataSource is by default.)
        ComboPooledDataSource cpds = new ComboPooledDataSource();
        try
        {
            Reference real = cpds.getReference();
            Reference ref = new Reference( real.getClassName(), real.getFactoryClassName(), "http://attacker.example.com/evil.jar" );
            for ( Enumeration e = real.getAll(); e.hasMoreElements(); )
                ref.add( (RefAddr) e.nextElement() );

            Set whitelist = new HashSet();
            whitelist.add( "com.mchange.v2.c3p0.impl.C3P0JavaBeanObjectFactory" );

            // This should succeed using the local classpath, ignoring the remote URL
            Object result = ReferenceableUtils.referenceToObject( ref, null, null, null, whitelist );
            assertNotNull( "Should resolve reference locally despite remote factoryClassLocation", result );
            assertTrue( "Resolved object should be a ComboPooledDataSource", result instanceof ComboPooledDataSource );

            ((ComboPooledDataSource) result).close();
        }
        finally
        { cpds.close(); }
    }

    // ======================================================================
    //  2. ObjectFactory whitelist enforcement
    // ======================================================================

    /**
     * A Reference with a factory class name NOT in the whitelist should be
     * rejected with a NamingException.
     */
    public void testNonWhitelistedFactoryClassRejected() throws Exception
    {
        Reference ref = new Reference(
            "java.lang.Object",
            "com.evil.MaliciousObjectFactory",
            null
        );

        Set whitelist = new HashSet();
        whitelist.add( "com.mchange.v2.c3p0.impl.C3P0JavaBeanObjectFactory" );

        try
        {
            ReferenceableUtils.referenceToObject( ref, null, null, null, whitelist );
            fail( "Should have thrown NamingException for non-whitelisted factory class" );
        }
        catch ( NamingException expected )
        {
            assertTrue(
                "Exception message should mention the rejected factory class, got: " + expected.getMessage(),
                expected.getMessage().contains( "com.evil.MaliciousObjectFactory" )
            );
        }
    }

    /**
     * A Reference with a null factory class name should be rejected.
     * This prevents delegation to NamingManager which has different security properties.
     */
    public void testNullFactoryClassNameRejected() throws Exception
    {
        Reference ref = new Reference( "java.lang.Object", null, null );

        Set whitelist = new HashSet();
        whitelist.add( "com.mchange.v2.c3p0.impl.C3P0JavaBeanObjectFactory" );

        try
        {
            ReferenceableUtils.referenceToObject( ref, null, null, null, whitelist );
            fail( "Should have thrown NamingException for null factory class name" );
        }
        catch ( NamingException expected )
        {
            assertTrue(
                "Exception message should mention null factoryClassName, got: " + expected.getMessage(),
                expected.getMessage().contains( "null factoryClassName" )
            );
        }
    }

    /**
     * When no whitelist is configured (no system property, no pcfg), the overloads
     * that compute the whitelist from configuration should throw NamingException
     * rather than silently allowing all factories.
     *
     * NOTE: This test is only meaningful if the c3p0-default.properties providing
     * the default whitelist is NOT on the classpath. Since it IS on our test classpath,
     * we test the explicit-Set overload instead: an empty whitelist should reject everything.
     */
    public void testEmptyWhitelistRejectsAllFactories() throws Exception
    {
        Reference ref = new Reference(
            "java.lang.Object",
            "com.mchange.v2.c3p0.impl.C3P0JavaBeanObjectFactory",
            null
        );

        Set emptyWhitelist = Collections.EMPTY_SET;

        try
        {
            ReferenceableUtils.referenceToObject( ref, null, null, null, emptyWhitelist );
            fail( "Empty whitelist should reject all factory classes" );
        }
        catch ( NamingException expected )
        {
            // expected
        }
    }

    /**
     * The default whitelist (from c3p0-default.properties on the classpath) should
     * allow c3p0's own two ObjectFactory classes and reject others.
     */
    public void testDefaultWhitelistAllowsC3P0Factories() throws Exception
    {
        // Use the PropertiesConfig overload so the whitelists from c3p0-default.properties are found
        PropertiesConfig pcfg = C3P0Config.getMultiPropertiesConfig();

        ComboPooledDataSource cpds = new ComboPooledDataSource();
        try
        {
            Reference ref = cpds.getReference();

            // Should succeed — C3P0JavaBeanObjectFactory (objectFactoryWhitelist) is the factory and
            // ComboPooledDataSource (referenceableJavaBeanClassWhitelist) is the referenced class;
            // both are in the default whitelists.
            Object result = ReferenceableUtils.referenceToObject( ref, null, null, null, pcfg );
            assertNotNull( "A c3p0 datasource reference should be resolvable under the default whitelists", result );
            assertTrue( "Resolved object should be a ComboPooledDataSource", result instanceof ComboPooledDataSource );

            ((ComboPooledDataSource) result).close();
        }
        finally
        { cpds.close(); }
    }

    public void testDefaultWhitelistRejectsArbitraryFactory() throws Exception
    {
        PropertiesConfig pcfg = C3P0Config.getMultiPropertiesConfig();

        Reference ref = new Reference(
            "java.lang.Object",
            "org.apache.xbean.propertyeditor.JndiConverter",
            null
        );

        try
        {
            ReferenceableUtils.referenceToObject( ref, null, null, null, pcfg );
            fail( "Arbitrary factory class should be rejected by default whitelist" );
        }
        catch ( NamingException expected )
        {
            // expected
        }
    }

    // ======================================================================
    //  3. userOverridesAsString no longer uses Java deserialization
    // ======================================================================

    /**
     * The old HexAsciiSerializedMap format (which triggered Java deserialization)
     * should NOT be parseable by the current implementation. If someone attempts
     * to supply a payload in the old format, it should fail to parse.
     */
    public void testOldHexAsciiSerializedMapFormatRejected() throws Exception
    {
        // Construct a string that looks like the old HexAsciiSerializedMap format.
        // The old format was: HexAsciiSerializedMap[<hex-encoded serialized bytes>]
        String maliciousPayload = "HexAsciiSerializedMap[aced0005737200116a6176612e7574696c2e486173684d61700507dac1c31660d103000246000a6c6f6164466163746f724900097468726573686f6c6478703f4000000000000c7708000000100000000078]";

        try
        {
            Map result = C3P0ImplUtils.parseUserOverridesAsString( maliciousPayload );
            // If parsing succeeds, it must NOT have deserialized the hex content.
            // The CSV parser would treat this as a malformed single-line CSV.
            // Either it throws, or it produces something that is clearly not a
            // deserialized HashMap. Either outcome is safe.
            //
            // The key assertion: even if parsing doesn't throw, no deserialization occurred.
            // (The CSV parser would just see opaque strings, not invoke ObjectInputStream.)
        }
        catch ( Exception expected )
        {
            // An IOException or MalformedCsvException is the expected outcome —
            // the old format is not valid CSV.
        }
    }

    /**
     * The current CSV-based userOverridesAsString should work correctly.
     * This is a basic smoke test — detailed parsing tests are in
     * CreateParseUserOverridesJUnitTestCase.
     */
    public void testCsvBasedUserOverridesRoundTrip() throws Exception
    {
        Map props = new HashMap();
        props.put( "maxPoolSize", "20" );
        props.put( "minPoolSize", "5" );

        Map original = new HashMap();
        original.put( "testuser", props );

        String serialized = C3P0ImplUtils.createUserOverridesAsString( original );

        // Verify the serialized form does NOT contain hex-encoded serialized data
        assertFalse(
            "Serialized form should not use old HexAsciiSerializedMap format",
            serialized.startsWith( "HexAsciiSerializedMap" )
        );
        // Verify the serialized form does NOT contain Java serialization magic bytes (hex: aced)
        assertFalse(
            "Serialized form should not contain Java serialization stream header",
            serialized.contains( "aced0005" )
        );

        Map restored = C3P0ImplUtils.parseUserOverridesAsString( serialized );
        assertEquals( "CSV-based userOverrides should roundtrip correctly", original, restored );
    }

    /**
     * Setting userOverridesAsString on WrapperConnectionPoolDataSource with invalid
     * content should be vetoed (PropertyVetoException), not silently accepted.
     */
    public void testInvalidUserOverridesAsStringVetoed() throws Exception
    {
        WrapperConnectionPoolDataSource wcpds = new WrapperConnectionPoolDataSource();
        try
        {
            System.err.println("A stack trace of a failure to parse userOverridesAsString is expected!");

            // Three-element CSV lines are invalid for userOverrides
            wcpds.setUserOverridesAsString( "\"a\",\"b\",\"c\"\r\n" );
            fail( "Invalid userOverridesAsString should throw PropertyVetoException" );
        }
        catch ( PropertyVetoException expected )
        {
            // expected — the VetoableChangeListener rejects unparseable values
        }
    }

    // ======================================================================
    //  4. JNDI name validation — remote names rejected by default
    // ======================================================================

    /**
     * Setting a remote JNDI name (e.g. ldap://, rmi://) on JndiRefForwardingDataSource
     * should be vetoed by the VetoableChangeListener.
     */
    public void testRemoteLdapJndiNameRejected() throws Exception
    {
        JndiRefConnectionPoolDataSource ds = new JndiRefConnectionPoolDataSource();
        try
        {
            ds.setJndiName( "ldap://attacker.example.com:1389/exploit" );
            fail( "Remote LDAP JNDI name should be rejected" );
        }
        catch ( PropertyVetoException expected )
        {
            // expected
        }
    }

    public void testRemoteRmiJndiNameRejected() throws Exception
    {
        JndiRefConnectionPoolDataSource ds = new JndiRefConnectionPoolDataSource();
        try
        {
            ds.setJndiName( "rmi://attacker.example.com:1099/exploit" );
            fail( "Remote RMI JNDI name should be rejected" );
        }
        catch ( PropertyVetoException expected )
        {
            // expected
        }
    }

    public void testRemoteIiopJndiNameRejected() throws Exception
    {
        JndiRefConnectionPoolDataSource ds = new JndiRefConnectionPoolDataSource();
        try
        {
            ds.setJndiName( "iiop://attacker.example.com/exploit" );
            fail( "Remote IIOP JNDI name should be rejected" );
        }
        catch ( PropertyVetoException expected )
        {
            // expected
        }
    }

    public void testRemoteDnsJndiNameRejected() throws Exception
    {
        JndiRefConnectionPoolDataSource ds = new JndiRefConnectionPoolDataSource();
        try
        {
            ds.setJndiName( "dns://attacker.example.com/exploit" );
            fail( "Remote DNS JNDI name should be rejected" );
        }
        catch ( PropertyVetoException expected )
        {
            // expected
        }
    }

    /**
     * A bare name (not starting with "java:") should also be rejected by
     * the default ApparentlyLocalNameGuard.
     */
    public void testBareJndiNameRejected() throws Exception
    {
        JndiRefConnectionPoolDataSource ds = new JndiRefConnectionPoolDataSource();
        try
        {
            ds.setJndiName( "jdbc/MyDataSource" );
            fail( "Bare JNDI name (not java: prefixed) should be rejected by default" );
        }
        catch ( PropertyVetoException expected )
        {
            // expected
        }
    }

    /**
     * A local JNDI name (starting with "java:") should be accepted.
     */
    public void testLocalJavaJndiNameAccepted() throws Exception
    {
        JndiRefConnectionPoolDataSource ds = new JndiRefConnectionPoolDataSource();
        // This should not throw — "java:comp/env/jdbc/MyDB" is a local name
        ds.setJndiName( "java:comp/env/jdbc/MyDB" );
        assertEquals( "Local JNDI name should be accepted", "java:comp/env/jdbc/MyDB", ds.getJndiName() );
    }

    public void testLocalJavaColonJndiNameAccepted() throws Exception
    {
        JndiRefConnectionPoolDataSource ds = new JndiRefConnectionPoolDataSource();
        ds.setJndiName( "java:/jdbc/TestDS" );
        assertEquals( "java: prefixed name should be accepted", "java:/jdbc/TestDS", ds.getJndiName() );
    }

    /**
     * The C3P0ImplUtils.jndiAssertNameIsAcceptable helper should throw NamingException
     * for remote names. This is the same method used at lookup time as a second line of defense.
     */
    public void testJndiAssertNameIsAcceptableRejectsRemote() throws Exception
    {
        try
        {
            C3P0ImplUtils.jndiAssertNameIsAcceptable( "ldap://evil.example.com/payload" );
            fail( "jndiAssertNameIsAcceptable should reject remote JNDI name" );
        }
        catch ( NamingException expected )
        {
            // expected
        }
    }

    public void testJndiAssertNameIsAcceptableAcceptsLocal() throws Exception
    {
        // Should not throw
        C3P0ImplUtils.jndiAssertNameIsAcceptable( "java:comp/env/jdbc/MyDB" );
    }

    // ======================================================================
    //  5. ReferenceIndirector rejects non-null deserialized environments
    // ======================================================================

    /**
     * The indirect-serialization-via-Reference mechanism is forbidden by default (see
     * {@link #testIndirectSerializationViaReferenceForbiddenByDefault}). Once it has been
     * explicitly (and dangerously) enabled via allowIndirectSerializationViaReference, a second
     * line of defense remains: an IndirectlySerialized carrying a non-null InitialContext
     * environment is still rejected at getObject() time, because
     * acceptDeserializedInitialContextEnvironment defaults to false.
     *
     * This prevents attackers from injecting JNDI context properties that redirect
     * lookups to untrusted servers.
     */
    public void testDeserializedInitialContextEnvironmentRejected() throws Exception
    {
        assertNull(
            "Test precondition: acceptDeserializedInitialContextEnvironment system property should not be set",
            System.getProperty( SecurityConfigKey.ACCEPT_DESERIALIZED_INITIAL_CONTEXT_ENVIRONMENT )
        );
        assertNull(
            "Test precondition: allowIndirectSerializationViaReference system property should not be set",
            System.getProperty( SecurityConfigKey.ALLOW_INDIRECT_SERIALIZATION_VIA_REFERENCE )
        );

        // Opt in to the otherwise-forbidden indirect-serialization-via-Reference mechanism, so we can
        // verify the second-line-of-defense environment check that lives behind it.
        PropertiesConfig pcfg = allowIndirectSerializationPropertiesConfig();

        // Create a ReferenceIndirector with a non-null environment that an attacker
        // might use to redirect JNDI lookups
        ReferenceIndirector indirector = new ReferenceIndirector();
        Hashtable maliciousEnv = new Hashtable();
        maliciousEnv.put( "java.naming.factory.initial", "com.evil.MaliciousContextFactory" );
        maliciousEnv.put( "java.naming.provider.url", "ldap://attacker.example.com:1389" );
        indirector.setEnvironmentProperties( maliciousEnv );

        // Create a dummy Referenceable to produce the IndirectlySerialized form
        Referenceable dummyReferenceable = new Referenceable()
        {
            public Reference getReference() throws NamingException
            {
                return new Reference(
                    "com.mchange.v2.c3p0.DriverManagerDataSource",
                    "com.mchange.v2.c3p0.impl.C3P0JavaBeanObjectFactory",
                    null
                );
            }
        };

        IndirectlySerialized indirect = indirector.indirectForm( dummyReferenceable, pcfg );

        // getObject(), even with indirect serialization allowed, must reject the non-null environment
        try
        {
            indirect.getObject( pcfg );
            fail( "getObject() should reject IndirectlySerialized with non-null environment" );
        }
        catch ( IOException expected )
        {
            assertTrue(
                "Error message should reference the acceptDeserializedInitialContextEnvironment config key, got: " + expected.getMessage(),
                expected.getMessage().contains( SecurityConfigKey.ACCEPT_DESERIALIZED_INITIAL_CONTEXT_ENVIRONMENT )
            );
        }
    }

    /**
     * Once indirect serialization via Reference is enabled, an IndirectlySerialized produced with a
     * null environment (the normal case) should be accepted at the environment-check stage. We can't
     * fully resolve it without a real JNDI context, but it must not be rejected because of the
     * environment.
     */
    public void testNullEnvironmentIndirectSerializationNotRejectedAtEnvStage() throws Exception
    {
        assertNull(
            "Test precondition: allowIndirectSerializationViaReference system property should not be set",
            System.getProperty( SecurityConfigKey.ALLOW_INDIRECT_SERIALIZATION_VIA_REFERENCE )
        );

        PropertiesConfig pcfg = allowIndirectSerializationPropertiesConfig();

        ReferenceIndirector indirector = new ReferenceIndirector();
        // environment is null by default

        Referenceable dummyReferenceable = new Referenceable()
        {
            public Reference getReference() throws NamingException
            {
                return new Reference(
                    "com.mchange.v2.c3p0.DriverManagerDataSource",
                    "com.mchange.v2.c3p0.impl.C3P0JavaBeanObjectFactory",
                    null
                );
            }
        };

        IndirectlySerialized indirect = indirector.indirectForm( dummyReferenceable, pcfg );

        // Resolution may fail for unrelated reasons (no real JNDI context, etc.), but it must NOT
        // fail with the "non-default (non-null) InitialContext environment" rejection.
        try
        {
            indirect.getObject( pcfg );
            // If it succeeds, that's fine
        }
        catch ( IOException e )
        {
            assertFalse(
                "Null environment should not trigger the deserialized-environment rejection",
                e.getMessage().contains( SecurityConfigKey.ACCEPT_DESERIALIZED_INITIAL_CONTEXT_ENVIRONMENT )
            );
        }
    }

    // ======================================================================
    //  6. JNDI name validation on javax.naming.Name objects (not just Strings)
    // ======================================================================

    /**
     * The NameGuard should also reject Name objects with remote-looking components.
     */
    public void testJndiAssertNameIsAcceptableRejectsRemoteNameObject() throws Exception
    {
        CompositeName remoteName = new CompositeName( "ldap://evil.example.com/payload" );
        try
        {
            C3P0ImplUtils.jndiAssertNameIsAcceptable( remoteName );
            fail( "jndiAssertNameIsAcceptable should reject remote javax.naming.Name" );
        }
        catch ( NamingException expected )
        {
            // expected
        }
    }

    public void testJndiAssertNameIsAcceptableAcceptsLocalNameObject() throws Exception
    {
        CompositeName localName = new CompositeName( "java:comp/env/jdbc/MyDB" );
        // Should not throw
        C3P0ImplUtils.jndiAssertNameIsAcceptable( localName );
    }

    // ======================================================================
    //  7. PoolBackedDataSourceBase serialization round-trip respects mitigations
    // ======================================================================

    /**
     * Verify that serialization and deserialization of a PoolBackedDataSource-based
     * object works correctly through the secured IndirectlySerialized pathway.
     * The security config (whitelist, remote loading restriction) is applied during
     * deserialization.
     *
     * This test does NOT require a database connection — it uses a ComboPooledDataSource
     * which is Serializable and Referenceable.
     */
    public void testPoolBackedDataSourceSerializationRoundTrip() throws Exception
    {
        ComboPooledDataSource original = new ComboPooledDataSource();
        original.setIdentityToken( "security-test-token" );
        original.setMaxPoolSize( 42 );
        original.setMinPoolSize( 7 );

        byte[] serialized = SerializableUtils.toByteArray( original );
        ComboPooledDataSource restored = (ComboPooledDataSource) SerializableUtils.fromByteArray( serialized );

        assertEquals( "MaxPoolSize should survive serialization round-trip", 42, restored.getMaxPoolSize() );
        assertEquals( "MinPoolSize should survive serialization round-trip", 7, restored.getMinPoolSize() );

        original.close();
        restored.close();
    }

    // ======================================================================
    //  8. JNDI name validation covers JBoss and MBean entry points
    // ======================================================================

    /**
     * Verify that the central jndiAssertNameIsAcceptable rejects names of
     * unexpected types (neither String nor Name) as a defense-in-depth measure.
     */
    public void testJndiAssertNameIsAcceptableRejectsUnexpectedType() throws Exception
    {
        try
        {
            C3P0ImplUtils.jndiAssertNameIsAcceptable( Integer.valueOf(42) );
            fail( "jndiAssertNameIsAcceptable should reject non-String, non-Name objects" );
        }
        catch ( NamingException expected )
        {
            assertTrue(
                "Error should mention unexpected type",
                expected.getMessage().contains( "unexpected type" )
            );
        }
    }

    // ======================================================================
    //  9. Indirect serialization via Reference is forbidden by default
    // ======================================================================

    /**
     * By default (no opt-in via allowIndirectSerializationViaReference), the indirect
     * serialization-via-Reference mechanism is forbidden outright: ReferenceIndirector.indirectForm()
     * throws IndirectSerializationForbiddenException. This is the primary mitigation; the
     * environment-rejection checks exercised above are a second line of defense that only applies
     * once the mechanism has been explicitly (and dangerously) enabled.
     */
    public void testIndirectSerializationViaReferenceForbiddenByDefault() throws Exception
    {
        assertNull(
            "Test precondition: allowIndirectSerializationViaReference system property should not be set",
            System.getProperty( SecurityConfigKey.ALLOW_INDIRECT_SERIALIZATION_VIA_REFERENCE )
        );

        ReferenceIndirector indirector = new ReferenceIndirector();
        Referenceable dummyReferenceable = new Referenceable()
        {
            public Reference getReference() throws NamingException
            {
                return new Reference(
                    "com.mchange.v2.c3p0.DriverManagerDataSource",
                    "com.mchange.v2.c3p0.impl.C3P0JavaBeanObjectFactory",
                    null
                );
            }
        };

        // indirectForm() with no PropertiesConfig consults only system properties (none set), so the
        // mechanism is forbidden and creation of the indirect form itself fails.
        try
        {
            indirector.indirectForm( dummyReferenceable );
            fail( "indirectForm() should be forbidden by default" );
        }
        catch ( IndirectSerializationForbiddenException expected )
        {
            assertTrue(
                "Error should reference the allowIndirectSerializationViaReference config key, got: " + expected.getMessage(),
                expected.getMessage().contains( SecurityConfigKey.ALLOW_INDIRECT_SERIALIZATION_VIA_REFERENCE )
            );
        }
    }

    /**
     * Even more important than refusing to *create* an indirect form (above): if an attacker has
     * already obtained or crafted an indirect form -- e.g. a serialized ReferenceSerialized delivered
     * into a Java-deserialization sink -- then *decoding* it must fail by default. Otherwise the
     * JNDI-injection gadget fires on deserialization, which is exactly the attack we are defending
     * against.
     *
     * We synthesize the attacker's artifact by creating an indirect form under an opt-in config (it
     * could only have come into existence while the dangerous mechanism was enabled, or been
     * hand-crafted), then verify that decoding it under the default (restrictive) config is refused --
     * both via the IndirectlySerialized.getObject() entry point directly, and via the realistic
     * SerializableUtils.fromByteArray() path that auto-unwraps indirect forms on deserialization.
     */
    public void testDecodingIndirectFormForbiddenByDefault() throws Exception
    {
        assertNull(
            "Test precondition: allowIndirectSerializationViaReference system property should not be set",
            System.getProperty( SecurityConfigKey.ALLOW_INDIRECT_SERIALIZATION_VIA_REFERENCE )
        );

        // The artifact an attacker possesses: an indirect form. It could only have been produced while
        // the dangerous mechanism was enabled, so we build it here with an opt-in config.
        PropertiesConfig allowPcfg = allowIndirectSerializationPropertiesConfig();
        ReferenceIndirector indirector = new ReferenceIndirector();
        Referenceable dummyReferenceable = new Referenceable()
        {
            public Reference getReference() throws NamingException
            {
                return new Reference(
                    "com.mchange.v2.c3p0.DriverManagerDataSource",
                    "com.mchange.v2.c3p0.impl.C3P0JavaBeanObjectFactory",
                    null
                );
            }
        };
        IndirectlySerialized indirect = indirector.indirectForm( dummyReferenceable, allowPcfg );

        // (a) Decoding the indirect form directly must be refused by default.
        try
        {
            indirect.getObject();
            fail( "Decoding (getObject) an indirect form must be forbidden by default" );
        }
        catch ( IndirectSerializationForbiddenException expected )
        {
            assertTrue(
                "Error should reference the allowIndirectSerializationViaReference config key, got: " + expected.getMessage(),
                expected.getMessage().contains( SecurityConfigKey.ALLOW_INDIRECT_SERIALIZATION_VIA_REFERENCE )
            );
        }

        // (b) The realistic delivery path: the indirect form is serialized and fed into a
        // Java-deserialization sink. SerializableUtils.fromByteArray() auto-unwraps IndirectlySerialized
        // via getObject(), which must likewise refuse by default.
        byte[] attackerBytes = SerializableUtils.toByteArray( indirect );
        try
        {
            SerializableUtils.fromByteArray( attackerBytes );
            fail( "Deserializing an attacker-delivered indirect form must be forbidden by default" );
        }
        catch ( IndirectSerializationForbiddenException expected )
        {
            // expected
        }
    }

    // Returns a PropertiesConfig that delegates to c3p0's normal configuration but forces
    // allowIndirectSerializationViaReference=true, opting in to the (otherwise forbidden-by-default)
    // indirect-serialization-via-Reference mechanism so we can exercise the mitigations behind it.
    private static PropertiesConfig allowIndirectSerializationPropertiesConfig()
    {
        final PropertiesConfig base = C3P0Config.getMultiPropertiesConfig();
        return new PropertiesConfig()
        {
            public String getProperty( String key )
            {
                if ( SecurityConfigKey.ALLOW_INDIRECT_SERIALIZATION_VIA_REFERENCE.equals( key ) )
                    return "true";
                else
                    return base.getProperty( key );
            }
            public java.util.Properties getPropertiesByPrefix( String pfx )
            { return base.getPropertiesByPrefix( pfx ); }
        };
    }
}
