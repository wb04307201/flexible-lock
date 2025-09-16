package cn.wubo.flexible.lock.autoconfigure;

import cn.wubo.flexible.lock.aop.LockAnnotationAspect;
import cn.wubo.flexible.lock.exception.LockRuntimeException;
import cn.wubo.flexible.lock.factory.IFactory;
import cn.wubo.flexible.lock.factory.impl.*;
import cn.wubo.flexible.lock.lock.ILock;
import cn.wubo.flexible.lock.propertes.LockProperties;
import cn.wubo.flexible.lock.retry.IRetryStrategy;
import cn.wubo.flexible.lock.retry.impl.ExponentialRetryStrategy;
import cn.wubo.flexible.lock.retry.impl.FixedRetryStrategy;
import cn.wubo.flexible.lock.retry.impl.RandomRetryStrategy;
import jakarta.validation.Validator;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
@EnableConfigurationProperties({LockProperties.class})
public class LockConfiguration {

    @Bean
    public List<IFactory> factories() {
        List<IFactory> list = new ArrayList<>();
        list.add(new RedisFactory());
        list.add(new RedisSentinelFactory());
        list.add(new RedisClusterFactory());
        list.add(new ZookeeperFactory());
        list.add(new StandaloneFactory());
        return list;
    }

    @Bean
    public List<IRetryStrategy> retryStrategies() {
        List<IRetryStrategy> list = new ArrayList<>();
        list.add(new FixedRetryStrategy());
        list.add(new ExponentialRetryStrategy());
        list.add(new RandomRetryStrategy());
        return list;
    }

    @Bean
    public List<ILock> locks(LockProperties lockProperties, List<IFactory> factories, List<IRetryStrategy> retryStrategies) {
        // @formatter:off
        return lockProperties.getProps().stream()
                .map(properties -> {
                    IRetryStrategy rs = retryStrategies.stream()
                            .filter(retryStrategy -> retryStrategy.supports(properties.getRetryStrategy()))
                            .findAny()
                            .orElseThrow(() -> new IllegalArgumentException("Unsupported retry strategy: " + properties.getRetryStrategy()));

                   return factories.stream()
                            .filter(factory -> factory.supports(properties.getLocktype()))
                            .findAny()
                            .orElseThrow(() -> new IllegalArgumentException("Unsupported lock type: " + properties.getLocktype()))
                            .create(properties, rs);

                })
                .collect(Collectors.toList());
        // @formatter:on
    }


    /**
     * 创建LockAnnotationAspect的Bean
     *
     * @param locks       锁集合
     * @param beanFactory Bean工厂
     * @return LockAnnotationAspect的Bean实例
     */
    @Bean
    public LockAnnotationAspect lockAspect(List<ILock> locks, BeanFactory beanFactory) {
        if (locks == null) {
            throw new IllegalArgumentException("locks parameter cannot be null");
        }
        if (beanFactory == null) {
            throw new IllegalArgumentException("beanFactory parameter cannot be null");
        }
        return new LockAnnotationAspect(locks, beanFactory);
    }

}
