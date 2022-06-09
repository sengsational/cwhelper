package org.cwepg.svc;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.net.URLDecoder;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;

import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.cwepg.hr.Capture;
import org.cwepg.hr.CaptureManager;
import org.cwepg.hr.DbCopier;
import org.cwepg.hr.Emailer;
import org.cwepg.hr.ShutdownHookThread;
import org.cwepg.hr.Slot;
import org.cwepg.hr.Target;
import org.cwepg.hr.Tuner;
import org.cwepg.hr.TunerExternal;
import org.cwepg.hr.TunerFusion;
import org.cwepg.hr.TunerManager;
import org.cwepg.hr.WakeupEvent;
import org.cwepg.reg.Registry;

class TinyConnection implements Runnable {
	protected Socket client;
	protected BufferedReader in;
	protected PrintStream out;
    protected CaptureManager captureManager;
    protected TunerManager tunerManager;
	private static final String HEAD = "HTTP/1.0 200 OK\nContent-type: text/html\n\n<html><body><h2>CW_EPG Helper Interface</h2><br>";
    private static final String HEAD_SHORT = "HTTP/1.0 200 OK\nContent-type: text/html\n\n";
    private static final String FOOT = "</body></html>";
    private static final SimpleDateFormat clock = new SimpleDateFormat("HH:mm:ss");
    private static final Object locker = new Object();
    protected static String mLastRequest = "";

	public TinyConnection(Socket client_socket) {
		this.client = client_socket;
        this.captureManager = CaptureManager.getInstance();
        this.tunerManager = TunerManager.getInstance();
        
		try {
			in = new BufferedReader(new InputStreamReader(new DataInputStream(client.getInputStream())));
			out = new PrintStream(client.getOutputStream());
		} catch (IOException e) {
			System.err.println(e);
            try {in.close();} catch (Exception e2) {/* ignore */}
            try {out.close();} catch (Exception e2) {/* ignore */}
            try {client.close();} catch (IOException e2) {/* ignore */}
			return;
		}
		(new Thread(this)).start();
	}

	public void run() {
		String line = null; // read buffer
		String req = null; // first line of request
		try {
			req = in.readLine();
			String requestMsg =  "request [" + req + "] " + clock.format(new Date());
			if(!mLastRequest.equals(requestMsg)) System.out.println(requestMsg);
            mLastRequest = requestMsg;

			// loop through and discard rest of request
			line = req;
			while (line != null && line.length() > 0) {
				line = in.readLine();
				//System.out.println("other data [" + line + "]");
			}

			if (req == null) throw new Exception(new Date() + " The request for this connection was null."); //DRS 20150419 - Added 1 - Prevent raw null pointer error.
			
			// get properties map 
            Map request = getRequestMap(req);
            String action = (String)request.get("action");
            if (action == null || !action.startsWith("/")) throw new Exception(new Date() + " The action was null or did not start with /.");
            
            if (action.equals("/set")){ // ************* SET ***************
                String setItem = null;
                int goodSettingCount = 0;
                setItem = (String)request.get("simulate");
                if (setItem != null) {
                    goodSettingCount++;
                    if (setItem.equals("true")){
                        CaptureManager.setSimulate(true);
                        out.print(HEAD + "simulate=true" + FOOT);
                    } else if (setItem.equals("false")){
                        CaptureManager.setSimulate(false);
                        out.print(HEAD + "simulate=false" + FOOT);
                    }
                }

                setItem = (String)request.get("sleepmanaged");
                if (setItem != null) {
                    goodSettingCount++;
                    if (setItem.equals("true")){
                        CaptureManager.setSleepManaged(true);
                        out.print(HEAD + "sleepmanaged=true" + FOOT);
                    } else if (setItem.equals("false")){
                        CaptureManager.setSleepManaged(false);
                        out.print(HEAD + "sleepmanaged=false" + FOOT);
                    }
                }
                
                setItem = (String)request.get("myhdwakeup");
                if (setItem != null ) {
                    goodSettingCount++;
                    if (setItem.equals("true")){
                        CaptureManager.setMyhdWakeup(true);
                        out.print(HEAD + "myhdwakeup=true" + FOOT);
                    } else if (setItem.equals("false")){
                        CaptureManager.setMyhdWakeup(false);
                        out.print(HEAD + "myhdwakeup=false" + FOOT);
                    }
                }
                
                setItem = (String)request.get("endfusionwatchevents");
                if (setItem != null ) {
                    goodSettingCount++;
                    if (setItem.equals("true")){
                        CaptureManager.setEndFusionWatchEvents(true);
                        out.print(HEAD + "endfusionwatchevents=true" + FOOT);
                    } else if (setItem.equals("false")){
                        CaptureManager.setEndFusionWatchEvents(false);
                        out.print(HEAD + "endfusionwatchevents=false" + FOOT);
                    }
                }
                
                setItem = (String)request.get("trayicon");
                if (setItem != null ) {
                    goodSettingCount++;
                    if (setItem.equals("true")){
                        CaptureManager.setTrayIcon(true);
                        out.print(HEAD + "trayIcon=true" + FOOT);
                    } else if (setItem.equals("false")){
                        CaptureManager.setTrayIcon(false);
                        out.print(HEAD + "trayIcon=false" + FOOT);
                    }
                }
                
                // DRS 20210301 -  Removed - No longer needed (used with now removed ClockChecker)
                //setItem = (String)request.get("recreateiconuponwakeup");
                //if (setItem != null ) {
                //    goodSettingCount++;
                //    if (setItem.equals("true")){
                //        CaptureManager.setRecreateIconUponWakeup(true);
                //        out.print(HEAD + "recreateIconUponWakeup=true" + FOOT);
                //    } else if (setItem.equals("false")){
                //        CaptureManager.setRecreateIconUponWakeup(false);
                //        out.print(HEAD + "recreateIconUponWakeup=false" + FOOT);
                //    }
                //}
                
                setItem = (String)request.get("alltraditionalhdhr");
                if (setItem != null ) {
                    goodSettingCount++;
                    if (setItem.equals("true")){
                        CaptureManager.setAllTraditionalHdhr(true);
                        out.print(HEAD + "allTraditionalHdhr=true" + FOOT);
                    } else if (setItem.equals("false")){
                        CaptureManager.setAllTraditionalHdhr(false);
                        out.print(HEAD + "allTraditionalHdhr=false" + FOOT);
                    }
                }
                
                setItem = (String)request.get("rerundiscover");
                if (setItem != null ) {
                    goodSettingCount++;
                    if (setItem.equals("true")){
                        CaptureManager.setRerunDiscover(true);
                        out.print(HEAD + "rerunDiscover=true" + FOOT);
                    } else if (setItem.equals("false")){
                        CaptureManager.setRerunDiscover(false);
                        out.print(HEAD + "rerunDiscover=false" + FOOT);
                    }
                }
                
                setItem = (String)request.get("leadtime");
                if (setItem != null){
                    goodSettingCount++;
                    String message = "leadtime=";
                    try {
                        int leadTimeSeconds = Integer.parseInt(setItem);
                        if (leadTimeSeconds < 20) throw new Exception("leadtime must be 20 seconds or more.");
                        CaptureManager.setLeadTimeSeconds(leadTimeSeconds);
                        message = message + leadTimeSeconds;
                    } catch (Throwable t){
                        message = message + "(ERROR) " + t.getMessage();
                    }
                    out.print(HEAD + message + FOOT);
                }

                setItem = (String)request.get("fusionleadtime");
                if (setItem != null){
                    goodSettingCount++;
                    String message = "fusionleadtime=";
                    try {
                        int leadTimeSeconds = Integer.parseInt(setItem);
                        CaptureManager.setFusionLeadTime(leadTimeSeconds);
                        message = message + leadTimeSeconds;
                    } catch (Throwable t){
                        message = message + "(ERROR) " + t.getMessage();
                    }
                    out.print(HEAD + message + FOOT);
                }

                setItem = (String)request.get("shortenexternalrecordingsseconds");
                if (setItem != null){
                    goodSettingCount++;
                    String message = "shortenexternalrecordingsseconds=";
                    try {
                        int leadTimeSeconds = Integer.parseInt(setItem);
                        CaptureManager.setShortenExternalRecordingsSeconds(leadTimeSeconds);
                        message = message + leadTimeSeconds;
                    } catch (Throwable t){
                        message = message + "(ERROR) " + t.getMessage();
                    }
                    out.print(HEAD + message + FOOT);
                }
                
                setItem = (String)request.get("discoverretries");
                if (setItem != null){
                    goodSettingCount++;
                    String message = "discoverretries=";
                    try {
                        int discoverRetries = Integer.parseInt(setItem);
                        CaptureManager.setDiscoverRetries(discoverRetries);
                        message = message + discoverRetries;
                    } catch (Throwable t){
                        message = message + "(ERROR) " + t.getMessage();
                    }
                    out.print(HEAD + message + FOOT);
                }
                
                setItem = (String)request.get("hdhrrecordmonitorseconds");
                if (setItem != null){
                    goodSettingCount++;
                    String message = "hdhrrecordmonitorseconds=";
                    try {
                        int proposedHdhrRecordMonitorSeconds = Integer.parseInt(setItem);
                        int currentSetting = CaptureManager.hdhrRecordMonitorSeconds;
                        if (proposedHdhrRecordMonitorSeconds <= 0) { 
                            CaptureManager.setHdhrRecordMonitorSeconds(proposedHdhrRecordMonitorSeconds);
                            message = "Removed any existing alternative channels.  No backup recordings will take place.";
                            TunerManager.getInstance().clearAltChannels();
                        } else if (currentSetting <= 0){
                            CaptureManager.setHdhrRecordMonitorSeconds(proposedHdhrRecordMonitorSeconds);
                            message = "Added alternative channels in support of backup recordings.";
                            TunerManager.getInstance().addAltChannels(CaptureManager.alternateChannels);
                        } else {
                            CaptureManager.setHdhrRecordMonitorSeconds(proposedHdhrRecordMonitorSeconds);
                            message = message + proposedHdhrRecordMonitorSeconds;
                        }
                    } catch (Throwable t){
                        message = message + "(ERROR) " + t.getMessage();
                    }
                    out.print(HEAD + message + FOOT);
                }
                
                setItem = (String)request.get("hdhrbadrecordingpercent");
                if (setItem != null){
                    goodSettingCount++;
                    String message = "hdhrbadrecordingpercent=";
                    try {
                        float hdhrBadRecordingPercent = Float.parseFloat(setItem);
                        CaptureManager.setHdhrBadRecordingPercent(hdhrBadRecordingPercent);
                        message = message + hdhrBadRecordingPercent;
                    } catch (Throwable t){
                        message = message + "(ERROR) " + t.getMessage();
                    }
                    out.print(HEAD + message + FOOT);
                }
                
                setItem = (String)request.get("alternatechannels");
                if (setItem != null){
                    goodSettingCount++;
                    String message = "alternatechannels=";
                    try {
                        String alternateChannels = setItem;
                        CaptureManager.setAlternateChannels(alternateChannels);
                        message = message + alternateChannels;
                    } catch (Throwable t){
                        message = message + "(ERROR) " + t.getMessage();
                    }
                    out.print(HEAD + message + FOOT);
                }
                
                setItem = (String)request.get("discoverdelay");
                if (setItem != null){
                    goodSettingCount++;
                    String message = "discoverdelay=";
                    try {
                        int discoverDelay = Integer.parseInt(setItem);
                        CaptureManager.setDiscoverDelay(discoverDelay);
                        message = message + discoverDelay;
                    } catch (Throwable t){
                        message = message + "(ERROR) " + t.getMessage();
                    }
                    out.print(HEAD + message + FOOT);
                }
                
                setItem = (String)request.get("clockoffset");
                if (setItem != null){
                    goodSettingCount++;
                    String message = "clockoffset=";
                    try {
                        int requestedOffset = Integer.parseInt(setItem);
                        CaptureManager.setClockOffset(requestedOffset);
                        message = message + requestedOffset;
                        String clockAlterResponse = "";
                        try {
                            int timeoutSeconds = 2;
                            clockAlterResponse = CaptureManager.alterPcClock(requestedOffset, timeoutSeconds, false);
                        } catch (Throwable t) {
                            clockAlterResponse = "ERROR: Unable to get time from the Interent. " +  t.getMessage();
                        }
                        message += clockAlterResponse;
                    } catch (Throwable t){
                        message = message + "(ERROR) " + t.getMessage();
                    }
                    out.print(HEAD + message + FOOT);
                }
               
                // DRS 20210215 - Added setItem & 'if' block - Functionality moved from /clockchecker?minutes=
                //setItem = (String)request.get("pollintervalseconds");
                //if (setItem != null){
                //    goodSettingCount++;
                //    String message = "pollintervalseconds=";
                //    try {
                //        int pollIntervalSeconds = Integer.parseInt(setItem);
                //        ClockChecker.getInstance(); // For cases where ClockChecker not started/initialized in CaptureManager.
                //        ClockChecker.setPollInterval(setItem); // This sets the value in ClockChecker and CaptureManager (saves to settings txt file if the input is valid). 
                //        message = message + pollIntervalSeconds;
                //    } catch (Throwable t){
                //        message = message + "(ERROR) " + t.getMessage();
                //    }
                //    out.print(HEAD + message + FOOT);
                //}
                
                /*
                System.out.println("DEBUG: goodSettingCount " + goodSettingCount);
                System.out.println("DEBUG: size: " + request.keySet().size());
                Iterator<String> keysIter = request.keySet().iterator();
                System.out.println("DEBUG: keysIter.size() " + request.keySet().size());
                while (keysIter.hasNext()) {
                    System.out.println("DEBUG: keysIter: " + keysIter.next());
                }
                */

                String message = "";
                if (request.keySet().size() > 1 && goodSettingCount == 0) {
                    message = "<H2>Setting NOT understood</H2><br><br>";
                    Iterator<String> keysIter2 = request.keySet().iterator();
                    String actionText = (String)request.get(keysIter2.next());
                    System.out.println(new Date() + " Setting NOT understood [" + actionText + "]");
                    out.print(HEAD + message + captureManager.getProperties() + FOOT);
                } else {
                    out.print(HEAD + captureManager.getProperties() + FOOT);
                }
                
            } else if (action.equals("/tuners")){ // ************* TUNERS ***************
                out.print(HEAD + tunerManager.getWebTunerList() + FOOT);
            } else if (action.equals("/discover")){ // ************* DISCOVER ***************
                //if (tunerManager.hasHdhrTuner()) {
                    tunerManager.countTuners();
                    tunerManager.addAltChannels(CaptureManager.alternateChannels); // DRS 20200225 - Added Line - This was key for backup recordings because doing only 'countTuners()' left the lineup without alternates.
                    CaptureManager.requestInterrupt("TinyConnection.run (/discover)"); // need to interrupt in case new tuner had new captures
                //}
                out.print(HEAD + tunerManager.getWebTunerList() + FOOT);
            } else if (action.equals("/scan")){ // ************* SCAN ***************
                boolean useExistingFile = false;
                String signalType = (String)request.get("signaltype");
                String timeout = (String)request.get("timeout");
                String tuner = (String)request.get("tuner");
                String interrupt = (String)request.get("interrupt");
                System.out.println("timeout [" + timeout + "]");
                System.out.println("tuner [" + tuner + "]");
                System.out.println("signalType [" + signalType + "]");
                System.out.println("interrupt [" + interrupt + "]");
                if (interrupt != null) {
                    out.print(HEAD + "Requested interrupt for all tuners. " + tunerManager.interruptScan() + " NOTE: You lost your previos scan data!  Hdhr channels incomplete!");
                    out.print("<br>" + tunerManager.getWebChannelList() + FOOT);
                } else {
                    int maxSeconds = 1200; // DRS 20191222 - Increased from 1000 to 1200
                    if (timeout != null && !timeout.equals("")){
                        try {maxSeconds = Integer.parseInt(timeout);} catch (Throwable t){}
                    }
                    out.print(HEAD + "Scanning started.  It takes several minutes.  Unplug your HDHR to stop sooner.");
                    String message = tunerManager.scanRefreshLineUpTm(useExistingFile, tuner, signalType, maxSeconds);
                    // DRS20200908 - Added 'if' 
                    if (message != null && message.toUpperCase().indexOf("USE DISCOVER") > -1){
                        tunerManager.countTuners();
                        tunerManager.scanRefreshLineUpTm(useExistingFile, tuner, signalType, maxSeconds);
                    }
                    tunerManager.addAltChannels(CaptureManager.alternateChannels); // DRS 20200305 - Added line - Should have been added 20200225
                    out.print("<br>" + tunerManager.getWebChannelList() + FOOT);
                }
            } else if (action.equals("/channels") || action.equals("/channels2")){ // ************* CHANNELS and CHANNELS2 ***************
                String message = tunerManager.scanRefreshLineupTm();
                if (message != null && message.toUpperCase().indexOf("USE DISCOVER") > -1){
                    tunerManager.countTuners();
                    tunerManager.scanRefreshLineupTm();
                }
                tunerManager.addAltChannels(CaptureManager.alternateChannels); // DRS 20200305 - Added line - Should have been added 20200225
                if (action.equals("/channels2")) out.print(HEAD + "<br>" + tunerManager.getWebChannelListSingleFusion() + FOOT);
                else out.print(HEAD + "<br>" + tunerManager.getWebChannelList() + FOOT);
            } else if (action.equals("/capture")){ // ************* CAPTURE ***************
                synchronized (locker){
                    String channelName = (String)request.get("channelname");
                    String channelVirtual = (String)request.get("channelvirtual");
                    String dateTime = (String)request.get("datetime");
                    String durationMinutes = (String)request.get("durationminutes");
                    String dateTimeEnd = (String)request.get("datetimeend");
                    String tunerString = (String)request.get("tuner");
                    String fileName = (String)request.get("filename");
                    String protocol = (String)request.get("protocol");
                    String title = (String)request.get("title");
                    String rfChannel = (String)request.get("rfchannel");
                    System.out.println("channelname [" + channelName + "]");
                    System.out.println("channelvirtual [" + channelVirtual + "]");
                    System.out.println("datetime [" + dateTime + "]");
                    System.out.println("datetimeend [" + dateTimeEnd + "]");
                    System.out.println("durationminutes [" + durationMinutes + "]");
                    System.out.println("tuner [" + tunerString + "]");
                    System.out.println("filename [" + fileName + "]");
                    System.out.println("title [" + title + "]");
                    System.out.println("protocol [" + protocol + "]");
                    System.out.println("rfchannel [" + rfChannel + "]");
                    Slot slot = null;
                    if (durationMinutes != null){
                        slot = new Slot(dateTime, durationMinutes);
                    } else {
                        slot = new Slot(dateTime, dateTimeEnd, 1);
                    }
                    System.out.println("slot [" + slot + "]"); // <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<20161214
                    
                    // DRS 20110618 - Added 1+'if' block externalTuner
                    String[] externalTunerValues = tunerManager.getExternalTunerValues(tunerString, channelName);  //Creates tuner if it doesn't exist already
                    if (externalTunerValues != null){                        
                        // substitute good values in fileName
                        int dashLoc = tunerString.indexOf("-");
                        if (dashLoc > 1 && fileName != null && channelName != null){
                            String shortenedTunerString = tunerString.substring(0,dashLoc-1);
                            int tsLoc = fileName.indexOf(shortenedTunerString);
                            StringBuffer fileNameBuf = new StringBuffer(fileName);
                            if (tsLoc > 1){
                                fileNameBuf.delete(tsLoc, tsLoc+shortenedTunerString.length() + 1);
                                fileNameBuf.insert(tsLoc, externalTunerValues[1]);
                            }
                            int cnLoc = fileNameBuf.indexOf(channelName);
                            if (cnLoc > 1){
                                fileNameBuf.delete(cnLoc, cnLoc + channelName.length());
                                fileNameBuf.insert(cnLoc, externalTunerValues[0]);
                            }
                            fileName = fileNameBuf.toString();
                        }
                        
                        // replace tuner and channel with externalTuner values
                        channelName = externalTunerValues[0];
                        tunerString = externalTunerValues[1];
                        
                    }
                    
                    //if (tunerString != null && (tunerString.equalsIgnoreCase("myhd") || tunerString.indexOf(".") > 0 || tunerString.indexOf("-") > 0)){
                    if (tunerManager.getTuner(tunerString) != null){
                        tunerManager.clearLastReason();
                        Capture capture = null;
                        ArrayList<Capture> captureList = null;
                        ArrayList<Target> targetList = null;
                        int tunerType = -1;
                        // DRS 20110618 - Added 'if' block externalTuner
                        if (externalTunerValues != null){
                            tunerType = Tuner.EXTERNAL_TYPE;
                            System.out.println(new Date() + " WARNING: Tuner.EXTERNAL_TYPE detected.  Uncommon.");
                            capture = tunerManager.getCaptureForChannelNameSlotAndTuner(channelName, slot, tunerString, protocol);
                            //capture = tunerManager.getCaptureForNewChannelNameSlotAndTuner(channelName, slot, tunerString, "unk");
                        } else if ("myhd".equalsIgnoreCase(tunerString)) { /******* MYHD **********/
                            tunerType = Tuner.MYHD_TYPE;
                            System.out.println(new Date() + " WARNING: Tuner.MYHD_TYPE detected.  Uncommon.");
                            if (channelName == null || "".equals(channelName)) channelName = "0.0";
                            capture = tunerManager.getCaptureForMyHD(channelName, slot, tunerString, channelVirtual, rfChannel, protocol);
                        } else if (tunerString.toUpperCase().indexOf("MYHD") > -1){
                            postErrorMessage("Please use just 'myhd' for the tuner. The input is specified on channelvirtual.  See /help.");
                        } else if (tunerString.indexOf(".") > -1 ){ /******* FUSION **********/
                            tunerType = Tuner.FUSION_TYPE;
                            System.out.println(new Date() + " WARNING: Tuner.FUSION_TYPE detected.  Uncommon.");
                            capture = tunerManager.getCaptureForChannelNameSlotAndTuner(channelName, slot, tunerString, protocol);
                        } else if (tunerString.indexOf("-") > -1){ /******* HDHR **********/
                            tunerType = Tuner.HDHR_TYPE;
                            if (tunerString.toUpperCase().indexOf("FFFFFFFF") > -1){
                                Tuner realHdhr = tunerManager.getRealHdhrTuner(tunerString);
                                if (realHdhr != null) tunerString = realHdhr.getFullName();
                            }
                            // DRS 20110214 - Added 'if' (existing in else)
                            if ("*".equals(channelName)){ // Multiple captures for signal testing
                                captureList = tunerManager.getCapturesForAllChannels(channelName, slot, tunerString, protocol);
                                targetList = new ArrayList<Target>();
                            } else {
                                capture = tunerManager.getCaptureForChannelNameSlotAndTuner(channelName, slot, tunerString, protocol);// <<<<<<<<<<<<20161214
                                System.out.println(new Date() + " Scheduled with [" + channelName + "] [" + tunerString + "] [" + protocol + "]");
                                if (capture == null){
                                    //capture = tunerManager.getCaptureForNewChannelNameSlotAndTuner(channelName, slot, tunerString, protocol);
                                }
                            }
                        }
                        
                        Target target = null;
                        boolean errorPosted = false;
                        try {
                            String defaultRecordPath = "";
                            String analogFileExtension = "";
                            if (tunerType == Tuner.FUSION_TYPE){
                                TunerFusion tf = (TunerFusion)tunerManager.getTuner(tunerString);
                                if (tf != null){
                                    defaultRecordPath = tf.getRecordPath();
                                    analogFileExtension = tf.getAnalogFileExtension();
                                }
                            }
                            // DRS 20181031 - Sometimes Tim sends the wrong file type
                            if (tunerType == Tuner.HDHR_TYPE && fileName.contains(".avi")) {
                                fileName = fileName.replace(".avi", ".ts");
                            }
                            if (capture != null){
                                target = new Target(fileName, title, defaultRecordPath, analogFileExtension, capture.getChannelProtocol(), tunerType);
                            // DRS 20110214 - Added 'else if'    
                            } else if (captureList != null && captureList.size() > 0){
                                for (Capture aCapture : captureList) {
                                    target = new Target(fileName, title, defaultRecordPath, analogFileExtension, aCapture.getChannelProtocol(), tunerType, aCapture.getChannelDescription());
                                    targetList.add(target);
                                }
                            } else {
                                target = new Target();
                                target.setInvalidMessage(new Date() + " ERROR - capture was not defined. "); // <<<<<<<<<<<<20161214
                            }
                        } catch (Exception e) {
                            String eGetMessage = e.getMessage();
                            if (eGetMessage == null){
                                eGetMessage = "Undefined value.";
                                boolean testing = true; ////////////////////////REMOVE
                                if (testing) e.printStackTrace();////////////////////////REMOVE
                            }
                            String message = " Could not create target file. [" + eGetMessage + "]";
                            System.out.println(new Date() + message);
                            target = new Target();
                            target.setInvalidMessage(eGetMessage);
                            tunerString = "invalidated due to bad target file name";
                            postErrorMessage(message);
                            errorPosted = true;
                        }
                                                                                                    // <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<20161214
                        System.out.println(new Date() + " open slot:" + (capture != null) + " file name:" + (fileName != null) + " file path:" + (target.isValid())); 
                        if (capture != null && fileName != null && target.isValid()) {
                            capture.setTarget(target);
                            CaptureManager.scheduleCapture(capture, true);
                            CaptureManager.requestInterrupt("TinyConnection.run (/capture)"); // need to interrupt so it reads new schedule

                            String nextpage = (String)request.get("nextpage");
                            if ("vcr".equals(nextpage)) {
                                out.print(HEAD_SHORT + HtmlVcrDoc.get());
                            } else {
                                out.print(HEAD + tunerManager.getWebCapturesList(false) + FOOT);
                            }
                        //DRS 20110214 - Added 'else if'    
                        } else if (captureList != null && captureList.size() > 0){
                            StringBuffer errorMsg = new StringBuffer();
                            int listItem = 0;
                            for (Capture aCapture : captureList) {
                                target = targetList.get(listItem++);
                                if (aCapture != null && fileName != null && target.isValid()) {
                                    aCapture.setTarget(target);
                                    CaptureManager.scheduleCapture(aCapture, true);
                                    if (aCapture.getLastError() != null){
                                        errorMsg.append("<br>" + aCapture.getLastError());
                                    }
                                } else {
                                    String message = "Not able to schedule capture for " + channelName + " " + slot;
                                    if (!target.isValid()) message += "<br>" + target.getInvalidMessage();
                                    if (aCapture == null){
                                        message += "<br>" + tunerManager.getLastReason();
                                    } else if (aCapture.getLastError() != null){
                                        message += "<br>" + aCapture.getLastError();
                                    }
                                    errorMsg.append(message);
                                }
                            }
                            if (errorMsg.length() > 0){
                                if (!errorPosted) postErrorMessage(errorMsg.toString());
                            } else {
                                CaptureManager.requestInterrupt("TinyConnection.run (/capture) multiple"); // need to interrupt so it reads new schedule
                                out.print(HEAD + tunerManager.getWebCapturesList(false) + FOOT);
                            }
                        } else {
                            String message = "Not able to schedule capture for " + channelName + " " + slot;
                            if (!target.isValid()) message += "<br>" + target.getInvalidMessage();
                            if (capture == null){
                                message += "<br>" + tunerManager.getLastReason();
                            } else if (capture.getLastError() != null){
                                message += "<br>" + capture.getLastError();
                            }
                            if (!errorPosted) postErrorMessage(message);
                        }
                    } else {
                        StringBuffer buf = new StringBuffer("Tuner name [" + tunerString + "] not recognized.<br>Try one of these:<br>");
                        buf.append(tunerManager.getSimpleTunerList());
                        postErrorMessage(new String(buf));
                    }
                } // end synchronized
            } else if (action.equals("/captures")){ // ************* CAPTURES ***************
                synchronized (locker){
                    out.print(HEAD + tunerManager.getWebCapturesList(false) + FOOT);
                }
            } else if (action.equals("/decapture")){ // ************* DECAPTURE ***************
                String sequence = (String)request.get("sequence");
                captureManager.removeCapture(sequence);
                String nextpage = (String)request.get("nextpage");
                if ("vcr".equals(nextpage)) {
                    out.print(HEAD_SHORT + HtmlVcrDoc.get());
                } else {
                    out.print(HEAD + tunerManager.getWebCapturesList(false) + FOOT);
                }
            } else if (action.equals("/update")){ // ************* UPDATE ***************
                String sequence = (String)request.get("sequence");
                String fileName = (String)request.get("filename");
                captureManager.updateFileName(sequence, fileName);
                out.print(HEAD + tunerManager.getWebCapturesList(false) + FOOT);
            } else if (action.equals("/decaptureall")){ // ************* DECAPTUREALL ***************
                captureManager.removeAllCaptures();
                out.print(HEAD + tunerManager.getWebCapturesList(false) + FOOT);
            } else if (action.equals("/help")){ // ************* HELP ***************
                out.print(HEAD + "<h2>Version: " + CaptureManager.version + "</h2><br>" + WebHelpDoc.doc + FOOT);
            } else if (action.equals("/vcr")){ // ************* VCR ***************
                out.print(HEAD_SHORT + HtmlVcrDoc.get());
            } else if (action.equals("/configure")){ // ************* CONFIGURE ***************
                out.print(HEAD_SHORT + HtmlSettingsDoc.get());
            } else if (action.equals("/checkedshutdown")){ // ************* CHECKEDSHUTDOWN ***************
                out.print(HEAD_SHORT + HtmlShutdownDoc.get());
            } else if (action.equals("/log")){ // ************* LOG ***************
                out.print(HEAD + WebHelpDoc.getLog() + FOOT);
            } else if (action.equals("/properties")){ // ************* PROPERTIES ***************
                out.print(HEAD + captureManager.getProperties() + FOOT);
            } else if (action.equals("/shutdown")){ // ************* SHUTDOWN ***************
                out.print(HEAD + CaptureManager.shutdown("web") + FOOT);
            } else if (action.equals("/actives")){ // ************* ACTIVITIES ***************
                out.print(HEAD + captureManager.getActiveWebCapturesList() + FOOT);
            } else if (action.equals("/recent")){ // ************* RECENT ***************
                boolean limited = true;
                String[] inputs = {(String)request.get("filename"), (String)request.get("channel"), (String)request.get("title")};
                out.print(HEAD + captureManager.getWebCapturesList(inputs, limited) + FOOT);
            } else if (action.equals("/recentall")){ // ************* RECENTALL ***************
                String[] inputs = {(String)request.get("filename"), (String)request.get("channel"), (String)request.get("title")};
                boolean limited = false;
                out.print(HEAD + captureManager.getWebCapturesList(inputs, limited) + FOOT);
            } else if (action.equals("/path")){ // ************* PATH ***************
                String root = (String)request.get("root");
                out.print(HEAD + captureManager.getWebPathList(root) + FOOT);
            } else if (action.equals("/myhdpass")){ // ************* MYHDPASS ***************
                String command = (String)request.get("command");
                MyhdPassManager passManager = new MyhdPassManager(command);
                out.print(HEAD + passManager.getHtmlResult() + FOOT);
            } else if (action.equals("/settunerpath")){ // ************* SETTUNERPATH ***************
                String tunerName = (String)request.get("tuner");
                String path = (String)request.get("path");
                tunerManager.setTunerPath(tunerName, path);
                if (tunerManager.getLastReason().equals("")){
                    out.print(HEAD + tunerManager.getWebTunerList() + FOOT);
                } else {
                    out.print(HEAD + tunerManager.getLastReason() + FOOT);
                }
            //} else if (action.equals("/dbcopy")){ // ************* DBCOPY ***************
                //dbcopy?source=<MasterIP>&share=<share name>&lastresult=true
            //    String source = (String)request.get("source");
            //    String share = (String)request.get("share");
            //    String timeout = (String)request.get("timeout");
            //    String lastResult = (String)request.get("lastresult");
            //    DbCopier copier = DbCopier.getInstance();
            //    try {
            //        if (lastResult == null && source != null){
            //            copier.startCopyAndReturn(source, share, timeout);
            //            out.print(HEAD + copier.getLastResult() + FOOT);
            //        } else if (source != null) {
            //            copier.copyAndWait(source, share, timeout);
            //            out.print(HEAD + copier.getLastResult() + FOOT);
            //        } else {
            //            throw new Exception("Need at least 'source' specified.");
            //        }
            //    } catch (Exception e) {
            //        out.print(HEAD + "No dbcopy done: " + e.getMessage() + FOOT);
            //    }
            } else if (action.equals("/wakeupevent")){ // ************* WAKEUPEVENT ***************
                String hourToSend = (String)request.get("hourtosend");
                String minuteToSend = (String)request.get("minutetosend");
                String durationMinutes = (String)request.get("durationminutes");
                String parameters = (String)request.get("parameters");
                if (parameters == null) parameters = "null";
                String overrideCommand = (String)request.get("overridecommand");
                if (overrideCommand == null) overrideCommand = "null";
                WakeupEvent event = WakeupEvent.getInstance();
                //DRS 20160927 - initialize will save to disk
                event.initialize(hourToSend,minuteToSend,durationMinutes,"true", parameters, overrideCommand); // DRS 20151031 - Added comment: Sets hardware wake up, adjusted for CaptureManager.leadTimeSeconds.
                if ("0".equals(durationMinutes)){
                    WakeupEvent.removeWakeupEventDataFile();
                    CaptureManager.setWakeupEvent(null);
                    System.out.println(new Date() + " /wakeupevent durationMinutes 0. Removing wake event.");
                } else if (event.isValid()) { 
                    CaptureManager.setWakeupEvent(event);
                } else {
                    System.out.println(new Date() + " /wakeupevent data passed in was invalid.");
                }
                out.print(HEAD + event.getHtml() + FOOT);
            } else if (action.equals("/sortchannels")) { // ************* SORTCHANNELS ***************
                long daysHistoryLong = 14; // Default to 2 weeks
                try {
                    String daysHistory = (String)request.get("dayshistory");
                    daysHistoryLong = Long.parseLong(daysHistory);
                } catch (Throwable t) {
                    System.out.println(new Date() + " WARNING: /sortchannels missing dayshistory= parameter.  Using 14 days.");
                }
                String message = tunerManager.sortChannelMap(daysHistoryLong);
                out.print(HEAD + message + FOOT);
                // DRS 20200822 - Added 'else if' *****************UNDOCUMENTED FEATURE using hand-made frequency_tuner_priority
            } else if (action.equals("/sortchannels2")) { // ************* SORTCHANNELS2 ***************
                String message = TunerManager.sortChannelMapUsingSignalPriorityFile();
                out.print(HEAD + message + FOOT);
            } else if (action.equals("/emailer")){ // ************* EMAILER ***************
                String sendTestEmail = (String)request.get("sendtestemail");
                String removeEmailer = (String)request.get("removeemailer");
                String message = "";
                if (sendTestEmail != null){
                    Emailer emailer = CaptureManager.getEmailer();
                    if (emailer != null){
                        if (emailer.isValid()){
                            emailer.sendTestMessage();
                            message = "Test message attempt at " + new Date() + "<BR><BR>";
                        } else {
                            message = "Test message attempt failed.  Emailer data is not defined properly.<BR><BR>";
                        }
                        out.print(HEAD + message + emailer.getHtml() + FOOT);
                    } else{
                        out.print(HEAD + "Emailer has not been defined yet." + FOOT);
                    }
                } else if (removeEmailer != null) {
                    Emailer emailer = CaptureManager.getEmailer();
                    if (emailer != null){
                        emailer.removeEmailerDataFile();
                        emailer.removeHardwareWakeupEvent("TinyConnection removeEmailer");
                        CaptureManager.setEmailer(null);
                        out.print(HEAD + "Emailer data has been removed." + FOOT);
                    } else {
                        out.print(HEAD + "No emailer was available for removal." + FOOT);
                    }
                } else if (null != (String)request.get("smtpservername")){
                    String hourToSend = (String)request.get("hourtosend");
                    String minuteToSend = (String)request.get("minutetosend");
                    String smtpServerName = (String)request.get("smtpservername");
                    String smtpServerPort = (String)request.get("smtpserverport");
                    String logonUser = (String)request.get("logonuser");
                    String logonPassword = (String)request.get("logonpassword");
                    String saveToDisk = (String)request.get("savetodisk");
                    String sendUsers = (String)request.get("sendusers");
                    String lowDiskGb = (String)request.get("lowdiskgb");
                    String sendScheduled = (String)request.get("sendscheduled");
                    String sendRecorded = (String)request.get("sendrecorded");
                    Emailer emailer = CaptureManager.getEmailer();
                    if (emailer == null){
                        emailer = Emailer.getInstance(); 
                        emailer.initialize(hourToSend, minuteToSend, smtpServerName, smtpServerPort, logonUser, logonPassword, saveToDisk, sendUsers, lowDiskGb, sendScheduled, sendRecorded);
                    }
                    if (emailer.isValid()){
                        CaptureManager.setEmailer(emailer);
                        message = "Emailer has been defined.<BR><BR>";
                    } else {
                        message = "Emailer definition attempt failed.  Supplied Emailer data is not sufficient.<BR><BR>";
                    }
                    out.print(HEAD + message + emailer.getHtml() + FOOT);
                } else {
                    out.print(HEAD + "Emailer command not understood" + FOOT);
                }
            } else if (action.equals("/clock")) { // ************* CLOCK ***************
                String response = " clock command didn't work ";
                long clockOffsetSeconds = 0;
                try {
                    int timeoutSeconds = 2;
                    clockOffsetSeconds = CaptureManager.getPcClockOffsetSeconds(timeoutSeconds);
                    if (clockOffsetSeconds == Long.MAX_VALUE) {
                        response = "Unable to get to us.pool.ntp.org to get the time";
                    } else {
                        response = "Clock on the PC is " + Math.abs(clockOffsetSeconds) + " seconds " + (clockOffsetSeconds > 0?"slower":"faster") + " than us.pool.ntp.org.  ";
                    }

                    String clockOffset = (String)request.get("clockoffset");
                    if (clockOffset != null ) {
                        try { 
                            int requestedOffset = Integer.parseInt(clockOffset);
                            response = CaptureManager.alterPcClock(requestedOffset, timeoutSeconds, false);
                        } catch (Throwable t) {
                            response += t.getMessage();
                        }
                    }
                } catch (Exception e) {
                    response += e.getClass().getCanonicalName() + " " + e.getMessage(); 
                }
                System.out.println(new Date() + " " + response);
                out.print(HEAD + response + FOOT);
            } else if (action.equals("/dump")) { // ************* DUMP ***************
                String response = " dump command didn't work ";
                try {
                    Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
                    StringBuffer buf = new StringBuffer();
                    buf.append(new Date() + " CurrentThreads:");
                    buf.append("<uol>\n");
                    for (Thread thread : threadSet) {
                        System.out.println("Thread: " + thread.getName() + "\n" + ShutdownHookThread.getNiceStackTrace(thread.getStackTrace()) + "\n");
                        buf.append("<li>" + thread.getName() + "</li>\n");
                    }
                    buf.append("</uol>\n");
                    response = buf.toString();
                } catch (Exception e) { 
                    response += e.getMessage(); 
                }
                System.out.println(new Date() + " " + response);
                out.print(HEAD + response + FOOT);
            } else if (action.equals("/token")) {
                String username = (String)request.get("username");
                String password = (String)request.get("password");
                String url = "https://json.schedulesdirect.org/20141201/token";
                String response = "Response to [" + url + "] was [" + HttpRequester.performPost(url, username, password, false) + "]";
                response += "<br><br>" + HttpRequester.getLastError();
                System.out.println(new Date() + " " + response);
                out.print(HEAD + response + FOOT);
            } else {
                out.print(HEAD + "<br><h2>Command Not Understood</h2><br>" + FOOT);
            }
            //DRS 20210323 - Commented 1 - No longer to keep machine awake for X amount of time after the last web command.  TODO: Remove keepAwakeWorhtyAction and remove registerKeepAwakeEvent();
            //if (keepAwakeWorthyAction) CaptureManager.registerKeepAwakeEvent();
		} catch (Exception e) {
            System.err.println(new Date() + " Error in TinyConnection.run");
            e.printStackTrace();
            out.print(e.getClass().getName() + " " + e.getMessage());
            System.out.println(e.getMessage());
		} finally {
            try {in.close();} catch (Exception e2) {/* ignore */}
            try {out.close();} catch (Exception e2) {/* ignore */}
            try {client.close();} catch (IOException e2) {/* ignore */}
		}
	}

    Map getRequestMap(String req){
        StringTokenizer st = new StringTokenizer(req);
        // discard first token ("GET")
        st.nextToken();
        StringTokenizer st2 = new StringTokenizer(st.nextToken(),"?");
        String action = st2.nextToken();
        
        Properties prop = new Properties();
        prop.put("action", action);
        if (st2.hasMoreTokens()){
            StringTokenizer st3 = new StringTokenizer(st2.nextToken(),"&");
            while(st3.hasMoreTokens()){
                String item = st3.nextToken();
                if (item != null && item.indexOf("=") > 0){
                    try {
						String name = URLDecoder.decode(item.substring(0,item.indexOf("=")), "UTF-8");
						String value = URLDecoder.decode(item.substring(item.indexOf("=") + 1), "UTF-8");
                        if ("tuner".equals(name) && !item.equals(name + "=" + value)){
                            System.out.println(new Date() + " Overriding UTF-8 value for tuner.");
                            value = item.substring(item.indexOf("=") + 1);
                            value = value.replaceAll("%20"," ");
                        }
						if (name != null && value != null && !name.equals("")){
						    prop.put(name.toLowerCase(), value);
						}
						//System.out.println(new Date() + " DEBUG URL REQUEST [" + item + "]");
					} catch (UnsupportedEncodingException e) {
						System.out.println("Problem decoding URL " + e.getMessage());
					}
                }
            }
        }
        return prop;
    }
    
    void postErrorMessage(String message){
        out.println(HEAD + message + FOOT);
        System.out.println(message);
    }
    
    public static void main(String[] args) {
        System.out.println(HEAD + "<h2>Version: " + CaptureManager.version + "</h2><br>" + WebHelpDoc.doc + FOOT);
    }
}
