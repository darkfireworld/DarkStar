package org.dfw.darkstar.tcc.api;

/**
 * Tcc Exception
 */
public class TccException extends RuntimeException {
    public TccException() {
    }

    public TccException(String message) {
        super(message);
    }

    public TccException(String message, Throwable cause) {
        super(message, cause);
    }

    public TccException(Throwable cause) {
        super(cause);
    }

    public TccException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
