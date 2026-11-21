package com.bankservices.bankservices_api.dto.response;

import com.bankservices.bankservices_api.enums.StatutUtilisateur;

public class ClientResponse {

    private Integer idClient;
    private String nom;
    private String prenom;
    private String email;
    private String telephone;
    private String adresse;
    private String username;
    private StatutUtilisateur accountStatus;

    public ClientResponse() {
    }

    public ClientResponse(Integer idClient, String nom,
                          String prenom, String email,
                          String telephone, String adresse) {
        this.idClient = idClient;
        this.nom = nom;
        this.prenom = prenom;
        this.email = email;
        this.telephone = telephone;
        this.adresse = adresse;
    }

    public Integer getIdClient() {
        return idClient;
    }

    public String getNom() {
        return nom;
    }

    public String getPrenom() {
        return prenom;
    }

    public String getEmail() {
        return email;
    }

    public String getTelephone() {
        return telephone;
    }

    public String getAdresse() {
        return adresse;
    }

    // Online banking login linked to the client (null when none exists).
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    // PENDING_ACTIVATION, ACTIVE, DISABLED, or null when the client has no login.
    public StatutUtilisateur getAccountStatus() {
        return accountStatus;
    }

    public void setAccountStatus(StatutUtilisateur accountStatus) {
        this.accountStatus = accountStatus;
    }
}