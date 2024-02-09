package org.cwepg.hr;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Date;

import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.cwepg.reg.Registry;
import org.cwepg.svc.HdhrCommandLine;

public class LineUpHdhr extends LineUp {
    
    HdhrCommandLine mCommandLine = null;
//    private String liveXml;
	
    public void scan(Tuner tuner, boolean useExistingFile, String signalType, int maxSeconds) throws Exception {
        String extraInfo = "";
        try {
            StackTraceElement[] trace = Thread.currentThread().getStackTrace();
            for(int i = 1; i < 4; i++) {
                extraInfo += trace[i].getClassName() + " " + trace[i].getMethodName() + " -- ";
            }
            System.out.println(new Date() + " Call Stack: " + extraInfo);
        } catch (Throwable t) {}

        StringBuffer debugBuf = new StringBuffer(tuner.getFullName() + " - useExistingFile:" + useExistingFile + "  ");
        
        try {
            String scanOutput = null;
            this.signalType = signalType;
            boolean vChannelTuner = ((TunerHdhr)tuner).isVchannel; 
            String xmlFileName = getRegistryXmlFileName(tuner);
            String airCatSource = getRegistryAirCatSource(tuner); // Will be null for tuners not saved in the registry.
            
            if (vChannelTuner) {// <<<<<<<<<<<<< NOTE: This will make all vchannel logic in the last 'else' irrelevant!!!
                debugBuf.append("vChannelTuner: true ");
                String xmlOutput = "";
                if (useExistingFile) {
                    xmlOutput = LineUpHdhr.getXmlOutputFromDevice(tuner, maxSeconds);
                } else {
                    xmlOutput = this.scanAndGetXmlOutputFromDevice(tuner, maxSeconds);
                }
                loadChannelsFromXmlString(xmlOutput, xmlFileName, airCatSource, tuner);
            } else if (!useExistingFile){
                debugBuf.append("useExistingFile: false ");
                scanOutput = getScanOutputFromDevice(tuner, maxSeconds);
                loadChannelsFromScanOutput(scanOutput, tuner);
            } else {
                debugBuf.append("default processing: true ");
                long minimumSize = 100L; // completely arbitrary.  The 'empty' XML file had the XML header and one empty element.
                boolean validHdhrXmlExists = checkForHdhrXmlFile(xmlFileName, minimumSize);
                
                //DRS 20130310 - Always ignore CableCARD.xml
                String cableCardIgnoredMessage = "";
                if (xmlFileName != null && xmlFileName.toUpperCase().startsWith("CABLECARD")) {
                    validHdhrXmlExists = false;
                    cableCardIgnoredMessage = "(CableCARD.xml was ignored)";
                }
                
                //DRS 20130310 - Ignore xml file if older than scan
                String oldXmlFileIgnoredMessage = "";
                if (validHdhrXmlExists && getHdhrXmlFileDate(xmlFileName).before(getScanFileDate(tuner))){
                    validHdhrXmlExists = false; //DRS 20200831 - Uncommented, per Terry //DRS 20200316 - Per Terry (commented)
                    oldXmlFileIgnoredMessage = xmlFileName + ".xml was older than the scan file, so is ignored."; //DRS 20200831 - Uncommented, per Terry //DRS 20200316 - Per Terry (commented)
                    //oldXmlFileIgnoredMessage = xmlFileName + ".xml was older than the scan file, but it is still being used."; //DRS 20200831 - Commented, per Terry //DRS 20200316 - Per Terry (new line)
                }
                
                boolean previousScanExists = checkForPreviousScan(tuner);
                debugBuf.append("validHdhrXmlExists:" + validHdhrXmlExists + " " + cableCardIgnoredMessage + " " + oldXmlFileIgnoredMessage + " ");
                debugBuf.append("previousScanExists:" + previousScanExists + "  ");
                
                // DRS 20220707 - Added 7 - Get scan from http if traditional processing not providing.
                boolean liveXmlAvailable = false;
                String liveXmlFileName = null;
                if (!previousScanExists && !validHdhrXmlExists) {
                    liveXmlFileName = loadXmlFromDevice(tuner, maxSeconds);
                    if (liveXmlFileName != null) liveXmlAvailable = true;
                }
                debugBuf.append("liveXmlAvailable:" + liveXmlAvailable + " ");
                
                System.out.println(new Date() + " " + debugBuf.toString());
                boolean useUpdatedLogic = true;
                if (useUpdatedLogic) {
                    if (validHdhrXmlExists) {
                        loadChannelsFromXml(xmlFileName, airCatSource, tuner);
                        debugBuf.append("validHdhrXmlExists:" + validHdhrXmlExists);
                    } else if (previousScanExists){
                        scanOutput = getScanOutputFromFile(tuner);
                        loadChannelsFromScanOutput(scanOutput, tuner);
                    } else if (liveXmlAvailable) {
                        loadChannelsFromXml(liveXmlFileName, airCatSource, tuner);
                    } else {
                        System.out.println(new Date() + " ERROR: no valid hdhr xml file and no previous scan file.");
                    }
                } else {
                    if (previousScanExists){
                        scanOutput = getScanOutputFromFile(tuner);
                        loadChannelsFromScanOutput(scanOutput, tuner);
                        if (validHdhrXmlExists){
                            updateChannelsFromXml(xmlFileName, airCatSource, tuner);
                        }
                    } else if (validHdhrXmlExists) {
                        loadChannelsFromXml(xmlFileName, airCatSource, tuner);
                    }
                }
            }
        } catch (Exception t) {
            System.out.println(new Date() + " ERROR: Exception in LineUpHdhr.scan(): " + t.getClass().getName() + " " + t.getMessage());
            System.out.println(new Date() + " ERROR: " + debugBuf.toString());
            throw t;
        }
    }
    
    // DRS 20200225 - Added Method - Shouldn't be used / needed
    //private void loadAltChannelsForTunerNotUsed(TunerHdhr tuner) {
    //    System.out.println(new Date() + " Loading alternate channels with same alphaDescription for " + tuner.id);
    //    Collection<Channel> channels = tuner.lineUp.channels.values();
    //    for (Channel channel : channels) {
    //        Channel[] altChannelList = tuner.lineUp.getChannelsByDescription(channel.alphaDescription);
    //        ArrayList<String> noCopyVirtuals = null;
    //        channel.addAltChannels(altChannelList, "alphaDescription match", noCopyVirtuals);
    //    }
    //}
    
    private void loadChannelsFromXml(String xmlFileName, String airCatSource, Tuner tuner) throws Exception {
        String fileNameString = getRegistryCommonAppFolder() + File.separator + "SiliconDust\\HDHomeRun\\" + xmlFileName + ".xml";
        ArrayList<String> programDefinitions = getProgramDefinitions((TunerHdhr)tuner, fileNameString);
        tuner.getDeviceId();
        double priority = getStartPriority(tuner.getDeviceId(), tuner.number);
        System.out.println(new Date() + " Clearing channels for tuner- " + tuner.getFullName());
        tuner.lineUp.clearAllChannels(); // DRS 2019 12 15 - Added Line
        for (String programDefinition : programDefinitions) {
            addChannel(new ChannelDigital(programDefinition, airCatSource, xmlFileName, tuner, priority++));
        }
    }
    
    // DRS 20220707 - Added method for when traditional scan unavailable
    private String loadXmlFromDevice(Tuner tuner, int maxSeconds) {
        String liveXmlFileName = null;
        String xmlOutput = scanAndGetXmlOutputFromDevice(tuner, 60);
        if (xmlOutput == null || xmlOutput.length() == 0) {
            return null;
        }
        // Save to xml to file
        liveXmlFileName = CaptureManager.dataPath + "\\" + tuner.getFullName() + ".xml";
        BufferedWriter out = null;
        try {
            out = new BufferedWriter(new FileWriter(liveXmlFileName));
            out.write(xmlOutput + "\n");
        } catch (Throwable t) {
            System.out.println(new Date() + " ERROR: Could not write to " + liveXmlFileName + ": " + t.getMessage());
        } finally {
            try {out.close();} catch (Throwable t){}
        }
        return liveXmlFileName;
    }
    
    // Try to return the last three digits of the deviceId times 10000 or a random number
    private double getStartPriority(String deviceId, int number) {
        if (number < 0 || number > 20) number = (int)Math.round(Math.random() * 10) + 1; // Probably never gets used, but just in case.
        if (deviceId != null && deviceId.length() > 3) {
            try {
                String lastThree = deviceId.substring(deviceId.length() - 3);
                double startNumber = Integer.parseInt(lastThree) * 10000 + number * 1000;
                return startNumber;
            } catch (Throwable t ) {
                // fall through
            }
        }
        double startNumber = Math.round(Math.random() * 1000) * 10000 + number * 1000;
        return startNumber;
    }

    private void loadChannelsFromXmlString(String programDefinitionString, String xmlFileName, String airCatSource, Tuner tuner) throws Exception {
        if (programDefinitionString == null || "".equals(programDefinitionString)) System.out.println(new Date() + " ERROR: nothing to parse for tuner " + tuner);
        BufferedReader in = new BufferedReader(new StringReader(programDefinitionString));
        ArrayList<String> programDefinitions = getProgramDefinitions(((TunerHdhr)tuner), in);
        double priority = getStartPriority(tuner.getDeviceId(), tuner.number);
        System.out.println(new Date() + " Clearing channels for tuner: " + tuner.getFullName());
        tuner.lineUp.clearAllChannels(); // DRS 2019 12 15 - Added Line
        int count = 0;
        for (String programDefinition : programDefinitions) {
            addChannel(new ChannelDigital(programDefinition, airCatSource, xmlFileName, tuner, priority++));
            count++;
        }
        System.out.println(new Date() + " " + count + " channels added to tuner: " + tuner.getFullName());
    }
    
    private ArrayList<String> getProgramDefinitions(TunerHdhr tuner, String fileNameString) throws Exception {
        return getProgramDefinitions(tuner, new BufferedReader(new FileReader(fileNameString)));
    }
    
    private ArrayList<String> getProgramDefinitions(TunerHdhr tuner, BufferedReader in) throws Exception {
        ArrayList<String> programDefinitions = new ArrayList<String>();
        StringBuffer aProgramDefinition = new StringBuffer();
        String l = null;
        boolean build = false;
        int ignoredShortChannelCount = 0;
        int skippedAtsc3ChannelCount = 0;
        while ((l = in.readLine()) != null){
            if (l.trim().equals("<Program>")){
                build = true;
                continue;
            } else if (l.trim().equals("</Program>") || (l.contains("<Program>") && l.contains("</Program>"))){
                build = false;
                // Run this if the whole thing is on one line.  Usually we 'build' and it's on separate lines.
                if (l.contains("<Program>") && l.contains("</Program>")) {
                    int programLoc = l.indexOf("<Program>"); // Probably 0
                    l = l.substring(programLoc + 9); // remove "<Program>" from the start of the string
                    int endProgramLoc = Math.min(l.indexOf("</Program>") + 10, l.length()); // keep "</Program>" in string (for count)
                    aProgramDefinition.append(l.substring(0, endProgramLoc)); 
                }
                boolean tunerNeedsFullDefinition = !tuner.isVchannel;
                int definitionEntryCount = 99;
                try {definitionEntryCount = aProgramDefinition.toString().split("</").length;} catch (Throwable t) {System.out.println(new Date() + " Error counting program definition entries " + aProgramDefinition + " " + t.getMessage());}
                if (tunerNeedsFullDefinition && definitionEntryCount < 5) {
                    //String logName = aProgramDefinition.toString();
                    //logName = logName.substring(0, Math.min(logName.length() - 1, 20)) + "...";
                    //System.out.println(new Date() + " Ignoring short channel [" + logName + "]");
                    ignoredShortChannelCount++;
                } else if (skipAtsc3Channel(tuner, aProgramDefinition.toString())) {   
                	skippedAtsc3ChannelCount++;
                } else {
                    programDefinitions.add(aProgramDefinition.toString());
                }
                aProgramDefinition = new StringBuffer();
                continue;
            }
            if (build){
                aProgramDefinition.append(l);
            }
        }
        in.close();
        if (ignoredShortChannelCount > 0) System.out.println(new Date() + " Ignored " + ignoredShortChannelCount + " channels for tuner " + tuner.getFullName());
        if (skippedAtsc3ChannelCount > 0) System.out.println(new Date() + " Ignored " + skippedAtsc3ChannelCount + " ATSC 3.0 channels for tuner " + tuner.getFullName());
        return programDefinitions;
    }

    private boolean skipAtsc3Channel(Tuner tuner, String aChannelDef) {
//    	System.out.print(new Date() + " skip Atsc3Channel called - protocol:" + getFromXml(aChannelDef, "Modulation") + " vc:" + getFromXml(aChannelDef, "GuideNumber") + " tuner:" + tuner.getFullName());
    	if (ChannelDigital.getFromXml(aChannelDef, "Modulation") != "8vsb") {  // no need to test more if known 8vsb
        	String virtualChannelNo = ChannelDigital.getFromXml(aChannelDef, "GuideNumber");
        	if (virtualChannelNo.contains(".")) {  // Not cable or non ATSC guide number
    			int vc = Integer.parseInt(virtualChannelNo.substring(0, virtualChannelNo.indexOf(".")));
    			if ((vc > 99) && (vc < 200)) {  // We have a virtual channel number of the form "1xx.yy"
    				int devID = Integer.parseInt(tuner.getDeviceId());
    				if ((devID < 0x10800000) || (devID > 0x13100000)) {  // HDHR is old or Prime, not 4k type
//    					System.out.println( " returning true");
    					return true;
    				} else if (tuner.number > 1) {  // HDHR is 4k type but tuners 2 & 3 are ATSC 1.0 only
//    					System.out.println( " returning true");
    					return true;
    				}
    			}
    		}
    	}
//		System.out.println( " returning false");
    	return false;
    }


    private void updateChannelsFromXml(String xmlFileName, String airCatSource, Tuner tuner) throws Exception {
        StringBuffer debugBufReplaced = new StringBuffer(new Date() + " Updated items.  HDHR scanfile channel replaced by HDHR XML file data: ");
        StringBuffer debugBufIgnored  = new StringBuffer(new Date() + " Ignored items.  HDHR scanfile channel did not contain entry for:      ");
        String fileNameString = getRegistryCommonAppFolder() + File.separator + "SiliconDust\\HDHomeRun\\" + xmlFileName + ".xml";
        
        ArrayList<String> programDefinitions = getProgramDefinitions((TunerHdhr)tuner, fileNameString);
        double priority = getStartPriority(tuner.getDeviceId(), tuner.number);
        for (String programDefinition : programDefinitions) {
            Channel hdhrXmlChannel = new ChannelDigital(programDefinition, airCatSource, xmlFileName, tuner, priority++);
            boolean enabledFalse = programDefinition.toUpperCase().indexOf("<ENABLED>FALSE") > -1;
            if (!enabledFalse){
                boolean replaced = updateChannel(hdhrXmlChannel);
                if (replaced){
                    debugBufReplaced.append(hdhrXmlChannel.alphaDescription + "/" + hdhrXmlChannel.channelKey + ", ");
                } else {
                    debugBufIgnored.append(hdhrXmlChannel.alphaDescription + "/" + hdhrXmlChannel.channelKey + ", ");
                }
            } else {
                System.out.println(new Date() + " Skipping disabled channel: " + hdhrXmlChannel.alphaDescription + "/" + hdhrXmlChannel.channelKey);
            }
        }

        System.out.println(debugBufReplaced);
        System.out.println(debugBufIgnored);
    }

    private boolean updateChannel(Channel hdhrXmlChannel) {
        Channel foundChannel = channels.get(hdhrXmlChannel.channelKey);
        //String xmlChannelName = hdhrXmlChannel.channelDescription;
        if ( foundChannel != null){
            channels.put(hdhrXmlChannel.channelKey, hdhrXmlChannel);
            return true;
        } 
        return false;
    }

    String getRegistryXmlFileName(Tuner tuner) {
        String xmlFileName = null;
        if (TunerManager.skipRegistryForTesting || !CaptureManager.useHdhrCommandLine) return xmlFileName;  //DRS 20220707 - no registry if command line unavailable.
        try {
            String location = "SOFTWARE\\Silicondust\\HDHomeRun\\Tuners\\" + tuner.getFullName();
            xmlFileName = Registry.getStringValue("HKEY_LOCAL_MACHINE", location, "Source");
            if (xmlFileName == null) throw new Exception("Could not find 'Source' at HKLM " + location);
        } catch (Exception e) {
            System.out.println(new Date() + " ERROR: Could not read registry for HDHomeRun. " + e.getMessage());
            //System.err.println(new Date() + " ERROR: Could not read registry for HDHomeRun. " + e.getMessage());
            //e.printStackTrace();
        }
        return xmlFileName;
    }

    String getRegistryAirCatSource(Tuner tuner) {
        String airCatSource = null;
        if (TunerManager.skipRegistryForTesting || !CaptureManager.useHdhrCommandLine) return "air"; //DRS 20220707 - no registry if command line unavailable.
        try {
            String location = "SOFTWARE\\Silicondust\\HDHomeRun\\Tuners\\" + tuner.getFullName();
            airCatSource = Registry.getStringValue("HKEY_LOCAL_MACHINE", location, "SourceType");
            if (airCatSource == null) throw new Exception("Could not find 'SourceType' at HKLM " + location);
        } catch (Exception e) {
            System.out.println(new Date() + " ERROR: Could not read registry for HDHomeRun. " + e.getMessage());
            //System.err.println(new Date() + " ERROR: Could not read registry for HDHomeRun. " + e.getMessage());
            //e.printStackTrace();
        }
        return airCatSource;
    }
    private String getRegistryCommonAppFolder() {
        String commonAppFolder = null;
        if (TunerManager.skipRegistryForTesting || !CaptureManager.useHdhrCommandLine) { //DRS 20220707 - no registry if command line unavailable.
            commonAppFolder = new File(".").getAbsolutePath();
            System.out.println("WARNING: default being used for commonAppFolder. [" + commonAppFolder + "]");
            return commonAppFolder;
        }
        try {
            String location = "SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Shell Folders\\";
            commonAppFolder = Registry.getStringValue("HKEY_LOCAL_MACHINE", location,"Common AppData");
            if (commonAppFolder == null) throw new Exception("Could not find 'Common AppData' at HKLM " + location);
        } catch (Exception e) {
            System.out.println(new Date() + " ERROR: Could not read registry for HDHomeRun. " + e.getMessage());
            //System.err.println(new Date() + " ERROR: Could not read registry for HDHomeRun. " + e.getMessage());
            //e.printStackTrace();
        }
        return commonAppFolder;
    }
    
    //DRS 20200316 - updated method to check for minimum file size
    private boolean checkForHdhrXmlFile(String airCableSource, long minimumBytes){
        boolean useHdhrXml = false;
        String fileNameString = getRegistryCommonAppFolder() + File.separator + "SiliconDust\\HDHomeRun\\" + airCableSource + ".xml";
        File xmlFile = new File(fileNameString);
        System.out.println(new Date() + " Checking " + xmlFile.getAbsolutePath() + " can read? " + xmlFile.canRead());
        if (xmlFile.canRead()) {
            long xmlByteLength = xmlFile.length();
            String sizeMessage = (xmlByteLength < minimumBytes)?"too small":"OK";
            if ("too small".equals(sizeMessage)) {
                System.out.println(new Date() + "          " + xmlFile.getAbsolutePath() + " was too small to be valid (" + xmlByteLength + ")"); 
                useHdhrXml = false;
            } else {
                System.out.println(new Date() + "          " + xmlFile.getAbsolutePath() + " valid size.  Associated scan files will be ignored.");
                useHdhrXml = true;
            }
        } else {
            System.out.println(new Date() + "          " + xmlFile.getAbsolutePath() + " not readable.  Depending on scan files.");
            useHdhrXml = false;
        }
        return useHdhrXml;
    }
    
    private Date getHdhrXmlFileDate(String airCableSource){
        String fileNameString = getRegistryCommonAppFolder() + File.separator + "SiliconDust\\HDHomeRun\\" + airCableSource + ".xml";
        File xmlFile = new File(fileNameString);
        Date fileDate = new Date(xmlFile.lastModified());
        System.out.println(new Date() + " Checking " + xmlFile.getAbsolutePath() + " date: " + fileDate);
        return fileDate;
    }

    private boolean checkForPreviousScan(Tuner tuner) {
        String fileName = CaptureManager.dataPath + "scan" + tuner.id + "-" + tuner.number + ".txt";
        File scanFile = new File(fileName);
        System.out.println(new Date() + " scanFile:" + scanFile.getAbsolutePath());
        return scanFile.canRead();
    }

    private String getScanOutputFromFile(Tuner tuner) throws Exception  {
        String scanOutput = null;
        System.out.println("reading file " + CaptureManager.dataPath + "scan" + tuner.id + "-" + tuner.number + ".txt");
        StringBuffer buf = new StringBuffer();
        BufferedReader in = new BufferedReader(new FileReader(CaptureManager.dataPath + "scan" + tuner.id + "-" + tuner.number + ".txt"));
        String l = null;
        while ((l = in.readLine()) != null){
            buf.append(l + "\n");
        }
        in.close();
        buf.append("SCANNING:");
        scanOutput = new String(buf);
        return scanOutput;
    }

    private Date getScanFileDate(Tuner tuner) {
        System.out.println("checking date on " + CaptureManager.dataPath + "scan" + tuner.id + "-" + tuner.number + ".txt");
        File scanFile = new File(CaptureManager.dataPath + "scan" + tuner.id + "-" + tuner.number + ".txt");
        return new Date(scanFile.lastModified());
    }
    
    private String getScanOutputFromDevice(Tuner tuner, int maxSeconds) {
        String scanOutput = null;
        System.out.println("starting scan...");
        String outputFile = CaptureManager.dataPath + "scan" + tuner.getFullName() + ".txt"; 
        String[] commands = {tuner.id, "scan", "" + tuner.number, outputFile};  
        if ("8vsb".equals(signalType) || "qam64".equals(signalType) || "qam256".equals(signalType)){
            System.out.println("WARNING: specifying the signalType may cause a malfunction.  Try without " + signalType + " if there are problems.");
            commands = new String[4];
            commands[0] = tuner.id;
            commands[1] = "scan";
            commands[2] = signalType + ":1";
            commands[3] = outputFile;
        }
        mCommandLine = new HdhrCommandLine(commands, maxSeconds, false);
        boolean goodResult = mCommandLine.runProcess();
        if (!goodResult) System.out.println("failed to get scan output from " + mCommandLine.getCommands());
        scanOutput = mCommandLine.getOutput();
        mCommandLine = null;
        System.out.println("scan complete (" + scanOutput.length() + ").");
        if (scanOutput.length() < 200){
            System.out.println(scanOutput);
        }
        scanOutput += "SCANNING:";
        return scanOutput;
    }
    
    public void interruptCommandLine() {
        if (mCommandLine != null) mCommandLine.interrupt();
    }

    private void loadChannelsFromScanOutput(String scanOutput, Tuner tuner) throws Exception {
        BufferedReader in = new BufferedReader(new StringReader(scanOutput));
        String l = null;
        double priority = getStartPriority(tuner.getDeviceId(), tuner.number);
        StringBuffer buf = new StringBuffer();
        System.out.println(new Date() + " Clearing channels for tuner " + tuner.getFullName());
        tuner.lineUp.clearAllChannels(); // DRS 2019 12 15 - Added Line
        while ((l = in.readLine()) != null){
            if (l.indexOf("SCANNING:") > -1){
                try {
                    String programs = removeInvalidPrograms(new String(buf));
                    int programCount = countPrograms(programs);
                    for (int i = 0; i < programCount; i++) {
                        addChannel(new ChannelDigital(programs, i, tuner, priority++));
                    }
                    buf = new StringBuffer();
                } catch (RuntimeException e) {
                    System.err.println(new Date() + " error in loadChannelsFromScanOutput");
                    e.printStackTrace();
                }
            } 
            buf.append(l + "\n");
        }
    }

    private String removeInvalidPrograms(String scanData){
        // scanData expected to start with a "SCANNING:" row and ends before the next "SCANNING:" row
        StringBuffer buf = new StringBuffer();
        try {
            BufferedReader in = new BufferedReader(new StringReader(scanData));
            String l = null;
            while ((l = in.readLine())!= null){
                if (l.indexOf("PROGRAM")> -1 && (l.indexOf("none") > 0 || l.indexOf("encrypted") > 0 ) || l.trim().endsWith(": 0") || l.indexOf("no data") > 0){
                    // discard program none and program encrypted
                    System.out.println(new Date() + " removed " + l + " from scanData");
                } else {
                    buf.append(l + "\n");
                }
            }
        } catch (IOException e) {
            System.out.println(new Date() + " removeInvalidPrograms() " + e.getMessage());
        } 
        return new String(buf);
    }

    private int countPrograms(String scanData){
        // scanData expected to start with a "SCANNING:" row and ends before the next "SCANNING:" row
        int count = 0;
        try {
            BufferedReader in = new BufferedReader(new StringReader(scanData));
            String l = null;
            while ((l = in.readLine())!= null){
                if (l.indexOf("PROGRAM") > -1){
                    count++;
                }             }
        } catch (IOException e) {
            System.out.println("countPrograms " + e.getMessage());
        } 
        return count;
    }
    
    // DRS 20181106 - Added method
    public static String getXmlOutputFromDevice(Tuner tuner, int maxSeconds) {
        TunerHdhr aTuner = (TunerHdhr)tuner;
        String lineupPage = getPage("http://" + aTuner.ipAddressTuner + "/lineup.xml?tuning", maxSeconds, false, false);
        if (lineupPage.length() == 0) {
            System.out.println(new Date() + " ERROR: unable to get lineup xml.");
        }
        return lineupPage;
    }
    
    // DRS 20181106 - Added method
    private String scanAndGetXmlOutputFromDevice(Tuner tuner, int maxSeconds) {
        TunerHdhr aTuner = (TunerHdhr)tuner;
        
        boolean scanPossible = false;
        
        int maxTries = 3;
        int tries = 0;
        while (!scanPossible && tries < maxTries) {
            tries++;
            String scanPossiblePage = getPage("http://" + aTuner.ipAddressTuner + "/lineup_status.json", maxSeconds, false, false);
            if (scanPossiblePage.contains("\"ScanPossible\":1")) {
                scanPossible = true;
                break;
            } else if (scanPossiblePage.length() == 0) {
                System.out.println(new Date() + " ERROR: Not able to reach HDHR device.");
                break;
            } else {
                // got some output, but not good output
                boolean retryItAgain = (tries < maxTries);
                if (retryItAgain) {
                    System.out.println(new Date() + " Scan not possible now. Retrying.");
                } else {
                    System.out.println(new Date() + " ERROR: Scan not possible now.");
                    break;
                }
            }
        }
        
        if (!scanPossible) return "";
       
        String scanStartPage = getPage("http://" + aTuner.ipAddressTuner + "/lineup.post?scan=start", maxSeconds, false, true); // Does not block, returns ""
        System.out.println(new Date() + " Requested scan http://" + aTuner.ipAddressTuner + "/lineup.post?scan=start [" + scanStartPage + "]  Can take several minutes...");
        
        boolean scanFound = false;
        int tryDurationMs = 1000 * 15; // 15 seconds
        maxTries = 4 * 20; // 20 minutes
        tries = 0;
        boolean debug = false;
        boolean triesExceeded = false;
        do {
            tries++;
            try {Thread.sleep(tryDurationMs);} catch (InterruptedException ee){}
            String scanPossiblePage = getPage("http://" + aTuner.ipAddressTuner + "/lineup_status.json", maxSeconds, true, false);
            if (debug) {
                String airCatSource = ((LineUpHdhr)aTuner.lineUp).getRegistryAirCatSource(aTuner);
                System.out.println(new Date() + " DEBUG: airCatSource for " + aTuner.getFullName() + ": " + airCatSource);
                if (scanPossiblePage != null) {
                    int pageLength = scanPossiblePage.length();
                    if (pageLength < 80) System.out.println(new Date() + " DEBUG: scanPossiblePage: " + scanPossiblePage );
                    else System.out.println(new Date() + " DEBUG: scanPossiblePage: "  + scanPossiblePage.substring(0, 75) + " ... ...");
                }
            }
            if (scanPossiblePage.contains("\"ScanPossible\":1")) {
                scanFound = true;
                break;
            }
            triesExceeded = tries >= maxTries;
        } while (!scanFound && !triesExceeded);
        
        if (!scanFound) {
            System.out.println(new Date() + " ERROR: Indication of scan completion never received.");
            return "";
        } else {
            String lineupPage = getPage("http://" + aTuner.ipAddressTuner + "/lineup.xml?tuning", maxSeconds, false, false);
            if (lineupPage.length() == 0) {
                System.out.println(new Date() + " ERROR: unable to get lineup xml.");
            }
            return lineupPage;
        }
    }
    
    static String getPage(String url, int maxSeconds, boolean quiet, boolean isPost) {
        int maxTries = 10;
        return getPage(url, maxSeconds, quiet, isPost, maxTries);
    }

    static String getPage(String url, int maxSeconds, boolean quiet, boolean isPost, int maxTries) {
        long startedAtMs = new Date().getTime();
        for (int i = 0; i < maxTries; i++) {
            HttpClient httpclient = new DefaultHttpClient();
            try {
                String responseBody = "(uninitialized)";
                if (!isPost) {
                    HttpGet httpget = new HttpGet(url); 
                    if (!quiet) System.out.println(new Date() + " Executing request " + httpget.getURI());
                    ResponseHandler<String> responseHandler = new BasicResponseHandler();
                    responseBody = httpclient.execute(httpget, responseHandler);
                } else {
                    HttpPost httppost = new HttpPost(url);
                    if (!quiet) System.out.println(new Date() + " Executing request " + httppost.getURI());
                    ResponseHandler<String> responseHandler = new BasicResponseHandler();
                    responseBody = httpclient.execute(httppost, responseHandler);
                }
                if (!quiet) System.out.println("finished executing request with " + responseBody.length() + " characters received.");
                //if (!quiet && responseBody.length() < 100) System.out.println("[" + responseBody + "]");
                if (responseBody != null && responseBody.contains("Bad Request")) throw new Exception("Get page responded with 'Bad Request'");
                return responseBody;
                
            } catch (Exception e) {
                System.out.println(new Date() + " Failed to get http page. " + url + " " + e.getMessage());
                if (e.getMessage().contains("refused")) {
                    TunerManager.getInstance().removeHdhrByUrl(url);
                    break;
                }
                if (e.getMessage().contains("Not Found")) {
                    break;
                }
            } finally {
                httpclient.getConnectionManager().shutdown();
            }
            try {Thread.sleep(500);} catch (InterruptedException ee){}
            if (((new Date().getTime() - startedAtMs)/1000) > maxSeconds) {
                System.out.println(new Date() + " Timeout on page. " + url );
                return "";
            }
        }
        System.out.println(new Date() + " Tried " + maxTries + "time(s) for " + url + " without success.");
        return "";
    }
}
