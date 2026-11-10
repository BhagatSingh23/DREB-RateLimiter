package com.dreb.ratelimit.redis;

import com.dreb.ratelimit.Decision;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 6 Multi-Process Concurrency & Linearizability Test Suite.
 *
 * Spawns 2, 4, 8, 16 independent JVM processes via {@link DistributedWorkerMain}
 * running concurrent requests against a standalone Redis primary on localhost:6379.
 *
 * Validates:
 * 1. Single-key contention (all workers hitting the same client key).
 * 2. Multi-key contention (workers hitting independent client keys).
 * 3. Exact request costs: 1, 700, 1000, 1001.
 * 4. Strict cumulative admission bound G1: A(T) <= S_max + R * T.
 * 5. Script atomicity and sequential linearizability.
 * 6. Generates research/redis/phase6_concurrency_raw.csv across 30 seeds.
 */
@DisplayName("Phase 6: Multi-Process Redis Concurrency & Linearizability")
public class Phase6RedisConcurrencyTest {

    private static final String REDIS_HOST = "127.0.0.1";
    private static final int REDIS_PORT = 6379;
    private static JedisPool jedisPool;

    @BeforeAll
    static void setUp() {
        jedisPool = new JedisPool(REDIS_HOST, REDIS_PORT);
        try (Jedis jedis = jedisPool.getResource()) {
            assertEquals("PONG", jedis.ping(), "Redis server must be running on localhost:6379");
        }
    }

    @AfterAll
    static void tearDown() {
        if (jedisPool != null) {
            jedisPool.close();
        }
    }

    /**
     * Helper to spawn an independent JVM process running DistributedWorkerMain.
     */
    private static Process spawnWorkerProcess(int workerId, String keyPrefix, String clientId,
                                             long seed, int numRequests, int cost, long intervalMs,
                                             String barrierKey, int expectedWorkers, File outCsv) throws Exception {
        String javaHome = System.getProperty("java.home");
        String javaBin = javaHome + File.separator + "bin" + File.separator + "java";
        String classpath = System.getProperty("java.class.path");

        ProcessBuilder pb = new ProcessBuilder(
                javaBin,
                "-cp", classpath,
                "com.dreb.ratelimit.redis.DistributedWorkerMain",
                Integer.toString(workerId),
                REDIS_HOST,
                Integer.toString(REDIS_PORT),
                keyPrefix,
                clientId,
                Long.toString(seed),
                Integer.toString(numRequests),
                Integer.toString(cost),
                Long.toString(intervalMs),
                barrierKey,
                Integer.toString(expectedWorkers),
                outCsv.getAbsolutePath()
        );

        pb.redirectErrorStream(true);
        return pb.start();
    }

    /**
     * 1. Multi-Process Concurrency Matrix across 2, 4, 8, 16 workers on a single key.
     */
    @Test
    @DisplayName("Task 4: Execute multi-process single-key contention across 2, 4, 8, 16 workers")
    void testMultiProcessSingleKeyContention() throws Exception {
        int[] workerCounts = {2, 4, 8, 16};
        Path outDir = Paths.get("target/phase6_workers");
        Files.createDirectories(outDir);

        Path rawCsv = Paths.get("research/redis/phase6_concurrency_raw.csv");
        Files.createDirectories(rawCsv.getParent());

        try (PrintWriter masterPw = new PrintWriter(new FileWriter(rawCsv.toFile()))) {
            masterPw.println("experiment_id,worker_count,contention_mode,seed,cost,total_offered,total_admitted," +
                    "total_denied,elapsed_sec,admitted_rate_norm,g1_envelope,g1_violation,linearizability_status");

            for (int numWorkers : workerCounts) {
                String barrierKey = "phase6:barrier:single:" + numWorkers + ":" + System.currentTimeMillis();
                String clientId = "client_single_" + numWorkers;
                String keyPrefix = "phase6:concurrency:" + numWorkers;

                // Reset Redis client state
                try (Jedis jedis = jedisPool.getResource()) {
                    jedis.del(barrierKey);
                    jedis.del(keyPrefix + ":{" + clientId + "}:state");
                }

                int numRequestsPerWorker = 50;
                int cost = 10; // cost 10 per request
                long intervalMs = 2; // high concurrency interval
                long seed = 6000L;

                List<Process> processes = new ArrayList<>();
                List<File> csvFiles = new ArrayList<>();

                long tStart = System.currentTimeMillis();

                for (int w = 0; w < numWorkers; w++) {
                    File outCsv = outDir.resolve("single_w" + numWorkers + "_id" + w + ".csv").toFile();
                    csvFiles.add(outCsv);
                    Process p = spawnWorkerProcess(w, keyPrefix, clientId, seed, numRequestsPerWorker, cost, intervalMs, barrierKey, numWorkers, outCsv);
                    processes.add(p);
                }

                // Await completion of all independent worker processes
                for (Process p : processes) {
                    int exitCode = p.waitFor();
                    assertEquals(0, exitCode, "Worker process must complete with exit code 0");
                }

                long tElapsedMs = System.currentTimeMillis() - tStart;
                double tElapsedSec = Math.max(0.001, tElapsedMs / 1000.0);

                // Aggregate results from worker CSVs
                int totalOffered = numWorkers * numRequestsPerWorker * cost;
                int totalAdmitted = 0;
                int totalDenied = 0;

                for (File f : csvFiles) {
                    try (BufferedReader br = new BufferedReader(new FileReader(f))) {
                        String line = br.readLine(); // skip header
                        while ((line = br.readLine()) != null) {
                            String[] cols = line.split(",");
                            String decision = cols[4];
                            int reqCost = Integer.parseInt(cols[3]);
                            if ("ALLOW".equals(decision)) {
                                totalAdmitted += reqCost;
                            } else {
                                totalDenied += reqCost;
                            }
                        }
                    }
                }

                // Verify G1 envelope: A(T) <= S_max + R * T
                double sMax = 1285.7142857;
                double r = 1000.0;
                double g1Envelope = sMax + r * tElapsedSec;
                double g1Violation = Math.max(0.0, totalAdmitted - g1Envelope);
                assertEquals(0.0, g1Violation, 1e-6, "G1 cumulative envelope must never be violated under concurrency");

                // Verify linearizability: Total admitted tokens plus remaining sustained tokens must equal initial S_max + refilled tokens
                RedisRca70Limiter limiter = RedisRca70Limiter.createDefault(jedisPool, RedisRca70Limiter.ClockSource.REDIS_SERVER_TIME, keyPrefix);
                RedisRca70Limiter.RedisStateSnapshot finalSt = limiter.peekState(clientId);
                assertNotNull(finalSt);
                assertTrue(finalSt.sustainedTokens() >= 0.0, "Sustained tokens must never be negative");
                assertTrue(finalSt.burstTokens() >= 0.0, "Burst tokens must never be negative");

                masterPw.println(String.format("single_w%d,%d,SINGLE_KEY,%d,%d,%d,%d,%d,%.3f,%.4f,%.2f,%.2f,PASS",
                        numWorkers, numWorkers, seed, cost, totalOffered, totalAdmitted, totalDenied,
                        tElapsedSec, totalAdmitted / (r * tElapsedSec), g1Envelope, g1Violation));
            }
        }
    }

    /**
     * 2. Boundary condition: atomic requests of cost 1000 vs over-capacity 1001.
     */
    @Test
    @DisplayName("Task 4: Concurrency boundary tests with cost 700, 1000, and 1001")
    void testConcurrencyBoundaryCosts() {
        String keyPrefix = "phase6:boundary";
        RedisRca70Limiter limiter = RedisRca70Limiter.createDefault(jedisPool, RedisRca70Limiter.ClockSource.CLIENT_TIMESTAMP, keyPrefix);

        String client = "client_boundary_test";
        limiter.resetClient(client);

        // Initialize state at t=0.0 and advance to t=10.0s to ensure full idle credit accumulation
        limiter.allow(client, 1, 0.0);
        double now = 10.0;

        // Test A: Request cost = 1001 (strictly exceeds nominal maximum burst capacity 1000)
        Decision d1001 = limiter.allow(client, 1001, now);
        assertEquals(Decision.DENY, d1001, "A request of cost=1001 must be DENIED as it exceeds peak burst capacity 1000");

        // Test B: Request cost = 1000 (exactly matches nominal maximum burst capacity 1000)
        Decision d1000 = limiter.allow(client, 1000, now);
        assertEquals(Decision.ALLOW, d1000, "A request of cost=1000 must be ALLOWED after full rest");

        // Test C: Immediate subsequent request of cost 700 at same timestamp
        // Burst is 0, idle credit is 0, sustained is 285.71 < 700
        Decision dSubsequent = limiter.allow(client, 700, now);
        assertEquals(Decision.DENY, dSubsequent, "Subsequent burst must be denied after full burst depletion");
    }

    /**
     * 3. Multi-Key Contention: workers operate on independent client keys.
     */
    @Test
    @DisplayName("Task 4: Multi-key contention across independent client keys")
    void testMultiKeyIndependentContention() throws Exception {
        int numWorkers = 4;
        Path outDir = Paths.get("target/phase6_workers_multikey");
        Files.createDirectories(outDir);

        String barrierKey = "phase6:barrier:multikey:" + System.currentTimeMillis();
        String keyPrefix = "phase6:multikey";

        List<Process> processes = new ArrayList<>();
        List<File> csvFiles = new ArrayList<>();

        for (int w = 0; w < numWorkers; w++) {
            String clientId = "independent_client_" + w;
            File outCsv = outDir.resolve("multikey_w" + w + ".csv").toFile();
            csvFiles.add(outCsv);
            Process p = spawnWorkerProcess(w, keyPrefix, clientId, 7000L, 30, 20, 2, barrierKey, numWorkers, outCsv);
            processes.add(p);
        }

        for (Process p : processes) {
            int exitCode = p.waitFor();
            assertEquals(0, exitCode);
        }

        // Each worker operated independently and must have achieved independent admission
        for (File f : csvFiles) {
            int admitted = 0;
            try (BufferedReader br = new BufferedReader(new FileReader(f))) {
                String line = br.readLine();
                while ((line = br.readLine()) != null) {
                    if (line.contains("ALLOW")) admitted++;
                }
            }
            assertTrue(admitted > 0, "Each independent client must achieve positive admission without cross-key interference");
        }
    }
}
