
package com.bankservices.bankservices_api.service;

import com.bankservices.bankservices_api.dto.request.ActivateAccountRequest;
import com.bankservices.bankservices_api.dto.request.ClientRegistrationRequest;
import com.bankservices.bankservices_api.dto.response.ClientResponse;
import com.bankservices.bankservices_api.entity.AccountActivationToken;
import com.bankservices.bankservices_api.entity.Client;
import com.bankservices.bankservices_api.entity.Utilisateur;
import com.bankservices.bankservices_api.enums.StatutUtilisateur;
import com.bankservices.bankservices_api.exception.ResourceNotFoundException;
import com.bankservices.bankservices_api.notification.AccountNotificationEvent;
import com.bankservices.bankservices_api.repository.AccountActivationTokenRepository;
import com.bankservices.bankservices_api.repository.ClientRepository;
import com.bankservices.bankservices_api.repository.UtilisateurRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

/**
 * Client registration by an administrator and activation of the
 * client's online banking login.
 *
 * Flow:
 *  1. ADMIN registers a client -> Client + Utilisateur (role CLIENT,
 *     status PENDING_ACTIVATION, generated username, unusable random password).
 *  2. A random 256-bit token is generated; only its SHA-256 hash is stored,
 *     with an expiry date. The raw token is only placed in the e-mailed link.
 *  3. The client opens the link and chooses a password -> BCrypt hash stored,
 *     status ACTIVE, token marked as used.
 *
 * No password is ever e-mailed. E-mails are sent AFTER the database commit
 * (AccountNotificationEvent + NotificationEventListener).
 */
@Service
public class AccountActivationService {

    static final String INVALID_LINK_MESSAGE = "This activation link is invalid.";

    private static final int USERNAME_MIN_LENGTH = 4;
    private static final int USERNAME_BASE_MAX_LENGTH = 40;

    private final ClientRepository clientRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final AccountActivationTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl = "http://localhost:5173";

    @Value("${app.activation-token-validity-hours:48}")
    private long tokenValidityHours = 48;

    public AccountActivationService(
            ClientRepository clientRepository,
            UtilisateurRepository utilisateurRepository,
            AccountActivationTokenRepository tokenRepository,
            PasswordEncoder passwordEncoder,
            ApplicationEventPublisher eventPublisher) {

        this.clientRepository = clientRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
    }

    // =====================================
    // ADMIN: REGISTER A NEW CLIENT
    // =====================================

    @Transactional
    public ClientResponse registerClient(ClientRegistrationRequest request) {

        String email = request.getEmail().trim();

        if (clientRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalStateException(
                    "A client with this email address already exists.");
        }

        Client client = new Client();
        client.setNom(request.getNom().trim());
        client.setPrenom(request.getPrenom().trim());
        client.setEmail(email);
        client.setTelephone(blankToNull(request.getTelephone()));
        client.setAdresse(blankToNull(request.getAdresse()));

        Client savedClient = clientRepository.save(client);

        Utilisateur utilisateur = createPendingUser(savedClient);

        issueActivation(utilisateur, savedClient);

        return toResponse(savedClient, utilisateur);
    }

    // =====================================
    // ADMIN: (RE)SEND THE ACTIVATION E-MAIL
    // Also creates the pending login for an existing client
    // registered before this feature (client without login).
    // =====================================

    @Transactional
    public ClientResponse sendActivationForClient(Integer idClient) {

        Client client = clientRepository.findById(idClient)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Client not found: " + idClient));

        if (client.getEmail() == null || client.getEmail().isBlank()) {
            throw new IllegalStateException(
                    "This client has no email address. Add one before sending an activation link.");
        }

        Utilisateur utilisateur = utilisateurRepository
                .findByClient_IdClient(idClient)
                .orElseGet(() -> createPendingUser(client));

        if (utilisateur.getStatut() == StatutUtilisateur.ACTIVE) {
            throw new IllegalStateException(
                    "This client's online banking account is already active.");
        }

        if (utilisateur.getStatut() == StatutUtilisateur.DISABLED) {
            throw new IllegalStateException(
                    "This client's online banking account is disabled.");
        }

        issueActivation(utilisateur, client);

        return toResponse(client, utilisateur);
    }

    // =====================================
    // PUBLIC: RESEND THE ACTIVATION E-MAIL
    // Never reveals whether the e-mail exists or its account status.
    // =====================================

    @Transactional
    public void resendActivation(String email) {

        if (email == null || email.isBlank()) {
            return;
        }

        Optional<Utilisateur> optionalUser =
                utilisateurRepository.findByClient_EmailIgnoreCase(email.trim());

        if (optionalUser.isEmpty()
                || optionalUser.get().getStatut() != StatutUtilisateur.PENDING_ACTIVATION) {
            return;
        }

        Utilisateur utilisateur = optionalUser.get();

        issueActivation(utilisateur, utilisateur.getClient());
    }

    // =====================================
    // PUBLIC: ACTIVATE THE ACCOUNT
    // =====================================

    @Transactional
    public void activateAccount(ActivateAccountRequest request) {

        String rawToken = request.getToken();
        String password = request.getPassword();

        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException(INVALID_LINK_MESSAGE);
        }

        if (password == null || password.length() < 8 || password.length() > 100) {
            throw new IllegalArgumentException(
                    "Password must contain between 8 and 100 characters.");
        }

        if (!password.equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match.");
        }

        AccountActivationToken token = tokenRepository
                .findByTokenHashForUpdate(SecureTokens.sha256Hex(rawToken))
                .orElseThrow(() -> new IllegalArgumentException(INVALID_LINK_MESSAGE));

        if (token.isUsed()) {
            throw new IllegalArgumentException(
                    "This activation link has already been used.");
        }

        if (!token.getExpiryDate().isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException(
                    "This activation link has expired. Request a new one.");
        }

        Utilisateur utilisateur = token.getUtilisateur();

        if (utilisateur.getStatut() != StatutUtilisateur.PENDING_ACTIVATION) {
            throw new IllegalStateException(
                    "This account cannot be activated. Please contact BankServices.");
        }

        utilisateur.setPassword(passwordEncoder.encode(password));
        utilisateur.setStatut(StatutUtilisateur.ACTIVE);
        utilisateurRepository.save(utilisateur);

        token.setUsed(true);
        tokenRepository.save(token);
    }

    // =====================================
    // USERNAME GENERATION
    // first-name initial + last name, lower-case ASCII ("Élise Dupont" -> "edupont"),
    // numeric suffix on collision ("edupont1", "edupont2", ...).
    // =====================================

    String generateUsername(String prenom, String nom) {

        String first = normalize(prenom);
        String last = normalize(nom);

        String base = (first.isEmpty() ? "" : first.substring(0, 1)) + last;

        if (base.isEmpty()) {
            base = "client";
        }

        if (base.length() > USERNAME_BASE_MAX_LENGTH) {
            base = base.substring(0, USERNAME_BASE_MAX_LENGTH);
        }

        if (base.length() >= USERNAME_MIN_LENGTH
                && !utilisateurRepository.existsByUsername(base)) {
            return base;
        }

        for (int suffix = 1; suffix < 100_000; suffix++) {
            String candidate = base + suffix;

            if (candidate.length() >= USERNAME_MIN_LENGTH
                    && !utilisateurRepository.existsByUsername(candidate)) {
                return candidate;
            }
        }

        throw new IllegalStateException("Could not generate a unique username.");
    }

    // =====================================
    // HELPERS
    // =====================================

    private Utilisateur createPendingUser(Client client) {

        Utilisateur utilisateur = new Utilisateur();
        utilisateur.setUsername(generateUsername(client.getPrenom(), client.getNom()));

        // The column is NOT NULL: store the BCrypt hash of a random secret that
        // is immediately discarded. It can never be used to log in, and login is
        // refused anyway while the status is PENDING_ACTIVATION.
        utilisateur.setPassword(passwordEncoder.encode(SecureTokens.newUrlToken()));

        utilisateur.setRole("CLIENT");
        utilisateur.setStatut(StatutUtilisateur.PENDING_ACTIVATION);
        utilisateur.setClient(client);

        return utilisateurRepository.save(utilisateur);
    }

    private void issueActivation(Utilisateur utilisateur, Client client) {

        // Previous links stop working as soon as a new one is issued.
        tokenRepository.deleteByUtilisateur_IdUtilisateur(utilisateur.getIdUtilisateur());

        String rawToken = SecureTokens.newUrlToken();

        AccountActivationToken token = new AccountActivationToken();
        token.setTokenHash(SecureTokens.sha256Hex(rawToken));
        token.setUtilisateur(utilisateur);
        token.setCreatedAt(LocalDateTime.now());
        token.setExpiryDate(LocalDateTime.now().plusHours(tokenValidityHours));
        token.setUsed(false);

        tokenRepository.save(token);

        String activationLink = frontendUrl + "/activate-account?token=" + rawToken;

        eventPublisher.publishEvent(new AccountNotificationEvent(
                client.getEmail(),
                "BankServices - Activate your online banking account",
                buildActivationEmail(client, utilisateur.getUsername(), activationLink)
        ));
    }

    private String buildActivationEmail(Client client, String username, String link) {
        return "Hello " + client.getPrenom() + ",\n\n"
                + "An online banking account has been created for you at BankServices.\n\n"
                + "Your username: " + username + "\n\n"
                + "To activate your account:\n"
                + "  1. Open the activation link below.\n"
                + "  2. Choose a password (8 to 100 characters) and confirm it.\n"
                + "  3. Sign in with your username and your new password.\n\n"
                + link + "\n\n"
                + "This link expires in " + tokenValidityHours + " hours and can only be used once.\n"
                + "BankServices will never send you a password by email or ask for it.\n\n"
                + "If you were not expecting this message, you can ignore it.";
    }

    private ClientResponse toResponse(Client client, Utilisateur utilisateur) {
        ClientResponse response = new ClientResponse(
                client.getIdClient(),
                client.getNom(),
                client.getPrenom(),
                client.getEmail(),
                client.getTelephone(),
                client.getAdresse()
        );
        response.setUsername(utilisateur.getUsername());
        response.setAccountStatus(utilisateur.getStatut());
        return response;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
