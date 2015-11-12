/*
 * Created on Mar 13, 2011
 *
 */
package org.cwepg.hr;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;

public class SignalSorter {

    // list sorted by strongest station first
    List<CaptureDetails> captureDetailsList = new ArrayList<CaptureDetails>();
    private int lastMoveCount;
    
    public SignalSorter(List<CaptureDetails> list) {
        // This is a list of one OTA channel pulled in by more than one HDHR tuner
        if (list == null || list.size() < 2) return;
        
        CaptureDetails toSave = (CaptureDetails)list.get(0);
        captureDetailsList.add(toSave);
        
        for(int i = 1 ; i < list.size(); i++) {
            try {
                toSave = (CaptureDetails)list.get(i);
                boolean saved = false;
                for (int j = 0; j < captureDetailsList.size(); j++){
                    if (toSave.stronger(captureDetailsList.get(j)) > 0){
                        captureDetailsList.add(j, toSave);
                        saved = true;
                        break;
                    } 
                }
                if (!saved) captureDetailsList.add(toSave);
            } catch (Exception e){
                System.out.println("Signal details invalid");
            }
        }

    }

    public TreeMap<Integer, CwEpgChannelRow> sort(TreeMap<Integer, CwEpgChannelRow> channelSortMapNew) {
        if (this.captureDetailsList == null || this.captureDetailsList.size() < 2) return channelSortMapNew;
        TreeMap<Integer, CwEpgChannelRow> mappedSlots = new TreeMap<Integer, CwEpgChannelRow>(); // the key of mapped slots is the row number in the original file
        
        // populate availableSlots.  These are slots in the cwEpg list that match.
        // also, as sad as it is, if the channel is not mapped, we must delete it from 'captureDetailsList'.
        HashSet<CaptureDetails> skip = new HashSet<CaptureDetails>();
        TreeSet<Integer> rowNumbersAdded = new TreeSet<Integer>();
        for(int i = 0, j = 0 ; i < captureDetailsList.size(); i++) {
            // Take each set of details, strongest first, and if mapped, add it to mappedSlots
            CaptureDetails details = (CaptureDetails)captureDetailsList.get(i);
            System.out.println("Strength: " + details.getStrengthSummary());
            // and find it in the map.  If we find it, add it to mappedSlots
            boolean added = false;
            for (Iterator iter = channelSortMapNew.keySet().iterator(); iter.hasNext();) {
                Integer originalRowNumber = (Integer)iter.next();
                CwEpgChannelRow cwEpgChannelRow = (CwEpgChannelRow)channelSortMapNew.get(originalRowNumber);
                if (details.getChannelTunerKey().equals(cwEpgChannelRow.getFormattedChannelTuner())){
                    rowNumbersAdded.add(originalRowNumber);
                    mappedSlots.put(new Integer(j++), cwEpgChannelRow);
                    details.setCwEpgChannelRow(cwEpgChannelRow);
                    added = true;
                }
            }
            if (!added) skip.add(details);
        }
        System.out.println("----------");
        // we now have mappedSlots with the strongest station first
        
        //ok we need to remove the skips from the captureDetailsList details.  I'm too weary to do this in a more fancy way
        for (Iterator iter = skip.iterator(); iter.hasNext();) {
            CaptureDetails cd = (CaptureDetails) iter.next();
            captureDetailsList.remove(cd);
        }
        
        // OK, we should have the same number in captureDetailsList as we do in mappedSlots
        if(mappedSlots.size() == captureDetailsList.size()){
            for (int i = 0; i < captureDetailsList.size(); i++){
                Integer aRowNumber = rowNumbersAdded.first(); // grab the first row number that has our channel in it.
                CwEpgChannelRow aChannelRow = captureDetailsList.get(i).getCwEpgChannelRow();
                CwEpgChannelRow existingChannelRow = channelSortMapNew.get(aRowNumber);
                if (!existingChannelRow.equals(aChannelRow)){
                    channelSortMapNew.put(aRowNumber, aChannelRow);  // overwrite the existing
                    this.lastMoveCount++;
                } 
                rowNumbersAdded.remove(aRowNumber);  // get rid of it now, so first() will be the next one.
            }
        } else {
            System.out.println("ERROR: not equal number of channels to swap.  number in mapped:" + mappedSlots.size() + " number in captureDetailsList:" + captureDetailsList.size() + " for ");
        }
        return channelSortMapNew;
    }
    
    private void spitOutForTest(String string, TreeMap<Integer, CwEpgChannelRow> aMap) {
        for (Iterator iter = aMap.keySet().iterator(); iter.hasNext();) {
            Integer key = (Integer)iter.next();
            CwEpgChannelRow cwEpgChannelRow = (CwEpgChannelRow)aMap.get(key);
            System.out.println(string + cwEpgChannelRow);
        }
        
    }

    public int getLastMoveCount() {
        return this.lastMoveCount;
    }

}
