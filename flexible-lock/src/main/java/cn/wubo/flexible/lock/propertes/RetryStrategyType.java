package cn.wubo.flexible.lock.propertes;

/**
 * 重试策略类型，决定 {@code @Locking} 在重试时如何计算两次尝试之间的等待时间。
 *
 * <p>具体行为见 {@link cn.wubo.flexible.lock.factory.RetryStrategyFactory}：
 * <ul>
 *   <li>{@link #FIXED} — 固定时间间隔（默认）</li>
 *   <li>{@link #EXPONENTIAL} — 指数退避</li>
 *   <li>{@link #RANDOM} — 随机退避</li>
 * </ul>
 */
public enum RetryStrategyType {

    /** 固定时间间隔重试（{@code fixed}），每次重试都等待 {@code waitTime} 毫秒。 */
    FIXED,

    /** 指数退避重试（{@code exponential}），等待时间按 {@code waitTime * 2^(retryCount - 1)} 增长并做溢出保护。 */
    EXPONENTIAL,

    /** 随机退避重试（{@code random}），在 {@code [waitTime, waitTime * (retryCount + 1))} 区间内随机。 */
    RANDOM;


}
