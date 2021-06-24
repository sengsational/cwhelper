/*
 * Created on Jan 17, 2021
 *
 */
package org.cwepg.exex;

import java.util.Date;
import java.util.concurrent.Callable;

public class DelayCallable<ReturnObect> implements Callable<Object> {
    
    private long msDelay;

    public DelayCallable (long msDelay) { 
        this.msDelay = msDelay;
        System.out.println(new Date() + " DelayCallable object created.  The delay is " + msDelay + " milliseconds.");
    }

    @Override
    public Object call() throws Exception {
        // Could return an object of my own design here, if needed.
        return null;
    }

    public long getMsDelay() {
        return this.msDelay;
    }

    public long getSecondsDelay() {
        return Math.round(this.msDelay/1000d);
    }

}
