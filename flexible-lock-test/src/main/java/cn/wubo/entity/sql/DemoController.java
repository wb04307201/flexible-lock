package cn.wubo.entity.sql;

import cn.wubo.flexible.lock.exception.LockRuntimeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

/**
 * The original demo controller — fires 10 concurrent {@code @Locking}-wrapped
 * invocations and returns "success". Kept for backward compatibility with
 * the README example.
 *
 * <p>The richer UI lives in {@link UiController}, which exposes
 * per-method invocations and surfaces {@link LockRuntimeException} instead of
 * hiding it behind a blanket "success".
 */
@Slf4j
@RestController
@RequestMapping(value = "test")
public class DemoController {

    @Autowired
    DemoService demoService;

    @GetMapping(value = "lock")
    public Map<String, Object> lock(@RequestParam(name = "key") String key) {
        Map<String, Object> response = new HashMap<>();
        long t0 = System.nanoTime();
        AtomicInteger failure = new AtomicInteger();
        try {
            int callers = 10;
            CompletableFuture<?>[] futures = IntStream.range(0, callers)
                    .mapToObj(i -> CompletableFuture.runAsync(() -> {
                        try {
                            demoService.doWork(key);
                        } catch (LockRuntimeException e) {
                            failure.incrementAndGet();
                        }
                    }))
                    .toArray(CompletableFuture[]::new);
            CompletableFuture.allOf(futures).join();
        } catch (Exception e) {
            response.put("error", e.getMessage());
        }
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
        int f = failure.get();
        response.put("success", 10 - f);
        response.put("failure", f);
        response.put("elapsedMs", elapsedMs);
        return response;
    }
}
