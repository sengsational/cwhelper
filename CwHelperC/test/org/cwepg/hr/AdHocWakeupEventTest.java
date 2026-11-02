package org.cwepg.hr;

import java.util.Calendar;
import org.cwepg.svc.AutorunFile;

/** Run with assertions enabled; native Windows timers and the executable are faked. */
public class AdHocWakeupEventTest {
    static class Event extends AdHocWakeupEvent {
        int timers, clears, runs;
        Event(String hour, String minute, String parameters) { super(hour, minute, "10", parameters); }
        public com.sun.jna.platform.win32.WinNT.HANDLE setWakeup(String who) { timers++; return null; }
        public void removeHardwareWakeupEvent(String who) { clears++; }
        protected void executeAutorun() { runs++; }
    }
    private static void checkDailySchedule() {
        WakeupEvent daily = WakeupEvent.getInstance();
        // First half-hour, cutoff boundaries, late evening, and year rollover.
        int[][] cases = {{10, 12, 0, 28, 10, 30}, {10, 25, 0, 27, 11, 0},
                {10, 24, 59, 28, 10, 30}, {10, 30, 0, 27, 11, 0},
                {23, 54, 59, 1, 0, 0}, {23, 55, 0, 0, 0, 0}};
        for (int[] c : cases) {
            Calendar now = Calendar.getInstance();
            now.clear();
            now.set(2026, Calendar.DECEMBER, 31, c[0], c[1], c[2]);
            long original = now.getTimeInMillis();
            java.util.List<AdHocWakeupEvent> events = daily.createAdHocWakeupEvents(now);
            assert now.getTimeInMillis() == original;
            assert events.size() == c[3] : events.size();
            if (events.isEmpty()) continue;
            Calendar first = events.get(0).getNextTriggerTimeCalendar();
            assert first.get(Calendar.HOUR_OF_DAY) == c[4] && first.get(Calendar.MINUTE) == c[5];
            assert first.getTimeInMillis() - original > 300000;
            long previous = first.getTimeInMillis() - 1800000;
            for (AdHocWakeupEvent event : events) {
                Calendar trigger = event.getNextTriggerTimeCalendar();
                assert trigger.getTimeInMillis() - previous == 1800000;
                assert trigger.get(Calendar.SECOND) == 0 && trigger.get(Calendar.MILLISECOND) == 0;
                previous = trigger.getTimeInMillis();
            }
            Calendar last = events.get(events.size() - 1).getNextTriggerTimeCalendar();
            assert last.get(Calendar.YEAR) == 2027 && last.get(Calendar.DAY_OF_YEAR) == 1;
            assert last.get(Calendar.HOUR_OF_DAY) == 0 && last.get(Calendar.MINUTE) == 0;
        }
    }

    public static void main(String[] args) throws Exception {
        checkDailySchedule();
        CaptureManager.sleeping = true; // Let requestInterrupt return without waiting for an event loop.
        WakeupEvent daily = WakeupEvent.getInstance();
        daily.hourToTrigger = "23";
        daily.minuteToTrigger = "59";
        WakeupEvent.osParameters = "-auto -download -detail -keep";
        WakeupEvent.arf = new AutorunFile();
        AutorunFile dailyFile = WakeupEvent.arf;
        CaptureManager.wakeupEvent = daily;
        String persisted = daily.getPersistenceData();
        Event first = new Event("0", "0", "-AUTO%20-download -detail -keep \"two words\" -wol");
        Event second = new Event("1", "0", null);
        assert first.getAutorunFile().getParameters().equals("-keep \"two words\" -wol");
        assert second.getAutorunFile().getParameters().equals("-wol");
        assert AdHocWakeupEvent.wolParameters("\"-auto\" -details \"x -detail y\"").equals("-details \"x -detail y\" -wol");
        Calendar when = first.getNextTriggerTimeCalendar();
        assert when.get(Calendar.HOUR_OF_DAY) == 0 && when.get(Calendar.MINUTE) == 0;
        when.add(Calendar.DATE, 5);
        assert !when.equals(first.getNextTriggerTimeCalendar());
        java.nio.file.Path remoteDirectory = java.nio.file.Files.createTempDirectory("remote-app-test");
        java.nio.file.Path remoteFile = java.nio.file.Files.createFile(remoteDirectory.resolve("remote[1].exe"));
        CaptureManager.remoteAppPath = remoteFile.toString();
        CaptureManager.isUsingRemote = false;
        assert !CaptureManager.scheduleAdHocWakeupEvent(first);
        CaptureManager.isUsingRemote = true;
        CaptureManager.remoteAppPath = "";
        assert !CaptureManager.scheduleAdHocWakeupEvent(first);
        java.io.PrintStream originalOut = System.out;
        java.io.ByteArrayOutputStream log = new java.io.ByteArrayOutputStream();
        try {
            System.setOut(new java.io.PrintStream(log));
            for (String path : new String[] {remoteDirectory.resolve("missing.exe").toString(),
                    new java.io.File(remoteDirectory.toFile(), "missing*.exe").toString(), remoteDirectory.toString()}) {
                CaptureManager.remoteAppPath = path;
                assert !CaptureManager.scheduleAdHocWakeupEvent(first);
                assert log.toString().contains(path);
                log.reset();
            }
        } finally {
            System.setOut(originalOut);
        }
        assert first.timers == 0;
        CaptureManager.remoteAppPath = remoteFile.toString();
        assert CaptureManager.scheduleAdHocWakeupEvent(first);
        // Do not construct wildcard patterns with Path: Windows rejects '*' before scheduling.
        CaptureManager.remoteAppPath = new java.io.File(new java.io.File(remoteDirectory.getParent().toFile(),
                "remote-app-test*"), "*remote[1]*.exe").toString();
        assert CaptureManager.scheduleAdHocWakeupEvent(second);
        java.nio.file.Files.delete(remoteFile);
        java.nio.file.Files.delete(remoteDirectory);
        assert first.timers == 1 && second.timers == 1;
        assert CaptureManager.getMsUntilNextEvent(true) <= first.getNextTriggerTimeCalendar().getTimeInMillis() - System.currentTimeMillis() + 100;
        java.lang.reflect.Field trigger = AdHocWakeupEvent.class.getDeclaredField("trigger");
        trigger.setAccessible(true);
        ((Calendar) trigger.get(first)).setTimeInMillis(System.currentTimeMillis() - 600000);
        assert first.isDue(); // A delayed resume must not discard the one-shot event.
        assert first.claim() && !first.claim();
        assert first.isActive();
        first.run();
        first.run();
        assert first.runs == 1 && first.clears == 1 && first.timers == 1;
        assert first.isFinished() && !first.isDue() && !first.isActive();
        assert !second.isFinished() && !second.isActive();
        assert daily == CaptureManager.wakeupEvent && dailyFile == WakeupEvent.arf;
        assert persisted.equals(daily.getPersistenceData());
        assert !WakeupEvent.isRunning();
        Event failing = new Event("2", "0", "-auto") {
            protected void executeAutorun() { throw new IllegalStateException("simulated failure"); }
        };
        failing.claim();
        try { failing.run(); throw new AssertionError("Expected failure"); }
        catch (IllegalStateException expected) { }
        assert failing.isFinished() && !failing.isActive() && !failing.isDue();
        assert persisted.equals(daily.getPersistenceData());
        for (String[] invalid : new String[][] {{"24","0","1"},{"0","60","1"},{"0","0","0"},{null,"0","1"},{"0","0","oops"}}) {
            try { new AdHocWakeupEvent(invalid[0], invalid[1], invalid[2], null); throw new AssertionError("Accepted invalid input"); }
            catch (IllegalArgumentException expected) { }
        }
        System.out.println("AdHocWakeupEventTest passed");
        Thread.interrupted();
    }
}
