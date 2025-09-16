package cn.wubo.flexible.lock.factory.impl;

import cn.wubo.flexible.lock.factory.IFactory;
import cn.wubo.flexible.lock.lock.ILock;
import cn.wubo.flexible.lock.lock.platform.redis.RedisClusterLock;
import cn.wubo.flexible.lock.propertes.LockPlatformProperties;
import cn.wubo.flexible.lock.retry.IRetryStrategy;

public class RedisClusterFactory implements IFactory {
    @Override
    public Boolean supports(String locktype) {
        return "redis-cluster".equals(locktype);
    }

    @Override
    public ILock create(LockPlatformProperties properties , IRetryStrategy retryStrategy) {
        return new RedisClusterLock(properties, retryStrategy);
    }
}
