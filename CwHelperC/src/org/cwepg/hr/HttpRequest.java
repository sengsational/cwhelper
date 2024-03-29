/*
 * Created on Jul 2, 2022
 *
 */
package org.cwepg.hr;

import java.util.Date;

import org.cwepg.svc.RecordingMonitor;

public class HttpRequest {

    private HttpProcess proc;
    private String errors;
    private boolean runFlag = true;
    private boolean procResult = false;
    protected boolean timedOut = false;
    protected int maxSeconds;
    
    private int debug = 0;
 
    protected long startedAtMs = -1;
    
    private int tunerNumber;
    private String ipAddress;
    private String channelKey;
    private int durationSeconds;
    private String fileName;

    public HttpRequest(int tunerNumber, String ipAddress, String channelKey, int durationSeconds, String fileName, int maxSeconds) {
        this.tunerNumber = tunerNumber;
        this.ipAddress = ipAddress;
        this.channelKey = channelKey;
        this.durationSeconds = durationSeconds;
        this.fileName = fileName;
        this.maxSeconds = maxSeconds;
    }
    
    public boolean checkAvailable() {
        proc = new HttpProcess(tunerNumber, ipAddress, channelKey, 0, fileName);
        return proc.checkAvailable();
    }

    public boolean runProcess() {
        proc = new HttpProcess(tunerNumber, ipAddress, channelKey, durationSeconds, fileName);
        Thread processThread = new Thread(proc, "Thread-" + ipAddress + "-Tuner" + tunerNumber + " " + fileName);
        startedAtMs = new Date().getTime();
        processThread.start(); // does not block
        
        for (int i = 0;;i++){
            if (i > maxSeconds || !runFlag) {
                if (debug > 4) System.out.println(new Date() + " i:" + i + " runFlag:" + runFlag );
                if (i > maxSeconds){
                    timedOut = true;
                    System.out.println(new Date() + " WARNING: HttpRequest timed-out after " + this.maxSeconds + " seconds.");
                }
                break; 
            }
            // This try/catch is a one second sleep that breaks out of the for loop if the process has ended.
            try {
                Thread.sleep(1000);
                if (proc.ended()) break;
            } catch (InterruptedException e) {
                System.out.println(new Date() + " Process interrupted. ");
            }
        }
        proc.destroy();
        return proc.endStatus();
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
    }
    
    public void setDebug(int debug){
        this.debug = debug;
    }

    public String getRemainingMinutes() {
        return ((new Date().getTime() - startedAtMs)/1000/60) + "";
    }

    void extendKillSeconds(int secondsToExtend) {
        this.maxSeconds += secondsToExtend;
        proc.extendDurationSeconds(secondsToExtend);
        System.out.println(new Date() + " HttpRequest timeout extended by " + secondsToExtend + " seconds to " + this.maxSeconds);
    }
    
    public void extendCommandKillDuration(int extendMinutes) {
        extendKillSeconds(extendMinutes * 60);
    }
    
    public String toString() {
        return this.ipAddress + " tuner" + this.tunerNumber + " " + this.channelKey + " " + this.fileName;
    }

}
