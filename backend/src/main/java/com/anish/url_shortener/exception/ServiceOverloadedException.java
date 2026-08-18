package com.anish.url_shortener.exception;

/**
 * Thrown when a database-bound path is already at its concurrency limit.
 *
 * <p>Deliberately a fast 503 rather than a queued request that eventually times out. Past
 * capacity the request was not going to succeed either way; failing immediately frees the
 * thread, tells the caller when to come back, and keeps latency for everyone else flat.
 */
public class ServiceOverloadedException extends RuntimeException {

    private final long retryAfterSeconds;

    public ServiceOverloadedException(long retryAfterSeconds) {
        super("Service is at capacity");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
