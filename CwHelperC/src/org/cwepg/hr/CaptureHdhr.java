package org.cwepg.hr;

import java.awt.Component;
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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URL;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.StringTokenizer;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.UIManager;

import org.cwepg.reg.Registry;
import org.cwepg.svc.CommandLine;
import org.cwepg.svc.HdhrCommandLine;
import org.cwepg.svc.VlcCommandLine;

public class CaptureHdhr extends Capture implements Runnable {

    CommandLine runningCommandLine;
    //TimeoutCommandLine runningCommandLine;
    TrayIcon trayIcon;
    String lockKey = "42";
    private int mNonDotCount;
    int killAfterSeconds;
    String[] trayMessage = {"","",""};
    int extendedMinutes = 0;
    private ActionListener listener;
    
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
        RETRY_LOOP:
        for(int tries = 6; tries >= 0; tries--){
            HdhrCommandLine cl = null;
            boolean goodResult = true;
            boolean errorWasReturned = false;
            boolean forceRequired = false;
            int forceCount = 0;
            int eboCount = 0;
            boolean commandFailure = false;

            // Silicon Dust Command Line Data: 103AEA6C set /tuner0/lockkey 8851
            do {
                
                if (this.target.isWatch()) break; // DRS 20181112 - Don't set lock for watches
                
                String[] setLock = {tuner.id, "set", "/tuner" + tuner.number +"/lockkey", this.lockKey};
                cl = new HdhrCommandLine(setLock, 5, false);
                if (cl.runProcess()){ // normally true
                    commandFailure = false;
                } else { // if we need retries
                    eboCount = retriesExpired(eboCount, cl, waitTime, "set");
                    commandFailure = true;
                    continue;
                }
                report("setLock", cl, false);
                forceRequired = report(cl) != null && report(cl).indexOf("resource locked") > -1;
                if (forceRequired) clearLockByForce(tuner.id, tuner.number);
            } while (commandFailure || (forceRequired && forceCount++ < 1));
    
            if (this.target.isWatch()){
                /*
                System.out.println(new Date() + " Tuner acquired for HDHR Watch event.  Tuner's channel will not be set (quick tv app will do it).  Releasing the lock.");
                String[] releaseLock = {tuner.id, "key", this.lockKey, "set", "/tuner" + tuner.number + "/lockkey", "none"};  
                cl = new HdhrCommandLine(releaseLock, 5, false);
                goodResult = cl.runProcess();
                if (!goodResult) throw new Exception("failed to handle " + cl.getCommands());
                report("releaseLock", cl, false);
                */

                /*
                // For watch events that are on traditional tuners, configure the device
                if (this.channel.frequency != null) { 
                    System.out.println(new Date() + " Watch event on traditional tuner.  Configuring the HDHR device.");
                    // Silicon Dust Command Line Data: 103AEA6C key 8851 set /tuner0/channel 8vsb:7
                    String[] setChannel = {tuner.id, "set", "/tuner" + tuner.number +"/channel", channel.protocol + ":" + ((ChannelDigital)channel).getFirstRf()};
                    cl = new HdhrCommandLine(setChannel, 5, false);
                    goodResult = cl.runProcess();
                    if (!goodResult) throw new Exception("failed to handle " + cl.getCommands());
                    report("setChannel", cl, false);
                    if (report(cl)!= null && report(cl).indexOf("ERROR:") > -1) errorWasReturned = true;
                    
                    String[] setPid = {tuner.id, "set", "/tuner" + tuner.number +"/program", ((Channel)channel).pid};  
                    cl = new HdhrCommandLine(setPid, 5, false);
                    goodResult = cl.runProcess();
                    if (!goodResult) throw new Exception("failed to handle " + cl.getCommands());
                    report("setPid", cl, false);
    
                    String[] setTarget = {tuner.id, "set", "/tuner" + tuner.number + "/target", "rtp://" + ((TunerHdhr)tuner).ipAddressMachine + ":" + target.port};  
                    cl = new HdhrCommandLine(setTarget, 5, false);
                    goodResult = cl.runProcess();
                    if (!goodResult) throw new Exception("failed to handle " + cl.getCommands());
                    report("setTarget", cl, false);
                    
                } else {
                    System.out.println(new Date() + " Watch event on vchannel tuner.");
                }
                */
                return;
            }

            if (((TunerHdhr)tuner).isVchannel) {
                String[] setVchannel = {tuner.id, "key", this.lockKey, "set", "/tuner" + tuner.number +"/vchannel", ((ChannelDigital)channel).getCleanedChannelName()};
                cl = new HdhrCommandLine(setVchannel, 5, false);
            } else {
                // Silicon Dust Command Line Data: 103AEA6C key 8851 set /tuner0/channel 8vsb:7
                String[] setChannel = {tuner.id, "key", this.lockKey, "set", "/tuner" + tuner.number +"/channel", channel.protocol + ":" + ((ChannelDigital)channel).getFirstRf()};
                cl = new HdhrCommandLine(setChannel, 5, false);
            }
            goodResult = cl.runProcess();
            if (!goodResult) throw new Exception("failed to handle " + cl.getCommands());
            report("setChannel", cl, false);
            if (report(cl)!= null && report(cl).indexOf("ERROR:") > -1) errorWasReturned = true;
            
            // Silicon Dust Command Line Data: 103AEA6C key 8851 get /tuner0/streaminfo
            String[] getStreamInfo = {tuner.id, "key", this.lockKey, "get", "/tuner" + tuner.number +"/streaminfo"};  
            cl = new HdhrCommandLine(getStreamInfo, 5, false);
            goodResult = cl.runProcess();
            if (!goodResult) throw new Exception("failed to handle " + cl.getCommands());
            report("getStreamInfo", cl, false);
            if (report(cl)!= null && report(cl).indexOf("ERROR:") > -1) errorWasReturned = true;
    
            // No stream info (should be "none")... do we have a device?
            if (cl.getOutput().trim().length() == 0 || errorWasReturned){
                errorWasReturned = false;
                System.out.println(new Date() + " WARNING: Could not configure HDHR device.  Waiting " + waitTime + " seconds. " + tries + " tries remaining.");
                try {Thread.sleep(waitTime * 1000);} catch (Exception e){}
                String[] discover = {"discover"};
                cl = new HdhrCommandLine(discover, 5, false);
                goodResult = cl.runProcess();
                report("discover", cl, false);
                if("no devices found".equals(cl.getOutput())) System.out.println(new Date() + " WARNING: No HDHR devices found.");
            } else if (!((TunerHdhr)tuner).isVchannel) { // Normal good processing here
                int pidCount = 0;
                commandFailure = false;
                do {
                    String[] setPid = {tuner.id, "key", this.lockKey, "set", "/tuner" + tuner.number +"/program", ((Channel)channel).pid};  
                    cl = new HdhrCommandLine(setPid, 5, false);
                    boolean goodRun = cl.runProcess();
                    report("setPid", cl, false);
                    if (goodRun){ // normally true
                        if (report(cl)!= null && report(cl).indexOf("ERROR:") > -1) { // DRS 20160613 - Add 'if' - Address "lock no longer held"
                            System.out.println(new Date() + " Error encountered in \"set\" command.  Clearing lock.");
                            clearLockByForce(tuner.id, tuner.number);
                            pidCount = retriesExpired(pidCount, cl, waitTime, "setPid");  //<<<< retriesExpired
                            commandFailure = true;
                            this.lockKey = "" + (int)(Math.random()*1000) + 1; 
                            break RETRY_LOOP;
                            //continue; // back to 'do'
                        }
                        commandFailure = false; 
                    } else { // if we need to retry
                        pidCount = retriesExpired(pidCount, cl, waitTime, "setPid");
                        commandFailure = true;
                        continue; // back to 'do'
                    }
                    report("setPid", cl, false);
                } while(commandFailure && pidCount < 5);

                if (!commandFailure) {
                    deviceFound = true;
                    break; // break out of retry loop
                }
            } else { // vchannel, everything just works perfectly.  This is way too good to be true.
                deviceFound = true;
                break;
            }
            waitTime = 30;
        } // Retry loop ends

        if (!deviceFound) throw new Exception("ERROR: No HDHR devices found after retries.");
        
        
        /* DRS 20130310 - Removed as not needed according to Terry
        String[] setTarget = {tuner.id, "key", this.lockKey, "set", "/tuner" + tuner.number +"/target", this.target.ip + ":" + this.target.port};  
        cl = new HdhrCommandLine(setTarget, 5, false);
        goodResult = cl.runProcess();
        if (!goodResult) throw new Exception("failed to handle " + cl.getCommands());
        report("setTarget", cl, false);
        */
    }

    private int retriesExpired(int failureCount, HdhrCommandLine cl, int waitTime, String commandName) throws Exception {
        failureCount++;
        if (failureCount > 5) {
            System.out.println(new Date() + " ERROR: Failed to handle \"" + commandName + "\" command after retries. " + cl.getCommands());
            throw new Exception ("ERROR: Failed to handle \"" + commandName + "\" command after retries. " + cl.getCommands());
        } else {
            System.out.println(new Date() + " WARNING: Failed to handle \"set\" command.  Waiting " + waitTime + " second. " + cl.getCommands());
            try {Thread.sleep(waitTime * 1000);} catch (Exception e){}
        }
        return failureCount;
    }

    private void clearLockByForce(String id, int number) throws Exception{
        String[] clearLock = {id, "set", "/tuner" + number +"/lockkey", "force"};
        HdhrCommandLine cl = new HdhrCommandLine(clearLock, 5, false);
        boolean goodResult = cl.runProcess();
        if (!goodResult) throw new Exception("failed to handle " + cl.getCommands());
        report("clearLock", cl, false);
    }

    private void report(String string, CommandLine cl, boolean pullErrors) {
        String clResult = null;
        if (!pullErrors) clResult = report(cl);
        else clResult = reportErrors(cl);
        if (clResult != null){
            if ("saveFile".equals(string)) {
                System.out.println(string + " {" + cl.getCommands() + "} said: [" + clResult + "]");
                try {
                    mNonDotCount = clResult.replace(".","").length();
                    if (mNonDotCount > 0) System.out.println(new Date() + " There were " + mNonDotCount + " errors in the recording status results (CaptureHdhr report).");
                } catch (Throwable t) {
                    // do nothing because this isn't critical
                }
            } else {
                System.out.println(string + " said: [" + clResult + "]");
            }
        } else {
            System.out.println(string + " said: []");
        }
    }
    
    private String report(CommandLine cl){
        String clResult = cl.getOutput();
        if (clResult != null && clResult.trim().length() > 0){
            return clResult.trim();
        }
        return null;
    }

    private String reportErrors(CommandLine cl){
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
            
            //DRS 2018111 - Moved this code into the "not is watch" block.
            System.out.println(new Date() + " HDHR event ended. Releasing the lock on tuner " + tuner.getFullName());
            String[] releaseLock = {tuner.id, "key", this.lockKey, "set", "/tuner" + tuner.number + "/lockkey", "none"};  
            cl = new HdhrCommandLine(releaseLock, 5, false);
            goodResult = cl.runProcess();
            if (!goodResult) throw new Exception("failed to handle " + cl.getCommands());
            report("releaseLock", cl, false);
            boolean forceRequired = report(cl) == null || (report(cl) != null && report(cl).indexOf("resource locked") > -1);
            if (forceRequired) clearLockByForce(tuner.id, tuner.number);
        } else {
            /*
            String[] setChannel = {tuner.id, "set", "/tuner" + tuner.number + "/channel", "none"};  
            HdhrCommandLine cl = new HdhrCommandLine(setChannel, 5, false);
            boolean goodResult = cl.runProcess();
            if (!goodResult) throw new Exception("failed to handle " + cl.getCommands());
            report("resetChannel", cl, false);
            */
            
            String[] setTarget = {tuner.id, "set", "/tuner" + tuner.number + "/target", "none"};  
            HdhrCommandLine cl = new HdhrCommandLine(setTarget, 5, false);
            boolean goodResult = cl.runProcess();
            if (!goodResult) throw new Exception("failed to handle " + cl.getCommands());
            report("resetTarget", cl, false);
        }
    }
    
    public String getSignalQualityData() {
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
    
    @Override
    public int getNonDotCount() {
        return mNonDotCount; 
    }
    
    public Channel getChannel() {
        return channel;
    }


    public boolean addIcon() {
        URL iconUrl = CaptureHdhr.class.getClassLoader().getResource("cw_rs16.GIF");
        System.out.println(new Date() + " [cw_rs16.GIF] URL:[" + iconUrl + "]");
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

        String popupTextStop = "Stop Recording";
        if (this.target.isWatch()) popupTextStop = "Stop Watching";
        final MenuItem itemStop = new MenuItem(popupTextStop);
        final MenuItem itemExtend = new MenuItem("Extend 10 Minutes"); // DRS 20181124

        PopupMenu popup = new PopupMenu();
        
        listener = new ActionListener(){
            public void actionPerformed(ActionEvent arg0) {
                if (arg0.getActionCommand() == null) return; //double click
                System.out.println(new Date() + " Tray Icon Action Command:" + arg0.getActionCommand());
                System.out.flush();
                
                if (arg0.getActionCommand().startsWith("Stop")) { // DRS 20181124
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
                } else {
                    try {
                        if (extendSlot(10)) {
                            extendedMinutes += 10;
                            if (trayIcon != null) {
                                trayMessage[2] = "(extended " + extendedMinutes + " min.)";
                                trayIcon.setToolTip(trayMessage[0] + " " + trayMessage[2] + "\n" + trayMessage[1]);
                            }
                        } else {
                            System.out.println(new Date() + " Could not extend recording " + trayMessage[0]);
                        }
                    } catch (Exception e) {
                        System.out.println(new Date() + " Could not extend recording. " + trayMessage[0] + " " +  e.getMessage());
                    }
                }
            }
        };
        itemStop.addActionListener(listener);
        popup.add(itemStop);
        if (!this.target.isWatch()) {
            itemExtend.addActionListener(listener); // DRS 20181124
            popup.add(itemExtend); // DRS 20181124
        }
        trayMessage[1] = this.target.getFileNameOrWatch();
        if (this.target.title != null){
            trayMessage[0] = this.target.title;
        }
        trayIcon = new TrayIcon(imageFromIcon, trayMessage[0] + "\n" + trayMessage[1], popup);
        trayIcon.setActionCommand(trayMessage[0] + " " + trayIcon);
        
        trayIcon.addActionListener(listener);
        
        
        if (!this.target.isWatch()) {
            trayIcon.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    boolean extendPossible = checkExtendSlot(10); // DRS 20181124
                    System.out.println(new Date() + " Pop-up Menu set itemExtend.setEnabled(" + extendPossible + "). " + (extendPossible?"":"No addtional extension possible.")); // DRS 20181124
                    itemExtend.setEnabled(extendPossible); // DRS 20181124
                }
            });
        }
        
        try {
            System.out.println(new Date() + " adding trayIcon.");
            System.out.flush();
            SystemTray.getSystemTray().add(trayIcon);
            System.out.println(new Date() + " [" + trayIcon.getActionCommand() + "] added.");
            System.out.flush();
            return true;
        } catch (Throwable t) {
            System.out.println(new Date() + " System Tray Icon Error: " + t.getMessage());
            System.out.flush();
        }
        return false;
    }
    
    private Capture getThisCapture(){
        return this;
    }
    
    public boolean removeIcon() {
        try {
            System.out.println(new Date() + " removing trayIcon.");
            System.out.flush();
            trayIcon.removeActionListener(listener);
            TrayIcon[] icons = SystemTray.getSystemTray().getTrayIcons();
            for (TrayIcon listIcon : icons) {
                System.out.println(new Date() + " Before [" + listIcon.getActionCommand() + "]" + (listIcon.equals(trayIcon)?"<<<<<< REMOVING":"") );
            }
            SystemTray.getSystemTray().remove(trayIcon);
            try {Thread.sleep(500);} catch (Exception e){}
            icons = SystemTray.getSystemTray().getTrayIcons();
            for (TrayIcon listIcon : icons) {
                System.out.println(new Date() + " After  [" + listIcon.getActionCommand() + "]" + (listIcon.equals(trayIcon)?"<<<<<< STILL HERE":"") );
            }
            System.out.println(new Date() + " Requesting garbage collection."); // Humoring Terry in his windmill-tilting task of clearing stuff from the Windows 10 task bar
            System.gc();
            return true;
        } catch (Throwable t) {
            System.out.println(new Date() + " System Tray Icon Error: " + t.getMessage());
            System.out.flush();
        }
        return false;
    }

    public void run(){
        try {
            boolean goodResult = false;
            Tuner tuner = channel.tuner;
            killAfterSeconds = this.slot.getRemainingSeconds() + 120; // wait 2 minutes for normal end, and if not gone yet, kill it.
            String commandName = null;
            
            if (this.target.isWatch()){
                if (this.channel.frequency != null) { // traditional processing
                    /* DRS 20181112 - Replace quick tv with VLC for traditional tuners
                    commandName = "quickTv";
                    String[] quickTv = {"--new-window", "hdhomerun://" + tuner.getFullName() + "/ch" + this.channel.frequency + "-" + this.channel.pid};
                    killAfterSeconds = this.slot.getRemainingSeconds() + (6 * 60 * 60); // wait 6 hours for normal end, and if not gone yet, kill it. 
                    runningCommandLine = new HdhrCommandLine(quickTv, killAfterSeconds, true); //
                    //runningCommandLine = new TimeoutCommandLine(quickTv, this.slot.getRemainingSeconds(), true); //
                     */
                    commandName = "vlcWatchRtp";
                    String[] vlcWatchRtp = {"rtp://@:" + this.target.port};
                    killAfterSeconds = this.slot.getRemainingSeconds() + (6 * 60 * 60); // wait 6 hours for normal end, and if not gone yet, kill it. 
                    runningCommandLine = new VlcCommandLine(vlcWatchRtp, killAfterSeconds, true); //
                    
                    //* THIS WAS A TEST TO SEE IF DOING THESE AFTER THE VLC WOULD MAKE A DIFFERENCE.  IT DIDN'T WORK AT ALL
                    if (this.channel.frequency != null) { // traditional processing
                        // Silicon Dust Command Line Data: 103AEA6C key 8851 set /tuner0/channel 8vsb:7
                        String[] setChannel = {tuner.id, "set", "/tuner" + tuner.number +"/channel", channel.protocol + ":" + ((ChannelDigital)channel).getFirstRf()};
                        CommandLine cl = new HdhrCommandLine(setChannel, 5, false);
                        goodResult = cl.runProcess();
                        if (!goodResult) throw new Exception("failed to handle " + cl.getCommands());
                        report("setChannel", cl, false);
                        if (report(cl)!= null && report(cl).indexOf("ERROR:") > -1) System.out.println(new Date() + " ERROR FROM SET CHANNEL!");
                        
                        String[] setPid = {tuner.id, "set", "/tuner" + tuner.number +"/program", ((Channel)channel).pid};  
                        cl = new HdhrCommandLine(setPid, 5, false);
                        goodResult = cl.runProcess();
                        if (!goodResult) throw new Exception("failed to handle " + cl.getCommands());
                        report("setPid", cl, false);
        
                        String[] setTarget = {tuner.id, "set", "/tuner" + tuner.number + "/target", "rtp://" + ((TunerHdhr)tuner).ipAddressMachine + ":" + target.port};  
                        cl = new HdhrCommandLine(setTarget, 5, false);
                        goodResult = cl.runProcess();
                        if (!goodResult) throw new Exception("failed to handle " + cl.getCommands());
                        report("setTarget", cl, false);
                    }
                    // */
                    
                } else { // vchannel
                    commandName = "vlcWatchHttp";
                    String[] vlcWatchHttp = {"http://" + ((TunerHdhr)tuner).ipAddressTuner+":5004/tuner"+tuner.number+"/v"+this.channel.channelVirtual};
                    killAfterSeconds = this.slot.getRemainingSeconds() + (6 * 60 * 60); // wait 6 hours for normal end, and if not gone yet, kill it. 
                    runningCommandLine = new VlcCommandLine(vlcWatchHttp, killAfterSeconds, true); //
                }
            } else {
                commandName = "saveFile";
                //this.target.mkdirsAndTestWrite(false, this.target.getFileNameOrWatch(), 20);
                this.target.fixFileName();
                if (((TunerHdhr) tuner).isVchannel) {
                    System.out.println(new Date() + " tuner model is vChannel capable: " + ((TunerHdhr)tuner).isVchannel + ". Using alternative save file command.");
                    String[] saveFile = {tuner.id, "key", this.lockKey, "save", "/tuner" + tuner.number, "\"" + this.target.getFileNameOrWatch() + "\""};
                    runningCommandLine = new HdhrCommandLine(saveFile, killAfterSeconds, false); //
                    // Setting the capture on the command line causes the RecordingMonitor to start monitoring that capture
                    if (CaptureManager.hdhrRecordMonitorSeconds > 0) ((HdhrCommandLine) runningCommandLine).setCapture(this);
                } else {
                    String[] saveFile = {tuner.id, "key", this.lockKey, "save", "/tuner" + tuner.number, "\"" + this.target.getFileNameOrWatch() + "\""};
                    runningCommandLine = new HdhrCommandLine(saveFile, killAfterSeconds, false); //
                    // Setting the capture on the command line causes the RecordingMonitor to start monitoring that capture
                    if (CaptureManager.hdhrRecordMonitorSeconds > 0) ((HdhrCommandLine) runningCommandLine).setCapture(this);
                }
                //String[] noSaveFile = {"" + this.slot.getRemainingSeconds()};
                //runningCommandLine = new TimeoutCommandLine(noSaveFile, this.slot.getRemainingSeconds(), false); //
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
            if (commandName.startsWith("vlcWatch")) {
                String reportOutput = report(runningCommandLine);
                System.out.println(new Date() + " Not showing " + (reportOutput!=null?reportOutput.length():"(???)") + " characters from " + commandName + " output.");
            } else {
                report(commandName, runningCommandLine, true);
            }
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
                // removeIcon(); // DRS 20190419 - Called separately
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
    
    /*
    public static String getRegistryVlcFolder() {
        String vlcFolder = null;
        try {
            String location = "SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\App Paths\\vlc.exe\\";
            vlcFolder = Registry.getStringValue("HKEY_LOCAL_MACHINE", location,"Path");
            if (vlcFolder == null) throw new Exception("Could not find 'Path' at HKLM " + location);
        } catch (Exception e) {
            System.out.println(new Date() + " ERROR: Could not read registry for HDHomeRun. " + e.getMessage());
            //System.err.println(new Date() + " ERROR: Could not read registry for HDHomeRun. " + e.getMessage());
            //e.printStackTrace();
        }
        return vlcFolder;
    }
    */
    
    // DRS 20181124 - new method
    public boolean checkExtendSlot(int minutes) {
        if(!CaptureManager.useHdhrCommandLine) return false; //DRS 20220707 Extending recording not possible with http type.
        return getExtendedCapture(minutes) != null;
    }

    // DRS 20181124 - new method
    public boolean extendSlot(int minutes) {
        if (checkExtendSlot(minutes)) {
            slot.adjustEndTimeMinutes(minutes);
            this.killAfterSeconds = this.killAfterSeconds += minutes * 60;
            ((HdhrCommandLine)this.runningCommandLine).extendKillSeconds(minutes * 60);
            CaptureManager.requestInterrupt("extendSlot");
            return true;
        } else {
            System.out.println(new Date() + " Not able to schedule with [" + this.channel.getCleanedChannelName() + "] [" + this.channel.tuner.getFullName() + "] [" + this.channel.protocol +"]");
        }
        return false;
    }

    // DRS 20181124 - new method
    Capture getExtendedCapture(int minutes) {
        Calendar testStartCal = (Calendar)this.slot.end.clone();
        testStartCal.add(Calendar.SECOND, 1);
        Slot aSlot= new Slot(testStartCal, testStartCal);
        aSlot.adjustEndTimeMinutes(minutes);
        boolean debug = true;
        if (debug) System.out.println(new Date() + " Slot defined for extension of existing recording: " + aSlot);
        return TunerManager.getInstance().getCaptureForChannelNameSlotAndTuner(this.channel.getCleanedChannelName(), aSlot, this.channel.tuner.getFullName(), this.channel.protocol);
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
