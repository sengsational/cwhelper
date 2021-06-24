package org.cwepg.svc;

import java.util.Date;

import org.cwepg.hr.CaptureManager;
import org.cwepg.hr.Tuner;
import org.cwepg.hr.TunerHdhr;
import org.cwepg.reg.Registry;

public class VlcCommandLine extends CommandLine {
	
    public VlcCommandLine(String[] commands, int maxSeconds, boolean isWatch){
        String debug = "";
        if (isWatch){
            cmds.add(getRegistryVlcFolder() + "\\vlc.exe" );
        } else {
            System.out.println(new Date() + " ERROR: Recording is not supported on VLC.");
            cmds.add(getRegistryVlcFolder() + "\\vlc.exe" );
        }
        for (int i = 0; i < commands.length; i++) {
            cmds.add(commands[i]);
            debug += commands[i] + " ";
        }
        System.out.println("VLC Command Line Data: " + debug);
		this.maxSeconds = maxSeconds;
	}
    
    private static String getRegistryVlcFolder() {
        String vlcFolder = null;
        try {
            String location = "SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\App Paths\\vlc.exe\\";
            vlcFolder = Registry.getStringValue("HKEY_LOCAL_MACHINE", location,"Path");
            if (vlcFolder == null) throw new Exception("Could not find 'Path' at HKLM " + location);
        } catch (Exception e) {
            System.out.println(new Date() + " ERROR: Could not read registry for VLC. " + e.getMessage());
            //System.err.println(new Date() + " ERROR: Could not read registry for VLC. " + e.getMessage());
            //e.printStackTrace();
        }
        return vlcFolder;
    }
    

}
