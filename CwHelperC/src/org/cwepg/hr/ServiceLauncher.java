package org.cwepg.hr;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import org.cwepg.reg.Registry;
import org.cwepg.svc.TinyWebServer;

public class ServiceLauncher {

    public static final SimpleDateFormat SDF = new SimpleDateFormat("MMddHHmmssSSS"); //
    public static final String WEB_SERVER_PORT = "8181";
    public static final int WEB_SERVER_SECURE_PORT = 8443;
    public static final String[] PROTOCOLS = {"TLSv1", "TLSv1.1", "TLSv1.2", "SSLv3"};
    public static final StringBuffer printBuffer = new StringBuffer();
    
    public static void main(String[] args) throws Throwable {
        
        //===========================================================================================SET DIRECTORIES==============================
        String cwepgExecutablePathSource    = "the current runtime directory";
        String hdhrPathSource               = "the current runtime directory";
        String dataPathSource               = "the current runtime directory";
        
        // get local runtime path
        String cwepgExecutablePath = new File("test").getAbsoluteFile().getParentFile().getAbsolutePath();
        String hdhrPath = cwepgExecutablePath;
        String dataPath = cwepgExecutablePath;
        
        
        try {
            // overwrite previous path if registry value available, which is true for all pre-Win 11 22H2 installations
            String cwepgfolder = Registry.getStringValue("HKEY_LOCAL_MACHINE", "SOFTWARE\\CW_EPG", "cwepgfolder");
            if (cwepgfolder != null){
                cwepgExecutablePath         = cwepgfolder;
                hdhrPath                    = cwepgfolder;
                dataPath                    = cwepgfolder;
                cwepgExecutablePathSource   = "the path found in registry HKEY_LOCAL_MACHINE\\SOFTWARE\\CW_EPG\\cwepgfolder";
                hdhrPathSource              = cwepgExecutablePathSource;
                dataPathSource              = cwepgExecutablePathSource;
            }
        } catch (Throwable e1) {
            // stays empty if there is an error accessing the registry (reverse a86a97b)
        }
        
        try {
            // overwrite previous path if registry value available, true for all pre-Win 11 22H2 installations
            String cwepgdatafolder = Registry.getStringValue("HKEY_LOCAL_MACHINE", "SOFTWARE\\CW_EPG", "cwepgdatafolder");
            if (cwepgdatafolder != null){
                dataPath                    = cwepgdatafolder;
                dataPathSource              = "the path found in registry HKEY_LOCAL_MACHINE\\SOFTWARE\\CW_EPG\\cwepgdatafolder";
            }
        } catch (Throwable e1) {
            // stays empty if there is an error accessing the registry (reverse a86a97b)
        }
        

		/*
		 * try { // overwrite local runtime path if the configuration file exists
		 * BufferedReader in = new BufferedReader(new FileReader("CwHdHrDir.txt"));
		 * String pathFromFile = in.readLine();
		 * 
		 * cwepgExecutablePath = pathFromFile; cwepgExecutablePathSource =
		 * "the path found in file: " + new File("CwHdHrDir.txt").getPath(); in.close();
		 * } catch (Throwable e) { // stays empty if the file is not found 
		 * }
		 */
        try {
            // overwrite previous path if registry value available
            String registryFolder = Registry.getStringValue("HKEY_LOCAL_MACHINE", "SOFTWARE\\Silicondust\\HDHomeRun", "InstallDir");
            if (registryFolder != null){
                hdhrPath = registryFolder;
                hdhrPathSource = "the path found in registry HKEY_LOCAL_MACHINE\\SOFTWARE\\Silicondust\\HDHomeRun\\InstallDir";
            }
        } catch (Throwable e1) {
            // stays empty if there is an error accessing the registry
        }

        //DRS 20241108 - Added try/catch - overwrite the data path if "ProgramData" is populated in the Windows environment
        try {
            // overwrite previous data path if "ProgramData" is populated
            String programDataFolderFromWindows = System.getenv("ProgramData");
            if (programDataFolderFromWindows != null){
                dataPath = programDataFolderFromWindows + "\\CW_EPG";
                dataPathSource = "the path found by System.getenv(\"ProgramData\") method";
            }
        } catch (Throwable e1) {
            // stays empty if there is an error accessing the environment variable
        }
        
        try {
            // overwrite local runtime path if the configuration file exists
            BufferedReader in = new BufferedReader(new FileReader(dataPath + "\\CwHdHrDir.txt"));
            String pathFromFile = in.readLine();
            
            cwepgExecutablePath         = pathFromFile;
            cwepgExecutablePathSource   = "the path found in file: " + new File(dataPath + "\\CwHdHrDir.txt").getPath();
            in.close();
        } catch (Throwable e) {
            // stays empty if the file is not found
        }
        
        // TMP 20241114 - Remove executable path if we have a Store package using alias "cw_epg.exe" 
        try {
        	// use MSIX execution alias if OS > Win 9 (i.e., if a Store package, alias needs no path def'n)
        	int OS = Integer.parseInt(System.getProperty("os.version"));  // Not sure if this needs exception process??
        	if (OS > 9) { cwepgExecutablePath = ""; }        		
        	} catch (Throwable e) {
        		// stays unchanged if not OS 10/11
        	}        
        
        
        // DRS20210306 - Added boolean and try/catch
        String logPath = dataPath + "\\logs";
        boolean logFileIsValid = true;
        try {
            // Create logs sub-directory
            if (new File(logPath).mkdirs()){
                System.out.println(new Date() + " Created " + logPath);
            }
            if (!new File(logPath).canWrite()){
                logPath = dataPath;
            } 
            if (!new File(logPath).canWrite()) {
                logFileIsValid = false;
            }
        } catch (Throwable t) {
            System.out.println(new Date() + " ERROR: Unable to create and/or write to the logging directory. " + t.getMessage());
            logFileIsValid = false;
        }
        
        // This output will usually hit the bit bucket since there is usually no console, so grab it in a buffer for later.
        if (logFileIsValid) {
            bufferedPrintln(new Date() + " Logging to files in " + logPath + " as dictated by " + dataPathSource);
        } else {
            bufferedPrintln(new Date() + " Logging to console because the program is unable to write to [" + logPath + "]");
        }
        if (new File(hdhrPath + File.separator + "hdhomerun_config.exe").exists()){
            bufferedPrintln(new Date() + " Using hdhomerun_config.exe in " + hdhrPath + " as dictated by " + hdhrPathSource);
        } else {
            bufferedPrintln(new Date() + " WARNING: hdhomerun_config.exe not found in " + hdhrPath + " as dictated by " + hdhrPathSource);
            bufferedPrintln(new Date() + " WARNING: Captures will only work on tuners with the http interface.");
        }
        
        //========================================================================START WEB SEVER (QUIT IF ALREADY RUNNING)
        TinyWebServer webServer = TinyWebServer.getInstance(WEB_SERVER_PORT); 
        if (!webServer.start()) {
            bufferedPrintln("The web server port was taken, so we presume a copy of CwHelper is already running.  We will quit.");
            System.exit(0);
        }

        PrintStream out = null;
        PrintStream err = null;
        if (logFileIsValid) {
            //===============================================================================PRESERVE OLD LOG FILES
            DFile outFile = new DFile(logPath + File.separator + "stdout.txt");
            DFile errFile = new DFile(logPath + File.separator + "stderr.txt");
            long outNum = Math.round(Math.random()*10000);
            long errNum = Math.round(Math.random()*10000);
            if (outFile.exists()){
                outNum = makeLongFromMs(outFile.lastModified(), outNum);
                outFile.copyTo(new File(logPath + File.separator + "stdout" + outNum + ".txt"));
            }
            if (errFile.exists()){
                errNum = makeLongFromMs(errFile.lastModified(), errNum);
                errFile.copyTo(new File(logPath + File.separator + "stderr" + errNum + ".txt"));
            }
    
            if(args != null && args.length > 0 && "-copyLogs".equals(args[0])) {
                System.out.println(new Date() + " Quitting after log copy.");
                System.exit(0);
            }
    
            //==============================================================================REDIRECT STDOUT AND STDERR TO FILES
            out = new PrintStream(new FileOutputStream(logPath + File.separator + "stdout.txt"), true);
            err = new PrintStream(new FileOutputStream(logPath + File.separator + "stderr.txt"), true);
    
            System.setOut(out);
            System.setErr(err);

            // This output will go to the log file
            System.out.println(printBuffer);
        }

        //========================================================================================COPY XML and MDB FILES, IF THEY DON'T EXIST
        // DRS 20210304 - Added  'try' block to copy MDB files, if needed
        try {
            if (!cwepgExecutablePath.equals(dataPath)) {
                boolean eatException = false;
                boolean debug = true;
                DFile source = new DFile(cwepgExecutablePath + File.separator + "cw_record.mdb");
                DFile sink = new DFile(dataPath + File.separator + "cw_record.mdb");
                if (!sink.exists()) {
                    if (debug) System.out.println(new Date() + " Copy from [" + source.getAbsolutePath() + "] to [" + sink.getAbsolutePath() + "]");
                    source.copyTo(sink, eatException);
                } else {
                    if (debug) System.out.println(new Date() + " File [" + sink.getAbsolutePath() + "] already exists.  No copy needed.");
                }
            } else {
                System.out.println(new Date() + " No file copy possible because the data path was not defined ([" + cwepgExecutablePath + "] equals [" + dataPath + "])");
           }
        } catch (Throwable t) {
            System.out.println(new Date() + " ERROR: Unable to copy files from the cwepgExecutablePath to the dataPath.  " + t.getMessage());
        }
        
		// =====================================================================================================RUN CAPTURE MANAGER
		try {
		    // Prepare the shutdown hook thread.  The run thread needs to be added later, once established.
		    Runtime.getRuntime().addShutdownHook(new ShutdownHookThread(dataPath) ); // DRS 20210225
		    
			// load real main class, replace with your class name
			Class maincls = Class.forName("org.cwepg.hr.CaptureManager");

			// find the "main" method
			Method mainmethod = maincls.getMethod("main", new Class[] { args.getClass() });

			// run with parameter "-start"
			mainmethod.invoke(null, new Object[] { new String[] {"-cwepgExecutablePath", cwepgExecutablePath, "-hdhrPath", hdhrPath, "-dataPath", dataPath } });

		} catch (Exception e) {
            System.err.println(new Date() + " Failure in ServiceLauncher");
			e.printStackTrace();
		}

		// close
		if (out != null) out.close();
		if (err != null) err.close();
	}

    private static long makeLongFromMs(long lastModified, long outNum) {
        try {
            String shortRandom = "000" + Math.round(Math.random()*1000);
            shortRandom = shortRandom.substring(shortRandom.length() - 3);
            Calendar aCalendar = Calendar.getInstance();
            aCalendar.setTimeInMillis(new Date().getTime());
            outNum = (Long.valueOf(SDF.format(aCalendar.getTime())) * 1000L) + Long.parseLong(shortRandom);
        } catch (Throwable t) {} 
        return outNum;
    }
    
    public static void bufferedPrintln(String s) {
        System.out.println(s);
        printBuffer.append(s).append("\n");
    }
}
