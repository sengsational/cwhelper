/*
 * Created on Jun 18, 2011
 *
 */
package org.cwepg.svc;

import java.util.Date;

public class ExternalTunerCommandLine extends CommandLine {

    public ExternalTunerCommandLine(String[] commands, int maxSeconds, boolean isWatch) {
        String debug = "";
        for (int i = 0; i < commands.length; i++) {
            cmds.add(commands[i]);
            debug += commands[i] + " ";
        }
        System.out.println(new Date() + " ExternalTuner Command Line Data: " + debug + " [command line timeout " + maxSeconds + "]");
        this.maxSeconds = maxSeconds;
    }
}
