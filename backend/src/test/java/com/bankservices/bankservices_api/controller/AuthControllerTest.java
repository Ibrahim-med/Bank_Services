package com.bankservices.bankservices_api.controller;

import com.bankservices.bankservices_api.config.SecurityConfig;
import com.bankservices.bankservices_api.dto.response.LoginResponse;
import com.bankservices.bankservices_api.service.AccountActivationService;
import com.bankservices.bankservices_api.service.AuthService;
import com.bankservices.bankservices_api.service.PasswordResetService;

import io.jsonwebtoken.MalformedJwtException;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc tests for {@link AuthController}: public access to the auth
 * endpoints, request validation and error mapping.
 */
@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest extends AbstractControllerTest {

    @MockitoBean private AuthService authService;
    @MockitoBean private PasswordResetService passwordResetService;
    @MockitoBean private AccountActivationService accountActivationService;

    // ---------------- register ----------------

    @Test
    void register_withValidPayload_isPublicAndReturns201() throws Exception {
        when(authService.register(any())).thenReturn("User registered successfully");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"alice","password":"Str0ngTestPass","idClient":1}
                                """))
                .andExpect(status().isCreated())
                .andExpect(content().string("User registered successfully"));
    }

    @Test
    void register_withTooShortUsernameAndPassword_returns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"ab","password":"short","idClient":1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message", containsString("username")))
                .andExpect(jsonPath("$.message", containsString("password")));

        verifyNoInteractions(authService);
    }

    @Test
    void register_withDuplicateUsername_returns400WithServiceMessage() throws Exception {
        when(authService.register(any()))
                .thenThrow(new IllegalArgumentException("Username already exists"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"alice","password":"Str0ngTestPass","idClient":1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Username already exists"))
                .andExpect(jsonPath("$.path").value("/api/auth/register"));
    }

    // ---------------- login ----------------

    @Test
    void login_withValidCredentials_returnsTokenPayload() throws Exception {
        when(authService.login(any())).thenReturn(
                new LoginResponse("jwt-token", "Bearer", "alice", "CLIENT", 1));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"alice","password":"Str0ngTestPass"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.role").value("CLIENT"))
                .andExpect(jsonPath("$.idClient").value(1));
    }

    /** Documents CURRENT behaviour: bad credentials map to 400 (not 401). */
    @Test
    void login_withWrongCredentials_returns400WithGenericMessage() throws Exception {
        when(authService.login(any()))
                .thenThrow(new IllegalArgumentException("Invalid username or password"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"alice","password":"wrong-pass"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    void login_withBlankFields_returns400AndServiceIsNotCalled() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"","password":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Username is required")));

        verifyNoInteractions(authService);
    }

    /**
     * Documents CURRENT behaviour: the JWT filter runs on public endpoints too,
     * so a stale/invalid Bearer header makes even /login return 401.
     */
    @Test
    void login_withInvalidBearerHeaderAttached_isRejectedWith401ByJwtFilter() throws Exception {
        when(jwtService.extractUsername("stale-token"))
                .thenThrow(new MalformedJwtException("bad"));

        mockMvc.perform(post("/api/auth/login")
                        .header("Authorization", "Bearer stale-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"alice","password":"Str0ngTestPass"}
                                """))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(authService);
    }

    // ---------------- forgot / reset password ----------------

    @Test
    void forgotPassword_withValidEmail_returnsGenericMessage() throws Exception {
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"alice@example.test"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", containsString("If an account exists")));

        verify(passwordResetService).requestPasswordReset("alice@example.test");
    }

    @Test
    void forgotPassword_withInvalidEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Invalid email format")));

        verifyNoInteractions(passwordResetService);
    }

    @Test
    void resetPassword_withValidPayload_returnsSuccessMessage() throws Exception {
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"raw-token","newPassword":"NewTestPass123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", containsString("reset successfully")));

        verify(passwordResetService).resetPassword("raw-token", "NewTestPass123");
    }

    @Test
    void resetPassword_withShortPassword_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"raw-token","newPassword":"short"}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(passwordResetService);
    }

    @Test
    void resetPassword_withExpiredToken_returns400WithServiceMessage() throws Exception {
        doThrow(new IllegalArgumentException("Invalid or expired reset token."))
                .when(passwordResetService).resetPassword(anyString(), anyString());

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"old-token","newPassword":"NewTestPass123"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid or expired reset token."));
    }
}
