package org.cwepg.svc;

import java.io.File;
import java.util.Date;
import java.util.StringTokenizer;

import org.cwepg.hr.CaptureManager;
import org.cwepg.reg.RegistryHelperMyhd;

public class DbCopyCommandLine extends CommandLine {
	
    public DbCopyCommandLine(String commands, int maxSeconds){
        //COPY /B \\<MasterIP>\CW_EPG_DATA\cw_epg.mdb <cw_epg data folder>\cw_epg.mdb 
        cmds.add("cmd.exe");
        cmds.add("/C");
        cmds.add("COPY");
        cmds.add("/B");
        StringTokenizer tok = new StringTokenizer(commands);
        StringBuffer build = new StringBuffer();
        if (tok.hasMoreTokens()){
            build.append("\\\\" + tok.nextToken() + File.separator);
        }
        if (tok.hasMoreTokens()){
            build.append(tok.nextToken() + File.separator + "cw_epg.mdb");
        }
        cmds.add(build.toString());
        String externalEpgFile = CaptureManager.dataPath + "cw_epg.mdb";
        cmds.add(externalEpgFile);
        RegistryHelperMyhd.setExternalEpgFile(externalEpgFile);
        StringBuffer debug = new StringBuffer();
        for (int i = 0; i < cmds.size(); i++){
            debug.append(cmds.get(i) + " ");
        }
        System.out.println(new Date() + " DbCopy Command Line Data: " + debug);
		this.maxSeconds = maxSeconds;
	}

	public static void main(String[] args) throws Exception {
        String dbCopyParms = "192.168.1.45 CW_EPG_DAT";
        int durationSeconds = 3;
        DbCopyCommandLine cl = new DbCopyCommandLine(dbCopyParms, durationSeconds);
        boolean goodResult = cl.runProcess(); // blocks
        if (!goodResult) throw new Exception("failed to handle " + cl.getCommands() + "\n" + cl.getErrors());
	}
}














