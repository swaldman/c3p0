package com.mchange.v2.c3p0.test;

import java.util.Timer;
import com.mchange.v2.async.ThreadPoolReportingAsynchronousRunner;
import com.mchange.v2.c3p0.TaskRunnerFactory;
import com.mchange.v2.c3p0.impl.DefaultTaskRunnerFactory;

import com.mchange.v2.log.*;

public class LogCreationTaskRunnerFactory implements TaskRunnerFactory
{
    //MT: thread-safe
    final static MLogger logger = MLog.getLogger( LogCreationTaskRunnerFactory.class );

    public ThreadPoolReportingAsynchronousRunner createTaskRunner( int num_threads_if_supported, int max_administrative_task_time_if_supported, Timer timer, String threadLabelIfSupported )
    {
        TaskRunnerFactory inner = new DefaultTaskRunnerFactory();
        ThreadPoolReportingAsynchronousRunner out = inner.createTaskRunner( num_threads_if_supported, max_administrative_task_time_if_supported, timer, threadLabelIfSupported );
        if (logger.isLoggable(MLevel.INFO))
            logger.log(MLevel.INFO, "Created TaskRunner: " + out);
        return out;
    }
}
