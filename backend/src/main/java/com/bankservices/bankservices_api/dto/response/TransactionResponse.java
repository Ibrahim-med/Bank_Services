
package com.bankservices.bankservices_api.dto.response;

import com.bankservices.bankservices_api.enums.TypeTransaction;
import com.bankservices.bankservices_api.enums.StatutTransaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class TransactionResponse {

    private Integer idTransaction;
    private TypeTransaction typeTransaction;
    private BigDecimal montant;
    private LocalDateTime dateTransaction;
    private String description;
    private StatutTransaction statut;
    private Integer idCompteSource;
    private Integer idCompteDestination;

    public TransactionResponse(
            Integer idTransaction,
            TypeTransaction typeTransaction,
            BigDecimal montant,
            LocalDateTime dateTransaction,
            String description,
            StatutTransaction statut,
            Integer idCompteSource,
            Integer idCompteDestination) {

        this.idTransaction = idTransaction;
        this.typeTransaction = typeTransaction;
        this.montant = montant;
        this.dateTransaction = dateTransaction;
        this.description = description;
        this.statut = statut;
        this.idCompteSource = idCompteSource;
        this.idCompteDestination = idCompteDestination;
    }

    public Integer getIdTransaction() { return idTransaction; }
    public TypeTransaction getTypeTransaction() { return typeTransaction; }
    public BigDecimal getMontant() { return montant; }
    public LocalDateTime getDateTransaction() { return dateTransaction; }
    public String getDescription() { return description; }
    public StatutTransaction getStatut() { return statut; }
    public Integer getIdCompteSource() { return idCompteSource; }
    public Integer getIdCompteDestination() { return idCompteDestination; }
}
