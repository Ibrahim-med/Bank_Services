package com.bankservices.bankservices_api.service;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.MalformedJwtException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link JwtService} using real HMAC signing with a fake,
 * test-only base64 key (no Spring context needed).
 */
class JwtServiceTest {

    private static final String TEST_SECRET =
            "dGVzdC1vbmx5LWp3dC1zaWduaW5nLWtleS1ub3QtZm9yLXByb2R1Y3Rpb24tdXNlISE=";
    private static final String OTHER_SECRET =
            "YW5vdGhlci10ZXN0LW9ubHktc2lnbmluZy1rZXktdGhhdC1pcy1kaWZmZXJlbnQhIQ==";

    private JwtService jwtService;

    private JwtService newService(String secret, long expirationMs) {
        JwtService service = new JwtService();
        ReflectionTestUtils.setField(service, "secret", secret);
        ReflectionTestUtils.setField(service, "expiration", expirationMs);
        return service;
    }

    @BeforeEach
    void setUp() {
        jwtService = newService(TEST_SECRET, 60_000);
    }

    @Test
    void generateToken_producesThreePartCompactJwt() {
        String token = jwtService.generateToken("alice");

        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    void extractUsername_returnsSubjectOfGeneratedToken() {
        String token = jwtService.generateToken("alice");

        assertThat(jwtService.extractUsername(token)).isEqualTo("alice");
    }

    @Test
    void isTokenValid_forMatchingUsernameAndUnexpiredToken_returnsTrue() {
        String token = jwtService.generateToken("alice");

        assertThat(jwtService.isTokenValid(token, "alice")).isTrue();
    }

    @Test
    void isTokenValid_forDifferentUsername_returnsFalse() {
        String token = jwtService.generateToken("alice");

        assertThat(jwtService.isTokenValid(token, "bob")).isFalse();
    }

    /** An expired token is rejected by the parser with an exception, not by returning false. */
    @Test
    void expiredToken_isRejectedWithExpiredJwtException() {
        JwtService shortLived = newService(TEST_SECRET, -1_000);
        String expired = shortLived.generateToken("alice");

        assertThatThrownBy(() -> jwtService.isTokenValid(expired, "alice"))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void tokenSignedWithAnotherKey_isRejected() {
        String foreign = newService(OTHER_SECRET, 60_000).generateToken("alice");

        assertThatThrownBy(() -> jwtService.extractUsername(foreign))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void tamperedToken_isRejected() {
        String token = jwtService.generateToken("alice");
        String[] parts = token.split("\\.");
        String forgedPayload = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"admin\"}".getBytes());
        String tampered = parts[0] + "." + forgedPayload + "." + parts[2];

        assertThatThrownBy(() -> jwtService.extractUsername(tampered))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void malformedToken_isRejected() {
        assertThatThrownBy(() -> jwtService.extractUsername("not-a-jwt"))
                .isInstanceOf(MalformedJwtException.class);
    }
}
