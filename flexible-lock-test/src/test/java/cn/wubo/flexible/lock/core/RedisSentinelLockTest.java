package cn.wubo.flexible.lock.core;

import cn.wubo.entity.sql.IntegrationTestBase;
import cn.wubo.flexible.lock.propertes.FlexibleLockProperties;
import cn.wubo.flexible.lock.propertes.LockType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end test for {@link RedisSentinelLock} backed by a real Sentinel
 * cluster (1 master + 1 replica + 1 sentinel) running in Docker.
 *
 * <p>Setup (one-time):
 * <pre>{@code
 * # Master
 * docker run -d --name flex-sm --net flexible-lock-net \
 *     -p 6379:6379 redis:6-alpine redis-server --port 6379
 *
 * # Replica of master
 * docker run -d --name flex-sr --net flexible-lock-net \
 *     -p 6380:6380 redis:6-alpine redis-server --port 6380 \
 *     --replicaof 172.20.0.20 6379
 *
 * # Sentinel (monitor master with name "mymaster")
 * docker run -d --name flex-sentinel --net flexible-lock-net \
 *     -p 26379:26379 redis:6-alpine \
 *     redis-sentinel --port 26379 \
 *     --sentinel monitor mymaster 172.20.0.20 6379 1 \
 *     --sentinel down-after-milliseconds mymaster 5000 \
 *     --sentinel parallel-syncs mymaster 1 \
 *     --sentinel failover-timeout mymaster 10000
 * }</pre>
 *
 * <p>Verifies:
 * <ul>
 *   <li>constructor builds a live sentinel-aware RedissonClient;</li>
 *   <li>tryLock/unLock round-trip;</li>
 *   <li>two independent instances share the same master key (server-side
 *       mutual exclusion);</li>
 *   <li>{@code shutdown()} closes the underlying Redisson client;</li>
 *   <li>{@code client} field is final (P0 fix).</li>
 * </ul>
 */
class RedisSentinelLockTest extends IntegrationTestBase {

    private static final String[] SENTINEL_NODES = new String[]{
            "redis://127.0.0.1:26379"
    };
    private static final String MASTER_NAME = "mymaster";

    private FlexibleLockProperties.RedisSentinelProperties sentinelProperties;
    private FlexibleLockProperties flexibleLockProperties;

    @BeforeEach
    void setUp() {
        sentinelProperties = new FlexibleLockProperties.RedisSentinelProperties();
        sentinelProperties.setNodes(SENTINEL_NODES);
        sentinelProperties.setMaster(MASTER_NAME);
        sentinelProperties.setDatabase(0);

        flexibleLockProperties = new FlexibleLockProperties();
        flexibleLockProperties.setLockType(LockType.REDIS_SENTINEL);
        flexibleLockProperties.setRedisSentinel(sentinelProperties);
    }

    @Test
    void constructorBuildsLiveSentinelClient() {
        RedisSentinelLock lock = new RedisSentinelLock(flexibleLockProperties);
        RedissonClient client = readClient(lock);
        try {
            assertNotNull(client);
            assertFalse(client.isShutdown(),
                    "client must NOT be shutdown right after construction");
        } finally {
            lock.shutdown();
        }
    }

    @Test
    void shutdownClosesRedissonClient() {
        RedisSentinelLock lock = new RedisSentinelLock(flexibleLockProperties);
        RedissonClient client = readClient(lock);
        assertFalse(client.isShutdown());

        lock.shutdown();

        assertTrue(client.isShutdown(),
                "RedissonClient must be shut down after RedisSentinelLock.shutdown()");
    }

    @Test
    void tryLockUnlockRoundTrip() {
        String lockKey = "sentinel-rt-" + System.nanoTime();
        RedisSentinelLock lock = new RedisSentinelLock(flexibleLockProperties);
        try {
            assertTrue(lock.tryLock(lockKey));
            lock.unLock(lockKey);

            // After release the same key must be acquirable again.
            assertTrue(lock.tryLock(lockKey),
                    "key must be acquirable again after release");
            lock.unLock(lockKey);
        } finally {
            lock.shutdown();
        }
    }

    @Test
    void timedTryLockGivesUpWhileHolderStillHolds() {
        String lockKey = "sentinel-wait-" + System.nanoTime();
        RedisSentinelLock holder = new RedisSentinelLock(flexibleLockProperties);
        RedisSentinelLock waiter = new RedisSentinelLock(flexibleLockProperties);
        try {
            assertTrue(holder.tryLock(lockKey));

            long t0 = System.nanoTime();
            Boolean acquired = waiter.tryLock(lockKey, 300L);
            long elapsedMs = (System.nanoTime() - t0) / 1_000_000;

            assertFalse(acquired, "must not acquire while holder still holds");
            assertTrue(elapsedMs >= 250,
                    "should have waited at least the budget; actual=" + elapsedMs + "ms");
        } finally {
            holder.unLock(lockKey);
            holder.shutdown();
            waiter.shutdown();
        }
    }

    @Test
    void mutualExclusionHoldsAcrossSentinelClients() throws Exception {
        String lockKey = "sentinel-mutex-" + System.nanoTime();
        RedisSentinelLock holder = new RedisSentinelLock(flexibleLockProperties);
        RedisSentinelLock contender = new RedisSentinelLock(flexibleLockProperties);
        try {
            CountDownLatch inside = new CountDownLatch(1);
            CountDownLatch mayExit = new CountDownLatch(1);
            AtomicInteger insideCount = new AtomicInteger();
            AtomicInteger maxConcurrent = new AtomicInteger();

            Thread t1 = new Thread(() -> {
                assertTrue(holder.tryLock(lockKey, 5000L));
                insideCount.incrementAndGet();
                maxConcurrent.updateAndGet(p -> Math.max(p, insideCount.get()));
                inside.countDown();
                try { mayExit.await(); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                insideCount.decrementAndGet();
                holder.unLock(lockKey);
            }, "t1");

            Thread t2 = new Thread(() -> {
                try {
                    inside.await();
                    Thread.sleep(150);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                assertTrue(contender.tryLock(lockKey, 5000L),
                        "contender must acquire after holder releases");
                insideCount.incrementAndGet();
                maxConcurrent.updateAndGet(p -> Math.max(p, insideCount.get()));
                insideCount.decrementAndGet();
                contender.unLock(lockKey);
            }, "t2");

            t1.start();
            t2.start();

            assertTrue(inside.await(5, TimeUnit.SECONDS));
            Thread.sleep(300);
            assertEquals(1, maxConcurrent.get(),
                    "Only one thread may be in CS at a time; actual=" + maxConcurrent.get());
            mayExit.countDown();
            t1.join(5000);
            t2.join(5000);
            assertEquals(1, maxConcurrent.get());
        } finally {
            holder.shutdown();
            contender.shutdown();
        }
    }

    @Test
    void exposesRetryCountAndWaitTimeFromProperties() {
        flexibleLockProperties.setRetryCount(9);
        flexibleLockProperties.setWaitTime(7777L);
        RedisSentinelLock lock = new RedisSentinelLock(flexibleLockProperties);
        try {
            assertEquals(9, lock.getRetryCount());
            assertEquals(7777L, lock.getWaitTime());
        } finally {
            lock.shutdown();
        }
    }

    @Test
    void clientFieldIsFinal() throws Exception {
        Field f = RedisSentinelLock.class.getDeclaredField("client");
        assertTrue(Modifier.isFinal(f.getModifiers()),
                "RedisSentinelLock.client must be final");
    }

    @Override
    protected boolean requiresRedisSentinel() {
        return true;
    }

    private static RedissonClient readClient(RedisSentinelLock lock) {
        try {
            Field f = RedisSentinelLock.class.getDeclaredField("client");
            f.setAccessible(true);
            return (RedissonClient) f.get(lock);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}