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
