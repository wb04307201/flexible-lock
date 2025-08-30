# Flexible Lock 灵锁

> 一个基于Spring Boot的锁starter，提供了统一的锁接口和多种实现方式，包括Redis单点、Redis集群、Redis哨兵、Zookeeper和本地锁。通过简单的配置即可在项目中使用锁功能。


[![](https://jitpack.io/v/com.gitee.wb04307201/flexible-lock.svg)](https://jitpack.io/#com.gitee.wb04307201/flexible-lock)
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
    - Zookeeper分布式锁
    - 本地内存锁(Standalone)
- 多种重试策略:
    - 固定时间间隔重试
    - 指数退避重试
    - 随机退避重试
- 基于注解的锁机制
- 支持SpEL表达式定义锁key
- 可配置的重试次数和等待时间

## 引入

### 增加 JitPack 仓库
```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```

### 引入jar
```xml
<dependency>
    <groupId>com.gitee.wb04307201.flexible-lock</groupId>
    <artifactId>flexible-lock-spring-boot-starter</artifactId>
    <version>1.1.4</version>
</dependency>
```

### 配置
```yaml
lock:
  props:
    - alias: "redis-lock"  # 锁别名
      locktype: "redis"    # 锁类型: redis, redis-cluster, redis-sentinel, zookeeper, standalone
      retryCount: 3        # 重试次数，默认0(不重试)，-1表示无限重试
      waitTime: 3000       # 等待时间(毫秒)，默认3000
      retryStrategy: "exponential"  # 重试策略: fixed(固定), exponential(指数退避), random(随机)
      attributes:          # 根据不同锁类型配置相应参数
        address: "redis://127.0.0.1:6379"
        password: "your_password"
        database: 0
    - alias: "zk-lock"
      locktype: "zookeeper"
      retryCount: 5
      waitTime: 5000
      retryStrategy: "fixed"
      attributes:
        connect: "127.0.0.1:2181"
        root: "/locks"
```

### 锁类型配置参数

1. **Redis单点模式** (`locktype: redis`):
    - `address`: Redis地址，如 "redis://127.0.0.1:6379"
    - `password`: Redis密码
    - `database`: 数据库索引，默认0

2. **Redis集群模式** (`locktype: redis-cluster`):
    - `nodes`: 节点地址数组
    - `password`: Redis密码

3. **Redis哨兵模式** (`locktype: redis-sentinel`):
    - `nodes`: 哨兵节点地址数组
    - `password`: Redis密码
    - `database`: 数据库索引，默认0
    - `masterName`: 主节点名称

4. **Zookeeper** (`locktype: zookeeper`):
    - `connect`: Zookeeper连接地址
    - `root`: 锁目录，默认"/locks"
    - `maxElapsedTimeMs`: 最大重试时间，默认1000ms
    - `sleepMsBetweenRetries`: 重试间隔时间，默认4ms

5. **本地锁** (`locktype: standalone`):
    - 无需额外配置

## 使用方法

### 基本使用

在需要加锁的方法上添加`@Locking`注解：

```java
@Service
public class BusinessService {
    
    @Locking(alias = "redis-lock", keys = {"#userId", "#orderId"})
    public void processOrder(String userId, String orderId) {
        // 业务逻辑
    }
}
```

### 注解参数说明

- [alias](flexible-lock\src\main\java\cn\wubo\flexible\lock\annotation\Locking.java#L13-L13): 锁的别名，对应配置中的alias
- [keys](flexible-lock\src\main\java\cn\wubo\flexible\lock\annotation\Locking.java#L19-L19): 锁的key，支持SpEL表达式，可以引用方法参数
- [time](flexible-lock\src\main\java\cn\wubo\flexible\lock\annotation\Locking.java#L21-L21): 超时时间，默认-1(不超时)
- [unit](flexible-lock\src\main\java\cn\wubo\flexible\lock\annotation\Locking.java#L23-L23): 时间单位，默认SECONDS

### SpEL表达式示例

```java
@Locking(alias = "redis-lock", keys = {"#user.id", "#order.orderNo"})
public void process(User user, Order order) {
    // ...
}

@Locking(alias = "redis-lock", keys = {"'static-key'", "#param"})
public void processWithStaticKey(String param) {
    // ...
}
```

## 重试策略

1. **Fixed(固定间隔)**: 每次重试间隔固定时间
2. **Exponential(指数退避)**: 重试间隔按指数增长
3. **Random(随机退避)**: 在基础等待时间基础上增加随机因素

## 自定义扩展

### 添加新的锁实现

1. 实现[ILock](flexible-lock\src\main\java\cn\wubo\flexible\lock\lock\ILock.java#L4-L50)接口或继承[AbstractLock](flexible-lock\src\main\java\cn\wubo\flexible\lock\lock\platform\AbstractLock.java#L8-L45)类
2. 实现[IFactory](flexible-lock\src\main\java\cn\wubo\flexible\lock\factory\IFactory.java#L7-L12)接口创建锁实例
3. 在[LockConfiguration](flexible-lock-spring-boot-autoconfigure\src\main\java\cn\wubo\flexible\lock\autoconfigure\LockConfiguration.java#L22-L86)中注册工厂

### 添加新的重试策略

1. 实现[IRetryStrategy](flexible-lock\src\main\java\cn\wubo\flexible\lock\retry\IRetryStrategy.java#L5-L20)接口
2. 在[LockConfiguration](flexible-lock-spring-boot-autoconfigure\src\main\java\cn\wubo\flexible\lock\autoconfigure\LockConfiguration.java#L22-L86)中注册策略


## 注意事项

1. 确保配置的锁别名与注解中的alias一致
2. 合理设置重试次数和等待时间，避免资源浪费
3. 根据业务场景选择合适的锁类型和重试策略
4. 在集群环境中使用分布式锁实现(zookeeper, redis等)
