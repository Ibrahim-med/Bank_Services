package com.bankservices.bankservices_api.service;

import com.bankservices.bankservices_api.entity.PasswordResetToken;
import com.bankservices.bankservices_api.entity.Utilisateur;
import com.bankservices.bankservices_api.repository.PasswordResetTokenRepository;
import com.bankservices.bankservices_api.repository.UtilisateurRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

import static com.bankservices.bankservices_api.support.TestFixtures.client;
import static com.bankservices.bankservices_api.support.TestFixtures.utilisateur;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link PasswordResetService}: token generation (hashed storage,
 * expiry, e-mail link) and password reset (validity, expiry, single use).
 */
@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock private UtilisateurRepository utilisateurRepository;
    @Mock private PasswordResetTokenRepository tokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private EmailService emailService;

    @InjectMocks
    private PasswordResetService passwordResetService;

    private Utilisateur alice;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(passwordResetService, "frontendUrl", "http://frontend.test");
        alice = utilisateur(5, "alice", "CLIENT", client(1, "Alice", "Test", "alice@example.test"));
    }

    private static String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private PasswordResetToken storedToken(String hash, LocalDateTime expiry) {
        PasswordResetToken token = new PasswordResetToken();
        token.setTokenHash(hash);
        token.setUtilisateur(alice);
        token.setExpiryDate(expiry);
        token.setCreatedAt(LocalDateTime.now().minusMinutes(1));
        token.setUsed(false);
        return token;
    }

    // ---------------- requestPasswordReset ----------------

    @Test
    void requestPasswordReset_forUnknownEmail_doesNothingAndRevealsNothing() {
        when(utilisateurRepository.findByClient_EmailIgnoreCase("unknown@example.test"))
                .thenReturn(Optional.empty());

        passwordResetService.requestPasswordReset("unknown@example.test");

        verifyNoInteractions(tokenRepository, emailService);
    }

    @Test
    void requestPasswordReset_forKnownEmail_storesOnlyHashAndEmailsMatchingRawToken() throws Exception {
        when(utilisateurRepository.findByClient_EmailIgnoreCase("alice@example.test"))
                .thenReturn(Optional.of(alice));

        passwordResetService.requestPasswordReset("alice@example.test");

        ArgumentCaptor<PasswordResetToken> saved = ArgumentCaptor.forClass(PasswordResetToken.class);
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(tokenRepository).deleteByUtilisateur_IdUtilisateur(5);
        verify(tokenRepository).save(saved.capture());
        verify(emailService).sendPasswordResetEmail(eq("alice@example.test"), link.capture());

        assertThat(link.getValue()).startsWith("http://frontend.test/reset-password?token=");
        String rawToken = link.getValue().substring(link.getValue().indexOf("token=") + 6);

        PasswordResetToken token = saved.getValue();
        assertThat(token.getTokenHash()).hasSize(64).isEqualTo(sha256(rawToken));
        assertThat(token.getTokenHash()).isNotEqualTo(rawToken);
        assertThat(token.isUsed()).isFalse();
        assertThat(token.getUtilisateur()).isSameAs(alice);
        assertThat(token.getExpiryDate())
                .isCloseTo(LocalDateTime.now().plusMinutes(15), within(5, java.time.temporal.ChronoUnit.SECONDS));
    }

    @Test
    void requestPasswordReset_invalidatesPreviousTokensBeforeSavingNewOne() {
        when(utilisateurRepository.findByClient_EmailIgnoreCase(anyString()))
                .thenReturn(Optional.of(alice));

        passwordResetService.requestPasswordReset("ALICE@example.test");

        var inOrder = inOrder(tokenRepository);
        inOrder.verify(tokenRepository).deleteByUtilisateur_IdUtilisateur(5);
        inOrder.verify(tokenRepository).save(any(PasswordResetToken.class));
    }

    @Test
    void requestPasswordReset_generatesDifferentTokenEachTime() {
        when(utilisateurRepository.findByClient_EmailIgnoreCase(anyString()))
                .thenReturn(Optional.of(alice));
        ArgumentCaptor<String> links = ArgumentCaptor.forClass(String.class);

        passwordResetService.requestPasswordReset("alice@example.test");
        passwordResetService.requestPasswordReset("alice@example.test");

        verify(emailService, times(2)).sendPasswordResetEmail(anyString(), links.capture());
        assertThat(links.getAllValues().get(0)).isNotEqualTo(links.getAllValues().get(1));
    }

    // ---------------- resetPassword ----------------

    @Test
    void resetPassword_withValidToken_encodesNewPasswordAndMarksTokenUsed() throws Exception {
        PasswordResetToken token = storedToken(sha256("raw-token"), LocalDateTime.now().plusMinutes(10));
        when(tokenRepository.findByTokenHashAndUsedFalse(sha256("raw-token"))).thenReturn(Optional.of(token));
        when(passwordEncoder.encode("NewTestPass123")).thenReturn("NEW-HASH");

        passwordResetService.resetPassword("raw-token", "NewTestPass123");

        assertThat(alice.getPassword()).isEqualTo("NEW-HASH");
        assertThat(token.isUsed()).isTrue();
        verify(utilisateurRepository).save(alice);
        verify(tokenRepository).save(token);
    }

    @Test
    void resetPassword_withExpiredToken_isRejectedAndPasswordUnchanged() throws Exception {
        String originalHash = alice.getPassword();
        PasswordResetToken token = storedToken(sha256("raw-token"), LocalDateTime.now().minusSeconds(1));
        when(tokenRepository.findByTokenHashAndUsedFalse(sha256("raw-token"))).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> passwordResetService.resetPassword("raw-token", "NewTestPass123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid or expired");

        assertThat(alice.getPassword()).isEqualTo(originalHash);
        assertThat(token.isUsed()).isFalse();
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void resetPassword_withUnknownOrAlreadyUsedToken_isRejected() {
        when(tokenRepository.findByTokenHashAndUsedFalse(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> passwordResetService.resetPassword("used-token", "NewTestPass123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid or expired");

        verify(utilisateurRepository, never()).save(any());
    }

    @Test
    void resetPassword_withBlankToken_isRejectedWithoutLookup() {
        assertThatThrownBy(() -> passwordResetService.resetPassword("  ", "NewTestPass123"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> passwordResetService.resetPassword(null, "NewTestPass123"))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(tokenRepository);
    }

    @Test
    void resetPassword_withTooShortPassword_isRejected() {
        assertThatThrownBy(() -> passwordResetService.resetPassword("raw-token", "short"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 8 and 100");

        verifyNoInteractions(tokenRepository);
    }

    @Test
    void resetPassword_withTooLongPassword_isRejected() {
        assertThatThrownBy(() -> passwordResetService.resetPassword("raw-token", "x".repeat(101)))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(tokenRepository);
    }
}
