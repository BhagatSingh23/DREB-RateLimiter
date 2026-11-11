package com.dreb.ratelimit.redis;

import com.dreb.ratelimit.Decision;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 6.1 Audit: State Lifecycle, TTL Expiration, and Abuse Reset Audit.
 * Tests exact behavior of B, S, I, K under key expiration and verifies linear recovery dynamics.
 */
public class Phase61StateResetAndTtlTest {

    private JedisPool jedisPool;
    private RedisRca70Limiter limiter;

    @BeforeEach
    public void setUp() {
        JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxTotal(16);
        config.setMaxIdle(8);
        config.setMinIdle(2);
        this.jedisPool = new JedisPool(config, "127.0.0.1", 6379, 2000);
        this.limiter = RedisRca70Limiter.createDefault(jedisPool, RedisRca70Limiter.ClockSource.CLIENT_TIMESTAMP, "p61");

        try (Jedis jedis = jedisPool.getResource()) {
            jedis.flushAll();
        }
    }

    @AfterEach
    public void tearDown() {
        if (jedisPool != null && !jedisPool.isClosed()) {
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.flushAll();
            }
            jedisPool.close();
        }
    }

    @Test
    @DisplayName("Audit Task 1.1: Key expiration after large adversarial debit clears abuse penalty K")
    public void testKeyExpirationClearsAbuseScore() {
        String clientId = "adv_expire_test";
        double t0 = 100.0;

        // 1. Initialize state at t0
        limiter.allow(clientId, 0, t0);
        // 2. Idle for 10.0s so I reaches full 450.0 and B remains 700.0
        limiter.allow(clientId, 0, t0 + 10.0);

        RedisRca70Limiter.RedisStateSnapshot sInit = limiter.peekState(clientId);
        assertNotNull(sInit);
        assertEquals(700.0, sInit.burstTokens(), 1e-4);
        assertEquals(450.0, sInit.idleCredit(), 1e-4);

        // 3. Adversary executes a full nominal burst of 1000
        Decision burstDec = limiter.allow(clientId, 1000, t0 + 10.0);
        assertEquals(Decision.ALLOW, burstDec, "Nominal burst of 1000 should be allowed");

        RedisRca70Limiter.RedisStateSnapshot sAfterBurst = limiter.peekState(clientId);
        assertNotNull(sAfterBurst);
        assertTrue(sAfterBurst.abuseScore() > 0.0, "Abuse score K must be positive after nominal burst deficit");
        assertEquals(0.0, sAfterBurst.burstTokens(), 1e-4);
        assertEquals(0.0, sAfterBurst.idleCredit(), 1e-4);

        // 4. Simulate key expiration by manually expiring the Redis key
        try (Jedis jedis = jedisPool.getResource()) {
            String stateKey = limiter.formatStateKey(clientId);
            jedis.del(stateKey);
        }

        // 5. Request arrives immediately after key expiration
        double tNext = t0 + 10.1; // only 0.1s later!
        Decision dec = limiter.allow(clientId, 700, tNext);
        assertEquals(Decision.ALLOW, dec, "New request should be allowed as a fresh client");

        RedisRca70Limiter.RedisStateSnapshot sAfterReset = limiter.peekState(clientId);
        assertNotNull(sAfterReset);
        // CRITICAL VULNERABILITY: Abuse score K was purged from >0 down to 0!
        assertEquals(0.0, sAfterReset.abuseScore(), 1e-4, "Abuse score K was wiped to 0 by key expiration!");
        // Burst tokens were restored to B_max = 700 then debited 700 -> 0
        assertEquals(0.0, sAfterReset.burstTokens(), 1e-4);
    }

    @Test
    @DisplayName("Audit Task 1.2: State comparison immediately before nominal TTL vs just after expiration")
    public void testStateTransitionBeforeAndAfterExpiration() {
        String clientId = "ttl_boundary_test";
        double t0 = 1000.0;
        long shortTtl = 5; // 5s short TTL for demonstration

        RedisRca70Limiter shortTtlLimiter = new RedisRca70Limiter(jedisPool,
                RedisRca70Limiter.ClockSource.CLIENT_TIMESTAMP, "p61",
                700.0, 1666.6667, 1285.7143, 1000.0,
                1500.0, 450.0, 1.5,
                0.75, 0.05,
                shortTtl, 60);

        // Client debits all burst tokens
        shortTtlLimiter.allow(clientId, 700, t0);
        RedisRca70Limiter.RedisStateSnapshot before = shortTtlLimiter.peekState(clientId);
        assertNotNull(before);
        assertEquals(0.0, before.burstTokens(), 1e-4);

        // Before TTL expiration (at t0 + 4s, key still exists)
        shortTtlLimiter.allow(clientId, 10, t0 + 4.0);
        assertTrue(shortTtlLimiter.peekState(clientId).burstTokens() > 0.0);

        // Now simulate expiration (key removed by TTL)
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.del(shortTtlLimiter.formatStateKey(clientId));
        }

        // Immediately after expiration (key deleted)
        RedisRca70Limiter.RedisStateSnapshot recreated = shortTtlLimiter.peekState(clientId);
        assertNull(recreated);

        // Submitting a request recreates the state with B=B_max, S=S_max
        shortTtlLimiter.allow(clientId, 10, t0 + 6.0);
        RedisRca70Limiter.RedisStateSnapshot afterRecreation = shortTtlLimiter.peekState(clientId);
        assertNotNull(afterRecreation);
        // B was reset to 700 then debited 10 -> 690
        assertEquals(690.0, afterRecreation.burstTokens(), 1.0);
    }

    @Test
    @DisplayName("Audit Task 1.3: Repeated client burst cycling just after expiration")
    public void testRepeatedClientBurstCyclingViaExpiration() {
        String clientId = "cycling_attacker";
        double t = 100.0;

        // Attacker sends burst of 700, waits for key to expire, then repeats
        for (int cycle = 0; cycle < 5; cycle++) {
            Decision dec = limiter.allow(clientId, 700, t);
            assertEquals(Decision.ALLOW, dec, "Burst in cycle " + cycle + " allowed");

            // Key expires
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.del(limiter.formatStateKey(clientId));
            }
            t += 1.0; // Advance only 1s!
        }
    }

    @Test
    @DisplayName("Audit Task 1.4: Expiration while client has depleted sustained capacity restores S_max")
    public void testExpirationRestoresDepletedSustainedCapacity() {
        String clientId = "sustained_depleted";
        double t = 50.0;

        // Debit sustained capacity via successive bursts as burst refills:
        // Cycle 1: t=50.0, cost=700 -> B=0, S = 1285.71 - 700 = 585.71
        limiter.allow(clientId, 700, t);
        // Advance 0.5s: B refills by 0.5 * 1666.67 = 700 (capped), S refills by 0.5 * 1000 = 500 (S = 1085.71)
        t += 0.5;
        // Cycle 2: cost=700 -> B=0, S = 1085.71 - 700 = 385.71
        limiter.allow(clientId, 700, t);
        // Advance 0.2s: B refills 333.33, S refills 200 (S = 585.71)
        t += 0.2;
        // Cycle 3: cost=330 -> B=3.33, S = 585.71 - 330 = 255.71
        limiter.allow(clientId, 330, t);
        // Advance 0.15s: B refills 250, S refills 150 (S = 405.71)
        t += 0.15;
        // Cycle 4: cost=250 -> B=3.33, S = 405.71 - 250 = 155.71
        limiter.allow(clientId, 250, t);
        // Advance 0.05s: B refills 83.33, S refills 50 (S = 205.71)
        t += 0.05;
        // Cycle 5: cost=80 -> B=6.66, S = 205.71 - 80 = 125.71
        limiter.allow(clientId, 80, t);
        // Cycle 6: cost=120 at same t -> B=0, deficit=113.33 (denied if I=0)
        // Let's check remaining sustained tokens
        RedisRca70Limiter.RedisStateSnapshot s = limiter.peekState(clientId);
        assertNotNull(s);
        double sRemaining = s.sustainedTokens();
        assertTrue(sRemaining < 300.0, "Sustained tokens should be heavily depleted: " + sRemaining);

        // Next request of cost 350 at t + 0.001 is denied due to sustained depletion
        Decision denied = limiter.allow(clientId, 350, t + 0.001);
        assertEquals(Decision.DENY, denied, "Should be denied due to S depletion");

        // Simulate premature key expiration
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.del(limiter.formatStateKey(clientId));
        }

        // Immediately retry at t + 0.002s
        Decision allowedAfterReset = limiter.allow(clientId, 700, t + 0.002);
        assertEquals(Decision.ALLOW, allowedAfterReset,
                "Premature expiration restored S_max = 1285.71 immediately, bypassing sustained throttling!");
    }

    @Test
    @DisplayName("Audit Task 1.5: Expiration after legitimate idle interval strips earned idle credit I")
    public void testExpirationStripsEarnedIdleCreditAndCausesFalseRejection() {
        String clientId = "legit_idle_user";
        double t0 = 100.0;

        // 1. Client initializes state at t0
        limiter.allow(clientId, 0, t0);

        // 2. Client idles for 10 seconds. In continuous state, I reaches 450.0
        limiter.allow(clientId, 0, t0 + 10.0);
        RedisRca70Limiter.RedisStateSnapshot sIdle = limiter.peekState(clientId);
        assertNotNull(sIdle);
        assertEquals(450.0, sIdle.idleCredit(), 1e-4, "Legitimate idle interval must accrue full 450.0 idle credit");

        // 3. Now assume client is idle for longer than Redis key TTL (e.g. key expires)
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.del(limiter.formatStateKey(clientId));
        }

        // 4. Client returns and attempts a nominal burst of cost C = 1000
        // Because key expired, cold start sets I = 0.0, B = 700.0
        // Effective burst = B + I / 1.5 = 700.0 + 0.0 = 700.0 < 1000.0!
        Decision dec = limiter.allow(clientId, 1000, t0 + 200.0);

        // CRITICAL AUDIT FINDING: Legitimate client suffers a FALSE REJECTION!
        assertEquals(Decision.DENY, dec,
                "Key expiration stripped idle credit I, causing false rejection of nominal 1000 burst!");
    }

    @Test
    @DisplayName("Audit Task 1.6: Expiration and recreation under concurrent requests")
    public void testConcurrentRecreationUnderExpiration() throws InterruptedException, ExecutionException {
        String clientId = "concurrent_expire_client";
        int threads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threads);

        // Set key then expire it
        limiter.allow(clientId, 10, 100.0);
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.del(limiter.formatStateKey(clientId));
        }

        // 8 threads simultaneously attempt request
        CountDownLatch latch = new CountDownLatch(1);
        List<Future<Decision>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                latch.await();
                return limiter.allow(clientId, 100, 101.0);
            }));
        }
        latch.countDown();

        int allowedCount = 0;
        for (Future<Decision> f : futures) {
            if (f.get() == Decision.ALLOW) {
                allowedCount++;
            }
        }
        executor.shutdown();

        // Fresh state has B=700, S=1285.71.
        // Cost = 100. 7 requests take 700 burst tokens, all 8 can be allowed (since S >= 800)
        assertTrue(allowedCount >= 7, "All valid concurrent cold-start requests should be admitted without race errors");
        RedisRca70Limiter.RedisStateSnapshot finalState = limiter.peekState(clientId);
        assertNotNull(finalState);
        assertTrue(finalState.burstTokens() >= 0.0);
        assertTrue(finalState.sustainedTokens() >= 0.0);
    }

    @Test
    @DisplayName("Audit Task 1.7: Linear recovery equation verification (K(t) = max(0, K0 - lambda*t))")
    public void testLinearRecoveryEquationExactDuration() {
        String clientId = "linear_recovery_test";
        double t0 = 1000.0;

        limiter.allow(clientId, 0, t0);
        limiter.allow(clientId, 0, t0 + 10.0); // I = 450.0, B = 700.0

        // Incur deficit of 300: K += 300 / 450 = 2/3 ≈ 0.666667
        Decision burstDec = limiter.allow(clientId, 1000, t0 + 10.0);
        assertEquals(Decision.ALLOW, burstDec);

        RedisRca70Limiter.RedisStateSnapshot s0 = limiter.peekState(clientId);
        assertNotNull(s0);
        double k0 = s0.abuseScore();
        assertEquals(2.0 / 3.0, k0, 1e-4);

        // Theoretical linear recovery time to reach exactly 0.0:
        // tau = K0 / lambda_rec = (2/3) / 0.05 = 13.3333 seconds!
        double lambdaRec = 0.05;
        double tauExact = k0 / lambdaRec;
        assertEquals(13.3333, tauExact, 0.001);

        // Check K at half-recovery (t = tauExact / 2 = 6.6667s)
        limiter.allow(clientId, 0, t0 + 10.0 + (tauExact / 2.0));
        RedisRca70Limiter.RedisStateSnapshot sHalf = limiter.peekState(clientId);
        assertNotNull(sHalf);
        assertEquals(k0 / 2.0, sHalf.abuseScore(), 1e-3, "Linear recovery must be exactly half at tau/2");

        // Check K at exact recovery time (t = tauExact)
        limiter.allow(clientId, 0, t0 + 10.0 + tauExact);
        RedisRca70Limiter.RedisStateSnapshot sFull = limiter.peekState(clientId);
        assertNotNull(sFull);
        assertEquals(0.0, sFull.abuseScore(), 1e-4, "Linear recovery reaches exactly 0.0 at tau = K0 / lambda");
    }
}
