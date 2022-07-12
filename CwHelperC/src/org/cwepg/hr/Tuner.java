package org.cwepg.hr;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;


public abstract class Tuner { //implements Comparable {
	
	ArrayList<Capture> captures = new ArrayList<Capture>();
	LineUp lineUp;
    
    public String id;
    public int number;
    public String analogFileExtension = "";

    boolean liveDevice = false;
    long lastRefresh = 0;

    public static final int MYHD_TYPE = 0;
    public static final int FUSION_TYPE = 1;
    public static final int HDHR_TYPE = 2;
    public static final int EXTERNAL_TYPE = 3;
    
    public static final int TIME_HORIZON_HOURS = 336; // 14 days * 24 hours
    
    public static final SimpleDateFormat SDF = new SimpleDateFormat("MM/dd HH:mm:ss");
    
    int tunerType = -1;
    protected String recordPath = "";
    private String startEndNextEvent; //DRS 20220707 - For improved event logging
    
    public Tuner(){
    }
    
    public void addLineUp(LineUp lineUp){
    	this.lineUp = lineUp;
    }
    
    public abstract void scanRefreshLineUp(boolean useExistingFile, String signalType, int maxSeconds) throws Exception; 

    /*
     * methods for adding captures to this tuner
     */
    
	public boolean available(Capture capture, boolean mustBeInLineup, boolean verbose) {
        if (capture == null){
            if (verbose) System.out.println(new Date() + " Tuner.available() Capture null.");
            return false;
        }
        if (!slotOpen(capture.slot)) {
            if (verbose) System.out.println(new Date() + " Tuner.available() Slot not open. " + this.getFullName());
            return false;
        }
        if (mustBeInLineup && !this.lineUp.contains(capture.channel)){
            if (verbose) System.out.println(new Date() + " Tuner.available() Channel not in lineup. " + this.getFullName());
            return false;
        }
		return true;
	}
    
	boolean slotOpen(Slot slot) {
		return getConflictingCapture(slot) == null;
	}

	Capture getConflictingCapture(Slot slot){
    	Capture conflictingCapture = null;
        //System.out.println("checking " + captures.size() + " for conflict.");
		for (Iterator iter = captures.iterator(); iter.hasNext();) {
			Capture capture = (Capture) iter.next();
			if (capture.slot.conflicts(slot)) conflictingCapture = capture;
		}
        if (conflictingCapture != null){
            //System.out.println("Existing capture conflicts: " + conflictingCapture);
        }
		return conflictingCapture;
    }

    public int getRemainingGb() {
        int remainingSpaceGb = -1;
        if (this.recordPath != null && !this.recordPath.trim().equals("")){
            File recordPathFile = new File(recordPath);
            long remainingSpace = recordPathFile.getUsableSpace();
            remainingSpaceGb = (int)(remainingSpace / (1024L * 1024L * 1024L));
        } 
        return remainingSpaceGb;
    }

    public String getLowSpaceComment(int lowGbValue) {
        int gbValue = getRemainingGb();
        if (gbValue < 0) return "";
        if (gbValue < lowGbValue) return "WARNING: " + gbValue + " GB remains on drive.";
        else return "";
    }

    /*
     * Queries used by CaptureManager
     */
    
    public abstract void addCaptureAndPersist(Capture newCapture, boolean writeIt) throws CaptureScheduleException;
    public abstract void removeAllCaptures(boolean localRemovalOnly);
    public abstract void removeCapture(int j) throws Exception;
    public abstract void removeCapture(Capture capture);
    public abstract void removeLocalCapture(Capture capture);
    public abstract void refreshCapturesFromOwningStore(boolean interrupt);
    public abstract String getFullName();
    public abstract List<Capture> getCaptures();
    public abstract String getDeviceId();
    public abstract int getType();
    public abstract void writeDefaultRecordPathFile();
    public abstract void removeDefaultRecordPathFile();
    public abstract void addCapturesFromStore();


    public Calendar getNextCaptureCalendar(){
    	Capture captureWithNextEvent = getCaptureWithNextEvent();
    	if (captureWithNextEvent != null){
    	    Calendar unhandledEvent = captureWithNextEvent.getUnhandledEvent();
    	    //System.out.println(new Date() + " [NEXTCAP DEBUG] Tuner " + this.id + this.number + " " + captureWithNextEvent.getTitle() + " " + captureWithNextEvent.getNextEvent() + " Next Event: " + SDF.format(unhandledEvent.getTime()));
    	    this.startEndNextEvent = captureWithNextEvent.getStartHandled()?"end":"start"; // Only to clarify logging
    		return unhandledEvent;
    	}
    	return null;
    }
    
    // DRS 20220707 - Improve event logging
    public String getStartEndFromLastCalendarInquiry() {
        return this.startEndNextEvent;
    }

    
	public Capture getCaptureWithNextEventInLeadTime(long lead_time_ms) {
		Capture nextCaptureInLeadTime = null;
		Capture captureWithNextEvent = getCaptureWithNextEvent();
		if (captureWithNextEvent != null){
			if (!captureWithNextEvent.getStartHandled()){
				long msToStart = captureWithNextEvent.slot.start.getTimeInMillis() - new Date().getTime();
				if (msToStart <= lead_time_ms){
					nextCaptureInLeadTime = captureWithNextEvent;
				} else {
				    //System.out.println("Tuner.getcaptureWithNextEventInLeadTime START NOT within lead time " + nextCapture);
				}
			} else if (!captureWithNextEvent.getEndHandled()){
				long msToEnd = captureWithNextEvent.slot.end.getTimeInMillis() - new Date().getTime();
				if (msToEnd <= lead_time_ms){
					nextCaptureInLeadTime = captureWithNextEvent;
				} else {
					//System.out.println("Tuner.getcaptureWithNextEventInLeadTime END NOT within lead time " + nextCapture);
				}
			}
		}
		return nextCaptureInLeadTime;
	}

	private Capture getCaptureWithNextEvent(){
		Capture nextCapture = null;
		Calendar nextEventCalendar = null;
		//StringBuffer buf = new StringBuffer();
		for (Iterator iter = captures.iterator(); iter.hasNext();) {
			Capture capture = (Capture) iter.next();
			Calendar unhandledEvent = capture.getUnhandledEvent();
			if (unhandledEvent != null){
				if (nextEventCalendar == null || unhandledEvent.before(nextEventCalendar)){
					nextCapture = capture;
					nextEventCalendar = unhandledEvent;
				} else {
					//System.out.println(new Date() + " [NEXTCAP DEBUG] capture did not have next event " + capture);
				}
			}
			//buf.append("[" + capture.getTitle() + " " + (unhandledEvent!=null?SDF.format(unhandledEvent.getTime()):"(none)") + "],");
		}
		//System.out.println(new Date() + " [NEXTCAP DEBUG] " + buf.toString());
		return nextCapture;
	}

    public Channel getChannel(String channelVirtual, String rfChannel, String input, String protocol) {
        if (channelVirtual != null && !(channelVirtual.indexOf(".0:1") > 0 || channelVirtual.indexOf(".0:2") > 0)){
            return this.lineUp.getChannelDigital(channelVirtual, rfChannel, input, protocol);
        } else {
            return this.lineUp.getChannelAnalog(rfChannel, input, protocol);
        }
    }
    
    public void delistCapturesMatchingFileName(String fileName, ArrayList<Capture> captures, String title) {
        boolean isWatch = fileName.contains(":\\WATCH");
        ArrayList<Capture> toRemove = new ArrayList<Capture>();
        for (int i = 0; i < captures.size(); i++){
            Capture aCapture = captures.get(i);
            if (!isWatch && aCapture.getFileName().equals(fileName)){
                toRemove.add(aCapture);
            } else if (isWatch && title!=null && aCapture.getTitle().equals(title)) {
                toRemove.add(aCapture);
            }
        }
        for (Iterator iter = toRemove.iterator(); iter.hasNext();) {
            Capture aCapture = (Capture) iter.next();
            // DRS 20190719 - Added 1 - Removed wakeups for Fusion per Terry's email today
            if (!(aCapture instanceof CaptureFusion)) 
            aCapture.removeWakeup();
            captures.remove(aCapture);
        }
    }



    public boolean getLiveDevice() {
        return liveDevice;
    }

    //DRS 20101218 - Added method - Don't add disabled HDHR tuners
    public boolean isDisabled() {
        return false;
    }

    /*
     * 
     * compareTo, equals, hashCode, toString
     * 
     */
    
	public int compareTo(Object otherTuner){
		if (otherTuner instanceof Tuner){
			Tuner ot = (Tuner)otherTuner;
			System.out.println("Tuner.compareTo " + (id + number).compareTo(ot.id + ot.number));
			return (id + number).compareTo(ot.id + ot.number);
			
		} else {
			return -1;
		}
	}
	
	public boolean equals(Object otherTuner){
		if (otherTuner instanceof Tuner){
			Tuner ot = (Tuner)otherTuner;
			return (ot.id + ot.number + ot.recordPath + ot.liveDevice).equals(id + number + recordPath + liveDevice);
		} else {
			return false;
		}
	}
	
	public int hashCode(){
		return (id + number).hashCode();
	}

    public String toString(){
        StringBuffer buf = new StringBuffer("Tuner: id [" + id + "] number [" + number + "] channels [" + lineUp.size() + "]");
        for (Iterator iter = captures.iterator(); iter.hasNext();) {
			buf.append(((Capture) iter.next()).toString() + "\n");
		}
        return new String(buf);
    }

    public String getRecordPath() {
        return this.recordPath;
    }
    
    public String getRecordPath(boolean backslashes, boolean doublebackslashes) {
        if (this.recordPath != null) {
            String updatedRecordPath = this.recordPath;
            if (!this.recordPath.endsWith("\\") && !this.recordPath.endsWith("/")) updatedRecordPath = updatedRecordPath + "\\";
            updatedRecordPath = updatedRecordPath.replaceAll("/","\\\\");
            if (doublebackslashes) {
                updatedRecordPath = updatedRecordPath.replaceAll("\\\\", "\\\\\\\\");
            }
            return updatedRecordPath;
        }
        return this.recordPath;
    }

    public String getAnalogFileExtension() {
        return this.analogFileExtension;
    }

    public void setAnalogFileExtension(String analogFileExtension) {
        this.analogFileExtension = analogFileExtension;
        
    }

    public void setLiveDevice(boolean liveDevice) {
        this.liveDevice = liveDevice;
        
    }

    public void setRecordPath(String recordPath) {
        if (recordPath == null) recordPath = "";
        this.recordPath = recordPath;
        if (recordPath.equals("")){
            this.removeDefaultRecordPathFile();
        } else {
            this.writeDefaultRecordPathFile();
        }
    }
}