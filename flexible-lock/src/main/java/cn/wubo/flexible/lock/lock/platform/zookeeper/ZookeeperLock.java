package cn.wubo.flexible.lock.lock.platform.zookeeper;

import cn.wubo.flexible.lock.exception.LockRuntimeException;
import cn.wubo.flexible.lock.lock.platform.AbstractLock;
import cn.wubo.flexible.lock.propertes.LockPlatformProperties;
import cn.wubo.flexible.lock.retry.IRetryStrategy;
import cn.wubo.flexible.lock.utils.ValidationUtils;
import jakarta.validation.Validator;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryUntilElapsed;
import org.springframework.integration.zookeeper.lock.ZookeeperLockRegistry;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ZookeeperLock extends AbstractLock {

    private ZookeeperLockRegistry zookeeperLockRegistry;

    public ZookeeperLock(LockPlatformProperties properties, Validator validator, IRetryStrategy retryStrategy) {
        super(properties, validator, retryStrategy);
        CuratorFramework curatorFramework = CuratorFrameworkFactory.newClient(
                (String) properties.getAttributes().get("connect"),
                new RetryUntilElapsed(
                        (Integer) properties.getAttributes().get("maxElapsedTimeMs"),
                        (Integer) properties.getAttributes().get("sleepMsBetweenRetries")
                )
        );
        curatorFramework.start();
        this.zookeeperLockRegistry = new ZookeeperLockRegistry(curatorFramework,
                (String) properties.getAttributes().get("root")
        );
    }


    @Override
    public void validate() {
        super.validate();

        Map<String, Object> attributes = properties.getAttributes();

        ValidationUtils.validateStringAttribute( attributes,"connect","ZookeeperLock password is null");

        attributes.putIfAbsent("maxElapsedTimeMs", 1000);
        attributes.putIfAbsent("sleepMsBetweenRetries", 4);
        attributes.putIfAbsent("root", "/locks");
    }

    @Override
    public Boolean tryLock(String key) {
        return zookeeperLockRegistry.obtain(key).tryLock();
    }

    @Override
    public Boolean tryLock(String key, Long time, TimeUnit unit) {
        try {
            return zookeeperLockRegistry.obtain(key).tryLock(time, unit);
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
