package org.cwepg.reg;


import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.sun.jna.win32.W32APIFunctionMapper;
import com.sun.jna.win32.W32APITypeMapper;

public interface W32APIOptions {

   /** Standard options to use the unicode version of a w32 API. */
   Map<String, Object> UNICODE_OPTIONS = Collections.unmodifiableMap(new HashMap<String, Object>() {
       private static final long serialVersionUID = 1L;    // we're not serializing it

       {
           put("type-mapper", W32APITypeMapper.UNICODE);
           put("function-mapper", W32APIFunctionMapper.UNICODE);
       }
   });

   /** Standard options to use the ASCII/MBCS version of a w32 API. */
   Map<String, Object> ASCII_OPTIONS = Collections.unmodifiableMap(new HashMap<String, Object>() {
       private static final long serialVersionUID = 1L;    // we're not serializing it
       {
           put("type-mapper", W32APITypeMapper.UNICODE);
           put("function-mapper", W32APIFunctionMapper.ASCII);
       }
   });

   /** Default options to use - depends on the value of {@code w32.ascii} system property */
   Map<String, Object> DEFAULT_OPTIONS = Boolean.getBoolean("w32.ascii") ? ASCII_OPTIONS : UNICODE_OPTIONS;
}