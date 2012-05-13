/*
 * Distributed as part of c3p0 v.0.9.2-pre1
 *
 * Copyright (C) 2010 Machinery For Change, Inc.
 *
 * Author: Steve Waldman <swaldman@mchange.com>
 *
 * This library is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License version 2.1, as 
 * published by the Free Software Foundation.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this software; see the file LICENSE.  If not, write to the
 * Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 * Boston, MA 02111-1307, USA.
 */


package com.mchange.v2.c3p0.impl;

import java.beans.*;
import java.util.*;
import java.lang.reflect.*;
import java.net.InetAddress;

import com.mchange.v2.c3p0.*;
import com.mchange.v2.cfg.MultiPropertiesConfig;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.SQLException;
import com.mchange.lang.ByteUtils;
import com.mchange.v2.encounter.EncounterCounter;
import com.mchange.v2.encounter.EqualityEncounterCounter;
import com.mchange.v2.lang.VersionUtils;
import com.mchange.v2.log.MLevel;
import com.mchange.v2.log.MLog;
import com.mchange.v2.log.MLogger;
import com.mchange.v2.ser.SerializableUtils;
import com.mchange.v2.sql.SqlUtils;

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
		    ID_TOKEN_COUNTER = new EqualityEncounterCounter();
		else
		    ID_TOKEN_COUNTER = null;
	    }
	else
	    ID_TOKEN_COUNTER = new EqualityEncounterCounter();
     }
    
    public final static String VMID_PROPKEY = "com.mchange.v2.c3p0.VMID";
    private final static String VMID_PFX;
    
    static
    {
        String vmid = MultiPropertiesConfig.readVmConfig().getProperty(VMID_PROPKEY);
        if (vmid == null || (vmid = vmid.trim()).equals("") || vmid.equals("AUTO"))
            VMID_PFX = generateVmId() + '|';
        else if (vmid.equals("NONE"))
            VMID_PFX = "";
        else
            VMID_PFX = vmid + "|";
    }

    //MT: protected by class' lock
    static String connectionTesterClassName = null;
    static ConnectionTester cachedTester = null;
    
    private static String generateVmId()
    {
        DataOutputStream dos = null;
        DataInputStream  dis = null;
        try
        {
            SecureRandom srand = new SecureRandom();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            dos = new DataOutputStream( baos );
            try
            {
                dos.write( InetAddress.getLocalHost().getAddress() );
            }
            catch (Exception e)
            {
                if (logger.isLoggable(MLevel.INFO))
                    logger.log(MLevel.INFO, "Failed to get local InetAddress for VMID. This is unlikely to matter. At all. We'll add some extra randomness", e);
                dos.write( srand.nextInt() );
            }
            dos.writeLong(System.currentTimeMillis());
            dos.write( srand.nextInt() );
            
            int remainder = baos.size() % 4; //if it wasn't a 4 byte inet address
            if (remainder > 0)
            {
                int pad = 4 - remainder;
                byte[] pad_bytes = new byte[pad];
                srand.nextBytes(pad_bytes);
                dos.write(pad_bytes);
            }
            
            StringBuffer sb = new StringBuffer(32);
            byte[] vmid_bytes = baos.toByteArray();
            dis = new DataInputStream(new ByteArrayInputStream( vmid_bytes ) );
            for (int i = 0, num_ints = vmid_bytes.length / 4; i < num_ints; ++i)
            {
                int signed = dis.readInt();
                long unsigned = ((long) signed) & 0x00000000FFFFFFFFL; 
                sb.append(Long.toString(unsigned, Character.MAX_RADIX));
            }
            return sb.toString();
        }
        catch (IOException e)
        {
            if (logger.isLoggable(MLevel.WARNING))
                logger.log(MLevel.WARNING, 
                           "Bizarro! IOException while reading/writing from ByteArray-based streams? " +
                           "We're skipping the VMID thing. It almost certainly doesn't matter, " +
                           "but please report the error.", 
                           e);
            return "";
        }
        finally
        {
            // this is like total overkill for byte-array based streams,
            // but it's a good habit
            try { if (dos != null) dos.close(); }
            catch ( IOException e )
            { logger.log(MLevel.WARNING, "Huh? Exception close()ing a byte-array bound OutputStream.", e); }
            try { if (dis != null) dis.close(); }
            catch ( IOException e )
            { logger.log(MLevel.WARNING, "Huh? Exception close()ing a byte-array bound IntputStream.", e); }
        }
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

    public synchronized static ConnectionTester defaultConnectionTester()
    {
	String dfltCxnTesterClassName = PoolConfig.defaultConnectionTesterClassName();
	if ( connectionTesterClassName != null && connectionTesterClassName.equals(dfltCxnTesterClassName) )
	    return cachedTester;
	else
	    {
		try 
		    { 
			cachedTester = (ConnectionTester) Class.forName( dfltCxnTesterClassName ).newInstance(); 
			connectionTesterClassName = cachedTester.getClass().getName();
		    }
		catch ( Exception e )
		    {
			//e.printStackTrace();
			if ( logger.isLoggable( MLevel.WARNING ) )
			    logger.log(MLevel.WARNING, 
				       "Could not load ConnectionTester " + dfltCxnTesterClassName + ", using built in default.", 
				       e);
			cachedTester = C3P0Defaults.connectionTester();
			connectionTesterClassName = cachedTester.getClass().getName();
		    }
		return cachedTester;
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
