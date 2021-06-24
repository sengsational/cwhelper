/*
 * Created on Nov 11, 2010
 *
 */
package org.cwepg.hr;

import java.util.Date;

import org.cwepg.svc.CommandLine;
import org.cwepg.svc.DbCopyCommandLine;

public class DbCopier implements Runnable {
    
    static DbCopier copier = null;
    static final String DEFAULT_SHARE = "CW_EPG_DATA";
    int timeout = 20;
    String lastResult = "";
    String dbCopyParms = "";
    
    private DbCopier(){
    }

    public static DbCopier getInstance() {
        if (copier == null){
            copier = new DbCopier();
        }
        return copier;
    }

    private void init(String source, String share, String timeout){
        if (share == null || share.trim().length() == 0){
            share = DEFAULT_SHARE;
        }
        if (timeout != null && timeout.trim().length() != 0){
            try {this.timeout = Integer.parseInt(timeout);} catch (Exception e) {};
        }
        dbCopyParms = source + " " + share;
    }
    
    public void copyAndWait(String source, String share, String timeout) {
        init(source, share, timeout);
        run();
    }

    public void startCopyAndReturn(String source, String share, String timeout) {
        init(source, share, timeout);
        new Thread(this).start();
    }

    public String getLastResult() {
        return lastResult;
    }

    public void run(){
        try {
            boolean goodResult = false;
            DbCopyCommandLine cl = new DbCopyCommandLine(dbCopyParms, timeout);
            lastResult = new Date() + " Started.";
            goodResult = cl.runProcess(); // blocks
            if (!goodResult){
                lastResult = new Date() + " failed to handle " + cl.getCommands() + ((cl.getErrors()==null)?"-":("\n" + cl.getErrors()));
                System.out.println("last result was :" + lastResult);
                throw new Exception(lastResult);
            }
            lastResult = new Date() + " OK.";
            report("dbcopy", cl);
        } catch (Throwable e) {
            System.out.println("ERROR DbCopier.run method <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");
            System.out.println(new Date() + " DbCopier.run " + ((e.getMessage()==null)?"":e.getMessage()));
            System.err.println(new Date() + " DbCopier.run " + ((e.getMessage()==null)?"":e.getMessage()));
            e.printStackTrace();
        }
    }

    private void report(String string, CommandLine cl) {
        String clResult = report(cl);
        if (clResult != null){
            System.out.println(string + " said: [" + clResult + "]");
        } else {
            System.out.println(string + " said: []");
        }
    }
    
    private String report(CommandLine cl){
        String clResult = cl.getOutput();
        if (clResult != null && clResult.trim().length() > 0){
            return clResult.trim();
        }
        return null;
    }
    
    public static void main(String[] args) {
        DbCopier copier = DbCopier.getInstance();
        
        boolean testCopyAndReturn = false;
        if (testCopyAndReturn){
            copier.startCopyAndReturn("192.168.1.45", "CW_EPG_DAT", "5");
            System.out.println("clr1: " + copier.getLastResult());
            try {Thread.sleep(1000);} catch(Exception e){};
            System.out.println("clr2: " + copier.getLastResult());
        }

        boolean testCopyAndWait = false;
        if (testCopyAndWait){
            copier.copyAndWait("192.168.1.45", "CW_EPG_DATA", "20");
            System.out.println("clr1: " + copier.getLastResult());
            try {Thread.sleep(1000);} catch(Exception e){};
            System.out.println("clr2: " + copier.getLastResult());
        }

        boolean testNotSpecifyEverything = false;
        if (testNotSpecifyEverything){
            copier.copyAndWait("192.168.1.45", null, null);
            System.out.println("clr1: " + copier.getLastResult());
        }

        boolean testName = true;
        if (testName){
            copier.copyAndWait("E521DRS", null, null);
            System.out.println("clr1: " + copier.getLastResult());
        }
        
    }

}
