package org.cwepg.svc;

import java.time.Instant;
import java.time.ZoneId;

/** Run with assertions enabled; no native window or Windows timers are needed. */
public class RestartManagerTimeChangeTest {
    public static void main(String[] args) {
        testTimeChangeThreshold();
        ZoneId zone = ZoneId.of("America/New_York");
        Instant spring = Instant.parse("2026-03-08T07:00:00Z");
        Instant autumn = Instant.parse("2026-11-01T06:00:00Z");
        RestartManager manager = new RestartManager();
        assert !manager.isNewTimeZoneTransition(spring.minusMillis(1), zone);
        assert manager.isNewTimeZoneTransition(spring, zone);
        assert !manager.isNewTimeZoneTransition(spring.plusSeconds(30), zone);
        assert !manager.isNewTimeZoneTransition(spring.plusSeconds(60), zone);
        assert manager.isNewTimeZoneTransition(autumn.plusSeconds(60), zone);
        assert !manager.isNewTimeZoneTransition(autumn.plusSeconds(61), zone);
        assert new RestartManager().isNewTimeZoneTransition(spring.plusSeconds(120), zone);
        assert !new RestartManager().isNewTimeZoneTransition(spring.plusSeconds(120).plusMillis(1), zone);
        assert !new RestartManager().isNewTimeZoneTransition(spring.plusSeconds(86400), zone);
        assert !new RestartManager().isNewTimeZoneTransition(spring, ZoneId.of("UTC"));
        assert !new RestartManager().isNewTimeZoneTransition(spring, ZoneId.of("America/Phoenix"));
        // Do not assume that every offset transition is one hour.
        assert new RestartManager().isNewTimeZoneTransition(
                Instant.parse("2026-04-04T15:00:30Z"), ZoneId.of("Australia/Lord_Howe"));
        System.out.println("RestartManagerTimeChangeTest passed");
    }

    private static void testTimeChangeThreshold() {
        ZoneId utc = ZoneId.of("UTC");
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        long elapsedMillis = 86_400_000L;
        for (long correction : new long[] {0, 1, -1, 59_999, -59_999, 60_000, -60_000, 3_600_000, -3_600_000}) {
            RestartManager manager = new RestartManager(start, 0, utc);
            Instant changed = start.plusMillis(elapsedMillis + correction);
            assert manager.isSignificantTimeChange(changed, elapsedMillis * 1_000_000, utc)
                    == (Math.abs(correction) >= 60_000) : "Correction: " + correction;
            assert !manager.isSignificantTimeChange(changed.plusSeconds(1),
                    (elapsedMillis + 1000) * 1_000_000, utc) : "Duplicate notification";
        }
        RestartManager manager = new RestartManager(start, 0, utc);
        for (int i = 1; i <= 3; i++) {
            assert !manager.isSignificantTimeChange(start.plusSeconds(i * 40),
                    i * 10_000_000_000L, utc) : "Small corrections must not accumulate";
        }
        ZoneId eastern = ZoneId.of("America/New_York");
        for (Instant transition : new Instant[] {Instant.parse("2026-03-08T07:00:00Z"),
                Instant.parse("2026-11-01T06:00:00Z")}) {
            manager = new RestartManager(transition.minusSeconds(10), 0, eastern);
            assert manager.isSignificantTimeChange(transition, 10_000_000_000L, eastern);
            assert !manager.isSignificantTimeChange(transition.plusSeconds(1), 11_000_000_000L, eastern);
        }
        // An ignored callback must return before touching CaptureManager or native user32.
        assert new RestartManager().callback(null, 0x1e, null, null).longValue() == 0;
    }
}
