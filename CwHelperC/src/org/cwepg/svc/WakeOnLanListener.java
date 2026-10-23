package org.cwepg.svc;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import org.cwepg.hr.AdHocWakeupEvent;
import org.cwepg.hr.CaptureManager;

/** Receives UDP magic packets until CaptureManager shuts down. */
public class WakeOnLanListener implements Runnable {
    private volatile boolean stopped;
    private DatagramSocket socket;
    private boolean receivedSignal;
    private long lastSignal;

    public void run() {
        try {
            synchronized (this) {
                if (stopped) return;
                socket = new DatagramSocket(9);
            }
            System.out.println(new Date() + " WakeOnLanListener listening on UDP port 9.");
            byte[] buffer = new byte[65535];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            while (!stopped) {
                packet.setLength(buffer.length);
                socket.receive(packet);
                if (isMagicPacket(packet)) handleSignal(System.nanoTime());
            }
        } catch (IOException e) {
            if (!stopped) System.out.println(new Date() + " WakeOnLanListener failed: " + e);
        } finally {
            shutdown();
        }
    }

    synchronized void handleSignal(long now) {
        if (stopped) return;
        boolean duplicate = receivedSignal && now - lastSignal < TimeUnit.SECONDS.toNanos(60);
        receivedSignal = true;
        lastSignal = now;
        if (duplicate) return;
        Calendar current = Calendar.getInstance();
        try {
            schedule(current);
        } catch (RuntimeException e) {
            System.out.println(new Date() + " WakeOnLanListener could not schedule wakeup: " + e);
        }
    }

    protected void schedule(Calendar current) {
        // Scheduling also requests the CaptureManager interrupt.
        CaptureManager.scheduleAdHocWakeupEvent(new AdHocWakeupEvent(
                Integer.toString(current.get(Calendar.HOUR_OF_DAY)),
                Integer.toString(current.get(Calendar.MINUTE)), "10", "-wol"));
    }

    static boolean isMagicPacket(DatagramPacket packet) {
        byte[] bytes = packet.getData();
        int end = packet.getOffset() + packet.getLength();
        for (int start = packet.getOffset(); start + 102 <= end; start++) {
            int i = 0;
            while (i < 6 && (bytes[start + i] & 0xff) == 0xff) i++;
            if (i != 6) continue;
            for (i = 6; i < 102; i++) {
                if (bytes[start + i] != bytes[start + 6 + (i - 6) % 6]) break;
            }
            if (i == 102) return true;
        }
        return false;
    }

    public synchronized void shutdown() {
        stopped = true;
        if (socket != null) socket.close(); // Unblock receive immediately.
    }
}
