// GIUClient.java
// CNET101 Spring 2026 — GIU-Net UDP Chat Client
//
// Team Leader ID : 19005470
// Port derivation : last 4 digits of Leader ID = 5470, which is >= 1024, so port = 5470
// HELLO string   : HELLO-GIU-19005470-14  (sum of last digits: 0+7+6+1 = 14)
// OK string      : OK-GIU-19005470-14

import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class GIUClient {

    // -----------------------------------------------------------------------
    // Constants
    // -----------------------------------------------------------------------
    static final int    PORT        = 5470;

    // TODO: Replace with the server machine's IPv4 address before the live two-machine run.
    //       Run `ifconfig` (Mac/Linux) or `ipconfig` (Windows) on the server machine.
    static final String SERVER_IP   = "172.20.10.4"; // Khalid's machine (server) — run GIUServer.java here

    static final String HELLO_MSG   = "HELLO-GIU-19005470-14";
    static final String OK_EXPECTED = "OK-GIU-19005470-14";
    static final int    TIMEOUT_MS  = 3000;   // 3-second ACK wait per spec
    static final int    BUFFER_SIZE = 65535;

    // -----------------------------------------------------------------------
    // Shared state between sender (main) thread and receiver thread
    // -----------------------------------------------------------------------
    static volatile boolean running = false;
    static DatagramSocket   socket;
    static InetAddress      serverAddr;

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
     * Byte.toUnsignedInt() is required because Java's byte is signed.
     */
    static int unpackSeq(byte[] pkt) {
        return (Byte.toUnsignedInt(pkt[0]) << 8) | Byte.toUnsignedInt(pkt[1]);
    }

    /** Read the payload starting at byte 2. */
    static String unpackPayload(byte[] pkt, int length) {
        return new String(pkt, 2, length - 2, StandardCharsets.UTF_8);
    }

    /** Send a datagram to the server and log it. */
    static void sendPacket(byte[] data) throws IOException {
        DatagramPacket dp = new DatagramPacket(data, data.length, serverAddr, PORT);
        socket.send(dp);
        GIULogger.log("SEND", data, System.currentTimeMillis());
    }

    // -----------------------------------------------------------------------
    // Main
    // -----------------------------------------------------------------------

    public static void main(String[] args) throws Exception {

        serverAddr = InetAddress.getByName(SERVER_IP);
        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));

        // Per spec: the socket must NOT be opened until the user types CONNECT.
        System.out.println("[CLIENT] Type CONNECT to initiate the connection.");
        while (true) {
            String cmd = stdin.readLine();
            if (cmd != null && cmd.trim().equals("CONNECT")) break;
        }

        // ---- Open socket and perform handshake ----
        socket = new DatagramSocket();
        byte[] helloPkt = pack(0, HELLO_MSG);
        sendPacket(helloPkt);
        System.out.println("[CLIENT] HELLO sent. Waiting for server...");

        byte[] buf = new byte[BUFFER_SIZE];
        DatagramPacket response = new DatagramPacket(buf, buf.length);
        socket.receive(response);
        byte[] rawResp = Arrays.copyOf(response.getData(), response.getLength());
        GIULogger.log("RECV", rawResp, System.currentTimeMillis());

        String respPayload = unpackPayload(rawResp, rawResp.length);

        if (!respPayload.equals(OK_EXPECTED)) {
            System.out.println("[CLIENT] Connection rejected: " + respPayload);
            socket.close();
            return;
        }

        System.out.println("[CLIENT] Connected! Type a message and press Enter. Type EXIT to quit.");
        running = true;

        // ---- Receiver thread ----
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
                        int ackNum = Integer.parseInt(p.substring(4).trim());
                        synchronized (ackLock) {
                            lastAck = ackNum;
                            ackLock.notifyAll();
                        }
                    } else {
                        // DATA packet: print and acknowledge
                        System.out.println("[RECEIVED] " + p);
                        byte[] ack = pack(0, "ACK:" + s);
                        sendPacket(ack);
                    }

                } catch (IOException e) {
                    if (running) System.out.println("[CLIENT] Receiver IO error: " + e.getMessage());
                } catch (Throwable t) {
                    if (running) System.out.println("[CLIENT] Receiver crashed: " + t);
                }
            }
        });
        receiver.setDaemon(true);
        receiver.start();

        // ---- Sender loop (main thread reads stdin) ----
        int seqNum = 1;

        while (running) {
            String line;
            try {
                line = stdin.readLine();
            } catch (IOException e) {
                break;
            }
            if (line == null || !running) break;

            if (line.trim().equals("EXIT")) {
                byte[] exitPkt = pack(0, "EXIT");
                sendPacket(exitPkt);
                System.out.println("[Connection closed by remote.]");
                running = false;
                socket.close();
                break;
            }

            byte[] dataPkt = pack(seqNum, line);
            boolean acked  = false;

            for (int attempt = 0; attempt < 2 && !acked && running; attempt++) {
                if (attempt == 1) System.out.println("[TIMEOUT] Retrying...");
                sendPacket(dataPkt);

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

            seqNum++;
        }

        if (!socket.isClosed()) socket.close();
        System.exit(0);
    }
}
