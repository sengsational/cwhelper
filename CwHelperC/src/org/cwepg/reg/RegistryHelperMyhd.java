/*
 * Created on Aug 11, 2009
 *
 */
package org.cwepg.reg;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.cwepg.hr.Capture;
import org.cwepg.hr.CaptureManager;
import org.cwepg.hr.CaptureMyhd;
import org.cwepg.hr.CaptureScheduleException;
import org.cwepg.hr.Channel;
import org.cwepg.hr.ChannelAnalog;
import org.cwepg.hr.Slot;
import org.cwepg.hr.Tuner;
import org.cwepg.hr.TunerManager;
import org.cwepg.hr.TunerMyhd;


public class RegistryHelperMyhd {

    public static final String topKey = "HKEY_LOCAL_MACHINE";
    public static final String myhdBranch = "SOFTWARE\\MyHD";
    public static final int FILE_NAME_START = 64;
    public static final int MAX_FILE_NAME_LENGTH = 259;
    public static final int CHANNEL_START = 342;
    public static final int MAX_CHANNEL_LENGTH = 20;
    public static final int TITLE_START = 362;
    public static final int MAX_TITLE_LENGTH = 254;
    private static NumberFormat threeDigit = NumberFormat.getInstance();

    public static final int INPUT = 0;                    // Ant input  0 or 1 
    public static final int PHYSICAL_CHANNEL = 1;
    public static final int VIRTUAL_CHANNEL = 2;          // Virtual Maj-Ch
    public static final int SUBCHANNEL = 3;               //  Virtual Sub-Ch
    public static final int REPEAT_FLAG = 4;              // 0 = one time, 1 = daily, 2 = weekly
    public static final int YEAR = 5;
    public static final int MONTH = 6;
    public static final int DAY = 7;
    public static final int DAY_OF_WEEK = 8;              //  Day of the week  (1:Sun, 2:Mon, 4:Tue, 8:Wed...)
    public static final int CAPTURE_MODE = 9;             //  0 = Watch  2 = CaptureDigital 1=CaptureAnalog
    public static final int PROTOCOL = 10;                //  MyHD 1=AIR/8vsb and 2=Cable/qam - (0=??)
    public static final int START_HOUR = 11;
    public static final int START_MINUTE = 12;
    public static final int END_HOUR = 13;
    public static final int END_MINUTE = 14;
    public static final int FILENAME_DEFAULT_STYLE = 15;  //  MyHDs default filename style  (We don't use)
    public static final int FILE_NAMING = 16;             //  MyHDs filenaming style  (We don’t use)
    public static final int FOLDER_NAMING = 17;           //  MyHDs foldernaming style (We don’t use)
    public static final int WIDTH = 18;
    public static final String[] DESCRIPTION = {"alt input","physical channel", "virtual channel",
        "subchannel","one-time flag","year","month","date","day of the week",
        "capture flag","input","start hour","start minute","end hour","end minute",
        "default fn style", "file naming code", "folder naming code"};

    static int debug = 0;

    public static ArrayList getChannels(Tuner tuner) {
            return getChannelsMyhd(tuner);
    }
    
    private static ArrayList getChannelsMyhd(Tuner tuner){
        ArrayList<Channel> list = new ArrayList<Channel>();
        try {
            Map map = Registry.getValues(topKey, myhdBranch);

            int input1Count = ((Integer)map.get("INPUT1_CH Tail_VCH")).intValue();
            int input2Count = ((Integer)map.get("INPUT2_CH Tail_VCH")).intValue();
            int favoriteCount = ((Integer)map.get("FAVORITE_CH Tail_VCH")).intValue();
            byte[] data = (byte[])map.get("FAVORITE_CH_DATA_VCH");
            int[][] favs = getFavoritesNumbers(data, favoriteCount);
            
            data = (byte[])map.get("INPUT1_CH_DATA_VCH_QAM");
            if (data == null) data = (byte[])map.get("INPUT1_CH_DATA_VCH");
            if (data != null) addChannelsMyhd(data, input1Count, list, tuner, 1, favs[0]);
            else System.out.println(new Date() + " ERROR: channel data not found for MyHD input 1 in the registry");

            data = (byte[])map.get("INPUT2_CH_DATA_VCH_QAM");
            if (data == null) data = (byte[])map.get("INPUT2_CH_DATA_VCH");
            if (data != null) addChannelsMyhd(data, input2Count, list, tuner, 2, favs[1]);
            else System.out.println(new Date() + " ERROR: channel data not found for MyHD input 2 in the registry");
            
            
        } catch (Exception e) {
            System.out.println(new Date() + " ERROR: getChannelsMyhd( tuner ) " + e.getClass().getName() + " " + e.getMessage());
            System.err.println(new Date() + " ERROR: getChannelsMyhd( tuner ) " + e.getClass().getName() + " " + e.getMessage());
            e.printStackTrace();
        }
        return list;
    }
    
    private static int[][] getFavoritesNumbers(byte[] data, int inputCount) throws Exception {
        int[][] favs = new int[2][inputCount];
        Arrays.fill(favs[0], -1);Arrays.fill(favs[1], -1);
        int input0 = 0;
        int input1 = 0;
        for (int i = 0; i < inputCount; i++) {
            int favKey = RegValueConverter.getIntFromBinary(data, i * 8 + 4, 4);
            int regInput = RegValueConverter.getIntFromBinary(data, i * 8, 4);
            if (regInput == 0) favs[0][input0++]=favKey;
            if (regInput == 1) favs[1][input1++]=favKey;
        }
        return favs;
    }

    private static void addChannelsMyhd(byte[] data, int inputCount, ArrayList<Channel> list, Tuner tuner, int iinput, int[] favs) throws Exception {
        Channel channel = null;
        boolean stackTracePrinted = false;
        //01 00 00 00 00 00 00 00 (0-7)
        //1B 00 12 00 01 01 00 00 (8-15)
        //03 00 00 00 02 00 00 00 (16-23)
        for (int i = 0; i < inputCount; i++) {
            try {
                int protocol = RegValueConverter.getIntFromBinary(data, i * 40,      4); //0,1,2,3      - 01 00 00 00   (1)
                int signal = RegValueConverter.getIntFromBinary  (data, i * 40 + 4,  4); //4,5,6,7      - 00 00 00 00   (0)
                int rf = RegValueConverter.getIntFromBinary      (data, i * 40 + 8,  1); //8            - 1B            (27)
                int virm = RegValueConverter.getIntFromBinary    (data, i * 40 + 10, 2); //10,11        - 12 00         (18)
                int virs = RegValueConverter.getIntFromBinary    (data, i * 40 + 12, 1); //12           - 01            (1)
                int rfsub = RegValueConverter.getIntFromBinary   (data, i * 40 + 13, 1); //13           - 01            (1) 
                int prog = RegValueConverter.getIntFromBinary    (data, i * 40 + 16, 4); //16           - 03 00 00 00   (3)
                int srcid = RegValueConverter.getIntFromBinary   (data, i * 40 + 20, 4); //20           - 02 00 00 00   (2)
                int input = iinput;
                String description = RegValueConverter.getStringFromBinary(data, i * 40 + 24);
                if (signal != 3){
                    channel = new Channel(description, tuner, protocol, signal, rf, virm, virs, rfsub, prog, input, srcid);
                    list.add(channel);
                } else {
                    String airCat = "Cat";
                    String protocolString = "analogCable";
                    if (protocol == 1) {
                        airCat = "Air";
                        protocolString = "analogAir";
                    }
                    if (description != null && !description.trim().equals("")){
                        channel = new ChannelAnalog(description, tuner, protocolString, rf, virm, airCat, input);
                        list.add(channel);
                    } else{
                        channel = new ChannelAnalog("" + rf, tuner, protocolString, rf, virm, airCat, input);
                        list.add(channel);
                    }
                }
                if (channel != null){
                    for (int j = 0; j < favs.length; j++){
                        if (favs[j] == i){
                            channel.setFavorite();
                            break;
                        }
                    }
                }
                
            } catch (Throwable t) {
                System.out.println(new Date() + " ERROR: addChannelsMyhd( tuner ) " + t.getClass().getName() + " " + t.getMessage());
                System.err.println(new Date() + " ERROR: addChannelsMyhd( tuner ) " + t.getClass().getName() + " " + t.getMessage());
                if (!stackTracePrinted) {
                    t.printStackTrace();
                    stackTracePrinted = true;
                }
            }
        }
    }
    
    public static ArrayList getActiveReservationsMyHD() {
        ArrayList<byte[]> reservations = new ArrayList<byte[]>();
        threeDigit.setMinimumIntegerDigits(3);
        String reservationName = null;
        try {
            Map map = Registry.getValues(topKey, myhdBranch);
            for(int i = 0; i < 50; i++){
                reservationName = "RESERVATION_INFO_VCH_" + threeDigit.format(i);
                byte[] buf = (byte[])map.get(reservationName);
                int check = RegValueConverter.getIntFromBinary(buf, 4, 4);
                if (check != -1){
                    reservations.add(buf);
                }
            }
        } catch (Exception e) {
            System.out.println(new Date() + " getActiveReservations() " + e.getMessage());
        }
        return reservations;
    }
    
    public static String getFirstUnusedReservationName() {
        threeDigit.setMinimumIntegerDigits(3);
        String reservationName = null;
        try {
            Map map = Registry.getValues(topKey, myhdBranch);
            byte[] buf = null;
            for(int i = 0; i < 50; i++){
                reservationName = "RESERVATION_INFO_VCH_" + threeDigit.format(i);
                buf = (byte[])map.get(reservationName);
                int check = RegValueConverter.getIntFromBinary(buf, 4, 4);
                if (check == -1){
                    break;
                }
            }
        } catch (Exception e) {
            System.out.println(new Date() + " getFirstUnusedReservationName() " + e.getMessage());
        }
        return reservationName;
    }

    public static void createReservation(CaptureMyhd capture, String reservationName) throws Exception {
        Map map = Registry.getValues(topKey, myhdBranch);

        // first check for conflicting reservation
        String conflictingReservation = getReservationNameForSlot(map, capture.getSlot(), true);
        if (conflictingReservation != null) throw new Exception("Can not create reservation.  Conflicts with " + conflictingReservation);

        byte[] dataBytes = (byte[])map.get(reservationName);
        int size = 4;
        int[] data = new int[16];
        for (int i = 0; i < 16; i++){
            int offset = i * size;
            data[i] = RegValueConverter.getIntFromBinary(dataBytes, offset, size);
        }

        Calendar startCal = capture.getStartCalendar();
        Calendar endCal = capture.getEndCalendar(); //clone (safe to change)
        endCal.add(Calendar.SECOND, 1); // to make it an even minute, since we always end at :59
        
        data[INPUT] = capture.getInput() - 1;
        data[PHYSICAL_CHANNEL] = capture.getPhysicalChannel();
        data[VIRTUAL_CHANNEL] = capture.getVirtualChannel();
        data[SUBCHANNEL] = capture.getSubchannel();
        data[REPEAT_FLAG] = 0; // 0 one time recordings only
        data[YEAR] = startCal.get(Calendar.YEAR);
        data[MONTH] = startCal.get(Calendar.MONTH) + 1;
        data[DAY] = startCal.get(Calendar.DATE);
        data[DAY_OF_WEEK] = startCal.get(Calendar.DAY_OF_WEEK);
        data[CAPTURE_MODE] = capture.getCaptureMode();
        data[PROTOCOL] = capture.getProtocol();
        data[START_HOUR] = startCal.get(Calendar.HOUR_OF_DAY);
        data[START_MINUTE] = startCal.get(Calendar.MINUTE);
        data[END_HOUR] = endCal.get(Calendar.HOUR_OF_DAY);
        data[END_MINUTE] = endCal.get(Calendar.MINUTE);
        data[FILENAME_DEFAULT_STYLE] = 0;
        
        for (int i = 0; i < data.length; i++) {
            if (debug > 0) System.out.println(new Date() + " RegistryHelperMyhd.createReservation(): " + DESCRIPTION[i] + " [" + data[i] + "]");
        }
        
        if (debug > 4 ){ // Print out the before and after for debugging
            System.out.println("before:");
            System.out.println(new String(dataBytes));
        }

        // Update the data on the byte[] record
        for (int i = 0; i < data.length; i++) {
            dataBytes = RegValueConverter.setIntIntoBinary(data[i], dataBytes, i);
        }
        
        // write the file name into the byte array
        dataBytes = RegValueConverter.setStringIntoBinary(capture.getFileName(), dataBytes, FILE_NAME_START, MAX_FILE_NAME_LENGTH);
        
        // write the program title into the byte array
        dataBytes = RegValueConverter.setStringIntoBinary(capture.getTitle(), dataBytes, TITLE_START, MAX_TITLE_LENGTH);
        
        // write the channel description into the byte array
        dataBytes = RegValueConverter.setStringIntoBinary(capture.getChannelDescription(), dataBytes, CHANNEL_START, MAX_CHANNEL_LENGTH);

        if (debug > 4){
            System.out.println("after:");
            System.out.println(new String(dataBytes));
        }
        
        // Write the updated record to the registry
        Registry.setBinaryValue(topKey, myhdBranch, reservationName, dataBytes);

        if (debug > -1) System.out.println(new Date() + " Saved " + reservationName + " in the registry.");
    }
    
    public static String getReservationNameForSlot(Slot slot, boolean warn) throws CaptureScheduleException {
        //System.out.println(new Date() + " getReservationNameForSlot " + slot);
        Map map;
        try {
            map = Registry.getValues(topKey, myhdBranch);
        } catch (UnsupportedEncodingException e) {
            throw new CaptureScheduleException(e.getMessage());
        }
        return getReservationNameForSlot(map, slot, warn);
    }
    
    private static String getReservationNameForSlot(Map map, Slot slot, boolean warn){
        threeDigit.setMinimumIntegerDigits(3);
        String reservationName = null;
        try {
            byte[] buf = null;
            for(int i = 0; i < 50; i++){
                reservationName = "RESERVATION_INFO_VCH_" + threeDigit.format(i);
                buf = (byte[])map.get(reservationName);
                int check = RegValueConverter.getIntFromBinary(buf, 4, 4);
                if (check == -1){
                    reservationName = null;
                    continue;
                } else {
                    ArrayList calendars = getStartEndCalendars(buf);
                    Calendar compareStartCal = (Calendar)calendars.get(0);
                    Calendar compareEndCal = (Calendar)calendars.get(1);
                    compareEndCal.add(Calendar.SECOND, -1);
                    Slot compareSlot = new Slot(compareStartCal,compareEndCal);

                    if (compareSlot.conflicts(slot)){
                        if (warn) System.out.println(new Date() + " Registry entry " + reservationName + " overlaps " + slot);
                        break;
                    } else {
                        reservationName = null;
                    }
                }
            }
        } catch (Exception e) {
            System.out.println(new Date() + " ERROR: getReservationNameForSlot() " + e.getMessage());
        }
        return reservationName;
    }

    private static String getReservationNameForFileName(Map map, String fileName, boolean b, String title) {
        threeDigit.setMinimumIntegerDigits(3);
        String reservationName = null;
        boolean isWatch = fileName.contains(":\\WATCH");
        try {
            byte[] buf = null;
            for(int i = 0; i < 50; i++){
                reservationName = "RESERVATION_INFO_VCH_" + threeDigit.format(i);
                buf = (byte[])map.get(reservationName);
                int check = RegValueConverter.getIntFromBinary(buf, 4, 4);
                if (check == -1){
                    reservationName = null;
                    continue;
                } else {
                    String targetFileName = RegValueConverter.getStringFromBinary(buf, RegistryHelperMyhd.FILE_NAME_START);
                    if (!isWatch) {
                        System.out.println("compare " + fileName + " with " + targetFileName);
                        if (targetFileName.equalsIgnoreCase(fileName)){
                            break;
                        }
                    } else {
                        if (targetFileName.contains(title)) {
                            break;
                        }
                    }
                }
                
            }
        } catch (Exception e) {
            System.out.println(new Date() + " ERROR: getReservationNameForFileName() " + e.getMessage());
        }
        return reservationName;
    }


    public static ArrayList getStartEndCalendars(byte[] buf) throws Exception {
        int[] scheduleData = new int[16];
        for (int i = 0; i < 16; i++){
            int offset = i * 4;
            scheduleData[i] = RegValueConverter.getIntFromBinary(buf, offset, 4);
        }
        ArrayList<Calendar> list = new ArrayList<Calendar>();
        Calendar startCal = Calendar.getInstance(); startCal.clear();
        Calendar endCal = Calendar.getInstance(); endCal.clear();
        list.add(startCal);
        list.add(endCal);
        int year = scheduleData[YEAR];
        int month = scheduleData[MONTH];
        int day = scheduleData[DAY];
        int startHour = scheduleData[START_HOUR];
        int startMinute = scheduleData[START_MINUTE];
        int endHour = scheduleData[END_HOUR];
        int endMinute = scheduleData[END_MINUTE];
        startCal.set(Calendar.YEAR, year);
        startCal.set(Calendar.MONTH, month - 1);
        startCal.set(Calendar.DATE, day);
        startCal.set(Calendar.HOUR_OF_DAY, startHour);
        startCal.set(Calendar.MINUTE, startMinute);
        endCal.set(Calendar.YEAR, year);
        endCal.set(Calendar.MONTH, month - 1);
        endCal.set(Calendar.DATE, day);
        endCal.set(Calendar.HOUR_OF_DAY, endHour);
        endCal.set(Calendar.MINUTE, endMinute);
        if (endCal.before(startCal)){
            endCal.add(Calendar.DATE, 1);
        }
        return list;
    }
    
    public static void deleteFromSchedule(Slot slot) throws Exception {
        Map map = Registry.getValues(topKey, myhdBranch);
        String scheduleToDelete = getReservationNameForSlot(map, slot, true);
        if (scheduleToDelete == null) throw new Exception("There is no matching reservation for " + slot);
        deleteReservation(scheduleToDelete, map);
    }
    
    public static void deleteFromSchedule(String fileName, String title) throws Exception {
        Map map = Registry.getValues(topKey, myhdBranch);
        String scheduleToDelete = getReservationNameForFileName(map, fileName, true, title);
        String errorMessage = "file name " + fileName;
        boolean isWatch = fileName.contains(":\\WATCH");
        if (isWatch) errorMessage = "show title " + title;
        if (scheduleToDelete == null) throw new Exception("There is no matching reservation for " + errorMessage);
        deleteReservation(scheduleToDelete, map);
    }
    
    public static void deleteReservation(String reservation, Map map) throws Exception {
        boolean found = false;
        String previousElement = null;
        for (Iterator iter = map.keySet().iterator(); iter.hasNext();) {
            String regElement = (String) iter.next();
            if (regElement.indexOf("RESERVATION_INFO_VCH_") < 0) continue;
            if (!found){
                if (reservation.equals(regElement)){
                    System.out.println(new Date() + " Removing " + reservation + " from the registry.");
                    found = true;
                }
            } else {
                // once found, just shift data from each element up to the previous position
                Registry.setBinaryValue(topKey, myhdBranch, previousElement, (byte[])map.get(regElement));
            }
            previousElement = regElement;
        }
    }

    public static String getExternalEpgFile() {
        String externalEpgFileName = null;
        try {
            externalEpgFileName = Registry.getStringValue(topKey, myhdBranch, "EXTERNAL_EPG_FILE");
        } catch (Throwable e) {
            System.out.println(new Date() + " ERROR: Could not get MyHD external file name. " + e.getMessage());
        }
        return externalEpgFileName;
    }

    public static void setExternalEpgFile(String externalEpgFile) {
        try {
            Registry.setStringValue(topKey, myhdBranch, "EXTERNAL_EPG_FILE", externalEpgFile);
        } catch (UnsupportedEncodingException e) {
            System.out.println(new Date() + " ERROR: Could not set MyHD external file name [" + externalEpgFile + "]. " + e.getMessage());
        }
    }
    
    /*
     * Test Harness
     */
    public static void main(String[] args) throws Exception {
        
        boolean testSetEpgFileName = true;
        if (testSetEpgFileName){
            CaptureManager.getInstance("C:\\Program Files\\MyHDEpg\\\\", "C:\\Program Files\\Silicondust\\HDHomeRun\\\\", "C:\\Documents and Settings\\All Users\\Application Data\\CW_EPG\\\\");
            String externalEpgFile = CaptureManager.dataPath + "cw_epg.mdb";
            RegistryHelperMyhd.setExternalEpgFile(externalEpgFile);
            System.out.println(RegistryHelperMyhd.getExternalEpgFile());
        }

        boolean testGetEpgFileName = false;
        if (testGetEpgFileName){
            System.out.println(RegistryHelperMyhd.getExternalEpgFile());
        }
        
        boolean testChannels = false;
        if (testChannels) {
            Tuner tuner = new TunerMyhd(true);
            System.out.println("------Done making Myhd tuner in the main -----");
            boolean testWebOutput = true;
            if (testWebOutput){
                System.out.println(TunerManager.getInstance().getWebChannelList());
            } else {
                List list = RegistryHelperMyhd.getChannels(tuner);
                for (Iterator iter = list.iterator(); iter.hasNext();) {
                    Channel channelDigital = (Channel) iter.next();
                    System.out.println("channel: " + channelDigital);
                }
            }
        }
        
        boolean testFirstReservation = false;
        if (testFirstReservation){
            System.out.println(RegistryHelperMyhd.getFirstUnusedReservationName());
        }
        
        boolean testGetReservationForSlot = false;
        if (testGetReservationForSlot){
            Slot slot = new Slot("08/13/2009 21:45","5");
            if (getReservationNameForSlot(slot, true) == null){
                System.out.println("There is no conflicting reservation for " + slot);
            }
        }
        
        boolean testCreateReservation = false;
        if (testCreateReservation){
            CaptureMyhd cap = CaptureMyhd.getTestCapture();
            RegistryHelperMyhd.createReservation(cap, "RESERVATION_INFO_VCH_022");
        }
        
        boolean testFullCreateReservation = false;
        if (testFullCreateReservation){
            Capture cap = CaptureMyhd.getTestCapture();
            cap.persist(true, true, new TunerMyhd("myhd", true));
            System.out.println("Last error: " + cap.getLastError());
        }
        
        boolean testFullCreateReservationSameVirtual = false;
        if (testFullCreateReservationSameVirtual){
            Capture cap114 = CaptureMyhd.getTestCapture(114, 1, 1, 1, 2);
            Capture cap115 = CaptureMyhd.getTestCapture(115, 1, 1, 1, 2);
            //cap114.persist(true,true);
            System.out.println("Last error: " + cap114.getLastError());
            System.out.println("cap114 persist completed.");
            //cap115.persist(true,true);
            System.out.println("Last error: " + cap115.getLastError());
            System.out.println("cap115 persist completed.");
        }

        boolean testDeleteReservation = false;
        if (testDeleteReservation){
            Capture cap = CaptureMyhd.getTestCapture();
            RegistryHelperMyhd.deleteFromSchedule(cap.getSlot());
        }
        
    }



}
