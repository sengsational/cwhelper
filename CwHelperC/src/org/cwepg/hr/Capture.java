/*
 * Created on Jul 25, 2009
 *
 */
package org.cwepg.hr;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import com.sun.jna.platform.win32.Kernel32;


public abstract class Capture implements Runnable, Comparable {

    public Target target;
    public String lastError;
    public Slot slot;

    Channel channel;
    Kernel32.HANDLE wakeupHandle;
    boolean startHandled;
    boolean endHandled;
    boolean isRecurring = false;
    String recurrenceDays = "";

    static final int START = 0;
    static final int END = 1;
    static String[] EVENT_NAMES = {"START", "END"};
    static final SimpleDateFormat GCDTF = new SimpleDateFormat("yyyyMMddHHmm");
    static final boolean dc = false; // dump compare
    
    public Capture(){
        
    }
    
    // used in when captures created from registry or database
    public Capture(Slot slot, Channel channel) {
        try {
            this.slot = slot;
            this.channel = channel;
            if (channel == null){
                throw new Exception("ERROR: Channel was not in the lineup.");
            }
        } catch (Exception e) {
            //lastError = new Date() + " Capture<init> " + e.getMessage();
            //System.out.println(lastError);
        }
    }
    
    // only used by Hdhr
    public String getPersistanceData() {
        return slot.getPersistanceData() + "~" + ((ChannelDigital)channel).getPersistanceData() + "~" + target.getPersistanceData();
    }
    
    public void setTarget(Target target){
        this.target = target;
    }
    
    public void setWakeup() {
        wakeupHandle = WakeupManager.set(slot.getMsUntilStart(), this.getTitle());
    }

    public void removeWakeup() {
        if (wakeupHandle != null){
            WakeupManager.clear(wakeupHandle, this.getTitle());
            wakeupHandle = null;
        }
    }
    
    public boolean hasEnded() {
        return slot.endsBefore(Calendar.getInstance());
    }

    public boolean hasStarted() {
        if (this.hasEnded()) return false;
        Calendar nowCal = Calendar.getInstance();
        nowCal.add(Calendar.SECOND, 60); // treat things just about to start as already started
        if (slot.startsAfter(nowCal)) return false;
        return true;
    }
    
    public boolean isNearEnd(int seconds){
        Calendar slightFutureCalendar = Calendar.getInstance();
        slightFutureCalendar.add(Calendar.SECOND, seconds);
        return slot.endsBefore(slightFutureCalendar);
    }
    
    public synchronized Calendar getUnhandledEvent(){
        if (!getStartHandled()){
            return slot.start;
        } else if (!getEndHandled()){
            return slot.end; 
        }
        return null;
    }
    
    public synchronized int getNextEvent(){
        // if they ask about the next event and this capture should already
        // be done, make sure startHandled flag is flipped, and return
        // the next event is an END.
        if (hasEnded()){
            startHandled = true;
            return CaptureHdhr.END;
        }
        if (!startHandled) return CaptureHdhr.START;
        if (!endHandled) return CaptureHdhr.END;
        return -1;
    }
    
    public synchronized String markEventHandled() {
        if (!startHandled){
            startHandled = true;
            // DRS 20190719 - Added 1 - Removed wakeups for Fusion per Terry's email today
            if (this instanceof CaptureFusion) return EVENT_NAMES[START];
            removeWakeup();
            return EVENT_NAMES[START];
        } else {
            endHandled = true;
            return EVENT_NAMES[END];
        }
    }
    
    public synchronized boolean getEndHandled(){
        return endHandled;
    }

    public synchronized boolean getStartHandled(){
        return startHandled;
    }
    
    // used for making test captures
    public void shift(int randomHours) {
        if (this.slot != null){
            slot.shift(randomHours);
        }
    }
  
    public String toString(){
        String targetData = this.target == null?"":this.target.toString();
        return "Capture [" + channel + "] [" + slot + "] start/end [" + startHandled + "/" + endHandled + "] target [" + targetData + "]";
    }
    
    public String getHtml(int sequence) {
        return getHtml(sequence, true);
    }
        
    public String getHtml(int sequence, boolean full) {    
        StringBuffer buf = new StringBuffer();
        try {
            if (full) {
            buf.append("<tr><td>Sequence:" + sequence + "</td><td>" 
                    + slot + "</td>" 
                    + channel.getHtml() + "<td>" 
                    + target.getFileNameOrWatch() + "</td><td>" 
                    + target.title + "</td><td>" 
                    + (this.isRecurring()?"recurring":"") + "</td><td>"
                    + this.recurrenceDays + "</td>"
                    + "</tr>\n");
            } else {
            buf.append("<tr><td>Sequence:" + sequence + "</td><td>" 
                    + "[" + slot.getFormattedStartTimeString() + " - " + slot.getFormattedStopTimeString() + "]</td>" 
                    + channel.getHtml(false) + "<td>" 
                    + target.title + "</td>" 
                    + "</tr>\n");
            }
        } catch (Throwable e) {
            buf.append("<tr><td>Sequence:" + sequence + "</td></tr>\n");
            System.out.println("Bad capture data: channel null? " + (channel == null) + ", tuner null? " + ((channel == null)?"":(channel.tuner == null)) + ", target null? " + (target == null));
        }
        return new String(buf);
    }

    public String getXml(int sequence) {
        StringBuffer xmlBuf = new StringBuffer();
        try {
            xmlBuf.append("  <capture sequence=\"" + sequence + "\" " 
                    + slot.getXml() + " "
                    + channel.getXml() + " fileName=\"" // DRS 20181125 - removed extra quote
                    + target.getFileNameOrWatch() + "\" title=\"" 
                    + target.title + "\" recurring=\"" 
                    + this.isRecurring() + "\" recurrenceDays=\"" 
                    + this.recurrenceDays + "\" " 
                    + "/>\n");
        } catch (Throwable e) {
            xmlBuf.append("  <capture sequence=\"" + sequence + "\"/>\n");
            System.out.println("Bad capture data:" + (channel == null) + (channel.tuner == null) + (target == null));
        }
        return new String(xmlBuf);
    }

    public String getFileName() {
        return target.fileName;
    }

    public String getFileNameEscaped() {
        return target.fileName.replaceAll("\\'", "\\''");
    }

    public String getTitle() {
        return target.title;
    }

    public String getChannelDescription() {
        if (channel == null ) return "";
        return channel.channelDescription;
    }
    
    public String getChannelProtocol() {
        if (channel == null ) return "";
        return channel.protocol;
    }

    public Calendar getStartCalendar() {
        return (Calendar)slot.start.clone();
    }

    public String getStartTimeString() {
        Calendar startCal = getStartCalendar();
        return GCDTF.format(startCal.getTime());
    }
    
    public Calendar getEndCalendar() {
        return (Calendar)slot.end.clone();
    }

    public int getPhysicalChannel() {
        int frequency = -1;
        try {
            frequency = channel.getPhysicalChannel();
        } catch (Throwable e){
            System.out.println(new Date() + " ERROR: The physical channel frequency was not a number!  This recording will NOT work!" );
        }
        return frequency;
    }

    public int getVirtualChannel() {
        int virtualChannel = -1;
        try {
            virtualChannel = channel.getVirtualChannel();
        } catch (Throwable e){
            System.out.println(new Date() + " ERROR: The virtual channel (before the dot) was not a number!  This recording will NOT work!" );
        }
        return virtualChannel;
    }

    public int getSubchannel() {
        int subchannel = -1;
        try {
            subchannel = channel.getVirtualSubchannel();
        } catch (Throwable e){
            System.out.println(new Date() + " ERROR: The subchannel (after the dot) was not a number!  This recording will NOT work!" );
        }
        return subchannel;
    }
    
    public int getInput() {
        // for tuners that have more than one input, the channel must supply an input
        // MyHD has inputs "1" and "2"
        int input = -1;
        try {
            input = channel.getInput();
        } catch (Throwable e){
            System.out.println(new Date() + " ERROR: The channelname input was not a number!  This recording will NOT work!" );
        }
        return input;
    }
    
    public Slot getSlot() {
        return slot.clone();
    }

    public boolean isRecurring() {
        return isRecurring;
    }

    public void setRecurring(boolean isRecurring) {
        this.isRecurring = isRecurring;
    }
    
    public String getRecurrenceDays() {
    	return recurrenceDays;
    }
    
    public void setRecurrenceDays(String recurrenceDays) {
    	this.recurrenceDays = recurrenceDays;
    }
    
    public int compareTo(Object object){
        if (!(object instanceof Capture)) return -1;
        Capture otherCapture = (Capture)object;
        if (this.equals(object)){
            return 0;
        } else {
            return otherCapture.slot.compareTo(this.slot);
        }
    }

    @Override
    public int hashCode() {
        final int PRIME = 31;
        int result = 1;
        int[] results = {0,0,0,0,0,0,0,0};
        result = PRIME * result + ((channel == null) ? 0 : channel.hashCode()); results[0] = result;
        result = PRIME * result + (isRecurring ? 1231 : 1237);results[1] = result;
        result = PRIME * result + ((slot == null) ? 0 : slot.hashCode());results[2] = result;
        result = PRIME * result + ((target == null) ? 0 : target.hashCode());results[3] = result;
        if (dc) System.out.println("\t\t" + getClass().getName() + " hashCode():" + result + " " + results[0] + " " + results[1] + " " + results[2] + " " + results[3] + " " + results[4]);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            if (dc) System.out.println("\t\tThe same object. Equals is true.");
            return true;
        }
        if (obj == null) {
            if (dc) System.out.println("\t\tThe compare was against a null object");
            return false;
        }
        if (getClass() != obj.getClass()) {
            if (dc) System.out.println("\t\tNot the same class" + getClass().getName() + " ? " + obj.getClass().getName());
            return false;
        }
        final Capture other = (Capture) obj;
        if (channel == null) {
            if (other.channel != null) {
                if (dc) System.out.println("\t\tThe compare had null channel");
                return false;
            }
        } else if (!channel.equals(other.channel)){
            if (dc) System.out.println("\t\tNot same channel");
            return false;
        }
        if (isRecurring != other.isRecurring){
            if (dc) System.out.println("\t\tNot same isRecurring");
            return false;
        }
        if (slot == null) {
            if (other.slot != null) {
                if (dc) System.out.println("\t\tThe compare had null slot");
                return false;
            }
        } else if (!slot.equals(other.slot)){
            if (dc) System.out.println("\t\tNot same slot");
            return false;
        }
        if (target == null) {
            if (other.target != null) {
                if (dc) System.out.println("\t\tThe compare had null target");
                return false;
            }
        } else if (!target.equals(other.target)){
            if (dc) System.out.println("\t\tNot same target");
            return false;
        }
        if (dc) System.out.println("\t\tAll Comparisons passed.  The items are equal.");
        return true;
    }

    public abstract boolean addIcon();
    public abstract boolean removeIcon();
    public abstract String getSignalQualityData();
    public abstract int getNonDotCount();
    public abstract void interrupt();
    public abstract void configureDevice() throws Exception;
    public abstract void deConfigureDevice() throws Exception;
    public abstract void persist(boolean writeIt, boolean warn, Tuner tuner) throws CaptureScheduleException;
    public abstract int getTunerType();
    public abstract void setFileName(String fileName) throws Exception;

    public String getLastError() {
        return lastError;
    }

}
