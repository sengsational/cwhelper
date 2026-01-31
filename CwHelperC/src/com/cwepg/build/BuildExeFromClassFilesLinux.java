/*
 * Created on Apr 30, 2021
 *
 */
package com.cwepg.build;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipException;

import org.cwepg.hr.DFile;

// import org.cwepg.hr.CaptureManager;

public class BuildExeFromClassFilesLinux {
	public static final String USER = "/home/owner/";
    public static final String PROJECT_ROOT = USER + "eclipse-workspace/cwhelper/";
    public static final String PROJECT_DIRECTORY = USER + "eclipse-workspace/cwhelper/CwHelperC/";
    public static final String PROJECT_DIRECTORY_BINARIES = PROJECT_DIRECTORY + "target/";
    public static final String CLASSPATH_CONFIG_FILE = ".classpath";
	public static final String WORKSPACE_DIRECTORY = USER + "eclipse-workspace/";
    public static final String KEYSTORE = WORKSPACE_DIRECTORY + "KnurderKeyStore.jks";
    public static final String STOREPASS = "Hnds#1111";
    public static final String KEYPASS = "Hnds#1111";
    public static final String LIBRARY_DIRECTORY = PROJECT_DIRECTORY + "CwHelper_lib/";
    public static final String VERSION_FILE_NAME = "version.txt";
    public static final String JRE_PATH = "/home/owner/Eclipse/plugins/org.eclipse.justj.openjdk.hotspot.jre.full.linux.x86_64_21.0.9.v20251105-0741/jre/";
    public static final String BASE_VERSION = "5-4-0-";
    public static final String COMMA_VERSION = "5,4,0,";
    public static final String DOT_VERSION = "5.4.0.";
	private static ArrayList<String> jarFileList;

    public static void main(String[] args) throws Exception {
    	
        boolean doJarSigning = true; // DRS 20230513 - Signing prevents Terry from iterating on his own.
        
        boolean forceRevisionNumber = true; //>>>>>>>>>>>>>>>>>>>>>>>>>>>>
        String revision = "999";
        if (forceRevisionNumber) {
            revision = "1083";
        } else {
        	String fullVersion = ""; 
        	try {
        		fullVersion = getVersionFromJarFile(PROJECT_DIRECTORY + "CwHelper.jar");
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
        
        jarFileList = getJarFileListFromConfiguration();

    	boolean useOldJarFileRenaming = false;
    	if (useOldJarFileRenaming) {
    		jarFileRenaming(revision, doJarSigning);
    	} else {
    		System.out.println("Creating runnable jar from project " + PROJECT_DIRECTORY);
    		makeJarFromProject(revision, doJarSigning, jarFileList);
            new DFile(PROJECT_DIRECTORY + "CwHelper_" + BASE_VERSION + revision + ".jar").copyTo(new File("CwHelper_Staged.jar")); // DRS 20250512 - Make a jar file to stage for local testing
    	}
    }


    private static String[] combineStringArrays(String[]... arrays) {
        int totalLength = 0;
        for (String[] arr : arrays) {
            totalLength += arr.length;
        }

        String[] result = new String[totalLength];
        int currentIndex = 0;
        for (String[] arr : arrays) {
            System.arraycopy(arr, 0, result, currentIndex, arr.length);
            currentIndex += arr.length;
        }
        return result;
    }

    
	private static String[] getEmbedParametersFromConfiguration(ArrayList<String> jarFileList) throws Exception {
        String[] parameters = new String[jarFileList.size() * 2];
        int i = 0;
        for (String jarFileName : jarFileList) {
			parameters[i++] = "/embed";
			parameters[i++] = jarFileName;
		}
        return parameters;
	}


	private static ArrayList<String> getJarFileListFromConfiguration() throws Exception {
        // Read the build information from the project and make some definitions
    	ClasspathReader reader = new ClasspathReader(CLASSPATH_CONFIG_FILE, PROJECT_DIRECTORY);
    	
    	if (!reader.load()) throw new Exception("Unable to load " + CLASSPATH_CONFIG_FILE);
    	
    	/* 1 : Regular classpath jars */
    	String[] jarFileNames = reader.getLibraryEntries(PROJECT_DIRECTORY);
    	System.out.println("There were " + jarFileNames.length + " lib entries in " + PROJECT_DIRECTORY + CLASSPATH_CONFIG_FILE);

    	/* 2 : User library jars */
    	String[] userLibraryJarFileNames = reader.getUserLibraryEntries(PROJECT_DIRECTORY, WORKSPACE_DIRECTORY);
    	System.out.println("There were " + userLibraryJarFileNames.length + " lib entries in " + PROJECT_DIRECTORY + CLASSPATH_CONFIG_FILE + " USER LIBRARY entries.");

    	/* 3 : Maven jars */
        // NOTE: If Maven dependencies changed, you need to open a terminal window, 
    	//       change to the project directory (where the POM.XML is) and 
    	//       type "mvn dependency:copy-dependencies"
        //       This should create (project directory)\target\dependency folder with all the jar files.
    	//String[] mavenJarFileNames = reader.getMavenLibraryEntries(PROJECT_DIRECTORY, WORKSPACE_DIRECTORY);
    	//System.out.println("There were " + mavenJarFileNames.length + " lib entries in " + PROJECT_DIRECTORY + "target/dependency MAVEN LIBRARY entries.");
    	
    	ArrayList<String> jarFileList = new ArrayList<>();
    	for (String fileName : jarFileNames) {
			System.out.println("jarFile [" + fileName + "]");
			jarFileList.add(fileName);
		}
    	for (String fileName : userLibraryJarFileNames) {
			System.out.println("userLibraryJarFile [" + fileName + "]");
			jarFileList.add(fileName);
		}
    	//for (String fileName : mavenJarFileNames) {
		//	System.out.println("mavenJarFile [" + fileName + "]");
		//	jarFileList.add(fileName);
		//}
    	return jarFileList;
	}


	private static void makeJarFromProject(String revision, boolean doJarSigning, ArrayList<String> jarFileList) throws Exception {
        String outputJar = PROJECT_DIRECTORY + "CwHelper_" + BASE_VERSION + revision + ".jar";
        File runnableJarFileNewName = new File(outputJar);
        
        // If destination already exists, rename it first
        if (runnableJarFileNewName.exists()) {
            int randomInt = (int)(Math.random() * 10000);
            runnableJarFileNewName.renameTo(new File(PROJECT_DIRECTORY + "CwHelper_" + BASE_VERSION + revision + "_" + randomInt + ".jar"));
        }

        // Define the class to start in the manifest
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attributes.put(Attributes.Name.CLASS_PATH, ".");
        attributes.put(Attributes.Name.MAIN_CLASS, "org.cwepg.hr.ServiceLauncher");
        
        // Read the build information from the project and make some definitions
    	ClasspathReader reader = new ClasspathReader(CLASSPATH_CONFIG_FILE, PROJECT_DIRECTORY);
    	if (!reader.load()) throw new Exception("Unable to load " + CLASSPATH_CONFIG_FILE);
    	//else System.out.println("read " + reader.getLineCount() + " lines from " + CLASSPATH_CONFIG_FILE);
    	
    	String classFilesDirectory = reader.getClassFilesDirectory();

    	//String[] jarFileNames = reader.getLibraryEntries(PROJECT_DIRECTORY);
    	//System.out.println("There were " + jarFileNames.length + " lib entries in " + PROJECT_DIRECTORY + CLASSPATH_CONFIG_FILE);
    	//String[] userLibraryJarFileNames = reader.getUserLibraryEntries(PROJECT_DIRECTORY, WORKSPACE_DIRECTORY);
    	//System.out.println("There were " + userLibraryJarFileNames.length + " lib entries in " + WORKSPACE_DIRECTORY + ".metadata ... org.eclipse.jdt.core.prefs");
    	
        // Take all the jars in "jarFileNames" and "userLibraryJarFilename" and merge them into "outputJar"
    	ArrayList<CheckEntry> checkEntryList = new ArrayList<>(); // To post analyze the duplication between jar files
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(outputJar), manifest)) {
    		for (String jarFileName : jarFileList) {
    			mergeJar(jos, jarFileName, checkEntryList);
    		}

        	/*
            for (int i = 0; i < jarFileNames.length; i++) {
                String inputJar = jarFileNames[i];
                //System.out.println("Processing [" + inputJar + "]");
                mergeJar(jos, inputJar, checkEntryList);
            }
            for (int i = 0; i < userLibraryJarFileNames.length; i++) {
                String inputJar = userLibraryJarFileNames[i];
                //System.out.println("Processing [" + inputJar + "]");
                mergeJar(jos, inputJar, checkEntryList);
            }
            */
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        // quickie analysis of the duplication between jar files merged
        boolean saveInterJarDuplicationReport = false;
        if (saveInterJarDuplicationReport) {
        	// For each item already flagged as duplicate, set the original one as a duplicate as well
        	for (CheckEntry checkEntry : checkEntryList) {
        		if (checkEntry.isDuplicate()) {
                	for (CheckEntry dupCheckEntry : checkEntryList) {
                		if (dupCheckEntry.getName().equals(checkEntry.getName())) {
                			dupCheckEntry.setDuplicate(true);
                		}
                	}
        			
        		}
    		}
        	String jarFileDuplicationReportFileName = "JarFileDuplicationReport.csv";
        	BufferedWriter out = new BufferedWriter(new FileWriter(jarFileDuplicationReportFileName));
        	out.write(CheckEntry.getHeader() + "\n");
        	for (CheckEntry checkEntry : checkEntryList) {
        		out.write(checkEntry.getCsv() + "\n");
        	}        	
        	out.close();
        	System.out.println("saved " + new File(jarFileDuplicationReportFileName).getAbsolutePath());
        }
        
        // Take all the files in "classesFileList" and merge them into "outputJar"
        System.out.println("Looking in [" + PROJECT_ROOT + classFilesDirectory + "] for class files.");
		List<String> classesFileList = getAbsoluteFileNames(PROJECT_ROOT + classFilesDirectory);
		System.out.println("There were " + classesFileList.size() + " class files.");
		for (String aFileName : classesFileList) {
			System.out.println("aFileName [" + aFileName + "]");
		}
        boolean usePath = true;
        JarUpdater.updateZipFileWithFilesOnDisk(new File(outputJar), classesFileList, usePath, PROJECT_ROOT + classFilesDirectory);

        if (doJarSigning) {
            String[] signTheJarFile = {JRE_PATH + "bin/" + "jarsigner","-tsa","http://timestamp.digicert.com","-tsacert","alias","-keystore", KEYSTORE,"-storepass",STOREPASS,"-keypass",KEYPASS,outputJar, "knurderkeyalias"};
            if (runOsProcess(signTheJarFile, "jar signed.", null)) {
                String[] verifyTheJarFile = {JRE_PATH +"bin/" + "jarsigner", "-verify", outputJar};
                if (runOsProcess(verifyTheJarFile, "jar verified.", null)) {
                    System.out.println("SUCCESSFULLY SIGNED " + outputJar);
                } else {
                    System.out.println("ERROR: Unable to sign the jar file.");
                }
            }
        } else {
            System.out.println("NOTICE: doJarSigning was false.  Jar file remains unsigned.");
        }

		
	}

    public static List<String> getAbsoluteFileNames(String directoryPath) throws IOException {
        List<String> fileNames = new ArrayList<>();
        getAbsoluteFileNamesRecursive(directoryPath, fileNames);
        return fileNames;
    }

    private static void getAbsoluteFileNamesRecursive(String directoryPath, List<String> fileNames) throws IOException {
        File directory = new File(directoryPath);

        if (!directory.exists() || !directory.isDirectory()) {
            return;
        }

        for (File file : directory.listFiles()) {
            if (file.isFile()) {
            	String fileToSave = file.getAbsolutePath();
                fileNames.add(fileToSave);
            } else if (file.isDirectory()) {
                getAbsoluteFileNamesRecursive(file.getAbsolutePath(), fileNames);
            }
        }
    }

    static void mergeJar(JarOutputStream jos, String inputJar, ArrayList<CheckEntry> checkEntryList) throws IOException {
    	int duplicateEntryCount = 0;
    	String inputJarFileName = new File(inputJar).getName();
        try (JarInputStream jis = new JarInputStream(new FileInputStream(inputJar))) {
            JarEntry entry;
            while ((entry = jis.getNextJarEntry()) != null) {
            	CheckEntry checkEntry = new CheckEntry(entry, inputJarFileName);
                try {
                	if (!checkEntry.isDirectory()) checkEntryList.add(checkEntry);
                    jos.putNextEntry(entry);
                    copyStream(jis, jos);
                    jos.closeEntry();
                } catch (ZipException e) {
                	String message = e.getMessage();
                	if (message.contains("duplicate entry")) {
                		checkEntry.setDuplicate(true);
                		duplicateEntryCount++;
                	} else {
                		System.out.println(e.getMessage() + " 1 continuing to process.");
                	}
                }
            }
            if (duplicateEntryCount > 0) System.out.println("Jar " + inputJar + " had " + duplicateEntryCount + " duplicate entries.");
        }
    }

    static void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int bytesRead;
        while ((bytesRead = in.read(buffer)) != -1) {
        	try {
                out.write(buffer, 0, bytesRead);
        	} catch (ZipException e) {
        		System.out.println(e.getMessage() + " 2 continuing to process.");
        	}
        }
    }
	private static String getVersionFromJarFile(String jarFileName) throws IOException {
        return JarUpdater.getFileContent(jarFileName, "version.txt");
    }

    private static void writeVersionToJarFile(String versionFileName, String jarFileName) throws Exception {
        File[] contents = {new File(versionFileName)};
        File jarFile = new File(jarFileName);

        try {
        	boolean usePath = false;
        	String baseDirectory = "";
            JarUpdater.updateZipFileWithFilesOnDisk(jarFile, contents, usePath, baseDirectory);
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

    static boolean runOsProcess(String[] parms, String requiredResult, String wDir) throws Exception {
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
    		BuildExeFromClassFilesLinux.writeVersionToLibDirectory(COMMA_VERSION, revision, filePath);
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

    private static void jarFileRenaming(String revision, boolean doJarSigning) throws Exception {
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
            String contentsOfVersionFile = getVersionFromJarFile(PROJECT_DIRECTORY + "CwHelper_" + BASE_VERSION + revision + ".jar");
            System.out.println("Version inside the jar file: " + contentsOfVersionFile);
            if (contentsOfVersionFile.trim().equals(COMMA_VERSION + revision)) {
                System.out.println("version.txt is aligned");
                if (doJarSigning) {
                    String[] signTheJarFile = {JRE_PATH + "bin/" + "jarsigner.exe","-tsa","http://timestamp.digicert.com","-tsacert","alias","-keystore", KEYSTORE,"-storepass",STOREPASS,"-keypass",KEYPASS,"CwHelper_" + BASE_VERSION + revision + ".jar", "knurderkeyalias"};
                    if (runOsProcess(signTheJarFile, "jar signed.", null)) {
                        String[] verifyTheJarFile = {JRE_PATH +"bin/" + "jarsigner.exe", "-verify","CwHelper_" + BASE_VERSION + revision + ".jar"};
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
		
	}
}
