package cn.wubo.flexible.lock.aop;

import cn.wubo.flexible.lock.annotation.Locking;
import cn.wubo.flexible.lock.exception.LockRuntimeException;
import cn.wubo.flexible.lock.core.ILock;
import cn.wubo.flexible.lock.retry.IRetryStrategy;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.BeanResolver;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Aspect
public class LockAnnotationAspect {

    private final ILock lock;
    private final IRetryStrategy retryStrategy;
    private final BeanResolver beanResolver;
    // SpEL 表达式解析器
    private final ExpressionParser parser = new SpelExpressionParser();
    // 用于发现方法参数名的工具
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();


    public LockAnnotationAspect(ILock lock, IRetryStrategy retryStrategy, BeanFactory beanFactory) {
        this.lock = lock;
        this.retryStrategy = retryStrategy;
        this.beanResolver = new BeanFactoryResolver(beanFactory);
    }

    @Before("@annotation(locking)")
    public void before(JoinPoint joinPoint, Locking locking) {
        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
        tryLock(locking, methodSignature, joinPoint.getArgs());
    }

    @After("@annotation(locking)")
    public void after(JoinPoint joinPoint, Locking locking) {
        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
        String key = getSpelDefinitionKey(locking.key(), methodSignature.getMethod(), joinPoint.getArgs());
        lock.unLock(key);
    }

private String getSpelDefinitionKey(String spelExpression, Method method, Object[] args) {
    StandardEvaluationContext context = new StandardEvaluationContext();
    context.setBeanResolver(beanResolver);
    context.setVariable("method", method);

    // 设置方法参数名和值
    String[] paramNames = parameterNameDiscoverer.getParameterNames(method);
    if (paramNames != null && args.length == paramNames.length) {
        for (int i = 0; i < paramNames.length; i++) {
            context.setVariable(paramNames[i], args[i]);
        }
    }

    Expression expression = parser.parseExpression(spelExpression);
    return expression.getValue(context, String.class);
}



    /**
     * 尝试获取分布式锁
     *
     * @param locking         锁配置信息，包含锁的key、等待时间、重试次数等配置
     * @param methodSignature 方法签名，用于获取方法相关信息
     * @param args            方法调用参数数组
     */
    private void tryLock(Locking locking, MethodSignature methodSignature, Object[] args) {
        // 解析锁的key值
        String key = getSpelDefinitionKey(locking.key(), methodSignature.getMethod(), args);
        // 计算基础等待时间，优先使用locking配置，否则使用默认配置
        long baseWaitTime = locking.waitTime() > 0 ? locking.waitTime() : lock.getWaitTime();
        // 计算重试次数，优先使用locking配置，否则使用默认配置
        int retryCount = locking.retryCount() > 0 ? locking.retryCount() : lock.getRetryCount();
        log.debug("准备加锁: key={} retryCount={} waitTime={}ms", key, retryCount, baseWaitTime);

        // 循环尝试获取锁，直到成功或达到最大重试次数
        int count = 1;
        boolean tryLock;
        do {
            // 根据重试策略计算当前等待时间
            long calculateWaitTime = retryStrategy.calculateWaitTime(baseWaitTime, count);
            log.debug("加锁: count={} key={} waitTime={}ms", count, key, calculateWaitTime);
            // 执行加锁操作，根据等待时间是否大于0选择不同的加锁方式
            tryLock = calculateWaitTime > 0 ? lock.tryLock(key, calculateWaitTime) : lock.tryLock(key);
            log.debug("加锁结果: count={} key={} waitTime={}ms tryLock={}", count, key, calculateWaitTime, tryLock);
            count++;
        } while (!tryLock && count <= retryCount);

        // 如果最终仍未获取到锁，则抛出异常
        if (!tryLock) {
            String message = String.format("加锁失败: key=%s", key);
            log.debug(message);
            throw new LockRuntimeException(message);
        }
    }


}
