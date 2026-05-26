# CNET101: Computer Networks
## Project Report — Spring 2026

---

**Course:** CNET101 Computer Networks  
**Submission Date:** May 2026  
**GitHub:** https://github.com/khalidstark/GIUNet_19005470_SS26

---

## Team Information

| Name | Student ID | Role |
|------|-----------|------|
| Ahmed Masoud | 19005470 | Team Leader |
| Khaled Osam | 19010617 | Member |
| Ahmed Fouad | 19009026 | Member |
| Yousef Hany | 19009431 | Member |

---

## 1. Port Number Derivation

The port number is derived from the last 4 digits of the team leader's ID.

Team Leader ID: **19005470**  
Last 4 digits: **5470**  
Since 5470 >= 1024 (not a reserved port), we use it directly.

**Port = 5470**

---

## 2. HELLO / OK Handshake String Derivation

The handshake string is built using the format: `HELLO-GIU-<ID>-<sum>`

The sum is calculated from the last digit of each team member's ID:

| Member | ID | Last Digit |
|--------|----|-----------|
| Ahmed Masoud | 19005470 | 0 |
| Khaled Osam | 19010617 | 7 |
| Ahmed Fouad | 19009026 | 6 |
| Yousef Hany | 19009431 | 1 |

Sum = 0 + 7 + 6 + 1 = **14**

**HELLO string: `HELLO-GIU-19005470-14`**  
**OK string: `OK-GIU-19005470-14`**

---

## 3. Protocol Design Choices

### 3.1 Retransmission Timeout

We chose a timeout value of **3000 milliseconds (3 seconds)**. The reason for this is that 3 seconds give enough time for the packet to travel across the network and for the ACK to come back, even on a slow or congested connection. We also thought that a shorter timeout like 1 second would cause too many false retransmissions on a normal network, while a longer one like 10 seconds would make the chat feel very slow if something actually goes wrong.

In our implementation, after sending a data packet the sender thread waits on a `synchronized` block using `ackLock.wait(remaining)` where `remaining` is recalculated each loop iteration against a fixed deadline. This way we dont over-wait if the ACK arrives just before the deadline.

If after 2 attempts (first send + one retry) no ACK is received, we print `[CONNECTION LOST]` and close the socket.

### 3.2 Handling ACK Wait While Reading Keyboard

This was actually one of the harder parts to think about. The problem is that the main thread is blocked on `stdin.readLine()` waiting for the user to type, while at the same time we need to receive ACKs from the other side.

We solved this by using **two threads**:

- **Main thread** — reads from keyboard and sends data packets
- **Receiver thread (daemon)** — runs in background, receives all incoming packets

When an ACK arrives, the receiver thread puts the ACK number into the shared variable `lastAck` and calls `ackLock.notifyAll()` to wake up the main thread which is waiting inside the `synchronized` block. This way the keyboard reading and ACK waiting dont block each other at all.

The receiver thread is set as a daemon thread so it automatically stops when the main thread exits.

---

## 4. Debugging Story

### Symptom
During our first test between two machines, Ahmed's client was sending the HELLO packet (we could see it in the GIULogger output), but Khalid's server never responded. The server was just stuck printing "Listening on port 5470..." and nothing happened.

### What We First Thought
We thought at first the problem was in the code — maybe the server wasn't binding to the right port or maybe the HELLO string didnt match exactly. We checked the strings multiple times and they were identical.

### How We Diagnosed It
We opened Wireshark on Khalid's machine and filtered by `udp.port == 5470`. We could see that **no packets were arriving at all** — so the packets were being sent by Ahmed but never reaching Khalid's machine. This told us it was a network issue, not a code issue.

We then checked and found that the two laptops were on different networks — Khalid was connected to his home WiFi and Ahmed was connected to a hotspot. After connecting both to the same WiFi network, we ran Wireshark again and saw the UDP packets arriving correctly.

### The Fix
The fix was simply to make sure both machines are on the **same network**. Once we did that, the HELLO packet arrived, the server replied with OK, and the chat worked. We also had to update `SERVER_IP` in `GIUClient.java` to Khalid's current IP address on that network (`192.168.18.149`), because DHCP had given him a different IP than before.

---

## 5. VLSM Subnet Table

### Base Network Derivation

Leader ID: 19005470  
Digit pairs: 19 . 00 . 54 . 0  
Raw network: 19.0.54.0/22  
Aligned /22 block: **19.0.52.0/22** (covers 19.0.52.0 – 19.0.55.255, 1022 usable hosts)

### Subnets Ordered by Size (Largest First)

---

**Subnet C — Student Housing (200 hosts needed)**

Step 1: Find smallest power of 2 greater than 200  
2^7 = 128 → not enough  
2^8 = 256 → 256 - 2 = 254 usable ✓ → prefix length = 32 - 8 = **/24**

Step 2: Assign from start of /22 block  
Network address: **19.0.52.0**  
Binary: `00010011 . 00000000 . 00110100 . 00000000`  
Subnet mask: `11111111 . 11111111 . 11111111 . 00000000` = 255.255.255.0  
First IP: 19.0.52.1 | Last IP: 19.0.52.254 | Broadcast: 19.0.52.255

---

**Subnet B — Student Lab (100 hosts needed)**

Step 1: Find smallest power of 2 greater than 100  
2^6 = 64 → not enough  
2^7 = 128 → 128 - 2 = 126 usable ✓ → prefix length = 32 - 7 = **/25**

Step 2: Assign after Subnet C ends (19.0.52.255 + 1 = 19.0.53.0)  
Network address: **19.0.53.0**  
Binary: `00010011 . 00000000 . 00110101 . 00000000`  
Subnet mask: `11111111 . 11111111 . 11111111 . 10000000` = 255.255.255.128  
First IP: 19.0.53.1 | Last IP: 19.0.53.126 | Broadcast: 19.0.53.127

---

**Subnet A — Staff Offices (50 hosts needed)**

Step 1: Find smallest power of 2 greater than 50  
2^5 = 32 → not enough  
2^6 = 64 → 64 - 2 = 62 usable ✓ → prefix length = 32 - 6 = **/26**

Step 2: Assign after Subnet B ends (19.0.53.127 + 1 = 19.0.53.128)  
Network address: **19.0.53.128**  
Binary: `00010011 . 00000000 . 00110101 . 10000000`  
Subnet mask: `11111111 . 11111111 . 11111111 . 11000000` = 255.255.255.192  
First IP: 19.0.53.129 | Last IP: 19.0.53.190 | Broadcast: 19.0.53.191

---

**Subnet E — Server DMZ (10 hosts needed)**

Step 1: Find smallest power of 2 greater than 10  
2^3 = 8 → not enough  
2^4 = 16 → 16 - 2 = 14 usable ✓ → prefix length = 32 - 4 = **/28**

Step 2: Assign after Subnet A ends (19.0.53.191 + 1 = 19.0.53.192)  
Network address: **19.0.53.192**  
Binary: `00010011 . 00000000 . 00110101 . 11000000`  
Subnet mask: `11111111 . 11111111 . 11111111 . 11110000` = 255.255.255.240  
First IP: 19.0.53.193 | Last IP: 19.0.53.206 | Broadcast: 19.0.53.207

---

**Subnet D — Router WAN Link (2 hosts needed)**

Step 1: Find smallest power of 2 greater than 2  
2^2 = 4 → 4 - 2 = 2 usable ✓ → prefix length = 32 - 2 = **/30**

Step 2: Assign after Subnet E ends (19.0.53.207 + 1 = 19.0.53.208)  
Network address: **19.0.53.208**  
Binary: `00010011 . 00000000 . 00110101 . 11010000`  
Subnet mask: `11111111 . 11111111 . 11111111 . 11111100` = 255.255.255.252  
First IP: 19.0.53.209 | Last IP: 19.0.53.210 | Broadcast: 19.0.53.211

---

### Summary Table

| Subnet | Purpose | Hosts | Prefix | Network | Mask | First IP | Last IP | Broadcast |
|--------|---------|-------|--------|---------|------|----------|---------|-----------|
| C | Student Housing | 200 | /24 | 19.0.52.0 | 255.255.255.0 | 19.0.52.1 | 19.0.52.254 | 19.0.52.255 |
| B | Student Lab | 100 | /25 | 19.0.53.0 | 255.255.255.128 | 19.0.53.1 | 19.0.53.126 | 19.0.53.127 |
| A | Staff Offices | 50 | /26 | 19.0.53.128 | 255.255.255.192 | 19.0.53.129 | 19.0.53.190 | 19.0.53.191 |
| E | Server DMZ | 10 | /28 | 19.0.53.192 | 255.255.255.240 | 19.0.53.193 | 19.0.53.206 | 19.0.53.207 |
| D | WAN Link | 2 | /30 | 19.0.53.208 | 255.255.255.252 | 19.0.53.209 | 19.0.53.210 | 19.0.53.211 |

---

## 6. Routing Tables

### Router 1

Router 1 is connected directly to Subnets B, A, E, and D. It needs one static route to reach Subnet C (which is behind Router 2).

| Network | Mask | Next Hop | Type |
|---------|------|----------|------|
| 19.0.53.0 | 255.255.255.128 | — | directly connected (Gi0/0) |
| 19.0.53.128 | 255.255.255.192 | — | directly connected (Gi0/2) |
| 19.0.53.192 | 255.255.255.240 | — | directly connected (Gi0/1) |
| 19.0.53.208 | 255.255.255.252 | — | directly connected (Se0/3/0) |
| 19.0.52.0 | 255.255.255.0 | 19.0.53.210 | static |

**Static route command entered:**
```
ip route 19.0.52.0 255.255.255.0 19.0.53.210
```

### Router 2

Router 2 is connected directly to Subnet C and the WAN link (Subnet D). It needs static routes to reach all three subnets behind Router 1.

| Network | Mask | Next Hop | Type |
|---------|------|----------|------|
| 19.0.52.0 | 255.255.255.0 | — | directly connected (Gi0/0) |
| 19.0.53.208 | 255.255.255.252 | — | directly connected (Se0/3/0) |
| 19.0.53.0 | 255.255.255.128 | 19.0.53.209 | static |
| 19.0.53.128 | 255.255.255.192 | 19.0.53.209 | static |
| 19.0.53.192 | 255.255.255.240 | 19.0.53.209 | static |

**Static route commands entered:**
```
ip route 19.0.53.0 255.255.255.128 19.0.53.209
ip route 19.0.53.128 255.255.255.192 19.0.53.209
ip route 19.0.53.192 255.255.255.240 19.0.53.209
```

---

## 7. Screenshots

*(Insert screenshots here in the submitted PDF)*

**Screenshot 1 — Task 2:** Ping from Housing-PC-1 (19.0.52.2) to Web Server (19.0.53.194) showing successful replies across both routers.

**Screenshot 2 — Task 3:** Web browser on a Lab PC showing the team webpage loaded from the HTTP server at 19.0.53.194.

**Screenshot 3 — Task 4:** Simulation mode showing the packet path from LAB1 → Switch A → Router 1 → Switch C → GIU Net Server, with the event list visible on the right.

**Screenshot 4 — Wireshark:** Wireshark capture showing the UDP packets for the GIU-Net handshake (HELLO and OK strings visible in the packet data).

---

## 8. Conclusion

In this project we implemented a full UDP chat application in Java that follows the GIU-Net protocol, and also designed and simulated a campus network using Cisco Packet Tracer. Working on both parts at the same time helped us better understand how protocols actually work on real networks. The Wireshark capture was specially useful because it let us see our own packets in real time and confirm the byte structure was correct. The VLSM part was challanging at first but once we understood the idea of fitting subnets from largest to smallest it became straightforward.
