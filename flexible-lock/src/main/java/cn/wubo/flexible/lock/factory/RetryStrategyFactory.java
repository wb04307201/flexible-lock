package cn.wubo.flexible.lock.factory;

import cn.wubo.flexible.lock.propertes.FlexibleLockProperties;
import cn.wubo.flexible.lock.propertes.RetryStrategyType;
import cn.wubo.flexible.lock.retry.IRetryStrategy;
import cn.wubo.flexible.lock.retry.ExponentialRetryStrategy;
import cn.wubo.flexible.lock.retry.FixedRetryStrategy;
import cn.wubo.flexible.lock.retry.RandomRetryStrategy;

/**
 * 重试策略工厂：按 {@link FlexibleLockProperties#getRetryStrategyType()} 派生出
 * 对应的 {@link IRetryStrategy} 实例。
 *
 * <p>配置为 {@code null} 或未知 enum 值时默认使用 {@link FixedRetryStrategy}。
 */
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
