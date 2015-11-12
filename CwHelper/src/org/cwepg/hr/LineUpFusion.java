/*
 * Created on Aug 1, 2009
 *
 */
package org.cwepg.hr;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;

import org.cwepg.reg.RegistryHelperFusion;

public class LineUpFusion extends LineUp {
    
    public LineUpFusion(Tuner tuner){
        scan(tuner);
    }
    
    public void scan(Tuner tuner){
        this.channels.clear();
        ArrayList list = RegistryHelperFusion.getChannels(tuner);
        Iterator iter = list.iterator();
        while (iter.hasNext()) {
            Channel channel = (Channel)iter.next();
            this.addChannel(channel);
        }
        System.out.println(new Date() + " Got " + this.channels.size() + " channels from the registry.");
    }
    
    public static void main(String[] args) throws Exception {
        TunerManager tm = TunerManager.getInstance();
        tm.countTuner(Tuner.FUSION_TYPE, true);
        Collection<Channel> channels = tm.getAllChannels(false);
        for (Channel channel : channels) {
            System.out.println(channel);
        }
    }
}
