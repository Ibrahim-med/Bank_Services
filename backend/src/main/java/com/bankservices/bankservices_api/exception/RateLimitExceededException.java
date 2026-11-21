package com.bankservices.bankservices_api.exception;

/**
 * Raised by the rate-limit filter when a client exceeds its quota.
 * Mapped to HTTP 429 by GlobalExceptionHandler. It deliberately carries no
 * information about the client key or the internal limiter.
 */
public class RateLimitExceededException extends RuntimeException {

    public static final String MESSAGE =
            "You have made too many requests. Please wait before trying again.";

    private final long retryAfterSeconds;

    public RateLimitExceededException(long retryAfterSeconds) {
        super(MESSAGE);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
