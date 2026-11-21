
package com.bankservices.bankservices_api.dto.response;

import com.bankservices.bankservices_api.enums.TypeCompte;
import com.bankservices.bankservices_api.enums.StatutCompte;

import java.math.BigDecimal;
import java.time.LocalDate;

public class CompteResponse {

    private Integer idCompte;
    private String numero;
    private BigDecimal solde;
    private TypeCompte typeCompte;
    private BigDecimal decouvertAutorise;
    private BigDecimal tauxInteret;
    private LocalDate dateCreation;
    private Integer idClient;
    private StatutCompte statut;

    public CompteResponse(
            Integer idCompte,
            String numero,
            BigDecimal solde,
            TypeCompte typeCompte,
            BigDecimal decouvertAutorise,
            BigDecimal tauxInteret,
            LocalDate dateCreation,
            Integer idClient,
            StatutCompte statut) {

        this.idCompte = idCompte;
        this.numero = numero;
        this.solde = solde;
        this.typeCompte = typeCompte;
        this.decouvertAutorise = decouvertAutorise;
        this.tauxInteret = tauxInteret;
        this.dateCreation = dateCreation;
        this.idClient = idClient;
        this.statut = statut;
    }

    public Integer getIdCompte() {
        return idCompte;
    }

    public String getNumero() {
        return numero;
    }

    public BigDecimal getSolde() {
        return solde;
    }

    public TypeCompte getTypeCompte() {
        return typeCompte;
    }

    public BigDecimal getDecouvertAutorise() {
        return decouvertAutorise;
    }

    public BigDecimal getTauxInteret() {
        return tauxInteret;
    }

    public LocalDate getDateCreation() {
        return dateCreation;
    }

    public Integer getIdClient() {
        return idClient;
    }

    public StatutCompte getStatut() {
        return statut;
    }
}
