
package com.bankservices.bankservices_api.dto.response;

public class LoginResponse {

    private String token;
    private String tokenType;
    private String username;
    private String role;
    private Integer idClient;

    public LoginResponse() {
    }

    public LoginResponse(
            String token,
            String tokenType,
            String username,
            String role,
            Integer idClient) {

        this.token = token;
        this.tokenType = tokenType;
        this.username = username;
        this.role = role;
        this.idClient = idClient;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Integer getIdClient() {
        return idClient;
    }

    public void setIdClient(Integer idClient) {
        this.idClient = idClient;
    }
}
