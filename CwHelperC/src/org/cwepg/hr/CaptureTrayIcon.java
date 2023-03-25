/*
 * Created on Jul 8, 2022
 *
 */
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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.Date;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.UIManager;

public class CaptureTrayIcon {
    
    private ActionListener listener;
    private TrayIcon trayIcon;
    private int extendedMinutes = 0;
    private String[] trayMessage = {"","",""};
    private Capture capture;

    
    public CaptureTrayIcon(Capture capture) {
        this.capture = capture;
    }

    public boolean addIcon() {
        URL iconUrl = CaptureHdhr.class.getClassLoader().getResource("cw_rs16.GIF");
        //System.out.println(new Date() + " [cw_rs16.GIF] URL:[" + iconUrl + "]");
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
        if (capture.target.isWatch()) popupTextStop = "Stop Watching";
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
                        if (capture.target.isWatch() && (capture instanceof CaptureHdhr)) ((CaptureHdhr)capture).interruptWatch();
                        CaptureManager.getInstance().removeActiveCapture(capture, needsCaptureManagerLoopInterrupt, true);
                    } catch (Exception e) {
                        System.out.println(new Date() + " Error trying to stop capture. " + e.getMessage());
                        System.err.println(new Date() + " Error trying to stop capture. " + e.getMessage());
                        e.printStackTrace();
                    }
                } else {
                    try {
                        int extendIncrement = 10;
                        boolean shortenForTesting = false; // <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<FALSE FOR PRODUCTION RELEASES
                        if (shortenForTesting) extendIncrement = 1;
                        if (((CaptureHdhr) capture).extendSlot(extendIncrement)) {
                            extendedMinutes += extendIncrement;
                            if (trayIcon != null) {
                                trayMessage[2] = "(extended " + extendedMinutes + " min.)";
                                trayIcon.setToolTip(trayMessage[0] + " " + trayMessage[2] + "\n" + trayMessage[1]);
                            }
                        } else {
                            System.out.println(new Date() + " Could not extend recording " + trayMessage[0]);
                        }
                    } catch (Exception e) {
                        System.out.println(new Date() + " Could not extend recording. " + trayMessage[0] + " " +  e.getClass().getName() + " " + e.getMessage());
                    }
                }
            }
        };
        itemStop.addActionListener(listener);
        popup.add(itemStop);
        if (!capture.target.isWatch()) {
            itemExtend.addActionListener(listener); // DRS 20181124
            popup.add(itemExtend); // DRS 20181124
        }
        trayMessage[1] = capture.target.getFileNameOrWatch();
        if (capture.target.title != null){
            trayMessage[0] = capture.target.title;
        }
        trayIcon = new TrayIcon(imageFromIcon, trayMessage[0] + "\n" + trayMessage[1], popup);
        trayIcon.setActionCommand(trayMessage[0] + " " + trayIcon);
        
        trayIcon.addActionListener(listener);
        
        
        if (!capture.target.isWatch()) {
            trayIcon.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    boolean extendPossible = ((CaptureHdhr) capture).checkExtendSlot(10); // DRS 20181124
                    System.out.println(new Date() + " Pop-up Menu set itemExtend.setEnabled(" + extendPossible + "). " + (extendPossible?"":"No addtional extension possible.")); // DRS 20181124
                    itemExtend.setEnabled(extendPossible); // DRS 20181124
                }
            });
        }
        
        try {
            //System.out.println(new Date() + " adding trayIcon.");
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
    
    public boolean removeIcon() {
        try {
            System.out.println(new Date() + " removing trayIcon [" + trayIcon.getActionCommand() + "] that had " + trayIcon.getActionListeners().length + " listener(s).");
            System.out.flush();
            trayIcon.removeActionListener(listener);
            /*
            TrayIcon[] icons = SystemTray.getSystemTray().getTrayIcons();
            for (TrayIcon listIcon : icons) {
                System.out.println(new Date() + " Before [" + listIcon.getActionCommand() + "]" + (listIcon.equals(trayIcon)?"<<<<<< REMOVING":"") );
            }
            */
            SystemTray.getSystemTray().remove(trayIcon);
            try {Thread.sleep(500);} catch (Exception e){}
            /*
            icons = SystemTray.getSystemTray().getTrayIcons();
            for (TrayIcon listIcon : icons) {
                System.out.println(new Date() + " After  [" + listIcon.getActionCommand() + "]" + (listIcon.equals(trayIcon)?"<<<<<< STILL HERE":"") + " listener " + listener );
            }
            */
            System.out.println(new Date() + " Requesting garbage collection."); // Humoring Terry in his windmill-tilting task of clearing stuff from the Windows 10 task bar
            System.gc();
            return true;
        } catch (Throwable t) {
            System.out.println(new Date() + " System Tray Icon Error: " + t.getMessage());
            System.out.flush();
        }
        return false;
    }
}
