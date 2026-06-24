package cn.wubo.flexible.lock.annotation;

import java.lang.annotation.*;

/**
 * 在方法(或类)上加锁。可与 Spring AOP 配合,在方法调用前后自动加锁和解锁。
 *
 * <p>典型用法:
 * <pre>{@code
 * @Locking(key = "#userId + '-' + #orderId")
 * public void processOrder(String userId, String orderId) { ... }
 * }</pre>
 *
 * <p>也可以放在类上,对类内所有方法生效;方法上若同时存在 {@code @Locking},
 * 则方法级的注解覆盖类级。
 *
 * <p>注意:
 * <ul>
 *   <li>基于 Spring AOP 代理实现,同类内部的 {@code this.xxx()} 调用不会触发代理,锁不会生效。</li>
 *   <li>{@code key} 必须是合法的 SpEL 表达式;参数通过 {@code #参数名} 引用,Spring Bean
 *       通过 {@code @beanName} 引用。</li>
 *   <li>{@code waitTime / retryCount = -1} 表示使用全局 {@code flexible.lock.*} 配置;
 *       {@code retryCount = 0} 表示仅尝试一次不重试;{@code N > 0} 表示初次失败后再重试 N 次,
 *       共 N+1 次尝试。</li>
 * </ul>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface Locking {

    /**
     * 锁的 key,支持 SpEL 表达式(包括 {@code @beanName} 引用)。
     */
    String key();

    /**
     * 锁的超时时间(毫秒)。
     * <p>{@code -1} 表示使用 {@code flexible.lock.waitTime} 全局配置。
     */
    long waitTime() default -1;

    /**
     * 在初次尝试失败后的重试次数。
     * <p>{@code -1} 表示使用 {@code flexible.lock.retryCount} 全局配置;
     * {@code 0} 表示仅尝试一次不重试;{@code N > 0} 表示共 N+1 次尝试。
     */
    int retryCount() default -1;
}
