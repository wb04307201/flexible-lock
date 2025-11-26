package cn.wubo.flexible.lock.factory;

import cn.wubo.flexible.lock.core.*;
import cn.wubo.flexible.lock.propertes.FlexibleLockProperties;
import cn.wubo.flexible.lock.propertes.LockType;

public class LockFactory {

    public ILock create(FlexibleLockProperties properties) {
        LockType type = properties.getLockType();
        if (type == null) {
            type = LockType.STANDALONE; // 使用默认策略
        }

        return switch (type) {
            case REDIS -> new RedisLock(properties);
            case REDIS_CLUSTER -> new RedisClusterLock(properties);
            case REDIS_SENTINEL -> new RedisSentinelLock(properties);
            case ZOOKEEPER -> new ZookeeperLock(properties);
            case STANDALONE -> new StandaloneLock(properties);
            default -> new NoneLock(properties);
        };
    }
}
