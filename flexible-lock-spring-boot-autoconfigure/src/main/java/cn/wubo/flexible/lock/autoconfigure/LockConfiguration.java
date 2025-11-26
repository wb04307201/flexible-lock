package cn.wubo.flexible.lock.autoconfigure;

import cn.wubo.flexible.lock.aop.LockAnnotationAspect;
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

    @Bean
    public LockFactory lockFactory() {
        return new LockFactory();
    }

    @Bean
    public RetryStrategyFactory retryStrategyFactory() {
        return new RetryStrategyFactory();
    }

    /**
     * 创建分布式锁切面Bean
     *
     * @param properties 配置属性，用于初始化锁和重试策略
     * @param lockFactory 锁工厂，用于创建具体的锁实现
     * @param retryStrategyFactory 重试策略工厂，用于创建重试策略
     * @param beanFactory Spring Bean工厂，用于获取Spring管理的Bean
     * @return LockAnnotationAspect 分布式锁注解切面实例
     */
    @Bean
    public LockAnnotationAspect lockAspect(FlexibleLockProperties properties, LockFactory lockFactory, RetryStrategyFactory retryStrategyFactory, BeanFactory beanFactory) {
        return new LockAnnotationAspect(lockFactory.create(properties), retryStrategyFactory.create(properties), beanFactory);
    }


}
