/*
 * Distributed as part of c3p0 v.0.9.1.1
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