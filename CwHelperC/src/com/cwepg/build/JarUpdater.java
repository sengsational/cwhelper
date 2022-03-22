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
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;


public class JarUpdater {
    public static void main(String[] args) {

        File[] contents = {new File("F:\\ResourceTest.txt"),
                           new File("F:\\ResourceTest2.bmp")};

        File jarFile = new File("F:\\RepackMe.jar");

        try {
            updateZipFile(jarFile, contents);
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


    public static void updateZipFile(File zipFile, File[] files) throws IOException {
               // get a temp file
        File tempFile = File.createTempFile(zipFile.getName(), null);
               // delete it, otherwise you cannot rename your existing zip to it.
        tempFile.delete();

        boolean renameOk=zipFile.renameTo(tempFile);
        if (!renameOk) {
            throw new RuntimeException("could not rename the file "+zipFile.getAbsolutePath()+" to "+tempFile.getAbsolutePath());
        }
        byte[] buf = new byte[1024];

        System.out.println("tempFile [" + tempFile + "]");
        System.out.println("zipFile [" + zipFile + "]");
        ZipInputStream zin = new ZipInputStream(new FileInputStream(tempFile));
        ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zipFile));

        ZipEntry entry = zin.getNextEntry();
        while (entry != null) {
            String name = entry.getName();
            boolean notInFiles = true;
            for (File f : files) {
                if (f.getName().equals(name)) {
                    notInFiles = false;
                    break;
                }
            }
            if (notInFiles) {
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
        for (int i = 0; i < files.length; i++) {
            System.out.println("trying to find and use [" + files[i] + "]");
            InputStream in = new FileInputStream(files[i]);
            // Add ZIP entry to output stream.
            out.putNextEntry(new ZipEntry(files[i].getName()));
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

}