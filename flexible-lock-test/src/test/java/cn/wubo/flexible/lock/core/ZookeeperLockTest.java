package cn.wubo.flexible.lock.core;

import cn.wubo.flexible.lock.propertes.FlexibleLockProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.integration.zookeeper.lock.ZookeeperLockRegistry;

import static org.junit.jupiter.api.Assertions.*;

class ZookeeperLockTest {

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

        // When
        // 使用反射调用protected方法
        ZookeeperLockRegistry client = null;
        try {
            java.lang.reflect.Method createMethod = ZookeeperLock.class.getDeclaredMethod("create", FlexibleLockProperties.ZookeeperProperties.class);
            createMethod.setAccessible(true);
            client = (ZookeeperLockRegistry) createMethod.invoke(zookeeperLock, zookeeperProperties);
        } catch (Exception e) {
            fail("无法访问或调用create方法: " + e.getMessage());
        }

        // Then
        assertNotNull(client, "ZookeeperLockRegistry实例不应为空");
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
}
