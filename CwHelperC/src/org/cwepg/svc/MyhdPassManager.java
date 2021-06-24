/*
 * Created on Feb 7, 2010
 *
 */
package org.cwepg.svc;

import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.cwepg.hr.Tuner;
import org.cwepg.hr.TunerManager;
import org.cwepg.hr.TunerMyhd;

public class MyhdPassManager {

    private static final int GET = 0;
    private static final int REMOVEALL = 1;
    
    int command = -1;
    String message = "";
    int serial = -1;
    
    public MyhdPassManager(String command) {
        if (command == null) {
            message = "No command entered.";
        } else if (command.equalsIgnoreCase("GET")){
            this.command = GET;
        } else if (command.equalsIgnoreCase("REMOVEALL")){
            this.command = REMOVEALL;
        } else {
            message = "Command not understood.";
        }
        //System.out.println(new Date() + " DEBUG: " + message);
    }

    public String getHtmlResult() {
        if (!message.equals("")) return "<br>" + message + "<br>";
        TunerMyhd tunerMyhd = (TunerMyhd)TunerManager.getInstance().getTuner("MYHD");
        if (tunerMyhd == null) return "<br>There was no MyHD tuner defined<br>";
        if (this.command == GET) {
            List passItems = tunerMyhd.getPassItems();
            StringBuffer buf = new StringBuffer("<table border=\"1\">\n");
            StringBuffer xmlBuf = new StringBuffer("\n<xml id=\"myhdpass\">\n");
            for (Iterator iter = passItems.iterator(); iter.hasNext();) {
                MyhdPass aPassItem = (MyhdPass) iter.next();
                if (aPassItem != null){
                    buf.append(aPassItem.getHtml(-1));
                    xmlBuf.append(aPassItem.getXml(-1));
                } else {
                    System.out.println(new Date() + " ERROR: passItems had a null object.");
                }
            }
            buf.append("</table>\n");
            xmlBuf.append("</xml>\n");
            return new String(buf) + new String(xmlBuf);
        }
        if (this.command == REMOVEALL){
            //System.out.println(new Date() + " DEBUG: removePassItems");
            tunerMyhd.removePassItems();
            StringBuffer buf = new StringBuffer("<table border=\"1\">\n");
            StringBuffer xmlBuf = new StringBuffer("\n<xml id=\"myhdpass\">\n");
            buf.append("</table>\n");
            xmlBuf.append("</xml>\n");
            return new String(buf) + new String(xmlBuf);
        }
        return "<br>Command not recognized.<br>";
    }

    public static void main(String[] args) {
        TunerManager tm = TunerManager.getInstance();
        tm.countTuner(Tuner.MYHD_TYPE, true);
        MyhdPassManager pm = new MyhdPassManager("GET");
        System.out.println(pm.getHtmlResult());
        System.out.println("----------------------------------");
        pm = new MyhdPassManager("REMOVEALL");
        System.out.println(pm.getHtmlResult());
    }
}
