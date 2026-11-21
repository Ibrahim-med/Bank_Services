
package com.bankservices.bankservices_api.dto.request;

import com.bankservices.bankservices_api.validation.InputPatterns;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Administrator registration of a new client together with an online
 * banking login. Unlike ClientRequest, the e-mail is mandatory because
 * the activation link is sent to it.
 */
public class ClientRegistrationRequest {

    // Names accept apostrophes, hyphens, accents, spaces... only control characters are refused.
    @NotBlank(message = "Last name is required")
    @Size(max = 50, message = "Last name cannot exceed 50 characters")
    @Pattern(regexp = InputPatterns.SINGLE_LINE_TEXT, message = "Last name contains invalid characters")
    private String nom;

    @NotBlank(message = "First name is required")
    @Size(max = 50, message = "First name cannot exceed 50 characters")
    @Pattern(regexp = InputPatterns.SINGLE_LINE_TEXT, message = "First name contains invalid characters")
    private String prenom;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Size(max = 100, message = "Email cannot exceed 100 characters")
    private String email;

    @Size(max = 20, message = "Phone number cannot exceed 20 characters")
    @Pattern(regexp = InputPatterns.PHONE,
            message = "Phone number may only contain digits, spaces and + ( ) . - /")
    private String telephone;

    @Size(max = 255, message = "Address cannot exceed 255 characters")
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
