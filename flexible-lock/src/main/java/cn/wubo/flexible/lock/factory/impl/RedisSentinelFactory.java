package cn.wubo.flexible.lock.factory.impl;

import cn.wubo.flexible.lock.factory.IFactory;
import cn.wubo.flexible.lock.lock.ILock;
import cn.wubo.flexible.lock.lock.platform.redis.RedisSentinelLock;
import cn.wubo.flexible.lock.propertes.LockPlatformProperties;
import cn.wubo.flexible.lock.retry.IRetryStrategy;

public class RedisSentinelFactory implements IFactory {
    @Override
    public Boolean supports(String locktype) {
        return "redis-sentinel".equals(locktype);
    }

    @Override
    public ILock create(LockPlatformProperties properties, IRetryStrategy retryStrategy) {
        return new RedisSentinelLock(properties, retryStrategy);
    }
}
