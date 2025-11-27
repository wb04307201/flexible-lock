package cn.wubo.flexible.lock.propertes;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "flexible.lock")
public class FlexibleLockProperties {

    private LockType lockType;

    private int retryCount = 3;

    private long waitTime = 3000L;

    private RetryStrategyType retryStrategyType;

    // Redis 单点配置
    private RedisStandaloneProperties redis = new RedisStandaloneProperties();

    // Redis 集群配置
    private RedisClusterProperties redisCluster = new RedisClusterProperties();

    // Redis 哨兵配置
    private RedisSentinelProperties redisSentinel = new RedisSentinelProperties();

    // Zookeeper 配置
    private ZookeeperProperties zookeeper = new ZookeeperProperties();

    @Data
    public static class RedisStandaloneProperties {
        @NotBlank
        private String host = "redis://127.0.0.1";
        private int port = 6379;
        private String password;
        private int database = 0;
    }

    @Data
    public static class RedisClusterProperties {
        @NotBlank
        @Size(min = 1)
        private String[] nodes;
        private String password;
    }

    @Data
    public static class RedisSentinelProperties {
        @NotBlank
        @Size(min = 1)
        private String[] nodes;
        @NotBlank
        private String master;
        private String password;
        private int database = 0;
    }

    @Data
    public static class ZookeeperProperties {
        @NotBlank
        private String connectString = "127.0.0.1:2181";
        private int maxElapsedTimeMs = 1000;
        private int sleepMsBetweenRetries = 4;
        private String digest;
        @NotBlank
        private String root = "/locks";
    }
}
