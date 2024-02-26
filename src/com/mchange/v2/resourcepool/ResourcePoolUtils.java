package com.mchange.v2.resourcepool;

import com.mchange.v2.log.*;

final class ResourcePoolUtils
{
    final static MLogger logger = MLog.getLogger( ResourcePoolUtils.class );

    final static ResourcePoolException convertThrowable( String msg, Throwable t )
    {
	if (Debug.DEBUG)
	    {
		//t.printStackTrace();
		if (logger.isLoggable( MLevel.FINE ) )
		    logger.log( MLevel.FINE , "Converting throwable to ResourcePoolException..." , t );
	    }
	if ( t instanceof ResourcePoolException)
	    return (ResourcePoolException) t;
	else
	    return new ResourcePoolException( msg, t );
    }

    final static ResourcePoolException convertThrowable( Throwable t )
    { return convertThrowable("Ouch! " + t.toString(), t ); }
}
