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
