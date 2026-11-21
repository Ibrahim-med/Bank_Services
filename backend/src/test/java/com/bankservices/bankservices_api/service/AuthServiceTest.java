package com.bankservices.bankservices_api.service;

import com.bankservices.bankservices_api.dto.request.LoginRequest;
import com.bankservices.bankservices_api.dto.request.RegisterRequest;
import com.bankservices.bankservices_api.dto.response.LoginResponse;
import com.bankservices.bankservices_api.entity.Client;
import com.bankservices.bankservices_api.entity.Utilisateur;
import com.bankservices.bankservices_api.repository.ClientRepository;
import com.bankservices.bankservices_api.repository.UtilisateurRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static com.bankservices.bankservices_api.support.TestFixtures.client;
import static com.bankservices.bankservices_api.support.TestFixtures.utilisateur;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AuthService}: user registration (linking a login to a
 * client) and login (credential check + JWT issuance).
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UtilisateurRepository utilisateurRepository;
    @Mock private ClientRepository clientRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    private Client alice;

    @BeforeEach
    void setUp() {
        alice = client(1, "Alice", "Test", "alice@example.test");
    }

    private RegisterRequest registerRequest() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("alice");
        request.setPassword("Str0ngTestPass");
        request.setIdClient(1);
        return request;
    }

    private LoginRequest loginRequest(String username, String password) {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(password);
        return request;
    }

    // ---------------- register ----------------

    @Test
    void register_withNewUsernameAndFreeClient_savesClientUserWithEncodedPassword() {
        when(utilisateurRepository.existsByUsername("alice")).thenReturn(false);
        when(clientRepository.findById(1)).thenReturn(Optional.of(alice));
        when(utilisateurRepository.existsByClient_IdClient(1)).thenReturn(false);
        when(passwordEncoder.encode("Str0ngTestPass")).thenReturn("ENCODED");

        String message = authService.register(registerRequest());

        ArgumentCaptor<Utilisateur> saved = ArgumentCaptor.forClass(Utilisateur.class);
        verify(utilisateurRepository).save(saved.capture());
        assertThat(message).isEqualTo("User registered successfully");
        assertThat(saved.getValue().getUsername()).isEqualTo("alice");
        assertThat(saved.getValue().getPassword()).isEqualTo("ENCODED");
        assertThat(saved.getValue().getRole()).isEqualTo("CLIENT");
        assertThat(saved.getValue().getClient()).isSameAs(alice);
    }

    @Test
    void register_withExistingUsername_isRejected() {
        when(utilisateurRepository.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Username already exists");

        verify(utilisateurRepository, never()).save(any());
    }

    @Test
    void register_forNonExistentClient_isRejected() {
        when(utilisateurRepository.existsByUsername("alice")).thenReturn(false);
        when(clientRepository.findById(1)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.register(registerRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Client not found");

        verify(utilisateurRepository, never()).save(any());
    }

    @Test
    void register_forClientThatAlreadyHasALogin_isRejected() {
        when(utilisateurRepository.existsByUsername("alice")).thenReturn(false);
        when(clientRepository.findById(1)).thenReturn(Optional.of(alice));
        when(utilisateurRepository.existsByClient_IdClient(1)).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already has an account");

        verify(utilisateurRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }

    // ---------------- login ----------------

    @Test
    void login_withValidClientCredentials_returnsBearerTokenRoleAndClientId() {
        Utilisateur user = utilisateur(5, "alice", "CLIENT", alice);
        when(utilisateurRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Str0ngTestPass", user.getPassword())).thenReturn(true);
        when(jwtService.generateToken("alice")).thenReturn("jwt-token");

        LoginResponse response = authService.login(loginRequest("alice", "Str0ngTestPass"));

        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getUsername()).isEqualTo("alice");
        assertThat(response.getRole()).isEqualTo("CLIENT");
        assertThat(response.getIdClient()).isEqualTo(1);
    }

    @Test
    void login_asAdminWithoutLinkedClient_returnsNullClientId() {
        Utilisateur admin = utilisateur(1, "admin", "ADMIN", null);
        when(utilisateurRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(jwtService.generateToken("admin")).thenReturn("admin-token");

        LoginResponse response = authService.login(loginRequest("admin", "AdminTestPass1"));

        assertThat(response.getRole()).isEqualTo("ADMIN");
        assertThat(response.getIdClient()).isNull();
    }

    @Test
    void login_withWrongPassword_isRejectedWithGenericMessageAndNoToken() {
        Utilisateur user = utilisateur(5, "alice", "CLIENT", alice);
        when(utilisateurRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", user.getPassword())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(loginRequest("alice", "wrong")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid username or password");

        verifyNoInteractions(jwtService);
    }

    @Test
    void login_withUnknownUsername_isRejectedWithSameGenericMessage() {
        when(utilisateurRepository.findByUsername("nobody")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(loginRequest("nobody", "whatever1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid username or password");

        verifyNoInteractions(passwordEncoder, jwtService);
    }
}
