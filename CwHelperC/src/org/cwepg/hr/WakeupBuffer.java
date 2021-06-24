/*
 * Created on Jan 6, 2021
 *
 */
package org.cwepg.hr;

import java.util.Date;


public class WakeupBuffer implements Runnable {

    public static WakeupBuffer singleton;

    public static boolean isWaiting = false;
    public static final int BUFFER_MS = 2000;

    public static long lastAllow = 1;       // initially, last allow is 1/1/1970
    public static long lastPrevent = -1;    // initially, last prevent was 'never'
    
    public static boolean debug = false;

    private static boolean testMode = false;
    
    /**
     * This class is not used because it's not legitimate to call WakeupManager.preventSleep() from anywhere except the main thread.
     * @return
     */
    public static WakeupBuffer xGetInstance(){
        if (singleton == null) {
            singleton = new WakeupBuffer();
        }
        return singleton;
    }
    
    private WakeupBuffer(){
    } 
    
    public static void setTestMode(boolean testMode) {
        WakeupBuffer.testMode = testMode;
    }

    public static void xallowSleep() {
        if (!testMode && !CaptureManager.isSleepManaged) return;
        if (lastAllow > 0 || isWaiting) {
            System.out.println(new Date() + " using  allow sleep from                     " + new Date(lastAllow));
        } else {
            WakeupBuffer buf = xGetInstance();
            isWaiting = true;
            new Thread(buf).start();
        }
        return;
    }
    
    public static void xpreventSleep() {
        if (!testMode && !CaptureManager.isSleepManaged) return;
        isWaiting = false;
        if (lastPrevent > 0) {
            System.out.println(new Date() + " using preventSleep from                     " + new Date(lastPrevent));
        } else {
            lastAllow = -1;
            lastPrevent = new Date().getTime();
            xGetInstance();
            if (debug) System.out.println(new Date() + " isWaiting: " + isWaiting);
            if (debug) System.out.println(new Date() + " >> WakeupManager.preventSleep(); ");
            WakeupManager.preventSleep();
        }
        return;
    }
    
    @Override
    public void run() {
        if (debug) System.out.println(new Date() + " WakeupBuffer run() [delay before processing allowSleep]");
        try {
            Thread.sleep(BUFFER_MS); // Blocks BUFFER_MS milliseconds
            if (isWaiting) {
                if (debug) System.out.println(new Date() + " >> WakeupManager.allowSleep();");
                lastPrevent = -1;
                lastAllow = new Date().getTime();
                WakeupManager.allowSleep();
            }
        } catch (InterruptedException e) {
            System.out.println(new Date() + " ERROR: WakeupBuffer run() has been interrupted (unexpected: we don't interrupt this short sleep).");
        } finally {
            isWaiting = false;
        }
    }
    
    public static void main(String[] args) {
        WakeupBuffer.xGetInstance();
        WakeupBuffer.setTestMode(true);
        System.out.println("-----------------------------------------------------------\n");
        try {
            
            // Main use-case...an allow occurs followed shortly thereafter by a prevent
            System.out.println("-----------------------------------------------------------");
            System.out.println("First test: Wait only 1 second after allow, then send prevent.  We should not process the allow, but process the prevent.");
            WakeupBuffer.xallowSleep();
            Thread.sleep(1000);
            WakeupBuffer.xpreventSleep();
            System.out.println("-----------------------------------------------------------------------------------------------------------------------");
            System.out.println("Above, we should see only a >>set to prevent machine from sleeping. (0x80000000)<<");
            System.out.println("-----------------------------------------------------------------------------------------------------------------------");

            System.out.println("-------------CURRENTLY PREVENTING SLEEP---------------------------- (waiting 10 seconds to continue)\n");
            Thread.sleep(10000);
            
            System.out.println("Second test: Wait 5 seconds after allow, then send prevent.");
            WakeupBuffer.xallowSleep();
            Thread.sleep(5000);
            WakeupBuffer.xpreventSleep();
            System.out.println("-----------------------------------------------------------------------------------------------------------------------");
            System.out.println("Above, we should see both a >>set to allow machine to sleep. (0x80000000)<<");
            System.out.println("                      and a >>set to prevent machine from sleeping. (0x80000001)<<");
            System.out.println("-----------------------------------------------------------------------------------------------------------------------");
            
            System.out.println("---------------------CURRENTLY PREVENTING SLEEP------------------ (waiting 10 seconds to continue)\n");
            Thread.sleep(10000);

            System.out.println("Third test: Wait 5 seconds after allow, then another allow, which should be ignored.");
            WakeupBuffer.xallowSleep();
            Thread.sleep(5000);
            WakeupBuffer.xallowSleep();
            System.out.println("-----------------------------------------------------------------------------------------------------------------------");
            System.out.println("Above, we should see a >> WakeupManager.allowSleep() set to allow machine to sleep. (0x80000000)<<");
            System.out.println("                 and a >>  Using allowSleep from (some earlier point in time)<<");
            System.out.println("-----------------------------------------------------------------------------------------------------------------------");
            
            System.out.println("-------------------CURRENTLY ALLOWING SLEEP----------------------- (waiting 10 seconds to continue)\n");
            Thread.sleep(10000);
            
            System.out.println("Fourth test: Wait 5 seconds after prevent, then another prevent.");
            WakeupBuffer.xpreventSleep();
            Thread.sleep(5000);
            WakeupBuffer.xpreventSleep();
            System.out.println("--------------------------------------------------------------------------------------------------------------------------");
            System.out.println("Above, we should see a >> WakeupManager.preventSleep() set to prevent machine from sleeping. (0x80000001)");
            System.out.println("                 and a >> Using preventSleep from (some earlier point in time)<<");
            System.out.println("--------------------------------------------------------------------------------------------------------------------------");
            
            System.out.println("----------------CURRENTLY PREVENTING SLEEP-------------------------- (waiting 10 seconds to continue)\n");
            Thread.sleep(10000);

            System.out.println("Sixth test: Wait 1/2 second after allow, then another allow that is ignored.");
            WakeupBuffer.xallowSleep();
            Thread.sleep(500);
            WakeupBuffer.xallowSleep();
            Thread.sleep(BUFFER_MS); // This is added to keep the logs in the right order, otherwise it's horribly confusing.
            System.out.println("--------------------------------------------------------------------------------------------------------------------------");
            System.out.println("Above, we should see a >> WakeupManager.allowSleep()");
            System.out.println("                 and a >>  Using allowSleep from (some earlier point in time)<<");
            System.out.println("--------------------------------------------------------------------------------------------------------------------------");
            
            System.out.println("----------------CURRENTLY ALLOWING SLEEP--------------------------(waiting 10 seconds to continue)\n");
            Thread.sleep(10000);

            System.out.println("Seventh test: preventSleep, Wait 5s, allowSleep, wait only .5, preventSleep again.");
            WakeupBuffer.xpreventSleep();
            Thread.sleep(5000);
            WakeupBuffer.xallowSleep();
            Thread.sleep(500);
            WakeupBuffer.xpreventSleep();
            System.out.println("--------------------------------------------------------------------------------------------------------------------------");
            System.out.println("Above, we should see only a single prevent sleep.");
            System.out.println("--------------------------------------------------------------------------------------------------------------------------");
            
            System.out.println("----------------CURRENTLY PREVENTING SLEEP-------------------------- (waiting 10 seconds to continue)\n");
            Thread.sleep(10000);

            System.out.println("Eighth test: Wait 5 seconds after allow, then another allow, 5 seconds after allow, then another allow, which should be ignored.");
            WakeupBuffer.xallowSleep();
            Thread.sleep(5000);
            WakeupBuffer.xallowSleep();
            Thread.sleep(1000);
            WakeupBuffer.xallowSleep();
            Thread.sleep(1000);
            WakeupBuffer.xallowSleep();
            System.out.println("-----------------------------------------------------------------------------------------------------------------------");
            System.out.println("Above, we should see a >> WakeupManager.allowSleep() set to allow machine to sleep. (0x80000000)<<");
            System.out.println("                 and several >>  Using allowSleep from (some earlier point in time)<<");
            System.out.println("-----------------------------------------------------------------------------------------------------------------------");
            
            System.out.println("-------------------CURRENTLY ALLOWING SLEEP----------------------- (waiting 10 seconds to continue)\n");
            Thread.sleep(10000);
            
            System.out.println("\n\nuse control-break");
            System.out.println("sleeping 99");
            Thread.sleep(99000);
            
        } catch (Throwable t) {
            t.printStackTrace();
        }
        

    }

}
