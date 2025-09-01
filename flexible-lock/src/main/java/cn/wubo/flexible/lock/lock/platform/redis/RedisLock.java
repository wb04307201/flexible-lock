package cn.wubo.flexible.lock.lock.platform.redis;

import cn.wubo.flexible.lock.exception.LockRuntimeException;
import cn.wubo.flexible.lock.lock.platform.AbstractLock;
import cn.wubo.flexible.lock.propertes.LockPlatformProperties;
import cn.wubo.flexible.lock.retry.IRetryStrategy;
import cn.wubo.flexible.lock.utils.ValidationUtils;
import jakarta.validation.Validator;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public class RedisLock extends AbstractLock {

    private RedissonClient client;

    public RedisLock(LockPlatformProperties properties, Validator validator, IRetryStrategy retryStrategy) {
        super(properties, validator, retryStrategy);
        Config config = new Config();
        config.useSingleServer()
                .setAddress((String) properties.getAttributes().get("address"))
                .setPassword((String) properties.getAttributes().get("password"))
                .setDatabase((Integer) properties.getAttributes().get("database"));
        this.client = Redisson.create(config);
    }


    @Override
    public void validate() {
        super.validate();

        Map<String, Object> attributes = properties.getAttributes();

        ValidationUtils.validateStringAttribute( attributes,"address","RedisLock password is null");
        ValidationUtils.validateStringAttribute( attributes,"password","RedisLock password is null");

        attributes.putIfAbsent("database", 0);
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
