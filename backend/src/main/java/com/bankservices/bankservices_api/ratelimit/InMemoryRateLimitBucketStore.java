package com.bankservices.bankservices_api.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;

import java.time.Duration;

/**
 * Single-instance bucket store.
 *
 * <ul>
 *   <li>Buckets are created atomically per key (Caffeine {@code get(key, loader)}),
 *       and Bucket4j's local buckets are thread-safe (lock-free compare-and-swap),
 *       so concurrent requests can never consume more tokens than configured.</li>
 *   <li>Memory is bounded: at most {@code maximumSize} buckets, and a bucket idle for
 *       longer than the longest window is dropped (it would be full again anyway).</li>
 *   <li>Limitations: state is lost on restart and is not shared between several
 *       application instances. Use a distributed store (e.g. Redis) when scaling out.</li>
 * </ul>
 */
public class InMemoryRateLimitBucketStore implements RateLimitBucketStore {

    private final Cache<String, Bucket> buckets;
    private final TimeMeter timeMeter;

    public InMemoryRateLimitBucketStore(long maximumSize, Duration idleExpiry) {
        this(maximumSize, idleExpiry, TimeMeter.SYSTEM_NANOTIME);
    }

    /** Visible for tests: a custom clock lets tests simulate the passing of time. */
    public InMemoryRateLimitBucketStore(long maximumSize, Duration idleExpiry, TimeMeter timeMeter) {
        this.buckets = Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .expireAfterAccess(idleExpiry)
                .build();
        this.timeMeter = timeMeter;
    }

    @Override
    public ConsumptionProbe tryConsume(String key, BucketConfiguration configuration) {
        Bucket bucket = buckets.get(key, ignored -> Bucket.builder()
                .withCustomTimePrecision(timeMeter)
                .addLimit(configuration.getBandwidths()[0])
                .build());

        return bucket.tryConsumeAndReturnRemaining(1);
    }

    long size() {
        buckets.cleanUp();
        return buckets.estimatedSize();
    }
}
