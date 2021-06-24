package org.cwepg.hr;

import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.StringTokenizer;

public class Slot {
	
	private static final String DATE_FORMAT = "MM/dd/yy HH:mm";
    private static final SimpleDateFormat DF = new SimpleDateFormat(DATE_FORMAT);
    private static final SimpleDateFormat DFS = new SimpleDateFormat("MM/dd/yy HH:mm:ss");
    private static final SimpleDateFormat DFSS = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
	public Calendar start = Calendar.getInstance();
	public Calendar end = Calendar.getInstance();
    static final boolean dc = false; // dump compare
    static private NumberFormat nf = NumberFormat.getInstance();
    
    static {
        nf.setMinimumIntegerDigits(2);
    }
	
	public Slot(String dateTime, String durationMinutes) throws SlotException {
        dateTime = cleanUpDateTime(dateTime);
		try {
			start.clear();
			start.setTime(DF.parse(dateTime));
			end.clear();
			end.setTime(DF.parse(dateTime));
            end.add(Calendar.MINUTE, Integer.parseInt(durationMinutes));
            end.add(Calendar.SECOND, -1);
		} catch (NumberFormatException e) {
			throw new SlotException("To create a slot, use integer duration.");
		} catch (ParseException e) {
			throw new SlotException("To create a slot, use date format " + DATE_FORMAT);
		}
	}

    public Slot(String dateTime, String dateTimeEnd, int ignored) throws SlotException {
        dateTime = cleanUpDateTime(dateTime);
        dateTimeEnd = cleanUpDateTime(dateTimeEnd);
        try {
            this.start.clear();
            this.start.setTime(DF.parse(dateTime));
            this.end.clear();
            this.end.setTime(DF.parse(dateTimeEnd));
            if (this.end.get(Calendar.SECOND) == 00) this.end.add(Calendar.SECOND, -1); // Slots ALWAYS end on 59 seconds
        } catch (ParseException e) {
            throw new SlotException("To create a slot, use date format " + DATE_FORMAT);
        }
    }

    private String cleanUpDateTime(String dateTime) throws SlotException {
        if (dateTime == null) throw new SlotException("ERROR: You must specify datetime and duration minutes, or datetime and datetimeend");
        if (dateTime.indexOf(" ") < 1 && dateTime.indexOf(":") != 2) throw new SlotException("To create a slot, use date format " + DATE_FORMAT);
        if (dateTime.indexOf(":") == 2){
            // presume it's "today"
            StringBuffer tempDateTime = new StringBuffer(DF.format(new Date()));
            tempDateTime.replace(9, 14, dateTime);
            dateTime = new String(tempDateTime);
        }
        return dateTime;
    }
    
	public Slot(Calendar start, String durationMinutes) throws SlotException{
        try {
        	this.start = (Calendar)start.clone();
        	this.end = (Calendar)start.clone();
            this.end.add(Calendar.MINUTE, Integer.parseInt(durationMinutes));
            this.end.add(Calendar.SECOND, -1);
        } catch (NumberFormatException e) {
            throw new SlotException("To create a slot, use integer duration.");
        }
    }

    public Slot(Calendar start, Calendar end) {
        this.start = (Calendar)start.clone();
        this.end = (Calendar)end.clone();
        if (this.end.get(Calendar.SECOND) == 00) this.end.add(Calendar.SECOND, -1); // Slots ALWAYS end on 59 seconds
    }

    public Slot(Date scheduledStart, Date scheduledEnd) {
        this.start.setTime(scheduledStart);
        this.end.setTime(scheduledEnd);
        if (this.end.get(Calendar.SECOND) == 00) this.end.add(Calendar.SECOND, -1); // Slots ALWAYS end on 59 seconds
    }

    public Slot(String date, String startTime, String durationMinutes) throws SlotException {
		this(date + " " + startTime, durationMinutes);
	}
    
    public Slot(String persistanceData) {
        try {
            StringTokenizer tok = new StringTokenizer(persistanceData, "|");
            start.setTime(DFS.parse(tok.nextToken())); 
            end.setTime(DFS.parse(tok.nextToken()));
        } catch (Exception e) {
            System.out.println("Could not parse persistance data for slot. " + e.getMessage());
        } 
    }

    public Slot advanceDays(int days){
        start.add(Calendar.HOUR, 24 * days);
        end.add(Calendar.HOUR, 24 * days);
        return this;
    }
    
    public int[] getHourMinuteSecondDuration() {
        long durationMs = end.getTimeInMillis() - start.getTimeInMillis();
        durationMs += 1000; // adjust for the 1 second shortened end time.
        int[] returnTime = new int[3];
        returnTime[0] = (int)(durationMs / 60L / 60L / 1000L);  //hours
        returnTime[1] = (int)(durationMs / 60L / 1000L - (returnTime[0] * 60)); // minutes
        returnTime[2] = (int)(durationMs / 1000L - (returnTime[0] * 60 * 60) - (returnTime[1] * 60)); // Added seconds to the result
        return returnTime;
    }
    
    public boolean conflicts(Slot slot) {
		return !(endsBefore(slot.start) || startsAfter(slot.end));
	}
    
	public boolean endsBefore(Calendar otherEvent){
		long otherEventValue = otherEvent.getTimeInMillis();
		long thisEndValue = this.end.getTimeInMillis();
		return (otherEventValue - thisEndValue) > 0;
	}
    
	public boolean startsAfter(Calendar otherEvent){
		long otherEventValue = otherEvent.getTimeInMillis();
		long thisStartValue = this.start.getTimeInMillis();
		return (thisStartValue - otherEventValue) > 0;
	}

    public boolean isInThePast() {
        return endsBefore(Calendar.getInstance());
    }
    
    public boolean isInThePastBy(long milliseconds) {
        Calendar pastCalendar = Calendar.getInstance();
        pastCalendar.add(Calendar.MILLISECOND, -(int)milliseconds);
        return endsBefore(pastCalendar);
    }

    public long getEndMs(){
    	return end.getTimeInMillis();
    }

    public long getMsUntilStart() {
        return start.getTimeInMillis() - new Date().getTime();
    }
    
    public void applyPadding(int startPad, int endPad){
        start.add(Calendar.MINUTE, -startPad);
        end.add(Calendar.MINUTE, endPad);
    }
    
    //DRS 20101024 - Added method
    public void adjustStartTimeToNowPlusLeadTimeSeconds(int fusionLeadTime) {
        Calendar nowCal = Calendar.getInstance();
        nowCal.add(Calendar.SECOND, fusionLeadTime);
        this.start.setTime(nowCal.getTime());
    }
    
    //DRS 20101031 - Added method
    public void adjustStartTimeToFutureMinute() {
        Calendar nowCal = Calendar.getInstance();
        nowCal.add(Calendar.SECOND, 5);
        if (nowCal.get(Calendar.SECOND) !=0){
            nowCal.set(Calendar.SECOND, 0);
            nowCal.add(Calendar.MINUTE, 1);
        }
        this.start.setTime(nowCal.getTime());
    }
    
    /**
     * DRS 20110713 - Added method - CapDVHS end recordings early
     * To shorten captures that have an ini file that needs to be written.
     * This way, the exe will finish, run it's ini write before the process is killed.
     * @param shortenExternalRecordingsSeconds
     */
    public void adjustEndTimeMinusSeconds(int shortenExternalRecordingsSeconds) {
        if (!isEndAdjusted()){
            Calendar endCal = (Calendar)this.end.clone();
            endCal.add(Calendar.SECOND, -shortenExternalRecordingsSeconds);
            this.end.setTime(endCal.getTime());
        }
    }

    // DRS 20181114 - New method
    public void adjustEndTimeMinutes(int minutes) {
        Calendar endCal = (Calendar)this.end.clone();
        endCal.add(Calendar.MINUTE, minutes);
        this.end.setTime(endCal.getTime());
    }
    
    /** DRS 20110731 - Rather than save the shortenExternalRecordingSeconds value
     * we will just detect an end-time change like this
     */
    private boolean isEndAdjusted(){
        return this.end.get(Calendar.SECOND) != 59;
    }
    
    // DRS 20110214 - Added method
    public ArrayList<Slot> split(int segments) throws SlotException  {
        long durationMs = end.getTimeInMillis() - start.getTimeInMillis();
        durationMs += 1000; // adjust for the 1 second shortened end time.
        int splitDurationMinutes = (int) (durationMs / (segments * 60 * 1000));
        if (splitDurationMinutes < 2) splitDurationMinutes = 2;
        ArrayList<Slot> slots = new ArrayList<Slot>();
        Calendar aCal = (Calendar)this.start.clone();
        for (int i = 0; i < segments; i++  ){
            Slot aSlot = new Slot(aCal, "" + splitDurationMinutes);
            System.out.println("aSlot: " + aSlot);
            slots.add(aSlot);
            aCal.add(Calendar.MINUTE, splitDurationMinutes);
        }
        return slots;
    }


    public int getRemainingMinutes() {
        return (int)getRemainingSeconds()/60 + 1; 
    }
    
    public int getRemainingSeconds() {
        if (isInThePast()) return 0;
        if (isInTheFuture()) return (int)((this.end.getTimeInMillis() - this.start.getTimeInMillis())/1000);
        return (int)((getEndMs() - Calendar.getInstance().getTimeInMillis()))/1000;
    }
    
    public String getPersistanceData() {
        return DFS.format(this.start.getTime()) + "|" + DFS.format(this.end.getTime());
    }

    public void shift(int randomHours) {
        this.start.add(Calendar.HOUR, randomHours);
        this.end.add(Calendar.HOUR, randomHours);
    }

    public String getXml() {
        StringBuffer buf = new StringBuffer("start=\"" + DFS.format(this.start.getTime()) + "\" end=\"" +  DFS.format(this.end.getTime()) + "\"");
        return new String(buf);
    }

    public static String getHtmlHeadings() {
        return "<th>" + 
        "start</th><th>" +
        "end</th>";
    }

    public String getHtml() {
        String result = "";
        try {
            result = "<td>" + DF.format(this.start.getTime()) + "</td><td> " + DF.format(this.end.getTime()) + "</td>";
        } catch (Exception e){
            result = "<td>error</td><td>error</td>";
        }
        return result;
    }
    
    /*
     * hashCode, equals, compareTo, toString
     * 
     */
    
    public int compareTo(Object otherSlot){
        if (otherSlot instanceof Slot){
            Slot os = (Slot)otherSlot;
            return os.start.compareTo(this.start);
        }
        return -1;
    }

    @Override
    public int hashCode() {
        final int PRIME = 31;
        int result = 1;
        result = PRIME * result + ((end == null) ? 0 : end.hashCode());
        result = PRIME * result + ((start == null) ? 0 : start.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final Slot other = (Slot) obj;
        if (end == null) {
            if (other.end != null)
                return false;
        } else if (other.end == null){
            if (end != null)
                return false;
        } else {
            if (end != null && other.end == null){
                long endMs = end.getTimeInMillis();
                long otherEndMs = other.end.getTimeInMillis();
                long differenceMs = Math.abs(endMs - otherEndMs);
                if (differenceMs < 60000){
                    if (dc) System.out.println("\t\tNot same end");
                    return false;
                }
            }
        }
        if (start == null) {
            if (other.start != null)
                return false;
        } else if (other.start == null){
            if (start != null)
                return false;
        } else {
            if (start != null && other.start == null){
                long startMs = start.getTimeInMillis();
                long otherStartMs = other.start.getTimeInMillis();
                long differenceMs = Math.abs(startMs - otherStartMs);
                if (differenceMs < 60000){
                    if (dc) System.out.println("\t\tNot same start");
                    return false;
                }
            }
        }
        return true;
    }
    
    public String toString(){
		return "Slot: start [" + DFS.format(this.start.getTime()) + "] stop [" + DFS.format(this.end.getTime()) + "]";
	}
    
    public Slot clone(){
        return new Slot(start, end);
    }
	
    /** Test Harness
     * 
     * @param args
     * @throws SlotException
     */
	public static void main (String args[]) throws SlotException {
        System.out.println("Slot main:");
        Slot aSlot = new Slot("15:30", "90");
        //System.out.println("remaining minutes: " + aSlot.getRemainingMinutes());
        //System.out.println("remaining seconds: " + aSlot.getRemainingSeconds());
        //String persistanceData = aSlot.getPersistanceData();
        //Slot aaSlot = new Slot(persistanceData);
        //System.out.println(aSlot);
        //System.out.println(aaSlot);
        
        int[] hmsDur = aSlot.getHourMinuteSecondDuration();
        System.out.println("hmsDur 0 " + hmsDur[0]);
        System.out.println("hmsDur 1 " + hmsDur[1]);
        System.out.println("hmsDur 2 " + hmsDur[2]);
        
        Slot aaSlot = aSlot.clone();
        aaSlot.adjustEndTimeSeconds(-10);

        int[] hmsDur2 = aaSlot.getHourMinuteSecondDuration();
        System.out.println("hmsDur 0 " + hmsDur2[0]);
        System.out.println("hmsDur 1 " + hmsDur2[1]);
        System.out.println("hmsDur 2 " + hmsDur2[2]);

        
        /*
         * 
        Slot bSlot = new Slot("1/6/2008 1:30", "60");
        Slot cSlot = new Slot("1/6/2008 2:05", "55");
        Slot dSlot = new Slot("1/6/2008 1:15", "10");
        Slot eSlot = new Slot("1/6/2008 0:01", "29");
        Slot fSlot = new Slot("1/6/2008 0:15", "60");
        Slot gSlot = new Slot("1/6/2008 0:30", "100");
        System.out.println("aSlot" + aSlot);
        System.out.println("bSlot" + bSlot);
        System.out.println("cSlot" + cSlot);
        System.out.println("dSlot" + dSlot);
        System.out.println("eSlot" + eSlot);
        System.out.println("fSlot" + fSlot);
        System.out.println("gSlot" + gSlot);

        System.out.println("bSlot--------");
        System.out.println("true:" + aSlot.startsBefore(bSlot.start));
        System.out.println("true:" + aSlot.endsAfter(bSlot.start));
        System.out.println("true:" + aSlot.endsBefore(bSlot.end));
        System.out.println("true:" + aSlot.conflicts(bSlot));
        System.out.println("true:" + aSlot.isInThePast());
        System.out.println("false:" + aSlot.isHappeningNow());
        System.out.println("false:" + aSlot.isInTheFuture());
        System.out.println("a/b overlap minutes:" + aSlot.getOverlapSeconds(bSlot)/60F);
        
        System.out.println("cSlot--------");
        System.out.println("true:" + aSlot.endsBefore(cSlot.start));
        System.out.println("true:" + aSlot.startsBefore(cSlot.start));
        System.out.println("false:" + aSlot.conflicts(cSlot));
        System.out.println("a/c overlap minutes:" + aSlot.getOverlapSeconds(cSlot)/60F);
        
        System.out.println("dSlot--------");
        System.out.println("true:" + aSlot.startsBefore(dSlot.start));
        System.out.println("true:" + aSlot.endsAfter(dSlot.end));
        System.out.println("true:" + aSlot.conflicts(dSlot));
        System.out.println("a/d overlap minutes:" + aSlot.getOverlapSeconds(dSlot)/60F);
        
        System.out.println("eSlot--------");
        System.out.println("true:" + aSlot.startsAfter(eSlot.end));
        System.out.println("true:" + aSlot.startsAfter(eSlot.start));
        System.out.println("false:" + aSlot.conflicts(eSlot));
        System.out.println("a/e overlap minutes:" + aSlot.getOverlapSeconds(eSlot)/60F);

        System.out.println("fSlot--------");
        System.out.println("true:" + aSlot.startsAfter(fSlot.start));
        System.out.println("true:" + aSlot.startsBefore(fSlot.end));
        System.out.println("true:" + aSlot.conflicts(fSlot));
        System.out.println("a/f overlap minutes:" + aSlot.getOverlapSeconds(fSlot)/60F);

        System.out.println("gSlot--------");
        System.out.println("true:" + aSlot.startsAfter(gSlot.start));
        System.out.println("true:" + aSlot.endsBefore(gSlot.end));
        System.out.println("true:" + aSlot.conflicts(gSlot));
        System.out.println("a/g overlap minutes:" + aSlot.getOverlapSeconds(gSlot)/60F);
        
        System.out.println("--------");
        System.out.println("false:" + aSlot.endsAfter(bSlot.end));
        System.out.println("false:" + aSlot.startsAfter(fSlot.end));
        System.out.println("false:" + aSlot.endsBefore(dSlot.end));
        System.out.println("false:" + aSlot.startsBefore(eSlot.end));
        
        System.out.println("nowSlot--------");
        Slot nowSlot = new Slot(Calendar.getInstance(),"30");
        System.out.println("false:" + nowSlot.isInThePast());
        System.out.println("true:" + nowSlot.isHappeningNow());
        System.out.println("false:" + nowSlot.isInTheFuture());
        
        Calendar futureCal = Calendar.getInstance();
        futureCal.add(Calendar.SECOND,5);
        Slot futureSlot = new Slot(futureCal, "20");
        System.out.println("false:" + futureSlot.isInThePast());
        System.out.println("false:" + futureSlot.isHappeningNow());
        System.out.println("true:" + futureSlot.isInTheFuture());
        */
	}

    /*
     * Methods used only in the test harness
     */
    
    private boolean endsAfter(Calendar otherEvent){
        return !endsBefore(otherEvent);
    }

    private boolean startsBefore(Calendar otherEvent){
        return !startsAfter(otherEvent);
    }

    @SuppressWarnings("unused")
    private int getOverlapSeconds(Slot otherSlot) throws SlotException {
        int overlap = 0;
        int overlapType = -1;
        if (!conflicts(otherSlot)) return overlap;
        if (this.startsBefore(otherSlot.start) && this.endsAfter(otherSlot.end)){
            // all of the other slot is contained in this slot
            overlap = (int)(otherSlot.end.getTimeInMillis() - otherSlot.start.getTimeInMillis())/1000;
            overlapType = 1;
        } else if (this.startsAfter(otherSlot.start) && this.endsBefore(otherSlot.end)){
            // all of this slot is contained in the other slot
            overlap = (int)(this.end.getTimeInMillis() - this.start.getTimeInMillis())/1000;
            overlapType = 2;
        } else if (this.startsAfter(otherSlot.start) && this.endsAfter(otherSlot.end)){
            // the end of the other slot runs over this start
            overlap = (int)(otherSlot.end.getTimeInMillis() - this.start.getTimeInMillis())/1000;
            overlapType = 3;
        } else if (this.startsBefore(otherSlot.start) && this.endsBefore(otherSlot.end)){
            // the other slot starts before this slot ends
            overlap = (int)(this.end.getTimeInMillis() - otherSlot.start.getTimeInMillis())/1000;
            overlapType = 4;
        } else {
            throw new SlotException ("The overlap calculator failed.\n thisSlot:" + this + "\notherSlot:" + otherSlot);
        }
        if (overlap < 0) throw new SlotException ("The overlap calculator generated a negative number.\n thisSlot:" + this + "\notherSlot:" + otherSlot + " overlap type " + overlapType);
        return overlap;
    }

    @SuppressWarnings("unused")
    private boolean isHappeningNow() {
        return startsBefore(Calendar.getInstance()) && endsAfter(Calendar.getInstance());
    }

    private boolean isInTheFuture() {
        return startsAfter(Calendar.getInstance());
    }

    public void adjustEndTimeSeconds(int i) {
        this.end.add(Calendar.SECOND, i);
    }

    //DRS 20101113 - Added method - quick capture
    public void convertToQuickEndCapture(int seconds) {
        start.setTimeInMillis(end.getTimeInMillis() + 2000);
        end.add(Calendar.SECOND, seconds);
    }

    public String getHourMinuteSecondDurationString() {
        StringBuffer buf = new StringBuffer();
        int[] hms = getHourMinuteSecondDuration();
        for (int i = 0; i < hms.length; i++) {
            buf.append(nf.format(hms[i]));
        }
        return buf.toString();
    }

    public String getStartTimeString() {
        return nf.format(start.get(Calendar.HOUR_OF_DAY)) + nf.format(start.get(Calendar.MINUTE)) + nf.format(start.get(Calendar.SECOND));
    }
    
    public String getFormattedStartTimeString() {
        return DFSS.format(start.getTime());
    }
    
    public String getFormattedStopTimeString() {
        return DFSS.format(end.getTime());
    }

}
