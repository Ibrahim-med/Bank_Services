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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.bankservices.bankservices_api.support.TestFixtures.client;
import static com.bankservices.bankservices_api.support.TestFixtures.utilisateur;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for client registration and account activation.
 * A REAL BCrypt encoder is used so that password hashing is verified,
 * not just mocked.
 */
@ExtendWith(MockitoExtension.class)
class AccountActivationServiceTest {

    private static final Pattern TOKEN_IN_LINK = Pattern.compile("activate-account\\?token=([A-Za-z0-9_-]+)");

    @Mock private ClientRepository clientRepository;
    @Mock private UtilisateurRepository utilisateurRepository;
    @Mock private AccountActivationTokenRepository tokenRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    private AccountActivationService service;

    @BeforeEach
    void setUp() {
        service = new AccountActivationService(
                clientRepository, utilisateurRepository, tokenRepository,
                passwordEncoder, eventPublisher);
    }

    private ClientRegistrationRequest registration(String prenom, String nom, String email) {
        ClientRegistrationRequest request = new ClientRegistrationRequest();
        request.setPrenom(prenom);
        request.setNom(nom);
        request.setEmail(email);
        request.setTelephone("0600000000");
        request.setAdresse("1 Test Street");
        return request;
    }

    private void stubSaves() {
        when(clientRepository.save(any(Client.class))).thenAnswer(invocation -> {
            Client saved = invocation.getArgument(0);
            saved.setIdClient(10);
            return saved;
        });
        when(utilisateurRepository.save(any(Utilisateur.class))).thenAnswer(invocation -> {
            Utilisateur saved = invocation.getArgument(0);
            saved.setIdUtilisateur(20);
            return saved;
        });
    }

    private static String tokenFrom(AccountNotificationEvent event) {
        Matcher matcher = TOKEN_IN_LINK.matcher(event.message());
        assertThat(matcher.find()).as("activation link present in e-mail").isTrue();
        return matcher.group(1);
    }

    private static ActivateAccountRequest activation(String token, String password, String confirm) {
        ActivateAccountRequest request = new ActivateAccountRequest();
        request.setToken(token);
        request.setPassword(password);
        request.setConfirmPassword(confirm);
        return request;
    }

    private static AccountActivationToken storedToken(Utilisateur user, String rawToken,
                                                      LocalDateTime expiry, boolean used) {
        AccountActivationToken token = new AccountActivationToken();
        token.setTokenHash(SecureTokens.sha256Hex(rawToken));
        token.setUtilisateur(user);
        token.setExpiryDate(expiry);
        token.setCreatedAt(LocalDateTime.now().minusHours(1));
        token.setUsed(used);
        return token;
    }

    private static Utilisateur pendingUser() {
        Utilisateur user = utilisateur(20, "aclient", "CLIENT", client(10, "Alice", "Client", "alice@example.test"));
        user.setStatut(StatutUtilisateur.PENDING_ACTIVATION);
        return user;
    }

    // ============================================================
    // REGISTRATION
    // ============================================================

    @Nested
    @DisplayName("Admin registers a client")
    class Registration {

        @Test
        void createsClient_pendingUser_hashedToken_andActivationEmail() {
            stubSaves();
            when(clientRepository.existsByEmailIgnoreCase("alice@example.test")).thenReturn(false);
            when(utilisateurRepository.existsByUsername(anyString())).thenReturn(false);

            ClientResponse response = service.registerClient(
                    registration("Alice", "Martin", " alice@example.test "));

            // Client
            ArgumentCaptor<Client> clientCaptor = ArgumentCaptor.forClass(Client.class);
            verify(clientRepository).save(clientCaptor.capture());
            assertThat(clientCaptor.getValue().getEmail()).isEqualTo("alice@example.test");

            // Pending CLIENT login linked to the client
            ArgumentCaptor<Utilisateur> userCaptor = ArgumentCaptor.forClass(Utilisateur.class);
            verify(utilisateurRepository).save(userCaptor.capture());
            Utilisateur user = userCaptor.getValue();
            assertThat(user.getUsername()).isEqualTo("amartin");
            assertThat(user.getRole()).isEqualTo("CLIENT");
            assertThat(user.getStatut()).isEqualTo(StatutUtilisateur.PENDING_ACTIVATION);
            assertThat(user.getClient()).isSameAs(clientCaptor.getValue());
            assertThat(user.getPassword()).startsWith("$2"); // BCrypt hash of an unknown secret

            // Token: only a hash is stored, with a future expiry, unused
            ArgumentCaptor<AccountActivationToken> tokenCaptor =
                    ArgumentCaptor.forClass(AccountActivationToken.class);
            verify(tokenRepository).save(tokenCaptor.capture());
            AccountActivationToken token = tokenCaptor.getValue();
            assertThat(token.isUsed()).isFalse();
            assertThat(token.getExpiryDate()).isAfter(LocalDateTime.now().plusHours(47));
            assertThat(token.getUtilisateur()).isSameAs(user);

            // Activation e-mail: username + link, never a password
            ArgumentCaptor<AccountNotificationEvent> eventCaptor =
                    ArgumentCaptor.forClass(AccountNotificationEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            AccountNotificationEvent event = eventCaptor.getValue();
            assertThat(event.email()).isEqualTo("alice@example.test");
            assertThat(event.message())
                    .contains("Your username: amartin")
                    .contains("http://localhost:5173/activate-account?token=")
                    .doesNotContainIgnoringCase("password:");

            String rawToken = tokenFrom(event);
            assertThat(token.getTokenHash())
                    .isEqualTo(SecureTokens.sha256Hex(rawToken))
                    .isNotEqualTo(rawToken)
                    .hasSize(64);

            // Response
            assertThat(response.getIdClient()).isEqualTo(10);
            assertThat(response.getUsername()).isEqualTo("amartin");
            assertThat(response.getAccountStatus()).isEqualTo(StatutUtilisateur.PENDING_ACTIVATION);
        }

        @Test
        void duplicateEmail_isRejected_andNothingIsCreated() {
            when(clientRepository.existsByEmailIgnoreCase("alice@example.test")).thenReturn(true);

            assertThatThrownBy(() -> service.registerClient(
                    registration("Alice", "Martin", "alice@example.test")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already exists");

            verify(clientRepository, never()).save(any());
            verify(utilisateurRepository, never()).save(any());
            verifyNoInteractions(tokenRepository, eventPublisher);
        }

        @Test
        void twoTokensForTwoRegistrations_areDifferent() {
            stubSaves();
            when(utilisateurRepository.existsByUsername(anyString())).thenReturn(false);

            service.registerClient(registration("Alice", "Martin", "a1@example.test"));
            service.registerClient(registration("Bob", "Durand", "b1@example.test"));

            ArgumentCaptor<AccountNotificationEvent> events =
                    ArgumentCaptor.forClass(AccountNotificationEvent.class);
            verify(eventPublisher, times(2)).publishEvent(events.capture());

            assertThat(tokenFrom(events.getAllValues().get(0)))
                    .isNotEqualTo(tokenFrom(events.getAllValues().get(1)));
        }
    }

    // ============================================================
    // USERNAME GENERATION
    // ============================================================

    @Nested
    @DisplayName("Unique username generation")
    class Usernames {

        @Test
        void initialPlusLastName_lowercaseAscii() {
            when(utilisateurRepository.existsByUsername(anyString())).thenReturn(false);

            assertThat(service.generateUsername("Élise", "De La Côte")).isEqualTo("edelacote");
        }

        @Test
        void collision_addsTheFirstFreeNumericSuffix() {
            when(utilisateurRepository.existsByUsername("amartin")).thenReturn(true);
            when(utilisateurRepository.existsByUsername("amartin1")).thenReturn(true);
            when(utilisateurRepository.existsByUsername("amartin2")).thenReturn(false);

            assertThat(service.generateUsername("Alice", "Martin")).isEqualTo("amartin2");
        }

        @Test
        void shortName_isPaddedToTheMinimumLength() {
            when(utilisateurRepository.existsByUsername(anyString())).thenReturn(false);

            assertThat(service.generateUsername("Li", "Wu")).isEqualTo("lwu1");
        }

        @Test
        void nonLatinName_fallsBackToClient() {
            when(utilisateurRepository.existsByUsername(anyString())).thenReturn(false);

            assertThat(service.generateUsername("李", "王")).isEqualTo("client");
        }
    }

    // ============================================================
    // ACTIVATION
    // ============================================================

    @Nested
    @DisplayName("Client activates the account")
    class Activation {

        @Test
        void validToken_setsBcryptPassword_activatesUser_andConsumesToken() {
            Utilisateur user = pendingUser();
            AccountActivationToken token = storedToken(user, "raw-token", LocalDateTime.now().plusHours(1), false);
            when(tokenRepository.findByTokenHashForUpdate(SecureTokens.sha256Hex("raw-token")))
                    .thenReturn(Optional.of(token));

            service.activateAccount(activation("raw-token", "NewPassword1", "NewPassword1"));

            assertThat(user.getStatut()).isEqualTo(StatutUtilisateur.ACTIVE);
            assertThat(user.getPassword()).isNotEqualTo("NewPassword1").startsWith("$2");
            assertThat(passwordEncoder.matches("NewPassword1", user.getPassword())).isTrue();
            assertThat(token.isUsed()).isTrue();
            verify(utilisateurRepository).save(user);
            verify(tokenRepository).save(token);
        }

        @Test
        void expiredToken_isRejected() {
            Utilisateur user = pendingUser();
            when(tokenRepository.findByTokenHashForUpdate(anyString()))
                    .thenReturn(Optional.of(storedToken(user, "raw", LocalDateTime.now().minusMinutes(1), false)));

            assertThatThrownBy(() -> service.activateAccount(activation("raw", "NewPassword1", "NewPassword1")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("expired");

            assertThat(user.getStatut()).isEqualTo(StatutUtilisateur.PENDING_ACTIVATION);
            verify(utilisateurRepository, never()).save(any());
        }

        @Test
        void unknownToken_isRejected() {
            when(tokenRepository.findByTokenHashForUpdate(anyString())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.activateAccount(activation("forged", "NewPassword1", "NewPassword1")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(AccountActivationService.INVALID_LINK_MESSAGE);
        }

        @Test
        void alreadyUsedToken_isRejected() {
            Utilisateur user = pendingUser();
            when(tokenRepository.findByTokenHashForUpdate(anyString()))
                    .thenReturn(Optional.of(storedToken(user, "raw", LocalDateTime.now().plusHours(1), true)));

            assertThatThrownBy(() -> service.activateAccount(activation("raw", "NewPassword1", "NewPassword1")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already been used");

            verify(utilisateurRepository, never()).save(any());
        }

        @Test
        void passwordConfirmationMismatch_isRejected_beforeTouchingTheToken() {
            assertThatThrownBy(() -> service.activateAccount(activation("raw", "NewPassword1", "Different1")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Passwords do not match.");

            verifyNoInteractions(tokenRepository, utilisateurRepository);
        }

        @Test
        void tooShortPassword_isRejected() {
            assertThatThrownBy(() -> service.activateAccount(activation("raw", "short", "short")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("between 8 and 100");

            verifyNoInteractions(tokenRepository);
        }

        @Test
        void disabledUser_cannotBeActivated() {
            Utilisateur user = pendingUser();
            user.setStatut(StatutUtilisateur.DISABLED);
            when(tokenRepository.findByTokenHashForUpdate(anyString()))
                    .thenReturn(Optional.of(storedToken(user, "raw", LocalDateTime.now().plusHours(1), false)));

            assertThatThrownBy(() -> service.activateAccount(activation("raw", "NewPassword1", "NewPassword1")))
                    .isInstanceOf(IllegalStateException.class);

            assertThat(user.getStatut()).isEqualTo(StatutUtilisateur.DISABLED);
        }
    }

    // ============================================================
    // RESENDING
    // ============================================================

    @Nested
    @DisplayName("Resending the activation e-mail")
    class Resend {

        @Test
        void admin_existingClientWithoutLogin_getsPendingLoginAndEmail() {
            Client legacy = client(3, "Carl", "Legacy", "carl@example.test");
            when(clientRepository.findById(3)).thenReturn(Optional.of(legacy));
            when(utilisateurRepository.findByClient_IdClient(3)).thenReturn(Optional.empty());
            when(utilisateurRepository.existsByUsername(anyString())).thenReturn(false);
            when(utilisateurRepository.save(any(Utilisateur.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ClientResponse response = service.sendActivationForClient(3);

            assertThat(response.getUsername()).isEqualTo("clegacy");
            assertThat(response.getAccountStatus()).isEqualTo(StatutUtilisateur.PENDING_ACTIVATION);
            verify(tokenRepository).save(any(AccountActivationToken.class));
            verify(eventPublisher).publishEvent(any(AccountNotificationEvent.class));
        }

        @Test
        void admin_pendingLogin_invalidatesOldLinksAndSendsNewOne() {
            Utilisateur user = pendingUser();
            when(clientRepository.findById(10)).thenReturn(Optional.of(user.getClient()));
            when(utilisateurRepository.findByClient_IdClient(10)).thenReturn(Optional.of(user));

            service.sendActivationForClient(10);

            verify(tokenRepository).deleteByUtilisateur_IdUtilisateur(20);
            verify(tokenRepository).save(any(AccountActivationToken.class));
            verify(eventPublisher).publishEvent(any(AccountNotificationEvent.class));
        }

        @Test
        void admin_activeLogin_isRejected() {
            Utilisateur user = pendingUser();
            user.setStatut(StatutUtilisateur.ACTIVE);
            when(clientRepository.findById(10)).thenReturn(Optional.of(user.getClient()));
            when(utilisateurRepository.findByClient_IdClient(10)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> service.sendActivationForClient(10))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already active");

            verifyNoInteractions(tokenRepository, eventPublisher);
        }

        @Test
        void admin_clientWithoutEmail_isRejected() {
            when(clientRepository.findById(4)).thenReturn(Optional.of(client(4, "No", "Mail", null)));

            assertThatThrownBy(() -> service.sendActivationForClient(4))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("no email");
        }

        @Test
        void admin_unknownClient_isNotFound() {
            when(clientRepository.findById(99)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.sendActivationForClient(99))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void public_pendingAccount_receivesNewLink() {
            Utilisateur user = pendingUser();
            when(utilisateurRepository.findByClient_EmailIgnoreCase("alice@example.test"))
                    .thenReturn(Optional.of(user));

            service.resendActivation(" alice@example.test ");

            verify(tokenRepository).save(any(AccountActivationToken.class));
            verify(eventPublisher).publishEvent(any(AccountNotificationEvent.class));
        }

        @Test
        void public_activeOrUnknownAccount_silentlyDoesNothing() {
            Utilisateur active = pendingUser();
            active.setStatut(StatutUtilisateur.ACTIVE);
            when(utilisateurRepository.findByClient_EmailIgnoreCase("alice@example.test"))
                    .thenReturn(Optional.of(active));
            when(utilisateurRepository.findByClient_EmailIgnoreCase("nobody@example.test"))
                    .thenReturn(Optional.empty());

            service.resendActivation("alice@example.test");
            service.resendActivation("nobody@example.test");

            verifyNoInteractions(tokenRepository, eventPublisher);
        }
    }
}
