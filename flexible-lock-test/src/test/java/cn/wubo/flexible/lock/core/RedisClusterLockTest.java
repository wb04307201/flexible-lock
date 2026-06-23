package cn.wubo.flexible.lock.core;

import cn.wubo.entity.sql.IntegrationTestBase;
import cn.wubo.flexible.lock.propertes.FlexibleLockProperties;
import cn.wubo.flexible.lock.propertes.LockType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end test for {@link RedisClusterLock} backed by a real 3-node
 * Redis cluster running in Docker.
 *
 * <p>The {@link IntegrationTestBase} gates these tests on the cluster nodes
 * being reachable on 127.0.0.1:7000/7001/7002 (plus the cluster bus ports).
 * In environments without a cluster, the tests are skipped — never failing.
 *
 * <p>Setup (one-time, per developer machine):
 * <pre>{@code
 * docker network create --subnet=172.20.0.0/16 flexible-lock-net
 *
 * docker run -d --name flex-c1 --net flexible-lock-net --ip 172.20.0.10 \
 *     -p 7000:7000 -p 17000:17000 redis:6-alpine \
 *     redis-server --cluster-enabled yes --port 7000 \
 *     --cluster-config-file nodes.conf --cluster-node-timeout 5000 \
 *     --cluster-announce-ip 172.20.0.10 --cluster-announce-port 7000 \
 *     --cluster-announce-bus-port 17000 --appendonly no --save ""
 *
 * # (start c2 and c3 with IPs 172.20.0.11 / .12, ports 7001/7002, bus 17001/17002)
 *
 * docker exec flex-c1 sh -c \
 *     "echo 'yes' | redis-cli --cluster create 172.20.0.10:7000 172.20.0.11:7001 172.20.0.12:7002 --cluster-replicas 0"
 * }</pre>
 *
 * <p>After setup, this test class verifies:
 * <ul>
 *   <li>the constructor builds a live {@link RedissonClient} (not shutdown);</li>
 *   <li>{@code tryLock}/{@code unLock} round-trip works on the cluster;</li>
 *   <li>two independent {@code RedisClusterLock} instances on the same
 *       key produce server-side mutual exclusion across the cluster;</li>
 *   <li>{@code shutdown()} closes the underlying Redisson client.</li>
 * </ul>
 */
class RedisClusterLockTest extends IntegrationTestBase {

    /** Cluster bootstrap nodes — Redisson discovers the rest automatically. */
    private static final String[] CLUSTER_NODES = new String[]{
            "redis://127.0.0.1:7000",
            "redis://127.0.0.1:7001",
            "redis://127.0.0.1:7002"
    };

    private FlexibleLockProperties.RedisClusterProperties clusterProperties;
    private FlexibleLockProperties flexibleLockProperties;

    @BeforeEach
    void setUp() {
        clusterProperties = new FlexibleLockProperties.RedisClusterProperties();
        clusterProperties.setNodes(CLUSTER_NODES);

        flexibleLockProperties = new FlexibleLockProperties();
        flexibleLockProperties.setLockType(LockType.REDIS_CLUSTER);
        flexibleLockProperties.setRedisCluster(clusterProperties);
    }

    @Test
    void constructorBuildsLiveClusterClient() {
        // When
        RedisClusterLock lock = new RedisClusterLock(flexibleLockProperties);

        // Then: the underlying RedissonClient is alive
        RedissonClient client = readClient(lock);
        try {
            assertNotNull(client, "RedissonClient must be created");
            assertFalse(client.isShutdown(),
                    "client must NOT be shutdown right after construction");
        } finally {
            lock.shutdown();
        }
    }

    @Test
    void shutdownClosesRedissonClient() {
        // Given: a live cluster-backed lock
        RedisClusterLock lock = new RedisClusterLock(flexibleLockProperties);
        RedissonClient client = readClient(lock);
        assertFalse(client.isShutdown());

        // When
        lock.shutdown();

        // Then: client is shut down
        assertTrue(client.isShutdown(),
                "RedissonClient must be shut down after RedisClusterLock.shutdown()");
    }

    @Test
    void tryLockUnlockRoundTrip() {
        String lockKey = "cluster-rt-" + System.nanoTime();
        RedisClusterLock lock = new RedisClusterLock(flexibleLockProperties);
        try {
            assertTrue(lock.tryLock(lockKey),
                    "fresh tryLock on an unheld key must succeed");
            lock.unLock(lockKey);

            // After release, the same key can be re-acquired — this catches a
            // class of bugs where unLock would clear server-side state.
            assertTrue(lock.tryLock(lockKey),
                    "key must be acquirable again after release");
            lock.unLock(lockKey);
        } finally {
            lock.shutdown();
        }
    }

    @Test
    void timedTryLockGivesUpWhileHolderStillHolds() {
        String lockKey = "cluster-wait-" + System.nanoTime();
        RedisClusterLock holder = new RedisClusterLock(flexibleLockProperties);
        RedisClusterLock waiter = new RedisClusterLock(flexibleLockProperties);
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
    void mutualExclusionHoldsAcrossClusterClients() throws Exception {
        // Two independent RedisClusterLock instances pointing at the same
        // cluster must observe server-side mutual exclusion on a shared key.
        String lockKey = "cluster-mutex-" + System.nanoTime();
        RedisClusterLock holder = new RedisClusterLock(flexibleLockProperties);
        RedisClusterLock contender = new RedisClusterLock(flexibleLockProperties);
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
        flexibleLockProperties.setRetryCount(11);
        flexibleLockProperties.setWaitTime(9999L);
        RedisClusterLock lock = new RedisClusterLock(flexibleLockProperties);
        try {
            assertEquals(11, lock.getRetryCount());
            assertEquals(9999L, lock.getWaitTime());
        } finally {
            lock.shutdown();
        }
    }

    @Test
    void clientFieldIsFinal() throws Exception {
        // The P0 fix made `client` final so the connection can't be reassigned.
        // Reflection on the declared modifiers catches accidental regression.
        Field f = RedisClusterLock.class.getDeclaredField("client");
        assertTrue(java.lang.reflect.Modifier.isFinal(f.getModifiers()),
                "RedisClusterLock.client must be final");
    }

    @Override
    protected boolean requiresRedisCluster() {
        return true;
    }

    private static RedissonClient readClient(RedisClusterLock lock) {
        try {
            Field f = RedisClusterLock.class.getDeclaredField("client");
            f.setAccessible(true);
            return (RedissonClient) f.get(lock);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}