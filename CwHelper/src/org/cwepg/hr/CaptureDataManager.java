/*
 * Created on Aug 29, 2008
 *
 */
package org.cwepg.hr;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;

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
                    System.out.println(new Date() + " ERROR: CW_EPG database file found, but no way to correct [" + makeResult + "] in the file" + CaptureManager.dataPath + CaptureDataManager.mdbFileName + ".");
                }
            } 
        }
        return captureDataManager;
    }
    
    public TreeMap getRecentCaptures() {
        TreeMap<String, CaptureDetails> detailsMap = new TreeMap<String, CaptureDetails>();
        Connection connection = null;
        Statement statement = null;
        ResultSet rs = null;
        try {
            //connection = DriverManager.getConnection("jdbc:odbc:Driver={M icroSoft Access Driver (*.mdb)};DBQ=" + CaptureManager.dataPath + CaptureDataManager.mdbFileName);
            connection = DriverManager.getConnection("jdbc:ucanaccess://" + CaptureManager.dataPath + CaptureDataManager.mdbFileName);
            statement = connection.createStatement();
            String query = "select top 50 * from " + CaptureDataManager.tableName + " order by tableKey desc";
            System.out.println("Getting Capture Data using [" + query + "]");
            rs = statement.executeQuery(query);
            String idList = "";
            while (rs.next()){
                CaptureDetails details = new CaptureDetails();
                details.setFromDb(rs);
                if(detailsMap.get(details.tableKey)!= null) details.tableKey += "+"; //for the rare case when a restart causes a duplicate
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
        return detailsMap;
    }
    
    public TreeMap<String, CaptureDetails> getHistoricalCaptureDetailsForTuner(String fullName) {
        TreeMap<String, CaptureDetails> detailsMap = new TreeMap<String, CaptureDetails>();
        Connection connection = null;
        Statement statement = null;
        ResultSet rs = null;
        String idList = null;
        try {
            //connection = DriverManager.getConnection("jdbc:odbc:Driver={M icroSoft Access Driver (*.mdb)};DBQ=" + CaptureManager.dataPath + CaptureDataManager.mdbFileName);
            connection = DriverManager.getConnection("jdbc:ucanaccess://" + CaptureManager.dataPath + CaptureDataManager.mdbFileName);
            statement = connection.createStatement();
            String query = "select * from " + CaptureDataManager.tableName + " where tunerName='" + fullName + "' order by tableKey desc";
            // DRS 20110215 - Added 'if' block
            if (fullName.equals("ALL")){
                query = "select * from " + CaptureDataManager.tableName + " where tunlock = '8vsb' order by tableKey desc";
            }
            System.out.println("Getting Capture Data using [" + query + "]");
            rs = statement.executeQuery(query);
            idList = "";
            while (rs.next()){
                CaptureDetails details = new CaptureDetails();
                details.setFromDb(rs);
                if(detailsMap.get(details.tableKey)!= null) details.tableKey += "+"; //for the rare case when a restart causes a duplicate
                detailsMap.put(details.tableKey, details);
                idList += details.tableKey + ",";
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
        return detailsMap;
    }
    
    // DRS 20110215 - Added method
    public TreeMap<String, CaptureDetails> getSignalStrengthByChannelAndTuner(){
        TreeMap<String, CaptureDetails> detailsMap = getHistoricalCaptureDetailsForTuner("ALL");
        TreeMap<String, CaptureDetails> channelMap = new TreeMap<String, CaptureDetails>();
        for (Object obj : detailsMap.keySet()) {
            String key = (String)obj;
            CaptureDetails details = detailsMap.get(key);
            String channel = details.channelKey;
            String tuner = details.tunerName;
            if (channelMap.get(channel + "::" + tuner) == null){
                channelMap.put(channel + "::" + tuner, details); // put the most recent details in the map
            }
        }
        return channelMap;
    }
    
    public List<Capture> getActiveFusion(TunerFusion tuner){
        ArrayList<Capture> list = new ArrayList<Capture>();
        Connection connection = null;
        Statement statement = null;
        ResultSet rs = null;
        try {
            //connection = DriverManager.getConnection("jdbc:odbc:Driver={M icroSoft Access Driver (*.mdb)};DBQ=" + CaptureManager.dataPath + CaptureDataManager.mdbFileName);
            connection = DriverManager.getConnection("jdbc:ucanaccess://" + CaptureManager.dataPath + CaptureDataManager.mdbFileName);
            statement = connection.createStatement();
            String query = "select top 10 * from " + CaptureDataManager.tableName + " order by tableKey desc";
            System.out.println("Getting Capture Data using [" + query + "]");
            rs = statement.executeQuery(query);
            while (rs.next()){
                CaptureDetails details = new CaptureDetails();
                details.setFromDb(rs);
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
        try {
            //connection = DriverManager.getConnection("jdbc:odbc:Driver={M icroSoft Access Driver (*.mdb)};DBQ=" + CaptureManager.dataPath + CaptureDataManager.mdbFileName);
            connection = DriverManager.getConnection("jdbc:ucanaccess://" + CaptureManager.dataPath + CaptureDataManager.mdbFileName);
            statement = connection.createStatement();
            String query = "select count(*) from " + aTable;
            System.out.print(new Date() + " Check if table " + aTable + " exists in " + CaptureDataManager.mdbFileName + " using [" + query + "] ");
            statement.execute(query);
            statement.close();
            connection.close();
            System.out.println("OK");
            return true;
        } catch (SQLException e) {
            System.out.println(new Date() + " ERROR: table does not exist or database file is not writeable. " + e.getMessage());
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
            connection = DriverManager.getConnection("jdbc:ucanaccess://" + CaptureManager.dataPath + CaptureDataManager.mdbFileName);
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
            connection = DriverManager.getConnection("jdbc:ucanaccess://" + CaptureManager.dataPath + CaptureDataManager.mdbFileName);
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
                connection = DriverManager.getConnection("jdbc:ucanaccess://" + localPath + CaptureDataManager.mdbFileName);
                statement = connection.createStatement();
                String query = "select top 50 * from " + CaptureDataManager.tableName + " order by tableKey desc";
                System.out.println("Getting Capture Data using [" + query + "]");
                rs = statement.executeQuery(query);
                while (rs.next()) {
                    CaptureDetails details = new CaptureDetails();
                    details.setFromDb(rs);
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

        boolean testWebOutput = false;
        if (testWebOutput){
            CaptureManager cm = CaptureManager.getInstance();
            System.out.println(cm.getRecentWebCapturesList());
        }
    }
    
}
