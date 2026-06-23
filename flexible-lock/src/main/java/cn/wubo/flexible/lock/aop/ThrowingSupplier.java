package cn.wubo.flexible.lock.aop;

/**
 * 与 {@link java.util.function.Supplier} 等价,但允许抛出 {@link Throwable}
 * (包括 {@link Error} 和任意受检异常)。
 *
 * <p>用于将 {@code @Locking} 标注方法的原始受检异常透传给调用方,而不是
 * 包装成 {@link RuntimeException} 抹掉方法签名上的 throws 声明。
 */
@FunctionalInterface
public interface ThrowingSupplier<T> {

    T get() throws Throwable;
}
