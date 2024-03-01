package com.mchange.v2.c3p0;

import java.util.Timer;
import javax.sql.ConnectionPoolDataSource;
import com.mchange.v2.async.*;

/**
 * A TaskRunnerFactory should be an immutable class with a public, no-arg constructor, and implement equals and hashCode methods to help support canonicalization.
 *
 * The <code>createTaskRunner</code> method will receive values for all supportable
 * configuration. It is up to the implementation to decide and document what config it can or cannot support.
 *
 * Implementations may find it convenient to capture configuration information as a {@link TaskRunnerInit}.
 */
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
