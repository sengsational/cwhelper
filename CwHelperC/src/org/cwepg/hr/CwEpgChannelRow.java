/*
 * Created on Mar 15, 2011
 *
 */
package org.cwepg.hr;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class CwEpgChannelRow  {

    Map map;
    private String rawLine;
    
    public CwEpgChannelRow(Map<String, String> map){
        this.map = map;
    }

    public Map getMap() {
        return this.map;
    }
    
    public String getFormattedChannelTuner() {
        if (map == null) return "(no map)";
        String tuner = (String)map.get("Tuner");
        if (tuner == null) return "(no tuner)";
        int loc = tuner.indexOf("HR(");
        if (loc < 0) return "(not hdhr)";
        tuner = tuner.substring(loc + 3, tuner.length()-1);
        return getFormattedChannel() + "::" + tuner;
    }
    
    public String getTunerName() {
        if (map == null) return "(no map)";
        String tuner = (String)map.get("Tuner");
        if (tuner == null) return "(no tuner)";
        int loc = tuner.indexOf("HR(");
        if (loc < 0) return "(not hdhr)";
        return tuner.substring(loc + 3, tuner.length()-1);
    }

    public String getPhysicalName() {
        if (map == null) return "(no map)";
        String physical = (String)map.get("Phy");
        if (physical == null) return "(no physical)";
        return physical;
    }

    public String getProgramName() {
        if (map == null) return "(no map)";
        String program = (String)map.get("Prog");
        if (program == null) return "(no program)";
        return program;
    }

    public String toString(){
        StringBuffer buf = new StringBuffer();
        for (Iterator iter = map.keySet().iterator(); iter.hasNext();) {
            String column = (String) iter.next();
            String value = (String)map.get(column);
            buf.append("[" + column + ":(" + value + ")] ");
        }
        return buf.toString();
    }

    public String getFormattedChannel() {
        if (map == null) return "(no map)";
        return (String)map.get("Phy") + "." + (String)map.get("Sub") + ":1-8vsb";
    }
    
    public String getFormattedPhysicalChannelWithSub() {
        if (map == null) return "(no map)";
        return (String)map.get("Phy") + "." + (String)map.get("Prog"); // DRS 20190916 - Was "Sub" instead of "Prog"
    }

    public String getFormattedPhysicalChannel() {
        if (map == null) return "(no map)";
        return (String)map.get("Phy");
    }

    @Override
    public int hashCode() {
        final int PRIME = 31;
        int result = 1;
        result = PRIME * result + ((map == null) ? 0 : map.hashCode());
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
        final CwEpgChannelRow other = (CwEpgChannelRow) obj;
        if (map == null) {
            if (other.map != null)
                return false;
        } else if (!map.equals(other.map))
            return false;
        return true;
    }

    public void setRawLine(String lastLine) {
        this.rawLine = lastLine;
    }
    
    public String getRawLine() {
        return this.rawLine;
    }

    public boolean matches(String tuner, String physical, String program) {
        //tuner = tuner.trim();
        //physical = physical.trim();
        //program = program.trim();
        String objectTuner = getTunerName();//.trim();
        String objectPhysical = getPhysicalName();//.trim();
        String objectProgram = getProgramName();//.trim();
        //String compare1 = objectTuner + " " + objectPhysical + " " + objectProgram;
        //String compare2 =  tuner + " " + physical + " " + program;
        //if (compare1.contains("1010CC54-0 9 4") || compare2.contains("1010CC54-0 9 4")) {
        //    System.out.println("matches?" + objectTuner + " " + objectPhysical + " " + objectProgram + "   :   " + tuner + " " + physical + " " + program + " >>>  tuner " + objectTuner.equals(tuner) + " physical " + objectTuner.equals(physical) + " program " + objectTuner.equals(program) );
        //}
        if (objectTuner.equals(tuner) && objectPhysical.equals(physical) && objectProgram.equals(program)) {
            //System.out.println("HIT");
            return true;
        }
        return false;
    }


}
