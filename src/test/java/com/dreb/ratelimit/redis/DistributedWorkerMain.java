package com.dreb.ratelimit.redis;

import com.dreb.ratelimit.Decision;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Random;

/**
 * Independent JVM Process Worker entry point for Phase 6 multi-process concurrency testing.
 */
public class DistributedWorkerMain {

    public static void main(String[] args) throws Exception {
        if (args.length < 12) {
            System.err.println("Usage: DistributedWorkerMain <workerId> <redisHost> <redisPort> <keyPrefix> <clientId> <seed> <numRequests> <cost> <intervalMs> <barrierKey> <expectedWorkers> <outCsvPath>");
            System.exit(1);
        }

        int workerId = Integer.parseInt(args[0]);
        String redisHost = args[1];
        int redisPort = Integer.parseInt(args[2]);
        String keyPrefix = args[3];
        String clientId = args[4];
        long seed = Long.parseLong(args[5]);
        int numRequests = Integer.parseInt(args[6]);
        int cost = Integer.parseInt(args[7]);
        long intervalMs = Long.parseLong(args[8]);
        String barrierKey = args[9];
        int expectedWorkers = Integer.parseInt(args[10]);
        String outCsvPath = args[11];

        JedisPool pool = new JedisPool(redisHost, redisPort);
        RedisRca70Limiter limiter = RedisRca70Limiter.createDefault(pool, RedisRca70Limiter.ClockSource.REDIS_SERVER_TIME, keyPrefix);

        // 1. Synchronized start barrier: Increment barrier counter in Redis
        try (Jedis jedis = pool.getResource()) {
            jedis.incr(barrierKey);
        }

        // Wait until all workers have registered
        long barrierTimeoutMs = 15_000;
        long startWait = System.currentTimeMillis();
        boolean ready = false;
        while (System.currentTimeMillis() - startWait < barrierTimeoutMs) {
            try (Jedis jedis = pool.getResource()) {
                String val = jedis.get(barrierKey);
                if (val != null && Integer.parseInt(val) >= expectedWorkers) {
                    ready = true;
                    break;
                }
            }
            Thread.sleep(10);
        }

        if (!ready) {
            System.err.println("Worker " + workerId + ": Barrier timeout waiting for " + expectedWorkers + " workers.");
            System.exit(2);
        }

        // Short stabilization pause to align start across processes
        Thread.sleep(50);

        Random rng = new Random(seed + workerId);
        int admittedCount = 0;
        int deniedCount = 0;

        try (PrintWriter pw = new PrintWriter(new FileWriter(outCsvPath))) {
            pw.println("worker_id,request_index,timestamp_ms,cost,decision,latency_ns");

            for (int i = 0; i < numRequests; i++) {
                long tStart = System.nanoTime();
                long tWall = System.currentTimeMillis();

                Decision d = limiter.allow(clientId, cost);
                long latencyNs = System.nanoTime() - tStart;

                if (d == Decision.ALLOW) {
                    admittedCount++;
                } else {
                    deniedCount++;
                }

                pw.println(workerId + "," + i + "," + tWall + "," + cost + "," + d.name() + "," + latencyNs);

                if (intervalMs > 0) {
                    long jitter = rng.nextInt(Math.max(1, (int) (intervalMs / 2) + 1));
                    Thread.sleep(Math.max(0, intervalMs + jitter - (intervalMs / 4)));
                }
            }
        }

        pool.close();
        System.out.println("Worker " + workerId + " finished: admitted=" + admittedCount + ", denied=" + deniedCount);
        System.exit(0);
    }
}
