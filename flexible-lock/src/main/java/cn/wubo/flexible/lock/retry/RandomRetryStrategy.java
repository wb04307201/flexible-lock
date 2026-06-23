package cn.wubo.flexible.lock.retry;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 随机退避重试策略
 */
public class RandomRetryStrategy implements IRetryStrategy {

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

        // 在 [baseWaitTime, baseWaitTime + maxWait) 区间内取随机值。
        // 用 ThreadLocalRandom.current() 而非共享 Random，避免多线程争用
        // 同一个 CAS seed；用 long 而非 int 参数，避免大 baseWaitTime × retryCount
        // 超过 Integer.MAX_VALUE 时被截断成静默错误。
        long randomValue = ThreadLocalRandom.current().nextLong(maxWait);
        return randomValue + baseWaitTime;
    }

}
