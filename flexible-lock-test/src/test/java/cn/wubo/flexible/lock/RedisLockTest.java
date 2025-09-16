package cn.wubo.flexible.lock;

import cn.wubo.flexible.lock.lock.platform.redis.RedisLock;
import cn.wubo.flexible.lock.propertes.LockPlatformProperties;
import cn.wubo.flexible.lock.retry.IRetryStrategy;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RedisLockTest {

    @Mock
    private IRetryStrategy mockRetryStrategy;

    private LockPlatformProperties properties;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        properties = new LockPlatformProperties();
        properties.setAlias("testRedis");
        properties.setLocktype("redis");

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("address", "redis://localhost:6379");
        attributes.put("password", "mypassword");
        attributes.put("database", 0);
        properties.setAttributes(attributes);
    }

    @Test
    void testSupportsAlias() {
        RedisLock redisLock = new RedisLock(properties,mockRetryStrategy);
        assertTrue(redisLock.supportsAlias("testRedis"));
        assertFalse(redisLock.supportsAlias("wrongAlias"));
    }

    @Test
    void testGetRetryCount() {
        properties.setRetryCount(5);
        RedisLock redisLock = new RedisLock(properties, mockRetryStrategy);
        assertEquals(5, redisLock.getRetryCount());
    }

    @Test
    void testGetWaitTime() {
        properties.setWaitTime(5000L);
        RedisLock redisLock = new RedisLock(properties, mockRetryStrategy);
        assertEquals(5000L, redisLock.getWaitTime());
    }

    @Test
    void testCalculateBackoffTime() {
        when(mockRetryStrategy.calculateWaitTime(anyLong(), anyInt())).thenReturn(1000L);
        RedisLock redisLock = new RedisLock(properties, mockRetryStrategy);
        long result = redisLock.calculateBackoffTime(3);
        assertEquals(1000L, result);
        verify(mockRetryStrategy).calculateWaitTime(3000L, 3);
    }
}
