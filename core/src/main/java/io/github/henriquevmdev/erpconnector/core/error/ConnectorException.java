package io.github.henriquevmdev.erpconnector.core.error;

/**
 * Every failure a connector can raise. The hierarchy is sealed, so a {@code switch} over it is checked for
 * exhaustiveness by the compiler. Messages never contain personal data, tokens or secrets (AGENTS.md, hard rule 4).
 */
public abstract sealed class ConnectorException extends RuntimeException
        permits AuthenticationException, RateLimitedException, TransientException, PermanentException,
        UnexpectedFormatException {

    protected ConnectorException(String message, Throwable cause) {
        super(message, cause);
    }

    /** Whether repeating the same call later can succeed. Drives {@code Retrier}. */
    public abstract boolean retryable();
}
