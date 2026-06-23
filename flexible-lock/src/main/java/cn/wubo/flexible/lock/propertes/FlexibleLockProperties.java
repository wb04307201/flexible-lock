package cn.wubo.flexible.lock.propertes;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Flexible-Lock starter 的配置属性,绑定到 {@code flexible.lock} 前缀。
 *
 * <p>完整配置示例:
 * <pre>{@code
 * flexible:
 *   lock:
 *     lockType: redis            # 必选,见 {@link LockType}
 *     retryCount: 3              # 全局默认重试次数(N>0 = N+1 次尝试)
 *     waitTime: 3000             # 全局默认等待时间(毫秒)
 *     retryStrategyType: fixed   # 固定 / 指数 / 随机
 *     redis:
 *       host: "redis://127.0.0.1"
 *       port: 6379
 *       password: ""
 *       database: 0
 * }</pre>
 *
 * <p>密码字段(password / digest)在 {@link #toString()} 中被排除,避免日志泄露。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "flexible.lock")
public class FlexibleLockProperties {

    /** 锁后端类型,默认 {@link LockType#STANDALONE}。 */
    private LockType lockType;

    /** 全局重试次数(N>0 = N+1 次尝试;{@code 0} 表示仅尝试一次)。 */
    @Min(0)
    private int retryCount = 3;

    /** 全局等待时间(毫秒)。 */
    @Min(0)
    private long waitTime = 3000L;

    /** 重试策略类型,默认 {@link RetryStrategyType#FIXED}。 */
    private RetryStrategyType retryStrategyType;

    // Redis 单点配置
    private RedisStandaloneProperties redis = new RedisStandaloneProperties();

    // Redis 集群配置
    private RedisClusterProperties redisCluster = new RedisClusterProperties();

    // Redis 哨兵配置
    private RedisSentinelProperties redisSentinel = new RedisSentinelProperties();

    // Zookeeper 配置
    private ZookeeperProperties zookeeper = new ZookeeperProperties();

    /** Redis 单点连接配置。 */
    @Getter
    @Setter
    @ToString(exclude = "password")
    public static class RedisStandaloneProperties {
        /** Redisson 地址,带 {@code redis://} 或 {@code rediss://} 前缀,后接 {@code host:port}。 */
        @NotBlank
        private String host = "redis://127.0.0.1";
        private int port = 6379;
        /** Redis 密码(无密码时为 null 或空字符串)。 */
        private String password;
        private int database = 0;
    }

    /** Redis 集群连接配置。 */
    @Getter
    @Setter
    @ToString(exclude = "password")
    public static class RedisClusterProperties {
        /** 集群节点列表,每个元素带 {@code redis://} 前缀,如 {@code ["redis://127.0.0.1:7000", ...]}。 */
        @NotBlank
        @Size(min = 1)
        private String[] nodes;
        private String password;
    }

    /** Redis 哨兵连接配置。 */
    @Getter
    @Setter
    @ToString(exclude = "password")
    public static class RedisSentinelProperties {
        @NotBlank
        @Size(min = 1)
        private String[] nodes;
        @NotBlank
        private String master;
        private String password;
        private int database = 0;
    }

    /**
     * Zookeeper 连接配置。
     */
    @Getter
    @Setter
    @ToString(exclude = "digest")
    public static class ZookeeperProperties {
        /** ZK 连接串,多个地址用逗号分隔,如 {@code "127.0.0.1:2181,127.0.0.1:2182"}。 */
        @NotBlank
        private String connectString = "127.0.0.1:2181";
        private int maxElapsedTimeMs = 1000;
        private int sleepMsBetweenRetries = 4;
        /**
         * 可选 digest 凭证,格式 {@code "user:password"}。
         * 当 ZK 节点设置了 ACL 时必须填写,否则客户端无权创建临时顺序节点,加锁会失败。
         */
        private String digest;
        /** 锁根节点路径,默认 {@code /locks}。 */
        @NotBlank
        private String root = "/locks";
    }
}
