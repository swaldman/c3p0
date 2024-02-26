package com.mchange.v2.c3p0;

import java.io.*;
import javax.naming.*;

/**
 * <p>For the meaning of most of these properties, please see c3p0's top-level documentation!</p>
 */
public final class ComboPooledDataSource extends AbstractComboPooledDataSource implements Serializable, Referenceable
{
    public ComboPooledDataSource()
    { super(); }

    public ComboPooledDataSource( boolean autoregister )
    { super( autoregister ); }

    public ComboPooledDataSource(String configName)
    { super( configName );  }


    // serialization stuff -- set up bound/constrained property event handlers on deserialization
    private static final long serialVersionUID = 1;
    private static final short VERSION = 0x0002;

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

