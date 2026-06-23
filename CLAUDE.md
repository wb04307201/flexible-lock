# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

`flexible-lock` is a Spring Boot starter that provides a unified `@Locking` annotation backed by pluggable lock implementations (Redis standalone/cluster/sentinel, Zookeeper, local `ReentrantLock`, and a `none` no-op). Licensed Apache 2.0, distributed via JitPack under `io.github.wb04307201.flexible-lock:flexible-lock-spring-boot-starter`.

- Java 17, Spring Boot 3.5.14, Maven multi-module build
- Coordinates: `io.github.wb04307201:flexible-lock-parent:1.0-SNAPSHOT`
- Locking backends: Redisson 3.52.0 (Redis variants), Spring Integration Zookeeper 6.5.1 (Curator under the hood)
- Build JDK: requires JDK 17 or 21 (Lombok 1.18.46 has known issues on JDK 25)

## Build & Test Commands

The repo ships with the Maven wrapper (`mvnw` / `mvnw.cmd`). Use it — no global Maven install required.

```bash
# Build all modules (skipping tests for a quick compile check)
./mvnw clean install -DskipTests

# Run the full test suite across all modules
./mvnw test

# Run a single test class (e.g. the StandaloneLock race condition test)
./mvnw -pl flexible-lock -Dtest=StandaloneLockTest test

# Run a single test method
./mvnw -pl flexible-lock -Dtest=StandaloneLockTest#unLockShouldKeepReentrantLockInstanceInMap test

# Install a single module and its deps into the local repo
./mvnw -pl flexible-lock -am install
```

If you hit network errors connecting to `repo.maven.apache.org`, the project ships with an Aliyun mirror configured in `~/.m2/settings.xml` for users in CN.

Integration tests in `flexible-lock-test` (`RedisLockTest`, `ZookeeperLockTest`) connect to live services on `127.0.0.1:6379` (Redis) and `127.0.0.1:2181` (ZooKeeper). They will fail if those services aren't running locally.

## Module Layout

| Module | Purpose |
|---|---|
| `flexible-lock` | Core library: lock implementations, AOP aspect, factories, properties, retry strategies. Holds the unit tests. |
| `flexible-lock-spring-boot-autoconfigure` | `LockConfiguration` (Spring `@Configuration`) + `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` |
| `flexible-lock-spring-boot-starter` | Empty starter that depends on the autoconfigure module |
| `flexible-lock-test` | Spring Boot test app (`FlexibleLockTestApplication`) with `DemoController`/`DemoService` and the Redis/ZooKeeper integration tests |

Dependency direction: `starter` → `autoconfigure` → `flexible-lock`. The autoconfigure layer is the only module that should depend on `spring-boot-autoconfigure` and the configuration metadata processor.

## Architecture

### Lock acquisition flow

1. User annotates a method with `@Locking(key = "#userId + '-' + #orderId")`.
2. `LockAnnotationAspect` (single `@Around`) resolves the SpEL key **exactly once** via `SpelExpressionParser`, binding parameter names with `DefaultParameterNameDiscoverer` (requires `<parameters>true</parameters>` at compile time) and supporting `@beanName` references via `BeanFactoryResolver`.
3. The aspect loops up to `retryCount + 1` times (1 initial + `retryCount` retries). `retryCount = 0` means a single attempt.
4. Each attempt computes its wait via `retryStrategy.calculateWaitTime(baseWaitTime, attempt - 1)` then calls `lock.tryLock(key, waitTime)`.
5. If all attempts fail, `LockRuntimeException` is thrown.
6. If acquisition succeeded, the body runs inside `try { ... } finally { lock.unLock(key); }`. The lock is released even if the body throws.

`@Around` (not `@Before`+`@After`) is essential so that:
- `unLock` is **never** called when `tryLock` failed (would throw `IllegalMonitorStateException` on Redisson/ZooKeeper);
- the SpEL key is parsed **once** and reused for unlock (otherwise side-effectful expressions like `T(System).currentTimeMillis()` would compute different keys for lock and unlock).

### Lock implementations (`cn.wubo.flexible.lock.core`)

All extend `AbstractLock` (which exposes `getRetryCount()` / `getWaitTime()` from `FlexibleLockProperties`) and implement `ILock`:

- `StandaloneLock` — `ConcurrentHashMap<String, ReentrantLock>`. **The map is permanent** — `unLock` only calls `lock.unlock()` and does **not** `remove(key)`, otherwise a new acquirer would create a fresh `ReentrantLock` and break mutual exclusion.
- `RedisLock`, `RedisClusterLock`, `RedisSentinelLock` — each builds a Redisson `Config` in its constructor and holds a private `RedissonClient`. `shutdown()` calls `client.shutdown()`.
- `ZookeeperLock` — builds a `CuratorFramework` via `CuratorFrameworkFactory.builder()` (so `digest` ACL auth is supported) and wraps it in Spring Integration's `ZookeeperLockRegistry`. `shutdown()` calls `curatorFramework.close()`.
- `NoneLock` — no-op. `tryLock` always returns true; `unLock` and `shutdown` are no-ops. Used to disable locking without removing `@Locking` annotations.

### Factories

- `LockFactory.create(properties)` — switch on `properties.getLockType()` (enum `LockType`); defaults to `STANDALONE` if null, returns `NoneLock` for `NONE`.
- `RetryStrategyFactory.create(properties)` — switch on `properties.getRetryStrategyType()` (enum `RetryStrategyType`); defaults to `FIXED`.

### Retry strategies (`cn.wubo.flexible.lock.retry`)

- `FixedRetryStrategy` — returns `baseWaitTime` unchanged.
- `ExponentialRetryStrategy` — `baseWaitTime * 2^(retryCount - 1)` (via bit shift), with guards for `retryCount <= 0 || retryCount > 30` (shift overflow) and multiplication overflow when `baseWaitTime` is large. Falls back to `baseWaitTime` in either overflow case.
- `RandomRetryStrategy` — uniform random in `[baseWaitTime, baseWaitTime * (retryCount + 1))` using `ThreadLocalRandom` (not shared `Random`) and a `long` bound (no `(int)` truncation).

### Autoconfiguration

`LockConfiguration` exposes three beans: `ILock flexibleLock(...)` (with `destroyMethod = "shutdown"` so connection pools release on context close), `RetryStrategyFactory`, and `LockAnnotationAspect`. The aspect is constructed with the `ILock` bean plus a concrete `IRetryStrategy` resolved from the properties.

Users who want to provide their own `RedissonClient` / `CuratorFramework` register those beans in their own configuration and the starter will not create duplicates.

### Configuration

`FlexibleLockProperties` is bound to the `flexible.lock` prefix with `@Min(0)` on `retryCount` and `waitTime`. Defaults: `retryCount=3`, `waitTime=3000L`. Inner static classes hold per-backend config (`RedisStandaloneProperties`, `RedisClusterProperties`, `RedisSentinelProperties`, `ZookeeperProperties`); validation annotations (`@NotBlank`, `@Size`) live on those inner classes.

## Adding a New Lock Backend

1. Create a class in `flexible-lock/src/main/java/cn/wubo/flexible/lock/core/` extending `AbstractLock`; implement `tryLock` / `unLock`. If the backend holds external resources, override `shutdown()`.
2. Add a value to the `LockType` enum.
3. Add a new branch in `LockFactory.create`.
4. If the backend needs new connection settings, add an inner class to `FlexibleLockProperties` with validation annotations and a field on the outer properties class.
5. Update `README.md` and `README.zh-CN.md` — both must stay in sync (EN + ZH).

## Adding a New Retry Strategy

1. Create a class implementing `IRetryStrategy` in the `retry` package.
2. Add a value to the `RetryStrategyType` enum.
3. Add a branch in `RetryStrategyFactory.create`.

## Conventions

- Public APIs and configuration comments are in Chinese; the README exists in both English (`README.md`) and Chinese (`README.zh-CN.md`). Match the surrounding language when editing comments.
- `LockAnnotationAspect` log messages are in **English** (they appear in user-facing logs); surrounding code comments stay Chinese to match the codebase style.
- Lombok `@Data` / `@Slf4j` are used throughout.
- The package root is `cn.wubo.flexible.lock.*` (note the misspelled `propertes` package — preserve it when adding properties classes).
- Tests live alongside the code they test: `flexible-lock/src/test/java/...` for unit tests; `flexible-lock-test/src/test/java/...` for integration tests that require live Redis/ZooKeeper.
- No external linter is configured; rely on the Maven build for compile-time checks. The project uses `maven-surefire-plugin` 3.5.2 and `maven-compiler-plugin` 3.13.0 (both managed in the parent POM).
