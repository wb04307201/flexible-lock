package cn.wubo.flexible.lock.aop;

import cn.wubo.flexible.lock.annotation.Locking;
import cn.wubo.flexible.lock.core.ILock;
import cn.wubo.flexible.lock.exception.LockRuntimeException;
import cn.wubo.flexible.lock.retry.IRetryStrategy;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.Ordered;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.annotation.Order;
import org.springframework.expression.BeanResolver;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParseException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LockAnnotationAspect {

    private final ILock lock;
    private final IRetryStrategy retryStrategy;
    private final BeanResolver beanResolver;
    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    /**
     * 同一份 SpEL 字符串会被反复解析(每次 {@code @Locking} 调用都解析一次),
     * 但热路径上的 key 表达式通常是固定的几个值。
     * 按 spel 字符串缓存编译后的 {@link Expression},避免每次调用都重新解析。
     */
    private final Map<String, Expression> expressionCache = new ConcurrentHashMap<>();

    public LockAnnotationAspect(ILock lock, IRetryStrategy retryStrategy, BeanFactory beanFactory) {
        this.lock = lock;
        this.retryStrategy = retryStrategy;
        this.beanResolver = beanFactory != null ? new BeanFactoryResolver(beanFactory) : null;
    }

    /**
     * 仅匹配方法上有 {@code @Locking} 的调用；类级注解通过反射解析，
     * 避免使用 {@code @annotation || @within} 复合 pointcut 在某些
     * Spring AOP / AspectJ 版本下产生的"参数绑定失败"问题。
     */
    @Around("@annotation(cn.wubo.flexible.lock.annotation.Locking)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Locking effective = resolveLocking(pjp, signature.getMethod());
        return executeWithLock(effective, signature.getMethod(), pjp.getArgs(), pjp::proceed);
    }

    /**
     * 解析实际生效的 {@code @Locking}:方法级优先,否则使用类级。
     */
    private Locking resolveLocking(ProceedingJoinPoint pjp, Method method) {
        Locking methodLevel = method.getAnnotation(Locking.class);
        if (methodLevel != null) {
            return methodLevel;
        }
        Object target = pjp.getTarget();
        return target != null ? target.getClass().getAnnotation(Locking.class) : null;
    }

    /**
     * 加锁、执行 body、在 finally 中释放锁。
     *
     * <p>该方法是锁生命周期的可测试接缝：SpEL key 仅解析一次，锁只有在成功获取时才会
     * 被释放，且 {@code retryCount=N} 表示初次尝试之后还会再重试 N 次（共 N+1 次；
     * N=0 表示仅尝试一次）。
     *
     * <p>body 通过 {@link ThrowingSupplier} 调用，使得目标方法声明的 checked 异常
     * 能够原样向上传递，而不是被包成 {@link RuntimeException}。
     */
    public Object executeWithLock(Locking locking, Method method, Object[] args, ThrowingSupplier body) throws Throwable {
        String key = resolveKey(locking.key(), method, args);
        long baseWaitTime = locking.waitTime() > -1 ? locking.waitTime() : lock.getWaitTime();
        int retryCount = locking.retryCount() > -1 ? locking.retryCount() : lock.getRetryCount();

        log.debug("acquiring lock: key={} retryCount={} baseWaitTime={}ms", key, retryCount, baseWaitTime);

        if (!acquireWithRetry(key, baseWaitTime, retryCount)) {
            String message = String.format("failed to acquire lock: key=%s", key);
            log.debug(message);
            throw new LockRuntimeException(message);
        }

        log.debug("lock acquired: key={}", key);
        try {
            return body.get();
        } finally {
            try {
                lock.unLock(key);
                log.debug("lock released: key={}", key);
            } catch (RuntimeException e) {
                // 不要因为 unLock 失败遮蔽 body 的异常(例如 Redisson 锁租约到期)。
                // 仅记录,继续向上抛出 body 的结果/异常。
                log.warn("failed to release lock cleanly: key={} err={}", key, e.toString());
            }
        }
    }

    private boolean acquireWithRetry(String key, long baseWaitTime, int retryCount) {
        // retryCount = N → 初次失败后最多再重试 N 次
        // retryCount = 0 → 仅尝试一次（与 README 中"0 表示不重试"的描述保持一致）
        int totalAttempts = retryCount + 1;
        for (int attempt = 1; attempt <= totalAttempts; attempt++) {
            long waitTime = retryStrategy.calculateWaitTime(baseWaitTime, attempt - 1);
            try {
                boolean acquired = waitTime > 0
                        ? lock.tryLock(key, waitTime)
                        : lock.tryLock(key);
                log.debug("tryLock: key={} attempt={} waitTime={}ms acquired={}", key, attempt, waitTime, acquired);
                if (acquired) {
                    return true;
                }
            } catch (RuntimeException e) {
                // 透传原始异常:传输层错误(连接失败、超时等)重试不会自愈,
                // 直接把根因抛给调用方,避免浪费 retry 预算并保留可观测性。
                log.debug("tryLock threw: key={} attempt={} err={}", key, attempt, e.toString());
                throw new LockRuntimeException(
                        String.format("tryLock failed for key=%s on attempt %d/%d", key, attempt, totalAttempts),
                        e);
            }
        }
        return false;
    }

    private String resolveKey(String spel, Method method, Object[] args) {
        // 使用 StandardEvaluationContext 是为了支持 @beanName.method() 形式的
        // Spring Bean 引用(SimpleEvaluationContext 没有 setBeanResolver)。
        // 已知代价:T(...) 类型引用和任意方法调用是可用的——但 @Locking.key()
        // 是编译期注解参数,不是运行期用户输入,所以 SpEL 注入面是开发者的
        // 编码失误,而非外部攻击。
        StandardEvaluationContext context = new StandardEvaluationContext();
        if (beanResolver != null) {
            context.setBeanResolver(beanResolver);
        }
        context.setVariable("method", method);

        String[] paramNames = parameterNameDiscoverer.getParameterNames(method);
        if (paramNames != null && args.length == paramNames.length) {
            for (int i = 0; i < paramNames.length; i++) {
                context.setVariable(paramNames[i], args[i]);
            }
        }

        try {
            // 同一份 SpEL 字符串会被反复解析,按 spel 字符串缓存编译结果。
            Expression expression = expressionCache.computeIfAbsent(spel, parser::parseExpression);
            return expression.getValue(context, String.class);
        } catch (ParseException | EvaluationException e) {
            throw new LockRuntimeException(
                    String.format("invalid SpEL expression for @Locking key on %s: %s",
                            method.getName(), e.getMessage()),
                    e);
        }
    }
}
