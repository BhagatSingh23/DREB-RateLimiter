# DREB — Dual-Rate Elastic Bucket
[![CI](https://github.com/BhagatSingh23/dreb-ratelimiter/actions/workflows/ci.yml/badge.svg)](https://github.com/BhagatSingh23/dreb-ratelimiter/actions/workflows/ci.yml)

A rate limiting algorithm for Java that rewards clients who've genuinely been idle with a larger
burst allowance, while resisting sustained or repeated abuse more effectively than a plain token
bucket at the same configured capacity.

Companion implementation for the paper *"The Dual-Rate Elastic Bucket (DREB) Algorithm."*
Benchmarked against Token Bucket, Leaky Bucket, Fixed Window, Sliding Window Log, and Sliding
Window Counter in a separate [benchmarking repository](https://github.com/BhagatSingh23/DREB-Comparator) — see the paper
for full results.

**Zero runtime dependencies.** Pure Java 17+, no framework required.

## Install

Not yet published to Maven Central. For now, install locally:

```bash
git clone https://github.com/BhagatSingh23/dreb-ratelimiter.git
cd dreb-ratelimiter
mvn install
```

Then add to your project's `pom.xml`:

```xml
<dependency>
    <groupId>com.dreb</groupId>
    <artifactId>dreb-ratelimiter</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Quick start

```java
import com.dreb.ratelimit.*;

RateLimiter limiter = Dreb.builder()
        .capacity(100)      // total capacity, comparable to a plain bucket's size
        .refillRate(100)    // sustained requests/sec
        .build();

if (limiter.allow(userId, 1) == Decision.ALLOW) {
    // serve the request
} else {
    // return HTTP 429
}
```

That's the whole integration. `Dreb.builder()` handles all the internal parameter derivation —
see [How it works](#how-it-works) below if you want to understand or tune what's happening
underneath.

## Status & Limitations

This library is functional and tested today via local install (`mvn install`), but it's worth knowing exactly what "done" means here before depending on it.

**What's verified:**
- 27 JUnit tests covering both algorithm versions directly, mocked Redis serialization, invalid inputs, and the public facade — passing with `mvn test`.
- Zero runtime dependencies; the `Dreb.builder()...build()` facade is the only API most integrations need.
- Benchmarked against five standard rate-limiting algorithms in a [companion repository](https://github.com/BhagatSingh23/DREB-Comparator), with results discussed in the accompanying paper.

**What's not yet true, and worth knowing before you rely on it:**
- **Not published to Maven Central.** There's no `<dependency>` you can add and have Maven resolve automatically — you need to `git clone` and `mvn install` locally first. Publishing to a public registry (Sonatype/Maven Central, requiring artifact signing and a registered namespace) hasn't happened yet.
- **`RedisStateStore`, as shipped, is not safe for concurrent multi-instance deployment.** It does a plain read-then-write (`HGETALL`/`HSET`), which is not atomic across multiple application servers hitting the same client's rate limit simultaneously. A production distributed deployment needs the whole refill/check/consume/save sequence pushed into a single atomic Redis Lua `EVAL` instead — see the `RedisStateStore` class Javadoc. The default `InMemoryStateStore` path (single JVM instance) does not have this limitation; it uses proper per-client locking.
- **v2's five tuning parameters** (`baseDecay`, `abuseGain`, `abuseIncrement`, `abuseDecayRate`, `sustainedCostGain`) were validated against the paper's specific benchmark scenarios and parameter ranges. An earlier version of this tuning had a real scaling bug that only appeared at a much larger capacity than initially tested (see the paper's evaluation section) — if you're deploying at capacity/rate values far outside what's been benchmarked, sanity-check behavior at your own scale rather than assuming the defaults generalize automatically.
- **CI status:** see the badge above — if it isn't green, treat the test suite as unverified for the current commit.

None of the above are reasons not to use this library for a single-instance deployment today — they're the honest boundary between "works and is tested" and "battle-tested, published, production-hardened for every deployment topology." Pull requests closing any of these gaps are welcome.

## Which version should I use?

```java
// v1 — flat idle-credit decay. Simpler, more predictable, slightly more conservative
// on repeated-abuse resistance in some configurations.
RateLimiter v1 = Dreb.builder().capacity(100).refillRate(100).build();

// v2 — abuse-adaptive. Tracks a client's recent pattern of drawing on idle credit and
// only escalates the penalty for repeat offenders; a first-time legitimate burst after
// genuine idleness costs close to nothing extra. Outperforms v1 in most (not all) of the
// paper's benchmark scenarios.
RateLimiter v2 = Dreb.builderV2().capacity(100).refillRate(100).build();
```

**If you're not sure, start with v2** (`Dreb.builderV2()`). It's newer and generally the better
default. The one place v1's `PROPORTIONAL` mapping edges out v2 in the paper's own benchmarking is
documented in the paper's evaluation section — worth reading if repeated-abuse resistance is your
primary concern.

## Choosing a capacity mapping

DREB splits a client's total capacity into a fast burst bucket, a slow sustained bucket, and an
idle-earned credit pool. There's more than one reasonable way to size those three pieces from a
single `capacity` + `refillRate` pair:

```java
Dreb.builder().capacity(100).refillRate(100)
    .mapping(Dreb.Mapping.PROPORTIONAL)          // default for v1
    .build();

Dreb.builder().capacity(100).refillRate(100)
    .mapping(Dreb.Mapping.BURST_CEILING_MATCHED) // default for v2
    .build();
```

- **`PROPORTIONAL`** preserves the reference implementation's internal ratios exactly. More
  conservative on instantaneous bursts, more restrictive on sustained abuse.
- **`BURST_CEILING_MATCHED`** sizes the burst+idle-credit pool so a client's maximum reachable
  instantaneous burst matches what a plain fixed-capacity bucket would allow at the same
  `capacity`. Use this if you don't want DREB's idle-reward behavior to cost you burst tolerance
  relative to a simple token bucket.

Both are derived and justified in full in the paper's Section 4 and in
`docs/algorithms.md` in the benchmarking repository.

## Distributed deployment (multiple instances)

The default `Dreb.builder()...build()` uses in-memory state — correct for a single JVM instance,
but **not safe across multiple application servers** sharing the same client's rate limit. For
that, implement `RedisHashClient` against your Redis client of choice and pass a `RedisStateStore`:

```java
RedisHashClient myRedisAdapter = /* wrap Jedis, Lettuce, etc. */;
StateStore store = new RedisStateStore(myRedisAdapter, "ratelimit");

RateLimiter limiter = Dreb.builder()
        .capacity(100).refillRate(100)
        .store(store)
        .build();
```

**Important:** `RedisStateStore` as shipped does a plain read-then-write (HGETALL/HSET), which is
**not atomic** under concurrent requests for the same client across multiple app instances. For a
genuinely safe distributed deployment, push the whole refill/check/consume/save sequence into a
single Redis Lua `EVAL` script instead, so it executes atomically server-side. See the class
Javadoc on `RedisStateStore` for specifics. Single-JVM deployments using the default
`InMemoryStateStore` don't have this concern — that path uses proper per-client locking
(`synchronized` on each client's state object).

## Testing your own integration

Inject a `ManualClock` for deterministic tests instead of relying on real time:

```java
ManualClock clock = new ManualClock(0.0);
RateLimiter limiter = Dreb.builder().capacity(20).refillRate(5).clock(clock).build();

clock.set(0.0);
limiter.allow("test-client", 0);   // touch to initialize state at t=0
clock.set(60.0);                    // simulate 60 seconds of idle time
// ... now test burst behavior deterministically
```

## How it works

DREB maintains four pieces of state per client:

```
burst_tokens      // fast bucket, refills quickly
sustained_tokens  // slow bucket, bounds the long-run average rate
idle_credit       // bonus burst capacity, earned only while burst_tokens sits full
last_refill_ts    // last update timestamp
```

A request is allowed only if both `sustained_tokens` and `burst_tokens + idle_credit` can cover
its cost. Drawing on `idle_credit` beyond what `burst_tokens` alone provides is debited at a rate
greater than 1:1 (v1: a fixed multiplier; v2: a multiplier that grows with how often this specific
client has been drawing on idle credit recently), which is what keeps the reward self-limiting —
a client can't bank idle credit and then sustain elevated throughput indefinitely.

Full formal specification, correctness argument, and the UML design are in the paper.

## Building from source

```bash
mvn clean install        # build + install to local Maven repo
mvn test                 # run the test suite
```

## License

Apache 2.0 — see [LICENSE](LICENSE).
