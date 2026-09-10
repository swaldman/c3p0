package com.mchange.v2.c3p0.test.junit;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Hashtable;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.spi.InitialContextFactory;

import junit.framework.TestCase;

import com.mchange.v2.c3p0.JndiRefConnectionPoolDataSource;

/**
 *  c3p0 must never resolve a JNDI reference against an InitialContext environment supplied
 *  on the DataSource, because it cannot establish where that environment came from.
 *
 *  <p>An attacker who can tamper with a serialized DataSource, or plant a Reference that
 *  some ObjectFactory outside c3p0's control dereferences, controls that Hashtable. A
 *  <code>java.naming.factory.initial</code> of their choosing turns an innocuous-looking
 *  lookup into code of their choosing, and the NameGuard cannot help: it constrains the
 *  <em>name</em>, while the environment redirects <em>where the name is resolved</em>.</p>
 *
 *  <p>These tests assert the property rather than the mechanism. Rather than matching on an
 *  exception message, they name a factory in the environment and assert it is never
 *  reached. That holds however the refusal is implemented, so a later redesign -- a filter,
 *  a veto hook, an opt-in property -- still has to keep the default closed to pass.</p>
 */
public class JndiEnvNotHonoredJUnitTestCase extends TestCase
{
    /** The NameGuard requires an apparently-local name, so use one it accepts. */
    private final static String LOCAL_NAME = "java:comp/env/jdbc/whatever";

    /**
     *  Stands in for whatever an attacker would name. If c3p0 ever builds an InitialContext
     *  from the supplied environment, this is instantiated and says so.
     */
    public static class TattlingContextFactory implements InitialContextFactory
    {
        static volatile boolean invoked = false;

        @Override
        public Context getInitialContext(Hashtable<?,?> environment) throws NamingException
        {
            invoked = true;
            throw new NamingException("TattlingContextFactory should never be reached.");
        }
    }

    private Hashtable<String,String> tattlingEnv()
    {
        Hashtable<String,String> env = new Hashtable<String,String>();
        env.put( Context.INITIAL_CONTEXT_FACTORY, TattlingContextFactory.class.getName() );
        env.put( Context.PROVIDER_URL, "ldap://never.contacted.invalid:1389" );
        return env;
    }

    @Override
    public void setUp()
    { TattlingContextFactory.invoked = false; }

    /** The core property: a supplied environment is never used to resolve anything. */
    public void testSuppliedEnvironmentIsNeverResolvedAgainst() throws Exception
    {
        JndiRefConnectionPoolDataSource cpds = new JndiRefConnectionPoolDataSource();
        cpds.setJndiEnv( tattlingEnv() );
        cpds.setJndiName( LOCAL_NAME );

        try
        {
            cpds.getPooledConnection();
            fail( "A lookup against a supplied JNDI environment must not succeed." );
        }
        catch ( Exception expected )
        { /* the refusal; its type and wording are not what this test is about */ }

        assertFalse( "c3p0 must not build an InitialContext from a supplied environment: the " +
                     "factory named in that environment was instantiated.",
                     TattlingContextFactory.invoked );
    }

    /**
     *  and it must stay true across Java serialization, which is one of the routes by which
     *  an attacker-controlled environment can arrive. Whether the deserialized instance
     *  survives or C3P0Registry coalesces it back to the original, the property is the same.
     */
    public void testSuppliedEnvironmentIsNeverResolvedAgainstAfterSerialization() throws Exception
    {
        JndiRefConnectionPoolDataSource cpds = new JndiRefConnectionPoolDataSource();
        cpds.setJndiEnv( tattlingEnv() );
        cpds.setJndiName( LOCAL_NAME );

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream( baos );
        oos.writeObject( cpds );
        oos.close();

        Object restored =
            new ObjectInputStream( new ByteArrayInputStream( baos.toByteArray() ) ).readObject();
        assertTrue( restored instanceof JndiRefConnectionPoolDataSource );

        try
        {
            ((JndiRefConnectionPoolDataSource) restored).getPooledConnection();
            fail( "A lookup against a deserialized JNDI environment must not succeed." );
        }
        catch ( Exception expected )
        {}

        assertFalse( "A deserialized environment must not be resolved against either.",
                     TattlingContextFactory.invoked );
    }

    /**
     *  An empty environment is not an environment. It must not be refused as though it were
     *  one, or a DataSource that merely had setJndiEnv(new Hashtable()) called on it would
     *  become unusable.
     */
    public void testEmptyEnvironmentIsTreatedAsNoEnvironment() throws Exception
    {
        JndiRefConnectionPoolDataSource cpds = new JndiRefConnectionPoolDataSource();
        cpds.setJndiEnv( new Hashtable<String,String>() );
        cpds.setJndiName( LOCAL_NAME );

        // No JNDI provider is configured in this JVM, so the lookup fails either way. What
        // matters is that it fails as a lookup, having got past the environment check --
        // and that nothing from any supplied environment was consulted.
        try { cpds.getPooledConnection(); }
        catch ( Exception expected ) {}

        assertFalse( TattlingContextFactory.invoked );
    }

    /** The factory really would tattle if it were reached -- otherwise the tests above prove nothing. */
    public void testTattlingFactoryActuallyTattles() throws Exception
    {
        try
        {
            new javax.naming.InitialContext( tattlingEnv() ).lookup( LOCAL_NAME );
            fail( "expected the tattling factory to throw" );
        }
        catch ( NamingException expected )
        {}

        assertTrue( "If this fails, the other tests are vacuous.", TattlingContextFactory.invoked );
    }
}
