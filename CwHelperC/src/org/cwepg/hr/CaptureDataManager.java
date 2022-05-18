/*
 * Created on Aug 29, 2008
 *
 */
package org.cwepg.hr;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;

import net.ucanaccess.jdbc.UcanaccessDriver;
import net.ucanaccess.jdbc.UcanaccessSQLException;

public class CaptureDataManager {

    private static CaptureDataManager captureDataManager;
    
    public static final String tableName = "CW_EPG_CAPTURES";
    public static String mdbFileName = "cw_record.mdb";

    private CaptureDataManager(){
    }

    public static CaptureDataManager getInstance() {
        if (captureDataManager == null){
            captureDataManager = new CaptureDataManager();
            File dbFile = new File(CaptureManager.dataPath + CaptureDataManager.mdbFileName);
            if (!dbFile.exists()) {
                System.out.println(new Date() + " ERROR: No CW_EPG database file found: " + CaptureManager.dataPath + CaptureDataManager.mdbFileName + ".");
            } else if (!CaptureDataManager.tableReadable(CaptureDataManager.tableName)){
                String makeResult = CaptureDataManager.makeDebugDataTable();
                if (makeResult.indexOf("READ ONLY") > -1){
                    System.out.println(new Date() + " ERROR: CW_EPG database file found, but it was not writable [" + makeResult + "]  " + CaptureManager.dataPath + CaptureDataManager.mdbFileName);
                } else if (!(makeResult.indexOf("OK") > -1)) {
                    System.out.println(new Date() + " ERROR: CW_EPG database file found, but no way to correct [" + makeResult + "] in the file " + CaptureManager.dataPath + CaptureDataManager.mdbFileName + ".");
                }
            } 
        }
        return captureDataManager;
    }

    public TreeMap<String, CaptureDetails> getRecentCaptures(String limitSqlText, String filename, String channel, String title) {
        TreeMap<String, CaptureDetails> detailsMap = new TreeMap<String, CaptureDetails>();
        Connection connection = null;
        PreparedStatement preparedStatement = null;
        String[] psStrings = new String[3];
        ResultSet rs = null;
        boolean debug = false;

        try {
            if (debug) System.out.println("DEBUG: Creating database connection.");
            connection = DriverManager.getConnection("jdbc:ucanaccess://" + CaptureManager.dataPath + CaptureDataManager.mdbFileName + ";singleConnection=true");
            String query = "select " + limitSqlText + " * from " + CaptureDataManager.tableName;
            
            StringBuffer whereBuf = new StringBuffer();
            if (filename.length()>0 || channel.length()>0 || title.length()>0) {
                boolean needsAnd = false;
                whereBuf.append(" where ");
                if (filename.length() > 0) {
                    whereBuf.append("targetFile like ? ");
                    needsAnd = true;
                    psStrings[0] = "%" + filename.replace("!", "!!").replace("%", "!%").replace("_", "!_").replace("[", "![") + "%";
                }
                if (channel.length() > 0) {
                    if (needsAnd) whereBuf.append(" AND ");
                    whereBuf.append("channelKey like ? ");
                    needsAnd = true;
                    psStrings[1] = "%" + channel.replace("!", "!!").replace("%", "!%").replace("_", "!_").replace("[", "![") + "%";
                }
                if (title.length() > 0) {
                    if (needsAnd) whereBuf.append(" AND ");
                    whereBuf.append("title like ? ");
                    needsAnd = true;
                    psStrings[2] = "%" + title.replace("!", "!!").replace("%", "!%").replace("_", "!_").replace("[", "![") + "%";
                }
            }
            whereBuf.append(" order by tableKey desc ");
            
            String fullQuery = query + whereBuf.toString();
            System.out.println(new Date() + " Prepared statement: " + fullQuery);
            preparedStatement = connection.prepareStatement(fullQuery);
            String debugPsStrings = "";
            for(int i = 0, j = 1; i < psStrings.length; i++) {
                if (psStrings[i] == null) continue;
                preparedStatement.setString(j++, psStrings[i]);
                debugPsStrings += psStrings[i] + ", ";
            }
            System.out.println("1Getting Capture Data using [" + query + whereBuf.toString() + "] with search as [" + debugPsStrings + "]");
            rs = preparedStatement.executeQuery();
            //System.out.println("after prepared statement. 1Getting Capture Data using [" + query + whereBuf.toString() + "] with search as [" + debugPsStrings + "]");

            // NOTE: rs.last() is not supported
            //int resultSetCount = 0;
            //if (rs != null) {
            //    rs.last();    // moves cursor to the last row
            //    resultSetCount = rs.getRow(); // get row id 
            //    rs.beforeFirst(); // put it back the way we found it
            //}
            //if (debug) System.out.println("Execution of prepared statement rs.count() " + resultSetCount );

            while (rs.next()){
                CaptureDetails details = new CaptureDetails();
                details.setFromDb(rs, false);
                if(detailsMap.get(details.tableKey)!= null) details.tableKey += "+"; //for the rare case when a restart causes a duplicate
                detailsMap.put(details.tableKey, details);
                //idList += details.tableKey + ",";
            }
            if (preparedStatement != null) preparedStatement.close();
            connection.close();
        } catch (SQLException e) {
            System.out.println("SQLException: " + e.getMessage());
            System.err.println(new Date() + " CaptureDataManager.getRecentCaptures:");
            e.printStackTrace();
        } finally {
            //System.out.println("Closing record set.");
            try { if (rs != null) rs.close(); } catch (Throwable t){}; 
            try { if (preparedStatement != null) preparedStatement.close(); } catch (Throwable t){};
            try { if (connection != null) connection.close(); } catch (Throwable t){}; 
        }
        return detailsMap;
    }
    
    public TreeMap<String, CaptureDetails> getRecentCaptures(String limitSqlText) {
        TreeMap<String, CaptureDetails> detailsMap = new TreeMap<String, CaptureDetails>();
        Connection connection = null;
        Statement statement = null;
        ResultSet rs = null;
        try {
            connection = DriverManager.getConnection("jdbc:ucanaccess://" + CaptureManager.dataPath + CaptureDataManager.mdbFileName + ";singleConnection=true");
            String query = "select " + limitSqlText + " * from " + CaptureDataManager.tableName;
            query += " order by tableKey desc";
            System.out.println("2Getting Capture Data using [" + query + "]");
            statement = connection.createStatement();
            rs = statement.executeQuery(query);
            //System.out.println("DEBUG rs " + rs.getMetaData());
            //String idList = "";
            while (rs.next()){
                CaptureDetails details = new CaptureDetails();
                boolean debug = false;
                details.setFromDb(rs, debug);
                if(detailsMap.get(details.tableKey)!= null) details.tableKey += "+"; //for the rare case when a restart causes a duplicate
                detailsMap.put(details.tableKey, details);
                //idList += details.tableKey + ",";
            }
            if (statement != null) statement.close();
            connection.close();
        } catch (SQLException e) {
            System.err.println(new Date() + " CaptureDataManager.getRecentCaptures:");
            e.printStackTrace();
        } finally {
            try { if (rs != null) rs.close(); } catch (Throwable t){}; 
            try { if (statement != null) statement.close(); } catch (Throwable t){}; 
            try { if (connection != null) connection.close(); } catch (Throwable t){}; 
        }
        return detailsMap;
    }
    
    public TreeMap<String, CaptureDetails> getHistoricalCaptureDetailsForTuner(String fullName) {
        TreeMap<String, CaptureDetails> detailsMap = new TreeMap<String, CaptureDetails>(Collections.reverseOrder());
        Connection connection = null;
        Statement statement = null;
        ResultSet rs = null;
        //String idList = null;
        try {
            //connection = DriverManager.getConnection("jdbc:odbc:Driver={M icroSoft Access Driver (*.mdb)};DBQ=" + CaptureManager.dataPath + CaptureDataManager.mdbFileName);
            connection = DriverManager.getConnection("jdbc:ucanaccess://" + CaptureManager.dataPath + CaptureDataManager.mdbFileName + ";singleConnection=true");
            statement = connection.createStatement();
            String query = "select * from " + CaptureDataManager.tableName + " where tunerName='" + fullName + "' order by tableKey desc"; // newest to oldest
            // DRS 20110215 - Added 'if' block
            if (fullName.equals("ALL")){ // This is only called from CaptureDataManager.getSignalStrengthForChannelAndTuner
                query = "select top 500 * from " + CaptureDataManager.tableName + " where tunlock like '8vsb%' order by tableKey desc";  // newest to oldest
            }
            System.out.println("3Getting Capture Data using [" + query + "]");
            rs = statement.executeQuery(query);
            //idList = "";
            while (rs.next()){
                CaptureDetails details = new CaptureDetails();
                boolean debug = false;
                details.setFromDb(rs, debug);
                if(detailsMap.get(details.tableKey)!= null) details.tableKey += "+"; //for the rare case when a restart causes a duplicate
                detailsMap.put(details.tableKey, details);
                //idList += details.tableKey + ",";
            }
            statement.close();
            connection.close();
        } catch (SQLException e) {
            System.err.println(new Date() + " CaptureDataManager.getHistoricalCaptureDetailsForTuner:");
            e.printStackTrace();
        } finally {
            try { if (rs != null) rs.close(); } catch (Throwable t){}; 
            try { if (statement != null) statement.close(); } catch (Throwable t){}; 
            try { if (connection != null) connection.close(); } catch (Throwable t){}; 
        }
        System.out.println("Should be 500: " + detailsMap.size());
        return detailsMap;
    }
    
    // DRS 20110215 - Added method
    public TreeMap<String, CaptureDetails> getSignalStrengthByChannelAndTuner(long daysHistory){
        TreeMap<String, CaptureDetails> detailsMap = getHistoricalCaptureDetailsForTuner("ALL");
        TreeMap<String, CaptureDetails> channelsMapWithCaptureDetails = new TreeMap<String, CaptureDetails>();
        String rfDone = ".";
        String dateError = "";
        try {
        for (Object obj : detailsMap.keySet()) {
            String key = (String)obj;
            CaptureDetails details = detailsMap.get(key);
            String channelKey = details.channelKey;
            try {
                long daysOld = (new Date().getTime() - details.startEvent.getTime())/1000/60/60/24;
                //System.out.println("DEBUG " + daysOld + " > " + daysHistory + " <<< true or false?");
                if (daysOld > daysHistory) break;
            } catch (Throwable t) {
                dateError += " DateProblem " + channelKey; // This will make a long string if there are many bad dates.
            }
            //System.out.println("Channel key: " + channelKey);
            //System.out.println("Tuner: " + details.tunerName);
            String channelRfAndTuner = channelKey.split("\\.")[0] + "::" + details.tunerName;
            if (rfDone.contains(channelRfAndTuner)) continue;
            rfDone += channelRfAndTuner;
            String tunerNameString = details.tunerName;
            ArrayList<String> sameRfChannels = TunerManager.getInstance().getSameRfChannels(tunerNameString, channelKey);
            for (String sisterChannel : sameRfChannels) {
                if (channelsMapWithCaptureDetails.get(sisterChannel + "::" + tunerNameString) == null){
                    CaptureDetails detailsClone = (CaptureDetails) details.clone();
                    detailsClone.channelKey = sisterChannel;
                    channelsMapWithCaptureDetails.put(sisterChannel + "::" + tunerNameString, detailsClone); // put only the first one found in the map.  The list should be newest to oldest.
                    System.out.println("channelMapWithCaptureDetails now has [" + sisterChannel + "::" + tunerNameString + "] based on " + details.scheduledStart + " recording.");
                }
            }
        }
            
        } catch (Throwable t) {
            System.out.println("Throwable " + t.getMessage());
        }
        if (dateError.length() > 0) System.out.println(new Date() + " ERROR: Recording(s) had bad start date: " + dateError);
        return channelsMapWithCaptureDetails;
    }
    
    public List<Capture> getActiveFusion(TunerFusion tuner){
        ArrayList<Capture> list = new ArrayList<Capture>();
        Connection connection = null;
        Statement statement = null;
        ResultSet rs = null;
        try {
            //connection = DriverManager.getConnection("jdbc:odbc:Driver={M icroSoft Access Driver (*.mdb)};DBQ=" + CaptureManager.dataPath + CaptureDataManager.mdbFileName);
            connection = DriverManager.getConnection("jdbc:ucanaccess://" + CaptureManager.dataPath + CaptureDataManager.mdbFileName + ";singleConnection=true");
            statement = connection.createStatement();
            String query = "select top 10 * from " + CaptureDataManager.tableName + " order by tableKey desc";
            System.out.println("4Getting Capture Data using [" + query + "]");
            rs = statement.executeQuery(query);
            while (rs.next()){
                CaptureDetails details = new CaptureDetails();
                details.setFromDb(rs, false);
                // DRS 20101024 - Commented line (adjusting for Fusion lead time done in a more universal place
                //details.setStartTimeToNowPlus(CaptureManager.fusionLeadTime); // Fusion recordings in the past do not work
                if (details.isCurrentlyNotFinished()){
                    if (details.tunerName.equals(tuner.getFullName())){
                        Capture capture = (Capture)new CaptureFusion(details, tuner);
                        try {
                            if (details.targetFile.equals("(watch)")) details.targetFile = "WATCH";
                            capture.setTarget(new Target(details.targetFile, details.title, tuner.getRecordPath(), tuner.analogFileExtension, details.getProtocolFromKey(), Tuner.FUSION_TYPE));
                            list.add(capture);
                        } catch (Exception e) {System.out.println(new Date() + " ERROR: Could not set target." + e.getMessage());}
                    }
                }
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
        return list;
    }

    static boolean tableReadable(String aTable) {
        Connection connection = null;
        Statement statement = null;
        String driverNameSimple = UcanaccessDriver.class.getSimpleName();
        String driverName = "net.ucanaccess.jdbc.UcanaccessDriver";
        try {
            Class.forName(driverName);
            connection = DriverManager.getConnection("jdbc:ucanaccess://" + CaptureManager.dataPath + CaptureDataManager.mdbFileName + ";singleConnection=true");
            statement = connection.createStatement();
            String query = "select count(*) from " + aTable;
            System.out.print(new Date() + " Check if table " + aTable + " exists in " + CaptureDataManager.mdbFileName + " using [" + query + "] ");
            statement.execute(query);
            statement.close();
            connection.close();
            System.out.println("OK -- jdbc:ucanaccess://" + CaptureManager.dataPath + CaptureDataManager.mdbFileName);
            return true;
        } catch (SQLException e) {
            System.out.println(new Date() + " ERROR: table does not exist or database file is not writeable. " + e.getMessage());
        } catch (ClassNotFoundException e) {
            System.err.println(new Date() + " Driver not accessable : " + driverName + ". " + e.getMessage());
            System.err.println(new Date() + " Driver not accessable-: " + driverNameSimple + ". " + e.getMessage());
            Enumeration<Driver> driverEnum = DriverManager.getDrivers();
            while (driverEnum.hasMoreElements()) {
                Driver driver = (Driver) driverEnum.nextElement();
                System.out.println(new Date() + " Available driver:" + driver);
            }
            e.printStackTrace();
        } catch (Exception e){
            System.out.println(new Date() + " ERROR: problem in tableExists(). " + e.getMessage());
        } finally {
            try { if (statement != null) statement.close(); } catch (Throwable t){}; 
            try { if (connection != null) connection.close(); } catch (Throwable t){}; 
        }
        return false;
    }
    
    static String makeDebugDataTable() {
        String result = "(none)";
        Statement statement = null;
        Connection connection = null;
        try {
            //connection = DriverManager.getConnection("jdbc:odbc:Driver={M icroSoft Access Driver (*.mdb)};DBQ=" + CaptureManager.dataPath + CaptureDataManager.mdbFileName);
            connection = DriverManager.getConnection("jdbc:ucanaccess://" + CaptureManager.dataPath + CaptureDataManager.mdbFileName + ";singleConnection=true");
            statement = connection.createStatement();
            String query = "CREATE TABLE " + tableName + " (" + new CaptureDetails().getFieldsForTableCreate() + ")";
            System.out.println(new Date() + " making table using [" + query + "]");
            statement.execute(query);
            statement.close();
            connection.close();
            result = "OK";
        } catch (UcanaccessSQLException e) {
            if (e.getMessage().indexOf("read only") > -1){
                result = "ERROR: READ ONLY " + e.getMessage();
            } else {
                result = "ERROR: UCANACCESS " + e.getMessage();
                System.out.println(new Date() + " ERROR: CaptureDataManager.makeDebugDataTable: UcanaccessSQLException: " + e.getMessage());
                System.err.println(new Date() + " ERROR: CaptureDataManager.makeDebugDataTable: UcanaccessSQLException: ");
                e.printStackTrace();
            }
        } catch (SQLException e) {
            result = "ERROR: SQL " + e.getMessage();
            System.out.println(new Date() + " ERROR: CaptureDataManager.makeDebugDataTable:" + e.getMessage());
            System.err.println(new Date() + " ERROR: CaptureDataManager.makeDebugDataTable:");
            e.printStackTrace();
        } finally {
            try { if (statement != null) statement.close(); } catch (Throwable t){}; 
            try { if (connection != null) connection.close(); } catch (Throwable t){}; 
        }
        return result;
    }

    static boolean dropTable(String aTable) {
        boolean result = false;
        Statement statement = null;
        Connection connection = null;
        try {
            //connection = DriverManager.getConnection("jdbc:odbc:Driver={M icroSoft Access Driver (*.mdb)};DBQ=" + CaptureManager.dataPath + CaptureDataManager.mdbFileName);
            connection = DriverManager.getConnection("jdbc:ucanaccess://" + CaptureManager.dataPath + CaptureDataManager.mdbFileName + ";singleConnection=true");
            statement = connection.createStatement();
            String query = "DROP TABLE " + aTable; 
            System.out.println(new Date() + " dropping table using [" + query + "]");
            statement.execute(query);
            statement.close();
            connection.close();
            result = true;
        } catch (SQLException e) {
            System.err.println(new Date() + " CaptureDataManager.dropTable:");
            e.printStackTrace();
        } finally {
            try { if (statement != null) statement.close(); } catch (Throwable t){}; 
            try { if (connection != null) connection.close(); } catch (Throwable t){}; 
        }
        return result;
    }

    /**************** MAIN **********************/
    
    public static void main(String[] args) {
        boolean testWideningOfKeyField = false;
        if (testWideningOfKeyField){
            CaptureDataManager.tableReadable("CW_EPG_CAPTURES");
        }

        boolean testDatabaseCopy = false;
        if (testDatabaseCopy){
            CaptureDataManager.getInstance();
        }

        boolean testDatabaseCreate = false;
        if (testDatabaseCreate){
            CaptureDataManager.getInstance();
            if(CaptureDataManager.tableReadable(CaptureDataManager.tableName)){
                CaptureDataManager.dropTable(CaptureDataManager.tableName);
            }
        }
        
        boolean testReadCaptureDetails = false;
        if (testReadCaptureDetails) {
            //String dataPath = "C:\\my\\dev\\eclipsewrk\\Hdhr\\";
            String localPath = "";
            Connection connection = null;
            Statement statement = null;
            ResultSet rs = null;
            TreeMap<String, CaptureDetails> detailsMap = new TreeMap<String, CaptureDetails>();
            String idList = "";
            try {
                //connection = DriverManager.getConnection("jdbc:odbc:Driver={M icroSoft Access Driver (*.mdb)};DBQ=" + localPath + CaptureDataManager.mdbFileName);
                connection = DriverManager.getConnection("jdbc:ucanaccess://" + localPath + CaptureDataManager.mdbFileName + ";singleConnection=true");
                statement = connection.createStatement();
                String query = "select top 50 * from " + CaptureDataManager.tableName + " order by tableKey desc";
                System.out.println("5Getting Capture Data using [" + query + "]");
                rs = statement.executeQuery(query);
                while (rs.next()) {
                    CaptureDetails details = new CaptureDetails();
                    details.setFromDb(rs, true);
                    System.out.println(details);
                    detailsMap.put(details.tableKey, details);
                    idList += details.tableKey + ",";
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
            System.out.println("id's found: " + idList);
            System.out.println("<html><body><table border=\"1\">");
            boolean headerDone = false;
            for (Iterator iter = detailsMap.descendingMap().values().iterator(); iter.hasNext();) {
                CaptureDetails details = (CaptureDetails) iter.next();
                if (!headerDone){
                    System.out.println(details.getHtmlHeadings());
                    headerDone = true;
                }
                System.out.println("<tr>" + details.getHtml() + "</tr>");
            }
            System.out.println("</table></body></html>");
        }

        boolean testWebOutput = true;
        if (testWebOutput){
            CaptureManager cm = CaptureManager.getInstance();
            //String[] inputs = {(String)request.get("filename"), (String)request.get("channel"), (String)request.get("title")};
            String[] inputs = null; // null test
            boolean limited = true;
            //System.out.println(cm.getWebCapturesList(inputs, limited));

            inputs = new String[3]; // empty selections test
            limited = true;
            //System.out.println(cm.getWebCapturesList(inputs, limited));
            
            inputs[2] = "NOVA"; // single title test
            limited = true;
            //System.out.println(cm.getWebCapturesList(inputs, limited));

            inputs[2] = "NOVA";  // unlimited test
            limited = false;
            //System.out.println(cm.getWebCapturesList(inputs, limited));

            inputs[0] = "the"; // multiple test
            inputs[1] = "44.5";
            inputs[2] = "NOVA"; 
            limited = true;
            System.out.println(cm.getWebCapturesList(inputs, limited));

        }
    }

    
}
