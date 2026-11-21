
package com.bankservices.bankservices_api.dto.request;

import com.bankservices.bankservices_api.validation.InputPatterns;
import com.bankservices.bankservices_api.validation.MaxUtf8Bytes;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class RegisterRequest {

    @NotBlank(message = "Username is required")
    @Size(min = 4, max = 50)
    private String username;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 100)
    @MaxUtf8Bytes(value = InputPatterns.PASSWORD_MAX_BYTES,
            message = "Password is too long (maximum 72 bytes)")
    private String password;

    // Was unvalidated: a missing id reached the repository and produced a server error.
    @NotNull(message = "Client is required")
    @Positive(message = "Client is invalid")
    private Integer idClient;

    public RegisterRequest() {
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

    public Integer getIdClient() {
        return idClient;
    }

    public void setIdClient(Integer idClient) {
        this.idClient = idClient;
    }
}
