package cn.wubo.flexible.lock.retry;

import java.util.Random;

/**
 * 随机退避重试策略
 */
public class RandomRetryStrategy implements IRetryStrategy {

    private final Random random = new Random();

        /**
     * 计算等待时间，基于基础等待时间和重试次数生成随机等待时间
     *
     * @param baseWaitTime 基础等待时间，单位为毫秒
     * @param retryCount 重试次数
     * @return 计算后的等待时间，单位为毫秒
     */
    @Override
    public long calculateWaitTime(long baseWaitTime, int retryCount) {
        // 计算最大等待时间
        long maxWait = baseWaitTime * retryCount;

        // 如果最大等待时间小于等于0，则直接返回基础等待时间
        if (maxWait <= 0) {
            return baseWaitTime;
        }

        // 生成随机等待时间，范围在[baseWaitTime, baseWaitTime + maxWait)之间
        long randomValue = random.nextLong((int) maxWait);
        return randomValue + baseWaitTime;
    }

}
