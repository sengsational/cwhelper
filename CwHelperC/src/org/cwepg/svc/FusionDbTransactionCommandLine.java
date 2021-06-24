/*
 * Created on Jul 30, 2019
 *
 */
package org.cwepg.svc;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.StringTokenizer;

import org.cwepg.hr.DFile;
import org.cwepg.hr.CaptureManager;
import org.cwepg.hr.TunerManager;

public class FusionDbTransactionCommandLine extends CommandLine {

    public FusionDbTransactionCommandLine(String sqlCommand, int maxSeconds){
        StringBuffer echoCommand = new StringBuffer(new Date() + " Fusion Db Transaction Command Line Data: ");

        // DRS 20210423 - comment 1, add 1 - Terry said path not needed
        //cmds.add(CaptureManager.cwepgPath + "\\FusionTraySQL.exe");
        cmds.add("FusionTraySQL.exe");
        echoCommand.append(getQuoted(cmds.get(0), " "));
        cmds.add(TunerManager.fusionInstalledLocation);
        echoCommand.append(getQuoted(cmds.get(1), " "));
        cmds.add(sqlCommand);
        echoCommand.append(getQuoted(cmds.get(2), ""));

        System.out.println(echoCommand);
        this.maxSeconds = maxSeconds;
        //super.setDebug(5);
    }

    // For testing only
    public void overrideExePath(String pathOverride) {
        String original = cmds.get(0);
        int exeLoc = original.lastIndexOf("\\");
        if (exeLoc > -1) {
            String exe = original.substring(exeLoc);
            cmds.set(0, pathOverride + exe);
            System.out.println("Override [" + cmds.get(0) + "]");
        } else {
            System.out.println("Override did not work. ");
        }
    }

    public static void main(String[] args) throws Exception {
        StringBuffer buf = new StringBuffer("");
        String sqlCommand = "\"insert into ReserveList values ('M0015060000420190722204500001500',15,65536,true,'Encore!','PJKF24','2019-07-22 20:45:00','2019-07-22 21:00:00',1536,'TheFileName','MyPC')\"";
        int durationSeconds = 60;
        FusionDbTransactionCommandLine cl = new FusionDbTransactionCommandLine(sqlCommand, durationSeconds);
        cl.overrideExePath("c:\\my\\dev");
        boolean goodResult = cl.runProcess(); // blocks
        if (!goodResult)
            buf.append(" failed to handle [" + cl.getCommands() + "]\n" + cl.getErrors());
        System.out.println("main:" + new String(buf));
    }
}
