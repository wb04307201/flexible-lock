package cn.wubo.flexible.lock.exception;

public class LockRuntimeException extends RuntimeException {

    public LockRuntimeException(Throwable cause) {
        super(cause);
    }

    public LockRuntimeException(String message) {
        super(message);
    }
}
