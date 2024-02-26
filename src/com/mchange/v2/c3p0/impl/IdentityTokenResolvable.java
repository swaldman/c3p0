package com.mchange.v2.c3p0.impl;

import com.mchange.v2.c3p0.*;
import java.io.ObjectStreamException;

/**
 * This is a convenient base class for all classes
 * that wish to establish an initial identity which
 * will be the basis of a one-per vm identity: i.e.
 * in any vm there should only ever be a single object
 * with a given identity token (except transiently during
 * canonicalization)
 *
 * It would be convenient to put the getter/setter methods
 * for the identity token here, but unfortunately we have no
 * way of setting up the for Referenceability in multiple
 * levels of a class hierarchy. So we leave the getters/setters,
 * and variable initialization to code-generators.
 */
public abstract class IdentityTokenResolvable extends AbstractIdentityTokenized
{
    public static Object doResolve(IdentityTokenized itd)
    { return C3P0Registry.reregister( itd ); }

    protected Object readResolve() throws ObjectStreamException
    { 
	//System.err.println("READ RESOLVE!!!!");
	Object out = doResolve( this ); 
	verifyResolve( out );
	//System.err.println("ORIG: " + this);
	//System.err.println("RSLV: " + out);
	return out;
    }

    protected void verifyResolve( Object o ) throws ObjectStreamException
    {}
}
