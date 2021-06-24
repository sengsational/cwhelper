package org.cwepg.reg;

import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.TreeSet;

import com.sun.jna.platform.win32.WinBase.FILETIME;
import com.sun.jna.ptr.IntByReference;


/**
 * Methods for accessing the Windows Registry. Only String and DWORD values supported at the moment.
 */
public class Registry {
  private static final HashMap rootKeyMap = new HashMap();
  static {
	  rootKeyMap.put("HKEY_CLASSES_ROOT", new Integer(WINREG.HKEY_CLASSES_ROOT));
	  rootKeyMap.put("HKEY_CURRENT_USER", new Integer(WINREG.HKEY_CURRENT_USER));
	  rootKeyMap.put("HKEY_LOCAL_MACHINE", new Integer(WINREG.HKEY_LOCAL_MACHINE));
	  rootKeyMap.put("HKEY_USERS", new Integer(WINREG.HKEY_USERS));
  }
  
  /**
   * Testing.
   *
   * @param args arguments
   * @throws java.lang.Exception on error
   */
  public static void main(String[] args) throws Exception {
	  String result = Registry.getStringValue("HKEY_LOCAL_MACHINE", "SYSTEM\\CurrentControlSet\\Control\\ProductOptions", "ProductType");
      if (result == null) throw new Exception("The value was not found in the registry");
	  System.out.println("result [" + result + "]");
  }

  /**
   * Gets one of the root keys.
   *
   * @param key key type
   * @return root key
   */
  private static int getRegistryRootKey(String key) {
    Advapi32 advapi32;
    IntByReference pHandle;
    int handle = 0;
    
    advapi32 = Advapi32.INSTANCE;
    pHandle = new IntByReference();
    
    if(advapi32.RegOpenKeyEx(((Integer)rootKeyMap.get(key)).intValue(), null, 0, 0, pHandle) == WINERROR.ERROR_SUCCESS) {
      handle = pHandle.getValue();
    }
    return(handle);
  }
  
  /**
   * Opens a key.
   *
   * @param rootKey root key
   * @param subKeyName name of the key
   * @param access access mode
   * @return handle to the key or 0
   */
  private static int openKey(String rootKey, String subKeyName, int access) {
    Advapi32 advapi32;
    IntByReference pHandle;
    int rootKeyHandle;
    
    advapi32 = Advapi32.INSTANCE;
    rootKeyHandle = getRegistryRootKey(rootKey);
    pHandle = new IntByReference();
    
    if(advapi32.RegOpenKeyEx(rootKeyHandle, subKeyName, 0, access, pHandle) == WINERROR.ERROR_SUCCESS) {
      return(pHandle.getValue());
      
    } else {
      return(0);
    }
  }
  
  /**
   * Converts a Windows buffer to a Java String.
   *
   * @param buf buffer
   * @throws java.io.UnsupportedEncodingException on error
   * @return String
   */
  private static String convertBufferToString(byte[] buf) throws UnsupportedEncodingException {
    return(new String(buf, 0, buf.length - 2, "UTF-16LE"));
  }
  
  /**
   * Converts a Windows buffer to an int.
   *
   * @param buf buffer
   * @return int
   */
  private static int convertBufferToInt(byte[] buf) {
   return(((int)(buf[0] & 0xff)) + (((int)(buf[1] & 0xff)) << 8) + (((int)(buf[2] & 0xff)) << 16) + (((int)(buf[3] & 0xff)) << 24)); 
  }
  
  /**
   * Read a String value.
   *
   * @param rootKey root key
   * @param subKeyName key name
   * @param name value name
   * @throws java.io.UnsupportedEncodingException on error
   * @return String or null
   */
  public static String getStringValue(String rootKey, String subKeyName, String name) throws UnsupportedEncodingException {
    Advapi32 advapi32;
    IntByReference pType, lpcbData;
    byte[] lpData = new byte[1];
    int handle = 0;
    String ret = null;
    
    advapi32 = Advapi32.INSTANCE;
    pType = new IntByReference();
    lpcbData = new IntByReference();
    handle = openKey(rootKey, subKeyName, WINNT.KEY_READ);
    
    if(handle != 0) {
      
      if(advapi32.RegQueryValueEx(handle, name, null, pType, lpData, lpcbData) == WINERROR.ERROR_MORE_DATA) {
        lpData = new byte[lpcbData.getValue()];

        if(advapi32.RegQueryValueEx(handle, name, null, pType, lpData, lpcbData) == WINERROR.ERROR_SUCCESS) {
          ret = convertBufferToString(lpData);
        }
      }
      advapi32.RegCloseKey(handle);
    }
    return(ret);
  }

  /**
   * Read an int value.
   * 
   * 
   * @return int or 0
   * @param rootKey root key
   * @param subKeyName key name
   * @param name value name
   */
  public static int getIntValue(String rootKey, String subKeyName, String name) {
    Advapi32 advapi32;
    IntByReference pType, lpcbData;
    byte[] lpData = new byte[1];
    int handle = 0;
    int ret = 0;
    
    advapi32 = Advapi32.INSTANCE;
    pType = new IntByReference();
    lpcbData = new IntByReference();
    handle = openKey(rootKey, subKeyName, WINNT.KEY_READ);
    
    if(handle != 0) {
      
      if(advapi32.RegQueryValueEx(handle, name, null, pType, lpData, lpcbData) == WINERROR.ERROR_MORE_DATA) {
        lpData = new byte[lpcbData.getValue()];

        if(advapi32.RegQueryValueEx(handle, name, null, pType, lpData, lpcbData) == WINERROR.ERROR_SUCCESS) {
          ret = convertBufferToInt(lpData);
        }
      }
      advapi32.RegCloseKey(handle);
    }
    return(ret);
  }
  
  /**
   * Delete a value.
   *
   * @param rootKey root key
   * @param subKeyName key name
   * @param name value name
   * @return true on success
   */
  public static boolean deleteValue(String rootKey, String subKeyName, String name) {
    Advapi32 advapi32;
    int handle;
    boolean ret = true;
    
    advapi32 = Advapi32.INSTANCE;
    
    handle = openKey(rootKey, subKeyName, WINNT.KEY_READ | WINNT.KEY_WRITE);
    
    if(handle != 0) {
      if(advapi32.RegDeleteValue(handle, name) == WINERROR.ERROR_SUCCESS) {
        ret = true;
      }
      advapi32.RegCloseKey(handle);
    }
    return(ret);
  }
  
  /**
   * Writes a String value.
   *
   * @param rootKey root key
   * @param subKeyName key name
   * @param name value name
   * @param value value
   * @throws java.io.UnsupportedEncodingException on error
   * @return true on success
   */
  public static boolean setStringValue(String rootKey, String subKeyName, String name, String value) throws UnsupportedEncodingException {
    Advapi32 advapi32;
    int handle;
    byte[] data;
    boolean ret = false;
    //data = Arrays.copyOf(value.getBytes("UTF-16LE"), value.length() * 2 + 2);

    byte[] fromArray = value.getBytes("UTF-16LE");
    data = new byte[value.length() * 2 + 2];
    for (int i = 0; i < fromArray.length; i++) {
        data[i] = fromArray[i];
    }
    
    advapi32 = Advapi32.INSTANCE;
    handle = openKey(rootKey, subKeyName, WINNT.KEY_READ | WINNT.KEY_WRITE);
    
    if(handle != 0) {
      if(advapi32.RegSetValueEx(handle, name, 0, WINNT.REG_SZ, data, data.length) == WINERROR.ERROR_SUCCESS) {
        ret = true;
      }
      advapi32.RegCloseKey(handle);
    }
    return(ret);
  }
  
  /**
   * Writes an int value.
   * 
   * 
   * @return true on success
   * @param rootKey root key
   * @param subKeyName key name
   * @param name value name
   * @param value value
   */
  public static boolean setIntValue(String rootKey, String subKeyName, String name, int value) {
    Advapi32 advapi32;
    int handle;
    byte[] data;
    boolean ret = false;
    
    data = new byte[4];
    data[0] = (byte)(value & 0xff);
    data[1] = (byte)((value >> 8) & 0xff);
    data[2] = (byte)((value >> 16) & 0xff);
    data[3] = (byte)((value >> 24) & 0xff);
    advapi32 = Advapi32.INSTANCE;
    handle = openKey(rootKey, subKeyName, WINNT.KEY_READ | WINNT.KEY_WRITE);
    
    if(handle != 0) {
      
      if(advapi32.RegSetValueEx(handle, name, 0, WINNT.REG_DWORD, data, data.length) == WINERROR.ERROR_SUCCESS) {
        ret = true;
      }
      advapi32.RegCloseKey(handle);
    }
    return(ret);
  }
  /**
   * DRS 20090809 - Added Method 
   * 
   * Writes a binary value from byte array.
   * 
   * 
   * @return true on success
   * @param rootKey root key
   * @param subKeyName key name
   * @param name value name
   * @param data byte array
   */

  public static boolean setBinaryValue(String rootKey, String subKeyName, String name, byte[] data) {
    Advapi32 advapi32;
    int handle;
    boolean ret = false;
    
    advapi32 = Advapi32.INSTANCE;
    handle = openKey(rootKey, subKeyName, WINNT.KEY_READ | WINNT.KEY_WRITE);
    
    if(handle != 0) {
      
      if(advapi32.RegSetValueEx(handle, name, 0, WINNT.REG_BINARY, data, data.length) == WINERROR.ERROR_SUCCESS) {
        ret = true;
      }
      advapi32.RegCloseKey(handle);
    }
    return(ret);
  }
  /**
   * Check for existence of a value.
   *
   * @param rootKey root key
   * @param subKeyName key name
   * @param name value name
   * @return true if exists
   */
  public static boolean valueExists(String rootKey, String subKeyName, String name) {
    Advapi32 advapi32;
    IntByReference pType, lpcbData;
    byte[] lpData = new byte[1];
    int handle = 0;
    boolean ret = false;
    
    advapi32 = Advapi32.INSTANCE;
    pType = new IntByReference();
    lpcbData = new IntByReference();
    handle = openKey(rootKey, subKeyName, WINNT.KEY_READ);
    
    if(handle != 0) {
      
      if(advapi32.RegQueryValueEx(handle, name, null, pType, lpData, lpcbData) != WINERROR.ERROR_FILE_NOT_FOUND) {
        ret = true;
        
      } else {
        ret = false;
      }
      advapi32.RegCloseKey(handle);
    }
    return(ret);
  }
  
  /**
   * Create a new key.
   *
   * @param rootKey root key
   * @param parent name of parent key
   * @param name key name
   * @return true on success
   */
  public static boolean createKey(String rootKey, String parent, String name) {
    Advapi32 advapi32;
    IntByReference hkResult, dwDisposition;
    int handle = 0;
    boolean ret = false;
    
    advapi32 = Advapi32.INSTANCE;
    hkResult = new IntByReference();
    dwDisposition = new IntByReference();
    handle = openKey(rootKey, parent, WINNT.KEY_READ);
    
    if(handle != 0) {
      
      if(advapi32.RegCreateKeyEx(handle, name, 0, null, WINNT.REG_OPTION_NON_VOLATILE, WINNT.KEY_READ, null,
         hkResult, dwDisposition) == WINERROR.ERROR_SUCCESS) {
        ret = true;
        advapi32.RegCloseKey(hkResult.getValue());
        
      } else {
        ret = false;
      }
      advapi32.RegCloseKey(handle);
    }
    return(ret);
  }
  
  /**
   * Delete a key.
   *
   * @param rootKey root key
   * @param parent name of parent key
   * @param name key name
   * @return true on success
   */
  public static boolean deleteKey(String rootKey, String parent, String name) {
    Advapi32 advapi32;
    int handle = 0;
    boolean ret = false;
    
    advapi32 = Advapi32.INSTANCE;
    handle = openKey(rootKey, parent, WINNT.KEY_READ);
    
    if(handle != 0) {
      
      if(advapi32.RegDeleteKey(handle, name) == WINERROR.ERROR_SUCCESS) {
        ret = true;
        
      } else {
        ret = false;
      }
      advapi32.RegCloseKey(handle);
    }
    return(ret);
  }
  
  /**
   * Get all sub keys of a key.
   *
   * @param rootKey root key
   * @param parent key name
   * @return array with all sub key names
   */
  public static String[] getSubKeys(String rootKey, String parent) {
    Advapi32 advapi32;
    int handle = 0, dwIndex;
    char[] lpName;
    IntByReference lpcName;
    FILETIME lpftLastWriteTime;
    TreeSet subKeys = new TreeSet();
    
    advapi32 = Advapi32.INSTANCE;
    handle = openKey(rootKey, parent, WINNT.KEY_READ);
    lpName = new char[256];
    lpcName = new IntByReference(256);
    lpftLastWriteTime = new FILETIME();
    
    if(handle != 0) {
      dwIndex = 0;
      
      while(advapi32.RegEnumKeyEx(handle, dwIndex, lpName, lpcName, null,
            null, null, lpftLastWriteTime) == WINERROR.ERROR_SUCCESS) {
        subKeys.add(new String(lpName, 0, lpcName.getValue()));
        lpcName.setValue(256);
        dwIndex++;
      }
      advapi32.RegCloseKey(handle);
    }
    
    return (String[])subKeys.toArray(new String[]{});
  }
  
  /**
   * Get all values under a key.
   *
   * @param rootKey root key
   * @param key jey name
   * @throws java.io.UnsupportedEncodingException on error
   * @return TreeMap with name and value pairs
   */
  public static TreeMap getValues(String rootKey, String key) throws UnsupportedEncodingException {
    Advapi32 advapi32;
    int handle = 0, dwIndex, result = 0;
    char[] lpValueName;
    byte[] lpData;
    IntByReference lpcchValueName, lpType, lpcbData;
    String name;
    TreeMap values = new TreeMap(String.CASE_INSENSITIVE_ORDER);
    
    advapi32 = Advapi32.INSTANCE;
    handle = openKey(rootKey, key, WINNT.KEY_READ);
    lpValueName = new char[16384];
    lpcchValueName = new IntByReference(16384);
    lpType = new IntByReference();
    lpData = new byte[1];
    lpcbData = new IntByReference();
    
    if(handle != 0) {
      dwIndex = 0;
      
      do {
        lpcbData.setValue(0);
        result = advapi32.RegEnumValue(handle, dwIndex, lpValueName, lpcchValueName, null,
          lpType, lpData, lpcbData);
        
        if(result == WINERROR.ERROR_MORE_DATA) {
          lpData = new byte[lpcbData.getValue()];
          lpcchValueName =  new IntByReference(16384);
          result = advapi32.RegEnumValue(handle, dwIndex, lpValueName, lpcchValueName, null,
            lpType, lpData, lpcbData);
          
          if(result == WINERROR.ERROR_SUCCESS) {
            name = new String(lpValueName, 0, lpcchValueName.getValue());
            
            switch(lpType.getValue()) {
              case WINNT.REG_SZ:
                //System.out.println("REG_SZ buffer " + lpData.length);
                values.put(name, convertBufferToString(lpData));
                break;
              case WINNT.REG_DWORD:
                //  System.out.println("REG_DWORD buffer " + lpData.length);
                values.put(name, new Integer(convertBufferToInt(lpData)));
                break;
              case WINNT.REG_BINARY:
                //  System.out.println("REG_BINARY buffer " + lpData.length);
                values.put(name, lpData);
                break;
              default:
                System.out.println("lpType " + lpType.getValue() + " not supported.  Not able to retrieve " + name);
                break;
            }
          }
        }
        dwIndex++;
      } while(result == WINERROR.ERROR_SUCCESS);
      
      advapi32.RegCloseKey(handle);
    }
    return(values);
  }
}
