package com.mchange.v2.c3p0.debug;

import java.io.*;
import java.sql.*;
import javax.naming.*;
import com.mchange.v2.log.*;
import com.mchange.v2.c3p0.*;

/**
 * <p>For the meaning of most of these properties, please see c3p0's top-level documentation!</p>
 */
public final class ConstructionLoggingComboPooledDataSource extends AbstractComboPooledDataSource implements Serializable, Referenceable
{
    final static MLogger logger = MLog.getLogger( ConstructionLoggingComboPooledDataSource.class );

    public ConstructionLoggingComboPooledDataSource()
    { 
	super(); 
	if ( logger.isLoggable( MLevel.FINE ) )
	    logger.log( MLevel.FINE, 
			"Creation of ConstructionLoggingComboPooledDataSource.",
			new Exception("DEBUG STACK TRACE -- CREATION OF ConstructionLoggingComboPooledDataSource") );
    }
  
    public ConstructionLoggingComboPooledDataSource( boolean autoregister )
    { 
	super( autoregister ); 
	if ( logger.isLoggable( MLevel.FINE ) )
	    logger.log( MLevel.FINE, 
			"Creation of ConstructionLoggingComboPooledDataSource.",
			new Exception("DEBUG STACK TRACE -- CREATION OF ConstructionLoggingComboPooledDataSource") );
    }
    
    public ConstructionLoggingComboPooledDataSource(String configName)
    { 
	super( configName );  
	if ( logger.isLoggable( MLevel.FINE ) )
	    logger.log( MLevel.FINE, 
			"Creation of ConstructionLoggingComboPooledDataSource.",
			new Exception("DEBUG STACK TRACE -- CREATION OF ConstructionLoggingComboPooledDataSource") );
    }
    
    // serialization stuff
    private static final long serialVersionUID = 1;
    private static final short VERSION = 0x0001;
    
    private void writeObject( ObjectOutputStream oos ) throws IOException
    {
        oos.writeShort( VERSION );
    }

    private void readObject( ObjectInputStream ois ) throws IOException, ClassNotFoundException
    {
        short version = ois.readShort();
        switch (version)
        {
        case VERSION:
	    //ok
            break;
        default:
            throw new IOException("Unsupported Serialized Version: " + version);
        }
    }
}
