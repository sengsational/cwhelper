/*
 * Created on Oct 31, 2010
 *
 */
package com.cwepg.test;

import java.awt.MenuItem;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.StringTokenizer;

import org.cwepg.hr.CaptureManager;
import org.cwepg.hr.Slot;
import org.cwepg.hr.WakeupBuffer;

import com.sun.jna.platform.win32.Kernel32;

public class Tester {
    
    public static void main(String[] args) throws InterruptedException, IOException, Exception {
        

        boolean testApplicationHome = false;
        if (testApplicationHome) {
            System.out.println("[" + System.getProperty("application.home") + "]");
        }
        
        boolean testArrayOut = false;
        if (testArrayOut) {
            String[] values = {"string1", "string2", "string 3", "str 4"};
            StringBuffer buf = new StringBuffer("[");
            for(int i = 0; i < values.length; i++) {
                buf.append(values[i]); buf.append("]  [");
            }
            buf.delete(buf.length() - 2, buf.length());
            System.out.println(buf.toString());
    
        }
        boolean testCommandSpaces = false;
        if (testCommandSpaces) {
            String[] values = new String[6];
            values[5] = "/c start \"c:\\program files\\cw_epg\\cw_epg.exe\" \"-auto\" \"-download -wipelog\" -detail";
            StringTokenizer tok = new StringTokenizer(values[5]);
            while (tok.hasMoreTokens()) {
                String parm = tok.nextToken();
                if (parm.startsWith("\"")) {
                    boolean closeQuoteFound = parm.endsWith("\"");
                    while (tok.hasMoreTokens() && !closeQuoteFound) {
                        parm += " " + tok.nextToken();
                        closeQuoteFound = parm.endsWith("\"");
                    }
                    if (parm.endsWith("\"")) {
                        closeQuoteFound = true;
                        try {parm = parm.substring(1, parm.length() - 1);} catch (Throwable t) {}
                    }
                }
                System.out.println("parm [" + parm + "]");
            }
        }
        boolean testDst = false;
        if (testDst) {
            Calendar startCal = Calendar.getInstance(); startCal.clear();startCal.set(Calendar.YEAR, 2021);startCal.set(Calendar.MONDAY, Calendar.NOVEMBER);startCal.set(Calendar.DATE,  6);
            startCal.set(Calendar.HOUR_OF_DAY, 20);
            Calendar endCal = (Calendar) startCal.clone();
            endCal.add(Calendar.HOUR, 1);
            System.out.println("startCal " + startCal.getTime());
            System.out.println("endCal " + endCal.getTime());
            Slot beforeTimeChange = new Slot(startCal, endCal);
            startCal.add(Calendar.DAY_OF_MONTH, 1);
            endCal.add(Calendar.DAY_OF_MONTH, 1);
            Slot afterTimeChange = new Slot(startCal, endCal);
            System.out.println("Before Time Change " + beforeTimeChange);
            System.out.println("After  Time Change " + afterTimeChange);
            long msToFirstSlot = beforeTimeChange.getMsUntilStart();
            long msToSecondSlot = afterTimeChange.getMsUntilStart();
            long msDifference = msToSecondSlot - msToFirstSlot;
            System.out.println("the time difference between the two is " + ((msDifference)/1000/60/60) + " << 25?");                 
            
        }
        
        boolean testdivision = false;
        if (testdivision) {
            long nowlong = new Date().getTime();
            long nowlongSec = Math.round(nowlong/1000d);
            System.out.println(" nowlong " + nowlong + " nowlongsec "+ nowlongSec);
        }
        
        boolean testThreads = false;
        if (testThreads) {
            /*
             * NOT USED ANY MORE...Can't call SetThreadExecutinonState from anywhere except the main thread or it doesn't work.
             */
            WakeupBuffer bufferInstance = WakeupBuffer.xGetInstance();
            // Main use-case...an allow occurs followed shortly thereafter by a prevent
            WakeupBuffer.xallowSleep();
            System.out.println("sleeping 1");
            Thread.sleep(1000);
            WakeupBuffer.xpreventSleep();
            System.out.println("hang on to reference: " + bufferInstance.toString());
        }
        
        
        boolean whyCantIRemeber = false;
        if (whyCantIRemeber) {
            long msLong = 20;
            System.out.println(" divided by " + (msLong / 3));
            
            System.out.println(" divided by " + (msLong / 3F));
            // DRS 20201029 - Rewrote this code to keep machines from nodding-off
            long LEAD_TIME_MS = 2000;
            int leadTimeSeconds = 90;
            int msUntilNextEvent = 107000;
            long msFirstSleep = Long.MAX_VALUE;
            long msSecondSleep = Long.MAX_VALUE;
            long msUntilEvent = msUntilNextEvent - LEAD_TIME_MS; // Can be negative
            long msInitialSubtraction = msUntilEvent - (leadTimeSeconds * 1000); // Can be negative
            if (msUntilEvent < 0) { // The start time is in the past - no sleeping required
                msFirstSleep = 0;
                msSecondSleep = 0;
            } else if (msInitialSubtraction < 0) { // The start time is within the lead time - sleep less than the lead time
                msFirstSleep = 0;
                msSecondSleep = msUntilEvent;
            } else { // The start time is in the future - sleep twice, where first sleep allows machine to nod-off, and the second where we keep machine active
                msFirstSleep = msInitialSubtraction;
                msSecondSleep = leadTimeSeconds * 1000; // no change
            }
            System.out.println("msFirstSleep " + msFirstSleep + " " + msFirstSleep/1000F );
            System.out.println("msSecondSleep " + msSecondSleep + " " + msSecondSleep/1000F );
        }
        
        boolean testReplace = false;
        if (testReplace) {
            String windowsAppsPublisherId = "";
            String cwepgfolder = "C:\\Program Files\\WindowsApps\\57870CliffWatsonEPGTeam.CliffWatsonEPGProgram_4.5.21.0_x86__mc88n03vh6xa2\\";
            if (cwepgfolder!=null && cwepgfolder.contains("WindowsApps")) {
                System.out.println("[" + cwepgfolder + "]");
                int lastUnderscoreLoc = cwepgfolder.lastIndexOf("_"); if (lastUnderscoreLoc == -1 || cwepgfolder.endsWith("_")) throw new Exception ("No underscore or ends in underscore " + cwepgfolder);
                windowsAppsPublisherId = cwepgfolder.substring(lastUnderscoreLoc + 1); // might have trailing character
                int locBackslash = windowsAppsPublisherId.indexOf("\\");
                System.out.println("locBackslash "  + locBackslash);
                windowsAppsPublisherId = windowsAppsPublisherId.replaceAll("\\\\", "");
                System.out.println("[" + windowsAppsPublisherId + "]");
            }
        }
        
        
        boolean testErrorStack = false;
        if (testErrorStack) {
            String[] whoarray = {"first", "second", "third"};
            String[] interrupterList = new String[30];

            for (int j = 0; j < whoarray.length; j++) {
                String thisGuy = new Date() + " " + whoarray[j];
                // If this guy has been here before, never mind.        
                // if (CaptureManager.interrupterList[0].startsWith(thisGuy)) return;
                
                // Lets keep track of who's calling interrupts on us
                for (int i = interrupterList.length - 2; i >= 0 ; i--) {
                    System.out.println("i is " + i);
                   interrupterList[i + 1] = interrupterList[i];
                }
                interrupterList[0] = thisGuy;

                for (int k = 0; k < interrupterList.length; k++){
                    System.out.println("interrupterList: " + interrupterList[k]);
                }
                System.out.println("--------------------");
            }
            

            
        }
        
        boolean testRc = false;
        if (testRc) {
            int continuous = Kernel32.ES_CONTINUOUS;
            int systemRequired = Kernel32.ES_SYSTEM_REQUIRED;
            
            System.out.println("systemRequired: " + String.format("0x%08x", systemRequired));
            System.out.println("continuous: " + String.format("0x%08x", continuous));

            System.out.println("systemRequired: 0x" + Integer.toHexString(systemRequired & 0xFFFFFFFF));
            System.out.println("continuous: 0x" +Integer.toHexString(continuous & 0xFFFFFFFF));
            
            
        }
        
        boolean testSplit = false;
        if (testSplit) {
            System.out.println("Split [" + "25.3:2-8vsb".split("\\.")[0] + "]");
        }
        
        boolean testRegexescape = false;
        if (testRegexescape) {
            String title = "Encore.!";
            title = title.replaceAll("\\.!", "!");
            System.out.println("[" + title + "]");
        }
                       

        
        boolean parseMinusOne = false;
        if (parseMinusOne) System.out.println(Integer.parseInt("-1") * 1000 * 60);
        
        boolean testStringArray = false;
        if (testStringArray) {
            final String[][] menuAndActions = {{"OpenVcrPage", "vcr"}, {"Shutdown", "shutdown"}, {"Testing", "testing"}};
        
            ArrayList<MenuItem> menuItems = new ArrayList<MenuItem>();
            
            for(int i = 0; i <= menuAndActions[0].length; i++) {
                System.out.println("menu: " + menuAndActions[i][0] + " " + menuAndActions[i][1]);
                final MenuItem item = new MenuItem(menuAndActions[i][0]);
                menuItems.add(item);
            }
    
        }
        
        boolean testDeviceId = false;
        if (testDeviceId) System.out.println(new Tester().getStartNumber("269536340"));
        if (testDeviceId) System.out.println(new Tester().getStartNumber(null));
        if (testDeviceId) System.out.println(new Tester().getStartNumber("2695363x40"));
            
        
        
        boolean testOtherStuff = false;
        if (testOtherStuff) {
            SimpleDateFormat hhmm = new SimpleDateFormat("kk:mm"); // DRS 20160427 - Support restart OS command
            SimpleDateFormat hhmmss = new SimpleDateFormat("kk:mm:ss"); // DRS 20160427 - Support restart OS command
    
            // Always wait until 15 seconds before the next minute before continuting
            Calendar aCal = Calendar.getInstance();
            long clockSeconds = aCal.get(Calendar.SECOND);
            long waitFor = 45L - clockSeconds;
            if (waitFor < 0) {
                waitFor = waitFor + 60;
            }
            System.out.println(new Date() + " Waiting for " + waitFor + " seconds before restarting CwHelper");
            try {Thread.sleep(waitFor * 1000);} catch (Exception e ){}
            aCal = Calendar.getInstance(); // it should be 15 seconds before the top of the next minute as this line runs
            aCal.add(Calendar.SECOND, 16);
            String nextMinute = hhmm.format(aCal.getTime()); // DRS 20160427
            System.out.println("nextMinute: " + nextMinute);
            System.out.println("nextMinsec: " + hhmmss.format(aCal.getTime()));
            System.out.println("time now  : " + new Date());
            
        }

        /*
        //boolean goodResult[] = {false, false, false, false, false, false, true};
        boolean goodResult[] = {false, true,  true,  true,  true};
        boolean fr[] =         {false, false, true,  true,  false};
        
        boolean forceRequired = false;
        int forceCount = 0;
        int i = 0;
        int eboCount = 0;
        boolean commandFailure = false;
        
        do {
            commandFailure = false;
            boolean loopGoodResult = goodResult[i++];
            if (loopGoodResult){
                System.out.println("Good result " + i + " was " + loopGoodResult + " so no need to retry.");
            } else {
                commandFailure = true;
                System.out.println("Good result " + i + " was " + loopGoodResult + " so do need to retry.");
                eboCount++;
                if (eboCount > 5) {
                    System.out.println(new Date() + " ERROR: Failed to handle set command after retries. ");
                    throw new Exception ("ERROR: Failed to handle set command after retries. " );
                }
                else System.out.println(new Date() + " WARNING: Failed to handle set command. ");
                continue;
            }
            forceRequired = fr[i];
            System.out.println("Force required: " + fr[i] + " forceCount " + forceCount);
            if (forceRequired) System.out.println("Running a force.");
        } while (commandFailure || (forceRequired && forceCount++ < 1));
        */
        
        /*
        int leadTimeSeconds = 0;
        int minutes = 1;
        System.out.println("result: " + (minutes + (leadTimeSeconds + 30)/60));
         * 
         */
        /*
        System.out.println("\\" + ("" + Math.random()).substring(2));
        File aFile = new File("c:\\my\\tmp\\doesntmatter.txt");
        String testFileName = aFile.getParent() + "\\" + Math.random();
        System.out.println("test file anme:"  + testFileName);
        File testFile = new File(testFileName);
        BufferedWriter writer = new BufferedWriter(new FileWriter(testFile));
        writer.append('D');
        writer.close();
        System.out.println("exists" + testFile.exists());
        System.out.println("canwrite" + testFile.canWrite());
        Thread.sleep(10000);
        System.out.println("delete" + testFile.delete());
        */

        
        /*
        String tf = "true";
        
        boolean thing = Boolean.valueOf(null);
        System.out.println("thing:" + thing);

        thing = Boolean.valueOf(tf);
        System.out.println("thing:" + thing);
        
        long tv1 = 23456L;
        long tv2 = 88881L;
        long tv3 = 8880;
        System.out.println(tv1 + " => " + ((tv1+5)/10)*10);
        System.out.println(tv2 + " => " + ((tv2+5)/10)*10);
        System.out.println(tv3 + " => " + ((tv3+5)/10)*10);
        */
        
    }
    private double getStartNumber(String deviceId) {
        if (deviceId != null && deviceId.length() > 3) {
            try {
                String lastThree = deviceId.substring(deviceId.length() - 3);
                double startNumber = Integer.parseInt(lastThree) * 10000;
                return startNumber;
            } catch (Throwable t ) {
                // fall through
            }
        }
        double startNumber = Math.round(Math.random() * 1000) * 10000;
        return startNumber;
    }

}
