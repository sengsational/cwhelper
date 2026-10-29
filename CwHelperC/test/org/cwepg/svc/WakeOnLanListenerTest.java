package org.cwepg.svc;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.concurrent.TimeUnit;

/** Run with -ea; avoids native Windows timers and autorun execution. */
public class WakeOnLanListenerTest {
    public static void main(String[] args) throws Exception {
        byte[] bytes = new byte[110];
        for (int i = 0; i < 6; i++) bytes[4 + i] = (byte) 0xff;
        for (int i = 0; i < 96; i++) bytes[10 + i] = (byte) (i % 6 + 1);
        assert WakeOnLanListener.isMagicPacket(new DatagramPacket(bytes, 4, 102));
        assert Arrays.equals(new byte[] {1, 2, 3, 4, 5, 6},
                WakeOnLanListener.getMagicPacketMac(new DatagramPacket(bytes, 4, 102)));
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
        final int[] accepted = {0};
        final boolean[] failLookup = {false};
        WakeOnLanListener filtered = new WakeOnLanListener() {
            protected boolean isLocalMac(byte[] mac) throws SocketException {
                if (failLookup[0]) throw new SocketException("test lookup failure");
                return Arrays.equals(mac, new byte[] {1, 2, 3, 4, 5, 6});
            }
            protected void schedule(Calendar current) { accepted[0]++; }
        };
        DatagramPacket packet = new DatagramPacket(bytes, 4, 102,
                InetAddress.getLoopbackAddress(), 9);
        filtered.processPacket(packet); // Malformed repetition.
        assert accepted[0] == 0;
        for (int i = 0; i < 96; i++) bytes[10 + i] = (byte) (i % 6 + 11);
        filtered.processPacket(packet); // Valid packet for another machine.
        assert accepted[0] == 0;
        for (int i = 0; i < 96; i++) bytes[10 + i] = (byte) (i % 6 + 1);
        failLookup[0] = true;
        filtered.processPacket(packet);
        assert accepted[0] == 0;
        failLookup[0] = false;
        filtered.processPacket(packet); // Rejected packets must not start the duplicate timer.
        assert accepted[0] == 1;
        filtered.processPacket(packet);
        assert accepted[0] == 1;
        filtered.shutdown();
        System.out.println("WakeOnLanListenerTest passed");
    }
}
