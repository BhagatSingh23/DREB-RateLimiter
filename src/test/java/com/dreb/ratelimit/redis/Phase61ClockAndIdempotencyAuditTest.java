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
 * Phase 6.1 Audit: Server Clock Continuity, Clock Jumps, and Idempotency Lifecycle Audit.
 */
public class Phase61ClockAndIdempotencyAuditTest {

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

    // =========================================================================
    // TASK 2: SERVER CLOCK BEHAVIOR & CONTINUITY
    // =========================================================================

    @Test
    @DisplayName("Audit Task 2.1: Server clock backward step clamps dt=0 and freezes refills")
    public void testServerClockBackwardStepFreezesRefill() {
        String clientId = "clock_backward_test";
        double t0 = 1000.0;

        // Initialize state at t0
        limiter.allow(clientId, 500, t0);
        RedisRca70Limiter.RedisStateSnapshot s0 = limiter.peekState(clientId);
        assertNotNull(s0);
        assertEquals(200.0, s0.burstTokens(), 1e-4);
        assertEquals(1000.0, s0.lastRefillTs(), 1e-4);

        // Host clock jumps backward by 10 seconds: now = 990.0 < lastRefillTs (1000.0)
        double tBackward = 990.0;
        Decision dec = limiter.allow(clientId, 10, tBackward);
        assertEquals(Decision.ALLOW, dec);

        RedisRca70Limiter.RedisStateSnapshot sBackward = limiter.peekState(clientId);
        assertNotNull(sBackward);
        // dt was clamped to 0: burst balance was debited from 200 to 190 with ZERO refill
        assertEquals(190.0, sBackward.burstTokens(), 1e-4);
        // lastRefillTs remains at 1000.0 (not pushed backward)
        assertEquals(1000.0, sBackward.lastRefillTs(), 1e-4);

        // Further requests while now < 1000.0 continue to have dt=0 (frozen refills)
        limiter.allow(clientId, 10, 995.0);
        RedisRca70Limiter.RedisStateSnapshot sMid = limiter.peekState(clientId);
        assertNotNull(sMid);
        assertEquals(180.0, sMid.burstTokens(), 1e-4);
        assertEquals(1000.0, sMid.lastRefillTs(), 1e-4);

        // Once clock advances past 1000.0, normal refill resumes
        limiter.allow(clientId, 0, 1001.0); // dt = 1.0s
        RedisRca70Limiter.RedisStateSnapshot sResumed = limiter.peekState(clientId);
        assertNotNull(sResumed);
        assertEquals(700.0, sResumed.burstTokens(), 1e-4); // Refilled to B_max
        assertEquals(1001.0, sResumed.lastRefillTs(), 1e-4);
    }

    @Test
    @DisplayName("Audit Task 2.2: Server clock forward step accelerates refill and penalty recovery")
    public void testServerClockForwardStepAcceleratesRefill() {
        String clientId = "clock_forward_test";
        double t0 = 100.0;

        limiter.allow(clientId, 700, t0);
        RedisRca70Limiter.RedisStateSnapshot s0 = limiter.peekState(clientId);
        assertNotNull(s0);
        assertEquals(0.0, s0.burstTokens(), 1e-4);

        // Host clock jumps forward by 1 hour (3600s)
        double tJump = t0 + 3600.0;
        limiter.allow(clientId, 10, tJump);
        RedisRca70Limiter.RedisStateSnapshot sJump = limiter.peekState(clientId);
        assertNotNull(sJump);

        // State fully replenished: B=690, S=1275.71, I=450.0, K=0.0
        assertEquals(690.0, sJump.burstTokens(), 1.0);
        assertEquals(450.0, sJump.idleCredit(), 1.0);
        assertEquals(0.0, sJump.abuseScore(), 1e-4);
    }

    @Test
    @DisplayName("Audit Task 2.3: Failover to replica with drifted clock causes temporary refill freeze")
    public void testReplicaFailoverClockSkewFreezesRefill() {
        String clientId = "failover_clock_skew";
        double tPrimary = 2000.0;

        // Primary processed request at tPrimary = 2000.0
        limiter.allow(clientId, 400, tPrimary);
        assertEquals(300.0, limiter.peekState(clientId).burstTokens(), 1e-4);

        // Failover occurs to replica whose clock is 3.0s behind (replica clock = 1997.0s)
        double tReplica = 1997.0;
        limiter.allow(clientId, 100, tReplica);
        RedisRca70Limiter.RedisStateSnapshot sReplica = limiter.peekState(clientId);
        assertNotNull(sReplica);

        // Refill is frozen on promoted replica for 3 seconds:
        assertEquals(200.0, sReplica.burstTokens(), 1e-4);
        assertEquals(2000.0, sReplica.lastRefillTs(), 1e-4);
    }

    @Test
    @DisplayName("Audit Task 2.4: Time basis consistency across refill, idle credit, and abuse recovery")
    public void testTimeBasisConsistency() {
        String clientId = "time_basis_test";
        double t0 = 500.0;
        double dt = 2.0;

        limiter.allow(clientId, 10, t0);
        limiter.allow(clientId, 10, t0 + dt);

        RedisRca70Limiter.RedisStateSnapshot s = limiter.peekState(clientId);
        assertNotNull(s);
        // All state transitions used the exact same dt = 2.0
        assertEquals(t0 + dt, s.lastRefillTs(), 1e-4);
        assertTrue(s.idleCredit() > 0.0);
    }

    // =========================================================================
    // TASK 3: IDEMPOTENCY LIFECYCLE & RETRY CORRECTNESS
    // =========================================================================

    @Test
    @DisplayName("Audit Task 3.1: Same request submitted twice returns cached decision with zero second debit")
    public void testDuplicateRequestZeroSecondDebit() {
        String clientId = "idemp_dup_client";
        String idempKey = "req-uuid-001";
        double t0 = 100.0;

        Decision d1 = limiter.allow(clientId, 500, t0, idempKey);
        assertEquals(Decision.ALLOW, d1);
        RedisRca70Limiter.RedisStateSnapshot s1 = limiter.peekState(clientId);
        assertNotNull(s1);
        assertEquals(200.0, s1.burstTokens(), 1e-4);

        // Immediate retry with identical key
        Decision d2 = limiter.allow(clientId, 500, t0, idempKey);
        assertEquals(Decision.ALLOW, d2);
        RedisRca70Limiter.RedisStateSnapshot s2 = limiter.peekState(clientId);
        assertNotNull(s2);
        // Burst tokens must remain exactly 200.0 (NO SECOND DEBIT)
        assertEquals(200.0, s2.burstTokens(), 1e-4);
    }

    @Test
    @DisplayName("Audit Task 3.2: Concurrent submissions with same idempotency key across threads")
    public void testConcurrentSubmissionsSameIdempotencyKey() throws InterruptedException, ExecutionException {
        String clientId = "concurrent_idemp_client";
        String idempKey = "concurrent-key-999";
        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);

        CountDownLatch latch = new CountDownLatch(1);
        List<Future<Decision>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                latch.await();
                return limiter.allow(clientId, 300, 100.0, idempKey);
            }));
        }
        latch.countDown();

        for (Future<Decision> f : futures) {
            assertEquals(Decision.ALLOW, f.get());
        }
        executor.shutdown();

        // Exactly one debit of 300 occurred: 700 - 300 = 400
        RedisRca70Limiter.RedisStateSnapshot s = limiter.peekState(clientId);
        assertNotNull(s);
        assertEquals(400.0, s.burstTokens(), 1e-4);
    }

    @Test
    @DisplayName("Audit Task 3.3: Ambiguous timeout retry returns cached outcome")
    public void testAmbiguousTimeoutRetry() {
        String clientId = "ambiguous_timeout_client";
        String idempKey = "req-ambiguous-01";

        // Initial submission executed in Redis but client socket times out before receiving ACK
        limiter.allow(clientId, 700, 100.0, idempKey);
        assertEquals(0.0, limiter.peekState(clientId).burstTokens(), 1e-4);

        // Client retries
        Decision retryDec = limiter.allow(clientId, 700, 100.5, idempKey);
        assertEquals(Decision.ALLOW, retryDec);
        // Zero double debit
        assertEquals(0.0, limiter.peekState(clientId).burstTokens(), 1e-4);
    }

    @Test
    @DisplayName("Audit Task 3.4: Retry after idempotency key expiration causes double debit")
    public void testRetryAfterIdempotencyKeyExpirationCausesDoubleDebit() {
        String clientId = "expired_idemp_client";
        String idempKey = "req-short-ttl";
        long shortIdempTtl = 2; // 2 seconds

        RedisRca70Limiter shortLimiter = new RedisRca70Limiter(jedisPool,
                RedisRca70Limiter.ClockSource.CLIENT_TIMESTAMP, "p61",
                700.0, 1666.6667, 1285.7143, 1000.0,
                1500.0, 450.0, 1.5,
                0.75, 0.05,
                120, shortIdempTtl);

        // Submit request
        shortLimiter.allow(clientId, 300, 100.0, idempKey);
        assertEquals(400.0, shortLimiter.peekState(clientId).burstTokens(), 1e-4);

        // Simulate idempotency key expiration in Redis
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.del(shortLimiter.formatIdempKey(clientId, idempKey));
        }

        // Retry arrives AFTER idempotency key expiration with zero elapsed time (dt=0)
        shortLimiter.allow(clientId, 300, 100.0, idempKey);
        RedisRca70Limiter.RedisStateSnapshot sAfterExpiredRetry = shortLimiter.peekState(clientId);
        assertNotNull(sAfterExpiredRetry);

        // CRITICAL AUDIT FINDING: Second debit applied because idempotency record was expired!
        assertEquals(100.0, sAfterExpiredRetry.burstTokens(), 1e-4,
                "Expired idempotency record allowed double-debit (400 - 300 = 100)!");
    }

    @Test
    @DisplayName("Audit Task 3.5: Same idempotency key used by different clients are strictly isolated")
    public void testSameIdempotencyKeyAcrossDifferentClients() {
        String clientA = "user_alpha";
        String clientB = "user_beta";
        String sharedKey = "tx-12345";

        // Alpha uses sharedKey
        Decision da = limiter.allow(clientA, 500, 100.0, sharedKey);
        assertEquals(Decision.ALLOW, da);
        assertEquals(200.0, limiter.peekState(clientA).burstTokens(), 1e-4);

        // Beta uses identical sharedKey
        Decision db = limiter.allow(clientB, 500, 100.0, sharedKey);
        assertEquals(Decision.ALLOW, db);
        assertEquals(200.0, limiter.peekState(clientB).burstTokens(), 1e-4);

        // Both clients debited independently due to {clientId} key namespacing
    }

    @Test
    @DisplayName("Audit Task 3.6: Idempotency key reused with different request cost returns cached decision without evaluating cost")
    public void testSameIdempotencyKeyReusedWithDifferentCostReturnsCachedWithoutDebit() {
        String clientId = "cost_mismatch_client";
        String idempKey = "req-cost-mismatch";

        // 1. Submit initial request of cost 10 -> ALLOWED
        Decision d1 = limiter.allow(clientId, 10, 100.0, idempKey);
        assertEquals(Decision.ALLOW, d1);
        assertEquals(690.0, limiter.peekState(clientId).burstTokens(), 1e-4);

        // 2. Submit second request with SAME idempotency key but cost 1000!
        // A cost 1000 request on state with I=0 would normally be DENIED!
        Decision d2 = limiter.allow(clientId, 1000, 100.0, idempKey);

        // CRITICAL FINDING: Idempotency cache is payload-blind; it returns cached ALLOW
        assertEquals(Decision.ALLOW, d2, "Cached decision returned without evaluating new cost");
        // State remains at 690.0 (no debit for the 1000 request)
        assertEquals(690.0, limiter.peekState(clientId).burstTokens(), 1e-4);
    }
}
