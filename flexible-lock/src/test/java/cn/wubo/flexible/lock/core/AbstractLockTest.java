package cn.wubo.flexible.lock.core;

import cn.wubo.flexible.lock.propertes.FlexibleLockProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link AbstractLock}.
 *
 * The base class is the single source of truth for {@code getRetryCount} and
 * {@code getWaitTime}: every concrete {@code ILock} implementation reads those
 * values from {@link FlexibleLockProperties} through this class. A regression
 * here silently breaks every backend's retry policy, so the contract is locked
 * down here independently of the integration tests.
 *
 * <p>Since {@link AbstractLock} is abstract, the tests use {@link StandaloneLock}
 * as a concrete carrier — its only behavior over the base is to delegate the
 * two getters, which is exactly the surface under test.
 */
class AbstractLockTest {

    @Test
    void getRetryCountReturnsPropertyValue() {
        FlexibleLockProperties properties = new FlexibleLockProperties();
        properties.setRetryCount(7);
        StandaloneLock lock = new StandaloneLock(properties);
        assertEquals(7, lock.getRetryCount());
    }

    @Test
    void getWaitTimeReturnsPropertyValue() {
        FlexibleLockProperties properties = new FlexibleLockProperties();
        properties.setWaitTime(5000L);
        StandaloneLock lock = new StandaloneLock(properties);
        assertEquals(5000L, lock.getWaitTime());
    }

    @Test
    void defaultRetryCountIsThree() {
        // The starter ships with a sensible default; users only override it
        // when they actually configure `flexible.lock.retryCount`.
        StandaloneLock lock = new StandaloneLock(new FlexibleLockProperties());
        assertEquals(3, lock.getRetryCount());
    }

    @Test
    void defaultWaitTimeIsThreeSeconds() {
        // 3000ms is a reasonable starting wait time for human-scale operations
        // (DB writes, external HTTP calls) without being a DOS vector.
        StandaloneLock lock = new StandaloneLock(new FlexibleLockProperties());
        assertEquals(3000L, lock.getWaitTime());
    }

    @Test
    void zeroValuesArePassedThrough() {
        // retryCount=0 means "single attempt, no retries" — must NOT be
        // silently rewritten to the default by AbstractLock.
        FlexibleLockProperties properties = new FlexibleLockProperties();
        properties.setRetryCount(0);
        properties.setWaitTime(0L);
        StandaloneLock lock = new StandaloneLock(properties);
        assertEquals(0, lock.getRetryCount());
        assertEquals(0L, lock.getWaitTime());
    }
}