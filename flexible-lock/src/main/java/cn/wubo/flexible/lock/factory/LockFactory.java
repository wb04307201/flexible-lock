package cn.wubo.flexible.lock.factory;

import cn.wubo.flexible.lock.core.*;
import cn.wubo.flexible.lock.propertes.FlexibleLockProperties;
import cn.wubo.flexible.lock.propertes.LockType;

/**
 * 锁后端工厂：按 {@link FlexibleLockProperties#getLockType()} 派生出对应的
 * {@link ILock} 实例。
 *
 * <p>当配置为 {@code null} 时默认使用 {@link StandaloneLock}（无需任何外部依赖）。
 * 未知 enum 值或新增类型未实现前，安全回退到 {@link NoneLock}（始终成功，便于灰度）。
 */
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
