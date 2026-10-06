package com.mchange.v2.c3p0.test.junit;

import java.util.Properties;
import javax.naming.Reference;
import javax.sql.DataSource;
import junit.framework.TestCase;

import com.mchange.v2.ser.SerializableUtils;
import com.mchange.v2.naming.ReferenceableUtils;
import com.mchange.v2.c3p0.DriverManagerDataSource;
import com.mchange.v2.c3p0.PoolBackedDataSource;
import com.mchange.v2.c3p0.WrapperConnectionPoolDataSource;
import com.mchange.v2.c3p0.cfg.C3P0Config;

/**
 *  Round trips a nested DataSource chain by value, through both JNDI Reference and Java
 *  serialization.
 *
 *  <p>MarshallUnmarshallDataSourcesJUnitTestCase already round-trips a ComboPooledDataSource,
 *  but every property it compares is a scalar, and it names "connectionPoolDataSource" in its
 *  EXCLUDE_PROPS -- so the one property whose value is itself a DataSource is the one property
 *  it does not check. A ComboPooledDataSource also builds its own internals rather than taking
 *  them from configuration, so nothing in that test ever reconstitutes a nested DataSource from
 *  a Reference.
 *
 *  <p>That matters because a nested DataSource travels as a SecurelyStringifiable: the outer
 *  Reference carries the inner one as a stringified payload naming the class to reconstruct, and
 *  reconstruction is gated by a whitelist. When the gate refuses, JavaBeanObjectFactory logs a
 *  warning and <i>omits the property</i>. The restored outer object is then perfectly valid, with
 *  a null where its nested DataSource used to be -- so a test that asserts only that nothing was
 *  thrown passes, and a test that compares bean properties while excluding the nested one passes
 *  too. Only reading the restored value at depth catches it.
 *
 *  <p>Hence these tests assert values rather than the absence of exceptions: a two-level chain
 *  (PoolBackedDataSource -> WrapperConnectionPoolDataSource -> DriverManagerDataSource) goes out,
 *  and the scalars on the innermost DataSource must come back equal.
 */
public final class NestedDataSourceRoundTripJUnitTestCase extends TestCase
{
    private final static String JDBC_URL    = "jdbc:mock://nested-round-trip/db";
    private final static String DRIVER_CLASS = "com.mchange.v2.c3p0.test.junit.MockDriver";
    private final static String DESCRIPTION = "innermost, and nested two deep";
    private final static int    MAX_POOL_SZ = 37;

    private PoolBackedDataSource pbds;

    @Override
    protected void setUp() throws Exception
    {
        DriverManagerDataSource dmds = new DriverManagerDataSource();
        dmds.setJdbcUrl( JDBC_URL );
        dmds.setDriverClass( DRIVER_CLASS );
        dmds.setDescription( DESCRIPTION );
        Properties props = new Properties();
        props.setProperty( "user", "nested-user" );
        dmds.setProperties( props );

        WrapperConnectionPoolDataSource wcpds = new WrapperConnectionPoolDataSource();
        wcpds.setNestedDataSource( dmds );
        wcpds.setMaxPoolSize( MAX_POOL_SZ );

        pbds = new PoolBackedDataSource();
        pbds.setConnectionPoolDataSource( wcpds );
        // A never-before-seen token, so restoring registers rather than resolving to this object.
        pbds.setIdentityToken( "nested-round-trip-" + System.identityHashCode( this ) );
    }

    @Override
    protected void tearDown()
    {
        try { if (pbds != null) pbds.close(); }
        catch (Exception e)
        { System.err.println( "Exception closing DataSource in tearDown(): " ); e.printStackTrace(); }
    }

    public void testNestedChainSurvivesAReferenceRoundTrip() throws Exception
    {
        Reference ref = pbds.getReference();
        Object restored = ReferenceableUtils.referenceToObject( ref, null, null, null, C3P0Config.getMultiPropertiesConfig() );

        assertTrue( "A PoolBackedDataSource Reference should dereference to one: " + restored,
                    restored instanceof PoolBackedDataSource );
        assertNestedChainIntact( (PoolBackedDataSource) restored, "Reference round trip" );
    }

    public void testNestedChainSurvivesASerializationRoundTrip() throws Exception
    {
        byte[] pickled = SerializableUtils.toByteArray( pbds );
        Object restored = SerializableUtils.fromByteArray( pickled );

        assertTrue( "A serialized PoolBackedDataSource should deserialize to one: " + restored,
                    restored instanceof PoolBackedDataSource );
        assertNestedChainIntact( (PoolBackedDataSource) restored, "serialization round trip" );
    }

    /**
     *  The whole point: walk down into the restored object and compare values. A refusal that is
     *  caught and logged shows up here as a null, which is why each level is asserted non-null
     *  before it is dereferenced.
     */
    private void assertNestedChainIntact( PoolBackedDataSource restored, String how ) throws Exception
    {
        Object cpds = restored.getConnectionPoolDataSource();
        assertNotNull( "The nested ConnectionPoolDataSource must survive the " + how + ". A null here " +
                       "means it was dropped rather than reconstituted -- most likely refused by the " +
                       "com.mchange.v2.naming.securelyStringifiable whitelist, which JavaBeanObjectFactory " +
                       "handles by warning and omitting the property.", cpds );
        assertTrue( "and must come back as a WrapperConnectionPoolDataSource, not some other type: " + cpds,
                    cpds instanceof WrapperConnectionPoolDataSource );

        WrapperConnectionPoolDataSource wcpds = (WrapperConnectionPoolDataSource) cpds;
        assertEquals( "A scalar on the middle DataSource must survive the " + how,
                      MAX_POOL_SZ, wcpds.getMaxPoolSize() );

        DataSource nested = wcpds.getNestedDataSource();
        assertNotNull( "The innermost DataSource must survive the " + how + " too -- the nesting is " +
                       "two deep, so reconstruction has to recurse.", nested );
        assertTrue( "and must come back as a DriverManagerDataSource: " + nested,
                    nested instanceof DriverManagerDataSource );

        DriverManagerDataSource dmds = (DriverManagerDataSource) nested;
        assertEquals( "the innermost jdbcUrl must come back equal, after the " + how,
                      JDBC_URL, dmds.getJdbcUrl() );
        assertEquals( "the innermost driverClass must come back equal, after the " + how,
                      DRIVER_CLASS, dmds.getDriverClass() );
        assertEquals( "the innermost description must come back equal, after the " + how,
                      DESCRIPTION, dmds.getDescription() );

        Properties restoredProps = dmds.getProperties();
        assertNotNull( "the innermost Properties must survive the " + how, restoredProps );
        assertEquals( "and must carry their value, after the " + how,
                      "nested-user", restoredProps.getProperty( "user" ) );
    }
}
