/*
 * Created on Sep 7, 2009
 *
 */
package org.cwepg.reg;

import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.cwepg.hr.Channel;
import org.cwepg.hr.ChannelAnalog;
import org.cwepg.hr.Tuner;

public class RegistryHelperFusion {

    public static final String topKey = "HKEY_CURRENT_USER";
    public static final String fusionBranch = "Software\\Dvico\\ZuluHDTV\\Data";
    private static int debug = 0;

    public static ArrayList getChannels(Tuner tuner) {
        return getChannelsFusion(tuner);
    }

    public static String getInstalledLocation() {
        String result = null;
        try {
            result = Registry.getStringValue(topKey, fusionBranch, "SetupDir");
        } catch (UnsupportedEncodingException e) {
            System.out.println(new Date() + " ERROR: No installed location for Fusion");
        }
        return result;
    }
    
    private static ArrayList getChannelsFusion(Tuner tuner) {
        ArrayList<Channel> list = new ArrayList<Channel>();
        ArrayList<String> digitalsSubChannelKeys = new ArrayList<String>();
        //ArrayList<String> activeTopKeys = new ArrayList<String>();
        byte[] data = null;
        try {
            Map map = Registry.getValues(topKey, fusionBranch);
            Set aSet = map.keySet();
            for (Iterator iter = aSet.iterator(); iter.hasNext();) {
                String regKey = (String) iter.next();
                if (regKey.endsWith("SubChannels")) {
                    digitalsSubChannelKeys.add(regKey);
                } else if ((regKey.indexOf("AirChannel") > -1 || regKey.indexOf("CatvChannel") > -1)) {
                    if (!regKey.endsWith("D")){
                        if (map.get(regKey) instanceof String) {
                            String alphaDescription = (String) map.get(regKey);
                            addChannelAnalogFusion(alphaDescription, list, tuner, regKey, map);
                        }
                    } else {
                        //activeTopKeys.add(regKey);
                    }
                }
            }
            for (String subchannelKey : digitalsSubChannelKeys) {
                //String derivedTopKey = getDerivedTopKey(subchannelKey);
                //if (activeTopKeys.contains(derivedTopKey)){
                    data = (byte[])map.get(subchannelKey);
                    addChannelDigitalFusion(data, list, tuner, subchannelKey);
                //}
            }
        } catch (Exception e) {
            System.out.println(new Date() + " " + e.getMessage());
            System.err.println(new Date() + " " + e.getMessage());
            e.printStackTrace();
        }
        return list;
    }

    /*
    private static String getDerivedTopKey(String subchannelKey) {
        int rfLoc = subchannelKey.toUpperCase().indexOf("RF");
        int subLoc = subchannelKey.indexOf("Sub");
        int channelsLoc = subchannelKey.indexOf("Channels");
        if (rfLoc < 0 || subLoc < 0 || channelsLoc < 0) {
            System.out.println(new Date() + " ERROR: Unexpected data sent to getDerivedTopKey() " + subchannelKey);
            return null;
        }
        int channelNumber = -1;
        String channelNumberString = subchannelKey.substring(rfLoc + 2, rfLoc + 5);
        try {channelNumber = Integer.parseInt(channelNumberString);} catch (Exception e){
            System.out.println(new Date() + " ERROR: Unexpected data sent to getDerivedTopKey() Could not parse number. " + channelNumberString + " [" + subchannelKey + "]");
            return null;
        };
        return subchannelKey.substring(0,rfLoc) + subchannelKey.substring(channelsLoc,channelsLoc + 7 ) + channelNumber + "D";
    }
    */

    private static void addChannelDigitalFusion(byte[] data, ArrayList<Channel> list, Tuner tuner, String regKey)
            throws Exception {
        int rfLoc = regKey.toUpperCase().indexOf("RF");
        int rf = Integer.parseInt(regKey.substring(rfLoc + 2, rfLoc + 5));
        int subchannelCount = RegValueConverter.getIntFromBinary(data, 0, 4);
        // System.out.println("there were " + subchannelCount + " subchannels");
        for (int i = 0; i < subchannelCount; i++) {
            int base = 122 * i;
            String description = RegValueConverter.getStringFromBinary(data, base + 4);
            int virm = RegValueConverter.getIntFromBinary(data, base + 44, 2);
            int virs = RegValueConverter.getIntFromBinary(data, base + 46, 2);
            int pid = RegValueConverter.getIntFromBinary(data, base + 48, 2);
            int protocol = RegValueConverter.getIntFromBinary(data, base + 50, 1);
            int encryption = RegValueConverter.getIntFromBinary(data, base + 56, 2);
            if (encryption == 0) {
                if (tuner == null){ // for testing only
                    System.out.println("description: " + description + " protocol: " + protocol + " rf: " + rf + " virm: " + virm + " virs: " + virs + " pid: " + pid);
                } else {
                    list.add(new Channel(description, tuner, protocol, 0, rf, virm, virs, 0, pid, 1, -1));
                }
            }
        }
    }

    private static void addChannelAnalogFusion(String description, ArrayList<Channel> list, Tuner tuner, String regKey, Map map) {
        int channelLoc = regKey.indexOf("Channel");
        int rf = Integer.parseInt(regKey.substring(channelLoc + 7));
        int virm = 0;
        String airCat = regKey.substring(0,3);
        String protocol = "analogAir";
        if ("Cat".equals(airCat)) protocol = "analogCable";
        String virtRegKey = "VirtualChannelFor" + regKey.substring(0, channelLoc) + rf;
        if (map.get(virtRegKey) != null) {
            virm = (Integer) map.get(virtRegKey);
        }
        list.add(new ChannelAnalog(description, tuner, protocol, rf, virm, airCat, 1));
    }

    public static Map getFusionRegistryEntries(String controlSetName) {
        Map<String, FusionRegistryEntry> fusionRegistryEntries = new TreeMap<String, FusionRegistryEntry>();
        try {
            String[] services = Registry.getSubKeys("HKEY_LOCAL_MACHINE", "System\\" + controlSetName + "\\Services");
            for (int i = 0; i < services.length; i++){
                String displayNameEntry = Registry.getStringValue("HKEY_LOCAL_MACHINE", "System\\" + controlSetName + "\\Services\\" + services[i], "DisplayName");
                if (displayNameEntry != null && displayNameEntry.indexOf("FusionHDTV") > -1){
                    int countEntry = Registry.getIntValue("HKEY_LOCAL_MACHINE", "System\\" + controlSetName + "\\Services\\" + services[i] + "\\Enum", "Count");
                    if (debug > 0) System.out.println("System\\" + controlSetName + "\\Services\\" + services[i] + "\\Enum\\Count=" + countEntry);
                    for (int currentCount = 0; currentCount < countEntry; currentCount++){
                        String currentCountEntry = Registry.getStringValue("HKEY_LOCAL_MACHINE", "System\\" + controlSetName + "\\Services\\" + services[i] + "\\Enum", "" + currentCount);
                        if (debug > 0) System.out.println("System\\" + controlSetName + "\\Services\\" + services[i] + "\\Enum\\" + currentCount + "=" + currentCountEntry);
                        String driverEntry = Registry.getStringValue("HKEY_LOCAL_MACHINE", "SYSTEM\\" + controlSetName + "\\Enum\\" + currentCountEntry, "Driver");
                        if (debug > 0) System.out.println("SYSTEM\\" + controlSetName + "\\Enum\\" + currentCountEntry +  "\\Driver=" + driverEntry);
                        String deviceDescEntry = Registry.getStringValue("HKEY_LOCAL_MACHINE", "SYSTEM\\" + controlSetName + "\\Enum\\" + currentCountEntry, "DeviceDesc");
                        if (debug > 0) System.out.println("SYSTEM\\" + controlSetName + "\\Enum\\" + currentCountEntry +  "\\DeviceDesc=" + deviceDescEntry);
                        if (Registry.valueExists("HKEY_LOCAL_MACHINE", "SYSTEM\\" + controlSetName + "\\Control\\Class\\" + driverEntry + "\\DriverData", "ZuluUINumber")){
                            int aZuluNumber = Registry.getIntValue("HKEY_LOCAL_MACHINE", "SYSTEM\\" + controlSetName + "\\Control\\Class\\" + driverEntry + "\\DriverData", "ZuluUINumber");
                            if (debug > 0) System.out.println("SYSTEM\\" + controlSetName + "\\Control\\Class\\" + driverEntry + "\\DriverData\\ZuluUINumber=" + aZuluNumber);
                            FusionRegistryEntry aFusionRegistryEntry = fusionRegistryEntries.get("" + aZuluNumber);
                            if ( aFusionRegistryEntry == null){
                                aFusionRegistryEntry = new FusionRegistryEntry(aZuluNumber);
                                fusionRegistryEntries.put("" + aZuluNumber, aFusionRegistryEntry);
                            }
                            aFusionRegistryEntry.setIsUsb(deviceDescEntry);
                        } else {
                            if (debug > 0) System.out.println("SYSTEM\\" + controlSetName + "\\Control\\Class\\" + driverEntry + "\\DriverData\\ZuluUINumber=(not available)");
                        }
                        if (Registry.valueExists("HKEY_LOCAL_MACHINE", "SYSTEM\\" + controlSetName + "\\Control\\Class\\" + driverEntry + "\\DriverData", "ZuluUINumber2")){
                            int aZuluNumber = Registry.getIntValue("HKEY_LOCAL_MACHINE", "SYSTEM\\" + controlSetName + "\\Control\\Class\\" + driverEntry + "\\DriverData", "ZuluUINumber2");
                            if (debug > 0) System.out.println("SYSTEM\\" + controlSetName + "\\Control\\Class\\" + driverEntry + "\\DriverData\\ZuluUINumber2=" + aZuluNumber);
                            FusionRegistryEntry aFusionRegistryEntry = fusionRegistryEntries.get("" + aZuluNumber);
                            if ( aFusionRegistryEntry == null){
                                aFusionRegistryEntry = new FusionRegistryEntry(aZuluNumber);
                                fusionRegistryEntries.put("" + aZuluNumber, aFusionRegistryEntry);
                            }
                            aFusionRegistryEntry.setIsUsb(deviceDescEntry);
                        } else {
                            if (debug > 0) System.out.println("SYSTEM\\" + controlSetName + "\\Control\\Class\\" + driverEntry + "\\DriverData\\ZuluUINumber2=(not available)");
                        }
                        if (debug > 0) System.out.println(new Date() + " There is/are " + fusionRegistryEntries.size() + " valid zulu number(s) so far.");
                    }
                }
            }
            try {
                String localMachineName = "myTvMachine";
                InetAddress localHost = InetAddress.getLocalHost();
                localMachineName = localHost.getHostName();
                if(localMachineName.equals("VostroDRS")){
                    FusionRegistryEntry aFusionRegistryEntry = new FusionRegistryEntry(5);
                    fusionRegistryEntries.put("" + 5, aFusionRegistryEntry);
                    aFusionRegistryEntry = new FusionRegistryEntry(8960);
                    fusionRegistryEntries.put("" + 8960, aFusionRegistryEntry);
                }
            } catch (Throwable t){}

        } catch (UnsupportedEncodingException e) {
            System.out.println(new Date() + " ERROR: getFusionRegistryEntries " + e.getMessage());
            System.err.println(new Date() + " ERROR: getFusionRegistryEntries " + e.getMessage());
            e.printStackTrace();
        }
        return fusionRegistryEntries;
    }
    
    public static void main(String[] args) {
        System.out.println("hello!");
        RegistryHelperFusion.getChannelsFusion(null);
    }
}
