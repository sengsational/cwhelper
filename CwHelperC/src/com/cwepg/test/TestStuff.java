/*
 * Created on Apr 15, 2018
 *
 */
package com.cwepg.test;


import java.net.InetAddress;
import java.net.NetworkInterface;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;

public class TestStuff {
    String abv;
    
    public static String unescapeJavaString(String st) {
        if (st == null) return null;

        StringBuilder sb = new StringBuilder(st.length());
        // [the rain \"In Spain\" is G\u2020one\.] st.length():37
        // [the rain \"In Spain\" is G\u2020one.\] st.length():37
        for (int i = 0; i < st.length(); i++) {
            char ch = st.charAt(i);
            if (ch == '\\') {
                char nextChar = (i == st.length() - 1) ? '\\' : st.charAt(i + 1);
                // Octal escape?
                if (nextChar >= '0' && nextChar <= '7') {
                    String code = "" + nextChar;
                    i++;
                    if ((i < st.length() - 1) && st.charAt(i + 1) >= '0' && st.charAt(i + 1) <= '7') {
                        code += st.charAt(i + 1);
                        i++;
                        if ((i < st.length() - 1) && st.charAt(i + 1) >= '0' && st.charAt(i + 1) <= '7') {
                            code += st.charAt(i + 1);
                            i++;
                        }
                    }
                    sb.append((char) Integer.parseInt(code, 8));
                    continue;
                }
                switch (nextChar) {
                    case '\\':
                        ch = '\\';
                        break;
                    case 'b':
                        ch = '\b';
                        break;
                    case 'f':
                        ch = '\f';
                        break;
                    case 'n':
                        ch = '\n';
                        break;
                    case 'r':
                        ch = '\r';
                        break;
                    case 't':
                        ch = '\t';
                        break;
                    case '\"':
                        ch = '\"';
                        break;
                    case '\'':
                        ch = '\'';
                        break;
                    // Hex Unicode: u????
                    case 'u':
                        if (i >= st.length() - 5) {
                            ch = 'u';
                            break;
                        }
                        String fourChars = "    ";
                        try {
                            fourChars = "" + st.charAt(i + 2) + st.charAt(i + 3)
                                    + st.charAt(i + 4) + st.charAt(i + 5);
                            int code = Integer.parseInt(fourChars, 16);
                            sb.append(Character.toChars(code));
                        } catch (Throwable t) {
                            System.out.println("sengsational " + "Error parsing unicode string [" + fourChars + "]");
                        }
                        i += 5;
                        continue;
                }
                i++;
            }
            sb.append(ch);
        }
        return sb.toString();
    }


    public static void main(String[] args) throws Exception {
        
        String backslash = "\\";
        String quote = "\"";
        //String content = "...began " + backslash + quote + "upping the ante" + backslash + quote + " in the game.";
        String content = "\"description\":\"This beer is the product ... is nothing like " + backslash + quote + "upping the ANTE" + backslash + quote +  " with a little...smoked malt and Triticale. 4.7% ABV<\\/p>\\r\\n";
        System.out.println("before   : " + content);
        content = content.replace("\\\"u", "u"); // "backslash quote u" will become "backslash u", so can't have that.
        content = content.replaceAll("\"", ""); // Remove quotes from within the content.  I don't know why we're doing this any more.
        
        System.out.println("unescaped: " + TestStuff.unescapeJavaString(content));
        
        boolean checkReport = false;
        if (checkReport) {
            String report = "123456789012345678901234567890x";
            int reportLength = report.length();
            int nonDotCount = report.replace(".","").length();
            if (nonDotCount > 0) System.out.println(new Date() + " There were " + nonDotCount + " errors in the recording status results (Recording Monitor).");
            
            if (reportLength > 30) { // if report length is more than 5 minutes, use statistics for the last 5 minutes.
                report = report.substring(reportLength - 30, reportLength);
                
            }
            System.out.println("output [" + report + "]");
            
        }
        
        
        /*
        Enumeration e = NetworkInterface.getNetworkInterfaces();
        while(e.hasMoreElements())
        {
            NetworkInterface n = (NetworkInterface) e.nextElement();
            Enumeration ee = n.getInetAddresses();
            while (ee.hasMoreElements())
            {
                InetAddress i = (InetAddress) ee.nextElement();
                System.out.println(i.getHostAddress());
            }
        }
        */

        //String tunerIp = "169.5.6.7";
        //System.out.println("split test "  +  tunerIp.split("\\.")[0]);
        
        /*
        String description = "basdf asdf asdf asdf 5.49% AxBV";
            int abvLoc = description.toUpperCase().indexOf("ABV");
            String abv = description.substring(Math.max(0,abvLoc-10), Math.min(abvLoc+3, description.length()));
            int pctLoc = abv.indexOf("%");
            if (pctLoc > 2) {
                int blankLoc = abv.lastIndexOf(" ", pctLoc-2);
                abv = abv.substring(blankLoc + 1);
            }       

            pctLoc = description.lastIndexOf("%");
            if(pctLoc > -1 && abv.length() == 2) {
                abv = description.substring(Math.max(0,pctLoc-10), pctLoc + 1);
            }
            if (abv.length() == 2) {
                int percentLoc = description.toUpperCase().indexOf("PERCENT");
                if (percentLoc > -1) {
                    abv = description.substring(Math.max(0,percentLoc-10), percentLoc + 1);
                }
                if (abv.length() == 2) abv = "0";
            }

            abv = abv.replaceAll("[^\\d.]", "");
            if (abv.startsWith(".")) abv = abv.substring(1);
            if (abv.trim().length() == 0) abv = "0";
            
            
        String quizPage = "V/QuizInteractor: <!DOCTYPE html PUBLIC -//W3C//DTD XHTML 1.0 Transitional;a=s.createElement(o),  m=s.getElementsByTagName(o)[0];a.async=1;a.src=g;m.parentNode.insertBefore(a,m)  })(window,document,'script','//www.google-analytics.com/analytics.js','ga');  ga('create', 'UA-7973694-15', 'auto');  ga('send', 'pageview');</script><div id=\"wrapper\"><center><img src=\"img/header.png\" class=\"header\" /></center><div id=\"quiz\">  <form id=\"quiz20180418\" name=\"quiz20180418\" method=\"POST\"";
            try {
                SimpleDateFormat quizDateFormat = new SimpleDateFormat("yyyyMMdd");
                int idLoc = quizPage.indexOf("<form id=\"quiz");
                if (idLoc > 0) {
                    String dateString = quizPage.substring(idLoc + 14, idLoc + 14 + 8);
                    System.out.println("got dateString [" + dateString + "]");
                    Date aDate = quizDateFormat.parse(dateString);
                    System.out.println("date: " + aDate);
                    
                    long nowMs = new Date().getTime();
                    long msSinceQuiz = nowMs - aDate.getTime();
                    long daysSinzeQuiz = msSinceQuiz / 1000 / 60 / 60 / 24;
                    System.out.println("days since quiz: " + daysSinzeQuiz);
                }
            } catch (Throwable t) {
                System.out.println("Failed to get quiz date from page content. " + t.getMessage());
            }
      */      
    }
    

}
