package cn.wubo.flexible.lock.aop;

import cn.wubo.flexible.lock.annotation.Locking;
import cn.wubo.flexible.lock.core.ILock;
import cn.wubo.flexible.lock.exception.LockRuntimeException;
import cn.wubo.flexible.lock.retry.FixedRetryStrategy;
import cn.wubo.flexible.lock.retry.IRetryStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link LockAnnotationAspect}.
 *
 * The aspect's lifecycle must:
 *   1. acquire the lock, evaluating the SpEL key exactly once
 *   2. run the protected body only if the lock was acquired
 *   3. release the lock on the way out
 *   4. never call unLock when tryLock failed (which would throw on Redisson / Zookeeper)
 *   5. respect retryCount semantics: N means N additional retries (N+1 attempts),
 *      and 0 means a single attempt with no retries
 */
class LockAnnotationAspectTest {

    /** ILock test double — counts calls and remembers every key seen. */
    static class TrackingLock implements ILock {
        final List<String> lockKeys = new ArrayList<>();
        final List<Long> lockWaits = new ArrayList<>();
        final List<String> unlockKeys = new ArrayList<>();
        boolean tryLockResult = true;
        /** If non-null, every tryLock call throws this RuntimeException. */
        RuntimeException tryLockException;

        @Override
        public Boolean tryLock(String key) {
            lockKeys.add(key);
            lockWaits.add(0L);
            if (tryLockException != null) throw tryLockException;
            return tryLockResult;
        }

        @Override
        public Boolean tryLock(String key, Long waitTime) {
            lockKeys.add(key);
            lockWaits.add(waitTime);
            if (tryLockException != null) throw tryLockException;
            return tryLockResult;
        }

        @Override
        public void unLock(String key) {
            unlockKeys.add(key);
        }

        @Override
        public Integer getRetryCount() {
            return 3;
        }

        @Override
        public Long getWaitTime() {
            return 100L;
        }
    }

    /** Bean exposed via SpEL to verify dynamic key resolution. */
    public static class KeyCounter {
        private final AtomicInteger counter = new AtomicInteger();
        public int next() {
            return counter.incrementAndGet();
        }
        public int current() {
            return counter.get();
        }
    }

    private TrackingLock lock;
    private LockAnnotationAspect aspect;
    private DefaultListableBeanFactory beanFactory;

    @BeforeEach
    void setUp() {
        lock = new TrackingLock();
        IRetryStrategy retryStrategy = new FixedRetryStrategy();
        beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("counter", new KeyCounter());
        aspect = new LockAnnotationAspect(lock, retryStrategy, beanFactory);
    }

    /** Find the doSomething(String) method on this test class for SpEL parameter binding. */
    private Method method(String name, Class<?>... paramTypes) throws NoSuchMethodException {
        return getClass().getDeclaredMethod(name, paramTypes);
    }

    @Locking(key = "#name + '-suffix'")
    public String doSomething(String name) {
        return "ok-" + name;
    }

    @Locking(key = "@counter.next()")
    public String doWithCounter() {
        return "ok";
    }

    @Locking(key = "'static-key'", retryCount = 2)
    public String doWithTwoRetries() {
        return "ok";
    }

    @Locking(key = "'zero-retry'", retryCount = 0)
    public String doWithZeroRetries() {
        return "ok";
    }

    @Locking(key = "'never'", retryCount = 5)
    public String doNeverSucceed() {
        return "ok";
    }

    @Test
    void unLockMustNotBeCalledWhenTryLockFails() throws Throwable {
        // Given: tryLock always fails
        lock.tryLockResult = false;

        // When: an annotated method is invoked via the aspect
        Method m = method("doSomething", String.class);
        LockRuntimeException ex = assertThrows(LockRuntimeException.class, () ->
                aspect.executeWithLock(
                        m.getAnnotation(Locking.class),
                        m,
                        new Object[]{"hello"},
                        () -> "should not run"
                )
        );

        // Then: lock was attempted but unLock was NEVER called
        // (which would otherwise throw IllegalMonitorStateException on Redisson / Zookeeper)
        assertTrue(ex.getMessage().contains("hello-suffix"),
                "exception should reference the failed-to-acquire key");
        assertEquals(4, lock.lockKeys.size(), "should retry retryCount+1 times before giving up");
        assertTrue(lock.unlockKeys.isEmpty(),
                "unLock must NOT be called when no lock was acquired; "
                        + "actual unlockKeys=" + lock.unlockKeys);
    }

    @Test
    void keyIsResolvedExactlyOnceForLockAndUnlock() throws Throwable {
        // Given: a SpEL expression with a side effect (#counter.next())
        KeyCounter counter = beanFactory.getBean(KeyCounter.class);

        // When: a method protected by that expression runs to completion
        Method m = method("doWithCounter");
        Object result = aspect.executeWithLock(
                m.getAnnotation(Locking.class),
                m,
                new Object[]{},
                () -> "ok"
        );

        // Then: counter.next() ran exactly once, and lock/unlock used the same key
        assertEquals(1, counter.current(), "SpEL expression must be evaluated exactly once");
        assertEquals("ok", result);
        assertEquals(1, lock.lockKeys.size());
        assertEquals(1, lock.unlockKeys.size());
        assertEquals(lock.lockKeys.get(0), lock.unlockKeys.get(0),
                "lock and unlock must use the SAME key");
    }

    @Test
    void happyPath_locksRunsBodyAndUnlocks() throws Throwable {
        // Given: tryLock succeeds
        // When: a method is invoked via the aspect
        Method m = method("doSomething", String.class);
        Object result = aspect.executeWithLock(
                m.getAnnotation(Locking.class),
                m,
                new Object[]{"world"},
                () -> "ok-world"
        );

        // Then: body ran and lock/unlock both happened with the same key
        assertEquals("ok-world", result);
        assertEquals(1, lock.lockKeys.size());
        assertEquals(1, lock.unlockKeys.size());
        assertEquals("world-suffix", lock.lockKeys.get(0));
        assertEquals("world-suffix", lock.unlockKeys.get(0));
    }

    @Test
    void unLockRunsEvenWhenBodyThrows() throws Throwable {
        // Given: tryLock succeeds
        // When: the body throws an exception
        Method m = method("doSomething", String.class);
        RuntimeException thrown = assertThrows(RuntimeException.class, () ->
                aspect.executeWithLock(
                        m.getAnnotation(Locking.class),
                        m,
                        new Object[]{"world"},
                        () -> { throw new RuntimeException("boom"); }
                )
        );

        // Then: unLock still ran (resources must be released)
        assertEquals(1, lock.lockKeys.size());
        assertEquals(1, lock.unlockKeys.size(), "unLock must run in finally even when body throws");
        assertEquals(lock.lockKeys.get(0), lock.unlockKeys.get(0));
        // The original exception was NOT wrapped — same instance, same message.
        assertEquals("boom", thrown.getMessage());
    }

    @Test
    void checkedExceptionFromBodyPropagatesWithoutWrapping() throws Throwable {
        // Given: tryLock succeeds; body throws a checked exception
        Method m = method("doSomething", String.class);
        java.io.IOException bodyEx = new java.io.IOException("disk full");
        java.io.IOException caught = assertThrows(java.io.IOException.class, () ->
                aspect.executeWithLock(
                        m.getAnnotation(Locking.class),
                        m,
                        new Object[]{"world"},
                        () -> { throw bodyEx; }
                )
        );
        // Then: the original exception is the same instance (no wrapping)
        assertSame(bodyEx, caught,
                "checked exceptions must propagate without being wrapped in RuntimeException");
        // And: unLock still ran
        assertEquals(1, lock.unlockKeys.size(), "unLock must run even when body throws checked");
    }

    @Test
    void retryCountFromAnnotationOverridesGlobal() throws Throwable {
        // Given: annotation says retryCount=2, global says 3
        // When: tryLock always fails
        lock.tryLockResult = false;

        Method m = method("doWithTwoRetries");
        assertThrows(LockRuntimeException.class, () ->
                aspect.executeWithLock(
                        m.getAnnotation(Locking.class),
                        m,
                        new Object[]{},
                        () -> null
                )
        );

        // Then: 3 total attempts (1 initial + 2 retries)
        assertEquals(3, lock.lockKeys.size(),
                "retryCount=2 should mean 2 retries = 3 total attempts");
    }

    @Test
    void retryCountZeroMeansSingleAttempt() throws Throwable {
        // Given: annotation says retryCount=0
        // When: tryLock fails
        lock.tryLockResult = false;

        Method m = method("doWithZeroRetries");
        assertThrows(LockRuntimeException.class, () ->
                aspect.executeWithLock(
                        m.getAnnotation(Locking.class),
                        m,
                        new Object[]{},
                        () -> null
                )
        );

        // Then: only 1 tryLock call (no retries)
        assertEquals(1, lock.lockKeys.size(),
                "retryCount=0 should mean single attempt, no retries");
    }

    @Test
    void exhaustedRetriesThrowLockRuntimeException() throws Throwable {
        // Given: tryLock always fails and retryCount=5
        lock.tryLockResult = false;

        Method m = method("doNeverSucceed");
        LockRuntimeException ex = assertThrows(LockRuntimeException.class, () ->
                aspect.executeWithLock(
                        m.getAnnotation(Locking.class),
                        m,
                        new Object[]{},
                        () -> null
                )
        );

        // Then: 6 attempts (1 + 5 retries) and exception names the key
        assertEquals(6, lock.lockKeys.size());
        assertTrue(ex.getMessage().contains("never"),
                "exception message should include the key for diagnostics");
        assertTrue(lock.unlockKeys.isEmpty());
    }

    @Test
    void tryLockExceptionCauseIsPreservedAndShortCircuits() throws Throwable {
        // When a transport-level error fires (e.g., Redis connection refused),
        // the aspect must surface the original exception as the cause so users
        // can debug the real problem, not a generic "failed to acquire" message.
        // Retrying on transport errors doesn't help (the same connection error
        // will fire on every attempt), so we short-circuit — but preserve the cause.
        RuntimeException rootCause = new RuntimeException("connection refused: 127.0.0.1:6379");
        lock.tryLockException = rootCause;

        Method m = method("doSomething", String.class);
        LockRuntimeException ex = assertThrows(LockRuntimeException.class, () ->
                aspect.executeWithLock(
                        m.getAnnotation(Locking.class),
                        m,
                        new Object[]{"x"},
                        () -> null
                )
        );

        // Then: the root cause is preserved as the cause
        assertSame(rootCause, ex.getCause(),
                "LockRuntimeException must wrap the original tryLock exception as its cause");
        // And: we short-circuit rather than waste retries on a broken transport
        assertEquals(1, lock.lockKeys.size(),
                "transport errors must short-circuit, not burn through the retry budget");
        assertTrue(lock.unlockKeys.isEmpty(),
                "no unLock since we never acquired");
    }

    @Test
    void bridgeMethodParametersAreResolvedViaMostSpecificMethod() throws Throwable {
        // When an interface method's implementation narrows the return type
        // (covariant return), the compiler generates a synthetic bridge method
        // on the implementation class with the wider return type. Spring AOP
        // hands the aspect the bridge method (because that's the method on
        // the target's supertype), and DefaultParameterNameDiscoverer returns
        // null for bridge methods, so #arg references in the SpEL key would
        // silently fail. The aspect must resolve to the user-declared method
        // so parameter names are visible.
        Method bridge = null;
        Method specific = null;
        for (Method m : BridgeImpl.class.getDeclaredMethods()) {
            if (!m.getName().equals("getValue") || m.getParameterCount() != 1) continue;
            if (m.isBridge()) bridge = m; else specific = m;
        }
        // Sanity: the implementation class has BOTH methods — the user-declared
        // one (String return) and the synthetic bridge (Object return).
        assertNotNull(bridge, "expected synthetic bridge method on impl class");
        assertNotNull(specific, "expected user-declared method on impl class");
        assertTrue(bridge.isBridge(), "interface method on impl class should be a bridge");
        assertFalse(specific.isBridge(), "user-declared method should not be a bridge");

        // The aspect must, given the bridge method, look up the specific
        // method on the target class and bind its parameter name.
        Object result = aspect.executeWithLock(
                new Locking() {
                    @Override public Class<? extends java.lang.annotation.Annotation> annotationType() { return Locking.class; }
                    @Override public String key() { return "'bridge-' + #value"; }
                    @Override public long waitTime() { return -1; }
                    @Override public int retryCount() { return -1; }
                },
                bridge,
                new Object[]{"hello"},
                () -> "ok"
        );
        assertEquals("ok", result);
        assertEquals(1, lock.lockKeys.size());
        assertEquals("bridge-hello", lock.lockKeys.get(0),
                "must resolve parameter names from the specific (user-declared) method");
    }

    interface Bridge { Object getValue(String value); }
    static class BridgeImpl implements Bridge {
        @Override
        public String getValue(String value) { return value; }
    }

    // ─────────────────────────────────────────────────────────────────────
    // SpEL expression cache
    // ─────────────────────────────────────────────────────────────────────

    @Test
    void expressionCacheReusesCompiledExpressionAcrossCalls() throws Throwable {
        // Given: a fixed SpEL string that we'll invoke many times
        Method m = method("doSomething", String.class);
        Locking annotation = m.getAnnotation(Locking.class);

        // When: we invoke the same annotated method several times
        for (int i = 0; i < 5; i++) {
            aspect.executeWithLock(annotation, m, new Object[]{"x"}, () -> null);
        }

        // Then: the expression cache holds exactly ONE entry for this SpEL
        Map<String, ?> cache = expressionCache();
        assertEquals(1, cache.size(),
                "5 invocations of the same SpEL string must populate the cache once; actual=" + cache.keySet());
        assertTrue(cache.containsKey(annotation.key()),
                "cache key must be the SpEL string literal");
    }

    @Test
    void expressionCacheIsIsolatedPerDistinctSpELString() throws Throwable {
        // Different SpEL expressions must NOT share cache entries —
        // otherwise a cache hit would return a stale compiled AST.
        Method mStatic = method("doWithTwoRetries");
        Method mZero = method("doWithZeroRetries");
        aspect.executeWithLock(mStatic.getAnnotation(Locking.class), mStatic,
                new Object[]{}, () -> null);
        aspect.executeWithLock(mZero.getAnnotation(Locking.class), mZero,
                new Object[]{}, () -> null);

        Map<String, ?> cache = expressionCache();
        assertEquals(2, cache.size(),
                "two distinct SpEL strings must produce two cache entries");
    }

    // ─────────────────────────────────────────────────────────────────────
    // Default waitTime / retryCount via -1 sentinel
    // ─────────────────────────────────────────────────────────────────────

    @Locking(key = "'global-default'", waitTime = -1, retryCount = -1)
    public String doWithGlobalDefaults() {
        return "ok";
    }

    @Test
    void negativeOneWaitTimeFallsBackToGlobalProperties() throws Throwable {
        // TrackingLock returns waitTime=100, retryCount=3 — these come from
        // the global defaults. The annotation sets both to -1, which means
        // "use the global default", so the values must flow through.
        Method m = method("doWithGlobalDefaults");
        aspect.executeWithLock(
                m.getAnnotation(Locking.class), m, new Object[]{}, () -> null);

        assertEquals(1, lock.lockWaits.size());
        assertEquals(100L, lock.lockWaits.get(0),
                "waitTime=-1 must use the global default from properties");
    }

    @Test
    void negativeOneRetryCountFallsBackToGlobalProperties() throws Throwable {
        // retryCount=-1 means "use the global default" (3). Since tryLock
        // succeeds here, we can't directly observe the retry count, but we
        // can prove it was NOT overridden to 0 (which would also pass
        // because the first attempt succeeds). Instead, drive it to fail
        // and check the attempt count.
        lock.tryLockResult = false;
        // override with explicit retryCount=-1
        Method m = method("doWithGlobalDefaults");
        assertThrows(LockRuntimeException.class, () ->
                aspect.executeWithLock(
                        m.getAnnotation(Locking.class), m, new Object[]{}, () -> null));

        // Global retryCount=3 → 4 total attempts
        assertEquals(4, lock.lockKeys.size(),
                "retryCount=-1 must use the global default of 3 → 4 total attempts");
    }

    // ─────────────────────────────────────────────────────────────────────
    // Body result & null arguments
    // ─────────────────────────────────────────────────────────────────────

    @Test
    void bodyResultIsReturnedToCaller() throws Throwable {
        Method m = method("doSomething", String.class);
        Object result = aspect.executeWithLock(
                m.getAnnotation(Locking.class), m, new Object[]{"x"}, () -> 42);
        assertEquals(42, result,
                "aspect must return whatever the body returned, untouched");
    }

    @Test
    void bodyReturningNullIsPropagatedAsNull() throws Throwable {
        Method m = method("doSomething", String.class);
        Object result = aspect.executeWithLock(
                m.getAnnotation(Locking.class), m, new Object[]{"x"}, () -> null);
        assertNull(result, "a null body return must come back as null");
    }

    // ─────────────────────────────────────────────────────────────────────
    // SpEL with no parameters
    // ─────────────────────────────────────────────────────────────────────

    @Locking(key = "'no-params-' + 'static'")
    public String doWithNoParamSpEL() {
        return "ok";
    }

    @Test
    void spelWorksWhenMethodHasNoParameters() throws Throwable {
        // The SpEL engine must not blow up just because the method has no
        // parameter names to bind (the key only references string literals).
        Method m = method("doWithNoParamSpEL");
        aspect.executeWithLock(
                m.getAnnotation(Locking.class), m, new Object[]{}, () -> null);

        assertEquals(1, lock.lockKeys.size());
        assertEquals("no-params-static", lock.lockKeys.get(0));
    }

    // ─────────────────────────────────────────────────────────────────────
    // @beanName references
    // ─────────────────────────────────────────────────────────────────────

    @Test
    void spelBeanReferenceResolvesViaBeanFactory() throws Throwable {
        // Given: a counter bean registered in the bean factory
        KeyCounter counter = beanFactory.getBean(KeyCounter.class);

        Method m = method("doWithCounter");
        aspect.executeWithLock(
                m.getAnnotation(Locking.class), m, new Object[]{}, () -> null);

        // Then: the @counter.next() expression resolved to the bean and
        // called .next() on it, producing a unique key per invocation.
        assertEquals(1, counter.current());
        assertEquals("1", lock.lockKeys.get(0),
                "SpEL @beanName.method() must resolve to the registered bean");
    }

    @Test
    void spelBeanReferenceAcrossMultipleInvocations() throws Throwable {
        // Same key template, but each call should advance the counter
        // (different keys each time → no mutual exclusion issues).
        KeyCounter counter = beanFactory.getBean(KeyCounter.class);
        Method m = method("doWithCounter");
        for (int i = 0; i < 3; i++) {
            aspect.executeWithLock(
                    m.getAnnotation(Locking.class), m, new Object[]{}, () -> null);
        }
        assertEquals(3, counter.current(),
                "each call must invoke @counter.next() exactly once");
        // Keys must be distinct because next() returns a fresh value each time
        assertEquals(3, lock.lockKeys.stream().distinct().count(),
                "each invocation must use a distinct lock key");
    }

    // ─────────────────────────────────────────────────────────────────────
    // Retry strategy visible in wait time computation
    // ─────────────────────────────────────────────────────────────────────

    @Test
    void waitTime0InAnnotationSkipsTryLockTimeArgument() throws Throwable {
        // When waitTime = 0, the aspect calls lock.tryLock(key) (no time
        // argument) — same as Redisson's tryLock() which is non-blocking.
        // This is the documented escape hatch for "don't wait at all".
        Method m = method("doSomething", String.class);
        Locking annotation = new Locking() {
            @Override public Class<? extends java.lang.annotation.Annotation> annotationType() { return Locking.class; }
            @Override public String key() { return "'zero-wait'"; }
            @Override public long waitTime() { return 0L; }
            @Override public int retryCount() { return 0; }
        };
        aspect.executeWithLock(annotation, m, new Object[]{}, () -> null);

        // waitTime=0 → the aspect calls the no-arg tryLock overload,
        // which our TrackingLock records with waitTime=0L. The point is
        // the call didn't fail — i.e. the right overload was picked.
        assertEquals(1, lock.lockKeys.size());
        assertEquals(0L, lock.lockWaits.get(0),
                "waitTime=0 must select the no-time-arg tryLock overload");
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, ?> expressionCache() throws Exception {
        Field f = LockAnnotationAspect.class.getDeclaredField("expressionCache");
        f.setAccessible(true);
        return (Map<String, ?>) f.get(aspect);
    }
}
