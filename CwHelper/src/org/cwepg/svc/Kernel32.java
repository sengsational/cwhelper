package org.cwepg.svc;

import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;

public interface Kernel32 extends W32API {
    
    Kernel32 INSTANCE = (Kernel32)Native.loadLibrary("kernel32", Kernel32.class, DEFAULT_OPTIONS);

    //=============================================================================================
    /*
     * EXECUTION_STATE WINAPI SetThreadExecutionState(
     * __in  EXECUTION_STATE esFlags
     * );
     */

    int SetThreadExecutionState(int esFlags);

    int ES_AWAYMODE_REQUIRED = 0x00000040; //  Enables away mode. This value must be specified with ES_CONTINUOUS. Away mode should be used only by media-recording and media-distribution applications that must perform critical background processing on desktop computers while the computer appears to be sleeping. See Remarks.    Windows Server 2003 and Windows XP/2000:  ES_AWAYMODE_REQUIRED is not supported.
    int ES_CONTINUOUS = 0x80000000; //Informs the system that the state being set should remain in effect until the next call that uses ES_CONTINUOUS and one of the other state flags is cleared.
    int ES_DISPLAY_REQUIRED = 0x00000002; //Forces the display to be on by resetting the display idle timer.
    int ES_SYSTEM_REQUIRED = 0x00000001; //Forces the system to be in the working state by resetting the system idle timer.
    int ES_USER_PRESENT = 0x00000004; //This value is not supported. If ES_USER_PRESENT is combined with other esFlags values, the call will fail and none of the specified states will be set.     Windows Server 2003 and Windows XP/2000:  Informs the system that a user is present and resets the display and system idle timers. ES_USER_PRESENT must be called with ES_CONTINUOUS.
    
    //=============================================================================================
    /*
     * HANDLE WINAPI CreateWaitableTimer(
     *      __in_opt  LPSECURITY_ATTRIBUTES lpTimerAttributes,
     *      __in      BOOL bManualReset,
     *      __in_opt  LPCTSTR lpTimerName
     *  );
     */
    
    HANDLE CreateWaitableTimer(SECURITY_ATTRIBUTES lpTimerAttributes, boolean bManualReset, String lpTimerName);

    public static class SECURITY_ATTRIBUTES extends Structure {
        public int nLength = size();
        public Pointer lpSecurityDescriptor;
        public boolean bInheritHandle;
    }

    /*
     * typedef struct _SECURITY_ATTRIBUTES {
     *  DWORD nLength;
     *  LPVOID lpSecurityDescriptor;
     *  BOOL bInheritHandle;
     * } SECURITY_ATTRIBUTES, 
     *   *PSECURITY_ATTRIBUTES, 
     *   *LPSECURITY_ATTRIBUTES;
     */

    //=============================================================================================
    /*
     * BOOL WINAPI SetWaitableTimer(
     *       __in      HANDLE hTimer,
     *       __in      const LARGE_INTEGER* pDueTime,
     *       __in      LONG lPeriod,
     *       __in_opt  PTIMERAPCROUTINE pfnCompletionRoutine,
     *       __in_opt  LPVOID lpArgToCompletionRoutine,
     *       __in      BOOL fResume
     *     );
     */
    
    boolean SetWaitableTimer(HANDLE htimer, FILETIME pDueTime, NativeLong lPeriod, Pointer pfnCompletionRoutine, Pointer lpArgToCompletionRoutine, boolean fResume);

    public static class FILETIME extends Structure {
        public int dwLowDateTime = size();
        public int dwHighDateTime = size();
    }
    /*
     * typedef struct _FILETIME {
     *  DWORD dwLowDateTime;
     *  DWORD dwHighDateTime;
     * } FILETIME, 
     *   *PFILETIME;
     */
    
    //=============================================================================================
    /*
     * BOOL WINAPI CancelWaitableTimer(
     *    __in  HANDLE hTimer
     *  );
     */
    //=============================================================================================
    int WaitForSingleObject(HANDLE hHandle, int dwMilliseconds);
    
    //=============================================================================================
    /*
     * BOOL WINAPI CloseHandle(
     *      __in  HANDLE hObject
     *  );
     */
    boolean CloseHandle(HANDLE hObject);

    /*
     * typedef struct _FILETIME {
     *  DWORD dwLowDateTime;
     *  DWORD dwHighDateTime;
     * } FILETIME, 
     *   *PFILETIME;
     */
    
    //=============================================================================================
    /*
     * BOOL WINAPI CancelWaitableTimer(
     *    __in  HANDLE hTimer
     *  );
     */
    
    boolean CancelWaitableTimer(HANDLE hTimer);
}
