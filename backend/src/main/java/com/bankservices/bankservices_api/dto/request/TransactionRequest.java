
package com.bankservices.bankservices_api.dto.request;

import com.bankservices.bankservices_api.enums.TypeTransaction;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class TransactionRequest {

    @NotNull
    private TypeTransaction typeTransaction;

    // DECIMAL(15,2): more digits/decimals are refused here instead of failing in MySQL.
    @NotNull
    @DecimalMin(value = "0.01")
    @Digits(integer = 13, fraction = 2, message = "Amount must have at most 13 digits and 2 decimals")
    private BigDecimal montant;

    @Size(max = 255)
    private String description;

    // Used for withdrawals and transfers
    @Positive(message = "Source account is invalid")
    private Integer idCompteSource;

    // Used for deposits and transfers
    @Positive(message = "Destination account is invalid")
    private Integer idCompteDestination;

    public TypeTransaction getTypeTransaction() {
        return typeTransaction;
    }

    public void setTypeTransaction(TypeTransaction typeTransaction) {
        this.typeTransaction = typeTransaction;
    }

    public BigDecimal getMontant() {
        return montant;
    }

    public void setMontant(BigDecimal montant) {
        this.montant = montant;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getIdCompteSource() {
        return idCompteSource;
    }

    public void setIdCompteSource(Integer idCompteSource) {
        this.idCompteSource = idCompteSource;
    }

    public Integer getIdCompteDestination() {
        return idCompteDestination;
    }

    public void setIdCompteDestination(Integer idCompteDestination) {
        this.idCompteDestination = idCompteDestination;
    }
}
