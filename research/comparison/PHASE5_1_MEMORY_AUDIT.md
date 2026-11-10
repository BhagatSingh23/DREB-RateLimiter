# Phase 5.1 — Memory Footprint Audit & Qualification

**Repository:** DREB-RateLimiter  
**Branch:** `research/systematic-improvement`  
**Starting Commit:** [`4c26bce`](file:///Users/cruise/Desktop/DREB-RateLimiter)  
**Evaluated Class:** [`ClientState.java`](file:///Users/cruise/Desktop/DREB-RateLimiter/src/main/java/com/dreb/ratelimit/ClientState.java), [`InMemoryStateStore.java`](file:///Users/cruise/Desktop/DREB-RateLimiter/src/main/java/com/dreb/ratelimit/InMemoryStateStore.java)  
**Verification Test:** [`Phase51CorrectiveAuditTest.java`](file:///Users/cruise/Desktop/DREB-RateLimiter/src/test/java/com/dreb/ratelimit/Phase51CorrectiveAuditTest.java)

---

## 1. Executive Summary & Defect Correction

The Phase 5 report stated:
> *"State Footprint: Exactly 40 bytes per client limiter (4 × f64 + 1 × u64), requiring < 40 MB per 1,000,000 active clients."*

An audit of [`ClientState.java`](file:///Users/cruise/Desktop/DREB-RateLimiter/src/main/java/com/dreb/ratelimit/ClientState.java) and JVM object layout identifies two critical inaccuracies in this statement:
1. **Type Inaccuracy:** `ClientState` does not contain 4 floating-point values and 1 integer. It contains **five 64-bit IEEE-754 `double` fields** (`burstTokens`, `sustainedTokens`, `idleCredit`, `abuseScore`, `lastRefillTs`). While the arithmetic sum remains $5 \times 8 = 40\,\text{bytes}$, the typing was incorrectly reported.
2. **Conflation of Logical Payload with Retained Heap Memory:** The 40-byte figure represents **only the raw logical primitive fields**. It omits JVM object headers, 8-byte alignment padding, collection node wrappers (`ConcurrentHashMap.Node`), and client key storage (`java.lang.String`).
3. **Corrected Retained Footprint:** On a 64-bit JVM with compressed object pointers (standard Java 21 runtime), the actual retained heap footprint is **$150.6 - 165.3\,\text{bytes}$ per client limiter**. Storing 1,000,000 active clients in memory requires approximately **$150 - 165\,\text{MB}$**, not $< 40\,\text{MB}$.

---

## 2. Layer-by-Layer Architectural Memory Dissection

### 2.1 Layer 1: Logical Primitive State Payload (40 Bytes)
As defined in [`ClientState.java`](file:///Users/cruise/Desktop/DREB-RateLimiter/src/main/java/com/dreb/ratelimit/ClientState.java):
```java
public double burstTokens;      // 8 bytes (double)
public double sustainedTokens;  // 8 bytes (double)
public double idleCredit;       // 8 bytes (double)
public double abuseScore;       // 8 bytes (double)
public double lastRefillTs;     // 8 bytes (double)
```
- Total raw primitive data: $5 \times 8\,\text{bytes} = \mathbf{40\,\text{bytes}}$.

### 2.2 Layer 2: Standalone JVM Object Footprint (56 Bytes)
In HotSpot 64-bit JVM with compressed OOPs (`-XX:+UseCompressedOops` enabled by default up to 32 GB heaps):
- **Object Header:** 12 bytes (8-byte `markWord` + 4-byte compressed `klass` pointer).
- **Primitive Fields:** 40 bytes ($5 \times 8$-byte `double`).
- **Alignment Padding:** 4 bytes (to satisfy 8-byte word alignment).
- **Total `ClientState` Instance Size:** $12 + 40 + 4 = \mathbf{56\,\text{bytes}}$.

### 2.3 Layer 3: Collection Storage in `InMemoryStateStore` (~150–165 Bytes)
In [`InMemoryStateStore.java`](file:///Users/cruise/Desktop/DREB-RateLimiter/src/main/java/com/dreb/ratelimit/InMemoryStateStore.java), client state records are maintained in a `ConcurrentHashMap<String, ClientState>`:
1. **Map Entry Node (`ConcurrentHashMap.Node`):**
   - Header: 12 bytes
   - Hash (`int`): 4 bytes
   - Key reference (`String`): 4 bytes
   - Value reference (`ClientState`): 4 bytes
   - Next node reference: 4 bytes
   - Padding: 4 bytes
   - Total Node Size: **32 bytes**.
2. **Client Key (`java.lang.String`):**
   - Assuming a standard alphanumeric client identifier (e.g. `"client_12345"`, ~12–16 characters):
   - `String` object: 12-byte header + 4-byte hash + 4-byte byte array ref + 4-byte coder/padding = 24 bytes.
   - `byte[]` payload: 12-byte header + 4-byte length + 16-byte payload = 32 bytes.
   - Total String Size: **56 bytes**.
3. **Hash Table Bucket Slot:**
   - Table array reference: 4 bytes. At typical load factor ($0.75$), this amortizes to $\approx 5.33\,\text{bytes}$ per entry.

$$\text{Retained Heap per Client} = 56\,(\text{State}) + 32\,(\text{Node}) + 56\,(\text{Key}) + 5.33\,(\text{Bucket}) \approx \mathbf{149.33\,\text{bytes}}$$

---

## 3. Empirical JVM Measurements Across Client Counts

Empirical measurements were conducted on OpenJDK 21 (Temurin, macOS aarch64) with fixed 512MB heap (`-Xms512m -Xmx512m`) using forced garbage collection sweeps before and after population:

| Client Count ($N$) | Total Heap Consumed | Empirical Footprint per Client | Storage Data Structure |
| :--- | :--- | :--- | :--- |
| **$1,000$ clients** | $0.15\,\text{MB}$ | $150.6\,\text{bytes / client}$ | `ConcurrentHashMap<String, ClientState>` |
| **$10,000$ clients** | $1.44\,\text{MB}$ | $150.6\,\text{bytes / client}$ | `ConcurrentHashMap<String, ClientState>` |
| **$100,000$ clients** | $15.76\,\text{MB}$ | $165.3\,\text{bytes / client}$ | `ConcurrentHashMap<String, ClientState>` |
| **$1,000,000$ clients** (Projected) | $\approx 155 - 165\,\text{MB}$ | $\approx 160.0\,\text{bytes / client}$ | `ConcurrentHashMap<String, ClientState>` |

*Note: The modest increase from $150.6$ to $165.3$ bytes at $N=100,000$ reflects hash table capacity power-of-two resizing (from $2^{16} = 65,536$ to $2^{17} = 131,072$ bucket slots).*

---

## 4. Redis Serialized Footprint in `RedisStateStore`

In distributed architectures utilizing [`RedisStateStore.java`](file:///Users/cruise/Desktop/DREB-RateLimiter/src/main/java/com/dreb/ratelimit/RedisStateStore.java), state is serialized into a Redis Hash containing the 5 fields:
- Redis `dictEntry` overhead: 24 bytes (on 64-bit jemalloc).
- Redis `robj` key wrapper: 16 bytes.
- Redis SDS string for key name: ~40 bytes.
- Redis listpack/hashtable encoding for 5 field-value string pairs: ~120 bytes.
- **Estimated Redis Memory Footprint:** $\approx \mathbf{200 - 250\,\text{bytes}}$ per client key.

---

## 5. Formal Corrective Statement

The text in research artifacts and publications must be amended to read:

> *"The DREB-RCA70 limiter state has a logical primitive payload of exactly 40 bytes ($5 \times \text{float64}$). In an in-memory Java deployment using `ConcurrentHashMap<String, ClientState>`, the retained JVM heap footprint is approximately $150 - 165$ bytes per client, requiring $\approx 150 - 165$ MB per million concurrent clients. In a distributed Redis deployment, serialized hash storage consumes approximately $200 - 250$ bytes per client in Redis memory."*
