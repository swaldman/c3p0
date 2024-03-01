package com.mchange.v2.c3p0;

import java.io.Serializable;
import java.sql.Connection;

/**
 *  <p>Define your own Connection tester if you want to
 *  override c3p0's default behavior for testing the validity
 *  of Connections and responding to Connection errors encountered.</p>
 *
 *  <p><b>Recommended:</b> If you'd like your ConnectionTester
 *  to support the user-configured <code>preferredTestQuery</code>
 *  parameter, please implement {@link com.mchange.v2.c3p0.UnifiedConnectionTester}.
 *
 *  <p>ConnectionTesters should be Serializable, immutable, 
 *  and must have public, no-arg constructors.</p>
 *  
 *  @see com.mchange.v2.c3p0.UnifiedConnectionTester
 *  @see com.mchange.v2.c3p0.AbstractConnectionTester
 */
public interface ConnectionTester extends Serializable
{
    public final static int CONNECTION_IS_OKAY       =  0;
    public final static int CONNECTION_IS_INVALID    = -1;
    public final static int DATABASE_IS_INVALID      = -8;

    public int activeCheckConnection(Connection c);

    public int statusOnException(Connection c, Throwable t);
}
