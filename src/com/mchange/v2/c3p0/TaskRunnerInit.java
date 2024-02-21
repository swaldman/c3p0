package com.mchange.v2.c3p0;

import java.util.*;

public final class TaskRunnerInit
{
    public final int num_threads_if_supported;
    public final int max_administrative_task_time_if_supported; // in seconds!
    public final String contextClassLoaderSourceIfSupported;
    public final boolean privilege_spawned_threads_if_supported;
    public final String threadLabelIfSupported;
    public final Map otherProperties;

    public TaskRunnerInit (
            int num_threads_if_supported,
            int max_administrative_task_time_if_supported, // in seconds!
            String contextClassLoaderSourceIfSupported,
            boolean privilege_spawned_threads_if_supported,
            String threadLabelIfSupported,
            HashMap otherProperties
        )
        {
            this.num_threads_if_supported = num_threads_if_supported;
            this.max_administrative_task_time_if_supported = max_administrative_task_time_if_supported; // in seconds!
            this.contextClassLoaderSourceIfSupported = contextClassLoaderSourceIfSupported;
            this.privilege_spawned_threads_if_supported = privilege_spawned_threads_if_supported;
            this.threadLabelIfSupported = threadLabelIfSupported;
            this.otherProperties = Collections.unmodifiableMap( (HashMap) otherProperties.clone() );
        }

    public boolean equals( Object o )
    {
        if (o instanceof TaskRunnerInit)
        {
            TaskRunnerInit other = (TaskRunnerInit) o;
            return
                this.num_threads_if_supported == other.num_threads_if_supported &&
                this.max_administrative_task_time_if_supported == other.max_administrative_task_time_if_supported &&
                this.contextClassLoaderSourceIfSupported.equals( other.contextClassLoaderSourceIfSupported ) &&
                this.privilege_spawned_threads_if_supported == other.privilege_spawned_threads_if_supported &&
                this.threadLabelIfSupported.equals( other.threadLabelIfSupported ) &&
                this.otherProperties.equals(other.otherProperties);
        }
        else
            return false;
    }

    public int hashCode()
    {
        return
            this.num_threads_if_supported ^
            this.max_administrative_task_time_if_supported ^
            this.contextClassLoaderSourceIfSupported.hashCode() ^
            (this.privilege_spawned_threads_if_supported ? 1 : 0) ^
            this.threadLabelIfSupported.hashCode() ^
            this.otherProperties.hashCode();
    }
}
