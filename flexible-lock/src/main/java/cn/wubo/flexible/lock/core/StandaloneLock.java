package cn.wubo.flexible.lock.core;

import cn.wubo.flexible.lock.exception.LockRuntimeException;
import cn.wubo.flexible.lock.propertes.FlexibleLockProperties;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

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
        Lock lock = this.lockMap.remove(key);
        if (lock != null) {
            lock.unlock();
        }
    }
}
