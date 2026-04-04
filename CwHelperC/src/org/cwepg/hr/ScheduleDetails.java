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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ScheduleDetails implements Cloneable {
    private static final int FIELDS = 0;
    private static final int VALUES = 1;
    private static final DateTimeFormatter SQL_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final DateTimeFormatter SLOT_FORMATTER = DateTimeFormatter.ofPattern("MM/dd/yy HH:mm");
    
    /* Instance data from database fields */
    /* from EP_DATA */
    Integer id = -1; 
    private String titles = "";
    private String episodeTitle150 = ""; 
    private String descriptions = ""; 
    private String originalAirDate = ""; //YYYY-MM-DD
    private String programID = "";
    private String contentRating = "";
    private String showType = "";
    private String contentAdvisory = "";
    private String movie = "";
    private String genres = "";
    private String cast = "";
    private String crew = "";
    private String metadata = "";
    private String entityType  = "";
    private String md5 = "";
    private Boolean used;
    
    /* from CW_DATA */
    private Integer id2 = -1;
    private String channel = "";
    private String stationId = "";
	private String programId = "";
	private Boolean mapped = false;
	private String airDateTime = "";
	private int duration = -1;
	private String md52 = "";
	private Boolean gnew = false;
	private String audioProperties = "";
	private String videoProperties = "";
	private String ratings = "";
	private String multipart = "";
	private Boolean used2 = false;
	
    /* from CH_DATA */
    private Integer id3 = -1;
    private String stnId = "";
    private String callSign = "";
    private String channel3 = "";
    private String minor = "";
    private String stnType = "";
    private String name3 = "";
    private String rfChannel = "";
    private String psip = "";
    private Boolean mapped3 = false;
    private String affiliate = "";
    

    static final String[] fieldNames = {
   	    // Must align with EP_DATA table
    	"ID", //0
        "titles", //1 <<
        "episodeTitle150", //2
        "descriptions", //3 <<
        "originalAirDate", //4 <<
        "programID", //5 
        "contentRating", //6
        "showType", //7
        "contentAdvisory", //8
        "movie", //9
        "genres", //10
        "cast", //11
        "crew", //12
        "metadata", //13 <<
        "entityType", //14
        "MD5", //15
        "USED", //16
        
        //Must align with CW_DATA
        "ID", //17
        "CHANNEL", //18
        "stationID", //19
        "programID", //20
        "MAPPED", //21
        "airDateTime", //22
        "duration", //23
        "md5", //24
        "new", //25
        "audioProperties", //26
        "videoProperties", //27
        "ratings", //28
        "multipart", //29
        "USED", //30

        //Must align with CH_DATA
        "ID1", //31
        "STN_ID", //32
        "CALLSIGN", //33
        "CHANNEL", //34
        "MINOR", //35
        "STNTYPE", //36
        "NAME", //37
        "RFCHANNEL", //38
        "PSIP", //39
        "MAPPED", //40
        "AFFILIATE" //41
    }; 
    
    // Positions within the result data
    private static final int ID = 0;
    private static final int TITLES = 1;
    private static final int EPISODE_TITLE_150 = 2;
    private static final int DESCRIPTIONS = 3;
    private static final int ORIGINAL_AIR_DATE = 4;
    private static final int METADATA = 13;
    private static final int STATION_ID = 19;
    static final int[] displayFields = {ID,TITLES, EPISODE_TITLE_150, DESCRIPTIONS, ORIGINAL_AIR_DATE, METADATA};

    public Object clone() throws CloneNotSupportedException { 
        return super.clone(); 
    } 
    
    // for create from database
    public ScheduleDetails() {
    }

    public String[] getTildeSeparatedFieldNamesWithValues() {
        StringBuffer fields = new StringBuffer();
        StringBuffer values = new StringBuffer();
        fields.append("id~");values.append("'" + this.id + "'~");
        fields.append("titles~");values.append("'" + this.titles + "'~");
        fields.append("episodeTitle150~");values.append("'" + this.episodeTitle150 + "'~"); 
        fields.append("descriptions~");values.append("'" + this.descriptions + "'~"); 
        fields.append("originalAirDate~");values.append("'" + this.originalAirDate + "'~"); //YYYY-MM-DD
        fields.append("programID~");values.append("'" + this.programID + "'~");
        fields.append("contentRating~");values.append("'" + this.contentRating + "'~");
        fields.append("showType~");values.append("'" + this.showType + "'~");
        fields.append("contentAdvisory~");values.append("'" + this.contentAdvisory + "'~");
        fields.append("movie~");values.append("'" + this.movie + "'~");
        fields.append("genres~");values.append("'" + this.genres + "'~");
        fields.append("cast~");values.append("'" + this.cast + "'~");
        fields.append("crew~");values.append("'" + this.crew + "'~");
        fields.append("metadata~");values.append("'" + this.metadata + "'~");
        fields.append("entityType ~");values.append("'" + this.entityType + "'~");
        fields.append("md5~");values.append("'" + this.md5 + "'~");
        fields.append("used~");values.append("'" + this.used + "'~");
        fields.append("id2~");values.append("'" + this.id2 + "'~");
        fields.append("channel~");values.append("'" + this.channel + "'~");
        fields.append("stationId~");values.append("'" + this.stationId+ "'~");
        String[] result = new String[2];
        result[FIELDS] = fields.toString();
        result[VALUES] = values.toString();
        return result;
    }

    public void setFromDb(ResultSet rs, boolean debug) {
        try {
            if (debug) System.out.println("DEBUG: setFromDb " + rs.getMetaData().getColumnCount() + " columns found." );
            
            id = rs.getInt("ID");
            System.out.println("setFromDb had ID as " + id);
            titles = rs.getString(fieldNames[1]);
            episodeTitle150 = rs.getString(fieldNames[2]); 
            descriptions = rs.getString(fieldNames[3]);
            originalAirDate = rs.getString(fieldNames[4]); //YYYY-MM-DD
            programID = rs.getString(fieldNames[5]);
            contentRating = rs.getString(fieldNames[6]);
            showType = rs.getString(fieldNames[7]);;
            contentAdvisory = rs.getString(fieldNames[8]);
            movie = rs.getString(fieldNames[9]);
            genres = rs.getString(fieldNames[10]);
            cast = rs.getString(fieldNames[11]);
            crew = rs.getString(fieldNames[12]);
            metadata = rs.getString(fieldNames[13]);
            entityType  = rs.getString(fieldNames[14]);
            md5 = rs.getString(fieldNames[15]);
            used = rs.getBoolean(fieldNames[16]);
            
            id2 = rs.getInt(fieldNames[17]);
            channel = rs.getString(fieldNames[18]);
            stationId = rs.getString(fieldNames[19]);
            programId = rs.getString(fieldNames[20]);
            mapped = rs.getBoolean(fieldNames[21]);
            airDateTime = rs.getString(fieldNames[22]); 
            duration = rs.getInt(fieldNames[23]);
            md52= rs.getString(fieldNames[24]);
            gnew = rs.getBoolean(fieldNames[25]);
            audioProperties = rs.getString(fieldNames[26]);
            videoProperties = rs.getString(fieldNames[27]);
            ratings = rs.getString(fieldNames[28]);
            multipart = rs.getString(fieldNames[29]);
            used2 = rs.getBoolean(fieldNames[30]);

            id3 = rs.getInt(fieldNames[31]);
            stnId = rs.getString(fieldNames[32]);
            callSign = rs.getString(fieldNames[33]);
            channel3 = rs.getString(fieldNames[34]);
            System.out.println("channel3 [" + channel3 + "]");
            minor = rs.getString(fieldNames[35]);
            stnType = rs.getString(fieldNames[36]);
            name3 = rs.getString(fieldNames[37]);
            rfChannel = rs.getString(fieldNames[38]);
            psip = rs.getString(fieldNames[39]);
            mapped3 = rs.getBoolean(fieldNames[40]);
            affiliate = rs.getString(fieldNames[41]);
            
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    
    /*************************************** GETS FOR OUTPUT *********************************************/
    
    public String getHtmlHeadings() {
        boolean debug = false;
        StringBuffer buf = new StringBuffer();
        String[] fields = getArray(FIELDS);
        if (debug) System.out.println("DEBUG: fields[].length(): "+ fields.length);
        if (debug) System.out.println("DEBUG: sortOrder.length(): "+ displayFields.length);
        buf.append("<th>");
        for (int i = 0 ; i < displayFields.length; i++){
            buf.append(fields[displayFields[i]] + "</th><th>");
        }
        buf.delete(buf.length() - 4 , buf.length());
        return buf.toString();
    }


	public String getHtml() {
        String[] values = getArray(VALUES);
        StringBuffer buf = new StringBuffer();
        buf.append("<td>");
        for (int i = 0 ; i < displayFields.length; i++){
            buf.append(getCleanContent(values[displayFields[i]], displayFields[i]) + "</td><td>");
        }
        buf.delete(buf.length() - 4 , buf.length());
        return buf.toString();
    }

	private String getCleanContent(String content, int item) {
    	if (content == null || "null".equals(content) || "'null'".equals(content)  ) return "";
    	Pattern pattern = null;
    	Matcher matcher = null;
    	switch (item) {
    	case 0:
    	case EPISODE_TITLE_150:
    	case ORIGINAL_AIR_DATE:
    	case STATION_ID:
    		return content;
    	case TITLES:
            pattern = Pattern.compile("\"title120\"\\s*:\\s*\"([^\"]+)\"");
            matcher = pattern.matcher(content);
            if (matcher.find()) {
                return matcher.group(1).trim();
            } else {
            	return content;
            }
    	case DESCRIPTIONS:
            pattern = Pattern.compile("\"description100\".*?\"description\":\"([^\"]+)\"");
            matcher = pattern.matcher(content);
            if (matcher.find()) {
                return (matcher.group(1));
            } else {
            	return "";
            }
    	case METADATA:
            pattern = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"");
            matcher = pattern.matcher(content);
            if (matcher.find()) {
            	String url = matcher.group(1);
            	int lastSlashLoc = url.lastIndexOf("/");
            	String nicerLink = url;
            	if (lastSlashLoc > -1) {
            		nicerLink = url.substring(lastSlashLoc);
            	}
                return "<a href='" + url + "'>" + nicerLink +"</a>";
            } else {
            	return "";
            }
		default:
			System.out.println("unexpected field");
    	}
		return content;
	}
    
	// Return a format useful for slot
	public String getDateTime() {
		ZonedDateTime utcDateTime = LocalDateTime.parse(this.airDateTime, SQL_FORMATTER).atZone(ZoneId.of("UTC"));
        ZonedDateTime localDateTime = utcDateTime.withZoneSameInstant(ZoneId.systemDefault());
        return localDateTime.format(ScheduleDetails.SLOT_FORMATTER);
	}

	public String getDurationMinutes() {
		String durationMinutes = "60";
		try {
			durationMinutes = "" + this.duration/60;
		} catch (Throwable t) {
			System.out.println(new Date() + " Unable to get duration of " + this.programId + " returning 1 hour.");
		}
		return durationMinutes;
	}

	public String getChannelName() {
		//System.out.println("channel " + this .channel);
		//System.out.println("minor " + this .minor);
		//System.out.println("rfChannel " + this.rfChannel);
		//System.out.println("channel3 " + this .channel3);
		//System.out.println("callSign " + this .callSign);
		return channel3 + "." + minor;
	}

	public String toString() {
        StringBuffer buf = new StringBuffer();
        boolean writeToDatabase = false;
        String[] captureData = this.getTildeSeparatedFieldNamesWithValues();
        buf.append(getNameValuePairsFromFieldsAndValues(captureData[FIELDS], captureData[VALUES]));
        return buf.toString();
    }

	/*************************************** HELPER METHODS *********************************************/
    
    private String[] getArray(int type){
        boolean debug = false;
        boolean writeToDatabase = false;
        String allValues = this.getTildeSeparatedFieldNamesWithValues()[type];
        if (debug) System.out.println("ScheduleDetails.getArray(" + type + ") allValues [" + allValues +"]");
        StringTokenizer tok = new StringTokenizer(allValues, "~");
        String[] values = new String[tok.countTokens()];
        int t = 0;
        while (tok.hasMoreTokens()){
            String nextToken = tok.nextToken();
            if (debug) System.out.println("DEBUG: ScheduleDetails.getArray(" + type + "):" + nextToken);
            values[t++] = nextToken;
        }
        return values;
    }

    public String getXml() {
        String[] values = getArray(VALUES);
        StringBuffer buf = new StringBuffer();
        for (int i = 0 ; i < displayFields.length; i++){
            buf.append(CaptureDetails.fieldNames[displayFields[i]] + "=\"");
            String aValue = values[displayFields[i]];
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
    

    /************************************* TESTING ***************************************/
    
    public static void main(String[] args) throws Exception {
    	boolean testRegex = true;
    	if (testRegex) {
    		        String input = "[{\"title120\":\"Animal Control\",\"titleLanguage\":\"en\"}]";
    		        
    		        // Regex to find content after 'title120 :' and before the comma
    		        Pattern pattern = Pattern.compile("\"title120\"\\s*:\\s*\"([^\"]+)\"");
    		        Matcher matcher = pattern.matcher(input);
    		        
    		        if (matcher.find()) {
    		            String result = matcher.group(1).trim();
    		            System.out.println(result); // Output: Animal Control
    		        }
    	}
    	
        boolean testDatabaseRead = false;
        if (testDatabaseRead){
            Connection connection = null;
            Statement statement = null;
            ResultSet rs = null;
                    
            try {
            	String dataPath = "/home/owner/CW_EPG/";
                System.out.println("data path " + dataPath);
                System.out.println("filename " + ScheduleDataManager.dbFileName);
                connection = DriverManager.getConnection("jdbc:sqlite:" + dataPath + ScheduleDataManager.dbFileName);
                statement = connection.createStatement();
                String query = "select * from " + ScheduleDataManager.epData + " where titles LIKE '%Animal Control%'";
                System.out.println("6Getting Capture Data using [" + query + "]");
                rs = statement.executeQuery(query);
                while (rs.next()){
                    ScheduleDetails details = new ScheduleDetails();
                    details.setFromDb(rs, true);
                    System.out.println("OUTPUT OF ScheduleDetails object\n" + details);
                }
                statement.close();
                connection.close();
            } catch (SQLException e) {
                System.err.println(new Date() + " ScheduleDetails.main():");
                e.printStackTrace();
            } finally {
                try { if (rs != null) rs.close(); } catch (Throwable t){}; 
                try { if (statement != null) statement.close(); } catch (Throwable t){}; 
                try { if (connection != null) connection.close(); } catch (Throwable t){}; 
            }
            
        }
    }


}
