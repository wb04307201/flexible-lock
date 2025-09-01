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

        // 如果 maxWait 不大于0，则直接返回基础等待时间
        if (maxWait <= 0) {
            return baseWaitTime;
        }

        // 生成 0 到 maxWait-1 之间的随机数，确保结果为正数
        long randomValue = Math.abs(random.nextLong()) % maxWait;
        return randomValue + baseWaitTime;
    }

    @Override
    public Boolean supports(String retryStrategy) {
        return "random".equals(retryStrategy);
    }
}
