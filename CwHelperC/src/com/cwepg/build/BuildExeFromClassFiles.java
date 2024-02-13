/*
 * Created on Apr 30, 2021
 *
 */
package com.cwepg.build;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Writer;
import java.util.Date;

// import org.cwepg.hr.CaptureManager;

public class BuildExeFromClassFiles {
    /**
     * Before running this class, 
     *      Commit the changes to source control (the version number is used later), then 
     *      Run the main method here, ending in "...CwHelper.jar ERROR: FILE NOT FOUND", then
     *      Use Eclipse IDE "Make runnable jar" process (the version number will be aligned by this second run).
     * 
     * This class  
     *          1) pulls the version from the source control system
     *          2) writes the version into the CwHelper_lib folder as "version.txt"
     *          3) updates the "version.txt" file inside of "cw_icons.jar" resource file
     *          4) packages the class files as an EXE file using Jar2Exe, creating CwHelper.exe
     *          5) zips the EXE file into CwHelper(version).zip (so it can be managed on a Google drive, that doesn't allow EXE files)
     *          6) renames the (already created) runnable jar file to include the version in the file name
     *          7) optionally signs the jar file (use 'doJarSigning' boolean)
     *          
     * Changes made here now must be copied to the alternative source control project (use WinMerge on c:\my\dev\eclipsewrk\CwHelper and C:\my\dev\gitrepo\CwHelperC).         
     * 
     */
    public static final String USER = "C:\\Users\\tmpet\\";
    public static final String PROJECT_DIRECTORY = USER + "git\\cwhelper\\CwHelperC\\";
    public static final String J2E_WIZ = "C:\\Program Files (x86)\\Jar2Exe Wizard\\j2ewiz.exe";
    public static final String KEYSTORE = "C:\\Users\\Owner\\AndroidStudioProjects\\KnurderKeyStore.jks";
    public static final String LIBRARY_DIRECTORY = PROJECT_DIRECTORY + "CwHelper_lib\\";
    public static final String VERSION_FILE_NAME = "version.txt";
    public static final String STOREPASS = "Hnds#1111";
    public static final String KEYPASS = "Hnds#1111";
    public static final String JRE_PATH = USER + ".p2\\pool\\plugins\\org.eclipse.justj.openjdk.hotspot.jre.full.win32.x86_64_17.0.9.v20231028-0858\\jre\\";
    public static final String BASE_VERSION = "5-4-0-";
    public static final String COMMA_VERSION = "5,4,0,";
    public static final String DOT_VERSION = "5.4.0.";

    public static void main(String[] args) throws Exception {
    	
        boolean doJarSigning = false; // DRS 20230513 - Signing prevents Terry from iterating on his own.
        
        boolean forceRevisionNumber = false; //>>>>>>>>>>>>>>>>>>>>>>>>>>>>
        String revision = "999";
        if (forceRevisionNumber) {
            revision = "804";
        } else {
        	String fullVersion = ""; 
        	try {
        		fullVersion = getVersionFromJarFile("CwHelper.jar");
                System.out.println("Version inside the jar file: " + fullVersion);
    			String[] versionArray = fullVersion.split(","); //[5,0,0,999]
    			int arraySize = versionArray.length; //4
    			revision = versionArray[arraySize - 1].trim();
        	} catch (Exception e) {
        		System.out.println("CwHelper.jar does not exist.  Incrementing the revision from version.txt....");
        	}
        	if (fullVersion.equals("")) {
                int revisionNumber = getRevisionFromFile();
                revision = "" + ++revisionNumber;
        	}
        }
        
        writeVersionToLibDirectory(COMMA_VERSION, revision, LIBRARY_DIRECTORY + VERSION_FILE_NAME);
        writeVersionToJarFile(LIBRARY_DIRECTORY + VERSION_FILE_NAME, LIBRARY_DIRECTORY + "cw_icons.jar");
        
        String[] j2ewizBuildCwHelperParms = {
                        J2E_WIZ,
                        "/jar ", PROJECT_DIRECTORY + "classes",
                        "/o", PROJECT_DIRECTORY + "CwHelper.exe",
                        "/m", "org.cwepg.hr.ServiceLauncher",
                        "/type","windows",
                        "/minjre","1.6",
                        "/platform","windows",
                        "/checksum",
                        "/embed", LIBRARY_DIRECTORY + "commons-codec-1.15.jar",            //
                        "/embed", LIBRARY_DIRECTORY + "commons-lang3-3.8.1.jar",              //commons-lang3-3.8.1.jar//commons-lang-2.6.jar
                        "/embed", LIBRARY_DIRECTORY + "commons-logging-1.2.jar",         //commons-logging-1.2.jar//commons-logging-1.1.3.jar
                        "/embed", LIBRARY_DIRECTORY + "cw_icons.jar",                      //
                        "/embed", LIBRARY_DIRECTORY + "hsqldb-2.5.0.jar",                        //hsqldb-2.5.0.jar//hsqldb.jar
                        "/embed", LIBRARY_DIRECTORY + "httpasyncclient-4.1.5.jar",         //
                        "/embed", LIBRARY_DIRECTORY + "httpasyncclient-cache-4.1.5.jar",   //
                        "/embed", LIBRARY_DIRECTORY + "httpclient-4.5.13.jar",             //
                        "/embed", LIBRARY_DIRECTORY + "httpcore-4.4.15.jar",               //
                        "/embed", LIBRARY_DIRECTORY + "httpcore-nio-4.4.15.jar",           //
                        "/embed", LIBRARY_DIRECTORY + "jackcess-3.0.1.jar",               //jackcess-3.0.1.jar//jackcess-2.1.11.jar
                        "/embed", LIBRARY_DIRECTORY + "jna-5.7.0.jar",                     //
                        "/embed", LIBRARY_DIRECTORY + "jna-platform-5.7.0.jar",            //
                        "/embed", LIBRARY_DIRECTORY + "mailapi.jar",                       //
                        "/embed", LIBRARY_DIRECTORY + "smtp.jar",                          //
                        "/embed", LIBRARY_DIRECTORY + "ucanaccess-5.0.1.jar",              //ucanaccess-5.0.1.jar//ucanaccess-4.0.4.jar
                        //"/embed", wDir + "CwHelper.p12",                         // keystore for secure web server
                        "/icon", "#" + PROJECT_DIRECTORY + "cw_logo16.ico, 0#",
                        "/pv", COMMA_VERSION + "0",
                        "/fv",  COMMA_VERSION + revision,
                        "/ve", "ProductVersion=" + DOT_VERSION + "0",
                        "/ve", "ProductName=CW_EPG",
                        "/ve", "#LegalCopyright=Copyright (c) 2008 - 2023#",
                        "/ve", "#SpecialBuild=" + COMMA_VERSION + " " +  revision + "#",
                        "/ve", "#FileDescription=Capture Manager#",
                        "/ve", "FileVersion=" + DOT_VERSION + "*",
                        "/ve", "OriginalFilename=CwHelper.exe",
                        "/ve", "#Comments=Background Job for CW_EPG#",
                        "/ve", "#CompanyName=CW_EPG Team# /ve #InternalName=" + COMMA_VERSION + "0#",
                        "/ve", "#LegalTrademarks=CW_EPG Team#",
                        "/splash", PROJECT_DIRECTORY + "CW_Logo.jpg",
                        "/closeonwindow:false",
                        "/splashtitle", "#Please wait ...#"
                    };
        //for (int i = 0; i < parms.length; i++) {
        //    parms[i] = parms[i].replaceAll("#", "\"");
        //    System.out.println("[" + parms[i] + "]");
        //}
        
        
        if (runOsProcess(j2ewizBuildCwHelperParms, "successfully", null)) {
            String zipDestinationFileNameString = "CwHelper_" + BASE_VERSION + revision + ".zip";
            File zipDestinationFile = new File(PROJECT_DIRECTORY + zipDestinationFileNameString);
            
            // If destination already exists, rename it first
            if (zipDestinationFile.exists()) {
                int randomInt = (int)(Math.random() * 10000);
                zipDestinationFile.renameTo(new File(PROJECT_DIRECTORY + "CwHelper_" + BASE_VERSION + revision + "_" + randomInt + ".zip"));
            }

            String[] zipTheExeParams = {JRE_PATH + "bin\\" + "jar.exe", "-cMfv", zipDestinationFileNameString, "CwHelper.exe"};
            if (runOsProcess(zipTheExeParams, "deflated", PROJECT_DIRECTORY)) {
                //Rename what we presume is the previously manually created "Runnable Jar"
                File runnableJarFile = new File(PROJECT_DIRECTORY + "CwHelper.jar");
                File runnableJarFileNewName = new File(PROJECT_DIRECTORY + "CwHelper_" + BASE_VERSION + revision + ".jar");
                
                // If destination already exists, rename it first
                if (runnableJarFileNewName.exists()) {
                    int randomInt = (int)(Math.random() * 10000);
                    runnableJarFileNewName.renameTo(new File(PROJECT_DIRECTORY + "CwHelper_" + BASE_VERSION + revision + "_" + randomInt + ".jar"));
                }
                
                if (runnableJarFile.renameTo(runnableJarFileNewName)) {
                    System.out.println("CwHelper.jar renamed to CwHelper_" + BASE_VERSION + revision + ".jar");
                    String contentsOfVersionFile = getVersionFromJarFile("CwHelper_" + BASE_VERSION + revision + ".jar");
                    System.out.println("Version inside the jar file: " + contentsOfVersionFile);
                    if (contentsOfVersionFile.trim().equals(COMMA_VERSION + revision)) {
                        System.out.println("version.txt is aligned");
                        if (doJarSigning) {
                            String[] signTheJarFile = {JRE_PATH + "bin\\" + "jarsigner.exe","-tsa","http://timestamp.digicert.com","-tsacert","alias","-keystore", KEYSTORE,"-storepass",STOREPASS,"-keypass",KEYPASS,"CwHelper_" + BASE_VERSION + revision + ".jar", "knurderkeyalias"};
                            if (runOsProcess(signTheJarFile, "jar signed.", null)) {
                                String[] verifyTheJarFile = {JRE_PATH +"bin\\" + "jarsigner.exe", "-verify","CwHelper_" + BASE_VERSION + revision + ".jar"};
                                if (runOsProcess(verifyTheJarFile, "jar verified.", null)) {
                                    System.out.println("SUCCESSFULLY SIGNED CwHelper_" + BASE_VERSION + revision + ".jar");
                                } else {
                                    System.out.println("ERROR: Unable to sign the jar file.");
                                }
                            }
                        } else {
                            System.out.println("NOTICE: doJarSigning was false.  Jar file remains unsigned.");
                        }
                    } else {
                        System.out.println("ERROR!!!!!!!! The version.txt inside the jar file does not align with the revision [" + COMMA_VERSION +  revision + "] that we just renamed it to. ERROR!!!!!!!!!" );
                    }
    
                } else {
                    System.out.println("Failed to rename " + runnableJarFile.getAbsolutePath() + " " + (runnableJarFile.exists()?"File Existed":"ERROR: FILE NOT FOUND"));
                }

            } else {
                System.out.println("ERROR: Exe not zipped into compressed file.");
            }
            
            
        } else {
            System.out.println("ERROR: Exe not created, so no attempt to zip being made.");
        }
    }

    private static String getVersionFromJarFile(String jarFileName) throws IOException {
        return JarUpdater.getFileContent(jarFileName, "version.txt");
    }

    private static void writeVersionToJarFile(String versionFileName, String jarFileName) throws Exception {
        File[] contents = {new File(versionFileName)};
        File jarFile = new File(jarFileName);

        try {
            JarUpdater.updateZipFileWithFilesOnDisk(jarFile, contents);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void writeVersionToLibDirectory(String version, String revision, String versionFileName) {
        try {
            System.out.println(new Date() + " Saving to " + versionFileName + " with revision " + revision);
            Writer writer = new FileWriter(versionFileName);
            writer.write(version + revision);
            writer.close();
        } catch (Exception e) {
            System.err.println(new Date() + " ERROR: Could not save version file. " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static boolean runOsProcess(String[] parms, String requiredResult, String wDir) throws Exception {
        System.out.println("\n--------------------- run OS process ----------------------------");
        StringBuffer debugString = new StringBuffer();
        debugString.append("runOsProcess with [").append(String.join(" ", parms)).append("] and create directory [").append(wDir).append("]\n");
        boolean processResult = false;
        ProcessBuilder builder = new ProcessBuilder(parms);
        debugString.append("builder created.\n");
        if (wDir != null) {
            builder.directory(new File(wDir));
            debugString.append("directory set to [").append(wDir).append("]\n");
        }
        builder.redirectErrorStream(true);
        debugString.append("starting process.\n");
        Process process = builder.start();
        InputStream is = process.getInputStream();
        debugString.append("reading input stream.\n");
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        
        String line = null;
        while ((line = reader.readLine()) != null) {
            System.out.println("output: [" + line + "]");
            if (requiredResult !=null && line.contains(requiredResult)) processResult = true;
        }
        if (requiredResult == null) processResult = true;
        
        if (!processResult) {
            System.out.println("DEBUG: " + debugString);
        }
        System.out.println("--------------------- END run OS process ----------------------------\n");

        return processResult;
    }

    private static int getRevisionFromFile() throws Exception {
    	String filePath = LIBRARY_DIRECTORY +  VERSION_FILE_NAME;
    	int revisionNumber = 9999;
    	StringBuffer progressBuf = new StringBuffer();
    	if (!new File(filePath).exists()) {
    		progressBuf.append("filePath " + filePath + " did not exist.").append("\n");
    		revisionNumber = 1000;
    		String revision = "" +  revisionNumber;
    		BuildExeFromClassFiles.writeVersionToLibDirectory(COMMA_VERSION, revision, filePath);
    	} else {
    		try {
    			BufferedReader in = new BufferedReader(new FileReader(filePath));
    			StringBuffer buf = new StringBuffer();
    			String l = "";
    			while ((l = in.readLine()) != null) {
    	    		progressBuf.append("read line from " + filePath + ".").append("\n");
    				buf.append(l); buf.append("\n");
    				break;
    			}
    			in.close();
    			// 5,0,0,9999
    			String fullVersion = buf.toString();
    			progressBuf.append("found [" + fullVersion + "]").append("\n");
    			String[] versionArray = fullVersion.split(","); //[5,0,0,999]
    			int arraySize = versionArray.length; //4
    			String revision = versionArray[arraySize - 1].trim();
    			progressBuf.append("revision [" + revision + "]").append("\n");
    			revisionNumber = Integer.parseInt(revision);
    		} catch (Exception e) {
    			System.out.println("Progress Buffer: " + progressBuf);
    			System.out.println("Something bad happened trying to get the version out of the version.txt file. "  + e.getMessage());
    		}
    		
    	}
    	return revisionNumber;
    }
    	
/*    	
  	private static String getRevisionFromSourceControl() throws Exception {
        //https://stackoverflow.com/a/14915348/897007
        ProcessBuilder builder = new ProcessBuilder("C:\\Program Files\\TortoiseSVN\\bin\\svn", "info", "svn://192.168.3.65/CwHelperB/src");
        builder.redirectErrorStream(true);
        Process process = builder.start();
        InputStream is = process.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        
        String[] revision = {"Revision:","0"};
        String line = null;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("Revision:")) {
                revision = line.split(":");
                System.out.println("revision [" + revision[1].trim() + "]");
                break;
            }
        }
        return revision[1].trim();
    }
*/
}
