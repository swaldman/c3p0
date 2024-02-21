package com.mchange.v2.c3p0;

import java.util.Timer;
import javax.sql.ConnectionPoolDataSource;
import com.mchange.v2.async.*;

public interface TaskRunnerFactory
{
    public ThreadPoolReportingAsynchronousRunner createTaskRunner(
        int num_threads_if_supported,
        int max_administrative_task_time_if_supported, // in seconds!
        String contextClassLoaderSourceIfSupported,
        boolean privilege_spawned_threads_if_supported,
        String threadLabelIfSupported,
        ConnectionPoolDataSource cpds,
        Timer timer
    );
}
