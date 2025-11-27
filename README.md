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
    - 单机ReentrantLock锁
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
    <version>1.1.7</version>
</dependency>
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

### 注解参数说明

| 属性         | 类型     | 默认值 | 描述                    |
|------------|--------|-----|-----------------------|
| key        | String |     | 锁的 key，必填，支持 SpEL 表达式 |
| waitTime   | long   | -1  | 锁的超时时间，-1表示使用全局配置     |
| retryCount | int    | -1  | 重试次数，-1表示使用全局配置       |

### 配置

默认使用单机ReentrantLock锁，重试等待时间策略为fixed，如想使用其他所请修改配置

```yaml
flexible:
  lock:
    retryCount: 3    # 重试次数，0不重试，默认为3
    waitTime: 3000   # 等待时间(毫秒)，默认3000
    #  重试等待时间策略   
    #  fixed(固定，默认)
    #  exponential(指数退避, 重试间隔按指数增长)
    #  random(在基础等待时长内随机)
    retryStrategyType: fixed 
    # Redis单点配置示例
    lockType: redis
    redis:
      host: "redis://127.0.0.1"
      port: 6379
      password: ""
      database: 0
    # Redis集群配置示例
    lockType: redis_cluster
    redisCluster:
      nodes:
        - "redis://127.0.0.1:7000"
        - "redis://127.0.0.1:7001"
      password: ""
    # Redis哨兵配置示例
    lockType: redis_sentinel
    redisSentinel:
      nodes:
        - "redis://127.0.0.1:26379"
        - "redis://127.0.0.1:26380"
      master: "mymaster"
      password: ""
      database: 0
    # Zookeeper配置示例
    lockType: zookeeper
    zookeeper:
      connectString: "127.0.0.1:2181"
      maxElapsedTimeMs: 1000
      sleepMsBetweenRetries: 4
      root: "/locks"
    # 单机ReentrantLock锁配置示例,默认配置
    lockType: standalone

# debug日志
logging:
  level:
    cn:
      wubo:
        flexible:
          lock: debug
```

## 重试策略

1. **Fixed（固定间隔）**：每次重试间隔固定为基础等待时间
2. **Exponential（指数退避）**：重试间隔按指数增长
3. **Random（随机退避）**：在基础等待时间基础上增加随机因素
