package cn.wubo.flexible.lock.autoconfigure;

import cn.wubo.flexible.lock.aop.LockAnnotationAspect;
import cn.wubo.flexible.lock.core.ILock;
import cn.wubo.flexible.lock.factory.LockFactory;
import cn.wubo.flexible.lock.factory.RetryStrategyFactory;
import cn.wubo.flexible.lock.propertes.FlexibleLockProperties;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({FlexibleLockProperties.class})
public class LockConfiguration {

    /**
     * The lock backend selected from {@code flexible.lock.lockType}.
     *
     * <p>Registered as a Spring bean so that:
     * <ul>
     *   <li>its {@code shutdown()} method runs on container close, releasing
     *       the underlying Redisson / Curator connection pool;</li>
     *   <li>users can override the bean to provide a custom lock backend.</li>
     * </ul>
     *
     * <p>If construction fails (e.g., Redis/ZK unreachable, bad host),
     * the original exception is wrapped in a more diagnostic message that
     * points the user at the relevant config property and at the {@code none}
     * escape hatch.
     */
    @Bean(destroyMethod = "shutdown")
    public ILock flexibleLock(FlexibleLockProperties properties) {
        try {
            return lockFactory().create(properties);
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                    String.format("failed to build lock backend for flexible.lock.lockType=%s; "
                                    + "check the corresponding config block (flexible.lock.redis / redisCluster / "
                                    + "redisSentinel / zookeeper) or set flexible.lock.lockType=none to disable locking",
                            properties.getLockType()),
                    e);
        }
    }

    /**
     * 创建分布式锁切面Bean
     *
     * @param properties 配置属性，用于初始化锁和重试策略
     * @param lock 锁实现（从工厂创建）
     * @param retryStrategyFactory 重试策略工厂，用于创建重试策略
     * @param beanFactory Spring Bean工厂，用于获取Spring管理的Bean
     * @return LockAnnotationAspect 分布式锁注解切面实例
     */
    @Bean
    public LockAnnotationAspect lockAspect(FlexibleLockProperties properties,
                                           ILock lock,
                                           RetryStrategyFactory retryStrategyFactory,
                                           BeanFactory beanFactory) {
        return new LockAnnotationAspect(lock, retryStrategyFactory.create(properties), beanFactory);
    }

    /**
     * Exposed as a bean so users can provide their own {@code LockFactory}
     * to plug in a custom {@code LockType} dispatch (e.g., for a new backend).
     */
    @Bean
    public LockFactory lockFactory() {
        return new LockFactory();
    }

    /**
     * Exposed as a bean so users can pick a non-default retry strategy by
     * providing their own {@code RetryStrategyFactory} bean.
     */
    @Bean
    public RetryStrategyFactory retryStrategyFactory() {
        return new RetryStrategyFactory();
    }
}
