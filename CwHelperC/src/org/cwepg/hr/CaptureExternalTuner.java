/*
 * Created on Jun 18, 2011
 *
 */
package org.cwepg.hr;

import java.util.Date;

import org.cwepg.svc.ExternalTunerCommandLine;

public class CaptureExternalTuner extends CaptureHdhr {
    ExternalTunerCommandLine runningCommandLine;

    // used for scheduling new captures
    public CaptureExternalTuner(Slot slot, Channel channel) {
        super(slot, channel);
    }

    // used by getCapturesFromFile (persistance)
    public CaptureExternalTuner(String l, TunerExternal tuner) {
        super(l,tuner);
    }

    public void configureDevice() throws Exception {
        TunerExternal tuner = (TunerExternal)channel.tuner;
        try {
            String[] setChannel = tuner.getSetChannelCommandArray(((ChannelDigital)channel).getFirstRf());
            ExternalTunerCommandLine cl = new ExternalTunerCommandLine(setChannel, 5, false);
            boolean goodResult = cl.runProcess();
            if (!goodResult) throw new Exception("failed to handle " + cl.getCommands());
            report("setChannel", cl);
        } catch (Throwable e){
            System.out.println("CaptureExternalTuner.configureDevice method <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");
            System.out.println(new Date() + " CaptureExternalTuner.configureDevice " + e.getMessage());
            System.err.println(new Date() + " CaptureExternalTuner.configureDevice " + e.getMessage());
            e.printStackTrace();
        }
        try {
            IniWriter iniWriter = new IniWriter(this);
            iniWriter.writeCapture(tuner);
        } catch (Exception e){
            System.out.println(new Date() + " Did not write CapDVHS.ini: " + e.getMessage());
        }
    }

    public String getCapDvhsReserveString() {
        String returnValue = this.slot.getFormattedStartTimeString() + this.slot.getFormattedStopTimeString() + "0001" + Math.random();
        try {
            return this.slot.getFormattedStartTimeString() + this.slot.getFormattedStopTimeString() + "0001" + Target.getNoPathFilename(this.target.fileName);            
        } catch (Throwable t){
            return returnValue; // because title is not required.
        }
    }

    public void run(){
        try {
            boolean goodResult = false;
            TunerExternal tuner = (TunerExternal)channel.tuner;
            if (tuner.needsClippedCaptures){ // the type that needs clipped captures also needs a delay
                System.out.println(new Date() + " CaptureExternal.run() waiting 2 seconds before proceeding. " + CaptureManager.shortenExternalRecordingsSeconds);
                try {Thread.sleep(2000);} catch (Exception e){}
            }
            // the slot has a shortened end-time, but we need to extend the kill to first let the exe finish
            int killAfterSeconds = this.slot.getRemainingSeconds() + (CaptureManager.shortenExternalRecordingsSeconds/2);
            String commandName = null;
            
            if (this.target.isWatch()){
                System.out.println(new Date() + " ERROR: Watch events not supported on this tuner type.");
            } else {
                commandName = "startCapture";
                //this.target.mkdirsAndTestWrite(false, this.target.getFileNameOrWatch(), 20);
                this.target.fixFileName();
                String[] startCapture = tuner.getStartCaptureCommandArray(this.target.fileName, this.slot.getRemainingSeconds());
                runningCommandLine = new ExternalTunerCommandLine(startCapture, killAfterSeconds, false); //
            }
            goodResult = runningCommandLine.runProcess(); // blocks while command runs
            if (!goodResult) throw new Exception("Command line did not return nicely.  Command underway at the time: " + runningCommandLine.getCommands());
            report(commandName, runningCommandLine);
        } catch (Throwable e) {
            System.out.println("CaptureExternalTuner.run method <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");
            System.out.println(new Date() + " CaptureExternalTuner.run " + e.getMessage());
            System.err.println(new Date() + " CaptureExternalTuner.run " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void report(String string, ExternalTunerCommandLine cl) {
        String clResult = report(cl);
        if (clResult != null){
            System.out.println(string + " said: [" + clResult + "]");
        } else {
            System.out.println(string + " said: []");
        }
    }
    
    private String report(ExternalTunerCommandLine cl){
        String clResult = cl.getOutput();
        if (clResult != null && clResult.trim().length() > 0){
            return clResult.trim();
        }
        return null;
    }

    public void interrupt(){
        if (this.target.isWatch()){
            System.out.println(new Date() + " Not removing watch event at end of scheduled event time.");
        } else {
            removeWakeup();
            removeIcon();
        }
    }
    public void interruptWatch(){
        removeWakeup();
        removeIcon();
    }
    @Override
    public int getTunerType() {
        return Tuner.EXTERNAL_TYPE;
    }

    //////DISABLED OVERRIDES///////////////
    @Override
    public void deConfigureDevice() throws Exception {
        // Nothing to do here
    }
    @Override
    public String getSignalQualityData() {
        return null; // no data in this tuner type
    }
    @Override
    public int getNonDotCount() {
        return 0; // no data in this tuner type
    }
    @Override
    public boolean addIcon() {
        return false; // not adding icon for this tuner type
    }
    @Override
    public boolean removeIcon() {
        return false; // not adding icon for this tuner type
    }

}
