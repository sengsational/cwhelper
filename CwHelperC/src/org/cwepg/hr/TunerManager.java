package org.cwepg.hr;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.TreeSet;

import org.cwepg.reg.FusionRegistryEntry;
import org.cwepg.reg.Registry;
import org.cwepg.reg.RegistryHelperFusion;
import org.cwepg.svc.HdhrCommandLine;
import org.cwepg.svc.HtmlVcrDoc;

public class TunerManager {
	
	Map<String, Tuner> tuners = new TreeMap<String, Tuner>();
	static TunerManager tunerManager;
    private String lastReason = "";
	Set<String> capvSet = new TreeSet<String>();
    Properties externalProps;
    private ArrayList<Tuner> nonResponsiveTuners = new ArrayList<Tuner>();
    public static String fusionInstalledLocation;
    private static boolean mCountingTuners = false;
    public static final int VIRTUAL_MATCHING = 0;
    public static final int NAME_MATCHING = 1;
    public static boolean skipFusionInit = false;
    public static boolean skipRegistryForTesting = false;
    
    
	private TunerManager(){
	    if (!skipFusionInit) {
            fusionInstalledLocation = RegistryHelperFusion.getInstalledLocation();
            if  (fusionInstalledLocation == null) fusionInstalledLocation = CaptureManager.cwepgPath;
            if ("".equals(fusionInstalledLocation)) fusionInstalledLocation = new File("test").getAbsoluteFile().getParentFile().getAbsolutePath();
	    }
	}
	
	public static TunerManager getInstance(){
		if (tunerManager == null){
			tunerManager = new TunerManager();
		}
		return tunerManager;
	}
    
	public int countTuners(){
	    mCountingTuners = true;
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
                // DRS 20220606 - Added 7 - Copy over the IP from the refreshed tuner to the existing tuner, then we just remove the tuner from the refreshedTuners list  (since anything left in the list, we will be adding later).
                boolean refreshLineup = true;
                if (existingTuner instanceof TunerHdhr) {
                    TunerHdhr existingHdhrTuner = (TunerHdhr)existingTuner;
                    TunerHdhr matchingTuner = getMatchingTuner(refreshedTuners, existingTuner);
                    existingHdhrTuner.setIpAddress(matchingTuner.ipAddressTuner);
                    refreshLineup = false; // no need to refresh lineup...only the IP address changed.
                }
                refreshedTuners.remove(existingTuner);
                // but the old (existing tuner) might have different lineup, so refresh that
                if (refreshLineup) refreshLineup(existingTuner); // THIS CLEARS ANY EXISTING CHANNELS // DRS 20220606 - Added Conditional
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
        
        
        // DRS 20210415 - Added 'for' loop + 1 - Concurrent Modification Exception
        for (Tuner tuner : nonResponsiveTuners) {
            System.out.println(new Date() + " Removing non-responsive tuner: " + tuner.getFullName());
            this.tuners.remove(tuner.getFullName());
            tuner.removeAllCaptures(true); //before deleting a tuner, delete it's captures
        }
        nonResponsiveTuners.clear();
        
        // any tuners left in the refreshed list need to be added to the tuner manager
        for (Tuner tuner : refreshedTuners) {
            System.out.println(new Date() + " Adding new or changed: " + tuner.getFullName());
            // refreshed tuners are not added to the tuner manager and did not pick-up captures from file, so do both
            this.tuners.put(tuner.getFullName(), tuner);
            tuner.addCapturesFromStore();
            try {refreshLineup(tuner);} catch (Throwable t) {System.out.println(new Date() + " Problem refreshing lineup on new or changed tuner " + t.getMessage());};
        }
        mCountingTuners = false;
        System.out.println(new Date() + " TunerManager.countTuners returned " + this.tuners.size() + " tuners.");
        return this.tuners.size();
	}
	
	// DRS 20220606 - Added method
	private static TunerHdhr getMatchingTuner(ArrayList<Tuner> refreshedTuners, Tuner existingTuner) {
	    for (Tuner tuner : refreshedTuners) {
            if (tuner.equals(existingTuner)) return (TunerHdhr)tuner;
        }
	    System.out.println(new Date() + " ERROR: Did not find matching tuner for " + existingTuner.id);
        return (TunerHdhr)existingTuner;
    }

    // DRS 20190422 - Added method
	// DRS 20210415 - Changed non-responsive tuners to instance variable (delete later) - Concurrent Modification Exception
    public void removeHdhrByUrl(String url) {
        nonResponsiveTuners = new ArrayList<Tuner>();
        for (Entry<String, Tuner> entry : this.tuners.entrySet()) {
            Tuner aTuner = entry.getValue();
            if (aTuner instanceof TunerHdhr) {
                TunerHdhr hdhrTuner = (TunerHdhr)aTuner;
                if(url.contains(hdhrTuner.ipAddressTuner)) {
                    nonResponsiveTuners.add(hdhrTuner);
                }
            }
        }
        // DRS 20210415 - Commented 'for' loop - Concurrent Modification Exception
        //for (Tuner tuner : deletedTuners) {
        //    System.out.println(new Date() + " Removing non-responsive tuner: " + tuner.getFullName());
        //    this.tuners.remove(tuner.getFullName());
        //    tuner.removeAllCaptures(true); //before deleting a tuner, delete it's captures
        //}
        
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
        if (TunerManager.skipFusionInit) return tunerList;
        String controlSetName = "CurrentControlSet";
        if (test) controlSetName = "ControlSet0002";
        Map<String, FusionRegistryEntry> entries = RegistryHelperFusion.getFusionRegistryEntries(controlSetName);
        try {
            int analogFileExtensionNumber = Registry.getIntValue("HKEY_CURRENT_USER", "Software\\Dvico\\ZuluHDTV\\Data", "AnalogRecProfile");
            
            Map<String, Map> lookupTables = TunerManager.getLookupTables();
            if (lookupTables == null) return tunerList;  // DRS 20210114 - Added 1 - If we get an null here, return empty list and stop any more attempts...all hope is lost.
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
            connection = DriverManager.getConnection("jdbc:ucanaccess://" + localPathFile + ";singleConnection=true");
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
            return null; // DRS 20210114 - Added 1 - If we get an error here, return null and stop any more attempts...all hope is lost.
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
            if (TunerManager.skipRegistryForTesting) return tunerList;
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
        String fileDiscoverText = getFileDiscoverText(); // each fileDiscoverText line looks like this "hdhomerun device 1010CC54 found at 192.168.3.209"
        ArrayList<String> fileDevices = findTunerDevicesFromText(fileDiscoverText, false); // returns a List of String like [1076C3A7, 1075D4B1, 1080F19F]  
        devices.addAll(fileDevices);
        System.out.println(new Date() + " Got " + fileDevices.size() + " items from discover.txt [" + getDeviceListAsString(devices) + "]"); //DRS 20220707 - Fix broken logging of devices

        // Get live devices
        ArrayList<String> liveDevices = null;
        String liveDiscoverText = null;
        if (CaptureManager.useHdhrCommandLine) {
            liveDiscoverText = getLiveDiscoverText(CaptureManager.discoverRetries, CaptureManager.discoverDelay);
        } else { //DRS 20220707 - Added getting discover from http if command line not available.
            int maxSeconds = 2;
            boolean quiet = false;
            boolean isPost = false;
            liveDiscoverText = LineUpHdhr.getPage("http://ipv4-api.hdhomerun.com/discover", maxSeconds, quiet, isPost);
            liveDiscoverText = reformatWebDiscover(liveDiscoverText);
        }
        liveDevices = findTunerDevicesFromText(liveDiscoverText, true); // devices.txt is always written out here (we read the old one already).
        System.out.println(new Date() + " Got " + liveDevices.size() + " items from active discover command. [" +  getDeviceListAsString(devices) + "]");
        devices.addAll(liveDevices);
        System.out.println(new Date() + " Total of  " + devices.size() + " items, accounting for duplication. [" +  getDeviceListAsString(devices) + "]");
        
        // Only if we picked-up a different device from the discover.txt file do we go into this logic that eliminates devices that do not come alive on retry
        if (liveDevices.size() < devices.size() ) {
            devices = eliminateDevicesThatDoNotComeAliveOnRetry(devices, liveDevices, fileDevices);
            // Now "devices" contains all live devices, even ones that had to be retried to get them going.
            // devices.txt is written out with whatever the result was after retrying
        }

        // DRS 20181103 - Adding IP address to HDHR tuners
        // DRS 20220605 - Comment 1, Add 1 - always use live discover text for IP addresses.
        //HashMap<String, String> ipAddressMap = getIpMap(fileDiscoverText, liveDiscoverText);
        HashMap<String, String> ipAddressMap = getIpMap(fileDiscoverText, liveDiscoverText);

        // DRS 20220606 - Added 6 - Debug
        //System.out.print(new Date() + " DEBUG: [");
        //Set<String> keys = ipAddressMap.keySet();
        //for (String key : keys) {
        //    System.out.print(key + " " + ipAddressMap.get(key) + ", ");
        //}
        //System.out.println("]");

        // DRS 20181025 - Adding model to HDHR tuners
        HashMap<String, Integer> liveModelMap = null;
        if (CaptureManager.useHdhrCommandLine) {
            liveModelMap = getLiveModelMap(liveDevices, 1, CaptureManager.discoverDelay);
        } else { //DRS 20220707 - Added getting model from http if command line unavailable.
            liveModelMap = getLiveModelMapHttp(ipAddressMap);
        }
        
        // Loop final list of devices
        for (String device : devices) {
            //DRS 20130126 - Added 1 - getTuner count for device
            int tunerCount = getTunerCountFromRegistryOrDevice(device, ipAddressMap);
            for (int j = 0; j < tunerCount; j++){
                Tuner aTuner = new TunerHdhr(device, j, addDevice, liveModelMap.get(device), ipAddressMap.get(device));  // may be automatically added to this.tuners map
                boolean nonHttpTunerUnavailable = (aTuner instanceof TunerHdhr) && !CaptureManager.useHdhrCommandLine && !((TunerHdhr)aTuner).isHttpCapable();
                if (!aTuner.isDisabled() && !nonHttpTunerUnavailable){
                    tunerList.add(aTuner);
                    aTuner.liveDevice = true;
                } else if (nonHttpTunerUnavailable) {
                    System.out.println(new Date() + " device " + device + "-" + j + " is not capable of http recording and hdhomerun_config.txt is unavailable, so not being added.");
                } else {
                    System.out.println(new Date() + " device " + device + "-" + j + " is disabled, so not being added.");
                }
            }
        }
        if (!CaptureManager.useHdhrCommandLine){
            System.out.println(new Date() + " Hdhr command line not configured [" + new File(CaptureManager.hdhrPath + File.separator + "hdhomerun_config.exe" + "]. CwHelper will attempt to use http capture methods."));
        }
        

        return tunerList;
    }

	//DRS 20220707 - Fix broken logging of devices 
    private String reformatWebDiscover(String liveDiscoverText) {
        StringBuffer reformattedBuf = new StringBuffer();
        /*
         *     
    {
        "DeviceID": "10A369BB",
        "LocalIP": "192.168.3.186",
        "BaseURL": "http://192.168.3.186",
        "DiscoverURL": "http://192.168.3.186/discover.json",
        "LineupURL": "http://192.168.3.186/lineup.json"
    }

         */
        
        try {
            BufferedReader in = new BufferedReader(new StringReader(liveDiscoverText));
            String lastDeviceId = "";
            String lastLocalIp = "";
                    
            String l = null;
            while ((l = in.readLine()) != null) {
                if (l.contains("DeviceID")){
                    String[] deviceLineItems = l.split(":");
                    if (deviceLineItems.length == 2) {
                        lastDeviceId = deviceLineItems[1].replace("\"","").replace(",", "").trim();
                    }
                }
                if (l.contains("LocalIP")){
                    String[] ipLineItems = l.split(":");
                    if (ipLineItems.length == 2) {
                        lastLocalIp = ipLineItems[1].replace("\"","").replace(",", "").trim();
                        reformattedBuf.append("hdhomerun device " + lastDeviceId + " found at " + lastLocalIp + "\n");
                    }
                }
            }
        } catch (Exception e) {
            System.out.println(new Date() + " ERROR: unable to parse liveDiscoverText from URL.");
            return "";
        }
        return reformattedBuf.toString();
    }

    // DRS 20220606 - Added method
    // DRS 20220707 - Fix broken logging of devices
    private static String getDeviceListAsString(TreeSet<String> devices) {
        StringBuffer buf = new StringBuffer();
        for (String device : devices) {
            buf.append(device).append(", ");
        }
        return buf.toString();
    }

    //DRS 20130126 - Added method
    private int getTunerCountFromRegistryOrDevice(String device, HashMap<String,String> ipAddressMap) {
        int tunerCount = 2; // minimum number is 2, even if we can't get this data out of the registry
        //DRS 20220708 - Added new code in 'if'
        if (!CaptureManager.useHdhrCommandLine && ipAddressMap != null) {
            Set<String> devices = ipAddressMap.keySet();
            for (String deviceName : devices) {
                System.out.println("key [" + deviceName + "] entry [" + ipAddressMap.get(device) + "]");
                if (deviceName.equalsIgnoreCase(device)) {
                    String discoverJson = LineUpHdhr.getPage("http://" + ipAddressMap.get(deviceName) + "/discover.json", 10, false, false);
                    // System.out.println("discoverJson [" + discoverJson + "]"); //{"FriendlyName":"HDHomeRun FLEX 4K","ModelNumber":"HDFX-4K","FirmwareName":"hdhomerun_dvr_atsc3","FirmwareVersion":"20220203","DeviceID":"10A369BB","DeviceAuth":"MNsOdjqp7sSa4rEv76QuwBUI","BaseURL":"http://192.168.3.186:80","LineupURL":"http://192.168.3.186:80/lineup.json","TunerCount":4}
                    int locTunerCount = discoverJson.indexOf("TunerCount\":");
                    if (locTunerCount > -1) {
                        try {
                            tunerCount = Integer.parseInt(discoverJson.substring(locTunerCount + 12, locTunerCount + 13));
                        } catch (Throwable t) {
                            System.out.println(new Date() + " ERROR: Unable to find tuner count value in json response " + discoverJson);
                        }
                    } else {
                        System.out.println(new Date() + " ERROR: Unable to find tuner count heading in json response " + discoverJson);
                    }
                    break;
                }
            }
        } else {
            int progress = -1;
            try {
                for (int i = 0; i < 6; i++){
                    progress = i;
                    String location = "SOFTWARE\\Silicondust\\HDHomeRun\\Tuners\\" + device + "-" + i;
                    boolean itemExists = false;
                    if (!TunerManager.skipRegistryForTesting) itemExists = Registry.valueExists("HKEY_LOCAL_MACHINE", location, "Source");
                    if (itemExists){
                        int proposedCount = i + 1;
                        if (proposedCount > tunerCount) tunerCount = proposedCount;
                    }
                }
            } catch (Exception e) {
                if (progress < 2) System.out.println(new Date() + " ERROR: Could not count tuners for HDHomeRun for device " + device + ". Returning " + tunerCount + ". " + e.getMessage());
            }
        }
        return tunerCount;
    }

    // DRS 20120315 - Added method - if there are valid captures, then wait, try again, and set liveDevice.
    private TreeSet<String> eliminateDevicesThatDoNotComeAliveOnRetry(TreeSet<String> devices, ArrayList<String> liveDevices, ArrayList<String> fileDevices) {
        boolean writeOutputToFile = true; // this is the best discover.txt file we'll have, go ahead and write it.
        HashSet<String> deadDevices = new HashSet<String>();
        for (String device : devices) {  // device is just something like "1076C3A7"
            boolean reallyDeadDevice = true;
            if (liveDevices.contains(device)){
                // Fine.  Device is live and we will add it later
                System.out.println(new Date() + " " + device + " was live.");
            } else if (fileDevices.contains(device)){
                System.out.println(new Date() + " " + device + " was NOT live.");
                // Device not live, but is contained in the discover.txt.  Might need time to start-up.
                //DRS 20130126 - moved code out into new method
                if (deviceHasValidCapture(device, null)){
                    for (int retries = 0; retries < 3; retries++){
                        System.out.println(new Date() + " device " + device + " was not live, but had valid current or future captures.  Waiting 15 seconds to try again.");
                        try {Thread.sleep(15000);} catch (Exception e){};
                        String localLiveDiscoverText = getLiveDiscoverText(CaptureManager.discoverRetries, CaptureManager.discoverDelay);
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
        BufferedReader in = null;
        try {
            System.out.println("looking for " + CaptureManager.dataPath + "discover.txt");
            in = new BufferedReader(new FileReader(CaptureManager.dataPath + "discover.txt"));
            String l = null;
            while ((l = in.readLine()) != null){
                fileDiscoverText += l + "\n";
            }
        } catch (Exception e) {
            System.out.println("discover.txt file not found. " + e.getMessage());
        } finally {
            try {in.close();} catch (Throwable t){}
        }
        return fileDiscoverText;
    }
    
    // DRS 20170106 - Added Method
    private String getLiveDiscoverText(int retryCount, int retryDelay) {
        String liveDiscoverText = "";
        for (int j = retryCount; j > 0; j--) {
            try {
                liveDiscoverText = getLiveDiscoverText();
                if (liveDiscoverText.indexOf("no devices") > -1){
                    System.out.println(new Date() + " discover reported no devices.");
                    return "";
                }
                if (!"".equals(liveDiscoverText)) return liveDiscoverText;
            } catch (Exception e) {
                System.out.println(new Date() + "getLiveDiscoverText error: " + e.getMessage());
            }
            try {Thread.sleep(retryDelay);} catch (Exception e) {};
        }
        return liveDiscoverText;
    }
    
    // DRS 20120315 - Added method - if there are valid captures, then wait, try again, and set liveDevice.
    private String getLiveDiscoverText() throws Exception {
        String[] commands = {"discover"};
        HdhrCommandLine cl = new HdhrCommandLine(commands, 30, false);
        boolean ok = false;
        String rtError = "";
        try {ok = cl.runProcess();} catch (RuntimeException e) {
            ok = false;
            rtError = e.getMessage();
        }
        String liveDiscoverText = "";
        try {liveDiscoverText = cl.getOutput();} catch (Throwable t) {}
        System.out.println(new Date() + " liveDiscoverText [\n" + liveDiscoverText + "] Command line run :" + ok + " - " + rtError);
        if (!ok) throw new Exception("Command line runtime error " + rtError);
        return liveDiscoverText;
    }


    // DRS 20181025 - Added Method
    // DRS 20220606 - Removed ipAddress (not needed), Removed dead code.
    private HashMap<String, Integer> getLiveModelMap(ArrayList<String> liveDevices, int retryCount, int retryDelay) {
        HashMap<String, Integer> liveCapabilityMap = new HashMap<String, Integer>();
        devices:
        for (String device : liveDevices) {
            for (int j = retryCount; j > 0; j--) {
                try {
                    String vChannelResponseString = getLiveVchannelText(device).toUpperCase();      // Old tuners say "...UNKNOWN...", New tuners say "" or "...RESOURCE LOCKED..." or (something else)
                    String hwmodelResponseString = getLiveModelText(device).toUpperCase();          // Old tuners say "...HDHR-US...", New tuners say (something else)
                    
                    //boolean liveModelIsEmpty = vChannelResponseString.isEmpty();                            // Possible New tuner
                    boolean vChannelResponseContainsUnknown = vChannelResponseString.contains("UNKNOWN");   // Old tuner for sure
                    boolean liveModelContainsKnownOldModel = hwmodelResponseString.contains("HDHR-US") || hwmodelResponseString.contains("HDHR3-US") ;   // DRS 20191219 - Added HDHR3-US as old   // Old tuner for sure
                    
                    boolean isOldTuner = vChannelResponseContainsUnknown || liveModelContainsKnownOldModel;
                    if (CaptureManager.allTraditionalHdhr) {
                        if (!isOldTuner) System.out.println(new Date() + " isOldTuner set to true (override) based on allTraditionalHdhr setting.");
                        isOldTuner = true;
                    } else {
                        System.out.println(new Date() + " isOldTuner: " + isOldTuner + " because vChannelResponseContainsUnknown: " + vChannelResponseContainsUnknown + " or  liveModelContainsHdhrUs: " + liveModelContainsKnownOldModel); // DRS 20191219 - Removed short lineup xml  + " or lineup.xml was short: " + hasShortLineupXml);
                    }
                    
                    if (isOldTuner) { //DRS 20181106 - Added detection for old devices
                        liveCapabilityMap.put(device, TunerHdhr.ORIGINAL); 
                        continue devices;
                    } else {
                        liveCapabilityMap.put(device, TunerHdhr.VCHANNEL); 
                        continue devices;
                    }
                } catch (Exception e) {
                    System.out.println(new Date() + "getLiveCapabilityMap error: " + e.getMessage());
                }
                try {Thread.sleep(retryDelay);} catch (Exception e) {};
            }
        }
        return liveCapabilityMap;
    }
    
    //DRS 20220707 - Added Method
    private HashMap<String, Integer> getLiveModelMapHttp(HashMap<String, String> ipAddressMap) {
        HashMap<String, Integer> liveCapabilityMap = new HashMap<String, Integer>();
        Set<String> keys = ipAddressMap.keySet();
        for (String key : keys) {
            liveCapabilityMap.put(key, TunerHdhr.VCHANNEL);
        }
        return liveCapabilityMap;
    }    

    // DRS 20181025 - Added Method
    private String getLiveVchannelText(String device) throws Exception {
        String[] commands = {device, "set","/tuner0/vchannel", "none"}; // returns error for vchannel capable tuners
        HdhrCommandLine cl = new HdhrCommandLine(commands, 30, false);
        boolean ok = false;
        String rtErr = "";
        try {ok = cl.runProcess();} catch (RuntimeException e) {
            ok = false;
            rtErr = e.getMessage();
        }
        String liveModelText = "";
        try {liveModelText = cl.getOutput();} catch (Throwable t) {}
        if (liveModelText != null) {
            liveModelText = liveModelText.trim();
            if (liveModelText.startsWith("ERROR:")) {
                liveModelText = "ERR:" + liveModelText.substring(5);
            }
        }
        //System.out.println(new Date() + " tuner said [" + liveModelText + "] to /tuner0/vchannel. Command ran ok? " + (ok?"YES":"NO") + " rtErr: " + (rtErr.isEmpty()?"":rtErr));
        if (!ok) throw new Exception("Command line runtime error " + rtErr);
        return liveModelText;
    }
    
    // DRS 20181106 - Added Method
    private String getLiveModelText(String device) throws Exception {
        String[] commands = {device, "get","/sys/hwmodel"}; // returns "HDHR-US" for old and we consider HDHR3-US also old
        HdhrCommandLine cl = new HdhrCommandLine(commands, 30, false);
        boolean ok = false;
        String rtErr = "";
        try {ok = cl.runProcess();} catch (RuntimeException e) {
            ok = false;
            rtErr = e.getMessage();
        }
        String liveModelText = "";
        try {liveModelText = cl.getOutput();} catch (Throwable t) {}
        if (liveModelText != null) liveModelText = liveModelText.trim(); else liveModelText = "";
        boolean containsOldText = liveModelText.contains("HDHR-US") || liveModelText.contains("HDHR3-US");
        System.out.println(new Date() + " tuner said [" + liveModelText + "] to /sys/hwmodel.  Is old model:" + (containsOldText?"YES":"NO") +  " command ran ok? " + (ok?"YES":"NO") + " rtErr: " + (rtErr.isEmpty()?"(none)":rtErr));
        if (!ok) throw new Exception("Command line runtime error " + rtErr);
        return liveModelText;
    }
    
    // DRS 20191206 - Added Method
    private String getXmlOutputFromDevice(String ipAddress, boolean quiet) {
        try {
            if (ipAddress == null || ipAddress.length() < 8) {
                System.out.println(new Date() + " ERROR: IP address for tuner not available. [" + ipAddress + "]");
                return "(unavailable)";
            }
            String lineupPage = LineUpHdhr.getPage("http://" + ipAddress + "/lineup.xml?tuning", CaptureManager.discoverDelay, quiet, false);
            if (lineupPage.length() == 0) {
                System.out.println(new Date() + " ERROR: unable to get lineup xml.");
            } else if (lineupPage.length() < 100) {
                System.out.println(new Date() + " WARNING: lineup xml was shorter than expected. [" + lineupPage.replace("\n", "").replace("\r", "").trim() + "]");
                return "(short lineup)";
            } else {
                System.out.println(new Date() + " lineup xml had " + lineupPage.length() + " characters.");
                return lineupPage;
            }
            return "(unavailable)";
        } catch (Exception e) {
            System.out.println(new Date() + " getXmlOutputFromDevice failed on ipAddress [" + ipAddress + "] " + e.getMessage());
            return "(unavailable)";
        }
    }
    

    //DRS 20130126 - Added method
    private boolean deviceHasValidCapture(String device, HashMap<String, String> ipMap) {
        boolean localAddDevice = false;
        int tunerCount = getTunerCountFromRegistryOrDevice(device, ipMap);
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
        String result = scanRefreshLineupTm();
        if (result.indexOf("ERROR") > -1) System.out.println(new Date() + " ERROR: LoadChannelsFromFile: " + result);
        return result;
	}
    
    public String scanRefreshLineupTm(){
        return scanRefreshLineUpTm(true, null, null, 9999);
    }

    public String scanRefreshLineUpTm(boolean useExistingFile, String tunerName, String signalType, int maxSeconds) {
        String returnValue = "";
        if (noTuners()) {
            System.out.println(new Date() + " No Tuners. Use discover.");
            return "No Tuners. Use discover.";
        }
        if (CaptureManager.rerunDiscover && !hdhrCountOk()) {  // if rerunDiscover is true then hdhrCountOk runs and causes live discover to be run.
            System.out.println(new Date() + " Hdhr Count not accurate. Use discover.");
            return "Hdhr Count not accurate. Use discover.";
        }
        System.out.println(new Date() + " TunerManager.scanRefreshLineUpTm for " + tuners.size() + " tuners. ");
        for (Iterator<Tuner> iter = this.iterator(); iter.hasNext();) {
            Tuner tuner = iter.next();
            if (tunerName == null || tuner.getFullName().equalsIgnoreCase(tunerName)){
                if (tuner instanceof TunerHdhr) {
                    TunerHdhr hdhrTuner = (TunerHdhr)tuner;
                    if (hdhrTuner.ipAddressTuner == null || hdhrTuner.ipAddressTuner.length() < 6) {
                        String message = " Tuner " + tuner.getFullName() + " had bad ip address [" + hdhrTuner.ipAddressTuner + "]";
                        returnValue += message;
                        System.out.println(new Date() + message);
                        nonResponsiveTuners.add(tuner);
                        continue;
                    }
                    if (!respondsWithWebPage((TunerHdhr)tuner)) {
                        String message = " Tuner " + tuner.getFullName() + " did not respond with web page.  Not scanning.";
                        returnValue += message;
                        System.out.println(new Date() + message);
                        nonResponsiveTuners.add(tuner);
                        continue;
                    }
                }
                if (!tuner.liveDevice){
                    String message =  " Tuner " + tuner.getFullName() + " is not active.  Not scanning.";
                    returnValue += message;
                    System.out.println(new Date() + message);
                    nonResponsiveTuners.add(tuner);
                    continue;
                }
                try {
                    System.out.println("running scan for tuner " + tuner.getFullName());
                    //((TunerHdhr)tuner).scanRefreshLineUp(useExistingFile, signalType, maxSeconds);
                    tuner.scanRefreshLineUp(useExistingFile, signalType, maxSeconds);
                    returnValue += "OK,";
                } catch (Throwable e) {
                    returnValue += "ERROR " + e.getMessage() + ",";
                }
            } else {
                System.out.println(new Date() + " Not scanning " + tuner.getFullName());
            }
        }
        // DRS 20210415 - Added 'for' loop + 1 - Concurrent Modification Exception
        for (Tuner tuner : nonResponsiveTuners) {
            System.out.println(new Date() + " Removing non-responsive tuner: " + tuner.getFullName());
            this.tuners.remove(tuner.getFullName());
            tuner.removeAllCaptures(true); //before deleting a tuner, delete it's captures
        }
        nonResponsiveTuners.clear();

        return returnValue;
    }
    
    public String interruptScan() {
        String returnValue = "TunerManager.interruptScan(): ";
        if (noTuners()) return "No Tuners. Use discover.";
        System.out.println(new Date() + " TunerManager.interruptScan() for all Hdhr tuners. ");
        for (Iterator<Tuner> iter = this.iterator(); iter.hasNext();) {
            Tuner tuner = iter.next();
            if (!(tuner instanceof TunerHdhr)) continue;
            if (!tuner.liveDevice){
                returnValue += "Tuner " + tuner.getFullName() + " is not active.  Not interrupting.";
                continue;
            }
            try {
                System.out.println("Interrupting scan for tuner " + tuner.getFullName());
                ((TunerHdhr)tuner).interruptScan();
                returnValue += "interrupt Sent, ";
            } catch (Throwable e) {
                returnValue += "ERROR " + e.getMessage() + ",";
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
    
    // DRS 20190422  - Added Method
    public boolean hdhrCountOk() {
        // DRS 20200908 - Changed count to devices, not tuners (which was wrong all along).  Example device id is 269940550, example tuners on the device is 1010FADA-1,1010FADA-0. 
        //int hdhrCount = 0;
        Set<String> deviceSet = new HashSet<String>();
        for (Iterator<Tuner> iter = this.iterator(); iter.hasNext();) {
            Tuner aTuner = iter.next();
            if (aTuner instanceof TunerHdhr) {
                //hdhrCount++;
                deviceSet.add(((TunerHdhr)aTuner).getDeviceId());
            }
        }
        String liveDiscoverText = getLiveDiscoverText(CaptureManager.discoverRetries, CaptureManager.discoverDelay);
        ArrayList<String> liveDevices = findTunerDevicesFromText(liveDiscoverText, true); // devices.txt is always written out here (we read the old one already).
        System.out.println(new Date() + " Got " + liveDevices.size() + " items from active discover command.");
        System.out.println(new Date() + " Had " + deviceSet.size() + " devices from the tuner list.  Returning '" + (liveDevices.size() == deviceSet.size()) + "' to hdhrCountOk()"); 
        //return liveDevices.size() == hdhrCount;
        return liveDevices.size() == deviceSet.size();
    }
    
    public boolean hasHdhrTuner() {
        Tuner aTuner = null;
        for (Iterator<Tuner> iter = this.iterator(); iter.hasNext();) {
            aTuner = iter.next();
            if (aTuner instanceof TunerHdhr) {
                return true;
            }
        }
        return false;
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
    
    // returns a list of String like [1076C3A7, 1075D4B1, 1080F19F] 
    private ArrayList<String> findTunerDevicesFromText(String output, boolean writeToFile) {
        // output looks like this [hdhomerun device 1076C3A7 found at 169.254.246.92]
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
                            Integer.parseInt(deviceId, 16); // just a validation... we don't actually use the converted value.
                            result.add(deviceId);
                        } catch (Throwable e){
                            // System.out.println(deviceId + " followed the word device, but wasn't hex string");
                        }
                    }
                }
			}
			in.close();
			// if we get valid discover output, write the file - overwriting, not appending
			if (result.size() > 0 && writeToFile){
				BufferedWriter out = new BufferedWriter(new FileWriter(CaptureManager.dataPath + "discover.txt"));
				out.write(new String(buf));
				out.flush();
				out.close();
			}
		} catch (IOException e) {}
		return result;
	}
    
    // discover text looks like this [hdhomerun device 1075D4B1 found at 192.168.1.16]
    // discover text input to hmap might look like this:
    //    1076C3A7 169.254.217.171 <<< Conflict
    //    1075D4B1 192.168.1.16
    //    1080F19F 192.168.1.18
    //    1076C3A7 169.254.3.188  <<< Conflict
    //    1075D4B1 192.168.1.16
    //    1080F19F 192.168.1.18
    //    The conflict must go to the liveDiscoverText, not the fileDiscoverText
    
    private static HashMap<String, String> getIpMap(String fileDiscoverText, String liveDiscoverText) {
        HashMap<String, String> result = new HashMap<String, String>();
        for (int i = 0; i < 2; i++ ) {
            try {
                String output = fileDiscoverText;
                if (i > 0) output = liveDiscoverText;
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
                            while (tok.hasMoreTokens()) {
                                t = tok.nextToken();
                                if (t.equals("at") && tok.hasMoreTokens()) {
                                    String ipAddress = tok.nextToken();
                                    result.put(deviceId, ipAddress);
                                }
                            }
                        }
                    }
                }
                in.close();
            } catch (IOException e) {}
        }
        return result;
    }

    // DRS 20210421 - Added method - Fail quicker on dead tuners during scan
    public boolean respondsWithWebPage(TunerHdhr tuner) {
        int maxTries = 3;
        return !LineUpHdhr.getPage("http://" + tuner.ipAddressTuner, 3, true, false, maxTries).isEmpty();
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
    
    // DRS 20200305 - Added method - Default changed from adding by virtual to adding by name
    public void addAltChannels(String altChannels) {
        addAltChannels(altChannels, NAME_MATCHING);
    }
   
    public void addAltChannels(String altChannels, int matchingType) {
        ArrayList<String> noCopyVirtuals = new ArrayList<String>();
        if (altChannels != null ) {
            // DRS 20200305 - Added 2 - Adding match on name
            String channelFormatMessage = "Each channel format is '(channelVirtual)'";
            if (matchingType == TunerManager.NAME_MATCHING) channelFormatMessage = "Each channel format is '(channelName)'";
            System.out.println(new Date() + " Loading alternate channels with String input [" + altChannels + "].  Should be \"ChA^ChAalt,ChB^ChBalt\".  " + channelFormatMessage);
            try {
                String[] channelNamePairs = altChannels.split(",");
                for (String channelNamePair : channelNamePairs) {
                    String[] channelNames = channelNamePair.split("\\^");
                    if (channelNames == null || channelNames.length != 2) {
                        System.out.println(new Date() + " Ignoring alternate channel input " + channelNamePair + " because it could not be parsed with '^'.");
                        continue;
                    } else if (channelNames[0].equals(channelNames[1])) {
                        System.out.println(new Date() + " Ignoring alternate channel input " + channelNamePair + " because it was stupid.");
                        continue;
                    }
                    if ("0".equals(channelNames[1])) {
                        noCopyVirtuals.add(channelNames[0]);
                        continue;
                    }
                    Channel aChannel = null;
                    Channel bChannel = null;
                    // DRS 20200305 - Added logic for name matching and to allow adding more than one matching channel
                    if (matchingType == NAME_MATCHING) {
                        ArrayList<Channel> matchingChannelListA = getChannelsByName(channelNames[0]);
                        ArrayList<Channel> matchingChannelListB = getChannelsByName(channelNames[1]);
                        for (Channel channelA : matchingChannelListA) {
                            for (Channel channelB : matchingChannelListB) {
                                if (channelA != null && channelB != null) {
                                    channelA.addAltChannel(channelB, "named alternate");
                                    channelB.addAltChannel(channelA, "named alternate");
                                } else {
                                    System.out.println(new Date() + " Ignoring alternate channel " + channelNamePair + " because one or both channels were not found.");
                                }
                            }
                        }
                    } else { // This is outdated code.  This matching method may never be revived.
                        aChannel = getChannelByVirtual(channelNames[0]);
                        bChannel = getChannelByVirtual(channelNames[1]);
                        if (aChannel != null && bChannel != null) {
                            aChannel.addAltChannel(bChannel, "named alternate");
                            //System.out.println(new Date() + " Alternate Channel: " + bChannel + " as an alternate to " + aChannel);
                            bChannel.addAltChannel(aChannel, "named alternate");
                            //System.out.println(new Date() + " Alternate Channel: " + aChannel + " as an alternate to " + bChannel);
                        } else {
                            System.out.println(new Date() + " Ignoring alternate channel " + channelNamePair + " because one or both channels were not found.");
                        }
                    }
                }
            } catch (Throwable t) {
                System.out.println(new Date() + " ERROR: Not setting altChannels " + t.getMessage());
            }
        } else {
            System.out.println(new Date() + " no altChannels defined.");
        }
        
        if (CaptureManager.hdhrRecordMonitorSeconds > 0) {
            System.out.println(new Date() + " Loading alternate channels with same alphaDescription.");
            Collection<Channel> channels = this.getAllChannels(false);
            for (Channel channel : channels) {
                Channel[] altChannelList = getChannelsByDescription(channel.alphaDescription);
                channel.addAltChannels(altChannelList, "alphaDescription match", noCopyVirtuals);
            }
        }
    }
    
    private Channel[] getChannelsByDescription(String alphaDescription) {
        if (alphaDescription == null) return null;
        Collection<Channel> channels = this.getAllChannels(false);
        ArrayList<Channel> foundChannels = new ArrayList<Channel>();
        for (Iterator<Channel> iter = channels.iterator(); iter.hasNext();) {
            Channel channelDigital = iter.next();
            String matchChannel = channelDigital.alphaDescription;
            if (matchChannel.equals(alphaDescription)) {
                foundChannels.add(channelDigital);
            }
        }
        return foundChannels.toArray(new Channel[0]);
    }
    
    public void clearAltChannels() {
        Collection<Channel> channels = this.getAllChannels(false);
        for (Channel channel : channels) {
            channel.clearAltChannels();
        }
    }


    public Capture getCaptureForChannelNameSlotAndTuner(String channelName, Slot slot, String tuner, String protocol) {
        boolean debug = true; // DRS 20181128
        if (debug) System.out.println("getCaputureForChannelNameSlotAndTuner(" + channelName + ", " + slot + ", " + tuner + ", " + protocol);
        List<Capture> captures = getAvailableCapturesForChannelNameAndSlot(channelName, slot, protocol);
        if (captures == null || captures.size() == 0) {
            lastReason += "<br>getAvailableCapturesForChannelNameAndSlot returned no captures for " + channelName + " " + tuner + " " + protocol + " " + slot; // <<<<<<<<<<<<<<<<<<20161214
            if (debug) System.out.println("LR:" + lastReason);
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
                if (debug) System.out.println("LR2:" + lastReason);
            } else {
                System.out.println(new Date() + " Found channel match on " + capture.channel.tuner.getFullName() +"'s lineup for channelName: " + channelName + " protocol: " + protocol);
            }
        }
        return capture;
    }
    
    // DRS 20110214 - Added method
    public ArrayList<Capture> getCapturesForAllChannels(String channelName, Slot slot, String tunerString, String protocol) {
        ArrayList<Capture> captureList = new ArrayList<Capture>();
        try {
            Map<String,Channel> channelMap = this.getTuner(tunerString).lineUp.channels;
            Map<String,Channel> compressedChannelMap = new TreeMap<String,Channel>();
            Set<String> keySet = channelMap.keySet();
            System.out.print("The tuner [" + tunerString + "] had a channeMap of size [" + channelMap.size() + "]");
            System.out.println((channelMap.size()<2)?" <<<<< ERROR":"");
            // DRS 20190120 - compress out the subchannels (the tuner tunes to the frequency that contains all subchannels)
            int i = 0; 
            StringBuffer foundFrequencies = new StringBuffer();
            for (Iterator<String> iter = keySet.iterator(); iter.hasNext(); i++) {
                String key = iter.next();
                Channel aChannel = channelMap.get(key);
                if(aChannel.frequency == null) {
                    System.out.println(new Date() + " ERROR: tuner [" + tunerString + "] is reporting a null frequency. [" + aChannel + "]");
                }
                String foundFrequency = "|" + aChannel.frequency + "|";
                if (foundFrequencies.indexOf(foundFrequency) < 0) {
                    compressedChannelMap.put(key, aChannel);
                    foundFrequencies.append(foundFrequency);
                } 
            }
            
            if (compressedChannelMap.size() < 2) System.out.println(new Date() + " ERROR: " + compressedChannelMap.size() + " channels found on the tuner!!  Should be more.");
            System.out.println(new Date() + " Frequencies for [" + tunerString + "].  The tuner had [" + foundFrequencies + "] frequencies.");
            
            ArrayList<Slot> slotList = slot.split(compressedChannelMap.size());
            i = 0;
            keySet = compressedChannelMap.keySet();
            for (Iterator<String> iter = keySet.iterator(); iter.hasNext(); i++) {
                Object key = iter.next();
                Channel aChannel = compressedChannelMap.get(key);
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
    public String sortChannelMap(long daysOldLimit) {
        TreeMap<String, CaptureDetails> channelsMapWithCaptureDetails = CaptureDataManager.getInstance().getSignalStrengthByChannelAndTuner(daysOldLimit);
        if (channelsMapWithCaptureDetails.size() == 0) return "No HDHR signal strength history found";
        
        // Get current channels from cw_epg "channel_maps.txt" file
        TreeMap<Integer, CwEpgChannelRow> numberedMapOfCwEpgChannelRow = new TreeMap<Integer,CwEpgChannelRow>();
        List<String>headers = null;
        CSVReader reader = null;
        try {
            reader = new CSVReader(new BufferedReader(new FileReader(CaptureManager.dataPath + File.separator + "channel_maps.txt"))); 
            headers = reader.readHeader();
            int i = 0;
            Map<String, String> map = null;
            int count = 0;
            while ((map = reader.readValues()) != null) {
                CwEpgChannelRow aRow = new CwEpgChannelRow(map);
                numberedMapOfCwEpgChannelRow.put(new Integer(i++), aRow);
                System.out.println(++count + " reading text file: " + aRow);
            }
        } catch (Exception e){
            return "Failed to sort. Failed to read sorted channels.  " + e.getMessage();
        } finally {
            try {reader.close();} catch (Throwable t){}
        }
        if (numberedMapOfCwEpgChannelRow.size() == 0) return "No CW_EPG channel map was found";
        else System.out.println("The numberedMapOfCwEpgChannelRow from channel_maps.txt was sized at " + numberedMapOfCwEpgChannelRow.size());

        
        // Make an empty Map to hold the strength-sorted items
        TreeMap<String, CaptureDetails> strengthSortedChannelsMapWithCaptureDetails = new TreeMap<String, CaptureDetails>();
        // Make an empty Map to hold the channels that are unmapped (got a recording, but there is no map)
        TreeMap<String, CaptureDetails> missingChannelsMapWithCaptureDetails = new TreeMap<String, CaptureDetails>();
        // Loop through the capture details
        Iterator<String> iter = channelsMapWithCaptureDetails.keySet().iterator();
        while(iter.hasNext()){
            String key = (String)iter.next();
            CaptureDetails details = channelsMapWithCaptureDetails.get(key);
            String channelNumber = key.split("::")[0];
            Integer strengthNumber = details.getStrengthValue();
            strengthSortedChannelsMapWithCaptureDetails.put(channelNumber + "::" + strengthNumber, details);
        }
        // (now we have channelsMapWithCaptureDetails sorted by channelNumber and signal strength called "strengthSortedChannelsMapWithCaptureDetails")
        
        Iterator<String> iter2 = strengthSortedChannelsMapWithCaptureDetails.keySet().iterator();
        while (iter2.hasNext()) {
            String key = iter2.next();
            System.out.println("proof of strenght sortation: " + key + " " + strengthSortedChannelsMapWithCaptureDetails.get(key).getStrengthSummary());
        }
        //proof of strenght sortation: 21.3:1-8vsb::100035 21.3:1-8vsb::1010CC54-0 tunsnq:85 tmiss:0
        //proof of strenght sortation: 21.3:1-8vsb::100042 21.3:1-8vsb::1013FADA-0 tunsnq:71 tmiss:0
        //proof of strenght sortation: 21.3:1-8vsb::100132 21.3:1-8vsb::1013FADA-1 tunsnq:100 tmiss:51
        //proof of strenght sortation: 21.3:1-8vsb::100571 21.3:1-8vsb::1010CC54-1 tunsnq:36 tmiss:244
        //proof of strenght sortation: 21.4:1-8vsb::100035 21.4:1-8vsb::1010CC54-0 tunsnq:85 tmiss:0
        //proof of strenght sortation: 21.4:1-8vsb::100042 21.4:1-8vsb::1013FADA-0 tunsnq:71 tmiss:0
        //proof of strenght sortation: 21.4:1-8vsb::100132 21.4:1-8vsb::1013FADA-1 tunsnq:100 tmiss:51
        //proof of strenght sortation: 21.4:1-8vsb::100571 21.4:1-8vsb::1010CC54-1 tunsnq:36 tmiss:244
        
        // (now we have printed channelsMapWithCaptureDetails sorted by signal strength called "strengthSortedChannelsMapWithCaptureDetails")

        // Make an empty numbered list to hold future numbered map of CwEpgChannelRow
        ArrayList<CwEpgChannelRow> channelsSorted = new ArrayList<CwEpgChannelRow>();

        ArrayList<Integer> toBeRemovedKeys = new ArrayList<Integer>();
        // For each item from channelsMapWithCaptureDetailsSortedBySignalStrength
        iter2 = strengthSortedChannelsMapWithCaptureDetails.keySet().iterator();
        while (iter2.hasNext()) {
            String iteratorKey = iter2.next();
            CaptureDetails details = strengthSortedChannelsMapWithCaptureDetails.get(iteratorKey);// key like 11.3:1-8vsb::100030, that second part is strength
            String channelTunerKey = details.getChannelTunerKey(); //  [channelKey + "::" + tunerName], for example "21.6:1-8vsb::1013FADA-1"
            boolean found = false;
            
            //   Find the matching item in numberedMapOfCwEpgChannelRow
            Iterator<Integer> iter3 = numberedMapOfCwEpgChannelRow.keySet().iterator();
            while (iter3.hasNext()) {
                Integer keyFromCwEpgChannelRowMap = iter3.next();
                CwEpgChannelRow aRow = numberedMapOfCwEpgChannelRow.get(keyFromCwEpgChannelRowMap);
                String aRowChannelTuner = aRow.getFormattedChannelTuner(); // for example "21.6:1-8vsb::1013FADA-1"
                if (channelTunerKey.startsWith(aRowChannelTuner)) {
                    System.out.println("FOUND<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<< ADDED TO NEW LIST<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<< " + channelTunerKey + " aRowChannelTuner " + aRowChannelTuner);
                    channelsSorted.add(aRow);
                    found = true;
                    toBeRemovedKeys.add(keyFromCwEpgChannelRowMap);
                    
                    break;
                }
            }
            if (!found) {
                //   If not found, we have an unmapped channel, make a note of it 
                System.out.println("Looking for " + channelTunerKey + " was not found in CwEpgChannelRows ");
                missingChannelsMapWithCaptureDetails.put(iteratorKey, details);
            }
            
        }
        // (now we have channels back into the new list, but it might not have all because we might not have gotten a recording for the other ones)
        
        //  Remove the items from the list we found them in (toBeRemovedKeys)
        System.out.println("Channel Rows starting " + numberedMapOfCwEpgChannelRow.size());
        int sizeOfOriginal = numberedMapOfCwEpgChannelRow.size();
        for (Integer keyToRemove : toBeRemovedKeys) {
            numberedMapOfCwEpgChannelRow.remove(keyToRemove);
        }
        System.out.println("Channel Rows after removing matches " + numberedMapOfCwEpgChannelRow.size());
        int sizeOfDefault = numberedMapOfCwEpgChannelRow.size();
        
        // Copy any remaining items still in the CwEpgChannelRow list onto the bottom of the new list.  These channels are probably 'dead' channels.
        // Put the rows that didn't have strength data at the bottom
        Set<Integer> keys = numberedMapOfCwEpgChannelRow.keySet();
        for (Integer key : keys) {
            CwEpgChannelRow aRow = numberedMapOfCwEpgChannelRow.get(key);
            channelsSorted.add(aRow);
        }
        
        // Write out the sorted map
        CSVWriter writer = null;
        try {
            writer = new CSVWriter(new BufferedWriter(new FileWriter(CaptureManager.dataPath + File.separator + "channel_maps.txt")));
            writer.setColumns(headers);
            writer.writeHeader();
            for (CwEpgChannelRow cwEpgChannelRow : channelsSorted) {
                writer.write(cwEpgChannelRow.getMap());
                
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

        iter2 = missingChannelsMapWithCaptureDetails.keySet().iterator();
        while (iter2.hasNext()) {
            CaptureDetails details = missingChannelsMapWithCaptureDetails.get(iter2.next()); 
            System.out.println("Recording from " + details.scheduledStart + " on " + details.channelName + " tuner " + details.tunerName + " was not included.");
        }
        System.out.println("A total of " + missingChannelsMapWithCaptureDetails.size() + " recordings were not included.");

        return "Signal strength data was used for " + (sizeOfOriginal - sizeOfDefault) + " channels.  " + sizeOfDefault + " channels were placed at the bottom, unsorted, for a total of " + sizeOfOriginal +".";
    }
    
        //  DRS 20200822 - Added method
    public static String sortChannelMapUsingSignalPriorityFile() {
        ArrayList<String> sortedChannelItems = new ArrayList<String>(); // This will be populated with all the lines from channel_maps.txt in an order specified by frequency_tuenr_priority
        StringBuffer returnMessage = new StringBuffer("nothing done");
        // **************************************************************************************************************
        // Get frequency/tuner priority from file and put into a list.  The list is supposed to be in priority order.
        // **************************************************************************************************************
        ArrayList<String> priorityList = new ArrayList<String>();
        List<String>headers = null;
        CSVReader reader = null;
        try {
            reader = new CSVReader(new BufferedReader(new FileReader(CaptureManager.dataPath + File.separator + "frequency_tuner_priority.txt"))); 
            headers = reader.readHeader();
            headers.set(0, reader.dropBOM(headers.get(0))); // If the first few bytes of the first header match standard Byte Order Mark, just drop those.
            System.out.println("frequency_tuner_priority.txt input file: " );
            Map<String, String> map = null;
            int count = 0;
            while ((map = reader.readValues()) != null) {
                String key = map.get("physical") + ":" + map.get("tuner");
                if (!key.contains("null")) {
                    priorityList.add(key);
                    System.out.println(++count + " tuner priority list filled with: " + key );
                } else {
                    System.out.println(++count + " tuner priority list problem with key: " + key );
                }
            }
        } catch (Exception e){
            return "Failed to load frequency_tuner_priority.txt   " + e.getMessage();
        } finally {
            if (reader != null) try {reader.close();} catch (Throwable t) {};
        }
        
        if (priorityList.size() == 0) returnMessage.replace(0, returnMessage.length(), "No data found in frequency_tuner_priority.txt");
        else returnMessage.replace(0, returnMessage.length(), "The frequency_tuner_priority.txt had " + priorityList.size() + " items.");
        
        // **************************************************************************************************************
        // Load original channel_maps file into a map
        // **************************************************************************************************************
        TreeMap<Integer, CwEpgChannelRow> numberedMapOfCwEpgChannelRow = new TreeMap<Integer,CwEpgChannelRow>();
        try {
            reader = new CSVReader(new BufferedReader(new FileReader(CaptureManager.dataPath + File.separator + "channel_maps.txt"))); 
            headers = reader.readHeader();
            sortedChannelItems.add(CSVReader.lastLine);  // put the header in the list as the first thing
            int i = 0;
            Map<String, String> map = null;
            int count = 0;
            while ((map = reader.readValues()) != null) {
                CwEpgChannelRow aRow = new CwEpgChannelRow(map);
                aRow.setRawLine(CSVReader.lastLine);
                numberedMapOfCwEpgChannelRow.put(new Integer(i++), aRow);
                System.out.println(++count + " reading text file: " + aRow);
            }
        } catch (Exception e){
            return "Failed to sort. Failed to read sorted channels.  " + e.getMessage();
        } finally {
            if (reader != null) try {reader.close();} catch (Throwable t) {};
        }

        if (numberedMapOfCwEpgChannelRow.size() == 0) return "No CW_EPG channel map was found";
        else System.out.println("The numberedMapOfCwEpgChannelRow from channel_maps.txt was sized at " + numberedMapOfCwEpgChannelRow.size());
        
        // **************************************************************************************************************
        // Put the lines from the channel_maps file into an array, based on the tuner priority.
        // **************************************************************************************************************
        // for each item in the priority list, pull matching items from the channel list.
        // match on "tuner", "physical" (frequency), and "program" (how physical is split)

        System.out.println("The numberedMapOfCwEpgChannelRow contained " + numberedMapOfCwEpgChannelRow.size() + " at the beginning.");
        for (String key : priorityList) {
            String[] aRow = key.split(":");
            String physical = aRow[0];
            String tuner = aRow[1];
            for (int program = 1; program < 15; program++) {
                Set<Integer> keySet = numberedMapOfCwEpgChannelRow.keySet();
                ArrayList<Integer> itemsToRemove = new ArrayList<Integer>();
                for (Integer item : keySet) {
                    CwEpgChannelRow epgChannel = numberedMapOfCwEpgChannelRow.get(item);
                    if (epgChannel.matches(tuner, physical, program + "")) {
                        sortedChannelItems.add(epgChannel.getRawLine());
                        itemsToRemove.add(item);
                    }
                }
                for (Integer item : itemsToRemove) {
                    numberedMapOfCwEpgChannelRow.remove(item);
                }
            }
        }
        System.out.println("The numberedMapOfCwEpgChannelRow contained " + numberedMapOfCwEpgChannelRow.size() + " at the end.");
        System.out.println("The sorted channel items contained " + sortedChannelItems.size() + " at the end.");
        BufferedWriter writer = null;
        try {
            // RENAME THE EXISTING FILE
            Path source = Paths.get(CaptureManager.dataPath + File.separator + "channel_maps.txt");
            String append = ServiceLauncher.SDF.format(new Date(new File(CaptureManager.dataPath + File.separator + "channel_maps.txt").lastModified()));
            Files.move(source, source.resolveSibling("channel_maps_" + append + ".txt"));
            
            writer = new BufferedWriter(new FileWriter(CaptureManager.dataPath + File.separator + "channel_maps.txt"));         
            // WRITE OUT THE SORTED ITEMS
            for (String item : sortedChannelItems) {
                writer.write(item);
                writer.write("\r\n");
            }
            
            // WRITE OUT ANY ITEMS THAT WERE NOT COVERED IN THE frequency_tuner_priority
            Set<Integer> keys = numberedMapOfCwEpgChannelRow.keySet();
            for (Integer key : keys) {
                CwEpgChannelRow epgChannel = numberedMapOfCwEpgChannelRow.get(key);
                writer.write(epgChannel.getRawLine());
                writer.write("\r\n");
            }
            
        } catch (Exception e) {
            System.out.println("Could not rename the original channel_maps.txt.  Aborting the sort. " + e.getMessage());
        } finally {
            if (writer != null) try {writer.close();} catch (Exception e) {}
        }

        returnMessage.replace(0, returnMessage.length(), "Successfully sorted the channel_maps.txt file using frequency_tuner_priority.txt " + "There were " + priorityList.size() + " priority rows used and " + sortedChannelItems.size() + " items were affected and " + numberedMapOfCwEpgChannelRow.size() + " items unaffected (put at the end)");
        System.out.println(returnMessage.toString());
        return returnMessage.toString();
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
        Channel channelDigital = new ChannelDigital(channelName, tuner, 20000.0, protocol);
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
            Channel aChannel = tuner.lineUp.getChannel(channelName, 1, protocol, tuner.getFullName());
            if (aChannel == null){
                this.lastReason += " channel " + channelName + " " + protocol + " not found on " + tuner.getFullName() + " lineup.<br>"; //channel 29.1 null not found on 10123716-0 lineup.
                continue;
            }
            Capture trialCapture = null;
            //System.out.println("tuner type:" + tuner.getType() + " (should be " + Tuner.HDHR_TYPE + " or " + Tuner.FUSION_TYPE + ")");
            if (tuner.getType() == Tuner.HDHR_TYPE && CaptureManager.useHdhrCommandLine){
                trialCapture = new CaptureHdhr(slot, aChannel);
            } else if (tuner.getType() == Tuner.HDHR_TYPE && !CaptureManager.useHdhrCommandLine){ // DRS 20220707 - Split processing between traditional and http.
                trialCapture = new CaptureHdhrHttp(slot, aChannel);
            } else if (tuner.getType() == Tuner.FUSION_TYPE){
                trialCapture = new CaptureFusion(slot, aChannel, false);
            } else if (tuner.getType() == Tuner.EXTERNAL_TYPE){
                trialCapture = new CaptureExternalTuner(slot, aChannel);
            }
            Double priority = new Double(aChannel.priority + Math.random()); // prevent identical priorities from getting lost 
            if (trialCapture != null && tuner.available(trialCapture, true, false)){
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
    
    public List<Capture> getAvailableCapturesAltChannelListAndSlot(Channel[] altChannels, Slot slot, String protocol, boolean debug) {
        if (debug) {
            System.out.println(new Date() + " altChannels size " + altChannels.length);
            System.out.println(new Date() + " slot " + slot);
            System.out.println(new Date() + " protocol " + protocol);
        }
        if (slot.isInThePast()){
            lastReason = new Date() + " slot in the past. " + slot;
            return new ArrayList<Capture>();
        }
        
        TreeMap<Double, Capture> prioritizedCaptures = new TreeMap<Double, Capture>();
        for (Channel aChannel : altChannels) {
            if (aChannel == null) continue;
            Capture trialCapture = null;
            if (aChannel.tuner.getType() == Tuner.HDHR_TYPE){
                trialCapture = new CaptureHdhr(slot, aChannel);
            } else if (aChannel.tuner.getType() == Tuner.FUSION_TYPE){
                trialCapture = new CaptureFusion(slot, aChannel, false);
            } else if (aChannel.tuner.getType() == Tuner.EXTERNAL_TYPE){
                trialCapture = new CaptureExternalTuner(slot, aChannel);
            }
            Double priority = new Double(aChannel.priority + Math.random()); // prevent identical priorities from getting lost 
            if (trialCapture != null && aChannel.tuner.available(trialCapture, true, false)){
                prioritizedCaptures.put(priority, trialCapture);
            } else {
                this.lastReason += "<br>tuner " + aChannel.tuner.getFullName() + " not available for " + trialCapture;
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
            System.err.println(this.getWebCapturesList(false));
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
        List<Capture> captures = getCaptures();
        int i = 0;
        boolean found = false;
        // DRS 20220611 - Change iteration to not involve tuners.
        for (Iterator<Capture> iter = captures.iterator(); iter.hasNext(); i++) {
            Capture capture = iter.next();
            if (capture != null){
                if (i == seq) {
                    foundCapture = captures.get(i);
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
    
    //DRS 20210319 - Added method 
    public Channel getRandomChannel() {
        Collection<Channel> channels = getAllChannels(false);
        int itemNumber = (int) Math.random() * channels.size();
        int counter = 0;
        for (Channel channel : channels) {
            if (counter++ == itemNumber) return channel;
        }
        System.out.println("ERROR:  returning first channel instead of random one.");
        return channels.iterator().next();
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
	
	public ArrayList<String> getSameRfChannels(String tunerNameString, String channelKey) {
	    ArrayList<String> channelKeys = new ArrayList<String>();
        for (Iterator<Tuner> iter = this.iterator(); iter.hasNext();) {
            Tuner tuner = iter.next();
            if (tuner instanceof TunerHdhr) {
                if (tuner.getFullName().equals(tunerNameString)) {
                    channelKeys.addAll(((LineUpHdhr)tuner.lineUp).getSameRfChannelKeys(channelKey));
                    //System.out.println("channelKeys last item was " + channelKeys.get(channelKeys.size() - 1));
                    break;
                } 
                
            }
        }
        return channelKeys;
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
			System.out.println(channelDigital.getXml());
		}
	}
	
	// DRS 20200305 - Changed method name (to clarify what it was really doing)
    public Channel getChannelByChannelKey(String channelKey) {
        if (channelKey == null) return null;
        channelKey = channelKey.trim();
        Collection<Channel> channels = this.getAllChannels(false);
        for (Iterator<Channel> iter = channels.iterator(); iter.hasNext();) {
            Channel channelDigital = iter.next();
            String matchChannel = channelDigital.channelKey;
            if (matchChannel.equals(channelKey)) {
                return channelDigital;
            }
        }
        return null;
    }
    
    public ArrayList<Channel> getChannelsByName(String channelName) {
        ArrayList<Channel> channelList = new ArrayList<Channel>();
        if (channelName == null) return channelList;
        channelName = channelName.trim();
        Collection<Channel> channels = this.getAllChannels(false);
        for (Iterator<Channel> iter = channels.iterator(); iter.hasNext();) {
            Channel channelDigital = iter.next();
            String matchChannel = channelDigital.getCleanedChannelName();
            if (matchChannel.equals(channelName)) {
                channelList.add(channelDigital);
            }
        }
        return channelList;
    }
    
    public Channel getChannelByName(String channelName) {
        if (channelName == null) return null;
        channelName = channelName.trim();
        Collection<Channel> channels = this.getAllChannels(false);
        for (Iterator<Channel> iter = channels.iterator(); iter.hasNext();) {
            Channel channelDigital = iter.next();
            String matchChannel = channelDigital.getCleanedChannelName();
            if (matchChannel.equals(channelName)) {
                return channelDigital;
            }
        }
        return null;
    }
    

    public Channel getChannelByVirtual(String channelVirtual) {
        if (channelVirtual == null) return null;
        channelVirtual = channelVirtual.trim();
        Collection<Channel> channels = this.getAllChannels(false);
        for (Iterator<Channel> iter = channels.iterator(); iter.hasNext();) {
            Channel channelDigital = iter.next();
            String matchChannel = channelDigital.getFullVirtualChannel(channelDigital.virtualHandlingRequired);
            if (matchChannel.equals(channelVirtual)) {
                return channelDigital;
            }
        }
        return null;
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
    
    public ArrayList<Tuner> getTuners(int tunerType) {
        ArrayList<Tuner> tuners = new ArrayList<Tuner>();
        for (Iterator<Tuner> iter = this.iterator(); iter.hasNext();){
            Tuner tuner = iter.next();
            if (tuner.tunerType == Tuner.HDHR_TYPE) {
                tuners.add(tuner);
            }
        }
        return tuners;
    }
    
    public String getTunerListAsOptions() {
        StringBuffer buf = new StringBuffer();
        for (Iterator<Tuner> iter = this.iterator(); iter.hasNext();){
            Tuner tuner = iter.next();
            buf.append("<option value=\"");
            buf.append(tuner.getFullName());
            //buf.append(",");
            //buf.append(tuner.getRecordPath(true));
            buf.append("\">");
            buf.append(tuner.getFullName());
            buf.append("</option>\n");
        }
        return new String(buf);
    }
    
    public String getTunerPathAsOptions() {
        StringBuffer buf = new StringBuffer();
        for (Iterator<Tuner> iter = this.iterator(); iter.hasNext();){
            Tuner tuner = iter.next();
            buf.append("tunerPath[\"");
            buf.append(tuner.getFullName());
            buf.append("\"] = \"");
            buf.append(tuner.getRecordPath(true, true));
            buf.append("\";\n");
        }
        return new String(buf);
    }
    
    public String getWebTunerList() {
        return getWebTunerList(0);
    }

    public String getWebTunerList(int lowGbValue) {
        int maxSecondsToWait = 20;
        for (int seconds = maxSecondsToWait; seconds > 0; seconds--) {
            if (TunerManager.mCountingTuners) { // rare path
                try {Thread.sleep(1000);} catch (Exception e) {};
            } else { // expected path
                break;
            }
        }
        TunerManager.mCountingTuners = false; // in case it doesn't reset in the method
        
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
            xmlBuf.append("  <channel " + channel.getXml() + "></channel>\n"); // DRS 20200305 - Changed xml to close channel explicitly
        }
        buf.append("</table>\n");
        xmlBuf.append("</xml>\n");
        return new String(buf) + new String(xmlBuf);
    }
    
    // <option value="12.3">11.1 KNTV HD</option>
    public String getChannelListAsOptions() {
        StringBuffer buf = new StringBuffer();
        Collection<Channel> allChannels = getAllChannels(false, false);
        for (Iterator<Channel> iter = allChannels.iterator(); iter.hasNext();) {
            Channel channel = iter.next();
            buf.append(channel.getHtmlOptions());
        }
        return new String(buf);
    }
    

    /*
channelList["1075D4B1-0"] = '<select id="channel"> '
+'<option value="11.1">11.1 KNTV HD</option>'
+'</select>';

     */
    public String getChannelListByTunerAsOptions() {
        StringBuffer buf = new StringBuffer();
        for (Iterator<Tuner> iter = this.iterator(); iter.hasNext();){
            Tuner tuner = iter.next();
            //channelList["1075D4B1-0"] = '<select id="channel"> '
            buf.append("channelList[\"");
            buf.append(tuner.getFullName());
            buf.append("\"] = '<select id=\"channel\"> '\n");

            Map<String, Channel> channelMap = tuner.lineUp.channels;
            Set<String> channelNames = channelMap.keySet();
            for (String channelName : channelNames) {
                
                //+'<option value="11.1">
                buf.append("+'<option value=\"");
                buf.append(channelMap.get(channelName).getCleanedChannelName());
                buf.append("\">");
                
                // 11.1 KNTV HD</option>
                buf.append(channelMap.get(channelName).channelDescription);
                buf.append("</option>'\n");
            }
            buf.append("+'<select>';\n");
        }
        return new String(buf);
    }    
    
    // channelName="12.3" alphaDescription="KNTV HD" input="1" protocol="8vsb" 
    // favorite="false" sourceId="" tuner="103AEA6C-0" deviceId="272296556" 
    // tunerType="2" channelDescription="11.1 KNTV HD" channelVirtual="11.1"

    // <option value="103AEA6C-0,C:\Users\Public\VAP\Monitored\,12.3">103AEA6C-0: 11.1 KNTV HD</option> 
    public String getChannelTunerListAsOptions() {
        StringBuffer buf = new StringBuffer();
        //<option value="103AEA6C-0,C:\Users\Public\VAP\Monitored\">103AEA6C-0</option>
        for (Iterator<Tuner> iter = this.iterator(); iter.hasNext();){
            Tuner tuner = iter.next();
            Map<String, Channel> channelMap = tuner.lineUp.channels;
            Set<String> channelNames = channelMap.keySet();
            for (String channelName : channelNames) {
                
                // <option value="103AEA6C-0,
                buf.append("<option value=\"");
                buf.append(tuner.getFullName());
                buf.append(",");
                
                // C:\Users\Public\VAP\Monitored\,
                buf.append(tuner.getRecordPath(true, false));
                buf.append(",");
    
                // 12.3">
                buf.append(channelMap.get(channelName).getCleanedChannelName());
                buf.append("\">");
                
                // 103AEA6C-0: 
                buf.append(tuner.getFullName());
                buf.append(": ");
                
                
                // 11.1 KNTV HD</option>
                buf.append(channelMap.get(channelName).channelDescription);
                buf.append("</option>\n");
            }
        }
        
        
        /*
         * 
         *  buf.append("<option value=\"44.3\">2-1</option>\n");
            buf.append("<option value=\"");
            buf.append(this.getCleanedChannelName());
            buf.append("\">");
            buf.append(this.channelDescription);
            buf.append("</option>\n");

         * 
         */
        return new String(buf);
    }

    public String getWebCapturesList(boolean rowsOnly){
        boolean allFields = !rowsOnly; // if they ask for rowsOnly, then don't give them all fields either
        StringBuffer buf = new StringBuffer("<table border=\"1\">\n");
        StringBuffer xmlBuf = new StringBuffer("\n<xml id=\"captures\">\n");
        if (rowsOnly) buf  = new StringBuffer();
        List<Capture> captures = getCaptures();
        int i = 0;
        for (Iterator<Capture> iter = captures.iterator(); iter.hasNext(); i++) {
            Capture capture = iter.next();
            if (capture != null){
                buf.append(capture.getHtml(i, allFields));
                xmlBuf.append(capture.getXml(i));
            }
        }
        if (rowsOnly) return new String(buf);
        buf.append("</table>\n");
        xmlBuf.append("</xml>\n");
        return new String(buf) + new String(xmlBuf);
    }
    
    public String getScheduledSequenceNumbersAsOptions() {
        StringBuffer buf = new StringBuffer();
        List<Capture> captures = getCaptures();
        int i = 0;
        for (Iterator<Capture> iter = captures.iterator(); iter.hasNext(); i++) {
            Capture capture = iter.next();
            if (capture != null){
                buf.append("<option value=\"" + i + "\">" + i + "</option>");
            }
        }
        buf.append("</select>\n");
        return buf.toString();
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
    
    public static void main(String[] args) throws Exception {
        boolean testIpMap = true;
        if (testIpMap) {
            String fileDiscoverText = "hdhomerun device 1076C3A7 found at 169.254.3.188\nhdhomerun device 1075D4B1 found at 192.168.1.16\nhdhomerun device 1080F19F found at 192.168.1.18";
            fileDiscoverText = "";
            String liveDiscoverText = "hdhomerun device 1076C3A7 found at 169.254.217.171\nhdhomerun device 1080F19F found at 192.168.1.18\nhdhomerun device 1075D4B1 found at 192.168.1.16";
            HashMap<String, String> ipMap = getIpMap(fileDiscoverText, liveDiscoverText);
            Set keys = ipMap.keySet();
            for (Object object : keys) {
                System.out.println(object + " " + ipMap.get(object));
            }
        }
        
        boolean testChannelSort = false;
        if (testChannelSort) {
            TunerManager.skipFusionInit = true;
            TunerManager.skipRegistryForTesting = true;
            CaptureManager cm = CaptureManager.getInstance("C:\\my\\dev\\eclipsewrk\\CwHelper\\","C:\\Program Files\\Silicondust\\HDHomeRun\\", "C:\\my\\dev\\eclipsewrk\\CwHelper\\"); // runs tunerManager.countTuners() here, which runs LineupHdhr.scan()
            System.out.println("cm " + cm);
            String responseToSort = sortChannelMapUsingSignalPriorityFile();
            System.out.println(responseToSort);
        }
        
        
        boolean testAltChannels = false;
        if (testAltChannels) {
            System.out.println(">>>>>>>>>>>>>>>>> THIS NEVER WORKED <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");
            CaptureManager cm = CaptureManager.getInstance("C:\\my\\dev\\eclipsewrk\\CwHelper","C:\\Program Files\\Silicondust\\HDHomeRun\\", "C:\\my\\dev\\eclipsewrk\\CwHelper"); // runs tunerManager.countTuners() here, which runs LineupHdhr.scan()
            System.out.println("cm " + cm); 
            TunerManager tm = TunerManager.getInstance();
            List<Tuner> hdhrTunerList = tm.countTunersHdhr(true);
            Thread.sleep(1000);
            for (Tuner tuner : hdhrTunerList) {
                Collection<Channel> channelList = tm.getAllChannels(false);
                System.out.println("Channel count + " + channelList.size() + " <<< this doesn't even use tuner!! " + tuner.id);
            }
            
            String alternateChannels = "58.3^42.1,18.2^64.3";
            tm.addAltChannels(alternateChannels);
        }
        
        boolean testIp = false;
        if (testIp) {
            CaptureManager.hdhrPath = "C:\\Program Files\\Silicondust\\HDHomeRun\\";
            TunerManager tm = TunerManager.getInstance();
            List<Tuner> hdhrTunerList = tm.countTunersHdhr(true);
            Thread.sleep(1000);
            for (Tuner tuner : hdhrTunerList) {
                System.out.println("tuner: " + tuner.id + " tuner IP: " + ((TunerHdhr)tuner).ipAddressTuner);
            }
            System.out.println("tuner list as options\n" + tunerManager.getTunerListAsOptions());
            System.out.println("scheduled list as options\n" + tunerManager.getWebCapturesList(true));
            System.out.println("scheduled sequence numbers as options\n" + tunerManager.getScheduledSequenceNumbersAsOptions());
            System.out.println("channel list by tuner as options\n" + tunerManager.getChannelListByTunerAsOptions());
            System.out.println("tunerPathAsOptions\n" + tunerManager.getTunerPathAsOptions());
        }
        
        
        boolean testThing = false;
        if (testThing) {
            TunerManager tm = TunerManager.getInstance();
            //tm.countTuners();
            ArrayList<String> liveMap = new ArrayList<String>();
            liveMap.add("1010CC54");
            liveMap.add("1013FADA");
            HashMap<String, String> ipAddressMap = new HashMap<String, String>();
            HashMap<String, Integer> liveModelMap = tm.getLiveModelMap(liveMap, 1, 5);
            for (String key : liveModelMap.keySet()){
                System.out.println("key: " + key + " value: " + liveModelMap.get(key));
            }
            
            //System.out.println("channel list as options\n" + tm.getChannelListAsOptions());
            System.out.println("tuner list as options\n" + tunerManager.getTunerListAsOptions());
            System.out.println("scheduled list as options\n" + tunerManager.getWebCapturesList(true));
            System.out.println("scheduled sequence numbers as options\n" + tunerManager.getScheduledSequenceNumbersAsOptions());
            System.out.println("channel list by tuner as options\n" + tunerManager.getChannelListByTunerAsOptions());
            System.out.println("tunerPathAsOptions\n" + tunerManager.getTunerPathAsOptions());
        }
    }

}
