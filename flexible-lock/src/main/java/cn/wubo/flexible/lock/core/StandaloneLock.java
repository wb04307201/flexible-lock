package cn.wubo.flexible.lock.core;

import cn.wubo.flexible.lock.propertes.FlexibleLockProperties;
import cn.wubo.lock.CHMRLock;

import java.util.concurrent.TimeUnit;

public class StandaloneLock extends AbstractLock {

    private final CHMRLock client;

    public StandaloneLock(FlexibleLockProperties properties) {
        super(properties);
        this.client = new CHMRLock();
    }

    @Override
    public Boolean tryLock(String key) {
        return client.tryLock(key);
    }

    @Override
    public Boolean tryLock(String key, Long time) {
            return client.tryLock(key, time, TimeUnit.MILLISECONDS);
    }


    @Override
    public void unLock(String key) {
        client.unlock(key);
    }
}
