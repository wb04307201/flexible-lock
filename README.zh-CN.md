# Flexible Lock 灵锁

<div align="right">
  <a href="README.md">English</a> | 中文
</div>

> 一个基于Spring Boot的锁starter，提供了统一的锁接口和多种实现方式，包括Redis单点、Redis集群、Redis哨兵、Zookeeper、本地锁以及用于测试场景的 no-op。通过简单的配置即可在项目中使用锁功能。


![Maven Central](https://img.shields.io/maven-central/v/io.github.wb04307201/flexible-lock-spring-boot-starter?style=flat-square)
[![star](https://gitee.com/wb04307201/flexible-lock/badge/star.svg?theme=dark)](https://gitee.com/wb04307201/flexible-lock)
[![fork](https://gitee.com/wb04307201/flexible-lock/badge/fork.svg?theme=dark)](https://gitee.com/wb04307201/flexible-lock)
[![star](https://img.shields.io/github/stars/wb04307201/flexible-lock)](https://github.com/wb04307201/flexible-lock)
[![fork](https://img.shields.io/github/forks/wb04307201/flexible-lock)](https://github.com/wb04307201/flexible-lock)  
![MIT](https://img.shields.io/badge/License-Apache2.0-blue.svg) ![JDK](https://img.shields.io/badge/JDK-17+-green.svg) ![SpringBoot](https://img.shields.io/badge/Srping%20Boot-3+-green.svg)

## 特性

- 支持多种锁实现:
    - Redis单点模式
    - Redis集群模式
    - Redis哨兵模式
    - Zookeeper分布式锁（支持 digest 认证）
    - 单机 `ReentrantLock` 锁
    - **`none`** —— 空操作锁（始终成功），用于在测试或预发环境关闭锁功能
- 多种重试策略:
    - 固定时间间隔重试
    - 指数退避重试
    - 随机退避重试
- 基于注解的锁机制
- 支持SpEL表达式定义锁key（支持 `@beanName` 引用 Spring Bean）
- 可配置的重试次数和等待时间
- `retryCount = N` 表示**在初次尝试后再重试 N 次**（即总共 `N + 1` 次尝试，`N = 0` 表示仅尝试一次不重试）
- 所有 Redis / Zookeeper 连接池在 Spring 容器关闭时会被自动释放

## 安装

### 添加依赖
```xml
<dependency>
    <groupId>io.github.wb04307201.flexible-lock</groupId>
    <artifactId>flexible-lock-spring-boot-starter</artifactId>
    <version>1.1.9</version>
</dependency>
```

### 增加 -Parameters 启动参数以支持 SpEL 表达式
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

## 使用方法

### 基本使用

在需要加锁的方法上添加`@Locking`注解：

```java
@Service
public class BusinessService {

    @Locking(key = "#userId + '-' + #orderId")
    public void processOrder(String userId, String orderId) {
        // 业务逻辑
    }
}
```

> ⚠️ **自调用注意**：`@Locking` 是基于 Spring AOP 代理实现的。**同一个 Bean 内部的方法调用另一个 `@Locking` 标注的方法时（`this.xxx()`），不会触发代理，加锁会失效**。
> 解决方法：把被调用方法抽到独立的 Bean 里、通过 `@Autowired` 注入自身、或者改用 AspectJ weaving 模式。

### 注解参数说明

| 属性         | 类型     | 默认值 | 说明                                       |
|------------|--------|-----|------------------------------------------|
| key        | String |     | 锁的 key，必填，支持 SpEL 表达式（支持 `@beanName` 引用 Bean） |
| waitTime   | long   | -1  | 锁的超时时间（毫秒），-1表示使用全局配置                |
| retryCount | int    | -1  | 在初次尝试后的**重试次数**，-1 表示使用全局配置；0 表示仅尝试一次不重试 |

`retryCount` 和 `waitTime` 在启动时会被 `@Min(0)` 校验。除了 `-1` 这个"使用全局配置"的哨兵值以外，其他负数都会被 Spring Boot 配置校验拒绝。

### 类级注解

`@Locking` 也可以放在类上，对类内所有方法生效。方法上若同时存在 `@Locking`，方法级的注解会**覆盖**类级的注解。

```java
@Locking(key = "'order-' + #orderId", waitTime = 5000)
@Service
public class OrderService {

    // 使用类级的 key / waitTime / retryCount
    public void create(Order order) { ... }

    // 方法级覆盖类级：不同的 key，且不重试
    @Locking(key = "'order-strict-' + #orderId", retryCount = 0)
    public void forceUpdate(Order order) { ... }

    // 完全不加锁——既不受类级影响，也不受方法级影响
    public Order find(String orderId) { ... }
}
```

### 失败行为

当 `retryCount + 1` 次尝试都未能获取锁时，切面会抛出 `LockRuntimeException`
（`RuntimeException` 的子类）。异常消息里会包含锁的 key 方便排查。如果希望优雅降级
（例如返回 503），可以在业务边界捕获：

```java
try {
    orderService.create(order);
} catch (LockRuntimeException e) {
    // e.getMessage() 包含未能获取的 key
    return ResponseEntity.status(503).build();
}
```

传输层错误（如 Redis 连接被拒绝）会**短路**整个重试流程，把原始异常作为
`LockRuntimeException` 的 `cause` 直接抛出——在已经断开的连接上反复重试没有意义，
直接快速失败并保留根本原因更利于排障。

### SpEL 上下文变量

SpEL key 表达式在求值时可以使用以下变量：

| 变量          | 类型                          | 说明                                       |
|-------------|-----------------------------|------------------------------------------|
| `#method`   | `java.lang.reflect.Method`  | 当前被加锁的方法                                  |
| `#<参数名>`    | 方法参数的类型                     | 按参数名绑定（要求编译时启用 `<parameters>true</parameters>`） |
| `@beanName` | 任意已注册的 Spring Bean        | 通过 `BeanFactoryResolver` 解析               |

使用 `#method` 的例子：

```java
@Locking(key = "#method.declaringClass.simpleName + ':' + #method.name")
public void foo() { ... }
```

**安全提示**：SpEL key 运行在 `StandardEvaluationContext` 中，意味着 `T(...)` 类型
引用和任意方法调用都可用。但请把 SpEL 字符串视为开发者在编译期写的注解参数，而不是
运行期的用户输入——可能的安全隐患是你写错了表达式，而不是来自外部的注入攻击。
**不要**通过字符串拼接把不可信输入拼到 SpEL 里。

### 配置

默认使用单机 `ReentrantLock` 锁，重试策略为 `fixed`，其他配置按需切换。

#### 单机 ReentrantLock（默认）
```yaml
flexible:
  lock:
    retryCount: 3
    waitTime: 3000
    retryStrategyType: fixed
    lockType: standalone
```

#### Redis 单点
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

#### Redis 集群
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

#### Redis 哨兵
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

#### Zookeeper（可选 digest 认证）
```yaml
flexible:
  lock:
    lockType: zookeeper
    zookeeper:
      connectString: "127.0.0.1:2181"
      maxElapsedTimeMs: 1000
      sleepMsBetweenRetries: 4
      root: "/locks"
      # 可选：当 ZK 节点设置了 ACL 时，必须填写用户名:密码
      # 否则客户端没有权限在 /locks 下创建临时顺序节点，加锁会失败
      digest: "user:password"
```

#### 关闭锁功能（none）
```yaml
flexible:
  lock:
    lockType: none
```
`none` 后端的 `tryLock` 始终返回 `true`，`@Locking` 注解变成空操作。适合本地开发、
集成测试或预发布环境，既能保留 `@Locking` 注解的存在，又不产生锁开销。

#### debug日志
```yaml
logging:
  level:
    cn:
      wubo:
        flexible:
          lock: debug
```

## 重试策略

| 策略 | 行为 |
|----|----|
| `fixed` | 每次重试等待 `waitTime` 毫秒（默认） |
| `exponential` | 等待时间按指数增长：`waitTime * 2^(retryCount - 1)`，溢出保护 |
| `random` | 在 `[waitTime, waitTime * (retryCount + 1))` 区间内随机等待 |

## 自定义连接池

如果你的应用已经在别处管理 `RedissonClient` 或 `CuratorFramework`，只需把它们注册成 Spring Bean，starter 就会复用而不再创建自己的。starter 内部创建的客户端会在 Spring 容器关闭时自动释放。

```java
@Bean(destroyMethod = "shutdown")
public RedissonClient redissonClient() {
    Config config = new Config();
    config.useSingleServer().setAddress("redis://127.0.0.1:6379");
    return Redisson.create(config);
}
```

## 试一试：演示控制台

仓库自带一个可运行的演示应用，路径在 `flexible-lock-test/`。它提供一个 Thymeleaf 控制台，覆盖了 starter 支持的所有 `@Locking` 写法——可以用来快速体验、对比后端、或者向同事演示。

```bash
cd flexible-lock-test
mvn spring-boot:run
# 然后打开 http://127.0.0.1:8089/
```

页面分三个面板：

- **DemoService** — 五个方法分别演示一种 `@Locking` 写法：简单 key、显式 `retryCount`/`waitTime`、Bean 引用（`@systemClock`）、`#method` SpEL。
- **OrderService** — 类级 `@Locking` 与方法级 override 并列展示。对 `forceUpdate` 发起并发请求，可以观察到方法级 override 在 `~50ms` 内快速失败，而类级默认配置会继续等待。
- **实时日志** — 每次调用都会被记录（key、状态、耗时、消息），页面每秒轮询一次，可以实时观察一次并发的执行结果。

每行有 **调用** 按钮（单次调用）和 **并发** 按钮（发起 N 个并发请求，查看谁拿到锁、谁抛出 `LockRuntimeException`）。

![控制台 — 并发请求后的实时日志](docs/screenshots/console-burst.png)

默认情况下，演示使用进程内 `STANDALONE` 后端。要切到 Redis 试试：

```bash
docker run -d --name flexible-lock-redis -p 6379:6379 redis:6-alpine
mvn spring-boot:run -Dspring-boot.run.arguments="\
  --flexible.lock.lockType=redis \
  --flexible.lock.redis.host=redis://127.0.0.1 \
  --flexible.lock.redis.port=6379"
# 用完后记得停止并删除容器
```

页面顶部的徽章会反映当前激活的后端：

| 徽章 | 后端 |
|---|---|
| `STANDALONE (default)` | 进程内 `ReentrantLock` |
| `REDIS` | Redisson Redis 单机 |
| `REDIS_CLUSTER` | Redisson Redis 集群 |
| `REDIS_SENTINEL` | Redisson Redis 哨兵 |
| `ZOOKEEPER` | Curator + Spring Integration |
| `NONE` | 关闭锁（始终成功） |
