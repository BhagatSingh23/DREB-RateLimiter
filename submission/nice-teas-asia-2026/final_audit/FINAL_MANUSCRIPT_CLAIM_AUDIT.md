# FINAL MANUSCRIPT SCIENTIFIC CLAIM AUDIT

**Audit Date:** November 14, 2026  
**Target Manuscripts:**  
1. Full 10-Page Edition: `submission/nice-teas-asia-2026/latex/main.tex` & `main.pdf`  
2. Compact 8-Page Edition: `submission/nice-teas-asia-2026/compact-8-page/latex/main.tex` & `main.pdf`  
**Reference Source of Truth:** Commit `1040cc4` (`PHASE9_2_CLAIM_FREEZE_MANIFEST.json`, `research/systematic-improvement`)  
**Auditor Status:** Rigorous Claim-by-Claim Verification Across Implementation, Data, and Manuscript  

---

## 1. Executive Summary

This audit independently cross-checks every core empirical and theoretical claim made in the NICE-TEAS ASIA 2026 manuscripts against the underlying Java and Redis Lua implementations, raw simulation CSV datasets, and mathematical proofs.

### Verification Status: **15 / 15 CLAIMS FULLY VERIFIED & SUPPORTED**

All 15 mandatory claims from the audit protocol adhere strictly to the frozen research manifest, retain their defensive qualification scopes, and contain zero inflated or unsupported statements.

---

## 2. Claim-by-Claim Audit Matrix

### Claim 1: Throughput Suppression Percentages (W04, W08, W10, and Mean)
- **Manuscript Statement:** DREB-RCA70 suppresses adversarial farming throughput by 81.9% on W04 (Periodic Farming), 56.7% on W08 (Adaptive AIMD Probe), and 85.7% on W10 (Coordinated Pulse), averaging 74.8% across evaluated attacks (relative to `tb_large`).
- **Mathematical Definition:**
  $$\text{Suppression} = 1.0 - \frac{\text{adv\_rate\_norm}(\text{RCA70})}{\text{adv\_rate\_norm}(\text{tb\_large})}$$
- **Underlying Data (CSV / Manifest):**
  - W04: `tb_large` admits $0.4517$ tokens/unit; RCA70 admits $0.0817 \implies 1.0 - (0.0817 / 0.4517) = 81.91\%$.
  - W08: `tb_large` admits $0.8000$ tokens/unit; RCA70 admits $0.3467 \implies 1.0 - (0.3467 / 0.8000) = 56.66\% \approx 56.7\%$.
  - W10: `tb_large` admits $0.3500$ tokens/unit; RCA70 admits $0.0500 \implies 1.0 - (0.0500 / 0.3500) = 85.71\% \approx 85.7\%$.
  - Mean across evaluated closed-loop attack workloads: $74.8\%$.
- **Audit Verdict:** **VERIFIED (EXACT MATCH)**.

---

### Claim 2: The 93.04% Figure Scoped to Credit-Accrual Reduction
- **Manuscript Statement:** The 93.04% reduction represents credit-accrual reduction under closed-loop parameter ablation (`rec_010`), not general request volume suppression.
- **Evidence:** In `latex/main.tex` (Abstract & Section 7.2) and `compact-8-page/latex/main.tex` (Section 5.2):
  > *"RCA70 drives credit accrual rate (`credit_rate_norm`) down by 93.04% ($1.0 - 0.069567 = 0.930433$), reflecting throttling of bonus credit generation rather than total request volume."*
- **Audit Verdict:** **VERIFIED (PROPERLY SCOPED)**. Conflation has been strictly eliminated.

---

### Claim 3: Quiescent Scope of 1,000-Token Legitimate Bursts
- **Manuscript Statement:** 0.00% False Rejection Rate (FRR) for legitimate 1,000-token batch bursts holds under quiescent, fully replenished initial conditions.
- **Evidence:** In `latex/main.tex` (Abstract, Section 6.1, Section 7.1) and `compact-8-page/latex/main.tex` (Section 5.1):
  > *"maintaining 0.00% false rejection for legitimate 1,000-token batch bursts under tested quiescent conditions ($B = B_{\max}, I = I_{\max}, K = 0$)."*
- **Audit Verdict:** **VERIFIED (PROPERLY QUALIFIED)**.

---

### Claim 4: Distinction Between Idle-Credit Pool and Total Burst Capacity
- **Manuscript Statement:** The idle-credit pool $I(t)$ is not total burst capacity; total burst capacity is $B(t) + I(t)/d$.
- **Evidence:** In `latex/main.tex` (Section 3.1) and `compact-8-page/latex/main.tex` (Section 3.1):
  > *"Total instantaneous burst headroom available to a client is $C_{\text{burst}}(t) = B(t) + \frac{I(t)}{d}$."*
- **Audit Verdict:** **VERIFIED (MATHEMATICALLY ACCURATE)**.

---

### Claim 5: Maximum Burst Headroom Expression
- **Manuscript Statement:** $C_{\text{burst,max}} = B_{\max} + \frac{I_{\text{ceil}}(0)}{d}$.
- **Evidence:** Under RCA70 ($B_{\max} = 700$, $\text{baseCap} = 450$, $d = 1.5$):
  $$C_{\text{burst,max}} = 700 + \frac{450}{1.5} = 700 + 300 = 1,000\,\text{tokens}$$
  Stated identically in `latex/main.tex` (Section 3.3) and `compact-8-page/latex/main.tex` (Section 3.3).
- **Audit Verdict:** **VERIFIED (EXACT MATCH)**.

---

### Claim 6: Deterministic Bounds $G_1$ (Local Time) and $G_{1,\delta}$ (Physical Time)
- **Manuscript Statement:**
  - $G_1$ (Local Server Time): $A(T) \le S_{\max} + R \cdot T = 1285.7143 + 1000.0 \cdot T$. Holds deterministically on an uninterrupted primary node.
  - $G_{1,\delta}$ (Physical Clock Skew): $A(t) \le 1295.7143 + 1000.0 \cdot t$. Formulated as a conditional theorem assuming true physical clock offset $|\Delta \tau| \le 2\delta = 10\,\text{ms}$.
- **Evidence:** Derived and stated with explicit proof in `latex/main.tex` (Section 4.1 & 4.2) and `compact-8-page/latex/main.tex` (Section 4.1).
- **Audit Verdict:** **VERIFIED (EXPLICIT CONDITIONAL PROOF)**.

---

### Claim 7: Single-Event vs. Repeated Discontinuity Failover Envelope
- **Manuscript Statement:** Under an isolated single-fault failover event, excess admission across nodes is bounded by $2 C_{\max} = 2,000$ tokens per client key. Under repeated discontinuities, no finite global excess bound exists without synchronous inter-node synchronization.
- **Evidence:** In `latex/main.tex` (Theorems 3 and 4) and `compact-8-page/latex/main.tex` (Section 4.2):
  > *"Theorem 3 establishes that single-event distributed failover over-admission is strictly bounded by $2 C_{\max} = 2,000$ tokens per client key... Theorem 4 proves that under an unbounded sequence of failover events, global over-admission can diverge without bound."*
- **Audit Verdict:** **VERIFIED (ACCURATE & HONEST)**.

---

### Claim 8: $G_2$ Necessity Qualified Correctly
- **Manuscript Statement:** Sublinear event frequency ($\lim_{T \to \infty} \frac{N_E(T)}{T} = 0$) is universally sufficient for asymptotic rate convergence, and strictly necessary under sustained saturated adversarial demand (not an unqualified if-and-only-if under all arbitrary arrival processes).
- **Evidence:** In `latex/main.tex` (Section 4.3, Theorem 5):
  > *"Sublinear event frequency is universally sufficient, and strictly necessary under sustained saturated adversarial demand."*
- **Audit Verdict:** **VERIFIED (PROPERLY BOUNDED)**.

---

### Claim 9: $G_3$ Abuse Score Recovery Time ($\tau_{0.05}$)
- **Manuscript Statement:** Under linear recovery $\lambda_{\text{rec}} = 0.05\,\text{s}^{-1}$, the time required for a client at maximum abuse score $K_0 = 1.0$ to recover to target $\epsilon = 0.05$ is:
  $$\tau_{0.05} = \frac{1.0 - 0.05}{0.05\,\text{s}^{-1}} = 19.0\,\text{s}$$
- **Evidence:** Verified in `latex/main.tex` (Section 4.4, Theorem 6) and `compact-8-page/latex/main.tex` (Section 4.3).
- **Audit Verdict:** **VERIFIED (MATHEMATICALLY EXACT)**.

---

### Claim 10: Redis `WAIT 1` Characterization
- **Manuscript Statement:** `WAIT 1` provides synchronous in-memory replication acknowledgment from at least one secondary replica; it is explicitly not described as Raft/Paxos consensus or disk fsync durability.
- **Evidence:** In `latex/main.tex` (Section 8.1) and `compact-8-page/latex/main.tex` (Section 6.1):
  > *"`WAIT 1` forces the primary Redis shard to block synchronously until the write has been transferred and acknowledged in the memory of at least one replica. This provides in-memory replica durability against single-node crashes, but does not guarantee synchronous disk fsync or multi-node consensus."*
- **Audit Verdict:** **VERIFIED (DEFENSIVE SYSTEMS CHARACTERIZATION)**.

---

### Claim 11: Idempotency Contract Assumptions
- **Manuscript Statement:** The Idempotency Contract specifies 5 formal operational conditions: unique request ID, SHA-256 payload fingerprinting, 60-second retry window (TTL), connection stability, and replica visibility.
- **Evidence:** Enumerated explicitly in `latex/main.tex` (Section 8.3, Gate H) and `compact-8-page/latex/main.tex` (Section 6.2).
- **Audit Verdict:** **VERIFIED (EXPLICIT 5-GATE ENUMERATION)**.

---

### Claim 12: Redis Replication Latency Reporting
- **Manuscript Statement:** Asynchronous Redis execution is sub-millisecond (0.259 ms P50, 0.667 ms P99), whereas synchronous replication durability (`WAIT 1`) exhibits a P99 latency of **1.301 ms** (not sub-millisecond).
- **Evidence:** Accurately presented in `latex/main.tex` (Abstract, Table 4, Section 8.1) and `compact-8-page/latex/main.tex` (Table 3, Section 6.1).
- **Audit Verdict:** **VERIFIED (TRANSPARENT TAIL LATENCY REPORTING)**.

---

### Claim 13: Memory Sizing Model Assumptions
- **Manuscript Statement:** Total memory expression is $M_{\text{Redis}} \approx 216 \cdot N_{\text{state}} + 176 \cdot N_{\text{idemp}}$ bytes, where the ~392 MB figure reflects $1,000,000$ client states alongside $1,000,000$ active idempotency keys.
- **Evidence:** Stated in `latex/main.tex` (Section 8.1) and `compact-8-page/latex/main.tex` (Section 6.1).
- **Audit Verdict:** **VERIFIED (FORMULA & ASSUMPTIONS ALIGNED)**.

---

### Claim 14: Pareto-Frontier Scope Definitions
- **Manuscript Statement:** Under Scope 1 ($c \le 700$), Static Dual-Bucket strictly dominates all candidates; DREB-RCA70 is not on the Scope 1 frontier. DREB-RCA70 and Token Bucket constitute the non-dominated Pareto frontier for Scope 2 ($c = 1,000$) and composite production mixes.
- **Evidence:** Explicitly proved and plotted in `latex/main.tex` (Section 7.3, Table 3, Fig. 4) and `compact-8-page/latex/main.tex` (Section 5.3).
- **Audit Verdict:** **VERIFIED (RIGOROUS MULTI-OBJECTIVE QUALIFICATION)**.

---

### Claim 15: Prototype vs. Production-Readiness Scope
- **Manuscript Statement:** DREB-RCA70 is evaluated as an academic reference implementation and research testbed prototype, not a production-certified enterprise appliance.
- **Evidence:** Emphasized in `latex/main.tex` (Section 9 Threats to Validity) and `compact-8-page/latex/main.tex` (Section 7 Limitations):
  > *"Production Scope: DREB-RCA70 is evaluated as an academic reference implementation and testbed prototype, not production-certified software."*
- **Audit Verdict:** **VERIFIED (CONSERVATIVE ACADEMIC POSTURE)**.
