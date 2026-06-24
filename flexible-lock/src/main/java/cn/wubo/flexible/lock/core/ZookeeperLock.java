package cn.wubo.flexible.lock.core;

import cn.wubo.flexible.lock.exception.LockRuntimeException;
import cn.wubo.flexible.lock.propertes.FlexibleLockProperties;
import jakarta.validation.Valid;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryUntilElapsed;
import org.springframework.integration.zookeeper.lock.ZookeeperLockRegistry;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * ZooKeeper 分布式锁后端，基于 Spring Integration 的 {@link ZookeeperLockRegistry}。
 *
 * <p>底层用 {@link CuratorFramework}（通过 {@code CuratorFrameworkFactory.builder()}
 * 而不是 {@code newClient()} 构造，以便支持 {@code digest} 认证 ACL）。当 ZK 节点
 * 配了 ACL 而客户端未提供正确凭证时，所有加锁都会失败。
 *
 * <p>由 Spring 在容器关闭时通过 {@code @Bean(destroyMethod = "shutdown")} 调用
 * {@link #shutdown()} 关闭底层 Curator 连接。
 */
public class ZookeeperLock extends AbstractLock {

    private final CuratorFramework curatorFramework;
    private final ZookeeperLockRegistry zookeeperLockRegistry;

    public ZookeeperLock(FlexibleLockProperties properties) {
        super(properties);
        this.curatorFramework = create(properties.getZookeeper());
        this.zookeeperLockRegistry = new ZookeeperLockRegistry(curatorFramework, properties.getZookeeper().getRoot());
    }

    /**
     * Build and start a {@link CuratorFramework}.
     *
     * <p>使用 builder() 而不是 newClient() 以支持 digest 认证。
     * 当 ZooKeeper 节点配置了 ACL（create /digest 用户名:密码）时，
     * 没有正确认证的客户端会被节点拒绝读写，所有加锁操作都会失败。
     */
    private CuratorFramework create(@Valid FlexibleLockProperties.ZookeeperProperties properties) {
        CuratorFrameworkFactory.Builder builder = CuratorFrameworkFactory.builder()
                .connectString(properties.getConnectString())
                .retryPolicy(new RetryUntilElapsed(
                        properties.getMaxElapsedTimeMs(),
                        properties.getSleepMsBetweenRetries()
                ));
        if (properties.getDigest() != null && !properties.getDigest().isEmpty()) {
            builder.authorization("digest", properties.getDigest().getBytes(StandardCharsets.UTF_8));
        }
        CuratorFramework cf = builder.build();
        cf.start();
        return cf;
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

    @Override
    public void shutdown() {
        curatorFramework.close();
    }
}
