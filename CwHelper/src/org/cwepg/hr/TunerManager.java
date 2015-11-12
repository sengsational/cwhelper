package org.cwepg.hr;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.TreeSet;

import org.cwepg.reg.FusionRegistryEntry;
import org.cwepg.reg.Registry;
import org.cwepg.reg.RegistryHelperFusion;
import org.cwepg.svc.HdhrCommandLine;

public class TunerManager {
	
	Map<String, Tuner> tuners = new TreeMap<String, Tuner>();
	static TunerManager tunerManager;
    public String lastReason = "";
	Set<String> capvSet = new TreeSet<String>();
    Properties externalProps;
    public static String fusionInstalledLocation;
    
	private TunerManager(){
        fusionInstalledLocation = RegistryHelperFusion.getInstalledLocation();
        if  (fusionInstalledLocation == null) fusionInstalledLocation = CaptureManager.cwepgPath;
        if ("".equals(fusionInstalledLocation)) fusionInstalledLocation = new File("test").getAbsoluteFile().getParentFile().getAbsolutePath();
	}
	
	public static TunerManager getInstance(){
		if (tunerManager == null){
			tunerManager = new TunerManager();
		}
		return tunerManager;
	}
    
	public int countTuners(){
        // First, read tuners from machine (no alteration of our current list of tuners)
        ArrayList<Tuner> refreshedTuners = new ArrayList<Tuner>();
        boolean addDevice = false;
        List<Tuner> hdhrTunerList = countTunersHdhr(addDevice);
        for (Iterator<Tuner> iter = hdhrTunerList.iterator(); iter.hasNext();) {refreshedTuners.add(iter.next());}
        List<Tuner> myhdTunerList = countTunersMyhd(addDevice);
        for (Iterator<Tuner> iter = myhdTunerList.iterator(); iter.hasNext();) {refreshedTuners.add(iter.next());}
        List<?> fusionTunerList = countTunersFusion(addDevice, false);
        for (Iterator<?> iter = fusionTunerList.iterator(); iter.hasNext();) {refreshedTuners.add((Tuner)iter.next());}
        // DRS 20110619 - Added 2 - externalTuner
        List<Tuner> externalTunerList = countTunersExternal(addDevice);
        for (Iterator<Tuner> iter = externalTunerList.iterator(); iter.hasNext();) {refreshedTuners.add(iter.next());}
        
        // Next, loop through existing tuners looking for changed/deleted tuners
        ArrayList<Tuner> deletedTuners = new ArrayList<Tuner>();
        for (Iterator<String> iter = tuners.keySet().iterator(); iter.hasNext();) {
            Tuner existingTuner = tuners.get(iter.next());
            if (refreshedTuners.contains(existingTuner)){
                // The two lists contain the same item.
                // This means we don't need to do anything.
                // We just remove the tuner from the refreshedTuners list
                // (since anything left in the list, we will be adding later).
                refreshedTuners.remove(existingTuner);
                // but the old (existing tuner) might have different lineup, so refresh that
                refreshLineup(existingTuner);
            } else {
                // this existing tuner is changed or deleted
                // see if we can find it by name
                boolean found = false;
                for (Tuner tuner : refreshedTuners) {
                    if (tuner.getFullName().equals(existingTuner.getFullName())){
                        // take the attributes off of the tuner we just created
                        // and update the existing tuner.
                        found = true;
                        existingTuner.setAnalogFileExtension(tuner.getAnalogFileExtension());
                        existingTuner.setLiveDevice(tuner.getLiveDevice());
                        existingTuner.setRecordPath(tuner.getRecordPath());
                        // We just remove the tuner from the refreshedTuners list
                        // (since anything left in the list, we will be adding later
                        // and we don't want to add this because the existing tuner
                        // just got updated with what we needed).
                        refreshedTuners.remove(existingTuner);
                        // but the old (existing tuner) might have different lineup, so refresh that
                        refreshLineup(existingTuner);
                    }
                    if (found) break;
                }
                if (!found){
                    // The latest and best list from our recent refresh did not
                    // include the tuner, we must conclude it's gone now, so 
                    // we will delete it shortly.
                    deletedTuners.add(existingTuner);
                }
            }
        }
        
        //remove any deleted tuners
        for (Tuner tuner : deletedTuners) {
            System.out.println(new Date() + " Removing deleted tuner: " + tuner.getFullName());
            this.tuners.remove(tuner.getFullName());
            tuner.removeAllCaptures(true); //before deleting a tuner, delete it's captures
        }
        
        // any tuners left in the refreshed list need to be added to the tuner manager
        for (Tuner tuner : refreshedTuners) {
            System.out.println(new Date() + " Adding new or changed: " + tuner.getFullName());
            // refreshed tuners are not added to the tuner manager and did not pick-up captures from file, so do both
            this.tuners.put(tuner.getFullName(), tuner);
            tuner.addCapturesFromStore();
        }
        System.out.println(new Date() + " TunerManager.countTuners returned " + this.tuners.size() + " tuners.");
        return this.tuners.size();
	}
    
    private void refreshLineup(Tuner existingTuner) {
        try {
            existingTuner.scanRefreshLineUp(true, existingTuner.lineUp.signalType, 10000);
        } catch (Throwable e){
            String msg = new Date() + " ERROR: Could not refresh lineup for an existing tuner " + existingTuner;
            System.out.println(msg);
            System.err.println(msg);
            e.printStackTrace();
        }
    }

    /* This Method Only Used in Testing */
    public void countTuner(int tunerType, boolean addDevice){
        removeAllTuners();
        switch (tunerType) {
        case Tuner.FUSION_TYPE:
            countTunersFusion(addDevice, false);
            break;
        case Tuner.MYHD_TYPE:
            countTunersMyhd(addDevice);
            break;
        case Tuner.HDHR_TYPE:
            countTunersHdhr(addDevice);
            break;
        case Tuner.EXTERNAL_TYPE:
            countTunersExternal(addDevice);
            break;
        }
        return;
    }
    
    public List<Tuner> countTunersFusion(boolean addDevice, boolean test){
        ArrayList<Tuner> tunerList = new ArrayList<Tuner>();
        String controlSetName = "CurrentControlSet";
        if (test) controlSetName = "ControlSet0002";
        Map<String, FusionRegistryEntry> entries = RegistryHelperFusion.getFusionRegistryEntries(controlSetName);
        try {
            int analogFileExtensionNumber = Registry.getIntValue("HKEY_CURRENT_USER", "Software\\Dvico\\ZuluHDTV\\Data", "AnalogRecProfile");
            
            Map<String, Map> lookupTables = TunerManager.getLookupTables();
            Map<String, String> recordPathsByNumber = lookupTables.get("recordPathsByNumber");
            Map<String, String> recordPathsByName = lookupTables.get("recordPathsByName");
            Map<String, String> names = lookupTables.get("names");

            // if the names array has a matching uinumber, then we apply the name, else we keep default
            for (Iterator<String> iter = entries.keySet().iterator(); iter.hasNext();) {
                FusionRegistryEntry entry = entries.get(iter.next());
                entry.setNameUsingKey(names);
                entry.setRecordPathUsingKey(recordPathsByNumber, recordPathsByName);
                entry.setAnalogFileExtensionNumber(analogFileExtensionNumber);
                System.out.println(entry);
                tunerList.add(new TunerFusion(entry, false));
            }
        } catch (Exception e) {
            System.out.println(new Date() + " ERROR: Problem with countTunersFusion: " + e.getMessage());
            System.err.println(new Date() + " ERROR: Problem with countTunersFusion: " + e.getMessage());
            e.printStackTrace();
        }
        return tunerList;
    }
    
    public static Map<String, Map> getLookupTables() {
        HashMap<String, String> recordPathsByNumber = new HashMap<String, String>();
        HashMap<String, String> recordPathsByName = new HashMap<String, String>();
        HashMap<String, String> names = new HashMap<String, String>();
        
        /**** Get Data from the Data or DeviceN branch(es) if they exist ****/
        try {
            // record path for single device registry
            String[] registryBranchSingle = {"HKEY_CURRENT_USER", "Software\\Dvico\\ZuluHDTV\\Data", ""};
            if (Registry.valueExists(registryBranchSingle[0],registryBranchSingle[1],"DeviceMainUID")){
                String recordPathEntry = Registry.getStringValue(registryBranchSingle[0], registryBranchSingle[1], "RecordPath");
                String deviceMainUidEntry = "" + Registry.getIntValue(registryBranchSingle[0], registryBranchSingle[1], "DeviceMainUID");
                String modelNameEntry = Registry.getStringValue(registryBranchSingle[0], registryBranchSingle[1], "ModelName");
                System.out.println(registryBranchSingle[1] +  "\\RecordPath=" + recordPathEntry);
                System.out.println(registryBranchSingle[1] +  "\\DeviceMainUID=" + deviceMainUidEntry);
                System.out.println(registryBranchSingle[1] +  "\\ModelName=" + modelNameEntry);
                recordPathsByNumber.put (deviceMainUidEntry , recordPathEntry);
                recordPathsByName.put (modelNameEntry, recordPathEntry);
            }
            
            // record paths and names for multiple device registry
            for (int i = 1; i < 5; i++){
                String[] registryBranch = {"HKEY_CURRENT_USER", "Software\\Dvico\\ZuluHDTV\\Data\\Device" + i,""};
                if (Registry.valueExists(registryBranch[0],registryBranch[1],"UINumber")){
                    String recordPathEntry = Registry.getStringValue(registryBranch[0], registryBranch[1], "RecordPath");
                    String modelNameEntry = Registry.getStringValue(registryBranch[0], registryBranch[1], "ModelName");
                    String uiNumberEntry = "" + Registry.getIntValue(registryBranch[0],registryBranch[1],"UINumber");
                    System.out.println(registryBranch[1] +  "\\RecordPath=" + recordPathEntry);
                    System.out.println(registryBranch[1] +  "\\UINumber=" + uiNumberEntry);
                    System.out.println(registryBranch[1] +  "\\ModelName=" + modelNameEntry);
                    names.put (uiNumberEntry, modelNameEntry);
                    recordPathsByNumber.put (uiNumberEntry, recordPathEntry);
                    recordPathsByName.put (modelNameEntry, recordPathEntry);
                } else {
                    break;
                }
            }
        } catch (UnsupportedEncodingException e1) {
            System.out.println(new Date() + " ERROR: Failed to get data from DeviceN branch:" + e1.getMessage());
            System.err.println(new Date() + " ERROR: Failed to get data from DeviceN branch:" + e1.getMessage());
            e1.printStackTrace();
        }
        // Just for debugging
        for (Iterator<String> iterator = names.keySet().iterator(); iterator.hasNext();) {
            String uinumber = iterator.next();
            String name = names.get(uinumber);
            String recordPath = recordPathsByNumber.get(uinumber); 
            System.out.println(new Date() + " Names after DeviceN Branch(es): " + name + "." + uinumber + "  RecordPath:" + recordPath);
        }

        /**** Get Possible Names from Fusion Table ****/
        Connection connection = null;
        Statement statement = null;
        ResultSet rs = null;
        String mdbFileName = "Epg2List.Mdb";
        String localPathFile = fusionInstalledLocation + "\\" + mdbFileName;
        String tableName = "DeviceList";
        if (!(new File(localPathFile).exists()))System.out.println(new Date() + " WARNING: Fusion database file " + localPathFile + " does not exist.  Fusion tuner naming might be impared.");
        try {
            //connection = DriverManager.getConnection("jdbc:odbc:Driver={M icroSoft Access Driver (*.mdb)};DBQ=" + localPathFile);
            connection = DriverManager.getConnection("jdbc:ucanaccess://" + localPathFile);
            statement = connection.createStatement();
            String sql = "select * from " + tableName;
            System.out.println(new Date() + " " + sql);
            rs = statement.executeQuery(sql);
            while (rs.next()){
                int devId = rs.getInt("devId");
                String devNm = rs.getString("devNm");
                int parenLoc = devNm.indexOf(")");
                if (parenLoc > -1 && parenLoc < devNm.length())
                    devNm = devNm.substring(parenLoc + 1);
                names.put("" + devId, devNm);
            }
            statement.close();
            connection.close();
        } catch (SQLException e) {
            System.out.println(new Date() + " ERROR: TunerManager.getLookupTables:" + e.getMessage());
            //System.err.println(new Date() + " ERROR: TunerManager.countTunersFusion:" + e.getMessage());
            //e.printStackTrace();
        } finally {
            try { if (rs != null) rs.close(); } catch (Throwable t){}; 
            try { if (statement != null) statement.close(); } catch (Throwable t){}; 
            try { if (connection != null) connection.close(); } catch (Throwable t){}; 
        }

        // Just for debugging
        for (Iterator<String> iterator = names.keySet().iterator(); iterator.hasNext();) {
            String uinumber = iterator.next();
            String name = names.get(uinumber);
            System.out.println(new Date() + " Names after Epg2List: " + name + "." + uinumber);
        }
        
        /****** Save the data and return *********/
        HashMap<String, Map> lookupTables = new HashMap<String, Map>();
        lookupTables.put("recordPathsByNumber", recordPathsByNumber);
        lookupTables.put("recordPathsByName", recordPathsByName);
        lookupTables.put("names", names);
        return lookupTables;
    }
    
    private List<Tuner> countTunersMyhd(boolean addDevice){
        ArrayList<Tuner> tunerList = new ArrayList<Tuner>();
        // if registry entry exists, presume the tuner is still there
        String recordPath = null;
        try {
            recordPath = Registry.getStringValue("HKEY_LOCAL_MACHINE", "SOFTWARE\\MyHD", "HD_DIR_NAME_FOR_RESCAP");
            int i = 0;
            while (recordPath == null && i < 12){
                try {Thread.sleep(250);} catch (Exception e){};
                recordPath = Registry.getStringValue("HKEY_LOCAL_MACHINE", "SOFTWARE\\MyHD", "HD_DIR_NAME_FOR_RESCAP");
                i++;
            }
            if (recordPath != null){
                Tuner tuner = new TunerMyhd(recordPath, addDevice); // automatically added to TunerManager list
                tuner.liveDevice = true;
                tunerList.add(tuner);
            } else {
                System.out.println(new Date() + " No MyHD registry data found.");
            }
        } catch (Exception e){
            System.out.println(new Date() + " No MyHD registry data found." + e.getMessage());
        }
        return tunerList;
    }
    
    // DRS 20120315 - Altered method - if there are valid captures, then wait, try again, and set liveDevice.
    public List<Tuner> countTunersHdhr(boolean addDevice){
        ArrayList<Tuner> tunerList = new ArrayList<Tuner>(); // To be returned from this method.  Live (including retry to get live), and not disabled.

        TreeSet<String> devices = new TreeSet<String>(); // Working list.  May include non-live to start with, added from discover.txt.

        // Get file devices (from last discover.txt file)
        String fileDiscoverText = getFileDiscoverText();
        ArrayList<String> fileDevices = findTunerDevicesFromText(fileDiscoverText, false);
        System.out.println(new Date() + " Got " + fileDevices.size() + " items from discover.txt");
        devices.addAll(fileDevices);

        // Get live devices
        String liveDiscoverText = getLiveDiscoverText();
        ArrayList<String> liveDevices = findTunerDevicesFromText(liveDiscoverText, true); // devices.txt is always written out here (we read the old one already).
        System.out.println(new Date() + " Got " + liveDevices.size() + " items from active discover command.");
        devices.addAll(liveDevices);
        System.out.println(new Date() + " Total of  " + devices.size() + " items, accounting for duplication.");
        
        
        
        // Only if we picked-up a different device from the discover.txt file do we go into this logic that eliminates devices that do not come alive on retry
        if (liveDevices.size() < devices.size() ) {
            devices = eliminateDevicesThatDoNotComeAliveOnRetry(devices, liveDevices, fileDevices);
            // Now "devices" contains all live devices, even ones that had to be retried to get them going.
            // devices.txt is written out with whatever the result was after retrying
        }
        
        // Loop final list of devices
        for (String device : devices) {
            //DRS 20130126 - Added 1 - getTuner count for device
            int tunerCount = getTunerCountFromRegistry(device);
            for (int j = 0; j < tunerCount; j++){
                Tuner aTuner = new TunerHdhr(device, j, addDevice);  // may be automatically added to this.tuners map
                if (!aTuner.isDisabled()){
                    tunerList.add(aTuner);
                    aTuner.liveDevice = true;
                } else {
                    System.out.println(new Date() + " device " + device + "-" + j + " is disabled, so not being added.");
                }
            }
        }
        return tunerList;
    }
    
    //DRS 20130126 - Added method
    private int getTunerCountFromRegistry(String device) {
        int tunerCount = 2; // minimum number is 2, even if we can't get this data out of the registry
        int progress = -1;
        try {
            for (int i = 0; i < 6; i++){
                progress = i;
                String location = "SOFTWARE\\Silicondust\\HDHomeRun\\Tuners\\" + device + "-" + i;
                boolean itemExists = Registry.valueExists("HKEY_LOCAL_MACHINE", location, "Source");
                if (itemExists){
                    int proposedCount = i + 1;
                    if (proposedCount > tunerCount) tunerCount = proposedCount;
                }
            }
        } catch (Exception e) {
            if (progress < 2) System.out.println(new Date() + " ERROR: Could not count tuners for HDHomeRun for device " + device + ". Returning " + tunerCount + ". " + e.getMessage());
        }
        return tunerCount;
    }

    // DRS 20120315 - Added method - if there are valid captures, then wait, try again, and set liveDevice.
    private TreeSet<String> eliminateDevicesThatDoNotComeAliveOnRetry(TreeSet<String> devices, ArrayList<String> liveDevices, ArrayList<String> fileDevices) {
        boolean writeOutputToFile = true; // this is the best discover.txt file we'll have, go ahead and write it.
        HashSet<String> deadDevices = new HashSet<String>();
        for (String device : devices) {
            boolean reallyDeadDevice = true;
            if (liveDevices.contains(device)){
                // Fine.  Device is live and we will add it later
                System.out.println(new Date() + " " + device + " was live.");
            } else if (fileDevices.contains(device)){
                System.out.println(new Date() + " " + device + " was NOT live.");
                // Device not live, but is contained in the discover.txt.  Might need time to start-up.
                //DRS 20130126 - moved code out into new method
                if (deviceHasValidCapture(device)){
                    for (int retries = 0; retries < 3; retries++){
                        System.out.println(new Date() + " device " + device + " was not live, but had valid current or future captures.  Waiting 15 seconds to try again.");
                        try {Thread.sleep(15000);} catch (Exception e){};
                        String localLiveDiscoverText = getLiveDiscoverText();
                        ArrayList<String> localLiveDevices = findTunerDevicesFromText(localLiveDiscoverText, writeOutputToFile);
                        if (localLiveDevices.contains(device)) {
                            reallyDeadDevice = false; // device now active, on a retry
                            System.out.println(new Date() + " device " + device + " came alive.");
                            break; // out of for loop
                        }
                    }
                } else {
                    System.out.println(new Date() + " device " + device + " was not live, and did not have current or future captures.  Not retrying discover.");
                }
                if (reallyDeadDevice){
                    System.out.println(new Date() + " device " + device + " did not respond, or did not need to get retries.");
                    deadDevices.add(device);
                }
            }
        }
        devices.removeAll(deadDevices);
        return devices;
    }

    // DRS 20120315 - Added method - if there are valid captures, then wait, try again, and set liveDevice.
    private String getFileDiscoverText() {
        String fileDiscoverText = "";
        try {
            System.out.println("looking for " + CaptureManager.dataPath + "discover.txt");
            BufferedReader in = new BufferedReader(new FileReader(CaptureManager.dataPath + "discover.txt"));
            String l = null;
            while ((l = in.readLine()) != null){
                fileDiscoverText += l + "\n";
            }
        } catch (Exception e) {
            System.out.println("discover.txt file not found. " + e.getMessage());
        }
        return fileDiscoverText;
    }

    // DRS 20120315 - Added method - if there are valid captures, then wait, try again, and set liveDevice.
    private String getLiveDiscoverText() {
        String[] commands = {"discover"};
        HdhrCommandLine cl = new HdhrCommandLine(commands, 30, false);
        boolean ok = false;
        try {ok = cl.runProcess();} catch (RuntimeException e) {ok = false;}
        if (ok && cl.getOutput().indexOf("no devices") > -1){
            ok = false;
        }
        if (ok) return cl.getOutput();
        else return "";
    }

    //DRS 20130126 - Added method
    private boolean deviceHasValidCapture(String device) {
        boolean localAddDevice = false;
        int tunerCount = getTunerCountFromRegistry(device);
        for (int i = 0; i < tunerCount; i++){
            if (hasValidCapture(new TunerHdhr(device, 0, localAddDevice).getCapturesFromFile())) return true;
        }
        return false;
    }

    private boolean hasValidCapture(ArrayList<CaptureHdhr> capturesFromFile) {
        boolean hasCurrentOrFutureCapture = false;
        for (Iterator iter = capturesFromFile.iterator(); iter.hasNext();) {
            CaptureHdhr aCapture = (CaptureHdhr) iter.next();
            if (aCapture.getSlot().isInThePast()) continue;
            hasCurrentOrFutureCapture = true;
            break;
        }
        return hasCurrentOrFutureCapture;
    }

    public List<Tuner> countTunersExternal(boolean addDevice){
        ArrayList<Tuner> tunerList = new ArrayList<Tuner>();
        System.out.println(new Date() + " Looking for ExternalTuners.properties");
        Properties externalProps = new Properties();
        try {
            // Get external tuners properties.  If not found, ignore
            externalProps.load(new FileInputStream(CaptureManager.dataPath + "ExternalTuners.properties"));
            
            // Find out prefixes in the file (01, 02, etc)
            Set keys = externalProps.keySet();
            TreeSet<String> prefixes = new TreeSet<String>();
            for (String s : (Set<String>)keys) {
                if (s != null && s.length()> 2)
                prefixes.add(s.substring(0,2));
            }
            
            // Once for each tuner in the properties file, based on the 01, 02, etc prefix
            for (String prefix : prefixes) {
                try {
                    Integer.parseInt(prefix);
                    TunerExternal tuner = new TunerExternal(externalProps, prefix, addDevice);
                    tunerList.add(tuner);
                    tuner.liveDevice = true;
                } catch (Throwable t){
                    System.out.println(new Date() + " ERROR: Did not understand prefix '" + prefix + "' in your ExternalTuners.properties file");
                }
            }
        } catch (Exception e) {
            System.out.println(new Date() + " INFO: ExternalTuners.properties not found.");
        }
        return tunerList;
    }
	
	public String loadChannelsFromFile() {
        String result = scanRefreshLineup();
        if (result.indexOf("ERROR") > -1) System.out.println(new Date() + " ERROR: LoadChannelsFromFile: " + result);
        return result;
	}
    
    public String scanRefreshLineup(){
        return scanRefreshLineUp(true, null, null, 9999);
    }

    public String scanRefreshLineUp(boolean useExistingFile, String tunerName, String signalType, int maxSeconds) {
        String returnValue = "";
        if (noTuners()) returnValue = "No Tuners. Use discover.";
        System.out.println("scanRefreshLineUp for " + tuners.size() + " tuners.");
        for (Iterator<Tuner> iter = this.iterator(); iter.hasNext();) {
            Tuner tuner = iter.next();
            if (tunerName == null || tuner.getFullName().equalsIgnoreCase(tunerName)){
                if (!tuner.liveDevice){
                    returnValue += "Tuner " + tuner.getFullName() + " is not active.  Not scanning.";
                    continue;
                }
                try {
                    System.out.println("running scan for tuner " + tuner.getFullName());
                    tuner.scanRefreshLineUp(useExistingFile, signalType, maxSeconds);
                    returnValue += "OK,";
                } catch (Throwable e) {
                    returnValue += "ERROR " + e.getMessage() + ",";
                }
            } else {
                System.out.println("not scanning " + tuner.getFullName());
            }
        }
        return returnValue;
    }

    // DRS 20110618 - Added method externalTuner
    public String[] getExternalTunerValues(String fakeTunerName, String fakeChannelName){
        String[] externalTunerValues = null;
        if (this.externalProps == null || !this.externalProps.containsKey("failed")){
            if (this.externalProps == null) this.externalProps = new Properties();
            try {
                if (!this.externalProps.containsKey("loaded")){
                    this.externalProps.load(new FileInputStream(CaptureManager.dataPath + "ExternalTuners.properties"));
                    //   TunerExternal tuner = TunerExternal.getInstance(externalProps);
                    //   this.tuners.put(tuner.getFullName(), tuner);
                    //   this.countTunersExternal(true);
                    externalProps.setProperty("loaded", "");
                }
                Set keys = externalProps.keySet();
                for (String s : (Set<String>)keys) {
                    if (s.indexOf(fakeTunerName + "-" + fakeChannelName) > -1){
                        externalTunerValues = new String[2];
                        externalTunerValues[0] = externalProps.getProperty(s) + ".0";
                        externalTunerValues[1] = externalProps.getProperty(s.substring(0,3) + "tunerName");
                    }
                }
            } catch (Exception e) {
                this.externalProps = new Properties();
                this.externalProps.setProperty("failed", "");
                System.out.println(new Date() + " Failed to load properties from ExternalTuner.properties. " + e.getMessage());
            }
        }
        return externalTunerValues;
    }
    
	public Iterator<Tuner> iterator(){
		return tuners.values().iterator();
	}

    public boolean noTuners() {
        return tuners.size() == 0;
    }
    
    public Tuner getTuner(int tunerNumber) {
        if (noTuners()) return null;
        if (tunerNumber < tuners.size()){
            return tuners.get(tuners.keySet().toArray()[tunerNumber]);
        } else {
            return null;
        }
            
    }

    public Tuner getTuner(String tunerFullName) {
        Tuner aTuner = null;
        if (tunerFullName != null && tunerFullName.indexOf("FFFFFFFF") > -1){
            return getRealHdhrTuner(tunerFullName);
        } else {
            for (Iterator<Tuner> iter = this.iterator(); iter.hasNext();) {
                aTuner = iter.next();
                //System.out.println("comparing " + aTuner.getFullName() + " with " + tunerName);
                if (aTuner.getFullName().equalsIgnoreCase(tunerFullName)) {
                    break;
                }
                aTuner = null;
            }
        }
        return aTuner;
    }
    
    //DRS 20100720 - Added method of Allen
    public Tuner getRealHdhrTuner(String fakeTuner){
        if (fakeTuner == null || fakeTuner.length() < 3) return null;
        String lastTwoChars = fakeTuner.substring(fakeTuner.length() - 2);
        Tuner aTuner = null;
        Tuner selectedTuner = null;
        for (Iterator<Tuner> iter = this.iterator(); iter.hasNext();) {
            aTuner = iter.next();
            if (aTuner.getType() == Tuner.HDHR_TYPE){
                selectedTuner = aTuner;
                if (selectedTuner.getFullName().endsWith(lastTwoChars)) return selectedTuner;
            }
        }
        return selectedTuner;
    }
    
    private ArrayList<String> findTunerDevicesFromText(String output, boolean writeToFile) {
    	ArrayList<String> result = new ArrayList<String>();
    	try {
			BufferedReader in = new BufferedReader(new StringReader(output));
			StringBuffer buf = new StringBuffer();
			String l = null;
			while ((l = in.readLine()) != null){
				buf.append(l + "\n");
			    StringTokenizer tok = new StringTokenizer(l);
                while(tok.hasMoreTokens()){
                    String t = tok.nextToken();
                    if (t.equals("device") && tok.hasMoreTokens()){
                        String deviceId = tok.nextToken();
                        try {
                            Integer.parseInt(deviceId, 16);
                            result.add(deviceId);
                        } catch (Throwable e){
                            // System.out.println(deviceId + " followed the word device, but wasn't hex string");
                        }
                    }
                }
			}
			in.close();
			// if we get valid discover output, write the file
			if (result.size() > 0 && writeToFile){
				BufferedWriter out = new BufferedWriter(new FileWriter(CaptureManager.dataPath + "discover.txt"));
				out.write(new String(buf));
				out.flush();
				out.close();
			}
		} catch (IOException e) {}
		return result;
	}

    public void addTuner(Tuner tuner){
        if (!tuners.containsKey(tuner.getFullName())){
            System.out.println(new Date() + " Tuner added :" + tuner.getFullName());
            tuners.put(tuner.getFullName(), tuner);
        } else {
            System.out.println(new Date() + " Tuner replaced :" + tuner.getFullName());
            tuners.put(tuner.getFullName(), tuner);
        }
    }

    /* This Method Only Used in Testing */
    private void removeAllTuners() {
        String[] tunerNames = tuners.keySet().toArray(new String[0]);
        for (int i = 0; i < tunerNames.length; i++) {
            removeTuner(tunerNames[i]);
        }
    }
    
    /* This Method Only Used in Testing */
    private boolean removeTuner(String tunerName){
        if (tuners.containsKey(tunerName)){
            Tuner tuner = tuners.get(tunerName);
            boolean localRemovalOnly = true;
            tuner.removeAllCaptures(localRemovalOnly);
            tuners.remove(tunerName);
            System.out.println(new Date() + " Tuner removed :" + tunerName);
        }
        return false;
    }

    public Capture getCaptureForChannelNameSlotAndTuner(String channelName, Slot slot, String tuner, String protocol) {
        //System.out.println("getCaputureForChannelNameSlotAndTuner(" + channelName + ", " + slot + ", " + tuner + ", " + protocol);
        List<Capture> captures = getAvailableCapturesForChannelNameAndSlot(channelName, slot, protocol);
        if (captures == null || captures.size() == 0) {
            lastReason += "<br>getAvailableCapturesForChannelNameAndSlot returned no captures for " + channelName + " " + tuner + " " + slot;
            //System.out.println("LR:" + lastReason);
            return null;
        }

        Capture capture = null;
        if (tuner == null || tuner.equals("")){
            capture = captures.get(0);
        } else {
            for (Iterator<Capture> iter = captures.iterator(); iter.hasNext();) {
                Capture cap = iter.next();
                if (tuner.equalsIgnoreCase(cap.channel.tuner.getFullName())){
                    capture = cap;
                }
            }
            if (capture == null){
                lastReason = "getAvailableCapturesForChannelNameAndSlot returned no captures matching tuner " + tuner;
                //System.out.println("LR2:" + lastReason);
            }
        }
        return capture;
    }
    
    // DRS 20110214 - Added method
    public ArrayList<Capture> getCapturesForAllChannels(String channelName, Slot slot, String tunerString, String protocol) {
        ArrayList<Capture> captureList = new ArrayList<Capture>();
        try {
            Map<String,Channel> channelMap = this.getTuner(tunerString).lineUp.channels;
            Set<String> keySet = channelMap.keySet();
            ArrayList<Slot> slotList = slot.split(channelMap.size());
            int i = 0;
            for (Iterator<String> iter = keySet.iterator(); iter.hasNext(); i++) {
                Object key = iter.next();
                Channel aChannel = channelMap.get(key);
                String aChannelName = aChannel.getCleanedChannelName();
                Capture aCapture = getCaptureForChannelNameSlotAndTuner(aChannelName, slotList.get(i), tunerString, protocol);
                if (aCapture != null){
                    captureList.add(aCapture);
                } else {
                    System.out.println(new Date() + " one capture of a multi-capture was null!");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            lastReason = "Failed to get captures for all channels. " + e.getMessage();
            System.out.println(new Date() + " " + lastReason);
        }
        return captureList;
    }
    
    //  DRS 20110215 - Added method
    public String sortChannelMap() {
        TreeMap<String, CaptureDetails> channelTunerMap = CaptureDataManager.getInstance().getSignalStrengthByChannelAndTuner();
        if (channelTunerMap.size() == 0) return "No HDHR signal strength history found";
        
        // Get channel sort from cw_epg file
        TreeMap<Integer, CwEpgChannelRow> channelSortMap = new TreeMap<Integer,CwEpgChannelRow>();
        List<String>headers = null;
        try {
            CSVReader reader = new CSVReader(new BufferedReader(new FileReader(CaptureManager.dataPath + File.separator + "channel_maps.txt"))); 
            headers = reader.readHeader();
            int i = 0;
            Map<String, String> map = null;
            while ((map = reader.readValues()) != null) {
                channelSortMap.put(new Integer(i++), new CwEpgChannelRow(map));
            }
        } catch (Exception e){
            return "Failed to sort. Failed to read sorted channels.  " + e.getMessage();
        }
        if (channelSortMap.size() == 0) return "No CW_EPG channel map was found";

        // Make the sorter object and use it
        ChannelMapSorter channelMapSorter = new ChannelMapSorter(channelTunerMap, channelSortMap, headers);
        TreeMap<Integer, CwEpgChannelRow> channelsSorted = channelMapSorter.getSorted();
        if (channelSortMap.size() != channelsSorted.size()) return "Error in sorting.  No changes made.";
        
        // Write out the sorted map
        CSVWriter writer = null;
        try {
            writer = new CSVWriter(new BufferedWriter(new FileWriter(CaptureManager.dataPath + File.separator + "channel_maps.txt")));
            writer.setColumns(headers);
            writer.writeHeader();
            for (Iterator iter = channelsSorted.keySet().iterator(); iter.hasNext();) {
                CwEpgChannelRow aRow = channelsSorted.get(iter.next());
                writer.write(aRow.getMap());
            }
            writer.close();
        } catch (Exception e){
            String message = new Date() + " ERROR.  Failed in critial operation.  Check channel_maps.txt.  Don't ignore this message! " + e.getMessage();
            try {if (writer != null) writer.close();} catch (Exception ee){}
            System.out.println(message);
            System.err.println(message);
            e.printStackTrace();
            return  message;
        }
        
        return channelMapSorter.getMoveCount() + " channels changed location. " + channelMapSorter.getSortingLog();
    }


    public CaptureHdhr getCaptureForNewChannelNameSlotAndTuner(String channelName, Slot slot, String tunerName, String protocol) throws Exception {
        // find or add tuner
        Tuner tuner = null;
        for (Iterator<String> iter = this.tuners.keySet().iterator(); iter.hasNext();) {
            Object key = iter.next();
            if(tuners.get(key).getFullName().equalsIgnoreCase(tunerName)){
                tuner = tuners.get(key);
            }
        }
        if (tuner == null){
            tuner = new TunerHdhr(tunerName, true);
        }
        // make a new channel (we wouldn't be here if it existed) and add it to the lineup
        Channel channelDigital = new ChannelDigital(channelName, tuner, 1000.0, protocol);
        tuner.lineUp.addChannel(channelDigital);
        return (CaptureHdhr)getCaptureForChannelNameSlotAndTuner(channelName, slot, tunerName, protocol);
    }

    public Capture getCaptureForMyHD(String channelName, Slot slot, String tuner, String channelVirtual, String rfChannel, String protocol) {
        if (slot.isInThePast()){
            lastReason = new Date() + " ERROR: Slot is in the past. " + slot;
            return null;
        }
        CaptureMyhd capture = new CaptureMyhd(channelName, slot, tuner, channelVirtual, rfChannel, protocol);
        if (capture != null && capture.isValid()){
            return capture;
        } else {
            System.out.println(new Date() + " ERROR: getCaptureForMyHD " + capture.getLastError());
            return null;
        }
    }

    // For Fusion and HDHR (so input is always 1)
    public List<Capture> getAvailableCapturesForChannelNameAndSlot(String channelName, Slot slot, String protocol) {
        if (slot.isInThePast()){
            lastReason = new Date() + " slot in the past. " + slot;
            return new ArrayList<Capture>();
        }
        
        TreeMap<Double, Capture> prioritizedCaptures = new TreeMap<Double, Capture>();
        for (Iterator<Tuner> iter = this.iterator(); iter.hasNext();) {
            Tuner tuner = iter.next();
            Channel aChannel = tuner.lineUp.getChannel(channelName, 1, protocol);
            if (aChannel == null){
                this.lastReason += " channel " + channelName + " " + protocol + " not found on " + tuner.getFullName() + " lineup.<br>";
                continue;
            }
            Capture trialCapture = null;
            //System.out.println("tuner type:" + tuner.getType() + " (should be " + Tuner.HDHR_TYPE + " or " + Tuner.FUSION_TYPE + ")");
            if (tuner.getType() == Tuner.HDHR_TYPE){
                trialCapture = new CaptureHdhr(slot, aChannel);
            } else if (tuner.getType() == Tuner.FUSION_TYPE){
                trialCapture = new CaptureFusion(slot, aChannel, false);
            } else if (tuner.getType() == Tuner.EXTERNAL_TYPE){
                trialCapture = new CaptureExternalTuner(slot, aChannel);
            }
            Double priority = new Double(aChannel.priority + Math.random()); // prevent identical priorities from getting lost 
            if (trialCapture != null && tuner.available(trialCapture, true)){
                prioritizedCaptures.put(priority, trialCapture);
            } else {
                this.lastReason += "<br>tuner " + tuner.getFullName() + " not available for " + trialCapture;
            }
        }
        ArrayList<Capture> list = new ArrayList<Capture>();
        for (Iterator<Double> iter = prioritizedCaptures.keySet().iterator(); iter.hasNext();) {
            list.add(prioritizedCaptures.get(iter.next()));
        }
        return list;
    }
    
    public List<Capture> getCaptures(){
        ArrayList<Capture> captures = new ArrayList<Capture>();
        if (this.noTuners()){
            System.out.println("TunerManager.getCaptures() counting tuners.");
            this.countTuners();
        }
        for (Iterator<Tuner> iter = this.iterator(); iter.hasNext();) {
            Tuner tuner = iter.next();
            captures.addAll(tuner.getCaptures());
        }
        return captures;
    }

    /* This method called from web UI with localRemoveOnly = false */
    public void removeAllCaptures(boolean localRemovalOnly) {
        for (Iterator<Tuner> iter = this.iterator(); iter.hasNext();) {
            Tuner tuner = iter.next();
            tuner.removeAllCaptures(localRemovalOnly);
        }
    }
    
    public void updateFileName(String sequence, String newFileName) throws Exception  {
        // Save info about this capture we're updating
        Capture foundCapture = this.getCaptureForSequence(sequence);
        Tuner foundTuner = this.getTunerForCapture(foundCapture);
        
        // Remove the capture from the tuner
        this.removeCapture(sequence);

        // Modify the file name in the found capture
        foundCapture.setFileName(newFileName);
        
        // Add the capture back to the tuner
        foundTuner.addCaptureAndPersist(foundCapture, true);
    }

    public Capture removeCapture(String sequence) throws Exception {
        Capture foundCapture = getCaptureForSequence(sequence);
        Tuner foundTuner = getTunerForCapture(foundCapture);
        foundTuner.removeCapture(foundCapture);
        return foundCapture;
    }
    
    // called only by removeActiveCapture (at end recording end time and when web ui requests end)
    public void removeCapture(Capture capture) {
        Tuner aTuner = getTunerForCapture(capture);
        if (aTuner == null){
            System.err.println(new Date() + " Could not find tuner for capture:\n" + capture);
            System.err.println(this.getWebCapturesList());
        } else {
            aTuner.removeCapture(capture);
        }
    }

    private Capture getCaptureForSequence(String sequence){
        Capture foundCapture = null;
        System.out.println(new Date() + " Trying to find capture sequence number " + sequence);
        int seq = -1;
        try {
            seq = Integer.parseInt(sequence);
        } catch (Exception e){
            System.out.println(new Date() + "ERROR: Failed to parse sequence number " + e.getMessage());
        }
        int i = 0;
        boolean found = false;
        for (Iterator<Tuner> iter = this.iterator(); iter.hasNext();) {
            Tuner tuner = iter.next();
            List<Capture> captures = tuner.captures;
            for (int j = 0; j < captures.size(); j++, i++){
                if (seq == i){
                    foundCapture = captures.get(j);
                    found = true;
                    break;
                }
            }
            if (found) break;
        }
        if (!found) System.out.println(new Date() + "ERROR: Failed to find capture by sequence number.");
        return foundCapture;
    }

    public Tuner getTunerForCapture(Capture foundCapture){
        Tuner foundTuner = null;
        for (Iterator<Tuner> iter = this.iterator(); iter.hasNext();) {
            Tuner tuner = iter.next();
            if (tuner.captures.contains(foundCapture)){
                foundTuner = tuner;
                break;
            }
            if (foundTuner != null) break;
        }
        if (foundTuner == null) System.out.println(new Date() + " ERROR: Did not find tuner for capture.");
        return foundTuner;
    }

    public Collection<Channel> getAllChannels(boolean usePriority){
        return getAllChannels(usePriority, false);
    }

    
	public Collection<Channel> getAllChannels(boolean usePriority, boolean compressFusion){
		TreeMap<Number, Channel> allChannels = new TreeMap<Number, Channel>();
        int counter = 0;
        boolean fusionDone = false;
		for (Iterator<Tuner> iter = this.iterator(); iter.hasNext();) {
			Tuner tuner = iter.next();
            if (compressFusion && (tuner.getType() == Tuner.FUSION_TYPE)){
                if (fusionDone) continue;
                fusionDone = true;
            }
			Map<?, ?> channels = tuner.lineUp.channels;
			for (Iterator<?> iterator = channels.keySet().iterator(); iterator.hasNext();) {
				String channelName = (String) iterator.next();
				Channel channel = (Channel)channels.get(channelName);
                if (usePriority){
                    allChannels.put(new Double(channel.priority + Math.random()), channel);
                } else {
                    allChannels.put(new Integer(counter), channel);
                }
                counter++;
			}
		}
		return allChannels.values();
	}
	
	public void adjustPriorities(String prioritiesFileName) {
		ArrayList<String> priorityList = new ArrayList<String>();
		try {
			BufferedReader in = new BufferedReader(new FileReader(prioritiesFileName));
			String l = null;
			while ((l = in.readLine()) != null){
				if (l.indexOf(":") > 1 && !l.endsWith(":")){
					priorityList.add(l.trim());
				}
			}
			in.close();
		} catch (Exception e){
			System.out.println("ChannelDigital priorities not considered.");
		}
		for (Iterator<Tuner> iter = this.iterator(); iter.hasNext();) {
			Tuner tuner = iter.next();
			Map<?, ?> channels = tuner.lineUp.channels;
			for (Iterator<?> iterator = channels.keySet().iterator(); iterator.hasNext();) {
				String channelName = (String) iterator.next();
				Channel channelDigital = (Channel)channels.get(channelName);
				int priority = priorityList.indexOf(tuner.id + "-" + tuner.number + ":" + channelName);
				if (priority > -1){
					channelDigital.priority = priority + (tuner.number/10);
				}
			}
		}
	}
	
	public void printAllChannels() {
		Collection<Channel> channels = this.getAllChannels(false);
		for (Iterator<Channel> iter = channels.iterator(); iter.hasNext();) {
			Channel channelDigital = iter.next();
			System.out.println(channelDigital);
		}
	}
	
	public void createDefaultPriorityChannelListFile() {
		Collection<Channel> channel = this.getAllChannels(true);
		if (channel.size() > 0){
			try {
				BufferedWriter out = new BufferedWriter(new FileWriter(CaptureManager.dataPath + "DefaultChannelPriority.txt"));
				for (Iterator<Channel> iter = channel.iterator(); iter.hasNext();) {
					Channel channelDigital = iter.next();
					out.write(channelDigital.tuner.id + "-" + channelDigital.tuner.number + ":" + channelDigital.channelKey + "\n");
				}
				out.close();
			} catch (Exception e) {
				System.out.println("Could not save default channel file " + e.getMessage());
			}
		} else {
			System.out.println("No channels to put in the default channel file.");
		}
	}

	public void printLineUps(){
		for (Iterator<Tuner> iter = this.iterator(); iter.hasNext();) {
			Tuner tuner = iter.next();
			System.out.println(tuner + "\n" + tuner.lineUp);
		}
	}
    
    public void setTunerPath(String tunerName, String path) {
        this.lastReason = "";
        Tuner aTuner = this.tuners.get(tunerName);
        if (aTuner == null) this.lastReason += "  Specified tuner not found.";
        else aTuner.setRecordPath(path);
        return;
    }

    public String getSimpleTunerList(){
        StringBuffer buf = new StringBuffer();
        for (Iterator<Tuner> iter = this.iterator(); iter.hasNext();){
            Tuner tuner = iter.next();
            buf.append(tuner.getFullName() + "<br>");
        }
        return new String(buf);
    }
    
    public String getWebTunerList() {
        return getWebTunerList(0);
    }

    public String getWebTunerList(int lowGbValue) {
        StringBuffer buf = new StringBuffer("<table border=\"1\">\n");
        StringBuffer xmlBuf = new StringBuffer("\n<xml id=\"tuners\">\n");
        for (Iterator<Tuner> iter = this.iterator(); iter.hasNext();){
            Tuner tuner = iter.next();
            // TODO: ******************* REMOVE AFTER TIM FIXES HIS SIDE ******************
            // TODO: ******************* REMOVE AFTER TIM FIXES HIS SIDE ******************
            // TODO: ******************* REMOVE AFTER TIM FIXES HIS SIDE ******************
            if (tuner.getType() == Tuner.EXTERNAL_TYPE) continue;
            // TODO: ******************* REMOVE AFTER TIM FIXES HIS SIDE ******************
            // TODO: ******************* REMOVE AFTER TIM FIXES HIS SIDE ******************
            // TODO: ******************* REMOVE AFTER TIM FIXES HIS SIDE ******************
            buf.append("<tr><td>" 
                    + tuner.getFullName() + "</td><td>" 
                    + tuner.getDeviceId() + "</td><td>" 
                    + tuner.getType() + "</td><td>" 
                    + tuner.getRecordPath() + "</td><td>"
                    + tuner.getLiveDevice() + "</td><td>" 
                    + tuner.getAnalogFileExtension() + "</td><td>"
                    + tuner.getLowSpaceComment(lowGbValue) + "</td></tr>\n");
            xmlBuf.append("  <tuner name=\"" 
                    + tuner.getFullName() + "\" deviceId=\"" 
                    + tuner.getDeviceId() + "\" tunerType=\"" 
                    + tuner.getType() + "\" recordPath=\"" 
                    + tuner.getRecordPath() + "\" liveDevice=\"" 
                    + tuner.getLiveDevice() + "\" analogFileExtension=\"" 
                    + tuner.getAnalogFileExtension() + "\" spaceAvailableGb=\""
                    + tuner.getRemainingGb() + "\"/>\n" );
        }
        buf.append("</table>\n");
        xmlBuf.append("</xml>\n");
        return new String(buf) + new String(xmlBuf);
    }
    
    public String getWebChannelList(){
        return getWebChannelList(false);
    }

    public String getWebChannelListSingleFusion() {
        boolean returnOnlyOneInstanceOfFusionChannels = true;
        return getWebChannelList(returnOnlyOneInstanceOfFusionChannels);
    }

    
    public String getWebChannelList(boolean compressFusion) {
        StringBuffer buf = new StringBuffer("<table border=\"1\">\n");
        StringBuffer xmlBuf = new StringBuffer("\n<xml id=\"channels\">\n");
        Collection<Channel> allChannels = getAllChannels(false, compressFusion);
        for (Iterator<Channel> iter = allChannels.iterator(); iter.hasNext();) {
            Channel channel = iter.next();
            buf.append("<tr>" + channel.getHtml() + "</tr>\n");
            xmlBuf.append("  <channel " + channel.getXml() + "/>\n");
        }
        buf.append("</table>\n");
        xmlBuf.append("</xml>\n");
        return new String(buf) + new String(xmlBuf);
    }
    


    public String getWebCapturesList(){
        StringBuffer buf = new StringBuffer("<table border=\"1\">\n");
        StringBuffer xmlBuf = new StringBuffer("\n<xml id=\"captures\">\n");
        List<Capture> captures = getCaptures();
        int i = 0;
        for (Iterator<Capture> iter = captures.iterator(); iter.hasNext(); i++) {
            Capture capture = iter.next();
            if (capture != null){
                buf.append(capture.getHtml(i));
                xmlBuf.append(capture.getXml(i));
            }
        }
        buf.append("</table>\n");
        xmlBuf.append("</xml>\n");
        return new String(buf) + new String(xmlBuf);
    }
    
    public boolean foundDiskFullAlertCondition(String lowDiskGb) {
        int lowDiskGbValue = -1;
        try {
            lowDiskGbValue = Integer.parseInt(lowDiskGb);
        } catch (Exception e){
            System.out.println(new Date() + " ERROR: Low disk space GB is non-numeric.");
        }
        boolean foundAlertCondition = false;
        for (Iterator<Tuner> iter = this.iterator(); iter.hasNext();){
            Tuner tuner = iter.next();
            if (tuner.getLowSpaceComment(lowDiskGbValue).length() > 0) foundAlertCondition = true;
        }
        return foundAlertCondition;
    }
    
    public String getLastReason(){
        return lastReason;
    }
    
    public void clearLastReason() {
        lastReason = "";
    }
   
    public String toString(){
        StringBuffer buf = new StringBuffer("Tuners:\n");
        for (Iterator<Tuner> iter = this.iterator(); iter.hasNext();){
            buf.append(iter.next().toString() + "\n");
        }
        return new String(buf);
    }
    
    public static void main(String[] args) {
        TunerManager tm = TunerManager.getInstance();
        tm.countTuners();
    }
}
