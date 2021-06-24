/*
 * Created on Jun 15, 2008
 *
 */
package org.cwepg.hr;

import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;

public class PortManager {

    private static final double PORT_RANGE = 100;
    private static final int PORT_BASE = 55100;
    private static HashMap<Integer, Date> recentlyAssignedPorts = new HashMap<Integer,Date>();
    private static boolean debug = false;
    private static int minutesOldForRemoval = 1;
    
    public static int getNextAvailablePort(int minutesOldForRemoval) {
        if (debug) System.out.println("DEBUG: getNextAvailablePort");
        PortManager.minutesOldForRemoval = minutesOldForRemoval;
        cleanRecentPortsMap();
        return setPort(PORT_BASE);
    }
    
    // Because ports assigned in rapid succession, opening a ServerSocket is not an effective determination
    private static int setPort(int port){
        boolean ok = true;
        for (; (PortManager.PORT_BASE <= port) && (port <= (PortManager.PORT_BASE + PortManager.PORT_RANGE)); port++) {
            try {
                ServerSocket listen = new ServerSocket(port);
                listen.close();
                if (recentlyAssignedPorts.containsKey(new Integer(port))){
                    throw new Exception("Port recently used");
                }
                break;
            } catch (Exception e) {
                // that one was used, keep going
            }
        }
        if (!ok){
            System.out.println("Error finding available port.  Assigning random port.");
            port = PortManager.PORT_BASE + (int) (Math.round(Math.random() * PortManager.PORT_RANGE));
        }
        recentlyAssignedPorts.put(new Integer(port), new Date());
        return port;
    }
    
    // remove items over mintesOldForRemoval old
    private static void cleanRecentPortsMap(){
        ArrayList<Object> itemsToClear = new ArrayList<Object>();
        long now = new Date().getTime();
        for (Iterator iter = recentlyAssignedPorts.keySet().iterator(); iter.hasNext();) {
            Object key = iter.next();
            long itemTime = ((Date) recentlyAssignedPorts.get(key)).getTime();
            if ((now - itemTime) > (minutesOldForRemoval * 60 * 1000)){
                itemsToClear.add(key);
            }
        }
        if (debug) System.out.println("itemsToClear had " + itemsToClear.size() + " items.");
        for (Iterator iter = itemsToClear.iterator(); iter.hasNext();) {
            Object itemToClear = iter.next();
            if (debug) System.out.println("DEBUG: Item to clear " + itemToClear);
            recentlyAssignedPorts.remove(itemToClear);
        }
    }
    
    // test harness
    public static void main(String args[]) throws Exception {
        System.out.println(PortManager.getNextAvailablePort(1)); //0
        System.out.println(PortManager.getNextAvailablePort(1)); //1
        System.out.println(PortManager.getNextAvailablePort(1)); //2
        Thread.sleep(30000);
        System.out.println(PortManager.getNextAvailablePort(1)); //3
        System.out.println(PortManager.getNextAvailablePort(1)); //4
        System.out.println(PortManager.getNextAvailablePort(1)); //5
        Thread.sleep(31000);
        System.out.println(PortManager.getNextAvailablePort(1)); //0
        System.out.println(PortManager.getNextAvailablePort(1)); //1
        System.out.println(PortManager.getNextAvailablePort(1)); //2
        System.out.println(PortManager.getNextAvailablePort(1)); //6
    }
}

