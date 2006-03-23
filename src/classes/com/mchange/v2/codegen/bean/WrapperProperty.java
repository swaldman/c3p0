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


package com.mchange.v2.codegen.bean;

import java.lang.reflect.Modifier;

public abstract class WrapperProperty implements Property
{
    Property p;

    public WrapperProperty(Property p)
    { this.p = p; }

    protected Property getInner()
    { return p; }

    public int getVariableModifiers()
    { return p.getVariableModifiers(); }

    public String  getName()
    { return p.getName(); }

    public String  getSimpleTypeName()
    { return p.getSimpleTypeName(); }

    public String getDefensiveCopyExpression()
    { return p.getDefensiveCopyExpression(); }

    public String getDefaultValueExpression()
    { return p.getDefaultValueExpression(); }

    public int getGetterModifiers()
    { return p.getGetterModifiers(); }

    public int getSetterModifiers()
    { return p.getSetterModifiers(); }

    public boolean isReadOnly()
    { return p.isReadOnly(); }

    public boolean isBound()
    { return p.isBound(); }

    public boolean isConstrained()
    { return p.isConstrained(); }
}
