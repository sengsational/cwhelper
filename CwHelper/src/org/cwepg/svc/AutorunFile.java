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

    private static final String TEMPLATE = CaptureManager.dataPath + "AutorunTemplate.xml";
    private static final String[] MATCHES = {"[yesterdaysDate]","[scheduledRunTime]","[maximumRunMinutes]","[executableDirectory]","[executable]","[autorunParameters]"};
    private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd");
    private static final SimpleDateFormat STF = new SimpleDateFormat("HH:mm:ss");
    
    private String[] values = new String[6];
    
    public AutorunFile(String hourToSend, String minuteToSend, String durationMinutes, String parameters, boolean isMaster, int leadTimeSeconds) {
        Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DAY_OF_MONTH, -1);
        yesterday.set(Calendar.HOUR_OF_DAY, safeToInt(hourToSend, 15));
        yesterday.set(Calendar.MINUTE, safeToInt(minuteToSend, 15));
        yesterday.set(Calendar.SECOND, 0);
        yesterday.add(Calendar.SECOND, -leadTimeSeconds); // //DRS 20151031 - adjust start time to wake up earlier to allow hardware to wake-up
        int durationMinutesHelper = safeToInt(durationMinutes, 5) + (leadTimeSeconds + 30)/60; // DRS 20151031 - Extend duration by leadTime, if any.
        int durationMinutesMaster = (leadTimeSeconds + 30)/60; // DRS 20151101 - Run timeout.exe only long enough to wait for cw_epg.exe to start if master.
        
        if (leadTimeSeconds == 0) {                                             // <<<<  Create a file to support running cw_epg.exe if zero lead time (called for master only)
            values[0] = SDF.format(yesterday.getTime()); //yesterdaysDate
            values[1] = STF.format(yesterday.getTime()); //scheduledRunTime
            values[2] = "" + safeToInt(durationMinutes, 5); //maximumRunMinutes
            values[3] = CaptureManager.cwepgPath; //executableDirectory
            values[4] = "cw_epg.exe"; //executable
            values[5] = parameters.replaceAll("%20", " "); //autorunParameters
        } else {                                                                // <<<<  Create a file to support running timeout.exe through cmd.exe for non-zero lead time (master or helper)
            values[0] = SDF.format(yesterday.getTime()); //yesterdaysDate
            values[1] = STF.format(yesterday.getTime()); //scheduledRunTime
            values[2] = "" + (isMaster?durationMinutesMaster:durationMinutesHelper); //maximumRunMinutes
            values[3] = System.getenv("WINDIR") + "\\system32\\"; //executableDirectory
            values[4] = "cmd.exe"; //executable // DRS 20151102 - Changed from timeout.exe to cmd.exe
            values[5] = "/c start \"CW_EPG Starting Automatic Scheduling\" /min timeout /t " + (leadTimeSeconds + 30); //autorunParameters DRS 20151106 - removed (isMaster?0:safeToInt(durationMinutes,5)) * 60
        }
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

    public boolean saveToDisk(String pathFileNameString) {
        BufferedReader in = null;
        StringBuffer outBuf = new StringBuffer();
        boolean goodRead = false;
        boolean successfulSave = false;
        try {
            in = new BufferedReader(new InputStreamReader(new FileInputStream(TEMPLATE)));
            String l = null;
            while ((l = in.readLine()) != null){
                //System.out.println("l  was [" + l + "]");
                if (l.indexOf("<!--") > -1) continue;
                String ll = substitute(l);
                if (l != null && !l.equals("")) outBuf.append(ll + "\r\n");
                if (!l.equals(ll)){
                    //System.out.println("ll was [" + ll + "]");
                }
            }
            goodRead = true;
        } catch (Exception e) {
            File templateFile = new File(TEMPLATE);
            String message = new Date() + " No template file [" + templateFile.getAbsolutePath() + "] found.  No scheduled task created. " + e.getMessage();
            System.out.println(message);
        } finally {
            try {in.close();} catch (Throwable t) {}
        }

        String message = "";
        if (goodRead) {
            BufferedOutputStream out = null;
            try {
                out = new BufferedOutputStream(new FileOutputStream(pathFileNameString));
                out.write(outBuf.toString().getBytes());
                message = new Date() + " Wrote " + pathFileNameString + " successfully.";
                successfulSave = true;
            } catch (Exception e) {
                message = new Date() + " Could not write to [" + pathFileNameString + "].  No scheduled task created. " + e.getMessage();
            } finally {
                try {out.close();} catch (Throwable t) {}
            }
        } else {
            message = new Date() + " No output to " + pathFileNameString + " since there was not a good read of " + TEMPLATE;
        }
        System.out.println(message);
        return successfulSave;
    }

    private String substitute(String l) {
        String ll = l;
        int limit = 5;
        while (ll.indexOf("[") > -1) {
            for (int i = 0; i < MATCHES.length; i++) {
                int pos = ll.indexOf(MATCHES[i]);
                if (pos > -1) {
                    ll = ll.substring(0, pos) + values[i] + ll.substring(pos + MATCHES[i].length());
                }
            }
            if (limit-- < 0) {
                System.out.println(new Date() + " Unexpected substitution problem. " + ll);
                System.out.println(new Date() + " Unexpected substitution problem. " + l);
                break;
            }
        }
        return ll;
    }
    
    public static boolean fileExists() {
        return (new File(CaptureManager.dataPath + "Autorun.xml")).exists();
    }

    public static void main(String[] args) {
        boolean unitTest = true;
        if (unitTest) {
            String hourToSend = "11";
            String minuteToSend = "46";
            String durationMinutes = "10";
            String parameters = "-auto%20-download";
            
            boolean isMaster = false;
            boolean isCreateRequest = false;
            
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
        }
        

        //   /wakeupevent?hourtosend=11&minutetosend=46&durationminutes=4&parameters=-auto%20-download
        /*
        String hourToSend = "11";
        String minuteToSend = "46";
        String durationMinutes = "4";
        String parameters = "-auto%20-download";
        boolean isMaster = true;
        
        AutorunFile aAutorunFile = new AutorunFile(hourToSend, minuteToSend, durationMinutes, parameters, isMaster, 0);

        boolean testSub = false;
        if (testSub) {
            String testString = "XXX[yesterdaysDate]YYY[executable]ZZZ";
            System.out.println(aAutorunFile.substitute(testString));
        }
        
        boolean testAll = true;
        if (testAll) {
            aAutorunFile.saveToDisk(CaptureManager.dataPath + "AutorunMaster.xml");
            isMaster = false;
            new AutorunFile(hourToSend, minuteToSend, durationMinutes, parameters, isMaster, 0).saveToDisk(CaptureManager.dataPath + "AutorunHelper.xml");
        }
        */
    }

}
