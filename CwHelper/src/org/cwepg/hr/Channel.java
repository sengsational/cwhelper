/*
 * Created on Sep 5, 2009
 *
 */
package org.cwepg.hr;

import java.util.Date;

public class Channel {

    protected static final int TUNER_STANDARD = 0;
    protected static final int TUNER_VALUE = 1;
    protected String alphaDescription;  // Goal is to be like "KUOW"
    protected String frequency;         // The RF on air or cable
    protected String protocol;          // "qam", "8vsb", "analogCable", etc
    protected String input = "1";       // Usually 1, except MyHD has 1 or 2.
    protected String sourceId = "";     // Usually blank, but MyHD populates from registry
    protected String channelVirtual;    // Just a counting number
    protected String channelKey;        // For digitals, it's frequency dot pid.  For analog it's frequency dot.
    protected int virtualChannel;
    protected int virtualSubchannel;

    protected Tuner tuner;              // Tuner object to which this channel is associated
    protected double priority;
    protected String megahertz;
    protected String ss = "0";
    protected String seq = "0";
    protected String snq = "0";
    protected String[][] physical;
    protected String channelDescription;
    protected String pid = "0";
    protected String airCat = "";       // "Air" (over the air) or "Cat" (cable tv)
    protected boolean favorite = false; // MyHD only - populated from registry
    
    static final boolean dc = false; // dump compare
    
    public Channel(){
    }

    //used for fusion and myhd tuners
    public Channel(String description, Tuner tuner, int protocol, int signal, int rf, int virm, int virs, int rfsub, int prog, int input, int srcid) {
        this.channelDescription = virm + "." + virs + " " + description;
        this.alphaDescription = description;
        this.tuner = tuner;
        this.input = "" + input;
        this.channelVirtual = "" + virm;
        this.frequency = "" + rf;
        this.physical = new String[1][2];
        this.pid = "" + prog;
        this.priority = 0;
        if (tuner.id.equals("MYHD")){
            if (signal == 3){
                this.protocol = "analogCable";
                System.out.println(new Date() + " WARNING: Analog channel created as digital (MyHD).  Should not happen.");
            } else if (signal == 0){
                this.protocol = "8vsb";
            } else {
                this.protocol = "qam";
            }
            this.sourceId = "" + srcid;
        } else { // Fusion
            switch (protocol){
            case 1:
                this.protocol = "analogCable";
                System.out.println(new Date() + " WARNING: Analog channel created as digital (Fusion).  Should not happen.");
                break;
            case 2:
                this.protocol = "qam64";
                break;
            case 3:
                this.protocol = "qam256";
                break;
            default:
                this.protocol = "8vsb";
            }
        }
        this.physical[0][TUNER_STANDARD] = this.protocol;
        this.physical[0][TUNER_VALUE] = "" + rf;
        this.virtualChannel = virm;
        this.virtualSubchannel = virs;

        this.channelKey = rf + "." + prog + ":" + this.input + "-" + this.protocol;
    }
    
    public String getChannelKey() {
        return channelKey;
    }

    String getFullVirtualChannel() {
        return this.virtualChannel + "." + this.virtualSubchannel;
    }

    public int getPhysicalChannel() throws Throwable {
        return Integer.parseInt(frequency);
    }

    public int getVirtualChannel() {
        return virtualChannel;
    }

    public int getInput() { // for tuners that have a more than one input
        return Integer.parseInt(input);
    }

    public String getProtocol() {
        return this.protocol;
    }

    public int getVirtualSubchannel() throws Throwable {
        return virtualSubchannel;
    }
    
    public String getCleanedChannelName(){
        int colLoc = this.channelKey.indexOf(":");
        String cleanedChannelName = this.channelKey;
        if (colLoc > 0){
            cleanedChannelName = this.channelKey.substring(0,colLoc);
        }
        return cleanedChannelName;
    }

    public String getFirstRf() {
        if (physical == null || physical.length == 0){
            return this.frequency;
        } else {
            if (this.physical[0].length == 0){
                return this.frequency;
            } else {
                return this.physical[0][Channel.TUNER_VALUE];
                // TODOOLD We need to figure out how and when to decide which of these to use.  For now, we use the first one
            }
        }
    }
    
    public int getMyhdProtocolValue() throws Throwable {
        if ("8vsb".equalsIgnoreCase(this.protocol)){
            return 1;
        } else if ("qam".equalsIgnoreCase(this.protocol)){
            return 2;
        } else if ("analogCable".equalsIgnoreCase(this.protocol)){
            return 3;
        } else if ("analogAir".equalsIgnoreCase(this.protocol)){
            return 3;
        }
        throw new Exception ("The protocol value '" + this.protocol + "' is not valid.  It must be '8vsb', 'qam', 'analogCable' or 'analogAir'.");
    }
    
    public void setFavorite() {
        this.favorite = true;
    }
    
    @Override
    public int hashCode() {
        final int PRIME = 31;
        int result = 1;
        result = PRIME * result + ((channelKey == null) ? 0 : channelKey.hashCode());
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
        final Channel other = (Channel) obj;
        if (channelKey == null) {
            if (other.channelKey != null)
                return false;
        } else if (!channelKey.equalsIgnoreCase(other.channelKey)){
            if (dc) System.out.println("\t\tNot same channelKey");
            return false;
        }
        return true;
    }

    public String getHtml(){
        StringBuffer buf = new StringBuffer();
        try {
            buf.append("<td>" 
            + this.getCleanedChannelName() + "</td><td>" 
            + this.alphaDescription + "</td><td>" 
            + this.input + "</td><td>" 
            + this.channelDescription + "</td><td>" 
            + this.protocol + "</td><td>" 
            + (this.favorite?"fav":"&nbsp;") + "</td><td>" 
            + this.sourceId + "</td><td>" 
            + this.tuner.getFullName() + "</td><td>" 
            + this.tuner.getDeviceId() + "</td><td>" 
            + this.tuner.getType() + "</td><td>" 
            + this.getFullVirtualChannel() + "</td>");
        } catch (RuntimeException e) {
            System.out.println(new Date() + " ERROR: Channel.getHtml failure " + e.getMessage());
            e.printStackTrace();
        }
        return new String(buf);
    }
    
    public String getXml() {
        /*      Not Inlcuded:
                protected String channelVirtual;    // Just a counting number
                protected double priority;
                protected String megahertz;
                protected String ss = "0";
                protected String seq = "0";
                protected String snq = "0";
                protected String[][] physical;
         */
        StringBuffer xmlBuf = new StringBuffer();
        try {
            xmlBuf.append(" channelName=\"" 
                    + this.getCleanedChannelName() + "\" alphaDescription=\"" 
                    + this.alphaDescription + "\" input=\"" 
                    + this.input +"\" protocol=\"" 
                    + this.protocol + "\" favorite=\"" 
                    + this.favorite + "\" sourceId=\"" 
                    + this.sourceId + "\" tuner=\"" 
                    + this.tuner.getFullName() + "\" deviceId=\"" 
                    + this.tuner.getDeviceId() + "\" tunerType=\"" 
                    + this.tuner.getType() + "\" channelDescription=\"" 
                    + this.channelDescription + "\" channelVirtual=\""
                    + this.getFullVirtualChannel()
                    + "\"");
        } catch (Throwable e) {
            System.out.println(new Date() + " ERROR: Bad channel data.");
        }
        return new String(xmlBuf);
    }

    public String toString(){
        /* Not Included:
        String alphaDescription;
        String ss = "0";
        String seq = "0";
        String snq = "0";
        int virtualChannel;
        int virtualSubchannel;
        */
        StringBuffer buf = new StringBuffer("ChannelDigital [");
        buf.append(this.getCleanedChannelName() + " ");
        buf.append(this.alphaDescription + " ");
        buf.append(this.channelDescription + " ");
        buf.append("fav:" + this.favorite + " ");
        buf.append("sourceId:" + this.sourceId + " ");
        buf.append("pid:" + this.pid + " ");
        buf.append("freq:" + this.frequency + " ");
        buf.append("proto:" + this.protocol + " ");
        buf.append("virt:" + this.getFullVirtualChannel() + " ");
        buf.append("input:" + this.input + " ");
        if (tuner != null){
            buf.append(this.tuner.getFullName());
        } 
        buf.append(" (");
        for (int i = 0; physical != null && i < physical.length; i++) {
            buf.append(this.physical[i][TUNER_STANDARD] + " " + this.physical[i][TUNER_VALUE] + ", ");
        }
        if (physical != null && physical.length > 0){
            buf.deleteCharAt(buf.length()-1); buf.deleteCharAt(buf.length()-1);
        }
        buf.append(") " + this.priority + "]");
        return new String(buf);
    }
}
