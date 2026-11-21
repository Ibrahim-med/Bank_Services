
package com.bankservices.bankservices_api.entity;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import java.math.BigDecimal;

@Entity
@DiscriminatorValue("COURANT")
public class CompteCourant extends CompteBancaire {

    public CompteCourant() {
        super();
    }

    public BigDecimal getDecouvertAutoriseCourant() {
        return super.getDecouvertAutorise();
    }

    public void setDecouvertAutoriseCourant(BigDecimal montant) {
        super.setDecouvertAutorise(montant);
    }
}
