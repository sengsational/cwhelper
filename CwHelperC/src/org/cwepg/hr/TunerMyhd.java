/*
 * Created on Aug 16, 2009
 *
 */
package org.cwepg.hr;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.cwepg.reg.RegValueConverter;
import org.cwepg.reg.RegistryHelperMyhd;
import org.cwepg.svc.MyhdPass;

public class TunerMyhd extends Tuner {

    int tunerType = Tuner.MYHD_TYPE;
    public static final int DAILY = 1;
    public static final int WEEKLY = 2;
    
    //////////// CONSTRUCTORS & RELATED ////////////////
    public TunerMyhd (boolean addDevice) {
        System.out.println(new Date() + " ==========>  TunerMyhd<init> STARTING  <===============");
        this.id = "MYHD";
        this.number = 0;
        lineUp = new LineUpMyhd(this);
        if(addDevice){
            addCaptures(getCapturesFromFile(lineUp));
            TunerManager.getInstance().addTuner(this);
        }
        this.lastRefresh = new Date().getTime();
        System.out.println(new Date() + " ==========>  TunerMyhd<init> COMPLETE  <===============");
    }

    public TunerMyhd(String recordPath, boolean addDevice) {
        this(addDevice);
        this.recordPath = recordPath;
    }

    public void addCapturesFromStore(){
        addCaptures(getCapturesFromFile(lineUp));
    }

    private List<Capture> getCapturesFromFile(LineUp lineUp) {
        ArrayList<Capture> captures = new ArrayList<Capture>();
        try {
            ArrayList reservations = RegistryHelperMyhd.getActiveReservationsMyHD();
            TOP:
            for (Iterator iter = reservations.iterator(); iter.hasNext();) {
                byte[] buf = (byte[])iter.next();
                int repeatFlag = RegValueConverter.getIntFromBinary(buf, RegistryHelperMyhd.REPEAT_FLAG * 4, 4);
                if (repeatFlag == 0){
                    Capture capture = makeCaptureFromBytes(buf, lineUp);
                    int captureMode = ((CaptureMyhd)capture).getCaptureMode(); // 0=watch, 1=analog, 2=digital, 10=pass
                    if (capture != null && capture.channel != null && !capture.hasEnded() && captureMode <= 2){
                        captures.add(capture);
                    } 
                } else {
                    int[] daysOfWeek = getDaysOfWeekFromBinary(buf);
                    ArrayList calendars = RegistryHelperMyhd.getStartEndCalendars(buf);
                    Calendar startCal = (Calendar)calendars.get(0);
                    Calendar endCal = (Calendar)calendars.get(1);
                    Calendar now = Calendar.getInstance();
                    while (startCal.before(now)){
                        startCal.add(Calendar.DATE, 1);
                        endCal.add(Calendar.DATE, 1);
                    }
                    Calendar endHorizon = (Calendar)now.clone();
                    endHorizon.add(Calendar.HOUR, TIME_HORIZON_HOURS);
                    
                    while (startCal.before(endHorizon)){
                        Capture capture = makeCaptureFromBytes(calendars, buf, lineUp, true);
                        capture.setRecurring(true);
                        if (repeatFlag == WEEKLY){
                            for(int i = 0; i < daysOfWeek.length; i++){
                                if(startCal.get(Calendar.DAY_OF_WEEK) == daysOfWeek[i]){
                                    if (capture != null && capture.channel != null && !capture.hasEnded()){
                                        captures.add(capture);
                                    }
                                }
                            }
                        } else if (repeatFlag == DAILY){
                            if (capture != null && capture.channel != null && !capture.hasEnded()){
                                captures.add(capture);
                            }
                        } else {
                            System.out.println(new Date() + " ERROR: MyHD recurrance flag " + repeatFlag + " is not supported.");
                            continue TOP;
                        }
                        startCal.add(Calendar.DATE, 1);
                        endCal.add(Calendar.DATE, 1);
                    }
                }
            }
        } catch (Exception e) {
            System.out.println(new Date() + " ERROR: Not reading persisted captures from registry. " + e.getMessage());
        }
        return captures;
    }
    
    private static Capture makeCaptureFromBytes(byte[] reservationData, LineUp lineUp){
        ArrayList calendars = null;
        try {
            calendars = RegistryHelperMyhd.getStartEndCalendars(reservationData);
        } catch (Exception e){
            System.out.println(new Date() + " ERROR: Could not make start/end Calendars from an active MyHD reservation. " + e.getMessage());
            System.err.println(new Date() + " ERROR: Could not make start/end Calendars from an active MyHD reservation. " + e.getMessage());
            e.printStackTrace();
        }
        return makeCaptureFromBytes(calendars, reservationData, lineUp, false);
    }
    
    private static Capture makeCaptureFromBytes(ArrayList calendars, byte[] reservationData, LineUp lineUp, boolean quiet) {
        Capture capture = null;
        try {
            Calendar startCal = (Calendar)calendars.get(0);
            Calendar endCal = (Calendar)calendars.get(1);
            Slot slot = new Slot(startCal,endCal); // running 'new Slot' takes care of removing a second
            int virtualChannel = RegValueConverter.getIntFromBinary(reservationData, RegistryHelperMyhd.VIRTUAL_CHANNEL * 4, 4);
            int subchannel = RegValueConverter.getIntFromBinary(reservationData, RegistryHelperMyhd.SUBCHANNEL * 4, 4);
            int input = RegValueConverter.getIntFromBinary(reservationData, RegistryHelperMyhd.INPUT * 4, 4) + 1;
            String physicalChannel = RegValueConverter.getIntFromBinary(reservationData, RegistryHelperMyhd.PHYSICAL_CHANNEL * 4, 4) + "";
            //String protocol = RegValueConverter.getIntFromBinary(reservationData, RegistryHelperMyhd.PROTOCOL * 4, 4) + "";
            String targetFileName = RegValueConverter.getStringFromBinary(reservationData, RegistryHelperMyhd.FILE_NAME_START);
            String title = RegValueConverter.getStringFromBinary(reservationData, RegistryHelperMyhd.TITLE_START);
            //System.out.println(new Date() + " TunerMyhd.makeCaptureFromBytes() for " + targetFileName);
            int captureMode = RegValueConverter.getIntFromBinary(reservationData, RegistryHelperMyhd.CAPTURE_MODE * 4, 4);
            if (captureMode == 0){ // Watch
                targetFileName = "WATCH";
            }

            String channelVirtualFull = virtualChannel + "." + subchannel + ":" + input;

            Channel channel = null;
            if (subchannel != 0){
                channel = lineUp.getChannelVirtual(channelVirtualFull, physicalChannel);
            } else {
                channel = lineUp.getChannelAnalog(physicalChannel, "" + input, "analogCable");
                System.out.println(new Date() + " WARNING: defaulted 'analogCable' as protocol for Myhd channel.");
                //TODO: Figure out how to get the right protocol here
            }

            capture = new CaptureMyhd(slot, channel);
            ((CaptureMyhd)capture).setCaptureMode(captureMode);
            Target target = new Target(targetFileName, title, null, null, capture.getChannelProtocol(), Tuner.MYHD_TYPE);
            capture.setTarget(target);
            if(capture.getLastError() != null && !quiet) {
                System.out.println(new Date() + " ERROR: " + capture.getLastError());
            }
        } catch (Exception e){
            System.out.println(new Date() + " ERROR: Could not make a capture from an active MyHD reservation. " + e.getMessage());
            System.err.println(new Date() + " ERROR: Could not make a capture from an active MyHD reservation. " + e.getMessage());
            e.printStackTrace();
        }
        return capture;
    }
    
    private static int[] getDaysOfWeekFromBinary(byte[] reservationData){
        int[] result = new int[0];
        try {
            int dayOfWeek = RegValueConverter.getIntFromBinary(reservationData, RegistryHelperMyhd.DAY_OF_WEEK * 4, 4);
            String dayOfWeekBinary = Integer.toBinaryString(dayOfWeek);
            StringBuffer dowBuf = new StringBuffer(dayOfWeekBinary).reverse();
            result = new int[dayOfWeekBinary.replaceAll("0", "").length()];
            for (int i = 0, j = 0; i < dowBuf.length(); i++){
                if (dowBuf.charAt(i) == '1') {
                    result[j++] = i + 1;
                }
            }
        } catch (Exception e){
            System.out.println(new Date() + " ERROR: Could not get days of week for recurring MyHD capture.");
        }
        return result;
    }
    
    public String getFullName() {
        return this.id;
    }

    public String getDeviceId(){
        return "1";
    }

    public int getType(){
        return this.tunerType;
    }


    public void scanRefreshLineUp(){
        ((LineUpMyhd)this.lineUp).scan(this);
    }
    
    public void scanRefreshLineUp(boolean useExistingFile, String signalType, int maxSeconds) throws Exception {
        scanRefreshLineUp();
    }

    public void addToCapturesList(Capture newCapture, boolean writeIt) throws CaptureScheduleException {
        if (!slotOpen(newCapture.slot)) throw new CaptureScheduleException("Capture " + newCapture + " conflicts with " + getConflictingCapture(newCapture.slot));
        captures.add(newCapture);
        if (CaptureManager.myhdWakeup) newCapture.setWakeup();
    }

    public List getPassItems() {
        ArrayList<MyhdPass> passItems = new ArrayList<MyhdPass>();
        try {
            ArrayList reservations = RegistryHelperMyhd.getActiveReservationsMyHD();
            int sequence = 0;
            for (Iterator iter = reservations.iterator(); iter.hasNext();) {
                byte[] buf = (byte[])iter.next();
                int captureMode = RegValueConverter.getIntFromBinary(buf, RegistryHelperMyhd.CAPTURE_MODE * 4, 4);
                if (captureMode == 10){
                    String title = RegValueConverter.getStringFromBinary(buf, RegistryHelperMyhd.TITLE_START);
                    String fileName = RegValueConverter.getStringFromBinary(buf, RegistryHelperMyhd.FILE_NAME_START);
                    MyhdPass aPassItem = new MyhdPass(title, fileName, sequence++);
                    passItems.add(aPassItem);
                }
            }
        } catch (Exception e) {
            System.out.println(new Date() + " ERROR: Failure in getPassItems() " + e.getMessage());
            e.printStackTrace();
        }
        return passItems;
    }

    public void removePassItems() {
        try {
            ArrayList reservations = RegistryHelperMyhd.getActiveReservationsMyHD();
            for (Iterator iter = reservations.iterator(); iter.hasNext();) {
                byte[] buf = (byte[])iter.next();
                int captureMode = RegValueConverter.getIntFromBinary(buf, RegistryHelperMyhd.CAPTURE_MODE * 4, 4);
                if (captureMode == 10){
                    String fileName = RegValueConverter.getStringFromBinary(buf, RegistryHelperMyhd.FILE_NAME_START);
                    String title = RegValueConverter.getStringFromBinary(buf, RegistryHelperMyhd.TITLE_START); // not tested
                    RegistryHelperMyhd.deleteFromSchedule(fileName, title);
                }
            }
        } catch (Exception e) {
            System.out.println(new Date() + " ERROR: Failure in getPassItems() " + e.getMessage());
            e.printStackTrace();
        } finally {
            //System.out.println(new Date() + " DEBUG: notify myhd");
            CaptureMyhd.notifyExternalApplication();
        }
    }

    
    
    /*
     * Queries used by CaptureManager
     */
    
    public void addCaptureAndPersist(Capture newCapture, boolean writeIt) throws CaptureScheduleException {
        if (newCapture.hasStarted() && !newCapture.isNearEnd(120)){
            newCapture.slot.adjustStartTimeToFutureMinute();
        } 
        addToCapturesList(newCapture, writeIt);
        System.out.println(new Date() + " TunerMyhd.addCapture(newCapture, " + writeIt + ")");
        boolean warn = false;
        newCapture.persist(writeIt, warn, this);
    }
    
    public void addCaptures(List captures) {
        boolean writeIt = true;
        int count = 0;
        System.out.println("there were " + captures.size() + " items in the list.");
        for (Iterator iter = captures.iterator(); iter.hasNext();) {
            Capture capture = (Capture) iter.next();
            try {
                this.addToCapturesList(capture, writeIt);
                System.out.println(new Date() + " Added to MyHD: " + capture);
            } catch (CaptureScheduleException e) {
                System.out.println(new Date() + " ERROR: Can not add capture. " + e.getMessage());
            }
            count++;
        }
        System.out.println(new Date() + " Loaded " + count + " captures from the registry");
    }
    
    synchronized public void refreshCapturesFromOwningStore(boolean interrupt){
        boolean localCaptureListAltered = false;
        if (new Date().getTime() - this.lastRefresh < 30000) {
            //System.out.println(new Date() + " TunerMyhd.refreshCapturesFromOwningStore() skipped.");
            return;
        }
        //System.out.println(new Date() + " TunerMyhd.refreshCapturesFromOwningStore() processing.");
        List<Capture> fileCaptures = getCapturesFromFile(this.lineUp);

        //TODO: REMOVE DEBUG
        //StringBuffer capturesWeHave = new StringBuffer();
        //for (Iterator iter2 = this.captures.iterator(); iter2.hasNext();) {
        //    Capture capture = (Capture) iter2.next();
        //    capturesWeHave.append(capture.getFileName() + " - " + capture.getTitle() + "\n");
        //}
        //System.out.println(new Date() + " Our List Before refresh:\n" + new String(capturesWeHave));
        //TODO: REMOVE DEBUG
        
        /* 1 - Add captures that MyHD   has that we don't */
        for (Iterator iter = fileCaptures.iterator(); iter.hasNext();) {
            Capture fileCapture = (Capture) iter.next();
            if (this.captures.contains(fileCapture)){
                System.out.println(new Date() + " Capture " + fileCapture.getTitle() + " was in cwhelper memory already.  Do nothing.");
                continue;
            } else {
                try {
                    System.out.println(new Date() + " Found a capture in the MyHD   list that wasn't in cwhelper memory.  Adding it to cwhelper memory: " + fileCapture);
                    addCaptureAndPersist(fileCapture, false); // add capture, but don't write to the store (already there)
                    localCaptureListAltered = true;
                } catch (Exception e){
                    System.out.println(new Date() + " ERROR: problem adding capture from MyHD   list.  Capture:\n" + fileCapture.getHtml(0));
                }
            }
        }

        /* 2 - Remove captures the MyHD   has that we don't */
        ArrayList<Capture> capturesToRemove = new ArrayList<Capture>();
        for (Iterator iter = this.captures.iterator(); iter.hasNext();) {
            Capture capture = (Capture) iter.next();
            if (capture.hasStarted()) {
                System.out.println(new Date() + " Capture " + capture.getTitle() + " not removed (it has already started).");
                continue;
            }
            if (fileCaptures.contains(capture)){
                System.out.println(new Date() + " Capture " + capture.getTitle() + " not removed (it's in the MyHD   list).");
                continue;
            }
            System.out.println(new Date() + " Capture " + capture.getTitle() + ", in cwhelper memory, was not found in the MyHD   list.  Removing it from cwhelper memory.");
            capturesToRemove.add(capture);
            localCaptureListAltered = true;
        }
        for (Capture capture : capturesToRemove) {
            this.removeLocalCapture(capture);
        }
        //System.out.println(new Date() + " TunerMyhd.refreshCapturesFromOwningStore(): lastRefresh ");
        this.lastRefresh = new Date().getTime();
        if (localCaptureListAltered && interrupt) CaptureManager.requestInterrupt("TunerMyhd.refreshCapturesFromOwningStore");
    }
    
    public void removeLocalCapture(Capture aCapture) {
        int size = captures.size();
        System.out.println(new Date() + " Removing capture from cwhelper memory. " + aCapture.getTitle());
        aCapture.removeWakeup();
        captures.remove(aCapture);
        if (captures.size() == size){
            System.out.println(new Date() + " RemoveLocalCapture failed to remove " + aCapture + "\nCapture List:\n");
            for (int i = 0; i < captures.size(); i++){
                System.out.println(captures.get(i));
            }
        }
    }

    public List<Capture> getCaptures(){
        refreshCapturesFromOwningStore(true);
        return this.captures;
    }
    
    public void removeAllCaptures(boolean localRemovalOnly) {
        try {
            ArrayList<Capture> toRemove = new ArrayList<Capture>();
            ArrayList<Capture> localRemove = new ArrayList<Capture>();
            System.out.println(new Date() + " Removing all non-recurring captures from TunerMyhd " + this.getFullName() + ".");
            for (int i = 0; i < captures.size(); i++){
                Capture aCapture = (Capture)captures.get(i);
                if (!localRemovalOnly && !aCapture.isRecurring()){
                    toRemove.add(aCapture);
                } else if (localRemovalOnly){
                    localRemove.add(aCapture);
                }
            }
            for (Iterator iter = toRemove.iterator(); iter.hasNext();) {
                Capture aCapture = (Capture) iter.next();
                aCapture.removeWakeup();
                removeCapture(aCapture);
                captures.remove(aCapture);
            }
            for (Iterator iter = localRemove.iterator(); iter.hasNext();) {
                Capture aCapture = (Capture) iter.next();
                aCapture.removeWakeup();
                captures.remove(aCapture);
            }
            String debug = CaptureMyhd.notifyExternalApplication();
            System.out.println(new Date() + " DEBUG: " + debug);
        } catch (Exception e) {
            System.out.println(new Date() + " Could not notify external application. " + e.getMessage());
        }
    }
    
    public void removeCapture(int j) throws Exception  {
        Capture aCapture = (Capture)captures.get(j);
        System.out.println(new Date() + " Removing " + (aCapture.isRecurring?"recurring":"") + " capture from the MyHD registry. " + aCapture.getTitle());
        boolean isWatch = aCapture.getFileName().contains(":\\WATCH");
        if (aCapture.isRecurring() && !isWatch){
            RegistryHelperMyhd.deleteFromSchedule(aCapture.getFileName(), aCapture.getTitle());
            delistCapturesMatchingFileName(aCapture.getFileName(), this.captures, aCapture.getTitle());
        } else if (aCapture.isRecurring() && isWatch) {
            System.out.println("Trying to delete a watch based on title [" + aCapture.getTitle() + "] instead of file name.");
            RegistryHelperMyhd.deleteFromSchedule(aCapture.getFileName(), aCapture.getTitle());
            delistCapturesMatchingFileName(aCapture.getFileName(), this.captures, aCapture.getTitle());
        } else {
            aCapture.removeWakeup();
            RegistryHelperMyhd.deleteFromSchedule(aCapture.slot);
            captures.remove(j);
        }
        CaptureMyhd.notifyExternalApplication();
    }
    
    public void removeCapture(Capture capture) {
        for (int i = 0; i < captures.size(); i++){
            Capture aCapture = captures.get(i);
            if (aCapture.equals(capture)){
                try {
                    this.removeCapture(i);
                } catch (Exception e) {
                    System.out.println(new Date() + " Could not remove capture " + e.getMessage());
                    System.err.println(new Date() + " Could not remove capture " + e.getMessage());
                    e.printStackTrace();
                }
                break;
            }
        }
    }
    
    @Override
    public void removeDefaultRecordPathFile() { /*not used in Fusion*/ }
    @Override
    public void writeDefaultRecordPathFile() { /*not used in Fusion */ }
    
    public static void main(String[] args) {
        TunerManager tm = TunerManager.getInstance();
        tm.countTuner(Tuner.MYHD_TYPE, true);

        boolean testReadFromRegistry = true;
        if (testReadFromRegistry){
            Tuner tuner = tm.getTuner("MYHD");
            List captures = tuner.getCaptures();
            for (Iterator iter = captures.iterator(); iter.hasNext();) {
                Capture aCapture = (Capture) iter.next();
                System.out.println("aCapture: " + aCapture);
            }
        }
        
        boolean testRemoveFromRegistry = false;
        if (testRemoveFromRegistry){
            Tuner tuner = tm.getTuner("MYHD");
            tuner.removeAllCaptures(false);
            List captures = tuner.getCaptures();
            for (Iterator iter = captures.iterator(); iter.hasNext();) {
                Capture aCapture = (Capture) iter.next();
                System.out.println("aCapture: " + aCapture);
            }
        }
        
        boolean testGetChannels = false;
        if (testGetChannels){
            System.out.println("============================");
            System.out.println(tm.getWebChannelList());
            System.out.println("============================");
        }

        boolean printChannels = false;
        if (printChannels){
            Tuner tuner = tm.getTuner("MYHD");
            Map channels = tuner.lineUp.channels;
            for (Iterator iter = channels.keySet().iterator(); iter.hasNext();) {
                String key = (String) iter.next();
                System.out.println("TunerMyhd.main " + channels.get(key));
            }
        }
    }
}
