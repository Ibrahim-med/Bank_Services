package com.bankservices.bankservices_api.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Rate-limit configuration ({@code app.rate-limit.*}). The defaults below apply
 * when a property is not set; every value can be overridden in
 * application.properties or through environment variables.
 */
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

    /** Master switch (disabled in the test profile, enabled by default). */
    private boolean enabled = true;

    /**
     * IP addresses or CIDR ranges of reverse proxies that are allowed to report the
     * client address in X-Forwarded-For. Empty (the default) means forwarded headers
     * are never trusted and the TCP peer address is used.
     */
    private List<String> trustedProxies = new ArrayList<>();

    /** Maximum number of buckets kept in memory (bounds memory under abuse). */
    private long maxTrackedClients = 100_000;

    private Limit login = new Limit(5, Duration.ofMinutes(1));
    private Limit activation = new Limit(5, Duration.ofMinutes(15));
    private Limit resendActivation = new Limit(3, Duration.ofMinutes(15));
    private Limit passwordReset = new Limit(3, Duration.ofMinutes(15));
    private Limit billPayment = new Limit(5, Duration.ofMinutes(1));
    private Limit transfer = new Limit(5, Duration.ofMinutes(1));
    private Limit general = new Limit(100, Duration.ofMinutes(1));

    public Map<RateLimitCategory, Limit> limits() {
        Map<RateLimitCategory, Limit> limits = new EnumMap<>(RateLimitCategory.class);
        limits.put(RateLimitCategory.LOGIN, login);
        limits.put(RateLimitCategory.ACTIVATION, activation);
        limits.put(RateLimitCategory.RESEND_ACTIVATION, resendActivation);
        limits.put(RateLimitCategory.PASSWORD_RESET, passwordReset);
        limits.put(RateLimitCategory.BILL_PAYMENT, billPayment);
        limits.put(RateLimitCategory.TRANSFER, transfer);
        limits.put(RateLimitCategory.GENERAL, general);
        return limits;
    }

    public Limit limitFor(RateLimitCategory category) {
        return limits().get(category);
    }

    /** Longest configured window: idle buckets older than this are full and can be dropped. */
    public Duration longestWindow() {
        return limits().values().stream()
                .map(Limit::getWindow)
                .max(Duration::compareTo)
                .orElse(Duration.ofMinutes(15));
    }

    /** "N requests per window". */
    public static class Limit {

        private int capacity;
        private Duration window;

        public Limit() {
        }

        public Limit(int capacity, Duration window) {
            this.capacity = capacity;
            this.window = window;
        }

        public int getCapacity() {
            return capacity;
        }

        public void setCapacity(int capacity) {
            this.capacity = capacity;
        }

        public Duration getWindow() {
            return window;
        }

        public void setWindow(Duration window) {
            this.window = window;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getTrustedProxies() {
        return trustedProxies;
    }

    public void setTrustedProxies(List<String> trustedProxies) {
        this.trustedProxies = trustedProxies;
    }

    public long getMaxTrackedClients() {
        return maxTrackedClients;
    }

    public void setMaxTrackedClients(long maxTrackedClients) {
        this.maxTrackedClients = maxTrackedClients;
    }

    public Limit getLogin() {
        return login;
    }

    public void setLogin(Limit login) {
        this.login = login;
    }

    public Limit getActivation() {
        return activation;
    }

    public void setActivation(Limit activation) {
        this.activation = activation;
    }

    public Limit getResendActivation() {
        return resendActivation;
    }

    public void setResendActivation(Limit resendActivation) {
        this.resendActivation = resendActivation;
    }

    public Limit getPasswordReset() {
        return passwordReset;
    }

    public void setPasswordReset(Limit passwordReset) {
        this.passwordReset = passwordReset;
    }

    public Limit getBillPayment() {
        return billPayment;
    }

    public void setBillPayment(Limit billPayment) {
        this.billPayment = billPayment;
    }

    public Limit getTransfer() {
        return transfer;
    }

    public void setTransfer(Limit transfer) {
        this.transfer = transfer;
    }

    public Limit getGeneral() {
        return general;
    }

    public void setGeneral(Limit general) {
        this.general = general;
    }
}
