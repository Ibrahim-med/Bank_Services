
package com.bankservices.bankservices_api.entity;

import com.bankservices.bankservices_api.enums.StatutCompte;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "Compte_Bancaire")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(
        name = "type_compte",
        discriminatorType = DiscriminatorType.STRING,
        length = 20
)
public abstract class CompteBancaire {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_compte")
    private Integer idCompte;

    @Column(name = "numero", nullable = false, unique = true, length = 30)
    private String numero;

    @Column(name = "solde", nullable = false,
            precision = 15, scale = 2)
    private BigDecimal solde = BigDecimal.ZERO;

    @Column(name = "decouvert_autorise", nullable = false,
            precision = 15, scale = 2)
    private BigDecimal decouvertAutorise = BigDecimal.ZERO;

    @Column(name = "taux_interet", nullable = false,
            precision = 5, scale = 2)
    private BigDecimal tauxInteret = BigDecimal.ZERO;

    @Column(name = "date_creation", nullable = false)
    private LocalDate dateCreation;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 20)
    private StatutCompte statut = StatutCompte.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_client", nullable = false)
    private Client client;

    @OneToMany(mappedBy = "compteSource")
    private List<TransactionBancaire> transactionsSource =
            new ArrayList<>();

    @OneToMany(mappedBy = "compteDestination")
    private List<TransactionBancaire> transactionsDestination =
            new ArrayList<>();

    public CompteBancaire() {
    }

    @PrePersist
    protected void initialiserDateCreation() {
        if (dateCreation == null) {
            dateCreation = LocalDate.now();
        }
    }

    public Integer getIdCompte() {
        return idCompte;
    }

    public void setIdCompte(Integer idCompte) {
        this.idCompte = idCompte;
    }

    public String getNumero() {
        return numero;
    }

    public void setNumero(String numero) {
        this.numero = numero;
    }

    public BigDecimal getSolde() {
        return solde;
    }

    public void setSolde(BigDecimal solde) {
        this.solde = solde;
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

    public LocalDate getDateCreation() {
        return dateCreation;
    }

    public void setDateCreation(LocalDate dateCreation) {
        this.dateCreation = dateCreation;
    }

    public StatutCompte getStatut() {
        return statut;
    }

    public void setStatut(StatutCompte statut) {
        this.statut = statut;
    }

    public Client getClient() {
        return client;
    }

    public void setClient(Client client) {
        this.client = client;
    }

    public List<TransactionBancaire> getTransactionsSource() {
        return transactionsSource;
    }

    public List<TransactionBancaire> getTransactionsDestination() {
        return transactionsDestination;
    }
}
