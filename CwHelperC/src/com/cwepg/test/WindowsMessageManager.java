/*
 * Created on Mar 1, 2021
 *
 */
package com.cwepg.test;

import static com.sun.jna.Library.OPTION_FUNCTION_MAPPER;
import static com.sun.jna.Library.OPTION_TYPE_MAPPER;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIFunctionMapper;
import com.sun.jna.win32.W32APITypeMapper;

public class WindowsMessageManager implements StdCallLibrary.StdCallCallback, Runnable {
    private static final int GWL_WNDPROC = -4;
    private static final int WS_MINIMIZE = 0x20000000;

    private static WindowsMessageManager messageManager;
    private static ExtCallLibrary extLib;
    
    private ArrayList<Integer> messageList = new ArrayList<Integer>();
    private ArrayList<CallbackObject> callbackList = new ArrayList<CallbackObject>();
    
    private WindowsMessageManager () {
    }
    
    public static WindowsMessageManager getInstance() {
        if (messageManager == null) {
            messageManager = new WindowsMessageManager();
            Thread messageManagerThread = new Thread(messageManager, "Windows Message Manager");
            messageManagerThread.setDaemon(true);
            messageManagerThread.start();
        }
        return messageManager;
    }

    @Override
    public void run() {
        Map<String, Object> options = new HashMap<String, Object>();
        options.put(OPTION_TYPE_MAPPER, W32APITypeMapper.UNICODE);
        options.put(OPTION_FUNCTION_MAPPER, W32APIFunctionMapper.UNICODE);
        extLib = Native.loadLibrary("user32", ExtCallLibrary.class, Collections.unmodifiableMap(options));
 
        WinDef.HWND window = extLib.CreateWindowEx(0, "STATIC", "ThreadS3 Window.", WS_MINIMIZE, 0, 0, 0, 0, null, null, null, null);
 
        try {
            extLib.SetWindowLongPtr(window, GWL_WNDPROC, this);
        } catch (Throwable e) {
            extLib.SetWindowLong(window, GWL_WNDPROC, this);
        }
    
        WinUser.MSG msg = new WinUser.MSG();
        while (extLib.GetMessage(msg, null, 0, 0) > 0) { // Blocks if there are no messages
            extLib.TranslateMessage(msg);
            extLib.DispatchMessage(msg);
        }
    }
    
    public void register(int windowsMessage, CallbackObject callbackObject) {
        messageList.add(windowsMessage);
        callbackList.add(callbackObject);
    }
    
    public WinDef.LRESULT callback(WinDef.HWND hwnd, int uMsg, WinDef.WPARAM wParam, WinDef.LPARAM lParam) {
        for (int i = 0; i < messageList.size(); i++){
            if (uMsg == messageList.get(i)) {
                callbackList.get(i).callback(uMsg);
            }
        }
        return extLib.DefWindowProc(hwnd, uMsg, wParam, lParam);
    }

    private interface ExtCallLibrary extends StdCallLibrary {
        WinDef.HWND CreateWindowEx(int dwExStyle, String lpClassName, String lpWindowName, int dwStyle, int x, int y, int nWidth, int nHeight, WinDef.HWND hWndParent, WinDef.HMENU hMenu, WinDef.HINSTANCE hInstance, Pointer lpParam);
        WinDef.LRESULT DefWindowProc(WinDef.HWND hWnd, int Msg, WinDef.WPARAM wParam, WinDef.LPARAM lParam);
        WinDef.LRESULT SetWindowLong(WinDef.HWND hWnd, int nIndex, StdCallLibrary.StdCallCallback dwNewLong);
        WinDef.LRESULT SetWindowLongPtr(WinDef.HWND hWnd, int nIndex, StdCallLibrary.StdCallCallback dwNewLong);
        int GetMessage(WinUser.MSG lpMsg, WinDef.HWND hWnd, int wMsgFilterMin, int wMsgFilterMax);
        boolean TranslateMessage(WinUser.MSG lpMsg);
        WinDef.LRESULT DispatchMessage(WinUser.MSG lpmsg);
    }
    
    public interface CallbackObject {
        public void callback(int uMsg);
    }
}
