/*
 * Created on Feb 27, 2010
 *
 */
package org.cwepg.reg;

import java.util.Date;
import java.util.Iterator;
import java.util.Map;

public class FusionRegistryEntry {

    private int zuluUiNumber;
    private String deviceName = "Fusion";
    private boolean isDefaultName = true;
    private int analogFileExtensionNumber;
    private String recordPath;
    private boolean isUsb;

    public FusionRegistryEntry(int zuluUiNumber) {
        this.zuluUiNumber = zuluUiNumber;
    }

    public void setNameUsingKey(Map<String, String> names) {
        for (Iterator iterator = names.keySet().iterator(); iterator.hasNext();) {
            String uinumber = (String) iterator.next();
            String name = (String) names.get(uinumber);
            if (uinumber.equals("" + this.zuluUiNumber) && name != null){
                this.deviceName = name;
                this.isDefaultName = false;
                break;
            }
        }
    }

    public void setRecordPathUsingKey(Map<String, String> recordPathsByNumber, Map<String, String> recordPathsByName) {
        this.recordPath = (String)recordPathsByNumber.get("" + this.zuluUiNumber);
        if (this.recordPath == null) this.recordPath = (String)recordPathsByName.get(this.deviceName);
        if (this.recordPath == null){
            System.out.println(new Date() + " WARNING: Software\\Dvico\\ZuluHDTV\\Data or Data\\DeviceN did not have a good RecordPath for UINumber " + this.zuluUiNumber + ", nor for ModelName " + this.deviceName + ", so using c:\\.");
            this.recordPath = "c:\\";
        }
    }

    public String getRecordPath() {
        return this.recordPath;
    }
    
    public String getZuluNumberString() {
        return "" + this.zuluUiNumber;
    }
    
    public int getZuluNumber(){
        return this.zuluUiNumber;
    }

    public String getDeviceName() {
        return this.deviceName;
    }

    public void setAnalogFileExtensionNumber(int analogFileExtensionNumber) {
        this.analogFileExtensionNumber = analogFileExtensionNumber;
    }

    public int getAnalogFileExtensionNumber() {
        return this.analogFileExtensionNumber;
    }


    public void setIsUsb(String deviceDescEntry) {
        if (deviceDescEntry == null) return;
        if (deviceDescEntry.toUpperCase().indexOf("USB") > -1){
            this.isUsb = true;
            if (this.isDefaultName) this.deviceName = "FusionUSB";
        }
    }

    public String toString(){
        return "zuluUiNumber: " + this.zuluUiNumber + 
                "  deviceName:" + this.deviceName + 
                "  isDefaultName: " + this.isDefaultName + 
                "  analogFileExtensionNumber: " + this.analogFileExtensionNumber + 
                "  recordpath: " + this.recordPath + 
                "  isUsb: " + this.isUsb;
    }

}
