/*
 * Created on Aug 1, 2009
 *
 */
package org.cwepg.hr;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;

import org.cwepg.reg.RegistryHelperMyhd;

public class LineUpMyhd extends LineUp {
    
    public LineUpMyhd(Tuner tuner){
        scan(tuner);
    }
    
    public void scan(Tuner tuner){
        this.channels.clear();
        ArrayList list = RegistryHelperMyhd.getChannels(tuner);
        Iterator iter = list.iterator();
        while (iter.hasNext()) {
            Channel channelDigital = (Channel)iter.next();
            this.addChannel(channelDigital);
        }
        //System.out.println(new Date() + " Got " + this.channels.size() + " channels from the registry.");
    }
    
    public static void main(String[] args) throws Exception {
        Tuner tuner = new TunerMyhd(true);
        LineUpMyhd lineUp = new LineUpMyhd(tuner);
        System.out.println(lineUp);
        
        boolean testDuplicateVirtuals = false;
        if (testDuplicateVirtuals){
            System.out.println(lineUp.getChannelVirtual("1.1:2", "115"));
            System.out.println(lineUp.getChannelVirtual("1.1:2", "114"));
        }
        
    }
}
