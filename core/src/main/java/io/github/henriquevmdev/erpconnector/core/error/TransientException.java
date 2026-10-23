package io.github.henriquevmdev.erpconnector.core.error;

/** A failure that may pass on its own: timeout, connection reset, HTTP 5xx, database deadlock. Retried. */
public final class TransientException extends ConnectorException {

    public TransientException(String message, Throwable cause) {
        super(message, cause);
    }

    public TransientException(String message) {
        this(message, null);
    }

    @Override
    public boolean retryable() {
        return true;
    }
}
