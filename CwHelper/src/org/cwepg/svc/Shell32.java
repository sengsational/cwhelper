package org.cwepg.svc;

import com.sun.jna.Native;
import com.sun.jna.Structure;


public interface Shell32 extends W32API {
    
    Shell32 INSTANCE = (Shell32)Native.loadLibrary("shell32", Shell32.class, DEFAULT_OPTIONS);

    /*
     * HICON ExtractIcon(      
     *   HINSTANCE hInst,           [in] Handle to the instance of the application calling the function.
     *   LPCTSTR lpszExeFileName,   [in] Pointer to a null-terminated string specifying the name of an executable file, DLL, or icon file.
     *   UINT nIconIndex            [in] Specifies the zero-based index of the icon to retrieve.
     * );
     * 
     * The return value is a handle to an icon. 
     * If the file specified was not an executable file, DLL, or icon file, the return is 1. 
     * If no icons were found in the file, the return value is NULL.
     */
    
    HANDLE ExtractIcon(HANDLE hInst, String lpszExeFileName, int nIconIndex);
    
    //================================================================================
    
    /*
     * BOOL Shell_NotifyIcon(      
     *   DWORD dwMessage,			[in] A variable of type DWORD that specifies the action to be taken.
     *   							NIM_ADD 0x00000000, NIM_MODIFY 0x00000001, NIM_DELETE 0x00000002
     *   PNOTIFYICONDATA lpdata		[in] A pointer to a NOTIFYICONDATA structure.
     * );
     * 
     * Returns TRUE if successful, or FALSE otherwise.
     */
    
    boolean Shell_NotifyIcon(int dwMessage, NOTIFYICONDATA lpdata);
    
    /*
     * typedef struct _NOTIFYICONDATA{
     *   DWORD cbSize;				The size of this structure, in bytes.
     *   HWND hWnd;					A handle to the window that receives notification messages associated with an icon in the taskbar status area.
     *   								The Shell uses hWnd and uID to identify which icon to operate on when Shell_NotifyIcon is invoked.
     *   UINT uID;					The application-defined identifier of the taskbar icon.
     *   UINT uFlags;				Flags that either indicate which of the other members contain valid data.
     *   								NIF_MESSAGE 0x00000001, NIF_ICON 0x00000002, NIF_TIP 0x00000004
     *   UINT uCallbackMessage;		An application-defined message identifier. 
     *   								For example, when the pointer moves over a taskbar icon, lParam is set to WM_MOUSEMOVE. 
     *   HICON hIcon;				A handle to the icon to be added, modified, or deleted. 
     *   
     * #if (NTDDI_VERSION < NTDDI_WIN2K)
	 *  TCHAR szTip[64];
     * #endif
     * #if (NTDDI_VERSION >= NTDDI_WIN2K)
     *  TCHAR szTip[128];
     *  DWORD dwState;
     *  DWORD dwStateMask;
     *  TCHAR szInfo[256];
     *  union{
     *    UINT  uTimeout;
     *    UINT  uVersion;  // Used with Shell_NotifyIcon flag NIM_SETVERSION.
     *  } DUMMYUNIONNAME;
     *  TCHAR szInfoTitle[64];
     *  DWORD dwInfoFlags;
     *  # endif
     *  #if (NTDDI_VERSION >= NTDDI_WINXP)
     *   GUID guidItem;
     *  #endif
     *  }
     */
    
    public static class NOTIFYICONDATA extends Structure {
        public int cbSize;
        public HANDLE hWnd;
        public int uID;
        public int uFlags;
        public int uCallbackMessage;
        public HANDLE hIcon;
        public char[] szTip = new char[SZ_TIP_SIZE_XP];
        /*
        public char szTip1;
        public char szTip2;
        public char szTip3;
        public char szTip4;
        public char szTip5;
        public char szTip6;
        public char szTip7;
        public char szTip8;
        public char szTip9;
        public char szTip10;
        public char szTip11;
        public char szTip12;
        public char szTip13;
        public char szTip14;
        public char szTip15;
        public char szTip16;
        public char szTip17;
        public char szTip18;
        public char szTip19;
        public char szTip20;
        public char szTip21;
        public char szTip22;
        public char szTip23;
        public char szTip24;
        public char szTip25;
        public char szTip26;
        public char szTip27;
        public char szTip28;
        public char szTip29;
        public char szTip30;
        public char szTip31;
        public char szTip32;
        public char szTip33;
        public char szTip34;
        public char szTip35;
        public char szTip36;
        public char szTip37;
        public char szTip38;
        public char szTip39;
        public char szTip40;
        public char szTip41;
        public char szTip42;
        public char szTip43;
        public char szTip44;
        public char szTip45;
        public char szTip46;
        public char szTip47;
        public char szTip48;
        public char szTip49;
        public char szTip50;
        public char szTip51;
        public char szTip52;
        public char szTip53;
        public char szTip54;
        public char szTip55;
        public char szTip56;
        public char szTip57;
        public char szTip58;
        public char szTip59;
        public char szTip60;
        public char szTip61;
        public char szTip62;
        public char szTip63;
        public char szTip64;
        */
        /*public int dwState;
        public int dwStateMask;
        public char[] szInfo = new char[256];
        public int uTimeout_uVersion;
        public char[] szInfoTitle = new char[64];
        public int dwInfoFlags;
        public char[] guidItem = new char[32]; // mapping might be wrong here
        */
        
        public void setSzTip(String str){
        	for (int i = 0; i < str.length() && i < SZ_TIP_SIZE_XP; i++){
        		szTip[i] = str.charAt(i);
        	}
        }
        
        /*
        public void setSzTip(String str) {
        	try {
        	szTip1 = str.charAt(0);
        	szTip2 = str.charAt(1);
        	szTip3 = str.charAt(2);
        	szTip4 = str.charAt(3);
        	szTip5 = str.charAt(4);
        	szTip6 = str.charAt(5);
        	szTip7 = str.charAt(6);
        	szTip8 = str.charAt(7);
        	szTip9 = str.charAt(8);
        	szTip10 = str.charAt(9);
            szTip11 = str.charAt(10);
            szTip12 = str.charAt(11);
            szTip13 = str.charAt(12);
            szTip14 = str.charAt(13);
            szTip15 = str.charAt(14);
            szTip16 = str.charAt(15);
            szTip17 = str.charAt(16);
            szTip18 = str.charAt(17);
            szTip19 = str.charAt(18);
            szTip20 = str.charAt(19);
            szTip21 = str.charAt(20);
            szTip22 = str.charAt(21);
            szTip23 = str.charAt(22);
            szTip24 = str.charAt(23);
            szTip25 = str.charAt(24);
            szTip26 = str.charAt(25);
            szTip27 = str.charAt(26);
            szTip28 = str.charAt(27);
            szTip29 = str.charAt(28);
            szTip30 = str.charAt(29);
            szTip31 = str.charAt(30);
            szTip32 = str.charAt(31);
            szTip33 = str.charAt(32);
            szTip34 = str.charAt(33);
            szTip35 = str.charAt(34);
            szTip36 = str.charAt(35);
            szTip37 = str.charAt(36);
            szTip38 = str.charAt(37);
            szTip39 = str.charAt(38);
            szTip40 = str.charAt(39);
            szTip41 = str.charAt(40);
            szTip42 = str.charAt(41);
            szTip43 = str.charAt(42);
            szTip44 = str.charAt(43);
            szTip45 = str.charAt(44);
            szTip46 = str.charAt(45);
            szTip47 = str.charAt(46);
            szTip48 = str.charAt(47);
            szTip49 = str.charAt(48);
            szTip50 = str.charAt(49);
            szTip51 = str.charAt(50);
            szTip52 = str.charAt(51);
            szTip53 = str.charAt(52);
            szTip54 = str.charAt(53);
            szTip55 = str.charAt(54);
            szTip56 = str.charAt(55);
            szTip57 = str.charAt(56);
            szTip58 = str.charAt(57);
            szTip59 = str.charAt(58);
            szTip60 = str.charAt(59);
            szTip61 = str.charAt(60);
            szTip62 = str.charAt(61);
            szTip63 = str.charAt(62);
            szTip64 = '\u0000';
        	} catch (Throwable t){}
            
        }
        */

    }

    int SZ_TIP_SIZE = 64;
    int SZ_TIP_SIZE_XP = 128;
    int NIF_MESSAGE = 0x00000001;
    int NIF_ICON = 0x00000002;
    int NIF_TIP = 0x00000004;
    int NIM_ADD = 0x00000000;
    int NIM_MODIFY = 0x00000001;
    int NIM_DELETE = 0x00000002;
    int WM_MOUSEMOVE = 0x00000200;
    //================================================================================
}
