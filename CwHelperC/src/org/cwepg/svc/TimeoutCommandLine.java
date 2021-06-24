package org.cwepg.svc;

import java.util.Date;

import org.cwepg.hr.CaptureManager;
import org.cwepg.hr.Tuner;
import org.cwepg.hr.TunerHdhr;

public class TimeoutCommandLine extends CommandLine {
	
    /* This appears not to have any references to it */
    
    public TimeoutCommandLine(String[] commands, int maxSeconds, boolean isWatch){
        String debug = "";
        if (isWatch){
            cmds.add("timeout.exe");
        } else {
            cmds.add("timeout.exe");
        }
        for (int i = 0; i < commands.length; i++) {
            cmds.add(commands[i]);
            debug += commands[i] + " ";
            System.out.println(new Date() + " Output from saveFile will be pulled by byte rather than by line.");
            getOutputByByte = true;
        }
        System.out.println("Timeout Command Line Data: " + debug);
		this.maxSeconds = maxSeconds;
	}
}














