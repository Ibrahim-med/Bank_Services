
package com.bankservices.bankservices_api.dto.request;

import com.bankservices.bankservices_api.validation.InputPatterns;
import com.bankservices.bankservices_api.validation.MaxUtf8Bytes;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class LoginRequest {

    @NotBlank(message = "Username is required")
    @Size(max = 50, message = "Username cannot exceed 50 characters")
    private String username;

    // Passwords longer than BCrypt's 72 bytes cannot exist, so longer input is refused
    // instead of being silently truncated during the comparison.
    @NotBlank(message = "Password is required")
    @MaxUtf8Bytes(value = InputPatterns.PASSWORD_MAX_BYTES, message = "Password is too long")
    private String password;

    public LoginRequest() {
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
