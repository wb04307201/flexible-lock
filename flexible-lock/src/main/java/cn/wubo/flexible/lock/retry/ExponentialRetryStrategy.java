package cn.wubo.flexible.lock.retry;

/**
 * 指数退避重试策略
 */
public class ExponentialRetryStrategy implements IRetryStrategy {

    /**
     * 位移上限：{@code 1L << n} 在 n >= 63 时会回绕为负数或零，因此拒绝超过 63 的 shift 输入。
     * 实际取值 30 进一步留出 {@code baseWaitTime * shift} 的乘法溢出空间。
     */
    private static final int MAX_SAFE_SHIFT = 30;

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
        if (retryCount <= 0 || retryCount > MAX_SAFE_SHIFT) {
            return baseWaitTime;
        }

        // 使用位运算实现指数退避：baseWaitTime * 2^(retryCount-1)
        // 用 Math.min 钳制到 Long.MAX_VALUE，避免大 baseWaitTime 与大指数相乘时溢出为负数。
        long shift = 1L << (retryCount - 1);
        // 当 shift * baseWaitTime 可能溢出 long 时退化为 baseWaitTime。
        if (baseWaitTime > 0 && shift > Long.MAX_VALUE / baseWaitTime) {
            return baseWaitTime;
        }
        return Math.min(baseWaitTime * shift, Long.MAX_VALUE);
    }

}
