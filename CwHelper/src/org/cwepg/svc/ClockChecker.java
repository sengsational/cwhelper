/*
 * Created on Apr 16, 2015
 *
 * The purpose of this class is to periodically check to see if the time of the Thread.sleep() agrees with the system clock.
 * If it does not, we need to send an interrupt to the CaptureManager.
 * 
 * This is because after Windows 7, Thread.sleep does not count down in S3 or S4 mode.
 * http://stackoverflow.com/questions/29394222/java-thread-sleep-on-windows-10-stops-in-s3-sleep-status
 * 
 */
package org.cwepg.svc;

import java.io.IOException;
import java.net.Socket;
import java.util.Date;

import org.cwepg.hr.CaptureManager;

public class ClockChecker implements Runnable{

    public static boolean runFlag = false;
    public static Thread runningThread;
    public static ClockChecker singleton;
    private static Thread runThread;
    public static long POLL_INTERVAL = 15000;
    public static final int ACCURACY_MS = 1000;
    
    public static ClockChecker getInstance(){
        if (singleton == null) {
            singleton = new ClockChecker();
        }
        return singleton;
    }

    private ClockChecker(){
        // only run this on Windows greater than version 7.
        runningThread = new Thread(this);
        //System.out.println("runINGThread: " + runningThread.getId());
        if (!CaptureManager.usingOldOs) {
            //System.out.println("state before start:" + runningThread.getState().toString());
            runningThread.start();
            try {Thread.sleep(100);} catch (Throwable t){};
            //System.out.println("state after start:" + runningThread.getState().toString());
        } else {
            System.out.println(new Date() + " ClockChecker is not needed on " + System.getProperty("os.name") + ".  Thread not started.");
            runFlag = false;
        }
    }

    @Override
    public void run() {
        if (isRunning()) {
            //System.out.println("running state in run check:" + runningThread.getState().toString());
            //System.out.println("run     state in run check:" + runThread.getState().toString());
            System.out.println(new Date() + " ERROR: ClockChecker should only have one run thread.");
            return;
        } else {
            System.out.println(new Date() + " ClockChecker run() is starting.");
            setRunning(true);
        }
        long wakePoint = 0;
        long msAccuracy = 0;
        ClockChecker.runThread = Thread.currentThread();
        //System.out.println("runThread: " + runThread.getId());
        while (isRunning()) {
            try {
                wakePoint = ((new Date().getTime() + POLL_INTERVAL));// REMOVED Round to nearest 100: ((ms+50)/100)*100
                Thread.sleep(POLL_INTERVAL); // Blocks
                //////////////for testing/////////////////////////
                //double randomNumber = Math.random(); //System.out.println("randomNumber:" +randomNumber);
                //if(randomNumber < 0.1d){
                //    wakePoint = wakePoint - 2000;
                //   System.out.println(new Date() + "simulating a clock problem.");
                //}
                //////////////for testing///////////////////////////
                msAccuracy = Math.abs(new Date().getTime() - wakePoint);
                if (msAccuracy > ACCURACY_MS) {
                    System.out.println(new Date() + " ClockChecker has detected a clock accuracy issue of " + msAccuracy + "ms.");
                    System.out.println(new Date() + " Requesting CaptureManager interrupt to re-align.");
                    CaptureManager.requestInterrupt("ClockChecker");
                }
            } catch (InterruptedException e) {
                System.out.println(new Date() + " ClockChecker run() has been interrupted.");
                setRunning(false);
            }
        } 
        System.out.println(new Date() + " ClockChecker run() is ending.");
        setRunning(false);
    }
    
    public synchronized boolean isRunning(){
        return runFlag;
    }
    
    public synchronized void setRunning(boolean running){
        this.runFlag = running;
    }

    public static void shutDown() {
        runThread.interrupt();
        try {Thread.sleep(100);} catch (Throwable t){}
    }

    public String setPollInterval(String minutes) {
        boolean result = false;
        String additionalMessage = "";
        if (!ClockChecker.runFlag) {
            additionalMessage = "ClockChecker was not running.  Started ClockChecker. ";
            if (!runningThread.isAlive()){
                POLL_INTERVAL = 15000;
                runningThread = new Thread(this);
                runningThread.start();
                try {Thread.sleep(500);} catch (Throwable t){};
                System.out.println(new Date() + " ClockChecker has a new thread.");
                setRunning(true);
            } else {
                additionalMessage = "ClockChecker was not running.  Could not start ClockChecker. ";
            }
            try {Thread.sleep(100);} catch (Throwable t){};
        }
        try {
            POLL_INTERVAL = Integer.parseInt(minutes) * 1000;
            result = true;
        } catch (Throwable t) {
            System.out.println(new Date() + " /clockchecker minutes not understood.  Ignored.");
        }
        if (result && POLL_INTERVAL < 0) {
            ClockChecker.shutDown();
            return "ClockChecker shutdown requested (/clockchecker?minutes was negative).";
        } else if (result) {
            return additionalMessage + "ClockChecker now set to " + minutes + " minutes.  Takes effect after the current cycle.";
        } else {
            return "ERROR: /clockchecker?minutes= input not parsed properly.  Nothing done.";
        }
    }
    
    public static void main(String[] args) throws InterruptedException {
        
        System.out.println("TEST: simulate starting the main app.");
        ClockChecker.getInstance();
        System.out.println("TEST: now we have a ClockChecker object, but since it's win7, it does not have an active run thread.");
        Thread.sleep(5 * 1000);
        System.out.println("TEST: now we simulate what happens in the web interface to set poll interval.");
        ClockChecker.getInstance().setPollInterval("15");
        System.out.println("TEST: we are done with setting poll interval.");
        Thread.sleep(5 * 1000);
        System.out.println("TEST: we are shutting down by sending a -1.");
        ClockChecker.getInstance().setPollInterval("-1");
        Thread.sleep(5 * 1000);
        System.out.println("TEST: now we simulate what happens in the web interface to set poll interval.");
        ClockChecker.getInstance().setPollInterval("15");
        System.out.println("TEST: we are done with setting poll interval.");
        Thread.sleep(5 * 1000);
        System.out.println("TEST: we will now shutdown.");
        ClockChecker.shutDown();                               // "interrupted/ending" testing shutDown method
        
        
        boolean doOldTesting = false;
        if (doOldTesting){
            System.out.println("TEST: ClockChecker getInstance is about to run.");
            ClockChecker clockChecker = ClockChecker.getInstance(); // Creates object and starts the run method "is starting" if legit OS
            if (clockChecker.isRunning()){
                Thread.sleep(5 * 1000); // let it run x seconds 
                System.out.println(clockChecker.setPollInterval("3")); // "now set to 3"
                Thread.sleep(5 * 1000); // let it run x seconds
                ClockChecker.shutDown();                               // "interrupted/ending" testing shutDown method
            } else {
                System.out.println("TEST: ClockChecker was not running for this first test.");
            }
            Thread.sleep(5 * 1000);
            System.out.println("TEST: Now setting poll interval the first time.");
            System.out.println(clockChecker.setPollInterval("15"));
            Thread.sleep(10 * 1000);
            System.out.println("TEST: Now setting poll interval the second time.");
            System.out.println(clockChecker.setPollInterval("5"));
            Thread.sleep(10 * 1000);
            ClockChecker.shutDown();                               // "interrupted/ending" testing shutDown method
        }
        
        //System.out.println(clockChecker.setPollInterval("1"));     // "has a new thread / starting / now set to 1" testing start by poll interval
        //if (clockChecker.isRunning()){
        //    Thread.sleep(65 * 1000); // let it run 10 seconds
        //    System.out.println(clockChecker.setPollInterval("-1")); // "interrupted/ending/minutes was negative" testing shut down by negative
        //}
        System.out.println(new Date() + " ClockChecker main() ending.");
    }

}
