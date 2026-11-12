# IEEE/ACM ToN: STRATEGIC FIT EVALUATION AND JOURNAL ALTERNATIVE ROADMAP

**Target Venue Evaluated:** IEEE/ACM Transactions on Networking (ToN)  
**Format:** IEEE double-column journal format (`IEEEtran`), 10 pages standard (overlength up to 14–16 pages with fees)  
**Review Process:** Single-anonymous (author names and affiliations revealed)  
**Status:** **NOT RECOMMENDED**  
**Recommended Journal Alternatives:** IEEE Transactions on Cloud Computing (TCC) or ACM Transactions on Computer Systems (TOCS)  
**Auditor:** Publication Preparation Review Panel  

---

## 1. Executive Summary & Recommendation

While IEEE/ACM Transactions on Networking (ToN) offers the operational advantage of **rolling submissions with no deadlines**, submitting DREB-RCA70 to ToN carries a **high probability of out-of-scope rejection**.

ToN is focused on **communication networks**—specifically Layer 2 (data link), Layer 3 (network routing/IP), and Layer 4 (transport/TCP/congestion control), hardware packet switches, and network calculus queueing theory. In contrast, DREB-RCA70 is an **application-layer (Layer 7) cloud API gateway admission control mechanism** backed by an in-memory Redis cluster.

Submitting to ToN would require complete re-framing of the paper as a packet-queuing protocol, risking harsh reviewer criticism for lacking packet traces, router buffer models, or TCP congestion dynamics.

---

## 2. Structural and Disciplinary Mismatch Analysis

```
+---------------------------------------------------------------------------------------------------------+
| Table 1: Disciplinary Alignment Matrix: DREB-RCA70 vs. IEEE/ACM ToN Expectations                       |
+--------------------------+------------------------------------+-----------------------------------------+
| Dimension                | IEEE/ACM ToN Reviewer Expectation  | DREB-RCA70 Current Research Foundation  |
+--------------------------+------------------------------------+-----------------------------------------+
| Primary Protocol Layer   | Layer 2 (Link), L3 (IP), L4 (TCP)  | Layer 7 (HTTP/gRPC API Gateways)        |
| Operational Substrate    | Routers, SDN switches, NIC buffers | Reverse proxies (Envoy/Kong), Redis Lua |
| Evaluation Methodology   | Packet traces (PCAP), ns-3/Mininet | In-memory microbenchmarks (JMH), Redis  |
| Mathematical Framework   | Network calculus, fluid queues     | Discrete token arithmetic, clock skew   |
| Core Focus               | Bandwidth, congestion, drops       | API tokens, tenant quotas, farming abuse|
+--------------------------+------------------------------------+-----------------------------------------+
```

---

## 3. Disadvantages of Submitting to ToN

1. **High Desk-Reject or Reviewer Reject Risk:** Area editors and reviewers are likely to return the paper with the feedback: *"This work addresses application-layer API management in cloud clusters, which belongs in a cloud systems venue rather than a networking journal."*
2. **Extensive Reframing Effort:** Converting the manuscript to IEEEtran format and attempting to force-fit Layer 7 admission control into network transport theory would distort the core contributions.
3. **Mandatory Overlength Charges:** The canonical manuscript would translate to approximately 14–15 IEEEtran journal pages. ToN charges $220 per page beyond 10 pages, resulting in a mandatory publication fee of **$880 to $1,100** upon acceptance.
4. **No Formal Artifact Evaluation:** ToN does not possess an ACM-style Artifact Evaluation Track that formally reviews and badges the turnkey Docker/JMH reproducibility suite.

---

## 4. Superior Journal Alternatives

If the human co-authors decide to pursue an archival journal trajectory rather than a top-tier conference (such as ACM SoCC or ACM ICPE), they should submit to journals where cloud systems, API gateways, and distributed middleware are top-priority core subjects:

### Alternative 1: IEEE Transactions on Cloud Computing (TCC)
- **Publisher:** IEEE Computer Society.
- **Topical Fit:** **EXCELLENT**. Explicitly covers cloud service architectures, API management, multi-tenancy, distributed cache consistency, and microservice resilience.
- **Review Model:** Rolling submission; single-anonymous; peer-reviewed journal.
- **Acceptance Scope:** Actively publishes empirical cloud systems, distributed Redis/Key-Value evaluations, and rate-limiting middleware.

### Alternative 2: ACM Transactions on Computer Systems (TOCS)
- **Publisher:** ACM.
- **Topical Fit:** **EXCELLENT**. Premier archival journal for operating systems, cloud systems, storage, and distributed architectures.
- **Expectation:** Welcomes deep systems engineering papers with formal mathematical bounds and distributed failover models.
