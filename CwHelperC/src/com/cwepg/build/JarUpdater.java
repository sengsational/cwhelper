/*
 * Created on Jun 4, 2021
 *
 */
package com.cwepg.build;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;


public class JarUpdater {
    public static void main(String[] args) {

        File[] contents = {new File("C:\\F\\ResourceTest.txt"),
                           new File("C:\\F\\ResourceTest2.bmp")};

        File jarFile = new File("C:\\F\\RepackMe.jar");

        try {
            updateZipFileWithFilesOnDisk(jarFile, contents, false, null);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public static String getFileContent(String jarFileName, String fileName) throws IOException {
        StringBuffer stringBuffer = new StringBuffer();
        ZipFile zipFile = new ZipFile(jarFileName);
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while(entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (entry.getName().equals(fileName)) {
                byte[] buf = new byte[1024];
                System.out.println("Found " + fileName);
                InputStream stream = zipFile.getInputStream(entry);
                int len;
                while ((len = stream.read(buf)) > 0) {
                    stringBuffer.append(new String(buf));
                }
            }
        }
        zipFile.close();
        return stringBuffer.toString();
    }

    /** Each file in 'fileList' exists on the hard drive, and will be added to the zip file.
     *  Files that are in the zip file already and are not in the file list will stay in the zip file as-is.
     *  
     * @param zipFile
     * @param fileList
     * @param usePath 		//DRS 20241116 - Added parameter
     * @param baseDirectory //DRS 20241116 - Added parameter
     * @throws IOException
     */
    public static void updateZipFileWithFilesOnDisk(File zipFile, File[] fileList, boolean usePath, String baseDirectory) throws IOException {
        // get a temp file
        File tempFile = File.createTempFile(zipFile.getName(), null);
        // delete it, otherwise you cannot rename your existing zip to it.
        tempFile.delete();

        //boolean renameOk=zipFile.renameTo(tempFile);
        //if (!renameOk) {
        //    throw new RuntimeException("could not rename the file "+zipFile.getAbsolutePath()+" to "+tempFile.getAbsolutePath());
        //}
        
        FileMover.moveFile(zipFile.getAbsolutePath(), tempFile.getAbsolutePath());
        
        byte[] buf = new byte[1024];

        System.out.println("tempFile [" + tempFile + "]");
        System.out.println("zipFile [" + zipFile + "]");
        ZipInputStream zin = new ZipInputStream(new FileInputStream(tempFile));
        ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zipFile));
        
        // Step through each entry in the zip file
        // 
        ZipEntry entry = zin.getNextEntry();
        while (entry != null) {
            String name = entry.getName();
            boolean filenameNotInFileList = true;
            for (File f : fileList) {
                if (f.getName().equals(name)) {
                    filenameNotInFileList = false;
                    break;
                }
            }
            if (filenameNotInFileList) {
                // Add ZIP entry to output stream.
                out.putNextEntry(new ZipEntry(name));
                // Transfer bytes from the ZIP file to the output file
                int len;
                while ((len = zin.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            }
            entry = zin.getNextEntry();
        }
        // Close the streams        
        zin.close();
        // Compress the files
        for (int i = 0; i < fileList.length; i++) {
            System.out.println("trying to find and use [" + fileList[i] + "]");
            InputStream in = new FileInputStream(fileList[i]);
            // Add ZIP entry to output stream.
            // DRS 202041116 - Added 'if/else', existing content in else - enhance to allow other than files into root directory
            if (usePath) {
            	String absoluteFileNameString = fileList[i].getAbsolutePath().replace("\\","/");
            	if (baseDirectory != null && baseDirectory.length() > 0 && absoluteFileNameString.length() > (baseDirectory.length() + 1) ) {
            		absoluteFileNameString = absoluteFileNameString.substring(baseDirectory.length() + 1);
            	}
            	try  {
                    out.putNextEntry(new ZipEntry(absoluteFileNameString));
            	} catch (ZipException z) {
            		System.out.println("WARNING: " + absoluteFileNameString + " was NOT placed in the jar file. " + z.getLocalizedMessage());
            		continue;
            	}
            } else {
                out.putNextEntry(new ZipEntry(fileList[i].getName()));
            }
            // Transfer bytes from the file to the ZIP file
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            // Complete the entry
            out.closeEntry();
            in.close();
        }
        // Complete the ZIP file
        out.close();
        tempFile.delete();
    }

    // DRS 20241116 - Added method - same functionality, convenience method signature
    public static void updateZipFileWithFilesOnDisk(File zipFile, List<String> listsOfFileNames, boolean usePath, String baseDirectory) throws IOException {
    	List<File> listOfFiles = new ArrayList<>();
    	for (String fileNameString : listsOfFileNames) {
			listOfFiles.add(new File(fileNameString));
		}
    	
    	File[] fileArray = listOfFiles.toArray(new File[0]);
    	
    	updateZipFileWithFilesOnDisk(zipFile, fileArray, usePath, baseDirectory);
    }
    

    public static void clearPreviousCwrFromJar(File zipFile, boolean writeResult) throws IOException {
        HashSet preserveFileNameSet = new HashSet();
        preserveFileNameSet.add("CW_EPG_Icon.gif");
        preserveFileNameSet.add("cw_logo16.gif");
        preserveFileNameSet.add("cw_meatball16.GIF");
        preserveFileNameSet.add("cw_rs16.GIF");
        preserveFileNameSet.add("version.txt");
        preserveFileNameSet.add("META-INF/");
        preserveFileNameSet.add("META-INF/MANIFEST.MF");
        
               // get a temp file
        File tempFile = File.createTempFile(zipFile.getName(), null);
               // delete it, otherwise you cannot rename your existing zip to it.
        tempFile.delete();

        boolean renameOk=zipFile.renameTo(tempFile);
        if (!renameOk) {
            throw new RuntimeException("could not rename the file "+zipFile.getAbsolutePath()+" to "+tempFile.getAbsolutePath());
        }
        byte[] buf = new byte[1024];

        //System.out.println("tempFile [" + tempFile + "]");
        System.out.println("zipFile [" + zipFile + "]");
        ZipInputStream zin = new ZipInputStream(new FileInputStream(tempFile));
        ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zipFile));
        
        // Step through each entry in the zip file
        // 
        ZipEntry entry = zin.getNextEntry();
        while (entry != null) {
            String name = entry.getName();
            if (preserveFileNameSet.contains(name)) {
                // Add ZIP entry to output stream.
                out.putNextEntry(new ZipEntry(name));
                // Transfer bytes from the ZIP file to the output file
                int len;
                while ((len = zin.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            } else {
                System.out.println("Not writing out " + name);
            }
            entry = zin.getNextEntry();
        }
        // Close the streams        
        zin.close();
        // Complete the ZIP file
        out.close();
        
        if (!writeResult) {
            System.out.println("Restoring original zip file because writeResult was false.");
            zipFile.delete();
            renameOk=tempFile.renameTo(zipFile);
            if (!renameOk) {
                throw new RuntimeException("could not rename the file "+tempFile.getAbsolutePath()+" to "+zipFile.getAbsolutePath());
            }
        } else {
            System.out.println("Zip file updated.");
            tempFile.delete();
        }
    }

    public static void addZipFileContents(File zipInputFile, File zipFileOriginal, boolean writeResult) throws IOException {
               // get a temp file
        File tempFile = File.createTempFile(zipFileOriginal.getName(), null);
               // delete it, otherwise you cannot rename your existing zip to it.
        tempFile.delete();

        boolean renameOk=zipFileOriginal.renameTo(tempFile);
        if (!renameOk) {
            throw new RuntimeException("could not rename the file "+zipFileOriginal.getAbsolutePath()+" to "+tempFile.getAbsolutePath());
        }
        byte[] buf = new byte[1024];

        //System.out.println("tempFile [" + tempFile + "]");
        System.out.println("zipFileOriginal [" + zipFileOriginal + "]");
        System.out.println("zipFileToAdd [" + zipInputFile + "]");
        ZipInputStream zinOriginal = new ZipInputStream(new FileInputStream(tempFile));
        ZipInputStream zinToAdd = new ZipInputStream(new FileInputStream(zipInputFile));
        ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zipFileOriginal));
        
        // Step through each entry in the base zip file
        // 
        ZipEntry entry = zinOriginal.getNextEntry();
        while (entry != null) {
            String name = entry.getName();
            System.out.println("replicating " + name);
            // Add ZIP entry to output stream.
            out.putNextEntry(new ZipEntry(name));
            // Transfer bytes from the ZIP file to the output file
            int len;
            while ((len = zinOriginal.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            entry = zinOriginal.getNextEntry();
        }
        // Close the original stream       
        zinOriginal.close();
        zinOriginal = null;

        entry = zinToAdd.getNextEntry();
        while (entry != null) {
            String name = entry.getName();
            System.out.println("adding " + name);
            // Add ZIP entry to output stream.
            out.putNextEntry(new ZipEntry(name));
            // Transfer bytes from the ZIP file to the output file
            int len;
            while ((len = zinToAdd.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            entry = zinToAdd.getNextEntry();
        }
        // Close the toAdd stream       
        zinToAdd.close();
        // Complete the ZIP file
        out.close();

        if (!writeResult) {
            System.out.println("Restoring original zip file because writeResult was false.");
            zipFileOriginal.delete();
            renameOk=tempFile.renameTo(zipFileOriginal);
            if (!renameOk) {
                throw new RuntimeException("could not rename the file "+tempFile.getAbsolutePath()+" to "+zipFileOriginal.getAbsolutePath());
            }
        } else {
            System.out.println("Zip file updated.");
            tempFile.delete();
        }
    }
}