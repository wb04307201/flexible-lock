package cn.wubo.flexible.lock.retry.impl;

import cn.wubo.flexible.lock.retry.IRetryStrategy;

import java.util.Random;

/**
 * 随机退避重试策略
 */
public class RandomRetryStrategy implements IRetryStrategy {

    private static final long MAX_WAIT_TIME = 30 * 1000L;
    private final Random random = new Random();

    @Override
    public long calculateWaitTime(long baseWaitTime, int retryCount) {
        // 在基础等待时间的基础上增加随机因素
        long maxWait = Math.min(baseWaitTime * retryCount, MAX_WAIT_TIME);
        return random.nextLong() % maxWait + baseWaitTime;
    }

    @Override
    public Boolean supports(String retryStrategy) {
        return "random".equals(retryStrategy);
    }
}
