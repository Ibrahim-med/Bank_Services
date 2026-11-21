package com.bankservices.bankservices_api.ratelimit;

/**
 * Rate-limited endpoint categories. Every API request belongs to exactly one
 * category, and each category has its own, independent buckets.
 *
 * <ul>
 *   <li>Public authentication endpoints are keyed by client IP only, so changing the
 *       submitted username/e-mail never yields a fresh bucket.</li>
 *   <li>Other endpoints are keyed by the authenticated user ID (or by client IP when
 *       the request is not authenticated).</li>
 * </ul>
 */
public enum RateLimitCategory {

    /** POST /api/auth/login */
    LOGIN(KeyStrategy.CLIENT_IP),

    /** POST /api/auth/activate */
    ACTIVATION(KeyStrategy.CLIENT_IP),

    /** POST /api/auth/resend-activation */
    RESEND_ACTIVATION(KeyStrategy.CLIENT_IP),

    /** POST /api/auth/forgot-password and POST /api/auth/reset-password (shared bucket) */
    PASSWORD_RESET(KeyStrategy.CLIENT_IP),

    /** POST /api/client/bills/{id}/pay */
    BILL_PAYMENT(KeyStrategy.USER_OR_IP),

    /** POST /api/transactions (deposits, withdrawals and transfers) */
    TRANSFER(KeyStrategy.USER_OR_IP),

    /** Every other /api/** request */
    GENERAL(KeyStrategy.USER_OR_IP);

    public enum KeyStrategy {
        CLIENT_IP,
        USER_OR_IP
    }

    private final KeyStrategy keyStrategy;

    RateLimitCategory(KeyStrategy keyStrategy) {
        this.keyStrategy = keyStrategy;
    }

    public KeyStrategy keyStrategy() {
        return keyStrategy;
    }
}
