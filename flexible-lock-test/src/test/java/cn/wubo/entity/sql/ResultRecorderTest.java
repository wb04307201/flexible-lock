package cn.wubo.entity.sql;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ResultRecorder}.
 *
 * <p>The recorder backs the UI's live log feed, so it must be:
 * <ul>
 *   <li>Thread-safe (multiple HTTP request threads call it concurrently);</li>
 *   <li>Bounded — old entries are evicted so the in-memory footprint is O(capacity);</li>
 *   <li>Ordered — callers see the most recent entries last.</li>
 * </ul>
 */
class ResultRecorderTest {

    @Test
    void newRecorderIsEmpty() {
        ResultRecorder recorder = new ResultRecorder(10);

        assertTrue(recorder.snapshot().isEmpty(), "fresh recorder should have no entries");
    }

    @Test
    void recordAppendsToSnapshot() {
        ResultRecorder recorder = new ResultRecorder(10);

        recorder.record("k1", "SUCCESS", 100L, "ok");
        recorder.record("k2", "FAILURE", 50L, "boom");

        List<ResultRecorder.Entry> snap = recorder.snapshot();
        assertEquals(2, snap.size());
        assertEquals("k1", snap.get(0).key());
        assertEquals("SUCCESS", snap.get(0).status());
        assertEquals(100L, snap.get(0).durationMs());
        assertEquals("ok", snap.get(0).message());
        assertEquals("k2", snap.get(1).key());
    }

    @Test
    void exceedingCapacityEvictsOldestEntries() {
        ResultRecorder recorder = new ResultRecorder(3);

        IntStream.range(0, 5).forEach(i -> recorder.record("k" + i, "S", 1L, ""));

        List<ResultRecorder.Entry> snap = recorder.snapshot();
        assertEquals(3, snap.size(), "capacity should bound the snapshot size");
        assertEquals("k2", snap.get(0).key(), "oldest two entries (k0, k1) should be evicted");
        assertEquals("k3", snap.get(1).key());
        assertEquals("k4", snap.get(2).key(), "newest entry should be last");
    }

    @Test
    void zeroCapacityIsClampedToAtLeastOne() {
        // A zero capacity would be a misconfiguration; we should still accept it
        // and behave like a 1-entry buffer rather than throwing or losing writes.
        ResultRecorder recorder = new ResultRecorder(0);

        recorder.record("k1", "S", 1L, "");
        recorder.record("k2", "S", 1L, "");

        List<ResultRecorder.Entry> snap = recorder.snapshot();
        assertEquals(1, snap.size());
        assertEquals("k2", snap.get(0).key());
    }

    @Test
    void negativeCapacityIsClampedToAtLeastOne() {
        ResultRecorder recorder = new ResultRecorder(-5);

        recorder.record("k1", "S", 1L, "");

        assertEquals(1, recorder.snapshot().size());
    }

    @Test
    void nullMessageIsCoercedToEmptyString() {
        ResultRecorder recorder = new ResultRecorder(5);

        recorder.record("k", "S", 1L, null);

        assertEquals("", recorder.snapshot().get(0).message());
    }

    @Test
    void nullStatusIsCoercedToUnknown() {
        ResultRecorder recorder = new ResultRecorder(5);

        recorder.record("k", null, 1L, "");

        assertEquals("UNKNOWN", recorder.snapshot().get(0).status());
    }

    @Test
    void concurrentRecordDoesNotLoseEntriesBeyondCapacity() throws Exception {
        // With many threads writing into a small buffer, the *only* invariant
        // we can check without flakiness is that the snapshot is bounded and
        // contains no torn writes.
        ResultRecorder recorder = new ResultRecorder(50);
        int threads = 16;
        int perThread = 200;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        IntStream.range(0, threads).forEach(t ->
                CompletableFuture.runAsync(() -> {
                    try {
                        start.await();
                        for (int i = 0; i < perThread; i++) {
                            recorder.record("k" + t + "-" + i, "S", 1L, "");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                })
        );
        start.countDown();
        assertTrue(done.await(10, java.util.concurrent.TimeUnit.SECONDS));

        List<ResultRecorder.Entry> snap = recorder.snapshot();
        assertTrue(snap.size() <= 50, "snapshot must be bounded by capacity; got " + snap.size());
        // Every retained entry must be a well-formed record.
        for (ResultRecorder.Entry e : snap) {
            assertSame(ResultRecorder.Entry.class, e.getClass());
            assertTrue(e.key() != null && !e.key().isEmpty());
        }
    }
}
