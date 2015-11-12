package org.cwepg.svc;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;

import org.cwepg.hr.Capture;
import org.cwepg.hr.CaptureManager;
import org.cwepg.hr.DbCopier;
import org.cwepg.hr.Emailer;
import org.cwepg.hr.Slot;
import org.cwepg.hr.Target;
import org.cwepg.hr.Tuner;
import org.cwepg.hr.TunerExternal;
import org.cwepg.hr.TunerFusion;
import org.cwepg.hr.TunerManager;
import org.cwepg.hr.WakeupEvent;

class TinyConnection implements Runnable {
	protected Socket client;
	protected BufferedReader in;
	protected PrintStream out;
    protected CaptureManager captureManager;
    protected TunerManager tunerManager;
	private static final String HEAD = "HTTP/1.0 200 OK\nContent-type: text/html\n\n<html><body><h2>CW_EPG Helper Interface</h2><br>";
    private static final String FOOT = "</body></html>";
    private static final SimpleDateFormat clock = new SimpleDateFormat("HH:mm:ss");
    private static final Object locker = new Object();

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
			System.out.println("request [" + req + "] " + clock.format(new Date()));

			// loop through and discard rest of request
			line = req;
			while (line != null && line.length() > 0) {
				line = in.readLine();
				//System.out.println("other data [" + line + "]");
			}

			if (req == null) throw new Exception(new Date() + " The request for this connection was null."); //DRS 20150419 - Added 1 - Prevent raw null pointer error.
			
			// get properties map 
            Map request = getRequestMap(req);
            boolean eventWorthyAction = false;
            String action = (String)request.get("action");
            if (action != null && action.startsWith("/")) {
                //CaptureManager.registerEvent();
                eventWorthyAction = true;
            }
            else throw new Exception(new Date() + " The action was null or did not start with /.");
            
            if (action.equals("/set")){
                String setItem = null;
                
                setItem = (String)request.get("simulate");
                if (setItem != null && setItem.equals("true")){
                    CaptureManager.setSimulate(true);
                    out.print(HEAD + "simulate=true" + FOOT);
                } else if (setItem != null && setItem.equals("false")){
                    CaptureManager.setSimulate(false);
                    out.print(HEAD + "simulate=false" + FOOT);
                }

                setItem = (String)request.get("sleepmanaged");
                if (setItem != null && setItem.equals("true")){
                    CaptureManager.setSleepManaged(true);
                    out.print(HEAD + "sleepmanaged=true" + FOOT);
                } else if (setItem != null && setItem.equals("false")){
                    CaptureManager.setSleepManaged(false);
                    out.print(HEAD + "sleepmanaged=false" + FOOT);
                }
                
                setItem = (String)request.get("endfusionwatch");
                if (setItem != null && setItem.equals("true")){
                    CaptureManager.setEndFusionWatchEvents(true);
                    out.print(HEAD + "endfusionwatch=true" + FOOT);
                } else if (setItem != null && setItem.equals("false")){
                    CaptureManager.setEndFusionWatchEvents(false);
                    out.print(HEAD + "endfusionwatch=false" + FOOT);
                }
                
                setItem = (String)request.get("leadtime");
                if (setItem != null){
                    String message = "leadtime=";
                    try {
                        int leadTimeSeconds = Integer.parseInt(setItem);
                        CaptureManager.setLeadTimeSeconds(leadTimeSeconds);
                        message = message + leadTimeSeconds;
                    } catch (Throwable t){
                        message = message + "(ERROR) " + t.getMessage();
                    }
                    out.print(HEAD + message + FOOT);
                } 

                setItem = (String)request.get("fusionleadtime");
                if (setItem != null){
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
                if (setItem == null){
                    out.print(HEAD + captureManager.getProperties() + FOOT);
                }
            } else if (action.equals("/tuners")){
                out.print(HEAD + tunerManager.getWebTunerList() + FOOT);
            } else if (action.equals("/discover")){
                tunerManager.countTuners();
                CaptureManager.requestInterrupt("TinyConnection.run (/discover)"); // need to interrupt in case new tuner had new captures
                out.print(HEAD + tunerManager.getWebTunerList() + FOOT);
            } else if (action.equals("/scan")){
                boolean useExistingFile = false;
                String signalType = (String)request.get("signaltype");
                String timeout = (String)request.get("timout");
                String tuner = (String)request.get("tuner");
                System.out.println("timeout [" + timeout + "]");
                System.out.println("tuner [" + tuner + "]");
                System.out.println("signalType [" + signalType + "]");
                int maxSeconds = 1000;
                if (timeout != null && !timeout.equals("")){
                    try {maxSeconds = Integer.parseInt(timeout);} catch (Throwable t){}
                }
                out.print(HEAD + "Scanning started.  It takes several minutes.  Unplug your HDHR to stop sooner.");
                tunerManager.scanRefreshLineUp(useExistingFile, tuner, signalType, maxSeconds);
                out.print("<br>" + tunerManager.getWebChannelList() + FOOT);
            } else if (action.equals("/channels") || action.equals("/channels2")){
                String message = tunerManager.scanRefreshLineup();
                if (message != null && message.toUpperCase().indexOf("USE DISCOVER") > -1){
                    tunerManager.countTuners();
                    tunerManager.scanRefreshLineup();
                }
                if (action.equals("/channels2")) out.print(HEAD + "<br>" + tunerManager.getWebChannelListSingleFusion() + FOOT);
                else out.print(HEAD + "<br>" + tunerManager.getWebChannelList() + FOOT);
            } else if (action.equals("/capture")){
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
                    System.out.println("slot [" + slot + "]");
                    
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
                            capture = tunerManager.getCaptureForChannelNameSlotAndTuner(channelName, slot, tunerString, protocol);
                            //capture = tunerManager.getCaptureForNewChannelNameSlotAndTuner(channelName, slot, tunerString, "unk");
                        } else if ("myhd".equalsIgnoreCase(tunerString)) { /******* MYHD **********/
                            tunerType = Tuner.MYHD_TYPE;
                            if (channelName == null || "".equals(channelName)) channelName = "0.0";
                            capture = tunerManager.getCaptureForMyHD(channelName, slot, tunerString, channelVirtual, rfChannel, protocol);
                        } else if (tunerString.toUpperCase().indexOf("MYHD") > -1){
                            postErrorMessage("Please use just 'myhd' for the tuner. The input is specified on channelvirtual.  See /help.");
                        } else if (tunerString.indexOf(".") > -1 ){ /******* FUSION **********/
                            tunerType = Tuner.FUSION_TYPE;
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
                                capture = tunerManager.getCaptureForChannelNameSlotAndTuner(channelName, slot, tunerString, protocol);
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
                            if (capture != null){
                                target = new Target(fileName, title, defaultRecordPath, analogFileExtension, capture.getChannelProtocol(), tunerType);
                            // DRS 20110214 - Added 'else if'    
                            } else if (captureList != null && captureList.size() > 0){
                                for (Capture aCapture : captureList) {
                                    target = new Target(fileName, title, defaultRecordPath, analogFileExtension, aCapture.getChannelProtocol(), tunerType, aCapture.getChannelDescription());
                                    if (target == null) {
                                        target = new Target();
                                        target.setInvalidMessage(new Date() + " ERROR = capture was not defined. ");
                                    }
                                    targetList.add(target);
                                }
                            } else {
                                target = new Target();
                                target.setInvalidMessage(new Date() + " ERROR - capture was not defined. ");
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
                        System.out.println(new Date() + " open slot:" + (capture != null) + " file name:" + (fileName != null) + " file path:" + (target.isValid()));
                        if (capture != null && fileName != null && target.isValid()) {
                            capture.setTarget(target);
                            CaptureManager.scheduleCapture(capture, true);
                            CaptureManager.requestInterrupt("TinyConnection.run (/capture)"); // need to interrupt so it reads new schedule
                            out.print(HEAD + tunerManager.getWebCapturesList() + FOOT);
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
                                out.print(HEAD + tunerManager.getWebCapturesList() + FOOT);
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
            } else if (action.equals("/captures")){
                synchronized (locker){
                    out.print(HEAD + tunerManager.getWebCapturesList() + FOOT);
                }
            } else if (action.equals("/decapture")){
                String sequence = (String)request.get("sequence");
                captureManager.removeCapture(sequence);
                out.print(HEAD + tunerManager.getWebCapturesList() + FOOT);
            } else if (action.equals("/update")){
                String sequence = (String)request.get("sequence");
                String fileName = (String)request.get("filename");
                captureManager.updateFileName(sequence, fileName);
                out.print(HEAD + tunerManager.getWebCapturesList() + FOOT);
            } else if (action.equals("/decaptureall")){
                captureManager.removeAllCaptures();
                out.print(HEAD + tunerManager.getWebCapturesList() + FOOT);
            } else if (action.equals("/help")){
                out.print(HEAD + "<h2>Version: " + CaptureManager.version + "</h2><br>" + WebHelpDoc.doc + FOOT);
            } else if (action.equals("/log")){
                out.print(HEAD + WebHelpDoc.getLog() + FOOT);
            } else if (action.equals("/properties")){
                out.print(HEAD + captureManager.getProperties() + FOOT);
            } else if (action.equals("/shutdown")){
                eventWorthyAction = false;
                out.print(HEAD + captureManager.shutdown("web") + FOOT);
            } else if (action.equals("/actives")){
                out.print(HEAD + captureManager.getActiveWebCapturesList() + FOOT);
            } else if (action.equals("/recent")){
                out.print(HEAD + captureManager.getRecentWebCapturesList() + FOOT);
            } else if (action.equals("/path")){
                String root = (String)request.get("root");
                out.print(HEAD + captureManager.getWebPathList(root) + FOOT);
            } else if (action.equals("/myhdpass")){
                String command = (String)request.get("command");
                MyhdPassManager passManager = new MyhdPassManager(command);
                out.print(HEAD + passManager.getHtmlResult() + FOOT);
            } else if (action.equals("/settunerpath")){
                String tunerName = (String)request.get("tuner");
                String path = (String)request.get("path");
                tunerManager.setTunerPath(tunerName, path);
                if (tunerManager.getLastReason().equals("")){
                    out.print(HEAD + tunerManager.getWebTunerList() + FOOT);
                } else {
                    out.print(HEAD + tunerManager.getLastReason() + FOOT);
                }
            } else if (action.equals("/dbcopy")){
                //dbcopy?source=<MasterIP>&share=<share name>&lastresult=true
                String source = (String)request.get("source");
                String share = (String)request.get("share");
                String timeout = (String)request.get("timeout");
                String lastResult = (String)request.get("lastresult");
                DbCopier copier = DbCopier.getInstance();
                try {
                    if (lastResult == null && source != null){
                        copier.startCopyAndReturn(source, share, timeout);
                        out.print(HEAD + copier.getLastResult() + FOOT);
                    } else if (source != null) {
                        copier.copyAndWait(source, share, timeout);
                        out.print(HEAD + copier.getLastResult() + FOOT);
                    } else {
                        throw new Exception("Need at least 'source' specified.");
                    }
                } catch (Exception e) {
                    out.print(HEAD + "No dbcopy done: " + e.getMessage() + FOOT);
                }
            } else if (action.equals("/wakeupevent")){
                String hourToSend = (String)request.get("hourtosend");
                String minuteToSend = (String)request.get("minutetosend");
                String durationMinutes = (String)request.get("durationminutes");
                boolean isCreateRequest = !"0".equals(durationMinutes);
                String parameters = (String)request.get("parameters");
                if (parameters == null) parameters = "null";
                boolean isMaster = !"null".equals(parameters); // run the helper command, not the master command
                if (CaptureManager.usingOldOs) { // DRS 20150530 - added 'if' to preserve function for older OS's
                    WakeupEvent event = WakeupEvent.getInstance();
                    event.initialize(hourToSend,minuteToSend,durationMinutes,"true", parameters); // DRS 20151031 - Added comment: Sets hardware wake up, adjusted for CaptureManager.leadTimeSeconds.
                    if ("0".equals(durationMinutes)){
                        WakeupEvent.removeWakeupEventDataFile();
                        CaptureManager.setWakeupEvent(null);
                        System.out.println(new Date() + " /wakupevent durationMinutes 0. Removing wake event.");
                    } else if (event.isValid()){
                        CaptureManager.setWakeupEvent(event);
                    } else {
                        System.out.println(new Date() + " /wakeupevent data passed in was invalid.  No wake event set.");
                    }
                    out.print(HEAD + event.getHtml() + FOOT);
                } else { // DRS 20150530 - Added a new way for Windows 7 or higher
                    String htmlMessage = "";
                    if (isCreateRequest) {                 // this schedules a timer event for both helper and master, plus an additional real command (cw_epg.exe) for a master
                        AutorunFile arf = new AutorunFile(hourToSend, minuteToSend, durationMinutes, parameters, isMaster, CaptureManager.leadTimeSeconds);
                        boolean goodWrite = arf.saveToDisk(CaptureManager.dataPath + "Autorun.xml"); // DRS 20151031 - Saves XML file with TIMER ONLY (master and helper) since leadTimeSeconds not zero.
                        if (goodWrite) {
                            String[] create = {"/create", "/f", "/tn", "CWEPG_Autorun_Timer", "/xml", CaptureManager.dataPath + "Autorun.xml"};  
                            TaskCreateCommandLine tcclc = new TaskCreateCommandLine(create);
                            System.out.println(new Date() + " Creating scheduled task CWEPG_Autorun_Timer for " + (isMaster?"Master":"Helper") + " with lead time " + CaptureManager.leadTimeSeconds + " seconds."); 
                            boolean goodResult = tcclc.runProcess();
                            if (!goodResult) htmlMessage += "failed to handle " + tcclc.getCommands() + "<br>";
                            else {
                                htmlMessage += "/create:<br>";
                                htmlMessage += tcclc.getOutput() + "<br>";
                                htmlMessage += tcclc.getErrors() + "<br>";
                            }
                            if (isMaster) { // DRS 20151031 - Create/Run the XML with real (cw_epg.exe) task
                                arf = new AutorunFile(hourToSend, minuteToSend, durationMinutes, parameters, isMaster, 0);
                                goodWrite = arf.saveToDisk(CaptureManager.dataPath + "Autorun.xml"); // DRS 20151031 - Saves XML file with TASK (for master only) since leadTimeSeconds is zero.
                                if (goodWrite) {
                                    create[3] = "CWEPG_Autorun_Task"; 
                                    tcclc = new TaskCreateCommandLine(create);
                                    System.out.println(new Date() + " Creating scheduled task CWEPG_Autorun_Task for " + (isMaster?"Master":"Helper") + " with no lead time."); 
                                    goodResult = tcclc.runProcess();
                                    if (!goodResult) htmlMessage += "failed to handle " + tcclc.getCommands() + "<br>";
                                    else {
                                        htmlMessage += "/create:<br>";
                                        htmlMessage += tcclc.getOutput() + "<br>";
                                        htmlMessage += tcclc.getErrors() + "<br>";
                                    }
                                } else {
                                    htmlMessage += "Failed to write Autorun.xml.<br>";
                                }
                            }
                        } else {
                            htmlMessage += "Failed to write Autorun.xml..<br>";
                        }
                    } else if(!isCreateRequest) {                                            // this is a 'delete'
                        String[] delete = {"/delete", "/tn", "CWEPG_Autorun_Timer", "/f"};  
                        TaskCreateCommandLine tccld = new TaskCreateCommandLine(delete);
                        System.out.println(new Date() + " Deleting scheduled task CWEPG_Autorun_Timer."); 
                        boolean goodResult = tccld.runProcess();
                        if (!goodResult) htmlMessage += "failed to handle " + tccld.getCommands() + "<br>";
                        else {
                            htmlMessage += "/delete:<br>";
                            htmlMessage += tccld.getOutput() + "<br>";
                            htmlMessage += tccld.getErrors() + "<br>";
                        }
                        delete[2] = "CWEPG_Autorun_Task";
                        tccld = new TaskCreateCommandLine(delete);
                        System.out.println(new Date() + " Deleting scheduled task CWEPG_Autorun_Task."); 
                        goodResult = tccld.runProcess();
                        if (!goodResult) htmlMessage += "failed to handle " + tccld.getCommands() + "<br>";
                        else {
                            htmlMessage += "/delete:<br>";
                            htmlMessage += tccld.getOutput() + "<br>";
                            htmlMessage += tccld.getErrors() + "<br>";
                        }
                    }
                    System.out.println(new Date() + " " + htmlMessage.replaceAll("\\r|\\n",""));
                    out.print(HEAD + htmlMessage + FOOT);
                }
                // DRS 20110215 - Added 'else if'    
            } else if (action.equals("/sortchannels")) {
                String message = tunerManager.sortChannelMap();
                out.print(HEAD + message + FOOT);
                // DRS 20150429 - Added 'else if'    
            } else if (action.equals("/clockchecker")) {
                String minutes = (String)request.get("minutes");
                String message = ClockChecker.getInstance().setPollInterval(minutes);
                out.print(HEAD + message + FOOT);
                eventWorthyAction = false;
            } else if (action.equals("/emailer")){
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
            } else {
                out.print(HEAD + "<br><h2>Command Not Understood</h2><br>" + FOOT);
            }
            if (eventWorthyAction) CaptureManager.registerEvent();
		} catch (Exception e) {
            System.err.println(new Date() + " Error in TinyConnection.run");
            e.printStackTrace();
            out.print(e.getMessage());
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
