
package com.bankservices.bankservices_api.dto.request;

import com.bankservices.bankservices_api.validation.InputPatterns;
import com.bankservices.bankservices_api.validation.MaxUtf8Bytes;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ActivateAccountRequest {

    // Tokens are 43-character URL-safe strings; anything far longer is not a token.
    @NotBlank(message = "Activation token is required")
    @Size(max = 128, message = "This activation link is invalid.")
    private String token;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 100,
            message = "Password must contain between 8 and 100 characters")
    @MaxUtf8Bytes(value = InputPatterns.PASSWORD_MAX_BYTES,
            message = "Password is too long (maximum 72 bytes)")
    private String password;

    @NotBlank(message = "Password confirmation is required")
    @Size(max = 100, message = "Passwords do not match.")
    private String confirmPassword;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }
}
