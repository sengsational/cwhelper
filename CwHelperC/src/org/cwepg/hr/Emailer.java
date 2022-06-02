/*
 * Created on Jan 22, 2010
 *
 */
package org.cwepg.hr;


import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Date;
import java.util.Properties;
import java.util.StringTokenizer;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import com.sun.mail.smtp.SMTPTransport;

public class Emailer extends TimedEvent {

    static String smtpServerName;
    static String smtpServerPort = "25";
    static String logonUser;
    static String logonPassword;
    static String saveToDisk;
    static String sendUsers;
    static String lowDiskGb = "-1";
    static String sendScheduled = "true";
    static String sendRecorded = "true";
    
    static StringBuffer problems;
    
    static Emailer emailer;
    
    private Emailer(){
        super();
    }
    
    public static Emailer getInstance(){
        if (emailer == null){
            emailer = new Emailer();
        }
        return emailer;
    }

    public static Emailer getInstanceFromDisk() {
        emailer = null;
        String fileName = CaptureManager.dataPath + "EmailerData.txt";
        String message = new Date() + " Optional emailer data file not available for intialization.";
        try {
            BufferedReader in = new BufferedReader(new FileReader(fileName));
            String l = null;
            if ((l = in.readLine()) != null){
                emailer = Emailer.getInstance();
                emailer.initialize(l);
                message = new Date() + " Emailer initialized from data file.";
            }
            in.close();
        } catch (Exception e) {
            message = new Date() + " Optional emailer data file not available. " + e.getMessage();
        }
        System.out.println(message);
        return emailer;
    }
    
    public void initialize(String hourToSend, String minuteToSend, String smtpServerName, String smtpServerPort, String logonUser, String logonPassword, String saveToDisk, String sendUsers, String lowDiskGb, String sendScheduled, String sendRecorded) {
        if (hourToSend != null) this.hourToTrigger = hourToSend;
        if (minuteToSend != null) this.minuteToTrigger = minuteToSend;
        if (smtpServerPort != null) Emailer.smtpServerPort = smtpServerPort;
        Emailer.smtpServerName = smtpServerName;
        Emailer.logonUser = logonUser;
        Emailer.logonPassword = logonPassword;
        Emailer.saveToDisk = saveToDisk;
        Emailer.sendUsers = sendUsers;
        if (lowDiskGb != null) Emailer.lowDiskGb = lowDiskGb;
        if (sendScheduled != null) Emailer.sendScheduled = sendScheduled;
        if (sendRecorded != null) Emailer.sendRecorded = sendRecorded;
        if (isValid()){
            this.setWakeup("Emailer initialize()");
            if (Emailer.saveToDisk.toUpperCase().equals("TRUE")) writeEmailerData();
        }
    }
    
    public void initialize(String persistenceData){
        StringTokenizer tok = new StringTokenizer(persistenceData, "|");
        hourToTrigger = tok.nextToken();
        minuteToTrigger = tok.nextToken();
        smtpServerName = tok.nextToken();
        smtpServerPort = tok.nextToken();
        logonUser = tok.nextToken();
        logonPassword = tok.nextToken();
        saveToDisk = tok.nextToken();
        sendUsers = tok.nextToken();
        lowDiskGb = tok.nextToken();
        sendScheduled = tok.nextToken();
        sendRecorded = tok.nextToken();
    }

    public String getPersistenceData(){
        StringBuffer buf = new StringBuffer();
        buf.append(hourToTrigger + "|");
        buf.append(minuteToTrigger + "|");
        buf.append(smtpServerName + "|");
        buf.append(smtpServerPort + "|");
        buf.append(logonUser + "|");
        buf.append(logonPassword + "|");
        buf.append(saveToDisk + "|");
        buf.append(sendUsers + "|");
        buf.append(lowDiskGb + "|");
        buf.append(sendScheduled + "|");
        buf.append(sendRecorded);
        return new String(buf);
    }
    
    private void writeEmailerData() {
        try {
            BufferedWriter out = new BufferedWriter(new FileWriter(CaptureManager.dataPath + "EmailerData.txt"));
            System.out.println(new Date() + " Writing emailer data to " + CaptureManager.dataPath + "EmailerData.txt");
            out.write(getPersistenceData() + "\n");
            out.flush(); out.close();
        } catch (IOException e) {
            System.out.println(new Date() + " ERROR: Failed to write emailer data to " + CaptureManager.dataPath + "EmailerData.txt " + e.getMessage());
        }
    }

    public void removeEmailerDataFile() {
        try {
            File emailerDataFile = new File(CaptureManager.dataPath + "EmailerData.txt");
            boolean ok = emailerDataFile.delete();
            if (ok) System.out.println(new Date() + " Removed EmailerData.txt");
        } catch (Exception e){
            System.out.println(new Date() + " Error removing EmailerData.txt " + e.getMessage());
        }
    }
    
    public void sendWarningEmail(String tunerName, String channelKey, String targetFile, String tsmiss, int nonDotCount, int strengthValue) {
        System.out.println(new Date() + " Sending quality problem email to " + Emailer.sendUsers);
        try {
            Session session = getSession();
            MimeMessage msg = new MimeMessage(session);
            String sendFromDomain = getLocalMachineName() + ".com";
            int atLoc = logonUser.indexOf("@");
            if (atLoc > -1 && !logonUser.endsWith("@")){
                sendFromDomain = logonUser.substring(atLoc + 1);
            } else {
                System.out.println(new Date() + " ERROR: You have specified a logonUser without an '@'.  Using your machine name as send from domain. " + sendFromDomain );
            }
            msg.setFrom(new InternetAddress(logonUser));
            msg.setRecipients(Message.RecipientType.TO, getToAddresses());
            msg.setSubject("Possible Poor Quality Recording");
            String tsMissMessage = "-1".equals(tsmiss)?"":" there were " + tsmiss + " misses, ";
            msg.setContent("The file " + targetFile + " might have too many dropouts.  The strength value was " + strengthValue + ", " + tsMissMessage + "and " + nonDotCount + " continuity errors.  This was on tuner " + tunerName + " and channel " + channelKey + ".","text/plain");
            SMTPTransport transport =(SMTPTransport)session.getTransport("smtp");//ok
            transport.connect();
            transport.sendMessage(msg, msg.getAllRecipients());
            transport.close();
        } catch (Exception e) {
            System.out.println(new Date() + " ERROR sending quality data email.  " + e.getMessage());
        }
        
    }

    public void sendTestMessage() {
        System.out.println(new Date() + " Sending TEST email to " + Emailer.sendUsers);
        try {
            Session session = getSession();
            MimeMessage msg = new MimeMessage(session);
            String sendFromDomain = getLocalMachineName() + ".com";
            int atLoc = logonUser.indexOf("@");
            if (atLoc > -1 && !logonUser.endsWith("@")){
                sendFromDomain = logonUser.substring(atLoc + 1);
            } else {
                System.out.println(new Date() + " ERROR: You have specified a logonUser without an '@'.  Using your machine name as send from domain. " + sendFromDomain );
            }
            //msg.setFrom(new InternetAddress("cwhelpertest@" + sendFromDomain));
            msg.setFrom(new InternetAddress(logonUser));
            msg.setRecipients(Message.RecipientType.TO, getToAddresses());
            msg.setSubject("testing cwhelper");
            msg.setContent("The email test was successful.","text/plain");
            SMTPTransport transport =(SMTPTransport)session.getTransport("smtp");//ok
            transport.connect();
            transport.sendMessage(msg, msg.getAllRecipients());
            transport.close();
        } catch (Exception e) {
            System.out.println(new Date() + " ERROR sending TEST email.  " + e.getMessage());
        }
    }

    public void send() {
        lastTriggerMs = new Date().getTime();
        try {Thread.sleep(500);} catch (Exception e){}
        System.out.println(new Date() + " Sending email to " + Emailer.sendUsers);
        TunerManager tm = TunerManager.getInstance();
        CaptureManager cm = CaptureManager.getInstance();
        String sendFromDomain = null;
        try {
            Session session = getSession();
            MimeMessage msg = new MimeMessage(session);
            sendFromDomain = getLocalMachineName() + ".com";
            int atLoc = logonUser.indexOf("@");
            if (atLoc > -1 && !logonUser.endsWith("@")){
                sendFromDomain = logonUser.substring(atLoc + 1);
            }
            //msg.setFrom(new InternetAddress("cwhelper@" + sendFromDomain));
            msg.setFrom(new InternetAddress(logonUser));
            msg.setRecipients(Message.RecipientType.TO, getToAddresses());
            msg.setSubject("Daily CW_EPG News");
            StringBuffer content = new StringBuffer();
            String recentWebCaptures = cm.getRecentWebCapturesList(24, false, null);
            String scheduledWebCaptures = tm.getWebCapturesList(false);
            if (Emailer.sendRecorded.toUpperCase().equals("TRUE") && recentWebCaptures.length() > 0) content.append("<h1>Recently Recorded</h1><br>" + recentWebCaptures + "<BR><BR>");
            if (Emailer.sendScheduled.toUpperCase().equals("TRUE") && scheduledWebCaptures.length() > 0) content.append("<h1>Scheduled for Recording</h1><br>" + scheduledWebCaptures + "<BR><BR>");
            boolean alertExists = tm.foundDiskFullAlertCondition(Emailer.lowDiskGb);
            if (!Emailer.lowDiskGb.equals("-1") && alertExists) content.append("<h1>Disk Status Alert</h1><br>" + tm.getWebTunerList());
            if (content.length() > 0){
                msg.setContent(new String(content),"text/html");
                SMTPTransport transport =(SMTPTransport)session.getTransport("smtp");//ok
                transport.connect();
                transport.sendMessage(msg, msg.getAllRecipients());
                transport.close();
            }
        } catch (Exception e) {
            System.out.println(new Date() + " ERROR sending status email.  " + e.getMessage() + " " + sendFromDomain);
        }
        setWakeup("Emailer send()");
    }

    private Session getSession() {
        Properties props = new Properties();
        props.put("mail.smtp.host", Emailer.smtpServerName);
        props.put("mail.smtp.port", Emailer.smtpServerPort);
        props.put("mail.smtp.auth","true");
        props.put("mail.smtp.starttls.enable", "true");
        Session session = Session.getDefaultInstance(props, new SmtpAuthenticator());
        return session;
    }
    
    private InternetAddress[] getToAddresses(){
        InternetAddress[] toAddresses = new InternetAddress[0];
        String lastToken = "";
        try {
            StringTokenizer tok = new StringTokenizer(Emailer.sendUsers, ";");
            ArrayList<InternetAddress> internetAddressList = new ArrayList<InternetAddress>();
            while(tok.hasMoreTokens()){
                lastToken = tok.nextToken();
                internetAddressList.add(new InternetAddress(lastToken));
            }
            toAddresses = internetAddressList.toArray(new InternetAddress[0]);
        } catch (AddressException e) {
            System.out.println(new Date() + " ERROR: Could not for email address from " + lastToken);
        }
        return toAddresses;
    }
    
    private String getLocalMachineName(){
        String localMachineName = "myTvMachine";
        try {
            InetAddress localHost = InetAddress.getLocalHost();
            localMachineName = localHost.getHostName();
        } catch (Exception e){}
        return localMachineName;
    }
    
    class SmtpAuthenticator extends Authenticator {
        protected PasswordAuthentication getPasswordAuthentication(){
            return new PasswordAuthentication(Emailer.logonUser, Emailer.logonPassword);
        }
    }
    
    public boolean isValid() {
        problems = new StringBuffer();
        try {Integer.parseInt(this.hourToTrigger);} catch (Exception e){problems.append("hour to send not numeric<br>");}
        try {Integer.parseInt(this.minuteToTrigger);} catch (Exception e){problems.append("minute to send not numeric<br>");}
        try {Integer.parseInt(Emailer.smtpServerPort);} catch (Exception e){problems.append("smtp server port not numeric<br>");}
        try {Integer.parseInt(Emailer.lowDiskGb);} catch (Exception e){problems.append("low disk percent not numeric<br>");}
        if (Emailer.smtpServerName == null) problems.append("smtp server name was not specified<br>");
        if (Emailer.logonUser == null) problems.append("logon user was not specified<br>");
        if (Emailer.logonPassword == null) problems.append("logon password was not specified<br>");
        if (Emailer.saveToDisk == null) problems.append("save to disk was not specified<br>");
        if (Emailer.sendUsers == null) problems.append("user(s) to send to was not specified<br>");
        if (Emailer.lowDiskGb.equals("-1") && !Emailer.sendScheduled.toUpperCase().equals("TRUE") && !Emailer.sendRecorded.toUpperCase().equals("TRUE")) problems.append("no service was requested (specify at least one of low disk percent, send recorded, send scheduled).<br>");
        return problems.length() == 0;
    }

    public String getHtml() {
        isValid();
        StringBuffer buf = new StringBuffer("<table border=\"1\">\n");
        StringBuffer xmlBuf = new StringBuffer("\n<xml id=\"emailers\">\n");
        buf.append(
                "<tr><td>isValid:</td><td>" + (problems.length()==0) + "</td></tr>" + 
                "<tr><td>problems:</td><td>" + new String(problems) + "</td></tr>" + 
                "<tr><td>hourToTrigger:</td><td>" + hourToTrigger + "</td></tr>" + 
                "<tr><td>minuteToTrigger:</td><td>" + minuteToTrigger + "</td></tr>" + 
                "<tr><td>smtpServerName:</td><td>" + smtpServerName + "</td></tr>" + 
                "<tr><td>smtpServerPort:</td><td>" + smtpServerPort + "</td></tr>" +
                "<tr><td>logonUser:</td><td>" + logonUser + "</td></tr>" + 
                "<tr><td>logonPassword:</td><td>" + logonPassword + "</td></tr>" + 
                "<tr><td>saveToDisk:</td><td>" + saveToDisk + "</td></tr>" + 
                "<tr><td>sendUsers:</td><td>" + sendUsers + "</td></tr>" + 
                "<tr><td>lowDiskGb:</td><td>" + lowDiskGb + "</td></tr>" + 
                "<tr><td>sendScheduled:</td><td>" + sendScheduled + "</td></tr>" + 
                "<tr><td>sendRecorded:</td><td>" + sendRecorded + "</td></tr>" + 
                "\n");
        xmlBuf.append(
                "  <emailer "+ 
                "isValid=\"" + (problems.length() == 0) + "\" " + 
                "problems=\"" + new String(problems) + "\" " + 
                "hourToTrigger=\"" + hourToTrigger + "\" " + 
                "minuteToTrigger=\"" + minuteToTrigger + "\" " + 
                "smtpServerName=\"" + smtpServerName + "\" " + 
                "smtpServerPort=\"" + smtpServerPort + "\" " + 
                "logonUser=\"" + logonUser + "\" " + 
                "logonPassword=\"" + logonPassword + "\" " + 
                "saveToDisk=\"" + saveToDisk + "\" " + 
                "sendUsers=\"" + sendUsers + "\" " + 
                "lowDiskGb=\"" + lowDiskGb + "\" " + 
                "sendScheduled=\"" + sendScheduled + "\" " + 
                "sendRecorded=\"" + sendRecorded + "\" " + 
                "/>\n");
        buf.append("</table>\n");
        xmlBuf.append("</xml>\n");
        return new String(buf) + new String(xmlBuf);
    }

    public static void main(String[] args) {
        Emailer emailer = Emailer.getInstance();
        //emailer.initialize("22","58","smtp.everyone.net","2525", "dale@sengsational.com","","true","dale@sengsational.com","90","true","true");
        emailer.initialize("20","32","smtp.googlemail.com","587", "DRS2.Usenet@sengsational.com","aqP7LXB4","true","dale@sengsational.com","90","true","true");

        /*
        System.out.println(emailer.getHtml());
        for(;;){
            if (emailer.isDue())emailer.send();
            try {
                System.out.println("sleeping 3");
                Thread.sleep(3000);
            } catch (Exception e) {}
        }
        */
        System.out.println("Is Valid:" + emailer.isValid());
        emailer.sendTestMessage();
    }

}
