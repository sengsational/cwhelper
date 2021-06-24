/*
 * Created on Sep 7, 2009
 *
 */
package org.cwepg.hr;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.cwepg.svc.FusionCommandLine;
import org.cwepg.svc.FusionDbTransactionCommandLine;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.User32;

public class CaptureFusion extends Capture {

    private static final SimpleDateFormat DBDTFS = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
    private static final SimpleDateFormat GCDTF = new SimpleDateFormat("yyyyMMddHHmmss");
    private static final SimpleDateFormat ISODTF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final int QC_SECONDS = 5;
    protected String gCode;
    private String devId;
    private String watch;
    private int attribute;
    private String reserveFName;  // file name with no path
    private String etcStr;
    private boolean useCommandLinePersistance = false; // DRS 20190730 - Added this and supporting as requested by Terry

    public CaptureFusion(Slot slot, Channel channel, boolean isRecurring) {
        super(slot, channel);
        this.isRecurring = isRecurring;
    }

    public CaptureFusion(CaptureDetails details, Tuner tuner) {
        this.slot = new Slot(details.scheduledStart, details.scheduledEnd);
        this.channel = tuner.lineUp.getChannelByKey(details.channelKey); // channelKey is like "27.3:1-8vsb" (27 is rf)
        //this.channel = tuner.lineUp.getChannelByDescription(details.channelName);
    }

    public void setFusionData(String gCode, String devId, boolean isRecord, int attribute, String fileNameNoPath, String etcStr, boolean useCommandLinePersistance) {
        this.gCode = gCode;
        this.devId = devId;
        if (isRecord) watch="N";
        else watch="Y";
        this.attribute = attribute;
        this.reserveFName = fileNameNoPath;
        this.etcStr = etcStr;
        this.useCommandLinePersistance = useCommandLinePersistance;
    }

    public void setCommandLinePersistance(boolean useCommandLinePersistance) {
        this.useCommandLinePersistance = useCommandLinePersistance;
    }
    
    @Override
    public void persist(boolean writeIt, boolean warn, Tuner tuner) throws CaptureScheduleException {
        if (writeIt && !isRecurring){
            fixFields();
            String mdbFileName = "Epg2List.Mdb";
            String localPathFile = TunerManager.fusionInstalledLocation + "\\" + mdbFileName;
            String tableName = "ReserveList";
            boolean isRecord = true; if (this.watch == "Y") isRecord = false;
            
            if (!useCommandLinePersistance) {
                Connection connection = null;
                Statement statement = null;
                ResultSet rs = null;
    
                try {
                    connection = DriverManager.getConnection("jdbc:ucanaccess://" + localPathFile + ";singleConnection=true");
                    
                    //DRS 20190712 - Added 1 + try/catch - find ChId for later insertion 
                    long chId = -1;
                    try {
                        statement = connection.createStatement();
                        String selectQuery = "select ChId from ChannelList where ChNm like '%" + this.channel.alphaDescription + "%'";
                        rs = statement.executeQuery(selectQuery);
                        if (rs.next()) {
                            chId = rs.getLong(1);
                        }
                        System.out.println(new Date() + " Executing [" + selectQuery + "] and resulting ChId [" + chId + "]");
                    } catch (Throwable t) {
                        System.out.println(new Date() + " Failed to access ChId: " + t.getMessage() + " Continuing to persist the record anyway.");
                    } finally {
                        try { if (statement != null) statement.close(); } catch (Throwable t){}; 
                    }
                    
                    statement = connection.createStatement();
                    for (int i = 1; i < 5; i++){
                        rs = statement.executeQuery("select * from " + tableName + " where gCode = '" + this.gCode + "'");
                        if (rs.next()){
                            System.out.print(new Date() + " This key already exists [" + this.gCode + "], so ");
                            this.gCode = this.gCode.substring(0,this.gCode.length()-1) + i;
                            System.out.println("trying this key [" + this.gCode + "]");
                        } else break;
                    }
                    boolean useIsoDate = false;
                    System.out.print(new Date() + " " + this.getInsertSql(tableName, slot, isRecord, this.gCode, chId, "!", useIsoDate));
                    int count = statement.executeUpdate(this.getInsertSql(tableName, slot, isRecord, this.gCode, chId, "!", useIsoDate));
                    System.out.println(" OK. " + count + " inserted into " + mdbFileName);
                    // DRS 20101113 - Added quick capture if watch just inserted
                    if (!isRecord && CaptureManager.endFusionWatchEvents){
                        Slot shortSlot = slot.clone();
                        shortSlot.adjustEndTimeSeconds(-QC_SECONDS);
                        shortSlot.convertToQuickEndCapture(QC_SECONDS - 1);
                        String quickCaptureGcode = this.gCode.substring(0,this.gCode.length()- 12) + shortSlot.getStartTimeString() + shortSlot.getHourMinuteSecondDurationString();
                        System.out.print(new Date() + " " + this.getInsertSql(tableName, shortSlot, true, quickCaptureGcode, chId, "!", useIsoDate));
                        count = statement.executeUpdate(this.getInsertSql(tableName, shortSlot, true, quickCaptureGcode, chId, "!", useIsoDate));
                        System.out.println(" OK. " + count + " inserted into " + mdbFileName);
                    }
                    statement.close();
                    connection.close();
                } catch (SQLException e) {
                    System.out.println(" ERROR: CaptureFusion.persist:" + e.getMessage());
                    System.err.println(new Date() + " CaptureFusion.persist:");
                    e.printStackTrace();
                } finally {
                    try { if (rs != null) rs.close(); } catch (Throwable t){}; 
                    try { if (statement != null) statement.close(); } catch (Throwable t){}; 
                    try { if (connection != null) connection.close(); } catch (Throwable t){}; 
                }
                //String result = notifyExternalApplication(true); // true means use command line instead of interprocess communication
                String result = notifyExternalApplication(false); // true means use command line instead of interprocess communication
                System.out.println(new Date() + " Notified Fusion: " + result);
            } else { //DRS 20190730 - New persistence through Terry's command line exe.
                int durationSeconds = 3;
                boolean useIsoDate = true;
                String sqlCommand = this.getInsertSql(tableName, slot, isRecord, this.gCode, -1, "", useIsoDate);
                FusionDbTransactionCommandLine cl = new FusionDbTransactionCommandLine(sqlCommand, durationSeconds);
                boolean goodResult = cl.runProcess(); // blocks
                if (!goodResult) System.out.println(new Date() + " failed to handle [" + cl.getCommands() + "]\n" + cl.getErrors());
                else System.out.println(new Date() + " Executed [" + cl.getCommands() + "]\n" + cl.getErrors());
            }
        }
    }
    
    private String getInsertSql(String tableName, Slot aSlot, boolean isRecord, String gCode, long chId, String bangPrefix, boolean useIsoDate) {
        // DRS 20150611 - Added 2 - Work around Jackcess date format limitation toDateFormattingStart + DBDTF.format(this.endEvent) + toDateFormattingEnd
        String toDateFormattingStart = "TO_TIMESTAMP('";
        String toDateFormattingEnd = "','MM/DD/YYYY HH:MI:SS'),";

        StringBuffer setBuf = new StringBuffer();
        StringBuffer varBuf = new StringBuffer();
        setBuf.append("("); varBuf.append("(");
        setBuf.append("'" + gCode + "',"); varBuf.append("Gcode,");
        if (chId > -1) {setBuf.append("" + chId + ","); varBuf.append("ChId,");} // DRS20190712 - Added 1, Terry said it might help Fusion "see" this faster
        setBuf.append("" + devId + ","); varBuf.append("DevId,");
        setBuf.append(isRecord + ","); varBuf.append("IsRecord,");
        setBuf.append("'" + target.getTitle().replaceAll("\'", "\'\'").replaceAll("!", bangPrefix + "!") + "',"); varBuf.append("ReservePgName,"); // DRS20190722 - Added "!" protection
        setBuf.append("'" + this.channel.alphaDescription +  "',"); varBuf.append("ReserveChName,");
        int watchAdjustment = 0;
        if (!isRecord) watchAdjustment = CaptureFusion.QC_SECONDS * 1000;
        if (!useIsoDate) {
            setBuf.append(toDateFormattingStart + DBDTFS.format(aSlot.start.getTime()) + toDateFormattingEnd); varBuf.append("Start_Time,");
            setBuf.append(toDateFormattingStart + DBDTFS.format(new Date(aSlot.end.getTime().getTime() + 1000 - watchAdjustment)) + toDateFormattingEnd); varBuf.append("End_Time,");
        } else {
            setBuf.append("'" + ISODTF.format(aSlot.start.getTime()) + "',"); varBuf.append("Start_Time,");
            setBuf.append("'" + ISODTF.format(new Date(aSlot.end.getTime().getTime() + 1000 - watchAdjustment)) + "',"); varBuf.append("End_Time,");
        }
        
        setBuf.append(this.attribute + ","); varBuf.append("Attribute,");
        setBuf.append("'" + reserveFName.replaceAll("\'", "\'\'").replaceAll("!", bangPrefix + "!") + "',"); varBuf.append("ReserveFName,"); // DRS20190722 - Added "!" protection
        setBuf.append("'" + etcStr + "'"); varBuf.append("etcStr");
        setBuf.append(")"); varBuf.append(")");
        return new String("insert into " + tableName + " " + varBuf.toString() + " values " + setBuf.toString());
    }
    
    private void fixFields(){
        String progress = "0";
        try {
            NumberFormat nf = NumberFormat.getInstance();
            nf.setMinimumIntegerDigits(4);
            nf.setGroupingUsed(false);
            StringBuffer buf = new StringBuffer();
            if (this.gCode == null){
                progress +="1";
                buf.append("M001");
                int adder = -1;
                if (this.channel.protocol.equals("analogAir")){
                    adder = 0;
                } else if (this.channel.protocol.equals("analogCable")){
                    adder = 2000;
                } else if (this.channel.protocol.equals("8vsb")){ 
                    adder = 5000; // air digital
                } else {
                    adder = 7000; // cable digital (qam)
                }
                int channelValue = channel.virtualChannel;
                if (channelValue == 0){
                    channelValue = Integer.parseInt(channel.frequency);
                }
                if (channelValue > 1999) adder = 0;
                buf.append(nf.format(adder + channelValue));
                int pidint = Integer.parseInt(channel.pid); // ie 13
                buf.append(Integer.toHexString( 0x10000 | pidint).substring(1).toUpperCase()); // ie 000D
                buf.append(GCDTF.format(this.slot.start.getTime()));
                //DRS 20101113 - Added 'if', slot clone, and seconds (instead of 00 default) - quick captures
                Slot aSlot = this.slot.clone();
                if (this.target.isWatch() && CaptureManager.endFusionWatchEvents){
                    aSlot.adjustEndTimeSeconds(-QC_SECONDS);
                }
                buf.append(aSlot.getHourMinuteSecondDurationString());
                this.gCode = new String(buf);
                progress +=".";

            }
            if (this.devId == null){
                progress +="2";
                this.devId = "" + this.channel.tuner.number;
                progress +=".";
            }
            if (this.watch == null){
                progress +="3";
                this.watch = "N";
                if (this.target.isWatch()){
                    this.watch = "Y";
                }
                progress +=".";
            }
            if (this.attribute == 0){
                this.attribute = 1536;
            }
            if (this.reserveFName == null){
                progress +="4";
                this.reserveFName = this.target.getNoPathNoExtensionFilename();
                progress +=".";
            }
            if (this.etcStr == null){
                progress +="5";
                this.etcStr = this.target.machineName;
                progress +=".";
            }
        } catch (Exception e) {
            System.out.println(new Date() + " ERROR: CaptureFusion.fixFields failed (" + progress + ")");
        }
    }

    public static String notifyExternalApplication() {
        StringBuffer buf = new StringBuffer("");
        try {
            User32.HWND fusionmsg = User32.INSTANCE.FindWindow("TFusionHdtvTray","HDTV Tray");
            int wmUserPlusOffset = 0x400 + 5014;
            User32.WPARAM two = new User32.WPARAM(2L);
            User32.LPARAM zero = new User32.LPARAM(0L);
            //boolean fusResult = User32.INSTANCE.PostMessage(fusionmsg, wmUserPlusOffset, two, zero);
            User32.INSTANCE.PostMessage(fusionmsg, wmUserPlusOffset, two, zero);
            //buf.append(" fusResult was " + fusResult + "\n");
            int winapiLastError = Native.getLastError();
            buf.append(" winapiLastError was " + winapiLastError + "\n");
        } catch (RuntimeException e) {
            e.printStackTrace();
            buf.append(" RuntimeException: " + e.getMessage());
        } 
        return new String(buf);
    }

    // DRS 20150613 - Added alternative method
    public static String notifyExternalApplication(boolean useCommandLineOption) {
        //if (1==1) return "Fusion notification turned off."; // DRS 20190712 - Added 1 - Turned of all Fusion notification per Terry's email today
        
        if(!useCommandLineOption) return notifyExternalApplication();
        StringBuffer buf = new StringBuffer("Notify Recap: ");
        
        // Delete the D99 entry in the table if it is there.
        try {
            new TunerFusion("HDTV7USB",-1,"c:\\",42,false).removeCaptureFromOwningStore(null, true);
            //System.out.println(new Date() + " INFO: Deleted the dummy record. ");
            //buf.append(" Early delete of D99 ok.");
        } catch (Throwable t) {
            //System.out.println(new Date() + " INFO: Database was free of dummy record. ");
            //buf.append(" Early delete not required. No D99 record found.");
        }

        // Copy the dummy file to a temp directory
        String dummyTempFilePath = System.getProperty("java.io.tmpdir") + "dummy.tvpi";
        boolean copyResult = false;
        try {
            DFile dummyFileInOurDataPath = new DFile(CaptureManager.dataPath + "dummy.tvpi");
            copyResult = dummyFileInOurDataPath.copyTo(new File(dummyTempFilePath));
            if (!copyResult) {
                //System.out.println(new Date() + "copied to [" + dummyTempFilePath + "]");
                System.out.println(new Date() + " ERROR: Unable to copy to " + dummyTempFilePath + " from " +  CaptureManager.dataPath + "dummy.tvpi ");
                buf.append(" Copy of dummy.tvpi file failed.");
            } else {
                buf.append(" Copy of dummy.tvpi file worked.");
            }
            
        } catch (Throwable t) {
            System.out.println(new Date() + " ERROR: Unable to copy to " + dummyTempFilePath + " from " +  CaptureManager.dataPath + "dummy.tvpi " + t.getMessage() + " ");
            buf.append(" Copy of dummy.tvpi file failed. " + t.getMessage());
        }
        
        if (copyResult) {
            // Run the fusion command line
            int durationSeconds = 60;
            FusionCommandLine cl = new FusionCommandLine(dummyTempFilePath, durationSeconds);
            boolean goodResult = cl.runProcess(); // blocks
            if (!goodResult){
                System.out.println(new Date() + " ERROR: Failed to handle " + cl.getCommands() + "\n Command line errors: " + cl.getErrors() + " ");
                buf.append(" FusionHdtvTray.exe with dummy.tvpi failed." );
            } else {
                buf.append(" FusionHdtvTray.exe with dummy.tvpi worked. Leaving dummy record." );
            }
            
            /*
            // Delete the entry in the table
            try {
                new TunerFusion("HDTV7USB",-1,"c:\\",42,false).removeCaptureFromOwningStore(null);
            } catch (Throwable t) {
                System.out.println(new Date() + " ERROR: Could not delete the dummy record. " + t.getMessage() + " ");
            }
            */
        } else {
            buf.append(" FusionHdtvTray.exe interaction cancelled"); // DRS 20190709 - Added so Terry could remove the dummy file to prevent running the Fusion command line.
        }
        return new Date() + new String(buf);
        
    }

    
    //DRS 20101024 - Added method
    public boolean hasStarted(int fusionLeadTime) {
        if (this.hasEnded()) return false;
        Calendar nowCal = Calendar.getInstance();
        nowCal.add(Calendar.SECOND, fusionLeadTime);
        if (slot.startsAfter(nowCal)) return false;
        return true;
    }

    @Override
    public void interrupt(){
        // removeWakeup(); // DRS 20190719 - Removed per Terry's email today
        removeIcon();
    }

    @Override
    public int getTunerType() {
        return Tuner.FUSION_TYPE;
    }

    @Override
    public void setFileName(String fileName) throws Exception {
        this.target.setFileName(fileName, null, null, null, this.getTunerType(), true, null);
        if (this.target.isWatch()) this.watch = "Y";
        else this.watch = "N";
        this.reserveFName = this.target.getNoPathNoExtensionFilename();
    }

    /**************** UNUSED OVERRIDES ***********************/
    @Override
    public boolean addIcon() {return true;} // nothing to do in Fusion Land
    @Override
    public boolean removeIcon() { return true;} // nothing to do in Fusion Land
    @Override
    public void configureDevice() throws Exception { return;} // nothing to do in Fusion Land
    @Override
    public void deConfigureDevice() throws Exception {return;} // nothing to do in Fusion Land
    @Override
    public String getSignalQualityData() { return null;} // nothing to do in Fusion Land
    @Override
    public int getNonDotCount() {
        return 0; // no data in this tuner type
    }
    @Override
    public void run() {return;} // nothing to do in Fusion Land


    /**
     * @param args
     */
    public static void main(String[] args) throws Exception {
        TunerManager tm = TunerManager.getInstance();
        Tuner tuner = null;

        boolean testTunerDefinition = false;
        if (testTunerDefinition){
            tm.countTuner(Tuner.FUSION_TYPE, true);
            if (tm.tuners.size() > 0){
                System.out.println("Found tuner(s)");
            } else {
                System.out.println("*_*_*_*_*_* Adding a fake tuner.");
                new TunerFusion("HDTV7USB",8704,"c:\\",42,true);
            }
            tuner = tm.getTuner(0);
        }
        
        boolean testDummyRecordRemoval = true;
        if (testDummyRecordRemoval)  System.out.println("main: " + CaptureFusion.notifyExternalApplication(true));
        
        
        boolean testPrintChannels = false;
        if (testPrintChannels){
            System.out.println("============================");
            System.out.println(tm.getWebChannelList());
            System.out.println("============================");
        }

        boolean testCaptureCreation = false;
        if (testCaptureCreation){
            System.out.println("Making capture for tuner of type " + tuner.getType());
            Slot slot = new Slot("3/21/2016 11:00", "30");
            String channelName = "12.4";
            String protocol = "8vsb";
            System.out.println("channels" + tuner.lineUp.channels);
            List captures = tm.getAvailableCapturesForChannelNameAndSlot(channelName, slot, protocol);
            Capture aCapture = null;
            
            if (captures != null && captures.size() > 0){
                Capture capture = (Capture)captures.get(0);
                if (capture != null){
                    //capture.setTarget(new Target("c:\\temp.ts", "Some TV Program Title"));
                    capture.setTarget(new Target("watch", "Some TV Program Title", null, null, capture.getChannelProtocol(), tuner.getType()));
                    System.out.println(capture);
                    tuner.addCaptureAndPersist(capture, true);
                } else {
                    System.out.println("Last Reason: " + tm.getLastReason());
                }
            } else {
                System.out.println("Didn't get anything. " + captures.size());
                Channel aChannel = new ChannelDigital("12.4",tuner, 1D, "8vsb");
                aCapture = new CaptureFusion(slot, aChannel, false);
                aCapture.setTarget(new Target("watch", "Some TV Program Title", null, null, aCapture.getChannelProtocol(), tuner.getType()));
                System.out.println(aCapture);
                tuner.addCaptureAndPersist(aCapture, true);
            }
            
            System.out.println("main: Done with capture create test.");
            
            boolean testDelete = false;
            if (testDelete) {
                System.out.println("main: Testing delete");
                tuner.removeCapture(aCapture);
            }
            
            boolean testConflict = false;
            if (testConflict){
                Slot slot2 = new Slot("3/18/2011 19:00", "60");
                String channelName2 = "23.3:1";
                String protocol2 = "8vsb";
                List captures2 = tm.getAvailableCapturesForChannelNameAndSlot(channelName2, slot2, protocol2);
                if (captures != null && captures2.size() > 0){
                    Capture capture2 = (Capture)captures2.get(0); 
                    if (captures2 != null){
                        capture2.setTarget(new Target("c:\\temp2.ts", "Some TV Program Title", null, null, "qam", tuner.getType()));
                        System.out.println(capture2);
                        tuner.addCaptureAndPersist(capture2, false);
                    } 
                }
            }
        }
    }
}
