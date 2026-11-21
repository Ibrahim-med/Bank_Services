
package com.bankservices.bankservices_api.dto.request;

import com.bankservices.bankservices_api.enums.TypeCompte;
import com.bankservices.bankservices_api.validation.InputPatterns;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class CompteRequest {

    @NotBlank
    @Size(max = 30)
    @Pattern(regexp = InputPatterns.ACCOUNT_NUMBER,
            message = "Account number may only contain letters, digits and hyphens")
    private String numero;

    @NotNull
    private TypeCompte typeCompte;

    // DECIMAL(15,2) in the database: larger values are refused here, not by MySQL.
    @DecimalMin("0.00")
    @Digits(integer = 13, fraction = 2, message = "Overdraft must have at most 13 digits and 2 decimals")
    private BigDecimal decouvertAutorise;

    // DECIMAL(5,2) in the database.
    @DecimalMin("0.00")
    @Digits(integer = 3, fraction = 2, message = "Interest rate must have at most 3 digits and 2 decimals")
    private BigDecimal tauxInteret;

    @NotNull
    @Positive
    private Integer idClient;

    public String getNumero() {
        return numero;
    }

    public void setNumero(String numero) {
        this.numero = numero;
    }

    public TypeCompte getTypeCompte() {
        return typeCompte;
    }

    public void setTypeCompte(TypeCompte typeCompte) {
        this.typeCompte = typeCompte;
    }

    public BigDecimal getDecouvertAutorise() {
        return decouvertAutorise;
    }

    public void setDecouvertAutorise(BigDecimal decouvertAutorise) {
        this.decouvertAutorise = decouvertAutorise;
    }

    public BigDecimal getTauxInteret() {
        return tauxInteret;
    }

    public void setTauxInteret(BigDecimal tauxInteret) {
        this.tauxInteret = tauxInteret;
    }

    public Integer getIdClient() {
        return idClient;
    }

    public void setIdClient(Integer idClient) {
        this.idClient = idClient;
    }
}
