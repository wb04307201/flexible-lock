package cn.wubo.flexible.lock.lock;

import java.util.concurrent.TimeUnit;

public interface ILock {

    Boolean supportsAlias(String alias);

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
     * @param time 最大等待时间
     * @param unit 时间单位
     * @return 如果成功加锁返回true，否则返回false
     */
    Boolean tryLock(String key, Long time, TimeUnit unit);

    /**
     * 重写的方法，用于解锁指定的键
     *
     * @param key 锁的键值
     */
    void unLock(String key);

    /**
     * 获取重试次数
     *
     * @return 重试次数
     */
    Integer getRetryCount();

    /**
     * 获取等待时间
     *
     * @return 返回等待时间
     */
    Long getWaitTime();

    long calculateBackoffTime(int retryCount);
}
