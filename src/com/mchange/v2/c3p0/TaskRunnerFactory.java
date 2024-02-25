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

    /**
     * Define an equals(...) method so that multiple instances
     * of your factory can be canoncalized and shared.
     *
     * Often something like...
     * <code><pre>
     *    public boolean equals( Object o ) { return this.getClass().equals( o.getClass() ); }
     * </pre><code>
     */
    public boolean equals( Object o );

    /**
     * keep consistent with equals()
     *
     * Often something like...
     * <code><pre>
     *     public int hashCode() { return this.getClass().getName().hashCode(); }
     * </pre><code>
     *
     */
    public int hashCode();
}
