/*
 * Created on Aug 16, 2009
 *
 */
package org.cwepg.hr;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;

import org.cwepg.reg.FusionRegistryEntry;
import org.cwepg.svc.FusionDbTransactionCommandLine;

public class TunerFusion extends Tuner {

    private static final String DATE_FORMAT = "MM/dd/yy HH:mm";
    private static final SimpleDateFormat DF = new SimpleDateFormat(DATE_FORMAT);
    private static final SimpleDateFormat DBDTFS = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
    private static final SimpleDateFormat ISODF = new SimpleDateFormat("yyyy-MM-dd");
    private static final SimpleDateFormat ISOTF = new SimpleDateFormat("HH:mm:ss");


    private static final int ONCE = 1536;
    private static final int DAILY = 1537;
    private static final int WEEKLY = 1538;
    private static final int MON_TUE = 1539;
    private static final int WED_THU = 1540;
    private static final int WEEKDAYS = 1541;
    private static final int WEEKENDS = 1542;
    private static final int OUTLOOK_DAYS = 15;
    
    int tunerType = Tuner.FUSION_TYPE;
    
    private static final boolean USE_COMMANDLINE_PERSISTANCE = true;
    
    static boolean debug = false; //true;
    
    //////////// CONSTRUCTORS & RELATED ////////////////
    public TunerFusion(String id, int uid, String recordPath, int analogFileExtensionNumber, boolean addDevice){
        System.out.println(new Date() + " ==========>  TunerFusion<init> STARTING  <===============");
        this.id = id;
        this.number = uid;
        this.recordPath = recordPath + "\\";
        switch (analogFileExtensionNumber) {
        case 5:
            this.analogFileExtension="wmv";
            break;
        case 6:
            this.analogFileExtension="avi";
            break;
        default:
            this.analogFileExtension="mpg";
        }
        lineUp = new LineUpFusion(this);
        if (addDevice){
            addCaptures(getCapturesFromFile(lineUp), false); // do not write to owning store
            if (TunerManager.getInstance().noTuners()) addInProgressCaptures();
            TunerManager.getInstance().addTuner(this); 
        }
        this.lastRefresh = new Date().getTime();
        System.out.println(new Date() + " ==========>  TunerFusion<init> COMPLETE  <===============");
    }
    
    public TunerFusion(FusionRegistryEntry entry, boolean addDevice) {
        this(entry.getDeviceName(), entry.getZuluNumber(), entry.getRecordPath(), entry.getAnalogFileExtensionNumber(), addDevice);
        this.setLiveDevice(true);
    }
    
    public void addCapturesFromStore(){
        addCaptures(getCapturesFromFile(lineUp), false); // do not write to owning store
        if (TunerManager.getInstance().noTuners()) addInProgressCaptures();
    }

    private void addInProgressCaptures() {
        List<Capture> inProgressCaptures = CaptureDataManager.getInstance().getActiveFusion(this);
        addCaptures(inProgressCaptures, true);
    }

    private List<Capture> getCapturesFromFile(LineUp lineUp) {
        //System.out.println(new Date() + " TunerFusion.getCapturesFromFile()");
        ArrayList<Capture> captures = new ArrayList<Capture>();
        String message = "Not reading persisted captures from database. ";
        Connection connection = null;
        Statement statement = null;
        ResultSet rs = null;
        try {
            String localPathFile = TunerManager.fusionInstalledLocation + "\\Epg2List.Mdb"; // DRS 20150611 - use class field
            String tableName = "ReserveList";
            if (!this.tableExists(localPathFile, tableName) && !this.tableExists(localPathFile, tableName)){
                throw new Exception("Could not find the database.  Tried twice.");
            }
            //connection = DriverManager.getConnection("jdbc:odbc:Driver={M icroSoft Access Driver (*.mdb)};DBQ=" + localPathFile);
            connection = DriverManager.getConnection("jdbc:ucanaccess://" + localPathFile + ";singleConnection=true");
            statement = connection.createStatement();
            String query = "select * from " + tableName + " where devId = " + this.number;
            //System.out.println(new Date() + " Fusion Query:" + query);
            rs = statement.executeQuery(query);
            while (rs.next()){
                String gCode = rs.getString("Gcode"); message+="1";
                int devId = rs.getInt("DevId");message+="2";
                boolean isRecord = rs.getBoolean("IsRecord");message+="3";
                String title = rs.getString("ReservePgName");message+="4";
                String reserveChName = rs.getString("ReserveChName");
                //Channel channel = lineUp.getChannelByDescription(reserveChName);message+="5";
                Date startTime = rs.getTimestamp("Start_Time");
                Date endTime = rs.getTimestamp("End_Time");
                int attribute = rs.getInt("Attribute");message+="8";
                String etcStr = rs.getString("etcStr");message+="9";
                String fileNameNoPathNoExtension = rs.getString("ReserveFName");message+=" 10 ";

                String protocol = getProtocolFromGcode(gCode);
                int channelVirtual = getChannelVirtualFromGcode(gCode);
                int programNumber = getProgramNumberFromGcode(gCode);
                Channel channel = lineUp.getChannel(channelVirtual, programNumber);
                if ("D99-99".equals(reserveChName)) continue; // ignore the dummy records we use to wake-up Fusion for db read
                if (channel == null){
                    channel = lineUp.getChannelByDescription(reserveChName);message+="5";
                }
                if (debug) System.out.println(new Date() + " DEBUGFUSION  Fusion database record - ReservePgName [" + title + "]   ReserveFname [" + fileNameNoPathNoExtension + "]");
                
                List slots = getSlots(startTime, endTime, attribute);message+="6";
                if (slots.size() == 0){
                    System.out.println(new Date() + " WARNING: Not able to calculate slot(s) from Fusion record " + gCode + " probably outside our time horizon.");
                } else if (CaptureManager.endFusionWatchEvents){
                    // DRS 20101113 - Added else - ignore quick captures
                    Slot aSlot = (Slot)slots.get(0);
                    int durationSeconds = (int)(aSlot.end.getTimeInMillis() - aSlot.start.getTimeInMillis())/1000;
                    if (durationSeconds < 30){
                        slots.remove(0);
                    }
                }
                boolean isRecurring = slots.size() > 1; // A recurring recording never delete these since we don't ever save them
                
                Target target = null;
                String fileNameValidation = Target.getNoPathNoExtensionFilename(fileNameNoPathNoExtension);
                if (debug) System.out.println(new Date() + " DEBUGFUSION  comparing equals on [" + Target.getNoPathNoExtensionFilename(fileNameNoPathNoExtension) + "] and [" + fileNameNoPathNoExtension + "]");
                if (fileNameValidation.equals(fileNameNoPathNoExtension)){
                    target = new Target(fileNameNoPathNoExtension + "." + this.analogFileExtension, title, this.recordPath, this.analogFileExtension, protocol, Tuner.FUSION_TYPE);
                } else {
                    System.out.println(new Date() + " WARNING: file with path and/or extension was found in Fusion database.  Results will be unpredictable.");
                    target = new Target(fileNameNoPathNoExtension, title, this.recordPath, this.analogFileExtension, protocol,  Tuner.FUSION_TYPE);
                }
                for (Iterator iter = slots.iterator(); iter.hasNext();) {
                    Slot slot = (Slot) iter.next();
                    CaptureFusion capture = new CaptureFusion(slot, channel, isRecurring);
                    capture.setTarget(target);message+="11 ";
                    capture.setFusionData(gCode, "" + devId, isRecord, attribute, fileNameNoPathNoExtension, etcStr, USE_COMMANDLINE_PERSISTANCE);message+="12 "; // DRS 20190730 - Added per Terry's alternative
                    if(capture.getLastError() != null) {
                        System.out.println(new Date() + " ERROR: " + capture.getLastError());
                    }
                    System.out.println(new Date() + " Reading Fusion capture from database " + capture);
                    captures.add(capture);
                }
            }
        } catch (Exception e) {
            System.out.println(new Date() + " ERROR: TunerFusion.getCapturesFromFile: " + message + e.getMessage());
            e.printStackTrace();
        } finally {
            try { if (rs != null) rs.close(); } catch (Throwable t){}; 
            try { if (statement != null) statement.close(); } catch (Throwable t){}; 
            try { if (connection != null) connection.close(); } catch (Throwable t){}; 
        }
        return captures;
    }

    private int getChannelVirtualFromGcode(String code){
        if (code == null || code.equals("") || code.length() < 10) return 0;
        String virtualString = code.substring(5,8);
        int virtual = 0;
        try {virtual = Integer.parseInt(virtualString);} catch (Exception e){/*ignore*/}
        String firstChar = code.substring(4,5);
        int firstInt = 0;
        try {firstInt = Integer.parseInt(firstChar);} catch (Exception e){/*ignore*/}
        if (firstInt == 3 || firstInt == 8){
            virtual +=1000;
        }
        return virtual;
    }

    private int getProgramNumberFromGcode(String code){
        if (code == null || code.equals("") || code.length() < 10) return 0;
        String progNumString = code.substring(8,12);
        int progNum = 0;
        try {progNum = Integer.parseInt(progNumString, 16);} catch (Exception e){/*ignore*/}
        return progNum;
    }
    
    private String getProtocolFromGcode(String code) {
        if (code == null || code.equals("") || code.length() < 10) return "qam";
        String channelNumber = "'" + code.substring(4,5) + "'";
        if ("'0'".indexOf(channelNumber) > -1) return "analogAir";
        if ("'2'3'".indexOf(channelNumber) > -1) return "analogCable";
        if ("'5'".indexOf(channelNumber) > -1) return "8vsb"; 
        return "qam";
    }

    private List getSlots(Date startTime, Date endTime, int recurFlag) {
        ArrayList<Slot> slots = new ArrayList<Slot>();
        try {
            Slot slot = new Slot(DF.format(startTime), DF.format(endTime), -1);
            if (recurFlag == ONCE && !slot.isInThePast()){
                slots.add(slot);
            } else {
                slots = makeSlotsFromRecurring(slot, slots, recurFlag);
            }
        } catch (SlotException e) {
            e.printStackTrace();
        }
        return slots;
    }

    private ArrayList<Slot> makeSlotsFromRecurring(Slot originalSlot, ArrayList<Slot> slots, int recurFlag) {
        System.out.println(new Date() + " TunerFusion.makeSlotsFromRecurring()");
        Slot slot = originalSlot.clone();
        int dayOfWeek = slot.start.get(Calendar.DAY_OF_WEEK);
        Calendar futureCalendar = Calendar.getInstance();
        futureCalendar.add(Calendar.HOUR, 24 * OUTLOOK_DAYS);
        while(slot.start.before(futureCalendar)){
            if (slot.isInThePast()) {
                slot = slot.advanceDays(1);
                continue;
            }
            if (recurFlag == DAILY){
                slots.add(slot);
                slot = slot.clone();
            } else if (recurFlag == WEEKLY && dayOfWeek == originalSlot.start.get(Calendar.DAY_OF_WEEK)){
                slots.add(slot);
                slot = slot.clone();
            } else if (recurFlag == MON_TUE && (dayOfWeek == Calendar.MONDAY || dayOfWeek == Calendar.TUESDAY)){
                slots.add(slot);
                slot = slot.clone();
            } else if (recurFlag == WED_THU && (dayOfWeek == Calendar.WEDNESDAY || dayOfWeek == Calendar.THURSDAY)){
                slots.add(slot);
                slot = slot.clone();
            } else if (recurFlag == WEEKDAYS && (dayOfWeek == Calendar.MONDAY || dayOfWeek == Calendar.TUESDAY || dayOfWeek == Calendar.WEDNESDAY || dayOfWeek == Calendar.THURSDAY || dayOfWeek == Calendar.FRIDAY)){
                slots.add(slot);
                slot = slot.clone();
            } else if (recurFlag == WEEKENDS && (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY)){
                slots.add(slot);
                slot = slot.clone();
            }
            slot = slot.advanceDays(1);
            dayOfWeek = slot.start.get(Calendar.DAY_OF_WEEK);
        }
        return slots;
    }

    private boolean tableExists(String localPathFile, String tableName) {
        Connection connection = null;
        Statement statement = null;
        StringBuffer buf = new StringBuffer();
        try {
            //connection = DriverManager.getConnection("jdbc:odbc:Driver={M icroSoft Access Driver (*.mdb)};DBQ=" + localPathFile);
            connection = DriverManager.getConnection("jdbc:ucanaccess://" + localPathFile + ";singleConnection=true");
            if (!connection.isClosed()){
                buf.append("Connection is open | ");
                SQLWarning warning = connection.getWarnings();
                while (warning != null) {
                    buf.append("Warning: " + warning.getMessage() + " | State: " + warning.getSQLState() + " | ");
                    warning = warning.getNextWarning();
                }
            } else {
                buf.append("Connection is closed | ");
            }
            statement = connection.createStatement();
            if (!statement.isClosed()) {
                buf.append("Statement is open | ");
                SQLWarning warning = statement.getWarnings();
                while (warning != null) {
                    buf.append("Warning: " + warning.getMessage() + " | State: " + warning.getSQLState() + " | ");
                    warning = warning.getNextWarning();
                }
            } else {
                buf.append("Statement is closed | ");
            }
            String query = "select count(*) from " + tableName;
            //System.out.print(new Date() + " Check if table " + tableName + " exists in " + localPathFile + " using [" + query + "] ");
            statement.execute(query);
            //statement.close();
            //connection.close();
            //System.out.println("OK");
            return true;
        } catch (SQLException e) {
            System.out.println(new Date() + " Table or database file does not exist. " + localPathFile + " " + tableName + " " + e.getMessage() + ("\n" + new Date() + " ERROR: " + buf.toString()));
            try {
                Enumeration<Driver> drivers = DriverManager.getDrivers();
                while (drivers.hasMoreElements()) {
                    Driver aDriver = drivers.nextElement();
                    // DRS 2025401131 - Commented 2, Added 1 - Issue # 58
                    //System.out.println(new Date() + " Deregistering driver " + aDriver);
                    //DriverManager.deregisterDriver(aDriver);
                    System.out.println(new Date() + " No driver deregistration being done for " + aDriver);
                }
            } catch (Throwable t) {
                System.out.println(new Date() + " Blew up when trying to deregister. " + t.getMessage());
            }
        } finally {
            try { if (statement != null) statement.close(); } catch (Throwable t){}; 
            try { if (connection != null) connection.close(); } catch (Throwable t){}; 
        }
        return false;
    }

    public void addCaptures(List<Capture> captures, boolean writeIt) {
        int count = 0;
        for (Iterator iter = captures.iterator(); iter.hasNext();) {
            Capture capture = (Capture) iter.next();
            try{addCaptureAndPersist(capture, writeIt);} catch (CaptureScheduleException e) {System.out.println(new Date() + " ERROR: Can not add capture. " + e.getMessage());}
            System.out.println("Adding " + capture);
            count++;
        }
        System.out.println(new Date() + " Loaded " + count + " captures from the database");
    }
    
    public void addCaptureAndPersist(Capture newCapture, boolean writeIt) throws CaptureScheduleException {
        if (!slotOpen(newCapture.slot)) throw new CaptureScheduleException("Capture " + newCapture + " conflicts with " + getConflictingCapture(newCapture.slot));
        // DRS 20101024 - Added 6 - Fix adding currently airing Fusion recordings
        CaptureFusion newFusionCapture = (CaptureFusion)newCapture;
        boolean endingSoon = newFusionCapture.isNearEnd(CaptureManager.fusionLeadTime + 60);
        if (endingSoon) throw new CaptureScheduleException("Capture " + newCapture + " is too short to be scheduled, given lead time of " + CaptureManager.fusionLeadTime + " seconds.");
        if (newFusionCapture.hasStarted(CaptureManager.fusionLeadTime) && !endingSoon){
            newFusionCapture.slot.adjustStartTimeToNowPlusLeadTimeSeconds(CaptureManager.fusionLeadTime);
        } 
        captures.add(newFusionCapture);
        System.out.println(new Date() + " TunerFusion.addCapture(newCapture, " + writeIt + ")");
        boolean warn = false;
        newFusionCapture.setCommandLinePersistance(USE_COMMANDLINE_PERSISTANCE); // DRS 20190730 - Added 1 - Always use command line persistence
        newFusionCapture.persist(writeIt, warn, this);
        // newCapture.setWakeup(); // DRS 20190719 - Removed per Terry's email today
    }

    synchronized public void refreshCapturesFromOwningStore(boolean interrupt){
        boolean localCaptureListAltered = false;
        if (new Date().getTime() - this.lastRefresh < 30000) {
            //System.out.println(new Date() + " TunerFusion.refreshCapturesFromOwningStore() skipped.");
            return;
        }
        //System.out.println(new Date() + " TunerFusion.refreshCapturesFromOwningStore() processing.");
        List<Capture> fileCaptures = getCapturesFromFile(this.lineUp);
        
        /* 1 - Add captures that Fusion has that we don't */
        for (Iterator iter = fileCaptures.iterator(); iter.hasNext();) {
            Capture fileCapture = (Capture) iter.next();
            if (this.captures.contains(fileCapture)) continue;
            else {
                try {
                    System.out.println(new Date() + " Found a capture in the Fusion list that wasn't in cwhelper memory.  Adding it to cwhelper memory: " + fileCapture);
                    addCaptureAndPersist(fileCapture, false); // add capture, but don't write to the store (already there)
                    localCaptureListAltered = true;
                } catch (Exception e){
                    System.out.println(new Date() + " ERROR: problem adding capture from Fusion list.  Capture:\n" + fileCapture.getHtml(0) + "\n" + e.getMessage());
                }
            }
        }
        
        /* 2 - Remove captures the Fusion has that we don't */
        ArrayList<Capture> capturesToRemove = new ArrayList<Capture>();
        for (Iterator iter = this.captures.iterator(); iter.hasNext();) {
            Capture capture = (Capture) iter.next();
            if (capture.hasStarted()) {
                System.out.println(new Date() + " Capture " + capture.getTitle() + " not removed (it has already started).");
                continue;
            }
            if (fileCaptures.contains(capture)){
                System.out.println(new Date() + " Capture " + capture.getTitle() + " not removed (it's in the Fusion list).");
                continue;
            }
            System.out.println(new Date() + " Capture " + capture.getTitle() + ", in cwhelper memory, was not found in the Fusion list.  Removing it from cwhelper memory.");
            capturesToRemove.add(capture);
            localCaptureListAltered = true;
        }
        for (Capture capture : capturesToRemove) {
            this.removeLocalCapture(capture);
        }
        //System.out.println(new Date() + " TunerFusion.refreshCapturesFromOwningStore(): lastRefresh ");
        this.lastRefresh = new Date().getTime();
        if (localCaptureListAltered && interrupt) CaptureManager.requestInterrupt("TunerFusion.refreshCapturesFromOwningStore");
    }
    
    // Method Not Used //
    public void removeOldCaptures() {
        ArrayList<Capture> removeList = new ArrayList<Capture>();
        for (int i = 0; i < captures.size(); i++){
            Capture aCapture = captures.get(i);
            if (aCapture.hasEnded()){
                removeList.add(aCapture);
            }
        }
        for (Iterator iter = removeList.iterator(); iter.hasNext();) {
            removeCapture((Capture) iter.next());
        }
    }
    
    public void removeCapture(Capture capture) {
        for (int i = 0; i < captures.size(); i++){
            Capture aCapture = captures.get(i);
            if (aCapture.equals(capture)){
                this.removeCapture(i);
                break;
            }
        }
    }

    public void removeLocalCapture(Capture aCapture) {
        int size = captures.size();
        System.out.println(new Date() + " Removing " + (aCapture.isRecurring?"recurring":"") + " capture from cwhelper memory. " + aCapture.getTitle());
        if (aCapture.isRecurring()){
            delistCapturesMatchingFileName(aCapture.getFileName(), captures, null);
        } else {
            // aCapture.removeWakeup(); // DRS 20190719 - Removed per Terry's email today
            captures.remove(aCapture);
        }
        if (captures.size() == size){
            System.out.println(new Date() + " RemoveLocalCapture failed to remove " + aCapture + "\nCapture List:\n");
            for (int i = 0; i < captures.size(); i++){
                System.out.println(captures.get(i));
            }
        }
    }

    public void removeCapture(int j) {
        Capture aCapture = (Capture)captures.get(j);
        System.out.println(new Date() + " Removing " + (aCapture.isRecurring?"recurring":"") + " capture from the Fusion database. " + aCapture.getTitle());
        removeCaptureFromOwningStore(aCapture, true); 
        if (aCapture.isRecurring()){
            delistCapturesMatchingFileName(aCapture.getFileName(), captures, null);
        } else {
            // aCapture.removeWakeup(); // DRS 20190719 - Removed per Terry's email today
            captures.remove(j);
        }
        if (!USE_COMMANDLINE_PERSISTANCE) {
            String result =  CaptureFusion.notifyExternalApplication(USE_COMMANDLINE_PERSISTANCE); // true means use command line instead of interprocess communication
            System.out.println(new Date() + " Notified Fusion: " + result);
        }
    }
    
    public void removeAllCaptures(boolean localRemovalOnly) {
        
        ArrayList<Capture> toRemove = new ArrayList<Capture>();
        ArrayList<Capture> localRemove = new ArrayList<Capture>();
        System.out.println(new Date() + "  Removing all non-recurring captures from the Fusion database for " + this.getFullName());
        for (int i = 0; i < captures.size(); i++){
            Capture aCapture = (Capture)captures.get(i);
            if (!aCapture.isRecurring() && !localRemovalOnly){
                removeCaptureFromOwningStore(aCapture, true);
                toRemove.add(aCapture);
            } else if (localRemovalOnly){
                localRemove.add(aCapture);
            }
        }
        for (Iterator iter = toRemove.iterator(); iter.hasNext();) {
            Capture aCapture = (Capture) iter.next();
            //aCapture.removeWakeup(); // DRS 20190719 - Removed per Terry's email today
            captures.remove(aCapture);
        }
        for (Iterator iter = localRemove.iterator(); iter.hasNext();) {
            Capture aCapture = (Capture) iter.next();
            //aCapture.removeWakeup(); // DRS 20190719 - Removed per Terry's email today
            captures.remove(aCapture);
        }
        if (!USE_COMMANDLINE_PERSISTANCE) {
            String result =  CaptureFusion.notifyExternalApplication(USE_COMMANDLINE_PERSISTANCE); // true means use command line instead of interprocess communication
            System.out.println(new Date() + " Notified Fusion: " + result);
        }
    }
    
    void removeCaptureFromOwningStore(Capture capture, boolean useCommandLinePeristance) {
        if (useCommandLinePeristance) { //DRS 20190730 - New persistence through Terry's command line exe.
            int durationSeconds = 3;
            boolean useIsoDate = true;
            String sqlCommand = getDeleteSql((CaptureFusion)capture, useIsoDate);
            FusionDbTransactionCommandLine cl = new FusionDbTransactionCommandLine(sqlCommand, durationSeconds);
            boolean goodResult = cl.runProcess(); // blocks
            if (!goodResult) System.out.println(new Date() + " failed to handle [" + cl.getCommands() + "]\n" + cl.getErrors());
            else System.out.println(new Date() + " Executed [" + cl.getCommands() + "]\n" + cl.getErrors());
        } else {
            removeCaptureFromOwningStore(capture, "direct to database method");
        }
    }
    
    void removeCaptureFromOwningStore(Capture capture, String changeMethodSignature) {
        Connection connection = null;
        Statement statement = null;
        ResultSet rs = null;
        try {
            String localPathFile = TunerManager.fusionInstalledLocation + "\\Epg2List.Mdb"; // DRS 20150611 - use class field
            String tableName = "ReserveList";
            if (!this.tableExists(localPathFile, tableName )){
                throw new Exception("Could not find the database. ");
            }
            //connection = DriverManager.getConnection("jdbc:odbc:Driver={M icroSoft Access Driver (*.mdb)};DBQ=" + localPathFile);
            connection = DriverManager.getConnection("jdbc:ucanaccess://" + localPathFile + ";singleConnection=true");
            statement = connection.createStatement();
            int count = 0;
            if (capture != null) {
                String deleteSql = getDeleteSql((CaptureFusion)capture, false);
                System.out.println(new Date() + " Delete sql [" + deleteSql + "]");
                count = statement.executeUpdate(deleteSql);
                if (count == 0) throw new Exception("Could not remove capture from the database (this is ok for Fusion captures that have already started)." );
            } else { // if capture is null, remove the dummy record
                String countSql = "select count(*) from ReserveList where ReserveChName = 'D99-99'";
                ResultSet countRs = statement.executeQuery(countSql);
                if (countRs.next()){
                    int foundCount = countRs.getInt(1);
                    if (foundCount > 0){
                        StringBuffer tryBuf = new StringBuffer();
                        String deleteSql = "delete from ReserveList where ReserveChName = 'D99-99'";
                        tryBuf.append(new Date() + " Delete sql [" + deleteSql + "]  ");
                        for (int i = 0; i < 10 && count == 0 ; i++) {
                            try { Thread.sleep(1000); } catch (Exception e) {}
                            count = statement.executeUpdate(deleteSql);
                            tryBuf.append("" + count);
                        }
                        System.out.println(tryBuf.toString() + "   " + count + " dummy record removed. ");    
                    } else {
                        System.out.println(new Date() + " Dummy record count was zero.");
                    }
                } else {
                    System.out.println(new Date() + " No dummy record to remove.");
                }
            }
            
        } catch (Exception e) {
            System.out.println(new Date() + " TunerFusion.removeCaptureFromOwningStore(): " + e.getMessage());
        } finally {
            try { if (rs != null) rs.close(); } catch (Throwable t){}; 
            try { if (statement != null) statement.close(); } catch (Throwable t){}; 
            try { if (connection != null) connection.close(); } catch (Throwable t){}; 
        }
    }
    
    private String getDeleteSql(CaptureFusion capture, boolean useIsoDate) {
        String deleteSql = "(uninitialized)";
        Slot slot = capture.slot;
        if (debug) System.out.println(new Date() + " DEBUGFUSION  Fusion database record [" + capture.getFileName() + "]");
        String dbFilename = capture.target.getNoPathNoExtensionFilename();
        if (capture.isRecurring){
            if (((CaptureFusion)capture).gCode != null){
                deleteSql = "delete from ReserveList where gcode='" + ((CaptureFusion)capture).gCode + "'";
            } else { 
                System.out.println(new Date() + " ERROR: Can not find gCode for delete.");
            }
        } else {
            // DRS 20150612 - Added 2 - Work around Jackcess date format limitation toDateFormattingStart + DBDTF.format(this.endEvent) + toDateFormattingEnd
            String toTimestampFormattingStart = "TO_TIMESTAMP('";
            String toTimestampFormattingEnd = "','MM/DD/YYYY HH:MI:SS') ";
    
            StringBuffer buf = new StringBuffer();
            buf.append("delete from ReserveList ");
            // DRS 20150612 - Commented 2, Added 1
            //buf.append("where DateValue(Start_Time) = DateValue('" + DBDF.format(slot.start.getTime()) + "') "); 
            //buf.append("and TimeValue(Start_Time) = TimeValue('" + DBTF.format(slot.start.getTime())+ "') ");
            if (!useIsoDate) {
                buf.append("where Start_Time = " + toTimestampFormattingStart + DBDTFS.format(slot.start.getTime()) + toTimestampFormattingEnd);
            } else {
                buf.append(
                        "where Start_Time = " + 
                        "datevalue('" + ISODF.format(slot.start.getTime()) + "')+" + 
                        "timevalue('" + ISOTF.format(slot.start.getTime()) + "') ");
            }
            buf.append("and ReserveFName = '" + dbFilename.replaceAll("\'", "\'\'") + "' ");
            if (this.number > 0) {
                buf.append("and DevId = " + this.number);
            }
            deleteSql = new String(buf);
        }
        return deleteSql;
    }

    public String getFullName() {
        return this.id + "." + this.number;
    }

    public String getDeviceId(){
        return this.number + "";
    }

    public int getType(){
        return this.tunerType;
    }

    public void scanRefreshLineUp(){
        ((LineUpFusion)this.lineUp).scan(this);
    }
    
    public void scanRefreshLineUp(boolean useExistingFile, String signalType, int maxSeconds) throws Exception {
        scanRefreshLineUp();
    }

    public List<Capture> getCaptures(){
        refreshCapturesFromOwningStore(true);
        return this.captures;
    }

    @Override
    public void removeDefaultRecordPathFile() { /*not used in Fusion*/ }
    @Override
    public void writeDefaultRecordPathFile() { /*not used in Fusion */ }
    
    public static void main(String[] args) throws Exception {
        boolean checkDatabase = true;
        if (checkDatabase) {
            System.out.println("*_*_*_*_*_* Adding a fake tuner.");
            TunerManager.fusionInstalledLocation = "C:\\my\\dev\\eclipsewrk\\CwHelper";
            TunerFusion tf = new TunerFusion("HDTV7USB",8704,"C:\\",42,true);
            //System.out.println(tf.tableExists("C:\\my\\dev\\eclipsewrk\\CwHelper\\Epg2List.Mdb","ReserveList"));
            System.out.println("tf: " + tf);
        }
        boolean testGetFusionTuners = false;
        if (testGetFusionTuners){
            TunerManager tm = TunerManager.getInstance();
            tm.countTuner(Tuner.FUSION_TYPE, true);
            System.out.println("Found tuner(s)");
            System.out.println("============================");
            System.out.println(tm.getWebTunerList());
            System.out.println("============================");
        }
        
        boolean testGetCapturesFromDatabase = false;
        if (testGetCapturesFromDatabase){
                TunerManager tm = TunerManager.getInstance();
                tm.countTuner(Tuner.FUSION_TYPE, true);
                System.out.println("Found tuner(s)");
                System.out.println("============================");
            System.out.println(tm.getWebChannelList());
            System.out.println("============================");
            
            for (Iterator iterator = tm.iterator(); iterator.hasNext();) {
                TunerFusion tuner = (TunerFusion) iterator.next();
                System.out.println(tuner.lineUp);
                List captures = tuner.getCaptures();
                
                for (Iterator iter2 = captures.iterator(); iter2.hasNext();) {
                    Capture capture = (Capture) iter2.next();
                    System.out.println("A Capture: \n" + capture);
                }
                
            }
        }
    }

}
