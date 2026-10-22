package org.cwepg.hr;

import java.util.Calendar;
import org.cwepg.svc.AutorunFile;
import org.cwepg.svc.CwEpgCommandLine;

/** A one-shot autorun. Never reads or writes the daily event's mutable state. */
public class AdHocWakeupEvent extends TimedEvent implements Runnable {
    private final Calendar trigger;
    private final AutorunFile autorun;
    private volatile boolean started;
    private volatile boolean finished;
    private boolean executed;

    public AdHocWakeupEvent(String hour, String minute, String duration, String parameters) {
        int h = number(hour, 0, 23, "hourtosend");
        int m = number(minute, 0, 59, "minutetosend");
        number(duration, 1, Integer.MAX_VALUE / 60, "durationminutes");
        super.initialize(hour, minute, duration);
        trigger = Calendar.getInstance();
        trigger.set(Calendar.HOUR_OF_DAY, h);
        trigger.set(Calendar.MINUTE, m);
        trigger.set(Calendar.SECOND, 0);
        trigger.set(Calendar.MILLISECOND, 0);
        autorun = new AutorunFile(hour, minute, duration, wolParameters(parameters), true, CaptureManager.leadTimeSeconds, true, "");
    }

    private static int number(String value, int min, int max, String name) {
        try {
            int n = Integer.parseInt(value);
            if (n >= min && n <= max) return n;
        } catch (NumberFormatException ignored) { }
        throw new IllegalArgumentException(name + " must be between " + min + " and " + max);
    }

    static String wolParameters(String parameters) {
        if (parameters == null || "null".equals(parameters)) return "-wol";
        StringBuilder result = new StringBuilder();
        java.util.regex.Matcher tokens = java.util.regex.Pattern.compile("\"[^\"]*\"|\\S+")
                .matcher(parameters.replace("%20", " "));
        while (tokens.find()) {
            String token = tokens.group();
            String option = token.replace("\"", "");
            if (option.equalsIgnoreCase("-auto") || option.equalsIgnoreCase("-download")
                    || option.equalsIgnoreCase("-detail") || option.equalsIgnoreCase("-wol")) continue;
            result.append(token).append(' ');
        }
        return result.append("-wol").toString();
    }

    public AutorunFile getAutorunFile() { return autorun; }
    public Calendar getNextTriggerTimeCalendar() { return (Calendar) trigger.clone(); }
    public boolean isDue() { return !started && System.currentTimeMillis() >= (trigger.getTimeInMillis() - 2000); } //if within 2 seconds, call it due.
    public boolean isFinished() { return finished; }
    public boolean isActive() { return started && !finished; }
    public synchronized boolean claim() {
        if (started) return false;
        started = true;
        return true;
    }

    protected void executeAutorun() {
        CwEpgCommandLine command = new CwEpgCommandLine(autorun);
        if (!command.runProcess()) System.out.println("Ad-hoc autorun failed: " + command.getErrors());
    }

    public void run() {
        synchronized (this) {
            if (executed) return;
            executed = true;
            started = true;
        }
        try {
            removeHardwareWakeupEvent("Ad-hoc autorun starting");
            executeAutorun();
        } finally {
            finished = true;
            CaptureManager.requestInterrupt("Ad-hoc autorun finished");
        }
    }
}
