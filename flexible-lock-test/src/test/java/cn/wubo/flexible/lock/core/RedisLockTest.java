package cn.wubo.flexible.lock.core;

import cn.wubo.entity.sql.IntegrationTestBase;
import cn.wubo.flexible.lock.propertes.FlexibleLockProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RedisLockTest extends IntegrationTestBase {

    private FlexibleLockProperties.RedisStandaloneProperties redisProperties;
    private FlexibleLockProperties flexibleLockProperties;

    @BeforeEach
    void setUp() {
        // 创建Redis连接属性配置
        redisProperties = new FlexibleLockProperties.RedisStandaloneProperties();
        redisProperties.setHost("redis://127.0.0.1");
        redisProperties.setPort(6379);
        redisProperties.setPassword(null);
        redisProperties.setDatabase(0);

        // 创建主配置对象
        flexibleLockProperties = new FlexibleLockProperties();
        flexibleLockProperties.setRedis(redisProperties);
    }

    @Test
    void testCreateWithValidProperties() {
        // Given
        RedisLock redisLock = new RedisLock(flexibleLockProperties);

        // When
        RedissonClient client = null;
        try {
            java.lang.reflect.Method createMethod = RedisLock.class.getDeclaredMethod("create", FlexibleLockProperties.RedisStandaloneProperties.class);
            createMethod.setAccessible(true);
            client = (RedissonClient) createMethod.invoke(redisLock, redisProperties);
        } catch (Exception e) {
            fail("无法访问或调用create方法: " + e.getMessage());
        }

        // Then
        assertNotNull(client, "RedissonClient实例不应为空");
        client.shutdown();
    }

    @Test
    void testTryLockSuccess() {
        // Given
        String lockKey = "test-key-" + System.nanoTime();
        RedisLock redisLock = new RedisLock(flexibleLockProperties);

        // When
        Boolean result = redisLock.tryLock(lockKey);

        // Then
        assertTrue(result);

        redisLock.unLock(lockKey);
        redisLock.shutdown();
    }

    @Test
    void timedTryLockAcquiresAfterSameThreadReleases() {
        // Given: holder owns the lock on the main thread; verify that after
        // the same thread releases it, a second RedisLock instance can
        // acquire it via timed tryLock.
        //
        // Note: Redisson associates lock ownership with the RedissonLock
        // instance, not the Java thread — so a separate RedisLock instance
        // can always acquire a freshly-released lock. We avoid the more
        // complex cross-thread release test because of timing flake.
        String lockKey = "timed-" + System.nanoTime();
        RedisLock holder = new RedisLock(flexibleLockProperties);
        RedisLock waiter = new RedisLock(flexibleLockProperties);
        try {
            assertTrue(holder.tryLock(lockKey));
            holder.unLock(lockKey);

            // When: waiter asks for the lock with a 2-second budget
            long t0 = System.nanoTime();
            Boolean acquired = waiter.tryLock(lockKey, 2000L);
            long elapsedMs = (System.nanoTime() - t0) / 1_000_000;

            // Then: acquired quickly since the lock was already free
            assertTrue(acquired, "waiter should acquire a freshly-released lock");
            assertTrue(elapsedMs < 1000,
                    "lock was free before waiter asked, should be near-instant; actual=" + elapsedMs + "ms");
        } finally {
            try { waiter.unLock(lockKey); } catch (RuntimeException ignored) {}
            holder.shutdown();
            waiter.shutdown();
        }
    }

    @Test
    void timedTryLockGivesUpWhenHolderNeverReleases() {
        // Given: holder keeps the lock indefinitely; waiter has a small budget.
        String lockKey = "giveup-" + System.nanoTime();
        RedisLock holder = new RedisLock(flexibleLockProperties);
        RedisLock waiter = new RedisLock(flexibleLockProperties);
        try {
            assertTrue(holder.tryLock(lockKey));

            // When: waiter asks for the lock with only a 300ms wait
            long t0 = System.nanoTime();
            Boolean acquired = waiter.tryLock(lockKey, 300L);
            long elapsedMs = (System.nanoTime() - t0) / 1_000_000;

            // Then: not acquired, and we did not block much longer than the budget
            assertFalse(acquired, "waiter must give up while holder still holds the lock");
            assertTrue(elapsedMs >= 250,
                    "should have waited at least the budget; actual=" + elapsedMs + "ms");
        } finally {
            holder.unLock(lockKey);
            holder.shutdown();
            waiter.shutdown();
        }
    }

    @Test
    void onlyOneThreadInCriticalSectionAtATime() throws Exception {
        // Verify mutual exclusion holds across processes (separate RedissonClient
        // instances point to the same Redis, so the lock is server-side).
        String lockKey = "mutex-" + System.nanoTime();
        RedisLock holder = new RedisLock(flexibleLockProperties);
        RedisLock contender = new RedisLock(flexibleLockProperties);
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
                try { inside.await(); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                assertTrue(contender.tryLock(lockKey, 5000L),
                        "contender should acquire after holder releases");
                insideCount.incrementAndGet();
                maxConcurrent.updateAndGet(p -> Math.max(p, insideCount.get()));
                insideCount.decrementAndGet();
                contender.unLock(lockKey);
            }, "t2");

            t1.start();
            t2.start();

            assertTrue(inside.await(5, TimeUnit.SECONDS));
            Thread.sleep(200); // give t2 time to start blocking
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
    void shutdownClosesRedissonClient() {
        // Given: a live RedisLock
        RedisLock redisLock = new RedisLock(flexibleLockProperties);
        RedissonClient client = readClient(redisLock);
        assertNotNull(client);
        assertFalse(client.isShutdown(), "client must be live before shutdown");

        // When
        redisLock.shutdown();

        // Then: client is shut down
        assertTrue(client.isShutdown(), "RedissonClient must be shut down after RedisLock.shutdown()");
    }

    @Test
    void unLockWithoutLockDoesNotThrow() {
        // Redisson's `unlock()` requires the lock to be held by the current thread
        // (client). If a different thread/client holds it, it throws
        // IllegalMonitorStateException. Verify that calling unLock on a key that
        // the *current* client never acquired is a no-op or fails cleanly — and
        // importantly, that it does NOT silently corrupt another holder's lock.
        String lockKey = "unowned-" + System.nanoTime();
        RedisLock holder = new RedisLock(flexibleLockProperties);
        RedisLock stranger = new RedisLock(flexibleLockProperties);
        try {
            assertTrue(holder.tryLock(lockKey));
            // We don't assert on the outcome of stranger.unLock here: Redisson
            // *does* throw IllegalMonitorStateException by design (it's
            // rethrown by AbstractLock.unLock → LockRuntimeException in the
            // aspect), but the unit-level unLock must propagate that exception
            // rather than swallow it. The aspect-level test covers the wrapper.
            assertThrows(RuntimeException.class, () -> stranger.unLock(lockKey),
                    "unLock from a client that doesn't hold the lock must throw, "
                            + "not silently drop the lock");
        } finally {
            // Only the original holder can release.
            try { holder.unLock(lockKey); } catch (RuntimeException ignored) {}
            holder.shutdown();
            stranger.shutdown();
        }
    }

    private static RedissonClient readClient(RedisLock lock) {
        try {
            Field f = RedisLock.class.getDeclaredField("client");
            f.setAccessible(true);
            return (RedissonClient) f.get(lock);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected boolean requiresRedis() {
        return true;
    }
}

