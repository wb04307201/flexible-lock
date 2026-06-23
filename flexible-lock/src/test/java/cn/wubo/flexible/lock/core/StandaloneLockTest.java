package cn.wubo.flexible.lock.core;

import cn.wubo.flexible.lock.exception.LockRuntimeException;
import cn.wubo.flexible.lock.propertes.FlexibleLockProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link StandaloneLock}.
 *
 * The lock map must persist ReentrantLock instances for the lifetime of the
 * StandaloneLock so that subsequent acquirers share the same monitor and
 * mutual exclusion is preserved across release/acquire cycles.
 */
class StandaloneLockTest {

    private StandaloneLock lock;
    private Map<String, ReentrantLock> internalMap;

    @BeforeEach
    void setUp() throws Exception {
        FlexibleLockProperties properties = new FlexibleLockProperties();
        lock = new StandaloneLock(properties);
        Field field = StandaloneLock.class.getDeclaredField("lockMap");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, ReentrantLock> map = (Map<String, ReentrantLock>) field.get(lock);
        this.internalMap = map;
    }

    @Test
    void unLockShouldKeepReentrantLockInstanceInMap() {
        // Given: a lock is acquired
        assertTrue(lock.tryLock("key"));
        ReentrantLock instance = internalMap.get("key");
        assertNotNull(instance, "ReentrantLock must be registered when acquired");

        // When: the lock is released
        lock.unLock("key");

        // Then: the same ReentrantLock instance must remain in the map
        // so that future acquirers contend on the same monitor and mutual
        // exclusion is preserved.
        assertSame(instance, internalMap.get("key"),
                "unLock must not remove the ReentrantLock from the map");
    }

    @Test
    void unLockWithoutLockThrowsForSymmetryWithRedisAndZooKeeper() {
        // StandaloneLock must be symmetric with Redis/ZooKeeper backends:
        // calling unLock on a key that was never acquired must throw, so
        // bugs like a SpEL key mismatch between lock and unlock are surfaced
        // rather than silently dropped.
        assertThrows(LockRuntimeException.class, () -> lock.unLock("never-acquired"),
                "unLock without tryLock must throw to surface SpEL key bugs");
    }

    @Test
    void onlyOneThreadInCriticalSectionAtATime() throws Exception {
        // Three threads contend on the same key. With the bug (unLock clears
        // the map), t3 acquires a brand-new ReentrantLock after t2 is already
        // inside the critical section, breaking mutual exclusion.
        final CountDownLatch t1Released = new CountDownLatch(1);
        final CountDownLatch t2Inside = new CountDownLatch(1);
        final CountDownLatch t2MayExit = new CountDownLatch(1);
        final AtomicInteger insideCount = new AtomicInteger();
        final AtomicInteger maxConcurrent = new AtomicInteger();

        Runnable enterCriticalSection = () -> {
            int current = insideCount.incrementAndGet();
            maxConcurrent.updateAndGet(prev -> Math.max(prev, current));
        };

        // tryLock(key) without wait time returns immediately if the lock is
        // held. Use the timed variant so threads reliably wait for their turn.
        Thread t1 = new Thread(() -> {
            assertTrue(lock.tryLock("key", 5000L));
            enterCriticalSection.run();
            insideCount.decrementAndGet();
            lock.unLock("key");
            t1Released.countDown();
        }, "t1");

        Thread t2 = new Thread(() -> {
            assertTrue(lock.tryLock("key", 5000L));
            enterCriticalSection.run();
            t2Inside.countDown();
            try {
                t2MayExit.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            insideCount.decrementAndGet();
            lock.unLock("key");
        }, "t2");

        Thread t3 = new Thread(() -> {
            try {
                t2Inside.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            // Should block while t2 holds the lock — with the map-removal bug,
            // the map is empty after t1's release, so computeIfAbsent creates
            // a new ReentrantLock and tryLock succeeds immediately, allowing
            // t3 to enter the critical section concurrently with t2.
            assertTrue(lock.tryLock("key", 5000L));
            enterCriticalSection.run();
            insideCount.decrementAndGet();
            lock.unLock("key");
        }, "t3");

        t1.start();
        t2.start();
        t3.start();

        // Wait for t2 to enter the critical section. Whoever acquires first
        // (t1 or t2), the test converges: if t1 went first, t1 finishes,
        // t2 takes over and enters CS; if t2 went first, t2 enters CS
        // immediately while t1 blocks behind it.
        assertTrue(t2Inside.await(5, TimeUnit.SECONDS), "t2 should be inside");

        // Give t3 a chance to attempt and (correctly) block on t2's lock.
        // (If t1 acquired first, by now t1 has already finished and t2 is
        // in CS holding the shared ReentrantLock.)
        Thread.sleep(300);
        assertEquals(1, maxConcurrent.get(),
                "Only one thread may be in the critical section at a time; "
                        + "actual maxConcurrent=" + maxConcurrent.get());

        // Let t2 exit. Any acquirer currently blocked (t1 or t3) should now
        // take over, one at a time.
        t2MayExit.countDown();

        assertTrue(t1Released.await(5, TimeUnit.SECONDS),
                "t1 should finish (either it already did, or it just acquired after t2 exited)");
        t1.join(5000);
        t2.join(5000);
        t3.join(5000);

        assertEquals(1, maxConcurrent.get(),
                "Even after t2 exits, only one thread may be in the critical section at a time");
    }

    // ─────────────────────────────────────────────────────────────────────
    // Reusability after release
    // ─────────────────────────────────────────────────────────────────────

    @Test
    void sameKeyCanBeAcquiredAgainAfterRelease() {
        // Acquire → release → acquire again, on the same thread. This catches
        // a class of bugs where the map-clearing old behavior prevented reuse.
        assertTrue(lock.tryLock("k"));
        lock.unLock("k");
        assertTrue(lock.tryLock("k"),
                "after release, the same key must be acquirable again");
        lock.unLock("k");
    }

    @Test
    void releaseAndAcquireCyclesDoNotGrowTheLockMapUnboundedly() throws Exception {
        // StandaloneLock must NOT remove the ReentrantLock from the map on
        // release (otherwise mutual exclusion breaks), but it also must not
        // leak a new entry per acquire cycle. After 100 acquire/release
        // cycles on the same key, the map should have exactly 1 entry.
        for (int i = 0; i < 100; i++) {
            assertTrue(lock.tryLock("k"));
            lock.unLock("k");
        }
        assertEquals(1, internalMap.size(),
                "100 cycles on the same key must yield exactly 1 map entry; actual=" + internalMap.size());
    }

    // ─────────────────────────────────────────────────────────────────────
    // Reentrancy
    // ─────────────────────────────────────────────────────────────────────

    @Test
    void reentrantLockAllowsSameThreadToAcquireTwice() {
        // ReentrantLock semantics: the same thread can acquire the lock
        // multiple times. The count must increment, and two releases are
        // needed to fully release.
        assertTrue(lock.tryLock("k"));
        assertTrue(lock.tryLock("k"),
                "ReentrantLock must allow the same thread to re-acquire");
        ReentrantLock instance = internalMap.get("k");
        assertEquals(2, instance.getHoldCount(),
                "hold count must reflect nested acquires on the same thread");

        lock.unLock("k");
        assertEquals(1, instance.getHoldCount());
        lock.unLock("k");
        assertEquals(0, instance.getHoldCount());
        assertFalse(instance.isLocked());
    }

    // ─────────────────────────────────────────────────────────────────────
    // Multiple keys independent
    // ─────────────────────────────────────────────────────────────────────

    @Test
    void differentKeysHaveIndependentLocks() {
        // Acquiring key A must NOT block acquiring key B — they're
        // independent ReentrantLock instances on independent monitor.
        assertTrue(lock.tryLock("A"));
        assertTrue(lock.tryLock("B"),
                "different keys must not contend with each other");

        // Both should be present in the map
        assertNotNull(internalMap.get("A"));
        assertNotNull(internalMap.get("B"));

        lock.unLock("A");
        lock.unLock("B");
    }

    @Test
    void manyKeysCanBeAcquiredSimultaneouslyOnSameThread() {
        // StandaloneLock doesn't enforce "one lock at a time per JVM" — it
        // tracks per-key. Verify a single thread can hold N distinct locks.
        int n = 50;
        for (int i = 0; i < n; i++) {
            assertTrue(lock.tryLock("key-" + i));
        }
        assertEquals(n, internalMap.size(),
                "each unique key must produce its own ReentrantLock");
        for (int i = 0; i < n; i++) {
            lock.unLock("key-" + i);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Property forwarding (delegated to AbstractLock; pinned here too)
    // ─────────────────────────────────────────────────────────────────────

    @Test
    void exposesRetryCountAndWaitTimeFromProperties() {
        FlexibleLockProperties properties = new FlexibleLockProperties();
        properties.setRetryCount(13);
        properties.setWaitTime(1234L);
        StandaloneLock local = new StandaloneLock(properties);
        assertEquals(13, local.getRetryCount());
        assertEquals(1234L, local.getWaitTime());
    }

    // ─────────────────────────────────────────────────────────────────────
    // Exception isolation
    // ─────────────────────────────────────────────────────────────────────

    @Test
    void exceptionFromOneKeyDoesNotPoisonAnother() {
        // unLock on a never-acquired key throws, but the map state for
        // other keys must be unaffected. Verifies that the failure path
        // is properly bounded.
        assertTrue(lock.tryLock("ok"));
        assertThrows(LockRuntimeException.class, () -> lock.unLock("never-acquired"));
        // The OK key should still be releasable normally.
        lock.unLock("ok");
        assertFalse(internalMap.get("ok").isLocked());
    }
}
