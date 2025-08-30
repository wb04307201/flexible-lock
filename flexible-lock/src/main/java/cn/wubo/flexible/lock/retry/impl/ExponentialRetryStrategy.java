package cn.wubo.flexible.lock.retry.impl;

import cn.wubo.flexible.lock.retry.IRetryStrategy;

/**
 * 指数退避重试策略
 */
public class ExponentialRetryStrategy implements IRetryStrategy {

    private static final long MAX_WAIT_TIME = 30 * 1000L; // 最大等待时间30秒

    @Override
    public long calculateWaitTime(long baseWaitTime, int retryCount) {
        long waitTime = (long) (baseWaitTime * Math.pow(2, retryCount - 1));
        return Math.min(waitTime, MAX_WAIT_TIME);
    }

    @Override
    public Boolean supports(String retryStrategy) {
        return "exponential".equals(retryStrategy);
    }
}