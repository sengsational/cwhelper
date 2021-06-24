/*
 * Created on Apr 5, 2010
 *
 */
package org.cwepg.hr;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.io.Writer;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;

public class CSVWriter extends BufferedWriter {

    List mColumns;
    static final String emptyString = "\"\""; 

    public CSVWriter(Writer out) {
        super(out);
    }

    public void setColumns(List columns) {
        mColumns = columns;
    }

    public void setColumns(String[] stringColumns) {
        ArrayList columns = new ArrayList();
        for (int i = 0; i < stringColumns.length; i++) {
            columns.add(stringColumns[i]);
        }
        mColumns = columns;
    }

    public void addColumns(String[] stringColumns) {
        for (int i = 0; i < stringColumns.length; i++) {
            mColumns.add(stringColumns[i]);
        }
    }

    public void writeHeader() throws IOException {
        write(mColumns);
    }

    public void write(List values) throws IOException {
        Iterator itr = values.iterator();
        while (itr.hasNext()) {
            write((String) itr.next());
            if (itr.hasNext()) {
                writeSeparator();
            }
        }
        newLine();
    }

    public void write(Map values) throws IOException {
        Iterator itr = mColumns.iterator();
        while (itr.hasNext()) {
            String colName = (String)itr.next();
            String value = (String) values.get(colName);
            write(value == null ? emptyString : value);
            if (itr.hasNext()) {
                writeSeparator();
            }
        }
        newLine();
    }

    public void write(String[] stringValues) throws IOException {
        for (int i = 0; i < stringValues.length; i++) {
            String value = stringValues[i];
            write(value == null ? emptyString : value);
            if (i < (stringValues.length - 1)) {
                writeSeparator();
            }
        }
        newLine();
    }

    public void write(String[][] stringValueList, String[] stringValues, String key) throws IOException {
        System.out.println("stringValueList.length:" + stringValueList.length);
        for (int i = 0; i < stringValueList.length; i++) {
            String[] partOne = stringValueList[i];
            String[] partTwo = stringValues;
            String[] result = new String[partOne.length + partTwo.length + 1];
            partOne[5] = blankOut(partOne[5], new String[] { "\t", "\n", "\r" });
            System.out.println("partOne[5]:" + partOne[5] + "  partOne.length:" + partOne.length + "  partTwo.length:"
                    + partTwo.length);

            StringBuffer testOut = new StringBuffer();
            int j = 0;
            for (; j < partOne.length; j++) {
                result[j] = partOne[j];
                testOut.append("*" + result[j]);
            }
            for (int k = 0; j < (partOne.length + partTwo.length); j++, k++) {
                result[j] = partTwo[k];
                testOut.append("*" + result[j]);
            }
            result[j] = key;
            testOut.append("*" + key);
            System.out.println(testOut);
            write(result);
        }
        System.out.println("-------------------------------");

    }

    private String blankOut(String s, String[] zap) {
        StringBuffer b = new StringBuffer(s);
        for (int i = 0; i < zap.length; i++) {
            while (b.indexOf(zap[i]) > -1) {
                int zapLoc = b.indexOf(zap[i]);
                b.delete(zapLoc, zapLoc + zap[i].length());
                b.insert(zapLoc, " ");
            }
        }
        return new String(b);
    }

    public void write(String s) throws IOException {
        // If it's just an empty string, do put the quote marks on it
        if (s.equals("\"\"")){
            super.write(s);
            return;
        }
        
        // If the string does not contain a comma, just write it out
        if (s.indexOf(",") == -1 && s.indexOf("\"") == -1) {
            super.write(s);
            return;
        }

        // If the string does have a comma, then write it out
        // surrounded by quotation marks. Any quotation mark in the
        // string must be doubled.
        super.write("\"");
        int from = 0;
        for (;;) {
            int to = s.indexOf("\"", from);
            if (to == -1) {
                super.write(s, from, s.length() - from);
                break;
            }

            super.write(s, from, to - from);
            super.write("\"\"");

            from = to + 1;
        }
        super.write("\"");
    }

    public void writeSeparator() throws IOException {
        super.write(",");
    }

    public static void main(String[] args) {
        CSVReader reader = null;
        try {

            // First get some data using our reader class...

            reader = new CSVReader(new BufferedReader(new FileReader("InvestmentProperties.csv")));

            List l = reader.readHeader();
            Map map;
            ArrayList list = new ArrayList();
            while ((map = reader.readValues()) != null) {
                list.add(map);
            }

            // Okay now we have a list with headers, and an arraylist of maps

            CSVWriter writer = new CSVWriter(new BufferedWriter(new FileWriter("testWriter.log")));

            writer.setColumns(l);
            writer.writeHeader();
            for (int i = 0; i < list.size(); i++) {
                writer.write((Map) list.get(i));
            }
            writer.close();

        } catch (Exception e) {
            System.out.println("error in csvwriter main:" + e);
        } finally {
            try {reader.close();} catch(Throwable t){}
        }
    }
}

