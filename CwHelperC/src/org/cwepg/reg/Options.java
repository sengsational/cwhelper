package org.cwepg.reg;

import java.util.HashMap;
import java.util.Map;

import com.sun.jna.win32.W32APIFunctionMapper;
import com.sun.jna.win32.W32APITypeMapper;

public interface Options {
	Map UNICODE_OPTIONS = new HashMap() {
		private static final long serialVersionUID = 7565240371958738729L;
	{
      put("type-mapper", W32APITypeMapper.UNICODE);
      put("function-mapper", W32APIFunctionMapper.UNICODE);
    }
  };
  
}
