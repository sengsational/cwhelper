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
		return libraryEntries.toArray(new String[0]);
	}

	public int getLineCount() {
		return this.fileLines.size();
	}

	public static void main(String[] args) {
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
