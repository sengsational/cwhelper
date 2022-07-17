/*
 * Created on Feb 1, 2021
 *
 */
package org.cwepg.exex;

import java.util.Date;
import java.util.concurrent.ScheduledThreadPoolExecutor;

public class DelayRunner implements Runnable {

    private String label;
    private ScheduledThreadPoolExecutor mExecutor;
    private Thread runningThread;
    
    public DelayRunner(String label) {
        this.label = label;
        runningThread = new Thread(this, "Thread-DelayRunner");
        runningThread.start();
    }

    @Override
    public void run() {
        try {
            mExecutor = ExecutorDelay.sleep(Long.MAX_VALUE, label, true); // blocks until shutdown
        } catch (InterruptedException e) {
            System.out.println(new Date() + " DelayRunner instance Executor ended.");
        } finally {
            shutdownNow();
        }
    }

    public void shutdownNow() {
        if (mExecutor != null) {
            System.out.println(new Date() + " Delayrunner Shutting down " + label + " Executor.");
            mExecutor.shutdownNow();
        } else {
            System.out.println(new Date() + " Delayrunner " + label + " Executor was null at shutdown.");
        }
    }
}
