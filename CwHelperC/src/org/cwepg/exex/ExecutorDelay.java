package org.cwepg.exex;
/*
 * Created on Jan 27, 2021
 *
 */


import java.util.Date;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ExecutorDelay {
    
    public static final long THREAD_SLEEP_THRESHOLD_MS = 10000;
    private static boolean mDebug;
    
    public static ScheduledThreadPoolExecutor sleep(long mSecondsSleep, String label, boolean debug) throws InterruptedException {
        mDebug = debug;
        
        if (mSecondsSleep < THREAD_SLEEP_THRESHOLD_MS) {
            if (mDebug) System.out.println(new Date() + " Using Thread.sleep() for short duration sleep.");
            try {Thread.sleep(mSecondsSleep);} catch (InterruptedException e) {}
            if (mDebug) System.out.println(new Date() + " Short duration Thread.sleep() ended.");
            return null; // <<<<<<<<<<<<YIKES!!<<<<<<<<<<<<<<<<<<<<<<<<
        } 
        
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(3);
        String interruptedMessage = "";
        try {
            DelayCallable<Object> callableEvent = new DelayCallable<Object>(mSecondsSleep);
            FutureTask futureTask =  (FutureTask) executor.schedule(callableEvent, callableEvent.getSecondsDelay(), TimeUnit.SECONDS);
            try {Thread.sleep(10);} catch (InterruptedException e){};
            if (mDebug) System.out.println(new Date() + " FutureTask blocking for " + callableEvent.getMsDelay());
            long startMs = new Date().getTime();
            futureTask.get(); // blocks
            long actualMs = new Date().getTime() - startMs;
            long errorMs = actualMs - callableEvent.getMsDelay();
            if (mDebug)System.out.println(new Date() + " FutureTask blocking ended after " + actualMs + " and we expected " + callableEvent.getMsDelay());
            if (mDebug && Math.abs(errorMs) > 500) System.out.println(new Date() + " ERROR: the ExecutorDelay was off by " + errorMs + " ms.");
        } catch (InterruptedException e) {
            System.out.println(new Date() + " ExecutorDelay " + label + " was interrupted.");
            interruptedMessage = e.getMessage();
        } catch (ExecutionException e) {
            System.out.println(new Date() + " ExecutorDelay " + label + " had execution exception.");
        } finally {
            String shutdownNowMessage = " Executor was null.";
            if (executor != null) {
                shutdownNowMessage = " shutdowNow pending. ";
                executor.shutdownNow();
                shutdownNowMessage = " shutdownNow complete.";
            }
            System.out.println(new Date() + " ExecutorDelay.sleep() ending. " + interruptedMessage + " / " + shutdownNowMessage );
            if (!"".equals(interruptedMessage)) throw new InterruptedException(interruptedMessage);
        }
        return executor;
    }

    // Test Harness
    public static void main(String[] args) throws Exception {
        System.out.println(new Date() + " Starting test.");
        long mSecondsSleep = 20000;
        String aLabel = "Test Event in " + mSecondsSleep + "ms.";
        ExecutorDelay.sleep(mSecondsSleep, aLabel, true); //Blocks
        System.out.println(new Date() + " Ending test.");
    }


}
