/*
 * Created on Jun 18, 2011
 *
 */
package org.cwepg.hr;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeMap;

public class TunerExternal extends Tuner {

    static String validKeys = "tunerName,changeChannelCommand,startCaptureCommand,iniFileName,";
    int tunerType = Tuner.EXTERNAL_TYPE;
    String changeChannelCommand = "\"c:\\changeChannel.exe\",2,%STB_CHANNEL%";
    String startCaptureCommand = "\"c:\\recordEvent.exe\",someparameter,\"Some Parameter\",%DURATION_SECONDS%,\"%FILE_NAME%\"";
    String iniFileName = "c:\\default\\CapDVHS.INI";
    Properties channelProperties;
    boolean needsClippedCaptures = false;

    public TunerExternal(Properties externalProps, String prefix, boolean addDevice) {
        System.out.println(new Date() + " ==========>  TunerExternal<init> STARTING  <===============");
        this.id = "EXTERNAL" + prefix;
        try {
            this.id = externalProps.getProperty(prefix + "-tunerName");
            this.changeChannelCommand = externalProps.getProperty(prefix + "-changeChannelCommand");
            this.startCaptureCommand = externalProps.getProperty(prefix + "-startCaptureCommand");
            this.channelProperties = selectChannelProperties(externalProps, prefix);
            this.iniFileName = externalProps.getProperty(prefix + "-iniFileName");
            if (this.iniFileName != null) needsClippedCaptures = true;
            lineUp = new LineUpExternal();
            this.addCapturesFromStore();
            if (addDevice) TunerManager.getInstance().addTuner(this); 
        } catch (Exception e) {
            System.out.println(new Date() + " ERROR: Can not make tuner external:" + e.getMessage() + ". Using bogus defaults.");
        }
        System.out.println(new Date() + " ==========>  TunerExternal<init> COMPLETE  <===============");
    }

    private static Properties selectChannelProperties(Properties externalProps, String prefix){
        Properties props = new Properties();
        Set keysSet = externalProps.keySet();
        for (String s : (Set<String>)keysSet) {
            if (s != null && s.length() > 2 && s.startsWith(prefix)){
                if (TunerExternal.validKeys.indexOf(s.substring(3)) > -1) continue;
                props.put(s, externalProps.get(s));
            }
        }
        return props;
    }

    public Properties getChannelProperties() {
        return this.channelProperties;
    }
    
    @Override
    public void addCaptureAndPersist(Capture newCapture, boolean writeIt) throws CaptureScheduleException {
        if (!slotOpen(newCapture.slot)) throw new CaptureScheduleException("Capture " + newCapture + " conflicts with " + getConflictingCapture(newCapture.slot));
        if (this.needsClippedCaptures ){
            newCapture.slot.adjustEndTimeMinusSeconds(CaptureManager.shortenExternalRecordingsSeconds);
        }
        captures.add(newCapture);
        newCapture.persist(writeIt, true, this);
        newCapture.setWakeup();
        if (writeIt) writeExternalCaptures();
    }

    @Override
    public void addCapturesFromStore() {
        getCapturesFromFile();

    }
    private void getCapturesFromFile() {
        String fileName = CaptureManager.dataPath + id + "_captures.txt";
        String message = "Not reading persisted captures from file ";
        boolean writeIt = false;
        try {
            BufferedReader in = new BufferedReader(new FileReader(fileName));
            String l = null;
            int count = 0;
            while ((l = in.readLine()) != null){
                CaptureExternalTuner capture = new CaptureExternalTuner(l, this);
                if (this.recordPath.equals("")){
                    this.recordPath = capture.getRecordPath();
                    System.out.println(new Date() + " Default record path [" + this.recordPath + "] defined for " + this.getFullName() + " from a persisted scheduled capture." );
                }
                if (!capture.hasEnded()){
                    addCaptureAndPersist(capture, writeIt);
                    count++;
                }
            }
            in.close();
            System.out.println(new Date() + " Loaded " + count + " captures from " + fileName);
            message = "Could not re-write captures ";
            this.writeExternalCaptures();
        } catch (Exception e) {
            //e.printStackTrace();
            System.out.println(message + e.getMessage());
        }
    }


    @Override
    public List<Capture> getCaptures() {
        return this.captures;
    }

    @Override
    public String getDeviceId() {
        return this.id;
    }

    @Override
    public String getFullName() {
        return this.id;
    }

    @Override
    public int getType() {
        return this.tunerType;
    }

    @Override
    public void refreshCapturesFromOwningStore(boolean interrupt) {
        //      Nothing to do since this app is the owning store!
    }

    @Override
    public void removeAllCaptures(boolean localRemovalOnly) {
        for (int i = 0; i < this.captures.size(); i++){
            Capture aCapture = (Capture)captures.get(i);
            aCapture.removeWakeup();
        }
        System.out.println(new Date() + " Removing all captures from TunerHdhr " + this.getFullName() + ".");
        captures.clear();
        try {
            if(!localRemovalOnly) writeExternalCaptures();
        } catch (CaptureScheduleException e) {
            System.out.println(new Date() + "could not write captures");
            System.err.println(new Date() + "could not write captures");
            e.printStackTrace();
        }
    }

    private void writeExternalCaptures() throws CaptureScheduleException {
        try {
            BufferedWriter out = new BufferedWriter(new FileWriter(CaptureManager.dataPath + id + "_captures.txt"));
            for (Iterator iter = captures.iterator(); iter.hasNext();) {
                Capture capture = (Capture)iter.next();
                if (!(capture instanceof CaptureExternalTuner)) continue;
                System.out.println("writing capture " + capture);
                out.write(capture.getPersistanceData() + "\n");
            }
            out.flush(); out.close();
        } catch (IOException e) {
            throw new CaptureScheduleException("Failed to write capture persistance file. " + e.getMessage());
        }
    }

    @Override
    public void removeCapture(int j) throws CaptureScheduleException {
        Capture aCapture = (Capture)captures.get(j);
        System.out.println(new Date() + " Removing capture from TunerExternal. " + aCapture.getTitle());
        captures.remove(j);
        aCapture.removeWakeup();
        writeExternalCaptures();
    }

    @Override
    public void removeCapture(Capture capture) {
        for (int i = 0; i < captures.size(); i++){
            if (((Capture)captures.get(i)).equals(capture)){
                try {
                    this.removeCapture(i);
                } catch (CaptureScheduleException e) {
                    System.out.println(new Date() + "could not remove captures");
                    System.err.println(new Date() + "could not remove captures");
                    e.printStackTrace();
                }
                break;
            }
        }
    }

    @Override
    public void removeDefaultRecordPathFile() {
        try {
            File aFile = new File(CaptureManager.dataPath + id + "_path.txt");
            if (aFile.exists()){
                System.out.println(new Date() + " Removing default record path file for " + id);
                aFile.delete();
            } else {
                System.out.println(new Date() + " Failed to remove default record path persistance file (file does not exist).");
            }
        } catch (Exception e) {
            System.out.println(new Date() + " Failed to remove default record path persistance file. " + e.getMessage());
        }
    }

    @Override
    public void removeLocalCapture(Capture capture) {
        removeCapture(capture);    
    }

    @Override
    public void scanRefreshLineUp(boolean useExistingFile, String signalType, int maxSeconds) throws Exception {
        ((LineUpExternal)this.lineUp).scan(this, useExistingFile, signalType, maxSeconds);
    }

    @Override
    public void writeDefaultRecordPathFile() {
        try {
            BufferedWriter out = new BufferedWriter(new FileWriter(CaptureManager.dataPath + id + "_path.txt"));
            System.out.println(new Date() + " Writing default record path data " + this.recordPath);
            out.write(this.recordPath + "\n");
            out.flush(); out.close();
        } catch (IOException e) {
            System.out.println(new Date() + " Failed to write default record path persistance file. " + e.getMessage());
        }
    }
    
    public String[] getSetChannelCommandArray(String firstRf) {
        StringTokenizer tok = new StringTokenizer(this.changeChannelCommand, ",");
        String[] commandArray = new String[tok.countTokens()];
        int i = 0;
        while (tok.hasMoreTokens()){
            String aToken = tok.nextToken();
            int channelLoc = aToken.indexOf("%STB_CHANNEL%");
            if (channelLoc > -1) aToken = replacePctValue(aToken, "" + firstRf);
            commandArray[i++] = aToken;
        }
        return commandArray;
    }

    public String[] getStartCaptureCommandArray(String fileName, int seconds) {
        StringTokenizer tok = new StringTokenizer(this.startCaptureCommand, ",");
        String[] commandArray = new String[tok.countTokens()];
        int i = 0;
        while (tok.hasMoreTokens()){
            String aToken = tok.nextToken();
            int secLoc = aToken.indexOf("%DURATION_SECONDS%");
            int fnLoc = aToken.indexOf("%FILE_NAME%");
            if (secLoc > -1) aToken = replacePctValue(aToken, "" + seconds);
            if (fnLoc > -1) aToken = replacePctValue(aToken, fileName);
            commandArray[i++] = aToken;
        }
        return commandArray;
    }
    
    public String getIniFileName(){
        return this.iniFileName;
    }

    private static String replacePctValue(String aToken, String value) {
        StringBuffer buf = new StringBuffer(aToken);
        try {
            int startLoc = buf.indexOf("%");
            int endLoc = buf.indexOf("%", startLoc + 1);
            buf.delete(startLoc, endLoc + 1);
            buf.insert(startLoc, value);
        } catch (RuntimeException e) {
            return aToken;
        }
        return new String(buf);
    }

    public static void main(String[] args) {
        System.out.println("[" + TunerExternal.replacePctValue("this%PCT_VAL%is a test","<worked>") + "]");
        System.out.println("[" +TunerExternal.replacePctValue("this%PCT_VALis a test","<notworked>") + "]");
        System.out.println("[" +TunerExternal.replacePctValue("%PCT_VAL%is a test","<worked>") + "]");
        System.out.println("[" +TunerExternal.replacePctValue("%PCT_VALis a test","<notworked>") + "]");
        System.out.println("[" +TunerExternal.replacePctValue("thisPCT_VAL%is a test","<notworked>") + "]");
        System.out.println("[" +TunerExternal.replacePctValue("%PCT_VALis a test","<notworked>") + "]");
        System.out.println("[" +TunerExternal.replacePctValue("PCT_VAL%is a test","<notworked>") + "]");
        System.out.println("[" +TunerExternal.replacePctValue("%PCT_VAL%is a test","<worked>") + "]");
        System.out.println("[" +TunerExternal.replacePctValue("","<notworked>") + "]");
    }

}
