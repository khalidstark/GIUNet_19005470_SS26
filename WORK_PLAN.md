# GIU-Net Project — Work Plan

## Context

This plan is for the **CNET101 Spring 2026 Project** at German International University.

The project has two parts:

1. **GIU-Net UDP Chat** — a Java client/server pair implementing a reliable, connection-oriented chat protocol on top of raw UDP.
2. **Campus Network Simulation** — a Cisco Packet Tracer topology with VLSM subnetting, static routing, an HTTP server, and an optional ACL.

**Hard deadline:** Sunday, 31 May 2026, 23:59.
**Evaluation week:** 2–8 June 2026.

**Critical rule from the spec:** AI-generated code is prohibited and "trivially detectable" via the binary session log, the Wireshark capture (which embeds your real team IDs), and a live modification challenge at evaluation where you must change the protocol on the spot and explain every line of your code. This plan therefore lays out the work — concepts to learn, components to build, things to verify — but **you write the code yourself**. Claude tutors and reviews along the way.

**Intended outcome:** a complete, verifiable submission ZIP `GIUNet_19005470_SS26.zip` and a team that can answer any question about it during the live evaluation.

---

## Milestone 0 — Team Setup (Deadline: Mon 11 May 2026)

1. Form a team of exactly 4 members from the same tutorial group.
2. Identify the Team Leader = lowest student ID.
3. Compute and write down on paper:
   - **Sum** = sum of last digits of all 4 student IDs.
   - **HELLO string** = `HELLO-GIU-<LeaderID>-<Sum>`
   - **OK string** = `OK-GIU-<LeaderID>-<Sum>`
   - **Server port** = last 4 digits of LeaderID, +1000 if below 1024.
   - **Base IP** for Part 2: digit pairs of LeaderID → `D1D2.D3D4.D5D6.0/22`; subtract 100 from any octet >255.
4. Register the team via the CMS form.

**Verification:** Sum, port, and base IP triple-checked by every team member independently — these values are baked into all subsequent work.

See `TEAM_INFO.md` for this team's resolved values.

---

## Milestone 1 — Foundations (Concepts before code)

Before writing any Java, every member should be able to explain (this is what the live evaluation tests):

- Why UDP is "unreliable" — no ordering, no delivery guarantee, no connection state.
- What TCP gives you that UDP doesn't (handshake, ACKs, retransmission, ordering, flow control).
- How a 2-byte big-endian unsigned integer is laid out in memory and on the wire.
- Why Java's signed `byte` requires `Byte.toUnsignedInt()` when interpreting raw network bytes.
- What a socket really is, and what `DatagramSocket` / `DatagramPacket` represent.
- Why sender and receiver need to run on separate threads here (a single-threaded design blocks on `stdin.readLine()` and can't receive).

**Verification:** Each member can answer all of the above in their own words without notes.

---

## Milestone 2 — GIU-Net Protocol Design on Paper

Before any code, draw the protocol on paper:

1. The **handshake state machine** (client states: `IDLE → SENT_HELLO → CONNECTED / REJECTED`; server states: `LISTENING → CONNECTED`).
2. The **DATA send loop**: sequence numbers starting at 1, increment per DATA packet (**NOT** per send — retransmissions reuse the same seqnum).
3. The **ACK/retransmit timer**: 3-second wait → retransmit once → 3-second wait → CONNECTION LOST.
4. **Teardown** via EXIT.

Then decide the **threading model**: how does the sender thread wait for an ACK without blocking the receiver thread? (Hint: shared state + synchronisation. Discuss as a team and pick the approach you can all defend in the live evaluation.)

**Deliverable from this milestone:** a one-page hand-drawn protocol diagram you keep in your notes. The Report's "Protocol design choices" section is written from this.

---

## Milestone 3 — Part 1 Build: `GIUServer.java` and `GIUClient.java`

Implement in this order (each step verified before moving on):

1. **Packet helpers** — write methods that pack `(seq, payload)` into `byte[]` and unpack `byte[]` back. Big-endian. Test against hand-computed bytes.
2. **Handshake** — client sends HELLO (Seq=0), server validates exact string, replies OK or REJECTED. Verify on two terminals (still localhost for now — final run will be on two machines).
3. **Bi-directional chat single-threaded first** — get one direction working. Print received messages with `[RECEIVED]` prefix.
4. **Add second thread** — separate sender and receiver. Verify both directions concurrently.
5. **ACK + retransmit** — sender waits 3s for `ACK:<seqnum>`, retransmits once on timeout printing `[TIMEOUT] Retrying...`, exits with `[CONNECTION LOST]` if still no ACK. Test by killing one side mid-chat.
6. **EXIT teardown** — type `EXIT` → send EXIT packet → both sides print `[Connection closed by remote.]` and exit cleanly.
7. **GIULogger.log() integration** — call after every send AND every receive, on both client and server. Confirm `giunet_session.log` is being written.
8. **Port hardcoded + derivation comment** — at the top of both files, comment showing the port derivation from the LeaderID.
9. **Server IP hardcoded in `GIUClient.java`** — placeholder until the live run on two machines.

**Files to produce:** `GIUServer.java`, `GIUClient.java`, both fully commented.
**Files to reuse, not modify:** `GIULogger.class` (provided by the course — compile alongside).

---

## Milestone 4 — Live Run on Two Physical Machines + Wireshark

This produces two of the required deliverables (`giunet_session.log` and the `.pcapng`).

1. Set up a WLAN or mobile hotspot. Connect machine A (server) and machine B (client) to it.
2. On machine A: `ifconfig` / `ipconfig` → note the IPv4 address. Hardcode it into `GIUClient.java`.
3. Recompile both sides. Copy `GIUClient.java` (+ compiled `GIULogger.class`) to machine B.
4. Start Wireshark on either machine. Filter: `udp.port == 5470`.
5. Run a session:
   - Client types `CONNECT` → HELLO goes out.
   - Server replies OK.
   - Exchange **at least 5 chat messages in both directions** (the log deliverable requires ≥5).
   - Confirm ACKs appear for every DATA packet.
   - One side types `EXIT` to close cleanly.
6. Save the Wireshark capture as `.pcapng`.
7. Grab the `giunet_session.log` produced on whichever machine you ran from (the spec lets you submit either side's log).
8. Take an annotated screenshot of the Wireshark capture labelling HELLO, OK, DATA, and ACK packets.

**Verification:** The capture must clearly show ≥3 DATA + ≥3 ACK packets, the HELLO string visible in plain text, and the OK reply. If anything's missing, re-run.

---

## Milestone 5 — Part 2: VLSM Subnet Table (on paper, before Packet Tracer)

Using the base `19.0.54.0/22` (1022 usable hosts) — **see alignment note in `TEAM_INFO.md`**.

Allocate **largest-first** into:

| Subnet | Purpose         | Required Hosts | Needed Prefix    |
|--------|-----------------|---------------:|------------------|
| C      | Student Housing | 200            | /24 (254 usable) |
| B      | Student Lab     | 100            | /25 (126 usable) |
| A      | Staff Offices   | 50             | /26 (62 usable)  |
| E      | Server DMZ      | 10             | /28 (14 usable)  |
| D      | Router WAN Link | 2              | /30 (2 usable)   |

For each subnet, compute by hand and tabulate:
- Network Address
- Subnet Mask (dotted decimal)
- Prefix Length
- First Usable IP
- Last Usable IP
- Broadcast Address
- Device IP assignments

You may check with an online VLSM calculator, but the report must show the calculations done by hand.

**Verification:** Run the same numbers through the online calculator. They must match. If not, redo by hand — the calculator is right.

---

## Milestone 6 — Part 2: Packet Tracer Topology

Build the topology:

- Switch A → 4 PCs (Student Lab, Subnet B)
- Switch B → 4 PCs (Student Housing, Subnet C)
- Switch C → Web Server + GIU-Net Server device (DMZ, Subnet E)
- Switch D → 2 Staff PCs (Subnet A)
- Router 1 ↔ Switches A, C, D and WAN link
- Router 2 ↔ Switch B and WAN link
- WAN link between Router 1 and Router 2 = Subnet D

Then:

1. IP every interface and end device from your VLSM table.
2. Configure **static routes** on Router 1 and Router 2 so every subnet reaches every other subnet.
3. **Verify routing:** ping from a Housing PC to the GIU-Net Server. Capture this in Simulation Mode showing the packet hop by hop.
4. **HTTP service:** enable HTTP on the Web Server, edit `index.html` to show team name and all 4 members' full names + IDs. From a Lab PC, open Packet Tracer's browser → navigate to Web Server's IP → screenshot the loaded page.
5. **Simulation Mode packet trace:** trace one ICMP echo from Lab-PC-1 → GIU-Net Server. Screenshot every hop, annotate source IP / dest IP / layer (Network or Data Link).
6. **(Bonus) ACL on Router 2:** deny Subnet C → Subnet E, permit everything else. Demonstrate Housing PC ping fails, Lab PC ping succeeds.

Save as a `.pkt` file.

---

## Milestone 7 — Report

Write a PDF report containing (each member contributes to a section, so all members understand the whole):

1. **Team information:** full names, IDs, derived port (with derivation steps shown), HELLO/OK strings (with sum derivation).
2. **Protocol design choices:** why you chose 3 seconds for the timeout, and how you solved the "one thread waiting for ACK while another reads stdin" problem. Be specific to your implementation.
3. **Real debugging story** (this is asked about at evaluation): one specific bug — symptom, what you initially thought, how you diagnosed (Wireshark? print statements?), what the actual fix was.
4. **VLSM subnet table** with hand calculations.
5. **Routing tables** for Router 1 and Router 2.
6. **All screenshots** from Part 2 Tasks 2–4, annotated.
7. **(Bonus) ACL commands** and the paragraph explaining how an ACL relates to the HELLO/REJECTED logic from Part 1 (both are access control, different layers).

---

## Milestone 8 — Package & Submit (Deadline: Sun 31 May 2026, 23:59)

**ZIP filename:** `GIUNet_19005470_SS26.zip`

**ZIP contents:**
1. `GIUServer.java`
2. `GIUClient.java`
3. `giunet_session.log` (from the live two-machine run)
4. `<your>.pcapng` Wireshark capture
5. `<your>.pkt` Packet Tracer file
6. `report.pdf`

Submit via CMS. Reserve evaluation slot when the Doodle is posted.

---

## Milestone 9 — Evaluation Prep (the 20% that's individual Q&A)

In the week before the evaluation slot:

- Every member re-reads both `.java` files line by line and can explain any line if asked.
- Every member can demonstrate one live modification on the spot — practise these scenarios as a team:
  - Change handshake keyword from `HELLO` to a different word.
  - Change timeout from 3s to 2s.
  - Change the ACK format.
  - Change the port derivation rule.
- Every member can re-derive the VLSM table from scratch.
- Every member can explain the Wireshark capture packet by packet.

The evaluation is 20% of the grade and is **individual** — one weak member can drag the whole team's grade. Practice as a team.

---

## How to verify the whole thing end-to-end

Before zipping:

- `javac GIUServer.java GIUClient.java` (with `GIULogger.class` in the same directory) compiles clean.
- A **fresh** live run on two machines produces a fresh log and a Wireshark capture matching the screenshots in the report.
- Open the `.pkt` file in Packet Tracer, ping Housing→DMZ (succeeds if no ACL, fails if ACL is in place), open the team webpage from a Lab PC.
- Every team member can answer, in their own words, any question about any deliverable.

---

## What Claude can help with as you execute this plan

- Explaining any concept (UDP semantics, big-endian byte order, Java threading patterns, VLSM math, static routing logic, ACL syntax).
- Reviewing code you write and pointing out bugs / asking Socratic questions.
- Helping you interpret your own Wireshark capture.
- Talking through VLSM calculations to sanity-check your numbers.
- Helping you debug Packet Tracer config errors.
- Helping you structure and edit the report in your own voice.

## What Claude will not do

- Write `GIUServer.java` or `GIUClient.java` for you.
- Generate a finished `.pkt` topology.
- Write the report's debugging story or design rationale for you.

**Reason:** the project spec explicitly states AI-generated submissions are detectable and result in grade reduction, and the 20% live evaluation will expose any code you can't fully explain.
