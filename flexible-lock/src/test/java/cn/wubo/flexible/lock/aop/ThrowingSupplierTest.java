package cn.wubo.flexible.lock.aop;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for {@link ThrowingSupplier}.
 *
 * <p>The interface exists for one reason: Spring's {@code Supplier.call()} and
 * {@code java.util.function.Supplier.get()} only permit {@code Exception}, not
 * the broader {@code Throwable}. Locked methods often declare checked exceptions
 * (e.g. {@code throws IOException}), and we don't want to wrap those in
 * {@code RuntimeException}. These tests pin down that the contract allows:
 *
 * <ul>
 *   <li>normal return values</li>
 *   <li>{@link RuntimeException}</li>
 *   <li>checked exceptions like {@link IOException}</li>
 *   <li>{@link Error} (e.g. {@link AssertionError}, {@link OutOfMemoryError})</li>
 * </ul>
 *
 * <p>If the interface signature ever regresses to {@code Exception} instead of
 * {@code Throwable}, these tests will fail to compile, alerting us before
 * runtime.
 */
class ThrowingSupplierTest {

    @Test
    void normalReturnValuePropagates() throws Throwable {
        ThrowingSupplier<String> supplier = () -> "hello";
        assertEquals("hello", supplier.get());
    }

    @Test
    void nullReturnIsPermitted() throws Throwable {
        // Body may legitimately return null (void methods, etc.).
        ThrowingSupplier<String> supplier = () -> null;
        assertEquals(null, supplier.get());
    }

    @Test
    void runtimeExceptionPropagates() {
        // RuntimeException is the default expected case for most failures.
        RuntimeException expected = new IllegalStateException("upstream is down");
        ThrowingSupplier<String> supplier = () -> { throw expected; };
        RuntimeException caught = assertThrows(RuntimeException.class, supplier::get);
        assertSame(expected, caught,
                "the same instance must propagate (no wrapping)");
    }

    @Test
    void checkedExceptionPropagatesWithoutWrapping() {
        // This is the whole reason ThrowingSupplier exists: a checked
        // exception declared on the locked method must surface to the caller
        // unchanged, not be hidden inside RuntimeException.
        IOException expected = new IOException("disk full");
        ThrowingSupplier<String> supplier = () -> { throw expected; };
        IOException caught = assertThrows(IOException.class, supplier::get);
        assertSame(expected, caught,
                "checked exception must propagate unchanged");
    }

    @Test
    void errorPropagates() {
        // Errors (OOM, AssertionError) should never be caught and wrapped —
        // doing so masks JVM-level failures.
        Error expected = new AssertionError("invariant violated");
        ThrowingSupplier<String> supplier = () -> { throw expected; };
        Error caught = assertThrows(Error.class, supplier::get);
        assertSame(expected, caught);
    }

    @Test
    void supplierCanCaptureStateViaClosure() throws Throwable {
        // Sanity: the interface behaves like a normal functional interface —
        // a lambda body can close over local state.
        AtomicReference<String> sink = new AtomicReference<>();
        ThrowingSupplier<String> supplier = () -> {
            sink.set("captured");
            return "result";
        };
        String result = supplier.get();
        assertEquals("captured", sink.get());
        assertEquals("result", result);
    }

    @Test
    void supplierCanBeUsedAsMethodArgument() throws Throwable {
        // Round-trip through a helper that accepts the interface.
        String captured = invokeAndCapture(() -> "method-arg");
        assertEquals("method-arg", captured);
    }

    private static String invokeAndCapture(ThrowingSupplier<String> body) throws Throwable {
        return body.get();
    }
}