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

public class CommandLine {

    protected ArrayList<String> cmds = new ArrayList<String>();
    private Process proc;
    private String errors;
    private String output;
    private boolean runFlag = true;
    private boolean procResult = false;
    protected boolean timedOut = false;
    protected int maxSeconds;
    private int debug = 0;
    protected long startedAtMs = -1;
    boolean getOutputByByte = false;
    

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
        Thread errorThread = new Thread(myErrors); errorThread.start();
        Thread outputThread = new Thread(myOutput); outputThread.start();
        System.out.println(new Date() + " CommandLine started with timeout at " + maxSeconds + " seconds."); //DRS 20150716 - Gets Here
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
                if (debug > 4) System.out.println(new Date() + " checking exitValue.");
    			proc.exitValue();
    			procResult = true;
    			break; // if we hit this statement, the process is no longer running
            } catch (IllegalThreadStateException e){
                try {Thread.sleep(500);} catch (InterruptedException ee){if (debug > 4) System.out.println(new Date() + " command  " + this.getCommands() + " not done yet.");} // runs from proc.exitValue() if process is not done yet.
            }
            
    	}
        errors = myErrors.getResults();
        output = myOutput.getResults();
        errorThread.interrupt();
        outputThread.interrupt();
        proc.destroy();
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

}
