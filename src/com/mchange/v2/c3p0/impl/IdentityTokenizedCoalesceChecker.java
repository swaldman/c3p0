package com.mchange.v2.c3p0.impl;

import com.mchange.v2.coalesce.*;

public final class IdentityTokenizedCoalesceChecker implements CoalesceChecker
{
    public static IdentityTokenizedCoalesceChecker INSTANCE = new IdentityTokenizedCoalesceChecker();

    public boolean checkCoalesce( Object a, Object b )
    {
	IdentityTokenized aa = (IdentityTokenized) a;
	IdentityTokenized bb = (IdentityTokenized) b;
	
	String ta = aa.getIdentityToken();
	String tb = bb.getIdentityToken();
	
	if (ta == null || tb == null)
	    throw new NullPointerException( "[c3p0 bug] An IdentityTokenized object has no identity token set?!?! " + (ta == null ? ta : tb) );
	else
	    return ta.equals(tb);
    }
    
    public int coalesceHash( Object a )
    { 
	String t = ((IdentityTokenized) a).getIdentityToken();
	return (t != null ? t.hashCode() : 0); 
    }

    private IdentityTokenizedCoalesceChecker()
    {}
}
