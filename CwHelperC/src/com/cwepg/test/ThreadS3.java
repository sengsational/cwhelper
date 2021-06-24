/*
 * Created on Mar 1, 2021
 *
 */
package com.cwepg.test;

import java.util.Date;

import com.cwepg.test.WindowsMessageManager.CallbackObject;

public class ThreadS3 implements CallbackObject {
    private static final int WM_TIMECHANGE = 0x1e;

    private WindowsMessageManager messageManager;
    private Thread sleepThread;
    
    public ThreadS3(WindowsMessageManager messageManager){
        this.messageManager = messageManager;
    }

    public void sleep(long millis) throws InterruptedException {
        messageManager.register(WM_TIMECHANGE, this);
        long endMillis = new Date().getTime() + millis;
        sleepThread = Thread.currentThread();
        do {
            try {
                System.out.println("Sleeping " + millis + " ms.");
                Thread.sleep(millis);
                millis = 0;
            } catch (InterruptedException e) {
                millis = endMillis - new Date().getTime();
            }
        } while (millis > 0);
    }

    @Override
    public void callback(int uMsg) {
        if (uMsg == WM_TIMECHANGE) {
            System.out.println(new Date() + " Interrupted with WM_TIMECHANGE");
            sleepThread.interrupt();
        }
    }
    
    public static void main(String[] args) {
        long fiveMinutes = 5 * 60 * 1000;
        System.out.println(new Date() + " Testing sleep for 5 minutes.  Put machine to into S3 now.");
        long wakeupTime = new Date().getTime() + fiveMinutes;
        try {
            new ThreadS3(WindowsMessageManager.getInstance()).sleep(fiveMinutes);  // This sleep should sleep the right amount of time, even if the machine goes through S3 state
        } catch (InterruptedException e) {
            
        }
        long delta = new Date().getTime() - wakeupTime;
        System.out.println(new Date() + " Expected wakeup at " + new Date(wakeupTime) + " so the delta between 'now' and expected is " + delta + " milliseconds");
    }
}
