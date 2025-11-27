package cn.wubo.flexible.lock.core;

import cn.wubo.flexible.lock.propertes.FlexibleLockProperties;

public class NoneLock extends AbstractLock{

    public NoneLock(FlexibleLockProperties properties) {
        super(properties);
    }

    @Override
    public Boolean tryLock(String key) {
        return true;
    }

    @Override
    public Boolean tryLock(String key, Long waitTime) {
        return true;
    }

    @Override
    public void unLock(String key) {

    }
}
