/*
 * Created on Aug 29, 2008
 *
 */
package org.cwepg.hr;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.StringTokenizer;

public class CaptureDetails implements Comparable, Cloneable {
    private static final int FIELDS = 0;
    private static final int VALUES = 1;
    private static final SimpleDateFormat DBDTF = new SimpleDateFormat("MM/dd/yyyy HH:mm");
    private static final SimpleDateFormat GCDTF = new SimpleDateFormat("yyyyMMddHHmm");



    /* DATABASE FIELDS */
    String tableKey = ""; // start time YYYYmmDDHHMM || tunerName || channelKey
    String tunerName = "";
    String channelKey = ""; 
    String channelName = ""; //like KUOW
    Date scheduledStart;
    Date startEvent;
    Date scheduledEnd;
    Date endEvent;
    String targetFile = "";
    String title = "";
    String machineName = "";

    String tunch = "-1";
    String tunlock = "-1";
    String tunss = "-1";
    String tunsnq = "-1";
    String tunseq = "-1";
    String tundbg = "-1";
    String devresync = "-1";
    String devoverflow = "-1";
    String tsbps = "-1";
    String tsut = "-1";
    String tste = "-1";
    String tsmiss = "-1";
    String tscrc = "-1";
    String fltbps = "-1";
    String netpps = "-1";
    String neterr = "-1";
    String netstop = "-1";
    String devbps = "-1";
    private CwEpgChannelRow cwEpgChannelRow;
    static final String[] fieldNames = {
        "tableKey",
        "tunerName","channelKey","channelName","scheduledStart","startEvent",
        "scheduledEnd","targetFile","title","machineName","endEvent",
        "tunch","tunlock","tunss","tunsnq","tunseq",
        "tundbg","devresync","devoverflow","tsbps","tsut",
        "tste","tsmiss","tscrc","fltbps","netpps",
        "neterr","netstop"};
    static final int[] sortOrder = {1,2,3,7,8,4,5,6,10,11,12,13,14,21,22,23,24,25,26,27,15,16,17,18,19,20,9};

    public Object clone() throws CloneNotSupportedException { 
        return super.clone(); 
    } 
    
    // for create from active capture
    public CaptureDetails(Capture capture) {
        this.tunerName = capture.channel.tuner.getFullName();
        this.channelKey = capture.channel.getChannelKey();
        this.tableKey = capture.getStartTimeString() + "|" + this.tunerName + "|" + this.channelKey;
        this.channelName = capture.channel.alphaDescription;
        this.targetFile = capture.target.getFileNameOrWatch();
        this.title = capture.getTitle();
        this.machineName = capture.target.machineName;
        Slot slot = capture.getSlot(); //it's a clone now so have at it!
        slot.end.add(Calendar.SECOND, 1); //tidy up end time
        this.scheduledStart = slot.start.getTime();
        this.scheduledEnd = slot.end.getTime();
    }

    // for create from database
    public CaptureDetails() {
    }

    public void insertCaptureStartEvent() {
        boolean writeToDatabase = true;
        String[] insertData = getCaptureInsertData(true, writeToDatabase);
        executeDatabaseFunction("insert into", insertData[FIELDS], insertData[VALUES]);
    }

    public void updateCaptureEndEvent(String signalDetails, int nonDotCount) {
        loadDetails(signalDetails); // instance variables are set here, such as tsmiss
        this.endEvent = new Date(new Date().getTime() + 15000); // Add 15 seconds to the end time because it truncates seconds 
        String[] updateData = getSignalQualityUpdateData(true);
        executeDatabaseFunction("update", updateData[FIELDS], updateData[VALUES]);
        // DRS 20170215 - Added 'if'
        try {
            if (Integer.parseInt(tsmiss) > 99 || nonDotCount > 20) {
                Emailer emailer = CaptureManager.getEmailer();
                if (emailer != null){
                    if (emailer.isValid()){
                        emailer.sendWarningEmail(tunerName, channelKey, targetFile, tsmiss, nonDotCount);
                    } else {
                        throw new Exception("emailer was not valid.");
                    }
                } else{
                    throw new Exception("emailer was null.");
                }
            }
        } catch (Throwable t) {
            System.out.println(new Date() + " ERROR: Could not parse signal quality miss count. " + t);
        }
    }
    
    public void insertAll(){
        boolean writeToDatabase = true;
        String[] insertData = getCaptureInsertData(true, writeToDatabase);
        executeDatabaseFunction("insert into", insertData[FIELDS], insertData[VALUES]);
        String[] updateData = getSignalQualityUpdateData(true);
        executeDatabaseFunction("update", updateData[FIELDS], updateData[VALUES]);
    }

    public String[] getCaptureInsertData(boolean useNow, boolean writeToDatabase) {
        String toDateFormattingStart = "TO_DATE('";
        String toDateFormattingEnd = "','MM/DD/YYYY HH24:MI')~";
        if (!writeToDatabase) {
            toDateFormattingStart = "'";
            toDateFormattingEnd = "'~";
        }
        Date dateToUse = this.startEvent;
        if (useNow){
            dateToUse = new Date();
        } 
        StringBuffer fields = new StringBuffer();
        StringBuffer values = new StringBuffer();
        fields.append("tableKey~");values.append("'" + this.tableKey + "'~");
        fields.append("tunerName~");values.append("'" + this.tunerName + "'~");
        fields.append("channelKey~");values.append("'" + this.channelKey + "'~");
        fields.append("channelName~");values.append("'" + this.channelName + "'~");
        if (this.scheduledStart != null){
            fields.append("scheduledStart~"); values.append(toDateFormattingStart + DBDTF.format(this.scheduledStart) + toDateFormattingEnd);
        }
        if (dateToUse != null){
            fields.append("startEvent~");values.append(toDateFormattingStart + DBDTF.format(dateToUse) + toDateFormattingEnd);
        }
        if (this.scheduledEnd != null){
            fields.append("scheduledEnd~");values.append(toDateFormattingStart + DBDTF.format(this.scheduledEnd) + toDateFormattingEnd);
        }
        if (this.targetFile != null){
            fields.append("targetFile~");values.append("'" + this.targetFile.replaceAll("\'", "\'\'") + "'~");
        }
        if (this.title != null){
            fields.append("title~");values.append("'" + this.title.replaceAll("\'", "\'\'") + "'~");
        }
        if (this.machineName != null){
            //DRS 20200802 - truncate machineName to 15 max
            if (this.machineName.length() > 15) {
                this.machineName.substring(0, 15);
            }
            fields.append("machineName");values.append("'" + this.machineName.replaceAll("\'", "\'\'") + "'");
        }
        String[] result = new String[2];
        result[FIELDS] = fields.toString();
        result[VALUES] = values.toString();
        return result;
    }
    
    public String[] getSignalQualityUpdateData(boolean writeToDatabase) {
        String toDateFormattingStart = "TO_TIMESTAMP('";
        String toDateFormattingEnd = "','MM/DD/YYYY HH:MI')~";
        if (!writeToDatabase) {
            toDateFormattingStart = "'";
            toDateFormattingEnd = "'~";
        }
        StringBuffer fields = new StringBuffer();
        StringBuffer values = new StringBuffer();
        if (this.endEvent != null){
            fields.append("endEvent~");values.append(toDateFormattingStart + DBDTF.format(this.endEvent) + toDateFormattingEnd);
        } else {
           fields.append("endEvent~");values.append(toDateFormattingStart + "01/01/1900 00:00" + toDateFormattingEnd);
        }
        //DRS 20150611 - Protect from wide tunlock  "tunlock='qam256-393000000'"
        if (tunlock!=null && tunlock.length() > 15) tunlock = tunlock.substring(0,15);
        fields.append("tunch~");values.append("'" + tunch + "'~");
        fields.append("tunlock~");values.append("'" + tunlock + "'~");
        fields.append("tunss~");values.append(tunss + "~");
        fields.append("tunsnq~");values.append(tunsnq + "~");
        fields.append("tunseq~");values.append(tunseq + "~");
        fields.append("tundbg~");values.append("'" + tundbg + "'~");
        fields.append("devresync~");values.append(devresync + "~");
        fields.append("devoverflow~");values.append(devoverflow + "~");
        fields.append("tsbps~");values.append(tsbps + "~");
        fields.append("tsut~");values.append(tsut + "~");
        fields.append("tste~");values.append(tste + "~");
        fields.append("tsmiss~");values.append(tsmiss + "~");
        fields.append("tscrc~");values.append(tscrc + "~");
        fields.append("fltbps~");values.append(fltbps + "~");
        fields.append("netpps~");values.append(netpps + "~");
        fields.append("neterr~");values.append(neterr + "~");
        fields.append("netstop");values.append(netstop);
        String[] result = new String[2];
        result[FIELDS] = fields.toString();
        result[VALUES] = values.toString();
        return result;
    }

    
    void executeDatabaseFunction(String dbFunction, String updateFields, String updateValues) {
        Connection connection = null;
        Statement statement = null;
        try {
            //connection = DriverManager.getConnection("jdbc:odbc:Driver={M icroSoft Access Driver (*.mdb)};DBQ=" + CaptureManager.dataPath + CaptureDataManager.mdbFileName);
            connection = DriverManager.getConnection("jdbc:ucanaccess://" + CaptureManager.dataPath + CaptureDataManager.mdbFileName + ";singleConnection=true");
            statement = connection.createStatement();
            //,TO_DATE('03/31/2015 22:58','MM/DD/YYYY HH24:MI')
            String query = null;
            if (dbFunction.equalsIgnoreCase("update")){
                String nameValuePairs = getNameValuePairsFromFieldsAndValues(updateFields, updateValues);
                query = dbFunction + " " + CaptureDataManager.tableName + " set " + nameValuePairs + " where tableKey = '" + tableKey + "'";
            } else if (dbFunction.equalsIgnoreCase("insert into")){
                updateValues = updateValues.replaceAll("~",",");
                updateFields = updateFields.replaceAll("~",",");
                query = dbFunction + " " + CaptureDataManager.tableName + " (" + updateFields + ") values (" + updateValues + ")";
            }
            System.out.println(new Date() + " updating data using [" + query + "]");
            int updatedCount = statement.executeUpdate(query);
            if (updatedCount != 1){
                System.out.println(new Date() + "updating data - WARNING - unexpected result from update:  Records updated = " + updatedCount);
            } else {
                //System.out.println(new Date() + "updating data - updated " + updatedCount + " record.");
            }
            statement.close();
            connection.close();
        } catch (SQLException e) {
            System.out.println(" - ERROR");
            System.out.println(new Date() + " CaptureDetails.updateCaptureDetails:" + e.getMessage());
            System.err.println(new Date() + " CaptureDetails.updateCaptureDetails:");
            e.printStackTrace();
        } finally {
            try { if (statement != null) statement.close(); } catch (Throwable t){}; 
            try { if (connection != null) connection.close(); } catch (Throwable t){}; 
        }
    }

    public void setFromDb(ResultSet rs, boolean debug) {
        try {
            tableKey = rs.getString("tableKey");
            tunerName = rs.getString("tunerName");
            channelKey = rs.getString("channelKey");
            channelName = rs.getString("channelName");
            scheduledStart = rs.getTimestamp("scheduledStart");
            startEvent = rs.getTimestamp("startEvent");
            scheduledEnd = rs.getTimestamp("scheduledEnd");
            endEvent = rs.getTimestamp("endEvent");
            targetFile = rs.getString("targetFile");
            title = rs.getString("title");
            machineName = rs.getString("machineName");
            tunch = rs.getString("tunch");
            tunlock = rs.getString("tunlock");
            tunss = rs.getString("tunss");
            tunsnq = rs.getString("tunsnq");
            tunseq = rs.getString("tunseq");
            tundbg = rs.getString("tundbg");
            devresync = rs.getString("devresync");
            devoverflow = rs.getString("devoverflow");
            tsbps = rs.getString("tsbps");
            tsut = rs.getString("tsut");
            tste = rs.getString("tste");
            tsmiss = rs.getString("tsmiss");
            tscrc = rs.getString("tscrc");
            fltbps = rs.getString("fltbps");
            netpps = rs.getString("netpps");
            neterr = rs.getString("neterr");
            netstop = rs.getString("netstop");
            if (debug) System.out.println("key:" + tableKey + ", scheduledStart:" + scheduledStart + ", tunerName:" + tunerName + ", channelKey:" + channelKey + ", tunsnq:" + tunsnq + ", tsmiss:" + tsmiss);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // DRS 20101024 - Commented Method
    //public void setStartTimeToNowPlus(int seconds) {
    //    this.scheduledStart = new Date(new Date().getTime() + (seconds * 1000));
    //}
    
    public void setFromConversionDb(ResultSet rs) {
        try {
            Date startTime = rs.getTimestamp("STARTTIME");
            Date endTime = rs.getTimestamp("ENDTIME");
            
            this.tunerName = rs.getString("TunerId");
            this.channelKey = "" + rs.getInt("PHY") + "." + rs.getInt("SUB") + ":" + rs.getInt("ANT");
            this.tableKey = GCDTF.format(startTime) + "|" + this.tunerName + "|" + this.channelKey;
            this.channelName = rs.getString("CHANNEL");
            this.scheduledStart = startTime;
            this.scheduledEnd = endTime;
            this.startEvent = startTime;
            this.endEvent = endTime;
            this.targetFile = rs.getString("FILENAME");
            this.title = rs.getString("TITLE");
            this.machineName = rs.getString("COMPUTERNAME");

            this.tunch = rs.getString("tunch");
            this.tunlock = rs.getString("tunlock");
            this.tunss = rs.getString("tunss");
            this.tunsnq = rs.getString("tunsnq");
            this.tunseq = rs.getString("tunseq");
            this.tundbg = rs.getString("tundbg");
            this.devresync = rs.getString("devresync");
            this.devoverflow = rs.getString("devoverflow");
            this.tsbps = rs.getString("tsbps");
            this.tsut = rs.getString("tsut");
            this.tste = rs.getString("tste");
            this.tsmiss = rs.getString("tsmiss");
            this.tscrc = rs.getString("tscrc");
            this.fltbps = rs.getString("fltbps");
            this.netpps = rs.getString("netpps");
            this.neterr = rs.getString("neterr");
            this.netstop = rs.getString("netstop");
        } catch (Exception e){
            e.printStackTrace();
        }
        
    }

    
    public String getFieldsForTableCreate() {
        String statement = "" +
        "tableKey varchar(50)," +
        "tunerName varchar(20)," +
        "channelKey varchar(20)," + 
        "channelName varchar(15)," + 
        "scheduledStart datetime," + 
        "startEvent datetime," + 
        "scheduledEnd datetime," + 
        "endEvent datetime," + 
        "targetFile varchar(255)," + 
        "title varchar(120)," + 
        "machineName varchar(15)," + 
        "tunch varchar(15)," +
        "tunlock varchar(15)," +
        "tunss int," +
        "tunsnq int," +
        "tunseq int," +
        "tundbg varchar(15)," +
        "devresync int," +
        "devoverflow int," +
        "tsbps int," +
        "tsut int," +
        "tste int," +
        "tsmiss int," +
        "tscrc int," +
        "fltbps int," +
        "netpps int," +
        "neterr int," +
        "netstop int" + 
        "";
        return statement;
    }

    private void loadDetails(String data) {
        if (data == null || data.equals("")) {
            //System.out.println(new Date() + "CaptureDetails passed empty data");
            return;
        }
        data = data.replace('\n', ':');
        data = replaceColonWithDashInData("ch=", data);
        data = replaceColonWithDashInData("lock=", data);
        StringTokenizer tok = new StringTokenizer(data, ":");
        while (tok.hasMoreElements()) {
            String category = tok.nextToken().trim();
            String allData = tok.nextToken().trim();
            // "tun: ch=8vsb:22 lock=8vsb ss=100 snq=100 seq=100
            // dbg=23870-8076\n"
            if (category.equals("tun")) {
                StringTokenizer dataTok = new StringTokenizer(allData, "=");
                while (dataTok.hasMoreTokens()) {
                    String type = dataTok.nextToken("=").trim();
                    if (type.equals("ch")) {
                        this.tunch = dataTok.nextToken(" ").replace('=', ' ').trim();
                    } else if (type.equals("lock")) {
                        this.tunlock = dataTok.nextToken(" ").replace('=', ' ').trim();
                    } else if (type.equals("ss")) {
                        this.tunss = dataTok.nextToken(" ").replace('=', ' ').trim();
                    } else if (type.equals("snq")) {
                        this.tunsnq = dataTok.nextToken(" ").replace('=', ' ').trim();
                    } else if (type.equals("seq")) {
                        this.tunseq = dataTok.nextToken(" ").replace('=', ' ').trim();
                    } else if (type.equals("dbg")) {
                        this.tundbg = dataTok.nextToken(" ").replace('=', ' ').trim();
                        if(this.tundbg.length() > 15) this.tundbg = this.tundbg.substring(0,15); // DRS 20140419 - Could have increased the field in the DB but that's a PITA
                    } else {
                        System.out.println(new Date() + " category tun had unexpected type: " + type);
                    }
                }
                // "dev: resync=0 overflow=0\n" +
            } else if (category.equals("dev")) {
                StringTokenizer dataTok = new StringTokenizer(allData, "=");
                while (dataTok.hasMoreTokens()) {
                    String type = dataTok.nextToken("=").trim();
                    if (type.equals("resync")) {
                        this.devresync = dataTok.nextToken(" ").replace('=', ' ').trim();
                    } else if (type.equals("overflow")) {
                        this.devoverflow = dataTok.nextToken(" ").replace('=', ' ').trim();
                    } else if (type.equals("bps")) {
                        this.devbps = dataTok.nextToken(" ").replace('=', ' ').trim();
                    } else {
                        System.out.println(new Date() + " category dev had unexpected type: " + type);
                    }
                }

                // ts: bps=19391072 ut=96 te=0 miss=4 crc=0\n" +
            } else if (category.equals("ts")) {
                StringTokenizer dataTok = new StringTokenizer(allData, "=");
                while (dataTok.hasMoreTokens()) {
                    String type = dataTok.nextToken("=").trim();
                    if (type.equals("bps")) {
                        this.tsbps = dataTok.nextToken(" ").replace('=', ' ').trim();
                    } else if (type.equals("ut")) {
                        this.tsut = dataTok.nextToken(" ").replace('=', ' ').trim();
                    } else if (type.equals("te")) {
                        this.tste = dataTok.nextToken(" ").replace('=', ' ').trim();
                    } else if (type.equals("miss")) {
                        this.tsmiss = dataTok.nextToken(" ").replace('=', ' ').trim();
                    } else if (type.equals("crc")) {
                        this.tscrc = dataTok.nextToken(" ").replace('=', ' ').trim();
                    } else {
                        System.out.println(new Date() + " category ts had unexpected type: " + type);
                    }
                }
                // "flt: bps=15244544\n" +
            } else if (category.equals("flt")) {
                StringTokenizer dataTok = new StringTokenizer(allData, "=");
                while (dataTok.hasMoreTokens()) {
                    String type = dataTok.nextToken("=").trim();
                    if (type.equals("bps")) {
                        this.fltbps = dataTok.nextToken(" ").replace('=', ' ').trim();
                    } else {
                        System.out.println(new Date() + " category flt had unexpected type: " + type);
                    }
                }
                // "net: pps=1451 err=0 stop=0";
            } else if (category.equals("net")) {
                StringTokenizer dataTok = new StringTokenizer(allData, "=");
                while (dataTok.hasMoreTokens()) {
                    String type = dataTok.nextToken("=").trim();
                    if (type.equals("pps")){
                        this.netpps = dataTok.nextToken(" ").replace('=',' ').trim();
                    } else if (type.equals("err")){
                        this.neterr = dataTok.nextToken(" ").replace('=',' ').trim();
                    } else if (type.equals("stop")){
                        this.netstop = dataTok.nextToken(" ").replace('=',' ').trim();
                    } else {
                        System.out.println(new Date() + " category net had unexpected type: " + type);
                    }
                }

            } else {
                System.out.println(new Date() + " Unexpected contents of debug data " + category);
            }
        }
    }

    private String replaceColonWithDashInData(String equalsThing, String data) {
        try {
            int equalsLoc = data.indexOf(equalsThing);
            int endLoc = data.indexOf(" ", equalsLoc);
            if (equalsLoc == -1 || endLoc == -1){
                System.out.println(new Date() + " ERROR: did not find data between '" + equalsThing +"' and ' ' in capture details");
                System.out.println(new Date() + "[" + data + "]");
            }
            StringBuffer buf = new StringBuffer(data);
            buf.replace(equalsLoc, endLoc, data.substring(equalsLoc, endLoc).replace(':', '-'));
            data = new String(buf);
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
        return data;
    }

    public boolean isCurrentlyNotFinished() {
        return (this.endEvent == null  && (new Date()).before(this.scheduledEnd));
    }
    
    public Tuner getTuner(){
        return TunerManager.getInstance().getTuner(this.tunerName);
    }

    public int getTunerType() {
        int type = -1;
        try {
            type = TunerManager.getInstance().getTuner(this.tunerName).getType();
        } catch (Exception e){
            System.out.println(new Date() + " ERROR: Failed to get tuner type using " + this.tunerName + " " + e.getMessage());
        }
        return type;
    }

    public String getProtocolFromKey() {
        String protocol = "";
        try {
            int dashLoc = this.channelKey.lastIndexOf("-");
            if (dashLoc > 0){
                protocol = channelKey.substring(dashLoc + 1);
            }
        } catch (Exception e){
            System.out.println(new Date() + " ERROR: Could not get protocol from key. " + e.getMessage());
        }
        return protocol;
    }

    
    /*************************************** GETS FOR OUTPUT *********************************************/
    
    public String getHtmlHeadings() {
        StringBuffer buf = new StringBuffer();
        String[] fields = getArray(FIELDS);
        buf.append("<th>");
        for (int i = 0 ; i < sortOrder.length; i++){
            buf.append(fields[sortOrder[i]] + "</th><th>");
        }
        buf.delete(buf.length() - 4 , buf.length());
        return buf.toString();
    }

    public String getHtml() {
        String[] values = getArray(VALUES);
        StringBuffer buf = new StringBuffer();
        buf.append("<td>");
        for (int i = 0 ; i < sortOrder.length; i++){
            buf.append(values[sortOrder[i]] + "</td><td>");
        }
        buf.delete(buf.length() - 4 , buf.length());
        return buf.toString();
    }
    
    private String[] getArray(int type){
        boolean writeToDatabase = false;
        String allValues = this.getCaptureInsertData(false, writeToDatabase)[type] + "~" + this.getSignalQualityUpdateData(false)[type];
        StringTokenizer tok = new StringTokenizer(allValues, "~");
        String[] values = new String[tok.countTokens()];
        int t = 0;
        while (tok.hasMoreTokens()){
            values[t++] = tok.nextToken();
        }
        return values;
    }

    public String getXml() {
        String[] values = getArray(VALUES);
        StringBuffer buf = new StringBuffer();
        for (int i = 0 ; i < sortOrder.length; i++){
            buf.append(CaptureDetails.fieldNames[sortOrder[i]] + "=\"");
            String aValue = values[sortOrder[i]];
            if (aValue != null && aValue.endsWith("'")){
                aValue.substring(1, aValue.length() -1);
            }
            buf.append(aValue + "\" ");
        }
        return buf.toString();
    }
    
    private static String getNameValuePairsFromFieldsAndValues(String fields, String values){
        StringBuffer buf = new StringBuffer();
        StringTokenizer t1 = new StringTokenizer(fields,"~");
        StringTokenizer t2 = new StringTokenizer(values,"~");
        while(t1.hasMoreTokens() && t2.hasMoreTokens()){
            buf.append(t1.nextToken() + "=" + t2.nextToken() + ", ");
        }
        buf.delete(buf.length() - 2 , buf.length());
        return buf.toString();
    }
    
    public String toString() {
        StringBuffer buf = new StringBuffer();
        boolean writeToDatabase = false;
        String[] captureData = this.getCaptureInsertData(false, writeToDatabase);
        buf.append(getNameValuePairsFromFieldsAndValues(captureData[FIELDS], captureData[VALUES]));
        buf.append("\n");
        String[] signalQualityData = this.getSignalQualityUpdateData(false);
        buf.append(getNameValuePairsFromFieldsAndValues(signalQualityData[FIELDS], signalQualityData[VALUES]));
        return buf.toString();
    }

    public String getStrengthSummary() {
        return this.getChannelTunerKey() + " tunsnq:" + this.tunsnq + " tmiss:" + this.tsmiss;
    }

    public Integer getStrengthValue() {
        Float value = Float.MAX_VALUE;
        try {
            //System.out.println("this.tunsnq " + this.tunsnq + " this.tsmiss " + this.tsmiss);
            Float snq = Float.parseFloat(this.tunsnq);
            Float miss = Float.parseFloat(this.tsmiss);
            Float tste = Float.parseFloat(this.tste);
            
            if ((tste > 10000) && (miss == 0)) miss = 500F; // if tste is large and miss zero, it means it didn't even create a file, so penalize the score big-time!
            
            //System.out.println("this.tunsnq " + snq + " this.tsmiss " + miss);
            
            Float valueOne = 3000F / snq;
            //System.out.println("valueOne " + valueOne);
            
            Float valueTwo = miss * 2F;
            
            Float finalWeight = valueOne + valueTwo;
            
            //System.out.println("FinalWeight " + finalWeight);
            
            //value = 3F / (100F + (Float.parseFloat(this.tunsnq))*1000F) + (Float.parseFloat(this.tsmiss) * 2F) + 1000000F; // Total seat of the pants guess at "best / better" viewing quality
            value = finalWeight + 100000;

        } catch (Throwable t) {
            System.out.println("Failed to parse values in signal strength. " + this.tunsnq + " " + this.tsmiss);
        }
        return value.intValue();
    }
    /************************************* TESTING ***************************************/
    
    public static void main(String[] args) throws Exception {
        boolean testOriginalInsert = false;
        if (testOriginalInsert){
            Calendar startCal = Calendar.getInstance();
            startCal.clear();
            startCal.set(2009,0,2,16,0);
            Capture capture = new CaptureHdhr(new Slot(startCal, "60"), new ChannelDigital("11.1",new TunerHdhr("1013FADA-1", false), 42, "8vsb"));
            capture.setTarget(new Target("c:\\testFile.tp2","dale's test title2", "c:\\", "mpg", "8vsb", Tuner.HDHR_TYPE));
            CaptureDetails details = new CaptureDetails(capture);
            System.out.println(details);
            details.insertCaptureStartEvent();
        }
        
        boolean testAddSignalDetails  = false;
        if (testAddSignalDetails){
            Calendar startCal = Calendar.getInstance();
            startCal.clear();
            startCal.set(2009,0,2,16,0);
            Capture capture = new CaptureHdhr(new Slot(startCal, "60"), new ChannelDigital("11.1",new TunerHdhr("1013FADA-1", false), 42, "8vsb"));
            capture.setTarget(new Target("c:\\testFile2.tp","test title2", "c:\\", "mpg", "8vsb", Tuner.HDHR_TYPE));
            String data =   
                "tun: ch=8vsb:22 lock=8vsb ss=100 snq=100 seq=100 dbg=23870-8076\n" +
                "dev: resync=0 overflow=0\n" + 
                "ts:  bps=19391072 ut=96 te=0 miss=4 crc=0\n" +
                "flt: bps=15244544\n" +
                "net: pps=1451 err=0 stop=0"; 
            System.out.println("data" + data);
            String data2 = 
                    "tun: ch=8vsb:23 lock=8vsb:527000000 ss=95 snq=100 seq=100 dbg=23940-8249/-5904\n" +
                    "dev: bps=19391072 resync=0 overflow=0\n" +
                    "ts:  bps=19391072 ut=93 te=0 miss=0 crc=0\n" +
                    "flt: bps=3692320\n" +
                    "net: pps=352 err=0 stop=0";
            
            CaptureDetails details = new CaptureDetails(capture);
            //String tableKey = "200911011305|1013FADA-1|11.1:1-8vsb";
            System.out.println(details);
            details.updateCaptureEndEvent(data2, 0);
        }
        
        boolean testDatabaseRead = true;
        if (testDatabaseRead){
            Connection connection = null;
            Statement statement = null;
            ResultSet rs = null;
                    
            try {
                System.out.println("data path " + CaptureManager.dataPath);
                System.out.println("filename " + CaptureDataManager.mdbFileName);
                //connection = DriverManager.getConnection("jdbc:odbc:Driver={M icroSoft Access Driver (*.mdb)};DBQ=" + CaptureManager.dataPath + CaptureDataManager.mdbFileName);
                connection = DriverManager.getConnection("jdbc:ucanaccess://" + CaptureManager.dataPath + CaptureDataManager.mdbFileName + ";singleConnection=true");
                statement = connection.createStatement();
                String query = "select * from " + CaptureDataManager.tableName + " where tableKey='200901021600|1013FADA-1|11.1:1-8vsb'";
                System.out.println("Getting Capture Data using [" + query + "]");
                rs = statement.executeQuery(query);
                while (rs.next()){
                    CaptureDetails details = new CaptureDetails();
                    details.setFromDb(rs, true);
                    System.out.println("OUTPUT OF CaptureDetails object\n" + details);
                }
                statement.close();
                connection.close();
            } catch (SQLException e) {
                System.err.println(new Date() + " CaptureDataManager.getRecentCaptures:");
                e.printStackTrace();
            } finally {
                try { if (rs != null) rs.close(); } catch (Throwable t){}; 
                try { if (statement != null) statement.close(); } catch (Throwable t){}; 
                try { if (connection != null) connection.close(); } catch (Throwable t){}; 
            }
            
        }
    }

    public int compareTo(Object o) {
        String localOne = channelKey + "::" + tunerName;
        return localOne.compareTo((String)o);
    }
    
    public String getChannelTunerKey(){
        return this.channelKey + "::" + this.tunerName;
    }
    
    public int stronger(CaptureDetails other) throws Exception {
        if (this.tsmiss == null || this.tunsnq == null || this.tunsnq == null ) throw new Exception("The CaptureDetails have null values.");
        if (other == null || other.tsmiss == null || other.tunsnq == null || other.tunsnq == null ) throw new Exception("The CaptureDetails to compare has null values.");
        int thisTsMiss = Integer.parseInt(this.tsmiss);
        int otherTsMiss = Integer.parseInt(other.tsmiss);
        if ((thisTsMiss + otherTsMiss) != 0) return  otherTsMiss - thisTsMiss; // return negative this, because it's bad
        int thisTunSnq = Integer.parseInt(this.tunsnq);
        int otherTunSnq = Integer.parseInt(other.tunsnq);
        return thisTunSnq - otherTunSnq;  // return positive this, because it's good
    }

    public void setCwEpgChannelRow(CwEpgChannelRow cwEpgChannelRow) {
        this.cwEpgChannelRow = cwEpgChannelRow;
    }
    
    public CwEpgChannelRow getCwEpgChannelRow(){
        return this.cwEpgChannelRow;
    }

}
