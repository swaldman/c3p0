package com.mchange.v2.c3p0.debug;

import java.io.*;
import java.sql.*;
import javax.naming.*;
import com.mchange.v2.log.*;
import com.mchange.v2.c3p0.*;

/**
 * <p>For the meaning of most of these properties, please see c3p0's top-level documentation!</p>
 */
public final class CloseLoggingComboPooledDataSource extends AbstractComboPooledDataSource implements Serializable, Referenceable
{
    volatile MLevel level = MLevel.INFO;
    
    public void   setCloseLogLevel( MLevel level ) { this.level = level; }
    public MLevel getCloseLogLevel()               { return level; }
    
    public CloseLoggingComboPooledDataSource()
    { super(); }
  
    public CloseLoggingComboPooledDataSource( boolean autoregister )
    { super( autoregister ); }
    
    public CloseLoggingComboPooledDataSource(String configName)
    { super( configName );  }
    
    public Connection getConnection() throws SQLException
    { return new CloseLoggingConnectionWrapper( super.getConnection(), level );  }
    
    public Connection getConnection(String user, String password) throws SQLException
    { return new CloseLoggingConnectionWrapper( super.getConnection(user, password), level );  }
    
    // serialization stuff -- set up bound/constrained property event handlers on deserialization
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
