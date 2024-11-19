package com.cwepg.build;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

public class CheckEntry extends JarEntry {

	private static final String HEADER = "\"File or Directory\",\"Hosting Jar File\",\" Duplicate\"";
	private boolean isDuplicate;
	private String jarFileName;

	public CheckEntry(JarEntry entry, String jarFileName) {
		super(entry);
		this.jarFileName = jarFileName;
	}

	public void setDuplicate(boolean isDuplicate) {
		this.isDuplicate = isDuplicate;
	}

	public boolean isDuplicate() {
		return this.isDuplicate;
	}
	
	public static String getHeader() {
		return HEADER;
	}
	
	public String getCsv() {
		return "\"" + this + "\",\"" + this.jarFileName +"\",\"" + this.isDuplicate + "\"";
	}

	public static void main(String[] args) throws FileNotFoundException, IOException {
		// read a jar and create CheckedEntry  item and run getCsv and see what the attributes are.
		String inputJar = "C:\\Users\\Owner\\github\\cwhelper\\CwHelperC\\CwHelper_lib\\hsqldb.jar";
		String inputJarFileName = new File(inputJar).getName();
        try (JarInputStream jis = new JarInputStream(new FileInputStream(inputJar))) {
            JarEntry entry;
            System.out.println("CheckEntry.main() " + getHeader());
            while ((entry = jis.getNextJarEntry()) != null) {
            	CheckEntry checkEntry = new CheckEntry(entry, inputJarFileName);
                System.out.println("CheckEntry.main() " + checkEntry.getCsv());
            }
        }

	}

}
