/*
 * Created on Feb 7, 2010
 *
 */
package org.cwepg.svc;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.cwepg.hr.CaptureHdhr;
import org.cwepg.hr.CaptureManager;

public class CommandLine {

    protected ArrayList<String> cmds = new ArrayList<String>();
    private Process proc;
    private String errors;
    private String output;
    private boolean runFlag = true;
    private boolean procResult = false;
    protected boolean timedOut = false;
    protected int maxSeconds;

    public CaptureHdhr capture;
    public RecordingMonitor recordingMonitor;

    private int debug = 5;
    protected long startedAtMs = -1;
    boolean getOutputByByte = false;
    boolean hangProtection = false;
    CommandLineProcWrapper wrapper;
    private static final String QUOTE = "\"";
    
    public boolean runProcess() {
    	
    	// execute the command and route the errors and output
    	try {
    		proc = Runtime.getRuntime().exec((String[])cmds.toArray(new String[0]), null, null);
    		startedAtMs = new Date().getTime();
    	} catch (IOException e1) {
    		System.out.println(e1.getMessage());
    		return false;
    	}
    	StreamConverter myErrors = new StreamConverter(proc.getErrorStream(), "ERRORS", getOutputByByte);
    	StreamConverter myOutput = new StreamConverter(proc.getInputStream(), "OUTPUT", getOutputByByte);
        Thread errorThread = new Thread(myErrors, "Thread-ErrorRedirect"); errorThread.start();
        Thread outputThread = new Thread(myOutput, "Thread-OutputRedirect"); outputThread.start();
        System.out.println(new Date() + " CommandLine started with timeout at " + maxSeconds + " seconds."); //DRS 20150716 - Gets Here
        
        if (capture != null) { // if capture is not set, that signals we won't do this recordingMonitor
            int secondsBetweenChecking = CaptureManager.hdhrRecordMonitorSeconds; 
            if (secondsBetweenChecking < 1) secondsBetweenChecking = 1;
            recordingMonitor = new RecordingMonitor(myErrors, secondsBetweenChecking, capture, maxSeconds, CaptureManager.hdhrBadRecordingPercent);
            recordingMonitor.start();
        }
        
    	for (int i = 0;;i++){
    		if (i > maxSeconds || !runFlag) {
    		    if (debug > 4) System.out.println(new Date() + " i:" + i + " runFlag:" + runFlag );
                if (i > maxSeconds){
                    timedOut = true;
                    System.out.println(new Date() + " WARNING: CommandLine timed-out after " + this.maxSeconds + " seconds.");
                }
                break; 
            }
    		try {
                try {Thread.sleep(500);} catch (InterruptedException ee){}
                if (!hangProtection) { // The traditional way that has worked
                    if (debug > 5) System.out.println(new Date() + " checking exitValue.");
                    proc.exitValue(); // Throws IllegalStateExeption or if done, continues
                    procResult = true;
                    break; // if we hit this statement, the process is no longer running
                } else { // the new way that is needed for the setting of the PC clock
                    if (debug > 5) System.out.println(new Date() + " checking exitValue..");
                    wrapper = new CommandLineProcWrapper(proc, debug);
                    wrapper.run(); // does not block
                    try {Thread.sleep(250);} catch (Exception e){}
                    wrapper.exitValue(); // Throws IllegalStateExeption or if done, continues
                    break; // if we hit this statement, the process is no longer running
                }
                
            } catch (IllegalThreadStateException e){
                try {Thread.sleep(500);} catch (InterruptedException ee){if (debug > 4) System.out.println(new Date() + " command  " + this.getCommands() + " not done yet.");} // runs from proc.exitValue() if process is not done yet.
            }
            
    	}
        errors = myErrors.getResults();
        output = myOutput.getResults();
        errorThread.interrupt();
        outputThread.interrupt();
        if (!hangProtection) {
            proc.destroy();
        } else if (wrapper != null) {
            if (wrapper.isStuck()) {
                if (debug > 4) System.out.println("Detected stuck process.");
                wrapper.interrupt();
            }
            else {
                if (debug > 4) System.out.println("Stopping process.");
                wrapper.stopProcess();
                try {Thread.sleep(100);} catch (Exception e){}
                if (wrapper.isStuck()) {
                    if (debug > 4) System.out.println("Interrupting process.");
                    wrapper.interrupt();
                }
            }
            procResult = !wrapper.isStuck();
        }
    	return procResult;
    }

    public String getErrors() {
    	return errors;
    }
    
    public boolean timedOut() {
        return timedOut;
    }

    public void interrupt() {
        if (debug > 2) {
            System.out.println(new Date() + " interrupt requested ");
            StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
            System.out.println(stackTraceElements[1]);
            System.out.println(stackTraceElements[2]);
            System.out.println(stackTraceElements[3]);
        }
        this.runFlag = false;
        this.procResult = true;
        if (this.recordingMonitor != null) {  // DRS 20190208 - Stop monitoring if it's gone
            recordingMonitor.interruptLoop();
        }
    }
    
    public void setDebug(int debug){
        this.debug = debug;
    }

    public String getOutput() {
    	return output;
    }

    public String getCommands() {
        StringBuffer buf = new StringBuffer();
        for (int i = 0; i < this.cmds.size(); i++) {
            buf.append(cmds.get(i) + " ");
        }
        return new String(buf);
    }
    
    public String getRemainingMinutes() {
        return ((new Date().getTime() - startedAtMs)/1000/60) + "";
    }

    @SuppressWarnings("unused")
    private static void printArray(List list) {
        System.out.println("\nlist start --");
        for (Iterator iter = list.iterator(); iter.hasNext();) {
            System.out.println(iter.next());
        }
        System.out.println("list end --/n");
    }

    public void extendKillSeconds(int secondsToExtend) {
        this.maxSeconds += secondsToExtend;
        System.out.println(new Date() + " CommandLine timeout extended by " + secondsToExtend + " seconds to " + this.maxSeconds);
    }
    
    public void setHangProtection(boolean hangProtection) {
        this.hangProtection = hangProtection;
    }
    
    protected String getQuoted(String content, String postfix) {
        return QUOTE + content + QUOTE + postfix;
    }
}
