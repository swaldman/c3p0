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


package com.mchange.v2.async;

import com.mchange.v1.util.ClosableResource;

public interface AsynchronousRunner extends ClosableResource
{
    public void postRunnable(Runnable r);


    /**
     * Finish with this AsynchronousRunner, and clean-up
     * any Threads or resources it may hold.
     *
     * @param skip_remaining_tasks Should be regarded as
     *        a hint, not a guarantee. If true, pending,
     *        not-yet-performed tasks will be skipped,
     *        if possible.
     *        Currently executing tasks may or 
     *        may not be interrupted. If false, all
     *        previously scheduled tasks will be 
     *        completed prior to clean-up. The method
     *        returns immediately regardless.
     */ 
    public void close( boolean skip_remaining_tasks );

    /**
     * Clean-up resources held by this asynchronous runner
     * as soon as possible. Remaining tasks are skipped if possible,
     * and any tasks executing when close() is called may
     * or may not be interrupted. Equivalent to close( true ).
     */
    public void close();
}
