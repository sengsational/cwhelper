package org.cwepg.hr;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.util.Date;

import org.cwepg.reg.Registry;
import org.cwepg.svc.HdhrCommandLine;

public class LineUpHdhr extends LineUp {
	
    public void scan(Tuner tuner, boolean useExistingFile, String signalType, int maxSeconds) throws Exception {
        StringBuffer debugBuf = new StringBuffer(tuner.getFullName() + " - useExistingFile:" + useExistingFile + "  ");
        String scanOutput = null;
        this.signalType = signalType;
        if (!useExistingFile){
            scanOutput = getScanOutputFromDevice(tuner, maxSeconds);
            loadChannelsFromScanOutput(scanOutput, tuner);
        } else {
            String xmlFileName = getRegistryXmlFileName(tuner);
            String airCatSource = getRegistryAirCatSource(tuner);
            boolean hdhrXmlExists = checkForHdhrXmlFile(xmlFileName);
            
            //DRS 20130310 - Always ignore CableCARD.xml
            String cableCardIgnoredMessage = "";
            if (xmlFileName != null && xmlFileName.toUpperCase().startsWith("CABLECARD")) {
                hdhrXmlExists = false;
                cableCardIgnoredMessage = "(CableCARD.xml was ignored)";
            }
            
            //DRS 20130310 - Ignore xml file if older than scan
            String oldXmlFileIgnoredMessage = "";
            if (hdhrXmlExists && getHdhrXmlFileDate(xmlFileName).before(getScanFileDate(tuner))){
                hdhrXmlExists = false;
                oldXmlFileIgnoredMessage = xmlFileName + ".xml was older than the scan file, so is ignored.";
            }
            
            boolean previousScanExists = checkForPreviousScan(tuner);
            debugBuf.append("hdhrXmlExists:" + hdhrXmlExists + " " + cableCardIgnoredMessage + " " + oldXmlFileIgnoredMessage + " ");
            debugBuf.append("previousScanExists:" + previousScanExists + "  ");
            System.out.println(new Date() + " " + debugBuf.toString());
            if (previousScanExists){
                scanOutput = getScanOutputFromFile(tuner);
                loadChannelsFromScanOutput(scanOutput, tuner);
                if (hdhrXmlExists){
                    updateChannelsFromXml(xmlFileName, airCatSource, tuner);
                }
            } else if (hdhrXmlExists) {
                loadChannelsFromXml(xmlFileName, airCatSource, tuner);
            }
        }
    }
    
    private void loadChannelsFromXml(String xmlFileName, String airCatSource, Tuner tuner) throws Exception {
        String fileNameString = getRegistryCommonAppFolder() + File.separator + "SiliconDust\\HDHomeRun\\" + xmlFileName + ".xml";
        BufferedReader in = new BufferedReader(new FileReader(fileNameString));
        StringBuffer aProgramDefinition = new StringBuffer();
        String l = null;
        boolean build = false;
        double priority = 1000 + tuner.number * 100;
        while ((l = in.readLine()) != null){
            if (l.trim().equals("<Program>")){
                build = true;
                continue;
            } else if (l.trim().equals("</Program>")){
                build = false;
                addChannel(new ChannelDigital(aProgramDefinition.toString(), airCatSource, xmlFileName, tuner, priority++));
                aProgramDefinition = new StringBuffer();
                continue;
            }
            if (build){
                aProgramDefinition.append(l);
            }
        }
        in.close();
    }

    private void updateChannelsFromXml(String xmlFileName, String airCatSource, Tuner tuner) throws Exception {
        StringBuffer debugBufReplaced = new StringBuffer(new Date() + " Updated items.  HDHR scanfile channel replaced by HDHR XML file data: ");
        StringBuffer debugBufIgnored  = new StringBuffer(new Date() + " Ignored items.  HDHR scanfile channel did not contain entry for:      ");
        String fileNameString = getRegistryCommonAppFolder() + File.separator + "SiliconDust\\HDHomeRun\\" + xmlFileName + ".xml";
        BufferedReader in = new BufferedReader(new FileReader(fileNameString));
        StringBuffer aProgramDefinition = new StringBuffer();
        String l = null;
        boolean build = false;
        double priority = 2000 + tuner.number * 100;
        while ((l = in.readLine()) != null){
            if (l.trim().equals("<Program>")){
                build = true;
                continue;
            } else if (l.trim().equals("</Program>")){
                build = false;
                Channel hdhrXmlChannel = new ChannelDigital(aProgramDefinition.toString(), airCatSource, xmlFileName, tuner, priority++);
                boolean enabledFalse = aProgramDefinition.toString().toUpperCase().indexOf("<ENABLED>FALSE") > -1;
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
                aProgramDefinition = new StringBuffer();
                continue;
            }
            if (build){
                aProgramDefinition.append(l);
            }
        }
        in.close();
        System.out.println(debugBufReplaced);
        System.out.println(debugBufIgnored);
    }

    private boolean updateChannel(Channel hdhrXmlChannel) {
        Channel foundChannel = channels.get(hdhrXmlChannel.channelKey);
        String xmlChannelName = hdhrXmlChannel.channelDescription;
        if ( foundChannel != null){
            channels.put(hdhrXmlChannel.channelKey, hdhrXmlChannel);
            return true;
        } 
        return false;
    }

    String getRegistryXmlFileName(Tuner tuner) {
        String xmlFileName = null;
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
    
    private boolean checkForHdhrXmlFile(String airCableSource){
        String fileNameString = getRegistryCommonAppFolder() + File.separator + "SiliconDust\\HDHomeRun\\" + airCableSource + ".xml";
        File xmlFile = new File(fileNameString);
        System.out.println(new Date() + " Checking " + xmlFile.getAbsolutePath() + " can read? " + xmlFile.canRead());
        return xmlFile.canRead();
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
        HdhrCommandLine cl = new HdhrCommandLine(commands, maxSeconds, false);
        boolean goodResult = cl.runProcess();
        if (!goodResult) System.out.println("failed to get scan output from " + cl.getCommands());
        scanOutput = cl.getOutput();
        System.out.println("scan complete (" + scanOutput.length() + ").");
        if (scanOutput.length() < 200){
            System.out.println(scanOutput);
        }
        scanOutput += "SCANNING:";
        return scanOutput;
    }

    private void loadChannelsFromScanOutput(String scanOutput, Tuner tuner) throws Exception {
        BufferedReader in = new BufferedReader(new StringReader(scanOutput));
        String l = null;
        double priority = 1000 + tuner.number * 100;
        StringBuffer buf = new StringBuffer();
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
                if (l.indexOf("PROGRAM")> -1 && (l.indexOf("none") > 0 || l.indexOf("encrypted") > 0 )){
                    // discard program none and program encrypted
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
    
}
