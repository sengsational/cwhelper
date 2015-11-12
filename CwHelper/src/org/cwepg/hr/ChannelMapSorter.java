/*
 * Created on Mar 13, 2011
 *
 */
package org.cwepg.hr;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class ChannelMapSorter {
    
    TreeMap<String, CaptureDetails> channelTunerMap;
    TreeMap<Integer, CwEpgChannelRow> channelSortMap;
    List<String> headers; 
    int moveCount = 0;
    String sortingLog = "";

    public ChannelMapSorter(TreeMap<String, CaptureDetails> channelTunerMap, TreeMap<Integer, CwEpgChannelRow> channelSortMap, List<String> headers) {
        this.channelTunerMap = channelTunerMap;
        this.channelSortMap = channelSortMap;
        this.headers = headers;
    }

    public TreeMap<Integer, CwEpgChannelRow> getSorted() {
        TreeMap channelSortMapNew = (TreeMap)channelSortMap.clone();
        DetailsSorter detailsSorter = new DetailsSorter(channelTunerMap);
        while (detailsSorter.hasMoreToSort()){
            SignalSorter scs = new SignalSorter((List<CaptureDetails>)detailsSorter.nextToSort());
            channelSortMapNew = scs.sort(channelSortMapNew);
            this.moveCount += scs.getLastMoveCount();
        }
        return channelSortMapNew;
    }

    public int getMoveCount() {
        return moveCount;
    }

    public String getSortingLog() {
        return sortingLog;
    }
    
    public static String simulate() throws Exception {
        String cwepgPath = "C:\\my\\dev\\hdhr\\testchamber\\";
        String hdhrPath = "C:\\Program Files\\Silicondust\\HDHomeRun\\";
        String dataPath = "C:\\my\\dev\\hdhr\\testchamber\\";
        CaptureManager.getInstance(cwepgPath, hdhrPath, dataPath);
        
        CaptureDataManager captureDataManager = CaptureDataManager.getInstance();
        TreeMap<String, CaptureDetails> channelTunerMap = captureDataManager.getSignalStrengthByChannelAndTuner();
        if (channelTunerMap.size() == 0) return "No HDHR signal strength history found";
        
        // Get channel sort from cw_epg file
        TreeMap<Integer, CwEpgChannelRow> channelSortMap = new TreeMap<Integer,CwEpgChannelRow>();
        List<String>headers = null;
        try {
            CSVReader reader = new CSVReader(new BufferedReader(new FileReader(CaptureManager.dataPath + File.separator + "channel_maps.txt"))); 
            headers = reader.readHeader();
            int i = 0;
            Map<String, String> map = null;
            while ((map = reader.readValues()) != null) {
                channelSortMap.put(new Integer(i++), new CwEpgChannelRow(map));
            }
        } catch (Exception e){
            return "Failed to sort. Failed to read sorted channels.  " + e.getMessage();
        }
        if (channelSortMap.size() == 0) return "No CW_EPG channel map was found";

        // Make the sorter object and use it
        ChannelMapSorter channelMapSorter = new ChannelMapSorter(channelTunerMap, channelSortMap, headers);
        TreeMap<Integer, CwEpgChannelRow> channelsSorted = channelMapSorter.getSorted();
        if (channelSortMap.size() != channelsSorted.size()) return "Error in sorting.  No changes made.";
        
        // Write out the sorted map
        CSVWriter writer = null;
        try {
            writer = new CSVWriter(new BufferedWriter(new FileWriter(CaptureManager.dataPath + File.separator + "channel_maps.txt")));
            writer.setColumns(headers);
            writer.writeHeader();
            for (Iterator iter = channelsSorted.keySet().iterator(); iter.hasNext();) {
                CwEpgChannelRow aRow = channelsSorted.get(iter.next());
                writer.write(aRow.getMap());
            }
            writer.close();
        } catch (Exception e){
            String message = new Date() + " ERROR.  Failed in critial operation.  Check channel_maps.txt.  Don't ignore this message! " + e.getMessage();
            try {if (writer != null) writer.close();} catch (Exception ee){}
            System.out.println(message);
            System.err.println(message);
            e.printStackTrace();
            return  message;
        }
        
        return channelMapSorter.getMoveCount() + " channels changed location. " + channelMapSorter.getSortingLog();
    }

    /**
     * @param args
     */
    public static void main(String[] args) throws Exception {
        System.out.println(ChannelMapSorter.simulate());
    }


}
