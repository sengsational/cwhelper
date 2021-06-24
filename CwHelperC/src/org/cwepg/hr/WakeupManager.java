/*
 * Created on Apr 30, 2008
 *
 */
package org.cwepg.hr;

import java.util.Date;

import org.cwepg.svc.MyKernel32;

import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinBase.FILETIME;

public class WakeupManager {

    public static final long CONVERSION_FACTOR = -10000L;
    private static MyKernel32 kernel32 = MyKernel32.INSTANCE;
    private static final String PREVENT_MSG = String.format("0x%08x", (Kernel32.ES_CONTINUOUS | Kernel32.ES_SYSTEM_REQUIRED));
    private static final String ALLOW_MSG = String.format("0x%08x", (Kernel32.ES_CONTINUOUS));
    
    /**
     * @param aDuration in milliseconds until recording start
     * NOTE: we adjust for startup lead time, so no need to adjust for 
     * that when calling this method.
     * @return handle of Windows timer.  Returns null if inappropriate duration
     * returns null if the timer not set.
     */
    public static Kernel32.HANDLE set(long aMsDuration, String who){
        aMsDuration = aMsDuration - (CaptureManager.leadTimeSeconds * 1000);
        if (aMsDuration < 0) return null;
        long aDuration = aMsDuration * CONVERSION_FACTOR;

        // Set FILETIME structure from Java long primitive
        Kernel32.FILETIME pDueTime = new Kernel32.FILETIME();
        pDueTime.dwHighDateTime = (int)(aDuration >> 32);
        pDueTime.dwLowDateTime = (int)(0xFFFFFFFF & aDuration);

        // Make a zero value parameter
        NativeLong nl = new NativeLong();
        nl.setValue(0L);
        
        // Create Waitable Timer
        Kernel32.HANDLE hTimer = createWaitableTimer(null, true, null);//"MyWaitableTimer" + Math.random());
        
        // Set the waitable timer for aDuration 
        boolean setTimerResult = setWaitableTimer(hTimer, pDueTime, nl, null, null, true);
        System.out.println(new Date() + " set wake machine timer for " + (aMsDuration / 1000) + " seconds. " + hTimer + " " + who);
        
        if (!setTimerResult) {
            System.out.println(new Date() + " The timer has NOT been set.");
            return null;            
        }
        return hTimer;
    }

    /**
     * @param aHandle  of Windows timer
     * @return always true (!) because what can we do about it if it ain't?
     */
    public static boolean clear(Kernel32.HANDLE aHandle, String who){
        if (aHandle == null) {
            System.out.println(new Date() + " cancel wake machine timer failed.  Null handle. " + who);
            return false;
        } else {
            cancelWaitableTimer(aHandle);
            closeHandle(aHandle);
            System.out.println(new Date() + " cancelled wake machine timer. " + aHandle + " " + who);
            return true;
        }
    }
    
    /**
     * Call to prevent the machine from going into s3 or s4 (standby or hibernate)
     */
    public static void preventSleep() {
        System.out.print(new Date() + " set to prevent machine from sleeping (" + PREVENT_MSG + "). RC: ");
        int rc = setThreadExecutionState(Kernel32.ES_CONTINUOUS | Kernel32.ES_SYSTEM_REQUIRED); // prevent standby/hibernate/sleep/s3/s4
        System.out.println("(" + String.format("0x%08x", rc) + "). Thread: " + Thread.currentThread().getName());
    }

    /**
     * Call to allow the machine to go into s3 or s4 (if configured to do so)
     */
    public static void allowSleep() {
        System.out.print(new Date() + " set to allow machine to sleep        (" + ALLOW_MSG + "). RC: ");
        int rc = setThreadExecutionState(Kernel32.ES_CONTINUOUS);  // allow sleep
        System.out.println("(" + String.format("0x%08x", rc) + "). Thread: " + Thread.currentThread().getName());
    }

    /*
     * The following are used locally - the only route to the native functionality
     */
    
    private static boolean cancelWaitableTimer(Kernel32.HANDLE hTimer){
        return kernel32.CancelWaitableTimer(hTimer);
    }

    private static boolean closeHandle(Kernel32.HANDLE hObject){
        return kernel32.CloseHandle(hObject);
    }

    private static Kernel32.HANDLE createWaitableTimer(Kernel32.SECURITY_ATTRIBUTES lpTimerAttributes, boolean bManualReset, String lpTimerName){
        return kernel32.CreateWaitableTimer(lpTimerAttributes, bManualReset, lpTimerName);
    }

    private static boolean setWaitableTimer(Kernel32.HANDLE htimer, FILETIME pDueTime, NativeLong lPeriod, Pointer pfnCompletionRoutine, Pointer lpArgToCompletionRoutine, boolean fResume){
        return kernel32.SetWaitableTimer(htimer, pDueTime, lPeriod, pfnCompletionRoutine, lpArgToCompletionRoutine, fResume);
    }

    private static int setThreadExecutionState(int esFlags){
        return kernel32.SetThreadExecutionState(esFlags);
    }
    
    /*
    private static int WaitForSingleObject(Kernel32.HANDLE hHandle, int dwMilliseconds){
        return kernel32.WaitForSingleObject(hHandle, dwMilliseconds);
    }
     */

    /**
     * Test Harness Only
     */
    public static void main (String[] args) throws Exception {
        System.out.println("Running test for WakeupManager.");
        WakeupManager.preventSleep();
        Thread.sleep(1000);
        WakeupManager.preventSleep();
        Thread.sleep(1000);
        WakeupManager.allowSleep();
        Thread.sleep(1000);
        WakeupManager.allowSleep();
        Thread.sleep(1000);
        WakeupManager.allowSleep();
        WakeupManager.preventSleep();
        Thread.sleep(1000);
        WakeupManager.allowSleep();
        Thread.sleep(1000);
        WakeupManager.preventSleep();
        Thread.sleep(1000);
        WakeupManager.allowSleep();
        Thread.sleep(1000);
        WakeupManager.preventSleep();
        Thread.sleep(1000);
        WakeupManager.allowSleep();
        Thread.sleep(1000);
        
        /*
        Kernel32.HANDLE wakeupHandle = WakeupManager.set(60000, "test harness");
        System.out.println(new Date() + " wakeupHandle [" + wakeupHandle + "].  Wakeup timer 60 seconds. Sleeping 90 seconds...");
        Thread.sleep(90000);
        System.out.println(new Date() + " done sleeping.");
        WakeupManager.clear(wakeupHandle, "test harness");
        */
    }
}
