/*
 * Created on Sep 5, 2009
 *
 */
package org.cwepg.hr;

public class ChannelAnalog extends Channel {

    public ChannelAnalog(String description, Tuner tuner, String protocol, int rf, int virm, String airCat, int input) {
        this.alphaDescription = description;
        this.channelDescription = virm + ". " + description; 
        this.tuner = tuner;
        this.protocol = protocol;
        this.frequency = "" + rf;
        this.channelVirtual = "" + virm;
        this.channelKey = rf + ".:" + input + "-" + protocol;
        this.airCat = airCat;
        this.input = "" + input;
    }
    
    public String toString(){
        StringBuffer buf = new StringBuffer("ChannelAnalog [");
        int colLoc = this.channelKey.indexOf(":");
        String cleanedChannelName = this.channelKey;
        if (colLoc > 0){
            cleanedChannelName = this.channelKey.substring(0,colLoc);
        }
        buf.append(cleanedChannelName + " ");
        buf.append(this.channelDescription + " ");
        buf.append("freq:" + this.frequency + " ");
        buf.append("proto:" + this.protocol + " ");
        buf.append("input:" + this.input + " ");
        if (tuner != null){
            buf.append(this.tuner.getFullName());
        } 
        buf.append(" (");
        buf.append(") " + this.priority + "]");
        return new String(buf);
    }
}
