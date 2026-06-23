package cn.wubo.flexible.lock.retry;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the three retry strategies.
 *
 * Pure functions with no shared state, so each test exercises the same
 * invariants regardless of execution order.
 */
class RetryStrategyTest {

    @Test
    void fixedStrategyAlwaysReturnsBaseWait() {
        FixedRetryStrategy strategy = new FixedRetryStrategy();
        assertEquals(1000L, strategy.calculateWaitTime(1000L, 0));
        assertEquals(1000L, strategy.calculateWaitTime(1000L, 1));
        assertEquals(1000L, strategy.calculateWaitTime(1000L, 99));
    }

    @Test
    void exponentialFirstCallIsBaseThenDoublesEachRetry() {
        ExponentialRetryStrategy strategy = new ExponentialRetryStrategy();
        // retryCount 0 → guarded fallback to baseWaitTime
        // retryCount 1 → baseWaitTime * 2^0 = baseWaitTime
        // retryCount 2 → baseWaitTime * 2^1 = 2x baseWaitTime
        // retryCount 3 → baseWaitTime * 2^2 = 4x baseWaitTime
        // retryCount 4 → baseWaitTime * 2^3 = 8x baseWaitTime
        assertEquals(1000L, strategy.calculateWaitTime(1000L, 0));
        assertEquals(1000L, strategy.calculateWaitTime(1000L, 1));
        assertEquals(2000L, strategy.calculateWaitTime(1000L, 2));
        assertEquals(4000L, strategy.calculateWaitTime(1000L, 3));
        assertEquals(8000L, strategy.calculateWaitTime(1000L, 4));
    }

    @Test
    void exponentialGuardsAgainstShiftOverflow() {
        ExponentialRetryStrategy strategy = new ExponentialRetryStrategy();
        // attempt > 63 would shift by 63+ positions and either wrap or become 0;
        // the strategy must fall back to baseWaitTime rather than corrupt the value.
        assertEquals(1000L, strategy.calculateWaitTime(1000L, 64));
        assertEquals(1000L, strategy.calculateWaitTime(1000L, 1000));
    }

    @Test
    void exponentialGuardsAgainstMultiplicationOverflow() {
        ExponentialRetryStrategy strategy = new ExponentialRetryStrategy();
        // baseWaitTime * 2^62 is at the edge of long; doubling again would overflow.
        // We must not return a negative or zero value.
        long huge = Long.MAX_VALUE / 4;
        long result = strategy.calculateWaitTime(huge, 10);
        assertTrue(result > 0, "result must remain positive; was " + result);
    }

    @Test
    void exponentialGuardsNonPositiveAttempt() {
        ExponentialRetryStrategy strategy = new ExponentialRetryStrategy();
        // attempt <= 0 must fall back to baseWaitTime (no shift on a negative count).
        assertEquals(1000L, strategy.calculateWaitTime(1000L, 0));
        assertEquals(1000L, strategy.calculateWaitTime(1000L, -1));
    }

    @Test
    void randomStrategyStaysWithinExpectedRange() {
        RandomRetryStrategy strategy = new RandomRetryStrategy();
        long base = 100L;
        for (int attempt = 1; attempt <= 20; attempt++) {
            long maxWait = base * attempt;
            // The intent is: random value in [base, base + maxWait).
            for (int i = 0; i < 50; i++) {
                long result = strategy.calculateWaitTime(base, attempt);
                assertTrue(result >= base,
                        "result " + result + " must be >= baseWaitTime " + base);
                assertTrue(result < base + maxWait,
                        "result " + result + " must be < base+maxWait " + (base + maxWait));
            }
        }
    }

    @Test
    void randomStrategyDoesNotProduceDuplicateSequence() {
        // The strategy should use a source that varies between calls; a fixed
        // seed or a single Random could conceivably repeat. Sanity-check that
        // we see at least a handful of distinct values across 100 calls.
        RandomRetryStrategy strategy = new RandomRetryStrategy();
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            seen.add(strategy.calculateWaitTime(50L, 5));
        }
        assertTrue(seen.size() > 10,
                "random strategy should produce varied values; only saw " + seen.size());
    }

    @Test
    void randomStrategyHandlesLargeBoundWithoutTruncation() {
        // baseWaitTime * retryCount must not silently overflow when cast to int.
        // We pick a base that, multiplied by even a small retryCount, exceeds
        // Integer.MAX_VALUE to make sure we still get a positive result.
        RandomRetryStrategy strategy = new RandomRetryStrategy();
        long base = 3_000_000_000L; // > Integer.MAX_VALUE / 2
        long result = strategy.calculateWaitTime(base, 3);
        assertTrue(result >= base, "result " + result + " must be >= base " + base);
    }
}
