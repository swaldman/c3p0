/*
 * Distributed as part of c3p0 v.0.8.4.2
 *
 * Copyright (C) 2003 Machinery For Change, Inc.
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


package com.mchange.v2.beans;

import java.beans.*;

public final class BeansUtils
{
    public static PropertyEditor findPropertyEditor( PropertyDescriptor pd )
    {
	PropertyEditor out = null;
	Class editorClass = null;
	try
	    {
		editorClass = pd.getPropertyEditorClass();
		if (editorClass != null)
		    out = (PropertyEditor) editorClass.newInstance();
	    }
	catch (Exception e)
	    {
		e.printStackTrace();
		System.err.println("WARNING: Bad property editor class " + editorClass.getName() + 
				   " registered for property " + pd.getName());
	    }

	if ( out == null )
	    out = PropertyEditorManager.findEditor( pd.getPropertyType() );
	return out;
    }

    private BeansUtils()
    {}
}
