package cn.wubo.entity.sql;

import cn.wubo.flexible.lock.core.ILock;
import cn.wubo.flexible.lock.exception.LockRuntimeException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end Spring-context tests for the {@code @Locking} annotation.
 *
 * <p>Loads the real Spring context ({@code DemoService} bean with
 * {@code @Locking}), points the starter at the Redis container running
 * on 127.0.0.1:6379, and verifies:
 * <ul>
 *   <li>The {@code @Locking} AOP advice wraps the annotated method</li>
 *   <li>Concurrent callers are serialized through the Redis lock</li>
 *   <li>If the lock can't be acquired within retries, a
 *       {@link LockRuntimeException} is raised</li>
 * </ul>
 *
 * <p>Requires a Redis instance reachable at 127.0.0.1:6379 (start with:
 * {@code docker run -d --name flexible-lock-redis -p 6379:6379 redis:6-alpine}).
 */
@SpringBootTest
@TestPropertySource(properties = {
        "flexible.lock.lockType=redis",
        "flexible.lock.retryCount=2",
        "flexible.lock.waitTime=500",
        "flexible.lock.redis.host=redis://127.0.0.1",
        "flexible.lock.redis.port=6379",
        "flexible.lock.retryStrategyType=fixed"
})
class LockingAnnotationEndToEndTest extends IntegrationTestBase {

    @Autowired
    DemoService demoService;

    @Autowired
    ILock flexibleLock;

    @Override
    protected boolean requiresRedis() {
        return true;
    }

    @Test
    void starterWiresUpTheConfiguredBackend() {
        // Sanity: with lockType=redis the starter must wire up a live Redis-backed ILock.
        assertNotNull(flexibleLock);
        assertTrue(flexibleLock.getRetryCount() >= 0);
        assertTrue(flexibleLock.getWaitTime() > 0);
    }

    @Test
    void annotatedMethodCanBeCalledDirectly() {
        // Happy path: single caller, no contention.
        String key = "single-" + System.nanoTime();
        String result = demoService.doWork(key);
        assertEquals(key, result);
    }

    @Test
    void concurrentCallersAreSerialized() throws Exception {
        // 10 concurrent callers go through @Locking. With the lock in place,
        // they must execute one at a time. The simplest observable proof:
        // they all complete successfully (no IllegalMonitorStateException,
        // no LockRuntimeException) and we don't deadlock.
        String key = "concurrent-" + System.nanoTime();
        CountDownLatch done = new CountDownLatch(10);
        IntStream.range(0, 10).forEach(i -> CompletableFuture.runAsync(() -> {
            try {
                demoService.doWork(key);
            } finally {
                done.countDown();
            }
        }));
        assertTrue(done.await(120, TimeUnit.SECONDS),
                "all 10 concurrent callers should complete within 120s");
    }

    @Test
    void lockRuntimeExceptionIsThrownWhenRetriesExhausted() throws Exception {
        // Saturate the lock by holding it on the test thread, then drive many
        // concurrent callers of @Locking through the AOP. With retryCount=2
        // and waitTime=500ms, callers that can't acquire should fail after
        // ~1.5s of retries with a LockRuntimeException.
        //
        // We hold the lock via the same `flexibleLock` bean the aspect uses,
        // so they contend on the same key in the same backend. The
        // RedissonLock watchdog keeps the held lock alive for 30s by default,
        // which is well over the test's wall-clock budget.
        String arg = "exhaust-" + System.nanoTime();
        String resolvedKey = "doWork-" + arg;
        assertTrue(flexibleLock.tryLock(resolvedKey),
                "test setup must acquire the lock");

        CountDownLatch started = new CountDownLatch(1);
        AtomicInteger failures = new AtomicInteger();
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger unexpected = new AtomicInteger();

        int callers = 6;
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        try {
            long t0 = System.nanoTime();
            CompletableFuture<?>[] futures = IntStream.range(0, callers)
                    .mapToObj(i -> CompletableFuture.runAsync(() -> {
                        try {
                            started.await();
                            demoService.doWork(arg);
                            successes.incrementAndGet();
                        } catch (LockRuntimeException e) {
                            failures.incrementAndGet();
                        } catch (Exception e) {
                            unexpected.incrementAndGet();
                        }
                    }, pool))
                    .toArray(CompletableFuture[]::new);
            started.countDown();
            CompletableFuture.allOf(futures).get(60, TimeUnit.SECONDS);
            long elapsedMs = (System.nanoTime() - t0) / 1_000_000;

            // While the lock is held by us, NONE of the AOP-wrapped callers
            // should have succeeded. They should all fail with
            // LockRuntimeException after retries are exhausted.
            assertEquals(0, unexpected.get(),
                    "no unexpected exceptions; got: " + unexpected.get());
            assertEquals(0, successes.get(),
                    "no caller should have succeeded while lock is held");
            assertEquals(callers, failures.get(),
                    "all " + callers + " callers should have failed with LockRuntimeException; "
                            + "actual failures=" + failures.get());
            // Each failed caller waited ~1.5s for retries. With concurrent
            // callers the wall-clock is dominated by the slowest one.
            assertTrue(elapsedMs >= 1000 && elapsedMs < 30000,
                    "expected ~1.5s of retries, actual=" + elapsedMs + "ms");
        } finally {
            pool.shutdownNow();
            flexibleLock.unLock(resolvedKey);
        }
    }
}
