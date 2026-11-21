package com.bankservices.bankservices_api.ratelimit;

import com.bankservices.bankservices_api.ratelimit.RateLimitService.RateLimitDecision;

import io.github.bucket4j.TimeMeter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimitServiceTest {

    /** Controllable clock so refill can be tested without sleeping. */
    static final class FakeClock implements TimeMeter {
        private final AtomicLong nanos = new AtomicLong(1_000_000_000L);

        @Override
        public long currentTimeNanos() {
            return nanos.get();
        }

        @Override
        public boolean isWallClockBased() {
            return false;
        }

        void advance(Duration duration) {
            nanos.addAndGet(duration.toNanos());
        }
    }

    private FakeClock clock;
    private RateLimitService service;

    @BeforeEach
    void setUp() {
        RateLimitProperties properties = new RateLimitProperties();   // production defaults
        clock = new FakeClock();
        service = new RateLimitService(properties,
                new InMemoryRateLimitBucketStore(1_000, properties.longestWindow(), clock));
    }

    @Test
    void requestsBelowTheLimit_areAllowed_withDecreasingRemaining() {
        for (int i = 1; i <= 5; i++) {
            RateLimitDecision decision = service.consume(RateLimitCategory.LOGIN, "ip:203.0.113.1");
            assertThat(decision.allowed()).isTrue();
            assertThat(decision.limit()).isEqualTo(5);
            assertThat(decision.remaining()).isEqualTo(5 - i);
        }
    }

    @Test
    void requestAboveTheLimit_isRejected_withRetryAfterOfOneRefillPeriod() {
        consume(RateLimitCategory.LOGIN, "ip:203.0.113.1", 5);

        RateLimitDecision rejected = service.consume(RateLimitCategory.LOGIN, "ip:203.0.113.1");

        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.remaining()).isZero();
        // 5 per minute, greedy refill: one request comes back every 12 seconds.
        assertThat(rejected.retryAfterHeaderSeconds()).isEqualTo(12);
        assertThat(rejected.resetSeconds()).isEqualTo(60);
    }

    @Test
    void tokensComeBack_afterTheRetryAfterDelay() {
        consume(RateLimitCategory.LOGIN, "ip:203.0.113.1", 5);
        assertThat(service.consume(RateLimitCategory.LOGIN, "ip:203.0.113.1").allowed()).isFalse();

        clock.advance(Duration.ofSeconds(11));
        assertThat(service.consume(RateLimitCategory.LOGIN, "ip:203.0.113.1").allowed()).isFalse();

        clock.advance(Duration.ofSeconds(1));
        assertThat(service.consume(RateLimitCategory.LOGIN, "ip:203.0.113.1").allowed()).isTrue();
        assertThat(service.consume(RateLimitCategory.LOGIN, "ip:203.0.113.1").allowed()).isFalse();
    }

    @Test
    void fifteenMinuteWindows_areApplied() {
        consume(RateLimitCategory.RESEND_ACTIVATION, "ip:203.0.113.1", 3);

        RateLimitDecision rejected = service.consume(RateLimitCategory.RESEND_ACTIVATION, "ip:203.0.113.1");

        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.retryAfterHeaderSeconds()).isEqualTo(300);  // 15 min / 3
        assertThat(rejected.resetSeconds()).isEqualTo(900);
    }

    @Test
    void differentClients_haveIndependentLimits() {
        consume(RateLimitCategory.BILL_PAYMENT, "user:1", 5);

        assertThat(service.consume(RateLimitCategory.BILL_PAYMENT, "user:1").allowed()).isFalse();
        assertThat(service.consume(RateLimitCategory.BILL_PAYMENT, "user:2").allowed()).isTrue();
    }

    @Test
    void differentCategories_haveIndependentLimits() {
        consume(RateLimitCategory.TRANSFER, "user:1", 5);

        assertThat(service.consume(RateLimitCategory.TRANSFER, "user:1").allowed()).isFalse();
        assertThat(service.consume(RateLimitCategory.BILL_PAYMENT, "user:1").allowed()).isTrue();
        assertThat(service.consume(RateLimitCategory.GENERAL, "user:1").allowed()).isTrue();
    }

    @Test
    void configuredDefaults_matchTheSpecification() {
        RateLimitProperties defaults = new RateLimitProperties();

        assertThat(defaults.getLogin().getCapacity()).isEqualTo(5);
        assertThat(defaults.getLogin().getWindow()).isEqualTo(Duration.ofMinutes(1));
        assertThat(defaults.getActivation().getCapacity()).isEqualTo(5);
        assertThat(defaults.getActivation().getWindow()).isEqualTo(Duration.ofMinutes(15));
        assertThat(defaults.getResendActivation().getCapacity()).isEqualTo(3);
        assertThat(defaults.getPasswordReset().getCapacity()).isEqualTo(3);
        assertThat(defaults.getPasswordReset().getWindow()).isEqualTo(Duration.ofMinutes(15));
        assertThat(defaults.getBillPayment().getCapacity()).isEqualTo(5);
        assertThat(defaults.getTransfer().getCapacity()).isEqualTo(5);
        assertThat(defaults.getGeneral().getCapacity()).isEqualTo(100);
        assertThat(defaults.getGeneral().getWindow()).isEqualTo(Duration.ofMinutes(1));
        assertThat(defaults.getTrustedProxies()).isEmpty();
    }

    @Test
    void invalidConfiguration_isRejectedAtStartup() {
        RateLimitProperties properties = new RateLimitProperties();
        properties.getLogin().setCapacity(0);

        assertThatThrownBy(() -> new RateLimitService(properties,
                new InMemoryRateLimitBucketStore(10, Duration.ofMinutes(1))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LOGIN");
    }

    @Test
    void concurrentRequests_cannotExceedTheLimit() throws Exception {
        int threads = 32;
        int attemptsPerThread = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();

        for (int t = 0; t < threads; t++) {
            Callable<Integer> task = () -> {
                start.await();
                int allowed = 0;
                for (int i = 0; i < attemptsPerThread; i++) {
                    if (service.consume(RateLimitCategory.LOGIN, "ip:203.0.113.99").allowed()) {
                        allowed++;
                    }
                }
                return allowed;
            };
            results.add(pool.submit(task));
        }

        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        int totalAllowed = 0;
        for (Future<Integer> result : results) {
            totalAllowed += result.get();
        }

        // 640 simultaneous attempts on a 5-per-minute bucket (clock frozen): exactly 5 pass.
        assertThat(totalAllowed).isEqualTo(5);
    }

    @Test
    void idleBuckets_areEvicted_soMemoryStaysBounded() {
        InMemoryRateLimitBucketStore store = new InMemoryRateLimitBucketStore(3, Duration.ofMinutes(15));
        RateLimitService bounded = new RateLimitService(new RateLimitProperties(), store);

        for (int i = 0; i < 50; i++) {
            bounded.consume(RateLimitCategory.GENERAL, "ip:203.0.113." + i);
        }

        assertThat(store.size()).isLessThanOrEqualTo(3);
    }

    private void consume(RateLimitCategory category, String key, int times) {
        for (int i = 0; i < times; i++) {
            assertThat(service.consume(category, key).allowed()).isTrue();
        }
    }
}
