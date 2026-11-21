package com.bankservices.bankservices_api.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Applies the configured limit of a category to one client key.
 *
 * <p>Token bucket with greedy refill: a limit of "N per window" allows an
 * immediate burst of N requests, then gives one request back every window/N.
 * The sustained rate is therefore N per window (e.g. login: one attempt every
 * 12 seconds once the 5 are used). Note that right after an idle period a client
 * can use the full burst and then the tokens refilled meanwhile, so the first
 * window can see up to almost 2N requests; every following window is capped at N.
 */
public class RateLimitService {

    private final RateLimitBucketStore store;
    private final Map<RateLimitCategory, BucketConfiguration> configurations = new EnumMap<>(RateLimitCategory.class);
    private final Map<RateLimitCategory, Integer> capacities = new EnumMap<>(RateLimitCategory.class);

    public RateLimitService(RateLimitProperties properties, RateLimitBucketStore store) {
        this.store = store;

        properties.limits().forEach((category, limit) -> {
            if (limit == null || limit.getCapacity() <= 0 || limit.getWindow() == null
                    || limit.getWindow().isZero() || limit.getWindow().isNegative()) {
                throw new IllegalStateException("Invalid rate limit configuration for " + category);
            }

            Bandwidth bandwidth = Bandwidth.builder()
                    .capacity(limit.getCapacity())
                    .refillGreedy(limit.getCapacity(), limit.getWindow())
                    .build();

            configurations.put(category, BucketConfiguration.builder().addLimit(bandwidth).build());
            capacities.put(category, limit.getCapacity());
        });
    }

    /**
     * Consumes one request for {@code clientKey} in {@code category}.
     * The stored key is "category:clientKey"; the client key is either "ip:&lt;address&gt;"
     * or "user:&lt;numeric id&gt;" and never contains credentials or personal data.
     */
    public RateLimitDecision consume(RateLimitCategory category, String clientKey) {
        ConsumptionProbe probe = store.tryConsume(
                category.name().toLowerCase() + ":" + clientKey,
                configurations.get(category));

        return new RateLimitDecision(
                category,
                probe.isConsumed(),
                capacities.get(category),
                probe.getRemainingTokens(),
                ceilSeconds(probe.getNanosToWaitForRefill()),
                ceilSeconds(probe.getNanosToWaitForReset()));
    }

    private static long ceilSeconds(long nanos) {
        if (nanos <= 0) {
            return 0;
        }
        return (nanos + TimeUnit.SECONDS.toNanos(1) - 1) / TimeUnit.SECONDS.toNanos(1);
    }

    /**
     * Outcome of one consumption attempt.
     *
     * @param retryAfterSeconds seconds until the next request is allowed (when rejected)
     * @param resetSeconds      seconds until the quota is completely restored
     */
    public record RateLimitDecision(
            RateLimitCategory category,
            boolean allowed,
            int limit,
            long remaining,
            long retryAfterSeconds,
            long resetSeconds) {

        /** Retry-After must be at least one second when the request is rejected. */
        public long retryAfterHeaderSeconds() {
            return Math.max(1, retryAfterSeconds);
        }
    }
}
