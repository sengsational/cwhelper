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


}
