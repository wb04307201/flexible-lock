package cn.wubo.flexible.lock.core;

import cn.wubo.entity.sql.IntegrationTestBase;
import cn.wubo.flexible.lock.propertes.FlexibleLockProperties;
import org.apache.curator.framework.CuratorFramework;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.integration.zookeeper.lock.ZookeeperLockRegistry;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ZookeeperLockTest extends IntegrationTestBase {

    private FlexibleLockProperties.ZookeeperProperties zookeeperProperties;
    private FlexibleLockProperties flexibleLockProperties;

    @BeforeEach
    void setUp() {
        // 准备 Zookeeper 属性配置
        zookeeperProperties = new FlexibleLockProperties.ZookeeperProperties();
        zookeeperProperties.setConnectString("127.0.0.1:2181");
        zookeeperProperties.setMaxElapsedTimeMs(1000);
        zookeeperProperties.setSleepMsBetweenRetries(4);
        zookeeperProperties.setRoot("/locks");

        // 准备主配置对象
        flexibleLockProperties = new FlexibleLockProperties();
        flexibleLockProperties.setZookeeper(zookeeperProperties);
    }

    @Test
    void testCreateWithValidProperties() {
        // Given
        ZookeeperLock zookeeperLock = new ZookeeperLock(flexibleLockProperties);

        // When: invoke the private create() method via reflection
        CuratorFramework client = null;
        try {
            java.lang.reflect.Method createMethod = ZookeeperLock.class.getDeclaredMethod("create", FlexibleLockProperties.ZookeeperProperties.class);
            createMethod.setAccessible(true);
            client = (CuratorFramework) createMethod.invoke(zookeeperLock, zookeeperProperties);
        } catch (Exception e) {
            fail("无法访问或调用create方法: " + e.getMessage());
        }

        // Then: a started CuratorFramework is returned
        assertNotNull(client, "CuratorFramework实例不应为空");
        assertEquals(org.apache.curator.framework.imps.CuratorFrameworkState.STARTED, client.getState(),
                "CuratorFramework should be started");

        // Cleanup
        client.close();
    }

    @Test
    void testTryLockSuccess() {
        // Given
        String lockKey = "test-key";
        ZookeeperLock zookeeperLock = new ZookeeperLock(flexibleLockProperties);

        // When
        Boolean result = zookeeperLock.tryLock(lockKey);

        // Then
        assertTrue(result);

        zookeeperLock.unLock(lockKey);
    }

    @Test
    void onlyOneThreadInCriticalSectionAtATime() throws Exception {
        // Verify mutual exclusion holds end-to-end with ZK as the backend.
        String lockKey = "mutex-" + System.nanoTime();
        ZookeeperLock zookeeperLock = new ZookeeperLock(flexibleLockProperties);

        CountDownLatch inside = new CountDownLatch(1);
        CountDownLatch mayExit = new CountDownLatch(1);
        AtomicInteger insideCount = new AtomicInteger();
        AtomicInteger maxConcurrent = new AtomicInteger();

        Thread holder = new Thread(() -> {
            assertTrue(zookeeperLock.tryLock(lockKey, 5000L));
            insideCount.incrementAndGet();
            maxConcurrent.updateAndGet(prev -> Math.max(prev, insideCount.get()));
            inside.countDown();
            try {
                mayExit.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            insideCount.decrementAndGet();
            zookeeperLock.unLock(lockKey);
        }, "holder");

        Thread contender = new Thread(() -> {
            try {
                inside.await();
                Thread.sleep(100); // give the holder time to be deep in CS
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            // Must block until mayExit.countDown(); the timed tryLock gives
            // us a deterministic upper bound on the wait.
            assertTrue(zookeeperLock.tryLock(lockKey, 5000L),
                    "contender should acquire after holder releases");
            insideCount.incrementAndGet();
            maxConcurrent.updateAndGet(prev -> Math.max(prev, insideCount.get()));
            insideCount.decrementAndGet();
            zookeeperLock.unLock(lockKey);
        }, "contender");

        holder.start();
        contender.start();

        assertTrue(inside.await(5, TimeUnit.SECONDS), "holder should be inside");
        Thread.sleep(200); // give contender time to start waiting
        assertEquals(1, maxConcurrent.get(),
                "Only one thread may be in the critical section at a time; actual=" + maxConcurrent.get());

        mayExit.countDown();
        holder.join(5000);
        contender.join(5000);
        assertEquals(1, maxConcurrent.get(), "still mutually exclusive after release");
    }

    @Test
    void shutdownClosesCuratorFramework() {
        // Given
        ZookeeperLock zookeeperLock = new ZookeeperLock(flexibleLockProperties);
        CuratorFramework cf = readCuratorFramework(zookeeperLock);
        assertNotNull(cf);
        assertEquals(org.apache.curator.framework.imps.CuratorFrameworkState.STARTED, cf.getState());

        // When
        zookeeperLock.shutdown();

        // Then: the underlying CuratorFramework is closed (LATENT after close()).
        // CuratorFramework doesn't expose state directly after close, but we can
        // verify the lock can no longer be acquired.
        assertThrows(Exception.class, () -> zookeeperLock.tryLock("anything", 1000L),
                "After shutdown, lock operations must fail because the framework is closed");
    }

    @Test
    void digestAuthIsAppliedToCuratorFramework() throws Exception {
        // Verify that constructing ZookeeperLock with a non-empty digest
        // successfully starts a CuratorFramework and lets us acquire a lock.
        // (End-to-end ACL validation would require pre-creating an ACL'd
        // ZNode out of band, which is out of scope for this smoke test.)
        FlexibleLockProperties.ZookeeperProperties propsWithDigest =
                new FlexibleLockProperties.ZookeeperProperties();
        propsWithDigest.setConnectString("127.0.0.1:2181");
        propsWithDigest.setMaxElapsedTimeMs(1000);
        propsWithDigest.setSleepMsBetweenRetries(4);
        propsWithDigest.setRoot("/locks");
        propsWithDigest.setDigest("testuser:testpass");

        FlexibleLockProperties propertiesWithDigest = new FlexibleLockProperties();
        propertiesWithDigest.setLockType(cn.wubo.flexible.lock.propertes.LockType.ZOOKEEPER);
        propertiesWithDigest.setZookeeper(propsWithDigest);

        ZookeeperLock lock = new ZookeeperLock(propertiesWithDigest);
        CuratorFramework cf = readCuratorFramework(lock);
        assertNotNull(cf);
        assertEquals(org.apache.curator.framework.imps.CuratorFrameworkState.STARTED, cf.getState());

        // The lock should still be usable end-to-end.
        String lockKey = "digest-test-" + System.nanoTime();
        assertTrue(lock.tryLock(lockKey));
        lock.unLock(lockKey);
        lock.shutdown();
    }

    private static CuratorFramework readCuratorFramework(ZookeeperLock lock) {
        try {
            Field f = ZookeeperLock.class.getDeclaredField("curatorFramework");
            f.setAccessible(true);
            return (CuratorFramework) f.get(lock);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected boolean requiresZooKeeper() {
        return true;
    }
}
