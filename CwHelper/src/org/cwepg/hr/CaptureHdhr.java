package org.cwepg.hr;

import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URL;
import java.util.Date;
import java.util.List;
import java.util.StringTokenizer;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.UIManager;

import org.cwepg.svc.HdhrCommandLine;

public class CaptureHdhr extends Capture implements Runnable {

    HdhrCommandLine runningCommandLine;
    TrayIcon trayIcon;
    String lockKey = "42";
    
    // used for scheduling new captures
    public CaptureHdhr(Slot slot, Channel channelDigital) {
        super(slot, channelDigital);
        this.lockKey = "" + (int)(Math.random()*1000) + 1; // DRS 20150613 - Prevent 0
    }
    
    // used by getCapturesFromFile (persistance)
    public CaptureHdhr(String l, Tuner tuner) {
        StringTokenizer tok = new StringTokenizer(l, "~");
        this.slot = new Slot(tok.nextToken());
        this.channel = new ChannelDigital(tok.nextToken(), tuner);
        this.target = new Target(tok.nextToken(), 1.0);
        this.lockKey = "" + (int)(Math.random()*1000) + 1; // DRS 20150613 - Prevent 0
    }

    @Override
    public void persist(boolean writeIt, boolean warn, Tuner tuner) throws CaptureScheduleException {
        // nothing to do in HDHR land
        // super.persistToHistoryTable(writeIt, warn, tuner);
    }

    public void configureDevice() throws Exception {
        Tuner tuner = channel.tuner;
        int waitTime = 1; // changed after the first retry
        boolean deviceFound = false;
        for(int tries = 6; tries >= 0; tries--){
            HdhrCommandLine cl = null;
            boolean goodResult = true;
            boolean errorWasReturned = false;
            boolean forceRequired = false;
            int forceCount = 0;
            int eboCount = 0;
            boolean commandFailure = false;

            do {
                String[] setLock = {tuner.id, "set", "/tuner" + tuner.number +"/lockkey", this.lockKey};
                cl = new HdhrCommandLine(setLock, 5, false);
                goodResult = cl.runProcess();
                commandFailure = false;
                if (!goodResult) {
                    commandFailure = true;
                    eboCount++;
                    if (eboCount > 5) {
                        System.out.println(new Date() + " ERROR: Failed to handle \"set\" command after retries. " + cl.getCommands());
                        throw new Exception ("ERROR: Failed to handle \"set\" command after retries. " + cl.getCommands());
                    }
                    else {
                        System.out.println(new Date() + " WARNING: Failed to handle \"set\" command.  Waiting " + waitTime + " second. " + cl.getCommands());
                        try {Thread.sleep(waitTime * 1000);} catch (Exception e){}
                    }
                    continue;
                }
                report("setLock", cl, false);
                forceRequired = report(cl) != null && report(cl).indexOf("resource locked") > -1;
                if (forceRequired) clearLockByForce(tuner.id, tuner.number);
            } while (commandFailure || (forceRequired && forceCount++ < 1));
    
            if (this.target.isWatch()){
                System.out.println(new Date() + " Tuner acquired for HDHR Watch event.  Tuner's channel will not be set (quick tv app will do it).  Releasing the lock.");
                String[] releaseLock = {tuner.id, "key", this.lockKey, "set", "/tuner" + tuner.number + "/lockkey", "none"};  
                cl = new HdhrCommandLine(releaseLock, 5, false);
                goodResult = cl.runProcess();
                if (!goodResult) throw new Exception("failed to handle " + cl.getCommands());
                report("releaseLock", cl, false);
                return;
            }

            String[] setChannel = {tuner.id, "key", this.lockKey, "set", "/tuner" + tuner.number +"/channel", channel.protocol + ":" + ((ChannelDigital)channel).getFirstRf()};
            cl = new HdhrCommandLine(setChannel, 5, false);
            goodResult = cl.runProcess();
            if (!goodResult) throw new Exception("failed to handle " + cl.getCommands());
            report("setChannel", cl, false);
            if (report(cl)!= null && report(cl).indexOf("ERROR:") > -1) errorWasReturned = true;
            
            String[] getStreamInfo = {tuner.id, "key", this.lockKey, "get", "/tuner" + tuner.number +"/streaminfo"};  
            cl = new HdhrCommandLine(getStreamInfo, 5, false);
            goodResult = cl.runProcess();
            if (!goodResult) throw new Exception("failed to handle " + cl.getCommands());
            report("getStreamInfo", cl, false);
            if (report(cl)!= null && report(cl).indexOf("ERROR:") > -1) errorWasReturned = true;
    
            // No stream info ... do we have a device?
            if (cl.getOutput().trim().length() == 0 || errorWasReturned){
                errorWasReturned = false;
                System.out.println(new Date() + " WARNING: Could not configure HDHR device.  Waiting " + waitTime + " seconds. " + tries + " tries remaining.");
                try {Thread.sleep(waitTime * 1000);} catch (Exception e){}
                String[] discover = {"discover"};
                cl = new HdhrCommandLine(discover, 5, false);
                goodResult = cl.runProcess();
                report("discover", cl, false);
                if("no devices found".equals(cl.getOutput())) System.out.println(new Date() + " WARNING: No HDHR devices found.");
            } else {
                deviceFound = true;
                break;
            }
            waitTime = 30;
        }

        if (!deviceFound) throw new Exception("ERROR: No HDHR devices found after retries.");
        
        String[] setPid = {tuner.id, "key", this.lockKey, "set", "/tuner" + tuner.number +"/program", ((Channel)channel).pid};  
        HdhrCommandLine cl = new HdhrCommandLine(setPid, 5, false);
        boolean goodResult = cl.runProcess();
        if (!goodResult) throw new Exception("failed to handle " + cl.getCommands());
        report("setPid", cl, false);
        
        /* DRS 20130310 - Removed as not needed according to Terry
        String[] setTarget = {tuner.id, "key", this.lockKey, "set", "/tuner" + tuner.number +"/target", this.target.ip + ":" + this.target.port};  
        cl = new HdhrCommandLine(setTarget, 5, false);
        goodResult = cl.runProcess();
        if (!goodResult) throw new Exception("failed to handle " + cl.getCommands());
        report("setTarget", cl, false);
        */
    }

    private void clearLockByForce(String id, int number) throws Exception{
        String[] clearLock = {id, "set", "/tuner" + number +"/lockkey", "force"};
        HdhrCommandLine cl = new HdhrCommandLine(clearLock, 5, false);
        boolean goodResult = cl.runProcess();
        if (!goodResult) throw new Exception("failed to handle " + cl.getCommands());
        report("clearLock", cl, false);
    }

    private void report(String string, HdhrCommandLine cl, boolean pullErrors) {
        String clResult = null;
        if (!pullErrors) clResult = report(cl);
        else clResult = reportErrors(cl);
        if (clResult != null){
            System.out.println(string + " said: [" + clResult + "]");
        } else {
            System.out.println(string + " said: []");
        }
    }
    
    private String report(HdhrCommandLine cl){
        String clResult = cl.getOutput();
        if (clResult != null && clResult.trim().length() > 0){
            return clResult.trim();
        }
        return null;
    }

    private String reportErrors(HdhrCommandLine cl){
        String clResult = cl.getErrors();
        if (clResult != null && clResult.trim().length() > 0){
            return clResult.trim();
        }
        return null;
    }

    public void deConfigureDevice() throws Exception {
        Tuner tuner = channel.tuner;
        //tuner.removeCapture(this); // this is done in the tuner manager!!
        markEventHandled();
        if (!this.target.isWatch()){
            /*//DRS 20130311 - Terry said this was not required
            String[] setTarget = {tuner.id, "key", this.lockKey, "set", "/tuner" + tuner.number + "/target", "0.0.0.0:0"};  
            HdhrCommandLine cl = new HdhrCommandLine(setTarget, 5, false);
            boolean goodResult = cl.runProcess();
            if (!goodResult) throw new Exception("failed to handle " + cl.getCommands());
            report("setTarget", cl);
            */
            //DRS 20130311 - Terry wanted me to add this
            String[] setChannel = {tuner.id, "key", this.lockKey, "set", "/tuner" + tuner.number + "/channel", "none"};  
            HdhrCommandLine cl = new HdhrCommandLine(setChannel, 5, false);
            boolean goodResult = cl.runProcess();
            if (!goodResult) throw new Exception("failed to handle " + cl.getCommands());
            report("resetChannel", cl, false);
        }
        System.out.println(new Date() + " HDHR event ended. Releasing the lock on tuner " + tuner.getFullName());
        String[] releaseLock = {tuner.id, "key", this.lockKey, "set", "/tuner" + tuner.number + "/lockkey", "none"};  
        HdhrCommandLine cl = new HdhrCommandLine(releaseLock, 5, false);
        boolean goodResult = cl.runProcess();
        if (!goodResult) throw new Exception("failed to handle " + cl.getCommands());
        report("releaseLock", cl, false);
        boolean forceRequired = report(cl) == null || (report(cl) != null && report(cl).indexOf("resource locked") > -1);
        if (forceRequired) clearLockByForce(tuner.id, tuner.number);
    }
    
    public String getSignalQualityData() {
        /* DRS 20130310 Terry said this was not required 
        String[] discover = {"discover"};
        HdhrCommandLine cl = new HdhrCommandLine(discover, 5, false);
        boolean goodResult = cl.runProcess();
        report("discover", cl, false);
        */

        Tuner tuner = channel.tuner;
        String[] getDebug = {tuner.id, "key", this.lockKey, "get", "/tuner" + tuner.number + "/debug" };
        HdhrCommandLine cl = new HdhrCommandLine(getDebug, 5, false);
        boolean goodResult = cl.runProcess();
        report("debug", cl, false);
        if (!goodResult) {
            return null;
        } else {
            return cl.getOutput();
        }
    }
    
    public boolean addIcon() {
        URL iconUrl = CaptureHdhr.class.getClassLoader().getResource("cw_rs16.GIF");
        ImageIcon imageIcon = new ImageIcon();
        Image imageFromIcon = null;
        if (iconUrl != null){
            imageIcon = new ImageIcon(iconUrl);
            imageFromIcon = ((ImageIcon)imageIcon).getImage();
        } else {
            System.out.println("Not able to find custom icon.");
            Icon icon = UIManager.getIcon("OptionPane.informationIcon");
            int w = icon.getIconWidth();
            int h = icon.getIconHeight();
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            GraphicsDevice gd = ge.getDefaultScreenDevice();
            GraphicsConfiguration gc = gd.getDefaultConfiguration();
            BufferedImage image = gc.createCompatibleImage(w, h);
            Graphics2D g = image.createGraphics();
            icon.paintIcon(null, g, 0, 0);
            g.dispose();
            imageFromIcon = image;
        }
        PopupMenu popup = new PopupMenu();
        String popupText = "Stop Recording";
        if (this.target.isWatch()) popupText = "Stop Watching";
        MenuItem item = new MenuItem(popupText);
        ActionListener listener = new ActionListener(){
            public void actionPerformed(ActionEvent arg0) {
                if (arg0.getActionCommand() == null) return; //double click
                System.out.println(new Date() + " Tray Icon Action Command:" + arg0.getActionCommand());
                try {
                    boolean needsCaptureManagerLoopInterrupt = true;
                    CaptureHdhr thisCapture = (CaptureHdhr)getThisCapture();
                    if (thisCapture.target.isWatch()) thisCapture.interruptWatch();
                    CaptureManager.getInstance().removeActiveCapture(thisCapture, needsCaptureManagerLoopInterrupt);
                } catch (Exception e) {
                    System.out.println(new Date() + " Error trying to stop capture. " + e.getMessage());
                    System.err.println(new Date() + " Error trying to stop capture. " + e.getMessage());
                    e.printStackTrace();
                }
            }
        };
        item.addActionListener(listener);
        popup.add(item);
        String message = this.target.getFileNameOrWatch();
        if (this.target.title != null){
            message = this.target.title + "\n" + this.target.getFileNameOrWatch();
        }
        trayIcon = new TrayIcon(imageFromIcon, message, popup);
        trayIcon.addActionListener(listener);
        try {
            SystemTray.getSystemTray().add(trayIcon);
            return true;
        } catch (Throwable t) {
            System.out.println(new Date() + " System Tray Icon Error: " + t.getMessage());
        }
        return false;
    }
    
    private Capture getThisCapture(){
        return this;
    }
    
    public boolean removeIcon() {
        try {
            SystemTray.getSystemTray().remove(trayIcon);
            return true;
        } catch (Throwable t) {
            System.out.println(new Date() + " System Tray Icon Error: " + t.getMessage());
        }
        return false;
    }

    public void run(){
        try {
            boolean goodResult = false;
            Tuner tuner = channel.tuner;
            int killAfterSeconds = this.slot.getRemainingSeconds() + 120; // wait 2 minutes for normal end, and if not gone yet, kill it.
            String commandName = null;
            
            if (this.target.isWatch()){
                commandName = "quickTv";
                String[] quickTv = {"--new-window", "hdhomerun://" + tuner.getFullName() + "/ch" + this.channel.frequency + "-" + this.channel.pid};
                killAfterSeconds = this.slot.getRemainingSeconds() + (6 * 60 * 60); // wait 6 hours for normal end, and if not gone yet, kill it. 
                runningCommandLine = new HdhrCommandLine(quickTv, killAfterSeconds, true); //
            } else {
                commandName = "saveFile";
                //this.target.mkdirsAndTestWrite(false, this.target.getFileNameOrWatch(), 20);
                this.target.fixFileName();  
                String[] saveFile = {tuner.id, "key", this.lockKey, "save", "/tuner" + tuner.number, "\"" + this.target.getFileNameOrWatch() + "\""};
                runningCommandLine = new HdhrCommandLine(saveFile, killAfterSeconds, false); //
            }
            goodResult = runningCommandLine.runProcess(); // blocks
            if (runningCommandLine.timedOut()){ // nothing else is going to turn off the tuner...we need to do it
                try {
                    /*//DRS 20130311 - Terry said this was not required
                    String[] setEndTarget = {tuner.id, "key", this.lockKey, "set", "/tuner" + tuner.number + "/target", "0.0.0.0:0"};  
                    HdhrCommandLine cl = new HdhrCommandLine(setEndTarget, 5, false);
                    cl.runProcess();
                    */
                    //DRS 20130311 - Terry wanted me to add this
                    String[] setChannel = {tuner.id, "key", this.lockKey, "set", "/tuner" + tuner.number + "/channel", "none"};  
                    HdhrCommandLine cl = new HdhrCommandLine(setChannel, 5, false);
                    cl.runProcess();
                } catch (Throwable t){/*ignore*/}
            }
            if (!goodResult) throw new Exception("Command line did not return nicely (timedOut was " + runningCommandLine.timedOut() + ").  Command underway at the time: " + runningCommandLine.getCommands());
            report(commandName, runningCommandLine, true);
        } catch (Throwable e) {
            System.out.println("CaptureHdhr.run method <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");
            System.out.println(new Date() + " CaptureHdhr.run " + e.getMessage());
            System.err.println(new Date() + " CaptureHdhr.run " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void interrupt(){
        if (runningCommandLine != null){
            if (this.target.isWatch()){
                System.out.println(new Date() + " Not removing watch event at end of scheduled event time.");
            } else {
                runningCommandLine.interrupt();
                removeWakeup();
                removeIcon();
            }
        }
    }
    
    public void interruptWatch(){
        runningCommandLine.interrupt();
        removeWakeup();
        removeIcon();
    }

    @Override
    public int getTunerType() {
        return Tuner.HDHR_TYPE;
    }

    public String getRecordPath() {
        String rp = "";
        try {
            if (this.target != null && this.target.fileName != null){
                File aFile = new File(this.target.fileName);
                rp = aFile.getParent();
            }
        } catch (Exception e){
            
        }
        return rp;
    }

    @Override
    public void setFileName(String fileName) throws Exception { 
        this.target.setFileName(fileName, null, null, null, this.getTunerType(), true, null);
    }

    
    public static void main (String[] args) throws Exception {
        TunerManager tunMan = TunerManager.getInstance();
        boolean tunerOn = false;
        Tuner tuner0 = null;
        if (tunerOn){
            tunMan.countTuners();
        } else {
            tuner0 = new TunerHdhr("10123716", 1, true); 
            //tuner0 = new TunerHdhr("10119A68", 1, true);
            //tuner0 = new TunerHdhr("1013FADA", 0);
        }
        
        boolean useExistingFile = true;
        String signalType = null; // doesn't work anyway
        int maxSeconds = 1000;
        tuner0.scanRefreshLineUp(useExistingFile, signalType, maxSeconds);
        System.out.println(tuner0.lineUp);
        
        boolean forgetAboutTheRestOfIt = true;
        if (forgetAboutTheRestOfIt) return;

        Slot slot = new Slot("3/18/2009 12:12", "2");
        String channelName = "36.1 WCNC-HD";
        String protocol = "8vsb";
        List captures = tunMan.getAvailableCapturesForChannelNameAndSlot(channelName, slot, protocol);
        
        if (captures != null && captures.size() > 0){
            Capture capture = (Capture)captures.get(0); 
            capture.setTarget(new Target("c:\\temp.ts", "Some TV Program Title",null, null, "8vsb", Tuner.HDHR_TYPE));
            System.out.println(capture);
        }
    }
}
