/*
 * Created on Oct 2, 2016
 *
 */
package org.cwepg.hr;

import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.util.Date;
import java.util.Properties;
import java.util.Set;

// No need to try to save anything to the log...it doesn't show up.

public class ShutdownHookThread extends Thread {
    static Thread mainThread;
    String path = CaptureManager.dataPath + File.separator;
    FileWriter writer;
    
    public ShutdownHookThread(String dataPath) {
        //mainThread = runThread;
        path = dataPath + File.separator + "logs" + File.separator;
        System.out.println(new Date() + " ShutdownHookThread writer at " + path + "lastShutdownHook.txt");
        try {
            writer = new FileWriter(path + "lastShutdownHook.txt");
        } catch (Exception e) {
            System.out.println(new Date() + " Failed to create ShutdownHookThread writer.");
        }
    }
    
    public static void setRunThread(Thread mainThread) {
        ShutdownHookThread.mainThread = mainThread;
    }

    public void run() {
        try {
            Log("about to join()");
            mainThread.join();
            Log("join completed");
            Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
            for (Thread thread : threadSet) {
                Log("Thread: " + thread.getName() + "\n" + getNiceStackTrace(thread.getStackTrace()) + "\n");
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {writer.close();} catch (Throwable t) {}
        }
    }
    
    public static String getNiceStackTrace(StackTraceElement[] stackTraceElements) {
        StringBuffer buf = new StringBuffer();
        for (StackTraceElement stackTraceElement : stackTraceElements) {
            buf.append(stackTraceElement.toString()).append("\n");
        }
        return buf.toString();
    }


    public void Log(String propertyValue) throws Exception {
        if (writer != null) {
            writer.write(new Date() + " " + propertyValue + "\n");
            writer.flush();
        } else {
            System.out.println(new Date() + " ShutdownHookThread writer was null.");
        }
    }
}
