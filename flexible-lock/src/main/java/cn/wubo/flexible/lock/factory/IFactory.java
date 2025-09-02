package cn.wubo.flexible.lock.factory;

import cn.wubo.flexible.lock.lock.ILock;
import cn.wubo.flexible.lock.propertes.LockPlatformProperties;
import cn.wubo.flexible.lock.retry.IRetryStrategy;
import jakarta.validation.Validator;

public interface IFactory {

    Boolean supports(String locktype);

    ILock create(LockPlatformProperties properties, IRetryStrategy retryStrategy, Validator validator);
}
