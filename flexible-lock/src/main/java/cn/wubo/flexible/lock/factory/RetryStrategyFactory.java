package cn.wubo.flexible.lock.factory;

import cn.wubo.flexible.lock.propertes.FlexibleLockProperties;
import cn.wubo.flexible.lock.propertes.RetryStrategyType;
import cn.wubo.flexible.lock.retry.IRetryStrategy;
import cn.wubo.flexible.lock.retry.ExponentialRetryStrategy;
import cn.wubo.flexible.lock.retry.FixedRetryStrategy;
import cn.wubo.flexible.lock.retry.RandomRetryStrategy;

public class RetryStrategyFactory {

    public IRetryStrategy create(FlexibleLockProperties properties) {
        RetryStrategyType type = properties.getRetryStrategyType();
        if (type == null) {
            type = RetryStrategyType.FIXED; // 使用默认策略
        }

        return switch (type) {
            case EXPONENTIAL -> new ExponentialRetryStrategy();
            case RANDOM -> new RandomRetryStrategy();
            default -> new FixedRetryStrategy();
        };
    }
}
