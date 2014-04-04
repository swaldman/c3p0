/*
 * Distributed as part of c3p0 0.9.5-pre8
 *
 * Copyright (C) 2014 Machinery For Change, Inc.
 *
 * Author: Steve Waldman <swaldman@mchange.com>
 *
 * This library is free software; you can redistribute it and/or modify
 * it under the terms of EITHER:
 *
 *     1) The GNU Lesser General Public License (LGPL), version 2.1, as 
 *        published by the Free Software Foundation
 *
 * OR
 *
 *     2) The Eclipse Public License (EPL), version 1.0
 *
 * You may choose which license to accept if you wish to redistribute
 * or modify this work. You may offer derivatives of this work
 * under the license you have chosen, or you may provide the same
 * choice of license which you have been offered here.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * You should have received copies of both LGPL v2.1 and EPL v1.0
 * along with this software; see the files LICENSE-EPL and LICENSE-LGPL.
 * If not, the text of these licenses are currently available at
 *
 * LGPL v2.1: http://www.gnu.org/licenses/old-licenses/lgpl-2.1.html
 *  EPL v1.0: http://www.eclipse.org/org/documents/epl-v10.php 
 * 
 */

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
