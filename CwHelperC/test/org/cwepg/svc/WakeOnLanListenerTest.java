package org.cwepg.svc;

import java.net.DatagramPacket;
import java.util.Calendar;
import java.util.concurrent.TimeUnit;

/** Run with -ea; avoids native Windows timers and autorun execution. */
public class WakeOnLanListenerTest {
    public static void main(String[] args) {
        byte[] bytes = new byte[110];
        for (int i = 0; i < 6; i++) bytes[4 + i] = (byte) 0xff;
        for (int i = 0; i < 96; i++) bytes[10 + i] = (byte) (i % 6 + 1);
        assert WakeOnLanListener.isMagicPacket(new DatagramPacket(bytes, 4, 102));
        assert !WakeOnLanListener.isMagicPacket(new DatagramPacket(bytes, 4, 101));
        bytes[105] = 0;
        assert !WakeOnLanListener.isMagicPacket(new DatagramPacket(bytes, bytes.length));
        final int[] scheduled = {0};
        WakeOnLanListener listener = new WakeOnLanListener() {
            protected void schedule(Calendar current) {
                assert Math.abs(current.getTimeInMillis() - System.currentTimeMillis()) < 1000;
                scheduled[0]++;
            }
        };
        listener.handleSignal(0);
        listener.handleSignal(TimeUnit.SECONDS.toNanos(59));
        listener.handleSignal(TimeUnit.SECONDS.toNanos(60));
        assert scheduled[0] == 1;
        listener.handleSignal(TimeUnit.SECONDS.toNanos(120));
        assert scheduled[0] == 2;
        listener.shutdown();
        listener.handleSignal(TimeUnit.SECONDS.toNanos(180));
        listener.run(); // Shutdown before startup must not open a socket.
        assert scheduled[0] == 2;
        System.out.println("WakeOnLanListenerTest passed");
    }
}
