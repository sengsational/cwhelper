/*
 * Created on Feb 24, 2021
 *
 */
package org.cwepg.svc;

import java.util.HashMap;
import java.util.Map;

import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.win32.W32APIFunctionMapper;
import com.sun.jna.win32.W32APITypeMapper;

public interface MyKernel32 extends Kernel32 {
    /** Standard options to use the unicode version of a w32 API. */
    Map UNICODE_OPTIONS = new HashMap() {
        private static final long serialVersionUID = 8009496522546299551L;
        {
            put(OPTION_TYPE_MAPPER, W32APITypeMapper.UNICODE);
            put(OPTION_FUNCTION_MAPPER, W32APIFunctionMapper.UNICODE);
        }
    };
    /** Standard options to use the ASCII/MBCS version of a w32 API. */
    Map ASCII_OPTIONS = new HashMap() {
        private static final long serialVersionUID = 6899559321727203491L;
        {
            put(OPTION_TYPE_MAPPER, W32APITypeMapper.ASCII);
            put(OPTION_FUNCTION_MAPPER, W32APIFunctionMapper.ASCII);
        }
    };

    Map DEFAULT_OPTIONS = Boolean.getBoolean("w32.ascii") ? ASCII_OPTIONS : UNICODE_OPTIONS;
    
    MyKernel32 INSTANCE = (MyKernel32)Native.loadLibrary("kernel32", MyKernel32.class, DEFAULT_OPTIONS);

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
    
    HANDLE CreateWaitableTimer(WinBase.SECURITY_ATTRIBUTES lpTimerAttributes, boolean bManualReset, String lpTimerName);


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
    
    boolean SetWaitableTimer(HANDLE htimer, WinBase.FILETIME pDueTime, NativeLong lPeriod, Pointer pfnCompletionRoutine, Pointer lpArgToCompletionRoutine, boolean fResume);

    
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
