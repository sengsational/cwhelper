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
     *          
     *  Typically, the zip file would be uploaded.
     *  Manually, Eclipse IDE "Make runnable jar" can also be run and that result uploaded.  Manual addition of version in the file name is required.        
     * 
     */

    public static void main(String[] args) throws Exception {
        
        String version = "5,0,0,";
        String versionFileName = "version.txt";
        
        String wDir = "C:\\my\\dev\\eclipsewrk\\CwHelperC\\";
        String wDirLib = wDir + "CwHelper_lib\\";

        String revision = getRevision();
        writeVersionToLibDirectory(version, revision, wDirLib + versionFileName);
        writeVersionToJarFile(version, revision, wDirLib + versionFileName, wDirLib + "cw_icons.jar");
        
        String[] parms = {
                        "C:\\Program Files (x86)\\Jar2Exe Wizard\\j2ewiz.exe",
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
                        "/icon", "#" + wDir + "Cw_EPG.exe, 0#",
                        "/pv", "5,0,0,0",
                        "/fv",  "5,0,0," + revision,
                        "/ve", "ProductVersion=5.0.0.0",
                        "/ve", "ProductName=CW_EPG",
                        "/ve", "#LegalCopyright=Copyright (c) 2008 - 2021#",
                        "/ve", "#SpecialBuild=5, 0, 0, " +  revision + "#",
                        "/ve", "#FileDescription=Capture Manager#",
                        "/ve", "FileVersion=5.0.0.*",
                        "/ve", "OriginalFilename=CwHelper.exe",
                        "/ve", "#Comments=Background Job for CW_EPG#",
                        "/ve", "#CompanyName=CW_EPG Team# /ve #InternalName=5, 0, 0, 0#",
                        "/ve", "#LegalTrademarks=CW_EPG Team#",
                        "/splash", wDir + "CW_Logo.jpg",
                        "/closeonwindow:false",
                        "/splashtitle", "#Please wait ...#"
                    };
        //for (int i = 0; i < parms.length; i++) {
        //    parms[i] = parms[i].replaceAll("#", "\"");
        //    System.out.println("[" + parms[i] + "]");
        //}
        
        
        if (buildClassesToExe(parms)) {
            ProcessBuilder builder = new ProcessBuilder("C:\\Program Files\\Android\\Android Studio\\jre\\bin\\jar.exe", "-cMfv", "CwHelper_5-0-0-" + revision + ".zip", "CwHelper.exe");
            builder.directory(new File(wDir));
            //ProcessBuilder builder = new ProcessBuilder("C:\\Program Files\\Java\\jdk1.8.0_66\\bin\\jar.exe", "-verbose");
            builder.redirectErrorStream(true);
            Process process = builder.start();
            InputStream is = process.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            
            String line = null;
            while ((line = reader.readLine()) != null) {
                System.out.println("output: [" + line + "]");
                //if (line.contains("successfully")) processResult = true;
            }
            
            //Rename what we presume is the previously manually created "Runnable Jar"
            File runnableJarFile = new File(wDir + "CwHelper.jar");
            if (runnableJarFile.renameTo(new File(wDir + "CwHelper_5-0-0-" + revision + ".jar"))) {
                System.out.println("CwHelper.jar renamed to CwHelper_5-0-0-" + revision + ".jar");
            } else {
                System.out.println("Failed to rename CwHelper.jar");
            }
            
        } else {
            System.out.println("Exe not created, so no attempt to zip being made.");
        }
    }

    private static void writeVersionToJarFile(String version, String revision, String versionFileName, String jarFileName) throws Exception {
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

    private static boolean buildClassesToExe(String[] parms) throws Exception {
        boolean processResult = false;
        ProcessBuilder builder = new ProcessBuilder(parms);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        InputStream is = process.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        
        String line = null;
        while ((line = reader.readLine()) != null) {
            System.out.println("output: [" + line + "]");
            if (line.contains("successfully")) processResult = true;
        }
        return processResult;
    }

    private static String getRevision() throws Exception {
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
