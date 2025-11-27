package cn.wubo.flexible.lock.core;

import cn.wubo.flexible.lock.propertes.FlexibleLockProperties;
import jakarta.validation.Valid;

public abstract class AbstractLock implements ILock {

    protected final FlexibleLockProperties properties;

    protected AbstractLock(@Valid FlexibleLockProperties properties) {
        this.properties = properties;
    }

    @Override
    public Integer getRetryCount() {
        return properties.getRetryCount();
    }

    @Override
    public Long getWaitTime() {
        return properties.getWaitTime();
    }
}
