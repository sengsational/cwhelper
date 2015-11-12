/*
 * Created on Apr 27, 2015
 *
 */
package org.cwepg.svc;

import java.awt.SplashScreen;
import java.util.Date;

import org.cwepg.hr.CaptureManager;

public class SplashScreenCloser implements Runnable {
    
    private int msToWait;
    private Thread runningThread;

    public SplashScreenCloser(int i) {
        msToWait = i * 1000;
        runningThread = new Thread(this);
        runningThread.start();
    }

    @Override
    public void run() {
        try {
            Thread.sleep(msToWait);
            // without this, the spash screen does not close
            SplashScreen splashScreen = java.awt.SplashScreen.getSplashScreen();
            if (splashScreen != null) {
                java.awt.SplashScreen.getSplashScreen().close();
            } else  {
                System.out.println(new Date() + " ERROR: No splash screen to close!!");
            }
        } catch (Exception e){
            e.printStackTrace();
        }
    }
}
