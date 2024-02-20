package com.mchange.v2.c3p0;

import com.mchange.v2.async.*;
import java.util.Timer;

public interface TaskRunnerFactory
{
    public ThreadPoolAsynchronousRunner createTaskRunner( int num_threads_if_supported, int max_administrative_task_time_if_supported, Timer timer, String threadLabelIfSupported );
}
