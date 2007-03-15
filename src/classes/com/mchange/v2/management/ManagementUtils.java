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


package com.mchange.v2.management;

import javax.management.*;
import java.util.Comparator;

public class ManagementUtils
{
    public final static Comparator PARAM_INFO_COMPARATOR = new Comparator()
    {
        public int compare(Object a, Object b)
        {
            MBeanParameterInfo aa = (MBeanParameterInfo) a;
            MBeanParameterInfo bb = (MBeanParameterInfo) b;
            int out = aa.getType().compareTo(bb.getType());
            if (out == 0)
            {
                out = aa.getName().compareTo(bb.getName());
                if (out == 0)
                {
                    String aDesc = aa.getDescription();
                    String bDesc = bb.getDescription();
                    if (aDesc == null && bDesc == null)
                        out = 0;
                    else if (aDesc == null)
                        out = -1;
                    else if (bDesc == null)
                        out = 1;
                    else
                        out = aDesc.compareTo(bDesc);
                }
            }
            return out;
        }
    };
    
    public final static Comparator OP_INFO_COMPARATOR = new Comparator()
    {
        public int compare(Object a, Object b)
        {
            MBeanOperationInfo aa = (MBeanOperationInfo) a;
            MBeanOperationInfo bb = (MBeanOperationInfo) b;
            String aName = aa.getName();
            String bName = bb.getName();
            int out = String.CASE_INSENSITIVE_ORDER.compare(aName, bName);
            if (out == 0)
            {
                if (aName.equals(bName))
                {
                    MBeanParameterInfo[] aParams = aa.getSignature();
                    MBeanParameterInfo[] bParams = bb.getSignature();
                    if (aParams.length < bParams.length)
                        out = -1;
                    else if (aParams.length > bParams.length)
                        out = 1;
                    else
                    {
                        for (int i = 0, len = aParams.length; i < len; ++i)
                        {
                            out = PARAM_INFO_COMPARATOR.compare(aParams[i], bParams[i]);
                            if (out != 0)
                                break;
                        }
                    }
                }
                else
                {
                    out = aName.compareTo(bName);
                }
            }
            return out;
        }
    };
}
