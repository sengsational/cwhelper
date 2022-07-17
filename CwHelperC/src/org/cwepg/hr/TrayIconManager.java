/*
 * Created on Mar 2, 2019
 *
 */
package org.cwepg.hr;

import java.awt.Desktop;
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
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.UIManager;

public class TrayIconManager implements Runnable {
    
    private static boolean runFlag = false;
    private static TrayIconManager singleton;
    private static TrayIcon trayIcon;
    private static ActionListener listener;
    private static Thread runningThread;
    private static Thread runThread;


    public static TrayIconManager getInstance() {
        if (singleton == null) {
            singleton = new TrayIconManager();
        }
        return singleton;
    }
    
    private TrayIconManager() {
        runningThread = new Thread(this, "Thread-TrayIconManager");
        runningThread.start();
        try {Thread.sleep(100);} catch (Throwable t){};
    }
    
    public synchronized boolean isRunning(){
        return runFlag;
    }
    
    public synchronized void setRunning(boolean running){
        TrayIconManager.runFlag = running;
    }

    public static void shutDown() {
        runThread.interrupt();
        try {Thread.sleep(100);} catch (Throwable t){}
    }
    
    @Override
    public void run() {
        if (isRunning()) {
            System.out.println(new Date() + " ERROR: TrayIconManager should only have one run thread.");
            return;
        } else {
            System.out.println(new Date() + " TrayIconManager run() is starting.");
            setRunning(true);
            addIcon(); // <<<<<<<<<<<<<<< ADD ICON
        }
        TrayIconManager.runThread = Thread.currentThread();

        while (isRunning()) {
            try {
                Thread.sleep(Integer.MAX_VALUE); // Blocks until interrupted
            } catch (InterruptedException e) {
                System.out.println(new Date() + " TrayIconManager run() has been interrupted.");
                setRunning(false);
                if (removeIcon()) { // <<<<<<<<<<<<<<<<<<<<< REMOVE ICON
                    TrayIcon[] iconList = SystemTray.getSystemTray().getTrayIcons();
                    int trayCount = iconList.length;
                    System.out.println(new Date() + " Tray icon listener and tray icon have been removed.  There are " + trayCount + " icons reported in the system tray.");
                    for(int i = 0; i < trayCount; i++) {
                        if (iconList[i] == null) continue;
                        System.out.println(new Date() + " Tray icon remaining " + iconList[i].getActionCommand());
                    }
                } else {
                    TrayIcon[] iconList = SystemTray.getSystemTray().getTrayIcons();
                    int trayCount = iconList.length;
                    System.out.println(new Date() + " Tray icon was not removed. There are " + trayCount + " icons reported in the system tray.");
                }
            }
        } 
        System.out.println(new Date() + " TrayIconManager run() is ending.");
        setRunning(false);
    }
        
    private boolean removeIcon() {
        try {
            //System.out.println(new Date() + " removing cw_helper trayIcon.");
            //System.out.flush();
            trayIcon.removeActionListener(listener);
            //TrayIcon[] icons = SystemTray.getSystemTray().getTrayIcons();
            //for (TrayIcon listIcon : icons) {
            //    System.out.println(new Date() + " Before [" + listIcon.getActionCommand() + "]" + (listIcon.equals(trayIcon)?"<<<<<< REMOVING":"") );
            //}
            SystemTray.getSystemTray().remove(trayIcon);
            //try {Thread.sleep(500);} catch (Exception e){}
            //icons = SystemTray.getSystemTray().getTrayIcons();
            //for (TrayIcon listIcon : icons) {
            //    System.out.println(new Date() + " After  [" + listIcon.getActionCommand() + "]" + (listIcon.equals(trayIcon)?"<<<<<< STILL HERE":"") );
            //}
            return true;
        } catch (Throwable t) {
            System.out.println(new Date() + " System Tray cw_helper remove icon Error: " + t.getMessage());
            System.out.flush();
        }
        return false;
    }

    private boolean addIcon() {
        URL iconUrl = CaptureHdhr.class.getClassLoader().getResource("cw_logo16.gif");
        //System.out.println(new Date() + " [cw_rs16.GIF] URL:[" + iconUrl + "]");
        Image imageFromIcon = null;
        if (iconUrl != null){
            ImageIcon imageIcon = new ImageIcon();
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

        final String[][] menuAndActions = {{"Capture Editor", "vcr"}, {"Open Configuration Page", "configure"}, {"Shutdown", "checkedshutdown"}};
        
        ArrayList<MenuItem> menuItems = new ArrayList<MenuItem>();
        for(int i = 0; i <= menuAndActions[0].length; i++) {
            //System.out.println("menu: " + menuAndActions[i][0] + " " + menuAndActions[i][1]);
            final MenuItem item = new MenuItem(menuAndActions[i][0]);
            menuItems.add(item);
        }
        
        listener = new ActionListener() {
            public void actionPerformed(ActionEvent arg0) {
                if (arg0.getActionCommand() == null) return; //double click

                String actionCommand = arg0.getActionCommand();

                System.out.println(new Date() + " cw_helper Tray Icon Action Command:" + actionCommand);
                System.out.flush();

                String webPage = "";
                for(int i = 0; i <= menuAndActions[0].length; i++) {
                    //System.out.println("menu: " + menuAndActions[i][0] + " " + menuAndActions[i][1]);
                    if (actionCommand.equals(menuAndActions[i][0])) {
                        webPage = menuAndActions[i][1];
                        break;
                    }
                }

                if (!"".equals(webPage)) { 
                    try {
                        System.out.println(new Date() + " Opening web browser to " + webPage + " page.");
                        if(Desktop.isDesktopSupported()) { 
                            Desktop desktop = Desktop.getDesktop();
                            desktop.browse(new URI("http://localhost:8181/" + webPage));
                        }
                    } catch (Exception e) {
                        System.out.println(new Date() + " Error trying open " + webPage + " web page. " + e.getMessage());
                        System.err.println(new Date() + " Error trying open " + webPage + " web page. " + e.getMessage());
                        e.printStackTrace();
                    }
                } else if (arg0.getActionCommand().startsWith("Shutdown")) {
                    CaptureManager.shutdown("trayIcon");
                }
            }
        };

        PopupMenu popup = new PopupMenu();
        for (MenuItem menuItem : menuItems) {
            menuItem.addActionListener(listener);
            popup.add(menuItem);
        }

        trayIcon = new TrayIcon(imageFromIcon, "CW_Helper\nrunning", popup);
        trayIcon.setActionCommand("CW_Helper running " + " " + trayIcon);        
        
        trayIcon.addActionListener(listener);
        try {
            //System.out.println(new Date() + " Adding cw_helper trayIcon.");
            System.out.flush();
            SystemTray.getSystemTray().add(trayIcon);
            //System.out.println(new Date() + " [" + trayIcon.getActionCommand() + "] added.");
            return true;
        } catch (Throwable t) {
            System.out.println(new Date() + " System Tray cw_helper Icon Error: " + t.getMessage());
            System.out.flush();
        }
        return false;
    }

    /* DRS 20210301 -  Removed - No longer needed (used with now removed ClockChecker) 
    public void restart() {
        removeIcon();
        addIcon();
    }
    */
}
