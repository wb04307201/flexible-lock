package cn.wubo.flexible.lock.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link LockRuntimeException}.
 *
 * Callers need all three constructor shapes:
 *   - (String) — simple message
 *   - (Throwable) — wrap a cause with no extra message
 *   - (String, Throwable) — descriptive message plus the underlying cause
 *
 * <p>In addition to the constructor shapes, the class must behave correctly
 * as a {@link RuntimeException}: catchable by {@code catch(RuntimeException)},
 * polymorphic with the {@code Throwable} contract, stack-trace preserving,
 * and final (so users can't accidentally add state that diverges from the
 * lock runtime contract).
 */
class LockRuntimeExceptionTest {

    @Test
    void messageOnlyConstructorPreservesMessage() {
        LockRuntimeException ex = new LockRuntimeException("oops");
        assertEquals("oops", ex.getMessage());
        assertTrue(ex.getCause() == null);
    }

    @Test
    void causeOnlyConstructorPreservesCause() {
        Throwable cause = new IllegalStateException("root");
        LockRuntimeException ex = new LockRuntimeException(cause);
        assertSame(cause, ex.getCause());
    }

    @Test
    void messageAndCauseConstructorPreservesBoth() {
        Throwable cause = new IllegalStateException("root");
        LockRuntimeException ex = new LockRuntimeException("oops", cause);
        assertEquals("oops", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @Test
    void nullMessageIsPermittedInMessageOnlyConstructor() {
        // Some call sites pass null (e.g. wrapping a Throwable with no extra
        // context). getMessage() must return null, NOT throw.
        LockRuntimeException ex = new LockRuntimeException((String) null);
        assertNull(ex.getMessage());
        assertNull(ex.getCause());
    }

    @Test
    void emptyMessageIsPreservedVerbatim() {
        LockRuntimeException ex = new LockRuntimeException("");
        assertEquals("", ex.getMessage());
    }

    @Test
    void nullCauseIsPermittedInCauseOnlyConstructor() {
        // Defensive: if someone calls `new LockRuntimeException((Throwable) null)`,
        // we must not NPE. getCause() should return null.
        LockRuntimeException ex = new LockRuntimeException((Throwable) null);
        assertNull(ex.getCause());
    }

    @Test
    void isRuntimeExceptionAndThrowable() {
        // LockRuntimeException must be catchable both as a RuntimeException
        // (for Spring's generic handling) and as a Throwable (for low-level
        // catch-and-log patterns).
        LockRuntimeException ex = new LockRuntimeException("x");
        assertTrue(ex instanceof RuntimeException);
        assertTrue(ex instanceof Throwable);
    }

    @Test
    void stackTraceIsPopulatedByJVM() {
        // The stack trace should reflect where the exception was thrown,
        // even when constructed with a cause.
        Throwable cause = new IllegalStateException("root");
        LockRuntimeException ex = new LockRuntimeException("wrapping", cause);
        StackTraceElement[] trace = ex.getStackTrace();
        assertNotNull(trace);
        assertTrue(trace.length > 0,
                "stack trace must contain at least one frame");
        // The top frame should be inside this test class (since we threw
        // it just above).
        assertEquals(getClass().getName(), trace[0].getClassName(),
                "top frame should be in this test class");
    }

    @Test
    void classIsFinalSoCallersCannotExtendIt() {
        // `final` on the class prevents users from subclassing and adding
        // semantics that downstream code (e.g. retry handlers) might not
        // recognize. This guards the contract.
        assertTrue(java.lang.reflect.Modifier.isFinal(
                LockRuntimeException.class.getModifiers()),
                "LockRuntimeException must be final to prevent divergent subclasses");
    }

    @Test
    void canBeThrownAndCaughtAsRuntimeException() {
        // End-to-end shape: verify the exception round-trips through a
        // try/catch block matching the parent type.
        RuntimeException caught = null;
        try {
            throw new LockRuntimeException("from test");
        } catch (RuntimeException e) {
            caught = e;
        }
        assertNotNull(caught);
        assertEquals("from test", caught.getMessage());
        assertTrue(caught instanceof LockRuntimeException);
    }

    @Test
    void suppressedExceptionsArePreservedByDefault() {
        // Try-with-resources and explicit addSuppressed calls must not be
        // silently dropped — the inherited Throwable contract is what
        // enables patterns like "lock acquired, body threw, release also
        // threw" to keep both exceptions visible.
        LockRuntimeException primary = new LockRuntimeException("primary");
        RuntimeException secondary = new RuntimeException("secondary");
        primary.addSuppressed(secondary);
        assertEquals(1, primary.getSuppressed().length);
        assertSame(secondary, primary.getSuppressed()[0]);
    }
}
