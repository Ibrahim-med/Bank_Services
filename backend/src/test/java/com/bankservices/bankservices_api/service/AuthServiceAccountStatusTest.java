package com.bankservices.bankservices_api.service;

import com.bankservices.bankservices_api.dto.request.LoginRequest;
import com.bankservices.bankservices_api.dto.response.LoginResponse;
import com.bankservices.bankservices_api.entity.Utilisateur;
import com.bankservices.bankservices_api.enums.StatutUtilisateur;
import com.bankservices.bankservices_api.exception.AccountNotActiveException;
import com.bankservices.bankservices_api.repository.ClientRepository;
import com.bankservices.bankservices_api.repository.UtilisateurRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static com.bankservices.bankservices_api.support.TestFixtures.client;
import static com.bankservices.bankservices_api.support.TestFixtures.utilisateur;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Login is refused for PENDING_ACTIVATION and DISABLED users. */
@ExtendWith(MockitoExtension.class)
class AuthServiceAccountStatusTest {

    @Mock private UtilisateurRepository utilisateurRepository;
    @Mock private ClientRepository clientRepository;
    @Mock private JwtService jwtService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    private AuthService authService;
    private Utilisateur user;

    @BeforeEach
    void setUp() {
        authService = new AuthService(utilisateurRepository, clientRepository, passwordEncoder, jwtService);
        user = utilisateur(7, "amartin", "CLIENT", client(1, "Alice", "Martin", "alice@example.test"));
        user.setPassword(passwordEncoder.encode("CorrectPass1"));
        when(utilisateurRepository.findByUsername("amartin")).thenReturn(Optional.of(user));
    }

    private static LoginRequest login(String password) {
        LoginRequest request = new LoginRequest();
        request.setUsername("amartin");
        request.setPassword(password);
        return request;
    }

    @Test
    void pendingAccount_withCorrectPassword_isRejected_andNoTokenIssued() {
        user.setStatut(StatutUtilisateur.PENDING_ACTIVATION);

        assertThatThrownBy(() -> authService.login(login("CorrectPass1")))
                .isInstanceOf(AccountNotActiveException.class)
                .hasMessageContaining("not activated");

        verify(jwtService, never()).generateToken(anyString());
    }

    @Test
    void disabledAccount_withCorrectPassword_isRejected_andNoTokenIssued() {
        user.setStatut(StatutUtilisateur.DISABLED);

        assertThatThrownBy(() -> authService.login(login("CorrectPass1")))
                .isInstanceOf(AccountNotActiveException.class)
                .hasMessageContaining("disabled");

        verify(jwtService, never()).generateToken(anyString());
    }

    @Test
    void pendingAccount_withWrongPassword_getsTheGenericCredentialsError() {
        user.setStatut(StatutUtilisateur.PENDING_ACTIVATION);

        // The account status is not revealed without valid credentials.
        assertThatThrownBy(() -> authService.login(login("WrongPass1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid username or password");
    }

    @Test
    void activeAccount_logsIn() {
        when(jwtService.generateToken("amartin")).thenReturn("jwt");

        LoginResponse response = authService.login(login("CorrectPass1"));

        assertThat(response.getToken()).isEqualTo("jwt");
        assertThat(response.getIdClient()).isEqualTo(1);
    }
}
