package com.bankservices.bankservices_api.controller;

import com.bankservices.bankservices_api.config.SecurityConfig;
import com.bankservices.bankservices_api.dto.request.ActivateAccountRequest;
import com.bankservices.bankservices_api.dto.request.LoginRequest;
import com.bankservices.bankservices_api.exception.AccountNotActiveException;
import com.bankservices.bankservices_api.service.AccountActivationService;
import com.bankservices.bankservices_api.service.AuthService;
import com.bankservices.bankservices_api.service.PasswordResetService;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Public activation endpoints and login status errors on {@link AuthController}. */
@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthActivationEndpointsTest extends AbstractControllerTest {

    @MockitoBean private AuthService authService;
    @MockitoBean private PasswordResetService passwordResetService;
    @MockitoBean private AccountActivationService accountActivationService;

    @Test
    void activate_isPublic_andForwardsTokenAndPasswords() throws Exception {
        mockMvc.perform(post("/api/auth/activate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"abc\",\"password\":\"NewPassword1\",\"confirmPassword\":\"NewPassword1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", containsString("activated")));

        ArgumentCaptor<ActivateAccountRequest> captor = ArgumentCaptor.forClass(ActivateAccountRequest.class);
        verify(accountActivationService).activateAccount(captor.capture());
        assertThat(captor.getValue().getToken()).isEqualTo("abc");
        assertThat(captor.getValue().getConfirmPassword()).isEqualTo("NewPassword1");
    }

    @Test
    void activate_shortPassword_returns400_withoutCallingTheService() throws Exception {
        mockMvc.perform(post("/api/auth/activate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"abc\",\"password\":\"short\",\"confirmPassword\":\"short\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accountActivationService);
    }

    @Test
    void activate_invalidToken_returns400WithMessage() throws Exception {
        doThrow(new IllegalArgumentException("This activation link has expired. Request a new one."))
                .when(accountActivationService).activateAccount(any());

        mockMvc.perform(post("/api/auth/activate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"old\",\"password\":\"NewPassword1\",\"confirmPassword\":\"NewPassword1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("expired")));
    }

    @Test
    void resendActivation_isPublic_andAlwaysReturnsTheSameMessage() throws Exception {
        mockMvc.perform(post("/api/auth/resend-activation").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"unknown@example.test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", containsString("If a pending account exists")));

        verify(accountActivationService).resendActivation("unknown@example.test");
    }

    @Test
    void login_pendingAccount_returns403WithExplanation() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new AccountNotActiveException("Your account is not activated yet."));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"amartin\",\"password\":\"Whatever1\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("not activated")));
    }
}
