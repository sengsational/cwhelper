/*
 * Created on Aug 1, 2009
 *
 */
package org.cwepg.hr;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public abstract class LineUp {

    Map<String,Channel> channels = new TreeMap<String,Channel>();
    String signalType;
    boolean completedDump = true; // false; for debugging matching replacement captures
    
    //DRS 20241208 - Change signature to return boolean - Issue #49
    public boolean addChannel(Channel channel) {
        Channel nullChannel = channels.get(channel.channelKey);
        if ( nullChannel == null){
            channels.put(channel.channelKey, channel);
            return true;
        } 
        return false;
    }
    
    public void clearAllChannels() {
        channels = new TreeMap<String,Channel>();    
    }
    
    /* PRIMARY METHOD TO GET CHANNEL */
    public Channel getChannel(String channelName, int input, String protocol, String tunerName) {
        // use protocol, if specified
        if (protocol != null && !protocol.equals("")){
            String fullChannelName = channelName + ":" + input + "-" + protocol;
            for (Iterator iter = channels.keySet().iterator(); iter.hasNext();) {
                Object key = iter.next();
                Channel channel = (Channel)channels.get(key);
                if (fullChannelName.equals(channel.channelKey)) return channel;
            }
        // else match without protocol
        } else {
            String fullChannelName = channelName + ":" + input;
            for (Iterator iter = channels.keySet().iterator(); iter.hasNext();) {
                Object key = iter.next();
                Channel channel = (Channel)channels.get(key);
                int dashLoc = channel.channelKey.indexOf("-");
                if (dashLoc < 0) continue;
                String compareName = channel.channelKey.substring(0,dashLoc);
                if (fullChannelName.equals(compareName)) return channel;
            }
        }
        //System.out.println(new Date() + " No channel match found on " + tunerName +"'s lineup for channelName: " + channelName + " protocol: " + protocol + " input: " + input);
        return null;
    }
    
    /* Used in Fusion processing only */
    public Channel getChannelByDescription(String channelDescription){
        for (Iterator iter = channels.keySet().iterator(); iter.hasNext();) {
            Object key = iter.next();
            Channel channel = (Channel)channels.get(key);
            if (channelDescription.equals(channel.alphaDescription)) return channel;
        }
        System.out.println(new Date() + " ERROR: LineUp.getChannelByDescription failed for " + channelDescription + ".");
        return null;
    }

	//DRS 20200225 - Added Method (shouldn't be needed / used)
    //public Channel[] getChannelsByDescriptionNotUsed(String channelDescription){
    //    if (channelDescription == null) return null;
    //    ArrayList<Channel> foundChannels = new ArrayList<Channel>();
    //    for (Iterator<Channel> iter = channels.values().iterator(); iter.hasNext();) {
    //        Channel channelDigital = (Channel)iter.next();
    //        String matchChannel = channelDigital.alphaDescription;
    //        if (matchChannel.equals(channelDescription)) {
    //            foundChannels.add(channelDigital);
    //        }
    //    }
    //    return foundChannels.toArray(new Channel[0]);
    //}

    /* Used in Fusion processing only */
    public Channel getChannelByKey(String channelKey) {
        // channelKey is like "27.3:1-8vsb"
        for (Iterator iter = channels.keySet().iterator(); iter.hasNext();) {
            Object key = iter.next();
            Channel channel = (Channel)channels.get(key);
            if (((String)key).equals(channelKey)) return channel;
        }
        System.out.println(new Date() + " ERROR: LineUp.getChannelByKey failed for " + channelKey + ".");
        return null;
    }

    /* Used in signal strength analysis */
    public ArrayList<String> getSameRfChannelKeys(String channelKey) {
        ArrayList<String> channelKeys = new ArrayList<String>();
        String rfNumberString = channelKey.split("\\.")[0] + ".";
        for (Iterator iter = channels.keySet().iterator(); iter.hasNext();) {
            Object key = iter.next();
            if (((String)key).startsWith(rfNumberString)) {
                //System.out.println("key: " + key); //key: 32.1:1-8vsb
                channelKeys.add((String)key);
            }
        }
        if (channelKeys.size() == 0) channelKeys.add(channelKey);
        //System.out.println("Returning " + channelKeys.size() + " channels matching " + rfNumberString);
        return channelKeys;
    }
    
    /* Used in MyHD processing only */
    public Channel getChannelVirtual(String channelVirtualFull, String physicalChannel) {
        Channel returnChannel = null;
        for (Iterator iter = channels.keySet().iterator(); iter.hasNext();) {
            Object key = iter.next();
            Channel channel = (Channel)channels.get(key);
            if (channel instanceof ChannelAnalog) continue; // not looking for analog channels here
            String lookup = channel.channelVirtual + "." + channel.virtualSubchannel + ":" + channel.input;
            String lookup2 = channel.physical[0][1];
            if (channelVirtualFull.equals(lookup)) {
                if (physicalChannel.equals(lookup2)){
                    if (returnChannel != null) System.out.println(new Date() + " DUPLICATE VIRTUAL CHANNELS!");
                    returnChannel = channel;
                }
            }
        }
        if (returnChannel == null) System.out.println(new Date() + " WARNING: Line-up did not contain " + channelVirtualFull + ", " + physicalChannel);
        return returnChannel;
    }

    /* Method used in Hdhr replacement captures */
    public Channel getChannelDigitalVirtual(String channelVirtual) {
        for (Iterator iter = channels.keySet().iterator(); iter.hasNext();) {
            Object key = iter.next();
            Channel channelDigital = (Channel)channels.get(key);
            // DRS 20230129 - Correct matching for XML Cable Lineup
            String withoutVirtualProcessing = channelDigital.getFullVirtualChannel(!channelDigital.virtualHandlingRequired);
            String withVirtualProcessing = channelDigital.getFullVirtualChannel(channelDigital.virtualHandlingRequired);
            if (withoutVirtualProcessing.equals("0.0") && !withVirtualProcessing.contains(".")) withoutVirtualProcessing = withVirtualProcessing + ".0";
            if (channelVirtual.equals(withoutVirtualProcessing) || channelVirtual.equals(withVirtualProcessing)) {
                return channelDigital;
            }
        }
        if (!completedDump) {
            completedDump = true;
            System.out.println("*********************************");
            System.out.println("DEBUG: no match on channelVirtual [" + channelVirtual + "]");
            System.out.println("*********************************");
            for (Iterator iter = channels.keySet().iterator(); iter.hasNext();) {
                Object key = iter.next();
                Channel channelDigital = (Channel)channels.get(key);
                String withoutVirtualProcessing = channelDigital.getFullVirtualChannel(!channelDigital.virtualHandlingRequired);
                String withVirtualProcessing = channelDigital.getFullVirtualChannel(channelDigital.virtualHandlingRequired);
                System.out.println("DEBUG: [" + channelVirtual + "] key [" + key + "] with [" + withVirtualProcessing + "] without [" + withoutVirtualProcessing + "]");
            }
            System.out.println("*********************************");
        }
        return null;
    }

     /* Used in Fusion processing only */
    public Channel getChannel(int virtualPrefix, int pid){ //42, 3
        for (Iterator iter = channels.keySet().iterator(); iter.hasNext();) {
            Object key = iter.next();
            Channel channel = (Channel)channels.get(key);
            boolean matchedVirtualPrefix = channel.getVirtualChannel() == virtualPrefix;
            if (matchedVirtualPrefix){
                String rf = channel.getFirstRf(); // 11
                String matchString = rf + "." + pid; // ll.3
                if (matchString.equals(channel.getCleanedChannelName())){
                    return channel;
                }
            }
        }
        return null;
    }
    
    /* Called from Tuner.getChannel() MyHD processing only */
    public Channel getChannelDigital(String channelVirtual, String rfChannel, String input, String protocol) {
        for (Iterator iter = channels.keySet().iterator(); iter.hasNext();) {
            Object key = iter.next();
            Channel channel = (Channel)channels.get(key);
            String inputValue =  channelVirtual + "-" + protocol;
            //System.out.println("comparing " + inputValue + " with " + channel.getFullVirtualChannel() + ":" + channel.input + "-" + channel.getProtocol()+ " -- and -- " +
            //        "comparing " + rfChannel + " with " + channel.getFirstRf());
            boolean match1 = inputValue.equals(channel.getFullVirtualChannel(channel.virtualHandlingRequired) + ":" + channel.input + "-" + channel.getProtocol());
            boolean match2 = true;
            if (rfChannel != null && !rfChannel.equals("0")){
                match2 = rfChannel.equals(channel.getFirstRf());
            }
            if (match1 && match2) return channel;
        }
        return null;
    }

    /* Called from Tuner.getChannel() MyHD processing only */
    public Channel getChannelAnalog(String rfChannel, String input, String protocol) {
        String findChannel = rfChannel + ":" + input + "-" + protocol;
        for (Iterator iter = channels.keySet().iterator(); iter.hasNext();) {
            Object key = iter.next();
            Channel channel = (Channel)channels.get(key);
            String compareChannel = channel.getFirstRf() + ":" + channel.getInput() + "-" + channel.getProtocol();
            //System.out.println("comparing [" + findChannel + "] with [" + compareChannel + "]");
            if (findChannel.equals(compareChannel)) return channel;
        }
        return null;
    }

    public void save() {
        System.out.println(new Date() + " ERROR: lineup save not implemented.");
    }
    
    public boolean contains(Channel channelDigital) {
        if (channels.containsValue(channelDigital)) return true;
        System.out.println("ChannelDigital " + channelDigital + " does not to exist in the lineup.");
        return false;
    }

    public int size() {
        return channels.size();
    }

    public String toString(){
        StringBuffer buf = new StringBuffer();
        for (Iterator iter = channels.keySet().iterator(); iter.hasNext();) {
            Object key = iter.next();
            Channel aChannel = (Channel)channels.get(key);
            buf.append(aChannel.toString() + "\n");
        }
        return new String(buf);
    }

}
