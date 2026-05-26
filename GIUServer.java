// GIUServer.java
// CNET101 Spring 2026 — GIU-Net UDP Chat Server
//
// Team Leader ID : 19005470
// Port derivation : last 4 digits of Leader ID = 5470, which is >= 1024, so port = 5470
// HELLO string   : HELLO-GIU-19005470-14  (sum of last digits: 0+7+6+1 = 14)
// OK string      : OK-GIU-19005470-14

import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class GIUServer {

    // -----------------------------------------------------------------------
    // Constants
    // -----------------------------------------------------------------------
    static final int    PORT           = 5470;
    static final String HELLO_EXPECTED = "HELLO-GIU-19005470-14";
    static final String OK_REPLY       = "OK-GIU-19005470-14";
    static final int    TIMEOUT_MS     = 3000;   // 3-second ACK wait per spec
    static final int    BUFFER_SIZE    = 65535;  // max UDP payload

    // -----------------------------------------------------------------------
    // Shared state between sender (main) thread and receiver thread
    // -----------------------------------------------------------------------
    static volatile boolean running = false;
    static DatagramSocket   socket;
    static InetAddress      clientAddr;
    static int              clientPort;

    // The receiver writes here when an ACK arrives; the sender waits on ackLock.
    static final Object ackLock = new Object();
    static volatile int lastAck = 0;

    // -----------------------------------------------------------------------
    // Packet helpers
    // -----------------------------------------------------------------------

    /**
     * Build a GIU-Net packet: 2-byte big-endian sequence number followed by
     * the UTF-8 encoded payload.
     */
    static byte[] pack(int seq, String payload) {
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
        byte[] pkt = new byte[2 + payloadBytes.length];
        pkt[0] = (byte) ((seq >> 8) & 0xFF);   // high byte of seq
        pkt[1] = (byte) (seq & 0xFF);           // low byte of seq
        System.arraycopy(payloadBytes, 0, pkt, 2, payloadBytes.length);
        return pkt;
    }

    /**
     * Read the 16-bit big-endian sequence number from bytes 0-1.
     * Byte.toUnsignedInt() is required because Java's byte is signed (-128..127).
     */
    static int unpackSeq(byte[] pkt) {
        return (Byte.toUnsignedInt(pkt[0]) << 8) | Byte.toUnsignedInt(pkt[1]);
    }

    /** Read the payload starting at byte 2. */
    static String unpackPayload(byte[] pkt, int length) {
        return new String(pkt, 2, length - 2, StandardCharsets.UTF_8);
    }

    /** Send a datagram to the connected client and log it. */
    static void sendPacket(byte[] data) throws IOException {
        DatagramPacket dp = new DatagramPacket(data, data.length, clientAddr, clientPort);
        socket.send(dp);
        GIULogger.log("SEND", data, System.currentTimeMillis());
    }

    // -----------------------------------------------------------------------
    // Main
    // -----------------------------------------------------------------------

    public static void main(String[] args) throws Exception {

        socket = new DatagramSocket(PORT);
        System.out.println("[SERVER] Listening on port " + PORT);
        System.out.println("[SERVER] Waiting for connection...");

        // ---- Handshake ----
        byte[] buf = new byte[BUFFER_SIZE];
        DatagramPacket incoming = new DatagramPacket(buf, buf.length);
        socket.receive(incoming);

        // Trim to actual received length before passing to logger and parsers
        byte[] rawHello = Arrays.copyOf(incoming.getData(), incoming.getLength());
        GIULogger.log("RECV", rawHello, System.currentTimeMillis());

        clientAddr = incoming.getAddress();
        clientPort = incoming.getPort();

        int    seq     = unpackSeq(rawHello);
        String payload = unpackPayload(rawHello, rawHello.length);

        // Reject if seq != 0 or HELLO string doesn't match exactly
        if (seq != 0 || !payload.equals(HELLO_EXPECTED)) {
            byte[] rejected = pack(0, "REJECTED");
            sendPacket(rejected);
            System.out.println("[SERVER] REJECTED — wrong HELLO: \"" + payload + "\"");
            socket.close();
            return;
        }

        System.out.println("[SERVER] Received: " + payload);
        byte[] okPkt = pack(0, OK_REPLY);
        sendPacket(okPkt);
        System.out.println("[SERVER] Handshake complete.");
        System.out.println("[SERVER] Connection established!");

        running = true;

        // ---- Receiver thread ----
        // Handles all incoming packets: ACKs (signals sender thread) and DATA (prints + sends ACK).
        Thread receiver = new Thread(() -> {
            byte[] rbuf = new byte[BUFFER_SIZE];
            while (running) {
                try {
                    DatagramPacket pkt = new DatagramPacket(rbuf, rbuf.length);
                    socket.receive(pkt);
                    byte[] raw = Arrays.copyOf(pkt.getData(), pkt.getLength());
                    GIULogger.log("RECV", raw, System.currentTimeMillis());

                    int    s = unpackSeq(raw);
                    String p = unpackPayload(raw, raw.length);

                    if (p.equals("EXIT")) {
                        System.out.println("[Connection closed by remote.]");
                        running = false;
                        socket.close();
                        return;
                    }

                    if (p.startsWith("ACK:")) {
                        // Unblock the sender thread waiting for this ACK
                        int ackNum = Integer.parseInt(p.substring(4).trim());
                        synchronized (ackLock) {
                            lastAck = ackNum;
                            ackLock.notifyAll();
                        }
                    } else {
                        // DATA packet: print it and acknowledge immediately
                        System.out.println("[RECEIVED] " + p);
                        byte[] ack = pack(0, "ACK:" + s);
                        sendPacket(ack);
                    }

                } catch (IOException e) {
                    if (running) System.out.println("[SERVER] Receiver IO error: " + e.getMessage());
                } catch (Throwable t) {
                    if (running) System.out.println("[SERVER] Receiver crashed: " + t);
                }
            }
        });
        receiver.setDaemon(true);
        receiver.start();

        // ---- Sender loop (main thread reads stdin) ----
        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
        int seqNum = 1; // DATA sequence numbers start at 1

        while (running) {
            String line;
            try {
                line = stdin.readLine();
            } catch (IOException e) {
                break;
            }
            if (line == null) {
                // stdin closed (pipe/test) — park here until receiver signals done
                while (running) { try { Thread.sleep(100); } catch (InterruptedException ie) { break; } }
                break;
            }
            if (!running) break;

            if (line.trim().equals("EXIT")) {
                byte[] exitPkt = pack(0, "EXIT");
                sendPacket(exitPkt);
                System.out.println("[Connection closed by remote.]");
                running = false;
                socket.close();
                break;
            }

            // Build the DATA packet for this sequence number.
            // Retransmissions reuse the same packet (same seqNum — not incremented on retry).
            byte[] dataPkt = pack(seqNum, line);
            boolean acked  = false;

            for (int attempt = 0; attempt < 2 && !acked && running; attempt++) {
                if (attempt == 1) System.out.println("[TIMEOUT] Retrying...");
                sendPacket(dataPkt);

                // Wait up to 3 seconds for ACK:<seqNum> from the receiver thread
                final int expected = seqNum;
                synchronized (ackLock) {
                    long deadline = System.currentTimeMillis() + TIMEOUT_MS;
                    while (lastAck < expected && running) {
                        long remaining = deadline - System.currentTimeMillis();
                        if (remaining <= 0) break;
                        try {
                            ackLock.wait(remaining);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                    acked = (lastAck >= expected);
                }
            }

            if (!acked) {
                System.out.println("[CONNECTION LOST]");
                running = false;
                if (!socket.isClosed()) socket.close();
                break;
            }

            seqNum++; // Only increment after a confirmed ACK
        }

        if (!socket.isClosed()) socket.close();
        System.exit(0);
    }
}
