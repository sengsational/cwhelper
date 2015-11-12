package org.cwepg.hr;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintStream;
import java.io.Reader;
import java.io.Writer;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import org.cwepg.svc.ClockChecker;
import org.cwepg.svc.SplashScreenCloser;
import org.cwepg.svc.TinyWebServer;

public class CaptureManager implements Runnable { //, ServiceStatusHandler { //DRS 20080822 Comment Service Specific Code
    public static final String version = "4,3,0,656"; // 656 Uploaded 11/10/2015, last checked in as 256.
    public static final String propertiesFileName = "CaptureManagerSettings.txt";
	public static final long LEAD_TIME_MS = 2000;
    
    private static CaptureManager captureManager;
    private static TunerManager tunerManager;
    private static CaptureDataManager captureDataManager;
    private static TinyWebServer webServer;
    private static ClockChecker clockChecker;
    private static Emailer emailer;
    private static WakeupEvent wakeupEvent;
    private static boolean wakeupEventMessageDone = false;
    public static String WEB_SERVER_PORT = "8181";
    
    static boolean runFlag = true;
    static boolean running = false;
    static boolean sleeping = false;
    static final int SIMULATE_START = CaptureHdhr.START + 100;
    static final int SIMULATE_END = CaptureHdhr.END + 100;
    static final String[] interrupterList = new String[10];
    public static final boolean usingOldOs = getWindowsVersion(false) < 7F; // old OS is defined as before Windows 7

    public static Thread runThread;
    public static String dataPath = "";
    public static String hdhrPath = "";
    public static String cwepgPath = "";

    /* Persistent Settings */
    public static int fusionLeadTime = 120; // start Fusion recordings 120 seconds from 'now' or they won't start
    public static int leadTimeSeconds = 90; // number of seconds to subtract from the wait duration. This allows the set() method to be set  in accordance with the start time of the recording and not worry about adjusting for startup delay.
    public static boolean isSleepManaged = false;
    public static boolean endFusionWatchEvents = false;
    public static boolean simulate = false;
    public static int shortenExternalRecordingsSeconds=15;
    
    HashSet<Capture> activeCaptures = new HashSet<Capture>(); // So we can nix them if the service ends
    
    private CaptureManager(){
        //ServiceStatusManager.setServiceStatusHandler(this); //DRS 20080822 Comment Service Specific Code
        loadSettings();
        Arrays.fill(interrupterList,"");
    }
    
    public static CaptureManager getInstance(){
        if (captureManager == null){
            captureDataManager = CaptureDataManager.getInstance();
            captureManager = new CaptureManager();
            tunerManager = TunerManager.getInstance();
            if (tunerManager.noTuners()) tunerManager.countTuners();
            emailer = Emailer.getInstanceFromDisk();
            wakeupEvent = WakeupEvent.getInstanceFromDisk();
        } 
        return captureManager;
    }
    
    public static CaptureManager getInstance(String cwepgPath, String hdhrPath, String dataPath){
        if (dataPath != null && dataPath.endsWith("\\\\")) dataPath = dataPath.substring(0,dataPath.length()-1);
        if (hdhrPath != null && hdhrPath.endsWith("\\\\")) hdhrPath = hdhrPath.substring(0,hdhrPath.length()-1);
        if (cwepgPath != null && cwepgPath.endsWith("\\\\")) cwepgPath = cwepgPath.substring(0,cwepgPath.length()-1);
        CaptureManager.dataPath = dataPath;
        CaptureManager.hdhrPath = hdhrPath;
        CaptureManager.cwepgPath = cwepgPath;
        CaptureManager.getInstance();
        return captureManager;
    }

    ///////////////
    // MAIN LOOP //
    ///////////////
    public void run(){
    	System.out.println("CaptureManager.run() starting (version " + version + ")");
    	CaptureManager.running = true;
        CaptureManager.runThread = Thread.currentThread();
        CaptureManager.webServer = new TinyWebServer(WEB_SERVER_PORT); // starts itself
    	CaptureManager.clockChecker = ClockChecker.getInstance(); //starts itself
        if (!startedOk(CaptureManager.webServer)) runFlag = false;  // we quit if the web server does not start - prevents multiple copies of this from running 
    	for (;;){
			if (!runFlag) break;
            System.out.println("-----------------------------------------");
	    	long msUntilWake = ((getMsUntilNextEvent() - LEAD_TIME_MS));
	    	Date wakeup = new Date(new Date().getTime() + msUntilWake);
	    	try {
	    		if (msUntilWake > 0){
			    	System.out.println(new Date() + " sleeping until " + wakeup + " (" + msUntilWake / 1000 / 60 + " minutes).");
                    
                    // DRS 20121003 - Added 'if' block to shorten msUntilWake for long leadTimeSeconds
                    int shortenedBySeconds = 0;
                    if (leadTimeSeconds > 90){
                        System.out.println(new Date() + " leadTimeSeconds was " + leadTimeSeconds);
                        int extendedLeadTimeSeconds = leadTimeSeconds - 90;
                        if (extendedLeadTimeSeconds > 10 && (msUntilWake - (extendedLeadTimeSeconds * 1000)) > 10000){
                            msUntilWake = msUntilWake - (extendedLeadTimeSeconds * 1000);
                            shortenedBySeconds = extendedLeadTimeSeconds;
                        } else {
                            System.out.println(new Date() + " no pre wake up because leadTimeSeconds minus 90 was only " + extendedLeadTimeSeconds);    
                            System.out.println(new Date() + " AND");    
                            System.out.println(new Date() + " msUntilWake minus extendedLeadTimeSeconds*1000 was less than 10000 " + (msUntilWake - (extendedLeadTimeSeconds * 1000)));    
                        }
                    } else {
                        //System.out.println(new Date() + " no pre wake up because leadTimeSeconds was " + leadTimeSeconds);
                    }

                    sleeping = true;
					Thread.sleep(msUntilWake);
                    
                    // DRS 20121003 - Added 'if' block to shorten msUntilWake for long leadTimeSeconds
                    if (shortenedBySeconds > 0){
                        System.out.println(new Date() + " shortenedBySeconds was " + shortenedBySeconds + " so preventing sleep now.");                        
                        if (isSleepManaged){
                            WakeupManager.preventSleep();
                            System.out.println(new Date() + " preventSleep() has been issued due to long lead time.");                        
                        } else {
                            System.out.println(new Date() + " preventSleep() has been NOT been issued since isSleepManaged was " + isSleepManaged + ".");                        
                        }
                        Thread.sleep(shortenedBySeconds * 1000);
                        if (isSleepManaged) {
                            System.out.println(new Date() + " allowSleep() has been issued due to long lead time.");                        
                            WakeupManager.allowSleep();
                        }
                    }
					System.out.println(new Date() + " CaptureManager.run() sleep ended.");
	    		}
			} catch (InterruptedException e) {
				System.out.println(new Date() + " CaptureManager.run() resetting sleep time. Interupted by:" + CaptureManager.interrupterList[0]);
				//try {Thread.sleep(1000);} catch (Exception ee){} ///////////////////////////////////////////// DRS 20150911 - separate functionality in time.  Debug only.
			}
            sleeping = false;

            if (wakeupEvent != null && wakeupEvent.isValid() && wakeupEvent.isDue() && !wakeupEvent.isRunning()){
                new Thread(wakeupEvent).start();
                try {Thread.sleep(100);} catch (Exception e){}; // prevent looping if this is still be due to run a few ms later
            }
            if (emailer != null && emailer.isValid() && emailer.isDue()) emailer.send();
            
	    	for (Iterator iter = tunerManager.iterator(); iter.hasNext() && runFlag;) {
				Tuner tuner = (Tuner) iter.next();
                tuner.refreshCapturesFromOwningStore(false);
                Capture capture = tuner.getCaptureWithNextEventInLeadTime(LEAD_TIME_MS);

                if (capture != null){
					
                    int nextEvent = capture.getNextEvent();
					
					// IF SIMULATE TRUE, WE DO NOT ACTUALLY SEND COMMANDS TO THE HDHR
					if (simulate) nextEvent += 100;
					
                    switch (nextEvent) {
                    case Capture.START:
                        boolean successful = true;
                        try {
                            capture.target.setNextAvailablePort();
                            capture.configureDevice();
                            if (!capture.target.isWatch() && capture.target.mkdirsAndTestWrite(false, capture.target.fileName, 20) == false) throw new Exception (new Date() + " ERROR: The target directory of file [" + capture.target.getFileNameOrWatch() + "] was not writable.\n");
                            new Thread(capture).start(); // <<======================
                            System.out.println(new Date() + " DEBUG: Returned to main thread.");
                            if (isSleepManaged) WakeupManager.preventSleep();
                            else System.out.println(new Date() + " not sleep managed.");
                            Thread.sleep(1000); //So log will appear in the "right" order
                            capture.addIcon();
                            activeCaptures.add(capture);
                            System.out.println(new Date() + " There is/are " + activeCaptures.size() + " active capture after adding " + capture.getTitle());
                            System.out.println(new Date() + " Handled START event for " + tuner.id + "-" + tuner.number + " " + capture.channel.channelKey + " " + capture.slot);
                            System.out.println(new Date() + " The file [" + capture.target.fileName +"] " + (new File(capture.target.fileName).exists()?"has been created.":"has NOT BEEN CREATED!"));
                        } catch (Exception e1) {
                            successful = false;
                            System.out.println(new Date() + " Could not start capture! " + e1.getMessage());
                            System.err.println(new Date() + " Could not start capture! " + e1.getMessage());
                            e1.printStackTrace();
                        }
                        try {
                            if (successful) new CaptureDetails(capture).insertCaptureStartEvent();
                        } catch (Throwable t){
                            System.out.println(new Date() + " Could not save capture details! " + t.getMessage());
                            System.err.println(new Date() + " Could not save capture details! " + t.getMessage());
                            t.printStackTrace();
                        }
                        capture.markEventHandled();
                        if (!successful) capture.slot.adjustEndTimeSeconds(-capture.slot.getRemainingSeconds());
                        
                        break;
                    case Capture.END:
                        try {
                            boolean needsInterrupt = false;
                            removeActiveCapture(capture, needsInterrupt);
                            System.out.println(new Date() + " Handled END event for " + tuner.id + "-" + tuner.number + " " + capture.channel.channelKey + " " + capture.slot);
                        } catch (Exception e) {
                            System.out.println(new Date() + " Could not end capture! " + e.getMessage());
                            System.err.println(new Date() + " Could not end capture! " + e.getMessage());
                            e.printStackTrace();
                        }
                        capture.markEventHandled();
                        break;
                    case SIMULATE_START:
                    	activeCaptures.add(capture);
                        if(isSleepManaged) WakeupManager.preventSleep();
                        System.out.println(new Date() + " Simulated START event for " + tuner.id + "-" + tuner.number + " " + capture.channel.channelKey + " " + capture.slot);
                        capture.markEventHandled();
                    	break;
                    case SIMULATE_END:
                    	activeCaptures.remove(capture);
                        if (activeCaptures.size() == 0 && isSleepManaged){
                            WakeupManager.allowSleep();
                        }
                        tuner.removeCapture(capture);
                        System.out.println(new Date() + " Simulated END event for " + tuner.id + "-" + tuner.number + " " + capture.channel.channelKey + " " + capture.slot);
                        capture.markEventHandled();
                    	break;
                    default:
                        System.out.println(new Date() + " Next event (" + nextEvent + ") not defined for " + tuner.id + "-" + tuner.number + " " + capture.channel.channelKey + " " + capture.slot);
                        break;
                    }
					capture = null;
				} else {
				    //System.out.println("tuner " + tuner.id + "-" + tuner.number + " has no captures right now.");
                }
			}
	    	try {Thread.sleep(100);} catch(Exception e) {};  // if an event triggers, but doesn't 'catch' right away, avoid a huge log.
    	}
        
        if (CaptureManager.webServer.runFlag == false) {
            // Terminate ClockChecker
            clockChecker.shutDown();
            System.out.println(new Date() + " Duplicate instance.  No clean-up being done.");
            // Web server was not running, meaning this is a second copy of CaptureManager
            // No need to do any clean-up
        } else {
            System.out.println(new Date() + " Shutdown clean-up being done.");
            // Normal clean-up when we get a shutdown request (something set runFlag to false)
            
            // Stop the web interface since there's nothing to talk to any more
            CaptureManager.webServer.runFlag = false;

            // Stop any active recordings (if we are not simulating)
            for (Iterator iter = activeCaptures.iterator(); iter.hasNext() && !CaptureManager.simulate;) {
                Capture aCapture = (Capture) iter.next();
                this.removeActiveCapture(aCapture, false);
            }
            
            // Reset all wakeup timers (they will be re-created from persisted captures on restart)
            for (Iterator iter = tunerManager.iterator(); iter.hasNext();) {
                Tuner tuner = (Tuner) iter.next();
                for (Iterator iterator = tuner.captures.iterator(); iterator.hasNext();) {
                    Capture aCapture = (Capture) iterator.next();
                    aCapture.removeWakeup();
                }
            }
            
            // Allow the system to sleep
            if (isSleepManaged) WakeupManager.allowSleep();
            
            // Remove emailer wake up, if any
            if (CaptureManager.emailer != null){
                emailer.removeHardwareWakeupEvent("CaptureManager shutdown email");
            }

            // Cancel any wait thread && remove wakeup
            if (wakeupEvent != null){
                wakeupEvent.interruptTimer(WakeupEvent.CANCEL);
                wakeupEvent.removeHardwareWakeupEvent("CaptureManager shutdown wakeupEvent");
            }
            
            // Terminate ClockChecker
            clockChecker.shutDown();
        }


        System.out.println(new Date() + " CaptureManager.run() ending");
    	CaptureManager.running = false;
    }
    
    private boolean startedOk(TinyWebServer webServer2) {
        try {Thread.sleep(250);} catch (Exception e){};
        if (!CaptureManager.webServer.isRunning()){
            try {Thread.sleep(2000);} catch (Exception e){};
            if (!CaptureManager.webServer.isRunning()){
                System.out.println(new Date() + " ERROR: web server did not start.");
                return false;
            }
        }
        return true;
    }

    // Helpers for the run method
    private long getMsUntilNextEvent(){
    	Calendar nextCaptureCalendar = null;
        if (CaptureManager.emailer != null){
            nextCaptureCalendar = emailer.getNextTriggerTimeCalendar();
        }
        if (CaptureManager.wakeupEvent != null){
            Calendar wakeupCal = wakeupEvent.getNextTriggerTimeCalendar();
            if (nextCaptureCalendar == null || wakeupCal.before(nextCaptureCalendar)){
                nextCaptureCalendar = wakeupCal;
            }
        }
        
        // at this point, we have nextCaptureCalendar set for either the emailer or wakeupEvent, but it could be way off in the future
        
    	// this loop gets the next event for each tuner
        for (Iterator iter = tunerManager.iterator(); iter.hasNext();) {
			Tuner tuner = (Tuner) iter.next();
			Calendar nextCaptureCalendarForTuner = tuner.getNextCaptureCalendar();
			
			// if our current nextCaptureCalendar is older than the one from the tuner, have this newer one take-over the lead
			if (nextCaptureCalendarForTuner != null && (nextCaptureCalendar == null || nextCaptureCalendar.after(nextCaptureCalendarForTuner))){
				nextCaptureCalendar = nextCaptureCalendarForTuner;
			}
		}
        // now we have the newest thing represented by the nextCaptureCalendar.
    	long msUntilNextEvent = Long.MAX_VALUE;
    	if (nextCaptureCalendar != null){
    		msUntilNextEvent = nextCaptureCalendar.getTimeInMillis() - Calendar.getInstance().getTimeInMillis();
    	}
    	return msUntilNextEvent;
    }

    // PUBLIC METHODS
    
    // Method not used
    public static void scheduleCapture(CaptureHdhr newCapture, boolean writeIt) throws CaptureScheduleException {
        if (newCapture.hasEnded()) throw new CaptureScheduleException("CaptureHdhr in the past! " + newCapture);
        boolean found = false;
        boolean channelMustBeInTheLineup = true;
        Tuner tuner = newCapture.channel.tuner;
        if (!(tuner == null)){
            if (tuner.available(newCapture, channelMustBeInTheLineup)){
                tuner.addCaptureAndPersist(newCapture, writeIt);
                found = true;
            }
        } else {
            for (Iterator iter = tunerManager.iterator(); iter.hasNext();) {
                tuner = (Tuner) iter.next();
                if (tuner.available(newCapture, channelMustBeInTheLineup)){
                    tuner.addCaptureAndPersist(newCapture, writeIt);
                   	System.out.println("channel had null tuner.  Assigning tuner " + tuner.id + "-" + tuner.number);
                   	newCapture.channel.tuner = tuner;
                    found = true;
                    break;
                }
            }
        }
        if (!found) throw new CaptureScheduleException ("no tuner available for " + newCapture);
    }
    
    // Called by Web UI and writeIt is true
    public static void scheduleCapture(Capture newCapture, boolean writeIt) throws CaptureScheduleException {
        if (newCapture.hasEnded()) throw new CaptureScheduleException("Capture in the past! " + newCapture);
        boolean found = false;
        boolean channelMustBeInTheLineup = true;
        Tuner tuner = newCapture.channel.tuner;
        if (!(tuner == null)){
            System.out.println(new Date() + " CaptureManager.scheduleCapture(newCapture, " + writeIt + ")");
            tuner.refreshCapturesFromOwningStore(true);
            if (tuner.available(newCapture, channelMustBeInTheLineup)){
                tuner.addCaptureAndPersist(newCapture, writeIt);
                found = true;
            }
        }
        if (!found) throw new CaptureScheduleException ("no tuner available for " + newCapture);
    }

    public void updateFileName(String sequence, String fileName) throws Exception {
        tunerManager.updateFileName(sequence, fileName);
    }

    //Called by Web UI
    public void removeCapture(String sequence) throws Exception {
        Capture capture = tunerManager.removeCapture(sequence);
        if (this.activeCaptures.contains(capture)){
            removeActiveCapture(capture, true);
        }
    }
    
    //Called by Web UI
    public void removeAllCaptures() {
        ArrayList<Capture> removeThese = new ArrayList<Capture>();
        for (Iterator iter = this.activeCaptures.iterator(); iter.hasNext();) {
            Capture aCapture = (Capture) iter.next();
            removeThese.add(aCapture);
        }
        boolean needsInterrupt = false; // if you're removing all captures no interrupt needed here.
        for (Iterator iter = removeThese.iterator(); iter.hasNext();) {
            Capture aCapture = (Capture) iter.next();
            removeActiveCapture(aCapture, needsInterrupt);
        }
        boolean localRemovalOnly = false;
        tunerManager.removeAllCaptures(localRemovalOnly);
    }
    
    //Called by trayIcon, the above 2 methods, and the run method
    public void removeActiveCapture(Capture capture, boolean needsInterrupt){
        if (capture != null ){
            System.out.println(new Date() + " Saving capture details " + capture);
            try {
                new CaptureDetails(capture).updateCaptureEndEvent(capture.getSignalQualityData());
            } catch (Throwable t){
                System.out.println(new Date() + " Could not save capture details! " + t.getMessage());
                System.err.println(new Date() + " Could not save capture details! " + t.getMessage());
                t.printStackTrace();
            }
            System.out.println(new Date() + " Removing active capture " + capture);
            try {
                // stuff for finishing/removing ACTIVE captures
                capture.deConfigureDevice();
                capture.interrupt();
                if (activeCaptures.contains(capture)){
                    activeCaptures.remove(capture);
                } else {
                    StringBuffer allCapturesDebug = new StringBuffer();
                    for (Iterator iter = activeCaptures.iterator(); iter.hasNext();) {
                        Capture aCapture = (Capture) iter.next();
                        allCapturesDebug.append(aCapture.slot + aCapture.getFileName() + " *****, ");
                    }
                    System.out.println(new Date() + " WARNING: CaptureManager.removeActiveCapture was passed a non-active capture:" + capture.slot + capture.getFileName());
                    System.out.println(new Date() + "        : Active captures: " + allCapturesDebug.toString());
                    capture.removeWakeup();
                }

                // stuff for removing ALL captures
                tunerManager.removeCapture(capture);
                
                // DRS 20150718 - In case something old did not get cleared out
                List<Capture> removeList = new ArrayList<Capture>();
                for (Iterator iter = activeCaptures.iterator(); iter.hasNext();) {
                    Capture aCapture = (Capture) iter.next();
                    if (aCapture.slot.isInThePast()) removeList.add(aCapture);
                }

                for (Capture aCapture : removeList) {
                    try {
                        if (aCapture.slot.isInThePastBy(LEAD_TIME_MS)) {
                            activeCaptures.remove(aCapture);
                            tunerManager.removeCapture(aCapture);
                            System.out.println(new Date() + " WARNING: An old active capture was removed." + aCapture.getTitle());
                        }
                    } catch (Throwable t) {
                        System.out.println(new Date() + " ERROR: Could not remove old active capture." + t.getMessage());
                    }
                }
                
                if (activeCaptures.size() == 0){
                    WakeupManager.allowSleep();
                } else {
                    for (Iterator iter = activeCaptures.iterator(); iter.hasNext();) {
                        Capture aCapture = (Capture) iter.next();
                        System.out.println(new Date() + " active capture: ");
                    }
                    System.out.println(new Date() + " Not issuing allowSleep because there is/are " + activeCaptures.size() + " capture(s).");
                }
                
            } catch (Exception e) {
                System.out.println(new Date() + " Could not end capture! " + e.getMessage());
                System.err.println(new Date() + " Could not end capture! " + e.getMessage());
                e.printStackTrace();
            }
            if (needsInterrupt) requestInterrupt("removeActiveCapture");
        } else {
            System.out.println(new Date() + " WARNING: CaptureManager.removeActiveCapture was passed a null object.");
        }
    }

    public static synchronized void requestInterrupt(String who) {
        String thisGuy = new Date() + " " + who;
        // If this guy has been here before, never mind.        
        if (CaptureManager.interrupterList[0].startsWith(thisGuy)) return;
        
        // Lets keep track of who's calling interrupts on us
        for (int i = 0; i < CaptureManager.interrupterList.length - 1; i++) {
            CaptureManager.interrupterList[i + 1] = CaptureManager.interrupterList[i];
        }
        CaptureManager.interrupterList[0] = thisGuy;

        // instead of letting anyone call interrupt on us,
        // we make sure we're sleeping before we interrupt
        // to prevent interrupting valid work.
        for (int i = 0 ; i < 20; i++){
            if ((sleeping || who.endsWith(TinyWebServer.TINY_END)) && runThread!=null) {
                CaptureManager.runThread.interrupt();
                CaptureManager.interrupterList[0] += " ok.";
                return;
            }
            try {Thread.sleep(500);} catch (InterruptedException e){};
        }
        StringBuffer buf = new StringBuffer(new Date() + " WARNING: CaptureManager main thread was running for more than 10 seconds after an interrupt request came in.  We will still run an interrupt, but weird things might happen.\n");
        for (String s: CaptureManager.interrupterList){
            buf.append("\t" + s + "\n");
        }
        System.out.println(buf.toString());
        System.err.println(buf.toString());
        new Exception("Interrupt while sleeping.").printStackTrace();
        CaptureManager.runThread.interrupt(); // don't expect to get here, but honor interrupt request if we do
    }

    public String getActiveWebCapturesList() {
        StringBuffer buf = new StringBuffer("<table border=\"1\">\n");
        StringBuffer xmlBuf = new StringBuffer("\n<xml id=\"captures\">\n");
        for (Iterator iter = activeCaptures.iterator(); iter.hasNext() && !CaptureManager.simulate;) {
            Capture capture = (Capture) iter.next();
            if (capture != null){
                buf.append(capture.getHtml(-1));
                xmlBuf.append(capture.getXml(-1));
            } else {
                System.out.println(new Date() + " ERROR: activeCaptures list had a null object.");
            }
        }
        buf.append("</table>\n");
        xmlBuf.append("</xml>\n");
        return new String(buf) + new String(xmlBuf);
    }
    
    public String getRecentWebCapturesList(){
        return getRecentWebCapturesList(-1);
    }

    public String getRecentWebCapturesList(int hours) {
        StringBuffer buf = new StringBuffer("<table border=\"1\">\n");
        StringBuffer xmlBuf = new StringBuffer("\n<xml id=\"recentCaptures\">\n");
        boolean headersDone = false;
        long nowMs = new Date().getTime();
        for (Iterator iter = captureDataManager.getRecentCaptures().descendingMap().values().iterator(); iter.hasNext();) {
            CaptureDetails details = (CaptureDetails) iter.next();
            if (hours > 0){
                long startMs = details.startEvent.getTime();
                int minutesSinceStart = (int)((nowMs - startMs)/1000L/60L); 
                if (minutesSinceStart > (hours * 60)){
                    continue;
                }
            }
            if (details != null){
                if (!headersDone){
                    buf.append("<tr>" + details.getHtmlHeadings() + "</tr>\n");
                    headersDone = true;
                }
                buf.append("<tr>" + details.getHtml() + "</tr>\n");
                xmlBuf.append("<recentCapture " + details.getXml() + "/>\n");
            } else {
                System.out.println(new Date() + " ERROR: recentCaptures list had a null object.");
            }
        }
        buf.append("</table>\n");
        xmlBuf.append("</xml>\n");
        return new String(buf) + new String(xmlBuf);
    }

    public String getWebPathList(String root) {
        DirectoryFilter dirFilter = new DirectoryFilter();
        StringBuffer buf = new StringBuffer("<table border=\"1\">\n");
        StringBuffer xmlBuf = new StringBuffer("\n<xml id=\"directories\">\n");
        try {
            if (root != null && root.equalsIgnoreCase("drives")){
                File[] roots = File.listRoots();
                for (int i = 0; i < roots.length; i++) {
                    System.out.println(roots[i]);
                    buf.append("<tr>" + roots[i] + "</tr>\n");
                    xmlBuf.append("<directoryEntry parent='root' entry='" + roots[i] + "'>\n");
                }
            } else {
                File aFile = new File(root);
                String[] directories = aFile.list(dirFilter);
                for (int i = 0; directories != null && i < directories.length; i++) {
                    buf.append("<tr>" + directories[i] + "<tr>\n");
                    xmlBuf.append("<directoryEntry parent='" + root + "' entry='" + directories[i] + "'>\n");
                }
            }
        } catch (Exception e){
            return e.getMessage();
        }
        buf.append("</table>\n");
        xmlBuf.append("</xml>\n");
        return new String(buf) + new String(xmlBuf);
    }

    
    public static String shutdown(String who){
        System.out.println(new Date() + " Shutdown request being processed.");
        runFlag = false;
        //CaptureManager.webServer.setRunning(false);
        CaptureManager.requestInterrupt(new Date() + " shutdown() " + who);
        if (CaptureManager.wakeupEvent != null){
            CaptureManager.wakeupEvent.interruptTimer(WakeupEvent.KILL);
        }
        Socket socket = null;
        PrintStream out = null;
        try {
            try {Thread.sleep(500);} catch (Exception e){}
            socket = new Socket("127.0.0.1", Integer.parseInt(CaptureManager.WEB_SERVER_PORT));
            out = new PrintStream(new DataOutputStream(socket.getOutputStream()));
            out.write("GET /noop".getBytes());
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try { out.close(); } catch (Exception e) {}
            try { socket.close(); } catch (Exception e) {}
        }
        return "Shutdown Requested";
    }
    
    public static void setSimulate(boolean simulate){
    	CaptureManager.simulate = simulate;
        saveSettings();
    }
    
    public static void setSleepManaged(boolean isSleepManaged) {
        CaptureManager.isSleepManaged = isSleepManaged;
        saveSettings();
    }

    public static void setEndFusionWatchEvents(boolean endFusionWatchEvents) {
        CaptureManager.endFusionWatchEvents = endFusionWatchEvents;
        saveSettings();
    }
    
    public static void setShortenExternalRecordingsSeconds(int shortenExternalRecordingsSeconds){
        CaptureManager.shortenExternalRecordingsSeconds = shortenExternalRecordingsSeconds;
        saveSettings();
    }

    public static void setFusionLeadTime(int leadTimeSeconds) {
        CaptureManager.fusionLeadTime = leadTimeSeconds;
        saveSettings();
    }

    public static void setLeadTimeSeconds(int leadTimeSeconds) {
        CaptureManager.leadTimeSeconds = leadTimeSeconds;
        saveSettings();
    }
    
    public static void saveSettings(){
        Properties props = new Properties();
        props.put("simulate", "" + CaptureManager.simulate);
        props.put("isSleepManaged", "" + CaptureManager.isSleepManaged);
        props.put("fusionLeadTime", "" + CaptureManager.fusionLeadTime);
        props.put("leadTimeSeconds", "" + CaptureManager.leadTimeSeconds);
        props.put("endFusionWatchEvents", "" + CaptureManager.endFusionWatchEvents);
        props.put("shortenExternalRecordingsSeconds", "" + CaptureManager.shortenExternalRecordingsSeconds);
        String path = "";
        if (!CaptureManager.dataPath.equals("")){
            path = CaptureManager.dataPath + File.separator;
        }
        try {
            System.out.println("saving to " + path + CaptureManager.propertiesFileName);
            Writer writer = new FileWriter(path + CaptureManager.propertiesFileName);
            props.store(writer, CaptureManager.propertiesFileName);
            writer.close();
        } catch (Exception e) {
            System.out.println(new Date() + " ERROR: Could not save CaptureManager properties.");
            System.err.println(new Date() + " ERROR: Could not save CaptureManager properties.");
            e.printStackTrace();
        }
    }
    
    public static void loadSettings(){
        String path = "";
        if (!CaptureManager.dataPath.equals("")){
            path = CaptureManager.dataPath + File.separator;
        }
        String propertiesFileNamePath = path + CaptureManager.propertiesFileName;
        if (!new File(propertiesFileNamePath).exists()) {
            System.out.println(new Date() + " Could not find optional CaptureManager properties file [" + propertiesFileNamePath + "].  Using defaults.");
            return;
        }
        Properties props = new Properties();
        try {
            Reader reader = new FileReader(propertiesFileNamePath);
            props.load(reader);
            for (Iterator iter = props.keySet().iterator(); iter.hasNext();) {
                String key = (String) iter.next();
                if (key != null && key.equals("simulate")){
                    CaptureManager.simulate = Boolean.parseBoolean(props.getProperty(key));
                } else if (key != null && key.equals("isSleepManaged")){
                    CaptureManager.isSleepManaged = Boolean.parseBoolean(props.getProperty(key));
                } else if (key != null && key.equals("fusionLeadTime")){
                    CaptureManager.fusionLeadTime = Integer.parseInt(props.getProperty(key));
                } else if (key != null && key.equals("leadTimeSeconds")){
                    CaptureManager.leadTimeSeconds = Integer.parseInt(props.getProperty(key));
                } else if (key != null && key.equals("endFusionWatchEvents")){
                    CaptureManager.endFusionWatchEvents = Boolean.parseBoolean(props.getProperty(key));
                } else if (key != null && key.equals("shortenExternalRecordingsSeconds")){
                    CaptureManager.shortenExternalRecordingsSeconds = Integer.parseInt(props.getProperty(key));
                }
            }
            reader.close();
        } catch (Exception e) {
            System.out.println(new Date() + " ERROR: Could not load optional CaptureManager properties.  Using defaults. " + e.getMessage());
            System.err.println(new Date() + " ERROR: Could not load optional CaptureManager properties.  Using defaults. ");
            e.printStackTrace();
        }
    }

    public static void setEmailer(Emailer object) {
        emailer = object;
    }

    public static Emailer getEmailer() {
        return emailer;
    }

    public static void setWakeupEvent(WakeupEvent object) {
        CaptureManager.wakeupEventMessageDone = false;
        wakeupEvent = object;
        requestInterrupt("setWakeupEvent");
    }

    public static void registerEvent() {
        if (!usingOldOs) return; // Do not print warning if using a newer OS.  Newer OS's don't use wakeupEvent.
        if (wakeupEvent == null && !wakeupEventMessageDone) {
            System.out.println(new Date() + " ERROR: Wakeup event never defined!  This machine will have NO WAKEUP EVENT CAPABILITY.");
            CaptureManager.wakeupEventMessageDone = true;
        } else if (WakeupEvent.isRunning) {
            wakeupEvent.interruptTimer(WakeupEvent.RESET);
        } else if (!WakeupEvent.hasCommand()){
            new Thread(wakeupEvent).start();
            try {Thread.sleep(600);}catch(Exception e){}
        }
    }

    public static float getWindowsVersion(boolean quiet) {
        Float windowsVersion = 6F;
        String cleanedVersion = System.getProperty("os.name").replaceAll(".*?([\\d.]+).*", "$1");
        try {
            windowsVersion = Float.parseFloat(cleanedVersion);
        } catch (Exception e){
            if (!quiet) {
                System.out.println(new Date() + " ClockChecker could not parse OS name [" + cleanedVersion + "]");
                System.err.println(new Date() + " ClockChecker could not parse OS name [" + cleanedVersion + "]");
            }
        }
        //if ("C:\\my\\dev\\eclipsewrk\\CwHelper".equals(System.getProperty("user.dir"))) return 8F; // for testing on Win7
        //if ("C:\\Program Files\\MyHDEpg".equals(System.getProperty("user.dir"))) return 8F; // for testing on Win7
        return windowsVersion;
    }

    
    public String getProperties() {
        StringBuffer buf = new StringBuffer("<table border=\"1\">\n");
        StringBuffer xmlBuf = new StringBuffer("\n<xml id=\"properties\">\n");
        buf.append(
                "<tr><td>DataPath:</td><td>" + dataPath + "</td></tr>" + 
                "<tr><td>HdHrPath:</td><td>" + hdhrPath + "</td></tr>" + 
                "<tr><td>SleepManaged:</td><td>" + CaptureManager.isSleepManaged + "</td></tr>" + 
                "<tr><td>LeadTime:</td><td>" + CaptureManager.leadTimeSeconds + "</td></tr>" + 
                "<tr><td>FusionLeadTime:</td><td>" + CaptureManager.fusionLeadTime + "</td></tr>" + 
                "<tr><td>Simulate:</td><td>" + CaptureManager.simulate + "</td></tr>" + 
                "<tr><td>EndFusionWatchEvents:</td><td>" + CaptureManager.endFusionWatchEvents + "</td></tr>" + 
                "<tr><td>ShortenExternalRecordingsSeconds:</td><td>" + CaptureManager.shortenExternalRecordingsSeconds + "</td></tr>" + 
                "\n");
        xmlBuf.append(
                "  <capture "+ 
                "dataPath=\"" + dataPath + "\" " + 
                "hdhrPath=\"" + hdhrPath + "\" " + 
                "sleepManaged=\"" + CaptureManager.isSleepManaged + "\" " + 
                "leadTime=\"" + CaptureManager.leadTimeSeconds + "\" " + 
                "fusionLeadTime=\"" + CaptureManager.fusionLeadTime + "\" " + 
                "simulate=\"" + CaptureManager.simulate + "\" " + 
                "endFusionWatchEvents=\"" + CaptureManager.endFusionWatchEvents + "\" " + 
                "shortenExternalRecordingsSeconds=\"" + CaptureManager.shortenExternalRecordingsSeconds + "\" " + 
                "/>\n");
        buf.append("</table>\n");
        xmlBuf.append("</xml>\n");
        return new String(buf) + new String(xmlBuf);
    }

    // IMPLEMENTATION FOR THE ServiceStatusHandler
    /* DRS 20080822 Comment Service Specific Code
    public boolean canPause() {
        return false;
    }

    public boolean onContinue() {
        return false;
    }

    public boolean onPause() {
        return false;
    }

    public boolean onStop() {
        shutdown();
        try {Thread.sleep(1000);} catch (Exception e){}
        return !CaptureManager.running;
    }
    */
    
	// MAIN METHOD
    public static void main(String[] args) {

        // parse inputs, considering spaces in path
        StringBuffer cwepgExecutablePath = new StringBuffer(); boolean ep = false;
        StringBuffer hdhrPath = new StringBuffer(); boolean hp = false;
        StringBuffer dataPath = new StringBuffer(); boolean dp = false;
        for(int i=0; i<args.length; i++){
            if (args[i].indexOf("-cwepgExecutablePath") > -1){
                ep = true; hp = false; dp = false;
                continue;
            } else if (args[i].indexOf("-hdhrPath") > -1){
                hp = true; ep = false; dp = false;
                continue;
            } else if (args[i].indexOf("-dataPath") > -1){
                dp = true; hp = false; ep = false;
                continue;
            }
            if (ep){
                cwepgExecutablePath.append(args[i] + " ");
            }
            if (hp){
                hdhrPath.append(args[i] + " ");
            }
            if (dp){
                dataPath.append(args[i] + " ");
            }
        }
        String cwepgPathFinal = new String(cwepgExecutablePath).trim();
        if (cwepgPathFinal.length() > 0) cwepgPathFinal += "\\";
        String hdhrPathFinal = new String(hdhrPath).trim();
        if (hdhrPathFinal.length() > 0) hdhrPathFinal += "\\";
        String dataPathFinal = new String(dataPath).trim();
        if (dataPathFinal.length() > 0) dataPathFinal += "\\";
        
        System.out.println("Using [" + cwepgPathFinal + "] for cwepg executable directory, [" + hdhrPathFinal + "] for HDHR executable directory, and [" + dataPathFinal + "] for the data path.");

        new SplashScreenCloser(2); // starts itself waits 2 seconds and closes the splash window
        
    	// By creating the instance of CaptureManager, we
    	// also create the instance of TunerManager.  If there
    	// is no device on the network, simulate mode is set.
        CaptureManager cm = CaptureManager.getInstance(cwepgPathFinal, hdhrPathFinal, dataPathFinal);
        TunerManager tm = TunerManager.getInstance();
        
        // We need to get the channel list loaded.  The default
        // is to build the channels from the file saved during
        // the last scan.  Scan will not be performed unless 
        // the user initiates it because it takes a long time.
        tm.loadChannelsFromFile();
        
        // As input to the user for creating their own 
        // channel priority file, we write out the default
        // priority file (but not if it would be empty).
        tm.createDefaultPriorityChannelListFile();
        
        // If the user created custom channel priorities file,
        // we use that to control which tuner gets assigned in
        // the case where the same channel appears on multiple
        // tuners and the tuner is not specified when the capture
        // is scheduled.
        tm.adjustPriorities(cwepgPathFinal + "ChannelPriority.txt");
        
        // Let the person looking at the log know what channels
        // we have to work with.
        tm.printAllChannels();

        // It's possible that we have nothing at this point...
        // no tuners, no channels.  This would be indicated in
        // the log, but no exceptions.  The user may plug-in
        // the HDHR device, then through the web interface, 
        // run a discover, and a scan to get started for the
        // first time.
        
        // **********************************
        // RUN THE CAPTURE MANAGER SERVICE 
        // **********************************
        cm.run(); // blocks to keep our output redirect working
        System.out.println(new Date() + " CaptureManager.run() ended.");
    }

}
