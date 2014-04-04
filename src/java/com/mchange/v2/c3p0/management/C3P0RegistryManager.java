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

package com.mchange.v2.c3p0.management;

import java.util.*;
import java.sql.SQLException;
import com.mchange.v2.c3p0.C3P0Registry;
import com.mchange.v2.c3p0.subst.C3P0Substitutions;

public class C3P0RegistryManager implements C3P0RegistryManagerMBean 
{
    public String[] getAllIdentityTokens()
    { 
        Set tokens = C3P0Registry.allIdentityTokens(); 
        return (String[]) tokens.toArray( new String[ tokens.size() ] );
    }

    public Set getAllIdentityTokenized()
    { return C3P0Registry.allIdentityTokenized(); }

    public Set getAllPooledDataSources()
    { return C3P0Registry.allPooledDataSources(); }

    public int getAllIdentityTokenCount()
    { return C3P0Registry.allIdentityTokens().size(); }

    public int getAllIdentityTokenizedCount()
    { return C3P0Registry.allIdentityTokenized().size(); }

    public int getAllPooledDataSourcesCount()
    { return C3P0Registry.allPooledDataSources().size(); }

    public String[] getAllIdentityTokenizedStringified()
    { return stringifySet( C3P0Registry.allIdentityTokenized() ); }

    public String[] getAllPooledDataSourcesStringified()
    { return stringifySet( C3P0Registry.allPooledDataSources() ); }

    public int getNumPooledDataSources() throws SQLException
    { return C3P0Registry.getNumPooledDataSources(); }

    public int getNumPoolsAllDataSources() throws SQLException
    { return C3P0Registry.getNumPoolsAllDataSources(); }
    
    public String getC3p0Version()
    { return C3P0Substitutions.VERSION ; }

    private String[] stringifySet(Set s)
    {
	String[] out = new String[ s.size() ];
	int i = 0;
	for (Iterator ii = s.iterator(); ii.hasNext(); )
	    out[i++] = ii.next().toString();
	return out;
    }
}
