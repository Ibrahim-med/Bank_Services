
package com.bankservices.bankservices_api.entity;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import java.math.BigDecimal;

@Entity
@DiscriminatorValue("EPARGNE")
public class CompteEpargne extends CompteBancaire {

    public CompteEpargne() {
        super();
    }

    public BigDecimal getTauxInteretEpargne() {
        return super.getTauxInteret();
    }

    public void setTauxInteretEpargne(BigDecimal taux) {
        super.setTauxInteret(taux);
    }
}
