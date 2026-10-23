package io.github.henriquevmdev.erpconnector.core.error;

/** Credentials rejected or missing permissions (HTTP 401/403, database login failure). Never retried. */
public final class AuthenticationException extends ConnectorException {

    public AuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }

    public AuthenticationException(String message) {
        this(message, null);
    }

    @Override
    public boolean retryable() {
        return false;
    }
}
