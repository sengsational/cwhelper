/*
 * Created on Aug 1, 2009
 *
 */
package org.cwepg.hr;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public abstract class LineUp {

    Map<String,Channel> channels = new TreeMap<String,Channel>();
    String signalType;
    
    public void addChannel(Channel channel) {
        Channel nullChannel = channels.get(channel.channelKey);
        if ( nullChannel == null){
            channels.put(channel.channelKey, channel);
        } 
    }
    
    public Channel getChannel(String channelName, int input, String protocol) {
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
        return null;
    }
    
    public Channel getChannelByDescription(String channelDescription){
        for (Iterator iter = channels.keySet().iterator(); iter.hasNext();) {
            Object key = iter.next();
            Channel channel = (Channel)channels.get(key);
            if (channelDescription.equals(channel.alphaDescription)) return channel;
        }
        System.out.println(new Date() + " ERROR: LineUp.getChannelByDescription failed for " + channelDescription + ".");
        return null;
    }

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

    public Channel getChannelDigital(String channelVirtual, String input) {
        for (Iterator iter = channels.keySet().iterator(); iter.hasNext();) {
            Object key = iter.next();
            Channel channelDigital = (Channel)channels.get(key);
            //System.out.println("comparing " + channelVirtual + " with " + channel.getFullVirtualChannel() + ":" + channel.input);
            if (channelVirtual.equals(channelDigital.getFullVirtualChannel() + ":" + channelDigital.input)) return channelDigital;
        }
        return null;
    }

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
    
    public Channel getChannelDigital(String channelVirtual, String rfChannel, String input, String protocol) {
        for (Iterator iter = channels.keySet().iterator(); iter.hasNext();) {
            Object key = iter.next();
            Channel channel = (Channel)channels.get(key);
            String inputValue =  channelVirtual + "-" + protocol;
            //System.out.println("comparing " + inputValue + " with " + channel.getFullVirtualChannel() + ":" + channel.input + "-" + channel.getProtocol()+ " -- and -- " +
            //        "comparing " + rfChannel + " with " + channel.getFirstRf());
            boolean match1 = inputValue.equals(channel.getFullVirtualChannel() + ":" + channel.input + "-" + channel.getProtocol());
            boolean match2 = true;
            if (rfChannel != null && !rfChannel.equals("0")){
                match2 = rfChannel.equals(channel.getFirstRf());
            }
            if (match1 && match2) return channel;
        }
        return null;
    }

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
