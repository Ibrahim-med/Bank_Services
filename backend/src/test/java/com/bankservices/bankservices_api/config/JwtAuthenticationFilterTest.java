package com.bankservices.bankservices_api.config;

import com.bankservices.bankservices_api.repository.UtilisateurRepository;
import com.bankservices.bankservices_api.service.JwtService;

import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static com.bankservices.bankservices_api.support.TestFixtures.client;
import static com.bankservices.bankservices_api.support.TestFixtures.utilisateur;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link JwtAuthenticationFilter}: how a Bearer token in the
 * Authorization header becomes (or fails to become) an authenticated principal.
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock private JwtService jwtService;
    @Mock private UtilisateurRepository utilisateurRepository;

    private JwtAuthenticationFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain chain;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        filter = new JwtAuthenticationFilter(jwtService, utilisateurRepository);
        request = new MockHttpServletRequest("GET", "/api/comptes/1");
        response = new MockHttpServletResponse();
        chain = new MockFilterChain();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private boolean chainContinued() {
        return chain.getRequest() != null;
    }

    @Test
    void requestWithoutAuthorizationHeader_continuesChainUnauthenticated() throws Exception {
        filter.doFilter(request, response, chain);

        assertThat(chainContinued()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(jwtService);
    }

    @Test
    void requestWithNonBearerHeader_continuesChainUnauthenticated() throws Exception {
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");

        filter.doFilter(request, response, chain);

        assertThat(chainContinued()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void preflightOptionsRequest_isNotFiltered() throws Exception {
        request.setMethod("OPTIONS");
        request.addHeader("Authorization", "Bearer anything");

        filter.doFilter(request, response, chain);

        assertThat(chainContinued()).isTrue();
        verifyNoInteractions(jwtService, utilisateurRepository);
    }

    @Test
    void validTokenForExistingUser_authenticatesWithRoleFromDatabase() throws Exception {
        request.addHeader("Authorization", "Bearer good-token");
        when(jwtService.extractUsername("good-token")).thenReturn("alice");
        when(jwtService.isTokenValid("good-token", "alice")).thenReturn(true);
        when(utilisateurRepository.findByUsername("alice")).thenReturn(Optional.of(
                utilisateur(5, "alice", "CLIENT", client(1, "Alice", "Test", "a@example.test"))));

        filter.doFilter(request, response, chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(chainContinued()).isTrue();
        assertThat(auth).isNotNull();
        assertThat(auth.getName()).isEqualTo("alice");
        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_CLIENT");
    }

    @Test
    void tokenFailingValidation_returns401AndStopsChain() throws Exception {
        request.addHeader("Authorization", "Bearer bad-token");
        when(jwtService.extractUsername("bad-token")).thenReturn("alice");
        when(jwtService.isTokenValid("bad-token", "alice")).thenReturn(false);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(chainContinued()).isFalse();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void expiredOrMalformedToken_returns401AndStopsChain() throws Exception {
        request.addHeader("Authorization", "Bearer expired-token");
        when(jwtService.extractUsername("expired-token"))
                .thenThrow(new ExpiredJwtException(null, null, "expired"));

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(response.getErrorMessage()).isEqualTo("Invalid or expired token");
        assertThat(chainContinued()).isFalse();
    }

    @Test
    void validTokenForDeletedUser_returns401AndStopsChain() throws Exception {
        request.addHeader("Authorization", "Bearer orphan-token");
        when(jwtService.extractUsername("orphan-token")).thenReturn("deleted");
        when(jwtService.isTokenValid("orphan-token", "deleted")).thenReturn(true);
        when(utilisateurRepository.findByUsername("deleted")).thenReturn(Optional.empty());

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(response.getErrorMessage()).isEqualTo("User not found");
        assertThat(chainContinued()).isFalse();
    }
}
