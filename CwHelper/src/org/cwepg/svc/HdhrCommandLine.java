package org.cwepg.svc;

import java.util.Date;

import org.cwepg.hr.CaptureManager;
import org.cwepg.hr.Tuner;
import org.cwepg.hr.TunerHdhr;

public class HdhrCommandLine extends CommandLine {
	
    public HdhrCommandLine(String[] commands, int maxSeconds, boolean isWatch){
        String debug = "";
        if (isWatch){
            cmds.add(CaptureManager.hdhrPath + "hdhomerun_quicktv.exe");
        } else {
            cmds.add(CaptureManager.hdhrPath + "hdhomerun_config.exe");
        }
        for (int i = 0; i < commands.length; i++) {
            cmds.add(commands[i]);
            debug += commands[i] + " ";
            if ("save".equals(commands[i])) {
                System.out.println(new Date() + " Output from saveFile will be pulled by byte rather than by line.");
                getOutputByByte = true;
            }
        }
        System.out.println("Silicon Dust Command Line Data: " + debug);
		this.maxSeconds = maxSeconds;
	}
}














