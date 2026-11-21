
package com.bankservices.bankservices_api.config;

import com.bankservices.bankservices_api.entity.Utilisateur;
import com.bankservices.bankservices_api.repository.UtilisateurRepository;
import com.bankservices.bankservices_api.service.JwtService;

import io.jsonwebtoken.JwtException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** Request attribute holding the authenticated Utilisateur id (read by RateLimitFilter). */
    public static final String USER_ID_ATTRIBUTE = JwtAuthenticationFilter.class.getName() + ".userId";

    // Diagnostics at DEBUG only: never tokens, usernames or stack traces on every request.
    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;
    private final UtilisateurRepository utilisateurRepository;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            UtilisateurRepository utilisateurRepository) {

        this.jwtService = jwtService;
        this.utilisateurRepository = utilisateurRepository;
    }

    // Skip JWT authentication for CORS preflight requests.
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        // No token: continue and let Spring Security decide.
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {

            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        try {

            String username = jwtService.extractUsername(token);

            if (SecurityContextHolder.getContext().getAuthentication() == null) {

                if (!jwtService.isTokenValid(token, username)) {

                    logger.debug("JWT rejected: invalid token on {} {}", request.getMethod(), request.getRequestURI());

                    SecurityContextHolder.clearContext();

                    response.sendError(
                            HttpServletResponse.SC_UNAUTHORIZED,
                            "Invalid token"
                    );

                    return;
                }

                Utilisateur utilisateur = utilisateurRepository
                        .findByUsername(username)
                        .orElse(null);

                if (utilisateur == null) {

                    logger.debug("JWT rejected: unknown user on {} {}", request.getMethod(), request.getRequestURI());

                    SecurityContextHolder.clearContext();

                    response.sendError(
                            HttpServletResponse.SC_UNAUTHORIZED,
                            "User not found"
                    );

                    return;
                }

                // A token issued before the account was disabled
                // must stop working immediately.
                if (!utilisateur.isActive()) {

                    logger.debug("JWT rejected: inactive account on {} {}", request.getMethod(), request.getRequestURI());

                    SecurityContextHolder.clearContext();

                    response.sendError(
                            HttpServletResponse.SC_UNAUTHORIZED,
                            "Account is not active"
                    );

                    return;
                }

                SimpleGrantedAuthority authority =
                        new SimpleGrantedAuthority(
                                "ROLE_" + utilisateur.getRole()
                        );

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                username,
                                null,
                                List.of(authority)
                        );

                authentication.setDetails(
                        new WebAuthenticationDetailsSource()
                                .buildDetails(request)
                );

                SecurityContextHolder.getContext()
                        .setAuthentication(authentication);

                // Numeric user id for per-user rate limiting (no personal data in the key).
                request.setAttribute(USER_ID_ATTRIBUTE, utilisateur.getIdUtilisateur());

                logger.debug("JWT authenticated request {} {}", request.getMethod(), request.getRequestURI());
            }

        } catch (JwtException | IllegalArgumentException exception) {

            // Never log the token itself; the exception type is enough to diagnose.
            logger.warn("JWT rejected on {} {}: {}", request.getMethod(), request.getRequestURI(),
                    exception.getClass().getSimpleName());

            SecurityContextHolder.clearContext();

            response.sendError(
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "Invalid or expired token"
            );

            return;
        }

        // Continue the security chain.
        filterChain.doFilter(request, response);
    }
}
