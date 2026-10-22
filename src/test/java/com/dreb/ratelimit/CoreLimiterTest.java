package com.dreb.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class CoreLimiterTest {

    // ── DREBLimiter (v1) direct tests ───────────────────────────────────

    @Nested
    @DisplayName("DREBLimiter (v1) direct construction")
    class DREBLimiterDirectTests {

        private DREBLimiter buildLimiter(ManualClock clock) {
            InMemoryStateStore store = new InMemoryStateStore();
            ConstantRateRefillStrategy refill = new ConstantRateRefillStrategy(10, 5, 30, 10);
            LinearIdleCreditPolicy idle = new LinearIdleCreditPolicy(5, 20, 1.5);
            return new DREBLimiter(store, clock, refill, idle, 30);
        }

        @Test
        @DisplayName("First request from a new client is always ALLOW")
        void firstRequestIsAllowed() {
            ManualClock clock = new ManualClock(0.0);
            DREBLimiter limiter = buildLimiter(clock);
            assertEquals(Decision.ALLOW, limiter.allow("client1"));
        }

        @Test
        @DisplayName("Burst bucket drains and then DENY once exhausted (frozen clock)")
        void burstDrainsThenDenies() {
            ManualClock clock = new ManualClock(0.0);
            DREBLimiter limiter = buildLimiter(clock);

            // burstMax=10, so at most 10 consecutive allows at the burst level
            int allowed = 0;
            for (int i = 0; i < 40; i++) {
                if (limiter.allow("client1") == Decision.ALLOW) allowed++;
            }
            assertTrue(allowed > 0 && allowed <= 11,
                    "Expected between 1 and 11 allows (burst=10 + rounding), got " + allowed);
        }

        @Test
        @DisplayName("After time elapses, tokens refill and requests are ALLOW again")
        void refillAfterTimeElapse() {
            ManualClock clock = new ManualClock(0.0);
            DREBLimiter limiter = buildLimiter(clock);

            // Drain all tokens
            for (int i = 0; i < 40; i++) limiter.allow("client1");
            assertEquals(Decision.DENY, limiter.allow("client1"),
                    "Should be denied after draining");

            // Advance 2 seconds: burstRate=5 → +10 burst, sustainedRate=10 → +20 sustained
            clock.set(2.0);
            assertEquals(Decision.ALLOW, limiter.allow("client1"),
                    "Should be allowed again after refill");
        }

        @Test
        @DisplayName("Idle credit accrues when burst bucket is full, enables extra burst")
        void idleCreditAccruesAndEnablesExtraBurst() {
            ManualClock clock = new ManualClock(0.0);
            DREBLimiter limiter = buildLimiter(clock);

            // Touch client at t=0 to create state with full burst bucket
            limiter.peekState("idle-client");

            // Advance 10 seconds with no requests — burst stays full, idle credit accrues
            // idleGain=5 → +50 idle credit, capped at cap=20, so idleCredit=20
            clock.set(10.0);

            // Now burst (burstMax=10) + idleCredit(20) = 30 effective burst
            // But idle credit is spent at decay=1.5, so not a full 1:1 mapping.
            // Without idle credit a cold client would get ~10 from burst.
            // With idle credit we should get measurably more.
            int withIdle = 0;
            for (int i = 0; i < 30; i++) {
                clock.set(10.0 + i * 0.001); // tiny time steps, negligible refill
                if (limiter.allow("idle-client") == Decision.ALLOW) withIdle++;
            }

            // Compare against a cold client with no idle time
            ManualClock clock2 = new ManualClock(0.0);
            DREBLimiter limiter2 = buildLimiter(clock2);
            int cold = 0;
            for (int i = 0; i < 30; i++) {
                clock2.set(i * 0.001);
                if (limiter2.allow("cold-client") == Decision.ALLOW) cold++;
            }

            assertTrue(withIdle > cold,
                    "Idle client should accept more (" + withIdle + ") than cold client (" + cold + ")");
        }

        @Test
        @DisplayName("Multiple clients are isolated from each other")
        void multipleClientsAreIsolated() {
            ManualClock clock = new ManualClock(0.0);
            DREBLimiter limiter = buildLimiter(clock);

            // Drain client-a
            for (int i = 0; i < 40; i++) limiter.allow("client-a");
            assertEquals(Decision.DENY, limiter.allow("client-a"));

            // client-b should still be fresh
            assertEquals(Decision.ALLOW, limiter.allow("client-b"));
        }

        @Test
        @DisplayName("Cost > 1 consumes multiple tokens per request")
        void costGreaterThanOne() {
            ManualClock clock = new ManualClock(0.0);
            DREBLimiter limiter = buildLimiter(clock);

            // burstMax=10; cost=5 should allow 2 requests then deny
            int allowed = 0;
            for (int i = 0; i < 5; i++) {
                if (limiter.allow("costly-client", 5) == Decision.ALLOW) allowed++;
            }
            assertEquals(2, allowed, "Expected 2 allows at cost=5 with burstMax=10");
        }

        @Test
        @DisplayName("P1: Bucket empty and refills mid-gap: burstMax=10, rate=10, dt=2s -> credit for 1s")
        void midGapRefillAccrual() {
            ManualClock clock = new ManualClock(0.0);
            InMemoryStateStore store = new InMemoryStateStore();
            ConstantRateRefillStrategy refill = new ConstantRateRefillStrategy(10, 10, 50, 10);
            LinearIdleCreditPolicy idle = new LinearIdleCreditPolicy(5, 20, 1.5);
            DREBLimiter limiter = new DREBLimiter(store, clock, refill, idle, 50);

            // Drain all 10 burst tokens at t=0
            for (int i = 0; i < 10; i++) {
                assertEquals(Decision.ALLOW, limiter.allow("c-mid", 1));
            }
            clock.set(2.0); // 1s to refill, 1s full
            limiter.allow("c-mid", 1);
            ClientState s = limiter.peekState("c-mid");
            assertEquals(5.0, s.idleCredit, 1e-6, "Credit should be 1.0s * 5 = 5.0");
        }

        @Test
        @DisplayName("P1: Exact fill: dt equals time to fill -> 0 credit accrued")
        void exactFillAccrual() {
            ManualClock clock = new ManualClock(0.0);
            InMemoryStateStore store = new InMemoryStateStore();
            ConstantRateRefillStrategy refill = new ConstantRateRefillStrategy(10, 10, 50, 10);
            LinearIdleCreditPolicy idle = new LinearIdleCreditPolicy(5, 20, 1.5);
            DREBLimiter limiter = new DREBLimiter(store, clock, refill, idle, 50);

            // Drain all 10 burst tokens
            for (int i = 0; i < 10; i++) limiter.allow("c-exact", 1);
            // It takes exactly 1.0s to fill from 0 to 10 at rate 10
            clock.set(1.0);
            limiter.allow("c-exact", 1);
            ClientState s = limiter.peekState("c-exact");
            assertEquals(0.0, s.idleCredit, 1e-6, "Credit should be 0.0 when bucket only just filled at dt boundary");
        }

        @Test
        @DisplayName("P1: Already full: bucket full for entire dt -> credit for full dt")
        void alreadyFullAccrual() {
            ManualClock clock = new ManualClock(0.0);
            InMemoryStateStore store = new InMemoryStateStore();
            ConstantRateRefillStrategy refill = new ConstantRateRefillStrategy(10, 10, 50, 10);
            LinearIdleCreditPolicy idle = new LinearIdleCreditPolicy(5, 20, 1.5);
            DREBLimiter limiter = new DREBLimiter(store, clock, refill, idle, 50);

            limiter.peekState("c-full"); // initial full state at t=0
            clock.set(2.0); // full for all 2.0s
            limiter.allow("c-full", 1);
            ClientState s = limiter.peekState("c-full");
            assertEquals(10.0, s.idleCredit, 1e-6, "Credit should be 2.0s * 5 = 10.0");
        }

        @Test
        @DisplayName("P1: Long idle: idle credit is capped at idleCap")
        void longIdleAccrualCapped() {
            ManualClock clock = new ManualClock(0.0);
            InMemoryStateStore store = new InMemoryStateStore();
            ConstantRateRefillStrategy refill = new ConstantRateRefillStrategy(10, 10, 50, 10);
            LinearIdleCreditPolicy idle = new LinearIdleCreditPolicy(5, 20, 1.5);
            DREBLimiter limiter = new DREBLimiter(store, clock, refill, idle, 50);

            limiter.peekState("c-cap");
            clock.set(100.0); // 100s idle -> 500 credit potential, capped at 20
            limiter.allow("c-cap", 1);
            ClientState s = limiter.peekState("c-cap");
            assertEquals(20.0, s.idleCredit, 1e-6, "Credit should cap at 20.0");
        }

        @Test
        @DisplayName("P1: Zero refill rate: burst bucket cannot refill, so 0 idle credit")
        void zeroRefillRateAccrual() {
            ManualClock clock = new ManualClock(0.0);
            InMemoryStateStore store = new InMemoryStateStore();
            ConstantRateRefillStrategy refill = new ConstantRateRefillStrategy(10, 0, 50, 0);
            LinearIdleCreditPolicy idle = new LinearIdleCreditPolicy(5, 20, 1.5);
            DREBLimiter limiter = new DREBLimiter(store, clock, refill, idle, 50);

            for (int i = 0; i < 10; i++) limiter.allow("c-zero", 1);
            clock.set(10.0); // 10s pass, but rate is 0
            limiter.allow("c-zero", 1);
            ClientState s = limiter.peekState("c-zero");
            assertEquals(0.0, s.idleCredit, 1e-6, "Credit should be 0 when burst rate is 0");
        }

        @Test
        @DisplayName("P1: dt=0: no credit accrued")
        void dtZeroAccrual() {
            ManualClock clock = new ManualClock(0.0);
            InMemoryStateStore store = new InMemoryStateStore();
            ConstantRateRefillStrategy refill = new ConstantRateRefillStrategy(10, 10, 50, 10);
            LinearIdleCreditPolicy idle = new LinearIdleCreditPolicy(5, 20, 1.5);
            DREBLimiter limiter = new DREBLimiter(store, clock, refill, idle, 50);

            limiter.allow("c-dt0", 1);
            clock.set(0.0);
            limiter.allow("c-dt0", 1);
            ClientState s = limiter.peekState("c-dt0");
            assertEquals(0.0, s.idleCredit, 1e-6);
        }

        @Test
        @DisplayName("P1: Negative dt: clock rollback causes no credit and no state corruption")
        void negativeDtAccrual() {
            ManualClock clock = new ManualClock(10.0);
            InMemoryStateStore store = new InMemoryStateStore();
            ConstantRateRefillStrategy refill = new ConstantRateRefillStrategy(10, 10, 50, 10);
            LinearIdleCreditPolicy idle = new LinearIdleCreditPolicy(5, 20, 1.5);
            DREBLimiter limiter = new DREBLimiter(store, clock, refill, idle, 50);

            limiter.allow("c-neg", 1);
            clock.set(5.0); // negative dt
            limiter.allow("c-neg", 1);
            ClientState s = limiter.peekState("c-neg");
            assertEquals(0.0, s.idleCredit, 1e-6);
        }
    }

    // ── DREBLimiterV2 direct tests ──────────────────────────────────────

    @Nested
    @DisplayName("DREBLimiterV2 direct construction")
    class DREBLimiterV2DirectTests {

        @Test
        @DisplayName("V2 allows initial requests and responds to refill")
        void v2BasicOperations() {
            ManualClock clock = new ManualClock(0.0);
            InMemoryStateStore store = new InMemoryStateStore();
            ConstantRateRefillStrategy refill = new ConstantRateRefillStrategy(10, 5, 30, 10);
            AdaptiveIdleCreditPolicy idle = new AdaptiveIdleCreditPolicy(5, 20, 1.0, 0.3, 2.0, 1.5);
            DREBLimiterV2 limiter = new DREBLimiterV2(store, clock, refill, idle, 30, 1.0);

            assertEquals(Decision.ALLOW, limiter.allow("client"));

            // Drain
            for (int i = 0; i < 40; i++) limiter.allow("client");
            assertEquals(Decision.DENY, limiter.allow("client"));

            // Refill
            clock.set(100.0);
            assertEquals(Decision.ALLOW, limiter.allow("client"));
        }

        @Test
        @DisplayName("V2 abuse-adaptive: repeat abuser gets penalized more than first-time burster")
        void v2AbuseAdaptivePenalty() {
            // The key v2 property: a repeat abuser should be allowed fewer total
            // requests across multiple idle-burst cycles than a fresh client doing
            // the same burst pattern for the first time. This is what distinguishes
            // v2 from v1 — the escalating sustainedCostGain.
            ManualClock clock = new ManualClock(0.0);
            InMemoryStateStore store = new InMemoryStateStore();
            ConstantRateRefillStrategy refill = new ConstantRateRefillStrategy(10, 5, 30, 10);
            AdaptiveIdleCreditPolicy idle = new AdaptiveIdleCreditPolicy(5, 20, 1.0, 0.3, 2.0, 1.5);
            DREBLimiterV2 limiter = new DREBLimiterV2(store, clock, refill, idle, 30, 1.0);

            // First cycle: idle then burst
            limiter.peekState("abuser"); // touch at t=0
            clock.set(20.0); // idle
            int firstCycle = 0;
            for (int i = 0; i < 50; i++) {
                clock.set(20.0 + i * 0.01);
                if (limiter.allow("abuser") == Decision.ALLOW) firstCycle++;
            }

            // Second cycle: idle again then burst again
            clock.set(40.0); // idle
            int secondCycle = 0;
            for (int i = 0; i < 50; i++) {
                clock.set(40.0 + i * 0.01);
                if (limiter.allow("abuser") == Decision.ALLOW) secondCycle++;
            }

            // The second cycle should allow fewer or equal requests due to abuse tracking
            assertTrue(secondCycle <= firstCycle,
                    "V2 should penalize repeat bursters: first=" + firstCycle + ", second=" + secondCycle);
        }
    }

    // ── RedisStateStore tests ───────────────────────────────────────────

    @Nested
    @DisplayName("RedisStateStore with fake RedisHashClient")
    class RedisStateStoreTests {

        private final Map<String, Map<String, String>> fakeRedis = new HashMap<>();
        private final RedisHashClient mockClient = new RedisHashClient() {
            @Override
            public Map<String, String> hgetAll(String key) {
                return fakeRedis.getOrDefault(key, new HashMap<>());
            }
            @Override
            public void hset(String key, Map<String, String> fields) {
                fakeRedis.put(key, new HashMap<>(fields));
            }
        };

        @Test
        @DisplayName("Save and load round-trips all four fields correctly")
        void saveAndLoadRoundTrip() {
            RedisStateStore store = new RedisStateStore(mockClient, "rl");
            ClientState original = new ClientState(10.5, 20.25, 5.125, 100.75);
            store.save("client-a", original);

            assertTrue(fakeRedis.containsKey("rl:client-a"),
                    "Key should be prefix:clientId");

            ClientState loaded = store.load("client-a", () -> ClientState.fresh(0, 0, 0));
            assertEquals(10.5, loaded.burstTokens, 1e-9);
            assertEquals(20.25, loaded.sustainedTokens, 1e-9);
            assertEquals(5.125, loaded.idleCredit, 1e-9);
            assertEquals(100.75, loaded.lastRefillTs, 1e-9);
        }

        @Test
        @DisplayName("Load for missing client returns initializer's fresh state")
        void loadMissingClientReturnsFresh() {
            RedisStateStore store = new RedisStateStore(mockClient, "rl");
            ClientState fresh = store.load("unknown", () -> ClientState.fresh(42, 99, 1.0));
            assertEquals(42.0, fresh.burstTokens, 1e-9);
            assertEquals(99.0, fresh.sustainedTokens, 1e-9);
            assertEquals(0.0, fresh.idleCredit, 1e-9);
            assertEquals(1.0, fresh.lastRefillTs, 1e-9);
        }

        @Test
        @DisplayName("Key prefix correctly namespaces different stores")
        void keyPrefixNamespacing() {
            RedisStateStore storeA = new RedisStateStore(mockClient, "svc-a");
            RedisStateStore storeB = new RedisStateStore(mockClient, "svc-b");

            storeA.save("user1", new ClientState(1, 2, 3, 4));
            storeB.save("user1", new ClientState(10, 20, 30, 40));

            ClientState fromA = storeA.load("user1", () -> ClientState.fresh(0, 0, 0));
            ClientState fromB = storeB.load("user1", () -> ClientState.fresh(0, 0, 0));

            assertEquals(1.0, fromA.burstTokens, 1e-9);
            assertEquals(10.0, fromB.burstTokens, 1e-9);
        }
    }

    // ── InMemoryStateStore tests ────────────────────────────────────────

    @Nested
    @DisplayName("InMemoryStateStore")
    class InMemoryStateStoreTests {

        @Test
        @DisplayName("Tracks distinct clients and reports size")
        void sizeTracksDistinctClients() {
            InMemoryStateStore store = new InMemoryStateStore();
            assertEquals(0, store.size());

            store.load("a", () -> ClientState.fresh(10, 10, 0));
            assertEquals(1, store.size());

            store.load("b", () -> ClientState.fresh(10, 10, 0));
            assertEquals(2, store.size());

            // Loading an existing client should not increase size
            store.load("a", () -> ClientState.fresh(10, 10, 0));
            assertEquals(2, store.size());
        }
    }

    // ── Validation / invalid-input tests ────────────────────────────────

    @Nested
    @DisplayName("Input validation")
    class ValidationTests {

        @Test
        @DisplayName("Building with missing capacity throws IllegalStateException")
        void missingCapacity() {
            assertThrows(IllegalStateException.class,
                    () -> Dreb.builder().refillRate(10).build());
        }

        @Test
        @DisplayName("Building with missing refillRate throws IllegalStateException")
        void missingRefillRate() {
            assertThrows(IllegalStateException.class,
                    () -> Dreb.builder().capacity(10).build());
        }

        @Test
        @DisplayName("Building with negative capacity throws IllegalStateException")
        void negativeCapacity() {
            assertThrows(IllegalStateException.class,
                    () -> Dreb.builder().capacity(-1).refillRate(10).build());
        }

        @Test
        @DisplayName("Building with negative refillRate throws IllegalStateException")
        void negativeRefillRate() {
            assertThrows(IllegalStateException.class,
                    () -> Dreb.builder().capacity(10).refillRate(-5).build());
        }

        @Test
        @DisplayName("Building with zero capacity throws IllegalStateException")
        void zeroCapacity() {
            assertThrows(IllegalStateException.class,
                    () -> Dreb.builder().capacity(0).refillRate(10).build());
        }

        @Test
        @DisplayName("V2 builder with missing capacity throws IllegalStateException")
        void v2MissingCapacity() {
            assertThrows(IllegalStateException.class,
                    () -> Dreb.builderV2().refillRate(10).build());
        }

        @Test
        @DisplayName("Null clientId throws NullPointerException")
        void nullClientId() {
            RateLimiter limiter = Dreb.builder().capacity(10).refillRate(10).build();
            assertThrows(NullPointerException.class, () -> limiter.allow(null));
        }

        @Test
        @DisplayName("Unsupported IdleCreditPolicy on atomic store throws UnsupportedOperationException")
        void unsupportedIdleCreditPolicyOnAtomicStore() {
            StateStore atomicStore = new StateStore() {
                @Override public boolean supportsAtomicAllow() { return true; }
                @Override public Decision atomicAllow(String id, int cost, double now, AtomicAllowContext ctx) { return Decision.ALLOW; }
                @Override public ClientState load(String id, ClientStateInitializer init) { return init.initial(); }
                @Override public void save(String id, ClientState state) {}
            };
            IdleCreditPolicy customPolicy = new IdleCreditPolicy() {
                @Override public void accrue(ClientState s, double dt, double tFull) {}
                @Override public void spend(ClientState s, double d) {}
                @Override public double effectiveCredit(ClientState s) { return 0; }
            };
            DREBLimiter limiter = new DREBLimiter(atomicStore, new ManualClock(0.0),
                    new ConstantRateRefillStrategy(10, 5, 20, 10),
                    customPolicy, 20);

            UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class,
                    () -> limiter.allow("client1", 1));
            assertTrue(ex.getMessage().contains("unsupported for atomic stores"));
        }
    }
}
