package com.mchange.v2.c3p0.impl;

import java.io.*;
import java.beans.*;
import java.util.*;
import java.lang.reflect.*;

import java.security.AccessController;
import java.security.PrivilegedAction;

import com.mchange.v2.c3p0.*;
import com.mchange.v2.c3p0.cfg.*;

import com.mchange.v2.csv.*;

import javax.naming.NamingException;
import java.sql.Connection;
import java.sql.SQLException;
import com.mchange.lang.ByteUtils;
import com.mchange.v1.identicator.IdentityHashCodeIdenticator;
import com.mchange.v2.encounter.EncounterCounter;
import com.mchange.v2.encounter.EncounterUtils;
import com.mchange.v2.encounter.WeakIdentityEncounterCounter;
import com.mchange.v2.lang.VersionUtils;
import com.mchange.v2.log.MLevel;
import com.mchange.v2.log.MLog;
import com.mchange.v2.log.MLogger;
import com.mchange.v2.log.jdk14logging.ForwardingLogger;
import com.mchange.v2.naming.ReferenceableUtils;
import com.mchange.v2.naming.SecurityConfigKey;
import com.mchange.v2.ser.SerializableUtils;
import com.mchange.v2.sql.SqlUtils;
import com.mchange.v2.uid.UidUtils;

public final class C3P0ImplUtils
{
    // turning this on would only test to generate long tokens
    // on 64 bit machines, but since identityHashCode() is not
    // GUARANTEED unique under 32-bit JVMs, even if in practice
    // always is, we always test to be sure we're not reusing
    // an identity token.
    private final static boolean CONDITIONAL_LONG_TOKENS = false;

    final static MLogger logger = MLog.getLogger( C3P0ImplUtils.class );

    public final static DbAuth NULL_AUTH = new DbAuth(null,null);

    public final static Object[] NOARGS = new Object[0]; 

    public final static java.util.logging.Logger PARENT_LOGGER = new ForwardingLogger( MLog.getLogger("com.mchange.v2.c3p0"), null );

    // we use a wrapped/synchronized version for Thread safety
    private final static EncounterCounter ID_TOKEN_COUNTER;

    static
    {
	if (CONDITIONAL_LONG_TOKENS)
	    {
		boolean long_tokens;
		Integer jnb = VersionUtils.jvmNumberOfBits();
		if (jnb == null)
		    long_tokens = true;
		else if (jnb.intValue() > 32)
		    long_tokens = true;
		else
		    long_tokens = false;

		if (long_tokens)
		    ID_TOKEN_COUNTER = createEncounterCounter();
		else
		    ID_TOKEN_COUNTER = null;
	    }
	else
	    ID_TOKEN_COUNTER = createEncounterCounter();
     }

    // Note that is important that EncounterCounters be based on identity hash code here,
    // since they will be used to test IdentityTokenized, whose equals methods aren't well-formed,
    // until their identity tokens are allocated, which is what we are doing here.
    //
    // We are using weak semantics, which should be fine and minimizes the possibility of unwanted
    // memory retention here. There is a hypothetical corner case, whereunder, with a single VM/ClassLoader,
    // an IdentityTokenized might be created with a given identityHashCode, then serialized or stored as a reference,
    // then closed within the VM, then another IdentityTokenized with coincidentally the same identityHashCode could
    // be allocated, then the origial referenced or deserialized, again within the same VM/ClassLoader. This
    // very, very unlikely case is not dangerous enough to justify the memory cost of strong semantics. In the
    // very unlikely event it should ever prove an issue, we can add some randomness to the within-VM/ClassLoader
    // portion of the tokens.
    private static EncounterCounter createEncounterCounter()
    { return EncounterUtils.syncWrap( EncounterUtils.createWeak( IdentityHashCodeIdenticator.INSTANCE ) ); }

    public final static String VMID_PROPKEY = "com.mchange.v2.c3p0.VMID";
    private final static String VMID_PFX;

    static
    {
        String vmid = C3P0Config.getPropsFileConfigProperty( VMID_PROPKEY );
        if (vmid == null || (vmid = vmid.trim()).equals("") || vmid.equals("AUTO"))
            VMID_PFX = UidUtils.VM_ID + '|';
        else if (vmid.equals("NONE"))
            VMID_PFX = "";
        else
            VMID_PFX = vmid + "|";
    }

    // identityHashCode() is not a sufficient unique token, because they are
    // not guaranteed unique, and in practice are occasionally not unique,
    // particularly on 64-bit systems.

    public static String allocateIdentityToken(Object o)
    { 
	if (o == null)
	    return null;
	else
	    {
		String shortIdToken = Integer.toString( System.identityHashCode( o ), 16 );

		//new Exception( "DEBUG_STACK_TRACE: " + o.getClass().getName() + " " + shortIdToken ).printStackTrace();

		String out;
		long count;
		StringBuffer sb = new StringBuffer(128);
		sb.append(VMID_PFX);
		if (ID_TOKEN_COUNTER != null && ((count = ID_TOKEN_COUNTER.encounter( shortIdToken )) > 0))
		    {
			sb.append( shortIdToken );
			sb.append('#');
			sb.append( count );
		    }
		else
		    sb.append(shortIdToken);

		out = sb.toString().intern();

		return out;
	    }
    }

    public static DbAuth findAuth(Object o)
	throws SQLException
    {
	if ( o == null )
	    return NULL_AUTH;

	String user = null;
	String password = null;

	String overrideDefaultUser    = null;
	String overrideDefaultPassword = null;

	try
	    {
		BeanInfo bi = Introspector.getBeanInfo( o.getClass() );
		PropertyDescriptor[] pds = bi.getPropertyDescriptors();
		for (int i = 0, len = pds.length; i < len; ++i)
		    {
			PropertyDescriptor pd = pds[i];
			Class propCl = pd.getPropertyType();
			String propName = pd.getName();
			if (propCl == String.class)
			    {
//  				System.err.println( "---> " + propName );
//  				System.err.println( o.getClass() );
//  				System.err.println( pd.getReadMethod() );

				Method readMethod = pd.getReadMethod();
				if (readMethod != null)
				    {
					Object propVal = readMethod.invoke( o, NOARGS );
					String value = (String) propVal;
					if ("user".equals(propName))
					    user = value;
					else if ("password".equals(propName))
					    password = value;
					else if ("overrideDefaultUser".equals(propName))
					    overrideDefaultUser = value;
					else if ("overrideDefaultPassword".equals(propName))
					    overrideDefaultPassword = value;
				    }
			    }
		    }
		if (overrideDefaultUser != null)
		    return new DbAuth( overrideDefaultUser, overrideDefaultPassword );
		else if (user != null)
		    return new DbAuth( user, password );
		else
		    return NULL_AUTH;
	    }
	catch (Exception e)
	    {
		if (Debug.DEBUG && logger.isLoggable( MLevel.FINE ))
		    logger.log( MLevel.FINE, "An exception occurred while trying to extract the default authentification info from a bean.", e );
		throw SqlUtils.toSQLException(e);
	    }
    }

    /*
     * This method is called ONLY when user-visible proxy Connections are close()ing,
     * or when the PooledConnection that hosts pCon is close()ing. It is NOT called
     * on commit() and/or rollback() of a continuing user Connection. Given that "fresh"
     * user Connections always begin with autoCommit = true, the logic here is good.
     * We do not setAutoCommit( true ) underneath users holding visible Connections.
     *
     * Perhaps we should rename this to resetTxnStateOnProxyConnectionClose to avoid
     * confusion...
     */ 
    static void resetTxnState( Connection pCon, 
			       boolean forceIgnoreUnresolvedTransactions, 
			       boolean autoCommitOnClose, 
			       boolean txnKnownResolved ) throws SQLException
    {
	if ( !forceIgnoreUnresolvedTransactions && !pCon.getAutoCommit() )
	    {
		if (! autoCommitOnClose && ! txnKnownResolved)
		    {
			//System.err.println("Rolling back potentially unresolved txn...");
			pCon.rollback();
		    }
		pCon.setAutoCommit( true ); //implies commit if not already rolled back.
	    }
    }

    public static boolean supportsMethod(Object target, String mname, Class[] argTypes)
    {
	try {return (target.getClass().getMethod( mname, argTypes ) != null); }
	catch ( NoSuchMethodException e )
	    { return false; }
	catch (SecurityException e)
	    {
		if ( logger.isLoggable( MLevel.FINE ) )
		    logger.log(MLevel.FINE, 
			       "We were denied access in a check of whether " + target + " supports method " + mname + 
			       ". Prob means external clients have no access, returning false.",
			       e);
		return false;
	    }
    }

    /*
    // Java Serialization-based userOverridesAsString format creates an unnecessary attack surface for
    // placing malicious objects in the serialized format and provoking deserialization.
    //
    // We'll transition to a simpler, less dangerous format. 

    private final static String HASM_HEADER = "HexAsciiSerializedMap";

    public static String createUserOverridesAsString( Map userOverrides ) throws IOException
    {
	StringBuffer sb = new StringBuffer();
	sb.append(HASM_HEADER);
	sb.append('[');
	sb.append( ByteUtils.toHexAscii( SerializableUtils.toByteArray( userOverrides ) ) );
	sb.append(']');
	return sb.toString();
    }

    public static Map parseUserOverridesAsString( String userOverridesAsString ) throws IOException, ClassNotFoundException
    { 
	if (userOverridesAsString != null)
	    {
		String hexAscii = userOverridesAsString.substring(HASM_HEADER.length() + 1, userOverridesAsString.length() - 1);
		byte[] serBytes = ByteUtils.fromHexAscii( hexAscii );
		return Collections.unmodifiableMap( (Map) SerializableUtils.fromByteArray( serBytes ) );
	    }
	else
	    return Collections.EMPTY_MAP;
    }
    */

    // we serialize user overrides to "ragged CSV".
    // CSV lines containing only a single element are interpreted as the user for whom we are overriding config
    // lines following containing two elements are the config param overrides for that user.
    public static String createUserOverridesAsString( Map userOverrides ) throws IOException, MalformedCsvException
    {
        Writer w = new StringWriter();
        for (Object o : userOverrides.keySet())
        {
            // we don't check the type. We're treating this as basically an assertion, in our old-school, loosely typed Java
            // we'll let the user see a ClassCastException if something has been messed with
            String user = (String) o;
            w.append(FastCsvUtils.generateQuotedCsvItem(user));
            w.append("\r\n");
            Map userProps = (Map) userOverrides.get(user);
            String[] propNamePropValAsString = new String[2];
            for (Object pn : userProps.keySet())
            {
                propNamePropValAsString[0] = (String) pn;
                propNamePropValAsString[1] = (String) userProps.get(pn);
                w.append(FastCsvUtils.generateCsvLineQuotedUnterminated(propNamePropValAsString));
                w.append("\r\n");
            }
        }
        return w.toString();
    }

    private static Map parseSingleUserMap(String userOverridesAsString, BufferedReader br, String[] nextUserHolder) throws IOException, MalformedCsvException
    {
        Map out = new HashMap();
        nextUserHolder[0] = null;
        String line = FastCsvUtils.csvReadLine(br);
        if (line == null)
        {
            nextUserHolder[0] = null;
            return Collections.EMPTY_MAP;
        }
        else
        {
            do
            {
                String[] items = FastCsvUtils.csvSplitLine( line );
                switch ( items.length )
                {
                case 2: // this is an expected property override line
                    out.put(items[0],items[1]);
                    break;
                case 1: // this is the next user name
                    nextUserHolder[0] = items[0];
                    break;
                default:
                    throw new IOException("Unexpected CSV line in userOverridesAsString ('" + line + "'). All line should have 1 or 2 items:\r\n" + userOverridesAsString);
                }
            }
            while (nextUserHolder[0] == null && (line = FastCsvUtils.csvReadLine(br)) != null); // either EOL or discovery of next user terminates
            return Collections.unmodifiableMap(out);
        }
    }

    public static Map parseUserOverridesAsString( String userOverridesAsString ) throws IOException, MalformedCsvException
    {
        if ( userOverridesAsString != null )
        {
            String[] nextUserHolder = new String[1];
            BufferedReader br = new BufferedReader(new StringReader(userOverridesAsString));
            String line = FastCsvUtils.csvReadLine(br);
            if ( line == null )
                return Collections.EMPTY_MAP;
            else
            {
                Map out = new HashMap();
                String[] items = FastCsvUtils.csvSplitLine(line);
                if (items.length != 1)
                    throw new IOException("Cannot parse userOverridesAsString, one element line naming the user should come before other data:\r\n" + userOverridesAsString);
                String username = items[0];
                do
                {
                    Map overrides = parseSingleUserMap(userOverridesAsString, br, nextUserHolder);
                    out.put(username, overrides);
                    username = nextUserHolder[0];
                }
                while (username != null);
                return Collections.unmodifiableMap(out);
            }
        }
        else
            return Collections.EMPTY_MAP;
    }

    public static String stringifyExtensions(Map extensions) throws IOException, MalformedCsvException
    {
        Writer w = new StringWriter();
        String[] extensionKeyVal = new String[2];
        for (Object ext : extensions.keySet())
            {
                extensionKeyVal[0] = (String) ext;
                extensionKeyVal[1] = (String) extensions.get(ext);
                w.append(FastCsvUtils.generateCsvLineQuotedUnterminated(extensionKeyVal));
                w.append("\r\n");
            }
        return w.toString();
    }

    public static Map unstringifyExtensions(String stringified) throws IOException, MalformedCsvException
    {
        Map out = new HashMap();
        BufferedReader br = new BufferedReader(new StringReader(stringified));
        String line;
        while ((line = FastCsvUtils.csvReadLine(br)) != null)
        {
            String[] items = FastCsvUtils.csvSplitLine( line );
            out.put(items[0],items[1]);
        }
        return Collections.unmodifiableMap(out);
    }


    public static void runWithContextClassLoaderAndPrivileges( final String contextClassLoaderSource, final boolean privilege_spawned_threads, final Runnable runnable )
    {
	class ContextClassLoaderPoolsInitThread extends Thread
	{
	    ContextClassLoaderPoolsInitThread( ClassLoader ccl )
	    { this.setContextClassLoader( ccl ); }

	    public void run()
	    { maybePrivilegedRun( privilege_spawned_threads, runnable ); }
	};

	try
	{
	    if ( "library".equalsIgnoreCase( contextClassLoaderSource ) )
	    {
		Thread t = new ContextClassLoaderPoolsInitThread( C3P0ImplUtils.class.getClassLoader() );
		t.start();
		t.join();
	    }
	    else if ( "none".equalsIgnoreCase( contextClassLoaderSource ) )
	    {
		Thread t = new ContextClassLoaderPoolsInitThread( null );
		t.start();
		t.join();
	    }
	    else
	    {
		if ( logger.isLoggable( MLevel.WARNING ) && ! "caller".equalsIgnoreCase( contextClassLoaderSource ) )
		    logger.log( MLevel.WARNING, "Unknown contextClassLoaderSource: " + contextClassLoaderSource + " -- should be 'caller', 'library', or 'none'. Using default value 'caller'." );
		maybePrivilegedRun( privilege_spawned_threads, runnable );
	    }
	}
	catch ( InterruptedException e )
	{
	    if ( logger.isLoggable( MLevel.SEVERE ) )
		logger.log( MLevel.SEVERE, "Unexpected interruption while trying to run task with contextClassLoaderSource '"+contextClassLoaderSource+"' and privilege_spawned_threads '"+privilege_spawned_threads+"'.", e );
	}
    }

    private static void maybePrivilegedRun( final boolean privilege_spawned_threads, final Runnable runnable )
    {
	if ( privilege_spawned_threads )
	{
	    PrivilegedAction privilegedRun = new PrivilegedAction()
	    {
		public Object run()
		{
		    runnable.run();
		    return null;
		}
	    };
	    AccessController.doPrivileged( privilegedRun );
	}
	else
	    runnable.run();
    }

    /**
     *  never intended to be called. we just want a compiler error if somehow we are building/code-generating 
     *  against an old version of JDBC, as happened somehow with the c3p0-0.9.5-pre2 release
     */
    public static void assertCompileTimePresenceOfJdbc4_Jdk17Api( AbstractNewProxyConnection npc ) throws SQLException
    { npc.getNetworkTimeout(); }

    /**
     * Call to guard setting and use of JNDI names. Will throw a NamingException if the name is unsupported
     */
    public static void jndiAssertNameIsAcceptable(Object jndiName) throws NamingException
    { ReferenceableUtils.assertAcceptableName( jndiName, C3P0Config.getMultiPropertiesConfig() ); }

    //public static SQLException jndiCantResolveNonlocalSQLException( Object name )
    //{ return new SQLException("Could not find DataSource by JNDI name; '" + SecurityConfigKey.PERMIT_NONLOCAL_JNDI_NAMES + "' is false and we are unsure '" + name + "' is local."); }

    //public static NamingException jndiCantResolveNonlocalNamingException( Object name )
    //{ return new NamingException("Could not find DataSource by JNDI name; '" + SecurityConfigKey.PERMIT_NONLOCAL_JNDI_NAMES + "' is false and we are unsure '" + name + "' is local."); }

    private C3P0ImplUtils()
    {}
}



//  Class methodClass = readMethod.getDeclaringClass();
//  Package methodPkg = methodClass.getPackage();
//  System.err.println( methodPkg.getName() + '\t' + C3P0ImplUtils.class.getPackage().getName() );
//  if (! methodPkg.getName().equals( 
//  				 C3P0ImplUtils.class.getPackage().getName() ) )
//  {
//      System.err.println("public check: " + (methodClass.getModifiers() & Modifier.PUBLIC));
//      if ((methodClass.getModifiers() & Modifier.PUBLIC) == 0)
//  	{
//  	    System.err.println("SKIPPED -- Can't Access!");
//  	    continue;
//  	}
//  }
//  System.err.println( o );

    /*
    private final static ThreadLocal threadLocalConnectionCustomizer = new ThreadLocal();

    // used so that C3P0PooledConnectionPool can pass a ConnectionCustomizer 
    // to WrapperConnectionPoolDataSource without altering that class' public API
    public static void setThreadConnectionCustomizer(ConnectionCustomizer cc)
    { threadLocalConnectionCustomizer.set( cc ); }

    public static ConnectionCustomizer getThreadConnectionCustomizer()
    { return threadLocalConnectionCustomizer.get(); }

    public static void unsetThreadConnectionCustomizer()
    { setThreadConnectionCustomizer( null ); }
    */
