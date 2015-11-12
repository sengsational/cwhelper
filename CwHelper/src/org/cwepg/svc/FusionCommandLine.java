package org.cwepg.svc;

import java.io.File;
import java.util.StringTokenizer;
import org.cwepg.hr.DFile;
import org.cwepg.hr.CaptureManager;
import org.cwepg.hr.TunerManager;

public class FusionCommandLine extends CommandLine {
	
    public FusionCommandLine(String commands, int maxSeconds){
        String debug = "";
        cmds.add(TunerManager.fusionInstalledLocation + "\\FusionHdtvTray.exe");
        StringTokenizer tok = new StringTokenizer(commands);
        while (tok.hasMoreTokens()) {
            String parm = tok.nextToken();
            cmds.add(parm);
            debug += parm + " ";
        }
        System.out.println("Fusion Command Line Data: " + TunerManager.fusionInstalledLocation + "\\FusionHdtvTray.exe " + debug);
		this.maxSeconds = maxSeconds;
	}

	public static void main(String[] args) throws Exception {
        StringBuffer buf = new StringBuffer("");

	    TunerManager.getInstance();
        String fusionParms = System.getProperty("java.io.tmpdir") + "dummy.tvpi";
        try {
            DFile fromFile = new DFile(CaptureManager.dataPath + "dummy.tvpi");
            boolean copyResult = fromFile.copyTo(new File(fusionParms));
            if (copyResult) buf.append("copied to [" + fusionParms + "] ");
            else buf.append("ERROR: Unable to copy to " + fusionParms + " from " +  CaptureManager.dataPath + "dummy.tvpi");
        } catch (Throwable t) {
            buf.append("ERROR: Unable to copy to " + fusionParms + " from " +  CaptureManager.dataPath + "dummy.tvpi " + t.getMessage());
        }
        
        int durationSeconds = 60;
        FusionCommandLine cl = new FusionCommandLine(fusionParms, durationSeconds);
        boolean goodResult = cl.runProcess(); // blocks
        if (!goodResult) buf.append(" failed to handle [" + cl.getCommands() + "]\n" + cl.getErrors());
        System.out.println("main:" +  new String(buf));
	}
}














