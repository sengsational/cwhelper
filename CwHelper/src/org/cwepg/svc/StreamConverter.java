package org.cwepg.svc;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Iterator;

public class StreamConverter implements Runnable {
	private InputStream is;
	private String type;
	private ArrayList<String> results;
	private int debug = 0;
	private boolean getOutputByByte = false;
	private StringBuffer resultsBuf;

	public StreamConverter(InputStream is, String type, boolean getOutputByByte) {
		this.is = is;
		this.type = type;
		this.getOutputByByte = getOutputByByte;
	}

	/**
	 * Read from a Runtime.exec'ed program's output.
	 */
	public void run() {
		try {
			if (getOutputByByte) {
			    BufferedInputStream bis = new BufferedInputStream(is);
			    resultsBuf = new StringBuffer();
			    int intForChar;
			    while ((intForChar = bis.read()) != -1) {
			        char c = (char)intForChar;
                    if (debug > 0) System.out.println(type + ">" + c);
			        resultsBuf.append(c);
			    }
			} else {
	            BufferedReader br = new BufferedReader(new InputStreamReader(is));
	            String line = null;
	            results = new ArrayList<String>();
	            while ((line = br.readLine()) != null) {
	                if (debug > 0) System.out.println(type + ">" + line);
	                results.add(line);
	            }
			}
		} catch (Exception e) {
			System.out.println("Error in StreamConverter " + e);
		}
	}

	public String getResults() {
	    if (getOutputByByte) {
	        return getResultsByByte();
	    } else {
	        return getResultsByLine();
	    }
	}
	
	private String getResultsByByte() {
	    if (resultsBuf == null) return "";
	    return new String(resultsBuf);
    }

    private String getResultsByLine() {
        StringBuffer buf = new StringBuffer();
        if (results == null) return "";
        for (Iterator iter = results.iterator(); iter.hasNext();) {
            buf.append((String) iter.next() + "\n");
        }
        return new String(buf);
	}
}
