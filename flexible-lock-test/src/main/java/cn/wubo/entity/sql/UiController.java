package cn.wubo.entity.sql;

import cn.wubo.flexible.lock.exception.LockRuntimeException;
import cn.wubo.flexible.lock.propertes.FlexibleLockProperties;
import cn.wubo.flexible.lock.propertes.LockType;
import cn.wubo.flexible.lock.propertes.RetryStrategyType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

/**
 * UI controller for the demo application.
 *
 * <p>Exposes:
 * <ul>
 *   <li>{@code GET /} — Thymeleaf index page (the visual console);</li>
 *   <li>{@code GET /api/log} — JSON snapshot of recent attempts (polled by the page);</li>
 *   <li>{@code GET /api/invoke} — invoke a single {@code @Locking}-wrapped method;</li>
 *   <li>{@code GET /api/burst} — fire N concurrent invocations, return summary;</li>
 *   <li>{@code POST /api/clear} — reset the recorder.</li>
 * </ul>
 *
 * <p>The recorder and the {@link FlexibleLockProperties} are injected as
 * Spring beans. Every invocation (success or failure) is recorded with its
 * thread, key, status, and wall-clock duration so the page can render a
 * live feed.
 */
@Slf4j
@Controller
public class UiController {

    @Autowired
    private FlexibleLockProperties properties;

    @Autowired
    private DemoService demoService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private ResultRecorder recorder;

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("lockType", properties.getLockType());
        model.addAttribute("retryStrategyType", properties.getRetryStrategyType());
        model.addAttribute("retryCount", properties.getRetryCount());
        model.addAttribute("waitTime", properties.getWaitTime());
        model.addAttribute("demos", List.of(
                new DemoMeta("doWork", "单 key（参数拼接）", false),
                new DemoMeta("doWorkWithRetries", "显式重试 (retry=30, wait=1000ms)", false),
                new DemoMeta("doWorkWithShortWait", "快速失败 (retry=0, wait=100ms)", false),
                new DemoMeta("doWorkWithBeanRef", "Bean 引用 (@systemClock)", false),
                new DemoMeta("doWorkForMethod", "#method SpEL 引用", false)
        ));
        model.addAttribute("orderDemos", List.of(
                new DemoMeta("create", "类级注解：create (继承类级 key)", true),
                new DemoMeta("cancel", "类级注解：cancel (继承类级 key)", true),
                new DemoMeta("forceUpdate", "方法级 override：forceUpdate", true)
        ));
        return "index";
    }

    @GetMapping("/api/log")
    @ResponseBody
    public List<Map<String, Object>> log() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ResultRecorder.Entry e : recorder.snapshot()) {
            Map<String, Object> m = new HashMap<>();
            m.put("key", e.key());
            m.put("status", e.status());
            m.put("durationMs", e.durationMs());
            m.put("message", e.message());
            out.add(m);
        }
        return out;
    }

    @PostMapping("/api/clear")
    @ResponseBody
    public Map<String, Object> clear() {
        // Replace the recorder contents by re-creating it (Spring has no
        // direct "clear" on the existing instance).
        recorder.clear();
        return Map.of("cleared", true);
    }

    /**
     * Fire a single invocation and record it. Returns the raw result or
     * the exception message in JSON.
     */
    @GetMapping("/api/invoke")
    @ResponseBody
    public Map<String, Object> invoke(
            @RequestParam(name = "method") String method,
            @RequestParam(name = "key", defaultValue = "demo") String key,
            @RequestParam(name = "target", defaultValue = "demo") String target) {
        return runOne(target, method, key);
    }

    /**
     * Fire N concurrent invocations against the same key. The summary tells
     * the caller how many acquired the lock vs. how many were rejected with
     * {@link LockRuntimeException}.
     */
    @GetMapping("/api/burst")
    @ResponseBody
    public Map<String, Object> burst(
            @RequestParam(name = "method") String method,
            @RequestParam(name = "key", defaultValue = "burst") String key,
            @RequestParam(name = "target", defaultValue = "demo") String target,
            @RequestParam(name = "count", defaultValue = "10") int count) {
        long t0 = System.nanoTime();
        AtomicInteger success = new AtomicInteger();
        AtomicInteger failure = new AtomicInteger();
        List<CompletableFuture<Void>> futures = IntStream.range(0, count)
                .mapToObj(i -> CompletableFuture.runAsync(() -> {
                    runOneQuietly(target, method, key, success, failure);
                }))
                .toList();
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
        Map<String, Object> out = new HashMap<>();
        out.put("success", success.get());
        out.put("failure", failure.get());
        out.put("elapsedMs", elapsedMs);
        out.put("count", count);
        return out;
    }

    // ----------------- internals -----------------

    private Map<String, Object> runOne(String target, String method, String key) {
        long t0 = System.nanoTime();
        String status = "SUCCESS";
        String message = "";
        Object result = null;
        try {
            result = invokeMethod(target, method, key);
        } catch (LockRuntimeException e) {
            status = "FAILURE";
            message = "LockRuntimeException: " + e.getMessage();
        } catch (Exception e) {
            status = "ERROR";
            message = e.getClass().getSimpleName() + ": " + e.getMessage();
        } finally {
            long ms = (System.nanoTime() - t0) / 1_000_000;
            String resolvedKey = resolveKeyForRecording(target, method, key);
            recorder.record(resolvedKey, status, ms, message);
        }
        Map<String, Object> out = new HashMap<>();
        out.put("result", result);
        out.put("status", status);
        out.put("message", message);
        return out;
    }

    private void runOneQuietly(String target, String method, String key,
                                AtomicInteger success, AtomicInteger failure) {
        Map<String, Object> r = runOne(target, method, key);
        if ("SUCCESS".equals(r.get("status"))) {
            success.incrementAndGet();
        } else {
            failure.incrementAndGet();
        }
    }

    private Object invokeMethod(String target, String method, String key) {
        if ("demo".equals(target)) {
            return switch (method) {
                case "doWork" -> demoService.doWork(key);
                case "doWorkWithRetries" -> demoService.doWorkWithRetries(key);
                case "doWorkWithShortWait" -> demoService.doWorkWithShortWait(key);
                case "doWorkWithBeanRef" -> demoService.doWorkWithBeanRef(key);
                case "doWorkForMethod" -> demoService.doWorkForMethod();
                default -> throw new IllegalArgumentException("Unknown demo method: " + method);
            };
        }
        if ("order".equals(target)) {
            return switch (method) {
                case "create" -> orderService.create(key);
                case "cancel" -> orderService.cancel(key);
                case "forceUpdate" -> orderService.forceUpdate(key);
                default -> throw new IllegalArgumentException("Unknown order method: " + method);
            };
        }
        throw new IllegalArgumentException("Unknown target: " + target);
    }

    /**
     * The recorder logs the key AFTER the AOP aspect has resolved the SpEL
     * expression. We don't have access to the resolved key from outside the
     * aspect, so we approximate by reconstructing the key shape that the
     * aspect would have produced. This is best-effort: the SpEL may include
     * bean calls / method refs that we can't replicate here, in which case
     * the recorded key is just {@code "<method>(<key>)"} — still useful for
     * distinguishing which demo produced the entry.
     */
    private String resolveKeyForRecording(String target, String method, String key) {
        if ("demo".equals(target)) {
            return switch (method) {
                case "doWork" -> "doWork-" + key;
                case "doWorkWithRetries" -> "retry-" + key;
                case "doWorkWithShortWait" -> "strict-" + key;
                case "doWorkWithBeanRef" -> "clock-<dynamic>";
                case "doWorkForMethod" -> "DemoService:doWorkForMethod";
                default -> method + "(" + key + ")";
            };
        }
        if ("order".equals(target)) {
            return switch (method) {
                case "create", "cancel" -> "order-" + key;
                case "forceUpdate" -> "order-strict-" + key;
                default -> method + "(" + key + ")";
            };
        }
        return method + "(" + key + ")";
    }

    /** Meta info for the template — name, description, and which target it belongs to. */
    public record DemoMeta(String method, String description, boolean isOrder) {
        public String getMethod() { return method; }
        public String getDescription() { return description; }
        public boolean getIsOrder() { return isOrder; }
    }
}
