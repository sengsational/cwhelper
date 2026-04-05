package org.cwepg.hr;

import java.util.ArrayList;
import java.util.Date;

public class ShutdownAll implements Runnable {
	
	private boolean runFlag;
	private String who;
	private String message;
	private ArrayList<Capture> activeCaptures;

	public ShutdownAll(boolean runFlag, String who, ArrayList<Capture> activeCaptures) {
		this.runFlag = runFlag;
		this.who = who;
		this.activeCaptures = activeCaptures;
		this.message = "";
	}

	@Override
	public void run() {
		System.out.println(new Date() + " ShutdownAll.run()");
        if (!runFlag) {
            System.out.println(new Date() + " CaptureManager runFlag is already false (" + who + ")");
            message = "Thanks, but a shutdown was already in process.";
        } else if (activeCaptures.size() != 0  && !who.contains("ENDSESSION")) {
            System.out.println(new Date() + " Active capture(s) prevented shutdown (" + who + ")");
            message = "Active capture(s) prevented shutdown";
        } else {
            System.out.println(new Date() + " Shutdown request being processed.");
            CaptureManager.runFlag = false;
            System.out.println(new Date() + " CaptureManager runFlag is false and requesting interrupt.");
            CaptureManager.requestInterrupt("CaptureManager.shutdown(" + who + ")");
            if (CaptureManager.wakeupEvent != null){
                CaptureManager.wakeupEvent.interruptTimer(WakeupEvent.KILL);
            }
            message = "Ran CaptureManager.requestInterrupt()";
        }
        
        System.out.println(new Date() +  " ShutdownAll.run() finishing with message [" + message + "]");
	}
}
