package com.mchange.v2.c3p0.impl;
/*
 * It would be convenient to put the getter/setter methods
 * for the identity token here, but unfortunately we have no
 * way of setting up the for Referenceability in multiple
 * levels of a class hierarchy. So we leave the getters/setters,
 * and variable initialization to code-generators.
 */
public abstract class AbstractIdentityTokenized implements IdentityTokenized
{
    public boolean equals(Object o)
    {
	if (this == o)
	    return true;

	if (o instanceof IdentityTokenized)
	    return this.getIdentityToken().equals( ((IdentityTokenized) o).getIdentityToken() );
	else
	    return false;
    }

    public int hashCode()
    { return ~this.getIdentityToken().hashCode(); }
}
