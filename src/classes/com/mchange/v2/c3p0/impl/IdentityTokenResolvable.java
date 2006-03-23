/*
 * Distributed as part of c3p0 v.0.9.1-pre6
 *
 * Copyright (C) 2005 Machinery For Change, Inc.
 *
 * Author: Steve Waldman <swaldman@mchange.com>
 *
 * This library is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License version 2.1, as 
 * published by the Free Software Foundation.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this software; see the file LICENSE.  If not, write to the
 * Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 * Boston, MA 02111-1307, USA.
 */


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
 *
 * Non-generated classes, that of course take care of their own
 * referenceability, should extend BaseIdentityTokenized to
 * inherit these extra conveniences.
 *
 * NOTE: since I'm not using it just now, BaseIdentityTokenized
 *       is removed to the old/ directory... recover if necessary
 */
public abstract class IdentityTokenResolvable implements IdentityTokenized
{
    public static Object doResolve(IdentityTokenized itd)
    { return C3P0Registry.coalesce( itd ); }

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