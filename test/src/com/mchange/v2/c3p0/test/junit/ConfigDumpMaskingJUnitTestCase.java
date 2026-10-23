package com.mchange.v2.c3p0.test.junit;

import junit.framework.TestCase;

import com.mchange.v2.c3p0.ComboPooledDataSource;
import com.mchange.v2.c3p0.DriverManagerDataSource;
import com.mchange.v2.c3p0.JndiRefConnectionPoolDataSource;
import com.mchange.v2.c3p0.PoolBackedDataSource;
import com.mchange.v2.c3p0.PooledDataSource;
import com.mchange.v2.c3p0.WrapperConnectionPoolDataSource;
import com.mchange.v2.c3p0.impl.AuthMaskingProperties;
import com.mchange.v2.c3p0.impl.C3P0ImplUtils;

import com.mchange.v2.util.MaskUtils;

import java.beans.PropertyVetoException;
import java.lang.management.ManagementFactory;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.Hashtable;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.management.MBeanAttributeInfo;
import javax.management.StandardMBean;
import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import javax.naming.Context;

/**
 *  <code>AbstractComboPooledDataSource.toString( true )</code> is not a convenience. It is the
 *  text of <code>AbstractPoolBackedDataSource</code>'s "Initializing c3p0 pool..." line, logged
 *  at INFO, once per pool, in the default configuration. Whatever reaches it reaches every log
 *  aggregator the deployment ships to, every issue report it gets pasted into, and every mail to
 *  a vendor. A dump that is safe only because nobody raised the log level is not safe, and this
 *  one is not gated at all.
 *
 *  <p>So these cases are about a credential actually escaping, not about a tidy toString. Each
 *  one was a leak or a breakage observed by running it, not a hazard reasoned about: the
 *  per-user password, the URL-borne password, the driver property beyond
 *  <code>user</code>/<code>password</code>, the extension value, and in the other direction a
 *  dump destroyed by its own masking.</p>
 *
 *  <h3>Both directions, because masking fails both ways</h3>
 *
 *  <p>A masker that answered <code>****</code> for everything would pass every leak case here
 *  and be worthless, so the retentions are asserted as carefully as the maskings. Two of them
 *  earned their place by actually going wrong:</p>
 *
 *  <ul>
 *    <li><b>A hostless JDBC URL must survive intact.</b> Masking once answered null for any
 *        value with no <code>&#47;&#47;</code> in it, so every embedded database -- H2, SQLite,
 *        Derby, HSQLDB -- reported <code>jdbcUrl -&gt; null</code> for a datasource that was
 *        configured and working.</li>
 *    <li><b>A dump must be whole.</b> <code>jdbcUrl</code> defaults to null, and masking it
 *        threw <i>inside</i> the property walk, so the dump ended mid-line at
 *        <code>initialPoolSize</code> and silently lost everything sorting after
 *        <code>jdbcUrl</code> -- which is most of what anyone reads it for.</li>
 *  </ul>
 *
 *  <p>Both of those printed something that looked like correct output, which is why they are
 *  pinned rather than left to review.</p>
 *
 *  <p>No database is needed: none of this initializes a pool.</p>
 */
public final class ConfigDumpMaskingJUnitTestCase extends TestCase
{
    private final static String SENTINEL = "SENT1NEL-MUST-NOT-APPEAR";
    /** Spelled once, in commons' MaskUtilsJUnitTestCase.testTheMaskIsSixAsterisks. */
    private final static String MASK     = MaskUtils.MASK;

    private List<PooledDataSource> created;
    private NullConnectDriver       probeDriver;

    @Override
    protected void setUp() throws Exception
    {
        created = new ArrayList<PooledDataSource>();
        probeDriver = new NullConnectDriver();
        DriverManager.registerDriver( probeDriver );
    }

    @Override
    protected void tearDown()
    {
        for ( Iterator<PooledDataSource> ii = created.iterator(); ii.hasNext(); )
        {
            try { ii.next().close(); }
            catch ( Exception e ) { /* nothing was initialized; best effort */ }
        }
        // DriverManager is process-wide, and a driver left registered would outlive this case.
        try { DriverManager.deregisterDriver( probeDriver ); }
        catch ( Exception e ) { /* best effort */ }
    }

    /** Tracked so tearDown unregisters the MBean and registry entry each one claims. */
    private ComboPooledDataSource newCpds()
    {
        ComboPooledDataSource out = new ComboPooledDataSource();
        created.add( out );
        return out;
    }

    /**
     *  The other deployment style, and the one the transformer in AbstractComboPooledDataSource
     *  never sees: PoolBackedDataSource over WrapperConnectionPoolDataSource over
     *  DriverManagerDataSource, which is what DataSources.pooledDataSource(..) assembles and what
     *  a JNDI deployment binds. Its dump comes from the GENERATED bean bases plus one
     *  hand-written extraToStringInfo(), so it is masked by entirely different machinery.
     */
    private PoolBackedDataSource newPbds( DriverManagerDataSource dmds, WrapperConnectionPoolDataSource wcpds )
    {
        wcpds.setNestedDataSource( dmds );
        PoolBackedDataSource out = new PoolBackedDataSource();
        try { out.setConnectionPoolDataSource( wcpds ); }
        catch ( Exception e ) { throw new RuntimeException( e ); }
        created.add( out );
        return out;
    }

    // ------------------------------------------------------- the dump must be whole

    /**
     *  THE BREAKAGE WORTH PINNING MOST, because it is silent and reachable out of the box:
     *  C3P0Defaults.jdbcUrl() is null, so this is a DataSource nobody has finished configuring.
     *  A masker that assumed otherwise threw inside BeansUtils.appendPropNamesAndValues(..) --
     *  which catches and appends -- so the dump simply stopped, mid-property, at the
     *  alphabetical neighbour before jdbcUrl.
     *
     *  <p>Set to null explicitly rather than left at its default, because this test module's own
     *  environment supplies a -Dc3p0.jdbcUrl and the state under test is "jdbcUrl is null", not
     *  "nobody configured one".</p>
     */
    public void testADumpWithNoJdbcUrlIsStillAWholeDump()
    {
        ComboPooledDataSource cpds = newCpds();
        cpds.setJdbcUrl( null );
        assertNull( "precondition: the state under test is a null jdbcUrl", cpds.getJdbcUrl() );

        String dump = cpds.toString( true );

        assertFalse( "a dump reporting an exception has been truncated: " + dump,
                     dump.contains( "Exception" ) );
        assertTrue( "a null jdbcUrl should be reported, not fatal", dump.contains( "jdbcUrl -> null" ) );

        // Properties sorting after 'jdbcUrl' are precisely what a truncated dump loses.
        String[] afterJdbcUrl = { "maxPoolSize", "minPoolSize", "preferredTestQuery",
                                  "properties", "userOverrides", "testConnectionOnCheckout" };
        for ( int i = 0; i < afterJdbcUrl.length; ++i )
            assertTrue( "absent, so the dump was cut short at jdbcUrl: " + afterJdbcUrl[i],
                        dump.contains( afterJdbcUrl[i] ) );
    }

    // ------------------------------------------------------- credentials in a URL

    public void testAPasswordInAJdbcUrlIsMasked()
    {
        ComboPooledDataSource cpds = newCpds();
        cpds.setJdbcUrl( "jdbc:postgresql://db.example.com:5432/app?user=app&password=" + SENTINEL + "&ssl=true" );

        String dump = cpds.toString( true );

        assertNoSentinel( dump );
        assertEquals( "jdbc:postgresql://db.example.com:5432/app?user=app"
                          + "&password=" + MASK + "&ssl=true",
                      valueOf( dump, "jdbcUrl" ) );
    }

    public void testAPasswordInUserInfoIsMasked()
    {
        ComboPooledDataSource cpds = newCpds();
        cpds.setJdbcUrl( "jdbc:postgresql://app:" + SENTINEL + "@db.example.com:5432/prod" );

        String dump = cpds.toString( true );

        assertNoSentinel( dump );
        assertEquals( "the account survives, the password does not -- position says which is which",
                      "jdbc:postgresql://app:" + MASK + "@db.example.com:5432/prod",
                      valueOf( dump, "jdbcUrl" ) );
    }

    /**
     *  A hostless URL has no authority, so no userinfo -- but its parameters still carry
     *  credentials, and this is how every embedded database is configured. Requiring an
     *  authority looked for userinfo, found none, and concluded there was nothing here to read.
     */
    public void testCredentialsInAHostlessJdbcUrlAreMasked()
    {
        String[][] cases = {
            { "jdbc:h2:mem:testdb;USER=sa;PASSWORD=" + SENTINEL,
              "jdbc:h2:mem:testdb;USER=sa;PASSWORD=" + MASK },
            { "jdbc:derby:memory:db;create=true;user=app;password=" + SENTINEL,
              "jdbc:derby:memory:db;create=true;user=app;password=" + MASK },
            { "jdbc:hsqldb:file:/db/mydb;user=SA;password=" + SENTINEL,
              "jdbc:hsqldb:file:/db/mydb;user=SA;password=" + MASK },
            { "jdbc:firebirdsql:embedded:/path/db?user=app&password=" + SENTINEL,
              "jdbc:firebirdsql:embedded:/path/db?user=app&password=" + MASK }
        };
        for ( int i = 0; i < cases.length; ++i )
        {
            ComboPooledDataSource cpds = newCpds();
            cpds.setJdbcUrl( cases[i][0] );

            String dump = cpds.toString( true );

            assertNoSentinel( dump );
            assertEquals( cases[i][1], valueOf( dump, "jdbcUrl" ) );
        }
    }

    /**
     *  The retention half, and the one that broke. These URLs carry nothing secret, so they must
     *  appear exactly as configured. Reporting them as null erased the single most useful line
     *  in the dump for anyone running an embedded database.
     */
    public void testAJdbcUrlWithNothingToMaskSurvivesIntact()
    {
        String[] urls = { "jdbc:h2:mem:test",
                          "jdbc:sqlite:/var/db/app.sqlite",
                          "jdbc:derby:memory:db;create=true",
                          "jdbc:postgresql://db.example.com:5432/prod?ssl=true&ApplicationName=svc" };
        for ( int i = 0; i < urls.length; ++i )
        {
            ComboPooledDataSource cpds = newCpds();
            cpds.setJdbcUrl( urls[i] );

            assertEquals( urls[i], valueOf( cpds.toString( true ), "jdbcUrl" ) );
        }
    }

    /**
     *  A jdbcUrl with no scheme at all is not URL-shaped, masking declines it, and declining is
     *  reported as null -- so without a guard at the call site the dump erases it. This is the
     *  case where the dump matters MOST: a malformed jdbcUrl is usually WHY someone is reading
     *  it, and answering null hides the very mistake they are looking for.
     *
     *  <p>Found by mutation: removing that guard broke nothing, because once masking accepted
     *  any scheme, no well-formed JDBC URL could reach the null answer any more. Only a value
     *  carrying no colon at all can, and nothing here was testing one.</p>
     */
    public void testAMalformedJdbcUrlIsStillReportedAsConfigured()
    {
        String[] notUrls = { "not-a-url", "db.example.com/app", "" };
        for ( int i = 0; i < notUrls.length; ++i )
        {
            ComboPooledDataSource cpds = newCpds();
            cpds.setJdbcUrl( notUrls[i] );

            assertEquals( "a jdbcUrl too malformed to parse is still what the operator typed",
                          notUrls[i], valueOf( cpds.toString( true ), "jdbcUrl" ) );
        }
    }

    // ------------------------------------------------------- per-user overrides

    /**
     *  userOverrides is username -&gt; per-user property map, and the credentials are the INNER
     *  names: a per-user override may carry password, overrideDefaultPassword or user. Masking
     *  the outer level tested usernames, which match nothing, and rendered each inner map whole.
     *
     *  <p>The structure has to survive, because "which users have overrides, and what pool
     *  settings do they get" is the question being asked.</p>
     */
    public void testAPerUserPasswordIsMaskedAndTheStructureSurvives() throws Exception
    {
        ComboPooledDataSource cpds = newCpds();
        cpds.setUserOverridesAsString( userOverridesAsString( "steve", "password", SENTINEL ) );

        String dump = cpds.toString( true );
        String value = valueOf( dump, "userOverrides" );

        assertNoSentinel( dump );
        assertTrue( "the username is not a secret and answers the question: " + value,
                    value.contains( "steve" ) );
        assertTrue( "nor is the pool setting beside it: " + value, value.contains( "maxPoolSize" ) );
        assertTrue( "and the key whose value was masked must still be named: " + value,
                    value.contains( "password" ) );
        assertTrue( "masked: " + value, value.contains( MASK ) );
    }

    // ------------------------------------------------------- extensions

    public void testAnExtensionValueIsMaskedByItsKeyName()
    {
        ComboPooledDataSource cpds = newCpds();
        Map<String,Object> ext = new HashMap<String,Object>();
        ext.put( "apiSecret", SENTINEL );
        ext.put( "region", "us-east-1" );
        cpds.setExtensions( ext );

        String dump = cpds.toString( true );
        String value = valueOf( dump, "extensions" );

        assertNoSentinel( dump );
        assertTrue( "an extension with no sensitive name is the application's own diagnostic: "
                        + value, value.contains( "us-east-1" ) );
    }

    // ------------------------------------------------------- driver properties

    /**
     *  The generalization that AuthMaskingProperties did not survive: it concealed exactly
     *  <code>user</code> and <code>password</code>, because it knew it held a JDBC
     *  authentication map. Real driver property maps carry credentials under many other names,
     *  and under none of the two it knew when the connection is authenticated some other way.
     */
    public void testDriverPropertiesAreMaskedBeyondUserAndPassword()
    {
        ComboPooledDataSource cpds = newCpds();
        Properties props = new Properties();
        props.setProperty( "sslpassword",     SENTINEL );   // PostgreSQL
        props.setProperty( "password1",       SENTINEL );   // MySQL multi-factor
        props.setProperty( "accessToken",     SENTINEL );   // SQL Server
        props.setProperty( "ApplicationName", "svc" );      // not a secret, and useful
        cpds.setProperties( props );

        String dump = cpds.toString( true );
        String value = valueOf( dump, "properties" );

        assertNoSentinel( dump );
        assertTrue( "a non-secret driver property is worth seeing: " + value,
                    value.contains( "svc" ) );
    }

    // ------------------------------------------------------- what never appears at all

    /**
     *  Masking is the second line. The first is TO_STRING_IGNORE_PROPS, which omits the obvious
     *  credentials outright -- and it holds <code>userOverridesAsString</code>, evidence that
     *  the blob was recognized as sensitive, which is what made the parsed <code>userOverrides</code>
     *  slipping through under a second name worth finding.
     *
     *  <p>Tested as "<i>name</i> -&gt; " rather than by bare name, since <code>user</code> and
     *  <code>password</code> both occur as substrings of entries that legitimately appear.</p>
     */
    public void testTheObviousCredentialsAreOmittedRatherThanMasked() throws Exception
    {
        ComboPooledDataSource cpds = newCpds();
        cpds.setJdbcUrl( "jdbc:postgresql://db/app" );
        cpds.setUser( "appuser" );                       // an account, and no longer a secret
        cpds.setOverrideDefaultUser( "overrideuser" );
        cpds.setPassword( SENTINEL );
        cpds.setOverrideDefaultPassword( SENTINEL );
        cpds.setUserOverridesAsString( userOverridesAsString( "steve", "password", SENTINEL ) );

        String dump = cpds.toString( true );

        assertNoSentinel( dump );
        String[] omitted = { "password", "overrideDefaultPassword",
                             "userOverridesAsString", "allUsers", "connectionPoolDataSource" };
        for ( int i = 0; i < omitted.length; ++i )
            assertFalse( "should be omitted from the dump entirely, not masked: " + omitted[i],
                         dump.contains( omitted[i] + " -> " ) );

        // The loosening, pinned positively: these two were omitted before 0.15.0 and are not any
        // more, so an accidental re-exclusion fails here rather than quietly costing a diagnostic.
        assertTrue( "the account is reported now: " + dump, dump.contains( "user -> appuser" ) );
        assertTrue( "and the per-pool override account: " + dump,
                    dump.contains( "overrideDefaultUser -> overrideuser" ) );
    }

    // ------------------------------------------------------- the veto message

    /**
     *  A log line stays inside the deployment's logging configuration. An exception message does
     *  not -- it is handed to the caller, which puts it wherever it puts its own errors, often a
     *  response body. So the unparsable value is gated out of the log at FINEST and out of this
     *  message entirely.
     *
     *  <p>Harmless while userOverridesAsString was HexAsciiSerializedMap hex. Removing the Java
     *  deserialization made the format ragged CSV, and these messages plaintext.</p>
     */
    public void testTheVetoMessageDoesNotCarryTheUnparsableBlob()
    {
        ComboPooledDataSource cpds = newCpds();
        try
        {
            cpds.setUserOverridesAsString( "\"steve\"\r\n\"password\",\"" + SENTINEL + "\"\r\nBROKEN,\"" );
            fail( "a malformed userOverridesAsString must be vetoed, not accepted" );
        }
        catch ( PropertyVetoException expected )
        {
            assertFalse( "the veto message carries the blob out to the application: "
                             + expected.getMessage(),
                         String.valueOf( expected.getMessage() ).contains( SENTINEL ) );
        }
    }

    // ------------------------------------------------------- AuthMaskingProperties itself

    /** No user and no password key at all, which used to mean no masking whatsoever. */
    public void testAuthMaskingPropertiesMasksWithNeitherUserNorPasswordPresent()
    {
        AuthMaskingProperties amp = new AuthMaskingProperties();
        amp.setProperty( "sslpassword", SENTINEL );
        amp.setProperty( "accessToken", SENTINEL );
        amp.setProperty( "ApplicationName", "svc" );

        String s = amp.toString();

        assertNoSentinel( s );
        assertTrue( s, s.contains( "svc" ) );
    }

    /**
     *  As of 0.15.0 the account is NOT masked, in any spelling. AuthMaskingProperties masked
     *  'user' alongside 'password' for years, and briefly did so again through a PropertiesMasker
     *  subclass, before the policy settled: a username identifies rather than authorizes, and
     *  with per-user pools it is frequently the thing being debugged.
     *
     *  <p>Asserted across spellings because the subclass that used to mask it compared with
     *  equalsIgnoreCase, so a half-reverted change could leave one casing masked.</p>
     */
    public void testAuthMaskingPropertiesRetainsTheAccountInAnySpelling()
    {
        String[] spellings = { "user", "USER", "User" };
        for ( int i = 0; i < spellings.length; ++i )
        {
            AuthMaskingProperties amp = new AuthMaskingProperties();
            amp.setProperty( spellings[i], "appuser" );
            amp.setProperty( "password", SENTINEL );

            String s = amp.toString();
            assertNoSentinel( "the password must still go", s );
            assertTrue( "the account must survive, spelled " + spellings[i] + ": " + s,
                        s.contains( "appuser" ) );
        }
    }

    /** And the retention that the bare-'user' exclusion exists to protect. */
    public void testAuthMaskingPropertiesRetainsPropertiesMerelyBeginningWithUser()
    {
        AuthMaskingProperties amp = new AuthMaskingProperties();
        amp.setProperty( "user.name", "steve" );
        amp.setProperty( "user.home", "/Users/steve" );

        String s = amp.toString();

        assertTrue( s, s.contains( "steve" ) );
        assertTrue( s, s.contains( "/Users/steve" ) );
    }

    // ------------------------------------------------------- JMX

    /**
     *  The same credentials, over the other channel. The MBean's HIDE_PROPS had 'password' from
     *  the start but not 'overrideDefaultPassword', while c3p0's own toString ignore list and
     *  BeangenDataSourceGenerator's exclude list both carried it -- two of three lists agreeing
     *  is what made the third look like an oversight rather than a decision.
     *
     *  <p>jdbcUrl is hidden rather than masked because the attribute is writable: a console that
     *  read a masked value and wrote it back would destroy the configuration.</p>
     *
     *  <p>A benign attribute is asserted present, so that hiding everything would not pass.</p>
     */
    public void testJmxExposesNoCredentialBearingAttribute() throws Exception
    {
        ComboPooledDataSource cpds = newCpds();
        cpds.setJdbcUrl( "jdbc:postgresql://db/app?password=" + SENTINEL );
        cpds.setPassword( SENTINEL );
        cpds.setOverrideDefaultPassword( SENTINEL );
        cpds.setUserOverridesAsString( userOverridesAsString( "steve", "password", SENTINEL ) );

        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        ObjectName mine = pooledDataSourceMBeanFor( mbs, cpds.getIdentityToken() );
        assertNotNull( "c3p0 registered no MBean for this DataSource, so this test proves nothing",
                       mine );

        MBeanInfo info = mbs.getMBeanInfo( mine );
        MBeanAttributeInfo[] attrs = info.getAttributes();

        boolean sawBenign = false;
        for ( int i = 0; i < attrs.length; ++i )
        {
            String name = attrs[i].getName();

            assertFalse( "exposed over JMX: " + name,
                         "jdbcUrl".equals( name )
                             || "password".equals( name )
                             || "overrideDefaultPassword".equals( name )
                             || "userOverrides".equals( name )
                             || "userOverridesAsString".equals( name ) );

            if ( "maxPoolSize".equals( name ) ) sawBenign = true;

            if ( attrs[i].isReadable() )
            {
                Object value;
                try { value = mbs.getAttribute( mine, name ); }
                catch ( Exception e ) { continue; }   // an attribute may legitimately refuse
                assertNoSentinel( "attribute " + name, String.valueOf( value ) );
            }
        }
        assertTrue( "no ordinary attribute was exposed either -- is this MBean managing anything?",
                    sawBenign );
    }

    private static ObjectName pooledDataSourceMBeanFor( MBeanServer mbs, String identityToken )
        throws Exception
    {
        Set<ObjectName> names = mbs.queryNames( new ObjectName( "com.mchange.v2.c3p0:type=PooledDataSource,*" ), null );
        for ( Iterator<ObjectName> ii = names.iterator(); ii.hasNext(); )
        {
            ObjectName on = ii.next();
            if ( identityToken.equals( on.getKeyProperty( "identityToken" ) ) ) return on;
        }
        return null;
    }

    // ------------------------------------------- the OTHER deployment style

    /**
     *  THE GAP THAT MASKING THE COMBO CLASS LEFT OPEN. All three of these reach the same INFO
     *  pool-init line, and all three are masked by machinery the combo path does not use:
     *  jdbcUrl and extensions through value expressions the bean generator emits, userOverrides
     *  through WrapperConnectionPoolDataSource.extraToStringInfo().
     *
     *  <p>properties is masked here too, by a third mechanism again -- AuthMaskingProperties
     *  masks in its own toString, so the masking travels with the value rather than with any
     *  call site. That is why it was the one thing never broken.</p>
     */
    public void testTheNonComboDumpMasksEverywhereTheComboDumpDoes() throws Exception
    {
        DriverManagerDataSource dmds = new DriverManagerDataSource();
        dmds.setJdbcUrl( "jdbc:postgresql://db.example.com/app?user=app&password=" + SENTINEL );
        Properties props = new Properties();
        props.setProperty( "sslpassword", SENTINEL );
        dmds.setProperties( props );

        WrapperConnectionPoolDataSource wcpds = new WrapperConnectionPoolDataSource();
        wcpds.setUserOverridesAsString( userOverridesAsString( "steve", "password", SENTINEL ) );
        wcpds.setOverrideDefaultUser( "overrideuser" );
        wcpds.setOverrideDefaultPassword( SENTINEL );

        PoolBackedDataSource pbds = newPbds( dmds, wcpds );
        Map<String,Object> ext = new HashMap<String,Object>();
        ext.put( "apiSecret", SENTINEL );
        ext.put( "region", "us-east-1" );
        pbds.setExtensions( ext );

        String dump = pbds.toString( true );

        assertNoSentinel( dump );
        assertFalse( "a dump reporting an exception has been truncated: " + dump,
                     dump.contains( "Exception" ) );

        // Each of the four mechanisms left a mark, so none of this passed by the value being absent.
        assertTrue( "jdbcUrl missing from the dump: "    + dump, dump.contains( "jdbcUrl -> " ) );
        assertTrue( "extensions missing: "               + dump, dump.contains( "extensions -> " ) );
        assertTrue( "userOverrides missing: "            + dump, dump.contains( "userOverrides: " ) );
        assertTrue( "properties missing: "               + dump, dump.contains( "properties -> " ) );
        assertTrue( "and the non-secret half must survive: " + dump, dump.contains( "us-east-1" ) );
        assertTrue( "db.example.com should still be visible: " + dump, dump.contains( "db.example.com" ) );

        // The generated exclude list, on both sides. These two are adjacent in it and in three
        // other lists, so it is easy to drop or restore both together by mistake: as of 0.15.0
        // the account is reported and the password is omitted.
        assertTrue( "the override account is reported now: " + dump,
                    dump.contains( "overrideDefaultUser -> overrideuser" ) );
        assertFalse( "but the override password is omitted, not masked: " + dump,
                     dump.contains( "overrideDefaultPassword -> " ) );
    }

    /**
     *  The registry MBean reports each registered DataSource with a plain toString(). For a combo
     *  DataSource that is toString(false) -- a token and a name -- but for a PoolBackedDataSource
     *  it is the generated base, hence the whole nested chain. Two readable JMX attributes that
     *  HIDE_PROPS never touches, on an MBean c3p0 registers by itself.
     */
    public void testTheRegistryMBeanDoesNotDiscloseWhatTheDumpMasks() throws Exception
    {
        DriverManagerDataSource dmds = new DriverManagerDataSource();
        dmds.setJdbcUrl( "jdbc:postgresql://db/app?password=" + SENTINEL );
        newPbds( dmds, new WrapperConnectionPoolDataSource() );

        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        Set<ObjectName> names = mbs.queryNames( new ObjectName( "com.mchange.v2.c3p0:type=C3P0Registry*" ), null );
        assertFalse( "c3p0 registered no registry MBean, so this test proves nothing", names.isEmpty() );

        for ( Iterator<ObjectName> ii = names.iterator(); ii.hasNext(); )
        {
            ObjectName on = ii.next();
            String[] attrs = { "AllPooledDataSourcesStringified", "AllIdentityTokenizedStringified" };
            for ( int i = 0; i < attrs.length; ++i )
            {
                String[] vals = (String[]) mbs.getAttribute( on, attrs[i] );
                assertTrue( "the registry reported nothing, so this proves nothing: " + attrs[i],
                            vals.length > 0 );
                for ( int j = 0; j < vals.length; ++j )
                    assertNoSentinel( attrs[i], vals[j] );
            }
        }
    }

    // ------------------------------------------- JndiRefConnectionPoolDataSource

    /**
     *  The retention policy landing somewhere nobody designed it for: 'cred' catches
     *  java.naming.security.credentials, while java.naming.security.principal matches no
     *  sensitive substring and stays readable. Knowing WHICH identity a lookup binds as is most
     *  of the diagnostic value; the secret it binds with is none of it.
     */
    public void testJndiEnvCredentialsAreMaskedAndThePrincipalIsNot() throws Exception
    {
        JndiRefConnectionPoolDataSource jrcpds = new JndiRefConnectionPoolDataSource();
        jrcpds.setJndiName( "java:comp/env/jdbc/realDs" );
        Hashtable<String,String> env = new Hashtable<String,String>();
        env.put( Context.SECURITY_PRINCIPAL,   "cn=app,dc=example,dc=com" );
        env.put( Context.SECURITY_CREDENTIALS, SENTINEL );
        jrcpds.setJndiEnv( env );

        String dump = jrcpds.toString();

        assertNoSentinel( dump );
        assertTrue( "the principal answers 'as whom', and is not a secret: " + dump,
                    dump.contains( "cn=app,dc=example,dc=com" ) );
    }

    /**
     *  jndiEnv is null by default -- its beangen defensive copy is written
     *  <code>(jndiEnv != null ? clone() : null)</code> and it has no default value -- so the
     *  common case, where no foreign JNDI provider is configured, is the null one. Masking it
     *  threw from inside the property walk once already, in the very class whose toString had
     *  just been repaired.
     */
    public void testANullJndiEnvDoesNotTruncateTheDump() throws Exception
    {
        JndiRefConnectionPoolDataSource jrcpds = new JndiRefConnectionPoolDataSource();
        jrcpds.setJndiName( "java:comp/env/jdbc/realDs" );
        assertNull( "precondition: jndiEnv defaults to null", jrcpds.getJndiEnv() );

        String dump = jrcpds.toString();

        assertFalse( "a dump reporting an exception has been truncated: " + dump,
                     dump.contains( "Exception" ) );
        // Properties sorting after 'jndiEnv' are what a truncated dump loses.
        assertTrue( "cut short at jndiEnv: " + dump, dump.contains( "maxPoolSize" ) );
        assertTrue( "cut short at jndiEnv: " + dump, dump.contains( "jndiName" ) );
    }

    /**
     *  A fourth independently-maintained exclusion list. TO_STRING_IGNORE_PROPS, the bean
     *  generator's exclude list and HIDE_PROPS all omit userOverridesAsString; this one did not,
     *  and the omission hid behind the null-jndiEnv failure above -- the dump died before
     *  reaching it alphabetically, so nothing revealed the leak until that was fixed.
     */
    public void testTheJndiRefDumpOmitsUserOverridesAsString() throws Exception
    {
        JndiRefConnectionPoolDataSource jrcpds = new JndiRefConnectionPoolDataSource();
        jrcpds.setJndiName( "java:comp/env/jdbc/realDs" );
        jrcpds.setJndiEnv( new Hashtable<String,String>() );   // non-null, so the dump completes
        jrcpds.setUserOverridesAsString( userOverridesAsString( "steve", "password", SENTINEL ) );

        String dump = jrcpds.toString();

        assertFalse( "the dump must be whole, or this proves nothing: " + dump,
                     dump.contains( "Exception" ) );
        assertNoSentinel( dump );
        assertFalse( "should be omitted entirely, as the other three lists omit it",
                     dump.contains( "userOverridesAsString -> " ) );

        // IGNORE_PROPS holds two kinds of entry and the distinction is worth stating, because a
        // reader who assumes they are all about secrecy will remove the wrong ones. These three
        // are not configuration at all: they are bean-pattern getters by accident of JDBC
        // naming, and each DOES something -- parentLogger once threw unconditionally, and
        // logWriter and loginTimeout dereference the JNDI name, so reading them for a dump
        // performs a remote lookup.
        String[] notConfiguration = { "parentLogger", "logWriter", "loginTimeout" };
        for ( int i = 0; i < notConfiguration.length; ++i )
            assertFalse( "a config dump has no business reporting " + notConfiguration[i],
                         dump.contains( notConfiguration[i] + " -> " ) );
    }

    /**
     *  The same distinction on the combo class, and the sharpest instance of it: 'allUsers' is
     *  excluded from TO_STRING_IGNORE_PROPS not because usernames are secret but because
     *  getAllUsers() calls getPoolManager() -- the method whose body logs "Initializing c3p0
     *  pool...". Reading that property for a dump would START THE POOL, and emit a nested
     *  pool-init line inside the one being built.
     *
     *  <p>Pinned here because the exclusion looks like a secrecy decision and is not, so a later
     *  pass over the user-masking policy could reasonably delete it.</p>
     */
    public void testADumpDoesNotInitializeThePool() throws Exception
    {
        java.util.logging.Logger jul =
            java.util.logging.Logger.getLogger( "com.mchange.v2.c3p0.impl.AbstractPoolBackedDataSource" );
        CapturingHandler capture = new CapturingHandler();
        Level savedLevel = jul.getLevel();
        jul.addHandler( capture );
        jul.setLevel( Level.ALL );
        try
        {
            ComboPooledDataSource cpds = newCpds();
            cpds.setJdbcUrl( NullConnectDriver.PREFIX + "//host/db" );

            String dump = cpds.toString( true );

            assertFalse( "a dump reporting an exception has been truncated: " + dump,
                         dump.contains( "Exception" ) );
            assertFalse( "building a dump started the pool: " + capture.textAtOrAbove( Level.ALL ),
                         capture.textAtOrAbove( Level.ALL ).contains( "Initializing c3p0 pool" ) );
        }
        finally
        {
            jul.removeHandler( capture );
            jul.setLevel( savedLevel );
        }
    }

    /**
     *  getParentLogger() used to throw SQLFeatureNotSupportedException unconditionally on this
     *  class, which is WHY its toString never completed: the getter is a bean-pattern property by
     *  accident of JDBC 4.1 naming, so the reflective property walk called it. Delegating to the
     *  shared logger fixes the dump at its source rather than hiding the throw behind an
     *  exclusion.
     */
    public void testJndiRefParentLoggerNoLongerThrows() throws Exception
    {
        assertNotNull( new JndiRefConnectionPoolDataSource().getParentLogger() );
    }

    // ------------------------------------------- DriverManagerDataSource messages

    /**
     *  A log line stays within the deployment's logging configuration; an exception message goes
     *  to the caller, which puts it wherever it puts its own errors. And this message fires on a
     *  URL the driver rejected -- which is exactly when someone is reading it, and exactly when
     *  the URL is most likely to have been typed wrong with the password still in it.
     *
     *  <p>The masked form is LABELLED, so a reader comparing the message against their
     *  configuration knows why the two differ.</p>
     */
    public void testTheBadUrlExceptionMasksAndSaysSo() throws Exception
    {
        DriverManagerDataSource dmds = new DriverManagerDataSource();
        dmds.setJdbcUrl( NullConnectDriver.PREFIX + "//host/db?password=" + SENTINEL );
        try
        {
            dmds.getConnection();
            fail( "a driver returning null from connect(..) must raise an SQLException" );
        }
        catch ( SQLException expected )
        {
            String message = String.valueOf( expected.getMessage() );
            assertNoSentinel( "the bad-URL exception message", message );
            assertTrue( "the reader must be told the URL was altered: " + message,
                        message.contains( "(masked)" ) );
            assertTrue( "and enough of the URL must survive to identify it: " + message,
                        message.contains( "host/db" ) );
        }
    }

    /** And where there was nothing to mask, the message must not claim otherwise. */
    public void testTheBadUrlExceptionDoesNotCryMaskedWhenItMaskedNothing() throws Exception
    {
        DriverManagerDataSource dmds = new DriverManagerDataSource();
        dmds.setJdbcUrl( NullConnectDriver.PREFIX + "//host/db" );
        try
        {
            dmds.getConnection();
            fail( "a driver returning null from connect(..) must raise an SQLException" );
        }
        catch ( SQLException expected )
        {
            String message = String.valueOf( expected.getMessage() );
            assertFalse( "nothing was masked, so saying so is misleading: " + message,
                         message.contains( "(masked)" ) );
            assertTrue( message, message.contains( "host/db" ) );
        }
    }

    /**
     *  The other site, a WARNING rather than an exception. Reached only when driverClass is set,
     *  forceUseNamedDriverClass is false, and no registered driver claims the URL -- so c3p0
     *  reports the failed lookup and falls back to instantiating the named class.
     */
    public void testTheDriverLookupWarningMasksTheUrl() throws Exception
    {
        java.util.logging.Logger jul =
            java.util.logging.Logger.getLogger( "com.mchange.v2.c3p0.DriverManagerDataSource" );
        CapturingHandler capture = new CapturingHandler();
        Level savedLevel = jul.getLevel();
        jul.addHandler( capture );
        jul.setLevel( Level.ALL );
        try
        {
            DriverManagerDataSource dmds = new DriverManagerDataSource();
            dmds.setJdbcUrl( "jdbc:nosuchdriverclaimsthis://host/db?password=" + SENTINEL );
            dmds.setDriverClass( "com.mchange.v2.c3p0.test.fakedriver.FakeDriver" );
            try { dmds.getConnection(); }
            catch ( SQLException expected ) { /* no driver can serve it; the WARNING is the point */ }

            String warnings = capture.textAtOrAbove( Level.WARNING );
            assertTrue( "no WARNING was logged, so this proves nothing", warnings.length() > 0 );
            assertNoSentinel( "the driver-lookup WARNING", warnings );
            assertTrue( "the reader must be told the URL was altered: " + warnings,
                        warnings.contains( "(masked)" ) );
        }
        finally
        {
            jul.removeHandler( capture );
            jul.setLevel( savedLevel );
        }
    }

    // ------------------------------------------- the legacy MBean interfaces

    /**
     *  The legacy MBeans are configuration surfaces: a JBoss *-service.xml declares a pool by
     *  setting attributes on one. So the credentials are write-only -- a setter with no getter,
     *  which JMX renders as a writable, non-readable attribute. Removing the setters too would
     *  have left no way to configure a datasource at all.
     */
    public void testTheLegacyMBeansExposeCredentialsWriteOnly() throws Exception
    {
        Class<?>[] ifaces = { com.mchange.v2.c3p0.jboss.C3P0PooledDataSourceMBean.class,
                              com.mchange.v2.c3p0.mbean.C3P0PooledDataSourceMBean.class };
        Object[] impls    = { new com.mchange.v2.c3p0.jboss.C3P0PooledDataSource(),
                              new com.mchange.v2.c3p0.mbean.C3P0PooledDataSource() };

        for ( int i = 0; i < ifaces.length; ++i )
        {
            String label = ifaces[i].getName();
            MBeanInfo info = new StandardMBean( impls[i], (Class) ifaces[i], false ).getMBeanInfo();

            boolean sawWritablePassword = false;
            boolean sawWritableJdbcUrl  = false;
            for ( MBeanAttributeInfo ai : info.getAttributes() )
            {
                if ( "Password".equals( ai.getName() ) || "JdbcUrl".equals( ai.getName() ) )
                {
                    assertFalse( label + " exposes " + ai.getName() + " for reading",
                                 ai.isReadable() );
                    assertTrue( label + " can no longer be configured with " + ai.getName(),
                                ai.isWritable() );
                    if ( "Password".equals( ai.getName() ) ) sawWritablePassword = true;
                    else                                     sawWritableJdbcUrl  = true;
                }
            }
            assertTrue( label + ": JdbcUrl is not settable at all",  sawWritableJdbcUrl );
            assertTrue( label + ": Password is not settable at all", sawWritablePassword );
        }
    }

    // ------------------------------------------- the helpers, directly

    /**
     *  These three are the single definition of how c3p0 masks each shape, called from both the
     *  hand-written toStrings and the expressions the bean generator emits -- so their edges are
     *  worth pinning once here rather than inferred from each caller.
     */
    public void testTheMaskingHelpersPassNullThrough()
    {
        assertNull( C3P0ImplUtils.maskedMap( null ) );
        assertNull( C3P0ImplUtils.maskedUrl( null ) );
        assertNull( C3P0ImplUtils.maskedUserOverrides( null ) );
    }

    /**
     *  null rather than an empty Properties, deliberately: "jndiEnv -> null" says nothing was
     *  configured, where "jndiEnv -> {}" would claim an empty environment was.
     */
    public void testAnEmptyMapIsNotTheSameAnswerAsNoMap()
    {
        assertNotNull( C3P0ImplUtils.maskedMap( new HashMap<String,Object>() ) );
        assertNull(    C3P0ImplUtils.maskedMap( null ) );
    }

    /** A value too malformed to parse is still what the operator typed. */
    public void testMaskedUrlLeavesAValueItCannotParseAlone()
    {
        assertEquals( "not-a-url", C3P0ImplUtils.maskedUrl( "not-a-url" ) );
        assertEquals( "jdbc:h2:mem:test", C3P0ImplUtils.maskedUrl( "jdbc:h2:mem:test" ) );
    }

    // ------------------------------------------------------------------ fixtures

    /**
     *  A driver that claims one distinctive prefix and answers null from connect(..), which is
     *  the only way to reach DriverManagerDataSource's bad-URL message: a driver that refuses the
     *  URL outright makes driver() throw first.
     *
     *  <p>Narrow on purpose. DriverManager is process-wide and hands each URL to drivers in
     *  registration order, so a driver claiming more than its own prefix would answer other
     *  tests' URLs -- which has hung this suite before.</p>
     */
    private final static class NullConnectDriver implements Driver
    {
        final static String PREFIX = "jdbc:c3p0configdumpmaskingprobe:";

        @Override
        public boolean acceptsURL( String url )
        { return url != null && url.startsWith( PREFIX ); }

        @Override
        public Connection connect( String url, Properties info )
        { return null; }   // claimed above, so c3p0 must report it rather than fall through

        @Override
        public int getMajorVersion() { return 1; }

        @Override
        public int getMinorVersion() { return 0; }

        @Override
        public DriverPropertyInfo[] getPropertyInfo( String url, Properties info )
        { return new DriverPropertyInfo[0]; }

        @Override
        public boolean jdbcCompliant() { return false; }

        @Override
        public java.util.logging.Logger getParentLogger()
        { return java.util.logging.Logger.getLogger( NullConnectDriver.class.getName() ); }
    }

    /** Collects log records so an assertion can be made about what was logged. */
    private final static class CapturingHandler extends Handler
    {
        private final List<LogRecord> records = new ArrayList<LogRecord>();

        @Override
        public synchronized void publish( LogRecord record )
        { records.add( record ); }

        @Override
        public void flush() {}

        @Override
        public void close() {}

        synchronized String textAtOrAbove( Level level )
        {
            StringBuffer sb = new StringBuffer();
            for ( Iterator<LogRecord> ii = records.iterator(); ii.hasNext(); )
            {
                LogRecord r = ii.next();
                if ( r.getLevel().intValue() >= level.intValue() )
                    sb.append( r.getMessage() ).append( '\n' );
            }
            return sb.toString();
        }
    }

    // ------------------------------------------------------------------ helpers

    /** One user, one overridden property, plus a benign one so the retention is observable. */
    private static String userOverridesAsString( String user, String key, String value )
    {
        try
        {
            Map<String,Object> perUser = new HashMap<String,Object>();
            perUser.put( key, value );
            perUser.put( "maxPoolSize", "7" );
            Map<String,Map<String,Object>> overrides = new HashMap<String,Map<String,Object>>();
            overrides.put( user, perUser );
            return C3P0ImplUtils.createUserOverridesAsString( overrides );
        }
        catch ( Exception e )
        { throw new RuntimeException( "Could not build a userOverridesAsString", e ); }
    }

    /**
     *  The value of one "<i>name</i> -&gt; <i>value</i>" entry, tracking brace depth so a nested
     *  map is returned whole rather than cut at its first comma.
     */
    private static String valueOf( String dump, String key )
    {
        int i = dump.indexOf( key + " -> " );
        assertTrue( "'" + key + "' does not appear in the dump: " + dump, i >= 0 );
        i += key.length() + 4;

        int depth = 0;
        for ( int j = i; j < dump.length(); ++j )
        {
            char c = dump.charAt( j );
            if ( c == '{' ) ++depth;
            else if ( c == '}' ) --depth;
            else if ( depth == 0 && ( c == ',' || c == ']' ) ) return dump.substring( i, j );
        }
        return dump.substring( i );
    }

    private static void assertNoSentinel( String s )
    { assertNoSentinel( "output", s ); }

    private static void assertNoSentinel( String what, String s )
    { assertFalse( "a credential survived into " + what + ": " + s, s.contains( SENTINEL ) ); }
}
