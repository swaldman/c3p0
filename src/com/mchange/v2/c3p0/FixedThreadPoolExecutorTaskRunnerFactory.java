package com.mchange.v2.c3p0;

import java.util.Timer;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import javax.sql.ConnectionPoolDataSource;
import com.mchange.v2.async.ThreadPoolReportingAsynchronousRunner;

public final class FixedThreadPoolExecutorTaskRunnerFactory extends AbstractExecutorTaskRunnerFactory
{
    // for lazy initialization, called only on first-use
    protected Executor findCreateExecutor(
        int num_threads_if_supported,
        int max_administrative_task_time_if_supported, // in seconds!
        String contextClassLoaderSourceIfSupported,
        boolean privilege_spawned_threads_if_supported,
        String threadLabelIfSupported,
        ConnectionPoolDataSource cpds
    )
    {
        ThreadFactory tf = new TaskRunnerThreadFactory( contextClassLoaderSourceIfSupported, privilege_spawned_threads_if_supported, threadLabelIfSupported, null );
        return Executors.newFixedThreadPool( num_threads_if_supported, tf );
    }

    protected boolean taskRunnerOwnsExecutor() { return true; }

    public ThreadPoolReportingAsynchronousRunner createTaskRunner(
        int num_threads_if_supported,
        int max_administrative_task_time_if_supported, // in seconds!
        String contextClassLoaderSourceIfSupported,
        boolean privilege_spawned_threads_if_supported,
        String threadLabelIfSupported,
        ConnectionPoolDataSource cpds,
        Timer timer
    )
    {
        return new FixedThreadPoolExecutorAsynchronousRunner(
            num_threads_if_supported,
            max_administrative_task_time_if_supported, // in seconds!
            contextClassLoaderSourceIfSupported,
            privilege_spawned_threads_if_supported,
            threadLabelIfSupported,
            cpds,
            timer
        );
    }

    protected final class FixedThreadPoolExecutorAsynchronousRunner extends AbstractExecutorAsynchronousRunner
    {
        protected FixedThreadPoolExecutorAsynchronousRunner(
            int num_threads_if_supported,
            int max_administrative_task_time_if_supported, // in seconds!
            String contextClassLoaderSourceIfSupported,
            boolean privilege_spawned_threads_if_supported,
            String threadLabelIfSupported,
            ConnectionPoolDataSource cpds,
            Timer timer
        )
        {
            super(
                num_threads_if_supported,
                max_administrative_task_time_if_supported, // in seconds!
                contextClassLoaderSourceIfSupported,
                privilege_spawned_threads_if_supported,
                threadLabelIfSupported,
                cpds,
                timer
            );
        }

        public int getThreadCount() { return num_threads_if_supported; }
        public int getIdleCount()   { return getThreadCount() - getActiveCount(); }
    }
}
