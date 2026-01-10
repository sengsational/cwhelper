/*
 * Created on Feb 7, 2010
 *
 */
package org.cwepg.hr;

import java.util.Calendar;
import java.util.Date;

import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinNT.HANDLE;


public class TimedEvent {

    Kernel32.HANDLE wakeupHandle;
    Kernel32.HANDLE previousHandle;
    
    String hourToTrigger = "17";
    String minuteToTrigger = "0";
    String durationMinutes = "10";

    //inttriggerHour;
    //inttriggerMinute;
    long lastTriggerMs;

    public void initialize(String hourToSend, String minuteToSend, String duration) {
        //this.removeHardwareWakeupEvent("TimedEvent initialize() " + hourToSend +":" + minuteToSend); // DRS 20150524 Do delete of old handle until after creating new one
        previousHandle = wakeupHandle;
        if (hourToSend != null) this.hourToTrigger = hourToSend;
        if (minuteToSend != null) this.minuteToTrigger = minuteToSend;
        if (duration != null) this.durationMinutes = duration;
    }
    
    public Calendar getNextTriggerTimeCalendar() {
        Calendar nowCal = Calendar.getInstance();
        Calendar todaysSendTimeCalendar = getTodaysTriggerTimeCalendar();
        Calendar nextSendTimeCalendar = getTodaysTriggerTimeCalendar();
        //if today's send time has already passed, add 24 hours to today's to create the next send time
        if (todaysSendTimeCalendar.before(nowCal)){
            nextSendTimeCalendar.add(Calendar.DATE, 1);
        // if we just recently sent, add 24 hours to today's to create the next send time    
        } else if (Math.abs(lastTriggerMs - nextSendTimeCalendar.getTimeInMillis()) < 60000){
            nextSendTimeCalendar.add(Calendar.DATE, 1);
        }
        return nextSendTimeCalendar;
    }

    public long nextRunMinutes() {
		Calendar nextTriggerTime = getNextTriggerTimeCalendar();
		long differenceInMillis = nextTriggerTime.getTimeInMillis() - Calendar.getInstance().getTimeInMillis();
		return differenceInMillis / 60000L;
	}

    public void setOverrideHourMinute(int hour, int minute) {
    	this.hourToTrigger = "" + hour;
    	this.minuteToTrigger = "" + minute;
    }

    private Calendar getTodaysTriggerTimeCalendar() {
        Calendar todaysSendTimeCalendar = Calendar.getInstance();
        todaysSendTimeCalendar.set(Calendar.HOUR_OF_DAY, Integer.parseInt(hourToTrigger));
        todaysSendTimeCalendar.set(Calendar.MINUTE, Integer.parseInt(minuteToTrigger));
        todaysSendTimeCalendar.set(Calendar.SECOND, 0);
        return todaysSendTimeCalendar;
    }

    public boolean isDue() {
        // Don't send two back-to-back in the same 10 minutes // DRS 20210114 - increased from 5 to 10 minutes
        long nowMs = new Date().getTime();
        if ((nowMs - lastTriggerMs) < 600000){
            System.out.println(new Date() + " Returning 'false' to isDue() for daily timed event " + this.hourToTrigger + ":" + this.minuteToTrigger + " because it's been less than 10 minutes since the last one ran.");
            try {Thread.sleep(2000);} catch (Exception e){} // DRS 20210128 - NOT SURE WHY THIS HAS A 2 SECOND DELAY
            return false; 
        }

        // if the difference between now and the send time is less than 20 seconds, say it's due
        long todaysSendMs = getTodaysTriggerTimeCalendar().getTime().getTime();
        int overdueSeconds = (int)((nowMs - todaysSendMs) / 1000);
        if (overdueSeconds > -10 && overdueSeconds < 300) return true;
        return false;
    }

    public HANDLE setWakeup(String who) { // DRS 20161118 - Changed return from void to HANDLE
        // note the set method has a 90 second lead time built-in or this might be set elsewhere to something else.  We use whatever it is.
        wakeupHandle = WakeupManager.set(this.getNextTriggerTimeCalendar().getTimeInMillis() - new Date().getTime(), who);
        return wakeupHandle;
    }
    
    public void removePreviousHardwareWakeupEvent(String who) {
        if (previousHandle != null){
            WakeupManager.clear(previousHandle, who);
            previousHandle = null;
        }
    }

    public void removeHardwareWakeupEvent(String who) {
        if (wakeupHandle != null){
            WakeupManager.clear(wakeupHandle, who);
            wakeupHandle = null;
        }
    }
    
    public String getHtml() {
        StringBuffer buf = new StringBuffer();
        buf.append(
                "<tr><td>hourToTrigger:</td><td>" + hourToTrigger + "</td></tr>" + 
                "<tr><td>minuteToTrigger:</td><td>" + minuteToTrigger + "</td></tr>" + 
                "<tr><td>durationMinutes:</td><td>" + durationMinutes + "</td></tr>"); 
        return new String(buf);
    }
    
    public String getXml() {
        StringBuffer xmlBuf = new StringBuffer();
        xmlBuf.append(
                "hourToTrigger=\"" + hourToTrigger + "\" " + 
                "minuteToTrigger=\"" + minuteToTrigger + "\" " + 
                "durationMinutes=\"" + durationMinutes + "\" "); 
        return new String(xmlBuf);
    }

    public StringBuffer getProblems() {
        StringBuffer problems  = new StringBuffer();
        try {Integer.parseInt(this.hourToTrigger);} catch (Exception e){problems.append("hour to send not numeric<br>");}
        try {Integer.parseInt(this.minuteToTrigger);} catch (Exception e){problems.append("minute to send not numeric<br>");}
        try {Integer.parseInt(this.durationMinutes);} catch (Exception e){problems.append("duration minutes not numeric<br>");}
        return problems;
    }
    
    public String toString(){
        return "hour:" + hourToTrigger + " minute:" + minuteToTrigger + " duration:" + durationMinutes; 
    }
}
