package com.bankservices.bankservices_api.ratelimit;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Wiring of the rate limiter. Imported by SecurityConfig, which places the filter
 * in the security chain. To move to distributed limits, replace the
 * RateLimitBucketStore bean (e.g. a Bucket4j Redis ProxyManager adapter).
 */
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfiguration {

    @Bean
    public RateLimitBucketStore rateLimitBucketStore(RateLimitProperties properties) {
        return new InMemoryRateLimitBucketStore(properties.getMaxTrackedClients(), properties.longestWindow());
    }

    @Bean
    public RateLimitService rateLimitService(RateLimitProperties properties, RateLimitBucketStore store) {
        return new RateLimitService(properties, store);
    }

    @Bean
    public ClientIpResolver clientIpResolver(RateLimitProperties properties) {
        return new ClientIpResolver(properties.getTrustedProxies());
    }

    @Bean
    public RateLimitFilter rateLimitFilter(
            RateLimitProperties properties,
            RateLimitService rateLimitService,
            ClientIpResolver clientIpResolver,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {

        return new RateLimitFilter(properties, rateLimitService, clientIpResolver, exceptionResolver);
    }

    /** Runs only inside the security chain, never as a separate servlet filter. */
    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(RateLimitFilter filter) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
