package org.cwepg.hr;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

import org.cwepg.hr.CaptureHdhr.DeviceUnavailableException;
import org.cwepg.svc.RestartManager;
import org.cwepg.svc.SplashScreenCloser;
import org.cwepg.svc.TinyWebServer;

public class CaptureManager implements Runnable { //, ServiceStatusHandler { //DRS 20080822 Comment Service Specific Code
    public static final String propertiesFileName = "CaptureManagerSettings.txt";
    public static final String[] htmlFileNames = {"CWHelpervcr.html", "CWHelpersettings.html", "CWHelpershutdown.html"};
    public static final long LEAD_TIME_MS = 2000;
    public static final int LL_OFFSET = 10;
    public static final DecimalFormat DF = new DecimalFormat("0.0");

    public static String version = "0,0,0,0"; // DRS 20210424 - Value is pulled from version.txt resource at startup.
    private static CaptureManager captureManager;
    private static TunerManager tunerManager;
    private static CaptureDataManager captureDataManager;
    //private static TinyWebServer webServer;
    private static Emailer emailer;
    private static WakeupEvent wakeupEvent; // null wakeupEvent signals no wakeup event
    private static TrayIconManager trayIconManager;
    private static Thread eventLoopThread;
    private static boolean wakeupEventMessageDone = false;
    private static long memoryInUse;
    private static String nextEventType = "";
    private static Calendar nextEventCalendar = null;

    static boolean runFlag = true;
    static boolean running = false;
    static boolean sleeping = false;
    static final int SIMULATE_START = CaptureHdhr.START + 100;
    static final int SIMULATE_END = CaptureHdhr.END + 100;
    static final String[] interrupterList = new String[30];

    public static final Thread runThread = Thread.currentThread();
    public static String dataPath = "";
    public static String hdhrPath = "";
    public static String cwepgPath = "";

    /* Persistent Settings */
    public static int fusionLeadTime = 120; // start Fusion recordings 120 seconds from 'now' or they won't start
    public static int leadTimeSeconds = 90; // number of seconds to subtract from the wait duration. This allows the set() method to be set  in accordance with the start time of the recording and not worry about adjusting for startup delay.
    public static int loopLeadTimeSeconds = leadTimeSeconds - LL_OFFSET; // DRS 20210201 - Added 1 - machine wakes 10 seconds before the sleep is scheduled to end.
    public static boolean isSleepManaged = true;
    public static boolean myhdWakeup = true;
    public static boolean endFusionWatchEvents = false;
    public static boolean trayIcon = true;
    public static boolean simulate = false;
    public static int shortenExternalRecordingsSeconds=15;
    public static int discoverRetries = 5;
    public static int hdhrRecordMonitorSeconds = -1;
    public static float hdhrBadRecordingPercent = 1F / 300F * 100F;
    public static int discoverDelay = 1000;
    public static int clockOffset = 0;
    public static String alternateChannels = "";
    public static boolean allTraditionalHdhr = false;
    public static boolean rerunDiscover = false;
    public static boolean useHdhrCommandLine = true; // DRS 20200707 - Added instance variable allow hdhr via http
    public static boolean unlockWithForce = false; // DRS 20221210 - Added ability to configure unlock vs scheduling replacement
    
    public static ArrayList<Capture> activeCaptures = new ArrayList<Capture>(); //DRS 20220708 - Changed to ArrayList from HashSet because extended recordings didn't match hash
    
    private CaptureManager(){
        //ServiceStatusManager.setServiceStatusHandler(this); //DRS 20080822 Comment Service Specific Code
        loadSettings();
        System.out.println(new Date() + "\n" + getProperties(true));
        if (clockOffset != 0) alterPcClock(clockOffset, 5, true);
        // loadHtmlPages(htmlFileNames); // DRS 20240224 - No longer needed - https://github.com/sengsational/cwhelper/issues/16
        Arrays.fill(interrupterList,"");

        eventLoopThread = new Thread(new RestartManager(), "Win32 Event Loop");
        eventLoopThread.setDaemon(true); // Make the thread a daemon so it doesn't prevent program from exiting.
        eventLoopThread.start();
        RestartManager.registerApplicationRestart(); //DRS 20210318 - Moved this out - Tell the OS we will cooperate with shutdowns.

    }
    
    public static CaptureManager getInstance(){
        if (captureManager == null){
            version = getVersionFromResource();
            captureDataManager = CaptureDataManager.getInstance();
            captureManager = new CaptureManager(); // settings are loaded here
            tunerManager = TunerManager.getInstance();
            if (tunerManager.noTuners()) tunerManager.countTuners(); // THIS IS WHERE WE RUN COUNT TUNERS FOR THE FIRST TIME. 
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
        ShutdownHookThread.setRunThread(Thread.currentThread());
    	System.out.println("CaptureManager.run() starting (version " + version + ")");
    	CaptureManager.running = true;
        //CaptureManager.runThread = Thread.currentThread();
    	//if (CaptureManager.pollIntervalSeconds > 0) ClockChecker.getInstance(); //starts itself
        if (trayIcon) trayIconManager = TrayIconManager.getInstance(); //starts itself
    	for (;;){
			if (!runFlag) break;
			try {memoryInUse = Math.round((Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory())/1024/1024);} catch (Exception e) {memoryInUse = -1;}
            System.out.println("----------------------------------------- " + memoryInUse + " MB in use.");
            
            // DRS 20201029 - Rewrote this code to keep machines from nodding-off
            long msFirstSleep = Long.MAX_VALUE;
            long msSecondSleep = Long.MAX_VALUE;
            long msUntilEvent = getMsUntilNextEvent(false) - LEAD_TIME_MS; // Can be negative
	    	long msInitialSubtraction = msUntilEvent - (loopLeadTimeSeconds * 1000); // Can be negative
	    	if (msUntilEvent < 0) { // The start time is in the past - no sleeping required
	    	    msFirstSleep = 0;
	    	    msSecondSleep = 0;
	    	} else if (msInitialSubtraction < 0) { // The start time is within the lead time - sleep less than the lead time
	    	    msFirstSleep = 0;
	    	    msSecondSleep = msUntilEvent;
	    	} else { // The start time is in the future - sleep twice, where first sleep allows machine to nod-off, and the second where we keep machine active
	    	    msFirstSleep = msInitialSubtraction;
	    	    msSecondSleep = loopLeadTimeSeconds * 1000; // no change
	    	}
	    	
	    	// DRS 20210105 - Added 'if' - Prevent very short duration first sleeps
	    	if (msFirstSleep < 60000) { //DRS 20210114 - Fixed bug from 60ms to 60000ms
	    	    msSecondSleep = msFirstSleep + msSecondSleep;
	    	    msFirstSleep = 0;
	    	}

	    	try {
	    		if (msFirstSleep > 0) {
                    Date wakeupMsg = new Date(new Date().getTime() + (msFirstSleep + msSecondSleep));
                    System.out.println(new Date() + " sleeping until " + wakeupMsg + " (" + DF.format(msFirstSleep / 1000F / 60F) + " minutes), less " + DF.format(msSecondSleep / 1000) + " seconds lead time (" + msFirstSleep + "ms)");

                    sleeping = true;
					Thread.sleep(msFirstSleep);
					sleeping = false;
                    System.out.println(new Date() + " CaptureManager.run() firstSleep ended.");
	    		}
	    		
	    		if (msSecondSleep > 0) {
                    System.out.println(new Date() + " sleeping " + DF.format(msSecondSleep / 1000F) + " seconds lead time.");

                    if (isSleepManaged){
                        WakeupManager.preventSleep();
                    } else {
                        System.out.println(new Date() + " preventSleep() has NOT been issued since isSleepManaged was " + isSleepManaged + ".");                        
                    }
                    
                    sleeping = true;
                    Thread.sleep(msSecondSleep);
                    sleeping = false;
                    System.out.println(new Date() + " CaptureManager.run() secondSleep ended.");
	    		}
	    		// DRS 20101029 - END - Rewrote this code to keep machines from nodding-off

			} catch (InterruptedException e) {
				System.out.println(new Date() + " >>> CaptureManager.run() resetting sleep time. Interupted by:" + CaptureManager.interrupterList[0]);
				Calendar nowCalendar = Calendar.getInstance(); nowCalendar.add(Calendar.SECOND, -5);
				if (nextEventCalendar != null && nowCalendar.after(nextEventCalendar)) {
				    System.out.println(new Date() + " >>>>  M I S S E D   E V E N T <<<<  A " + nextEventType + " event was missed, probably because the system was sleeping.");
				}
				//try {Thread.sleep(1000);} catch (Exception ee){} ///////////////////////////////////////////// DRS 20150911 - separate functionality in time.  Debug only.
			}
            sleeping = false;

            if (runFlag && wakeupEvent != null && wakeupEvent.isValid() && !WakeupEvent.isRunning() && wakeupEvent.isDue()){  //isDue() blocks for 2 seconds if called >1 time in 10 min
                new Thread(wakeupEvent, "Thread-WakeupEvent").start();
                if (CaptureManager.isSleepManaged) WakeupManager.preventSleep(); // DRS 20210322 - Added 1 - Moved from the WakeupEvent.run() method
                try {Thread.sleep(100);} catch (Exception e){}; // prevent looping if this is still be due to run a few ms later
            }
            if (runFlag && emailer != null && emailer.isValid() && emailer.isDue()) { //isDue() blocks for 2 seconds if called >1 time in 10 min
                emailer.send();
                // On the same schedule as the emailer, try to set the PC clock to the offset in the settings
                try { if (clockOffset != 0) alterPcClock(clockOffset, 5, true); } catch (Throwable t){ System.out.println(new Date() + " ERROR: Could not set PC clock." );}
            }
            
            
	    	for (Iterator<Tuner> iter = TunerManager.iterator(); iter.hasNext() && runFlag;) {
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
                            capture.configureDevice(); //DRS 20220711 - Add Comment - Can throw DeviceUnavailableException
                            if (!capture.target.isWatch() && capture.target.mkdirsAndTestWrite(false, capture.target.fileName, 20) == false) throw new Exception (new Date() + " ERROR: The target directory of file [" + capture.target.getFileNameOrWatch() + "] was not writable.\n");
                            Thread captureThread = new Thread(capture, "Thread-Capture" + capture.target.fileName);
                            captureThread.start(); // <<======================
                            //System.out.println(new Date() + " DEBUG: Returned to main thread.");
                            if (isSleepManaged) WakeupManager.preventSleep();
                            else System.out.println(new Date() + " not sleep managed.");
                            System.out.flush();
                            Thread.sleep(1000); //So log will appear in the "right" order
                            if (!captureThread.isAlive()) throw new Exception("Capture.run() failed to start.");
                            capture.addIcon();
                            activeCaptures.add(capture);
                            System.out.println(new Date() + " There is/are " + activeCaptures.size() + " active capture after adding " + capture.getTitle());
                            System.out.println(new Date() + " Handled START event for " + tuner.id + "-" + tuner.number + " " + capture.channel.channelKey + " " + capture.slot);
                            System.out.println(new Date() + " The file [" + capture.target.fileName +"] " + (new File(capture.target.fileName).exists()?"has been created.":"has NOT BEEN CREATED!"));
                            //DRs 20240313 - Added 'if' - add metadata to transport stream file - separate thread in so we can pause if needed
                            if (capture.getTunerType() == Tuner.HDHR_TYPE) {
                            	Thread metaThread = new Thread(new CaptureMetadata((CaptureHdhr)capture), "Thread-Metadata save " + capture.target.fileName);
                            	metaThread.start();
                            }
                        // DRS 20220711 - Added catch - Attempt to reschedule if device unavailable.
                        } catch (DeviceUnavailableException d) {
                            successful = false;
                            System.out.println(new Date() + " WARNING: Device for capture was unavailable! " + d.getMessage());
                            if (capture instanceof CaptureHdhr) {
                            	if (capture instanceof CaptureHdhrHttp) capture.target.removeFile(); // DRS 20241129 - Added 'if' - issue #48
                            	// Copied this technique and used it in RecordingMonitor.run() if there is a weak recording.
                                System.out.println(new Date() + " Attempting to define and schedule a replacement for [" + capture + "]");
                                System.out.println(new Date() + " CaptureManager.hdhrRecordMonitorSeconds = " + CaptureManager.hdhrRecordMonitorSeconds);
                                CaptureHdhr captureHdhr = (CaptureHdhr)capture;
                                captureHdhr.addCurrentTunerToFailedDeviceList();
                                captureHdhr.target.fileName = Target.getNonAppendedFileName(captureHdhr.target.fileName);
                                Capture replacementCapture = TunerManager.getReplacementCapture(captureHdhr);
                                if (replacementCapture != null) {
                                    try {
                                        System.out.println(new Date() + " Created replacement [" + replacementCapture + "]");
                                        String fileNameAppendRandom = "_" + (Math.random() + "").substring(3, 6);
                                        fileNameAppendRandom = ""; // Terry wanted to try this.  We might or might not get a conflict...I didn't research it.
                                        Target replacementTarget = new Target(captureHdhr.target, captureHdhr, fileNameAppendRandom, 2);
                                        replacementCapture.setTarget(replacementTarget);
                                        scheduleCapture(replacementCapture, true);
                                        System.out.println(new Date() + " Replacement scheduled ok.");
                                    } catch (Exception e) {
                                        System.out.println(new Date() + " Could not schedule replacement capture! " + e.getMessage());
                                        System.err.println(new Date() + " Could not schedule replacement capture! " + e.getMessage());
                                        e.printStackTrace();
                                    }
                                } else {
                                    System.out.println(new Date() + " Unable to create replacement for  [" + capture + "]");
                                }
                             }
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
                            removeActiveCapture(capture, needsInterrupt, true);
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
                    	/* DRS 20201231 - Commented if - Don't do this when removing capture.  Instead, do it every loop.
                        if (activeCaptures.size() == 0 && isSleepManaged){
                            WakeupManager.allowSleep();
                        }
                        */
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
	    	// DRS 20201231 - Added if/else - Do "allow sleep" here, every loop, instead of when removing capture.
            if (activeCaptures.size() == 0 && !WakeupEvent.isRunning() && !(Math.abs(getMsUntilNextEvent(true)) < 30000)) { //DRS 20250101 - Added pending activity clause - Issue #54 // DRS 20210104 - Added isRunning clause - prevent allow sleep if WakeupEvent is running
                //System.out.println(new Date() + " allowSleep() has been issued.");                        
                if (isSleepManaged) WakeupManager.allowSleep();
            } else { //everything in the 'else' is logging only.
                for (Iterator<Capture> iter = activeCaptures.iterator(); iter.hasNext();) {
                    Capture aCapture = (Capture) iter.next();
                    System.out.println(new Date() + " active capture: " + aCapture);
                }
                if(CaptureManager.isSleepManaged) {
                    if (activeCaptures.size() != 0) {
                        System.out.println(new Date() + " Not issuing allowSleep because there is/are " + activeCaptures.size() + " capture(s).");
                    } else {
                        System.out.println(new Date() + " Not issuing allowSleep because there is an active WakeupEvent or pending activity.");
                    }
                }
            }

	    	try {Thread.sleep(100);} catch(Exception e) {};  // if an event triggers, but doesn't 'catch' right away, avoid a huge log.

    	} // ===================================    END OF MAIN LOOP  ===================================================================
        
    	TinyWebServer webServer = TinyWebServer.getInstance(ServiceLauncher.WEB_SERVER_PORT);
        if (webServer.isRunning() == false) {
            // Terminate ClockChecker
            // ClockChecker.shutDown();
             System.out.println(new Date() + " Duplicate instance.  No clean-up being done.");
            // Web server was not running, meaning this is a second copy of CaptureManager
            // No need to do any clean-up
        } else {
            System.out.println(new Date() + " Shutdown clean-up being done.");
            // Normal clean-up when we get a shutdown request (something set runFlag to false)

            // Remove our application's tray icon
            if (trayIconManager!=null) {
                TrayIconManager.shutDown();
                try {Thread.sleep(200);} catch (Exception e){}
            }
            
            // Kill the Windows Message listener
            if (eventLoopThread != null) eventLoopThread.interrupt();

            // Stop the web interface since there's nothing to talk to any more
            webServer.setRunning(false);
            webServer.pokeForShutdown();
            
            // Stop any active recordings (if we are not simulating)
            for (Iterator<Capture> iter = activeCaptures.iterator(); iter.hasNext() && !CaptureManager.simulate;) {
                Capture aCapture = (Capture) iter.next();
                this.removeActiveCapture(aCapture, false, false); // stop the captures, but do not re-write the captures persistence file
            }
            
            // Reset all wakeup timers (they will be re-created from persisted captures on restart)
            for (Iterator<Tuner> iter = TunerManager.iterator(); iter.hasNext();) {
                Tuner tuner = (Tuner) iter.next();
                for (Iterator<Capture> iterator = tuner.captures.iterator(); iterator.hasNext();) {
                    Capture aCapture = (Capture) iterator.next();
                    // DRS 20190719 - Added 1 - Removed wakeups for Fusion per Terry's email today
                    if (!(aCapture instanceof CaptureFusion)) 
                    aCapture.removeWakeup();
                }
            }
            
            // Allow the system to sleep
            if (isSleepManaged) WakeupManager.allowSleep(); // << ALLOW SLEEP FOR SHUTDOWN OF APPLICATION (probably redundant)
            
            // Remove emailer wake up, if any
            if (CaptureManager.emailer != null){
                emailer.removeHardwareWakeupEvent("CaptureManager shutdown email");
            }

            // Cancel any wait thread && remove wakeup
            if (wakeupEvent != null){
                wakeupEvent.interruptTimer(WakeupEvent.CANCEL);
                wakeupEvent.removeHardwareWakeupEvent("CaptureManager shutdown wakeupEvent");
            }
            
            Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
            for (Thread thread : threadSet) {
                System.out.println(new Date() + " Thread: " + thread.getName());    
            }
         }
        System.out.println(new Date() + " CaptureManager.run() ending");
    	CaptureManager.running = false;
    }
    
	public static String shutdown(String who){
        if (!runFlag) {
            System.out.println(new Date() + " CaptureManager runFlag is already false (" + who + ")");
            return "Thanks, but a shutdown was already in process.";
        }
        
        if (activeCaptures.size() != 0  && !who.contains("ENDSESSION")) {
            System.out.println(new Date() + " Active capture(s) prevented shutdown (" + who + ")");
            return "Active capture(s) prevented shutdown";
        }
        
        System.out.println(new Date() + " Shutdown request being processed.");
        
        runFlag = false;
        System.out.println(new Date() + " CaptureManager runFlag is false and requesting interrupt.");
        CaptureManager.requestInterrupt("CaptureManager.shutdown(" + who + ")");
        if (CaptureManager.wakeupEvent != null){
            CaptureManager.wakeupEvent.interruptTimer(WakeupEvent.KILL);
        }
        
        return "Shutdown Requested";
    }
    
    // Helpers for the run method
    public static long getMsUntilNextEvent(boolean silent){    //DRS 20250101 - added silent parameter - Issue #54 // DRS 20160425 - changed to public static - Support CwHelper Restart
        nextEventType = "";
    	nextEventCalendar = null;
        if (CaptureManager.emailer != null){
            nextEventCalendar = emailer.getNextTriggerTimeCalendar();
            nextEventType = "emailer";
        }
        if (CaptureManager.wakeupEvent != null){
            Calendar wakeupCal = wakeupEvent.getNextTriggerTimeCalendar();
            if (nextEventCalendar == null || wakeupCal.before(nextEventCalendar)){
                nextEventCalendar = wakeupCal;
                nextEventType = "wakeupEvent";
            }
        }
        
        // at this point, we have nextCaptureCalendar set for either the emailer or wakeupEvent, but it could be way off in the future
        
    	// this loop gets the next event for each tuner
        for (Iterator<?> iter = TunerManager.iterator(); iter.hasNext();) {
			Tuner tuner = (Tuner) iter.next();
			Calendar nextCaptureCalendarForTuner = tuner.getNextCaptureCalendar();
			String startEnd = tuner.getStartEndFromLastCalendarInquiry(); // DRS 20220707 - For improved event logging
			
			// if our current nextCaptureCalendar is older than the one from the tuner, have this newer one take-over the lead
			if (nextCaptureCalendarForTuner != null && (nextEventCalendar == null || nextEventCalendar.after(nextCaptureCalendarForTuner))){
				nextEventCalendar = nextCaptureCalendarForTuner;
				nextEventType = "capture " + startEnd;
			}
		}
        // now we have the newest thing represented by the nextCaptureCalendar.
    	long msUntilNextEvent = Long.MAX_VALUE;
    	if (nextEventCalendar != null){
    		msUntilNextEvent = nextEventCalendar.getTimeInMillis() - Calendar.getInstance().getTimeInMillis();
    		if (!silent) System.out.println(new Date() + " The next event (" + nextEventType + ") is scheduled for " + Tuner.SDF.format(nextEventCalendar.getTime()) + " which is " + msUntilNextEvent + "ms from now.");
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
            if (tuner.available(newCapture, channelMustBeInTheLineup, true)){
                tuner.addCaptureAndPersist(newCapture, writeIt);
                found = true;
            }
        } else {
            for (Iterator<?> iter = TunerManager.iterator(); iter.hasNext();) {
                tuner = (Tuner) iter.next();
                if (tuner.available(newCapture, channelMustBeInTheLineup, true)){
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
            if (tuner.available(newCapture, channelMustBeInTheLineup, true)){
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
        if (CaptureManager.activeCaptures.contains(capture)){
            removeActiveCapture(capture, true, true);
        }
    }
    
    //Called by Web UI
    public void removeAllCaptures() {
        ArrayList<Capture> removeThese = new ArrayList<Capture>();
        for (Iterator<Capture> iter = CaptureManager.activeCaptures.iterator(); iter.hasNext();) {
            Capture aCapture = (Capture) iter.next();
            removeThese.add(aCapture);
        }
        boolean needsInterrupt = false; // if you're removing all captures no interrupt needed here.
        for (Iterator<Capture> iter = removeThese.iterator(); iter.hasNext();) {
            Capture aCapture = (Capture) iter.next();
            removeActiveCapture(aCapture, needsInterrupt, true);
        }
        boolean localRemovalOnly = false;
        tunerManager.removeAllCaptures(localRemovalOnly);
    }
    
    //Called by trayIcon, the above 2 methods, and the run method
    public void removeActiveCapture(Capture capture, boolean needsInterrupt, boolean persist){
        if (capture != null ){
            capture.removeIcon();
            System.out.println(new Date() + " Saving capture details " + capture);
            try {
                int durationMinutes = capture.slot.getDurationMinutes();
                new CaptureDetails(capture).updateCaptureEndEvent(capture.getSignalQualityData(), capture.getNonDotCount(), durationMinutes);
            } catch (Throwable t){
                System.out.println(new Date() + " Could not save capture details! " + t.getMessage());
                System.err.println(new Date() + " Could not save capture details! " + t.getMessage());
                t.printStackTrace();
            }
            System.out.println(new Date() + " Removing active capture " + capture);
            try {
                // stuff for finishing/removing ACTIVE captures
                capture.deConfigureDevice();
                capture.interrupt(); // removes wakeup
                if (activeCaptures.contains(capture)){
                    activeCaptures.remove(capture);
                } else {
                    StringBuffer allCapturesDebug = new StringBuffer();
                    for (Iterator<Capture> iter = activeCaptures.iterator(); iter.hasNext();) {
                        Capture aCapture = (Capture) iter.next();
                        allCapturesDebug.append(aCapture.slot + aCapture.getFileName() + " *****, ");
                    }
                    System.out.println(new Date() + " WARNING: CaptureManager.removeActiveCapture was passed a non-active capture:" + capture.slot + capture.getFileName());
                    System.out.println(new Date() + "        : Active captures: " + allCapturesDebug.toString());
                    capture.removeWakeup();
                }

                // stuff for removing ALL captures
                if (persist) {
                    tunerManager.removeCapture(capture); // Normal case.  When you remove it, you want it gone forever.
                } else if (capture instanceof CaptureHdhr) {
                    tunerManager.removeCapture(capture, persist); // persist is false.  Do not re-write the persistence file.
                }
                
                // DRS 20150718 - In case something old did not get cleared out
                List<Capture> removeList = new ArrayList<Capture>();
                for (Iterator<Capture> iter = activeCaptures.iterator(); iter.hasNext();) {
                    Capture aCapture = (Capture) iter.next();
                    if (aCapture.slot.isInThePast()) removeList.add(aCapture);
                }

                for (Capture aCapture : removeList) {
                    try {
                        if (aCapture.slot.isInThePastBy(LEAD_TIME_MS * 18)) { // DRS 20230314 - up to 18...Error when ending 6.  //DRS20171016 - doubled from 6 to 12.  Error when ending 4 at a time.
                            activeCaptures.remove(aCapture);
                            tunerManager.removeCapture(aCapture);
                            System.out.println(new Date() + " WARNING: An old active capture was removed." + aCapture.getTitle());
                        }
                    } catch (Throwable t) {
                        System.out.println(new Date() + " ERROR: Could not remove old active capture." + t.getMessage());
                    }
                }
                /* DRS 20201231 - Commented if/else - Don't do this when removing capture.  Instead, do it every loop.
                if (activeCaptures.size() == 0){
                    WakeupManager.allowSleep();
                } else {
                    for (Iterator iter = activeCaptures.iterator(); iter.hasNext();) {
                        Capture aCapture = (Capture) iter.next();
                        System.out.println(new Date() + " active capture: " + aCapture);
                    }
                    System.out.println(new Date() + " Not issuing allowSleep because there is/are " + activeCaptures.size() + " capture(s).");
                }
				*/
                
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
        // if (CaptureManager.interrupterList[0].startsWith(thisGuy)) return;
        
        // Lets keep track of who's calling interrupts on us
        for (int i = CaptureManager.interrupterList.length - 2; i >= 0 ; i--) {
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
            if (s != null && s.length() > 0) buf.append("\t" + s + "\n");
        }
        System.out.println(buf.toString());
        System.err.println(buf.toString());
        new Exception("Interrupt while sleeping.").printStackTrace();
        CaptureManager.runThread.interrupt(); // don't expect to get here, but honor interrupt request if we do
    }
    
    // DRS 20160425 - Added method - Support CwHelper Restart
    public static int getActiveWebCapturesCount() {
        return activeCaptures.size();
    }

    public String getActiveWebCapturesList() {
        StringBuffer buf = new StringBuffer("<table border=\"1\">\n");
        StringBuffer xmlBuf = new StringBuffer("\n<xml id=\"captures\">\n");
        for (Iterator<Capture> iter = activeCaptures.iterator(); iter.hasNext() && !CaptureManager.simulate;) {
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
    
    public String getWebCapturesList(String[] inputs, boolean limited){
        return getRecentWebCapturesList(-1, limited, inputs);
    }

    public String getRecentWebCapturesList(int hours, boolean limited, String[] inputs) {
        //inputs = {(String)request.get("filename"), (String)request.get("channel"), (String)request.get("title")};
        boolean noInputs = false;
        String filename = "";
        String channel = "";
        String title = "";
        if (inputs != null) {
            for (int i = 0; i < inputs.length; i++) {
                if (inputs[i] != null && !inputs[i].equals("")) {
                    noInputs = false;
                    if (i == 0 && inputs[0] != null) filename = inputs[0];
                    if (i == 1 && inputs[1] != null) channel = inputs[1];
                    if (i == 2 && inputs[2] != null) title = inputs[2];
                }
            }
        } else {
            noInputs = true;
        }
        
        StringBuffer buf = new StringBuffer("<table border=\"1\">\n");
        StringBuffer xmlBuf = new StringBuffer("\n<xml id=\"recentCaptures\">\n");
        boolean headersDone = false;
        long nowMs = new Date().getTime();

        TreeMap<String, CaptureDetails> captures = null;
        if (noInputs && limited) {
            captures = captureDataManager.getRecentCaptures("top 50");
        } else if (noInputs && !limited) {
            captures = captureDataManager.getRecentCaptures("");
        } else if (limited) {
            captures = captureDataManager.getRecentCaptures("top 50", filename, channel, title);
        } else {
            captures = captureDataManager.getRecentCaptures("", filename, channel, title);
        }
        
        for (Iterator<CaptureDetails> iter = captures.descendingMap().values().iterator(); iter.hasNext();) {
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

    
    public static void setSimulate(boolean simulate){
    	CaptureManager.simulate = simulate;
        saveSettings();
    }
    
    public static void setSleepManaged(boolean isSleepManaged) {
        if (!CaptureManager.useHdhrCommandLine) {
            System.out.println(new Date() + " the isSleepManaged feature is not configurable with the current configuration.");
            return;
        }
        CaptureManager.isSleepManaged = isSleepManaged;
        saveSettings();
    }
    
    public static void setMyhdWakeup(boolean myhdWakeup) {
        CaptureManager.myhdWakeup = myhdWakeup;
        saveSettings();
    }
    
    // DRS 20210301 -  Removed - No longer needed (used with now removed ClockChecker)
    //public static void setRecreateIconUponWakeup(boolean recreateIconUponWakeup) {
    //    CaptureManager.recreateIconUponWakeup = recreateIconUponWakeup;
    //    saveSettings();
    //}
    
    public static void setAllTraditionalHdhr(boolean allTraditionalHdhr) {
        if (!CaptureManager.useHdhrCommandLine) {
            System.out.println(new Date() + " the allTraditionalHdhr feature is not configurable with the current configuration.");
            return;
        }
        CaptureManager.allTraditionalHdhr = allTraditionalHdhr;
        saveSettings();
    }
    
    public static void setUnlockWithForce(boolean unlockWithForce) {
        CaptureManager.unlockWithForce = unlockWithForce;
        saveSettings();
    }
    
    public static void setRerunDiscover(boolean rerunDiscover) {
        CaptureManager.rerunDiscover = rerunDiscover;
        saveSettings();
    }
    
    public static void setEndFusionWatchEvents(boolean endFusionWatchEvents) {
        CaptureManager.endFusionWatchEvents = endFusionWatchEvents;
        saveSettings();
    }
    
    public static void setTrayIcon(boolean trayIcon) {
        CaptureManager.trayIcon = trayIcon;
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
    
    public static void setDiscoverRetries(int discoverRetries) {
        CaptureManager.discoverRetries = discoverRetries;
        saveSettings();
    }

    public static void setHdhrRecordMonitorSeconds(int hdhrRecordMonitorSeconds) {
        CaptureManager.hdhrRecordMonitorSeconds = hdhrRecordMonitorSeconds;
        saveSettings();
    }
    
    public static void setHdhrBadRecordingPercent(float hdhrBadRecordingPercent) {
        CaptureManager.hdhrBadRecordingPercent = hdhrBadRecordingPercent;
        saveSettings();
    }
    
    public static void setAlternateChannels(String alternateChannels) {
        CaptureManager.alternateChannels = alternateChannels;
        saveSettings();
    }

    public static void setDiscoverDelay(int discoverDelay) {
        CaptureManager.discoverDelay = discoverDelay;
        saveSettings();
    }
    
    public static void setClockOffset(int clockOffset) {
        CaptureManager.clockOffset = clockOffset;
        saveSettings();
    }
    
    public static void setLeadTimeSeconds(int leadTimeSeconds) {
        CaptureManager.leadTimeSeconds = leadTimeSeconds;
        CaptureManager.loopLeadTimeSeconds = leadTimeSeconds - LL_OFFSET;
        saveSettings();
    }
    
    
    //public static void setPollIntervalSeconds(int pollIntervalSeconds) {
    //    CaptureManager.pollIntervalSeconds = pollIntervalSeconds;
    //    saveSettings();
    //}
    
    public static void saveSettings(){
        System.out.println(new Date() + " Saving settings.");
        Properties props = new Properties();
        String nextProperty = "";
        try {nextProperty = "simulate"; props.put(nextProperty, "" + CaptureManager.simulate); } catch (Throwable t) {System.out.println(new Date() + "ERROR: Failed to save property " + nextProperty + ".");}
        try {nextProperty = "isSleepManaged"; props.put(nextProperty, "" + CaptureManager.isSleepManaged);} catch (Throwable t) {System.out.println(new Date() + "ERROR: Failed to save property " + nextProperty + ".");}
        try {nextProperty = "fusionLeadTime"; props.put(nextProperty, "" + CaptureManager.fusionLeadTime);} catch (Throwable t) {System.out.println(new Date() + "ERROR: Failed to save property " + nextProperty + ".");}
        try {nextProperty = "leadTimeSeconds"; props.put(nextProperty, "" + CaptureManager.leadTimeSeconds);} catch (Throwable t) {System.out.println(new Date() + "ERROR: Failed to save property " + nextProperty + ".");}
        try {nextProperty = "endFusionWatchEvents"; props.put(nextProperty, "" + CaptureManager.endFusionWatchEvents);} catch (Throwable t) {System.out.println(new Date() + "ERROR: Failed to save property " + nextProperty + ".");}
        try {nextProperty = "trayIcon"; props.put(nextProperty, "" + CaptureManager.trayIcon);} catch (Throwable t) {System.out.println(new Date() + "ERROR: Failed to save property " + nextProperty + ".");}
        try {nextProperty = "shortenExternalRecordingsSeconds"; props.put(nextProperty, "" + CaptureManager.shortenExternalRecordingsSeconds);} catch (Throwable t) {System.out.println(new Date() + "ERROR: Failed to save property " + nextProperty + ".");}
        try {nextProperty = "discoverRetries"; props.put(nextProperty,  "" + CaptureManager.discoverRetries);} catch (Throwable t) {System.out.println(new Date() + "ERROR: Failed to save property " + nextProperty + ".");}
        try {nextProperty = "discoverDelay"; props.put(nextProperty,  "" + CaptureManager.discoverDelay);} catch (Throwable t) {System.out.println(new Date() + "ERROR: Failed to save property " + nextProperty + ".");}
        try {nextProperty = "clockOffset"; props.put(nextProperty,  "" + CaptureManager.clockOffset);} catch (Throwable t) {System.out.println(new Date() + "ERROR: Failed to save property " + nextProperty + ".");}
        try {nextProperty = "myhdWakeup"; props.put(nextProperty,  "" + CaptureManager.myhdWakeup);} catch (Throwable t) {System.out.println(new Date() + "ERROR: Failed to save property " + nextProperty + ".");}
        //try {nextProperty = "recreateIconUponWakeup"; props.put(nextProperty,  "" + CaptureManager.recreateIconUponWakeup);} catch (Throwable t) {System.out.println(new Date() + "ERROR: Failed to save property " + nextProperty + ".");} DRS 20210301 -  Removed - No longer needed (used with now removed ClockChecker)
        try {nextProperty = "allTraditionalHdhr"; props.put(nextProperty,  "" + CaptureManager.allTraditionalHdhr);} catch (Throwable t) {System.out.println(new Date() + "ERROR: Failed to save property " + nextProperty + ".");}
        try {nextProperty = "unlockWithForce"; props.put(nextProperty,  "" + CaptureManager.unlockWithForce);} catch (Throwable t) {System.out.println(new Date() + "ERROR: Failed to save property " + nextProperty + ".");}
        try {nextProperty = "rerunDiscover"; props.put(nextProperty,  "" + CaptureManager.rerunDiscover);} catch (Throwable t) {System.out.println(new Date() + "ERROR: Failed to save property " + nextProperty + ".");}
        try {nextProperty = "hdhrRecordMonitorSeconds"; props.put(nextProperty, "" + CaptureManager.hdhrRecordMonitorSeconds);} catch (Throwable t) {System.out.println(new Date() + "ERROR: Failed to save property " + nextProperty + ".");}
        try {nextProperty = "hdhrBadRecordingPercent"; props.put(nextProperty, "" + CaptureManager.hdhrBadRecordingPercent);} catch (Throwable t) {System.out.println(new Date() + "ERROR: Failed to save property " + nextProperty + ".");}
        try {nextProperty = "alternateChannels"; props.put(nextProperty, CaptureManager.alternateChannels);} catch (Throwable t) {System.out.println(new Date() + "ERROR: Failed to save property " + nextProperty + ".");}
        //try {nextProperty = "pollIntervalSeconds"; props.put(nextProperty, "" + CaptureManager.pollIntervalSeconds);} catch (Throwable t) {System.out.println(new Date() + "ERROR: Failed to save property " + nextProperty + ".");}
        
        System.out.println(new Date() + " There are " +  props.size() + " property value being saved.");
        String path = "";
        if (!CaptureManager.dataPath.equals("")){
            path = CaptureManager.dataPath + File.separator;
        }
        try {
            System.out.println(new Date() + " Saving to " + path + CaptureManager.propertiesFileName);
            Writer writer = new FileWriter(path + CaptureManager.propertiesFileName);
            props.store(writer, CaptureManager.propertiesFileName);
            writer.close();
        } catch (Exception e) {
            System.out.println(new Date() + " ERROR: Could not save CaptureManager properties. " + e.getMessage());
            System.err.println(new Date() + " ERROR: Could not save CaptureManager properties. " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void loadSettings() {
        String path = "";
        if (!CaptureManager.dataPath.equals("")){
            path = CaptureManager.dataPath + File.separator;
        }
        //DRS 20220707 - Set instance variable to know if traditional command line available.
        if (!new File(CaptureManager.hdhrPath + File.separator + "hdhomerun_config.exe").exists()) {
            CaptureManager.useHdhrCommandLine = false;
            CaptureManager.allTraditionalHdhr = false;
            CaptureManager.isSleepManaged = true;
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
            for (Iterator<?> iter = props.keySet().iterator(); iter.hasNext();) {
                String key = (String) iter.next();
                if (key !=null && key.equals("useHdhrCommandLine")) {
                    CaptureManager.useHdhrCommandLine = Boolean.parseBoolean(props.getProperty(key));
                    if (CaptureManager.useHdhrCommandLine == false) {
                        CaptureManager.allTraditionalHdhr = false;
                        CaptureManager.isSleepManaged = true;
                    }
                } else if (key != null && key.equals("simulate")){
                    CaptureManager.simulate = Boolean.parseBoolean(props.getProperty(key));
                } else if (key != null && key.equals("isSleepManaged") && !CaptureManager.useHdhrCommandLine){
                    CaptureManager.isSleepManaged = true;
                } else if (key != null && key.equals("isSleepManaged")){
                    CaptureManager.isSleepManaged = Boolean.parseBoolean(props.getProperty(key));
                } else if (key != null && key.equals("fusionLeadTime")){
                    CaptureManager.fusionLeadTime = Integer.parseInt(props.getProperty(key));
                } else if (key != null && key.equals("leadTimeSeconds")){
                    CaptureManager.leadTimeSeconds = Integer.parseInt(props.getProperty(key));
                    CaptureManager.loopLeadTimeSeconds = CaptureManager.leadTimeSeconds - LL_OFFSET;
                //} else if (key != null && key.equals("pollIntervalSeconds")){
                //    CaptureManager.pollIntervalSeconds = Integer.parseInt(props.getProperty(key));
                } else if (key != null && key.equals("endFusionWatchEvents")){
                    CaptureManager.endFusionWatchEvents = Boolean.parseBoolean(props.getProperty(key));
                } else if (key != null && key.equals("shortenExternalRecordingsSeconds")){
                    CaptureManager.shortenExternalRecordingsSeconds = Integer.parseInt(props.getProperty(key));
                } else if (key != null && key.equals("discoverRetries")){
                    CaptureManager.discoverRetries = Integer.parseInt(props.getProperty(key));
                } else if (key != null && key.equals("hdhrRecordMonitorSeconds")){
                    CaptureManager.hdhrRecordMonitorSeconds = Integer.parseInt(props.getProperty(key));
                } else if (key != null && key.equals("hdhrBadRecordingPercent")){
                    CaptureManager.hdhrBadRecordingPercent = Float.parseFloat(props.getProperty(key));
                } else if (key != null && key.equals("alternateChannels")){
                    CaptureManager.alternateChannels = props.getProperty(key);
                } else if (key != null && key.equals("discoverDelay")){
                    CaptureManager.discoverDelay = Integer.parseInt(props.getProperty(key));
                } else if (key != null && key.equals("clockOffset")){
                    CaptureManager.clockOffset = Integer.parseInt(props.getProperty(key));
                } else if (key != null && key.equals("trayIcon")){
                    CaptureManager.trayIcon = Boolean.parseBoolean(props.getProperty(key));
                //} else if (key != null && key.equals("recreateIconUponWakeup")){
                //    CaptureManager.recreateIconUponWakeup = Boolean.parseBoolean(props.getProperty(key)); DRS 20210301 -  Removed - No longer needed (used with now removed ClockChecker) 
                } else if (key != null && key.equals("allTraditionalHdhr") && !CaptureManager.useHdhrCommandLine){
                    CaptureManager.allTraditionalHdhr = false;
                } else if (key != null && key.equals("allTraditionalHdhr")){
                    CaptureManager.allTraditionalHdhr = Boolean.parseBoolean(props.getProperty(key));
                } else if (key != null && key.equals("rerunDiscover")){
                    CaptureManager.rerunDiscover = Boolean.parseBoolean(props.getProperty(key));
                } else if (key != null && key.equals("unlockWithForce")){
                    CaptureManager.unlockWithForce = Boolean.parseBoolean(props.getProperty(key));
                } else if (key != null && key.equals("myhdWakeup")){
                    CaptureManager.myhdWakeup = Boolean.parseBoolean(props.getProperty(key));
                    if (!CaptureManager.myhdWakeup) System.out.println(new Date() + " Machine wake-up for MyHD events will skipped.");
                }
            }
            reader.close();
        } catch (Exception e) {
            System.out.println(new Date() + " ERROR: Could not load optional CaptureManager properties.  Using defaults. " + e.getMessage());
            System.err.println(new Date() + " ERROR: Could not load optional CaptureManager properties.  Using defaults. ");
            e.printStackTrace();
        }
    }

    private static String getVersionFromResource() {
        String version = "0,0,0,0";
        InputStream versionTextStream = CaptureHdhr.class.getClassLoader().getResourceAsStream("version.txt");
        if (versionTextStream != null){
            BufferedReader reader = new BufferedReader(new InputStreamReader(versionTextStream));
            try {
                version = reader.readLine();   
            } catch (Exception e) {
                System.out.println(new Date() + " WARNING: problem reading version.txt.  Version not set. " + e.getMessage());
            } finally {
                try {reader.close();} catch (Throwable t) {}
            }
        } else {
            System.out.println(new Date() + " WARNING: version.txt not found.  Version not set.");
        }
        return version;
    }
    
    public static long getPcClockOffsetSeconds(int timeoutSeconds) {
        PcClockSetter.getInstance(); // New thread created
        while (PcClockSetter.isRunning() && timeoutSeconds-- > 0) {
            try {Thread.sleep(1000); } catch (Exception e) {}
        }
        long offsetSeconds = PcClockSetter.getOffsetFromInternet();
        PcClockSetter.finish(); // interrupts thread
        return offsetSeconds;
    }
    
    public static String alterPcClock(int requestedOffset, int timeoutSeconds, boolean logResults) {
        PcClockSetter.getInstance(requestedOffset); // New thread created
        while (PcClockSetter.isRunning() && timeoutSeconds-- > 0) {
            try {Thread.sleep(1000); } catch (Exception e) {}
        }
        String result = PcClockSetter.adjustPcClock(5);
        if (logResults) System.out.println(new Date() + " " + result);
        PcClockSetter.finish(); // interrupts thread
        return result;
    }

    public static void setEmailer(Emailer object) {
        emailer = object;
    }

    public static Emailer getEmailer() {
        return emailer;
    }

    public static void setWakeupEvent(WakeupEvent object) { // called from tinyConnection /wakeupevent [for old OS's, and now new OS's as of 20210105]
        CaptureManager.wakeupEventMessageDone = false;
        wakeupEvent = object;
        requestInterrupt("setWakeupEvent");
    }

    public static void registerKeepAwakeEvent() {
        if (wakeupEvent == null && !wakeupEventMessageDone) {
            System.out.println(new Date() + " ERROR: Wakeup event never defined!  This machine will have NO WAKEUP EVENT CAPABILITY.");
            CaptureManager.wakeupEventMessageDone = true;
        } else if (WakeupEvent.isRunning) {
            wakeupEvent.interruptTimer(WakeupEvent.RESET);
        } else if (!WakeupEvent.hasCommand()){
            new Thread(wakeupEvent, "Thread-WakupEvent" ).start();
            try {Thread.sleep(600);}catch(Exception e){}
        }
    }

    public static float xGetWindowsVersion(boolean quiet) {
        Float windowsVersion = 6F;
        String cleanedVersion = System.getProperty("os.name").replaceAll(".*?([\\d.]+).*", "$1");
        if (System.getProperty("os.name").contains("Windows NT")) cleanedVersion = "10";
        if ("Windows XP".equals(cleanedVersion)) {
            if (!quiet) System.out.println(new Date() + " Windows XP detected, usingOldOs is true.");
            return windowsVersion;
        } else {
            try {
                windowsVersion = Float.parseFloat(cleanedVersion);
            } catch (Exception e){
                if (!quiet) {
                    System.out.println(new Date() + " getWindowsVersion() could not parse OS name [" + cleanedVersion + "], returning " + windowsVersion + " (usingOldOS will be true)");
                    System.err.println(new Date() + " getWindowsVersion() could not parse OS name [" + cleanedVersion + "], returning " + windowsVersion + " (usingOldOS will be true)");
                }
            }
        }
        if (!quiet) System.out.println(new Date() + " Version " + windowsVersion +" detected, usingOldOs is " + (windowsVersion < 7F) +".  Java version " + System.getProperty("java.version") + " located at " + System.getProperty("java.class.path"));
        return windowsVersion;
    }

    
    public String getProperties(boolean plain) {
        if (plain) {
            StringBuffer buf2 = new StringBuffer();
            buf2.append("dataPath=\"" + dataPath + "\"\n");
            buf2.append("hdhrPath=\"" + hdhrPath + "\"\n"); 
            buf2.append("sleepManaged=\"" + CaptureManager.isSleepManaged + "\"\n"); 
            buf2.append("leadTime=\"" + CaptureManager.leadTimeSeconds + "\"\n"); 
            buf2.append("fusionLeadTime=\"" + CaptureManager.fusionLeadTime + "\"\n"); 
            buf2.append("simulate=\"" + CaptureManager.simulate + "\"\n"); 
            buf2.append("endFusionWatchEvents=\"" + CaptureManager.endFusionWatchEvents + "\"\n"); 
            buf2.append("trayIcon=\"" + CaptureManager.trayIcon + "\"\n"); 
            buf2.append("shortenExternalRecordingsSeconds=\"" + CaptureManager.shortenExternalRecordingsSeconds + "\"\n"); 
            buf2.append("discoverRetries=\"" + CaptureManager.discoverRetries + "\"\n"); 
            buf2.append("discoverDelay=\"" + CaptureManager.discoverDelay + "\"\n"); 
            buf2.append("clockOffset=\"" + CaptureManager.clockOffset + "\"\n"); 
            buf2.append("myhdWakeup=\"" + CaptureManager.myhdWakeup + "\"\n"); 
            buf2.append("hdhrRecordMonitorSeconds=\"" + CaptureManager.hdhrRecordMonitorSeconds + "\"\n"); 
            buf2.append("hdhrBadRecordingPercent=\"" + CaptureManager.hdhrBadRecordingPercent + "\"\n"); 
            buf2.append("alternateChannels=\"" + CaptureManager.alternateChannels + "\"\n"); 
            //buf2.append("pollIntervalSeconds=\"" + CaptureManager.pollIntervalSeconds + "\"\n"); 
            //buf2.append("recreateIconUponWakeup=\"" + CaptureManager.recreateIconUponWakeup + "\"\n"); DRS 20210301 -  Removed - No longer needed (used with now removed ClockChecker)
            buf2.append("allTraditionalHdhr=\"" + CaptureManager.allTraditionalHdhr + "\"\n"); 
            buf2.append("rerunDiscover=\"" + CaptureManager.rerunDiscover + "\"\n"); 
            buf2.append("userHdhrCommandLine=\"" + CaptureManager.useHdhrCommandLine + "\"\n"); 
            buf2.append("unlockWithForce=\"" + CaptureManager.unlockWithForce + "\"\n");
            return buf2.toString();
        }

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
                "<tr><td>TrayIcon:</td><td>" + CaptureManager.trayIcon + "</td></tr>" + 
                "<tr><td>ShortenExternalRecordingsSeconds:</td><td>" + CaptureManager.shortenExternalRecordingsSeconds + "</td></tr>" + 
                "<tr><td>DiscoverRetries:</td><td>" + CaptureManager.discoverRetries + "</td></tr>" + 
                "<tr><td>DiscoverDelay:</td><td>" + CaptureManager.discoverDelay + "</td></tr>" + 
                "<tr><td>ClockOffset:</td><td>" + CaptureManager.clockOffset + "</td></tr>" + 
                "<tr><td>MyhdWakeup:</td><td>" + CaptureManager.myhdWakeup + "</td></tr>" + 
                "<tr><td>HdhrRecordMonitorSeconds:</td><td>" + CaptureManager.hdhrRecordMonitorSeconds + "</td></tr>" + 
                "<tr><td>HdhrBadRecordingPercent:</td><td>" + CaptureManager.hdhrBadRecordingPercent + "</td></tr>" + 
                "<tr><td>AlternateChannels:</td><td>" + CaptureManager.alternateChannels + "</td></tr>" + 
                //"<tr><td>PollIntervalSeconds:</td><td>" + CaptureManager.pollIntervalSeconds + "</td></tr>" + 
                //"<tr><td>RecreateIconUponWakeup:</td><td>" + CaptureManager.recreateIconUponWakeup + "</td></tr>" + DRS 20210301 -  Removed - No longer needed (used with now removed ClockChecker)
                "<tr><td>AllTraditionalHdhr:</td><td>" + CaptureManager.allTraditionalHdhr + "</td></tr>" + 
                "<tr><td>RerunDiscover:</td><td>" + CaptureManager.rerunDiscover + "</td></tr>" + 
                "<tr><td>UseHdhrCommandLine:</td><td>" + CaptureManager.useHdhrCommandLine + "</td></tr>" + 
                "<tr><td>UnlockWithForce:</td><td>" + CaptureManager.unlockWithForce + "</td></tr>" + 
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
                "trayIcon=\"" + CaptureManager.trayIcon + "\" " + 
                "shortenExternalRecordingsSeconds=\"" + CaptureManager.shortenExternalRecordingsSeconds + "\" " + 
                "discoverRetries=\"" + CaptureManager.discoverRetries + "\" " + 
                "discoverDelay=\"" + CaptureManager.discoverDelay + "\" " + 
                "clockOffset=\"" + CaptureManager.clockOffset + "\" " + 
                "myhdWakeup=\"" + CaptureManager.myhdWakeup + "\" " + 
                "hdhrRecordMonitorSeconds=\"" + CaptureManager.hdhrRecordMonitorSeconds + "\" " + 
                "hdhrBadRecordingPercent=\"" + CaptureManager.hdhrBadRecordingPercent + "\" " + 
                "alternateChannels=\"" + CaptureManager.alternateChannels + "\" " + 
                //"pollIntervalSeconds=\"" + CaptureManager.pollIntervalSeconds + "\" " + 
                //"recreateIconUponWakeup=\"" + CaptureManager.recreateIconUponWakeup + "\" " + DRS 20210301 -  Removed - No longer needed (used with now removed ClockChecker)
                "allTraditionalHdhr=\"" + CaptureManager.allTraditionalHdhr + "\" " + 
                "rerunDiscover=\"" + CaptureManager.rerunDiscover + "\" " + 
                "userHdhrCommandLine=\"" + CaptureManager.useHdhrCommandLine + "\" " + 
                "unlockWithForce=\"" + CaptureManager.unlockWithForce + "\" " + 
                "/>\n");
        buf.append("</table>\n");
        xmlBuf.append("</xml>\n");
        return new String(buf) + new String(xmlBuf);
    }

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

        System.out.println(new Date() + " Setting sun.java2d.3d3 to false.");
        System.setProperty("sun.java2d.d3d", "false"); 
        
        // DRS 20250110 - Moved 1 down and added to it TrayIconManager - Issue #56
        //new SplashScreenCloser(2); // starts itself waits 2 seconds and closes the splash window
        
        // DRS 20210306 - Move this to the ServiceLauncher
        //CaptureManager.webServer = new TinyWebServer(WEB_SERVER_PORT); 
        //if (!webServer.start()) {
        //    System.out.println("The web server port was taken, so we presume a copy of CwHelper is already running.  We will quit.");
        //    System.exit(0);
        //}

        System.setProperty("java.awt.headless", "false"); // DRS 20190327 - Possibly help on Win10 with TrayIcons
        
    	// By creating the instance of CaptureManager, we
    	// also create the instance of TunerManager.  If there
    	// is no device on the network, simulate mode is set.
        CaptureManager cm = CaptureManager.getInstance(cwepgPathFinal, hdhrPathFinal, dataPathFinal); // runs tunerManager.countTuners() here, which runs LineupHdhr.scan()
        // DRS 20250110 - Moved 1 here instead of above. Splash shows when trayIcon is started or in this line if no trayIcon configured - Issue #56
        if (!CaptureManager.trayIcon) new SplashScreenCloser(2); // starts itself waits 2 seconds and closes the splash window

        TunerManager tm = TunerManager.getInstance();
        
        // We need to get the channel list loaded.  The default
        // is to build the channels from the file saved during
        // the last scan.  Scan will not be performed unless 
        // the user initiates it because it takes a long time.
        // DRS 20240320 - Commented 1 - No longer required because it happens when CaptureManager starts up.
        // tm.loadChannelsFromFile(); // runs scanRefreshLineUpTm(), which runs LineupHdhr.scan()
        
        // If the user has defined alternate channels, process those now.
        // Alternate channels are ones that are on a different frequency, 
        // but contain duplicate programming, such as a "repeater".
        // In the case of poor reception on one of the pair, the other 
        // will be tried if the system is configured to create new captures
        // on poor signal quality.
        tm.addAltChannels(alternateChannels);
        
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
