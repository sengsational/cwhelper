package org.cwepg.hr;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class CaptureMetadata implements Runnable {
	
	CaptureHdhr capture;
    Map<String, Object> metadata = new HashMap<>();
    
    
    static final String[][] REMAPPING = {
	    {"Category", "SHOWTYPE"},
	    {"ChannelName","CHANNEL"},
	    {"ChannelNumber_1","VIR"},
	    {"ChannelNumber_2","SUB"},
	    {"EndTime", "ORIG_END"},
	    {"EpisodeTitle", "SUBTITLE"},
	    {"OriginalAirdate","AIRDATE"},
	    {"ProgramID","EPISODE"},
	    {"RecordEndTime", "ENDTIME"},
	    {"RecordStartTime", "STARTTIME"},
	    {"RecordSuccess","CONFIRMED"},
	    {"StartTime", "ORIG_START"},
	    {"Synopsis","DESCRIPTION"},
	    {"Title","TITLE"}
    };

	public CaptureMetadata(CaptureHdhr capture) {
		this.capture = capture;
	}

	@Override
	public void run() {
		System.out.println(new Date() + " CaptureMetadata will be added here.");
	}

	public void loadMetadataFromDatabaseForCapture(CaptureHdhr capture, String dataPath, String mdbFileName, String recordsTableName) {
		
		//CaptureDataManager.tableReadable(recordsTableName, dataPath, mdbFileName);

		String targetFileName = capture.getFileNameEscaped();
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
		} catch (Exception e) {
			System.err.println("Unable to access result set.");
		}
        
        for (String[] namePair : REMAPPING) {
			try {
				if (namePair[0].contains("Time")) {
					metadata.put(namePair[0], rs.getTimestamp(namePair[1]).getTime());
				} else {
		            metadata.put(namePair[0], rs.getString(namePair[1]));
				}
			} catch (Throwable t) {
            	System.out.println("Database issue: Unable to get " + namePair[0] + " from " + namePair[1]  + " " + t.getMessage()) ;
            }
		}
        
        if (metadata.containsKey("ChannelNumber_1") && metadata.containsKey("ChannelNumber_2")) {
        	String channelNumberConcat = metadata.get("ChannelNumber_1") + "." + metadata.get("ChannelNumber_2");
        	metadata.put("ChannelNumber", channelNumberConcat);
        	metadata.remove("ChannelNumber_1");
        	metadata.remove("ChannelNumber_2");
        }
        
        if (debug) {
        	System.out.println("metadata contains " + metadata.size() + " items.");
        	System.out.println(this);
        }
            
	}

    private static Map<String, Object> transportStreamToMetadata(byte[] ts) {
        if (ts.length != 12032) {
            throw new IllegalArgumentException("transport stream too short for metadata");
        }

        Map<String, Object> metadata = new HashMap<>();
        for (int pkt = 0; pkt < 64; pkt++) {
            if (ts[pkt * 188 + 0] != 0x47) {
                throw new IllegalArgumentException("transport stream does not contain proper sync bytes");
            }
            if (pkt == 0) {
                if (ts[pkt * 188 + 1] != 0x5f) {
                    throw new IllegalArgumentException("transport stream does not contain proper start payload and PID value bytes");
                }
            } else {
                if (ts[pkt * 188 + 1] != 0x1f) {
                    throw new IllegalArgumentException("transport stream does not contain proper payload and PID value bytes");
                }
            }
            if (ts[pkt * 188 + 2] != (byte) 0xfa) {
                throw new IllegalArgumentException("transport stream does not contain proper PID value");
            }
            if (ts[pkt * 188 + 3] != (byte) (0x10 + pkt % 16)) {
                throw new IllegalArgumentException("transport stream does not contain proper adaption and sequence bytes");
            }
            for (int c = 4; c < 188; c++) {
                if (ts[pkt * 188 + c] == (byte) 0xff) {
                    break;
                }
                metadata.put(Integer.toString(pkt * 188 + c), ts[pkt * 188 + c]);
            }
        }
        if (metadata.isEmpty()) {
            throw new IllegalArgumentException("transport stream does not contain any metadata");
        }
        return metadata;
    }

	
	// From https://github.com/garybuhrmaster/HDHRUtil/blob/main/HDHRUtil-DVR-fileMetadata originally: metadataToTransportStream()
    private static byte[] metadataToTransportStream(Map<String, Object> metadata) {
        byte[] TS = new byte[12032];
        for (int pkt = 0; pkt < 64; pkt++) {
            TS[pkt * 188 + 0] = 0x47;
            if (pkt == 0) {
                TS[pkt * 188 + 1] = 0x5f;
            } else {
                TS[pkt * 188 + 1] = 0x1f;
            }
            TS[pkt * 188 + 2] = (byte) 0xfa;
            TS[pkt * 188 + 3] = (byte) (0x10 + pkt % 16);
        }

        StringBuilder builder = new StringBuilder();
        builder.append("{");
        Set<String> keySet = metadata.keySet();
        for (String key : keySet) {
        	builder.append("\"").append(key).append("\":");
        	if (!key.contains("Time")) {
        		builder.append(CaptureMetadata.quote((String)metadata.get(key)));
        	} else {
                builder.append(metadata.get(key));
        	}
        	builder.append(",");
		}
        builder.delete(builder.length() - 1, builder.length());
        builder.append("}");
        System.out.println("builder [" + builder.toString() + "]") ;
        byte[] metaBytes = builder.toString().getBytes(StandardCharsets.UTF_8);
        if (metaBytes.length > 11776) {
            throw new IllegalArgumentException("metadata json string exceeds allowable length");
        }

        for (int pkt = 0; pkt < 64; pkt++) {
            int byteLength = Math.min(metaBytes.length, 184);
            int offset = pkt * 188 + 4;
            for (int c = 0; c < byteLength; c++) {
                TS[offset + c] = metaBytes[c];
            }
            if (metaBytes.length > byteLength) {
            	builder = builder.delete(0, byteLength);
            	metaBytes = builder.toString().getBytes(StandardCharsets.UTF_8);
            } else {
            	break;
            }
        }
        return TS;
    }
    
    // from https://stackoverflow.com/a/16652683
    public static String quote(String string) {
        if (string == null || string.length() == 0) {
            return "\"\"";
        }

        char         c = 0;
        int          i;
        int          len = string.length();
        StringBuilder sb = new StringBuilder(len + 4);
        String       t;

        sb.append('"');
        for (i = 0; i < len; i += 1) {
            c = string.charAt(i);
            switch (c) {
            case '\\':
            case '"':
                sb.append('\\');
                sb.append(c);
                break;
            case '/':
//                if (b == '<') {
                    sb.append('\\');
//                }
                sb.append(c);
                break;
            case '\b':
                sb.append("\\b");
                break;
            case '\t':
                sb.append("\\t");
                break;
            case '\n':
                sb.append("\\n");
                break;
            case '\f':
                sb.append("\\f");
                break;
            case '\r':
               sb.append("\\r");
               break;
            default:
                if (c < ' ') {
                    t = "000" + Integer.toHexString(c);
                    sb.append("\\u" + t.substring(t.length() - 4));
                } else {
                    sb.append(c);
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }

	private Map<String, Object> getMetadata() {
		return this.metadata;
	}
	
	private boolean valid() {
		return this.metadata.size() > 5; // Arbitrary.  Just make sure there's something there.
	}


	
	
    public String toString() {
    	StringBuffer buf = new StringBuffer();
    	Set<String> keys =  metadata.keySet();
    	for (String key : keys) {
			buf.append(key).append("=").append(metadata.get(key)).append("\n");
		}
    	return buf.toString();
    	/*
    	 * Category=Series<
    	 * EndTime=1710428400000<
    	 * RecordEndTime=1710428400000<
    	 * ChannelNumber=23.6<
    	 * RecordSuccess=FALSE<
    	 * StartTime=1710424800000<
    	 * Title=Ice Road Truckers<
    	 * ProgramID=EP003889630071<
    	 * Synopsis=Jack Jesse must survive the arctic wilderness and cross a frozen river alone to reach the isolated town of Bettles; Lisa Kelly is back in action but the slippery slopes of the Dalton will make her road to redemption a dangerous one.<
    	 * ChannelName=WBTVDT4<
    	 * EpisodeTitle=(S04E10) The Ace vs. The Ice<
    	 * RecordStartTime=1710424920000<
    	 * OriginalAirdate=2010-08-15<
    	 * FirstAiring= (Field "NEW")?
    	 */
    }

	public static void main(String[] args) {
		boolean testGetMetadata = true;
		if (testGetMetadata) {
			String recordsTableName = "CW_EPG_RECORDS";
			String dataPath = "c:\\my\\dev\\";
			String mdbFileName = "cw_record.mdb";
			CaptureHdhr capture = getCaptureFromDisk("1010CC54", 1, dataPath);
			System.out.println("capture [" + capture + "]");
			CaptureMetadata captureMetadata = new CaptureMetadata(capture);
			captureMetadata.loadMetadataFromDatabaseForCapture(capture, dataPath, mdbFileName, recordsTableName);
			if (!captureMetadata.valid()) {
				System.out.println("invalid metadata");
				System.exit(1);
			}
			byte[] transportStreamBytes = CaptureMetadata.metadataToTransportStream(captureMetadata.getMetadata());
			
			String testFileNameLocation = dataPath + capture.target.fileName.substring(capture.target.fileName.lastIndexOf("\\"));
			System.out.println("test file name [" + testFileNameLocation + "]");
			File aFile = new File(testFileNameLocation);
			System.out.println("can read [" + aFile.canRead() + "] can write [" + aFile.canWrite() + "]");
			String progress = "none";
			
			RandomAccessFile file = null;
    		try {
    			file = new RandomAccessFile(testFileNameLocation, "rw");
    			progress = "1";
        		file.seek(0); progress = "2";
        		file.write(transportStreamBytes); progress = "6";
        		file.close();
    		} catch (Throwable t) {
    			System.out.println("Failure at progress " + progress + " with " + t.getMessage());
    		} finally {
    			if (file != null) try {file.close();} catch (Throwable t) {System.out.println("Could not close file. " + t.getMessage());}
    		} 

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
        	System.out.println("Test failed because capture was not created from capture file " +  recordPath + tunerName + "-" + tunerNumber + "_captures.txt - " + t.getMessage());
        }
        return capture;
	}
	

}
