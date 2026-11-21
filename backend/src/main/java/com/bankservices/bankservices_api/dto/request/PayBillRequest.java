
package com.bankservices.bankservices_api.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Client: pay a bill from one of their accounts.
 * There is deliberately NO amount field: the amount charged is always
 * the authoritative amount stored on the bill.
 */
public class PayBillRequest {

    @NotNull(message = "Bank account is required")
    @Positive(message = "Bank account is invalid")
    private Integer idCompte;

    public Integer getIdCompte() {
        return idCompte;
    }

    public void setIdCompte(Integer idCompte) {
        this.idCompte = idCompte;
    }
}
