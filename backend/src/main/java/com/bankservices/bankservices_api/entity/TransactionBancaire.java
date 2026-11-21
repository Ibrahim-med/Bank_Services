
package com.bankservices.bankservices_api.entity;

import com.bankservices.bankservices_api.enums.StatutTransaction;
import com.bankservices.bankservices_api.enums.TypeTransaction;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "Transaction_Bancaire")
public class TransactionBancaire {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_transaction")
    private Integer idTransaction;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_transaction", nullable = false)
    private TypeTransaction typeTransaction;

    @Column(name = "montant", nullable = false,
            precision = 15, scale = 2)
    private BigDecimal montant;

    @Column(name = "date_transaction", nullable = false)
    private LocalDateTime dateTransaction;

    @Column(name = "description", length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false)
    private StatutTransaction statut;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_compte_source")
    private CompteBancaire compteSource;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_compte_destination")
    private CompteBancaire compteDestination;

    public TransactionBancaire() {
    }

    @PrePersist
    protected void initialiserDateTransaction() {
        if (dateTransaction == null) {
            dateTransaction = LocalDateTime.now();
        }
    }

    // Getters and setters

    public Integer getIdTransaction() {
        return idTransaction;
    }

    public void setIdTransaction(Integer idTransaction) {
        this.idTransaction = idTransaction;
    }

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

    public LocalDateTime getDateTransaction() {
        return dateTransaction;
    }

    public void setDateTransaction(LocalDateTime dateTransaction) {
        this.dateTransaction = dateTransaction;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public StatutTransaction getStatut() {
        return statut;
    }

    public void setStatut(StatutTransaction statut) {
        this.statut = statut;
    }

    public CompteBancaire getCompteSource() {
        return compteSource;
    }

    public void setCompteSource(CompteBancaire compteSource) {
        this.compteSource = compteSource;
    }

    public CompteBancaire getCompteDestination() {
        return compteDestination;
    }

    public void setCompteDestination(CompteBancaire compteDestination) {
        this.compteDestination = compteDestination;
    }
}
