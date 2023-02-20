/*
 * Created on Feb 23, 2021
 * Credit for the code goes to pablo@flexpoint.tech, twitter.com/pupeno
 * https://flexpoint.tech/2018/05/21/implementing-windows-restart-manager-in-java/
 */
package org.cwepg.svc;

import static com.sun.jna.Library.OPTION_FUNCTION_MAPPER;
import static com.sun.jna.Library.OPTION_TYPE_MAPPER;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.cwepg.hr.CaptureManager;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinNT.HRESULT;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.platform.win32.COM.COMUtils;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIFunctionMapper;
import com.sun.jna.win32.W32APITypeMapper;
 
// Inspiration can be found at https://code.briarproject.org/akwizgran/briar
public class RestartManager implements Runnable, StdCallLibrary.StdCallCallback {
    // https://autohotkey.com/docs/misc/SendMessageList.htm
    private static final int WM_CLOSE = 0x10; // https://msdn.microsoft.com/en-us/library/windows/desktop/ms632617
    private static final int WM_QUERYENDSESSION = 0x11; // https://msdn.microsoft.com/en-us/library/windows/desktop/aa376890
    private static final int WM_ENDSESSION = 0x16; // https://msdn.microsoft.com/en-us/library/windows/desktop/aa376889
    private static final int WM_TIMECHANGE = 0x1e;
 
    // https://msdn.microsoft.com/en-us/library/windows/desktop/aa376890
    // https://msdn.microsoft.com/en-us/library/windows/desktop/aa376889
    private static final int ENDSESSION_CLOSEAPP = 0x00000001;
    //private static final int ENDSESSION_CRITICAL = 0x40000000;
    //private static final int ENDSESSION_LOGOFF = 0x80000000;
 
    // https://stackoverflow.com/questions/50409858/how-do-i-return-a-boolean-as-a-windef-lresult
    private static final int WIN_FALSE = 0;
    private static final int WIN_TRUE = 1;
 
    // https://msdn.microsoft.com/en-us/library/windows/desktop/ms633591(v=vs.85).aspx
    private static final int GWL_WNDPROC = -4;
 
    // https://msdn.microsoft.com/en-us/library/windows/desktop/ms632600(v=vs.85).aspx
    private static final int WS_MINIMIZE = 0x20000000;
    
    ExtCallLibrary extLib;
    
    private static Thread runThread;
    private static WinDef.HWND window;
    private static boolean shutdownInProgress = false;
    
    @Override
    public void run() {
        runThread = Thread.currentThread();
        System.out.println(new Date()+  " RestartManager: running.");
        // Load extLib.dll usi the Unicode versions of Win32 API calls
        Map options = new HashMap();
        options.put(OPTION_TYPE_MAPPER, W32APITypeMapper.UNICODE);
        options.put(OPTION_FUNCTION_MAPPER, W32APIFunctionMapper.UNICODE);
        extLib = Native.loadLibrary("user32", ExtCallLibrary.class, Collections.unmodifiableMap(options));
 
        // Create a native window that will receive the messages.
        window = extLib.CreateWindowEx(0, "STATIC", "CwHelper Win32 Restart Manager Window.", WS_MINIMIZE, 0, 0, 0, 0, null, null, null, null);
 
        // Register the callback
        try {
            extLib.SetWindowLongPtr(window, GWL_WNDPROC, this); // Use SetWindowLongPtr if available (64-bit safe)
        } catch (Throwable e) {
            extLib.SetWindowLong(window, GWL_WNDPROC, this); // Use SetWindowLong if SetWindowLongPtr isn't available
        }
        
        // The actual event loop.
        WinUser.MSG msg = new WinUser.MSG();
        while (extLib.GetMessage(msg, null, 0, 0) > 0) { // Blocks if there are no messages
            //System.out.println(new Date() + " RestartManager: In 'while' loop with message number [" + msg.message + "] Message dump:\n[" + msg + "]");
            extLib.TranslateMessage(msg);
            extLib.DispatchMessage(msg);
            if (msg.message == WM_CLOSE) {
                break;  // Allow this thread to exit if we get a WM_CLOSE.  The OS will have previously sent a WM_ENDSESSION/ENDSESSION_CLOSEAPP.
            }
        }
        System.out.println(new Date() + " RestartManager: run() ending.");
    }
    
    public static void interrupt() { 
        if (runThread != null) {
            System.out.println(new Date() + " RestartManager interrupt.");
            runThread.interrupt(); // This doesn't work... the thread is still sitting in extLib.GetMessage()
        } else {
            System.out.println(new Date() + " RestartManager runThread was null.  No interrupt.");
        }
    }

    // Function that handles the messages according to the Restart Manager Guidelines for Applications.
    // https://msdn.microsoft.com/en-us/library/windows/desktop/aa373651
    public WinDef.LRESULT callback(WinDef.HWND hwnd, int uMsg, WinDef.WPARAM wParam, WinDef.LPARAM lParam) {
        //System.out.println(new Date() + " RestartManager: The callback() method with uMsg [" + uMsg + "] wParam [" + wParam + "] lParam [" + lParam + "]");
        if (uMsg == WM_QUERYENDSESSION && lParam.intValue() == ENDSESSION_CLOSEAPP) {
            if (CaptureManager.activeCaptures.size() != 0) {
                System.out.println(new Date() + " RestartManager: >>>> WM_QUERYSESSION with ENDSESSION_CLOSEAPP <<<<< We reply with 'false' because we have active capture(s).");
                return new WinDef.LRESULT(WIN_FALSE); // No, we have active captures.
            }
            System.out.println(new Date() + " RestartManager: >>>> WM_QUERYSESSION with ENDSESSION_CLOSEAPP <<<<< We reply with 'true' to indicate yes, we can/will but not doing it yet.");
            // registerApplicationRestart(); //DRS 20210318 - Moved this out (allow the user of the class to call after startup, if needed).
            return new WinDef.LRESULT(WIN_TRUE); // Yes, we can exit whenever you want.
        } else if (!shutdownInProgress && ((uMsg == WM_ENDSESSION && lParam.intValue() == ENDSESSION_CLOSEAPP && wParam.intValue() == WIN_TRUE) || uMsg == WM_CLOSE)) {
            shutdownInProgress = true;
            System.out.println(new Date() + " RestartManager: >>>> WM_ENDSESSION with ENDSESSION_CLOSEAPP <<<<< Issuing a shutdown now.");
            CaptureManager.shutdown("WM_ENDSESSION/ENDSESSION_CLOSEAPP");
            return new WinDef.LRESULT(WIN_FALSE); // Done... don't call extLib.DefWindowProc.
        } else if (uMsg == WM_TIMECHANGE) {
            System.out.println(new Date() + " Requesting CaptureManager interrupt to re-align.");
            CaptureManager.requestInterrupt("WM_TIMECHANGE");
        } else {
            //System.out.println(new Date() + " RestartManager: Taking no action on " + uMsg);
        }
        return this.extLib.DefWindowProc(hwnd, uMsg, wParam, lParam); // Pass the message to the default window procedure
    }
    
    public static void registerApplicationRestart() {
        try {
            HRESULT returnRestart = Kernel32.INSTANCE.RegisterApplicationRestart(null, 0);
            boolean commCheck = COMUtils.SUCCEEDED(returnRestart);
            System.out.println(new Date() + " Processed RegisterApplicationRestart RC:" + returnRestart.intValue() + " Call succeeded: " + commCheck);
        } catch (Throwable t) {
            System.out.println(new Date() + " ERROR: unable to process RegisterApplicationRestart: " + t.getMessage());
        }
    }
    
    private interface ExtCallLibrary extends StdCallLibrary {
        // https://msdn.microsoft.com/en-us/library/windows/desktop/ms632680(v=vs.85).aspx
        WinDef.HWND CreateWindowEx(int dwExStyle, String lpClassName, String lpWindowName, int dwStyle, int x, int y, int nWidth, int nHeight, WinDef.HWND hWndParent, WinDef.HMENU hMenu, WinDef.HINSTANCE hInstance, Pointer lpParam);
 
        // https://msdn.microsoft.com/en-us/library/windows/desktop/ms633572(v=vs.85).aspx
        WinDef.LRESULT DefWindowProc(WinDef.HWND hWnd, int Msg, WinDef.WPARAM wParam, WinDef.LPARAM lParam);
 
        // https://msdn.microsoft.com/en-us/library/windows/desktop/ms633591(v=vs.85).aspx
        WinDef.LRESULT SetWindowLong(WinDef.HWND hWnd, int nIndex, StdCallLibrary.StdCallCallback dwNewLong);
 
        // https://msdn.microsoft.com/en-us/library/windows/desktop/ms644898(v=vs.85).aspx
        WinDef.LRESULT SetWindowLongPtr(WinDef.HWND hWnd, int nIndex, StdCallLibrary.StdCallCallback dwNewLong);
 
        // https://msdn.microsoft.com/en-us/library/windows/desktop/ms644936(v=vs.85).aspx
        int GetMessage(WinUser.MSG lpMsg, WinDef.HWND hWnd, int wMsgFilterMin, int wMsgFilterMax);
 
        // https://msdn.microsoft.com/en-us/library/windows/desktop/ms644955(v=vs.85).aspx
        boolean TranslateMessage(WinUser.MSG lpMsg);
 
        // https://msdn.microsoft.com/en-us/library/windows/desktop/ms644934(v=vs.85).aspx
        WinDef.LRESULT DispatchMessage(WinUser.MSG lpmsg);

    }
}