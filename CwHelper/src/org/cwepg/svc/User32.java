package org.cwepg.svc;

import com.sun.jna.Native;
import com.sun.jna.Pointer;


public interface User32 extends W32API {

    User32 INSTANCE = (User32)Native.loadLibrary("user32", User32.class, DEFAULT_OPTIONS);
    
    /*
     * BOOL DestroyIcon(      
     *   HICON hIcon				[in] Handle to the icon to be destroyed. The icon must not be in use.
     * );
     * 
     * If the function succeeds, the return value is nonzero.
     */
    int DestroyIcon(HANDLE hIcon);

    //================================================================================

   interface WM_MOUSEMOVE extends StdCallCallback {
        /*
    	*   LRESULT CALLBACK MouseMove(      
    	*	    HWND hwnd,			[in] Handle to the window.
    	*	    UINT uMsg,			[in] Specifies the message.
    	*	    WPARAM wParam,		[in] Specifies additional message information.
    	*	    LPARAM lParam		[in] Specifies additional message information.
    	*	);
    	*	The return value is the result of the message processing and depends on the message sent.
    	*/
        boolean callback(HWND hwnd, int uMsg, W32API.WPARAM wParam, W32API.LPARAM lParam);
        boolean MouseMove(HWND hwnd, int uMsg, W32API.WPARAM wParam, W32API.LPARAM lParam);
	};
    
    //==================================================================================
    
    interface WNDENUMPROCC extends StdCallCallback {
        /** Return whether to continue enumeration. */
        boolean callback(Pointer hWnd, Pointer arg);
    };

    
    boolean EnumWindows(WNDENUMPROCC lpEnumFunc, Pointer arg);
    /*
     * BOOL EnumWindows(      
     *   WNDENUMPROC lpEnumFunc,
     *   LPARAM lParam
     * );
     */
	
    int RegisterWindowMessage(String lpString);
    //=============================================================================================
    /*
     * UINT RegisterWindowMessage(      
     *   LPCTSTR lpString
     * );
     */
    
    boolean PostMessage(HWND hWnd, int msg, WPARAM wParam, LPARAM lParam);

    //=============================================================================================
    /*
     * BOOL PostMessage(      
     *       HWND hWnd, //HWND_BROADCAST The message is posted to all top-level windows in the system, including disabled or invisible unowned windows, overlapped windows, and pop-up windows. The message is not posted to child windows.
     *       UINT Msg,  //[in] Specifies the message to be posted.
     *       WPARAM wParam, //[in] Specifies additional message-specific information.
     *       LPARAM lParam //[in] Specifies additional message-specific information.
     *   );
     */
    
    
    
    boolean SendMessage(HWND hWnd, int msg, WPARAM wParam, LPARAM lParam);

    //=============================================================================================
    /*
     * BOOL SendMessage(      
     *       HWND hWnd, //HWND_BROADCAST The message is sent to all top-level windows in the system, including disabled or invisible unowned windows, overlapped windows, and pop-up windows. The message is not posted to child windows.
     *       UINT Msg,  //[in] Specifies the message to be sent.
     *       WPARAM wParam, //[in] Specifies additional message-specific information.
     *       LPARAM lParam //[in] Specifies additional message-specific information.
     *   );
     */
    
    HWND FindWindow(String lpClassName, String lpWindowName);
    //=============================================================================================
    /*
     * HWND FindWindow(      
     *   LPCTSTR lpClassName,
     *   LPCTSTR lpWindowName
     * );
     */
}
