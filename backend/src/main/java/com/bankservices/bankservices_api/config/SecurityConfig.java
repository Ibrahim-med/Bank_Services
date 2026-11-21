
package com.bankservices.bankservices_api.config;

import java.util.List;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.bankservices.bankservices_api.ratelimit.RateLimitConfiguration;
import com.bankservices.bankservices_api.ratelimit.RateLimitFilter;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@Import(RateLimitConfiguration.class)
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitFilter rateLimitFilter;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            RateLimitFilter rateLimitFilter) {

        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.rateLimitFilter = rateLimitFilter;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(List.of(
                "http://localhost:5173",
                "http://127.0.0.1:5173",
                "http://localhost:5175",
                "http://localhost:5180"
        ));

        // Allowed HTTP methods.
        configuration.setAllowedMethods(
                List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
        );

        // Allowed request headers.
        configuration.setAllowedHeaders(
                List.of("*")
        );

        // Allow credentials.
        configuration.setAllowCredentials(true);

        // Cache preflight response for one hour.
        configuration.setMaxAge(3600L);

        // Let the browser app read the rate-limit headers (e.g. Retry-After on 429).
        configuration.setExposedHeaders(List.of(
                RateLimitFilter.HEADER_RETRY_AFTER,
                RateLimitFilter.HEADER_LIMIT,
                RateLimitFilter.HEADER_REMAINING,
                RateLimitFilter.HEADER_RESET
        ));

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**", configuration);

        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http)
            throws Exception {

        http
                .cors(cors ->
                        cors.configurationSource(corsConfigurationSource())
                )

                .csrf(csrf -> csrf.disable())

                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                .exceptionHandling(exception -> exception

                        .authenticationEntryPoint((request, response, authException) -> {
                            response.sendError(
                                    HttpServletResponse.SC_UNAUTHORIZED,
                                    "Unauthorized"
                            );
                        })

                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.sendError(
                                    HttpServletResponse.SC_FORBIDDEN,
                                    "Forbidden"
                            );
                        })
                )

                .authorizeHttpRequests(auth -> auth

                        // Allow CORS preflight requests.
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // Let the container's error dispatch render the status chosen
                        // by the handlers above (sendError 401/403). Without this, the
                        // ERROR dispatch (which carries no JWT) was itself rejected and
                        // a 403 on /api/admin/** or /api/client/** became a 401, which
                        // makes the frontend log the user out.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()

                        // Public authentication endpoints.
                        .requestMatchers(
                                "/api/auth/register",
                                "/api/auth/login",
                                "/api/auth/forgot-password",
                                "/api/auth/reset-password",
                                "/api/auth/activate",
                                "/api/auth/resend-activation"
                        ).permitAll()

                        // ADMIN-only endpoints.
                        .requestMatchers("/api/admin/**")
                        .hasRole("ADMIN")

                        // CLIENT-only endpoints.
                        .requestMatchers("/api/client/**")
                        .hasRole("CLIENT")

                        // All other endpoints require authentication.
                        .anyRequest().authenticated()
                )

                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())

                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                )

                // Rate limiting: after authentication (per-user keys), before
                // authorization (refused requests are counted too).
                .addFilterAfter(
                        rateLimitFilter,
                        JwtAuthenticationFilter.class
                );

        return http.build();
    }
}
