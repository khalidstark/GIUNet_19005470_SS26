# Part 2 — Campus Network Plan
## Team Leader ID: 19005470

---

## Base Network Derivation
- Leader ID: 19005470 → digit pairs: 19 . 00 . 54 . 0 → 19.0.54.0/22
- Aligned /22 network: **19.0.52.0/22** (19.0.52.0 – 19.0.55.255, 1022 usable hosts)

---

## VLSM Subnet Table

| Subnet | Purpose         | Hosts | Prefix | Network       | Mask            | First IP     | Last IP      | Broadcast    |
|--------|-----------------|-------|--------|---------------|-----------------|--------------|--------------|--------------|
| C      | Student Housing | 200   | /24    | 19.0.52.0     | 255.255.255.0   | 19.0.52.1    | 19.0.52.254  | 19.0.52.255  |
| B      | Student Lab     | 100   | /25    | 19.0.53.0     | 255.255.255.128 | 19.0.53.1    | 19.0.53.126  | 19.0.53.127  |
| A      | Staff Offices   | 50    | /26    | 19.0.53.128   | 255.255.255.192 | 19.0.53.129  | 19.0.53.190  | 19.0.53.191  |
| E      | Server DMZ      | 10    | /28    | 19.0.53.192   | 255.255.255.240 | 19.0.53.193  | 19.0.53.206  | 19.0.53.207  |
| D      | Router WAN Link | 2     | /30    | 19.0.53.208   | 255.255.255.252 | 19.0.53.209  | 19.0.53.210  | 19.0.53.211  |

---

## Device IP Assignments

### Routers
| Device   | Interface        | IP           | Subnet | Gateway |
|----------|-----------------|--------------|--------|---------|
| Router 1 | Gi0/0 → Switch A | 19.0.53.1   | B /25  | —       |
| Router 1 | Gi0/1 → Switch C | 19.0.53.193 | E /28  | —       |
| Router 1 | Gi0/2 → Switch D | 19.0.53.129 | A /26  | —       |
| Router 1 | Se0/0 → WAN      | 19.0.53.209 | D /30  | —       |
| Router 2 | Gi0/0 → Switch B | 19.0.52.1   | C /24  | —       |
| Router 2 | Se0/0 → WAN      | 19.0.53.210 | D /30  | —       |

### End Devices
| Device        | IP           | Subnet Mask     | Default Gateway |
|---------------|--------------|-----------------|-----------------|
| Lab-PC-1      | 19.0.53.2    | 255.255.255.128 | 19.0.53.1       |
| Lab-PC-2      | 19.0.53.3    | 255.255.255.128 | 19.0.53.1       |
| Lab-PC-3      | 19.0.53.4    | 255.255.255.128 | 19.0.53.1       |
| Lab-PC-4      | 19.0.53.5    | 255.255.255.128 | 19.0.53.1       |
| Housing-PC-1  | 19.0.52.2    | 255.255.255.0   | 19.0.52.1       |
| Housing-PC-2  | 19.0.52.3    | 255.255.255.0   | 19.0.52.1       |
| Housing-PC-3  | 19.0.52.4    | 255.255.255.0   | 19.0.52.1       |
| Housing-PC-4  | 19.0.52.5    | 255.255.255.0   | 19.0.52.1       |
| Staff-PC-1    | 19.0.53.130  | 255.255.255.192 | 19.0.53.129     |
| Staff-PC-2    | 19.0.53.131  | 255.255.255.192 | 19.0.53.129     |
| Web Server    | 19.0.53.194  | 255.255.255.240 | 19.0.53.193     |
| GIU-Net Server| 19.0.53.195  | 255.255.255.240 | 19.0.53.193     |

---

## Static Routing

### Router 1 routes
```
ip route 19.0.52.0 255.255.255.0 19.0.53.210       ! reach Subnet C via Router 2
```

### Router 2 routes
```
ip route 19.0.53.0 255.255.255.128 19.0.53.209     ! reach Subnet B via Router 1
ip route 19.0.53.128 255.255.255.192 19.0.53.209   ! reach Subnet A via Router 1
ip route 19.0.53.192 255.255.255.240 19.0.53.209   ! reach Subnet E via Router 1
```

---

## Topology Summary
```
[Housing-PC x4] --- Switch B --- Router 2 --- (WAN /30) --- Router 1 --- Switch A --- [Lab-PC x4]
                                                                       |
                                                                  Switch C --- Web Server (19.0.53.194)
                                                                       |--- GIU-Net Server (19.0.53.195)
                                                                       |
                                                                  Switch D --- Staff-PC-1
                                                                           --- Staff-PC-2
```

---

## Checklist
- [ ] Task 1 — VLSM subnet table (done above, show calculations in report)
- [ ] Task 2 — Static routing configured, ping Housing→DMZ works
- [ ] Task 3 — HTTP server on Web Server, team webpage with all 4 names + IDs
- [ ] Task 4 — Simulation Mode packet trace Lab-PC-1 → GIU-Net Server
- [ ] Task 5 — (Bonus) ACL on Router 2: deny Subnet C → Subnet E
