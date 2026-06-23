package cn.wubo.entity.sql;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * Bounded, thread-safe ring buffer of recent lock attempts, backing the UI's
 * live log feed.
 *
 * <p>Entries are kept in insertion order and the oldest are evicted once the
 * configured capacity is exceeded. A single intrinsic lock guards both
 * {@link #record} and {@link #snapshot()} — the UI polls the log feed at
 * human-tick frequencies (1 Hz), so a coarse lock is simpler and faster than
 * a lock-free design here.
 */
@Component
public class ResultRecorder {

    /**
     * One recorded attempt.
     *
     * @param key        the lock key (after SpEL resolution)
     * @param status     {@code SUCCESS}, {@code FAILURE}, or {@code UNKNOWN} when null is passed
     * @param durationMs wall-clock time the locked section took
     * @param message    free-form context (exception class, etc.) — never null
     */
    public record Entry(String key, String status, long durationMs, String message) {}

    private static final int MIN_CAPACITY = 1;
    private static final int DEFAULT_CAPACITY = 100;
    private static final String UNKNOWN_STATUS = "UNKNOWN";

    private final int capacity;
    private final Deque<Entry> entries;

    /** Spring entry point: defaults to 100 entries. */
    public ResultRecorder() {
        this(DEFAULT_CAPACITY);
    }

    public ResultRecorder(int capacity) {
        this.capacity = Math.max(MIN_CAPACITY, capacity);
        this.entries = new ArrayDeque<>(this.capacity);
    }

    /**
     * Append a new entry, evicting the oldest if the buffer is full.
     *
     * @param key        the lock key (null is stored as-is)
     * @param status     the status — null is coerced to {@code "UNKNOWN"}
     * @param durationMs how long the locked section ran
     * @param message    free-form context — null is coerced to empty string
     */
    public void record(String key, String status, long durationMs, String message) {
        String safeStatus = (status == null) ? UNKNOWN_STATUS : status;
        String safeMessage = (message == null) ? "" : message;
        Entry e = new Entry(key, safeStatus, durationMs, safeMessage);
        synchronized (entries) {
            while (entries.size() >= capacity) {
                entries.pollFirst();
            }
            entries.addLast(e);
        }
    }

    /**
     * Return a read-only snapshot of the current entries in oldest-first order.
     */
    public List<Entry> snapshot() {
        synchronized (entries) {
            return List.copyOf(entries);
        }
    }

    /** Drop all recorded entries. Used by the UI's "clear log" button. */
    public void clear() {
        synchronized (entries) {
            entries.clear();
        }
    }

    /** Visible for tests: the resolved (post-clamp) capacity. */
    int capacity() {
        return capacity;
    }

    /**
     * Convenience for building a mutable copy when callers need to mutate.
     * The public API is intentionally read-only via {@link #snapshot()}.
     */
    static List<Entry> unmodifiableCopy(List<Entry> in) {
        return Collections.unmodifiableList(new java.util.ArrayList<>(in));
    }
}
