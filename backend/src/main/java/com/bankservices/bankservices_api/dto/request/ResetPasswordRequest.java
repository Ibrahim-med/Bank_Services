
package com.bankservices.bankservices_api.dto.request;

import com.bankservices.bankservices_api.validation.InputPatterns;
import com.bankservices.bankservices_api.validation.MaxUtf8Bytes;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ResetPasswordRequest {

    // Tokens are 43-character URL-safe strings; anything far longer is not a token.
    @NotBlank(message = "Token is required")
    @Size(max = 128, message = "Invalid or expired reset token")
    private String token;

    @NotBlank(message = "New password is required")
    @Size(min = 8, max = 100,
            message = "Password must contain between 8 and 100 characters")
    @MaxUtf8Bytes(value = InputPatterns.PASSWORD_MAX_BYTES,
            message = "Password is too long (maximum 72 bytes)")
    private String newPassword;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
