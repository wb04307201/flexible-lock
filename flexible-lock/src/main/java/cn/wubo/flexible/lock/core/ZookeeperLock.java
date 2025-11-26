package cn.wubo.flexible.lock.core;

import cn.wubo.flexible.lock.exception.LockRuntimeException;
import cn.wubo.flexible.lock.propertes.FlexibleLockProperties;
import jakarta.validation.Valid;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryUntilElapsed;
import org.springframework.integration.zookeeper.lock.ZookeeperLockRegistry;

import java.util.concurrent.TimeUnit;

public class ZookeeperLock extends AbstractLock {

    private ZookeeperLockRegistry zookeeperLockRegistry;

    public ZookeeperLock(FlexibleLockProperties properties) {
        super(properties);
        this.zookeeperLockRegistry = create(properties.getZookeeper());
    }

    private ZookeeperLockRegistry create(@Valid FlexibleLockProperties.ZookeeperProperties properties) {
        CuratorFramework curatorFramework = CuratorFrameworkFactory.newClient(
                properties.getConnectString(),
                new RetryUntilElapsed(
                        properties.getMaxElapsedTimeMs(),
                        properties.getSleepMsBetweenRetries()
                )
        );
        curatorFramework.start();
        return new ZookeeperLockRegistry(curatorFramework,properties.getRoot());
    }

    @Override
    public Boolean tryLock(String key) {
        return zookeeperLockRegistry.obtain(key).tryLock();
    }

    @Override
    public Boolean tryLock(String key, Long time) {
        try {
            return zookeeperLockRegistry.obtain(key).tryLock(time, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LockRuntimeException(e);
        }
    }


    @Override
    public void unLock(String key) {
        zookeeperLockRegistry.obtain(key).unlock();
    }
}
