package cn.wubo.flexible.lock.retry;

/**
 * 重试策略接口
 */
public interface IRetryStrategy {

    /**
     * 计算下次重试的等待时间
     * @param baseWaitTime 基础等待时间
     * @param retryCount 当前重试次数
     * @return 等待时间（毫秒）
     */
    long calculateWaitTime(long baseWaitTime, int retryCount);
}
