/*
 * Created on Jul 7, 2011
 *
 */
package org.cwepg.hr;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Properties;

public class IniWriter {

    private CaptureExternalTuner capture;
    public static final String NL = System.getProperty("line.separator"); 

    public IniWriter(CaptureExternalTuner capture) {
        this.capture = capture;
    }


    public void writeCapture(TunerExternal tuner) throws Exception {
        if (capture == null) return;
        if (tuner == null) return;
        String iniFileName = tuner.getIniFileName();
        if (iniFileName == null) throw new Exception("You need a 'iniFileName' entry in your properties file.");
        iniFileName = iniFileName.replaceAll("\"", "");
        System.out.println("We need to modify [" + iniFileName + "]");
        File iniFile = new File(iniFileName);
        if (!iniFile.canWrite()) throw new Exception ("Can not find the file " + iniFileName);
        ArrayList<String> fileLines = getLinesFromFile(iniFileName);
        ArrayList<String> outFileLines = new ArrayList<String>();
        boolean itHappened = false;
        for (String l : fileLines) {
            if (l.startsWith("Reserve") && l.length() < 11 && !itHappened){
                l = l + capture.getCapDvhsReserveString();
                itHappened = true;
            }
            outFileLines.add(l);
        }
        if (!itHappened){
            outFileLines = new ArrayList<String>();
            for (String l : fileLines) {
                if (l.startsWith("Reserve") && l.indexOf("3001") == 48 && !itHappened){
                    l = l.substring(0,10) + capture.getCapDvhsReserveString();
                    itHappened = true;
                }
                outFileLines.add(l);
            }
        }
        if (!itHappened){
            throw new Exception ("You need to remove old reservations from from " + iniFileName);
        }
        //for (String l : outFileLines) {
        //    System.out.println("debug " + l);
        //}
        try {
            BufferedWriter out = new BufferedWriter(new FileWriter(iniFileName));
            for (String l : outFileLines) {
                out.write(l + NL);
            }
            out.flush();
            out.close();
        } catch (Exception e){
            throw new Exception ("Could not re-write " + iniFileName + ". " + e.getMessage());
        }
    }
    
    private static ArrayList<String> getLinesFromFile(String iniFileName) throws Exception {
        ArrayList<String> fileLines = new ArrayList<String>();
        BufferedReader in = null;
        try {
            in = new BufferedReader(new FileReader(iniFileName));
            String l = null;
            while ((l = in.readLine()) != null){
                if (l != null && !l.equals("")) fileLines.add(l);
            }
            in.close();
        } catch (Exception e) {
            throw new Exception ("Problem reading " + iniFileName + ".  " + e.getMessage());
        } finally {
            try {
                in.close();
            } catch (Exception e) {}
        }
        return fileLines;
    }


    /**
     * @param args
     */
    public static void main(String[] args)  throws Exception {
        
        Properties externalProps = new Properties();
        externalProps.load(new FileInputStream("ExternalTuners.properties"));
        Slot slot = new Slot("12/12/2012 10:00", "60");
        TunerExternal tuner = new TunerExternal(externalProps, "02", false);
        Channel channel = new ChannelDigital("42.3",tuner, "42.3:1","unk");
        
        CaptureExternalTuner capture = new CaptureExternalTuner(slot, channel);
        Target target = new Target("c:\\aFileNameString.tp","This is the new title","C:\\", ".xxx", "unk", Tuner.EXTERNAL_TYPE);
        capture.setTarget(target);
        IniWriter writer = new IniWriter(capture);
        writer.writeCapture(tuner);
    }
}
