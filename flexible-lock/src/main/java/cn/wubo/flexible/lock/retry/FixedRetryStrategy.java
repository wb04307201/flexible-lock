package cn.wubo.flexible.lock.retry;

/**
 * 固定时间间隔重试策略
 */
public class FixedRetryStrategy implements IRetryStrategy {

    @Override
    public long calculateWaitTime(long baseWaitTime, int retryCount) {
        return baseWaitTime;
    }
}
