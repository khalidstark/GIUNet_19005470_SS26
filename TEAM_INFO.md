# GIU-Net Team Reference Card

**Course:** CNET101 — Computer Networks, Spring 2026
**Institution:** German International University (GIU)
**Submission deadline:** Sunday, 31 May 2026, 23:59 (via CMS)
**Evaluation week:** 2–8 June 2026 (individual oral, 20% of grade)

---

## Team Members

| Role        | Student ID | Last Digit |
|-------------|-----------:|-----------:|
| **Leader**  | 19005470   | 0          |
| Member      | 19010617   | 7          |
| Member      | 19009026   | 6          |
| Member      | 19009431   | 1          |

**Leader rule:** Lowest student ID → 19005470.

---

## Derived Constants (verify by hand, all 4 members)

| Value              | Computation                                  | Result                     |
|--------------------|----------------------------------------------|----------------------------|
| Last-digit sum     | 0 + 7 + 6 + 1                                | **14**                     |
| HELLO string       | `HELLO-GIU-<LeaderID>-<Sum>`                 | `HELLO-GIU-19005470-14`    |
| OK string          | `OK-GIU-<LeaderID>-<Sum>`                    | `OK-GIU-19005470-14`       |
| Server port        | Last 4 digits of LeaderID = 5470, > 1024     | **5470**                   |
| Base IP            | Digit pairs of 19005470 → 19.00.54.0/22      | **19.0.54.0/22**           |

### ⚠ Note on base IP alignment

`19.0.54.0/22` is **not aligned** to a true /22 boundary (mask 255.255.252.0 requires the 3rd octet to be a multiple of 4). The aligned /22 containing the address is `19.0.52.0/22` (range 19.0.52.0 – 19.0.55.255).

Re-read the project spec's exact wording on the base-IP rule before laying out Milestone 5 subnets. If the spec wants the mechanical formula as-written, use `19.0.54.0/22` and note the assumption in the report; if it wants a valid network, use `19.0.52.0/22`.

---

## Submission ZIP

**Filename:** `GIUNet_19005470_SS26.zip`

**Contents:**
1. `GIUServer.java`
2. `GIUClient.java`
3. `giunet_session.log` (from live two-machine run)
4. `*.pcapng` Wireshark capture
5. `*.pkt` Packet Tracer file
6. `report.pdf`
