package org.cwepg.svc;

import java.util.Date;

import org.cwepg.hr.CaptureManager;
import org.cwepg.hr.Tuner;
import org.cwepg.hr.TunerHdhr;

import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.WinReg;

public class DefaultBrowserCommandLine extends CommandLine {
	
    public DefaultBrowserCommandLine(String url, int maxSeconds){
        String debug = "";
        System.out.println(new Date() + " Starting Default Browser with " + url);
        cmds.add("cmd"); debug += "cmd ";
        cmds.add("/c"); debug += "/c ";
        cmds.add("start"); debug += "start ";
        cmds.add(url); debug += url;
        System.out.println("Default Browser Command Line Data: " + debug);
		this.maxSeconds = maxSeconds;
	}
}
