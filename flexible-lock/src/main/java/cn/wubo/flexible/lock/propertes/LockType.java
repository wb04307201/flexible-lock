package cn.wubo.flexible.lock.propertes;

public enum LockType {

    // redis单点 redis, redis集群 redis-cluster, redis哨兵 redis-sentinel, zookeeper, standalone
    REDIS,

    REDIS_CLUSTER,

    REDIS_SENTINEL,

    ZOOKEEPER,

    STANDALONE,

    NONE
}
