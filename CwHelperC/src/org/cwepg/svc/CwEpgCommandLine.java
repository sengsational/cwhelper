package org.cwepg.svc;

import java.util.ArrayList;
import java.util.Date;
import java.util.StringTokenizer;

import org.cwepg.hr.CaptureHdhr;
import org.cwepg.hr.CaptureManager;
import org.cwepg.hr.WakeupEvent;

public class CwEpgCommandLine extends CommandLine {
	
    public CwEpgCommandLine(String commands, int maxSeconds){
        String debug = "";
        cmds.add(CaptureManager.cwepgPath + "cw_epg.exe"); //DRS 20210621 Replaced path per Terry. DRS 20210327 - Removed path per Terry
        StringTokenizer tok = new StringTokenizer(commands);
        while (tok.hasMoreTokens()) {
            String parm = tok.nextToken();
            cmds.add(parm);
            debug += parm + " ";
        }
        System.out.println("CwEpg Command Line Data: " + CaptureManager.cwepgPath + "cw_epg.exe " + debug); //DRS 20210621 Replaced path per Terry.  //DRS 20210327 - Removed path per Terry
		this.maxSeconds = maxSeconds;
	}
    
    // DRS 20210314 - Added constructor - call using cmd.exe instead of directly with cw_epg.exe.
    public CwEpgCommandLine(AutorunFile autorunFile) {
        StringBuffer debugString = new StringBuffer();
        try {
            String overrideCommand = autorunFile.getOverrideCommand();
            if (overrideCommand != null && !overrideCommand.equals("null") && !overrideCommand.isEmpty()) {
                System.out.println(new Date() + " WARNING: WakeupEventData.txt override command is ignored!");
            } 
            cmds.add(CaptureManager.cwepgPath + "cw_epg.exe"); //DRS 20210621 Replaced path per Terry. DRS 20210327 - Removed path per Terry
            cmds.addAll(getCommandArrayFromParameters(autorunFile.getParameters()));
            for (String cmd : cmds) {
                debugString.append(cmd); debugString.append("    ");
            }
        } catch (Throwable t) {
            System.out.println(new Date() + " ERROR: Failed to completely load commands for command line. " + t.getMessage());
        }
        System.out.println("CwEpg Command Line Data: " + debugString.toString());
        this.maxSeconds = autorunFile.getMaximumRunSeconds();
    }
    
    static ArrayList<String> getCommandArrayFromParameters(String parameters) {
        ArrayList<String> parmList = new ArrayList<String>();
        StringTokenizer tok = new StringTokenizer(parameters);
        while (tok.hasMoreTokens()) {
            String parm = tok.nextToken();
            if (parm.startsWith("\"")) {
                boolean closeQuoteFound = parm.endsWith("\"");
                while (tok.hasMoreTokens() && !closeQuoteFound) {
                    parm += " " + tok.nextToken();
                    closeQuoteFound = parm.endsWith("\"");
                }
                if (parm.endsWith("\"")) {
                    closeQuoteFound = true;
                    try {parm = parm.substring(1, parm.length() - 1);} catch (Throwable t) {}
                }
            }
            System.out.println("Command component: [" + parm + "]");
            parmList.add(parm);
        }
        return parmList;
    }
    
	public static void main(String[] args) throws Exception {
	    CwEpgCommandLine cl = new CwEpgCommandLine( WakeupEvent.getInstanceFromDisk().getAutorunFile());
        boolean goodResult = cl.runProcess(); // blocks
        if (!goodResult) throw new Exception("failed to handle " + cl.getCommands() + "\n" + cl.getErrors());
	}
}














