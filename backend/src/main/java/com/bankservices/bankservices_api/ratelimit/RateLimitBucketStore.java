package com.bankservices.bankservices_api.ratelimit;

import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;

/**
 * Where buckets live. The in-memory implementation is used for a single
 * application instance; a distributed implementation (e.g. Bucket4j's Redis
 * ProxyManager) can replace it without touching the filter or the service.
 */
public interface RateLimitBucketStore {

    /**
     * Atomically tries to take one token from the bucket identified by {@code key},
     * creating it from {@code configuration} on first use.
     */
    ConsumptionProbe tryConsume(String key, BucketConfiguration configuration);
}
