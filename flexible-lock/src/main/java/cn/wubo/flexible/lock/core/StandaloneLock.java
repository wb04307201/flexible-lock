package cn.wubo.flexible.lock.core;

import cn.wubo.flexible.lock.exception.LockRuntimeException;
import cn.wubo.flexible.lock.propertes.FlexibleLockProperties;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 单机锁后端：用 {@link ConcurrentHashMap} 持有每个 key 对应的 {@link ReentrantLock}。
 *
 * <p><b>关键不变量</b>：{@code unLock} 必须<b>不</b>从 map 中移除 entry。
 * 否则后续的 {@code tryLock} 会通过 {@code computeIfAbsent} 创建出全新的
 * {@code ReentrantLock} 实例，从而绕过互斥——同一个 JVM 内的两个线程会拿到
 * 不同的监视器。
 *
 * <p>{@code unLock} 对从未 {@code tryLock} 过的 key 抛 {@link LockRuntimeException}，
 * 与 Redis/Zookeeper 后端行为对称，便于统一捕获。
 *
 * <p>仅适用于单 JVM 场景；多节点部署请改用 Redis/Zookeeper 后端。
 */
public class StandaloneLock extends AbstractLock {

    private final Map<String, ReentrantLock> lockMap;

    public StandaloneLock(FlexibleLockProperties properties) {
        super(properties);
        this.lockMap = new ConcurrentHashMap<>();
    }

    @Override
    public Boolean tryLock(String key) {
        return this.lockMap.computeIfAbsent(key, k -> new ReentrantLock()).tryLock();
    }

    @Override
    public Boolean tryLock(String key, Long time) {
        try {
            return this.lockMap.computeIfAbsent(key, k -> new ReentrantLock()).tryLock(time, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LockRuntimeException(e);
        }
    }

    @Override
    public void unLock(String key) {
        // 注意:不要从 map 中 remove(key),否则后续的 tryLock 会 computeIfAbsent
        // 出新的 ReentrantLock 实例,破坏同一 key 上的互斥语义。
        Lock lock = this.lockMap.get(key);
        if (lock == null) {
            // 与 Redis / Zookeeper 后端保持对称:对未持有(或从未 tryLock 过)
            // 的 key 调 unLock 必须抛错,这样 SpEL key 拼写错误导致 lock 和 unLock
            // 用了不同 key 这类 bug 才能被及时暴露,而不是被静默吞掉。
            throw new LockRuntimeException(
                    String.format("unLock called for key '%s' that was never acquired", key));
        }
        lock.unlock();
    }
}
