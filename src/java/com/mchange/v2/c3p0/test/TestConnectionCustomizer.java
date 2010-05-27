/*
 * Distributed as part of c3p0 v.0.9.2-pre1
 *
 * Copyright (C) 2010 Machinery For Change, Inc.
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


package com.mchange.v2.c3p0.test;

import com.mchange.v2.c3p0.*;
import java.sql.Connection;

public class TestConnectionCustomizer extends AbstractConnectionCustomizer
{
    public void onAcquire( Connection c, String pdsIdt )
    { System.err.println("Acquired " + c + " [" + pdsIdt + "]"); }

    public void onDestroy( Connection c, String pdsIdt )
    { System.err.println("Destroying " + c + " [" + pdsIdt + "]"); }

    public void onCheckOut( Connection c, String pdsIdt )
    { System.err.println("Checked out " + c + " [" + pdsIdt + "]"); }

    public void onCheckIn( Connection c, String pdsIdt )
    { System.err.println("Checking in " + c + " [" + pdsIdt + "]"); }
}
