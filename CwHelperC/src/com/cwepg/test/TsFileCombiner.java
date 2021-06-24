/*
 * Created on Feb 6, 2019
 *
 */
package com.cwepg.test;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class TsFileCombiner {

    public static void main(String[] args) {
        String inputFile1 = args[0];
        //String inputFile2 = args[1];
        String x = "message ";
        System.out.println(x + " 11:29");
        
        String destinationFile = args[1];
        
        try {
            //create FileInputStream object for source file
            System.out.println("file [" + inputFile1 + "]");
            FileInputStream fin1 = new FileInputStream(inputFile1);
            //create FileInputStream object for source file
            //System.out.println("file [" + inputFile2 + "]");
            //FileInputStream fin2 = new FileInputStream(inputFile2);
            
            //create FileOutputStream object for destination file
            FileOutputStream fout = new FileOutputStream(destinationFile, true);
            
            byte[] b = new byte[1024];
            int noOfBytes = 0;
            
            System.out.println("Copying file using streams");
            
            //read bytes from source file and write to destination file
            while( (noOfBytes = fin1.read(b)) != -1 ) {
                fout.write(b, 0, noOfBytes);
            }
            fin1.close();
            System.out.println("File copied " + inputFile1);

            /*
            while( (noOfBytes = fin2.read(b)) != -1 ) {
                fout.write(b, 0, noOfBytes);
            }
            fin2.close();
            System.out.println("File copied " + inputFile2);
            */

            //close the streams
            fout.close();           
            
        } catch(FileNotFoundException fnf) {
            System.out.println("Specified file not found :" + fnf);
        } catch(IOException ioe) {
            System.out.println("Error while copying file :" + ioe);
        }
    }        

}
