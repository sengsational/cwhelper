/*
 * Created on Apr 5, 2010
 *
 */
package org.cwepg.hr;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.io.Reader;
import java.io.LineNumberReader;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.FileReader;

public class CSVReader extends LineNumberReader {
    List mColumns = null;
    int debug = 0;

    public CSVReader(Reader in) {
        super(in);
    }

    public List getColumns() throws IOException {
        return mColumns;
    }

    public List<String> readHeader() throws IOException {
        mColumns = readRawValues();
        return mColumns;
    }

    public Map<String, String> readValues() throws IOException {
        // make sure the columns are loaded
        if (getColumns() == null) {
            return null;
        }

        List values = readRawValues();
        if (values == null) {
            return null;
        }

        Iterator columnItr = mColumns.iterator();
        Iterator valueItr = values.iterator();
        Map valueMap = new HashMap();
        while (columnItr.hasNext() && valueItr.hasNext()) {
            String column = (String) columnItr.next();
            String value = (String) valueItr.next();

            if (value.length() > 0) {
                valueMap.put(column, value);
            }
        }

        return valueMap;
    }

    public List<String> readRawValues() throws IOException {
        String line;
        // StringTokenizer tokenizer;
        List<String> values;

        line = "#";
        while (line != null && line.startsWith("#")) {
            line = readLine();
        }
        if (line == null) {
            return null;
        }

        values = new ArrayList<String>();

        line = line + ","; // now is there is always a trailing delimiter
        int pos = 0;
        while (line.indexOf(",", pos) != -1) {
            String value;
            if (debug > 4)
                System.out.println("line.substring(pos) [" + line.substring(pos) + "]");
            if (line.charAt(pos) == '"') {
                StringBuffer buf = new StringBuffer();

                pos++;
                for (;;) {
                    if (line.charAt(pos) == '"') {
                        char next = line.charAt(pos + 1);
                        pos += 2;

                        if (next == ',') {
                            break;
                        }

                        buf.append(next);
                    } else {
                        int end = line.indexOf("\"", pos + 1);
                        buf.append(line.substring(pos, end));
                        pos = end;
                    }
                }

                value = buf.toString();
            } else {
                int end = line.indexOf(",", pos);
                value = line.substring(pos, end);
                pos = end + 1;
            }

            values.add(value);
        }

        return values;
    }

    // main here only to test this class
    public static void main(String[] args) {
        try {
            CSVReader reader = new CSVReader(new BufferedReader(new FileReader("C:\\my\\dev\\hdhr\\testchamber\\channel_maps.txt")));
            Map map;

            List l = reader.readHeader();
            for (int i = 0; i < l.size(); i++) {
                System.out.println("list:" + l.get(i));
            }

            int i = 0;
            while ((map = reader.readValues()) != null) {
                i++;
                Iterator iter = map.keySet().iterator();
                if ("runthru".equals("runthru")) {
                    while (iter.hasNext()) {
                        Object o = iter.next();
                        System.out.println("map:" + i + " " + o + ":" + map.get(o));
                    }
                }
            }

        } catch (Exception e) {
            System.out.println("error in main:" + e);
        }
    }
}
