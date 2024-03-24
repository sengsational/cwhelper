package org.cwepg.hr;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class CaptureMetadata implements Runnable {
	
	CaptureHdhr capture;
    Map<String, Object> metadata = new HashMap<>();
    private static final SimpleDateFormat DF = new SimpleDateFormat("yyyy-MM-dd");
    private static final SimpleDateFormat DF2 = new SimpleDateFormat("yyyyMMdd");
	private String lastJson;
	
    
    static final int JSONID = 0, DBID = 1, TYPE = 2, DEFAULT = 3; 
    static final int BLOCK_SIZE = 12032;
    static final String RECORDS_TABLE_NAME = "CW_EPG_RECORDS";

    static final String[][] REMAPPING = {
        {"Category", "SHOWTYPE","String",""},
        {"ChannelAffiliate","","String",""},
//      {"ChannelImageURL","","String",""},
        {"ChannelName","CHANNEL","String",""},
        {"ChannelNumber_1","VIR","String",""},
        {"ChannelNumber_2","SUB","String",""},
        {"EndTime", "ORIG_END","Long",""},
        {"EpisodeNumber","","String",""},				//Built from SUBTITLE
        {"EpisodeTitle", "SUBTITLE","String",""},
//      {"FirstAiring","","Boolean","0"},
//      {"ImageURL","","String",""},
        {"OriginalAirdate","AIRDATE","Long",""},
        {"ProgramID","EPISODE","String",""},
        {"RecordEndTime", "ENDTIME","Long",""},
        {"RecordStartTime", "STARTTIME","Long",""},
        {"RecordSuccess","","Boolean","1"},
//      {"Resume","","Boolean","2"},
        {"SeriesID","","String",""},					//Built from SUBTITLE
        {"StartTime", "ORIG_START","Long",""},
        {"Synopsis","DESCRIPTION","String",""},
        {"Title","TITLE","String",""},
    };
    
	public CaptureMetadata(CaptureHdhr capture) {
		this.capture = capture;
	}

	@Override
	public void run() {
		try {Thread.sleep(30000);}catch(Exception e) {}

		// 1) Make sure the file is not empty and we can read it
		File aFile = new File(capture.target.fileName);
		FileInputStream fis = null;
		try {
			fis = new FileInputStream(aFile);
			if (fis.read() == -1) {
				System.out.println(new Date() + " WARNING: Transport stream file " + capture.target.fileName + " was empty.  No metadata being added to stream.");
				return;
			}
		} catch (IOException i) {
			System.out.println(new Date() + " WARNING: Transport stream file " + capture.target.fileName + " was not readable.  No metadata being added to stream.");
			return;
		} finally {
			if (fis != null) {try {fis.close();} catch (Throwable t) {}}
		}
		
		// 2) Populate local metadata, failing if unable to read the metdata from the database
		loadMetadataFromDatabaseForCapture(capture, CaptureManager.dataPath, CaptureDataManager.mdbFileName, RECORDS_TABLE_NAME);
		if (!valid()) {
			System.out.println(new Date() + " WARNING: Unable to get metadata for capture for file " + capture.target.fileName + ".  No metadata being added to stream.");
			return;
		}
		
		// 3) Convert the metadata for use in transport stream and report
		byte[] transportStreamBytes = metadataToTransportStream(this.metadata);
		System.out.println(new Date() + " CaptureMetadata.run() " + capture.target.fileName + " can read [" + aFile.canRead() + "] can write [" + aFile.canWrite() + "]  metadata elements count " + metadata.size() + ".");
		
		// 4) Write to the transport stream file
		String progress = "none";
		RandomAccessFile file = null;
		try {
			file = new RandomAccessFile(capture.target.fileName, "rw");	progress = "A";
	        byte[] transportStreamContentBytes = new byte[BLOCK_SIZE]; progress = "B";
	        // 4a) Make sure the file is big enough
			int amountRead = file.read(transportStreamContentBytes); progress = "C";  //Blocks if file is empty! We have no watchdog thread, but we should not get here if the file is empty.
			if (amountRead < BLOCK_SIZE) {
				System.out.println(new Date() + " WARNING: Capture in file " + capture.target.fileName + " was too small to hold metadata.  No metadata being added to stream.");
				return;
			}
			// 4b) Actually write out the data and close the file
			file.seek(0);
    		file.write(transportStreamBytes); progress = "D";
			file.close();
			System.out.println(new Date() + " CaptureMetadata.run() wrote metadata to " + capture.target.fileName + " successfully.");
		} catch (Throwable t) {
			System.out.println(new Date() + " ERROR: CaptureMetadata.run() failure on " + capture.target.fileName + " at progress " + progress + " with " + t.getClass().getCanonicalName() + " " + t.getMessage());
			System.err.println(new Date() + " ERROR: CaptureMetadata.run() failure on " + capture.target.fileName + " at progress " + progress + " with " + t.getClass().getCanonicalName() + " " + t.getMessage());
		} finally {
			if (file != null) try {file.close();} catch (Throwable t) {System.err.println(new Date() + " ERROR: CaptureMetadata.run() could not close file! " + t.getMessage());}
		}
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
        	//System.out.println("vmp [" + validateMdbPath +"]");
            connection = DriverManager.getConnection("jdbc:ucanaccess://" + validateMdbPath + ";singleConnection=true");
            statement = connection.createStatement();
            String query = "select * from " + recordsTableName + " where FILENAME='" + targetFileName + "'";
            System.out.println("Getting Metadata using [" + query + "]");
            rs = statement.executeQuery(query);
            while (rs.next()){
                boolean debug = false;
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
				
				switch (namePair[TYPE]) {
				case "Long":
					if ("OriginalAirdate".equals(namePair[JSONID])) {
						long airDateNumber = getDateNumberFromText(rs.getString(namePair[DBID]));
						if (airDateNumber == 0) {
							airDateNumber = getDateNumberFromText(rs.getString("MOVIE_YEAR") + "0101");
						}
						if (airDateNumber !=0) metadata.put("OriginalAirdate", airDateNumber);
					} else if (!namePair[DBID].isEmpty()) {
						metadata.put(namePair[JSONID], rs.getTimestamp(namePair[DBID]).getTime()/1000);
					} else {
						metadata.put(namePair[JSONID], namePair[DEFAULT]);
					}
					break;
				case "Boolean":
					if (!namePair[DBID].isEmpty()) {
						metadata.put(namePair[JSONID], rs.getBoolean(namePair[DBID])?"1":"0");
					} else {
						metadata.put(namePair[JSONID], namePair[DEFAULT]);
					}
					break;
				case "String":
					switch (namePair[JSONID]) {
					case "EpisodeNumber":
						// We do not have episode number in the database.  Do nothing here. Populate based on EpisodeTitle. 
						break;
					case "SeriesID":
						// We do not have series ID in the database.  Do nothing here. Populate based on EpisodeTitle. 
						break;
					case "EpisodeTitle":
						// We populate episode *number* here. If not found, populated with "".
						String episodeNumber = getEpisodeNumberFromEpisodeTitle(rs.getString(namePair[DBID])); //"S10E11"
						metadata.put("EpisodeNumber", episodeNumber);
						
						metadata.put("EpisodeTitle", rs.getString(namePair[DBID]));
						
						// Remove (SxxEyy) from EpisodeTitle, if it exists, and overwrite.
						if (episodeNumber.length() > 0) {
							String cleanedEpisodeTitle = rs.getString(namePair[DBID]).replace(episodeNumber, "").replace("()", "").trim();
							if (cleanedEpisodeTitle.length() > 0) {
								metadata.put("EpisodeTitle", cleanedEpisodeTitle);
							} else {
								metadata.put("EpisodeTitle", rs.getString("TITLE")); // Better than nothing?
							}
						}
						
						// We populate series ID here.
						String seasonNumber = getSeasonNumberFromEpisodeNumber(episodeNumber); // "10"
						metadata.put("SeriesID", "C" + seasonNumber + "EN" + Crc16.getCrc16(rs.getString("TITLE")));

						break;
					default:
						if (!namePair[DBID].isEmpty()) {
							metadata.put(namePair[JSONID], rs.getString(namePair[DBID]));
						} else {
							metadata.put(namePair[JSONID], namePair[DEFAULT]);
						}
						break;
					}
				default:
					break;
				}
			} catch (Throwable t) {
            	System.out.println("Database issue: Unable to get " + namePair[JSONID] + " from " + namePair[DBID]  + " " + t.getMessage()) ;
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

	//2019-12-13 or 20191213
	public static long getDateNumberFromText(String dateString) {
		StringBuffer statusBuf = new StringBuffer();
		try {
			return DF.parse(dateString).getTime() / 1000;
		} catch (Throwable t) {
			statusBuf.append("(error parsing date with yyyy-MM-dd, ");
		}
		if (statusBuf.length() > 0) {
			try {
				return DF2.parse(dateString).getTime() / 1000;
			} catch (Throwable t) {
				statusBuf.append("error parsing date for yyyyMMdd)");
			}
		}
		//System.out.println(new Date() + " WARNING: Unable to parse AIRDATE database field [" + dateString + "] " + statusBuf.toString() + " setting to '0'");
		return 0;
	}

	// "(S10E11) Ka I Ka 'Ino, No Ka 'Ino"
    public static String getEpisodeNumberFromEpisodeTitle(String episodeTitle) {
    	int seasonLoc = episodeTitle.indexOf("(S");
    	int blankLoc = episodeTitle.indexOf(" ", seasonLoc);
    	if (seasonLoc > -1 && blankLoc > seasonLoc) {
    		return episodeTitle.substring(seasonLoc + 1, blankLoc - 1); // "S10E11"
    	} else {
    		System.out.println(new Date() + " WARNING: Unable to determine episode number from episode title [" + episodeTitle + "]");
    	}
    	return "";
	}
    
    public static String getSeasonNumberFromEpisodeNumber(String episodeNumber) {
    	if (episodeNumber.length() == 0) return "00";
    	int sLoc = episodeNumber.indexOf("S");
    	int eLoc = episodeNumber.indexOf("E", sLoc);
    	if (sLoc > -1 && eLoc > -1 && eLoc > sLoc && episodeNumber.length() > (sLoc + 3)) {
    		return episodeNumber.substring(sLoc + 1, sLoc + 3);
    	} else {
    		System.out.println(new Date() + " WARNING: Unable to determine season number from episodeNumber [" + episodeNumber + "]");
    	}
    	return "00";
    	
    }

	private static String transportStreamToJson(byte[] ts) {
        StringBuffer jsonBuffer = new StringBuffer();
        
    	if (ts.length != BLOCK_SIZE) {
            throw new IllegalArgumentException("transport stream too short for metadata");
        }
        
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
                jsonBuffer.append((char)ts[pkt * 188 + c]);
            }
        }
        if (jsonBuffer.length() == 0) {
            throw new IllegalArgumentException("transport stream does not contain any metadata");
        }
        return jsonBuffer.toString();
    }

	
	// From https://github.com/garybuhrmaster/HDHRUtil/blob/main/HDHRUtil-DVR-fileMetadata originally: metadataToTransportStream()
    private byte[] metadataToTransportStream(Map<String, Object> metadata) {
        byte[] TS = new byte[BLOCK_SIZE];
        for(int i = 0; i < 12032; i++) {
        	TS[i] = (byte) 0xFF;
        }
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
			switch (CaptureMetadata.getDataTypeForKey(key)) {
			case "Long":
			case "Boolean":
                builder.append(metadata.get(key));
				break;
			case "String":
        		builder.append(CaptureMetadata.quote((String)metadata.get(key)));
				break;
			default:
				break;
			}
        	builder.append(",");
		}
        builder.delete(builder.length() - 1, builder.length());
        builder.append("}");
        this.lastJson = builder.toString();
        //System.out.println("builder [" + this.lastJson + "]") ;
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
    
    private static String getDataTypeForKey(String key) {
    	for (int i = 0; i < REMAPPING.length; i++) {
    		if (REMAPPING[i][JSONID].contains(key)) {
    			return REMAPPING[i][TYPE];
    		}
    	}
    	System.out.println("WARNING: No data type for "  + key + " using 'String'");
		return "String";
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

	private String getJson() {
		return this.lastJson;
	}

	private boolean doesMatch(String jsonMetadataFromFile) {
		return jsonMetadataFromFile.equals(this.lastJson);
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
    		Map<String, Object> originalMetadata = captureMetadata.getMetadata();
			byte[] transportStreamBytes = captureMetadata.metadataToTransportStream(originalMetadata);
			
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
    		System.out.println("Metadata written to transport stream file successfully.");
    		
			try {
				progress = "11";
				file = new RandomAccessFile(testFileNameLocation, "r");
				progress = "12";
				
		        byte[] transportStreamContentBytes = new byte[BLOCK_SIZE];
				int amountRead = file.read(transportStreamContentBytes);
				progress = "13";
				System.out.println("There were " + amountRead + " bytes read from " + testFileNameLocation);
				file.close();
				String jsonMetadataFromFile = CaptureMetadata.transportStreamToJson(transportStreamContentBytes);
				if (captureMetadata.doesMatch(jsonMetadataFromFile)) {
					System.out.println("Metadata added to transport stream equals metadata read from transport stream.");
					System.out.println("original and read:" + jsonMetadataFromFile);
				} else {
					System.out.println("Metadata written not equal to what was read:");
					System.out.println("orig:" + captureMetadata.getJson());
					System.out.println("read:" + jsonMetadataFromFile);
				}
    		} catch (Throwable t) {
    			System.out.println("Failure at progress " + progress + " with " + t.getMessage());
    		} finally {
    			if (file != null) try {file.close();} catch (Throwable t) {System.out.println("Could not close file.. " + t.getMessage());}
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
	
	//Similar to https://introcs.cs.princeton.edu/java/61data/CRC16.java
	public static class Crc16 {
        private static final int[] CRC_TABLE = {
                0x0000, 0xC0C1, 0xC181, 0x0140, 0xC301, 0x03C0, 0x0280, 0xC241,
                0xC601, 0x06C0, 0x0780, 0xC741, 0x0500, 0xC5C1, 0xC481, 0x0440,
                0xCC01, 0x0CC0, 0x0D80, 0xCD41, 0x0F00, 0xCFC1, 0xCE81, 0x0E40,
                0x0A00, 0xCAC1, 0xCB81, 0x0B40, 0xC901, 0x09C0, 0x0880, 0xC841,
                0xD801, 0x18C0, 0x1980, 0xD941, 0x1B00, 0xDBC1, 0xDA81, 0x1A40,
                0x1E00, 0xDEC1, 0xDF81, 0x1F40, 0xDD01, 0x1DC0, 0x1C80, 0xDC41,
                0x1400, 0xD4C1, 0xD581, 0x1540, 0xD701, 0x17C0, 0x1680, 0xD641,
                0xD201, 0x12C0, 0x1380, 0xD341, 0x1100, 0xD1C1, 0xD081, 0x1040,
                0xF001, 0x30C0, 0x3180, 0xF141, 0x3300, 0xF3C1, 0xF281, 0x3240,
                0x3600, 0xF6C1, 0xF781, 0x3740, 0xF501, 0x35C0, 0x3480, 0xF441,
                0x3C00, 0xFCC1, 0xFD81, 0x3D40, 0xFF01, 0x3FC0, 0x3E80, 0xFE41,
                0xFA01, 0x3AC0, 0x3B80, 0xFB41, 0x3900, 0xF9C1, 0xF881, 0x3840,
                0x2800, 0xE8C1, 0xE981, 0x2940, 0xEB01, 0x2BC0, 0x2A80, 0xEA41,
                0xEE01, 0x2EC0, 0x2F80, 0xEF41, 0x2D00, 0xEDC1, 0xEC81, 0x2C40,
                0xE401, 0x24C0, 0x2580, 0xE541, 0x2700, 0xE7C1, 0xE681, 0x2640,
                0x2200, 0xE2C1, 0xE381, 0x2340, 0xE101, 0x21C0, 0x2080, 0xE041,
                0xA001, 0x60C0, 0x6180, 0xA141, 0x6300, 0xA3C1, 0xA281, 0x6240,
                0x6600, 0xA6C1, 0xA781, 0x6740, 0xA501, 0x65C0, 0x6480, 0xA441,
                0x6C00, 0xACC1, 0xAD81, 0x6D40, 0xAF01, 0x6FC0, 0x6E80, 0xAE41,
                0xAA01, 0x6AC0, 0x6B80, 0xAB41, 0x6900, 0xA9C1, 0xA881, 0x6840,
                0x7800, 0xB8C1, 0xB981, 0x7940, 0xBB01, 0x7BC0, 0x7A80, 0xBA41,
                0xBE01, 0x7EC0, 0x7F80, 0xBF41, 0x7D00, 0xBDC1, 0xBC81, 0x7C40,
                0xB401, 0x74C0, 0x7580, 0xB541, 0x7700, 0xB7C1, 0xB681, 0x7640,
                0x7200, 0xB2C1, 0xB381, 0x7340, 0xB101, 0x71C0, 0x7080, 0xB041,
                0x5000, 0x90C1, 0x9181, 0x5140, 0x9301, 0x53C0, 0x5280, 0x9241,
                0x9601, 0x56C0, 0x5780, 0x9741, 0x5500, 0x95C1, 0x9481, 0x5440,
                0x9C01, 0x5CC0, 0x5D80, 0x9D41, 0x5F00, 0x9FC1, 0x9E81, 0x5E40,
                0x5A00, 0x9AC1, 0x9B81, 0x5B40, 0x9901, 0x59C0, 0x5880, 0x9841,
                0x8801, 0x48C0, 0x4980, 0x8941, 0x4B00, 0x8BC1, 0x8A81, 0x4A40,
                0x4E00, 0x8EC1, 0x8F81, 0x4F40, 0x8D01, 0x4DC0, 0x4C80, 0x8C41,
                0x4400, 0x84C1, 0x8581, 0x4540, 0x8701, 0x47C0, 0x4680, 0x8641,
                0x8201, 0x42C0, 0x4380, 0x8341, 0x4100, 0x81C1, 0x8081, 0x4040,
            };
        
        public static String getCrc16(String string) {
        	if (string == null || string.length() == 0) return (Math.random() + "").substring(2,6);
            int crc = getCrc16Hex(string);
            return String.format("%1$04X", crc);
        }

		public static String getCrc16(byte[] bytes) {
            int crc = 0x0000;
            for (byte b : bytes) {
                crc = (crc >>> 8) ^ Crc16.CRC_TABLE[(crc ^ b) & 0xff];
            }
            return  String.format("%1$04X", crc);
		}

		public static int getCrc16Hex(String string) {
            byte[] bytes = string.getBytes();
            int crc = 0x0000;
            for (byte b : bytes) {
                crc = (crc >>> 8) ^ Crc16.CRC_TABLE[(crc ^ b) & 0xff];
            }
            return crc;
		}
	}
}
