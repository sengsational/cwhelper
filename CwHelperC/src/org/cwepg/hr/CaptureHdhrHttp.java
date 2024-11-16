package org.cwepg.hr;

import java.util.Date;
import java.util.List;
import java.util.StringTokenizer;

import org.cwepg.svc.HdhrCommandLine;
import org.cwepg.svc.TinyWebServer;

//DRS 20220707 - Added class - Manage http based captures
public class CaptureHdhrHttp extends CaptureHdhr implements Runnable {
    
    HttpRequest runningHttpRequest;
    private boolean interrupted;

    public CaptureHdhrHttp(Slot slot, Channel channelDigital) {
        super(slot, channelDigital);
    }
    
    public CaptureHdhrHttp(String line, Tuner tuner) {
        super(line, tuner);
    }
    
    @Override
    public void configureDevice() throws Exception {
        TunerHdhr tuner = (TunerHdhr)channel.tuner;
        killAfterSeconds = this.slot.getRemainingSeconds() + 120; // wait 2 minutes for normal end, and if not gone yet, kill it.
        int durationSeconds = this.slot.getRemainingSeconds() - 1; // Ending a second early helps keep the logs from being jumbled.
        String channel = "v" + this.channel.virtualChannel + "." + this.channel.virtualSubchannel;
        this.target.fixFileName(); //DRS 20241116 - Added 1 -  Http capture type fails if target file name exists #39 
        runningHttpRequest = new HttpRequest(tuner.number, tuner.ipAddressTuner, channel, durationSeconds, this.target.getFileNameOrWatch(), killAfterSeconds);
        if (!runningHttpRequest.checkAvailable()) throw new DeviceUnavailableException("The device is not available. " + runningHttpRequest);
    }
    
    @Override
    public void deConfigureDevice() throws Exception {
        markEventHandled();
        if (!this.target.isWatch()){
            System.out.println(new Date() + " Deconfigure device not required for http captures.");
        } else {
            System.out.println(new Date() + " Deconfigure device not required for http watches.");
        }
    }

    
    private String getMatchingResourceString(String liveStatusText, String channelNumber, String alphaDescription) {
        String[] resourceStrings = liveStatusText.split("\"}\"");
        for (String resourceString : resourceStrings) {
            if (resourceString.contains("VctNumber\":\"" + channelNumber) && resourceString.contains("VctName\":\"" + alphaDescription)) return resourceString;
        }
        return null;
    }

    @Override
    public void run(){
        try {
            boolean goodResult = false;
            boolean isWatch = this.target.isWatch();
            if (!isWatch){
                goodResult = runningHttpRequest.runProcess(); // blocks
                if (!interrupted && !goodResult) throw new Exception("failed to handle " + runningHttpRequest);
            } else {
                System.out.println(new Date() + " 'Watch' not implemented on http.");
            }
        } catch (Throwable e) {
            System.out.println("CaptureHdhrHttp.run method <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");
            System.out.println(new Date() + " CaptureHdhrHttp.run " + e.getMessage());
            System.err.println(new Date() + " CaptureHdhrHttp.run " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println(new Date() +  " CaptureHdhrHttp ending " + (interrupted?"after interrupt.":""));
    }

    @Override
    public void interrupt(){
        if (runningHttpRequest != null){
            if (this.target.isWatch()){
                System.out.println(new Date() + " Not removing watch event at end of scheduled event time.");
            } else {
                if (this.slot.getRemainingSeconds() > 20) {
                    System.out.println(new Date() + " Interrupt not at the end of recording for http capture.");
                    this.interrupted = true;
                    runningHttpRequest.interrupt();
                } else {
                    System.out.println(new Date() + " Interrupt at the end of recording for http capture.");
                    this.interrupted = true;
                    runningHttpRequest.interrupt();
                }
                removeWakeup();
            }
        }
    }
    
    @Override
    public void interruptWatch(){
        runningHttpRequest.interrupt();
        removeWakeup();
        removeIcon();
    }
    
    @Override
    public String getSignalQualityData() {
        TunerHdhr tuner = (TunerHdhr)channel.tuner;
        String liveStatusText = LineUpHdhr.getPage("http://" + tuner.ipAddressTuner + "/status.json", 3, true, false);
        String matchingResourceString = getMatchingResourceString(liveStatusText, this.channel.virtualChannel + "." + this.channel.virtualSubchannel, this.channel.alphaDescription);
        //System.out.println("matchingResourceString " + matchingResourceString);
        return matchingResourceString;
        /*
        "Resource":"tuner2",
        "VctNumber":"3.1",
        "VctName":"WBTV-DT",
        "Frequency":527000000,
        "SignalStrengthPercent":100,
        "SignalQualityPercent":91,
        "SymbolQualityPercent":100,
        "TargetIP":"192.168.3.176",
        "NetworkRate":4566880
         */
    }
    
    @Override
    public boolean extendSlot(int minutes) {
        if (checkExtendSlot(minutes)) {
            slot.adjustEndTimeMinutes(minutes);
            this.killAfterSeconds = this.killAfterSeconds += minutes * 60;
            runningHttpRequest.extendKillSeconds(minutes * 60);
            CaptureManager.requestInterrupt("extendSlot");
            return true;
        } else {
            System.out.println(new Date() + " Not able to schedule with [" + this.channel.getCleanedChannelName() + "] [" + this.channel.tuner.getFullName() + "] [" + this.channel.protocol +"]");
        }
        return false;
    }
    
    public static void main (String[] args) throws Exception {
        
        boolean testWithCaptureManager = true;
        if (testWithCaptureManager) {
            TinyWebServer.getInstance("8181").start(); 
            CaptureManager cm = CaptureManager.getInstance("C:\\my\\dev\\eclipsewrk\\CwHelper\\","C:\\my\\dev\\eclipsewrk\\CwHelper\\","C:\\my\\dev\\eclipsewrk\\CwHelper\\");
            TunerManager tm = TunerManager.getInstance();
            tm.loadChannelsFromFile(); // runs scanRefreshLineUpTm(), which runs LineupHdhr.scan()
            tm.printAllChannels();
            cm.run(); // blocks 
        }
        
        boolean testWithTunerOnly = false;
        if (testWithTunerOnly) {
            TunerManager tunMan = TunerManager.getInstance();
            boolean tunerOn = true;
            Tuner tuner0 = null;
            if (tunerOn){
                tunMan.countTuners();
                tuner0 = tunMan.getTuner("10A369BB-1");
            } else {
                tuner0 = new TunerHdhr("10A369BB", 1, true); 
            }
            
            boolean useExistingFile = true;
            String signalType = null; // doesn't work anyway
            int maxSeconds = 1000;
            tuner0.scanRefreshLineUp(useExistingFile, signalType, maxSeconds);
            System.out.println(tuner0.lineUp);
            
            //boolean forgetAboutTheRestOfIt = true;
            //if (forgetAboutTheRestOfIt) return;
    
            Slot slot = new Slot("7/2/2023 12:12", "2");
            String channelName = "103.1";
            String protocol = "8vsb";
            //String protocol = null;
            List captures = tunMan.getAvailableCapturesForChannelNameAndSlot(channelName, slot, protocol);
            System.out.println("CaptureHdhrHttp tunMan result " + tunMan.getLastReason());
            
            if (captures != null && captures.size() > 0){
                Capture capture = (Capture)captures.get(0); 
                capture.setTarget(new Target("c:\\temp.ts", "Some TV Program Title",null, null, "8vsb", Tuner.HDHR_TYPE));
                System.out.println(capture);
            } else if (captures.size() == 0) {
                System.out.println("getAvailableCapturesForChannelNameAndSlot(" + channelName + ", " + slot + ", " + protocol + ") returned no capture.");
            }
        }
            
    }

}
