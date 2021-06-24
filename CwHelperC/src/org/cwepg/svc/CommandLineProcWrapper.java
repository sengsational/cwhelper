/*
 * Created on Mar 16, 2019
 *
 */
package org.cwepg.svc;

public class CommandLineProcWrapper extends Thread {
    
    int exitValue = Integer.MAX_VALUE;
    Process proc;
    boolean isStuck = false;
    int debugLevel = 0;

    public CommandLineProcWrapper(Process proc, int debugLevel) {
        this.proc = proc;
        this.debugLevel = debugLevel;
    }

    @Override
    public void run() {
        if (debugLevel > 3) System.out.println("CommandLineProcWrapper running...");
        isStuck = true;
        exitValue = proc.exitValue(); // could hang
        isStuck = false;
        if (debugLevel > 3) System.out.println("CommandLineProcWrapper got exit value " + exitValue);
    }
    
    public void stopProcess() {
        isStuck = true;
        proc.destroy();
        isStuck = false;
    }

    public void exitValue() {
        if (debugLevel > 3) System.out.println("CommandLineProcWrapper checking exitValue " + exitValue);
        if (exitValue == Integer.MAX_VALUE) throw new IllegalThreadStateException("not done");
    }

    public boolean isStuck() {
        return isStuck;
    }

}
