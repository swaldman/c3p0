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


package com.mchange.v2.cfg.junit;

import junit.framework.*;
import com.mchange.v2.cfg.*;

public final class BasicMultiPropertiesConfigJUnitTestCase extends TestCase
{
    final static String RP_A = "/com/mchange/v2/cfg/junit/a.properties";
    final static String RP_B = "/com/mchange/v2/cfg/junit/b.properties";

    public void testNoSystemConfig()
    {
	MultiPropertiesConfig mpc = new BasicMultiPropertiesConfig(new String[] {RP_A, RP_B});
	//System.err.println(mpc.getProperty( "user.home" ));
	assertTrue( "/b/home".equals( mpc.getProperty( "user.home" ) ) );
    }

    public void testSystemShadows()
    {
	MultiPropertiesConfig mpc = new BasicMultiPropertiesConfig(new String[] {RP_A, RP_B, "/"});
	//System.err.println(mpc.getProperty( "user.home" ));
	assertTrue( (! "/b/home".equals( mpc.getProperty( "user.home" ) ) ) && 
		    (! "/a/home".equals( mpc.getProperty( "user.home" ) ) ) );
    }

    public void testSystemShadowed()
    {
	MultiPropertiesConfig mpc = new BasicMultiPropertiesConfig(new String[] {RP_A, "/", RP_B});
	//System.err.println(mpc.getProperty( "user.home" ));
	assertTrue( "/b/home".equals( mpc.getProperty( "user.home" ) ) );
    }

}
