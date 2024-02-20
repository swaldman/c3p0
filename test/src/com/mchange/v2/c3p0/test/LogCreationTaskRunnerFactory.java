package com.mchange.v2.c3p0.test;

import java.util.Timer;
import javax.sql.ConnectionPoolDataSource;
import com.mchange.v2.async.ThreadPoolReportingAsynchronousRunner;
import com.mchange.v2.c3p0.TaskRunnerFactory;
import com.mchange.v2.c3p0.impl.DefaultTaskRunnerFactory;

import com.mchange.v2.log.*;

public final class LogCreationTaskRunnerFactory implements TaskRunnerFactory
{
    //MT: thread-safe
    final static MLogger logger = MLog.getLogger( LogCreationTaskRunnerFactory.class );

    public ThreadPoolReportingAsynchronousRunner createTaskRunner(
        int num_threads_if_supported,
        int max_administrative_task_time_if_supported, // in seconds!
        String contextClassLoaderSourceIfSupported,
        boolean privilige_spawned_threads_if_supported,
        String threadLabelIfSupported,
        ConnectionPoolDataSource cpds,
        Timer timer
    )
    {
        TaskRunnerFactory inner = new DefaultTaskRunnerFactory();
        ThreadPoolReportingAsynchronousRunner out =
            inner.createTaskRunner(
               num_threads_if_supported,
               max_administrative_task_time_if_supported,
               contextClassLoaderSourceIfSupported,
               privilige_spawned_threads_if_supported,
               threadLabelIfSupported,
               cpds,
               timer
            );
        if (logger.isLoggable(MLevel.INFO))
            logger.log(MLevel.INFO, "Created TaskRunner: " + out);
        return out;
    }
}
