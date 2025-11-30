# Flexible Lock

<div align="right">
  English | <a href="README.zh-CN.md">中文</a>
</div>

> A Spring Boot-based lock starter that provides a unified lock interface with multiple implementations including Redis standalone, Redis cluster, Redis sentinel, Zookeeper, and local locks. You can easily use locking functionality in your project through simple configuration.

[![](https://jitpack.io/v/com.gitee.wb04307201/flexible-lock.svg)](https://jitpack.io/#com.gitee.wb04307201/flexible-lock)
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
    - Zookeeper distributed lock
    - Standalone ReentrantLock
- Multiple retry strategies:
    - Fixed interval retry
    - Exponential backoff retry
    - Random backoff retry
- Annotation-based locking mechanism
- Support for SpEL expressions to define lock keys
- Configurable retry count and waiting time

## Installation

### Add JitPack Repository
```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```


### Add Dependency
```xml
<dependency>
    <groupId>com.gitee.wb04307201.flexible-lock</groupId>
    <artifactId>flexible-lock-spring-boot-starter</artifactId>
    <version>1.1.8</version>
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


### Annotation Parameters

| Property   | Type   | Default | Description                                      |
|------------|--------|---------|--------------------------------------------------|
| key        | String |         | Lock key, required, supports SpEL expressions    |
| waitTime   | long   | -1      | Lock timeout, -1 means using global configuration |
| retryCount | int    | -1      | Retry count, -1 means using global configuration  |

### Configuration

By default, standalone ReentrantLock is used with fixed retry strategy. To use other lock types, modify the configuration accordingly.

```yaml
flexible:
  lock:
    retryCount: 3    # Number of retries, 0 means no retry, default is 3
    waitTime: 3000   # Wait time (milliseconds), default is 3000
    # Retry strategy type
    # fixed (fixed, default)
    # exponential (exponential backoff, retry interval increases exponentially)
    # random (random within base wait time)
    retryStrategyType: fixed 
    # Redis standalone configuration example
    lockType: redis
    redis:
      host: "redis://127.0.0.1"
      port: 6379
      password: ""
      database: 0
    # Redis cluster configuration example
    lockType: redis_cluster
    redisCluster:
      nodes:
        - "redis://127.0.0.1:7000"
        - "redis://127.0.0.1:7001"
      password: ""
    # Redis sentinel configuration example
    lockType: redis_sentinel
    redisSentinel:
      nodes:
        - "redis://127.0.0.1:26379"
        - "redis://127.0.0.1:26380"
      master: "mymaster"
      password: ""
      database: 0
    # Zookeeper configuration example
    lockType: zookeeper
    zookeeper:
      connectString: "127.0.0.1:2181"
      maxElapsedTimeMs: 1000
      sleepMsBetweenRetries: 4
      root: "/locks"
    # Standalone ReentrantLock configuration example, default configuration
    lockType: standalone

# Debug logging
logging:
  level:
    cn:
      wubo:
        flexible:
          lock: debug
```


## Retry Strategies

1. **Fixed**: Each retry has a fixed interval equal to the base wait time
2. **Exponential**: Retry intervals increase exponentially
3. **Random**: Adds randomness to the base wait time