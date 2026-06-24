package cn.wubo.flexible.lock.propertes;

/**
 * 锁后端类型,通过 {@code flexible.lock.lockType} 配置选择。
 *
 * <p>每个值对应 {@code cn.wubo.flexible.lock.core} 包下的一个具体实现。
 * YAML 中可使用驼峰或连字符命名(如 {@code REDIS_CLUSTER} → {@code redis_cluster}),
 * Spring Boot 配置属性绑定大小写不敏感。
 */
public enum LockType {

    /** Redis 单点模式(Redisson single-server)。 */
    REDIS,

    /** Redis 集群模式(Redisson cluster)。 */
    REDIS_CLUSTER,

    /** Redis 哨兵模式(Redisson sentinel)。 */
    REDIS_SENTINEL,

    /** Zookeeper 分布式锁(基于 Spring Integration + Curator,支持 digest ACL)。 */
    ZOOKEEPER,

    /** 单机 {@link java.util.concurrent.locks.ReentrantLock} 后端(默认)。 */
    STANDALONE,

    /** 空操作锁:无论何时都返回成功,适合测试或预发布环境关闭锁功能。 */
    NONE
}
