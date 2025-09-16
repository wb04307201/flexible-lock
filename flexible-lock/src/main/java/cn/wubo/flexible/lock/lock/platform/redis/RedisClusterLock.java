package cn.wubo.flexible.lock.lock.platform.redis;

import cn.wubo.flexible.lock.exception.LockRuntimeException;
import cn.wubo.flexible.lock.lock.platform.AbstractLock;
import cn.wubo.flexible.lock.propertes.LockPlatformProperties;
import cn.wubo.flexible.lock.retry.IRetryStrategy;
import jakarta.validation.Valid;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

import java.util.concurrent.TimeUnit;

public class RedisClusterLock extends AbstractLock {

    private RedissonClient client;

    public RedisClusterLock(@Valid LockPlatformProperties properties, IRetryStrategy retryStrategy) {
        super(properties, retryStrategy);
        Config config = new Config();
        config.useClusterServers()
                .addNodeAddress((String[]) properties.getAttributes().get("nodes"))
                .setPassword((String) properties.getAttributes().get("password"));
        this.client = Redisson.create(config);
    }

    @Override
    public Boolean tryLock(String key) {
        return client.getLock(key).tryLock();
    }

    @Override
    public Boolean tryLock(String key, Long time, TimeUnit unit) {
        try {
            return client.getLock(key).tryLock(time, unit);
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
