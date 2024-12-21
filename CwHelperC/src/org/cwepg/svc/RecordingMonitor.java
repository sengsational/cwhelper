/*
 * Created on Jan 25, 2019
 *
 */
package org.cwepg.svc;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.cwepg.hr.Capture;
import org.cwepg.hr.CaptureHdhr;
import org.cwepg.hr.CaptureManager;
import org.cwepg.hr.Channel;
import org.cwepg.hr.Target;
import org.cwepg.hr.Tuner;
import org.cwepg.hr.TunerManager;

public class RecordingMonitor extends Thread implements Runnable {

    private StreamConverter myErrors;
    private CaptureHdhr capture;
    private int maxLoops;
    private float triggerThreshhold;
    private int loopSeconds;
    boolean debug = false;
    boolean running = true;
    private int maxMessages = 10; // no more than 10 routine messages in the log per recording
    private int loopsPerMessage;
    
    public RecordingMonitor(StreamConverter myErrors, int loopSeconds, CaptureHdhr capture, int maxSeconds, float hdhrBadRecordingPercent) {
        this.myErrors = myErrors;
        this.capture = capture;
        this.loopSeconds = loopSeconds;
        this.maxLoops = maxSeconds / loopSeconds;
        this.loopsPerMessage = maxLoops / maxMessages;
        this.triggerThreshhold = hdhrBadRecordingPercent / 100F;
        if (debug) System.out.println(new Date() + " loopSeconds: " + loopSeconds + " maxSeconds: " + maxSeconds + " maxMessages " + maxMessages +  " loopsPerMessage " + loopsPerMessage + " hdhdrBadRecordingPercent: " + hdhrBadRecordingPercent + " maxLoops: " + maxLoops + " triggerThreshhold: " + triggerThreshhold);
    }
    
    public void interruptLoop() {
        running = false; // falls out of run loop the next time the sleep ends.
        this.interrupt(); // DRS 20201031 - Added 1 - don't hang around until the next loop because capture can't end if this hangs around.
    }

    @Override
    public void run() {
        System.out.println(new Date() + " RecordMonitor.run() starting. <<<<<<<<<<<<<<<<<<<<<<< check " + maxLoops + " times during the program (every " + loopSeconds + " seconds).  Triggering level: " + triggerThreshhold);
        if (debug) System.out.println(new Date() + " Channel to monitor " + capture.getChannel());
        if (debug) System.out.println(new Date() + " Channel alternates " + capture.getChannel().getAlternatesString());
        OUT:
        for (int loopCount = 1;;loopCount++) {
            // Sleep for loopSeconds (should be 60 seconds or so, but Terry wants it smaller, like 20 seconds)
            try {Thread.sleep(loopSeconds * 1000);} catch (Exception e) {System.out.println(new Date() + " RecordingMonitor interrupted!");}
            if (!running) break;
            // Get out if the recording is done
            if (loopCount > maxLoops ) {
                break;
            }
            
            try {
                // See how many discontinuities there are
                String report = myErrors.getResults();
                //DRS 20241221 - Added 'if' - Issue #51
                if (report != null && report.contains("error")) {
                	String errorMessage = report.substring(report.indexOf("error"));
                	System.out.println(new Date() + " ERROR: hdhomerun_config.exe 'save' command threw an error: [" + errorMessage + "] (RecordingMonitor.run()).");
                	if (errorMessage.contains("error writing output")) {
                    	System.out.println(new Date() + " ERROR: hdhomerun_config.exe 'save' could not write to disk.  No replacement recording will be created.");
                		break OUT;
                	}
                }
                int reportLength = report.length();
                int nonDotCount = report.replace(".","").length();
                String durationStartOrFiveMinuteMessage = "since start";

                if (reportLength > 300) { // if report length is more than 5 minutes, use statistics for the last 5 minutes.
                    report = report.substring(reportLength - 300, reportLength);
                    reportLength = 300;
                    nonDotCount = report.replace(".","").length();
                    durationStartOrFiveMinuteMessage = "the last five minute";
                }

                if (debug) System.out.println(new Date() + " Checking errors interval: " + durationStartOrFiveMinuteMessage + " reportLength: " + reportLength);
                
                float severity = 0;
                
                if (((float)reportLength) > 0F) severity = ((float)nonDotCount)/((float)reportLength);
                
                if (debug || (loopCount%loopsPerMessage == 0)) {
                    if (nonDotCount > 0) System.out.println(new Date() + " There were " + nonDotCount + " errors resulting in " + durationStartOrFiveMinuteMessage + " severity " + severity + " as compared to " + this.triggerThreshhold);
                }
                
                
                // If the recording is bad, try to start another recording on the same channel, same time, but different tuner (if available)
                if (severity > this.triggerThreshhold) {
                    System.out.println(new Date() + " Poor quality recording has been detected.  Attempting to start another recording in place of " + this.capture + " There were " + nonDotCount + " errors resulting in " + durationStartOrFiveMinuteMessage + " severity " + severity + " as compared to " + this.triggerThreshhold);
                    TunerManager tunerManager = TunerManager.getInstance();
                    Channel channel = capture.getChannel();

                    //List<Capture> allPossibleCaptures = new ArrayList<Capture>();

                    // Build list of alternate captures.  
                    //List<Capture> allAlternateCaptures = new ArrayList<Capture>();
                    //Channel[] alternateChannels = channel.getAltChannelList();
                    //for (Channel alternateChannel : alternateChannels) {
                    //    List<Capture> alternateCaptures = tunerManager.getAvailableCapturesForChannelNameAndSlot(alternateChannel.getCleanedChannelName(), capture.getSlot(), capture.getChannelProtocol());
                    //    for (Capture alternateCapture : alternateCaptures) {
                    //        //allAlternateCaptures.add(alternateCapture);
                    //        allPossibleCaptures.add(alternateCapture);
                    //    }
                    //}
                    
                    // Build list of standard captures.  Typical.  Happens when more than one tuner can pull in a station.
                    //List<Capture> allStandardCaptures = tunerManager.getAvailableCapturesForChannelNameAndSlot(channel.getCleanedChannelName(), capture.getSlot(), capture.getChannelProtocol());
                    

                    //for (Capture capture : allAlternateCaptures) {
                    //    allPossibleCaptures.add(capture);
                    //}
                    
                    //for (Capture capture : allStandardCaptures) {
                    //    allPossibleCaptures.add(capture);
                    //}
                    
                    if (debug) System.out.println(new Date() + " channel " + channel);
                    
                    List<Capture> allPossibleCaptures = tunerManager.getAvailableCapturesAltChannelListAndSlot(channel.getAltChannelList(debug), capture.getSlot(), capture.getChannelProtocol(), debug);

                    boolean foundAlternative = false;
                    for (Capture capture : allPossibleCaptures) {
                        System.out.println(new Date() + " Trying to schedule a backup capture: " + capture);
                        Target target = new Target(this.capture.getFileName(), this.capture.getTitle(), "", "", this.capture.getChannelProtocol(), Tuner.HDHR_TYPE);
                        if (capture != null && target.getFileNameOrWatch() != null && target.isValid()) {
                            capture.setTarget(target);
                            CaptureManager.scheduleCapture(capture, true);
                            CaptureManager.requestInterrupt("RecordingMonitor.run"); // need to interrupt so it reads new schedule
                            foundAlternative = true;
                        } 
                        if (foundAlternative) {
                            System.out.println(new Date() + " A backup capture was scheduled. " + capture);
                            break OUT; // Only one backup recording can be spawned.
                        } else {
                            System.out.println(new Date() + " A backup capture not successful with:  " + capture);
                        }
                    }
                    if (!foundAlternative) {
                        System.out.println(new Date() + " No alternative tuners were found to allow a backup capture to begin. This capture will not get a backup: " + this.capture);
                        break OUT;
                    }
                }
            } catch (Throwable t) {
                System.out.println(new Date() + " ERROR: There was an error in RecordingMonitor.  Poor quality recordings will not spawn backup recordings. " + t.getMessage());
                break OUT;
            }
            
        } // end of loop
        System.out.println(new Date() + " RecordingMonitor.run() ending.  <<<<<<<<<<<<<<<<<<<<<<<<<<<");
    }

}
