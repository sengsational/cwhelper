package org.cwepg.svc;

import java.util.Date;

import org.cwepg.hr.CaptureHdhr;
import org.cwepg.hr.CaptureManager;
import org.cwepg.hr.Tuner;
import org.cwepg.hr.TunerHdhr;

public class HdhrCommandLine extends CommandLine {
	
    public HdhrCommandLine(String[] commands, int maxSeconds, boolean isWatch){
        String debug = "";
        if (isWatch){
            cmds.add("\"" + CaptureManager.hdhrPath + "hdhomerun_quicktv.exe\""); // DRS 20200907 - Suround path exe with quotes
        } else {
            cmds.add("\"" + CaptureManager.hdhrPath + "hdhomerun_config.exe\""); // DRS 20200907 - Suround path exe with quotes
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

    public void extendCommandKillDuration(int extendMinutes) {
        super.extendKillSeconds(extendMinutes * 60);
    }
    
    public void setCapture(CaptureHdhr captureHdhr) {
        super.capture = captureHdhr;
    }

    
}














