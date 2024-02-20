package com.mchange.v2.c3p0.impl;

import java.util.Timer;
import javax.sql.ConnectionPoolDataSource;
import com.mchange.v2.async.*;
import com.mchange.v2.c3p0.TaskRunnerFactory;

public final class DefaultTaskRunnerFactory implements TaskRunnerFactory
{
    public ThreadPoolReportingAsynchronousRunner createTaskRunner( int num_threads, int matt  /* maxAdministrativeTaskTime */, Timer timer, String threadLabel, ConnectionPoolDataSource cpds )
    {
        ThreadPoolAsynchronousRunner out = null;
        if ( matt > 0 )
        {
            int matt_ms = matt * 1000;
            out = new ThreadPoolAsynchronousRunner( num_threads, 
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
            out = new ThreadPoolAsynchronousRunner( num_threads, true, timer, threadLabel );

        return out;
    }
}
