package com.mchange.v2.c3p0.impl;

import java.util.Timer;
import javax.sql.ConnectionPoolDataSource;
import com.mchange.v2.async.*;
import com.mchange.v2.c3p0.TaskRunnerFactory;

public final class DefaultTaskRunnerFactory implements TaskRunnerFactory
{
    public ThreadPoolReportingAsynchronousRunner createTaskRunner(
        final int num_threads,
        final int matt,  // maxAdministrativeTaskTime, in seconds
        final String contextClassLoaderSource,
        final boolean privilege_spawned_threads,
        final String threadLabel,
        final ConnectionPoolDataSource cpds,
        final Timer timer
    )
    {
        // we use the array as holder, because we need a final variable for the innner class
        final ThreadPoolAsynchronousRunner[] outHolder = new ThreadPoolAsynchronousRunner[1];

        Runnable initializer = new Runnable()
            {
                public void run()
                {
                    if ( matt > 0 )
                    {
                        int matt_ms = matt * 1000;
                        outHolder[0] = new ThreadPoolAsynchronousRunner( num_threads,
                                                                         true,        // daemon thread
                                                                         matt_ms,     // wait before interrupt()
                                                                         matt_ms * 3, // wait before deadlock declared if no tasks clear
                                                                         matt_ms * 6, // wait before deadlock tasks are interrupted (again)
                                                                                      // after the hung thread has been cleared and replaced
                                                                                      // (in hopes of getting the thread to terminate for
                                                                                      // garbage collection)
                                                                         timer,
                                                                         threadLabel );
                    }
                    else
                        outHolder[0] = new ThreadPoolAsynchronousRunner( num_threads, true, timer, threadLabel );
                }
            };
        C3P0ImplUtils.runWithContextClassLoaderAndPrivileges( contextClassLoaderSource, privilege_spawned_threads, initializer );

        return outHolder[0];
    }

    public boolean equals( Object o ) { return this.getClass().equals( o.getClass() ); }
    public int hashCode() { return this.getClass().getName().hashCode(); }
}
