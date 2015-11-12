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
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Properties;
import java.util.StringTokenizer;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.cwepg.svc.AutorunFile;
import org.cwepg.svc.ClockChecker;
import org.cwepg.svc.CwEpgCommandLine;
import org.cwepg.svc.Kernel32;
import org.cwepg.svc.TaskCreateCommandLine;

public class WakeupEvent extends TimedEvent implements Runnable {

    static StringBuffer problems;
    
    static WakeupEvent wakeupEvent;

    static String saveToDisk = "true";
    static String osCommand = "null";

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

        // Set wakeup method migration variable or return null if new OS and no file
        boolean newOsWithUnmigratedEventFile = false;
        boolean oldFileExists = new File(fileName).exists();
        boolean newFileExists = AutorunFile.fileExists();
        if (!CaptureManager.usingOldOs) {
            if (oldFileExists && !newFileExists) {
                newOsWithUnmigratedEventFile = true;
            } else {
                return null; // DO NOT USE WakeupEvent on new OS versions
            }
        }

        // Pull from disk for old OS or if we need to migrate
        if (CaptureManager.usingOldOs || newOsWithUnmigratedEventFile) {
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
        }       

        // Run once when this version of the application is run on a new OS the first time
        if (newOsWithUnmigratedEventFile && wakeupEvent!=null) {
            boolean isMaster = (osCommand != null && !osCommand.equals("") && !osCommand.equals("null"));
            boolean goodWrite = new AutorunFile(wakeupEvent.hourToTrigger, wakeupEvent.minuteToTrigger, wakeupEvent.durationMinutes, wakeupEvent.osCommand, isMaster, 0).saveToDisk(CaptureManager.dataPath + "Autorun.xml");
            if (goodWrite) {
                String[] create = {"/create", "/f", "/tn", "CWEPG_Autorun_Task", "/xml", CaptureManager.dataPath + "Autorun.xml"};  
                TaskCreateCommandLine tcclc = new TaskCreateCommandLine(create);
                boolean goodResult = tcclc.runProcess();
                if (goodResult) {
                    removeWakeupEventDataFile();
                    wakeupEvent = null;
                    System.out.println(new Date() + " Migrated autorun function to use Windows task.");
                } else {
                    System.out.println(new Date() + " ERROR: Failed to migrate autorun function to use Windows task. Still using original autorun scheme. \nFailed command: " + tcclc.getCommands() + "\nOutput: " + tcclc.getOutput() + "\nErrors: " + tcclc.getErrors());
                }
            } else {
                System.out.println(new Date() + " ERROR: Could not save the AutorunFile to disk.");
            }
        } else if (newOsWithUnmigratedEventFile && wakeupEvent == null) {
            System.out.println(new Date() + " ERROR: WakeUpEvent file exists, but can not get data for migrate to autorun function.  No autorun will happen. User action: use CW_EPG Automatic Settings tab and click Save Settings.");
        }
        
        return wakeupEvent;
    }
    
    public void initialize(String hourToSend, String minuteToSend, String duration, String saveToDisk, String osCommand) {
        super.initialize(hourToSend, minuteToSend, duration);
        if (osCommand != null && !osCommand.equals("")){
            WakeupEvent.osCommand = osCommand;
        }
        if (isValid()){
            this.setWakeup("WakeupEvent initialize for " + osCommand);
            if (WakeupEvent.saveToDisk.toUpperCase().equals("TRUE")) writeWakeupEventData();
            super.removePreviousHardwareWakeupEvent("TimedEvent initialize() " + hourToSend +":" + minuteToSend); // DRS 20150524 Delete old handle if there is one
        }
    }
    
    public void initialize(String persistenceData){
        StringTokenizer tok = new StringTokenizer(persistenceData, "|");
        String hourToTrigger = tok.nextToken(); 
        String minuteToTrigger = tok.nextToken(); 
        String durationMinutes = tok.nextToken(); 
        String osCommand = tok.nextToken(); 
        String saveToDisk = tok.nextToken();  
        initialize(hourToTrigger, minuteToTrigger, durationMinutes, saveToDisk, osCommand);
    }

    public String getPersistenceData(){
        StringBuffer buf = new StringBuffer();
        buf.append(hourToTrigger + "|");
        buf.append(minuteToTrigger + "|");
        buf.append(durationMinutes + "|");
        buf.append(osCommand + "|");
        buf.append(saveToDisk + "|");
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
        //if (isRunning) {
        //    System.out.println(new Date() + " Only one WakeupEvent at a time is allowed.");
        //    System.err.println(new Date() + " Only one WakeupEvent at a time is allowed.");
        //    Thread.currentThread().getStackTrace();
        //   return;
        //}
        System.out.println(new Date() + " WakeupEvent is starting.");
        isRunning = true;
        WakeupEvent.runThread = Thread.currentThread(); 
        lastTriggerMs = new Date().getTime();
        try {Thread.sleep(500);} catch (Exception e){}
        System.out.println(new Date() + " Triggering WakeupEvent.");
        WakeupManager.preventSleep();
        if (osCommand != null && !osCommand.equals("") && !osCommand.equals("null")){
            int durationSeconds = Integer.parseInt(this.durationMinutes) * 60;
            cl = new CwEpgCommandLine(WakeupEvent.osCommand, durationSeconds);
            boolean goodResult = cl.runProcess(); // blocks
            if (!goodResult){
                System.out.println(new Date() + " ERROR: WakeupEvent.trigger() failed to run command " + cl.getCommands() + "\n" + cl.getErrors() );
            }
        } else {
            System.out.println(new Date() + " WakeupEvent will prevent sleep for " + durationMinutes + " minutes after the last web command.");
            do {
                try {
                    int minutes = Integer.parseInt(durationMinutes);
                    if (debug > 4) System.out.println(new Date() + " WakeupEvent is sleeping for " + durationMinutes + " minutes.");
                    Thread.sleep(minutes * 60 * 1000);
                    currentCommand = DONE;
                    System.out.println(new Date() + " WakeupEvent.run() sleep ended.");
                } catch (InterruptedException e) {
                    if (debug > 4) System.out.println(new Date() + " WakeupEvent.run() interupted.");
                }
            } while(currentCommand == RESET);
        }
        WakeupManager.allowSleep();
        if(currentCommand != KILL) {
            setWakeup("WakeupEvent run (" + COMMAND[currentCommand] + ")."); // DRS 20150524 - Commented the setWakeup as possible solution to Win10 napping.
            System.out.println(new Date() + " Doing a setWakeup.  Event is scheduled for tomorrow. " + COMMAND[currentCommand]);
        } else {
            System.out.println(new Date() + " Kill issued, so not doing a setWakeup.  No event is scheduled for tomorrow. " + COMMAND[currentCommand]);
        }
        System.out.println(new Date() + " WakeupEvent thread ended (" + COMMAND[currentCommand] + ").");
        isRunning = false;
        System.out.println(new Date() + " WakeupEvent is ending.");
        CaptureManager.requestInterrupt("WakeupEvent.run");
    }

    public boolean isRunning() {
        return isRunning;
    }

    public void interruptTimer(int command) {
        this.currentCommand = command;
        if (WakeupEvent.runThread != null) {
            WakeupEvent.runThread.interrupt();
            WakeupEvent.runThread = null; // DRS 20150522 added in case GC is what's causing Win10 an issue
        }
    }
    
    public boolean isValid() {
        problems = super.getProblems();
        if (WakeupEvent.saveToDisk == null) problems.append("save to disk was not specified<br>");
        if (Integer.parseInt(this.durationMinutes) == 0) problems.append("duration minutes zero, no wakeups will occur.");
        return problems.length() == 0;
    }

    public static boolean hasCommand() {
        return osCommand != null && !osCommand.equals("") && !osCommand.equals("null");
    }

    public String getHtml() {
        isValid();
        StringBuffer buf = new StringBuffer("<table border=\"1\">\n");
        StringBuffer xmlBuf = new StringBuffer("\n<xml id=\"wakupevents\">\n");
        buf.append(
                "<tr><td>isValid:</td><td>" + (problems.length()==0) + "</td></tr>" + 
                "<tr><td>problems:</td><td>" + new String(problems) + "</td></tr>" + 
                super.getHtml() + 
                "<tr><td>osCommand:</td><td>" + osCommand + "</td></tr>" + 
                "<tr><td>saveToDisk:</td><td>" + saveToDisk + "</td></tr>" + 
                "\n");
        xmlBuf.append(
                "  <wakeupEvent "+ 
                "isValid=\"" + (problems.length() == 0) + "\" " + 
                "problems=\"" + new String(problems) + "\" " +
                super.getXml() + 
                "osCommand=\"" + osCommand + "\" " + 
                "saveToDisk=\"" + saveToDisk + "\" " + 
                "/>\n");
        buf.append("</table>\n");
        xmlBuf.append("</xml>\n");
        return new String(buf) + new String(xmlBuf);
    }
    
    public String toString(){
        return super.toString() + " osCommand:" + osCommand + " problems:" + problems;
    }

    public static void main(String[] args) throws Exception {
        WakeupEvent event = WakeupEvent.getInstance();
        int sleepMinutes = 2;
        boolean testCancel = true;
        event.initialize("17","48","" + sleepMinutes ,"true", "");
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
