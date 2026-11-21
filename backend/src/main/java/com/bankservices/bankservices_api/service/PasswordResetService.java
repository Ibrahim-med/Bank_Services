
package com.bankservices.bankservices_api.service;

import com.bankservices.bankservices_api.entity.PasswordResetToken;
import com.bankservices.bankservices_api.entity.Utilisateur;
import com.bankservices.bankservices_api.repository.PasswordResetTokenRepository;
import com.bankservices.bankservices_api.repository.UtilisateurRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class PasswordResetService {

    private final UtilisateurRepository utilisateurRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    private static final Logger logger =
            LoggerFactory.getLogger(PasswordResetService.class);

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    public PasswordResetService(
            UtilisateurRepository utilisateurRepository,
            PasswordResetTokenRepository tokenRepository,
            PasswordEncoder passwordEncoder,
            EmailService emailService) {

        this.utilisateurRepository = utilisateurRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    @Transactional
    public void requestPasswordReset(String email) {

        Optional<Utilisateur> optionalUser =
                utilisateurRepository.findByClient_EmailIgnoreCase(email);

        // Do not reveal whether an email address exists.
        if (optionalUser.isEmpty()) {
            return;
        }

        Utilisateur utilisateur = optionalUser.get();

        // Only ACTIVE accounts can reset a password. A pending account must be
        // activated through its activation link; a disabled one stays locked.
        // Same silent response, so the account status is not revealed.
        if (!utilisateur.isActive()) {
            return;
        }

        // Invalidate previous reset tokens.
        tokenRepository.deleteByUtilisateur_IdUtilisateur(
                utilisateur.getIdUtilisateur()
        );

        // Generate a secure random token.
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);

        String rawToken = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);

        // Store only the SHA-256 hash.
        String tokenHash = hashToken(rawToken);

        PasswordResetToken resetToken = new PasswordResetToken();

        resetToken.setTokenHash(tokenHash);
        resetToken.setUtilisateur(utilisateur);
        resetToken.setExpiryDate(LocalDateTime.now().plusMinutes(15));
        resetToken.setCreatedAt(LocalDateTime.now());
        resetToken.setUsed(false);

        tokenRepository.save(resetToken);

        // Send the raw token only through the email link.
        String resetLink = frontendUrl
                + "/reset-password?token="
                + rawToken;

        // A mail failure (e.g. SMTP "authentication failed") must not leak to the
        // client: keep the generic response and log the real cause for the admin.
        try {
            emailService.sendPasswordResetEmail(
                    utilisateur.getClient().getEmail(),
                    resetLink
            );
        } catch (MailException exception) {
            logger.error("Could not send password reset email: {}",
                    exception.getMessage(), exception);
        }
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {

        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("Invalid or expired reset token.");
        }

        if (newPassword == null || newPassword.length() < 8
                || newPassword.length() > 100) {
            throw new IllegalArgumentException(
                    "Password must contain between 8 and 100 characters."
            );
        }

        String tokenHash = hashToken(rawToken);

        PasswordResetToken resetToken = tokenRepository
                .findByTokenHashAndUsedFalse(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Invalid or expired reset token."
                ));

        if (!resetToken.getExpiryDate().isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException(
                    "Invalid or expired reset token."
            );
        }

        Utilisateur utilisateur = resetToken.getUtilisateur();

        // Hash the new password before saving it.
        utilisateur.setPassword(passwordEncoder.encode(newPassword));

        utilisateurRepository.save(utilisateur);

        // Make the token unusable after successful reset.
        resetToken.setUsed(true);

        tokenRepository.save(resetToken);
    }

    private String hashToken(String token) {

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    token.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    "SHA-256 algorithm is not available.", e
            );
        }
    }
}
