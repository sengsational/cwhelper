/*
 * Created on Jun 1, 2015
 *
 */
package org.cwepg.svc;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import org.cwepg.hr.CaptureManager;

public class AutorunFile {

    private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd");
    private static final SimpleDateFormat STF = new SimpleDateFormat("HH:mm:ss");
    
    private String[] values = new String[9];
    private String parameters;
    private String overrideCommand;
    
    public AutorunFile() {
        this("17", "0", "10", "-auto -download -detail", true, CaptureManager.leadTimeSeconds, true, "");
    }
    
    public AutorunFile(String hourToSend, String minuteToSend, String durationMinutes, String parameters, boolean isMaster, int leadTimeSeconds, boolean runCwEpg, String overrideCommand) {
        Date constructorNow = new Date();
        Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DAY_OF_MONTH, -1);
        yesterday.set(Calendar.HOUR_OF_DAY, safeToInt(hourToSend, 15));
        yesterday.set(Calendar.MINUTE, safeToInt(minuteToSend, 15));
        yesterday.set(Calendar.SECOND, 0);
        yesterday.add(Calendar.SECOND, -leadTimeSeconds); 
        int durationMinutesHelper = safeToInt(durationMinutes, 5) + (leadTimeSeconds + 30)/60; 
        int durationMinutesMaster = (leadTimeSeconds + 30)/60; 
        this.parameters = parameters;
        this.overrideCommand = overrideCommand;

        values[0] = SDF.format(yesterday.getTime()); //yesterdaysDate
        values[1] = STF.format(yesterday.getTime()); //scheduledRunTime
        values[7] = SDF.format(constructorNow); //dateNow
        values[8] = STF.format(constructorNow); //timeNow
        if (runCwEpg) {  
            values[2] = "" + safeToInt(durationMinutes, 5); //maximumRunMinutes
            values[3] = CaptureManager.cwepgPath; //executableDirectory
            values[4] = "cw_epg.exe"; //executable
            values[5] = this.parameters.replaceAll("%20", " "); //autorunParameters
            values[6] = "Performs daily update for CW_EPG scheduling (directly starting cw_epg.exe)"; //description
        } else {                                                                // <<<<  Create a file to support running timeout.exe through cmd.exe for non-zero lead time (master or helper)
            values[2] = "" + (isMaster?durationMinutesMaster:durationMinutesHelper); //maximumRunMinutes
            values[3] = System.getenv("WINDIR") + "\\system32\\"; //executableDirectory
            values[4] = "cmd.exe"; //executable // DRS 20151102 - Changed from timeout.exe to cmd.exe
            int secondsForTimeout = leadTimeSeconds;
            if (!isMaster) secondsForTimeout = secondsForTimeout + safeToInt(durationMinutes,5)*60;
            values[5] = "/c \"echo Waiting for CW_EPG commands...&amp;&amp;" + values[3] + "timeout.exe /t " + secondsForTimeout + "&amp;&amp;exit\""; 
            values[6] = "Use timeout.exe to keep awake unattended PC during Autoruns"; //description
        }
    }
    
    public int getMaximumRunSeconds() {
        return safeToInt(values[2], 5) * 60;
    }
    
    public String getExecutableDirectory() {
        return values[3];
    }
    
    public String getExecutable() {
        return values[4];
    }
    
    public String getParameters() {
        return values[5];
    }
    
    public String getOverrideCommand() {
        return overrideCommand;
    }

    private int safeToInt(String intString, int defaultInt) {
        int resultInt = defaultInt;
        try {
            resultInt = Integer.parseInt(intString);
        } catch (Throwable t) {
            System.out.println(new Date() + " Ignoring " + intString + " using default " + defaultInt);
        }
        return resultInt;
    }
    
    public String toString() {
        StringBuffer buf = new StringBuffer("[");
        for(int i = 0; i < values.length; i++) {
            buf.append(values[i]); buf.append("]  [");
        }
        buf.delete(buf.length() - 2, buf.length());
        return buf.toString();
    }

    public static void main(String[] args) {
        boolean unitTest = true;
        if (unitTest) {
            String hourToSend = "11";
            String minuteToSend = "46";
            String durationMinutes = "10";
            String parameters = "-auto%20-download";
            String overrideCommand = "c:\\my_dir\\my.exe -parm -parm"; // the whole thing hard coded, nothing substituted.
            boolean isMaster = false;
            AutorunFile arf = new AutorunFile(hourToSend, minuteToSend, durationMinutes, parameters, isMaster, CaptureManager.leadTimeSeconds, false, overrideCommand);
            System.out.println("output [" + arf + "]");
        }
    }
}
