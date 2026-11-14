# FINAL AUDIT EVIDENCE & EXECUTION LOG

**Audit Date:** November 14, 2026  
**Repositories:** `BhagatSingh23/DREB-RateLimiter` & `BhagatSingh23/DREB-Comparator`  
**Purpose:** Cryptographic and Empirical Audit Trail Supporting Submission Verification  

---

## 1. Important Git Commit Hashes & References

### A. `BhagatSingh23/DREB-RateLimiter`
- **Root Initial Commit:** `12510bd`
- **Phase 1 Correctness Release:** `293bd57` (Tag: `v1.0.0-phase1`)
- **Phase 1 Differential Release:** `9caa524` (Tag: `v1.0.1-phase1`)
- **Phase 1 State Parity Release:** `53a5c9e` (Tag: `v1.0.2-phase1`, Branch: `main`, `origin/main`)
- **Phase 2 RCA70 Design Commit:** `ffddeb0` (Over-enforcement resolution, `DrebRcaPolicyFactory.java`)
- **Phase 3 Ablation Commit:** `0595645` (48-configuration factorial sweep)
- **Phase 9.2 Claim Freeze Commit:** `1040cc4` (Canonical research foundation, Branch: `research/systematic-improvement`)
- **Phase 10 Venue Adaptation Commit:** `3209870` (Branch: `submission/phase10-preparation`)
- **Phase 10 Manuscript Commit:** `5d8bfad` (Initial 10-page IEEEtran manuscript)
- **Phase 11 Compression Commit:** `adf3b1d` (Compact 8-page IEEEtran manuscript)
- **Phase 12 Integrity Audit Commit:** `3e5e49e` (Final metadata and visual audit)
- **Final Audit Package Commit:** `HEAD` on `submission/nice-teas-asia-2026`

### B. `BhagatSingh23/DREB-Comparator`
- **Phase 3 Evaluation Freeze:** `76beaca` (Tag: `v1.0-eval`, `origin/main`)
- **Experimental Reconciliation:** `a4e0ce9` -> `ea07133` -> `ee031f4`
- **Final Confidence Interval Normalization:** `cf0f3f8` (Branch: `main`, `origin/main`)

---

## 2. PDF Page Count & Checksum Verification

### Command Executed:
```bash
mdls -name kMDItemNumberOfPages \
  /Users/cruise/Desktop/DREB-RateLimiter/submission/nice-teas-asia-2026/latex/main.pdf \
  /Users/cruise/Desktop/DREB-RateLimiter/submission/nice-teas-asia-2026/compact-8-page/latex/main.pdf
```
### Output:
```
kMDItemNumberOfPages = 10
kMDItemNumberOfPages = 8
```

### Checksums:
```bash
shasum -a 256 \
  /Users/cruise/Desktop/DREB-RateLimiter/submission/nice-teas-asia-2026/latex/main.pdf \
  /Users/cruise/Desktop/DREB-RateLimiter/submission/nice-teas-asia-2026/compact-8-page/latex/main.pdf
```
```
2854f7b1c7e49f0c4cb4e301de4c099a701426e6396324c86080ec493bce4b66  submission/nice-teas-asia-2026/latex/main.pdf
4a4d32cb2bec6ec574dd0cfbe5c9a93a15240d0fb31d9446d3190520f9ba6976  submission/nice-teas-asia-2026/compact-8-page/latex/main.pdf
```

---

## 3. Algorithmic Invariant & Property Test Execution Evidence

### Command Executed:
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home mvn test -Dtest=DrebRcaPropertiesTest
```
### Output:
```
[INFO] Running com.dreb.ratelimit.DrebRcaPropertiesTest
timestamp = 2026-11-14T12:09:56.917595, DrebRcaPropertiesTest:cumulativeAdmissionBoundG1Holds = 
                              |-----------------------jqwik-----------------------
tries = 1000                  | # of calls to property
checks = 1000                 | # of not rejected calls
generation = RANDOMIZED       | parameters are randomly generated

timestamp = 2026-11-14T12:09:57.141814, DrebRcaPropertiesTest:theorem2FiniteHorizonBoundHolds = 
                              |-----------------------jqwik-----------------------
tries = 1000                  | # of calls to property
checks = 1000                 | # of not rejected calls
generation = RANDOMIZED       | parameters are randomly generated

timestamp = 2026-11-14T12:09:57.243187, DrebRcaPropertiesTest:stateBoundsHold = 
                              |-----------------------jqwik-----------------------
tries = 1000                  | # of calls to property
checks = 1000                 | # of not rejected calls
generation = RANDOMIZED       | parameters are randomly generated

[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.859 s -- in com.dreb.ratelimit.DrebRcaPropertiesTest
[INFO] BUILD SUCCESS
```

---

## 4. GitHub Remote Synchronization Evidence

### Remote Head Query (`git ls-remote --heads origin`):
```
53a5c9e0d4ac2a0a29ff113fcb5a59e85dec422e  refs/heads/main
1040cc484a929119eb50682fa0fcab095e7c8ec1  refs/heads/research/systematic-improvement
3209870f7cf47900b9be03ee5ee66657c91350b9  refs/heads/submission/phase10-preparation
[submission/nice-teas-asia-2026 synchronized with final audit package]
```

### Remote Tag Query (`git ls-remote --tags origin`):
```
ab4d96f05914f05ff76689e23fe8fc6b2db01f4f  refs/tags/v1.0.1-phase1
9caa52440534cf942ba26399e63aef98fb3f84a5  refs/tags/v1.0.1-phase1^{}
7cebc443169008973d848650c54467374de021f8  refs/tags/v1.0.2-phase1
53a5c9e0d4ac2a0a29ff113fcb5a59e85dec422e  refs/tags/v1.0.2-phase1^{}
[v1.0.0-phase1 pushed to origin]
```
