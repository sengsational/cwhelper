/*
 * Created on Aug 16, 2009
 *
 */
package org.cwepg.hr;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;

public class TunerHdhr extends Tuner {

    int tunerType = Tuner.HDHR_TYPE;
    public static final int ORIGINAL = 0;
    public static final int VCHANNEL = 1;
    boolean isVchannel = false;
    String ipAddressTuner;
    String ipAddressMachine;
    private int httpCapability = -1;
    
    //////////// CONSTRUCTORS & RELATED ////////////////
    public TunerHdhr(String id, int number, boolean addDevice){
        System.out.println(new Date() + " ==========>  TunerHdhr<init> STARTING  <===============");
        this.id = id;
        this.number = number;
        lineUp = new LineUpHdhr();
        getDefaultRecordPathFromFile();
        if (addDevice) loadCapturesFromFile();
        if (this.recordPath.equals("")) setLastRecordPathFromHistory();
        if (addDevice) TunerManager.getInstance().addTuner(this); 
        this.analogFileExtension = "ts";
        System.out.println(new Date() + " ==========>  TunerHdhr<init> COMPLETE  <===============");
    }
    
    public TunerHdhr (String fullName, boolean addDevice) throws Exception {
        System.out.println(new Date() + " ==========>  TunerHdhr<init> STARTING  <===============");
        if (fullName == null) throw new Exception ("Tuner name must be specified.");
        int dashLoc = fullName.indexOf("-");
        if (dashLoc > 0 && !fullName.endsWith("-")){
            this.id = fullName.substring(0, dashLoc);
            this.number = Integer.parseInt(fullName.substring(dashLoc + 1));
            lineUp = new LineUpHdhr();
            if(addDevice) loadCapturesFromFile();
            this.analogFileExtension = "ts";
        } else {
            throw new Exception ("Can not create tuner named " + fullName);
        }
        if (this.recordPath.equals("")) setLastRecordPathFromHistory();
        if (addDevice) TunerManager.getInstance().addTuner(this);
        System.out.println(new Date() + " ==========>  TunerHdhr<init> COMPLETE  <===============");
    }
    
    //DRS 20181025
    // For testing only
    public TunerHdhr (String fullName, boolean addDevice, int hdhrModel) throws Exception {
        this(fullName, addDevice);
        this.isVchannel = (hdhrModel == TunerHdhr.VCHANNEL);
    }
    
    //DRS 20181025
    public TunerHdhr(String id, int number, boolean addDevice, int hdhrModel, String ipAddress) {
        this(id, number, addDevice);
        this.tunerType = Tuner.HDHR_TYPE;
        this.isVchannel = (hdhrModel == TunerHdhr.VCHANNEL);
        this.ipAddressTuner = ipAddress; // might be null
        if (ipAddress != null && !"".equals(ipAddress)) {
            this.ipAddressMachine = getMachineIpForTunerIp(ipAddress);
        }
        System.out.println(new Date() + " TunerHdhr constructor with ipAddressTuner " + ipAddress + " resulting in ipAddressMachine " + ipAddressMachine + " isVchannel (new tuner type) " + isVchannel);
    }
    
    //DRS 20181112
    private String getMachineIpForTunerIp(String tunerIp) {
        boolean debug = false;
        if (debug) System.out.println(new Date() + " DEBUG: tunerIp " + tunerIp);
        String[] tunerIpNumbers = tunerIp.split("\\.");
        if (debug) System.out.println(new Date() + " DEBUG: tunerIpNumbers length " + tunerIpNumbers.length);
        if (tunerIpNumbers.length < 4){
            if (debug) System.out.println(new Date() + " DEBUG: there were not 4 numbers in the IP address provided [" + tunerIp +"]");
            return null; 
        }
        String tunerIpPrefix = tunerIpNumbers[0] + "." + tunerIpNumbers[1];
        if (debug) System.out.println(new Date() + " DEBUG: tunerIpPrefix " + tunerIpPrefix);
        StringBuffer buf = new StringBuffer();
        String foundMachineAddress = null;
        try {
            for (int tries = 2; tries > 0; tries--) {
                Enumeration e = NetworkInterface.getNetworkInterfaces();
                if (debug) System.out.println(new Date() + " DEBUG: interface has elements " + e.hasMoreElements());
                while(e.hasMoreElements()) {
                    NetworkInterface n = (NetworkInterface) e.nextElement();
                    if (debug) System.out.println(new Date() + " DEBUG: interface " + n.getName() + " " + n.getDisplayName());
                    buf.append("  Interface [" + n.getName() + "] Addresses [" );
                    Enumeration ee = n.getInetAddresses();
                    if (debug) System.out.println(new Date() + " DEBUG: addresses has elements " + ee.hasMoreElements());
                    while (ee.hasMoreElements()) {
                        InetAddress i = (InetAddress) ee.nextElement();
                        String aMachineAddress = i.getHostAddress();
                        if (debug) System.out.println(new Date() + " DEBUG: address " + aMachineAddress);
                        buf.append(aMachineAddress + ", ");
                        if (aMachineAddress.startsWith(tunerIpPrefix)) {
                            if (debug) System.out.println(new Date() + " DEBUG: found address: " + aMachineAddress + " returning.");
                            foundMachineAddress = aMachineAddress;
                            break;
                        }
                    }
                    buf.append("]");
                    if (foundMachineAddress != null) break;
                }
                if (foundMachineAddress != null) {
                    break;
                } else {
                    System.out.println(new Date() + " The prefix [" + tunerIpPrefix + "] was not found in the list of interfaces [" + buf.toString() + "].  Retrying after 1 second.");
                    Thread.sleep(1000);
                }
            }
        } catch (Exception e) {
            System.out.println(new Date()  + " ERROR: Could not get IP address of the machine for tuner " + tunerIp + " " + e.getMessage());
        } finally {
            if (debug) System.out.println(new Date() + " Looked at these machine IP's: " + buf.toString());
        }
        if (foundMachineAddress == null) {
            System.out.println(new Date() + " The prefix [" + tunerIpPrefix + "] was not found in the list of interfaces [" + buf.toString() + "]");
        }
        return foundMachineAddress; // null if not found
    }

    public void addCapturesFromStore(){
        loadCapturesFromFile();
    }

    private void loadCapturesFromFile() {
        String fileName = CaptureManager.dataPath + id + "-" + number + "" + "_captures.txt";
        String message = "Not reading persisted captures from file ";
        boolean writeIt = false;
        try {
            BufferedReader in = new BufferedReader(new FileReader(fileName));
            String l = null;
            int count = 0;
            while ((l = in.readLine()) != null){
                CaptureHdhr capture = new CaptureHdhr(l, this);
                if (this.recordPath.equals("")){
                    this.recordPath = capture.getRecordPath();
                    System.out.println(new Date() + " Default record path [" + this.recordPath + "] defined for " + this.getFullName() + " from a persisted scheduled capture." );
                }
                if (!capture.hasEnded()){
                    if (CaptureManager.useHdhrCommandLine) {
                        addCaptureAndPersist(capture, writeIt);
                    } else {
                        addCaptureAndPersist(new CaptureHdhrHttp(l, this), writeIt);
                    }
                    count++;
                } else {
                    System.out.println(new Date() + " WARNING: Capture from file was in the past. " + capture);
                }
            }
            in.close();
            System.out.println(new Date() + " Loaded " + count + " captures from " + fileName);
            message = "Could not re-write captures ";
            this.writeHdHrCaptures();
        } catch (Exception e) {
            //e.printStackTrace();
            System.out.println(message + e.getMessage());
        }
    }
    
    // DRS 20120315 - Added method - if there are valid captures, then wait, try again, and set liveDevice.
    public ArrayList<CaptureHdhr> getCapturesFromFile() {
        String fileName = CaptureManager.dataPath + id + "-" + number + "" + "_captures.txt";
        String message = "Not reading persisted captures from file ";
        ArrayList<CaptureHdhr> captureList = new ArrayList<CaptureHdhr>();
        try {
            BufferedReader in = new BufferedReader(new FileReader(fileName));
            String l = null;
            int count = 0;
            while ((l = in.readLine()) != null){
                CaptureHdhr capture = new CaptureHdhr(l, this);
                if (this.recordPath.equals("")){
                    this.recordPath = capture.getRecordPath();
                    System.out.println(new Date() + " Default record path [" + this.recordPath + "] defined for " + this.getFullName() + " from a persisted scheduled capture." );
                }
                captureList.add(capture);
            }
            in.close();
            System.out.println(new Date() + " Read " + count + " captures from " + fileName);
        } catch (Exception e) {
            //e.printStackTrace();
            System.out.println(message + e.getMessage());
        }
        return captureList;
    }
    
    private void getDefaultRecordPathFromFile() {
        String fileName = CaptureManager.dataPath + id + "-" + number + "" + "_path.txt";
        String message = "Not reading optional persisted record path from file ";
        try {
            BufferedReader in = new BufferedReader(new FileReader(fileName));
            String l = null;
            while ((l = in.readLine()) != null){
                this.recordPath = l;
                System.out.println(new Date() + " Loaded default record path [" + this.recordPath + "] from " + fileName);
            }
            in.close();
        } catch (Exception e) {
            //e.printStackTrace();
            System.out.println(new Date() + " " + message + e.getMessage());
        }
    }

    private void writeHdHrCaptures() throws CaptureScheduleException {
        try {
            BufferedWriter out = new BufferedWriter(new FileWriter(CaptureManager.dataPath + id + "-" + number + "_captures.txt"));
            for (Iterator iter = captures.iterator(); iter.hasNext();) {
                Capture capture = (Capture)iter.next();
                if (!(capture instanceof CaptureHdhr)) continue;
                System.out.println("writing capture " + capture);
                out.write(capture.getPersistanceData() + "\n");
            }
            out.flush(); out.close();
        } catch (IOException e) {
            throw new CaptureScheduleException("Failed to write capture persistance file. " + e.getMessage());
        }
    }

    public void writeDefaultRecordPathFile() {
        try {
            BufferedWriter out = new BufferedWriter(new FileWriter(CaptureManager.dataPath + id + "-" + number + "_path.txt"));
            System.out.println(new Date() + " Writing default record path data " + this.recordPath);
            out.write(this.recordPath + "\n");
            out.flush(); out.close();
        } catch (IOException e) {
            System.out.println(new Date() + " Failed to write default record path persistance file. " + e.getMessage());
        }
    }

    public void removeDefaultRecordPathFile() {
        try {
            File aFile = new File(CaptureManager.dataPath + id + "-" + number + "_path.txt");
            if (aFile.exists()){
                System.out.println(new Date() + " Removing default record path file for " + id + "-" + number);
                aFile.delete();
            } else {
                System.out.println(new Date() + " Failed to remove default record path persistance file (file does not exist).");
            }
        } catch (Exception e) {
            System.out.println(new Date() + " Failed to remove default record path persistance file. " + e.getMessage());
        }
    }

    private void setLastRecordPathFromHistory() {
        CaptureDataManager cdm = CaptureDataManager.getInstance();
        TreeMap<String, CaptureDetails> captureDetailsMap = cdm.getHistoricalCaptureDetailsForTuner(this.getFullName());
        for (Iterator iter = captureDetailsMap.keySet().iterator(); iter.hasNext();) {
            String key = (String) iter.next();
            CaptureDetails captureDetails = captureDetailsMap.get(key);
            if (captureDetails != null && captureDetails.targetFile != null){
                File aFile = new File(captureDetails.targetFile);
                if (aFile.getParent() != null && !aFile.getParent().equals("")){
                    this.recordPath = aFile.getParent();
                    break; //assume we get most recent first
                }
            }
        }
    }

    public void setIpAddress(String ipAddressTuner) {
        this.ipAddressTuner = ipAddressTuner;
    }
    
    public String getFullName() {
        return this.id + "-" + this.number;
    }
    
    public String getDeviceId(){
        return "" + Integer.parseInt(id, 16);
    }

    public int getType(){
        return this.tunerType;
    }
    
    public void scanRefreshLineUp(boolean useExistingFile, String signalType, int maxSeconds) throws Exception  {
        ((LineUpHdhr)this.lineUp).scan(this, useExistingFile, signalType, maxSeconds);
    }
    
    public void interruptScan() {
        ((LineUpHdhr)this.lineUp).interruptCommandLine();
    }
    
    //DRS 20101218 - Added method - Don't add disabled HDHR tuners
    public boolean isDisabled() {
        if (this.lineUp == null) return false;
        LineUpHdhr aLineUp = (LineUpHdhr)this.lineUp;
        return "Disabled".equals(aLineUp.getRegistryAirCatSource(this)) && "Disabled".equals(aLineUp.getRegistryXmlFileName(this));
    }
    
    public boolean isHttpCapable() {
        if (httpCapability < 0) {
            httpCapability = TunerManager.getInstance().respondsWithWebPage(this)?1:0;
        }
        return httpCapability  == 1;
    }

    /*
     * Queries used by CaptureManager
     */
    
    public void addCaptureAndPersist(Capture newCapture, boolean writeIt) throws CaptureScheduleException {
        if (!slotOpen(newCapture.slot)) throw new CaptureScheduleException("Capture " + newCapture + " conflicts with " + getConflictingCapture(newCapture.slot));
        captures.add(newCapture);
        newCapture.persist(writeIt, true, this);
        newCapture.setWakeup();
        if (writeIt) writeHdHrCaptures();
    }
    
    public void refreshCapturesFromOwningStore(boolean interrupt){
        // Nothing to do since this app is the owning store!
    }
    
    public List<Capture> getCaptures(){
        return this.captures;
    }

    public void removeAllCaptures(boolean localRemovalOnly) {
        for (int i = 0; i < this.captures.size(); i++){
            Capture aCapture = (Capture)captures.get(i);
            aCapture.removeWakeup();
        }
        System.out.println(new Date() + " Removing all captures from TunerHdhr " + this.getFullName() + ".");
        captures.clear();
        try {
            if(!localRemovalOnly) writeHdHrCaptures();
        } catch (CaptureScheduleException e) {
            System.out.println(new Date() + "could not write captures");
            System.err.println(new Date() + "could not write captures");
            e.printStackTrace();
        }
    }
    
    public void removeLocalCapture(Capture capture){
        removeCapture(capture);
    }
    
    public void removeCapture(int j) throws CaptureScheduleException {
        Capture aCapture = (Capture)captures.get(j);
        System.out.println(new Date() + " Removing capture from TunerHdhr. " + aCapture.getTitle());
        captures.remove(j);
        aCapture.removeWakeup();
        writeHdHrCaptures();
    }
    
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
    
    public static void main(String[] args) throws Exception {
        TunerHdhr tuner = new TunerHdhr("1013FADA-1", false);
        //System.out.println("low space: " + tuner.getLowSpaceComment(50));
        System.out.println(tuner.getRecordPath());
        tuner = new TunerHdhr("1010CC54-0", false);
        System.out.println(tuner.getRecordPath());
        tuner = new TunerHdhr("1010CC54-1", false);
        System.out.println(tuner.getRecordPath());
        tuner = new TunerHdhr("1013FADA-0", false);
        System.out.println(tuner.getRecordPath());
        tuner = new TunerHdhr("1013FADA-9", false);
        System.out.println(tuner.getRecordPath());
    }


}
