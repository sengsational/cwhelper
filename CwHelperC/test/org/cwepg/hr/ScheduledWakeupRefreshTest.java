package org.cwepg.hr;

import java.lang.reflect.Field;
import java.util.Calendar;
import java.util.List;

import com.sun.jna.platform.win32.WinNT.HANDLE;

/** Run with assertions enabled; handle operations are faked to run off Windows. */
public class ScheduledWakeupRefreshTest {
    private static Calendar at(long millis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(millis);
        return calendar;
    }

    private static class Email extends Emailer {
        int clears, sets;
        final Calendar trigger;
        Email(long when) { trigger = at(when); wakeupHandle = new HANDLE(); }
        public Calendar getNextTriggerTimeCalendar() { return (Calendar) trigger.clone(); }
        public void removeHardwareWakeupEvent(String who) { clears++; wakeupHandle = null; }
        public HANDLE setWakeup(String who) {
            assert wakeupHandle == null : "Old timer must be removed first";
            sets++;
            return wakeupHandle = new HANDLE();
        }
    }

    private static class Event extends AdHocWakeupEvent {
        int clears, sets;
        Event(long when) { super(at(when), "10", "-wol"); wakeupHandle = new HANDLE(); }
        public void removeHardwareWakeupEvent(String who) { clears++; wakeupHandle = null; }
        public HANDLE setWakeup(String who) {
            assert wakeupHandle == null : "Old timer must be removed first";
            sets++;
            return wakeupHandle = new HANDLE();
        }
        protected void executeAutorun() { }
    }

    private static class Recording extends CaptureHdhr {
        int clears, sets;
        Recording(long when) {
            super(new Slot(at(when), at(when + 3600000)), null);
            wakeupHandle = new HANDLE();
        }
        public void removeWakeup() { clears++; wakeupHandle = null; }
        public void setWakeup() {
            assert wakeupHandle == null : "Old timer must be removed first";
            sets++;
            wakeupHandle = new HANDLE();
        }
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        long now = System.currentTimeMillis();
        long future = now + 3600000;
        Email email = new Email(future);
        CaptureManager.setEmailer(email);
        Event pending = new Event(future);
        Event overdue = new Event(now - 60000);
        Event active = new Event(future);
        active.claim();
        Event finished = new Event(future);
        CaptureManager.sleeping = true;
        finished.run();
        int finishedClears = finished.clears;
        Event disabled = new Event(future);
        disabled.wakeupHandle = null;
        Event failing = new Event(future) {
            public HANDLE setWakeup(String who) { throw new IllegalStateException("simulated timer failure"); }
        };
        Field eventsField = CaptureManager.class.getDeclaredField("adHocWakeupEvents");
        eventsField.setAccessible(true);
        List<AdHocWakeupEvent> events = (List<AdHocWakeupEvent>) eventsField.get(null);
        events.add(failing);
        events.add(pending);
        events.add(overdue);
        events.add(active);
        events.add(finished);
        events.add(disabled);

        TunerHdhr tuner = new TunerHdhr("test", 0, "");
        TunerManager.tuners.put("test", tuner);
        Recording recording = new Recording(future);
        Recording soon = new Recording(now + 30000);
        Recording past = new Recording(now - 60000);
        Recording started = new Recording(future);
        started.startHandled = true;
        Recording ended = new Recording(future);
        ended.endHandled = true;
        Recording activeRecording = new Recording(future + 120000);
        CaptureManager.activeCaptures.add(activeRecording);
        Recording disabledRecording = new Recording(future);
        disabledRecording.wakeupHandle = null;
        for (Recording item : new Recording[] {recording, soon, past, started, ended, activeRecording, disabledRecording}) {
            tuner.captures.add(item);
        }
        HANDLE oldEmailHandle = email.wakeupHandle;
        HANDLE oldEventHandle = pending.wakeupHandle;
        HANDLE oldRecordingHandle = recording.wakeupHandle;
        try {
            CaptureManager.refreshScheduledWakeups();
            assert email.clears == 1 && email.sets == 1 && email.wakeupHandle != oldEmailHandle;
            assert pending.clears == 1 && pending.sets == 1 && pending.wakeupHandle != oldEventHandle;
            assert pending.getNextTriggerTimeCalendar().getTimeInMillis() == future;
            assert overdue.clears == 0 && overdue.sets == 0;
            assert active.clears == 0 && active.sets == 0;
            assert finished.clears == finishedClears && finished.sets == 0;
            assert disabled.clears == 0 && disabled.sets == 0;
            assert recording.clears == 1 && recording.sets == 1 && recording.wakeupHandle != oldRecordingHandle;
            assert recording.slot.start.getTimeInMillis() == future;
            assert soon.clears == 1 && soon.sets == 1;
            for (Recording item : new Recording[] {past, started, ended, activeRecording, disabledRecording}) {
                assert item.clears == 0 && item.sets == 0;
            }
            // Exact-now and overdue triggers must not be re-armed.
            Email due = new Email(now);
            CaptureManager.refreshScheduledWakeup(due, now);
            assert due.clears == 0 && due.sets == 0;
            CaptureManager.refreshScheduledWakeup(null, now);
            System.out.println("ScheduledWakeupRefreshTest passed");
        } finally {
            CaptureManager.setEmailer(null);
            events.clear();
            TunerManager.tuners.clear();
            CaptureManager.activeCaptures.clear();
            Thread.interrupted();
        }
    }
}
