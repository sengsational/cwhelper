/*
 * Created on Mar 3, 2019
 *
 */
package org.cwepg.hr;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.cwepg.svc.NtpMessage;
import org.cwepg.svc.SetTimeCommandLine;
import org.cwepg.svc.TimeLimitedCodeBlock;

public class PcClockSetter implements Runnable {
    
    static int requestedOffset;
    static PcClockSetter singleton;
    static boolean running = false;
    static String response = "";
    static long clockOffsetSeconds = Long.MAX_VALUE;
    static Thread runningThread;
    
    public static PcClockSetter getInstance() {
        if (singleton != null) {
            PcClockSetter.finish();
        }
        singleton = new PcClockSetter();
        return singleton;
    }

    public static PcClockSetter getInstance(int requestedOffset) {
        PcClockSetter.getInstance();
        PcClockSetter.requestedOffset = requestedOffset;
        return singleton;
    }

    private PcClockSetter() {
        running = true;
        runningThread = new Thread(this);
        runningThread.start();
    }

    @Override
    public void run() {
        response = " ERROR: Network problem. ";
        try {
            clockOffsetSeconds = NtpMessage.getLocalClockOffsetSeconds("us.pool.ntp.org", "Initial"); // MIGHT BLOCK HERE 'FOREVER', but we have a reference to the thread so we can terminate it
            response = " OK ";
        } catch (Throwable t) {
            response = "ERROR: could not get time from the Interent. " + t.getMessage() + " ";
        }
        running = false;
    }
    
    public static long getOffsetFromInternet() {
        return clockOffsetSeconds;
    }

    public static String adjustPcClock(int timeoutSeconds) {
        System.out.println(new Date() + " A PC Clock setting  of " + requestedOffset + " has been requested.  To disable, set clockoffset to 0.");
        if (response == null || response.startsWith("ERROR")) {
            return response;
        }
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        try { 
            // if offset is positive, it's slow.  So say we have clockOffsetSeconds as 28.
            // if they ask for "0", we need to add 28 seconds to 0 to get 28.
            int changeRequested = requestedOffset + (int)clockOffsetSeconds;
            Timestamp alteredTimestamp = new Timestamp(System.currentTimeMillis() + changeRequested * 1000);
            String alteredTimestampString = sdf.format(alteredTimestamp);
            response += "<br> original timestamp was " + sdf.format(new Timestamp(System.currentTimeMillis()));
            response += "<br> altering timestamp to  " + alteredTimestampString;
            
            SetTimeCommandLine runningCommandLine = new SetTimeCommandLine(alteredTimestampString, 2, 5); 
            runningCommandLine.setHangProtection(true); // It turns out that this wasn't where that hang was after all, but leaving hang protection in place.
            boolean goodResult = runningCommandLine.runProcess(); // blocks while command runs
            if (!goodResult) {
                response += "<br>" + "Command line did not return nicely.  Command underway at the time: " + runningCommandLine.getCommands() + " " + runningCommandLine.getOutput(); 
            } else {
                Thread.sleep(1000); // Wait for the clock to be set
                
                try {
                  TimeLimitedCodeBlock.runWithTimeout(new Runnable() {
                    @Override
                    public void run() {
                        response += "<br>Getting clock offset.";
                        try {
                            clockOffsetSeconds = NtpMessage.getLocalClockOffsetSeconds("us.pool.ntp.org", "Resulting"); // MIGHT BLOCK HERE 'FOREVER'
                            response += "<br>Got clock offset.";
                        } catch (Exception e) {
                            response += "<br>Get clock offset error: " + e.getMessage();
                        } 
                    }
                  }, timeoutSeconds, TimeUnit.SECONDS);
                } catch (TimeoutException e) {
                  response += "<br>Get clock offset timed-out.";
                } 
                if (response.indexOf("Got clock offset") > -1) {
                    response += "<br> Clock on the PC is now " + Math.abs(clockOffsetSeconds) + " seconds " + (clockOffsetSeconds > 0?"slower":"faster") + " than us.pool.ntp.org.  ";
                }
            }
        } catch (Throwable t) {
            String clockOffsetIgnoredMessage = "clockOffset was ignored due to error. " + t.getMessage();
            response+= "<br>" + clockOffsetIgnoredMessage;
        }
        return response;
    }

    public static boolean isRunning() {
        return running;
    }

    public static void finish() {
        runningThread.interrupt();
    }

}
