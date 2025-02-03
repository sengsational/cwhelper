package org.cwepg.hr;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.text.NumberFormat;
import java.util.Date;
import java.util.StringTokenizer;

public class Target {

	String ip;
    String machineName;
	public String fileName;
    String title;
    int port;
    boolean valid = true;
    String invalidMessage;
    static boolean debug = false;//true;
    
    static final boolean dc = false; // dump compare
    
    public Target(){
        this.valid = false;
    }
    
    public Target(Target copyTarget, Capture copyCapture, String appendFileNameString, int retryCount) throws Exception {
        this(copyTarget.fileName, copyTarget.title);
        this.setFileName(copyTarget.fileName, "", "", copyCapture.getChannelProtocol(), Tuner.HDHR_TYPE, false, appendFileNameString, retryCount);
        this.valid = true;
    }
    
	private Target(String fileNameComplete, String title) throws Exception {
        this.fileName = fileNameComplete;
        if (debug) System.out.println(new Date() + " DEBUGFUSION Target fileName [" + fileNameComplete + "]");
        this.title = title;
        this.ip = InetAddress.getLocalHost().getHostAddress();
        this.machineName = InetAddress.getLocalHost().getHostName();
	}

    // Called from TinyConnection
    public Target(String fileName, String title, String defaultRecordPath, String analogFileExtension, String protocol, int tunerType) throws Exception {
        this(fileName, title);
        this.setFileName(fileName, defaultRecordPath, analogFileExtension, protocol, tunerType, false, null);
        this.valid = true;
    }
    
    public Target(String fileName, String title, String defaultRecordPath, String analogFileExtension, String channelProtocol, int tunerType, String appendFileNameString) throws Exception{
        this(fileName, title);
        this.setFileName(fileName, defaultRecordPath, analogFileExtension, channelProtocol, tunerType, false, appendFileNameString);
        this.valid = true;
    }

    public void setFileName(String fileName, String defaultRecordPath, String analogFileExtension, String protocol, int tunerType, boolean update, String appendFileNameString) throws Exception {
    	setFileName(fileName, defaultRecordPath, analogFileExtension, protocol, tunerType, update, appendFileNameString, 1);
    }
        
    public void setFileName(String fileName, String defaultRecordPath, String analogFileExtension, String protocol, int tunerType, boolean update, String appendFileNameString, int retryCount) throws Exception {
        if (appendFileNameString != null && appendFileNameString.indexOf("WATCH") < 0 && !appendFileNameString.equals("")){
            fileName = Target.insertTextIntoFilename(fileName, appendFileNameString);
        }
        
        // Set convenience variables 
        String fileNameNoPath = Target.getNoPathFilename(fileName);
        String fileNameNoPathNoExtension = Target.getNoPathNoExtensionFilename(fileName);
        boolean emptyDefaultRecordPath = defaultRecordPath == null || defaultRecordPath.equals("");
        boolean emptyAnalogFileExtension = analogFileExtension == null || analogFileExtension.equals("");
        boolean emptyProtocol = protocol == null || protocol.equals("");
        boolean noPathOnFileName = fileName.equals(fileNameNoPath);
        boolean quiet = update || fileNameNoPathNoExtension.toUpperCase().equals("WATCH");
        if (!emptyDefaultRecordPath && !defaultRecordPath.endsWith("\\")) defaultRecordPath += "\\";
        if (debug) System.out.println(new Date() + " DEBUGFUSION  setFileName had [" + fileNameNoPath + "] and [" + fileNameNoPathNoExtension + "]");
        switch(tunerType){
        case Tuner.FUSION_TYPE:
            String fusionFileName = "";
            if (emptyDefaultRecordPath){
                if (!quiet) System.out.println(new Date() + " WARNING: Blank default record path for Fusion tuner.  Using c:\\");
                defaultRecordPath = "c:\\";
            }
            if (!noPathOnFileName){
                System.out.println(new Date() + " WARNING: Path on file name ignored.  Using default record path " + defaultRecordPath + ".");
            }
            if (emptyProtocol){
                if (!quiet) System.out.println(new Date() + " WARNING: Protocol not specified for Fusion recording. Assuming 'qam256'");
                protocol = "qam256";
            }
            if(!emptyAnalogFileExtension){
                if (protocol != null && protocol.startsWith("analog")){
                    fusionFileName = fileNameNoPathNoExtension + "." + analogFileExtension;
                } else {
                    fusionFileName = fileNameNoPathNoExtension + ".tp";
                }
            } else {
                if (!quiet) System.out.println(new Date() + " WARNING: Fusion target creation without analog file extension.  Unexpected. Going with TP.");
                fusionFileName = fileNameNoPathNoExtension + ".TP";
            }
            this.fileName = defaultRecordPath + fusionFileName;
            break;
        case Tuner.MYHD_TYPE:
            if(noPathOnFileName && !emptyDefaultRecordPath){
                this.fileName = defaultRecordPath + fileName;
                if (!quiet) System.out.println(new Date() + " WARNING: No path included on file name. Using defaultRecordPath for MyHD tuner " + defaultRecordPath);
            } else if (noPathOnFileName){
                this.fileName = "c:\\" + fileName;
                if (!quiet) System.out.println(new Date() + " WARNING: No path included on file name and blank default record path for MyHD tuner.  Using c:\\");
            } else {
                this.fileName = fileName; //had path on file name
            }
            break;
        case Tuner.HDHR_TYPE:
            if (noPathOnFileName){
                this.fileName = "c:\\" + fileName;
                if (!quiet) System.out.println(new Date() + " WARNING: No path included on file name. Using c:\\ for HDHR tuner.");
            } else {
                this.fileName = fileName; //had path on file name
            }
            break;
        }
        this.mkdirsAndTestWrite(true, this.fileName, retryCount);
    }
    
    // this.ip + "|" + this.port + "|" + this.fileName + "|" + this.title + "|" + this.machineName;
	public Target(String persistanceData, double d) {
        StringTokenizer tok = new StringTokenizer(persistanceData, "|");
        this.ip = tok.nextToken();
        this.port = Integer.parseInt(tok.nextToken());
        this.fileName = tok.nextToken();
        this.title = tok.nextToken();
        if (tok.hasMoreTokens()) this.machineName = tok.nextToken();
    }

    /**
	 * Call to create the path for the target file.  Use deleteAfterCreate to test if creation is possible. 
     * @param targetFileName
     * @param deleteAfterCreate
     * @throws Exception
	 */
    public boolean mkdirsAndTestWrite(boolean deleteAfterCreate, String filePathName, int retries) throws Exception {
        boolean result = false;
        File path = new File(filePathName).getParentFile();

        for (;retries > 0 && !result; retries--){
            try {
                if (this.isWatch()){
                    System.out.println(new Date() + " targetFileName was 'WATCH', no file will be saved.");
                    result = true;
                } else if (path == null || !path.exists()){
                    String messageEnd = "Creating the path.";
                    if (deleteAfterCreate){
                        messageEnd = "Checking ability to create the path.";
                    }
                    System.out.println(new Date() + " The path for targetFileName '" + filePathName + "' does not exist. " + messageEnd);
                    boolean directoriesCreated = false;
                    if (path != null) directoriesCreated = path.mkdirs(); 
                    if (!directoriesCreated){
                        result = false;
                        throw new Exception("Could not create directory for targetFileName '" + filePathName + "' (path [" + path + "])" );
                    } else { // reported that the directory was created
                        // attempt to write a file.
                        String writeResult = this.testPathWritability();
                        result = true;
                        if (deleteAfterCreate) {
                            boolean worked = path.delete();
                            System.out.println(new Date() + (worked?" Path removed.":" Could not remove path. " + writeResult));
                        } else {
                            System.out.println(new Date() + " Leaving the path on the drive. " + writeResult);
                        }
                    }
                } else if (path.exists()){
                    String writeResult = this.testPathWritability();
                    result = true;
                    System.out.println(new Date() + " Leaving existing path on the drive. " + writeResult);
                }
            } catch (Exception e){
                // Don't throw the "no longer available" message. Instead, retry.  DRS 20150726
                if (retries > 1) {
                    System.err.println(new Date() + " WARNING: Problem with file writability. Retries left " + retries + " " + e.getMessage() + "  " + path);
                    try {Thread.sleep(3000);} catch (Exception e2){}
                } else {
                    System.err.println(new Date() + " ERROR: Problem with file writability. " + e.getMessage() + "  " + path);
                    System.out.println(new Date() + " ERROR: Problem with file writability. " + e.getMessage() + "  " + path);
                    throw e;
                }
            }
        }
        return result;
    }

    /**
	 * Call to make sure the file name does not already exist.
     * If it does, make a new file name similar to the current one. 
     * Only called from CaptureHdhr and CaptureExternalTuner 
     * (where we are responsible for writing the files)
	 */
    public void fixFileName() {
        if (this.isWatch()) return;
        try {
            File aFile = new File(this.fileName);
            int suffix = 1;
            while (aFile.exists() && suffix < 500){
                String nameAndExt = aFile.getName();
                String path = aFile.getParent();
                if (nameAndExt.lastIndexOf(".") > -1){
                    int dotLoc = nameAndExt.lastIndexOf(".");
                    String name = nameAndExt.substring(0, dotLoc);
                    if (name.indexOf("_") == name.length() - 4){
                        name = name.substring(0,name.indexOf("_"));
                    }
                    String ext = nameAndExt.substring(dotLoc);
                    NumberFormat threeDigit = NumberFormat.getInstance();
                    threeDigit.setMinimumIntegerDigits(3);
                    aFile = new File(path + File.separator + name + "_" + threeDigit.format(suffix) + ext);
                    this.fileName = aFile.getPath();
                }
                suffix++;
            }
        } catch (Throwable t){
            valid = false;
            System.out.println(new Date() + " ERROR: Could not secure a good write destination. " + t.getMessage());
            System.err.println(new Date() + " ERROR: Could not secure a good write destination. " + t.getMessage());
            t.printStackTrace();
        }
        
    }

    // DRS 20241129 - Added method - Issue #48
    public void removeFile() {
        if (this.isWatch()) return;
        try {
            File aFile = new File(this.fileName);
            if (aFile.exists()) {
            	long sizeOfFile = aFile.length();
            	boolean fileDeleted = aFile.delete();
            	System.out.println(new Date() + (fileDeleted?" Deleted ":"Unable to delete ") + aFile.getAbsolutePath() + " of size " + sizeOfFile);
            } else {
            	System.out.println(new Date() + "File " + aFile.getAbsolutePath() + " was not found.  Nothing to delete.");
            }
        } catch (Throwable t) {
        	System.out.println(new Date() + " Unable to remove file. " + t.getClass().getName() + " " + t.getMessage());
        }
	}
    
    // DRS 20150718 - new method
    public String testPathWritability() throws Exception { 
        File aFile = new File(this.fileName);
        String testFileName = aFile.getParent() + "\\" + ("" + Math.random()).substring(2);
        File testFile = new File(testFileName);
        BufferedWriter writer = new BufferedWriter(new FileWriter(testFile));
        writer.append('D');
        writer.close();
        if (!testFile.exists()) throw new Exception("could not create test file in " + aFile.getParent());
        if (!testFile.canWrite()) throw new Exception("could not write to file in " + aFile.getParent());
        boolean deleteWorked = testFile.delete();
        System.out.println(new Date() + " INFO: Path [" + aFile.getParent() + "] is writable. " + (deleteWorked?"Temp file deleted.":"Not able to delete temp file."));
        return aFile.getParent();
    }
    
    public int setNextAvailablePort() {
        this.port = PortManager.getNextAvailablePort(180);
		return this.port;
	}
	
		
    public String getPersistanceData() {
        return this.ip + "|" + this.port + "|" + this.fileName + "|" + this.title + "|" + this.machineName;
    }

    public boolean isValid() {
        return valid;
    }
    
    public boolean isWatch() {
        if (this.fileName == null) {
            System.out.println(new Date() + " ERROR: Target fileName was null during WATCH check!");
            return false;
        }
        if (getNoPathNoExtensionFilename(this.fileName).toUpperCase().equals("WATCH")) return true;
        return false;
    }
    
    public static String getNoPathFilename(String pathFile){
        String justFileName = pathFile;
        if (pathFile != null && ((pathFile.indexOf("\\") > -1) || pathFile.indexOf("/") > -1)) {
            int lastBackslash = pathFile.lastIndexOf("\\");
            int lastSlash = pathFile.lastIndexOf("/");
            int lastKey = Math.max(lastBackslash, lastSlash);
            if (!pathFile.endsWith("\\") && !pathFile.endsWith("/")){
                justFileName = pathFile.substring(lastKey + 1);
            }
        }
        if (debug) System.out.println(new Date() + " DEBUGFUSION  getNoPathFilename returned [" + justFileName + "]");
        return justFileName;
    }

    public static String getNoPathNoExtensionFilename(String pathFile){
        String fn = getNoPathFilename(pathFile);
        String returnString = fn;
        int dotLoc = fn.lastIndexOf(".");
        if (dotLoc < 1 || (fn.length() - dotLoc) > 4 || fn.endsWith("."));  //no extension or long extention that's not really an extension
        else returnString = fn.substring(0,dotLoc);
        if (debug) System.out.println(new Date() + " DEBUGFUSION  getNoPathExtensionFilename returned [" + returnString + "]");
        return returnString;
    }
    
    // DRS20241123 - Added method - Issue #47
    static String getNoExtensionFilename(String fn) {
        String returnString = fn;
        int dotLoc = fn.lastIndexOf(".");
        if (dotLoc < 1 || (fn.length() - dotLoc) > 4 || fn.endsWith("."));  //no extension or long extention that's not really an extension
        else returnString = fn.substring(0,dotLoc);
        if (debug) System.out.println(new Date() + " getNoExtensionFilename returned [" + returnString + "]");
        return returnString;
	}
    

    public static String getFileExtension(String fileName) {
        int dotLoc = fileName.lastIndexOf(".");
        if (dotLoc > 0 && !fileName.endsWith(".")){
            return fileName.substring(dotLoc + 1);
        } else return "";
    }

    
    public String getNoPathFilename() {
        return Target.getNoPathFilename(this.fileName);
    }
    
    public String getNoPathNoExtensionFilename(){
        return Target.getNoPathNoExtensionFilename(this.fileName);
    }

	public String getNoExtensionFilename() {
        return Target.getNoExtensionFilename(this.fileName);
	}

	private static String insertTextIntoFilename(String fileName, String appendFileNameString) {
        StringBuffer fnBuf = new StringBuffer(fileName);
        int loc = fileName.indexOf(Target.getNoPathNoExtensionFilename(fileName));
        int length = Target.getNoPathNoExtensionFilename(fileName).length();
        if (debug) System.out.println(new Date() + " DEBUGFUSION  inserting [" + appendFileNameString + "] into filename");
        return fnBuf.insert((loc + length), appendFileNameString).toString();
    }
    
    public String getFileNameOrWatch(){
        if (isWatch()) return "(watch)";
        else return this.fileName;
    }
    
    public static String getNonAppendedFileName(String fileName) {
        String[] parts = fileName.split("\\.");
        int lastBeforeMarkLoc = parts.length - 2;
        if (lastBeforeMarkLoc >= 0) {
            String maybeHasAppend = parts[lastBeforeMarkLoc];
            String[] segs = maybeHasAppend.split("_");
            if (segs.length == 1) return fileName;
            lastBeforeMarkLoc = segs.length - 1;
            String maybeInteger = segs[lastBeforeMarkLoc];
            try {Integer.parseInt(maybeInteger);} catch (Throwable t) {return fileName;}
            return fileName.replace("_" + maybeInteger, "");
        }
        return fileName;
    }
    
    public String getTitle() {
        if (title == null) return "";
        return title;
    }

    public void setInvalidMessage(String message) {
        this.valid = false;
        this.invalidMessage = message;
    }

    public String getInvalidMessage() {
        return this.invalidMessage;
    }

	public String toString() {
		return this.ip + ":" + this.port + " " + this.fileName + " " + this.title + " " + this.machineName;
	}

    /*
     * hashCode, equals, compareTo, toString
     * 
     */

    @Override
    public int hashCode() {
        final int PRIME = 31;
        int result = 1;
        result = PRIME * result + ((fileName == null) ? 0 : fileName.hashCode());
        result = PRIME * result + ((title == null) ? 0 : title.hashCode());
        return result;
    }


    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final Target other = (Target) obj;
        if (fileName == null) {
            if (other.fileName != null)
                return false;
        } else if (!fileName.equalsIgnoreCase(other.fileName)){
            if (dc) System.out.println("\t\tNot same fileName");
            return false;
        }
        if (title == null) {
            if (other.title != null)
                return false;
        } else if (!title.equalsIgnoreCase(other.title)){
            if (dc) System.out.println("\t\tNot same title");
            return false;
        }
        return true;
    }

    public int compareTo(Object otherTarget){
        if (otherTarget instanceof Target){
            Target ot = (Target)otherTarget;
            return ot.fileName.compareTo(this.fileName);
        }
        return -1;
    }
    
	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception {
		
	    boolean testAppendRemoval = true;
	    if (testAppendRemoval) {
	        String testFile = "c:\\some\\path\\someFileName_123.tp";
	        System.out.println("With    append [" + testFile + "] result: [" +Target.getNonAppendedFileName(testFile) + "]");
	        testFile = "c:\\some\\path\\someFileName.tp";
            System.out.println("Without append [" + testFile + "] result: [" +Target.getNonAppendedFileName(testFile) + "]");
            testFile = "c:\\some\\path.withdot\\someFileName_023.tp";
            System.out.println("With append [" + testFile + "] result: [" +Target.getNonAppendedFileName(testFile) + "] has dot in path.");
            testFile = "c:\\some\\path\\some.FileName_023.tp";
            System.out.println("With append [" + testFile + "] result: [" +Target.getNonAppendedFileName(testFile) + "] has dot in file.");
	    }
	    
	    boolean testTargetCreation = false;
	    if (testTargetCreation) {
            String[][] testData = {
                    //{"HDHR non-existing directory full path specified"              ,"c:\\doesnotexist\\someFileName.tp", "title", null,null,null, "" + Tuner.HDHR_TYPE},
                    //{"HDHR existing directory, full path specified"                 ,"c:\\tv\\someFileName.tp"          , "title", null,null,null, "" + Tuner.HDHR_TYPE},
                    //{"MYHD non-existing directory full path specified"              ,"c:\\doesnotexist\\someFileName.tp", "title", "c:\\tv",null,"8vsb", "" + Tuner.MYHD_TYPE},
                    //{"MYHD existing directory full path specified"                  ,"c:\\tv\\someFileNameMyhd.tp"      , "title", "c:\\tv",null,"qam", "" + Tuner.MYHD_TYPE},
                    //{"MYHD no path specified"                                       ,"someFileNameMyhdNP.tp"            , "title", "c:\\tv",null,"qam", "" + Tuner.MYHD_TYPE},
                    //{"FUSN no path, no extension specified"                         ,"someFusionFile"                   , "title", "c:\\tv","AVI","analogCable", "" + Tuner.FUSION_TYPE},
                    //{"FUSN no path, good extension specified"                       ,"someFusionFile.mpg"               , "title", "c:\\tv","AVI","analogCable", "" + Tuner.FUSION_TYPE},
                    //{"FUSN no path, bad extension specified"                        ,"someFusionFile.xyz"               , "title", "c:\\tv","AVI","analogCable", "" + Tuner.FUSION_TYPE},
                    {"Allens Problem Target"                                          ,"c:\\hdhr6\\Test[HD0](07-20-10).tp", "Test",  ""      ,"",   "8vsb",        "" + Tuner.HDHR_TYPE},
            };
            for (int i = 0; i < testData.length; i++){
                System.out.println(">>>>>>>>>" + testData[i][0] + "<<<<<<<<<<<<<");
                System.out.println( new Target(testData[i][1],testData[i][2],testData[i][3], testData[i][4], testData[i][5], Integer.parseInt(testData[i][6])));
                System.out.println("-------------------------------------");
            }
	    }
	}
}
