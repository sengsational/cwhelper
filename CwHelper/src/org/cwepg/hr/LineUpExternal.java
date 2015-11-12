/*
 * Created on Jun 19, 2011
 *
 */
package org.cwepg.hr;

import java.util.Date;
import java.util.Properties;
import java.util.Set;

public class LineUpExternal extends LineUp {

    public void scan(TunerExternal tuner, boolean useExistingFile, String signalType, int maxSeconds) {
        Properties externalProps = tuner.getChannelProperties();
        Set keySet = externalProps.keySet();
        
        for (String s : (Set<String>)keySet) {
            String channelVirtual = "0.0:1";
            String channelName = (String)externalProps.get(s) + ".0"; // 306

            try {
                int lastDashLoc = s.lastIndexOf("-");
                channelVirtual = s.substring(lastDashLoc + 1) + ":1";
            } catch (RuntimeException e) {
                //take defaults
            } 

            try {
                this.addChannel(new ChannelDigital(channelName, tuner, channelVirtual, "unk"));
            } catch (Exception e) {
                System.out.println(new Date() + " ERROR: Not able to add channel [" + s + "]  " + e.getMessage());
            }
        }
    }
}
