package cn.wubo.flexible.lock.propertes;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class LockPlatformProperties {
    /**
     * 别名
     */
    @NotBlank
    private String alias;

    // redis单点 redis, redis集群 redis-cluster, redis哨兵 redis-sentinel, zookeeper, standalone
    @Pattern(regexp = "redis|redis-cluster|redis-sentinel|zookeeper|standalone")
    private String locktype;

//    private RedisProperties redis = new RedisProperties();
//
//    private ZookeeperProperties zookeeper = new ZookeeperProperties();

    private Boolean enableLock = true;

    private Integer retryCount = 0;

    private Long waitTime = 3_000L;

    // 固定时间间隔重试 fixed, 指数退避重试 exponential, 随机退避重试 random
    @Pattern(regexp = "fixed|exponential|random")
    private String retryStrategy = "fixed";

    Map<String, Object> attributes = new HashMap<>();

//    @Data
//    public static class RedisProperties {
//        // 单例地址
//        private String address = "localhost:6379";
//        // 密码
//        private String password;
//        // 数据库
//        private Integer database = 0;
//        // 集群、哨兵节点
//        private List<String> nodes;
//        // 烧饼主节点名
//        private String masterName;
//    }

//    @Data
//    public static class ZookeeperProperties {
//        // list of servers to connect to ip:port,ip:port...
//        private String connect;
//        // maxElapsedTimeMs 最大重试时间
//        private Integer maxElapsedTimeMs = 1000;
//        // sleepMsBetweenRetries 每次重试的间隔时间
//        private Integer sleepMsBetweenRetries = 4;
//        // root 锁目录
//        private String root = "/locks";
//    }
}
