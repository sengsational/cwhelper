package org.cwepg.svc;

import java.util.StringTokenizer;

import org.cwepg.hr.CaptureManager;

public class CwEpgCommandLine extends CommandLine {
	
    public CwEpgCommandLine(String commands, int maxSeconds){
        String debug = "";
        cmds.add(CaptureManager.cwepgPath + "cw_epg.exe");
        StringTokenizer tok = new StringTokenizer(commands);
        while (tok.hasMoreTokens()) {
            String parm = tok.nextToken();
            cmds.add(parm);
            debug += parm + " ";
        }
        System.out.println("CwEpg Command Line Data: " + CaptureManager.cwepgPath + "cw_epg.exe " + debug);
		this.maxSeconds = maxSeconds;
	}

	public static void main(String[] args) throws Exception {
        String cwepgParms = "-auto -debug";
        int durationSeconds = 600;
        CwEpgCommandLine cl = new CwEpgCommandLine(cwepgParms, durationSeconds);
        boolean goodResult = cl.runProcess(); // blocks
        if (!goodResult) throw new Exception("failed to handle " + cl.getCommands() + "\n" + cl.getErrors());
	}
}














