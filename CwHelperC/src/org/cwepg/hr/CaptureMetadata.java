package org.cwepg.hr;

import java.io.BufferedReader;
import java.io.FileReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Date;

public class CaptureMetadata implements Runnable {
	
	CaptureHdhr capture;
	
	/* DATABASE FIELDS                          garybuhrmaster field names*/
    private String actors;
    private String advisory;
    private String airDate; 					//Integer firstAiring; //Long originalAirdate;
    private String ant;
    private String cc;
    private String channel;
    private String computerName;
    private String confirmed;
    private String description; 				//String synopsis;
    private String deviceId;
    private String dolby;
    private Timestamp endTime; 					//Long recordEndTime //Long endTime
    private String episode; 					//String programID 
    private String fileName;
    private String genre; 						//String category;
    private String hd;
    private String id;
    private String movieYear;
    private Timestamp orig_end;
    private Timestamp orig_start;
    private String part;
    private String phy;
    private String rating;
    private Timestamp scheduled;
    private String showType;
    private Timestamp startTime; 				//Long startTime; //Long recordStartTime
    private String stereo;
    private String sub;
    private String subTitle; 					//String episodeNumber
    private String title; 						//String episodeTitle //String title
    private String tunerType;
    private String vir;
    
    											//String channelAffiliate = null;
												//String channelImageURL = null;
											    //String channelName = null;
											    //String channelNumber = null;
											    //String imageURL = null;
											    //Integer recordSuccess = null;
											    //Integer resume = null;
											    //String seriesID = null;
	

	public CaptureMetadata(CaptureHdhr capture) {
		this.capture = capture;
	}

	@Override
	public void run() {
		System.out.println(new Date() + " CaptureMetadata will be added here.");
	}

	public void loadMetadataFromDatabaseForCapture(CaptureHdhr capture, String dataPath, String mdbFileName, String recordsTableName) {
		
		//CaptureDataManager.tableReadable(recordsTableName, dataPath, mdbFileName);

		String targetFileName = capture.getFileName();
		System.out.println("looking up [" + targetFileName +  "] in " + recordsTableName);
		
        Connection connection = null;
        Statement statement = null;
        ResultSet rs = null;
        try {
        	String validateMdbPath = dataPath + mdbFileName;
        	System.out.println("vmp [" + validateMdbPath +"]");
            connection = DriverManager.getConnection("jdbc:ucanaccess://" + validateMdbPath + ";singleConnection=true");
            statement = connection.createStatement();
            String query = "select * from " + recordsTableName + " where FILENAME='" + targetFileName + "'";
            System.out.println("Getting Metadata using [" + query + "]");
            rs = statement.executeQuery(query);
            while (rs.next()){
                boolean debug = true;
                this.setFromDb(rs, debug);
                System.out.println("capture metadata: " + this);
            }
            statement.close();
            connection.close();
        } catch (SQLException e) {
            System.err.println(new Date() + " CaptureMetadata.loadMetadataFromDatabaseForCapture:");
            e.printStackTrace();
        } finally {
            try { if (rs != null) rs.close(); } catch (Throwable t){}; 
            try { if (statement != null) statement.close(); } catch (Throwable t){}; 
            try { if (connection != null) connection.close(); } catch (Throwable t){}; 
        }
		
	}
	
	private void setFromDb(ResultSet rs, boolean debug) {
        try {
            if (debug) System.out.println("DEBUG: setFromDb " + rs.getMetaData().getColumnCount() + " columns found." );
            actors = rs.getString("ACTORS");
            advisory = rs.getString("ADVISORY");
            airDate = rs.getString("AIRDATE");
            ant = rs.getString("ANT");
            cc = rs.getString("CC");
            channel = rs.getString("CHANNEL");
            computerName = rs.getString("COMPUTERNAME");
            confirmed = rs.getString("CONFIRMED");
            description = rs.getString("DESCRIPTION");
            deviceId = rs.getString("DEVICEID");
            dolby = rs.getString("DOLBY");
            episode = rs.getString("EPISODE");
            fileName = rs.getString("FILENAME");
            genre = rs.getString("GENRE");
            hd = rs.getString("HD");
            id = rs.getString("ID");
            movieYear = rs.getString("MOVIE_YEAR");
            part = rs.getString("PART");
            phy = rs.getString("PHY");
            rating = rs.getString("RATING");
            showType = rs.getString("SHOWTYPE");
            stereo = rs.getString("STEREO");
            sub = rs.getString("SUB");
            subTitle = rs.getString("SUBTITLE");
            title = rs.getString("TITLE");
            tunerType = rs.getString("TUNER_TYPE");
            vir = rs.getString("VIR");

            endTime = rs.getTimestamp("ENDTIME");
            orig_end = rs.getTimestamp("ORIG_END");
            orig_start = rs.getTimestamp("ORIG_START");
            scheduled = rs.getTimestamp("SCHEDULED");
            startTime = rs.getTimestamp("STARTTIME");

        } catch (SQLException e) {
            e.printStackTrace();
        }
	}
	
    public String toString() {
    	return "ACTORS:" + actors + 
    			", ADVISORY:" + advisory + 
    			", AIRDATE:" + airDate + 
    			", ANT:" + ant + 
    			", CC:" + cc + 
    			", CHANNEL:" + channel + 
    			", COMPUTERNAME:" + computerName + 
    			", CONFIRMED:" + confirmed + 
    			", DESCRIPTION:" + description + 
    			", DEVICEID:" + deviceId + 
    			", DOLBY:" + dolby + 
    			", ENDTIME:" + endTime + 
    			", EPISODE:" + episode + 
    			", FILENAME:" + fileName + 
    			", GENRE:" + genre + 
    			", HD:" + hd + 
    			", ID:" + id + 
    			", MOVIE_YEAR:" + movieYear + 
    			", ORIG_END:" + orig_end + 
    			", ORIG_START:" + orig_start + 
    			", PART:" + part + 
    			", PHY:" + phy + 
    			", RATING:" + rating + 
    			", SCHEDULED:" + scheduled + 
    			", SHOWTYPE:" + showType + 
    			", STARTTIME:" + startTime + 
    			", STEREO:" + stereo + 
    			", SUB:" + sub + 
    			", SUBTITLE:" + subTitle + 
    			", TITLE:" + title + 
    			", TUNER_TYPE:" + tunerType + 
    			", VIR:" + vir;
    }

	public static void main(String[] args) {
		boolean testGetMetadata = true;
		if (testGetMetadata) {
			String recordsTableName = "CW_EPG_RECORDS";
			String dataPath = "c:\\my\\dev\\";
			String mdbFileName = "cw_record.mdb";
			CaptureHdhr capture = getCaptureFromDisk("1010CC54", 1, dataPath);
			System.out.println("capture [" + capture + "]");
			CaptureMetadata metadata = new CaptureMetadata(capture);
			metadata.loadMetadataFromDatabaseForCapture(capture, dataPath, mdbFileName, recordsTableName);
		}
	}

	// Get the first capture from a persistence file for testing
	private static CaptureHdhr getCaptureFromDisk(String tunerName, int tunerNumber, String recordPath) {
		CaptureHdhr capture = null;
        try {
            BufferedReader in = new BufferedReader(new FileReader(recordPath + tunerName + "-" + tunerNumber + "_captures.txt"));
            capture = new CaptureHdhr(in.readLine(), new TunerHdhr(tunerName, tunerNumber, recordPath));
            in.close();
        } catch (Throwable t) {
        	System.out.println("Failed: " + t.getMessage());
        }
        return capture;
	}

}
