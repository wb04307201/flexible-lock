package cn.wubo.flexible.lock;

import cn.wubo.flexible.lock.autoconfigure.LockConfiguration;
import cn.wubo.flexible.lock.aop.LockAnnotationAspect;
import cn.wubo.flexible.lock.factory.IFactory;
import cn.wubo.flexible.lock.lock.ILock;
import cn.wubo.flexible.lock.retry.IRetryStrategy;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.BeanFactory;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class LockConfigurationTest {
    @Mock
    private BeanFactory mockBeanFactory;

    @Mock
    private Validator mockValidator;

    private LockConfiguration lockConfiguration;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        lockConfiguration = new LockConfiguration();
    }

    @Test
    void testFactoriesBean() {
        List<IFactory> factories = lockConfiguration.factories();
        assertNotNull(factories);
        assertFalse(factories.isEmpty());
        assertEquals(4, factories.size()); // RedisFactory, RedisSentinelFactory, RedisClusterFactory, ZookeeperFactory
    }

    @Test
    void testRetryStrategiesBean() {
        List<IRetryStrategy> retryStrategies = lockConfiguration.retryStrategies();
        assertNotNull(retryStrategies);
        assertFalse(retryStrategies.isEmpty());
        assertEquals(3, retryStrategies.size()); // FixedRetryStrategy, ExponentialRetryStrategy, RandomRetryStrategy
    }

    @Test
    void testLockAspectBean() {
        List<ILock> locks = new ArrayList<>();
        LockAnnotationAspect aspect = lockConfiguration.lockAspect(locks, mockBeanFactory);
        assertNotNull(aspect);
    }

    @Test
    void testLockAspectBeanWithNullLocks() {
        assertThrows(IllegalArgumentException.class, () ->
                lockConfiguration.lockAspect(null, mockBeanFactory));
    }

    @Test
    void testLockAspectBeanWithNullBeanFactory() {
        List<ILock> locks = new ArrayList<>();
        assertThrows(IllegalArgumentException.class, () ->
                lockConfiguration.lockAspect(locks, null));
    }
}
