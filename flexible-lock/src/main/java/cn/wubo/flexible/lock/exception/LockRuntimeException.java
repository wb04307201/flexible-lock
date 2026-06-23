package cn.wubo.flexible.lock.exception;

/**
 * Flexible-Lock starter 抛出的运行时异常。
 *
 * <p>触发场景:
 * <ul>
 *   <li>SpEL 表达式解析或求值失败(例如 key 写错或引用了不存在的变量);</li>
 *   <li>在 {@code retryCount+1} 次尝试内未能获取锁;</li>
 *   <li>底层 {@code tryLock} 抛出传输层异常(连接失败、超时等),原始异常作为 cause 透传;</li>
 *   <li>Zookeeper 的 {@link InterruptedException} 包装。</li>
 * </ul>
 */
public final class LockRuntimeException extends RuntimeException {

    public LockRuntimeException(Throwable cause) {
        super(cause);
    }

    public LockRuntimeException(String message) {
        super(message);
    }

    public LockRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }
}
