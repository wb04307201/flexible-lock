package cn.wubo.flexible.lock.core;

import cn.wubo.flexible.lock.exception.LockRuntimeException;
import cn.wubo.flexible.lock.propertes.FlexibleLockProperties;
import jakarta.validation.Valid;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

import java.util.concurrent.TimeUnit;

/**
 * Redis 哨兵模式锁后端，基于 Redisson {@link RedissonClient}。
 *
 * <p>按 {@link FlexibleLockProperties.RedisSentinelProperties} 指定的哨兵地址和
 * master 名称发现主从节点，由 Spring 在容器关闭时通过
 * {@code @Bean(destroyMethod = "shutdown")} 调用 {@link #shutdown()} 释放。
 *
 * <p>锁语义与 {@link RedisLock} 相同：可重入、watchdog 自动续期。
 */
public class RedisSentinelLock extends AbstractLock {

    private final RedissonClient client;

    public RedisSentinelLock(FlexibleLockProperties properties) {
        super(properties);
        this.client = create(properties.getRedisSentinel());
    }

    private RedissonClient create(@Valid  FlexibleLockProperties.RedisSentinelProperties properties) {
        Config config = new Config();
        config.useSentinelServers()
                .addSentinelAddress(properties.getNodes())
                .setPassword(properties.getPassword())
                .setDatabase(properties.getDatabase())
                .setMasterName(properties.getMaster());
        return Redisson.create(config);
    }

    @Override
    public Boolean tryLock(String key) {
        return client.getLock(key).tryLock();
    }

    @Override
    public Boolean tryLock(String key, Long time) {
        try {
            return client.getLock(key).tryLock(time, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LockRuntimeException(e);
        }
    }


    @Override
    public void unLock(String key) {
        client.getLock(key).unlock();
    }

    @Override
    public void shutdown() {
        client.shutdown();
    }
}
