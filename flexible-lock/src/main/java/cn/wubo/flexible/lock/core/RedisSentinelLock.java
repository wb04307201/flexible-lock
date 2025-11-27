package cn.wubo.flexible.lock.core;

import cn.wubo.flexible.lock.exception.LockRuntimeException;
import cn.wubo.flexible.lock.propertes.FlexibleLockProperties;
import jakarta.validation.Valid;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

import java.util.concurrent.TimeUnit;

public class RedisSentinelLock extends AbstractLock {

    private RedissonClient client;

    public RedisSentinelLock(FlexibleLockProperties properties) {
        super(properties);
        this.client = create(properties.getRedisSentinel());
    }

    private RedissonClient create(@Valid  FlexibleLockProperties.RedisSentinelProperties properties) {
        Config config = new Config();
        config.useSentinelServers()
                .addSentinelAddress(properties.getNodes())
                .setPassword(properties.getPassword())
                .setDatabase(properties.getDatabase())
                .setMasterName(properties.getMaster());
        return Redisson.create(config);
    }

    @Override
    public Boolean tryLock(String key) {
        return client.getLock(key).tryLock();
    }

    @Override
    public Boolean tryLock(String key, Long time) {
        try {
            return client.getLock(key).tryLock(time, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LockRuntimeException(e);
        }
    }


    @Override
    public void unLock(String key) {
        client.getLock(key).unlock();
    }
}
