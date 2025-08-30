package cn.wubo.flexible.lock.retry.impl;

import cn.wubo.flexible.lock.retry.IRetryStrategy;

/**
 * 固定时间间隔重试策略
 */
public class FixedRetryStrategy implements IRetryStrategy {

    @Override
    public long calculateWaitTime(long baseWaitTime, int retryCount) {
        return baseWaitTime;
    }

    @Override
    public Boolean supports(String retryStrategy) {
        return "fixed".equals(retryStrategy);
    }
}
