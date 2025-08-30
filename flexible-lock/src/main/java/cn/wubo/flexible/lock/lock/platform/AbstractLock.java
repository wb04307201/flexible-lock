package cn.wubo.flexible.lock.lock.platform;

import cn.wubo.flexible.lock.lock.ILock;
import cn.wubo.flexible.lock.propertes.LockPlatformProperties;
import cn.wubo.flexible.lock.retry.IRetryStrategy;
import cn.wubo.flexible.lock.utils.ValidationUtils;
import jakarta.validation.Validator;

public abstract class AbstractLock implements ILock {

    protected final LockPlatformProperties properties;
    private final Validator validator;
    private final IRetryStrategy retryStrategy;

    protected AbstractLock(LockPlatformProperties properties, Validator validator, IRetryStrategy retryStrategy) {
        this.properties = properties;
        this.validator = validator;
        this.retryStrategy = retryStrategy;
        validate();
    }

    @Override
    public Boolean supportsAlias(String alias) {
        return properties.getAlias().equals(alias);
    }

    @Override
    public void validate() {
        ValidationUtils.validate(this.validator, this.properties);
    }

    @Override
    public Integer getRetryCount() {
        return properties.getRetryCount();
    }

    @Override
    public Long getWaitTime() {
        return properties.getWaitTime();
    }

    @Override
    public long calculateBackoffTime(int retryCount) {
        return retryStrategy.calculateWaitTime(getWaitTime(), retryCount);
    }
}
