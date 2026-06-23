# Flexible Lock

<div align="right">
  English | <a href="README.zh-CN.md">中文</a>
</div>

> A Spring Boot-based lock starter that provides a unified lock interface with multiple implementations including Redis standalone, Redis cluster, Redis sentinel, Zookeeper, local locks, and a no-op for tests. You can easily use locking functionality in your project through simple configuration.

![Maven Central](https://img.shields.io/maven-central/v/io.github.wb04307201/flexible-lock-spring-boot-starter?style=flat-square)
[![star](https://gitee.com/wb04307201/flexible-lock/badge/star.svg?theme=dark)](https://gitee.com/wb04307201/flexible-lock)
[![fork](https://gitee.com/wb04307201/flexible-lock/badge/fork.svg?theme=dark)](https://gitee.com/wb04307201/flexible-lock)
[![star](https://img.shields.io/github/stars/wb04307201/flexible-lock)](https://github.com/wb04307201/flexible-lock)
[![fork](https://img.shields.io/github/forks/wb04307201/flexible-lock)](https://github.com/wb04307201/flexible-lock)  
![MIT](https://img.shields.io/badge/License-Apache2.0-blue.svg) ![JDK](https://img.shields.io/badge/JDK-17+-green.svg) ![SpringBoot](https://img.shields.io/badge/Srping%20Boot-3+-green.svg)

## Features

- Support for multiple lock implementations:
    - Redis standalone mode
    - Redis cluster mode
    - Redis sentinel mode
    - Zookeeper distributed lock (with optional `digest` ACL auth)
    - Standalone `ReentrantLock`
    - **`none`** — no-op lock (always succeeds), useful for disabling locking in tests or staging
- Multiple retry strategies:
    - Fixed interval retry
    - Exponential backoff retry
    - Random backoff retry
- Annotation-based locking mechanism
- Support for SpEL expressions to define lock keys (including `@beanName` bean references)
- Configurable retry count and waiting time
- `retryCount = N` means **N additional retries** (so `N + 1` total attempts; `N = 0` means a single attempt)
- All Redis / ZooKeeper connection pools are shut down cleanly on Spring container close

## Installation

### Add Dependency
```xml
<dependency>
    <groupId>io.github.wb04307201.flexible-lock</groupId>
    <artifactId>flexible-lock-spring-boot-starter</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```


### Add Parameters Compiler Option for SpEL Expression Support
```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <configuration>
                <parameters>true</parameters>
            </configuration>
        </plugin>
    </plugins>
</build>
```


## Usage

### Basic Usage

Add the `@Locking` annotation to methods that require locking:

```java
@Service
public class BusinessService {

    @Locking(key = "#userId + '-' + #orderId")
    public void processOrder(String userId, String orderId) {
        // Business logic
    }
}
```

> ⚠️ **Self-invocation caveat:** `@Locking` is implemented with Spring AOP proxies.
> A method on the same bean calling another `@Locking`-annotated method on `this`
> bypasses the proxy and the lock will NOT be acquired. Either inject the bean
> into itself, split the method into a separate bean, or use AspectJ weaving.


### Annotation Parameters

| Property   | Type   | Default | Description                                      |
|------------|--------|---------|--------------------------------------------------|
| key        | String |         | Lock key, required, supports SpEL expressions (including `@beanName`) |
| waitTime   | long   | -1      | Lock timeout in ms, -1 means using global configuration |
| retryCount | int    | -1      | Number of retries AFTER the initial attempt, -1 means using global configuration; 0 means single attempt |

`retryCount` and `waitTime` are validated at startup with `@Min(0)`. Negative
values other than the `-1` "use global default" sentinel will be rejected by
Spring Boot's configuration validator.

### Class-level Annotation

`@Locking` can also be placed on a class. The annotation then applies to every
method on that class that does NOT have its own `@Locking`. A method-level
annotation overrides the class-level one.

```java
@Locking(key = "'order-' + #orderId", waitTime = 5000)
@Service
public class OrderService {

    // Uses the class-level key/waitTime/retryCount
    public void create(Order order) { ... }

    // Method-level overrides class-level: different key, no retries
    @Locking(key = "'order-strict-' + #orderId", retryCount = 0)
    public void forceUpdate(Order order) { ... }

    // No lock at all — neither class-level nor method-level applies
    public Order find(String orderId) { ... }
}
```

### Behavior on Failure

When all `retryCount + 1` attempts fail to acquire the lock, the aspect throws
`LockRuntimeException` (a `RuntimeException` subclass). The exception message
includes the lock key for diagnostics. Catch it at the boundary of your
business flow if you want to degrade gracefully (e.g., return a 503):

```java
try {
    orderService.create(order);
} catch (LockRuntimeException e) {
    // e.getMessage() contains the key that could not be acquired
    return ResponseEntity.status(503).build();
}
```

Transport-level errors (e.g., Redis connection refused) short-circuit the
retry loop and surface the original exception as the `cause` of
`LockRuntimeException` — retrying a broken connection does not help, so we
fail fast and preserve the root cause for observability.

### SpEL Context Variables

The SpEL key expression runs against a context that exposes:

| Variable     | Type                          | Description                                       |
|--------------|-------------------------------|---------------------------------------------------|
| `#method`    | `java.lang.reflect.Method`    | The currently locked method                       |
| `#<param>`   | the method parameter's type   | Each parameter bound by name (requires `<parameters>true</parameters>` at compile time) |
| `@beanName`  | any registered Spring bean    | Resolved via `BeanFactoryResolver`                |

Example using `#method`:

```java
@Locking(key = "#method.declaringClass.simpleName + ':' + #method.name")
public void foo() { ... }
```

**Security note:** SpEL keys run in a `StandardEvaluationContext`, which means
`T(...)` type references and arbitrary method calls are available. Treat the
SpEL string as developer-controlled configuration: it is a compile-time
annotation parameter, not a runtime user input, so the injection surface is
typos in your code rather than external attacks. Do not build SpEL strings by
concatenating untrusted input.

### Configuration

By default, standalone `ReentrantLock` is used with fixed retry strategy.

#### Local in-process lock (default)
```yaml
flexible:
  lock:
    retryCount: 3
    waitTime: 3000
    retryStrategyType: fixed
    lockType: standalone
```

#### Redis standalone
```yaml
flexible:
  lock:
    lockType: redis
    retryCount: 3
    waitTime: 3000
    redis:
      host: "redis://127.0.0.1"
      port: 6379
      password: ""
      database: 0
```

#### Redis cluster
```yaml
flexible:
  lock:
    lockType: redis_cluster
    redisCluster:
      nodes:
        - "redis://127.0.0.1:7000"
        - "redis://127.0.0.1:7001"
      password: ""
```

#### Redis sentinel
```yaml
flexible:
  lock:
    lockType: redis_sentinel
    redisSentinel:
      nodes:
        - "redis://127.0.0.1:26379"
        - "redis://127.0.0.1:26380"
      master: "mymaster"
      password: ""
      database: 0
```

#### Zookeeper (with optional digest ACL)
```yaml
flexible:
  lock:
    lockType: zookeeper
    zookeeper:
      connectString: "127.0.0.1:2181"
      maxElapsedTimeMs: 1000
      sleepMsBetweenRetries: 4
      root: "/locks"
      # Optional: "user:password" for digest ACL on the ZK nodes.
      # Without this, the client cannot create /locks if an ACL is set.
      digest: "user:password"
```

#### Disable locking entirely (none)
```yaml
flexible:
  lock:
    lockType: none
```
The `none` backend always succeeds, so `@Locking` is a no-op. Useful for
local development, integration tests, or staging environments where you want
to skip the locking overhead.

#### Debug logging
```yaml
logging:
  level:
    cn:
      wubo:
        flexible:
          lock: debug
```

## Retry Strategies

| Type | Behavior |
|------|----------|
| `fixed` | Each retry waits exactly `waitTime` ms (default) |
| `exponential` | Wait time doubles each retry: `waitTime * 2^(retryCount - 1)`, capped to avoid overflow |
| `random` | Random wait in `[waitTime, waitTime * (retryCount + 1))` ms |

## Custom Connection Pools

If you already manage a `RedissonClient` or `CuratorFramework` elsewhere in your
application, register it as a Spring bean and the starter will not create its
own. Internal clients created by the starter are shut down automatically when
the Spring context closes.

```java
@Bean(destroyMethod = "shutdown")
public RedissonClient redissonClient() {
    Config config = new Config();
    config.useSingleServer().setAddress("redis://127.0.0.1:6379");
    return Redisson.create(config);
}
```

## Try It: Demo Console

The repo ships with a runnable demo app under `flexible-lock-test/`. It
exposes a Thymeleaf console that exercises every `@Locking` shape the starter
supports — useful for kicking the tyres, comparing backends, or showing the
library to a colleague.

```bash
cd flexible-lock-test
mvn spring-boot:run
# then open http://127.0.0.1:8089/
```

The page has three panels:

- **DemoService** — five methods demonstrating one `@Locking` shape each:
  simple key, explicit `retryCount`/`waitTime`, bean reference (`@systemClock`),
  and `#method` SpEL.
- **OrderService** — class-level `@Locking` versus a method-level override
  side-by-side. Force a contention burst on `forceUpdate` to see the
  method-level override fail in `~50ms` while the class-level default keeps
  waiting.
- **Live log** — every invocation is recorded (key, status, duration,
  message) and the page polls it once per second, so you can watch a burst
  resolve in real time.

Each row has a **调用** button (single call) and a **并发** button (fire N
concurrent requests, see who wins and who gets a `LockRuntimeException`).

![Console — populated after a burst](docs/screenshots/console-burst.png)

By default the demo uses the in-process `STANDALONE` backend. To try Redis:

```bash
docker run -d --name flexible-lock-redis -p 6379:6379 redis:6-alpine
mvn spring-boot:run -Dspring-boot.run.arguments="\
  --flexible.lock.lockType=redis \
  --flexible.lock.redis.host=redis://127.0.0.1 \
  --flexible.lock.redis.port=6379"
# remember to stop and remove the container when done
```

The badge in the page header reflects the active backend:

| Badge | Backend |
|---|---|
| `STANDALONE (default)` | in-process `ReentrantLock` |
| `REDIS` | Redisson Redis standalone |
| `REDIS_CLUSTER` | Redisson Redis cluster |
| `REDIS_SENTINEL` | Redisson Redis sentinel |
| `ZOOKEEPER` | Curator + Spring Integration |
| `NONE` | disabled (always succeeds) |
