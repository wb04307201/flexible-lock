package cn.wubo.flexible.lock.core;

import cn.wubo.flexible.lock.propertes.FlexibleLockProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;

import static org.junit.jupiter.api.Assertions.*;

class RedisLockTest {

    private FlexibleLockProperties.RedisStandaloneProperties redisProperties;
    private FlexibleLockProperties flexibleLockProperties;

    @BeforeEach
    void setUp() {
        // 创建Redis连接属性配置
        redisProperties = new FlexibleLockProperties.RedisStandaloneProperties();
        redisProperties.setHost("redis://127.0.0.1");
        redisProperties.setPort(6379);
        redisProperties.setPassword(null);
        redisProperties.setDatabase(0);

        // 创建主配置对象
        flexibleLockProperties = new FlexibleLockProperties();
        flexibleLockProperties.setRedis(redisProperties);
    }

    @Test
    void testCreateWithValidProperties() {
        // Given
        RedisLock redisLock = new RedisLock(flexibleLockProperties);

        // When
        // 使用反射调用protected方法
        RedissonClient client = null;
        try {
            java.lang.reflect.Method createMethod = RedisLock.class.getDeclaredMethod("create", FlexibleLockProperties.RedisStandaloneProperties.class);
            createMethod.setAccessible(true);
            client = (RedissonClient) createMethod.invoke(redisLock, redisProperties);
        } catch (Exception e) {
            fail("无法访问或调用create方法: " + e.getMessage());
        }

        // Then
        assertNotNull(client, "RedissonClient实例不应为空");
    }

    @Test
    void testTryLockSuccess() {
        // Given
        String lockKey = "test-key";
        RedisLock redisLock = new RedisLock(flexibleLockProperties);

        // When
        Boolean result = redisLock.tryLock(lockKey);

        // Then
        assertTrue(result);

        redisLock.unLock(lockKey);
    }

}
