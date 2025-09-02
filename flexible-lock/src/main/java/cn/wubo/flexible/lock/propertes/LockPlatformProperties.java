package cn.wubo.flexible.lock.propertes;

import cn.wubo.flexible.lock.annotation.LockPlatformPropertiesValidator;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
@LockPlatformPropertiesValidator
public class LockPlatformProperties {
    /**
     * 别名
     */
    @NotBlank
    private String alias;

    // redis单点 redis, redis集群 redis-cluster, redis哨兵 redis-sentinel, zookeeper, standalone
    private String locktype = "standalone";

    private Boolean enableLock = true;

    private Integer retryCount = 0;

    private Long waitTime = 3_000L;

    // 固定时间间隔重试 fixed, 指数退避重试 exponential, 随机退避重试 random
    private String retryStrategy = "fixed";

    Map<String, Object> attributes = new HashMap<>();
}
