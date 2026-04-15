package org.cwepg.hr;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;

public class ScheduleDataManager {

    private static ScheduleDataManager scheduleDataManager;
    
    public static final String epData = "EP_DATA";
    public static final String cwData = "CW_DATA";
    public static final String chData = "CH_DATA";
    public static String dbFileName = "cw_json1.db";

    private ScheduleDataManager(){
    }

    public static ScheduleDataManager getInstance() {
        if (scheduleDataManager == null){
            scheduleDataManager = new ScheduleDataManager();
            File dbFile = new File(CaptureManager.dataPath + ScheduleDataManager.dbFileName);
            if (!dbFile.exists()) {
                System.out.println(new Date() + " ERROR: No CW_EPG database file found: " + CaptureManager.dataPath + ScheduleDataManager.dbFileName + ".");
            } 
        }
        return scheduleDataManager;
    }

    public TreeMap<Integer, ScheduleDetails> getNextSchedules(String limitSqlText, String name) {
        TreeMap<Integer, ScheduleDetails> detailsMap = new TreeMap<Integer, ScheduleDetails>();
        Connection connection = null;
        PreparedStatement preparedStatement = null;
        String[] psStrings = new String[1];
        ResultSet rs = null;
        boolean debug = true;

        try {
        	
        	String fileNameString = CaptureManager.dataPath + ScheduleDataManager.dbFileName;
        	if (debug) System.out.println("1fileNameString " + fileNameString);
        	fileNameString = fileNameString.replaceAll("\\\\","/");
        	if (debug) System.out.println("2fileNameString " + fileNameString);
            if (debug) System.out.println("DEBUG: Creating database connection for ScheduleDataManager.");
            File aFile = new File(fileNameString);
            if (debug) System.out.println("file was found: " + aFile.exists()  + " " + aFile.getAbsolutePath());
            Class.forName("org.sqlite.JDBC"); // Manual registration for debugging
            connection = DriverManager.getConnection("jdbc:sqlite:" + fileNameString);
        	String query = "SELECT * " + 
					"FROM " + epData + " " +
					"JOIN " + 	cwData + " ON " + epData + ".ProgramID = " + cwData + ".ProgramID " + 
					"JOIN " + 	chData + " ON " + cwData + ".stationID = " + chData + ".STN_ID "; 
            
            StringBuffer whereBuf = new StringBuffer();
            if (name.length()>0) {
                boolean needsAnd = false;
                whereBuf.append(" where ");
                if (name.length() > 0) {
                    whereBuf.append("titles like ? ");
                    needsAnd = true;
                    psStrings[0] = "%" + name.replace("!", "!!").replace("%", "!%").replace("_", "!_").replace("[", "![") + "%";
                }
            }
            whereBuf.append(" order by titles desc "+ limitSqlText);
            
            String fullQuery = query + whereBuf.toString();
            System.out.println(new Date() + " Prepared statement: " + fullQuery);
            preparedStatement = connection.prepareStatement(fullQuery);
            String debugPsStrings = "";
            for(int i = 0, j = 1; i < psStrings.length; i++) {
                if (psStrings[i] == null) continue;
                preparedStatement.setString(j++, psStrings[i]);
                debugPsStrings += psStrings[i] + ", ";
            }
            System.out.println("1Getting Schedule Data using [" + query + whereBuf.toString() + "] with search as [" + debugPsStrings + "]");
            rs = preparedStatement.executeQuery();

            while (rs.next()){
                ScheduleDetails details = new ScheduleDetails();
                details.setFromDb(rs, true);
                detailsMap.put(details.id, details);
            }
            if (preparedStatement != null) preparedStatement.close();
            connection.close();
        } catch (SQLException e) {
            System.out.println("SQLException: " + e.getMessage());
            System.err.println(new Date() + " CaptureDataManager.getRecentCaptures:");
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            System.err.println("SQLite Driver NOT found in classpath!");
			e.printStackTrace();
		} finally {
            //System.out.println("Closing record set.");
            try { if (rs != null) rs.close(); } catch (Throwable t){}; 
            try { if (preparedStatement != null) preparedStatement.close(); } catch (Throwable t){};
            try { if (connection != null) connection.close(); } catch (Throwable t){}; 
        }
        return detailsMap;
    }

	public static ScheduleDetails getScheduleDetailsFromId(int scheduleId) {
        ScheduleDetails details = new ScheduleDetails();
        Connection connection = null;
        String fileNameString = CaptureManager.dataPath + ScheduleDataManager.dbFileName;
    	fileNameString = fileNameString.replaceAll("\\\\","/");
    	try {
            Class.forName("org.sqlite.JDBC"); // Manual registration for debugging
            connection = DriverManager.getConnection("jdbc:sqlite:" + fileNameString);
    	} catch (Throwable t) {
    		System.out.println(new Date() + " ERROR: unable to access database");
    		return details; // empty
    	}
    	
    	String query = "SELECT * " + 
    								"FROM " + epData + " " +
    								"JOIN " + 	cwData + " ON " + epData + ".ProgramID = " + cwData + ".ProgramID " + 
    								"JOIN " + 	chData + " ON " + cwData + ".stationID = " + chData + ".STN_ID " + 
    								"WHERE " + epData + ".ID = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setInt(1, scheduleId); 
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    details.setFromDb(rs, false);
                    System.out.println(new Date() + " Found record matching " + scheduleId);
                } else {
                    System.out.println("No record found with ID: " + scheduleId);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        return details;
	}



    /**************** MAIN 
     * @throws SlotException **********************/
    
    public static void main(String[] args) throws SlotException {
        
    	boolean testReadById = true;
    	boolean isRunningOnWindows = false;
    	if (testReadById && isRunningOnWindows) {
        	ScheduleDetails details = ScheduleDataManager.getScheduleDetailsFromId(Integer.parseInt("258545"));
        	String dateTime = details.getDateTime();
        	String duration = details.getDurationMinutes();
            Slot slot = new Slot(dateTime, duration);
            System.out.println("slot [" + slot + "]"); 
            String channel = details.getChannelName();
            System.out.println("channel [" + channel + "]");
            //getAvailableCapturesForChannelNameAndSlot(String channelName, Slot slot, String protocol, boolean isVirtualChannel)
            //List<Capture> x = TunerManager.getAvailableCapturesForChannelNameAndSlot(channel, slot, "8vsb", true);
    	}
    	
        boolean testReadScheduleDetails = false;
        if (testReadScheduleDetails) {
        	String dataPath = "/home/owner/CW_EPG/";
            System.out.println("data path " + dataPath);
            System.out.println("filename " + ScheduleDataManager.dbFileName);
            
            Connection connection = null;
            Statement statement = null;
            ResultSet rs = null;
            TreeMap<Integer, ScheduleDetails> detailsMap = new TreeMap<Integer, ScheduleDetails>();
            String idList = "";
            try {
            	connection = DriverManager.getConnection("jdbc:sqlite:" + dataPath + ScheduleDataManager.dbFileName);
                statement = connection.createStatement();
                String query = "select * from " + ScheduleDataManager.epData + " ORDER BY ID DESC LIMIT 50";
                System.out.println("5Getting Schedule Data using [" + query + "]");
                rs = statement.executeQuery(query);
                while (rs.next()) {
                    ScheduleDetails details = new ScheduleDetails();
                    details.setFromDb(rs, true);
                    System.out.println(details.id + " details:" + details.toString().substring(0,200));
                    
                    detailsMap.put(details.id, details);
                    idList += details.id + ",";
                }
                statement.close();
                connection.close();
            } catch (SQLException e) {
                System.err.println(new Date() + " ScheduleDataManager.getRecentCaptures:");
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
                ScheduleDetails details = (ScheduleDetails) iter.next();
                if (!headerDone){
                    System.out.println(details.getHtmlHeadings());
                    headerDone = true;
                }
                System.out.println("<tr>" + details.getHtml().substring(0,100) + "</tr>");
            }
            System.out.println("</table></body></html>");
        }

        boolean testWebOutput = false;
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
            System.out.println(cm.getWebSchedulesList(inputs));

        }
    }
}

