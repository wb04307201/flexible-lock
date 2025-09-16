package cn.wubo.flexible.lock.lock.platform.standalone;

import cn.wubo.flexible.lock.lock.platform.AbstractLock;
import cn.wubo.flexible.lock.propertes.LockPlatformProperties;
import cn.wubo.flexible.lock.retry.IRetryStrategy;
import cn.wubo.lock.CHMRLock;
import jakarta.validation.Validator;

import java.util.concurrent.TimeUnit;

public class StandaloneLock extends AbstractLock {

    private final CHMRLock client;

    public StandaloneLock(LockPlatformProperties properties, Validator validator, IRetryStrategy retryStrategy) {
        super(properties, retryStrategy);
        this.client = new CHMRLock();
    }

    @Override
    public Boolean tryLock(String key) {
        return client.tryLock(key);
    }

    @Override
    public Boolean tryLock(String key, Long time, TimeUnit unit) {
            return client.tryLock(key, time, unit);
    }


    @Override
    public void unLock(String key) {
        client.unlock(key);
    }
}
