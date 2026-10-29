package com.cwepg.test;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;

/**
 * Sends one magic packet to test WakeOnLanListener. Run as a Java Application.
 * Arguments: [destination address] [MAC address] [UDP port].
 * Defaults: 127.0.0.1 02:00:00:00:00:01 9.
 * For a real wakeup, supply the LAN broadcast address and target computer's MAC.
 */
public class TestWakeOnLan {
    public static void main(String[] args) throws IOException {
        if (args.length > 3) {
            throw new IllegalArgumentException(
                    "Usage: TestWakeOnLan [destination address] [MAC address] [UDP port]");
        }
        String destination = args.length > 0 ? args[0] : "127.0.0.1";
        //String mac = args.length > 1 ? args[1] : "02:00:00:00:00:01"; //00-CE-39-D1-49-1C This is the HTPC
        String mac = args.length > 1 ? args[1] : "00:CE:39:D1:49:1C"; //00-CE-39-D1-49-1C This is the HTPC
        //String mac = args.length > 1 ? args[1] : "00:CE:39:D1:01:01"; //00-CE-39-D1-01-01 This is NOT the HTPC
        int port = args.length > 2 ? Integer.parseInt(args[2]) : 9;
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("UDP port must be between 1 and 65535");
        }
        byte[] bytes = createMagicPacket(mac);
        DatagramPacket packet = new DatagramPacket(bytes, bytes.length,
                InetAddress.getByName(destination), port);
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            socket.send(packet);
        }
        System.out.println("Sent " + bytes.length + " byte Wake-on-LAN packet for "
                + mac + " to " + destination + ":" + port);
    }

    /** Six FF bytes followed by sixteen copies of the six-byte MAC address. */
    public static byte[] createMagicPacket(String macAddress) {
        if (macAddress == null || !macAddress.matches(
                "(?i)[0-9a-f]{2}([:-])[0-9a-f]{2}(\\1[0-9a-f]{2}){4}")) {
            throw new IllegalArgumentException(
                    "MAC address must contain six hexadecimal pairs, e.g. 02:00:00:00:00:01");
        }
        String[] pairs = macAddress.split("[:-]");
        byte[] mac = new byte[6];
        for (int i = 0; i < mac.length; i++) {
            mac[i] = (byte) Integer.parseInt(pairs[i], 16);
        }
        byte[] packet = new byte[102];
        Arrays.fill(packet, 0, 6, (byte) 0xff);
        for (int offset = 6; offset < packet.length; offset += mac.length) {
            System.arraycopy(mac, 0, packet, offset, mac.length);
        }
        return packet;
    }
}
