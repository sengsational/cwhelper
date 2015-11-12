/*
 * Created on May 30, 2015
 *
 */
package org.cwepg.svc;

import org.cwepg.hr.CaptureManager;
import org.cwepg.hr.Channel;

public class TaskCreateCommandLine extends CommandLine {
    
    public TaskCreateCommandLine(String[] commands){
        String debug = "";
        cmds.add(System.getenv("WINDIR") + "\\system32\\schtasks.exe");
        for (int i = 0; i < commands.length; i++) {
            cmds.add(commands[i]);
            debug += commands[i] + " ";
        }
        System.out.println("Task Command Line Data: " + cmds.get(0) + " " + debug);
        this.maxSeconds = 5;
    }
}
