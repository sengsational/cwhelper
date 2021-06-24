/*
 * Created on Feb 23, 2021
 *
 */
package com.cwepg.test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;

import org.cwepg.svc.RestartManager;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Platform;

/** Simple example of JNA interface mapping and usage. */
public class TestRestartManagerJna {

    // This is the standard, stable way of mapping, which supports extensive
    // customization and mapping of Java to native types.

    public interface CLibrary extends Library {
        CLibrary INSTANCE = (CLibrary) Native.load((Platform.isWindows() ? "msvcrt" : "c"), CLibrary.class);

        void printf(String format, Object... args);
    }

    public static void main(String[] args) throws Exception {

        /* redirect error messages
        String logPath = new File("test").getAbsoluteFile().getParentFile().getAbsolutePath();
        PrintStream out = new PrintStream(new FileOutputStream(logPath + File.separator + "stdout.txt"), true);
        PrintStream err = new PrintStream(new FileOutputStream(logPath + File.separator + "stderr.txt"), true);

        System.setOut(out);
        System.setErr(err);
        */
        
        CLibrary.INSTANCE.printf("Hello, World\n");
        
        RestartManager manager = new RestartManager();
        Thread eventLoopThread = new Thread(manager, "Win32 Event Loop");
        eventLoopThread.setDaemon(true); // Make the thread a daemon so it doesn't prevent program from exiting.
        eventLoopThread.start();

        Thread.sleep(9999999);
    }
}