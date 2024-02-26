package com.mchange.v2.c3p0.debug;

import java.io.*;
import java.sql.*;
import javax.naming.*;
import com.mchange.v2.log.*;
import com.mchange.v2.c3p0.*;

/**
 * <p>For the meaning of most of these properties, please see c3p0's top-level documentation!</p>
 */
public final class AfterCloseLoggingComboPooledDataSource extends AbstractComboPooledDataSource implements Serializable, Referenceable
{
    public AfterCloseLoggingComboPooledDataSource()
    { super(); }
  
    public AfterCloseLoggingComboPooledDataSource( boolean autoregister )
    { super( autoregister ); }
    
    public AfterCloseLoggingComboPooledDataSource(String configName)
    { super( configName );  }
    
    public Connection getConnection() throws SQLException
    { return AfterCloseLoggingConnectionWrapper.wrap( super.getConnection() );  }
    
    public Connection getConnection(String user, String password) throws SQLException
    { return AfterCloseLoggingConnectionWrapper.wrap( super.getConnection(user, password) );  }
    
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
