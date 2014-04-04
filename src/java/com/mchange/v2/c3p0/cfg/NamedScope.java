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

package com.mchange.v2.c3p0.cfg;

import java.util.*;

//all internal maps should be HashMaps (the implementation presumes HashMaps)

class NamedScope
{
    HashMap props;
    HashMap userNamesToOverrides;
    HashMap extensions;

    NamedScope()
    {
	this.props                = new HashMap();
	this.userNamesToOverrides = new HashMap();
	this.extensions           = new HashMap();
    }

    NamedScope( HashMap props, HashMap userNamesToOverrides, HashMap extensions)
    {
	this.props                = props;
	this.userNamesToOverrides = userNamesToOverrides;
	this.extensions           = extensions;
    }

    NamedScope mergedOver( NamedScope underScope )
    {
	HashMap mergedProps = (HashMap) underScope.props.clone();
	mergedProps.putAll( this.props );

	HashMap mergedUserNamesToOverrides = mergeUserNamesToOverrides( this.userNamesToOverrides, underScope.userNamesToOverrides );

	HashMap mergedExtensions = mergeExtensions( this.extensions, underScope.extensions );

	return new NamedScope( mergedProps, mergedUserNamesToOverrides, mergedExtensions );
    }

    static HashMap mergeExtensions( HashMap over, HashMap under )
    {
	HashMap out = (HashMap) under.clone();
	out.putAll( over );
	return out;
    }

    static HashMap mergeUserNamesToOverrides( HashMap over, HashMap under )
    {
	HashMap out = (HashMap) under.clone();

	HashSet underUserNames = new HashSet( under.keySet() );
	HashSet overUserNames = new HashSet( over.keySet() );

	HashSet newUserNames = (HashSet) overUserNames.clone();
	newUserNames.removeAll( underUserNames );

	for ( Iterator ii = newUserNames.iterator(); ii.hasNext(); )
	{
	    String name = (String) ii.next();
	    out.put( name, ((HashMap) over.get( name )).clone() );
	}

	HashSet mergeUserNames = (HashSet) overUserNames.clone();
	mergeUserNames.retainAll( underUserNames );

	for ( Iterator ii = mergeUserNames.iterator(); ii.hasNext(); )
	{
	    String name = (String) ii.next();
	    ((HashMap) out.get(name)).putAll( (HashMap) over.get( name ) );
	}

	return out;
    }
}
