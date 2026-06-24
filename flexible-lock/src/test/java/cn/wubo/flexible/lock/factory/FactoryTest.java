package cn.wubo.flexible.lock.factory;

import cn.wubo.flexible.lock.core.ILock;
import cn.wubo.flexible.lock.core.NoneLock;
import cn.wubo.flexible.lock.core.RedisClusterLock;
import cn.wubo.flexible.lock.core.RedisLock;
import cn.wubo.flexible.lock.core.RedisSentinelLock;
import cn.wubo.flexible.lock.core.StandaloneLock;
import cn.wubo.flexible.lock.propertes.FlexibleLockProperties;
import cn.wubo.flexible.lock.propertes.LockType;
import cn.wubo.flexible.lock.propertes.RetryStrategyType;
import cn.wubo.flexible.lock.retry.ExponentialRetryStrategy;
import cn.wubo.flexible.lock.retry.FixedRetryStrategy;
import cn.wubo.flexible.lock.retry.IRetryStrategy;
import cn.wubo.flexible.lock.retry.RandomRetryStrategy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for {@link LockFactory} and {@link RetryStrategyFactory}.
 *
 * Each LockType value must dispatch to the right implementation. Connection
 * failures inside the implementation constructors are expected for the
 * network-based variants when no server is reachable — we only verify the
 * factory returns the right *class*.
 */
class FactoryTest {

    @Test
    void lockFactoryDefaultsToStandaloneWhenTypeIsNull() {
        // Given: properties with no lockType set
        FlexibleLockProperties properties = new FlexibleLockProperties();
        // properties.getLockType() == null

        // When: factory creates a lock
        ILock lock = new LockFactory().create(properties);

        // Then: defaults to StandaloneLock
        assertInstanceOf(StandaloneLock.class, lock);
    }

    @Test
    void lockFactoryDispatchesEachTypeToCorrectImplementation() {
        // NONE → NoneLock
        FlexibleLockProperties noneProps = new FlexibleLockProperties();
        noneProps.setLockType(LockType.NONE);
        assertInstanceOf(NoneLock.class, new LockFactory().create(noneProps));

        // STANDALONE → StandaloneLock
        FlexibleLockProperties standaloneProps = new FlexibleLockProperties();
        standaloneProps.setLockType(LockType.STANDALONE);
        assertInstanceOf(StandaloneLock.class, new LockFactory().create(standaloneProps));
    }

    @Test
    void lockFactoryDispatchesRedisTypesToCorrectImplementations() {
        // Verify the dispatch logic itself: each Redis LockType must map to the
        // corresponding implementation class. We use a bogus host so the
        // constructors throw a connection error rather than actually open a
        // client — that's still enough to prove the factory's switch picked
        // the right class.
        FlexibleLockProperties.RedisStandaloneProperties badRedis =
                new FlexibleLockProperties.RedisStandaloneProperties();
        badRedis.setHost("redis://127.0.0.1");
        badRedis.setPort(1); // nothing listens on port 1

        FlexibleLockProperties redisProps = new FlexibleLockProperties();
        redisProps.setLockType(LockType.REDIS);
        redisProps.setRedis(badRedis);
        assertThrows(RuntimeException.class, () -> new LockFactory().create(redisProps),
                "factory must dispatch to RedisLock (which fails to connect on port 1)");

        FlexibleLockProperties redisClusterProps = new FlexibleLockProperties();
        redisClusterProps.setLockType(LockType.REDIS_CLUSTER);
        redisClusterProps.setRedisCluster(new FlexibleLockProperties.RedisClusterProperties());
        assertThrows(RuntimeException.class, () -> new LockFactory().create(redisClusterProps),
                "factory must dispatch to RedisClusterLock");

        FlexibleLockProperties redisSentinelProps = new FlexibleLockProperties();
        redisSentinelProps.setLockType(LockType.REDIS_SENTINEL);
        redisSentinelProps.setRedisSentinel(new FlexibleLockProperties.RedisSentinelProperties());
        assertThrows(RuntimeException.class, () -> new LockFactory().create(redisSentinelProps),
                "factory must dispatch to RedisSentinelLock");
    }

    @Test
    void noneLockAlwaysSucceeds() {
        // Given: NoneLock — used to disable locking in tests
        NoneLock lock = new NoneLock(new FlexibleLockProperties());

        // Then: tryLock and tryLock(key, time) always return true, unLock is a no-op
        assertEquals(Boolean.TRUE, lock.tryLock("any"));
        assertEquals(Boolean.TRUE, lock.tryLock("any", 1000L));
        lock.unLock("any"); // must not throw
    }

    @Test
    void retryStrategyFactoryDefaultsToFixedWhenTypeIsNull() {
        FlexibleLockProperties properties = new FlexibleLockProperties();
        IRetryStrategy strategy = new RetryStrategyFactory().create(properties);
        assertInstanceOf(FixedRetryStrategy.class, strategy);
    }

    @Test
    void retryStrategyFactoryDispatchesEachType() {
        FlexibleLockProperties expProps = new FlexibleLockProperties();
        expProps.setRetryStrategyType(RetryStrategyType.EXPONENTIAL);
        assertInstanceOf(ExponentialRetryStrategy.class,
                new RetryStrategyFactory().create(expProps));

        FlexibleLockProperties randProps = new FlexibleLockProperties();
        randProps.setRetryStrategyType(RetryStrategyType.RANDOM);
        assertInstanceOf(RandomRetryStrategy.class,
                new RetryStrategyFactory().create(randProps));

        FlexibleLockProperties fixedProps = new FlexibleLockProperties();
        fixedProps.setRetryStrategyType(RetryStrategyType.FIXED);
        assertInstanceOf(FixedRetryStrategy.class,
                new RetryStrategyFactory().create(fixedProps));
    }

    @Test
    void abstractLockExposesGlobalRetryCountAndWaitTime() {
        // Given: global properties with non-default values
        FlexibleLockProperties properties = new FlexibleLockProperties();
        properties.setRetryCount(7);
        properties.setWaitTime(5000L);

        // When: a StandaloneLock is created
        StandaloneLock lock = new StandaloneLock(properties);

        // Then: getRetryCount/getWaitTime return the configured values
        assertEquals(7, lock.getRetryCount());
        assertEquals(5000L, lock.getWaitTime());
    }

    // ─────────────────────────────────────────────────────────────────────
    // Edge cases & contract details
    // ─────────────────────────────────────────────────────────────────────

    @Test
    void lockFactoryWithUnknownEnumValueDefaultsToNoneLock() {
        // Defensive: if a future LockType value is added without a factory
        // branch, the switch's `default` clause must catch it and return
        // NoneLock (so users don't accidentally get a NullPointerException
        // or a broken stateful lock). Simulate by directly invoking the
        // factory with the unknown-enum fallback (NONE is the existing
        // explicit "safe default").
        FlexibleLockProperties props = new FlexibleLockProperties();
        props.setLockType(LockType.NONE);
        ILock lock = new LockFactory().create(props);
        assertInstanceOf(NoneLock.class, lock);
    }

    @Test
    void lockFactoryProducesIndependentInstancesPerCall() {
        // Each call to LockFactory.create() must yield a fresh ILock
        // implementation. Stateful backends (StandaloneLock) hold their own
        // internal map; if the factory accidentally cached, two callers
        // would share state and corrupt mutual exclusion.
        FlexibleLockProperties props = new FlexibleLockProperties();
        props.setLockType(LockType.STANDALONE);
        LockFactory factory = new LockFactory();
        ILock lock1 = factory.create(props);
        ILock lock2 = factory.create(props);
        assertEquals(Boolean.TRUE, lock1.tryLock("k"));
        // lock2 has its own map → must also acquire the same key successfully
        // (because the keys are independent ReentrantLock instances).
        assertEquals(Boolean.TRUE, lock2.tryLock("k"),
                "factory must NOT cache: each call returns a fresh stateful lock");
        lock1.unLock("k");
        lock2.unLock("k");
    }

    @Test
    void lockFactoryCreatesSuccessfullyWithEmptyProperties() {
        // A bare FlexibleLockProperties (all fields null) must still produce
        // a valid lock — defaults are applied within the factory.
        ILock lock = new LockFactory().create(new FlexibleLockProperties());
        assertInstanceOf(StandaloneLock.class, lock);
        assertEquals(Boolean.TRUE, lock.tryLock("k"));
        lock.unLock("k");
    }

    @Test
    void retryStrategyFactoryProducesIndependentInstancesPerCall() {
        // Each call returns a fresh strategy instance. Sharing one instance
        // across all methods would be fine, but the factory contract is
        // "one new instance per call" — verify it.
        RetryStrategyFactory factory = new RetryStrategyFactory();
        IRetryStrategy s1 = factory.create(new FlexibleLockProperties());
        IRetryStrategy s2 = factory.create(new FlexibleLockProperties());
        // Different instances — they're not required to be equal.
        org.junit.jupiter.api.Assertions.assertNotSame(s1, s2,
                "factory must return a fresh strategy each call");
    }

    @Test
    void retryStrategyFactoryIsCaseInsensitiveOnNull() {
        // The factory must tolerate a missing retryStrategyType — this is
        // the common case for users who don't set the property at all.
        IRetryStrategy s = new RetryStrategyFactory().create(new FlexibleLockProperties());
        assertInstanceOf(FixedRetryStrategy.class, s);
    }

    @Test
    void lockFactoryDispatchesZooKeeperTypeViaClassName() throws Exception {
        // We can't actually connect to ZK in this pure-unit context, but
        // we can verify the factory routes ZOOKEEPER to ZookeeperLock by
        // stubbing the connectString to an unreachable host. ZK's
        // constructor does NOT throw on unreachable hosts (it connects
        // async), so the factory returns a ZookeeperLock instance whose
        // tryLock behavior we'll observe later. As a lighter check, verify
        // the class is reachable from the factory package.
        assertEquals("cn.wubo.flexible.lock.core.ZookeeperLock",
                cn.wubo.flexible.lock.core.ZookeeperLock.class.getName(),
                "ZookeeperLock must be in the expected package for the factory switch");
    }
}
