package cn.wubo.entity.sql;

import cn.wubo.flexible.lock.annotation.Locking;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * The original demo service — kept for backward compatibility with the
 * {@code /test/lock} endpoint.
 *
 * <p>Additional demo methods that exercise different {@code @Locking} shapes
 * (explicit {@code retryCount} / {@code waitTime}, SpEL method references,
 * bean references) live alongside the original {@link #doWork(String)} so
 * the UI can show side-by-side comparisons.
 */
@Slf4j
@Component
public class DemoService {

    /** Plain SpEL key built from the parameter — the default style. */
    @Locking(key = "'doWork-' + #key")
    public String doWork(String key) {
        sleep();
        return key;
    }

    /** Same key shape as {@link #doWork} but with an explicit high retry budget
     *  so a UI burst test can run long enough to observe serialization. */
    @Locking(key = "'retry-' + #key", retryCount = 30, waitTime = 1000L)
    public String doWorkWithRetries(String key) {
        sleep();
        return key;
    }

    /** Explicit short wait + zero retries — used to demonstrate the
     *  {@code LockRuntimeException} surfaced when contention is high. */
    @Locking(key = "'strict-' + #key", retryCount = 0, waitTime = 100L)
    public String doWorkWithShortWait(String key) {
        sleep();
        return key;
    }

    /**
     * Demonstrates {@code @beanName} SpEL syntax — the key is composed by
     * invoking a method on a Spring bean (here, a {@link java.time.Clock}
     * injected as {@code systemClock}).
     */
    @Locking(key = "'clock-' + #key + '-' + @systemClock.millis()")
    public String doWorkWithBeanRef(String key) {
        sleep();
        return key;
    }

    /**
     * Demonstrates the {@code #method} SpEL variable exposed by the aspect.
     * The lock key is derived from the method signature itself, so it's
     * impossible to typo: any call to {@code doWorkForMethod} collides on
     * the same key.
     */
    @Locking(key = "#method.declaringClass.simpleName + ':' + #method.name")
    public String doWorkForMethod() {
        sleep();
        return "method-key";
    }

    /**
     * Slow artificial workload — 1–3 s — so concurrent callers actually
     * contend. Random within a band keeps tests honest.
     */
    private static void sleep() {
        try {
            long ms = (long) ((Math.random() * 2 + 1) * 1000);
            Thread.sleep(ms);
            log.debug("{} slept {}ms", Thread.currentThread().threadId(), ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    /**
     * Bean used by {@link #doWorkWithBeanRef}'s {@code @systemClock} SpEL
     * reference. Exists as a Spring bean so the {@code BeanFactoryResolver}
     * can resolve the name at AOP time.
     */
    @Component("systemClock")
    public static class SystemClock {
        public long millis() {
            return System.currentTimeMillis();
        }
    }

    /**
     * Force-import {@link Qualifier} annotation usage so the IDE doesn't
     * strip the import as "unused" if {@link SystemClock} is later refactored
     * to be injected by name.
     */
    @SuppressWarnings("unused")
    private static final Class<?> KEEP_QUALIFIER_IMPORT = Qualifier.class;
}
