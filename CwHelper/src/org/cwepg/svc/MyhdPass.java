/*
 * Created on Feb 7, 2010
 *
 */
package org.cwepg.svc;

public class MyhdPass {
    
    String title;
    String fileName;
    int sequence;

    public MyhdPass(String title, String fileName, int sequence) {
        this.title = title;
        this.fileName = fileName;
        this.sequence = sequence;
    }

    public StringBuffer getHtml(int i) {
        StringBuffer buf = new StringBuffer();
        buf.append("<tr><td>Sequence:" 
                + sequence + "</td><td>" 
                + title + "</td><td>" 
                + fileName + "</td>"
                + "</tr>\n");
        return buf;
    }

    public StringBuffer getXml(int i) {
        StringBuffer xmlBuf = new StringBuffer();
        xmlBuf.append("  <myhdpass sequence=\"" 
                + sequence + "\" title=\"" 
                + title + "\" fileName=\"" 
                + fileName + "\" " 
                + "/>\n");
        return xmlBuf;
    }

    public static void main(String[] args) {
        // TODO Auto-generated method stub

    }
}
