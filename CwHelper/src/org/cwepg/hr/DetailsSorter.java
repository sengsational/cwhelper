/*
 * Created on Mar 13, 2011
 *
 */
package org.cwepg.hr;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;

public class DetailsSorter {
    
    TreeMap<String, CaptureDetails> channelTunerMap;
    boolean hasMoreToSort = true;
    TreeSet<String> channelsInTheList = new TreeSet<String>();
    private Iterator<String> channelIterator;
    static int max = 500;
    

    public DetailsSorter(TreeMap<String, CaptureDetails> channelTunerMap) {
        System.out.println("started with " + channelTunerMap.size());
        this.channelTunerMap = channelTunerMap;
        makeChannelList();
    }

    private void makeChannelList(){
        Iterator iter = channelTunerMap.keySet().iterator();
        channelsInTheList = new TreeSet<String>();
        while(iter.hasNext()){
            String key = (String)iter.next();
            CaptureDetails details = (CaptureDetails)channelTunerMap.get(key);
            if (details.channelKey.indexOf("-") < 0){
                continue;
            }
            channelsInTheList.add(key.substring(0,key.indexOf("::")));
        }
        System.out.println("there are " + channelsInTheList.size() + " channels in the list.");
        channelIterator = channelsInTheList.iterator();
    }
    
    public List<CaptureDetails> nextToSort() {
        ArrayList<CaptureDetails> sameChannelToCompare = new ArrayList<CaptureDetails>();

        if (channelsInTheList.size() == 0 || !channelIterator.hasNext()){
            this.hasMoreToSort = false;
            return null;
        }
        String aChannelToWork = (String)channelIterator.next();
        
        Iterator iter = channelTunerMap.keySet().iterator();
        ArrayList<Object> removeKeys = new ArrayList<Object>();
        while(iter.hasNext()){
            String key = (String)iter.next();
            if (key.indexOf(aChannelToWork) > -1){
                sameChannelToCompare.add(channelTunerMap.get(key));
                removeKeys.add(key);
            }
        }
        removeKeys(channelTunerMap, removeKeys);
        if (--max == 0) this.hasMoreToSort = false;
        if (channelTunerMap.size() == 0) this.hasMoreToSort = false;
        
        
        if (sameChannelToCompare.size() < 2){
            return new ArrayList<CaptureDetails>();
        } 
        
        return sameChannelToCompare;

    }


    
    
    static private void removeKeys(TreeMap channelTunerMap, ArrayList removeKeys) {
        Iterator iter = removeKeys.iterator();
        while(iter.hasNext()){
            channelTunerMap.remove(iter.next());
        }
    }

    public boolean hasMoreToSort() {
        return hasMoreToSort;
    }

    /**
     * @param args
     */
    public static void main(String[] args) {
        // TODO Auto-generated method stub

    }

    public void removeSameStrength() {
        ArrayList<String> toRemove = new ArrayList<String>();
        Iterator iter = channelTunerMap.keySet().iterator();
        String lastChannel = "";
        String misses = "";
        String lastKey = "";
        boolean differenceFound = false;
        HashSet<String> channelsInThisSet = new HashSet<String>();
        while(iter.hasNext()){
            String key = (String)iter.next();
            CaptureDetails details = (CaptureDetails)channelTunerMap.get(key);
            if (details.channelKey.indexOf("-") < 0){
                toRemove.add(key);
                continue;
            }
            
            channelsInThisSet.add(key);
            String thisChannel = key.substring(0,key.indexOf("::"));
            if (lastChannel.equals(thisChannel)){
                if (!misses.equals(details.tsmiss)){
                    differenceFound = true;
                }
            } else {
                if (!differenceFound){
                    for (Iterator iterator = channelsInThisSet.iterator(); iterator.hasNext();) {
                        String removeKey = (String) iterator.next();
                        toRemove.add(removeKey);
                    }
                }
                channelsInThisSet = new HashSet<String>();
                misses = details.tsmiss == null ? "":details.tsmiss;
                lastChannel = thisChannel;
                lastKey = key;
                differenceFound = false;
            }
        
        }
        System.out.println(new Date() + " Removing " + toRemove.size() + " non-required channels from sorting consideration.");
        for(int i = 0; i < toRemove.size(); i++){
            channelTunerMap.remove(toRemove.get(i));
        }
    }



}
