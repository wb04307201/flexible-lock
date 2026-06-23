package cn.wubo.entity.sql;

import cn.wubo.flexible.lock.annotation.Locking;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Demonstrates <b>class-level</b> {@code @Locking}: every method on the
 * class that doesn't have its own {@code @Locking} inherits the class-level
 * key, waitTime and retryCount.
 *
 * <p>Used by the UI to show the difference between a class-level default and
 * a method-level override side by side.
 */
@Slf4j
@Service
@Locking(key = "'order-' + #orderId", waitTime = 5000L, retryCount = 5)
public class OrderService {

    /** Inherits the class-level {@code @Locking} (no method-level annotation). */
    public String create(String orderId) {
        work();
        return "created:" + orderId;
    }

    /** Inherits the class-level {@code @Locking}. */
    public String cancel(String orderId) {
        work();
        return "cancelled:" + orderId;
    }

    /**
     * Method-level override: same key shape, but a tight wait + zero retries
     * so concurrent calls on the same {@code orderId} surface a
     * {@code LockRuntimeException} quickly.
     */
    @Locking(key = "'order-strict-' + #orderId", retryCount = 0, waitTime = 50L)
    public String forceUpdate(String orderId) {
        work();
        return "force-updated:" + orderId;
    }

    private static void work() {
        try {
            Thread.sleep(800);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
