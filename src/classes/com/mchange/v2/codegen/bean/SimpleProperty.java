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

public class SimpleProperty implements Property
{
    int     variable_modifiers;
    String  name;
    String  simpleTypeName;
    String  defensiveCopyExpression;
    String  defaultValueExpression;
    int     getter_modifiers;
    int     setter_modifiers;
    boolean is_read_only;
    boolean is_bound;
    boolean is_constrained;

    public int     getVariableModifiers()       { return variable_modifiers; }
    public String  getName()                    { return name; }
    public String  getSimpleTypeName()          { return simpleTypeName; }
    public String  getDefensiveCopyExpression() { return defensiveCopyExpression; }
    public String  getDefaultValueExpression()  { return defaultValueExpression; }
    public int     getGetterModifiers()         { return getter_modifiers; }
    public int     getSetterModifiers()         { return setter_modifiers; }
    public boolean isReadOnly()                 { return is_read_only; }
    public boolean isBound()                    { return is_bound; }
    public boolean isConstrained()              { return is_constrained; }

    public SimpleProperty( int     variable_modifiers,
			   String  name,
			   String  simpleTypeName,
			   String  defensiveCopyExpression,
			   String  defaultValueExpression,
			   int     getter_modifiers,
			   int     setter_modifiers,
			   boolean is_read_only,
			   boolean is_bound,
			   boolean is_constrained )
    {
	this.variable_modifiers = variable_modifiers;
	this.name = name;
	this.simpleTypeName = simpleTypeName;
	this.defensiveCopyExpression = defensiveCopyExpression;
	this.defaultValueExpression = defaultValueExpression;
	this.getter_modifiers = getter_modifiers;
	this.setter_modifiers = setter_modifiers;
	this.is_read_only = is_read_only;
	this.is_bound = is_bound;
	this.is_constrained = is_constrained;
    }

    public SimpleProperty( String  name,
			   String  simpleTypeName,
			   String  defensiveCopyExpression,
			   String  defaultValueExpression,
			   boolean is_read_only,
			   boolean is_bound,
			   boolean is_constrained )
    {
	this ( Modifier.PRIVATE,
	       name,
	       simpleTypeName,
	       defensiveCopyExpression,
	       defaultValueExpression,
	       Modifier.PUBLIC,
	       Modifier.PUBLIC,
	       is_read_only,
	       is_bound,
	       is_constrained );
    }
}
