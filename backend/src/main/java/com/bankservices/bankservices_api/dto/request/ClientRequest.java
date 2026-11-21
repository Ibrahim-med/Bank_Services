package com.bankservices.bankservices_api.dto.request;

import com.bankservices.bankservices_api.validation.InputPatterns;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class ClientRequest {

    // Names accept apostrophes, hyphens, accents, spaces... only control characters are refused.
    @NotBlank
    @Size(max = 50)
    @Pattern(regexp = InputPatterns.SINGLE_LINE_TEXT, message = "Last name contains invalid characters")
    private String nom;

    @NotBlank
    @Size(max = 50)
    @Pattern(regexp = InputPatterns.SINGLE_LINE_TEXT, message = "First name contains invalid characters")
    private String prenom;

    @Email
    @Size(max = 100)
    private String email;

    @Size(max = 20)
    @Pattern(regexp = InputPatterns.PHONE,
            message = "Phone number may only contain digits, spaces and + ( ) . - /")
    private String telephone;

    @Size(max = 255)
    @Pattern(regexp = InputPatterns.MULTI_LINE_TEXT, message = "Address contains invalid characters")
    private String adresse;

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }

    public String getPrenom() {
        return prenom;
    }

    public void setPrenom(String prenom) {
        this.prenom = prenom;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getTelephone() {
        return telephone;
    }

    public void setTelephone(String telephone) {
        this.telephone = telephone;
    }

    public String getAdresse() {
        return adresse;
    }

    public void setAdresse(String adresse) {
        this.adresse = adresse;
    }
}