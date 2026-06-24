package cn.wubo.flexible.lock.core;

import cn.wubo.flexible.lock.propertes.FlexibleLockProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link NoneLock}.
 *
 * The none backend disables locking while preserving the @Locking annotation
 * call sites — useful for local dev, integration tests, and staging. Its
 * tryLock must always succeed and unLock must be a no-op (no resources
 * to release, so shutdown is unnecessary too).
 */
class NoneLockTest {

    private NoneLock lock;
    private FlexibleLockProperties properties;

    @BeforeEach
    void setUp() {
        properties = new FlexibleLockProperties();
        lock = new NoneLock(properties);
    }

    @Test
    void tryLockWithoutTimeAlwaysReturnsTrue() {
        assertEquals(Boolean.TRUE, lock.tryLock("any-key"));
        assertEquals(Boolean.TRUE, lock.tryLock(""));
        assertEquals(Boolean.TRUE, lock.tryLock(null));
    }

    @Test
    void tryLockWithTimeAlwaysReturnsTrue() {
        assertEquals(Boolean.TRUE, lock.tryLock("any-key", 0L));
        assertEquals(Boolean.TRUE, lock.tryLock("any-key", -1L));
        assertEquals(Boolean.TRUE, lock.tryLock("any-key", Long.MAX_VALUE));
    }

    @Test
    void unLockIsNoOp() {
        assertDoesNotThrow(() -> lock.unLock("any-key"));
        assertDoesNotThrow(() -> lock.unLock(""));
        assertDoesNotThrow(() -> lock.unLock(null));
    }

    @Test
    void shutdownIsNoOp() {
        // ILock.shutdown() default impl is a no-op; NoneLock inherits it.
        // Verify it doesn't throw and can be called multiple times.
        assertDoesNotThrow(() -> {
            lock.shutdown();
            lock.shutdown();
        });
    }

    @Test
    void exposesRetryCountAndWaitTimeFromProperties() {
        // AbstractLock forwards getRetryCount/getWaitTime to properties.
        properties.setRetryCount(7);
        properties.setWaitTime(5000L);
        NoneLock another = new NoneLock(properties);
        assertEquals(7, another.getRetryCount());
        assertEquals(5000L, another.getWaitTime());
    }

    // ─────────────────────────────────────────────────────────────────────
    // Idempotency & reentrancy
    // ─────────────────────────────────────────────────────────────────────

    @Test
    void unLockCanBeCalledWithoutPriorTryLock() {
        // NoneLock's contract: no state. unLock must work whether or not
        // tryLock was called first. This matches the behavior callers
        // expect from a no-op backend during exception unwinding.
        assertDoesNotThrow(() -> lock.unLock("never-acquired"));
    }

    @Test
    void tryLockSameKeyMultipleTimesAlwaysReturnsTrue() {
        // Re-entrancy on the same key: must always succeed, never throw.
        for (int i = 0; i < 10; i++) {
            assertTrue(lock.tryLock("same-key"));
        }
    }

    @Test
    void tryLockAndUnLockManyDistinctKeysAllReturnTrueAndDoNotThrow() {
        // Verify the no-op backend scales to many distinct keys without
        // accumulating state or throwing.
        for (int i = 0; i < 100; i++) {
            String key = "key-" + i;
            assertTrue(lock.tryLock(key, 100L));
            assertDoesNotThrow(() -> lock.unLock(key));
        }
    }

    @Test
    void exceptionsInCallerDoNotAffectLockState() {
        // NoneLock must never throw from its own methods; exceptions only
        // come from the caller's body. Verify unLock doesn't interact
        // with whatever the body does.
        assertTrue(lock.tryLock("k"));
        try {
            throw new RuntimeException("caller fault");
        } catch (RuntimeException ignored) {
            // ignored
        }
        // Even after the caller saw an exception, unLock is a clean no-op.
        assertDoesNotThrow(() -> lock.unLock("k"));
    }

    @Test
    void unLockDoesNotValidateKeyAgainstPriorTryLock() {
        // On a stateful backend, unLock("foo") after tryLock("bar") might
        // be a logic bug. NoneLock must not police this — it's the
        // caller's job to track SpEL symmetry.
        assertTrue(lock.tryLock("A"));
        assertDoesNotThrow(() -> lock.unLock("B"));
        assertDoesNotThrow(() -> lock.unLock("A"));
    }

    // ─────────────────────────────────────────────────────────────────────
    // Properties reflection on multiple instances
    // ─────────────────────────────────────────────────────────────────────

    @Test
    void zeroValuesArePassedThrough() {
        // retryCount=0 and waitTime=0 must be preserved verbatim — the same
        // way the stateful backends handle them.
        properties.setRetryCount(0);
        properties.setWaitTime(0L);
        NoneLock another = new NoneLock(properties);
        assertEquals(0, another.getRetryCount());
        assertEquals(0L, another.getWaitTime());
    }
}
