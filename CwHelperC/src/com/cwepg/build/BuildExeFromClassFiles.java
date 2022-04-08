/*
 * Created on Apr 30, 2021
 *
 */
package com.cwepg.build;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Writer;
import java.util.Date;

import org.cwepg.hr.CaptureManager;

public class BuildExeFromClassFiles {
    /**
     *
     * This class
     *          1) pulls the version from the source control system
     *          2) writes the version into the CwHelper_lib folder as "version.txt"
     *          3) updates the "version.txt" file inside of "cw_icons.jar" resource file
     *          4) packages the class files as an EXE file using Jar2Exe, creating CwHelper.exe
     *          5) zips the EXE file into CwHelper(version).zip (so it can be managed on a Google drive, that doesn't allow EXE files)
     *          6) renames the (already created) runnable jar file to include the version in the file name
     *          7) signs the jar file with
     *
     *  Typically, the zip file would be uploaded.
     *  Manually, Eclipse IDE "Make runnable jar" can also be run and that result uploaded.  Manual addition of version in the file name is required.
     *
     */
    public static final String PROJECT_DIRECTORY = "C:\\my\\dev\\gitrepo\\CwHelperC\\";
    public static final String J2E_WIZ = "C:\\Program Files (x86)\\Jar2Exe Wizard\\j2ewiz.exe";
    public static final String KEYSTORE = "C:\\Users\\Owner\\AndroidStudioProjects\\KnurderKeyStore.jks";
    public static String storePass = null;
    public static String keyPass = null;
    public static final String JRE_PATH = "C:\\Program Files\\Android\\Android Studio\\jre\\bin\\";
    public static final String BASE_VERSION = "5-0-0-";
    public static final String COMMA_VERSION = "5,0,0,";
    public static final String DOT_VERSION = "5.0.0.";

    public static void main(String[] args) throws Exception {
        
        storePass = getTextFromKeyboard("Enter [" + KEYSTORE + "] password (blank for no jar signing): ");
        keyPass = storePass; // Obviously depends on if you used the same password for both.  I did.
        
        String versionFileName = "version.txt";

        String wDir = PROJECT_DIRECTORY;
        String wDirLib = wDir + "CwHelper_lib\\";

        boolean forceRevisionNumber = false; //>>>>>>>>>>>>>>>>>>>>>>>>>>>>
        String revision = "999";
        if (forceRevisionNumber) {
            revision = "804";
        } else {
            revision = getRevisionFromSourceControl();
        }

        writeVersionToLibDirectory(COMMA_VERSION, revision, wDirLib + versionFileName);
        writeVersionToJarFile(wDirLib + versionFileName, wDirLib + "cw_icons.jar");

        String[] j2ewizBuildCwHelperParms = {
                        J2E_WIZ,
                        "/jar ", wDir + "classes",
                        "/o", wDir + "CwHelper.exe",
                        "/m", "org.cwepg.hr.ServiceLauncher",
                        "/type","windows",
                        "/minjre","1.6",
                        "/platform","windows",
                        "/checksum",
                        "/embed", wDirLib + "commons-lang-2.6.jar",        //
                        "/embed", wDirLib + "commons-logging-1.1.3.jar",   //
                        "/embed", wDirLib + "cw_icons.jar",                //
                        "/embed", wDirLib + "hsqldb.jar",                  //
                        "/embed", wDirLib + "jackcess-2.1.11.jar",         //
                        "/embed", wDirLib + "jna-5.7.0.jar",               //
                        "/embed", wDirLib + "jna-platform-5.7.0.jar",      //
                        "/embed", wDirLib + "mailapi.jar",                 //
                        "/embed", wDirLib + "smtp.jar",                    //
                        "/embed", wDirLib + "ucanaccess-4.0.4.jar",        //
                        "/embed", wDirLib + "httpclient-4.0.1.jar",        //
                        "/embed", wDirLib + "httpcore-4.0.1.jar",          //
                        "/icon", "#" + wDir + "cw_logo16.ico, 0#",
                        "/pv", COMMA_VERSION + "0",
                        "/fv",  COMMA_VERSION + revision,
                        "/ve", "ProductVersion=" + DOT_VERSION + "0",
                        "/ve", "ProductName=CW_EPG",
                        "/ve", "#LegalCopyright=Copyright (c) 2008 - 2022#",
                        "/ve", "#SpecialBuild=" + COMMA_VERSION + " " +  revision + "#",
                        "/ve", "#FileDescription=Capture Manager#",
                        "/ve", "FileVersion=" + DOT_VERSION + "*",
                        "/ve", "OriginalFilename=CwHelper.exe",
                        "/ve", "#Comments=Background Job for CW_EPG#",
                        "/ve", "#CompanyName=CW_EPG Team# /ve #InternalName=" + COMMA_VERSION + "0#",
                        "/ve", "#LegalTrademarks=CW_EPG Team#",
                        "/splash", wDir + "CW_Logo.jpg",
                        "/closeonwindow:false",
                        "/splashtitle", "#Please wait ...#"
                    };
        for (int i = 0; i < j2ewizBuildCwHelperParms.length; i++) {
            j2ewizBuildCwHelperParms[i] = j2ewizBuildCwHelperParms[i].replaceAll("#", "\"");
            System.out.println("[" + j2ewizBuildCwHelperParms[i] + "]");
        }


        if (runOsProcess(j2ewizBuildCwHelperParms, "successfully", null)) {
            String zipDestinationFileNameString = "CwHelper_" + BASE_VERSION + revision + ".zip";
            File zipDestinationFile = new File(wDir + zipDestinationFileNameString);

            // If destination already exists, rename it first
            if (zipDestinationFile.exists()) {
                int randomInt = (int)(Math.random() * 10000);
                zipDestinationFile.renameTo(new File(wDir + "CwHelper_" + BASE_VERSION + revision + "_" + randomInt + ".zip"));
            }

            String[] zipTheExeParams = {JRE_PATH +"jar.exe", "-cMfv", zipDestinationFileNameString, "CwHelper.exe"};
            if (runOsProcess(zipTheExeParams, "deflated", wDir)) {
                //Rename what we presume is the previously manually created "Runnable Jar"
                File runnableJarFile = new File(wDir + "CwHelper.jar");
                File runnableJarFileNewName = new File(wDir + "CwHelper_" + BASE_VERSION + revision + ".jar");

                // If destination already exists, rename it first
                if (runnableJarFileNewName.exists()) {
                    int randomInt = (int)(Math.random() * 10000);
                    runnableJarFileNewName.renameTo(new File(wDir + "CwHelper_" + BASE_VERSION + revision + "_" + randomInt + ".jar"));
                }

                if (runnableJarFile.renameTo(runnableJarFileNewName)) {
                    System.out.println("CwHelper.jar renamed to CwHelper_" + BASE_VERSION + revision + ".jar");
                    String contentsOfVersionFile = getVersionFromJarFile("CwHelper_" + BASE_VERSION + revision + ".jar");
                    System.out.println("Version inside the jar file: " + contentsOfVersionFile);
                    if (contentsOfVersionFile.trim().equals(COMMA_VERSION + revision)) {
                        System.out.println("version.txt is aligned");
                        if (storePass != null && keyPass != null) {
                            String[] signTheJarFile = {JRE_PATH +"jarsigner.exe","-tsa","http://timestamp.digicert.com","-tsacert","alias","-keystore", KEYSTORE,"-storepass",storePass,"-keypass",keyPass,"CwHelper_" + BASE_VERSION + revision + ".jar", "knurderkeyalias"};
                            if (runOsProcess(signTheJarFile, "jar signed.", null)) {
                                String[] verifyTheJarFile = {JRE_PATH +"jarsigner.exe", "-verify","CwHelper_" + BASE_VERSION + revision + ".jar"};
                                if (runOsProcess(verifyTheJarFile, "jar verified.", null)) {
                                    System.out.println("SUCCESSFULLY SIGNED CwHelper_" + BASE_VERSION + revision + ".jar");
                                } else {
                                    System.out.println("ERROR: Unable to sign the jar file.");
                                }
                            }
                        } else {
                            System.out.println("Not signing the jar due to lack of passwords.");
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

    private static String getTextFromKeyboard(String prompt) {
        System.out.print(prompt);
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        try {
            return(reader.readLine());
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            System.out.println("");
            if (reader != null) {
                try { reader.close(); } catch (IOException e) { }
            }
        }
        return null;
    }

    private static String getVersionFromJarFile(String jarFileName) throws IOException {
        return JarUpdater.getFileContent(jarFileName, "version.txt");
    }

    private static void writeVersionToJarFile(String versionFileName, String jarFileName) throws Exception {
        File[] contents = {new File(versionFileName)};
        File jarFile = new File(jarFileName);

        try {
            JarUpdater.updateZipFile(jarFile, contents);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void writeVersionToLibDirectory(String version, String revision, String versionFileName) {
        try {
            System.out.println(new Date() + " Saving to " + versionFileName);
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

}
