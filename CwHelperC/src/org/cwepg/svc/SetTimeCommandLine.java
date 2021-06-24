/*
 * Created on Jan 20, 2019
 *
 */
package org.cwepg.svc;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;

public class SetTimeCommandLine extends CommandLine {

    public SetTimeCommandLine(String time, int maxSeconds, int debugLevel) {
        String debug = "";
        String[] commands = new String[4];
        commands[0] = "cmd";
        commands[1] = "/C";
        commands[2] = "time";
        commands[3] = time;
        for (int i = 0; i < commands.length; i++) {
            cmds.add(commands[i]);
            debug += commands[i] + " ";
        }
        System.out.println(new Date() + " SetTime Command Line Data: " + debug + " [command line timeout " + maxSeconds + "]");
        this.maxSeconds = maxSeconds;
        super.setDebug(debugLevel);
    }
    
    public static void main(String[] args) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");

        String nowTimeString = sdf.format(new Timestamp(System.currentTimeMillis()));
        SetTimeCommandLine stcl = new SetTimeCommandLine(nowTimeString, 1, 0);
        stcl.runProcess();
    }
}
