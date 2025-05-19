package com.cwepg.build;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;

/**
 * @author Owner
 * DRS 2024116 - Added Class - Convienience class to read Eclipse project's ".classpath" file.
 * Lines of kind "output" and "lib" are what we are interested in.  Examples:
 * [    <classpathentry kind="output" path="CwHelperC/classes"/>]
 * [    <classpathentry kind="lib" path="CwHelperC/CwHelper_lib/hsqldb.jar"/>]
 */
public class ClasspathReader {

	private String fileNameString;
	private ArrayList<String> fileLines = new ArrayList<>();
	private ArrayList<String> settingsLines = new ArrayList<>();
	private String projectPath;

	public ClasspathReader(String classpathConfigFile, String projectPath) {
		fileNameString = classpathConfigFile;
		this.projectPath = projectPath;
	}

	public boolean load() {
		boolean success = false;
    	StringBuffer progressBuf = new StringBuffer();
		try {
			File projectDirectory = new File(projectPath);
			System.out.println("projectDirectory " + projectDirectory.getAbsolutePath() +  " from " + projectPath);
			File projectParent = projectDirectory.getParentFile();
			BufferedReader in = new BufferedReader(new FileReader(projectParent.getAbsoluteFile() + "\\" + fileNameString));
			String l = "";
			while ((l = in.readLine()) != null) {
	    		progressBuf.append("read line from " + fileNameString + ".").append("\n");
				fileLines.add(l); 
			}
			in.close();
			success = true;
		} catch (Exception e) {
			System.out.println("Progress Buffer: " + progressBuf);
			System.out.println("Something bad happened trying to read the dot classpath file. "  + e.getMessage());
		}
		return success;
	}

	private boolean loadEclipsePrefs(String workspaceDirectoryName) {
		boolean success = false;
		try {
			File settingsDirectory = new File(workspaceDirectoryName + ".metadata\\.plugins\\org.eclipse.core.runtime\\.settings");
			System.out.println("settingsDirectory " + settingsDirectory.getAbsolutePath());
			BufferedReader in = new BufferedReader(new FileReader(settingsDirectory.getAbsoluteFile() + "\\org.eclipse.jdt.core.prefs"));
			String l = "";
			while ((l = in.readLine()) != null) {
				settingsLines.add(l); 
			}
			in.close();
			success = true;
			System.out.println(settingsLines.size() + " lines saved from settings file.");
		} catch (Exception e) {
			System.out.println("Something bad happened trying to read the Eclipse settings file. "  + e.getMessage());
		}
		return success;
	}
	
	
	public String getClassFilesDirectory() {
		String classFilesDirectory = "";
		for (String l : fileLines) {
			if (l.contains("kind=\"output\"")) {
				int pathPos = l.indexOf("path=");
				int subDirectoryPos = l.indexOf("/", pathPos + 6) + 1;
				int endPos = l.indexOf("/>");
				if (subDirectoryPos > 0 && endPos > 0 && endPos > subDirectoryPos) {
					classFilesDirectory = l.substring(subDirectoryPos, endPos - 1);
					break;
				}
			}
		}
		return classFilesDirectory;
	}

	public String[] getLibraryEntries(String prependPath) {
		if (!prependPath.endsWith("/") && !prependPath.endsWith("\\")) System.err.println("ClasspathReader.getLibraryEntries(prependPath) ... prependPath should end with a slash or backslash!!");
		ArrayList<String> libraryEntries = new ArrayList<>();
		for (String l : fileLines) {
			if (l.contains("kind=\"lib\"")) {
				int pathPos = l.indexOf("path=");
				int subDirectoryPos = l.indexOf("/", pathPos + 6) + 1;
				int endPos = l.indexOf("/>");
				if (subDirectoryPos > 0 && endPos > 0 && endPos > subDirectoryPos) {
					libraryEntries.add(prependPath + l.substring(subDirectoryPos, endPos - 1));
				}
			}
		}
		System.out.println(libraryEntries.size() + " lines read from classpath file.");
		return libraryEntries.toArray(new String[0]);
	}

	public String[] getUserLibraryEntries(String prependPath, String workspaceDirectory) {
		ArrayList<String> userLibraryNames = new ArrayList<>();
		for (String l : fileLines) {
			int userLibraryPos = l.indexOf("USER_LIBRARY/");
			if (userLibraryPos > -1) {
				int endQuotePos = l.indexOf("\"",userLibraryPos);
				if (endQuotePos > -1 && endQuotePos > (userLibraryPos + 13)) {
					String userLibraryName = l.substring(userLibraryPos + 13, endQuotePos);
					System.out.println("userLibrary found [" + userLibraryName + "]");
					userLibraryNames.add(userLibraryName);
				}
			}
		}
		ArrayList<String> libraryEntries = new ArrayList<>();
		if (loadEclipsePrefs(workspaceDirectory)) {
			for (String libraryName : userLibraryNames) {
				for (String l : settingsLines) {
					int userLibraryPos = l.indexOf("userLibrary." + libraryName);
					if (userLibraryPos > -1) {
						System.out.println("jar file list found for " + libraryName + ".");
						String[] splitJarLine = l.split("path\\\\=");
						for (int i = 1; i < splitJarLine.length; i++) {
							//System.out.println("splitJarLine [" + splitJarLine[i] + "] " + i);
							int lastQuotePos = splitJarLine[i].lastIndexOf("\"");
							String jarFileName = splitJarLine[i].substring(2, lastQuotePos).replaceAll("/", "\\\\");
							jarFileName = combine(prependPath, jarFileName);
							//System.out.println("jarFileName [" + jarFileName + "]");
							libraryEntries.add(jarFileName);
						}
					}
				}
				
			}
		}
		return libraryEntries.toArray(new String[0]);
	}
	
	public int getLineCount() {
		return this.fileLines.size();
	}

	public static String combine(String str1, String str2) {
        if (str1 == null || str2 == null) return (str1 == null) ? str2 : str1;
        int overlap = findOverlap(str1, str2);
        if (overlap == 0) return str1 + str2;
        else return str1 + str2.substring(overlap);
    }

    private static int findOverlap(String str1, String str2) {
        int len1 = str1.length();
        int len2 = str2.length();
        int maxOverlap = 0;

        for (int i = 1; i <= Math.min(len1, len2); i++) {
            if (str1.substring(len1 - i).equals(str2.substring(0, i))) {
                maxOverlap = i;
            }
        }

        return maxOverlap;
    }

	public static void main(String[] args) {
		boolean testSettings = true;
		if (testSettings) {
			ClasspathReader reader = new ClasspathReader(".classpath","C:\\Users\\Owner\\github\\cwhelper\\CwHelperC\\");
			reader.load();
			reader.getLibraryEntries("C:\\Users\\Owner\\github\\cwhelper\\CwHelperC\\");
			reader.getUserLibraryEntries("C:\\Users\\Owner\\github\\cwhelper\\CwHelperC\\", "C:\\Users\\Owner\\eclipse-workspace\\");
		}
		
		boolean testReader = false;
		if (testReader) {
			ClasspathReader reader = new ClasspathReader("",""); //Testing just the parsing (not reading the file).
			String outputEntry = "    <classpathentry kind=\"output\" path=\"CwHelperC/classes\"/>";
			reader.fileLines.add(outputEntry);
			System.out.println("getClassFilesDirectory() [" + reader.getClassFilesDirectory() + "]");
			
			reader.fileLines.add("    <classpathentry kind=\"lib\" path=\"CwHelperC/CwHelper_lib/commons-codec-1.15.jar\"/>");
			reader.fileLines.add("    <classpathentry kind=\"lib\" path=\"CwHelperC/CwHelper_lib/cw_icons.jar\"/>");
			reader.fileLines.add("    <classpathentry kind=\"lib\" path=\"CwHelperC/CwHelper_lib/jna-platform-5.7.0.jar\"/>");
			
			String[] libraryEntries = reader.getLibraryEntries("PrependPath/");
			for (String string : libraryEntries) {
				System.out.println("library entry [" + string + "]");
			}
			
		}
	}

}
