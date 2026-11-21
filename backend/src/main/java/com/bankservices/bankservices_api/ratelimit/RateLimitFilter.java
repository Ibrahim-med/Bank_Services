package com.bankservices.bankservices_api.ratelimit;

import com.bankservices.bankservices_api.config.JwtAuthenticationFilter;
import com.bankservices.bankservices_api.exception.RateLimitExceededException;
import com.bankservices.bankservices_api.ratelimit.RateLimitService.RateLimitDecision;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.util.UrlPathHelper;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.io.IOException;

/**
 * Rate limiting for /api/**, registered in the Spring Security chain right after
 * JwtAuthenticationFilter and before authorization:
 * <ul>
 *   <li>after authentication, so authenticated requests are keyed by user ID;</li>
 *   <li>before authorization, so requests that will be refused (401/403) are still
 *       counted.</li>
 * </ul>
 * It only decides whether a request may proceed. All business protections
 * (validation, ownership checks, transactions, locking) stay in the services.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    public static final String HEADER_LIMIT = "RateLimit-Limit";
    public static final String HEADER_REMAINING = "RateLimit-Remaining";
    public static final String HEADER_RESET = "RateLimit-Reset";
    public static final String HEADER_RETRY_AFTER = "Retry-After";

    private static final Logger logger = LoggerFactory.getLogger(RateLimitFilter.class);

    private static final PathPatternParser PARSER = new PathPatternParser();
    private static final PathPattern API = PARSER.parse("/api/**");
    private static final PathPattern LOGIN = PARSER.parse("/api/auth/login");
    private static final PathPattern ACTIVATE = PARSER.parse("/api/auth/activate");
    private static final PathPattern RESEND_ACTIVATION = PARSER.parse("/api/auth/resend-activation");
    private static final PathPattern FORGOT_PASSWORD = PARSER.parse("/api/auth/forgot-password");
    private static final PathPattern RESET_PASSWORD = PARSER.parse("/api/auth/reset-password");
    private static final PathPattern PAY_BILL = PARSER.parse("/api/client/bills/{id}/pay");
    private static final PathPattern TRANSACTIONS = PARSER.parse("/api/transactions");

    private final RateLimitProperties properties;
    private final RateLimitService rateLimitService;
    private final ClientIpResolver clientIpResolver;
    private final HandlerExceptionResolver exceptionResolver;
    private final UrlPathHelper urlPathHelper = new UrlPathHelper();

    public RateLimitFilter(
            RateLimitProperties properties,
            RateLimitService rateLimitService,
            ClientIpResolver clientIpResolver,
            HandlerExceptionResolver exceptionResolver) {

        this.properties = properties;
        this.rateLimitService = rateLimitService;
        this.clientIpResolver = clientIpResolver;
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.isEnabled()
                || HttpMethod.OPTIONS.matches(request.getMethod())
                || !API.matches(path(request));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        RateLimitCategory category = categorize(request);
        RateLimitDecision decision = rateLimitService.consume(category, clientKey(category, request));

        response.setHeader(HEADER_LIMIT, String.valueOf(decision.limit()));
        response.setHeader(HEADER_REMAINING, String.valueOf(decision.remaining()));
        response.setHeader(HEADER_RESET, String.valueOf(decision.resetSeconds()));

        if (decision.allowed()) {
            filterChain.doFilter(request, response);
            return;
        }

        long retryAfter = decision.retryAfterHeaderSeconds();
        response.setHeader(HEADER_RETRY_AFTER, String.valueOf(retryAfter));

        // No client key, IP or username in logs: category and method/path only.
        logger.warn("Rate limit exceeded: category={} {} {}",
                category, request.getMethod(), request.getRequestURI());

        // Same JSON error format as every other API error (GlobalExceptionHandler).
        exceptionResolver.resolveException(request, response, null,
                new RateLimitExceededException(retryAfter));
    }

    RateLimitCategory categorize(HttpServletRequest request) {
        PathContainer path = path(request);

        if (HttpMethod.POST.matches(request.getMethod())) {
            if (LOGIN.matches(path)) return RateLimitCategory.LOGIN;
            if (ACTIVATE.matches(path)) return RateLimitCategory.ACTIVATION;
            if (RESEND_ACTIVATION.matches(path)) return RateLimitCategory.RESEND_ACTIVATION;
            if (FORGOT_PASSWORD.matches(path) || RESET_PASSWORD.matches(path)) return RateLimitCategory.PASSWORD_RESET;
            if (PAY_BILL.matches(path)) return RateLimitCategory.BILL_PAYMENT;
            if (TRANSACTIONS.matches(path)) return RateLimitCategory.TRANSFER;
        }

        return RateLimitCategory.GENERAL;
    }

    private String clientKey(RateLimitCategory category, HttpServletRequest request) {
        if (category.keyStrategy() == RateLimitCategory.KeyStrategy.USER_OR_IP) {
            Object userId = request.getAttribute(JwtAuthenticationFilter.USER_ID_ATTRIBUTE);
            if (userId != null) {
                return "user:" + userId;
            }
        }
        return "ip:" + clientIpResolver.resolve(request);
    }

    private PathContainer path(HttpServletRequest request) {
        return PathContainer.parsePath(urlPathHelper.getPathWithinApplication(request));
    }
}
