package com.mchange.v2.c3p0;

import java.util.Map;
import java.sql.Connection;
import java.sql.SQLException;

/**
 *  An abstract implementation of the
 *  ConnectionCustomizer interface
 *  in which all methods are no-ops.
 *
 *  Just a convenience class since
 *  most clients will only need to
 *  implement a single method.
 */
public abstract class AbstractConnectionCustomizer implements ConnectionCustomizer
{
    protected Map extensionsForToken( String parentDataSourceIdentityToken )
    { return C3P0Registry.extensionsForToken( parentDataSourceIdentityToken ); }

    public void onAcquire( Connection c, String parentDataSourceIdentityToken ) throws Exception
    {}

    public void onDestroy( Connection c, String parentDataSourceIdentityToken  ) throws Exception
    {}

    public void onCheckOut( Connection c, String parentDataSourceIdentityToken  ) throws Exception
    {}

    public void onCheckIn( Connection c, String parentDataSourceIdentityToken  ) throws Exception
    {}

    public boolean equals( Object o ) { return this.getClass().equals( o.getClass() ); }
    public int hashCode() { return this.getClass().getName().hashCode(); }
}
