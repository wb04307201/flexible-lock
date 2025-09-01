package cn.wubo.flexible.lock.aop;

import cn.wubo.flexible.lock.annotation.Locking;
import cn.wubo.flexible.lock.exception.LockRuntimeException;
import cn.wubo.flexible.lock.lock.ILock;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
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
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Slf4j
@Aspect
public class LockAnnotationAspect {

    private static final ParameterNameDiscoverer NAME_DISCOVERER = new DefaultParameterNameDiscoverer();
    private static final ExpressionParser PARSER = new SpelExpressionParser();
    private final CopyOnWriteArrayList<ILock> locks;
    private final BeanResolver beanResolver;

    public LockAnnotationAspect(List<ILock> locks, BeanFactory beanFactory) {
        this.locks = new CopyOnWriteArrayList<>(locks);
        this.beanResolver = new BeanFactoryResolver(beanFactory);
    }


    /**
 * 在执行带有@Locking注解的方法之前，执行此方法以获取锁
 *
 * @param joinPoint 切入点，提供了关于目标方法的信息
 * @param locking   锁定注解，包含锁的配置信息
 */
@Before("@annotation(locking)")
public void before(JoinPoint joinPoint, Locking locking) {
    try {
        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
        ILock lock = getLock(locking.alias());

        if (lock == null) {
            String errorMsg = String.format("锁别名%s不存在！", locking.alias());
            log.error("Lock not found: alias={}", locking.alias());
            throw new LockRuntimeException(errorMsg);
        } else {
            tryLock(locking, lock, methodSignature, joinPoint.getTarget(), joinPoint.getArgs());
        }
    } catch (Exception e) {
        // 记录详细异常信息
        log.error("获取锁时发生异常: alias={}, method={}",
                locking.alias(),
                joinPoint.getSignature().getName(),
                e);
        throw new LockRuntimeException("获取锁失败: " + e.getMessage(), e);
    }
}



    // 在带有@Locking注解的方法执行后执行的方法
    @After("@annotation(locking)")
    public void after(JoinPoint joinPoint, Locking locking) {
        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
        ILock lock = getLock(locking.alias());

        if (lock != null) {
            try {
                Long threadId = Thread.currentThread().getId();
                String newKey = getNewKey(locking.alias(), locking.keys(), joinPoint.getTarget(), methodSignature.getMethod(), joinPoint.getArgs());
                long startTime = System.currentTimeMillis();

                lock.unLock(newKey);

                long duration = System.currentTimeMillis() - startTime;
                log.debug("解锁成功: thread={} method={} alias={} key={} duration={}ms",
                        threadId, methodSignature.getMethod().getName(), locking.alias(), newKey, duration);
            } catch (Exception e) {
                log.warn("解锁过程中发生异常: alias={} method={}",
                        locking.alias(), methodSignature.getMethod().getName(), e);
            }
        }
    }



    /**
     * 根据别名获取锁
     *
     * @param alias 锁的别名
     * @return 锁对象，如果别名不存在则返回null
     */
    private ILock getLock(String alias) {
        if (alias == null) {
            return null;
        }
        return locks.stream().filter(lock -> lock.supportsAlias(alias))
                .findAny()
                .orElseThrow(() -> new LockRuntimeException(String.format("锁别名%s不存在！", alias)));
    }


    /**
     * 根据别名和给定的参数生成一个新的键。
     *
     * @param alias      别名
     * @param keys       键的数组
     * @param rootObject 根对象
     * @param method     方法
     * @param args       方法参数
     * @return 生成的新键
     */
    private String getNewKey(String alias, String[] keys, Object rootObject, Method method, Object[] args) {
        if (alias == null) {
            alias = "";
        }

        String temp = getSpelDefinitionKey(keys, rootObject, method, args);
        String prefix = alias + ":" + method.toGenericString();

        if ("".equals(temp)) {
            return prefix;
        } else {
            return prefix + ":" + temp;
        }
    }


    /**
     * 获取SPEL定义的键
     *
     * @param keys       键数组，用于构造最终的键值字符串
     * @param rootObject 根对象，作为SPEL表达式解析的上下文对象
     * @param method     方法，当前执行的方法，用于SPEL表达式的解析
     * @param args       参数，当前方法的参数，用于SPEL表达式的解析
     * @return SPEL定义的键，通过解析给定的键数组中的SPEL表达式并合并结果得到的键值字符串
     */
    private String getSpelDefinitionKey(String[] keys, Object rootObject, Method method, Object[] args) {
        if (keys == null || keys.length == 0) {
            return "";
        }
        if (method == null || args == null) {
            throw new IllegalArgumentException("method and args must not be null");
        }

        StandardEvaluationContext context = new MethodBasedEvaluationContext(rootObject, method, args, NAME_DISCOVERER);
        context.setBeanResolver(beanResolver);

        return Arrays.stream(keys)
                .filter(Objects::nonNull)
                .map(key -> {
                    try {
                        return PARSER.parseExpression(key).getValue(context, String.class);
                    } catch (Exception e) {
                        // 可选：添加日志记录
                        // log.warn("Failed to parse expression: {}", key, e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.joining(":"));
    }

    /**
     * 尝试获取锁的方法
     *
     * @param locking         锁的配置信息，包括别名、键、超时时间等
     * @param lock            锁的实现对象，用于执行实际的锁操作
     * @param methodSignature 方法签名，包含方法名、参数类型等信息，用于日志记录
     * @param target          目标对象，即被拦截的方法所属的实例
     * @param args            方法参数数组，用于日志记录和生成锁的键
     */
    private void tryLock(Locking locking, ILock lock, MethodSignature methodSignature, Object target, Object[] args) {
        if (lock == null) {
            throw new IllegalArgumentException("Lock object cannot be null");
        }

        // 获取当前线程ID，用于日志记录
        Long threadId = Thread.currentThread().getId();
        // 生成新的锁键
        String newKey = getNewKey(locking.alias(), locking.keys(), target, methodSignature.getMethod(), args);
        // 记录尝试加锁的日志
        log.debug("尝试加锁: thread={} method={} alias={} key={}",
                threadId, methodSignature.getMethod().getName(), locking.alias(), newKey);

        // 尝试获取锁，根据配置的超时时间决定是否使用带超时的尝试方法
        long startTime = System.currentTimeMillis();
        Boolean tryLock = locking.time() > 0 ? lock.tryLock(newKey, locking.time(), locking.unit()) : lock.tryLock(newKey);
        long duration = System.currentTimeMillis() - startTime;


        int count = 0;
        long totalWaitTime = 0;
        // 如果获取锁失败，并且配置了重试次数，则进行重试
        if (Boolean.FALSE.equals(tryLock)) {
            int retryCount = lock.getRetryCount();

            // 重试条件: retryCount > 0 且已重试次数小于最大重试次数，或者 retryCount < 0 (无限重试)
            boolean shouldRetry = (retryCount > 0 && count < retryCount) || retryCount < 0;

            while (Boolean.FALSE.equals(tryLock) && shouldRetry) {
                count++;
                try {
                    // 使用指数退避策略计算等待时间
                    long waitTime = lock.calculateBackoffTime(count);
                    totalWaitTime += waitTime;

                    // 记录重试日志
                    log.debug("加锁失败，第{}次重试: thread={} method={} alias={} key={} waitTime={}ms",
                            count, threadId, methodSignature.getMethod().getName(), locking.alias(), newKey, waitTime);

                    // 等待一段时间后重试
                    if (waitTime > 0) {
                        Thread.sleep(waitTime);
                    } else {
                        // 防止忙等
                        Thread.yield();
                    }
                } catch (InterruptedException e) {
                    // 恢复中断状态
                    Thread.currentThread().interrupt();
                    String errorMsg = String.format("获取锁过程中被中断: method=%s alias=%s key=%s retryCount=%d",
                            methodSignature.getMethod().getName(), locking.alias(), newKey, count);
                    log.warn(errorMsg);
                    throw new LockRuntimeException("获取锁过程中被中断", e);
                }

                // 重试获取锁
                tryLock = locking.time() > 0 ? lock.tryLock(newKey, locking.time(), locking.unit()) : lock.tryLock(newKey);

                // 更新重试条件
                shouldRetry = (retryCount > 0 && count < retryCount) || retryCount < 0;
            }
        }

        // 根据最终是否获取到锁，记录相应的日志
        if (Boolean.FALSE.equals(tryLock)) {
            String errorMsg = String.format("获取锁失败: method=%s alias=%s key=%s totalRetryTime=%dms retryCount=%d",
                    methodSignature.getMethod().getName(), locking.alias(), newKey, totalWaitTime, count);
            log.debug(errorMsg);
            throw new LockRuntimeException(errorMsg);
        } else {
            log.debug("加锁成功: thread={} method={} alias={} key={} duration={}ms",
                    threadId, methodSignature.getMethod().getName(), locking.alias(), newKey, duration);
        }
    }

}
