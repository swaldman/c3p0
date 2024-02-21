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
    protected Executor findCreateExecutor( TaskRunnerInit init )
    {
        ThreadFactory tf = new TaskRunnerThreadFactory( init.contextClassLoaderSourceIfSupported, init.privilege_spawned_threads_if_supported, init.threadLabelIfSupported, null );
        return Executors.newFixedThreadPool( init.num_threads_if_supported, tf );
    }

    protected boolean taskRunnerOwnsExecutor() { return true; }

    protected ThreadPoolReportingAsynchronousRunner createTaskRunner( TaskRunnerInit init, Timer timer )
    { return new FixedThreadPoolExecutorAsynchronousRunner( init, timer ); }

    protected final class FixedThreadPoolExecutorAsynchronousRunner extends AbstractExecutorAsynchronousRunner
    {
        protected FixedThreadPoolExecutorAsynchronousRunner( TaskRunnerInit init, Timer timer )
        { super( init, timer ); }

        public int getThreadCount() { return init.num_threads_if_supported; }
        public int getIdleCount()   { return getThreadCount() - getActiveCount(); }
    }
}
