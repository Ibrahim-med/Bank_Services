
package com.bankservices.bankservices_api.controller;

import com.bankservices.bankservices_api.dto.request.RegisterRequest;
import com.bankservices.bankservices_api.dto.request.LoginRequest;
import com.bankservices.bankservices_api.dto.request.ForgotPasswordRequest;
import com.bankservices.bankservices_api.dto.request.ResetPasswordRequest;
import com.bankservices.bankservices_api.dto.request.ActivateAccountRequest;
import com.bankservices.bankservices_api.dto.request.ResendActivationRequest;
import com.bankservices.bankservices_api.dto.response.LoginResponse;
import com.bankservices.bankservices_api.service.AccountActivationService;
import com.bankservices.bankservices_api.service.AuthService;
import com.bankservices.bankservices_api.service.PasswordResetService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final AccountActivationService accountActivationService;

    public AuthController(
            AuthService authService,
            PasswordResetService passwordResetService,
            AccountActivationService accountActivationService) {

        this.authService = authService;
        this.passwordResetService = passwordResetService;
        this.accountActivationService = accountActivationService;
    }

    @PostMapping("/register")
    public ResponseEntity<String> register(
            @Valid @RequestBody RegisterRequest request) {

        String message = authService.register(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(message);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request) {

        LoginResponse response = authService.login(request);

        return ResponseEntity.ok(response);
    }

    // Request a password reset email
    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {

        passwordResetService.requestPasswordReset(
                request.getEmail()
        );

        return ResponseEntity.ok(Map.of(
                "message",
                "If an account exists for this email, a password reset link will be sent."
        ));
    }

    // Reset password using the email token
    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {

        passwordResetService.resetPassword(
                request.getToken(),
                request.getNewPassword()
        );

        return ResponseEntity.ok(Map.of(
                "message",
                "Your password has been reset successfully."
        ));
    }

    // Activate a new client account: set the password with the e-mailed token
    @PostMapping("/activate")
    public ResponseEntity<Map<String, String>> activateAccount(
            @Valid @RequestBody ActivateAccountRequest request) {

        accountActivationService.activateAccount(request);

        return ResponseEntity.ok(Map.of(
                "message",
                "Your account has been activated. You can now sign in."
        ));
    }

    // Request a new activation link (same response whether or not the e-mail exists)
    @PostMapping("/resend-activation")
    public ResponseEntity<Map<String, String>> resendActivation(
            @Valid @RequestBody ResendActivationRequest request) {

        accountActivationService.resendActivation(request.getEmail());

        return ResponseEntity.ok(Map.of(
                "message",
                "If a pending account exists for this email, a new activation link will be sent."
        ));
    }
}
