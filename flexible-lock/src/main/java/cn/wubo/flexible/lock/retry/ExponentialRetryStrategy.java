package cn.wubo.flexible.lock.retry;

/**
 * 指数退避重试策略
 */
public class ExponentialRetryStrategy implements IRetryStrategy {

        /**
     * 计算等待时间，基于基础等待时间和重试次数进行指数退避计算
     *
     * @param baseWaitTime 基础等待时间，单位为毫秒
     * @param retryCount 重试次数，用于计算指数退避的倍数
     * @return 计算后的等待时间，如果重试次数无效则返回基础等待时间
     */
    @Override
    public long calculateWaitTime(long baseWaitTime, int retryCount) {
        // 检查重试次数是否在有效范围内，超出范围则直接返回基础等待时间
        if (retryCount <= 0 || retryCount > 63) {
            return baseWaitTime;
        }

        // 使用位运算实现指数退避：baseWaitTime * 2^(retryCount-1)
        return baseWaitTime << (retryCount - 1);
    }

}