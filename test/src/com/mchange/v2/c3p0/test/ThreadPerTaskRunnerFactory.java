package com.mchange.v2.c3p0.test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.Timer;
import java.util.TimerTask;
import javax.sql.ConnectionPoolDataSource;
import com.mchange.v2.async.ThreadPoolReportingAsynchronousRunner;
import com.mchange.v2.c3p0.TaskRunnerFactory;
import com.mchange.v2.c3p0.impl.DefaultTaskRunnerFactory;

import com.mchange.v2.log.*;

// TODO: Implement and use TaskRunnerThreadFactory
public final class ThreadPerTaskRunnerFactory implements TaskRunnerFactory
{
    //MT: thread-safe
    final static MLogger logger = MLog.getLogger( ThreadPerTaskRunnerFactory.class );

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
        ThreadPoolReportingAsynchronousRunner out = new ThreadPerAsynchronousRunner( timer, max_administrative_task_time_if_supported * 1000, threadLabelIfSupported );
        if (logger.isLoggable(MLevel.INFO))
            logger.log(MLevel.INFO, "Created TaskRunner: " + out);
        return out;
    }

    private static class ThreadPerAsynchronousRunner implements ThreadPoolReportingAsynchronousRunner
    {
        private ThreadGroup ourGroup = new ThreadGroup(this.toString() + "-ThreadGroup");
        private AtomicInteger idCounter = new AtomicInteger(0);

        private final static String SEP = System.lineSeparator();

        private Timer timer;
        int     matt_ms;
        String  threadLabel;

        ThreadPerAsynchronousRunner( Timer timer, int matt_ms, String threadLabel )
        {
            this.timer = timer;
            this.matt_ms = matt_ms;
            this.threadLabel = threadLabel;
        }

        public void postRunnable(Runnable r)
        {
            final Thread t = new Thread(ourGroup, r, threadLabel + "-" + idCounter.incrementAndGet());
            t.start();

            if (matt_ms > 0)
                {
                    TimerTask tt = new TimerTask()
                        {
                            public void run() { t.interrupt(); }
                        };
                    timer.schedule( tt, matt_ms );
                }
        }

        public void close( boolean skip_remaining_tasks ) { ourGroup.interrupt(); }
        public void close() { close( true ); }

        public int getThreadCount() { return ourGroup.activeCount(); }
        public int getActiveCount() { return ourGroup.activeCount(); }
        public int getIdleCount() { return 0; }
        public int getPendingTaskCount() { return 0; }

        public String getStatus()
        { return "ThreadPerAsynchronousRunner, " + getActiveCount() + " active tasks."; }

        public String getStackTraces()
        {
            StringBuilder sb = new StringBuilder(4096); // XXX: hard-coded
            int active = getActiveCount();
            int size = Math.max( active * 2, active + 10 );
            Thread[] threads = new Thread[size];
            int n = ourGroup.enumerate( threads );
            sb.append("Threads found: " + n + SEP);
            for( int i = 0; i < n; ++i )
            {
                Thread t = threads[i];
                String name = t.getName();
                StackTraceElement[] elements = t.getStackTrace();
                sb.append("Thread " + name + ":" + SEP);
                if ( elements.length == 0)
                {
                    sb.append("\t<unavailable>");
                    sb.append( SEP );
                }
                else
                {
                    for (StackTraceElement elem : elements)
                    {
                        sb.append("\t");
                        sb.append( elem.toString() );
                        sb.append( SEP );
                    }
                }
                ++i;
            }
            return sb.toString();
        }
    }
}
