
package com.bankservices.bankservices_api.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class VirementRequest {

    @NotNull(message = "Source account ID is required")
    private Integer idCompteSource;

    @NotNull(message = "Destination account ID is required")
    private Integer idCompteDestination;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    private BigDecimal montant;

    private String description;

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
}
