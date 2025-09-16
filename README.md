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
    <version>1.1.6</version>
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

## 配置说明

### LockPlatformProperties

| 属性名           | 类型                  | 默认值          | 说明                                                              |
|---------------|---------------------|--------------|-----------------------------------------------------------------|
| alias         | String              | -            | 锁的别名，必须唯一                                                       |
| locktype      | String              | "standalone" | 锁类型：redis, redis-cluster, redis-sentinel, zookeeper, standalone |
| enableLock    | Boolean             | true         | 是否启用锁                                                           |
| retryCount    | Integer             | 0            | 重试次数，-1表示无限重试                                                   |
| waitTime      | Long                | 3000         | 基础等待时间（毫秒）                                                      |
| retryStrategy | String              | "fixed"      | 重试策略：fixed, exponential, random                                 |
| attributes    | Map<String, Object> | -            | 各种锁类型的具体配置参数                                                    |

### 不同锁类型的 attributes 配置

#### Redis 单点模式
```yaml
attributes:
  address: "redis://127.0.0.1:6379"  # Redis 地址
  password: ""                       # Redis 密码
  database: 0                        # 数据库索引
```


#### Redis 集群模式
```yaml
attributes:
  nodes:                             # Redis 集群节点列表
    - "redis://127.0.0.1:7000"
    - "redis://127.0.0.1:7001"
  password: ""                       # Redis 密码
```


#### Redis 哨兵模式
```yaml
attributes:
  nodes:                             # Redis 哨兵节点列表
    - "redis://127.0.0.1:26379"
    - "redis://127.0.0.1:26380"
  password: ""                       # Redis 密码
  masterName: "mymaster"             # 主节点名称
  database: 0                        # 数据库索引
```


#### Zookeeper 模式
```yaml
attributes:
  connect: "127.0.0.1:2181"          # Zookeeper 连接字符串
  maxElapsedTimeMs: 1000             # 最大等待时间（毫秒）
  sleepMsBetweenRetries: 4           # 重试间隔（毫秒）
  root: "/locks"                     # 锁的根路径
```


#### 单机模式
```yaml
# 单机模式不需要额外的 attributes 配置
```

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

| 属性    | 类型       | 默认值     | 描述                 |
|-------|----------|---------|--------------------|
| alias | String   | -       | 锁的别名               |
| keys  | String[] | {""}    | 锁的 key，支持 SpEL 表达式 |
| time  | long     | -1      | 锁的超时时间             |
| unit  | TimeUnit | SECONDS | 时间单位               |

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

1. **Fixed（固定间隔）**：每次重试间隔固定为基础等待时间
2. **Exponential（指数退避）**：重试间隔按指数增长，最大不超过30秒
3. **Random（随机退避）**：在基础等待时间基础上增加随机因素

## 自定义扩展

### 添加新的锁实现

1. 实现[ILock](flexible-lock\src\main\java\cn\wubo\flexible\lock\lock\ILock.java#L4-L50)接口或继承[AbstractLock](flexible-lock\src\main\java\cn\wubo\flexible\lock\lock\platform\AbstractLock.java#L8-L45)类
2. 实现[IFactory](flexible-lock\src\main\java\cn\wubo\flexible\lock\factory\IFactory.java#L7-L12)接口创建锁实例
3. 在[LockConfiguration](flexible-lock-spring-boot-autoconfigure\src\main\java\cn\wubo\flexible\lock\autoconfigure\LockConfiguration.java#L22-L86)中注册工厂

### 添加新的重试策略

1. 实现[IRetryStrategy](flexible-lock\src\main\java\cn\wubo\flexible\lock\retry\IRetryStrategy.java#L5-L20)接口
2. 在[LockConfiguration](flexible-lock-spring-boot-autoconfigure\src\main\java\cn\wubo\flexible\lock\autoconfigure\LockConfiguration.java#L22-L86)中注册策略


## 注意事项

1. 确保配置的 alias 唯一且在使用时正确引用
2. 根据实际业务场景选择合适的锁类型和重试策略
3. 合理设置 retryCount 和 waitTime，避免资源浪费或业务阻塞
4. 使用 SPEL 表达式时确保参数名称正确
