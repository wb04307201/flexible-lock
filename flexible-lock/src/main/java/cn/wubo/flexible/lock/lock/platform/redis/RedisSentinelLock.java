package cn.wubo.flexible.lock.lock.platform.redis;

import cn.wubo.flexible.lock.exception.LockRuntimeException;
import cn.wubo.flexible.lock.lock.platform.AbstractLock;
import cn.wubo.flexible.lock.propertes.LockPlatformProperties;
import cn.wubo.flexible.lock.retry.IRetryStrategy;
import jakarta.validation.Validator;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public class RedisSentinelLock extends AbstractLock {

    private RedissonClient client;

    public RedisSentinelLock(LockPlatformProperties properties, Validator validator, IRetryStrategy retryStrategy) {
        super(properties, validator, retryStrategy);
        Config config = new Config();
        config.useSentinelServers()
                .addSentinelAddress((String[]) properties.getAttributes().get("nodes"))
                .setPassword((String) properties.getAttributes().get("password"))
                .setDatabase((Integer) properties.getAttributes().get("database"))
                .setMasterName((String) properties.getAttributes().get("masterName"));
        this.client = Redisson.create(config);
    }


    @Override
    public void validate() {
        super.validate();

        Map<String, Object> attributes = properties.getAttributes();

        Object nodes = attributes.get("nodes");
        if (nodes == null || !(nodes instanceof String[] strs) || strs.length > 0) {
            throw new LockRuntimeException("RedisSentinelLock nodes is null");
        }

        Object password = attributes.get("password");
        if (password == null || !(password instanceof String str2) || str2.isEmpty()) {
            throw new LockRuntimeException("RedisSentinelLock password is null");
        }

        attributes.putIfAbsent("database", 0);

        Object masterName = attributes.get("masterName");
        if (masterName == null || !(masterName instanceof String str3) || str3.isEmpty()) {
            throw new LockRuntimeException("RedisSentinelLock masterName is null");
        }
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
