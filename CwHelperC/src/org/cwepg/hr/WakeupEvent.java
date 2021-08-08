/*
 * Created on Jan 22, 2010
 *
 */
package org.cwepg.hr;


import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Date;
import java.util.StringTokenizer;

import org.cwepg.reg.Registry;
import org.cwepg.svc.AutorunFile;
import org.cwepg.svc.CwEpgCommandLine;

import com.sun.jna.platform.win32.Kernel32;

public class WakeupEvent extends TimedEvent implements Runnable {

    static StringBuffer problems;
    
    static WakeupEvent wakeupEvent;
    static Kernel32.HANDLE eventHandle; // DRS 20161118 - Added 1 - one event handle.  If a new one shows up, make sure to delete the old one.
    static String handleName; // DRS 20161119 - Added 1 - keeping track of handle name as a string for logging purposes.

    static String saveToDisk = "true";
    static String osParameters = "null";
    static String overrideCommand = "null";

    static String saveFileName = "WakeupEventData.txt";
    
    static Thread runThread;
    static boolean isRunning;
    
    public static final int DONE = 0;
    public static final int RESET = 1;
    public static final int CANCEL = 2;
    public static final int KILL = 3;
    public static final String[] COMMAND = {"Done", "Reset", "Cancel", "Kill"};
    
    int debug = 1;
    
    int currentCommand;
    CwEpgCommandLine cl = null;
    static AutorunFile arf;
    static boolean activeAutorun = false;
    
    private WakeupEvent(){
        super();
    }
    
    public static WakeupEvent getInstance(){
        if (wakeupEvent == null){
            wakeupEvent = new WakeupEvent();
        }
        return wakeupEvent;
    }

    public static WakeupEvent getInstanceFromDisk() {
        wakeupEvent = null;
        String fileName = CaptureManager.dataPath + saveFileName;
        String message = new Date() + " Optional wakeupEvent data file not available for intialization.";

        try {
            BufferedReader in = new BufferedReader(new FileReader(fileName));
            String l = null;
            if ((l = in.readLine()) != null) {
                wakeupEvent = WakeupEvent.getInstance();
                wakeupEvent.initialize(l);
                message = new Date() + " WakeupEvent initialized from data file. " + l;
            }
            in.close();
        } catch (Exception e) {
            message = new Date() + " Optional wakeupEvent data file not available. " + e.getMessage();
            wakeupEvent = null;
        }
        System.out.println(message);
        
        boolean isMaster = (osParameters != null && !osParameters.equals("") && !osParameters.equals("null"));
        if (wakeupEvent != null) arf = new AutorunFile(wakeupEvent.hourToTrigger, wakeupEvent.minuteToTrigger, wakeupEvent.durationMinutes, WakeupEvent.osParameters, isMaster, CaptureManager.leadTimeSeconds, isMaster, WakeupEvent.overrideCommand);

        return wakeupEvent;
    }
    
    public AutorunFile getAutorunFile() {
        if (arf != null) {
            return arf;
        } else {
            WakeupEvent.getInstanceFromDisk(); // will create arf if there is a wakeup event text file.
            if (arf == null) {
                arf = new AutorunFile();
            }
            return arf;
        }
    }
    
    public void initialize(String hourToSend, String minuteToSend, String duration, String saveToDisk, String osParameters, String overrideCommand) {
        super.initialize(hourToSend, minuteToSend, duration);
        if (osParameters != null && !osParameters.equals("")){
            WakeupEvent.osParameters = osParameters;
        }
        if (overrideCommand != null && !overrideCommand.equals("")) {
            WakeupEvent.overrideCommand = overrideCommand;
        }
        if (isValid()){
            arf = new AutorunFile(wakeupEvent.hourToTrigger, wakeupEvent.minuteToTrigger, wakeupEvent.durationMinutes, WakeupEvent.osParameters, validOsParameters(), CaptureManager.leadTimeSeconds, validOsParameters(), WakeupEvent.overrideCommand);
            logHardwareWakeupHandle("WakeupEvent initialize for " + osParameters, WakeupEvent.eventHandle); 
            WakeupEvent.eventHandle = this.setWakeup("WakeupEvent initialize for " + osParameters); // DRS 2016118 <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<< setWakeup
            if (WakeupEvent.eventHandle != null) WakeupEvent.handleName = WakeupEvent.eventHandle.toString(); // DRS 20161119 - Added 1 - For logging when handle is null
            super.removePreviousHardwareWakeupEvent("TimedEvent initialize() " + hourToSend +":" + minuteToSend); // DRS 20150524 Delete old handle if there is one
            if (WakeupEvent.saveToDisk.toUpperCase().equals("TRUE")) writeWakeupEventData();
        } else {
            System.out.println(new Date() + " There were some problems reported with the proposed WakeupEvent; " + WakeupEvent.problems.toString());
        }
    }
    
    public void initialize(String persistenceData){
        StringTokenizer tok = new StringTokenizer(persistenceData, "|");
        String hourToTrigger = tok.nextToken(); 
        String minuteToTrigger = tok.nextToken(); 
        String durationMinutes = tok.nextToken(); 
        String osParameters = tok.nextToken(); 
        String saveToDisk = tok.nextToken();
        String overrideCommand = "null";
        if (tok.hasMoreTokens()) {
            overrideCommand = tok.nextToken();
        }
        initialize(hourToTrigger, minuteToTrigger, durationMinutes, saveToDisk, osParameters, overrideCommand);
    }

    public String getPersistenceData(){
        StringBuffer buf = new StringBuffer();
        buf.append(hourToTrigger + "|");
        buf.append(minuteToTrigger + "|");
        buf.append(durationMinutes + "|");
        buf.append(osParameters + "|");
        buf.append(saveToDisk + "|");
        if (overrideCommand != null && !"null".equals(overrideCommand) && !overrideCommand.isEmpty()) {
            buf.append(overrideCommand + "|");
        }
        return new String(buf);
    }
    
    private void writeWakeupEventData() {
        try {
            BufferedWriter out = new BufferedWriter(new FileWriter(CaptureManager.dataPath + saveFileName));
            System.out.println(new Date() + " Writing wakeupEvent data to " + CaptureManager.dataPath + saveFileName);
            out.write(getPersistenceData() + "\n");
            out.flush(); out.close();
        } catch (IOException e) {
            System.out.println(new Date() + " ERROR: Failed to write wakeupEvent data to " + CaptureManager.dataPath + saveFileName + " " + e.getMessage());
        }
    }

    public static void removeWakeupEventDataFile() {
        try {
            File dataFile = new File(CaptureManager.dataPath + saveFileName);
            boolean ok = dataFile.delete();
            if (ok) System.out.println(new Date() + " Removed " + saveFileName);
        } catch (Exception e){
            System.out.println(new Date() + " Error removing " + saveFileName + " " + e.getMessage());
        }
    }

    public void run() {
        System.out.println(new Date() + " WakeupEvent is starting.");
        WakeupManager.clear(WakeupEvent.eventHandle, "WakeupEvent run");
        isRunning = true;
        WakeupEvent.runThread = Thread.currentThread(); 
        lastTriggerMs = new Date().getTime();
        try {Thread.sleep(500);} catch (Exception e){}
        System.out.println(new Date() + " Triggering WakeupEvent.");
        if (validOsParameters()){
            //DRS 20210728 - Added "for" around existing code, plus if/else
            for (int tries = 2; tries > 0; tries--) {
                try {
                    WakeupEvent.activeAutorun = true;
                    cl = new CwEpgCommandLine(getAutorunFile());
                    System.out.println(new Date() + " START Running CommandLine.  Thread: " + WakeupEvent.runThread);
                    boolean goodResult = cl.runProcess(); // blocks
                    System.out.println(new Date() + " END   Running CommandLine.  Thread: " + WakeupEvent.runThread);
                    if (!goodResult){
                        System.out.println(new Date() + " ERROR: WakeupEvent.trigger() failed to run command " + cl.getCommands() + "\n" + cl.getErrors() );
                    }
                } catch (Throwable t) {
                    System.out.println(new Date() +  " ERROR: Active autorun was interrupted. " + t.getMessage());
                } finally {
                    WakeupEvent.activeAutorun = false;
                }
                if (!cl.timedOut()) break;
                else System.out.println(new Date() + (tries==2?" Retrying after time-out.":"Failed after 2 tries."));
            }
        } else {
            try {
                WakeupEvent.activeAutorun = true;
                System.out.println(new Date() + " START Helper sleep.  Thread: " + WakeupEvent.runThread);
                Thread.sleep(getAutorunFile().getMaximumRunSeconds() * 1000);
                System.out.println(new Date() + " END   Helper sleep.  Thread: " + WakeupEvent.runThread);
            } catch (Throwable t) {
                System.out.println(new Date() +  " ERROR: Active autorun was interrupted. " + t.getMessage());
            } finally {
                WakeupEvent.activeAutorun = false;
            }
        }
        if(currentCommand != KILL) {
            logHardwareWakeupHandle("WakeupEvent run (" + COMMAND[currentCommand] + ").", WakeupEvent.eventHandle); 
            System.out.println(new Date() + " Doing a setWakeup.  Event is scheduled for tomorrow. " + COMMAND[currentCommand]);
            WakeupEvent.eventHandle = setWakeup("WakeupEvent run (" + COMMAND[currentCommand] + ")."); // DRS20161118 <<<<<<<<<<<<< setWakeup // DRS 20150524 - Commented the setWakeup as possible solution to Win10 napping.
            if (WakeupEvent.eventHandle != null) WakeupEvent.handleName = WakeupEvent.eventHandle.toString(); // DRS 20161119 - Added 1 - For logging when handle is null
        } else {
            System.out.println(new Date() + " Kill issued, so not doing a setWakeup.  No event is scheduled for tomorrow. " + COMMAND[currentCommand]);
        }
        System.out.println(new Date() + " WakeupEvent thread ended (" + COMMAND[currentCommand] + ").");
        isRunning = false;
        System.out.println(new Date() + " WakeupEvent is ending.");
        CaptureManager.requestInterrupt("WakeupEvent.run (ending)");
    }

    private boolean validOsParameters() {
        return osParameters != null && !osParameters.equals("") && !osParameters.equals("null");
    }

    public static boolean isRunning() {
        return isRunning;
    }

    public void interruptTimer(int command) {
        this.currentCommand = command;
        String commandLabel = "(undefined)";
        try {commandLabel = COMMAND[command];} catch (Throwable t){}
        String runThreadName = WakeupEvent.runThread!=null?WakeupEvent.runThread.getName():"(no runThread)";
        System.out.println(new Date() + " Interrupting WakeupEvent thread with command [" + commandLabel + "] runThread: " + runThreadName + " activeAutorun: " + activeAutorun );
        if (WakeupEvent.runThread != null && !WakeupEvent.activeAutorun) {
            WakeupEvent.runThread.interrupt();
            WakeupEvent.runThread = null; // DRS 20150522 added in case GC is what's causing Win10 an issue
        }
    }
    
    public static void logHardwareWakeupHandle(String who, Kernel32.HANDLE aHandle) {
        if(aHandle != null) {
            System.out.println(new Date() + " WakeupEvent.logHardwareWakeupEvent had handle " + aHandle);
        } else {
            System.out.println(new Date() + " WakeupEvent.logHardwareWakeupEvent had null handle.  Previous handle was " + WakeupEvent.handleName);
        }
    }

    public boolean isValid() {
        problems = super.getProblems();
        if (WakeupEvent.saveToDisk == null) problems.append("save to disk was not specified<br>");
        if (Integer.parseInt(this.durationMinutes) == 0) problems.append("duration minutes zero, no wakeups will occur.");
        return problems.length() == 0;
    }

    public static boolean hasCommand() {
        return osParameters != null && !osParameters.equals("") && !osParameters.equals("null");
    }

    public String getHtml() {
        isValid();
        StringBuffer buf = new StringBuffer("<table border=\"1\">\n");
        StringBuffer xmlBuf = new StringBuffer("\n<xml id=\"wakupevents\">\n");
        buf.append(
                "<tr><td>isValid:</td><td>" + (problems.length()==0) + "</td></tr>" + 
                "<tr><td>problems:</td><td>" + new String(problems) + "</td></tr>" + 
                super.getHtml() + 
                "<tr><td>osParameters:</td><td>" + osParameters + "</td></tr>" + 
                "<tr><td>saveToDisk:</td><td>" + saveToDisk + "</td></tr>" + 
                "<tr><td>overrideCommand:</td><td>" + overrideCommand + "</td></tr>" + 
                "\n");
        xmlBuf.append(
                "  <wakeupEvent "+ 
                "isValid=\"" + (problems.length() == 0) + "\" " + 
                "problems=\"" + new String(problems) + "\" " +
                super.getXml() + 
                "osParameters=\"" + osParameters + "\" " + 
                "saveToDisk=\"" + saveToDisk + "\" " + 
                "overrideCommand=\"" + overrideCommand + "\" " + 
                "/>\n");
        buf.append("</table>\n");
        xmlBuf.append("</xml>\n");
        return new String(buf) + new String(xmlBuf);
    }
    
    public String toString(){
        return super.toString() + " osParameters:" + osParameters + " problems:" + problems + " overrideCommand: + overrideCommand";
    }

    public static void main(String[] args) throws Exception {
        WakeupEvent event = WakeupEvent.getInstance();
        int sleepMinutes = 2;
        boolean testCancel = false;
        boolean testGetFromDisk = true;
        if (testGetFromDisk) {
            event = WakeupEvent.getInstanceFromDisk();
        } else {
            event.initialize("17","48","" + sleepMinutes ,"true", "", "");
        }
        System.out.println(wakeupEvent.getHtml());
        for(int i = 0; i < 500; i++){
            if (wakeupEvent.isDue()){
                System.out.println("MAIN: wakupEvent is starting.");
                new Thread(wakeupEvent).start();
                System.out.println("MAIN: wakupEvent is running.\nMAIN: We are sleeping for 65 seconds (then we will reset).");
                Thread.sleep(65000);
                System.out.println("MAIN: Timer being reset.  We should see that it's waiting for " + sleepMinutes + " once again.");
                wakeupEvent.interruptTimer(RESET);
                if (testCancel){
                    System.out.println("MAIN: Waiting in the main again.  This time for 10 seconds, then we will cancel.");
                    Thread.sleep(10000);
                    System.out.println("MAIN: Timer cancel start.");
                    wakeupEvent.interruptTimer(CANCEL);
                    System.out.println("MAIN: Timer cancel  end.");
                    System.out.println("MAIN: Waiting in the main again.  This time for 10 seconds, then main will end.");
                    Thread.sleep(10000);
                } else {
                    System.out.println("MAIN: Waiting in the main again.  This time for " + (sleepMinutes + 1 ) + " minutes (one minute longer so we test uninterrupted ending).");
                    Thread.sleep((sleepMinutes + 1 ) * 60 * 1000);
                    System.out.println("MAIN: Main ending (event should have expired before now).");
                }
                break;
            }
            else {
                try {
                    System.out.println("sleeping 3");
                    Thread.sleep(3000);
                } catch (Exception e) {}
            }
            
        }
        System.out.println(event.isValid());
    }

}
