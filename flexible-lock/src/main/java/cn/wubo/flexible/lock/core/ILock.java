package cn.wubo.flexible.lock.core;

/**
 * 统一锁接口，所有锁后端（Redis / Zookeeper / Standalone / none）都必须实现。
 *
 * <p>starter 通过 {@link cn.wubo.flexible.lock.factory.LockFactory} 根据
 * {@link cn.wubo.flexible.lock.propertes.LockType} 配置派生出具体实现，
 * 上层代码（{@code LockAnnotationAspect}）只面向本接口编程，不感知具体后端。
 *
 * <p>线程安全契约：实现必须可被多线程并发调用。{@link #tryLock} / {@link #unLock}
 * 的同一 key 调用可能来自不同线程，但同一线程绝不会对同一 key 调 {@code unLock}
 * 而没有先调 {@code tryLock}（这是切面在 finally 块里强制保证的）。
 */
public interface ILock {

    /**
     * 尝试对给定的键进行加锁操作。
     *
     * @param key 锁的键值
     * @return 如果成功加锁返回true，否则返回false
     */
    Boolean tryLock(String key);

    /**
     * 尝试对给定的键加锁，设置最大等待时间和时间单位
     *
     * @param key  锁的键值
     * @param waitTime 最大等待时间（毫秒）
     * @return 如果成功加锁返回true，否则返回false
     */
    Boolean tryLock(String key, Long waitTime);

    /**
     * 释放给定 key 上的锁。
     *
     * <p>必须在成功调用 {@link #tryLock(String)} 之后调用。对未持有（或从未
     * {@code tryLock} 过）的 key 调本方法，实现可以抛出运行时异常以暴露 SpEL key
     * 拼写错误——而不是静默吞掉。
     *
     * @param key 锁的键值
     */
    void unLock(String key);

    /**
     * 获取重试次数（供切面在 {@code @Locking} 未指定 retryCount 时使用）。
     *
     * @return 全局默认重试次数（{@code retryCount = N} 表示初次失败后再重试 N 次，共 N+1 次）
     */
    Integer getRetryCount();

    /**
     * 获取默认等待时间（毫秒，供切面在 {@code @Locking} 未指定 waitTime 时使用）。
     *
     * @return 全局默认等待时间
     */
    Long getWaitTime();

    /**
     * 释放锁实现持有的外部资源（Redisson 连接池、Curator 客户端等）。
     *
     * <p>无外部资源的后端（{@link StandaloneLock}、{@link NoneLock}）的默认实现为空操作。
     * Spring 容器关闭时会通过 {@code @Bean(destroyMethod = "shutdown")} 触发。
     */
    default void shutdown() {
    }
}
