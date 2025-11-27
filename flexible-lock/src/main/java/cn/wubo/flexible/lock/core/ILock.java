package cn.wubo.flexible.lock.core;


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
     * @param waitTime 最大等待时间
     * @return 如果成功加锁返回true，否则返回false
     */
    Boolean tryLock(String key, Long waitTime);

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
}
